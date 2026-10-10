package com.ahmad.nafs.domain.challenges

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ChallengeRestartTest {
    private val start = LocalDate.parse("2026-10-01")
    @Test fun currentPeriodRemainsPendingUntilItsWindowPasses() {
        assertFalse(ChallengeCadence.needsRestart(ChallengeFrequency.DAILY, start, emptyList(), start))
        assertFalse(ChallengeCadence.needsRestart(ChallengeFrequency.WEEKLY, start, emptyList(), start.plusDays(6)))
        assertTrue(ChallengeCadence.needsRestart(ChallengeFrequency.WEEKLY, start, emptyList(), start.plusDays(7)))
    }
    @Test fun explicitMissOffersANewAttemptButTodayCompletionDoesNot() {
        assertTrue(ChallengeCadence.needsRestart(ChallengeFrequency.DAILY, start, listOf(DatedChallengeCheckIn(start, false)), start))
        assertFalse(ChallengeCadence.needsRestart(ChallengeFrequency.DAILY, start, listOf(DatedChallengeCheckIn(start, true)), start))
    }
    @Test fun oldAttemptCheckinsDoNotSupplyTheNewAttemptsStreak() {
        assertTrue(ChallengeCadence.needsRestart(ChallengeFrequency.DAILY, start, listOf(DatedChallengeCheckIn(start.minusDays(1), true)), start.plusDays(1)))
        assertEquals(0, ChallengeCadence.periodStreak(ChallengeFrequency.WEEKLY, start, listOf(DatedChallengeCheckIn(start.minusDays(1), true)), start))
    }
    @Test fun aRecentCompletedUnitProtectsTheNextPendingUnit() {
        assertFalse(ChallengeCadence.needsRestart(ChallengeFrequency.DAILY, start, listOf(DatedChallengeCheckIn(start, true)), start.plusDays(1)))
        assertTrue(ChallengeCadence.needsRestart(ChallengeFrequency.DAILY, start, listOf(DatedChallengeCheckIn(start, true)), start.plusDays(2)))
    }
}
