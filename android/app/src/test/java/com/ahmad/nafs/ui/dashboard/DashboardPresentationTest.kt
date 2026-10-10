package com.ahmad.nafs.ui.dashboard

import com.ahmad.nafs.core.goals.Goal
import com.ahmad.nafs.core.habits.Habit
import com.ahmad.nafs.core.habits.HabitLog
import com.ahmad.nafs.core.tasks.NafsTask
import com.ahmad.nafs.core.tasks.TaskPriority
import com.ahmad.nafs.domain.goals.GoalType
import com.ahmad.nafs.domain.habits.HabitType
import com.ahmad.nafs.domain.habits.ScheduleKind
import com.ahmad.nafs.domain.tasks.TaskCompletionStatus
import com.ahmad.nafs.domain.tasks.TaskPeriodType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardPresentationTest {
    private val today = LocalDate.parse("2026-10-09")

    @Test
    fun metricsUseOnlyTodaysTasksAndScheduledHabits() {
        val state = DashboardUiState(
            today = today,
            tasks = listOf(
                task("done", completed = true),
                task("open", completed = false),
                task("weekly", completed = true, type = TaskPeriodType.WEEKLY),
            ),
            habits = listOf(
                habit("active"),
                habit("paused", paused = true),
            ),
            habitLogs = listOf(
                HabitLog("user", "active", today, true, 1.0, 0, null),
            ),
            goals = listOf(
                goal("half", storedProgress = 0, current = 5.0, target = 10.0),
                goal("done-goal", storedProgress = 100),
            ),
        )

        assertEquals(
            DashboardMetrics(
                completedTasks = 1,
                totalTasks = 2,
                completedHabits = 1,
                totalHabits = 1,
                averageGoalProgress = 75,
                completedGoals = 1,
                totalGoals = 2,
            ),
            state.metrics(),
        )
    }

    @Test
    fun nextTaskPrefersEarliestDueTimeThenPriority() {
        val state = DashboardUiState(
            today = today,
            tasks = listOf(
                task("untimed", dueTime = null, priority = TaskPriority.HIGH),
                task("later", dueTime = "18:00", priority = TaskPriority.HIGH),
                task("next", dueTime = "09:30", priority = TaskPriority.LOW),
            ),
        )

        assertEquals("next", state.nextTask()?.id)
    }

    private fun task(
        id: String,
        completed: Boolean = false,
        type: TaskPeriodType = TaskPeriodType.DAILY,
        dueTime: String? = null,
        priority: TaskPriority = TaskPriority.MEDIUM,
    ) = NafsTask(
        id = id,
        userId = "user",
        title = id,
        note = null,
        type = type,
        priority = priority,
        status = if (completed) TaskCompletionStatus.COMPLETED else TaskCompletionStatus.ACTIVE,
        periodDate = today,
        dueTime = dueTime,
        completedAt = null,
        createdAt = "2026-10-09T00:00:00Z",
        updatedAt = "2026-10-09T00:00:00Z",
    )

    private fun habit(id: String, paused: Boolean = false) = Habit(
        id = id,
        userId = "user",
        name = id,
        emoji = "⭐",
        type = HabitType.SIMPLE,
        targetValue = 1.0,
        unit = "",
        timeTargetMinutes = 0,
        subjectName = null,
        subjectTotal = null,
        subjectPosition = 0,
        subjectUnit = null,
        scheduleKind = ScheduleKind.DAILY,
        scheduleDays = emptySet(),
        weeklyTarget = null,
        isPaused = paused,
        sortOrder = 0,
        currentStreak = 0,
        longestStreak = 0,
    )

    private fun goal(
        id: String,
        storedProgress: Int,
        current: Double? = null,
        target: Double? = null,
    ) = Goal(
        id = id,
        userId = "user",
        title = id,
        emoji = "🎯",
        description = null,
        type = GoalType.MONTHLY,
        category = null,
        startDate = today,
        deadline = today.plusDays(30),
        storedProgressPercent = storedProgress,
        targetValue = target,
        currentValue = current,
        unit = target?.let { "items" },
        linkedHabitIds = emptyList(),
        milestones = emptyList(),
        createdAt = "2026-10-09T00:00:00Z",
        updatedAt = "2026-10-09T00:00:00Z",
    )
}
