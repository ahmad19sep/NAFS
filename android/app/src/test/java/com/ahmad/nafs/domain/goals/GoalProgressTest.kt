package com.ahmad.nafs.domain.goals

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class GoalProgressTest {
    @Test
    fun milestoneProgressUsesCompletedRatio() {
        assertEquals(0, GoalProgress.fromMilestones(emptyList()))
        assertEquals(50, GoalProgress.fromMilestones(listOf(true, false)))
        assertEquals(67, GoalProgress.fromMilestones(listOf(true, true, false)))
        assertEquals(100, GoalProgress.fromMilestones(listOf(true, true)))
    }

    @Test
    fun milestonesOverrideNumericAndStoredProgress() {
        assertEquals(
            50,
            GoalProgress.resolve(
                storedPercent = 90,
                milestones = listOf(true, false),
                currentValue = 9.0,
                targetValue = 10.0,
            ),
        )
    }

    @Test
    fun numericProgressIsClampedAndHandlesInvalidTarget() {
        assertEquals(25, GoalProgress.fromNumeric(5.0, 20.0))
        assertEquals(100, GoalProgress.fromNumeric(30.0, 20.0))
        assertEquals(0, GoalProgress.fromNumeric(5.0, 0.0))
    }

    @Test
    fun defaultDeadlinesUsePureCalendarArithmetic() {
        val start = LocalDate.parse("2026-12-20")

        assertEquals(LocalDate.parse("2026-12-27"), GoalDates.defaultDeadline(GoalType.WEEKLY, start))
        assertEquals(LocalDate.parse("2027-01-19"), GoalDates.defaultDeadline(GoalType.MONTHLY, start))
        assertEquals(7L, GoalDates.daysRemaining(LocalDate.parse("2026-12-27"), start))
    }
}

