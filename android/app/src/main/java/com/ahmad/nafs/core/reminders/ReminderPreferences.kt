package com.ahmad.nafs.core.reminders

import android.content.Context
import com.ahmad.nafs.domain.reminders.HabitReminderRules
import com.ahmad.nafs.domain.reminders.ScheduledReminderRules
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reminder settings for one account on this device. [habits] is the evening habit check at [habitCheckTime];
 * per-habit reminders are set on each habit and need no switch here.
 */
data class ReminderOptions(
    val tasks: Boolean = true, val habits: Boolean = false, val letters: Boolean = false,
    val prayers: Boolean = false, val daily: Boolean = false, val weekly: Boolean = false,
    val morning: Boolean = false,
    val morningTime: LocalTime = ScheduledReminderRules.morningPlanTime,
    val habitCheckTime: LocalTime = HabitReminderRules.defaultHabitCheckTime,
    val dailyTime: LocalTime = ScheduledReminderRules.dailySummaryTime,
    val taskLeadMinutes: Int = DEFAULT_TASK_LEAD,
) {
    companion object {
        const val DEFAULT_TASK_LEAD = 30
        val taskLeadChoices = listOf(10, 15, 30, 60)
    }
}

@Singleton
class ReminderPreferences @Inject constructor(@ApplicationContext private val context: Context) {
    fun read(user: String) = read(context, user)

    fun save(user: String, value: ReminderOptions) {
        check(context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean("$user/tasks", value.tasks).putBoolean("$user/habits", value.habits)
            .putBoolean("$user/letters", value.letters).putBoolean("$user/prayers", value.prayers)
            .putBoolean("$user/daily", value.daily).putBoolean("$user/weekly", value.weekly)
            .putBoolean("$user/morning", value.morning)
            .putString("$user/morning_time", value.morningTime.toString())
            .putString("$user/habit_check_time", value.habitCheckTime.toString())
            .putString("$user/daily_time", value.dailyTime.toString())
            .putInt("$user/task_lead", value.taskLeadMinutes).commit()) { "Unable to save reminder preferences" }
    }

    companion object {
        private const val FILE = "reminder_preferences"

        fun read(context: Context, user: String): ReminderOptions {
            val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            fun time(key: String, fallback: LocalTime) =
                preferences.getString("$user/$key", null)?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: fallback
            return ReminderOptions(
                tasks = preferences.getBoolean("$user/tasks", true),
                habits = preferences.getBoolean("$user/habits", false),
                letters = preferences.getBoolean("$user/letters", false),
                prayers = preferences.getBoolean("$user/prayers", false),
                daily = preferences.getBoolean("$user/daily", false),
                weekly = preferences.getBoolean("$user/weekly", false),
                morning = preferences.getBoolean("$user/morning", false),
                morningTime = time("morning_time", ScheduledReminderRules.morningPlanTime),
                habitCheckTime = time("habit_check_time", HabitReminderRules.defaultHabitCheckTime),
                dailyTime = time("daily_time", ScheduledReminderRules.dailySummaryTime),
                taskLeadMinutes = preferences.getInt("$user/task_lead", ReminderOptions.DEFAULT_TASK_LEAD)
                    .takeIf { it in ReminderOptions.taskLeadChoices } ?: ReminderOptions.DEFAULT_TASK_LEAD,
            )
        }
    }
}
