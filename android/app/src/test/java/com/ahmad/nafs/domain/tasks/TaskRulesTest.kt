package com.ahmad.nafs.domain.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRulesTest {
    @Test
    fun desiredCompletionStateIsSafeToReplay() {
        val first = TaskStatusRule.resolve(TaskCompletionStatus.ACTIVE, completed = true)
        val replay = TaskStatusRule.resolve(first.status, completed = true)

        assertEquals(TaskCompletionStatus.COMPLETED, first.status)
        assertTrue(first.changed)
        assertEquals(TaskCompletionStatus.COMPLETED, replay.status)
        assertFalse(replay.changed)
    }

    @Test
    fun desiredReopenStateIsSafeToReplay() {
        val first = TaskStatusRule.resolve(TaskCompletionStatus.COMPLETED, completed = false)
        val replay = TaskStatusRule.resolve(first.status, completed = false)

        assertEquals(TaskCompletionStatus.ACTIVE, first.status)
        assertTrue(first.changed)
        assertFalse(replay.changed)
    }

    @Test
    fun taskDraftRequiresTitleAndValidDailyTime() {
        assertEquals("Enter a task title.", TaskDraftValidator.titleError("  "))
        assertNull(TaskDraftValidator.titleError("Read"))
        assertNull(TaskDraftValidator.dueTimeError(TaskPeriodType.DAILY, "23:59"))
        assertEquals(
            "Use a 24-hour time such as 09:30.",
            TaskDraftValidator.dueTimeError(TaskPeriodType.DAILY, "25:90"),
        )
        assertEquals(
            "Only daily tasks can have a due time.",
            TaskDraftValidator.dueTimeError(TaskPeriodType.WEEKLY, "09:30"),
        )
    }
}
