package com.ahmad.nafs.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmad.nafs.core.habits.Habit
import com.ahmad.nafs.core.habits.HabitActionResult
import com.ahmad.nafs.core.habits.HabitDefinitionActionResult
import com.ahmad.nafs.core.habits.HabitDefinitionValidator
import com.ahmad.nafs.core.habits.HabitLoadResult
import com.ahmad.nafs.core.habits.HabitLog
import com.ahmad.nafs.core.habits.HabitRepository
import com.ahmad.nafs.core.habits.LogHabitIntent
import com.ahmad.nafs.core.habits.SaveHabitIntent
import com.ahmad.nafs.core.time.AccountDate
import com.ahmad.nafs.domain.habits.HabitResult
import com.ahmad.nafs.domain.habits.HabitStreak
import com.ahmad.nafs.domain.habits.HabitType
import com.ahmad.nafs.domain.habits.ScheduleKind
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HabitDefinitionDraftState(
    val habitId: String? = null,
    val requestId: String? = null,
    val name: String = "",
    val emoji: String = "⭐",
    val type: HabitType = HabitType.SIMPLE,
    val targetValue: String = "1",
    val unit: String = "times",
    val timeTargetMinutes: String = "30",
    val subjectName: String = "",
    val subjectTotal: String = "100",
    val subjectPosition: String = "0",
    val subjectUnit: String = "pages",
    val scheduleKind: ScheduleKind = ScheduleKind.DAILY,
    val scheduleDays: Set<DayOfWeek> = emptySet(),
    val weeklyTarget: String = "3",
)

data class HabitUiState(
    val userId: String? = null,
    val timezone: String = "UTC",
    val today: LocalDate = LocalDate.now(ZoneOffset.UTC),
    val habits: List<Habit> = emptyList(),
    val logs: List<HabitLog> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isOffline: Boolean = false,
    val error: String? = null,
    val actionError: String? = null,
    val editingHabitId: String? = null,
    val editingDate: LocalDate? = null,
    val historyHabitId: String? = null,
    val amount: String = "",
    val notes: String = "",
    val isSaving: Boolean = false,
    val formError: String? = null,
    val definitionDraft: HabitDefinitionDraftState? = null,
    val isSavingDefinition: Boolean = false,
    val definitionError: String? = null,
    val pendingHabitIds: Set<String> = emptySet(),
)

@HiltViewModel
class HabitViewModel @Inject constructor(
    private val repository: HabitRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HabitUiState())
    val uiState: StateFlow<HabitUiState> = _uiState.asStateFlow()

    fun initialize(userId: String, timezone: String) {
        if (_uiState.value.userId == userId && _uiState.value.timezone == timezone) return
        val today = runCatching { AccountDate.today(timezone) }
            .getOrElse { LocalDate.now(ZoneOffset.UTC) }
        _uiState.value = HabitUiState(userId = userId, timezone = timezone, today = today)
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
                isLoading = it.habits.isEmpty(),
                isRefreshing = it.habits.isNotEmpty(),
                error = null,
                actionError = null,
            )
        }
        viewModelScope.launch {
            when (val result = repository.load(userId, today)) {
                is HabitLoadResult.Success -> _uiState.update {
                    it.copy(
                        habits = result.habits.sortedForDisplay(),
                        logs = result.logs,
                        isLoading = false,
                        isRefreshing = false,
                        isOffline = result.isOffline,
                    )
                }
                is HabitLoadResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, error = result.message)
                }
            }
        }
    }

    fun toggleSimple(habit: Habit) {
        val existing = todayLog(habit.id)
        save(
            habit,
            value = if (existing?.completed == true) 0.0 else 1.0,
            completed = existing?.completed != true,
        )
    }

    fun openHistory(habit: Habit) { _uiState.update { it.copy(historyHabitId = habit.id) } }
    fun closeHistory() { _uiState.update { it.copy(historyHabitId = null) } }
    fun openLog(habit: Habit) = openDatedLog(habit, _uiState.value.today)
    fun openDatedLog(habit: Habit, date: LocalDate) {
        if (date > _uiState.value.today || date < _uiState.value.today.minusDays(399)) return
        if (date == _uiState.value.today && (habit.isPaused || !HabitStreak.isScheduled(habit.schedule(), date))) return
        val existing = _uiState.value.logs.find { it.habitId == habit.id && it.date == date }
        val amount = when (habit.type) {
            HabitType.DURATION -> existing?.durationMinutes?.takeIf { it > 0 }?.toString()
            else -> existing?.value?.takeIf { it > 0.0 }?.let(::formatNumber)
        }.orEmpty()
        _uiState.update {
            it.copy(
                editingHabitId = habit.id,
                editingDate = date,
                historyHabitId = null,
                amount = amount,
                notes = existing?.notes.orEmpty(),
                formError = null,
            )
        }
    }

    fun closeLog() {
        if (_uiState.value.isSaving) return
        _uiState.update {
            it.copy(editingHabitId = null, amount = "", notes = "", formError = null)
        }
    }

    fun setAmount(value: String) = _uiState.update { it.copy(amount = value, formError = null) }

    fun setNotes(value: String) = _uiState.update { it.copy(notes = value, formError = null) }

    fun saveEditor() {
        val state = _uiState.value
        val habit = state.habits.firstOrNull { it.id == state.editingHabitId } ?: return
        val amount = state.amount.toDoubleOrNull()
        if (amount == null || amount < 0.0 || !amount.isFinite()) {
            _uiState.update { it.copy(formError = "Enter a valid amount of zero or more.") }
            return
        }
        if (habit.type == HabitType.SUBJECT && amount % 1.0 != 0.0) {
            _uiState.update { it.copy(formError = "Subject progress must be a whole number.") }
            return
        }
        if (habit.type == HabitType.DURATION) {
            save(habit, durationMinutes = amount.toInt(), notes = state.notes)
        } else {
            save(habit, value = amount, notes = state.notes, completed = habit.type == HabitType.SIMPLE && amount >= 1.0)
        }
    }

    fun openCreateDefinition() {
        if (_uiState.value.isSavingDefinition) return
        _uiState.update {
            it.copy(definitionDraft = HabitDefinitionDraftState(), definitionError = null)
        }
    }

    fun openEditDefinition(habit: Habit) {
        if (habit.id in _uiState.value.pendingHabitIds || _uiState.value.isSavingDefinition) return
        _uiState.update {
            it.copy(
                definitionDraft = HabitDefinitionDraftState(
                    habitId = habit.id,
                    requestId = habit.id,
                    name = habit.name,
                    emoji = habit.emoji,
                    type = habit.type,
                    targetValue = formatNumber(habit.targetValue),
                    unit = habit.unit,
                    timeTargetMinutes = habit.timeTargetMinutes.coerceAtLeast(1).toString(),
                    subjectName = habit.subjectName.orEmpty(),
                    subjectTotal = habit.subjectTotal?.toString().orEmpty(),
                    subjectPosition = habit.subjectPosition.toString(),
                    subjectUnit = habit.subjectUnit ?: "pages",
                    scheduleKind = habit.scheduleKind,
                    scheduleDays = habit.scheduleDays,
                    weeklyTarget = (habit.weeklyTarget ?: 3).toString(),
                ),
                definitionError = null,
            )
        }
    }

    fun closeDefinition() {
        if (_uiState.value.isSavingDefinition) return
        _uiState.update { it.copy(definitionDraft = null, definitionError = null) }
    }

    fun setDefinitionName(value: String) = updateDefinition { copy(name = value) }
    fun setDefinitionEmoji(value: String) = updateDefinition { copy(emoji = value) }
    fun setDefinitionType(value: HabitType) = updateDefinition { copy(type = value) }
    fun setDefinitionTarget(value: String) = updateDefinition { copy(targetValue = value) }
    fun setDefinitionUnit(value: String) = updateDefinition { copy(unit = value) }
    fun setDefinitionDuration(value: String) = updateDefinition { copy(timeTargetMinutes = value) }
    fun setDefinitionSubjectName(value: String) = updateDefinition { copy(subjectName = value) }
    fun setDefinitionSubjectTotal(value: String) = updateDefinition { copy(subjectTotal = value) }
    fun setDefinitionSubjectPosition(value: String) = updateDefinition { copy(subjectPosition = value) }
    fun setDefinitionSubjectUnit(value: String) = updateDefinition { copy(subjectUnit = value) }
    fun setDefinitionSchedule(value: ScheduleKind) = updateDefinition { copy(scheduleKind = value) }
    fun setDefinitionWeeklyTarget(value: String) = updateDefinition { copy(weeklyTarget = value) }

    fun toggleDefinitionDay(day: DayOfWeek) = updateDefinition {
        copy(scheduleDays = if (day in scheduleDays) scheduleDays - day else scheduleDays + day)
    }

    fun saveDefinition() {
        val state = _uiState.value
        val userId = state.userId ?: return
        val draft = state.definitionDraft ?: return
        if (state.isSavingDefinition) return

        val targetValue = if (draft.type == HabitType.COUNTER) {
            draft.targetValue.toDoubleOrNull()
                ?: return definitionValidationFailed("Enter a valid counter target.")
        } else {
            1.0
        }
        val duration = if (draft.type == HabitType.DURATION) {
            draft.timeTargetMinutes.toIntOrNull()
                ?: return definitionValidationFailed("Enter a valid duration in minutes.")
        } else {
            0
        }
        val subjectTotal = if (draft.type == HabitType.SUBJECT) {
            draft.subjectTotal.toIntOrNull()
                ?: return definitionValidationFailed("Enter a valid subject total.")
        } else {
            null
        }
        val subjectPosition = if (draft.type == HabitType.SUBJECT) {
            draft.subjectPosition.toIntOrNull()
                ?: return definitionValidationFailed("Enter a valid current position.")
        } else {
            0
        }
        val weeklyTarget = if (draft.scheduleKind == ScheduleKind.PER_WEEK) {
            draft.weeklyTarget.toIntOrNull()
                ?: return definitionValidationFailed("Enter a valid weekly target.")
        } else {
            null
        }
        val requestId = draft.habitId ?: draft.requestId ?: UUID.randomUUID().toString()
        val intent = SaveHabitIntent(
            requestId = requestId,
            userId = userId,
            habitId = draft.habitId,
            name = draft.name,
            emoji = draft.emoji,
            type = draft.type,
            targetValue = targetValue,
            unit = draft.unit,
            timeTargetMinutes = duration,
            subjectName = draft.subjectName,
            subjectTotal = subjectTotal,
            subjectPosition = subjectPosition,
            subjectUnit = draft.subjectUnit,
            scheduleKind = draft.scheduleKind,
            scheduleDays = draft.scheduleDays,
            weeklyTarget = weeklyTarget,
        )
        HabitDefinitionValidator.validationError(intent)?.let {
            return definitionValidationFailed(it)
        }

        _uiState.update {
            it.copy(
                isSavingDefinition = true,
                definitionError = null,
                definitionDraft = it.definitionDraft?.copy(requestId = requestId),
            )
        }
        viewModelScope.launch {
            val result = if (draft.habitId == null) repository.create(intent) else repository.update(intent)
            when (result) {
                is HabitDefinitionActionResult.Success -> _uiState.update {
                    it.copy(
                        habits = it.habits.upsert(result.habit).sortedForDisplay(),
                        isSavingDefinition = false,
                        isOffline = false,
                        definitionDraft = null,
                        definitionError = null,
                    )
                }
                is HabitDefinitionActionResult.Failure -> _uiState.update {
                    it.copy(isSavingDefinition = false, definitionError = result.message)
                }
            }
        }
    }

    fun setPaused(habit: Habit, paused: Boolean) {
        val userId = _uiState.value.userId ?: return
        if (habit.id in _uiState.value.pendingHabitIds || habit.isPaused == paused) return
        _uiState.update {
            it.copy(pendingHabitIds = it.pendingHabitIds + habit.id, actionError = null)
        }
        viewModelScope.launch {
            when (val result = repository.setPaused(userId, habit.id, paused)) {
                is HabitDefinitionActionResult.Success -> _uiState.update {
                    it.copy(
                        habits = it.habits.upsert(result.habit).sortedForDisplay(),
                        pendingHabitIds = it.pendingHabitIds - habit.id,
                        isOffline = false,
                    )
                }
                is HabitDefinitionActionResult.Failure -> _uiState.update {
                    it.copy(
                        pendingHabitIds = it.pendingHabitIds - habit.id,
                        actionError = result.message,
                    )
                }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null, actionError = null) }

    private fun save(
        habit: Habit,
        value: Double = 0.0,
        durationMinutes: Int = 0,
        completed: Boolean = false,
        notes: String? = null,
    ) {
        val state = _uiState.value
        val userId = state.userId ?: return
        if (habit.id in state.pendingHabitIds || state.isSaving) return
        _uiState.update {
            it.copy(
                isSaving = true,
                pendingHabitIds = it.pendingHabitIds + habit.id,
                formError = null,
                actionError = null,
            )
        }
        val logDate = if (state.editingHabitId == habit.id) state.editingDate ?: state.today else state.today
        viewModelScope.launch {
            val intent = LogHabitIntent(
                userId,
                habit,
                logDate,
                value,
                durationMinutes,
                completed,
                notes,
            )
            when (val result = repository.log(intent)) {
                is HabitActionResult.Success -> _uiState.update {
                    val updatedHabit = result.subjectPosition?.let { position ->
                        habit.copy(subjectPosition = position)
                    }
                    it.copy(
                        habits = updatedHabit?.let(it.habits::upsert) ?: it.habits,
                        logs = it.logs.filterNot { log ->
                            log.habitId == habit.id && log.date == logDate
                        } + result.log,
                        isSaving = false,
                        isOffline = false,
                        editingHabitId = null,
                        amount = "",
                        notes = "",
                        pendingHabitIds = it.pendingHabitIds - habit.id,
                    )
                }
                is HabitActionResult.Failure -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        pendingHabitIds = it.pendingHabitIds - habit.id,
                        formError = result.message,
                        actionError = result.message,
                    )
                }
            }
        }
    }

    private fun updateDefinition(
        transform: HabitDefinitionDraftState.() -> HabitDefinitionDraftState,
    ) {
        _uiState.update {
            val current = it.definitionDraft ?: return@update it
            val updated = current.transform()
            it.copy(
                definitionDraft = updated.copy(
                    requestId = if (updated.habitId == null) null else updated.habitId,
                ),
                definitionError = null,
            )
        }
    }

    private fun definitionValidationFailed(message: String) {
        _uiState.update { it.copy(definitionError = message) }
    }

    private fun todayLog(habitId: String) = _uiState.value.logs.firstOrNull {
        it.habitId == habitId && it.date == _uiState.value.today
    }
}

fun HabitUiState.todayLog(habitId: String): HabitLog? =
    logs.firstOrNull { it.habitId == habitId && it.date == today }

fun HabitUiState.liveStreak(habit: Habit): Int = HabitStreak.current(
    habit.schedule(),
    logs.filter { it.habitId == habit.id }.map {
        HabitResult(it.date, it.completed, it.value, it.durationMinutes)
    },
    today,
)

fun HabitUiState.scheduledHabits(): List<Habit> = habits.filter {
    !it.isPaused && HabitStreak.isScheduled(it.schedule(), today)
}

fun HabitUiState.restingHabits(): List<Habit> = habits.filter {
    !it.isPaused && !HabitStreak.isScheduled(it.schedule(), today)
}

fun HabitUiState.pausedHabits(): List<Habit> = habits.filter(Habit::isPaused)

fun HabitUiState.visibleHabits(): List<Habit> = scheduledHabits()

private fun List<Habit>.upsert(habit: Habit): List<Habit> =
    filterNot { it.id == habit.id } + habit

private fun List<Habit>.sortedForDisplay(): List<Habit> =
    sortedWith(compareBy(Habit::isPaused, Habit::sortOrder, Habit::name))

private fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
