package com.ahmad.nafs.core.challenges

import com.ahmad.nafs.domain.challenges.ChallengeCadence
import com.ahmad.nafs.domain.challenges.ChallengePeriod
import com.ahmad.nafs.domain.challenges.ChallengePeriodStatus
import com.ahmad.nafs.domain.challenges.ChallengeTimeline
import com.ahmad.nafs.domain.challenges.unitCount

/** One period of a challenge attempt with what was recorded in it. */
data class ChallengeHistoryEntry(
    val period: ChallengePeriod,
    /** Completed check-ins that carry a proof photo, newest first. */
    val proofs: List<ChallengeCheckIn>,
    /** For a missed period, the latest recorded miss (null when the period simply closed without a check-in). */
    val miss: ChallengeCheckIn?,
) {
    val isMissed: Boolean get() = period.status == ChallengePeriodStatus.MISSED

    /** Sadqa is owed for every missed period of a pledged challenge; it is paid once its miss row says so. */
    val sadqaPaid: Boolean get() = isMissed && miss?.sadqaPaid == true
}

/** Missed periods of a pledged challenge and how many of them have their sadqa recorded as paid. */
data class ChallengeSadqaTally(val missed: Int, val paid: Int) {
    val owed: Int get() = missed - paid
}

/** Each period of the current attempt, oldest first, with the check-ins recorded inside it. */
fun Challenge.history(timeline: ChallengeTimeline): List<ChallengeHistoryEntry> {
    val byPeriod = currentCheckIns().groupBy { ChallengeCadence.periodIndex(frequency, startDate, it.date) }
    return timeline.periods.map { period ->
        val rows = byPeriod[period.index].orEmpty().filter { it.date in period }
        ChallengeHistoryEntry(
            period = period,
            proofs = rows.filter { it.completed && !it.photoUrl.isNullOrBlank() }.sortedByDescending(ChallengeCheckIn::date),
            miss = if (period.status == ChallengePeriodStatus.MISSED) {
                rows.filter { !it.completed }.maxByOrNull(ChallengeCheckIn::date)
            } else {
                null
            },
        )
    }
}

fun List<ChallengeHistoryEntry>.sadqaTally(): ChallengeSadqaTally =
    ChallengeSadqaTally(missed = count(ChallengeHistoryEntry::isMissed), paid = count(ChallengeHistoryEntry::sadqaPaid))

/** "30 of 30 days · longest streak 30": periods kept out of the attempt's periods, and the best run on record. */
fun Challenge.outcomeSummary(timeline: ChallengeTimeline): String {
    val longest = maxOf(longestStreak, timeline.longestStreak)
    return "${timeline.doneCount} of ${frequency.unitCount(timeline.periods.size)} · longest streak $longest"
}
