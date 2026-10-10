package com.ahmad.nafs.ui.habits

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmad.nafs.core.habits.Habit
import com.ahmad.nafs.core.habits.HabitActionResult
import com.ahmad.nafs.core.habits.HabitDefinitionActionResult
import com.ahmad.nafs.core.habits.HabitDefinitionValidator
import com.ahmad.nafs.core.habits.HabitLoadResult
import com.ahmad.nafs.core.habits.HabitLog
import com.ahmad.nafs.core.habits.HabitReminderSync
import com.ahmad.nafs.core.habits.HabitRepository
import com.ahmad.nafs.core.habits.LogHabitIntent
import com.ahmad.nafs.core.habits.SaveHabitIntent
import com.ahmad.nafs.core.time.AccountDate
import com.ahmad.nafs.domain.habits.HabitResult
import com.ahmad.nafs.domain.habits.HabitStreak
import com.ahmad.nafs.domain.habits.HabitTimerRules
import com.ahmad.nafs.domain.habits.HabitTimerState
import com.ahmad.nafs.domain.habits.HabitType
import com.ahmad.nafs.domain.habits.ScheduleKind
import com.ahmad.nafs.domain.habits.StreakSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
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
    /** Off until the user picks a time. */
    val reminderTime: LocalTime? = null,
)

/** A habit that was archived here, kept by name so a failed undo can be retried. */
data class ArchivedHabit(val id: String, val name: String)

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
    /** The log sheet's own save. Quick logs from the list run beside it and never touch this. */
    val isSaving: Boolean = false,
    val formError: String? = null,
    val definitionDraft: HabitDefinitionDraftState? = null,
    val isSavingDefinition: Boolean = false,
    val definitionError: String? = null,
    val pendingHabitIds: Set<String> = emptySet(),
    /** The dated log sheet was opened from the history sheet, which comes back once it closes. */
    val logReturnsToHistory: Boolean = false,
    val confirmArchiveHabitId: String? = null,
    val restoreFailed: ArchivedHabit? = null,
    /** The habit whose focus timer sheet is open. */
    val timerHabitId: String? = null,
    /** The focus session in progress, if any. Mirrored to saved state so it outlives the process. */
    val timer: HabitTimerState? = null,
    val isFinishingTimer: Boolean = false,
    val timerError: String? = null,
    val timerNotice: String? = null,
)

sealed interface HabitUiEvent {
    /** A log took the habit from open to complete for its day. */
    data class Completed(val habitId: String) : HabitUiEvent

    /** A focus session's minutes were added to the day. */
    data class FocusSaved(val habitId: String, val name: String, val minutes: Int) : HabitUiEvent

    /** Archived; [HabitViewModel.restoreArchived] can still bring it back. */
    data class Archived(val habitId: String, val name: String) : HabitUiEvent
}

@HiltViewModel
class HabitViewModel @Inject constructor(
    private val repository: HabitRepository,
    private val reminders: HabitReminderSync,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HabitUiState())
    val uiState: StateFlow<HabitUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<HabitUiEvent>(capacity = Channel.BUFFERED)
    val events: Flow<HabitUiEvent> = eventChannel.receiveAsFlow()

    /** Whether the habit list came from the server. Reminders are rebuilt only from that, never from a cached copy. */
    private var habitsFromServer = false

    /** Counts saves that reached the server, so a load that overlapped one is known to be possibly out of date. */
    private var completedWrites = 0L

    fun initialize(userId: String, timezone: String) {
        if (_uiState.value.userId == userId && _uiState.value.timezone == timezone) return
        val today = runCatching { AccountDate.today(timezone) }
            .getOrElse { LocalDate.now(ZoneOffset.UTC) }
        habitsFromServer = false
        _uiState.value = HabitUiState(
            userId = userId,
            timezone = timezone,
            today = today,
            timer = restoreTimer(userId),
        )
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
        val writesAtStart = completedWrites
        viewModelScope.launch {
            val result = repository.load(userId, today)
            if (_uiState.value.userId != userId) return@launch
            when (result) {
                is HabitLoadResult.Success -> {
                    if (completedWrites != writesAtStart) {
                        // A save finished while this was loading, so the list may predate it. Load again rather
                        // than show (or arm reminders from) a list that undoes the save.
                        _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
                        refresh()
                        return@launch
                    }
                    habitsFromServer = !result.isOffline
                    _uiState.update {
                        it.copy(
                            habits = result.habits.sortedForDisplay(),
                            logs = result.logs,
                            isLoading = false,
                            isRefreshing = false,
                            isOffline = result.isOffline,
                        )
                    }
                    if (!result.isOffline) syncReminders(userId, result.habits)
                }
                is HabitLoadResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, error = result.message)
                }
            }
        }
    }

    fun toggleSimple(habit: Habit) {
        val state = _uiState.value
        val existing = state.todayLog(habit.id)
        val done = existing?.completed == true
        save(
            habit,
            state.today,
            LogSource.Quick,
            value = if (done) 0.0 else 1.0,
            completed = !done,
            notes = existing?.notes,
        )
    }

    /** One tap adds one to today's count; the day's notes are kept. */
    fun addOne(habit: Habit) {
        val state = _uiState.value
        if (habit.type != HabitType.COUNTER || !state.canLogToday(habit)) return
        val existing = state.todayLog(habit.id)
        save(
            habit,
            state.today,
            LogSource.Quick,
            value = (existing?.value ?: 0.0) + 1.0,
            durationMinutes = existing?.durationMinutes ?: 0,
            notes = existing?.notes,
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
                logReturnsToHistory = it.historyHabitId == habit.id,
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
            it.copy(
                editingHabitId = null,
                amount = "",
                notes = "",
                formError = null,
                historyHabitId = if (it.logReturnsToHistory) it.editingHabitId else it.historyHabitId,
                logReturnsToHistory = false,
            )
        }
    }

    fun setAmount(value: String) = _uiState.update { it.copy(amount = value, formError = null) }

    fun setNotes(value: String) = _uiState.update { it.copy(notes = value, formError = null) }

    fun saveEditor() {
        val state = _uiState.value
        val habit = state.habits.firstOrNull { it.id == state.editingHabitId } ?: return
        if (state.isSaving) return
        if (habit.id in state.pendingHabitIds) {
            _uiState.update {
                it.copy(formError = "The last change to ${habit.name} is still saving. Try again in a moment.")
            }
            return
        }
        val amount = state.amount.toDoubleOrNull()
        if (amount == null || amount < 0.0 || !amount.isFinite()) {
            _uiState.update { it.copy(formError = "Enter a valid amount of zero or more.") }
            return
        }
        if (habit.type == HabitType.SUBJECT && amount % 1.0 != 0.0) {
            _uiState.update { it.copy(formError = "Subject progress must be a whole number.") }
            return
        }
        if (habit.type == HabitType.DURATION && amount > HabitTimerRules.MAX_MINUTES_PER_DAY) {
            _uiState.update { it.copy(formError = "A day holds at most 1,440 minutes.") }
            return
        }
        val date = state.editingDate ?: state.today
        if (habit.type == HabitType.DURATION) {
            save(habit, date, LogSource.Editor, durationMinutes = amount.toInt(), notes = state.notes)
        } else {
            save(
                habit,
                date,
                LogSource.Editor,
                value = amount,
                notes = state.notes,
                completed = habit.type == HabitType.SIMPLE && amount >= 1.0,
            )
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
                    reminderTime = habit.reminderTime,
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
    fun setDefinitionReminder(value: LocalTime?) = updateDefinition { copy(reminderTime = value) }

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
            reminderTime = draft.reminderTime,
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
                is HabitDefinitionActionResult.Success -> {
                    completedWrites += 1
                    _uiState.update {
                        it.copy(
                            habits = it.habits.upsert(result.habit).sortedForDisplay(),
                            isSavingDefinition = false,
                            isOffline = false,
                            definitionDraft = null,
                            definitionError = null,
                        )
                    }
                    afterDefinitionChange(userId)
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
                is HabitDefinitionActionResult.Success -> {
                    completedWrites += 1
                    _uiState.update {
                        it.copy(
                            habits = it.habits.upsert(result.habit).sortedForDisplay(),
                            pendingHabitIds = it.pendingHabitIds - habit.id,
                            isOffline = false,
                        )
                    }
                    afterDefinitionChange(userId)
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

    // ─── Archive ─────────────────────────────────────────────────────────────

    fun requestArchive(habit: Habit) {
        if (habit.id in _uiState.value.pendingHabitIds) return
        _uiState.update { it.copy(confirmArchiveHabitId = habit.id) }
    }

    fun dismissArchive() = _uiState.update { it.copy(confirmArchiveHabitId = null) }

    fun confirmArchive() {
        val state = _uiState.value
        val userId = state.userId ?: return
        val habit = state.habits.firstOrNull { it.id == state.confirmArchiveHabitId }
        if (habit == null || habit.id in state.pendingHabitIds) {
            dismissArchive()
            return
        }
        _uiState.update {
            it.copy(
                confirmArchiveHabitId = null,
                pendingHabitIds = it.pendingHabitIds + habit.id,
                actionError = null,
                restoreFailed = null,
            )
        }
        viewModelScope.launch {
            when (val result = repository.archive(userId, habit.id)) {
                is HabitDefinitionActionResult.Success -> {
                    completedWrites += 1
                    val endsTimer = _uiState.value.timer?.habitId == habit.id
                    _uiState.update {
                        if (it.userId != userId) return@update it
                        it.copy(
                            habits = it.habits.filterNot { item -> item.id == habit.id },
                            pendingHabitIds = it.pendingHabitIds - habit.id,
                            isOffline = false,
                            historyHabitId = it.historyHabitId.takeUnless { id -> id == habit.id },
                            timerHabitId = it.timerHabitId.takeUnless { id -> id == habit.id },
                        )
                    }
                    if (endsTimer) setTimer(null)
                    afterDefinitionChange(userId)
                    eventChannel.send(HabitUiEvent.Archived(habit.id, habit.name))
                }
                is HabitDefinitionActionResult.Failure -> _uiState.update {
                    it.copy(pendingHabitIds = it.pendingHabitIds - habit.id, actionError = result.message)
                }
            }
        }
    }

    /** Undo for an archive: the habit comes back with its history. */
    fun restoreArchived(habitId: String, name: String) {
        val userId = _uiState.value.userId ?: return
        if (habitId in _uiState.value.pendingHabitIds) return
        _uiState.update {
            it.copy(pendingHabitIds = it.pendingHabitIds + habitId, actionError = null, restoreFailed = null)
        }
        viewModelScope.launch {
            when (val result = repository.restore(userId, habitId)) {
                is HabitDefinitionActionResult.Success -> {
                    completedWrites += 1
                    _uiState.update {
                        if (it.userId != userId) return@update it
                        it.copy(
                            habits = it.habits.upsert(result.habit).sortedForDisplay(),
                            pendingHabitIds = it.pendingHabitIds - habitId,
                            isOffline = false,
                        )
                    }
                    afterDefinitionChange(userId)
                }
                is HabitDefinitionActionResult.Failure -> _uiState.update {
                    it.copy(
                        pendingHabitIds = it.pendingHabitIds - habitId,
                        restoreFailed = ArchivedHabit(habitId, name),
                    )
                }
            }
        }
    }

    fun retryRestore() {
        val failed = _uiState.value.restoreFailed ?: return
        restoreArchived(failed.id, failed.name)
    }

    fun dismissRestoreFailure() = _uiState.update { it.copy(restoreFailed = null) }

    // ─── Focus timer ─────────────────────────────────────────────────────────

    /** Opens the timer for a duration habit; while another habit's session is open, that one is shown instead. */
    fun openTimer(habit: Habit) {
        if (habit.type != HabitType.DURATION) return
        val state = _uiState.value
        val active = state.timer?.takeIf { timer -> state.habits.any { it.id == timer.habitId } }
        // A session for a habit that no longer exists has nowhere to be logged.
        if (active == null && state.timer != null) setTimer(null)
        val showing = active?.habitId ?: habit.id
        _uiState.update {
            it.copy(
                timerHabitId = showing,
                timerError = null,
                timerNotice = if (showing == habit.id) {
                    null
                } else {
                    "One timer at a time. Finish or discard this session to time ${habit.name}."
                },
            )
        }
    }

    fun closeTimer() {
        if (_uiState.value.isFinishingTimer) return
        _uiState.update { it.copy(timerHabitId = null, timerError = null, timerNotice = null) }
    }

    /** Starts the open habit's session, or resumes it. */
    fun startTimer() {
        val state = _uiState.value
        val userId = state.userId ?: return
        val habitId = state.timerHabitId ?: return
        if (state.isFinishingTimer) return
        val session = state.timer?.takeIf { it.habitId == habitId } ?: HabitTimerState(userId = userId, habitId = habitId)
        setTimer(session.start(System.currentTimeMillis()))
        _uiState.update { it.copy(timerError = null) }
    }

    fun pauseTimer() {
        val state = _uiState.value
        val session = state.timer ?: return
        if (state.isFinishingTimer) return
        setTimer(session.pause(System.currentTimeMillis()))
    }

    fun discardTimer() {
        if (_uiState.value.isFinishingTimer) return
        setTimer(null)
        _uiState.update { it.copy(timerError = null, timerNotice = null) }
    }

    /** Stops the clock and adds the session's whole minutes to today's minutes, through the usual log. */
    fun finishTimer() {
        val state = _uiState.value
        val session = state.timer ?: return
        if (state.isFinishingTimer) return
        val habit = state.habits.firstOrNull { it.id == session.habitId } ?: return
        val now = System.currentTimeMillis()
        val stopped = session.pause(now)
        val minutes = stopped.elapsedMinutes(now)
        if (minutes < 1) {
            _uiState.update { it.copy(timerError = "Time at least one minute before finishing.") }
            return
        }
        setTimer(stopped)
        val existing = state.todayLog(habit.id)
        val started = save(
            habit,
            state.today,
            LogSource.Timer(minutes),
            value = existing?.value ?: 0.0,
            durationMinutes = HabitTimerRules.minutesAfterSession(existing?.durationMinutes ?: 0, minutes),
            notes = existing?.notes,
        )
        if (!started) {
            _uiState.update {
                it.copy(timerError = "${habit.name} is still saving another change. Finish again in a moment.")
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null, actionError = null) }

    // ─── Saving logs ─────────────────────────────────────────────────────────

    private sealed interface LogSource {
        /** A tap on the list: the check circle or +1. */
        data object Quick : LogSource

        /** The log sheet. */
        data object Editor : LogSource

        data class Timer(val sessionMinutes: Int) : LogSource
    }

    /**
     * Saves one day's log. Different habits save side by side; the same habit waits for its previous save, so a
     * read-modify-write such as +1 never races itself. Returns false when nothing was sent.
     */
    private fun save(
        habit: Habit,
        date: LocalDate,
        source: LogSource,
        value: Double = 0.0,
        durationMinutes: Int = 0,
        completed: Boolean = false,
        notes: String? = null,
    ): Boolean {
        val state = _uiState.value
        val userId = state.userId ?: return false
        if (habit.id in state.pendingHabitIds) return false
        val wasComplete = state.isComplete(habit, date)
        val fromEditor = source == LogSource.Editor
        val fromTimer = source is LogSource.Timer
        _uiState.update {
            it.copy(
                pendingHabitIds = it.pendingHabitIds + habit.id,
                isSaving = it.isSaving || fromEditor,
                formError = if (fromEditor) null else it.formError,
                isFinishingTimer = it.isFinishingTimer || fromTimer,
                timerError = if (fromTimer) null else it.timerError,
                actionError = null,
            )
        }
        viewModelScope.launch {
            val intent = LogHabitIntent(userId, habit, date, value, durationMinutes, completed, notes)
            when (val result = repository.log(intent)) {
                is HabitActionResult.Success -> logSaved(userId, habit, date, source, result, wasComplete)
                is HabitActionResult.Failure -> logFailed(userId, habit.id, source, result.message)
            }
        }
        return true
    }

    private suspend fun logSaved(
        userId: String,
        habit: Habit,
        date: LocalDate,
        source: LogSource,
        result: HabitActionResult.Success,
        wasComplete: Boolean,
    ) {
        if (_uiState.value.userId != userId) return
        completedWrites += 1
        val log = result.log
        val endsTimer = source is LogSource.Timer && _uiState.value.timer?.habitId == habit.id
        _uiState.update { current ->
            val closesEditor = source == LogSource.Editor &&
                current.editingHabitId == habit.id &&
                (current.editingDate ?: current.today) == date
            current.copy(
                habits = result.subjectPosition?.let { position ->
                    current.habits.map { item -> if (item.id == habit.id) item.copy(subjectPosition = position) else item }
                } ?: current.habits,
                logs = current.logs.filterNot { item -> item.habitId == habit.id && item.date == date } + log,
                pendingHabitIds = current.pendingHabitIds - habit.id,
                isOffline = false,
                isSaving = if (source == LogSource.Editor) false else current.isSaving,
                editingHabitId = if (closesEditor) null else current.editingHabitId,
                amount = if (closesEditor) "" else current.amount,
                notes = if (closesEditor) "" else current.notes,
                historyHabitId = if (closesEditor && current.logReturnsToHistory) habit.id else current.historyHabitId,
                logReturnsToHistory = if (closesEditor) false else current.logReturnsToHistory,
                isFinishingTimer = if (source is LogSource.Timer) false else current.isFinishingTimer,
                timerHabitId = if (endsTimer) null else current.timerHabitId,
                timerNotice = if (endsTimer) null else current.timerNotice,
            )
        }
        if (endsTimer) setTimer(null)
        when {
            source is LogSource.Timer ->
                eventChannel.send(HabitUiEvent.FocusSaved(habit.id, habit.name, source.sessionMinutes))
            !wasComplete && HabitStreak.isComplete(habit.schedule(), log.toHabitResult()) ->
                eventChannel.send(HabitUiEvent.Completed(habit.id))
        }
        // Best effort, after the log is already shown as saved: a failure here leaves the stored streak as it was.
        val streak = repository.refreshStreak(userId, habit.id, _uiState.value.today) ?: return
        _uiState.update { current ->
            if (current.userId != userId) return@update current
            current.copy(
                habits = current.habits.map { item ->
                    if (item.id == habit.id) {
                        item.copy(currentStreak = streak.currentStreak, longestStreak = streak.longestStreak)
                    } else {
                        item
                    }
                },
            )
        }
    }

    private fun logFailed(userId: String, habitId: String, source: LogSource, message: String) {
        _uiState.update {
            if (it.userId != userId) return@update it
            val pending = it.pendingHabitIds - habitId
            when (source) {
                LogSource.Editor -> it.copy(
                    isSaving = false,
                    pendingHabitIds = pending,
                    formError = message,
                    actionError = message,
                )
                LogSource.Quick -> it.copy(pendingHabitIds = pending, actionError = message)
                is LogSource.Timer -> it.copy(
                    isFinishingTimer = false,
                    pendingHabitIds = pending,
                    timerError = "$message Your time is kept.",
                )
            }
        }
    }

    // ─── Reminders ───────────────────────────────────────────────────────────

    /** After a definition change: re-arm from the list when it is the server's, otherwise load it first. */
    private fun afterDefinitionChange(userId: String) {
        val state = _uiState.value
        if (state.userId != userId) return
        if (habitsFromServer) syncReminders(userId, state.habits) else refresh()
    }

    private fun syncReminders(userId: String, habits: List<Habit>) {
        reminders.reconcile(userId, habits, _uiState.value.timezone)
    }

    // ─── Timer persistence ───────────────────────────────────────────────────

    private fun setTimer(timer: HabitTimerState?) {
        _uiState.update { it.copy(timer = timer) }
        if (timer == null) {
            TIMER_KEYS.forEach { key -> savedState.remove<Any>(key) }
        } else {
            savedState[KEY_TIMER_USER] = timer.userId
            savedState[KEY_TIMER_HABIT] = timer.habitId
            savedState[KEY_TIMER_ACCUMULATED] = timer.accumulatedMillis
            savedState[KEY_TIMER_RUNNING_SINCE] = timer.runningSinceMillis
        }
    }

    /** The session saved before the process was recreated, if it belongs to this account. */
    private fun restoreTimer(userId: String): HabitTimerState? {
        val owner = savedState.get<String>(KEY_TIMER_USER)
        val habitId = savedState.get<String>(KEY_TIMER_HABIT)
        if (owner == null || habitId == null) return null
        if (owner != userId) {
            TIMER_KEYS.forEach { key -> savedState.remove<Any>(key) }
            return null
        }
        return HabitTimerState(
            userId = owner,
            habitId = habitId,
            accumulatedMillis = savedState.get<Long>(KEY_TIMER_ACCUMULATED) ?: 0L,
            runningSinceMillis = savedState.get<Long>(KEY_TIMER_RUNNING_SINCE),
        )
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

    private companion object {
        const val KEY_TIMER_USER = "habit_timer_user"
        const val KEY_TIMER_HABIT = "habit_timer_habit"
        const val KEY_TIMER_ACCUMULATED = "habit_timer_accumulated_ms"
        const val KEY_TIMER_RUNNING_SINCE = "habit_timer_running_since_ms"
        val TIMER_KEYS = listOf(KEY_TIMER_USER, KEY_TIMER_HABIT, KEY_TIMER_ACCUMULATED, KEY_TIMER_RUNNING_SINCE)
    }
}

fun HabitUiState.todayLog(habitId: String): HabitLog? =
    logs.firstOrNull { it.habitId == habitId && it.date == today }

/** This habit's saved days, as the streak and heatmap rules read them. */
fun HabitUiState.habitResults(habitId: String): List<HabitResult> =
    logs.filter { it.habitId == habitId }.map { it.toHabitResult() }

fun HabitUiState.liveStreak(habit: Habit): Int = HabitStreak.current(
    habit.schedule(),
    habitResults(habit.id),
    today,
)

/** Current and best streak from the loaded logs; the stored best only raises it (it may cover older history). */
fun HabitUiState.streakSummary(habit: Habit): StreakSummary =
    HabitStreak.summary(habit.schedule(), habitResults(habit.id), today, storedBest = habit.longestStreak)

/** Completed days so far in this Monday-to-Sunday week. */
fun HabitUiState.weekCount(habit: Habit): Int =
    HabitStreak.completedThisWeek(habit.schedule(), habitResults(habit.id), today)

/** A per-week habit that has already met this week's target: nothing more is due until Monday. */
fun HabitUiState.isDoneForWeek(habit: Habit): Boolean =
    habit.scheduleKind == ScheduleKind.PER_WEEK && weekCount(habit) >= HabitStreak.weeklyTarget(habit.schedule())

/** Nothing left to do today: logged complete, or a per-week target already met. */
fun HabitUiState.isSettledToday(habit: Habit): Boolean =
    todayLog(habit.id)?.completed == true || isDoneForWeek(habit)

fun HabitUiState.canLogToday(habit: Habit): Boolean =
    !habit.isPaused && HabitStreak.isScheduled(habit.schedule(), today)

fun HabitUiState.scheduledHabits(): List<Habit> = habits.filter {
    !it.isPaused && HabitStreak.isScheduled(it.schedule(), today)
}

fun HabitUiState.restingHabits(): List<Habit> = habits.filter {
    !it.isPaused && !HabitStreak.isScheduled(it.schedule(), today)
}

fun HabitUiState.pausedHabits(): List<Habit> = habits.filter(Habit::isPaused)

fun HabitUiState.visibleHabits(): List<Habit> = scheduledHabits()

internal fun HabitLog.toHabitResult() = HabitResult(date, completed, value, durationMinutes)

private fun HabitUiState.isComplete(habit: Habit, date: LocalDate): Boolean = HabitStreak.isComplete(
    habit.schedule(),
    logs.firstOrNull { it.habitId == habit.id && it.date == date }?.toHabitResult(),
)

private fun List<Habit>.upsert(habit: Habit): List<Habit> =
    filterNot { it.id == habit.id } + habit

private fun List<Habit>.sortedForDisplay(): List<Habit> =
    sortedWith(compareBy(Habit::isPaused, Habit::sortOrder, Habit::name))

private fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
