package com.ahmad.nafs.core.health

import java.time.LocalDate

interface HealthRepository {
    suspend fun load(userId: String, date: LocalDate): HealthLoadResult
    /** Saved daily logs from [from] to [to] inclusive (at most 31 days), for trends and the day switcher. */
    suspend fun loadRange(userId: String, from: LocalDate, to: LocalDate): HealthRangeResult
    suspend fun saveProfile(intent: SaveHealthProfileIntent): HealthActionResult
    suspend fun saveCustomMetrics(intent: SaveCustomMetricsIntent): HealthActionResult
    /** Saves one day. When it carries the latest weigh-in, users.weight_kg follows it, as on the web. */
    suspend fun saveLog(intent: SaveHealthLogIntent): HealthActionResult
    suspend fun clear(userId: String)
}
