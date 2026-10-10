package com.ahmad.nafs.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmad.nafs.core.challenges.Challenge
import com.ahmad.nafs.core.challenges.ChallengeLoadResult
import com.ahmad.nafs.core.challenges.ChallengeRepository
import com.ahmad.nafs.core.goals.Goal
import com.ahmad.nafs.core.goals.GoalLoadResult
import com.ahmad.nafs.core.goals.GoalRepository
import com.ahmad.nafs.core.habits.Habit
import com.ahmad.nafs.core.habits.HabitActionResult
import com.ahmad.nafs.core.habits.HabitLoadResult
import com.ahmad.nafs.core.habits.HabitLog
import com.ahmad.nafs.core.habits.HabitRepository
import com.ahmad.nafs.core.habits.LogHabitIntent
import com.ahmad.nafs.core.reminders.TaskReminderScheduler
import com.ahmad.nafs.core.tasks.NafsTask
import com.ahmad.nafs.core.tasks.TaskActionResult
import com.ahmad.nafs.core.tasks.TaskLoadResult
import com.ahmad.nafs.core.tasks.TaskRepository
import com.ahmad.nafs.core.time.AccountDate
import com.ahmad.nafs.domain.challenges.ChallengeStatus
import com.ahmad.nafs.domain.habits.HabitResult
import com.ahmad.nafs.domain.habits.HabitStreak
import com.ahmad.nafs.domain.habits.HabitType
import com.ahmad.nafs.domain.tasks.TaskCompletionStatus
import com.ahmad.nafs.domain.tasks.TaskPeriod
import com.ahmad.nafs.domain.tasks.TaskPeriodType
import com.ahmad.nafs.ui.tasks.LoadGate
import com.ahmad.nafs.ui.tasks.mergeLoaded
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
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
    /** Null until challenges (or their offline copy) have loaded, so the tile never shows a guess. */
    val challenges: List<Challenge>? = null,
    /** A load has finished at least once, so a later one is a refresh rather than a first load. */
    val hasLoaded: Boolean = false,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isOffline: Boolean = false,
    val message: String? = null,
    /** Today's items being saved from Home, as [taskItemKey] / [habitItemKey]. */
    val pendingItems: Set<String> = emptySet(),
    /** A tick from Home that didn't save; the item is already back as it was. */
    val actionError: DashboardActionError? = null,
)

data class DashboardActionError(val title: String, val message: String)

data class DashboardMetrics(
    val completedTasks: Int,
    val totalTasks: Int,
    val completedHabits: Int,
    val totalHabits: Int,
    val averageGoalProgress: Int,
    val completedGoals: Int,
    val totalGoals: Int,
)

/** Active challenges, and how many of those already running still need this period's check-in. */
data class ChallengeSummary(val active: Int, val needCheckIn: Int, val checkedIn: Int)

sealed interface DashboardUiEvent {
    /** The last open item for today was just completed from Home. */
    data object DayComplete : DashboardUiEvent
}

private data class DashboardLoadKey(val userId: String, val timezone: String, val today: LocalDate)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val habitRepository: HabitRepository,
    private val goalRepository: GoalRepository,
    private val challengeRepository: ChallengeRepository,
    private val reminderScheduler: TaskReminderScheduler,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<DashboardUiEvent>(capacity = Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private val loads = LoadGate<DashboardLoadKey>()

    /** Ticked here while a load was in flight; that load's older copy must not undo them. */
    private val tasksChangedDuringLoad = mutableSetOf<String>()
    private val habitsChangedDuringLoad = mutableSetOf<String>()

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
        val timezone = state.timezone
        val today = runCatching { AccountDate.today(timezone) }.getOrElse { state.today }
        val key = DashboardLoadKey(userId, timezone, today)
        // Opening Home asks twice (initialize, then the resume effect); one load answers both.
        if (loads.isRunning(key)) return
        tasksChangedDuringLoad.clear()
        habitsChangedDuringLoad.clear()
        _uiState.update {
            it.copy(
                today = today,
                isLoading = !it.hasLoaded,
                isRefreshing = it.hasLoaded,
                message = null,
            )
        }
        loads.launch(viewModelScope, key) {
            val taskRequest = async { taskRepository.load(userId, today) }
            val habitRequest = async { habitRepository.load(userId, today) }
            val goalRequest = async { goalRepository.load(userId) }
            val challengeRequest = async { challengeRepository.load(userId) }
            val taskResult = taskRequest.await()
            val habitResult = habitRequest.await()
            val goalResult = goalRequest.await()
            val challengeResult = challengeRequest.await()
            if (_uiState.value.userId != userId) return@launch
            _uiState.update { current ->
                val taskSuccess = taskResult as? TaskLoadResult.Success
                val habitSuccess = habitResult as? HabitLoadResult.Success
                val goalSuccess = goalResult as? GoalLoadResult.Success
                val challengeSuccess = challengeResult as? ChallengeLoadResult.Success
                val pendingTasks = current.pendingItems.mapNotNullTo(mutableSetOf()) { it.removePrefixOrNull(TASK_ITEM) }
                val pendingHabits = current.pendingItems.mapNotNullTo(mutableSetOf()) { it.removePrefixOrNull(HABIT_ITEM) }
                val failures = listOfNotNull(
                    (taskResult as? TaskLoadResult.Failure)?.message,
                    (habitResult as? HabitLoadResult.Failure)?.message,
                    (goalResult as? GoalLoadResult.Failure)?.message,
                    (challengeResult as? ChallengeLoadResult.Failure)?.message,
                ).distinct()
                current.copy(
                    tasks = taskSuccess?.let {
                        mergeLoaded(it.tasks, current.tasks, tasksChangedDuringLoad + pendingTasks, NafsTask::id)
                    } ?: current.tasks,
                    habits = habitSuccess?.habits ?: current.habits,
                    habitLogs = habitSuccess?.let {
                        val keep = (habitsChangedDuringLoad + pendingHabits).mapTo(mutableSetOf()) { id -> logKey(id, today) }
                        mergeLoaded(it.logs, current.habitLogs, keep) { log -> logKey(log.habitId, log.date) }
                    } ?: current.habitLogs,
                    goals = goalSuccess?.goals ?: current.goals,
                    challenges = challengeSuccess?.challenges ?: current.challenges,
                    hasLoaded = true,
                    isLoading = false,
                    isRefreshing = false,
                    isOffline = listOfNotNull(
                        taskSuccess?.isOffline,
                        habitSuccess?.isOffline,
                        goalSuccess?.isOffline,
                        challengeSuccess?.isOffline,
                    ).any { it },
                    message = failures.takeIf(List<String>::isNotEmpty)?.let {
                        "Some dashboard data could not refresh. Showing the latest available information."
                    },
                )
            }
        }
    }

    fun dismissMessage() = _uiState.update { it.copy(message = null) }

    fun dismissActionError() = _uiState.update { it.copy(actionError = null) }

    /** Ticks a task off (or back on) at once, then saves; a failed save puts it back and says why. */
    fun setTaskCompleted(task: NafsTask, completed: Boolean) {
        val state = _uiState.value
        val userId = state.userId ?: return
        val item = taskItemKey(task.id)
        if (item in state.pendingItems) return
        val before = state.tasks.firstOrNull { it.id == task.id } ?: return
        if (before.isCompleted == completed) return
        tasksChangedDuringLoad += task.id
        _uiState.update {
            it.copy(
                tasks = it.tasks.replaceTask(before.withCompletion(completed, Instant.now())),
                pendingItems = it.pendingItems + item,
                actionError = null,
            )
        }
        viewModelScope.launch {
            val result = taskRepository.setCompleted(userId, task.id, completed)
            if (_uiState.value.userId != userId) return@launch
            tasksChangedDuringLoad += task.id
            when (result) {
                is TaskActionResult.Success -> {
                    _uiState.update { current ->
                        current.copy(
                            tasks = result.task?.let(current.tasks::replaceTask) ?: current.tasks,
                            pendingItems = current.pendingItems - item,
                        )
                    }
                    // Completed: its alarm goes quiet. Reopened: schedule it again from the full list.
                    if (completed) {
                        reminderScheduler.cancelTask(task.id)
                    } else {
                        reminderScheduler.reconcile(userId, _uiState.value.tasks, _uiState.value.timezone)
                    }
                    if (completed && _uiState.value.isDayComplete()) eventChannel.send(DashboardUiEvent.DayComplete)
                }
                is TaskActionResult.Failure -> _uiState.update { current ->
                    current.copy(
                        tasks = current.tasks.map {
                            if (it.id == task.id) it.copy(status = before.status, completedAt = before.completedAt) else it
                        },
                        pendingItems = current.pendingItems - item,
                        actionError = DashboardActionError("Couldn't save “${before.title}”", result.message),
                    )
                }
            }
        }
    }

    /** Logs a simple (done / not done) habit for today from Home, optimistically, with rollback on failure. */
    fun setHabitCompleted(habit: Habit, completed: Boolean) {
        val state = _uiState.value
        val userId = state.userId ?: return
        val item = habitItemKey(habit.id)
        if (item in state.pendingItems) return
        val today = state.today
        val current = state.habits.firstOrNull { it.id == habit.id } ?: return
        if (current.type != HabitType.SIMPLE || !HabitStreak.isScheduled(current.schedule(), today)) return
        val before = state.habitLogs.firstOrNull { it.habitId == habit.id && it.date == today }
        if ((before?.completed == true) == completed) return
        val optimistic = HabitLog(
            userId = userId,
            habitId = habit.id,
            date = today,
            completed = completed,
            value = if (completed) 1.0 else 0.0,
            durationMinutes = before?.durationMinutes ?: 0,
            notes = before?.notes,
        )
        habitsChangedDuringLoad += habit.id
        _uiState.update {
            it.copy(
                habitLogs = it.habitLogs.withLog(optimistic),
                pendingItems = it.pendingItems + item,
                actionError = null,
            )
        }
        viewModelScope.launch {
            val result = habitRepository.log(
                LogHabitIntent(
                    userId = userId,
                    habit = current,
                    date = today,
                    value = optimistic.value,
                    durationMinutes = optimistic.durationMinutes,
                    completed = completed,
                    // Keeps a note written on the Habits screen; a plain upsert would clear it.
                    notes = before?.notes,
                ),
            )
            if (_uiState.value.userId != userId) return@launch
            habitsChangedDuringLoad += habit.id
            when (result) {
                is HabitActionResult.Success -> {
                    _uiState.update {
                        it.copy(habitLogs = it.habitLogs.withLog(result.log), pendingItems = it.pendingItems - item)
                    }
                    if (completed && _uiState.value.isDayComplete()) eventChannel.send(DashboardUiEvent.DayComplete)
                }
                is HabitActionResult.Failure -> _uiState.update {
                    val withoutOptimistic = it.habitLogs.filterNot { log -> log.habitId == habit.id && log.date == today }
                    it.copy(
                        habitLogs = if (before == null) withoutOptimistic else withoutOptimistic + before,
                        pendingItems = it.pendingItems - item,
                        actionError = DashboardActionError("Couldn't log “${current.name}”", result.message),
                    )
                }
            }
        }
    }

}

private const val TASK_ITEM = "task:"
private const val HABIT_ITEM = "habit:"

internal fun taskItemKey(taskId: String) = TASK_ITEM + taskId

internal fun habitItemKey(habitId: String) = HABIT_ITEM + habitId

private fun logKey(habitId: String, date: LocalDate) = "$habitId@$date"

private fun String.removePrefixOrNull(prefix: String): String? = if (startsWith(prefix)) removePrefix(prefix) else null

internal fun NafsTask.withCompletion(completed: Boolean, at: Instant): NafsTask = copy(
    status = if (completed) TaskCompletionStatus.COMPLETED else TaskCompletionStatus.ACTIVE,
    completedAt = if (completed) at.toString() else null,
)

private fun List<NafsTask>.replaceTask(task: NafsTask): List<NafsTask> = map { if (it.id == task.id) task else it }

/** One log per habit and day: [log] replaces whatever was there. */
internal fun List<HabitLog>.withLog(log: HabitLog): List<HabitLog> =
    filterNot { it.habitId == log.habitId && it.date == log.date } + log

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

/** Everything due today is done, and nothing is still being saved. */
fun DashboardUiState.isDayComplete(): Boolean {
    if (pendingItems.isNotEmpty()) return false
    val metrics = metrics()
    val total = metrics.totalTasks + metrics.totalHabits
    return total > 0 && metrics.completedTasks + metrics.completedHabits == total
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

/** Today's tasks in agenda order: due time (untimed last), then priority. Ticking one off doesn't move it. */
fun DashboardUiState.todayAgenda(): List<NafsTask> = todayTasks().sortedWith(
    compareBy<NafsTask, String?>(nullsLast<String>()) { it.dueTime?.take(5) }
        .thenBy { it.priority.rank }
        .thenBy { it.createdAt },
)

/** Habits scheduled today in the user's own order. */
fun DashboardUiState.todayHabits(): List<Habit> =
    scheduledHabits().sortedWith(compareBy<Habit> { it.sortOrder }.thenBy { it.name.lowercase() })

fun DashboardUiState.todayLog(habitId: String): HabitLog? =
    habitLogs.firstOrNull { it.habitId == habitId && it.date == today }

fun DashboardUiState.liveStreak(habit: Habit): Int = HabitStreak.current(
    habit.schedule(),
    habitLogs.filter { it.habitId == habit.id }.map { HabitResult(it.date, it.completed, it.value, it.durationMinutes) },
    today,
)

/**
 * Real challenge numbers for the Home tile, or null when they couldn't be loaded. A challenge that starts
 * later is active but has nothing to check in yet.
 */
fun DashboardUiState.challengeSummary(): ChallengeSummary? {
    val all = challenges ?: return null
    val active = all.filter { it.status == ChallengeStatus.ACTIVE }
    val running = active.filterNot { it.startDate.isAfter(today) }
    val checkedIn = running.count { it.checkedThisPeriod(today) }
    return ChallengeSummary(active = active.size, needCheckIn = running.size - checkedIn, checkedIn = checkedIn)
}

fun DashboardUiState.leadingGoals(limit: Int = 2): List<Goal> = goals
    .filter { it.progressPercent < 100 }
    .sortedWith(compareBy<Goal> { it.deadline == null }.thenBy { it.deadline }.thenByDescending { it.progressPercent })
    .take(limit)
