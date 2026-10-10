package com.ahmad.nafs.domain.tasks

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskReminderPlannerTest {
    private val dueDate = LocalDate.parse("2026-10-09")

    @Test
    fun plansOneHourAndThirtyMinuteAlertsInTheAccountTimezone() {
        val reminders = TaskReminderPlanner.plan(
            periodDate = dueDate,
            dueTime = "18:00",
            timezone = "Asia/Karachi",
            now = Instant.parse("2026-10-09T11:30:00Z"),
        )

        assertEquals(listOf(60, 30), reminders.map(PlannedTaskReminder::minutesBefore))
        assertEquals(
            listOf(
                Instant.parse("2026-10-09T12:00:00Z"),
                Instant.parse("2026-10-09T12:30:00Z"),
            ),
            reminders.map(PlannedTaskReminder::triggerAt),
        )
    }

    @Test
    fun skipsReminderWindowsThatAlreadyPassed() {
        val reminders = TaskReminderPlanner.plan(
            periodDate = dueDate,
            dueTime = "18:00:00",
            timezone = "Asia/Karachi",
            now = Instant.parse("2026-10-09T12:15:00Z"),
        )

        assertEquals(listOf(30), reminders.map(PlannedTaskReminder::minutesBefore))
    }

    @Test
    fun invalidTimeOrTimezoneProducesNoWork() {
        assertTrue(
            TaskReminderPlanner.plan(
                periodDate = dueDate,
                dueTime = "later",
                timezone = "Asia/Karachi",
                now = Instant.EPOCH,
            ).isEmpty(),
        )
        assertTrue(
            TaskReminderPlanner.plan(
                periodDate = dueDate,
                dueTime = "18:00",
                timezone = "Not/AZone",
                now = Instant.EPOCH,
            ).isEmpty(),
        )
    }

    @Test
    fun delayedDeliveryUsesRealRemainingTimeAndSkipsExpiredTasks() {
        val dueAt = Instant.parse("2026-10-09T13:00:00Z")

        assertEquals(
            17,
            TaskReminderDeliveryRule.minutesRemaining(
                dueAt,
                Instant.parse("2026-10-09T12:42:30Z"),
            ),
        )
        assertEquals(null, TaskReminderDeliveryRule.minutesRemaining(dueAt, dueAt))
        assertEquals(
            null,
            TaskReminderDeliveryRule.minutesRemaining(
                dueAt,
                Instant.parse("2026-10-09T13:05:00Z"),
            ),
        )
    }
}
