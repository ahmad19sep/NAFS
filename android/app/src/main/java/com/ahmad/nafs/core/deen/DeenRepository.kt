package com.ahmad.nafs.core.deen

import java.time.LocalDate

interface DeenRepository {
    suspend fun load(userId: String, date: LocalDate): DeenLoadResult
    suspend fun save(log: PrayerLog, expected: PrayerLog?): DeenSaveResult
    suspend fun clear(userId: String)
}
