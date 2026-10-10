package com.ahmad.nafs.domain.challenges

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.ceil

enum class ChallengeFrequency(val unitDays: Int) {
    DAILY(1),
    WEEKLY(7),
    MONTHLY(30),
    YEARLY(365),
}

enum class ChallengeStatus {
    ACTIVE,
    COMPLETED,
    FAILED,
}

data class DatedChallengeCheckIn(
    val date: LocalDate,
    val completed: Boolean,
)

object ChallengeCadence {
    fun needsRestart(frequency: ChallengeFrequency, startDate: LocalDate, logs: List<DatedChallengeCheckIn>, today: LocalDate): Boolean {
        if (today.isBefore(startDate)) return false
        if (logs.any { it.date == today && !it.completed }) return true
        if (ChronoUnit.DAYS.between(startDate, today) < frequency.unitDays) return false
        val last = logs.filter { it.completed && it.date in startDate..today }.maxOfOrNull { it.date }
        return if (last == null) true else ChronoUnit.DAYS.between(last, today) > frequency.unitDays
    }
    fun totalUnits(frequency: ChallengeFrequency, durationDays: Int): Int =
        ceil(durationDays.coerceAtLeast(1).toDouble() / frequency.unitDays).toInt().coerceAtLeast(1)

    fun unitsElapsed(
        frequency: ChallengeFrequency,
        startDate: LocalDate,
        today: LocalDate,
    ): Int {
        val elapsedDays = ChronoUnit.DAYS.between(startDate, today).coerceAtLeast(0) + 1
        return ceil(elapsedDays.toDouble() / frequency.unitDays).toInt().coerceAtLeast(1)
    }

    fun periodStart(frequency: ChallengeFrequency, date: LocalDate): LocalDate = when (frequency) {
        ChallengeFrequency.DAILY -> date
        ChallengeFrequency.WEEKLY -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        ChallengeFrequency.MONTHLY -> date.withDayOfMonth(1)
        ChallengeFrequency.YEARLY -> date.withDayOfYear(1)
    }

    fun hasCheckedThisPeriod(
        frequency: ChallengeFrequency,
        checkIns: List<DatedChallengeCheckIn>,
        today: LocalDate,
    ): Boolean {
        val start = periodStart(frequency, today)
        return checkIns.any { it.completed && it.date in start..today }
    }

    fun periodStreak(
        frequency: ChallengeFrequency,
        startDate: LocalDate,
        checkIns: List<DatedChallengeCheckIn>,
        today: LocalDate,
    ): Int {
        val completedDates = checkIns.asSequence()
            .filter(DatedChallengeCheckIn::completed)
            .map(DatedChallengeCheckIn::date)
            .toSet()
        var streak = 0
        var probe = today
        while (!probe.isBefore(startDate)) {
            val periodStart = periodStart(frequency, probe)
            val effectiveStart = maxOf(periodStart, startDate)
            if (completedDates.none { it in effectiveStart..probe }) break
            streak += 1
            probe = periodStart.minusDays(1)
        }
        return streak
    }

    fun progressPercent(
        frequency: ChallengeFrequency,
        durationDays: Int,
        startDate: LocalDate,
        today: LocalDate,
    ): Int {
        val total = totalUnits(frequency, durationDays)
        return (unitsElapsed(frequency, startDate, today).coerceAtMost(total) * 100 / total)
            .coerceIn(0, 100)
    }
}

fun ChallengeFrequency.displayName(): String = name.lowercase().replaceFirstChar(Char::uppercase)

fun ChallengeFrequency.unitName(): String = when (this) {
    ChallengeFrequency.DAILY -> "day"
    ChallengeFrequency.WEEKLY -> "week"
    ChallengeFrequency.MONTHLY -> "month"
    ChallengeFrequency.YEARLY -> "year"
}

