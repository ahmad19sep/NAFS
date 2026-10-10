package com.ahmad.nafs.ui.tasks

import com.ahmad.nafs.core.tasks.NafsTask
import com.ahmad.nafs.core.tasks.TaskPriority
import com.ahmad.nafs.domain.tasks.TaskCompletionStatus
import com.ahmad.nafs.domain.tasks.TaskPeriodType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskPresentationTest {
    private val today = LocalDate.parse("2026-10-09")

    @Test
    fun visibleTasksKeepOnlyCurrentAndGraceThenOrderByPeriodPriorityAndStatus() {
        val currentLow = task("current-low", today, TaskPriority.LOW)
        val currentDone = task(
            "current-done",
            today,
            TaskPriority.HIGH,
            TaskCompletionStatus.COMPLETED,
        )
        val currentActive = task("current-active", today, TaskPriority.HIGH)
        val grace = task("grace", today.minusDays(1), TaskPriority.HIGH)
        val expired = task("expired", today.minusDays(2), TaskPriority.HIGH)
        val weekly = task(
            id = "weekly",
            date = LocalDate.parse("2026-10-05"),
            priority = TaskPriority.HIGH,
            type = TaskPeriodType.WEEKLY,
        )

        val visible = visibleTasksFor(
            TaskUiState(
                today = today,
                selectedType = TaskPeriodType.DAILY,
                tasks = listOf(grace, expired, currentLow, currentDone, weekly, currentActive),
            ),
        )

        assertEquals(
            listOf("current-active", "current-done", "current-low", "grace"),
            visible.map(NafsTask::id),
        )
    }

    private fun task(
        id: String,
        date: LocalDate,
        priority: TaskPriority,
        status: TaskCompletionStatus = TaskCompletionStatus.ACTIVE,
        type: TaskPeriodType = TaskPeriodType.DAILY,
    ) = NafsTask(
        id = id,
        userId = "user",
        title = id,
        note = null,
        type = type,
        priority = priority,
        status = status,
        periodDate = date,
        dueTime = null,
        completedAt = null,
        createdAt = "2026-10-09T00:00:00Z",
        updatedAt = "2026-10-09T00:00:00Z",
    )
}
