package com.ahmad.nafs.core.reminders

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ahmad.nafs.MainActivity
import com.ahmad.nafs.core.tasks.NafsTask
import com.ahmad.nafs.domain.tasks.TaskPeriodType
import com.ahmad.nafs.domain.tasks.TaskReminderDeliveryRule
import com.ahmad.nafs.domain.tasks.TaskReminderPlanner
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.temporal.ChronoUnit
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
        val options = ReminderPreferences.read(context, userId)
        if (!options.tasks) {
            TaskReminderRegistry.clearUser(context, userId); return
        }
        eligible.forEach { schedule(it, timezone, options.taskLeadMinutes) }
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

    fun ensureNotificationChannel() = NafsNotifications.ensureChannels(context)

    /** Shows the reminder again in [minutes]; it still goes quiet once the task is completed or deleted. */
    internal fun snooze(userId: String, taskId: String, title: String, dueAtEpochMillis: Long, minutes: Long) {
        TaskReminderNotifications.cancel(context, taskId)
        ExactAlarms.set(
            context,
            ExactAlarms.intent(context, ReminderAlarmScheduler.ACTION_TASK, snoozeKey(taskId))
                .putExtra(TaskReminderWorker.KEY_TASK_ID, taskId)
                .putExtra(TaskReminderWorker.KEY_USER_ID, userId)
                .putExtra(TaskReminderWorker.KEY_TITLE, title)
                .putExtra(TaskReminderWorker.KEY_DUE_AT_EPOCH_MILLIS, dueAtEpochMillis)
                .putExtra(TaskReminderWorker.KEY_SNOOZED, true),
            Instant.now().plus(minutes, ChronoUnit.MINUTES),
        )
    }

    private fun schedule(task: NafsTask, timezone: String, leadMinutes: Int) {
        val now = Instant.now()
        val dueTime = task.dueTime ?: return
        TaskReminderPlanner.plan(
            periodDate = task.periodDate,
            dueTime = dueTime,
            timezone = timezone,
            now = now,
            leadMinutes = leadMinutes,
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

    /** Every lead time a version of the app may have used, so changing the setting never leaves an old alarm behind. */
    private fun cancelAlarms(taskId: String) {
        (ReminderOptions.taskLeadChoices + LEGACY_LEADS).distinct().forEach {
            ExactAlarms.cancel(context, ExactAlarms.intent(context, ReminderAlarmScheduler.ACTION_TASK, alarmKey(taskId, it)))
        }
        ExactAlarms.cancel(context, ExactAlarms.intent(context, ReminderAlarmScheduler.ACTION_TASK, snoozeKey(taskId)))
    }

    private fun alarmKey(taskId: String, minutesBefore: Int) = "task/$taskId/$minutesBefore"

    private fun snoozeKey(taskId: String) = "task/$taskId/snooze"

    private suspend fun cancelByTag(tag: String) {
        withContext(Dispatchers.IO) {
            runCatching { workManager.cancelAllWorkByTag(tag).result.get() }
        }
    }

    private fun userTag(userId: String) = "nafs-task-reminders-user-$userId"

    private fun taskTag(taskId: String) = "nafs-task-reminders-task-$taskId"

    private companion object {
        const val MAX_NOTIFICATION_TITLE_LENGTH = 200
        val LEGACY_LEADS = listOf(30, 60)
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
        intent.getBooleanExtra(TaskReminderWorker.KEY_SNOOZED, false),
    )

    fun deliver(context: Context, taskId: String?, userId: String?, title: String?, dueAtEpochMillis: Long, snoozed: Boolean = false) {
        if (taskId == null || userId == null || title == null || dueAtEpochMillis <= 0L) return
        if (!ReminderPreferences.read(context, userId).tasks) return
        if (ReminderAccount.read(context)?.user?.let { it != userId } == true) return
        // Completed, deleted and signed-out tasks leave the registry, so their alarms stay silent.
        if (!TaskReminderRegistry.isActive(context, userId, taskId)) return
        val dueAt = Instant.ofEpochMilli(dueAtEpochMillis)
        val now = Instant.now()
        // A first alert that arrives after the due time is pointless; a snoozed one may say the task is overdue.
        val timing = (
            if (snoozed) TaskReminderDeliveryRule.dueText(dueAt, now)
            else TaskReminderDeliveryRule.minutesRemaining(dueAt, now)?.let { TaskReminderDeliveryRule.dueText(dueAt, now) }
        ) ?: return
        TaskReminderNotifications.show(context, userId, taskId, title, timing, dueAtEpochMillis)
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
        const val KEY_SNOOZED = "snoozed"
    }
}

internal object TaskReminderNotifications {
    const val SNOOZE_MINUTES = 10L

    private fun tag(taskId: String) = "task:$taskId"

    fun show(context: Context, userId: String, taskId: String, title: String, timing: String, dueAtEpochMillis: Long) {
        val key = tag(taskId)
        val builder = NafsNotifications.builder(context, NafsChannel.TASKS)
            .setContentTitle(title)
            .setContentText(timing)
            .setContentIntent(NafsNotifications.openApp(context, key, MainActivity.DESTINATION_TASKS))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(0, "Mark done", ReminderActions.pending(context, ReminderActions.TASK_DONE, key) {
                putExtra(ReminderActions.EXTRA_USER, userId)
                putExtra(ReminderActions.EXTRA_ID, taskId)
                putExtra(ReminderActions.EXTRA_TITLE, title)
            })
            .addAction(0, "Snooze $SNOOZE_MINUTES min", ReminderActions.pending(context, ReminderActions.TASK_SNOOZE, key) {
                putExtra(ReminderActions.EXTRA_USER, userId)
                putExtra(ReminderActions.EXTRA_ID, taskId)
                putExtra(ReminderActions.EXTRA_TITLE, title)
                putExtra(ReminderActions.EXTRA_DUE, dueAtEpochMillis)
            })
        NafsNotifications.notify(context, key, builder)
    }

    fun cancel(context: Context, taskId: String) = NafsNotifications.cancel(context, tag(taskId))
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
