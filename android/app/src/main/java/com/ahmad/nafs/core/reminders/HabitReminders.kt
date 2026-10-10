package com.ahmad.nafs.core.reminders

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.ahmad.nafs.core.habits.Habit
import com.ahmad.nafs.core.habits.HabitLoadResult
import com.ahmad.nafs.core.habits.HabitLog
import com.ahmad.nafs.core.time.AccountDate
import com.ahmad.nafs.domain.habits.HabitResult
import com.ahmad.nafs.domain.habits.HabitSchedule
import com.ahmad.nafs.domain.habits.HabitStreak
import com.ahmad.nafs.domain.habits.HabitType
import com.ahmad.nafs.domain.habits.ScheduleKind
import com.ahmad.nafs.domain.reminders.HabitReminderFacts
import com.ahmad.nafs.domain.reminders.HabitReminderRules
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** What re-arming one habit's reminder needs without a connection, for example after a reboot. */
@Serializable
internal data class HabitReminderEntry(
    val id: String,
    val name: String,
    val emoji: String,
    val type: String,
    val time: String,
    val scheduleKind: String,
    val scheduleDays: List<String> = emptyList(),
    val weeklyTarget: Int? = null,
) {
    fun localTime(): LocalTime? = runCatching { LocalTime.parse(time) }.getOrNull()

    fun habitType(): HabitType = runCatching { HabitType.valueOf(type) }.getOrDefault(HabitType.SIMPLE)

    fun schedule() = HabitSchedule(
        type = habitType(),
        scheduleKind = runCatching { ScheduleKind.valueOf(scheduleKind) }.getOrDefault(ScheduleKind.DAILY),
        scheduleDays = scheduleDays.mapNotNull { day -> runCatching { DayOfWeek.valueOf(day) }.getOrNull() }.toSet(),
        weeklyTarget = weeklyTarget,
    )

    companion object {
        fun of(habit: Habit, time: LocalTime) = HabitReminderEntry(
            id = habit.id,
            name = habit.name.take(120),
            emoji = habit.emoji.take(16),
            type = habit.type.name,
            time = time.truncatedTo(ChronoUnit.MINUTES).toString(),
            scheduleKind = habit.scheduleKind.name,
            scheduleDays = habit.scheduleDays.map(DayOfWeek::name).sorted(),
            weeklyTarget = habit.weeklyTarget,
        )
    }
}

/** The habits with a reminder, per account, so alarms can be rebuilt offline and stale ones cancelled. */
internal object HabitReminderRegistry {
    private const val FILE = "habit_reminder_registry"
    private val json = Json { ignoreUnknownKeys = true }

    fun read(context: Context, user: String): List<HabitReminderEntry> {
        val raw = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(user, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<HabitReminderEntry>>(raw) }.getOrDefault(emptyList())
    }

    fun write(context: Context, user: String, entries: List<HabitReminderEntry>) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit(commit = true) {
            if (entries.isEmpty()) remove(user) else putString(user, json.encodeToString(entries))
        }
    }
}

/**
 * Per-habit reminders, from `habits.reminder_time` (the same column the web app edits). Each habit with a time
 * gets one exact alarm on its next scheduled day; when it fires it arms the following one, and a notification is
 * shown only if the habit is still open that day.
 */
@Singleton
class HabitReminderScheduler @Inject constructor(@ApplicationContext private val context: Context) {

    /**
     * Replaces this account's habit alarms with [habits]. Call it after an online load or a save: an offline copy can
     * be out of date, and would cancel reminders set on another device.
     */
    fun reconcile(userId: String, habits: List<Habit>, timezone: String) {
        val wanted = habits.mapNotNull { habit ->
            habit.reminderTime?.takeIf { !habit.isPaused }?.let { HabitReminderEntry.of(habit, it) }
        }
        val previous = HabitReminderRegistry.read(context, userId)
        val wantedIds = wanted.map(HabitReminderEntry::id).toSet()
        previous.filter { it.id !in wantedIds }.forEach { forgetAlarms(userId, it.id) }
        HabitReminderRegistry.write(context, userId, wanted)
        val now = Instant.now()
        wanted.forEach { entry ->
            // Leave an unchanged alarm alone; re-arming one that is due in a moment could skip it.
            if (previous.none { it == entry }) arm(userId, entry, timezone, now) else ensureArmed(userId, entry, timezone, now)
        }
    }

    /** Rebuilds alarms from the saved list, for example after a reboot or a clock change. */
    fun rearm(userId: String, timezone: String) {
        val now = Instant.now()
        HabitReminderRegistry.read(context, userId).forEach { arm(userId, it, timezone, now) }
    }

    fun cancelAll(userId: String) {
        HabitReminderRegistry.read(context, userId).forEach { forgetAlarms(userId, it.id) }
        HabitReminderRegistry.write(context, userId, emptyList())
    }

    /** Shows the reminder again in [minutes], once. */
    internal fun snooze(userId: String, habitId: String, minutes: Long) {
        HabitReminderNotifications.cancel(context, habitId)
        ExactAlarms.set(
            context,
            ExactAlarms.intent(context, ReminderAlarmScheduler.ACTION_HABIT, snoozeKey(habitId))
                .putExtra(ReminderAlarmScheduler.EXTRA_USER, userId)
                .putExtra(EXTRA_HABIT_ID, habitId)
                .putExtra(EXTRA_SNOOZED, true),
            Instant.now().plus(minutes, ChronoUnit.MINUTES),
        )
    }

    internal fun forget(userId: String, habitId: String) {
        forgetAlarms(userId, habitId)
        HabitReminderRegistry.write(context, userId, HabitReminderRegistry.read(context, userId).filterNot { it.id == habitId })
    }

    internal fun arm(userId: String, entry: HabitReminderEntry, timezone: String, after: Instant) {
        val time = entry.localTime() ?: return
        val next = HabitReminderRules.nextTrigger(time, entry.schedule(), timezone, after) ?: return
        ExactAlarms.set(context, alarmIntent(userId, entry.id), next)
        slots().edit(commit = true) { putLong(slotKey(userId, entry.id), next.toEpochMilli()) }
    }

    /** Arms [entry] only if no future alarm is recorded for it. */
    private fun ensureArmed(userId: String, entry: HabitReminderEntry, timezone: String, now: Instant) {
        val armed = Instant.ofEpochMilli(slots().getLong(slotKey(userId, entry.id), 0L))
        if (!armed.isAfter(now)) arm(userId, entry, timezone, now)
    }

    private fun forgetAlarms(userId: String, habitId: String) {
        ExactAlarms.cancel(context, ExactAlarms.intent(context, ReminderAlarmScheduler.ACTION_HABIT, alarmKey(habitId)))
        ExactAlarms.cancel(context, ExactAlarms.intent(context, ReminderAlarmScheduler.ACTION_HABIT, snoozeKey(habitId)))
        HabitReminderNotifications.cancel(context, habitId)
        slots().edit(commit = true) { remove(slotKey(userId, habitId)) }
    }

    private fun alarmIntent(userId: String, habitId: String): Intent =
        ExactAlarms.intent(context, ReminderAlarmScheduler.ACTION_HABIT, alarmKey(habitId))
            .putExtra(ReminderAlarmScheduler.EXTRA_USER, userId)
            .putExtra(EXTRA_HABIT_ID, habitId)

    private fun slots() = context.getSharedPreferences(SLOTS, Context.MODE_PRIVATE)

    internal companion object {
        const val EXTRA_HABIT_ID = "habit_id"
        const val EXTRA_SNOOZED = "snoozed"
        private const val SLOTS = "habit_reminder_slots"
        fun alarmKey(habitId: String) = "habit/$habitId"
        fun snoozeKey(habitId: String) = "habit-snooze/$habitId"
        private fun slotKey(userId: String, habitId: String) = "$userId/$habitId"
    }
}

/** Handles a habit alarm: arm the next one first, then remind only if the habit is still open today. */
internal object HabitReminderDelivery {
    suspend fun deliver(context: Context, intent: Intent) {
        val user = intent.getStringExtra(ReminderAlarmScheduler.EXTRA_USER) ?: return
        val habitId = intent.getStringExtra(HabitReminderScheduler.EXTRA_HABIT_ID) ?: return
        val account = ReminderAccount.read(context)?.takeIf { it.user == user } ?: return
        val entry = HabitReminderRegistry.read(context, user).firstOrNull { it.id == habitId } ?: return
        val deps = context.reminderDependencies()
        val scheduler = deps.habitReminders()
        // A snoozed reminder is a one-off; the regular alarm for the next day is already set.
        if (!intent.getBooleanExtra(HabitReminderScheduler.EXTRA_SNOOZED, false)) {
            scheduler.arm(user, entry, account.zone, Instant.now().plusSeconds(60))
        }
        if (!deps.signedIn(user)) return
        val today = runCatching { AccountDate.today(account.zone) }.getOrNull() ?: return
        val loaded = withTimeoutOrNull(12_000L) { deps.habits().load(user, today) } as? HabitLoadResult.Success
        val habit = loaded?.habits?.firstOrNull { it.id == habitId }
        if (loaded != null && !loaded.isOffline && habit == null) {
            // Archived or deleted on this or another device.
            scheduler.forget(user, habitId); return
        }
        val logs = loaded?.logs.orEmpty().filter { it.habitId == habitId }
        val todayLog = logs.firstOrNull { it.date == today }
        if (habit != null && !HabitReminderGate.shouldRemind(habit, logs, today)) return
        if (ReminderAccount.read(context)?.user != user) return
        HabitReminderNotifications.show(context, user, entry, habit, todayLog, today)
    }
}

internal object HabitReminderGate {
    /** Remind only on a scheduled day while the habit is unpaused, not yet complete and, per-week, under target. */
    fun shouldRemind(habit: Habit, logs: List<HabitLog>, today: LocalDate): Boolean {
        val schedule = habit.schedule()
        if (habit.isPaused || !HabitStreak.isScheduled(schedule, today)) return false
        val results = logs.map { HabitResult(it.date, it.completed, it.value, it.durationMinutes) }
        if (HabitStreak.isComplete(schedule, results.firstOrNull { it.date == today })) return false
        return !HabitReminderRules.weeklyTargetMet(schedule, results, today)
    }

    fun facts(habit: Habit, todayLog: HabitLog?) = HabitReminderFacts(
        name = habit.name,
        emoji = habit.emoji,
        type = habit.type,
        targetValue = habit.targetValue,
        unit = habit.unit,
        timeTargetMinutes = habit.timeTargetMinutes,
        subjectName = habit.subjectName,
        subjectPosition = habit.subjectPosition,
        subjectTotal = habit.subjectTotal,
        subjectUnit = habit.subjectUnit,
        currentStreak = habit.currentStreak,
        loggedValue = todayLog?.value ?: 0.0,
        loggedMinutes = todayLog?.durationMinutes ?: 0,
    )
}

internal object HabitReminderNotifications {
    private fun tag(habitId: String) = "habit:$habitId"

    /** [habit] is null when today's copy could not be loaded; the reminder still goes out, with simpler text. */
    fun show(context: Context, user: String, entry: HabitReminderEntry, habit: Habit?, todayLog: HabitLog?, date: LocalDate) {
        if (!NafsNotifications.allowed(context)) return
        val facts = habit?.let { HabitReminderGate.facts(it, todayLog) }
            ?: HabitReminderFacts(name = entry.name, emoji = entry.emoji, type = entry.habitType())
        val type = habit?.type ?: entry.habitType()
        val key = tag(entry.id)
        val builder = NafsNotifications.builder(context, NafsChannel.HABITS)
            .setContentTitle("${entry.emoji} ${entry.name}".trim())
            .setContentText(HabitReminderRules.reminderBody(facts))
            .setStyle(NotificationCompat.BigTextStyle().bigText(HabitReminderRules.reminderBody(facts)))
            .setContentIntent(NafsNotifications.openApp(context, key, "habits"))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
        if (type == HabitType.SIMPLE) {
            builder.addAction(0, "Done", ReminderActions.pending(context, ReminderActions.HABIT_DONE, key) {
                putExtra(ReminderActions.EXTRA_USER, user)
                putExtra(ReminderActions.EXTRA_ID, entry.id)
                putExtra(ReminderActions.EXTRA_DATE, date.toString())
            })
        } else {
            builder.addAction(0, "Log", NafsNotifications.openApp(context, "$key/log", "habits"))
        }
        builder.addAction(0, "In 1 hour", ReminderActions.pending(context, ReminderActions.HABIT_SNOOZE, key) {
            putExtra(ReminderActions.EXTRA_USER, user)
            putExtra(ReminderActions.EXTRA_ID, entry.id)
        })
        NafsNotifications.notify(context, key, builder)
    }

    fun cancel(context: Context, habitId: String) {
        NotificationManagerCompat.from(context).cancel(tag(habitId), 0)
    }
}
