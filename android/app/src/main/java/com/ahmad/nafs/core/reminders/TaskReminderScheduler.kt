package com.ahmad.nafs.core.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ahmad.nafs.MainActivity
import com.ahmad.nafs.R
import com.ahmad.nafs.core.tasks.NafsTask
import com.ahmad.nafs.domain.tasks.TaskPeriodType
import com.ahmad.nafs.domain.tasks.TaskReminderPlanner
import com.ahmad.nafs.domain.tasks.TaskReminderDeliveryRule
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class TaskReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager = WorkManager.getInstance(context)

    suspend fun reconcile(userId: String, tasks: List<NafsTask>, timezone: String) {
        cancelByTag(userTag(userId))
        TaskReminderRegistry.taskIdsForUser(context, userId).forEach { taskId ->
            TaskReminderNotifications.cancel(context, taskId)
            cancelAlarms(taskId)
        }
        val eligible = tasks.asSequence()
            .filter { it.type == TaskPeriodType.DAILY && !it.isCompleted && it.dueTime != null }
            .toList()
        TaskReminderRegistry.replaceForUser(context, userId, eligible.map(NafsTask::id).toSet())
        if (!ReminderPreferences.read(context, userId).tasks) {
            TaskReminderRegistry.clearUser(context, userId); return
        }
        eligible.forEach { schedule(it, timezone) }
    }

    suspend fun cancelTask(taskId: String) {
        TaskReminderRegistry.removeTask(context, taskId)
        TaskReminderNotifications.cancel(context, taskId)
        cancelAlarms(taskId)
        cancelByTag(taskTag(taskId))
    }

    suspend fun cancelAll(userId: String) {
        TaskReminderRegistry.taskIdsForUser(context, userId).forEach { taskId ->
            TaskReminderNotifications.cancel(context, taskId)
            cancelAlarms(taskId)
        }
        TaskReminderRegistry.clearUser(context, userId)
        cancelByTag(userTag(userId))
    }

    fun ensureNotificationChannel() = TaskReminderNotifications.ensureChannel(context)

    private fun schedule(task: NafsTask, timezone: String) {
        TaskReminderNotifications.ensureChannel(context)
        val now = Instant.now()
        val dueTime = task.dueTime ?: return
        TaskReminderPlanner.plan(
            periodDate = task.periodDate,
            dueTime = dueTime,
            timezone = timezone,
            now = now,
        ).forEach { reminder ->
            // An exact alarm, like prayer alerts: a deferred background job could be dropped after the due time.
            ExactAlarms.set(
                context,
                ExactAlarms.intent(context, ReminderAlarmScheduler.ACTION_TASK, alarmKey(task.id, reminder.minutesBefore))
                    .putExtra(TaskReminderWorker.KEY_TASK_ID, task.id)
                    .putExtra(TaskReminderWorker.KEY_USER_ID, task.userId)
                    .putExtra(TaskReminderWorker.KEY_TITLE, task.title.take(MAX_NOTIFICATION_TITLE_LENGTH))
                    .putExtra(TaskReminderWorker.KEY_DUE_AT_EPOCH_MILLIS,
                        reminder.triggerAt.plusSeconds(reminder.minutesBefore * 60L).toEpochMilli()),
                reminder.triggerAt,
            )
        }
    }

    /** Includes the earlier 60-minute offset so alarms from previous versions are also removed. */
    private fun cancelAlarms(taskId: String) = listOf(30, 60).forEach {
        ExactAlarms.cancel(context, ExactAlarms.intent(context, ReminderAlarmScheduler.ACTION_TASK, alarmKey(taskId, it)))
    }

    private fun alarmKey(taskId: String, minutesBefore: Int) = "task/$taskId/$minutesBefore"

    private suspend fun cancelByTag(tag: String) {
        withContext(Dispatchers.IO) {
            runCatching { workManager.cancelAllWorkByTag(tag).result.get() }
        }
    }

    private fun userTag(userId: String) = "nafs-task-reminders-user-$userId"

    private fun taskTag(taskId: String) = "nafs-task-reminders-task-$taskId"

    private companion object {
        const val MAX_NOTIFICATION_TITLE_LENGTH = 200
    }
}

/** Shared by the task alarm and by reminders queued as background work before alarms were used. */
internal object TaskReminderDelivery {
    fun deliver(context: Context, intent: Intent) = deliver(
        context,
        intent.getStringExtra(TaskReminderWorker.KEY_TASK_ID),
        intent.getStringExtra(TaskReminderWorker.KEY_USER_ID),
        intent.getStringExtra(TaskReminderWorker.KEY_TITLE),
        intent.getLongExtra(TaskReminderWorker.KEY_DUE_AT_EPOCH_MILLIS, 0L),
    )

    fun deliver(context: Context, taskId: String?, userId: String?, title: String?, dueAtEpochMillis: Long) {
        if (taskId == null || userId == null || title == null || dueAtEpochMillis <= 0L) return
        if (!ReminderPreferences.read(context, userId).tasks) return
        if (ReminderAccount.read(context)?.user?.let { it != userId } == true) return
        // Completed, deleted and signed-out tasks leave the registry, so their alarms stay silent.
        if (!TaskReminderRegistry.isActive(context, userId, taskId)) return
        val minutesRemaining = TaskReminderDeliveryRule.minutesRemaining(Instant.ofEpochMilli(dueAtEpochMillis), Instant.now()) ?: return
        TaskReminderNotifications.show(context = context, taskId = taskId, title = title, minutesRemaining = minutesRemaining)
    }
}

class TaskReminderWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val dueAtEpochMillis = inputData.getLong(KEY_DUE_AT_EPOCH_MILLIS, 0L)
        if (inputData.getString(KEY_TASK_ID) == null || dueAtEpochMillis <= 0L) return Result.failure()
        TaskReminderDelivery.deliver(applicationContext, inputData.getString(KEY_TASK_ID), inputData.getString(KEY_USER_ID),
            inputData.getString(KEY_TITLE), dueAtEpochMillis)
        return Result.success()
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        const val KEY_USER_ID = "user_id"
        const val KEY_TITLE = "title"
        const val KEY_MINUTES_BEFORE = "minutes_before"
        const val KEY_DUE_AT_EPOCH_MILLIS = "due_at_epoch_millis"
    }
}

private object TaskReminderNotifications {
    private const val CHANNEL_ID = "task_due_reminders"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.task_reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.task_reminder_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    fun show(
        context: Context,
        taskId: String,
        title: String,
        minutesRemaining: Int,
    ) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        ensureChannel(context)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.DESTINATION_TASKS)
            putExtra(MainActivity.EXTRA_TASK_ID, taskId)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            taskId.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val timing = if (minutesRemaining == 1) "1 minute" else "$minutesRemaining minutes"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("Due in $timing")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setColor(NOTIFICATION_ACCENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        manager.notify("task:$taskId", 0, notification)
    }

    fun cancel(context: Context, taskId: String) {
        NotificationManagerCompat.from(context).cancel("task:$taskId", 0)
    }
}

private object TaskReminderRegistry {
    private const val PREFERENCES = "task_reminder_registry"
    private const val ACTIVE_KEYS = "active_task_keys"

    fun replaceForUser(context: Context, userId: String, taskIds: Set<String>) {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val retained = preferences.getStringSet(ACTIVE_KEYS, emptySet()).orEmpty()
            .filterNot { it.startsWith("$userId:") }
            .toMutableSet()
        retained += taskIds.map { key(userId, it) }
        preferences.edit(commit = true) { putStringSet(ACTIVE_KEYS, retained) }
    }

    fun removeTask(context: Context, taskId: String) {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val retained = preferences.getStringSet(ACTIVE_KEYS, emptySet()).orEmpty()
            .filterNot { it.endsWith(":$taskId") }
            .toSet()
        preferences.edit(commit = true) { putStringSet(ACTIVE_KEYS, retained) }
    }

    fun clearUser(context: Context, userId: String) {
        replaceForUser(context, userId, emptySet())
    }

    fun isActive(context: Context, userId: String, taskId: String): Boolean =
        key(userId, taskId) in context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getStringSet(ACTIVE_KEYS, emptySet()).orEmpty()

    fun taskIdsForUser(context: Context, userId: String): Set<String> =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getStringSet(ACTIVE_KEYS, emptySet()).orEmpty()
            .filter { it.startsWith("$userId:") }
            .map { it.removePrefix("$userId:") }
            .toSet()

    private fun key(userId: String, taskId: String) = "$userId:$taskId"
}
