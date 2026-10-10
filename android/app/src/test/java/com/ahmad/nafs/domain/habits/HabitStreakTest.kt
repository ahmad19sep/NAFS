package com.ahmad.nafs.domain.habits

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitStreakTest {
    private val friday = LocalDate.parse("2026-10-09")

    @Test
    fun incompleteTodayDoesNotEraseYesterdayStreak() {
        val results = listOf(
            HabitResult(friday.minusDays(1), completed = true),
            HabitResult(friday.minusDays(2), completed = true),
        )

        assertEquals(2, HabitStreak.current(HabitSchedule(), results, friday))
    }

    @Test
    fun scheduledRestDaysAreSkipped() {
        val weekdays = HabitSchedule(
            scheduleKind = ScheduleKind.WEEKDAYS,
            scheduleDays = setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ),
        )
        val monday = LocalDate.parse("2026-10-12")
        val results = listOf(
            HabitResult(monday, completed = true),
            HabitResult(LocalDate.parse("2026-10-09"), completed = true),
        )

        assertEquals(2, HabitStreak.current(weekdays, results, monday))
    }

    @Test
    fun counterAndDurationMustMeetTheirTargets() {
        val counter = HabitSchedule(type = HabitType.COUNTER, targetValue = 8.0)
        val duration = HabitSchedule(type = HabitType.DURATION, timeTargetMinutes = 30)

        assertFalse(HabitStreak.isComplete(counter, HabitResult(friday, true, value = 7.0)))
        assertTrue(HabitStreak.isComplete(counter, HabitResult(friday, true, value = 8.0)))
        assertFalse(HabitStreak.isComplete(duration, HabitResult(friday, true, durationMinutes = 29)))
        assertTrue(HabitStreak.isComplete(duration, HabitResult(friday, true, durationMinutes = 30)))
    }

    @Test
    fun subjectProgressCompletesWhenAnyProgressWasLogged() {
        val subject = HabitSchedule(type = HabitType.SUBJECT, targetValue = 50.0)

        assertTrue(HabitStreak.isComplete(subject, HabitResult(friday, true, value = 3.0)))
    }

    @Test
    fun perWeekStreakCountsTargetWeeksAndKeepsCurrentWeekPending() {
        val habit = HabitSchedule(
            scheduleKind = ScheduleKind.PER_WEEK,
            weeklyTarget = 3,
        )
        val results = listOf(
            HabitResult(LocalDate.parse("2026-09-28"), true),
            HabitResult(LocalDate.parse("2026-09-30"), true),
            HabitResult(LocalDate.parse("2026-10-02"), true),
            HabitResult(LocalDate.parse("2026-10-05"), true),
        )

        assertEquals(1, HabitStreak.current(habit, results, friday))
    }

    @Test
    fun longestFindsTheBestRunEvenAfterItBroke() {
        val results = (10L..14L).map { HabitResult(friday.minusDays(it), completed = true) } +
            (1L..2L).map { HabitResult(friday.minusDays(it), completed = true) }

        assertEquals(5, HabitStreak.longest(HabitSchedule(), results, friday))
        assertEquals(2, HabitStreak.current(HabitSchedule(), results, friday))
    }

    @Test
    fun longestSkipsRestDaysAndIgnoresAnUnfinishedToday() {
        val mondayWednesdayFriday = HabitSchedule(
            scheduleKind = ScheduleKind.WEEKDAYS,
            scheduleDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
        )
        val results = listOf("2026-09-28", "2026-09-30", "2026-10-02", "2026-10-05", "2026-10-07").map {
            HabitResult(LocalDate.parse(it), completed = true)
        }

        assertEquals(5, HabitStreak.longest(mondayWednesdayFriday, results, friday))
    }

    @Test
    fun longestCountsOnlyCompleteDays() {
        val counter = HabitSchedule(type = HabitType.COUNTER, targetValue = 8.0)
        val results = listOf(
            HabitResult(friday.minusDays(3), completed = true, value = 8.0),
            HabitResult(friday.minusDays(2), completed = false, value = 6.0),
            HabitResult(friday.minusDays(1), completed = true, value = 9.0),
        )

        assertEquals(1, HabitStreak.longest(counter, results, friday))
    }

    @Test
    fun longestReadsPausedHabitsAsIfActive() {
        val results = (1L..3L).map { HabitResult(friday.minusDays(it), completed = true) }

        assertEquals(0, HabitStreak.current(HabitSchedule(isPaused = true), results, friday))
        assertEquals(3, HabitStreak.longest(HabitSchedule(isPaused = true), results, friday))
    }

    @Test
    fun longestPerWeekCountsWeeksThatMetTheTarget() {
        val habit = HabitSchedule(scheduleKind = ScheduleKind.PER_WEEK, weeklyTarget = 2)
        val results = listOf(
            // Week of 14 Sep: met. Week of 21 Sep: met. Week of 28 Sep: one short. This week: met.
            "2026-09-14", "2026-09-16", "2026-09-22", "2026-09-26", "2026-09-29", "2026-10-06", "2026-10-08",
        ).map { HabitResult(LocalDate.parse(it), completed = true) }

        assertEquals(2, HabitStreak.longest(habit, results, friday))
        assertEquals(1, HabitStreak.current(habit, results, friday))
    }

    @Test
    fun summaryNeverReadsBelowTheStoredBestOrTheCurrentRun() {
        val results = (1L..4L).map { HabitResult(friday.minusDays(it), completed = true) }

        assertEquals(StreakSummary(current = 4, best = 30), HabitStreak.summary(HabitSchedule(), results, friday, storedBest = 30))
        assertEquals(StreakSummary(current = 4, best = 4), HabitStreak.summary(HabitSchedule(), results, friday, storedBest = 2))
    }

    @Test
    fun completedThisWeekCountsMondayToToday() {
        val habit = HabitSchedule(scheduleKind = ScheduleKind.PER_WEEK, weeklyTarget = 3)
        val results = listOf(
            HabitResult(LocalDate.parse("2026-10-04"), completed = true), // last Sunday
            HabitResult(LocalDate.parse("2026-10-05"), completed = true), // Monday
            HabitResult(LocalDate.parse("2026-10-07"), completed = false, value = 0.0),
            HabitResult(friday, completed = true),
        )

        assertEquals(2, HabitStreak.completedThisWeek(habit, results, friday))
        assertEquals(3, HabitStreak.weeklyTarget(habit))
        assertEquals(1, HabitStreak.weeklyTarget(habit.copy(weeklyTarget = null)))
        assertEquals(LocalDate.parse("2026-10-05"), HabitStreak.weekStart(friday))
    }
}

