package com.ahmad.nafs.core.deen

import java.time.LocalDate

interface DeenRepository {
    suspend fun load(userId: String, date: LocalDate): DeenLoadResult
    suspend fun save(log: PrayerLog, expected: PrayerLog?): DeenSaveResult
    suspend fun clear(userId: String)

    /**
     * The five prayer statuses of every saved day from [from] to [to], for streaks and history. Offline, the last saved
     * copy is returned, marked offline. A repository without history (such as a test double) reports it as unavailable.
     */
    suspend fun history(userId: String, from: LocalDate, to: LocalDate): DeenHistoryResult =
        DeenHistoryResult.Failure("Prayer history isn't available right now.")
}
