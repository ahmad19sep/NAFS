package com.ahmad.nafs.domain.challenges

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ChallengeRestartTest {
    // Thursday: a weekly challenge started today has a partial first week (Thu–Sun).
    private val start = LocalDate.parse("2026-10-01")
    @Test fun currentPeriodRemainsPendingUntilItsWindowPasses() {
        assertFalse(ChallengeCadence.needsRestart(ChallengeFrequency.DAILY, start, emptyList(), start))
        assertFalse(ChallengeCadence.needsRestart(ChallengeFrequency.WEEKLY, start, emptyList(), start.plusDays(6)))
        // Changed: the old rule counted 7 days since the start and fired on Thursday 8 Oct, while the calendar week of
        // 5–11 Oct was still open. Rhythm is now period-based: the partial first week (1–4 Oct) is not held against it,
        // so the rhythm breaks only once the first full week closes without a check-in.
        assertFalse(ChallengeCadence.needsRestart(ChallengeFrequency.WEEKLY, start, emptyList(), start.plusDays(7)))
        assertFalse(ChallengeCadence.needsRestart(ChallengeFrequency.WEEKLY, start, emptyList(), LocalDate.parse("2026-10-11")))
        assertTrue(ChallengeCadence.needsRestart(ChallengeFrequency.WEEKLY, start, emptyList(), LocalDate.parse("2026-10-12")))
    }
    @Test fun explicitMissOffersANewAttemptButTodayCompletionDoesNot() {
        assertTrue(ChallengeCadence.needsRestart(ChallengeFrequency.DAILY, start, listOf(DatedChallengeCheckIn(start, false)), start))
        assertFalse(ChallengeCadence.needsRestart(ChallengeFrequency.DAILY, start, listOf(DatedChallengeCheckIn(start, true)), start))
    }
    @Test fun oldAttemptCheckinsDoNotSupplyTheNewAttemptsStreak() {
        val previousAttempt = listOf(DatedChallengeCheckIn(start.minusDays(1), true))
        // Changed: a daily challenge's yesterday stays open until the end of today, so on start + 1 the first day can
        // still be checked in. The old attempt's check-in still doesn't count: once the first day's window closes the
        // rhythm is broken.
        assertFalse(ChallengeCadence.needsRestart(ChallengeFrequency.DAILY, start, previousAttempt, start.plusDays(1)))
        assertTrue(ChallengeCadence.needsRestart(ChallengeFrequency.DAILY, start, previousAttempt, start.plusDays(2)))
        assertEquals(0, ChallengeCadence.periodStreak(ChallengeFrequency.WEEKLY, start, previousAttempt, start))
    }
    @Test fun aRecentCompletedUnitProtectsTheNextPendingUnit() {
        val firstDayDone = listOf(DatedChallengeCheckIn(start, true))
        assertFalse(ChallengeCadence.needsRestart(ChallengeFrequency.DAILY, start, firstDayDone, start.plusDays(1)))
        // Changed: on start + 2 the second day is still in its grace window (check in for yesterday), so the rhythm only
        // breaks on start + 3.
        assertFalse(ChallengeCadence.needsRestart(ChallengeFrequency.DAILY, start, firstDayDone, start.plusDays(2)))
        assertTrue(ChallengeCadence.needsRestart(ChallengeFrequency.DAILY, start, firstDayDone, start.plusDays(3)))
    }
}
