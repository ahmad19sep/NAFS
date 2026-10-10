package com.ahmad.nafs.core.health

import java.time.LocalDate

interface HealthRepository {
    suspend fun load(userId: String, date: LocalDate): HealthLoadResult
    suspend fun saveProfile(intent: SaveHealthProfileIntent): HealthActionResult
    suspend fun saveCustomMetrics(intent: SaveCustomMetricsIntent): HealthActionResult
    suspend fun saveLog(intent: SaveHealthLogIntent): HealthActionResult
    suspend fun clear(userId: String)
}
