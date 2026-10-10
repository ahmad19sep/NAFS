package com.ahmad.nafs.core.challenges

import com.ahmad.nafs.domain.challenges.ChallengeCadence
import com.ahmad.nafs.domain.challenges.ChallengeFrequency
import com.ahmad.nafs.domain.challenges.ChallengePeriodStatus
import com.ahmad.nafs.domain.challenges.ChallengeStatus
import com.ahmad.nafs.domain.challenges.ChallengeTimeline
import com.ahmad.nafs.domain.challenges.DatedChallengeCheckIn
import java.time.LocalDate

data class ChallengeCheckIn(
    val id: String,
    val challengeId: String,
    val date: LocalDate,
    val completed: Boolean,
    val photoUrl: String?,
    val sadqaPaid: Boolean,
    val createdAt: String,
) {
    fun dated() = DatedChallengeCheckIn(date, completed)
}

data class Challenge(
    val id: String,
    val userId: String,
    val title: String,
    val emoji: String,
    val description: String,
    val frequency: ChallengeFrequency,
    val durationDays: Int,
    val startDate: LocalDate,
    val requiresPhoto: Boolean,
    val sadqaAmount: Double?,
    val sadqaCurrency: String,
    val currentStreak: Int,
    val longestStreak: Int,
    val status: ChallengeStatus,
    val restartCount: Int,
    val lastRestartAt: String?,
    val checkIns: List<ChallengeCheckIn>,
    val createdAt: String,
) {
    fun checkedThisPeriod(today: LocalDate): Boolean = timeline(today).current?.status == ChallengePeriodStatus.DONE

    /** Check-ins of the current attempt; a restart keeps earlier ones on record but they no longer count. */
    fun currentCheckIns(): List<ChallengeCheckIn> = checkIns.filter { !it.date.isBefore(startDate) }

    /** The current attempt's periods judged on [today] (account date). */
    fun timeline(today: LocalDate): ChallengeTimeline = ChallengeTimeline.of(
        frequency,
        startDate,
        durationDays,
        currentCheckIns().map(ChallengeCheckIn::dated),
        today,
    )

    fun endDate(): LocalDate = ChallengeCadence.endDate(startDate, durationDays)

    fun needsRestart(today: LocalDate): Boolean = status == ChallengeStatus.FAILED ||
        (status == ChallengeStatus.ACTIVE && timeline(today).needsRestart)

    /** Periods in the current attempt, counting a partial first or last calendar period as one. */
    fun totalUnits(): Int = ChallengeCadence.periodCount(frequency, startDate, durationDays)

    fun elapsedUnits(today: LocalDate): Int = ChallengeCadence.unitsElapsed(frequency, startDate, today)

    fun progressPercent(today: LocalDate): Int =
        ChallengeCadence.progressPercent(frequency, durationDays, startDate, today)

    /**
     * The challenge as its check-ins say it stands on [today]: an active attempt's status resolved (complete once its
     * final period is done, ended once that period's window closes without a completion), the current streak recomputed
     * and the longest streak raised to the longest run on record. The stored longest streak is never lowered — it can
     * come from an earlier attempt — and a status other than active is never changed, so a challenge another client
     * completed stays complete. Returns this same instance when nothing changes.
     */
    fun reconciled(today: LocalDate): Challenge {
        val timeline = timeline(today)
        val resolvedStatus = if (status == ChallengeStatus.ACTIVE) timeline.outcome else status
        val streak = timeline.currentStreak
        val longest = maxOf(longestStreak, timeline.longestStreak, streak)
        return if (resolvedStatus == status && streak == currentStreak && longest == longestStreak) {
            this
        } else {
            copy(status = resolvedStatus, currentStreak = streak, longestStreak = longest)
        }
    }
}

data class SaveChallengeIntent(
    val requestId: String,
    val userId: String,
    val challengeId: String? = null,
    val title: String,
    val emoji: String,
    val description: String,
    val frequency: ChallengeFrequency,
    val durationDays: Int,
    val startDate: LocalDate,
    val requiresPhoto: Boolean = false,
    val sadqaAmount: Double? = null,
    val sadqaCurrency: String = "PKR",
)

data class ChallengeEvidence(
    val bytes: ByteArray,
    val mimeType: String,
    val extension: String,
)

object ChallengeDefinitionValidator {
    fun validationError(intent: SaveChallengeIntent): String? = when {
        intent.title.trim().isEmpty() -> "Enter a challenge title."
        intent.title.trim().length > 120 -> "Keep the challenge title under 120 characters."
        intent.emoji.trim().isEmpty() -> "Choose an emoji for the challenge."
        intent.description.trim().isEmpty() -> "Add your reason for taking this challenge."
        intent.description.trim().length > 1_000 -> "Keep your reason under 1,000 characters."
        intent.durationDays < minimumDuration(intent.frequency) ->
            "Duration must be at least ${minimumDuration(intent.frequency)} days for this cadence."
        intent.durationDays > maximumDuration(intent.frequency) ->
            "Duration is too long for this cadence."
        intent.sadqaAmount != null && (!intent.sadqaAmount.isFinite() || intent.sadqaAmount <= 0.0) ->
            "Sadqa must be a positive amount."
        intent.sadqaAmount != null && intent.sadqaAmount > 10_000_000.0 ->
            "Sadqa amount is too large."
        intent.sadqaCurrency !in SUPPORTED_CURRENCIES -> "Choose a supported sadqa currency."
        else -> null
    }

    val SUPPORTED_CURRENCIES = listOf("PKR", "USD", "GBP", "EUR", "AED", "SAR")

    fun minimumDuration(frequency: ChallengeFrequency): Int = when (frequency) {
        ChallengeFrequency.DAILY -> 7
        ChallengeFrequency.WEEKLY -> 7
        ChallengeFrequency.MONTHLY -> 30
        ChallengeFrequency.YEARLY -> 365
    }

    fun maximumDuration(frequency: ChallengeFrequency): Int = when (frequency) {
        ChallengeFrequency.DAILY -> 365
        ChallengeFrequency.WEEKLY -> 365
        ChallengeFrequency.MONTHLY -> 365
        ChallengeFrequency.YEARLY -> 1_825
    }

    fun defaultDuration(frequency: ChallengeFrequency): Int = when (frequency) {
        ChallengeFrequency.DAILY -> 30
        ChallengeFrequency.WEEKLY -> 84
        ChallengeFrequency.MONTHLY -> 180
        ChallengeFrequency.YEARLY -> 365
    }
}

sealed interface ChallengeLoadResult {
    data class Success(val challenges: List<Challenge>, val isOffline: Boolean) : ChallengeLoadResult
    data class Failure(val message: String) : ChallengeLoadResult
}

sealed interface ChallengeActionResult {
    data class Success(val challenge: Challenge? = null) : ChallengeActionResult
    data class Failure(val message: String) : ChallengeActionResult
}

