package com.ahmad.nafs.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmad.nafs.core.goals.Goal
import com.ahmad.nafs.core.goals.GoalLoadResult
import com.ahmad.nafs.core.goals.GoalRepository
import com.ahmad.nafs.core.habits.Habit
import com.ahmad.nafs.core.habits.HabitLoadResult
import com.ahmad.nafs.core.habits.HabitLog
import com.ahmad.nafs.core.habits.HabitRepository
import com.ahmad.nafs.core.tasks.NafsTask
import com.ahmad.nafs.core.tasks.TaskLoadResult
import com.ahmad.nafs.core.tasks.TaskRepository
import com.ahmad.nafs.core.time.AccountDate
import com.ahmad.nafs.domain.habits.HabitResult
import com.ahmad.nafs.domain.habits.HabitStreak
import com.ahmad.nafs.domain.tasks.TaskPeriod
import com.ahmad.nafs.domain.tasks.TaskPeriodType
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val userId: String? = null,
    val timezone: String = "UTC",
    val today: LocalDate = LocalDate.now(ZoneOffset.UTC),
    val tasks: List<NafsTask> = emptyList(),
    val habits: List<Habit> = emptyList(),
    val habitLogs: List<HabitLog> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isOffline: Boolean = false,
    val message: String? = null,
)

data class DashboardMetrics(
    val completedTasks: Int,
    val totalTasks: Int,
    val completedHabits: Int,
    val totalHabits: Int,
    val averageGoalProgress: Int,
    val completedGoals: Int,
    val totalGoals: Int,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val habitRepository: HabitRepository,
    private val goalRepository: GoalRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    fun initialize(userId: String, timezone: String) {
        if (_uiState.value.userId == userId && _uiState.value.timezone == timezone) return
        val today = runCatching { AccountDate.today(timezone) }
            .getOrElse { LocalDate.now(ZoneOffset.UTC) }
        _uiState.value = DashboardUiState(userId = userId, timezone = timezone, today = today)
        refresh()
    }

    fun refresh() {
        val state = _uiState.value
        val userId = state.userId ?: return
        if (state.isRefreshing) return
        val today = runCatching { AccountDate.today(state.timezone) }.getOrElse { state.today }
        val hasContent = state.tasks.isNotEmpty() || state.habits.isNotEmpty() || state.goals.isNotEmpty()
        _uiState.update {
            it.copy(
                today = today,
                isLoading = !hasContent,
                isRefreshing = hasContent,
                message = null,
            )
        }
        viewModelScope.launch {
            val taskRequest = async { taskRepository.load(userId, today) }
            val habitRequest = async { habitRepository.load(userId, today) }
            val goalRequest = async { goalRepository.load(userId) }
            val taskResult = taskRequest.await()
            val habitResult = habitRequest.await()
            val goalResult = goalRequest.await()
            _uiState.update { current ->
                val tasks = (taskResult as? TaskLoadResult.Success)?.tasks ?: current.tasks
                val habitSuccess = habitResult as? HabitLoadResult.Success
                val goals = (goalResult as? GoalLoadResult.Success)?.goals ?: current.goals
                val failures = listOfNotNull(
                    (taskResult as? TaskLoadResult.Failure)?.message,
                    (habitResult as? HabitLoadResult.Failure)?.message,
                    (goalResult as? GoalLoadResult.Failure)?.message,
                ).distinct()
                current.copy(
                    tasks = tasks,
                    habits = habitSuccess?.habits ?: current.habits,
                    habitLogs = habitSuccess?.logs ?: current.habitLogs,
                    goals = goals,
                    isLoading = false,
                    isRefreshing = false,
                    isOffline = listOfNotNull(
                        (taskResult as? TaskLoadResult.Success)?.isOffline,
                        habitSuccess?.isOffline,
                        (goalResult as? GoalLoadResult.Success)?.isOffline,
                    ).any { it },
                    message = failures.takeIf(List<String>::isNotEmpty)?.let {
                        "Some dashboard data could not refresh. Showing the latest available information."
                    },
                )
            }
        }
    }

    fun dismissMessage() = _uiState.update { it.copy(message = null) }
}

fun DashboardUiState.todayTasks(): List<NafsTask> {
    val periodKey = TaskPeriod.key(TaskPeriodType.DAILY, today)
    return tasks.filter { it.type == TaskPeriodType.DAILY && it.periodDate == periodKey }
}

fun DashboardUiState.scheduledHabits(): List<Habit> = habits.filter {
    !it.isPaused && HabitStreak.isScheduled(it.schedule(), today)
}

fun DashboardUiState.metrics(): DashboardMetrics {
    val todayTasks = todayTasks()
    val scheduledHabits = scheduledHabits()
    val logsByHabit = habitLogs.filter { it.date == today }.associateBy(HabitLog::habitId)
    val completedHabits = scheduledHabits.count { habit ->
        val log = logsByHabit[habit.id]
        HabitStreak.isComplete(
            habit.schedule(),
            log?.let { HabitResult(it.date, it.completed, it.value, it.durationMinutes) },
        )
    }
    return DashboardMetrics(
        completedTasks = todayTasks.count(NafsTask::isCompleted),
        totalTasks = todayTasks.size,
        completedHabits = completedHabits,
        totalHabits = scheduledHabits.size,
        averageGoalProgress = if (goals.isEmpty()) 0 else goals.sumOf(Goal::progressPercent) / goals.size,
        completedGoals = goals.count { it.progressPercent >= 100 },
        totalGoals = goals.size,
    )
}

fun DashboardUiState.nextTask(): NafsTask? = todayTasks()
    .asSequence()
    .filterNot(NafsTask::isCompleted)
    .sortedWith(
        compareBy<NafsTask> { it.dueTime == null }
            .thenBy { it.dueTime }
            .thenBy { it.priority.rank },
    )
    .firstOrNull()

fun DashboardUiState.leadingGoals(limit: Int = 2): List<Goal> = goals
    .filter { it.progressPercent < 100 }
    .sortedWith(compareBy<Goal> { it.deadline == null }.thenBy { it.deadline }.thenByDescending { it.progressPercent })
    .take(limit)
