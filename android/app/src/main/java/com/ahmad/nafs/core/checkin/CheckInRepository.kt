package com.ahmad.nafs.core.checkin

import java.time.LocalDate

interface CheckInRepository {
    suspend fun load(userId: String, date: LocalDate): CheckInLoadResult
    suspend fun save(record: DailyCheckIn, expected: DailyCheckIn?): CheckInSaveResult
    suspend fun cacheGeneratedReview(record: DailyCheckIn)
    suspend fun clear(userId: String)
}
