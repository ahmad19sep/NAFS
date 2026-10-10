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

/**
 * Calendar primitives for challenge cadences. Every rule — the card, the history, streaks, "needs a restart" and the
 * status written back — is built from these, through [ChallengeTimeline], so they cannot disagree.
 *
 * - A challenge attempt runs from its start date through `start + durationDays - 1`, the same range the web history
 *   counts a challenge as running.
 * - Its periods are calendar periods of the cadence: a day, an ISO week (Monday–Sunday), a calendar month or a calendar
 *   year, clipped to the attempt — so the first and last periods can be partial. These are the same boundaries the web
 *   uses to decide whether "this week/month" is checked in.
 * - A period can be checked in until its last day; a daily challenge also accepts the previous day until the end of
 *   today ([GRACE_DAYS]), like the tasks' grace list. Weekly, monthly and yearly challenges accept the current period only.
 *
 * All dates are account dates (the profile timezone's calendar day), never device dates.
 */
object ChallengeCadence {
    /** Extra days a daily period stays open after it ends: yesterday can be checked in until the end of today. */
    const val GRACE_DAYS = 1L

    /** Rhythm check with the attempt measured up to [today]; see [ChallengeTimeline.needsRestart]. */
    fun needsRestart(frequency: ChallengeFrequency, startDate: LocalDate, logs: List<DatedChallengeCheckIn>, today: LocalDate): Boolean {
        if (today.isBefore(startDate)) return false
        val daysSoFar = ChronoUnit.DAYS.between(startDate, today).toInt() + 1
        return ChallengeTimeline.of(frequency, startDate, daysSoFar, logs, today).needsRestart
    }

    /**
     * Nominal number of cadence units in a duration (30 days ≈ 1 month). A real attempt's period count depends on the
     * weekday or date it starts on; use [periodCount] for that.
     */
    fun totalUnits(frequency: ChallengeFrequency, durationDays: Int): Int =
        ceil(durationDays.coerceAtLeast(1).toDouble() / frequency.unitDays).toInt().coerceAtLeast(1)

    /** Number of the period holding [today], counting the attempt's first period as 1 (1 before the start). */
    fun unitsElapsed(
        frequency: ChallengeFrequency,
        startDate: LocalDate,
        today: LocalDate,
    ): Int = periodIndex(frequency, startDate, today).coerceAtLeast(0) + 1

    /** Last day of an attempt that starts on [startDate] and lasts [durationDays] days (at least one). */
    fun endDate(startDate: LocalDate, durationDays: Int): LocalDate =
        startDate.plusDays(durationDays.coerceAtLeast(1).toLong() - 1)

    fun periodStart(frequency: ChallengeFrequency, date: LocalDate): LocalDate = when (frequency) {
        ChallengeFrequency.DAILY -> date
        ChallengeFrequency.WEEKLY -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        ChallengeFrequency.MONTHLY -> date.withDayOfMonth(1)
        ChallengeFrequency.YEARLY -> date.withDayOfYear(1)
    }

    fun periodEnd(frequency: ChallengeFrequency, date: LocalDate): LocalDate = when (frequency) {
        ChallengeFrequency.DAILY -> date
        ChallengeFrequency.WEEKLY -> date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        ChallengeFrequency.MONTHLY -> date.with(TemporalAdjusters.lastDayOfMonth())
        ChallengeFrequency.YEARLY -> date.with(TemporalAdjusters.lastDayOfYear())
    }

    /** Calendar periods from the one holding [startDate] (index 0) to the one holding [date]; negative before it. */
    fun periodIndex(frequency: ChallengeFrequency, startDate: LocalDate, date: LocalDate): Int = when (frequency) {
        ChallengeFrequency.DAILY -> ChronoUnit.DAYS.between(startDate, date).toInt()
        // Both ends are Mondays, so the week count is exact in either direction.
        ChallengeFrequency.WEEKLY -> ChronoUnit.WEEKS.between(
            periodStart(ChallengeFrequency.WEEKLY, startDate),
            periodStart(ChallengeFrequency.WEEKLY, date),
        ).toInt()
        ChallengeFrequency.MONTHLY -> (date.year - startDate.year) * 12 + (date.monthValue - startDate.monthValue)
        ChallengeFrequency.YEARLY -> date.year - startDate.year
    }

    /** Number of periods in an attempt, counting a partial first or last period as one. */
    fun periodCount(frequency: ChallengeFrequency, startDate: LocalDate, durationDays: Int): Int =
        periodIndex(frequency, startDate, endDate(startDate, durationDays)) + 1

    /** The last account day on which a check-in for a period ending on [periodEnd] can still be saved. */
    fun checkInDeadline(frequency: ChallengeFrequency, periodEnd: LocalDate): LocalDate =
        if (frequency == ChallengeFrequency.DAILY) periodEnd.plusDays(GRACE_DAYS) else periodEnd

    fun hasCheckedThisPeriod(
        frequency: ChallengeFrequency,
        checkIns: List<DatedChallengeCheckIn>,
        today: LocalDate,
    ): Boolean {
        val start = periodStart(frequency, today)
        return checkIns.any { it.completed && it.date in start..today }
    }

    /**
     * Consecutive done periods ending with the period holding [today], which must itself be done (the web's
     * check-in-time streak). For the streak to show, use [ChallengeTimeline.currentStreak], which keeps an open period
     * from breaking the run.
     */
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

    /** Share of the attempt's periods that have begun by [today] (0 before the start, 100 from the last period on). */
    fun progressPercent(
        frequency: ChallengeFrequency,
        durationDays: Int,
        startDate: LocalDate,
        today: LocalDate,
    ): Int {
        if (today.isBefore(startDate)) return 0
        val total = periodCount(frequency, startDate, durationDays)
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

/** "1 day", "30 days", "1 week" … */
fun ChallengeFrequency.unitCount(count: Int): String = "$count ${unitName()}${if (count == 1) "" else "s"}"
