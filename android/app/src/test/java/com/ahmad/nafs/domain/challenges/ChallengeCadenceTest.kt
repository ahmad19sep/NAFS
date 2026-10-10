package com.ahmad.nafs.domain.challenges

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeCadenceTest {
    @Test
    fun `total units rounds partial cadence up`() {
        assertEquals(10, ChallengeCadence.totalUnits(ChallengeFrequency.DAILY, 10))
        assertEquals(2, ChallengeCadence.totalUnits(ChallengeFrequency.WEEKLY, 8))
        assertEquals(2, ChallengeCadence.totalUnits(ChallengeFrequency.MONTHLY, 31))
        assertEquals(1, ChallengeCadence.totalUnits(ChallengeFrequency.YEARLY, 365))
    }

    @Test
    fun `elapsed units include the first challenge day`() {
        val start = LocalDate.parse("2026-10-01")
        assertEquals(1, ChallengeCadence.unitsElapsed(ChallengeFrequency.DAILY, start, start))
        assertEquals(
            2,
            ChallengeCadence.unitsElapsed(
                ChallengeFrequency.WEEKLY,
                start,
                LocalDate.parse("2026-10-08"),
            ),
        )
    }

    @Test
    fun `weekly check-in uses ISO Monday boundary`() {
        val today = LocalDate.parse("2026-10-09")
        val sunday = DatedChallengeCheckIn(LocalDate.parse("2026-10-04"), true)
        val monday = DatedChallengeCheckIn(LocalDate.parse("2026-10-05"), true)

        assertFalse(
            ChallengeCadence.hasCheckedThisPeriod(
                ChallengeFrequency.WEEKLY,
                listOf(sunday),
                today,
            ),
        )
        assertTrue(
            ChallengeCadence.hasCheckedThisPeriod(
                ChallengeFrequency.WEEKLY,
                listOf(monday),
                today,
            ),
        )
    }

    @Test
    fun `monthly check-in does not leak from previous month`() {
        val today = LocalDate.parse("2026-10-09")
        assertFalse(
            ChallengeCadence.hasCheckedThisPeriod(
                ChallengeFrequency.MONTHLY,
                listOf(DatedChallengeCheckIn(LocalDate.parse("2026-09-30"), true)),
                today,
            ),
        )
        assertTrue(
            ChallengeCadence.hasCheckedThisPeriod(
                ChallengeFrequency.MONTHLY,
                listOf(DatedChallengeCheckIn(LocalDate.parse("2026-10-01"), true)),
                today,
            ),
        )
    }

    @Test
    fun `period streak counts consecutive periods from current period`() {
        val checkIns = listOf(
            DatedChallengeCheckIn(LocalDate.parse("2026-10-08"), true),
            DatedChallengeCheckIn(LocalDate.parse("2026-10-01"), true),
            DatedChallengeCheckIn(LocalDate.parse("2026-09-20"), true),
        )
        assertEquals(
            2,
            ChallengeCadence.periodStreak(
                ChallengeFrequency.WEEKLY,
                LocalDate.parse("2026-09-01"),
                checkIns,
                LocalDate.parse("2026-10-09"),
            ),
        )
    }

    @Test
    fun `missed current day resets a daily streak`() {
        val checkIns = listOf(
            DatedChallengeCheckIn(LocalDate.parse("2026-10-08"), true),
            DatedChallengeCheckIn(LocalDate.parse("2026-10-07"), true),
        )
        assertEquals(
            0,
            ChallengeCadence.periodStreak(
                ChallengeFrequency.DAILY,
                LocalDate.parse("2026-10-01"),
                checkIns,
                LocalDate.parse("2026-10-09"),
            ),
        )
    }
}

