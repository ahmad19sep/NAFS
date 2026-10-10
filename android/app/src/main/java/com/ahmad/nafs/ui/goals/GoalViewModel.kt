package com.ahmad.nafs.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmad.nafs.core.goals.Goal
import com.ahmad.nafs.core.goals.GoalActionResult
import com.ahmad.nafs.core.goals.GoalDefinitionValidator
import com.ahmad.nafs.core.goals.GoalLoadResult
import com.ahmad.nafs.core.goals.GoalRepository
import com.ahmad.nafs.core.habits.Habit
import com.ahmad.nafs.core.habits.HabitRepository
import com.ahmad.nafs.core.habits.HabitLoadResult
import com.ahmad.nafs.core.goals.SaveGoalIntent
import com.ahmad.nafs.core.time.AccountDate
import com.ahmad.nafs.domain.goals.GoalCategory
import com.ahmad.nafs.domain.goals.GoalDates
import com.ahmad.nafs.domain.goals.GoalType
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GoalEditorDraft(
    val goalId: String? = null,
    val requestId: String? = null,
    val title: String = "",
    val emoji: String = "🎯",
    val description: String = "",
    val type: GoalType = GoalType.MONTHLY,
    val category: GoalCategory? = null,
    val deadline: String = "",
    val hasNumericTarget: Boolean = false,
    val targetValue: String = "",
    val currentValue: String = "0",
    val unit: String = "",
    val milestones: List<String> = listOf(""),
    val linkedHabitIds: List<String> = emptyList(),
)

data class GoalUiState(
    val userId: String? = null,
    val timezone: String = "UTC",
    val today: LocalDate = LocalDate.now(ZoneOffset.UTC),
    val goals: List<Goal> = emptyList(),
    val habits: List<Habit> = emptyList(),
    val selectedType: GoalType? = null,
    val expandedGoalIds: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isOffline: Boolean = false,
    val loadError: String? = null,
    val actionError: String? = null,
    val editor: GoalEditorDraft? = null,
    val editorError: String? = null,
    val isSaving: Boolean = false,
    val pendingGoalIds: Set<String> = emptySet(),
    val pendingMilestoneIds: Set<String> = emptySet(),
    val progressGoalId: String? = null,
    val progressAmount: String = "",
    val progressError: String? = null,
    val deleteCandidate: Goal? = null,
)

@HiltViewModel
class GoalViewModel @Inject constructor(
    private val repository: GoalRepository,
    private val habitRepository: HabitRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GoalUiState())
    val uiState: StateFlow<GoalUiState> = _uiState.asStateFlow()

    fun initialize(userId: String, timezone: String) {
        if (_uiState.value.userId == userId && _uiState.value.timezone == timezone) return
        val today = runCatching { AccountDate.today(timezone) }
            .getOrElse { LocalDate.now(ZoneOffset.UTC) }
        _uiState.value = GoalUiState(userId = userId, timezone = timezone, today = today)
        refresh()
    }

    fun refresh() {
        val state = _uiState.value
        val userId = state.userId ?: return
        if (state.isRefreshing) return
        val today = runCatching { AccountDate.today(state.timezone) }.getOrElse { state.today }
        _uiState.update {
            it.copy(
                today = today,
                isLoading = it.goals.isEmpty(),
                isRefreshing = it.goals.isNotEmpty(),
                loadError = null,
                actionError = null,
            )
        }
        viewModelScope.launch {
            val habits = habitRepository.load(userId, today) as? HabitLoadResult.Success
            if (_uiState.value.userId != userId) return@launch
            _uiState.update { it.copy(habits = habits?.habits ?: it.habits) }
            when (val result = repository.load(userId)) {
                is GoalLoadResult.Success -> _uiState.update {
                    it.copy(
                        goals = result.goals.sortedByDescending(Goal::createdAt),
                        isLoading = false,
                        isRefreshing = false,
                        isOffline = result.isOffline,
                    )
                }
                is GoalLoadResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, loadError = result.message)
                }
            }
        }
    }

    fun selectType(type: GoalType?) = _uiState.update { it.copy(selectedType = type) }

    fun toggleExpanded(goalId: String) = _uiState.update {
        it.copy(
            expandedGoalIds = if (goalId in it.expandedGoalIds) {
                it.expandedGoalIds - goalId
            } else {
                it.expandedGoalIds + goalId
            },
        )
    }

    fun openCreate() {
        val today = _uiState.value.today
        _uiState.update {
            it.copy(
                editor = GoalEditorDraft(
                    deadline = GoalDates.defaultDeadline(GoalType.MONTHLY, today).toString(),
                ),
                editorError = null,
            )
        }
    }

    fun openEdit(goal: Goal) {
        if (goal.id in _uiState.value.pendingGoalIds) return
        _uiState.update {
            it.copy(
                editor = GoalEditorDraft(
                    goalId = goal.id,
                    requestId = goal.id,
                    title = goal.title,
                    emoji = goal.emoji,
                    description = goal.description.orEmpty(),
                    type = goal.type,
                    category = goal.category,
                    deadline = goal.deadline?.toString().orEmpty(),
                    hasNumericTarget = goal.targetValue != null,
                    targetValue = goal.targetValue?.let(::formatNumber).orEmpty(),
                    currentValue = goal.currentValue?.let(::formatNumber) ?: "0",
                    unit = goal.unit.orEmpty(),
                    milestones = emptyList(),
                    linkedHabitIds = goal.linkedHabitIds,
                ),
                editorError = null,
            )
        }
    }

    fun closeEditor() {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(editor = null, editorError = null) }
    }

    fun setTitle(value: String) = updateEditor { copy(title = value) }
    fun setEmoji(value: String) = updateEditor { copy(emoji = value) }
    fun setDescription(value: String) = updateEditor { copy(description = value) }
    fun setCategory(value: GoalCategory?) = updateEditor { copy(category = value) }
    fun setDeadline(value: String) = updateEditor { copy(deadline = value) }
    fun setTargetValue(value: String) = updateEditor { copy(targetValue = value) }
    fun setCurrentValue(value: String) = updateEditor { copy(currentValue = value) }
    fun setUnit(value: String) = updateEditor { copy(unit = value) }

    fun setType(value: GoalType) = updateEditor {
        val startDate = goalId?.let { id ->
            _uiState.value.goals.firstOrNull { it.id == id }?.startDate
        } ?: _uiState.value.today
        copy(
            type = value,
            deadline = GoalDates.defaultDeadline(value, startDate).toString(),
        )
    }

    fun setHasNumericTarget(value: Boolean) = updateEditor {
        copy(
            hasNumericTarget = value,
            targetValue = if (value) targetValue else "",
            currentValue = if (value) currentValue.ifEmpty { "0" } else "0",
            unit = if (value) unit else "",
        )
    }

    fun setMilestone(index: Int, value: String) = updateEditor {
        copy(milestones = milestones.mapIndexed { itemIndex, item -> if (itemIndex == index) value else item })
    }

    fun addMilestone() = updateEditor {
        if (milestones.size >= 20) this else copy(milestones = milestones + "")
    }

    fun toggleLinkedHabit(id: String) = updateEditor {
        copy(linkedHabitIds = if (id in linkedHabitIds) linkedHabitIds - id else linkedHabitIds + id)
    }

    fun removeMilestone(index: Int) = updateEditor {
        copy(milestones = milestones.filterIndexed { itemIndex, _ -> itemIndex != index }.ifEmpty { listOf("") })
    }

    fun save() {
        val state = _uiState.value
        val userId = state.userId ?: return
        val draft = state.editor ?: return
        if (state.isSaving) return

        val deadline = when {
            draft.deadline.isBlank() -> null
            else -> runCatching { LocalDate.parse(draft.deadline.trim()) }.getOrNull()
                ?: return editorFailed("Use deadline format YYYY-MM-DD.")
        }
        val target = if (draft.hasNumericTarget) {
            draft.targetValue.toDoubleOrNull()
                ?: return editorFailed("Enter a valid numeric target.")
        } else {
            null
        }
        val current = if (draft.hasNumericTarget) {
            draft.currentValue.toDoubleOrNull()
                ?: return editorFailed("Enter valid current progress.")
        } else {
            null
        }
        val requestId = draft.goalId ?: draft.requestId ?: UUID.randomUUID().toString()
        val startDate = draft.goalId?.let { id ->
            state.goals.firstOrNull { it.id == id }?.startDate
        } ?: state.today
        val intent = SaveGoalIntent(
            requestId = requestId,
            userId = userId,
            goalId = draft.goalId,
            title = draft.title,
            emoji = draft.emoji,
            description = draft.description,
            type = draft.type,
            category = draft.category,
            startDate = startDate,
            deadline = deadline,
            targetValue = target,
            currentValue = current,
            unit = draft.unit,
            milestoneTitles = if (draft.goalId == null) draft.milestones else emptyList(),
            linkedHabitIds = draft.linkedHabitIds,
        )
        GoalDefinitionValidator.validationError(intent)?.let { return editorFailed(it) }

        _uiState.update {
            it.copy(
                isSaving = true,
                editorError = null,
                editor = it.editor?.copy(requestId = requestId),
            )
        }
        viewModelScope.launch {
            val result = if (draft.goalId == null) repository.create(intent) else repository.update(intent)
            when (result) {
                is GoalActionResult.Success -> {
                    val goal = result.goal
                    _uiState.update {
                        it.copy(
                            goals = goal?.let(it.goals::upsert)?.sortedByDescending(Goal::createdAt)
                                ?: it.goals,
                            isSaving = false,
                            isOffline = false,
                            editor = null,
                            editorError = null,
                            expandedGoalIds = goal?.let { saved -> it.expandedGoalIds + saved.id }
                                ?: it.expandedGoalIds,
                        )
                    }
                }
                is GoalActionResult.Failure -> _uiState.update {
                    it.copy(isSaving = false, editorError = result.message)
                }
            }
        }
    }

    fun setMilestoneDone(goal: Goal, milestoneId: String, done: Boolean) {
        val userId = _uiState.value.userId ?: return
        if (milestoneId in _uiState.value.pendingMilestoneIds) return
        _uiState.update {
            it.copy(pendingMilestoneIds = it.pendingMilestoneIds + milestoneId, actionError = null)
        }
        viewModelScope.launch {
            when (val result = repository.setMilestoneDone(userId, goal.id, milestoneId, done)) {
                is GoalActionResult.Success -> mutationSucceeded(goal.id, milestoneId, result.goal)
                is GoalActionResult.Failure -> mutationFailed(goal.id, milestoneId, result.message)
            }
        }
    }

    fun openProgress(goal: Goal) {
        if (goal.targetValue == null || goal.id in _uiState.value.pendingGoalIds) return
        _uiState.update {
            it.copy(
                progressGoalId = goal.id,
                progressAmount = formatNumber(goal.currentValue ?: 0.0),
                progressError = null,
            )
        }
    }

    fun setProgressAmount(value: String) = _uiState.update {
        it.copy(progressAmount = value, progressError = null)
    }

    fun closeProgress() {
        val goalId = _uiState.value.progressGoalId
        if (goalId != null && goalId in _uiState.value.pendingGoalIds) return
        _uiState.update { it.copy(progressGoalId = null, progressAmount = "", progressError = null) }
    }

    fun saveProgress() {
        val state = _uiState.value
        val userId = state.userId ?: return
        val goalId = state.progressGoalId ?: return
        if (goalId in state.pendingGoalIds) return
        val value = state.progressAmount.toDoubleOrNull()
        if (value == null || !value.isFinite() || value < 0.0) {
            _uiState.update { it.copy(progressError = "Enter progress of zero or more.") }
            return
        }
        _uiState.update { it.copy(pendingGoalIds = it.pendingGoalIds + goalId, progressError = null) }
        viewModelScope.launch {
            when (val result = repository.setCurrentValue(userId, goalId, value)) {
                is GoalActionResult.Success -> _uiState.update {
                    it.copy(
                        goals = result.goal?.let(it.goals::upsert) ?: it.goals,
                        pendingGoalIds = it.pendingGoalIds - goalId,
                        progressGoalId = null,
                        progressAmount = "",
                        isOffline = false,
                    )
                }
                is GoalActionResult.Failure -> _uiState.update {
                    it.copy(pendingGoalIds = it.pendingGoalIds - goalId, progressError = result.message)
                }
            }
        }
    }

    fun requestDelete(goal: Goal) {
        if (goal.id in _uiState.value.pendingGoalIds) return
        _uiState.update { it.copy(deleteCandidate = goal) }
    }

    fun cancelDelete() = _uiState.update { it.copy(deleteCandidate = null) }

    fun confirmDelete() {
        val state = _uiState.value
        val userId = state.userId ?: return
        val goal = state.deleteCandidate ?: return
        if (goal.id in state.pendingGoalIds) return
        _uiState.update {
            it.copy(
                pendingGoalIds = it.pendingGoalIds + goal.id,
                deleteCandidate = null,
                actionError = null,
            )
        }
        viewModelScope.launch {
            when (val result = repository.delete(userId, goal.id)) {
                is GoalActionResult.Success -> _uiState.update {
                    it.copy(
                        goals = it.goals.filterNot { item -> item.id == goal.id },
                        pendingGoalIds = it.pendingGoalIds - goal.id,
                        expandedGoalIds = it.expandedGoalIds - goal.id,
                        isOffline = false,
                    )
                }
                is GoalActionResult.Failure -> mutationFailed(goal.id, null, result.message)
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(actionError = null, loadError = null) }

    private fun updateEditor(transform: GoalEditorDraft.() -> GoalEditorDraft) {
        _uiState.update {
            val editor = it.editor ?: return@update it
            val updated = editor.transform()
            it.copy(
                editor = updated.copy(
                    requestId = if (updated.goalId == null) null else updated.goalId,
                ),
                editorError = null,
            )
        }
    }

    private fun editorFailed(message: String) {
        _uiState.update { it.copy(editorError = message) }
    }

    private fun mutationSucceeded(goalId: String, milestoneId: String?, goal: Goal?) {
        _uiState.update {
            it.copy(
                goals = goal?.let(it.goals::upsert) ?: it.goals,
                pendingGoalIds = it.pendingGoalIds - goalId,
                pendingMilestoneIds = milestoneId?.let(it.pendingMilestoneIds::minus)
                    ?: it.pendingMilestoneIds,
                isOffline = false,
            )
        }
    }

    private fun mutationFailed(goalId: String, milestoneId: String?, message: String) {
        _uiState.update {
            it.copy(
                pendingGoalIds = it.pendingGoalIds - goalId,
                pendingMilestoneIds = milestoneId?.let(it.pendingMilestoneIds::minus)
                    ?: it.pendingMilestoneIds,
                actionError = message,
            )
        }
    }
}

fun GoalUiState.visibleGoals(): List<Goal> = selectedType?.let { type ->
    goals.filter { it.type == type }
} ?: goals

private fun List<Goal>.upsert(goal: Goal): List<Goal> = filterNot { it.id == goal.id } + goal

private fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
