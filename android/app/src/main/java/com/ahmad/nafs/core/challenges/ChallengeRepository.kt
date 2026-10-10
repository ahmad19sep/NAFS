package com.ahmad.nafs.core.challenges

import java.time.LocalDate

interface ChallengeRepository {
    suspend fun load(userId: String): ChallengeLoadResult
    suspend fun create(intent: SaveChallengeIntent): ChallengeActionResult
    suspend fun update(intent: SaveChallengeIntent): ChallengeActionResult
    suspend fun checkIn(
        userId: String,
        challengeId: String,
        date: LocalDate,
        completed: Boolean,
        evidence: ChallengeEvidence? = null,
        sadqaPaid: Boolean = false,
    ): ChallengeActionResult
    suspend fun setSadqaPaid(
        userId: String,
        challengeId: String,
        date: LocalDate,
        paid: Boolean,
    ): ChallengeActionResult
    suspend fun delete(userId: String, challengeId: String): ChallengeActionResult
    suspend fun restart(userId: String, challenge: Challenge, date: LocalDate): ChallengeActionResult = ChallengeActionResult.Failure("Restart is unavailable.")
    suspend fun clear(userId: String)
}
