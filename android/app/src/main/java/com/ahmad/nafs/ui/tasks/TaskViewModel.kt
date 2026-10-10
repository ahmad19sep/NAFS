package com.ahmad.nafs.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmad.nafs.core.reminders.ReminderPreferences
import com.ahmad.nafs.core.reminders.TaskReminderScheduler
import com.ahmad.nafs.core.tasks.CreateTaskIntent
import com.ahmad.nafs.core.tasks.EditTaskIntent
import com.ahmad.nafs.core.tasks.NafsTask
import com.ahmad.nafs.core.tasks.TaskActionResult
import com.ahmad.nafs.core.tasks.TaskLoadResult
import com.ahmad.nafs.core.tasks.TaskPriority
import com.ahmad.nafs.core.tasks.TaskRepository
import com.ahmad.nafs.core.time.AccountDate
import com.ahmad.nafs.domain.tasks.TaskDraftValidator
import com.ahmad.nafs.domain.tasks.TaskPeriod
import com.ahmad.nafs.domain.tasks.TaskPeriodType
import com.ahmad.nafs.domain.tasks.TaskReminderPlanner
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TaskDraftState(
    val title: String = "",
    val note: String = "",
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val dueTime: String = "",
    /** A later day for a new daily task; null keeps it in the current period (today). */
    val plannedDate: LocalDate? = null,
    val requestId: String? = null,
)

data class TaskUiState(
    val userId: String? = null,
    val timezone: String = "UTC",
    val today: LocalDate = LocalDate.now(ZoneOffset.UTC),
    val selectedType: TaskPeriodType = TaskPeriodType.DAILY,
    val tasks: List<NafsTask> = emptyList(),
    /** A load has succeeded at least once, so a later one is a refresh rather than a first load. */
    val hasLoaded: Boolean = false,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isOffline: Boolean = false,
    val loadError: String? = null,
    val actionError: String? = null,
    val showCreateDialog: Boolean = false,
    val editingTaskId: String? = null,
    val draft: TaskDraftState = TaskDraftState(),
    val isSaving: Boolean = false,
    val formError: String? = null,
    val pendingTaskIds: Set<String> = emptySet(),
    /** How long before a due time this device reminds; null when task reminders are switched off. */
    val reminderLeadMinutes: Int? = TaskReminderPlanner.DEFAULT_LEAD_MINUTES,
    val isSearching: Boolean = false,
    val searchQuery: String = "",
)

sealed interface TaskUiEvent {
    data class Deleted(val taskId: String, val title: String) : TaskUiEvent

    data object RequestNotificationPermission : TaskUiEvent

    /** A short confirmation of where a planned or moved task went. */
    data class Notice(val message: String) : TaskUiEvent
}

private data class TaskLoadKey(val userId: String, val timezone: String, val today: LocalDate)

@HiltViewModel
class TaskViewModel @Inject constructor(
    private val repository: TaskRepository,
    private val reminderScheduler: TaskReminderScheduler,
    private val reminderPreferences: ReminderPreferences,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TaskUiState())
    val uiState: StateFlow<TaskUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<TaskUiEvent>(capacity = Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private val loads = LoadGate<TaskLoadKey>()

    /** Tasks changed here while a load was in flight: the load's older copy must not overwrite them. */
    private val changedDuringLoad = mutableSetOf<String>()

    fun initialize(userId: String, timezone: String) {
        if (_uiState.value.userId == userId && _uiState.value.timezone == timezone) return
        _uiState.value = TaskUiState(userId = userId, timezone = timezone, today = accountToday(timezone))
        refresh()
    }

    fun refresh() {
        val current = _uiState.value
        val userId = current.userId ?: return
        val timezone = current.timezone
        val today = accountToday(timezone)
        val key = TaskLoadKey(userId, timezone, today)
        // Opening the screen asks twice (initialize, then the resume effect); one load answers both.
        if (loads.isRunning(key)) return
        changedDuringLoad.clear()
        _uiState.update {
            it.copy(
                today = today,
                isLoading = !it.hasLoaded,
                isRefreshing = it.hasLoaded,
                loadError = null,
                actionError = null,
            )
        }
        loads.launch(viewModelScope, key) {
            val result = repository.load(userId, today)
            if (_uiState.value.userId != userId) return@launch
            when (result) {
                is TaskLoadResult.Success -> {
                    _uiState.update {
                        it.copy(
                            tasks = mergeLoaded(
                                loaded = result.tasks,
                                local = it.tasks,
                                keepLocal = changedDuringLoad + it.pendingTaskIds,
                                id = NafsTask::id,
                            ),
                            hasLoaded = true,
                            isLoading = false,
                            isRefreshing = false,
                            isOffline = result.isOffline,
                            loadError = null,
                        )
                    }
                    // Every loaded task goes to the scheduler, so days planned ahead keep their reminders.
                    reminderScheduler.reconcile(userId, _uiState.value.tasks, timezone)
                }
                is TaskLoadResult.Failure -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        loadError = result.message,
                    )
                }
            }
        }
    }

    fun selectType(type: TaskPeriodType) {
        _uiState.update {
            it.copy(
                selectedType = type,
                showCreateDialog = false,
                editingTaskId = null,
                draft = TaskDraftState(),
                formError = null,
            )
        }
    }

    fun openCreate() {
        val lead = reminderLead()
        _uiState.update {
            it.copy(
                showCreateDialog = true,
                editingTaskId = null,
                draft = TaskDraftState(),
                formError = null,
                reminderLeadMinutes = lead,
            )
        }
    }

    fun openEdit(task: NafsTask) {
        if (task.id in _uiState.value.pendingTaskIds) return
        val lead = reminderLead()
        _uiState.update {
            it.copy(
                selectedType = task.type,
                showCreateDialog = true,
                editingTaskId = task.id,
                draft = TaskDraftState(
                    title = task.title,
                    note = task.note.orEmpty(),
                    priority = task.priority,
                    dueTime = task.dueTime.orEmpty(),
                ),
                formError = null,
                reminderLeadMinutes = lead,
            )
        }
    }

    fun closeCreate() {
        if (_uiState.value.isSaving) return
        _uiState.update {
            it.copy(
                showCreateDialog = false,
                editingTaskId = null,
                draft = TaskDraftState(),
                formError = null,
            )
        }
    }

    fun setTitle(value: String) = updateDraft { copy(title = value, requestId = null) }

    fun setNote(value: String) = updateDraft { copy(note = value, requestId = null) }

    fun setPriority(value: TaskPriority) = updateDraft { copy(priority = value, requestId = null) }

    fun setDueTime(value: String) = updateDraft { copy(dueTime = value, requestId = null) }

    fun setPlannedDate(value: LocalDate?) = updateDraft { copy(plannedDate = value, requestId = null) }

    fun save() {
        val current = _uiState.value
        val userId = current.userId ?: return
        if (current.isSaving) return
        val isNew = current.editingTaskId == null
        // The account's day at the moment of saving, so "today" is right even if the sheet stayed open overnight.
        val today = accountToday(current.timezone)
        val plannedDate = current.draft.plannedDate
            ?.takeIf { isNew && current.selectedType == TaskPeriodType.DAILY && it != today }
        val validationError = TaskDraftValidator.titleError(current.draft.title)
            ?: TaskDraftValidator.dueTimeError(current.selectedType, current.draft.dueTime)
            ?: TaskDraftValidator.plannedDateError(current.selectedType, plannedDate, today)
        if (validationError != null) {
            _uiState.update { it.copy(formError = validationError) }
            return
        }

        val requestId = if (isNew) current.draft.requestId ?: UUID.randomUUID().toString() else null
        _uiState.update {
            it.copy(
                isSaving = true,
                formError = null,
                draft = it.draft.copy(requestId = requestId),
            )
        }
        viewModelScope.launch {
            val result = current.editingTaskId?.let { taskId ->
                repository.update(
                    EditTaskIntent(
                        userId = userId,
                        taskId = taskId,
                        title = current.draft.title,
                        note = current.draft.note,
                        type = current.selectedType,
                        priority = current.draft.priority,
                        dueTime = current.draft.dueTime,
                    ),
                )
            } ?: repository.create(
                CreateTaskIntent(
                    requestId = checkNotNull(requestId),
                    userId = userId,
                    title = current.draft.title,
                    note = current.draft.note,
                    type = current.selectedType,
                    priority = current.draft.priority,
                    dueTime = current.draft.dueTime,
                    today = today,
                    plannedDate = plannedDate,
                ),
            )
            when (result) {
                is TaskActionResult.Success -> {
                    val task = result.task
                    task?.let { changedDuringLoad += it.id }
                    _uiState.update {
                        it.copy(
                            tasks = if (task == null) it.tasks else it.tasks.upsert(task),
                            isSaving = false,
                            isOffline = false,
                            showCreateDialog = false,
                            editingTaskId = null,
                            draft = TaskDraftState(),
                        )
                    }
                    reminderScheduler.reconcile(userId, _uiState.value.tasks, current.timezone)
                    if (task?.type == TaskPeriodType.DAILY && task.dueTime != null) {
                        eventChannel.send(TaskUiEvent.RequestNotificationPermission)
                    }
                    if (isNew && task != null && TaskPeriod.isUpcoming(task.type, task.periodDate, today)) {
                        val day = TaskLabels.periodPhrase(task.type, task.periodDate, today)
                        eventChannel.send(TaskUiEvent.Notice("Planned for $day. It's listed under Upcoming."))
                    }
                }
                is TaskActionResult.Failure -> _uiState.update {
                    it.copy(isSaving = false, formError = result.message)
                }
            }
        }
    }

    fun setCompleted(task: NafsTask, completed: Boolean) {
        val userId = _uiState.value.userId ?: return
        if (task.id in _uiState.value.pendingTaskIds || task.isCompleted == completed) return
        setPending(task.id, pending = true)
        viewModelScope.launch {
            when (val result = repository.setCompleted(userId, task.id, completed)) {
                is TaskActionResult.Success -> {
                    val updated = result.task
                    changedDuringLoad += task.id
                    _uiState.update {
                        it.copy(
                            tasks = if (updated == null) it.tasks else it.tasks.upsert(updated),
                            pendingTaskIds = it.pendingTaskIds - task.id,
                            isOffline = false,
                        )
                    }
                    reminderScheduler.reconcile(userId, _uiState.value.tasks, _uiState.value.timezone)
                }
                is TaskActionResult.Failure -> actionFailed(task.id, result.message)
            }
        }
    }

    /** Moves an open task to the period containing [periodDate], for example a missed task to today. */
    fun moveTask(task: NafsTask, periodDate: LocalDate) {
        val state = _uiState.value
        val userId = state.userId ?: return
        if (task.id in state.pendingTaskIds || task.isCompleted) return
        setPending(task.id, pending = true)
        viewModelScope.launch {
            when (val result = repository.reschedule(userId, task.id, periodDate)) {
                is TaskActionResult.Success -> {
                    val moved = result.task
                    changedDuringLoad += task.id
                    _uiState.update {
                        it.copy(
                            tasks = if (moved == null) it.tasks else it.tasks.upsert(moved),
                            pendingTaskIds = it.pendingTaskIds - task.id,
                            isOffline = false,
                        )
                    }
                    // The due time now falls on another day, so its reminder moves with it.
                    reminderScheduler.reconcile(userId, _uiState.value.tasks, _uiState.value.timezone)
                    if (moved != null && moved.periodDate != task.periodDate) {
                        val where = TaskLabels.periodPhrase(moved.type, moved.periodDate, _uiState.value.today)
                        eventChannel.send(TaskUiEvent.Notice("Moved “${moved.title}” to $where."))
                    }
                }
                is TaskActionResult.Failure -> actionFailed(task.id, result.message)
            }
        }
    }

    fun delete(task: NafsTask) {
        val userId = _uiState.value.userId ?: return
        if (task.id in _uiState.value.pendingTaskIds) return
        setPending(task.id, pending = true)
        viewModelScope.launch {
            when (val result = repository.softDelete(userId, task.id)) {
                is TaskActionResult.Success -> {
                    changedDuringLoad += task.id
                    _uiState.update {
                        it.copy(
                            tasks = it.tasks.filterNot { item -> item.id == task.id },
                            pendingTaskIds = it.pendingTaskIds - task.id,
                            isOffline = false,
                        )
                    }
                    reminderScheduler.cancelTask(task.id)
                    eventChannel.send(TaskUiEvent.Deleted(task.id, task.title))
                }
                is TaskActionResult.Failure -> actionFailed(task.id, result.message)
            }
        }
    }

    fun restore(taskId: String) {
        val userId = _uiState.value.userId ?: return
        setPending(taskId, pending = true)
        viewModelScope.launch {
            when (val result = repository.restore(userId, taskId)) {
                is TaskActionResult.Success -> {
                    val restored = result.task
                    changedDuringLoad += taskId
                    _uiState.update {
                        it.copy(
                            tasks = if (restored == null) it.tasks else it.tasks.upsert(restored),
                            pendingTaskIds = it.pendingTaskIds - taskId,
                            isOffline = false,
                        )
                    }
                    reminderScheduler.reconcile(userId, _uiState.value.tasks, _uiState.value.timezone)
                }
                is TaskActionResult.Failure -> actionFailed(taskId, result.message)
            }
        }
    }

    fun openSearch() = _uiState.update { it.copy(isSearching = true) }

    fun setSearchQuery(value: String) = _uiState.update { it.copy(searchQuery = value) }

    fun closeSearch() = _uiState.update { it.copy(isSearching = false, searchQuery = "") }

    fun dismissActionError() {
        _uiState.update { it.copy(actionError = null) }
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        if (!granted) {
            _uiState.update {
                it.copy(
                    actionError = "Task saved, but notifications are disabled. You can enable them in Android settings.",
                )
            }
        }
    }

    /** The device's task reminder setting, read when the editor opens so a change in Profile shows at once. */
    private fun reminderLead(): Int? {
        val userId = _uiState.value.userId ?: return TaskReminderPlanner.DEFAULT_LEAD_MINUTES
        val options = reminderPreferences.read(userId)
        return options.taskLeadMinutes.takeIf { options.tasks }
    }

    private fun updateDraft(transform: TaskDraftState.() -> TaskDraftState) {
        _uiState.update { it.copy(draft = it.draft.transform(), formError = null) }
    }

    private fun setPending(taskId: String, pending: Boolean) {
        _uiState.update {
            it.copy(
                pendingTaskIds = if (pending) {
                    it.pendingTaskIds + taskId
                } else {
                    it.pendingTaskIds - taskId
                },
                actionError = null,
            )
        }
    }

    private fun actionFailed(taskId: String, message: String) {
        _uiState.update {
            it.copy(pendingTaskIds = it.pendingTaskIds - taskId, actionError = message)
        }
    }
}

/** Current and grace-period tasks of the selected type: the current period first, open before done. */
fun visibleTasksFor(state: TaskUiState): List<NafsTask> {
    val currentKey = TaskPeriod.key(state.selectedType, state.today)
    return state.tasks
        .asSequence()
        .filter { it.type == state.selectedType }
        .filter { TaskPeriod.isCurrentOrGrace(it.type, it.periodDate, state.today) }
        .sortedWith(compareBy<NafsTask> { if (it.periodDate == currentKey) 0 else 1 }.then(taskListOrder))
        .toList()
}

private fun accountToday(timezone: String): LocalDate =
    runCatching { AccountDate.today(timezone) }.getOrElse { LocalDate.now(ZoneOffset.UTC) }

private fun List<NafsTask>.upsert(task: NafsTask): List<NafsTask> =
    filterNot { it.id == task.id } + task
