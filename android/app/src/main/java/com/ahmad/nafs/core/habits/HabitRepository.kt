package com.ahmad.nafs.core.habits

import java.time.LocalDate

interface HabitRepository {
    suspend fun load(userId: String, today: LocalDate): HabitLoadResult
    suspend fun create(intent: SaveHabitIntent): HabitDefinitionActionResult
    suspend fun update(intent: SaveHabitIntent): HabitDefinitionActionResult
    suspend fun setPaused(userId: String, habitId: String, paused: Boolean): HabitDefinitionActionResult
    suspend fun log(intent: LogHabitIntent): HabitActionResult
    suspend fun clear(userId: String)
}
