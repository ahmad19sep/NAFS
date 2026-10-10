package com.ahmad.nafs.core.reminders

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class ReminderOptions(
    val tasks: Boolean = true, val habits: Boolean = false, val letters: Boolean = false,
    val prayers: Boolean = false, val daily: Boolean = false, val weekly: Boolean = false,
)
@Singleton
class ReminderPreferences @Inject constructor(@ApplicationContext private val context: Context) {
    fun read(user: String) = read(context, user)
    fun save(user: String, value: ReminderOptions) {
        check(context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean("$user/tasks", value.tasks).putBoolean("$user/habits", value.habits)
            .putBoolean("$user/letters", value.letters).putBoolean("$user/prayers", value.prayers)
            .putBoolean("$user/daily", value.daily).putBoolean("$user/weekly", value.weekly).commit()) { "Unable to save reminder preferences" }
    }
    companion object {
        private const val FILE = "reminder_preferences"
        fun read(context: Context, user: String): ReminderOptions {
            val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            return ReminderOptions(preferences.getBoolean("$user/tasks", true), preferences.getBoolean("$user/habits", false),
                preferences.getBoolean("$user/letters", false), preferences.getBoolean("$user/prayers", false),
                preferences.getBoolean("$user/daily", false), preferences.getBoolean("$user/weekly", false))
        }
    }
}
