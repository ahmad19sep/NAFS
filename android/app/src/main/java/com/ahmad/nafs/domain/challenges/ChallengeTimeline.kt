package com.ahmad.nafs.domain.challenges

import java.time.LocalDate

enum class ChallengePeriodStatus {
    /** At least one completed check-in is dated inside the period. */
    DONE,

    /** The period's window closed without a completion, or a miss was recorded and nothing completed it since. */
    MISSED,

    /** The period has started, its window is still open and nothing has been recorded yet. */
    PENDING,

    /** The period has not started. */
    FUTURE,
}

/** One cadence period of a challenge attempt, as it stands on the account's today. */
data class ChallengePeriod(
    /** 0 for the attempt's first period. */
    val index: Int,
    /** First day, clipped to the attempt's start. */
    val start: LocalDate,
    /** Last day, clipped to the attempt's end. */
    val end: LocalDate,
    val status: ChallengePeriodStatus,
    /** A check-in for this period can still be saved today. */
    val isOpen: Boolean,
    /** An explicit "missed" check-in is recorded inside this period. */
    val missRecorded: Boolean,
) {
    /** 1-based, as shown to people ("Day 3", "Week 2"). */
    val number: Int get() = index + 1

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
}

/**
 * A challenge attempt's periods judged on [today] (account date), and everything derived from them. Rules:
 *
 * - **Streak** ([currentStreak]): consecutive done periods. A period that is still open never breaks a run — at 9 am the
 *   streak still counts through yesterday, and for a daily challenge an open yesterday keeps the run alive until the end
 *   of today. A missed period does break it. When an open gap separates two runs, the longer one is shown, so checking
 *   in today can never make the number drop before yesterday's window closes.
 * - **Rhythm** ([needsRestart]): broken when the latest period that is decided (done or missed) was missed. Open periods
 *   are skipped. A partial first period (a weekly challenge started on Thursday, a monthly one on the 28th) that closed
 *   without a check-in is not held against the rhythm — the person may have had a day or two, and a restart could not
 *   win it back — though it still counts as missed in the history. A miss the person recorded always counts.
 * - **Outcome** ([outcome]): an active attempt completes once its final period is done and no other period is still
 *   open; it ends unfinished (failed) once the final period's window has closed without a completion. A recorded miss
 *   in the final period therefore never completes it — the person can still check in until the window closes.
 */
class ChallengeTimeline private constructor(
    val frequency: ChallengeFrequency,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val today: LocalDate,
    val periods: List<ChallengePeriod>,
) {
    val last: ChallengePeriod get() = periods.last()

    val hasStarted: Boolean get() = !today.isBefore(startDate)

    /** The period holding [today]; null before the start and after the end. */
    val current: ChallengePeriod? = periods.firstOrNull { today in it }

    /** For a daily challenge: yesterday, while it can still be checked in and is not done. */
    val grace: ChallengePeriod? = periods.firstOrNull { it.isOpen && it.end.isBefore(today) && it.status != ChallengePeriodStatus.DONE }

    val doneCount: Int = periods.count { it.status == ChallengePeriodStatus.DONE }
    val missedCount: Int = periods.count { it.status == ChallengePeriodStatus.MISSED }
    val openCount: Int = periods.count { it.status == ChallengePeriodStatus.PENDING }
    val futureCount: Int = periods.count { it.status == ChallengePeriodStatus.FUTURE }

    val currentStreak: Int = aliveStreak(periods)

    val longestStreak: Int = longestRun(periods)

    val needsRestart: Boolean = run {
        val firstIsPartial = ChallengeCadence.periodStart(frequency, startDate) != startDate
        val decided = periods.asReversed().firstOrNull { period ->
            when (period.status) {
                ChallengePeriodStatus.DONE -> true
                ChallengePeriodStatus.MISSED -> period.missRecorded || !(period.index == 0 && firstIsPartial)
                ChallengePeriodStatus.PENDING, ChallengePeriodStatus.FUTURE -> false
            }
        }
        decided?.status == ChallengePeriodStatus.MISSED
    }

    /** The status an active attempt has on [today]. */
    val outcome: ChallengeStatus = when {
        last.status == ChallengePeriodStatus.DONE &&
            periods.none { it.isOpen && it.status != ChallengePeriodStatus.DONE } -> ChallengeStatus.COMPLETED
        today.isAfter(ChallengeCadence.checkInDeadline(frequency, last.end)) -> ChallengeStatus.FAILED
        else -> ChallengeStatus.ACTIVE
    }

    fun periodOn(date: LocalDate): ChallengePeriod? = periods.firstOrNull { date in it }

    /** Whether a check-in dated [date] may be saved today: inside the attempt, not in the future, window still open. */
    fun canCheckIn(date: LocalDate): Boolean = !date.isAfter(today) && periodOn(date)?.isOpen == true

    companion object {
        fun of(
            frequency: ChallengeFrequency,
            startDate: LocalDate,
            durationDays: Int,
            checkIns: List<DatedChallengeCheckIn>,
            today: LocalDate,
        ): ChallengeTimeline {
            val endDate = ChallengeCadence.endDate(startDate, durationDays)
            val inAttempt = checkIns.filter { !it.date.isBefore(startDate) && !it.date.isAfter(endDate) }
            val doneIndices = inAttempt.filter { it.completed }
                .mapTo(HashSet<Int>()) { ChallengeCadence.periodIndex(frequency, startDate, it.date) }
            val missIndices = inAttempt.filter { !it.completed }
                .mapTo(HashSet<Int>()) { ChallengeCadence.periodIndex(frequency, startDate, it.date) }

            val periods = ArrayList<ChallengePeriod>()
            var start = startDate
            var index = 0
            while (!start.isAfter(endDate)) {
                val naturalEnd = ChallengeCadence.periodEnd(frequency, start)
                val end = if (naturalEnd.isAfter(endDate)) endDate else naturalEnd
                val started = !today.isBefore(start)
                val open = started && !today.isAfter(ChallengeCadence.checkInDeadline(frequency, end))
                val status = when {
                    !started -> ChallengePeriodStatus.FUTURE
                    index in doneIndices -> ChallengePeriodStatus.DONE
                    index in missIndices -> ChallengePeriodStatus.MISSED
                    open -> ChallengePeriodStatus.PENDING
                    else -> ChallengePeriodStatus.MISSED
                }
                periods += ChallengePeriod(index, start, end, status, isOpen = open, missRecorded = index in missIndices)
                start = end.plusDays(1)
                index += 1
            }
            return ChallengeTimeline(frequency, startDate, endDate, today, periods)
        }

        /** Longest run that is still alive: walking back from today, open periods split runs and a miss ends the walk. */
        private fun aliveStreak(periods: List<ChallengePeriod>): Int {
            var best = 0
            var run = 0
            for (period in periods.asReversed()) {
                when (period.status) {
                    ChallengePeriodStatus.DONE -> run += 1
                    ChallengePeriodStatus.PENDING -> {
                        best = maxOf(best, run)
                        run = 0
                    }
                    ChallengePeriodStatus.MISSED -> return maxOf(best, run)
                    ChallengePeriodStatus.FUTURE -> Unit
                }
            }
            return maxOf(best, run)
        }

        private fun longestRun(periods: List<ChallengePeriod>): Int {
            var longest = 0
            var run = 0
            periods.forEach { period ->
                if (period.status == ChallengePeriodStatus.DONE) {
                    run += 1
                    if (run > longest) longest = run
                } else {
                    run = 0
                }
            }
            return longest
        }
    }
}
