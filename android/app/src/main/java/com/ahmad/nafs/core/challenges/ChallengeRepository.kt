package com.ahmad.nafs.core.challenges

import java.time.LocalDate

/**
 * Challenges and their check-ins. Every `today` is the account's calendar day (profile timezone): it decides which
 * periods can still be checked in and how a challenge's status and streaks stand.
 */
interface ChallengeRepository {
    /**
     * Loads the person's challenges, each reconciled with its check-ins as of [today] (see [Challenge.reconciled]).
     * Corrected statuses and streaks are written back best effort; offline, the cached copy is reconciled in memory.
     */
    suspend fun load(userId: String, today: LocalDate): ChallengeLoadResult
    suspend fun create(intent: SaveChallengeIntent): ChallengeActionResult
    suspend fun update(intent: SaveChallengeIntent): ChallengeActionResult

    /**
     * Records a check-in dated [date], which must fall in a period still open on [today]: the current period, or for a
     * daily challenge also yesterday. A completion requires [evidence] when the challenge asks for photo proof.
     */
    suspend fun checkIn(
        userId: String,
        challengeId: String,
        date: LocalDate,
        today: LocalDate,
        completed: Boolean,
        evidence: ChallengeEvidence? = null,
        sadqaPaid: Boolean = false,
    ): ChallengeActionResult

    /**
     * Marks the sadqa for the missed period holding [date] paid or unpaid. [date] is a recorded miss, or any day of a
     * period that closed without a check-in (a miss is then recorded for it).
     */
    suspend fun setSadqaPaid(
        userId: String,
        challengeId: String,
        date: LocalDate,
        paid: Boolean,
        today: LocalDate,
    ): ChallengeActionResult
    suspend fun delete(userId: String, challengeId: String): ChallengeActionResult
    suspend fun restart(userId: String, challenge: Challenge, date: LocalDate): ChallengeActionResult = ChallengeActionResult.Failure("Restart is unavailable.")
    suspend fun clear(userId: String)
}
