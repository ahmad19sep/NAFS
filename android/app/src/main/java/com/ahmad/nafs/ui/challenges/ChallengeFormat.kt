package com.ahmad.nafs.ui.challenges

import com.ahmad.nafs.domain.challenges.ChallengeCadence
import com.ahmad.nafs.domain.challenges.ChallengeFrequency
import com.ahmad.nafs.domain.challenges.ChallengePeriod
import com.ahmad.nafs.domain.challenges.unitName
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** "9 Oct", or "9 Oct 2025" outside the current year. */
internal fun shortDate(date: LocalDate, today: LocalDate): String =
    date.format(if (date.year == today.year) DAY_MONTH else DAY_MONTH_YEAR)

/** "Fri 9 Oct", or "Fri 9 Oct 2025" outside the current year. */
internal fun dayLabel(date: LocalDate, today: LocalDate): String =
    date.format(if (date.year == today.year) WEEKDAY_DAY_MONTH else WEEKDAY_DAY_MONTH_YEAR)

/** How a period is named in the history: "Fri 9 Oct", "Week of 5 Oct", "Oct 2026", "2026". */
internal fun periodLabel(period: ChallengePeriod, frequency: ChallengeFrequency, today: LocalDate): String = when (frequency) {
    ChallengeFrequency.DAILY -> dayLabel(period.start, today)
    ChallengeFrequency.WEEKLY -> "Week of ${shortDate(period.start, today)}"
    ChallengeFrequency.MONTHLY -> period.start.format(MONTH_YEAR)
    ChallengeFrequency.YEARLY -> period.start.year.toString()
}

/** The label inside a history tile: the day or week number, the month or the year. */
internal fun periodTileLabel(period: ChallengePeriod, frequency: ChallengeFrequency): String = when (frequency) {
    ChallengeFrequency.DAILY, ChallengeFrequency.WEEKLY -> period.number.toString()
    ChallengeFrequency.MONTHLY -> period.start.format(MONTH)
    ChallengeFrequency.YEARLY -> period.start.year.toString()
}

/** "today", "yesterday", "this week" — what a check-in dated [date] is for. */
internal fun checkInTarget(date: LocalDate, today: LocalDate, frequency: ChallengeFrequency): String = when {
    frequency != ChallengeFrequency.DAILY -> "this ${frequency.unitName()}"
    date == today -> "today"
    date == today.minusDays(1) -> "yesterday"
    else -> dayLabel(date, today)
}

/** "the end of today", "the end of tomorrow", "the end of Sun 11 Oct": when a period's check-in window closes. */
internal fun windowCloses(periodEnd: LocalDate, frequency: ChallengeFrequency, today: LocalDate): String {
    val deadline = ChallengeCadence.checkInDeadline(frequency, periodEnd)
    return when (deadline) {
        today -> "the end of today"
        today.plusDays(1) -> "the end of tomorrow"
        else -> "the end of ${dayLabel(deadline, today)}"
    }
}

internal fun formatMoney(amount: Double, currency: String): String =
    "$currency ${if (amount % 1.0 == 0.0) amount.toLong() else amount}"

private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
private val DAY_MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
private val WEEKDAY_DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")
private val WEEKDAY_DAY_MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy")
private val MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yyyy")
private val MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM")
