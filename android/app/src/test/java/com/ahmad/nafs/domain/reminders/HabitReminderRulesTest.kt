package com.ahmad.nafs.domain.reminders

import com.ahmad.nafs.domain.habits.HabitResult
import com.ahmad.nafs.domain.habits.HabitSchedule
import com.ahmad.nafs.domain.habits.HabitType
import com.ahmad.nafs.domain.habits.ScheduleKind
import com.ahmad.nafs.domain.tasks.TaskReminderDeliveryRule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitReminderRulesTest {
    private val zone = "Asia/Karachi" // UTC+5

    @Test
    fun nextTriggerIsTodayWhenTheTimeIsStillAhead() {
        val at = HabitReminderRules.nextTrigger(LocalTime.of(20, 0), HabitSchedule(), zone, Instant.parse("2026-10-10T10:00:00Z"))
        assertEquals(Instant.parse("2026-10-10T15:00:00Z"), at)
    }

    @Test
    fun nextTriggerSkipsUnscheduledDaysAndPausedHabits() {
        // 2026-10-10 is a Saturday; only Mondays are scheduled.
        val mondays = HabitSchedule(scheduleKind = ScheduleKind.WEEKDAYS, scheduleDays = setOf(DayOfWeek.MONDAY))
        assertEquals(Instant.parse("2026-10-12T03:00:00Z"),
            HabitReminderRules.nextTrigger(LocalTime.of(8, 0), mondays, zone, Instant.parse("2026-10-10T10:00:00Z")))
        assertNull(HabitReminderRules.nextTrigger(LocalTime.of(8, 0), HabitSchedule(isPaused = true), zone, Instant.parse("2026-10-10T10:00:00Z")))
        assertNull(HabitReminderRules.nextTrigger(LocalTime.of(8, 0), HabitSchedule(), "Not/AZone", Instant.parse("2026-10-10T10:00:00Z")))
    }

    @Test
    fun weeklyTargetCountsOnlyThisWeek() {
        val schedule = HabitSchedule(scheduleKind = ScheduleKind.PER_WEEK, weeklyTarget = 2)
        val today = LocalDate.of(2026, 10, 10) // Saturday; the week started Monday the 5th
        val lastWeek = HabitResult(LocalDate.of(2026, 10, 3), completed = true)
        val monday = HabitResult(LocalDate.of(2026, 10, 5), completed = true)
        assertFalse(HabitReminderRules.weeklyTargetMet(schedule, listOf(lastWeek, monday), today))
        assertTrue(HabitReminderRules.weeklyTargetMet(schedule, listOf(monday, HabitResult(LocalDate.of(2026, 10, 8), completed = true)), today))
    }

    @Test
    fun reminderBodyDescribesProgressAndStreak() {
        val counter = HabitReminderFacts("Water", "💧", HabitType.COUNTER, targetValue = 8.0, unit = "glasses", currentStreak = 5, loggedValue = 3.0)
        assertEquals("3 of 8 glasses so far today. Keep your 5-day streak going.", HabitReminderRules.reminderBody(counter))
        val duration = HabitReminderFacts("Read", "📖", HabitType.DURATION, timeTargetMinutes = 90)
        assertEquals("Today's target: 1 h 30 min.", HabitReminderRules.reminderBody(duration))
    }

    @Test
    fun eveningCheckLeadsWithTheLongestStreakAtRisk() {
        val open = listOf(
            HabitReminderFacts("Workout", "🏋️", HabitType.SIMPLE, currentStreak = 1),
            HabitReminderFacts("Quran", "🕌", HabitType.SUBJECT, currentStreak = 21),
        )
        val (title, body) = requireNotNull(HabitReminderRules.eveningCheck(open))
        assertEquals("Keep your 21-day Quran streak", title)
        assertEquals("🕌 Quran and 🏋️ Workout are still open. There's still time tonight.", body)
        assertNull(HabitReminderRules.eveningCheck(emptyList()))
    }

    @Test
    fun morningPlanLeavesUnknownCountsOut() {
        assertEquals("Your day: 3 tasks · 2 habits" to "1 high-priority task. First up: Call Ammi at 8:00 PM.",
            ScheduledReminderRules.morningPlan(3, 1, 2, "Call Ammi at 8:00 PM", true))
        assertEquals("Good morning" to "Open NAFS to plan your day.",
            ScheduledReminderRules.morningPlan(null, 0, null, null, null))
        assertEquals("Good morning" to "Set today's priorities in Check-in.",
            ScheduledReminderRules.morningPlan(0, 0, 0, null, false))
    }

    @Test
    fun snoozedTaskReminderSaysWhenItIsOverdue() {
        val due = Instant.parse("2026-10-10T15:00:00Z")
        assertEquals("Due in 10 minutes", TaskReminderDeliveryRule.dueText(due, Instant.parse("2026-10-10T14:50:00Z")))
        assertEquals("Due now", TaskReminderDeliveryRule.dueText(due, Instant.parse("2026-10-10T15:00:20Z")))
        assertEquals("Overdue by 5 minutes", TaskReminderDeliveryRule.dueText(due, Instant.parse("2026-10-10T15:05:30Z")))
        assertNull(TaskReminderDeliveryRule.dueText(due, Instant.parse("2026-10-10T16:30:00Z")))
    }
}
