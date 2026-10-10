package com.ahmad.nafs.core.habits

import java.time.LocalDate

interface HabitRepository {
    suspend fun load(userId: String, today: LocalDate): HabitLoadResult
    suspend fun create(intent: SaveHabitIntent): HabitDefinitionActionResult
    suspend fun update(intent: SaveHabitIntent): HabitDefinitionActionResult
    suspend fun setPaused(userId: String, habitId: String, paused: Boolean): HabitDefinitionActionResult

    /** Takes the habit off every list (`is_active = false`, as on the web); its logs stay for reports. */
    suspend fun archive(userId: String, habitId: String): HabitDefinitionActionResult

    /** Brings an archived habit back, with its history. */
    suspend fun restore(userId: String, habitId: String): HabitDefinitionActionResult

    suspend fun log(intent: LogHabitIntent): HabitActionResult

    /**
     * Recomputes `current_streak` and `longest_streak` from the server's logs, as the web log route does, and stores
     * them. Best effort: returns null when that fails, and never touches the log itself.
     */
    suspend fun refreshStreak(userId: String, habitId: String, today: LocalDate): HabitStreakUpdate?

    suspend fun clear(userId: String)
}
