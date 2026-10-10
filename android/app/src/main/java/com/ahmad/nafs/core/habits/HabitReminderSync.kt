package com.ahmad.nafs.core.habits

import com.ahmad.nafs.core.reminders.HabitReminderScheduler
import javax.inject.Inject

/**
 * Rebuilds this account's per-habit reminder alarms from a habit list. Callers pass only a list the server just
 * confirmed: an offline copy can be out of date and would cancel reminders set on another device.
 */
fun interface HabitReminderSync {
    fun reconcile(userId: String, habits: List<Habit>, timezone: String)
}

/** The device's alarms, through [HabitReminderScheduler]. */
class AlarmHabitReminderSync @Inject constructor(
    private val scheduler: HabitReminderScheduler,
) : HabitReminderSync {
    override fun reconcile(userId: String, habits: List<Habit>, timezone: String) {
        try {
            scheduler.reconcile(userId, habits, timezone)
        } catch (_: RuntimeException) {
            // An alarm the system refused must not undo the save that asked for it; the next online load retries.
        }
    }
}
