package com.ahmad.nafs.domain.challenges

import com.ahmad.nafs.domain.challenges.ChallengePeriodStatus.DONE
import com.ahmad.nafs.domain.challenges.ChallengePeriodStatus.FUTURE
import com.ahmad.nafs.domain.challenges.ChallengePeriodStatus.MISSED
import com.ahmad.nafs.domain.challenges.ChallengePeriodStatus.PENDING
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeTimelineTest {
    private fun date(value: String) = LocalDate.parse(value)
    private fun done(value: String) = DatedChallengeCheckIn(date(value), true)
    private fun miss(value: String) = DatedChallengeCheckIn(date(value), false)
    private fun daily(start: String, days: Int, today: String, vararg checkIns: DatedChallengeCheckIn) =
        ChallengeTimeline.of(ChallengeFrequency.DAILY, date(start), days, checkIns.toList(), date(today))
    private fun timeline(frequency: ChallengeFrequency, start: String, days: Int, today: String, vararg checkIns: DatedChallengeCheckIn) =
        ChallengeTimeline.of(frequency, date(start), days, checkIns.toList(), date(today))

    // ─── Periods ─────────────────────────────────────────────────────────────

    @Test fun `weekly periods are calendar weeks clipped to the attempt`() {
        // Thursday 1 Oct + 84 days ends Wednesday 23 Dec: a partial first and last week, 13 periods.
        val t = timeline(ChallengeFrequency.WEEKLY, "2026-10-01", 84, "2026-10-07")
        assertEquals(13, t.periods.size)
        assertEquals(13, ChallengeCadence.periodCount(ChallengeFrequency.WEEKLY, date("2026-10-01"), 84))
        assertEquals(date("2026-10-01") to date("2026-10-04"), t.periods.first().start to t.periods.first().end)
        assertEquals(date("2026-10-05") to date("2026-10-11"), t.periods[1].start to t.periods[1].end)
        assertEquals(date("2026-12-21") to date("2026-12-23"), t.last.start to t.last.end)
        assertEquals(date("2026-12-23"), t.endDate)
        assertEquals(1, t.current?.index)
    }

    @Test fun `monthly and yearly periods follow the calendar`() {
        val monthly = timeline(ChallengeFrequency.MONTHLY, "2026-10-10", 180, "2026-10-10")
        assertEquals(listOf("2026-10-10", "2026-11-01", "2026-12-01", "2027-01-01", "2027-02-01", "2027-03-01", "2027-04-01"),
            monthly.periods.map { it.start.toString() })
        assertEquals(date("2027-04-07"), monthly.last.end)
        val yearly = timeline(ChallengeFrequency.YEARLY, "2026-10-10", 365, "2026-10-10")
        assertEquals(listOf(date("2026-10-10") to date("2026-12-31"), date("2027-01-01") to date("2027-10-09")),
            yearly.periods.map { it.start to it.end })
    }

    @Test fun `period index counts calendar boundaries in both directions`() {
        val thursday = date("2026-10-01")
        assertEquals(0, ChallengeCadence.periodIndex(ChallengeFrequency.WEEKLY, thursday, date("2026-10-04")))
        assertEquals(1, ChallengeCadence.periodIndex(ChallengeFrequency.WEEKLY, thursday, date("2026-10-05")))
        assertEquals(-1, ChallengeCadence.periodIndex(ChallengeFrequency.WEEKLY, thursday, date("2026-09-27")))
        assertEquals(3, ChallengeCadence.periodIndex(ChallengeFrequency.MONTHLY, date("2026-10-31"), date("2027-01-01")))
        assertEquals(1, ChallengeCadence.periodIndex(ChallengeFrequency.YEARLY, date("2026-12-31"), date("2027-01-01")))
    }

    @Test fun `progress counts calendar periods begun`() {
        assertEquals(0, ChallengeCadence.progressPercent(ChallengeFrequency.DAILY, 10, date("2026-10-10"), date("2026-10-09")))
        assertEquals(10, ChallengeCadence.progressPercent(ChallengeFrequency.DAILY, 10, date("2026-10-10"), date("2026-10-10")))
        assertEquals(100, ChallengeCadence.progressPercent(ChallengeFrequency.DAILY, 10, date("2026-10-10"), date("2026-12-01")))
        // Monday 5 Oct is the second calendar week of a challenge started on Thursday 1 Oct.
        assertEquals(2, ChallengeCadence.unitsElapsed(ChallengeFrequency.WEEKLY, date("2026-10-01"), date("2026-10-05")))
    }

    // ─── Statuses and check-in windows ───────────────────────────────────────

    @Test fun `daily yesterday stays open until the end of today`() {
        val t = daily("2026-10-01", 30, "2026-10-10", done("2026-10-08"))
        assertEquals(DONE, t.periodOn(date("2026-10-08"))?.status)
        assertEquals(PENDING, t.periodOn(date("2026-10-09"))?.status)
        assertEquals(PENDING, t.periodOn(date("2026-10-10"))?.status)
        assertEquals(MISSED, t.periodOn(date("2026-10-07"))?.status)
        assertEquals(FUTURE, t.periodOn(date("2026-10-11"))?.status)
        assertEquals(date("2026-10-09"), t.grace?.start)
        assertTrue(t.canCheckIn(date("2026-10-10")))
        assertTrue(t.canCheckIn(date("2026-10-09")))
        assertFalse(t.canCheckIn(date("2026-10-08")))
        assertFalse(t.canCheckIn(date("2026-10-11")))
    }

    @Test fun `weekly and monthly accept the current period only`() {
        val weekly = timeline(ChallengeFrequency.WEEKLY, "2026-09-28", 84, "2026-10-05")
        assertTrue(weekly.canCheckIn(date("2026-10-05")))
        assertFalse(weekly.canCheckIn(date("2026-10-04")))
        assertNull(weekly.grace)
        assertEquals(MISSED, weekly.periods.first().status)
        val monthly = timeline(ChallengeFrequency.MONTHLY, "2026-09-01", 90, "2026-10-01")
        assertFalse(monthly.canCheckIn(date("2026-09-30")))
        assertTrue(monthly.canCheckIn(date("2026-10-01")))
    }

    @Test fun `a recorded miss is missed but can still be replaced while open`() {
        val t = daily("2026-10-01", 30, "2026-10-10", miss("2026-10-10"))
        val today = t.periodOn(date("2026-10-10"))!!
        assertEquals(MISSED, today.status)
        assertTrue(today.missRecorded)
        assertTrue(today.isOpen)
        // A completion anywhere in the period wins over a recorded miss (weekly: missed Tuesday, done Thursday).
        val weekly = timeline(ChallengeFrequency.WEEKLY, "2026-10-05", 28, "2026-10-09", miss("2026-10-06"), done("2026-10-08"))
        assertEquals(DONE, weekly.current?.status)
    }

    @Test fun `check-ins outside the attempt are ignored`() {
        val t = daily("2026-10-05", 7, "2026-10-12", done("2026-10-04"), done("2026-10-12"))
        assertEquals(0, t.doneCount)
        assertEquals(7, t.periods.size)
    }

    // ─── Streaks ─────────────────────────────────────────────────────────────

    @Test fun `an open today does not break the streak but a closed miss does`() {
        assertEquals(3, daily("2026-10-01", 30, "2026-10-10", done("2026-10-07"), done("2026-10-08"), done("2026-10-09")).currentStreak)
        // Yesterday open (grace) keeps the run through the day before alive until the end of today.
        assertEquals(2, daily("2026-10-01", 30, "2026-10-10", done("2026-10-07"), done("2026-10-08")).currentStreak)
        // Once yesterday's window has closed, the run is gone.
        assertEquals(0, daily("2026-10-01", 30, "2026-10-11", done("2026-10-07"), done("2026-10-08")).currentStreak)
        // An honest miss today resets it straight away.
        assertEquals(0, daily("2026-10-01", 30, "2026-10-10", done("2026-10-09"), miss("2026-10-10")).currentStreak)
    }

    @Test fun `checking in today never lowers the streak while yesterday is open`() {
        val before = daily("2026-10-01", 30, "2026-10-10", done("2026-10-06"), done("2026-10-07"), done("2026-10-08"))
        val after = daily("2026-10-01", 30, "2026-10-10", done("2026-10-06"), done("2026-10-07"), done("2026-10-08"), done("2026-10-10"))
        assertEquals(3, before.currentStreak)
        assertEquals(3, after.currentStreak)
        val filled = daily("2026-10-01", 30, "2026-10-10", done("2026-10-06"), done("2026-10-07"), done("2026-10-08"), done("2026-10-09"), done("2026-10-10"))
        assertEquals(5, filled.currentStreak)
    }

    @Test fun `weekly streak follows calendar weeks`() {
        val checkIns = arrayOf(done("2026-10-08"), done("2026-10-01"), done("2026-09-24"))
        // Wednesday 14 Oct: this week is still open, so the three earlier weeks count.
        assertEquals(3, timeline(ChallengeFrequency.WEEKLY, "2026-09-21", 84, "2026-10-14", *checkIns).currentStreak)
        // Monday 19 Oct: the week of 12 Oct closed without a check-in.
        assertEquals(0, timeline(ChallengeFrequency.WEEKLY, "2026-09-21", 84, "2026-10-19", *checkIns).currentStreak)
        assertEquals(3, timeline(ChallengeFrequency.WEEKLY, "2026-09-21", 84, "2026-10-19", *checkIns).longestStreak)
    }

    @Test fun `longest streak is the longest run of done periods`() {
        val t = daily("2026-10-01", 30, "2026-10-12",
            done("2026-10-01"), done("2026-10-02"), done("2026-10-03"), done("2026-10-05"), done("2026-10-06"))
        assertEquals(3, t.longestStreak)
        assertEquals(0, t.currentStreak)
    }

    // ─── Rhythm (needs restart) ──────────────────────────────────────────────

    @Test fun `rhythm is judged by calendar periods, not days since the last check-in`() {
        // Done on Monday 28 Sep; on Wednesday 7 Oct the second week is still open. The old rule (9 days > 7) fired here.
        assertFalse(timeline(ChallengeFrequency.WEEKLY, "2026-09-28", 84, "2026-10-07", done("2026-09-28")).needsRestart)
        // Done on 31 Jan; February closed without one. The old rule (29 days < 30) missed this.
        assertTrue(timeline(ChallengeFrequency.MONTHLY, "2027-01-01", 180, "2027-03-01", done("2027-01-31")).needsRestart)
        // Done 1 Oct, now 20 Nov: November is still open. The old rule (50 days > 30) fired here.
        assertFalse(timeline(ChallengeFrequency.MONTHLY, "2026-10-01", 180, "2026-11-20", done("2026-10-01")).needsRestart)
    }

    @Test fun `a partial first period is not held against the rhythm unless the miss was recorded`() {
        // Weekly, started on Sunday 4 Oct with no check-in that day.
        assertFalse(timeline(ChallengeFrequency.WEEKLY, "2026-10-04", 28, "2026-10-06").needsRestart)
        assertTrue(timeline(ChallengeFrequency.WEEKLY, "2026-10-04", 28, "2026-10-06", miss("2026-10-04")).needsRestart)
        // A weekly challenge started on a Monday has a full first week, which counts like any other.
        assertTrue(timeline(ChallengeFrequency.WEEKLY, "2026-09-28", 28, "2026-10-06").needsRestart)
        // A recovered rhythm (missed week, then a check-in) no longer asks for a restart.
        assertFalse(timeline(ChallengeFrequency.WEEKLY, "2026-09-28", 28, "2026-10-06", done("2026-10-06")).needsRestart)
    }

    // ─── Outcome ─────────────────────────────────────────────────────────────

    @Test fun `final period done completes the attempt`() {
        val checkIns = (1..7).map { done("2026-10-0$it") }.toTypedArray()
        assertEquals(ChallengeStatus.COMPLETED, daily("2026-10-01", 7, "2026-10-07", *checkIns).outcome)
        assertEquals(ChallengeStatus.ACTIVE, daily("2026-10-01", 7, "2026-10-06", *checkIns.copyOfRange(0, 6)).outcome)
    }

    @Test fun `a missed final period does not complete the attempt`() {
        val earlier = (1..6).map { done("2026-10-0$it") }
        val withMiss = (earlier + miss("2026-10-07")).toTypedArray()
        // Recorded miss on the last day: still active, it can be replaced until the window closes (end of 8 Oct).
        assertEquals(ChallengeStatus.ACTIVE, daily("2026-10-01", 7, "2026-10-07", *withMiss).outcome)
        assertEquals(ChallengeStatus.ACTIVE, daily("2026-10-01", 7, "2026-10-08", *withMiss).outcome)
        assertEquals(ChallengeStatus.FAILED, daily("2026-10-01", 7, "2026-10-09", *withMiss).outcome)
    }

    @Test fun `an attempt past its end with no final check-in is resolved`() {
        val earlier = (1..6).map { done("2026-10-0$it") }.toTypedArray()
        // The last day (7 Oct) can still be checked in on 8 Oct.
        assertEquals(ChallengeStatus.ACTIVE, daily("2026-10-01", 7, "2026-10-08", *earlier).outcome)
        assertEquals(ChallengeStatus.FAILED, daily("2026-10-01", 7, "2026-10-20", *earlier).outcome)
        // Weekly: no grace, the attempt is decided the day after its last week.
        assertEquals(ChallengeStatus.FAILED, timeline(ChallengeFrequency.WEEKLY, "2026-09-28", 14, "2026-10-12", done("2026-09-28")).outcome)
        assertEquals(ChallengeStatus.COMPLETED,
            timeline(ChallengeFrequency.WEEKLY, "2026-09-28", 14, "2026-10-12", done("2026-09-29"), done("2026-10-07")).outcome)
    }

    @Test fun `the final day done waits while yesterday can still be checked in`() {
        val gap = arrayOf(done("2026-10-01"), done("2026-10-02"), done("2026-10-03"))
        assertEquals(ChallengeStatus.ACTIVE, daily("2026-10-01", 5, "2026-10-05", *gap, done("2026-10-05")).outcome)
        assertEquals(ChallengeStatus.COMPLETED, daily("2026-10-01", 5, "2026-10-05", *gap, done("2026-10-04"), done("2026-10-05")).outcome)
        // The next day yesterday's window has closed: the attempt completes with the gap on record.
        val next = daily("2026-10-01", 5, "2026-10-06", *gap, done("2026-10-05"))
        assertEquals(ChallengeStatus.COMPLETED, next.outcome)
        assertEquals(4, next.doneCount)
        assertEquals(1, next.missedCount)
    }

    @Test fun `nothing is due before the start`() {
        val t = daily("2026-10-12", 7, "2026-10-10")
        assertFalse(t.hasStarted)
        assertNull(t.current)
        assertEquals(7, t.futureCount)
        assertEquals(ChallengeStatus.ACTIVE, t.outcome)
        assertFalse(t.needsRestart)
        assertFalse(t.canCheckIn(date("2026-10-10")))
    }
}
