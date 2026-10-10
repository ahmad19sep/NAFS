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
}

