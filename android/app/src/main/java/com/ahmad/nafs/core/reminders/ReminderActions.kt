package com.ahmad.nafs.core.reminders

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ahmad.nafs.core.deen.DeenLoadResult
import com.ahmad.nafs.core.deen.DeenSaveResult
import com.ahmad.nafs.core.deen.Prayer
import com.ahmad.nafs.core.deen.PrayerLog
import com.ahmad.nafs.core.deen.PrayerStatus
import com.ahmad.nafs.core.habits.HabitActionResult
import com.ahmad.nafs.core.habits.HabitLoadResult
import com.ahmad.nafs.core.habits.LogHabitIntent
import com.ahmad.nafs.core.tasks.TaskActionResult
import com.ahmad.nafs.widget.NafsWidgets
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Buttons on NAFS notifications: mark a task or habit done, mark a prayer, or snooze. */
object ReminderActions {
    const val TASK_DONE = "com.ahmad.nafs.action.TASK_DONE"
    const val TASK_SNOOZE = "com.ahmad.nafs.action.TASK_SNOOZE"
    const val HABIT_DONE = "com.ahmad.nafs.action.HABIT_DONE"
    const val HABIT_SNOOZE = "com.ahmad.nafs.action.HABIT_SNOOZE"
    const val PRAYER_DONE = "com.ahmad.nafs.action.PRAYER_DONE"
    internal val all = setOf(TASK_DONE, TASK_SNOOZE, HABIT_DONE, HABIT_SNOOZE, PRAYER_DONE)

    const val EXTRA_USER = "user"
    const val EXTRA_ID = "id"
    const val EXTRA_TITLE = "title"
    const val EXTRA_DATE = "date"
    const val EXTRA_DUE = "due"
    const val EXTRA_STATUS = "status"
    /** Tag of the notification the button belongs to, removed as soon as the button is pressed. */
    const val EXTRA_NOTIFICATION = "notification"

    /**
     * A broadcast for [action]. [requestKey] must differ between buttons (it becomes the intent data, which keeps
     * PendingIntents apart); [notificationTag] is the notification to dismiss.
     */
    fun pending(
        context: Context,
        action: String,
        requestKey: String,
        notificationTag: String = requestKey,
        extras: Intent.() -> Unit,
    ): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java)
            .setAction(action)
            .setData("nafs-action://${Uri.encode(requestKey)}/${action.substringAfterLast('.')}".toUri())
            .putExtra(EXTRA_NOTIFICATION, notificationTag)
            .apply(extras)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    internal fun data(intent: Intent): Data = Data.Builder().apply {
        listOf(EXTRA_USER, EXTRA_ID, EXTRA_TITLE, EXTRA_DATE, EXTRA_NOTIFICATION).forEach { key ->
            intent.getStringExtra(key)?.let { putString(key, it) }
        }
        if (intent.hasExtra(EXTRA_DUE)) putLong(EXTRA_DUE, intent.getLongExtra(EXTRA_DUE, 0L))
        if (intent.hasExtra(EXTRA_STATUS)) putInt(EXTRA_STATUS, intent.getIntExtra(EXTRA_STATUS, PrayerStatus.ALONE.value))
    }.build()
}

/**
 * Receives notification buttons. Snoozes are local. Writes are tried straight away and, if they fail (offline, slow
 * network), handed to [ReminderActionWorker], which retries once a connection is back.
 */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action?.takeIf { it in ReminderActions.all } ?: return
        val app = context.applicationContext
        // Immediate feedback: the notification goes away as soon as a button is pressed.
        intent.getStringExtra(ReminderActions.EXTRA_NOTIFICATION)?.let { NafsNotifications.cancel(app, it) }
        val user = intent.getStringExtra(ReminderActions.EXTRA_USER) ?: return
        val id = intent.getStringExtra(ReminderActions.EXTRA_ID) ?: return
        when (action) {
            ReminderActions.TASK_SNOOZE -> {
                val title = intent.getStringExtra(ReminderActions.EXTRA_TITLE) ?: return
                app.reminderDependencies().taskReminders()
                    .snooze(user, id, title, intent.getLongExtra(ReminderActions.EXTRA_DUE, 0L), TaskReminderNotifications.SNOOZE_MINUTES)
                return
            }
            ReminderActions.HABIT_SNOOZE -> {
                app.reminderDependencies().habitReminders().snooze(user, id, HABIT_SNOOZE_MINUTES)
                return
            }
        }
        val data = ReminderActions.data(intent)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val outcome = withTimeoutOrNull(9_000L) { ReminderActionHandler.run(app, action, data) } ?: ActionOutcome.RETRY
                if (outcome == ActionOutcome.RETRY) ReminderActionWorker.enqueue(app, action, data)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                ReminderActionWorker.enqueue(app, action, data)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val HABIT_SNOOZE_MINUTES = 60L
    }
}

internal enum class ActionOutcome { DONE, RETRY, DROP }

internal object ReminderActionHandler {
    suspend fun run(context: Context, action: String, data: Data): ActionOutcome {
        val user = data.getString(ReminderActions.EXTRA_USER) ?: return ActionOutcome.DROP
        val id = data.getString(ReminderActions.EXTRA_ID) ?: return ActionOutcome.DROP
        val deps = context.reminderDependencies()
        // Signed out, or another account signed in, since the notification was shown: nothing to write.
        if (ReminderAccount.read(context)?.user != user || !deps.signedIn(user)) return ActionOutcome.DROP
        val outcome = when (action) {
            ReminderActions.TASK_DONE -> when (deps.tasks().setCompleted(user, id, true)) {
                is TaskActionResult.Success -> { deps.taskReminders().cancelTask(id); ActionOutcome.DONE }
                is TaskActionResult.Failure -> ActionOutcome.RETRY
            }
            ReminderActions.HABIT_DONE -> habitDone(deps, user, id, date(data) ?: return ActionOutcome.DROP)
            ReminderActions.PRAYER_DONE -> prayerDone(deps, user, id, date(data) ?: return ActionOutcome.DROP,
                data.getInt(ReminderActions.EXTRA_STATUS, PrayerStatus.ALONE.value))
            else -> ActionOutcome.DROP
        }
        if (outcome == ActionOutcome.DONE) NafsWidgets.refresh(context)
        return outcome
    }

    private fun date(data: Data): LocalDate? = data.getString(ReminderActions.EXTRA_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private suspend fun habitDone(deps: ScheduledReminderDependencies, user: String, habitId: String, date: LocalDate): ActionOutcome {
        val loaded = deps.habits().load(user, date) as? HabitLoadResult.Success ?: return ActionOutcome.RETRY
        // Logging needs the server; an offline copy also can't tell whether it was already done elsewhere.
        if (loaded.isOffline) return ActionOutcome.RETRY
        val habit = loaded.habits.firstOrNull { it.id == habitId } ?: return ActionOutcome.DROP
        val existing = loaded.logs.firstOrNull { it.habitId == habitId && it.date == date }
        if (existing?.completed == true) return ActionOutcome.DONE
        val intent = LogHabitIntent(
            userId = user,
            habit = habit,
            date = date,
            value = maxOf(existing?.value ?: 0.0, 1.0),
            durationMinutes = existing?.durationMinutes ?: 0,
            completed = true,
            notes = existing?.notes,
        )
        return when (deps.habits().log(intent)) {
            is HabitActionResult.Success -> ActionOutcome.DONE
            is HabitActionResult.Failure -> ActionOutcome.RETRY
        }
    }

    private suspend fun prayerDone(deps: ScheduledReminderDependencies, user: String, name: String, date: LocalDate, statusValue: Int): ActionOutcome {
        val prayer = Prayer.entries.firstOrNull { it.label == name } ?: return ActionOutcome.DROP
        val status = PrayerStatus.entries.firstOrNull { it.value == statusValue && it != PrayerStatus.MISSED } ?: PrayerStatus.ALONE
        // One retry after a conflict: the log changed (for example on the web) between reading and saving.
        repeat(2) {
            val loaded = deps.deen().load(user, date) as? DeenLoadResult.Success ?: return ActionOutcome.RETRY
            if (loaded.isOffline) return ActionOutcome.RETRY
            val current = loaded.log
            // Already marked, here or elsewhere; never downgrade Jamaat to Alone.
            if (current != null && current.status(prayer) >= status.value) return ActionOutcome.DONE
            val draft = (current ?: PrayerLog(UUID.randomUUID().toString(), user, date.toString())).withStatus(prayer, status)
            when (deps.deen().save(draft, current)) {
                is DeenSaveResult.Saved -> return ActionOutcome.DONE
                DeenSaveResult.Conflict -> Unit
                is DeenSaveResult.Failure -> return ActionOutcome.RETRY
            }
        }
        return ActionOutcome.RETRY
    }
}

/** Retries a notification button's write once online. Gives up after a few attempts and says so. */
class ReminderActionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val action = inputData.getString(KEY_ACTION) ?: return Result.failure()
        return when (ReminderActionHandler.run(applicationContext, action, inputData)) {
            ActionOutcome.DONE, ActionOutcome.DROP -> Result.success()
            ActionOutcome.RETRY -> if (runAttemptCount + 1 < MAX_ATTEMPTS) Result.retry() else {
                reportFailure(action)
                Result.success()
            }
        }
    }

    private fun reportFailure(action: String) {
        val user = inputData.getString(ReminderActions.EXTRA_USER) ?: return
        if (ReminderAccount.read(applicationContext)?.user != user) return
        val (text, destination) = when (action) {
            ReminderActions.TASK_DONE -> {
                val title = inputData.getString(ReminderActions.EXTRA_TITLE)?.let { "“$it”" } ?: "the task"
                "Couldn't mark $title done. Open NAFS to finish it." to "tasks"
            }
            ReminderActions.HABIT_DONE -> "Couldn't save your habit. Open NAFS to log it." to "habits"
            else -> "Couldn't save your prayer. Open NAFS to mark it." to "deen"
        }
        val key = "action-failed-$user-${action.substringAfterLast('.')}"
        NafsNotifications.notify(
            applicationContext,
            key,
            NafsNotifications.builder(applicationContext, NafsChannel.ACTIVITY)
                .setContentTitle("Not saved yet")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(NafsNotifications.openApp(applicationContext, key, destination)),
        )
    }

    companion object {
        private const val KEY_ACTION = "action"
        private const val MAX_ATTEMPTS = 6

        fun enqueue(context: Context, action: String, data: Data) {
            val name = "reminder-action-${action.substringAfterLast('.')}-${data.getString(ReminderActions.EXTRA_ID)}-${data.getString(ReminderActions.EXTRA_DATE)}"
            WorkManager.getInstance(context).enqueueUniqueWork(
                name,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ReminderActionWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .setInputData(Data.Builder().putAll(data).putString(KEY_ACTION, action).build())
                    .build(),
            )
        }
    }
}
