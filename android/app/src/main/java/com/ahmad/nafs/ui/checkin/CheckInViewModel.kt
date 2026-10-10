package com.ahmad.nafs.ui.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmad.nafs.core.checkin.CheckInLoadResult
import com.ahmad.nafs.core.checkin.CheckInRepository
import com.ahmad.nafs.core.checkin.CheckInRules
import com.ahmad.nafs.core.checkin.CheckInSaveResult
import com.ahmad.nafs.core.checkin.CheckInTask
import com.ahmad.nafs.core.checkin.DailyCheckIn
import com.ahmad.nafs.core.checkin.EveningVerdictIntent
import com.ahmad.nafs.core.checkin.EveningVerdictRepository
import com.ahmad.nafs.core.checkin.EveningVerdictResult
import com.ahmad.nafs.core.network.ApiFailure
import com.ahmad.nafs.core.time.AccountDate
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CheckInUiState(
    val draft: DailyCheckIn? = null,
    val saved: DailyCheckIn? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isGenerating: Boolean = false,
    val generatedVersion: String? = null,
    val isOffline: Boolean = false,
    val hasConflict: Boolean = false,
    val isEvening: Boolean = false,
    val newTask: String = "",
    val error: String? = null,
    val message: String? = null,
) {
    val canEdit: Boolean get() = draft != null && !isLoading && !isSaving && !isGenerating && !isOffline && !hasConflict
    val canGenerate: Boolean get() = canEdit && !hasChanges && saved != null &&
        !saved.eveningText.isNullOrBlank() && generatedVersion != saved.updatedAt
    val hasChanges: Boolean get() = draft != null && if (saved == null) {
        draft.tasks.isNotEmpty() || !draft.eveningText.isNullOrBlank()
    } else {
        !CheckInRules.sameContent(CheckInRules.normalized(draft), CheckInRules.normalized(saved))
    }

    fun loaded(result: CheckInLoadResult.Success, empty: DailyCheckIn): CheckInUiState =
        if (result.isOffline && hasConflict) copy(
            isLoading = false, isOffline = true,
            error = "Reconnect to reload the latest record. Your unsaved entries are still shown.",
        ) else copy(
            draft = result.record ?: empty, saved = result.record,
            isLoading = false, isOffline = result.isOffline, hasConflict = false, error = null,
        )
}

@HiltViewModel
class CheckInViewModel @Inject constructor(
    private val repository: CheckInRepository,
    private val verdicts: EveningVerdictRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CheckInUiState())
    val uiState = _uiState.asStateFlow()
    private var userId: String? = null
    private var timezone = "UTC"
    private var loadJob: Job? = null
    private var pendingVerdict: EveningVerdictIntent? = null

    fun initialize(userId: String, timezone: String) {
        if (this.userId == userId && this.timezone == timezone) return
        this.userId = userId
        this.timezone = timezone
        pendingVerdict = null
        val zone = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.of("UTC"))
        _uiState.value = CheckInUiState(isEvening = ZonedDateTime.now(zone).hour >= 15)
        refresh(force = true)
    }

    fun refresh(force: Boolean = false) {
        val account = userId ?: return
        val state = _uiState.value
        if (state.isSaving || state.isGenerating || (!force && state.hasConflict)) return
        val date = AccountDate.today(timezone)
        if (!force && state.draft?.date == date.toString() && state.hasChanges) return
        loadJob?.cancel()
        _uiState.update { it.copy(isLoading = true, error = null, message = null) }
        loadJob = viewModelScope.launch {
            when (val result = repository.load(account, date)) {
                is CheckInLoadResult.Success -> _uiState.update {
                    it.loaded(result, DailyCheckIn(UUID.randomUUID().toString(), account, date.toString(), updatedAt = Instant.now().toString()))
                }
                is CheckInLoadResult.Failure -> _uiState.update { it.copy(isLoading = false, error = result.message) }
            }
        }
    }

    fun showEvening(value: Boolean) = _uiState.update { it.copy(isEvening = value) }
    fun setNewTask(value: String) = _uiState.update { it.copy(newTask = value.take(200), error = null) }

    fun addTask() {
        val state = _uiState.value
        val text = state.newTask.trim()
        if (!state.canEdit) return
        if (text.isBlank()) return fail("Enter a priority for today.")
        if ((state.draft?.tasks?.size ?: 0) >= CheckInRules.MAX_TASKS) return fail("Keep at most seven priorities in your daily plan.")
        edit { copy(tasks = tasks + CheckInTask(text)) }
        _uiState.update { it.copy(newTask = "") }
    }

    fun setTaskDone(index: Int, done: Boolean) = edit {
        copy(tasks = tasks.mapIndexed { i, task -> if (i == index) task.copy(done = done) else task })
    }

    fun removeTask(index: Int) = edit { copy(tasks = tasks.filterIndexed { i, _ -> i != index }) }
    fun setEveningText(value: String) = edit { copy(eveningText = value.take(10_000)) }

    fun save() {
        val state = _uiState.value
        val draft = state.draft ?: return
        if (!state.canEdit || !state.hasChanges) return
        if (LocalDate.parse(draft.date) != AccountDate.today(timezone)) return fail("The account day changed. Reload before saving today's check-in.")
        val desired = CheckInRules.normalized(draft)
        CheckInRules.error(desired)?.let { return fail(it) }
        _uiState.update { it.copy(isSaving = true, error = null, message = null) }
        viewModelScope.launch {
            val result = repository.save(desired, state.saved)
            if (_uiState.value.draft?.id != desired.id) return@launch
            when (result) {
                is CheckInSaveResult.Saved -> _uiState.update {
                    it.copy(draft = result.record, saved = result.record, isSaving = false, message = "Today's check-in was saved.")
                }
                CheckInSaveResult.Conflict -> _uiState.update { it.copy(isSaving = false, hasConflict = true) }
                is CheckInSaveResult.Failure -> _uiState.update { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    private fun edit(transform: DailyCheckIn.() -> DailyCheckIn) {
        if (!_uiState.value.canEdit) return
        _uiState.update { it.copy(draft = it.draft?.transform(), error = null, message = null) }
    }

    private fun fail(message: String) = _uiState.update { it.copy(error = message, message = null) }

    fun generateVerdict() {
        val state = _uiState.value
        val saved = state.saved ?: return
        if (!state.canGenerate) return
        if (LocalDate.parse(saved.date) != AccountDate.today(timezone)) return fail("The account day changed. Reload before requesting today's review.")
        val intent = pendingVerdict?.takeIf { it.checkInId == saved.id && it.expectedUpdatedAt == saved.updatedAt }
            ?: EveningVerdictIntent(
                checkInId = saved.id, expectedUpdatedAt = saved.updatedAt,
                requestId = UUID.randomUUID().toString(), date = saved.date,
                eveningText = saved.eveningText.orEmpty(), tasks = saved.tasks,
            ).also { pendingVerdict = it }
        _uiState.update { it.copy(isGenerating = true, error = null, message = null) }
        viewModelScope.launch {
            val result = verdicts.generate(saved.userId, intent)
            if (_uiState.value.draft?.id != saved.id) return@launch
            when (result) {
                is EveningVerdictResult.Saved -> {
                    pendingVerdict = null
                    _uiState.update { it.copy(
                        draft = result.record, saved = result.record, isGenerating = false,
                        generatedVersion = result.record.updatedAt, message = "Your AI evening review was saved.",
                    ) }
                }
                is EveningVerdictResult.Failure -> _uiState.update { it.copy(
                    isGenerating = false, hasConflict = result.reason == ApiFailure.CONFLICT,
                    error = result.message,
                ) }
            }
            if (LocalDate.parse(saved.date) != AccountDate.today(timezone)) refresh()
        }
    }
}
