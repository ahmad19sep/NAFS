package com.ahmad.nafs.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val requestId: String? = null,
)

data class TaskUiState(
    val userId: String? = null,
    val timezone: String = "UTC",
    val today: LocalDate = LocalDate.now(ZoneOffset.UTC),
    val selectedType: TaskPeriodType = TaskPeriodType.DAILY,
    val tasks: List<NafsTask> = emptyList(),
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
)

sealed interface TaskUiEvent {
    data class Deleted(val taskId: String, val title: String) : TaskUiEvent

    data object RequestNotificationPermission : TaskUiEvent
}

@HiltViewModel
class TaskViewModel @Inject constructor(
    private val repository: TaskRepository,
    private val reminderScheduler: TaskReminderScheduler,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TaskUiState())
    val uiState: StateFlow<TaskUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<TaskUiEvent>(capacity = Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    fun initialize(userId: String, timezone: String) {
        if (_uiState.value.userId == userId && _uiState.value.timezone == timezone) return
        val today = runCatching { AccountDate.today(timezone) }
            .getOrElse { LocalDate.now(ZoneOffset.UTC) }
        _uiState.value = TaskUiState(userId = userId, timezone = timezone, today = today)
        refresh()
    }

    fun refresh() {
        val current = _uiState.value
        val userId = current.userId ?: return
        if (current.isRefreshing) return
        val today = runCatching { AccountDate.today(current.timezone) }
            .getOrElse { LocalDate.now(ZoneOffset.UTC) }
        _uiState.update {
            it.copy(
                today = today,
                isLoading = it.tasks.isEmpty(),
                isRefreshing = it.tasks.isNotEmpty(),
                loadError = null,
                actionError = null,
            )
        }
        viewModelScope.launch {
            when (val result = repository.load(userId, today)) {
                is TaskLoadResult.Success -> {
                    _uiState.update {
                        it.copy(
                            tasks = result.tasks,
                            isLoading = false,
                            isRefreshing = false,
                            isOffline = result.isOffline,
                            loadError = null,
                        )
                    }
                    reminderScheduler.reconcile(userId, result.tasks, current.timezone)
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
        _uiState.update {
            it.copy(
                showCreateDialog = true,
                editingTaskId = null,
                draft = TaskDraftState(),
                formError = null,
            )
        }
    }

    fun openEdit(task: NafsTask) {
        if (task.id in _uiState.value.pendingTaskIds) return
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

    fun save() {
        val current = _uiState.value
        val userId = current.userId ?: return
        if (current.isSaving) return
        val titleError = TaskDraftValidator.titleError(current.draft.title)
        val dueTimeError = TaskDraftValidator.dueTimeError(
            current.selectedType,
            current.draft.dueTime,
        )
        val validationError = titleError ?: dueTimeError
        if (validationError != null) {
            _uiState.update { it.copy(formError = validationError) }
            return
        }

        val requestId = if (current.editingTaskId == null) {
            current.draft.requestId ?: UUID.randomUUID().toString()
        } else {
            null
        }
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
                    today = current.today,
                ),
            )
            when (result) {
                is TaskActionResult.Success -> {
                    val task = result.task
                    val tasks = if (task == null) _uiState.value.tasks else _uiState.value.tasks.upsert(task)
                    _uiState.update {
                        it.copy(
                            tasks = tasks,
                            isSaving = false,
                            isOffline = false,
                            showCreateDialog = false,
                            editingTaskId = null,
                            draft = TaskDraftState(),
                        )
                    }
                    reminderScheduler.reconcile(userId, tasks, current.timezone)
                    if (task?.type == TaskPeriodType.DAILY && task.dueTime != null) {
                        eventChannel.send(TaskUiEvent.RequestNotificationPermission)
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
                    val tasks = if (updated == null) {
                        _uiState.value.tasks
                    } else {
                        _uiState.value.tasks.upsert(updated)
                    }
                    _uiState.update {
                        it.copy(
                            tasks = tasks,
                            pendingTaskIds = it.pendingTaskIds - task.id,
                            isOffline = false,
                        )
                    }
                    reminderScheduler.reconcile(userId, tasks, _uiState.value.timezone)
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
                    val tasks = if (restored == null) {
                        _uiState.value.tasks
                    } else {
                        _uiState.value.tasks.upsert(restored)
                    }
                    _uiState.update {
                        it.copy(
                            tasks = tasks,
                            pendingTaskIds = it.pendingTaskIds - taskId,
                            isOffline = false,
                        )
                    }
                    reminderScheduler.reconcile(userId, tasks, _uiState.value.timezone)
                }
                is TaskActionResult.Failure -> actionFailed(taskId, result.message)
            }
        }
    }

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

fun visibleTasksFor(state: TaskUiState): List<NafsTask> {
    val currentKey = TaskPeriod.key(state.selectedType, state.today)
    return state.tasks
        .asSequence()
        .filter { it.type == state.selectedType }
        .filter { TaskPeriod.isCurrentOrGrace(it.type, it.periodDate, state.today) }
        .sortedWith(
            compareBy<NafsTask> { if (it.periodDate == currentKey) 0 else 1 }
                .thenBy { it.priority.rank }
                .thenBy { if (it.isCompleted) 1 else 0 }
                .thenByDescending { it.createdAt },
        )
        .toList()
}

private fun List<NafsTask>.upsert(task: NafsTask): List<NafsTask> =
    filterNot { it.id == task.id } + task
