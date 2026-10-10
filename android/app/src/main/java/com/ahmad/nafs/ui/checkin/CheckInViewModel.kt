package com.ahmad.nafs.ui.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmad.nafs.core.checkin.CheckInDraft
import com.ahmad.nafs.core.checkin.CheckInDraftRules
import com.ahmad.nafs.core.checkin.CheckInDraftStore
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
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the on-device copy of unsaved text stands. */
enum class DraftStatus { NONE, PENDING, SAVED, FAILED }

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
    val draftStatus: DraftStatus = DraftStatus.NONE,
    /** Set when unsaved text was restored from this device after the app was closed. */
    val draftNotice: String? = null,
    /** True when the restored draft was written on an older saved version than the one now loaded. */
    val draftBehindSaved: Boolean = false,
    /** Unsaved notes from an earlier account day; shown so they can be read or copied, then discarded. */
    val previousDraft: CheckInDraft? = null,
    val voiceMessage: String? = null,
) {
    val canEdit: Boolean get() = draft != null && !isLoading && !isSaving && !isGenerating && !isOffline && !hasConflict
    val canGenerate: Boolean get() = canEdit && !hasChanges && saved != null &&
        !saved.eveningText.isNullOrBlank() && generatedVersion != saved.updatedAt
    val hasChanges: Boolean get() = draft != null && if (saved == null) {
        draft.tasks.isNotEmpty() || !draft.eveningText.isNullOrBlank()
    } else {
        !CheckInRules.sameContent(CheckInRules.normalized(draft), CheckInRules.normalized(saved))
    }

    /** Unsaved text worth keeping on the device: changed priorities or notes, or a half-typed priority. */
    val hasUnsavedText: Boolean get() = hasChanges || newTask.isNotBlank()

    fun loaded(result: CheckInLoadResult.Success, empty: DailyCheckIn): CheckInUiState =
        if (result.isOffline && hasConflict) copy(
            isLoading = false, isOffline = true,
            error = "Reconnect to reload the latest record. Your unsaved entries are still shown.",
        ) else copy(
            draft = result.record ?: empty, saved = result.record,
            isLoading = false, isOffline = result.isOffline, hasConflict = false, error = null,
        )

    /** Puts restored text on top of the loaded record, keeping the record's identity and version. */
    fun restored(draft: CheckInDraft, empty: DailyCheckIn): CheckInUiState {
        val base = saved ?: empty.copy(id = draft.recordId)
        val behind = saved != null && draft.baseUpdatedAt != saved.updatedAt
        return copy(
            draft = base.copy(tasks = draft.tasks, eveningText = draft.eveningText),
            newTask = draft.newTask,
            draftStatus = DraftStatus.SAVED,
            draftBehindSaved = behind,
            draftNotice = when {
                behind -> "Your unsaved draft was restored, but this check-in was saved elsewhere since. " +
                    "Review it before saving; saving replaces that version."
                isOffline -> "Your unsaved draft is shown. Reconnect and reload to save it."
                else -> "Your unsaved draft was restored."
            },
        )
    }
}

@HiltViewModel
class CheckInViewModel @Inject constructor(
    private val repository: CheckInRepository,
    private val verdicts: EveningVerdictRepository,
    private val drafts: CheckInDraftStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CheckInUiState())
    val uiState = _uiState.asStateFlow()
    private var userId: String? = null
    private var timezone = "UTC"
    private var loadJob: Job? = null
    private var draftJob: Job? = null
    private var pendingVerdict: EveningVerdictIntent? = null

    /** The account day follows this clock in the account timezone; tests replace it. */
    internal var clock: Clock = Clock.systemUTC()

    fun initialize(userId: String, timezone: String) {
        if (this.userId == userId && this.timezone == timezone) return
        draftJob?.cancel()
        this.userId = userId
        this.timezone = timezone
        pendingVerdict = null
        val zone = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.of("UTC"))
        _uiState.value = CheckInUiState(isEvening = ZonedDateTime.now(clock.withZone(zone)).hour >= 15)
        refresh(force = true)
    }

    fun refresh(force: Boolean = false) {
        val account = userId ?: return
        val state = _uiState.value
        if (state.isSaving || state.isGenerating || (!force && state.hasConflict)) return
        val date = today()
        if (!force && state.draft?.date == date.toString() && state.hasChanges) return
        // Reloading after a conflict deliberately replaces local edits, so their stored copy goes too.
        val discardLocal = state.hasConflict
        loadJob?.cancel()
        _uiState.update { it.copy(isLoading = true, error = null, message = null) }
        loadJob = viewModelScope.launch {
            // Text typed in the last second is written before anything replaces it.
            if (!discardLocal) flushPendingDraft(account)
            when (val result = repository.load(account, date)) {
                is CheckInLoadResult.Success -> {
                    val empty = DailyCheckIn(UUID.randomUUID().toString(), account, date.toString(), updatedAt = Instant.now(clock).toString())
                    var restore = false
                    _uiState.update {
                        // A same-day online refresh keeps what is on screen. A fresh open, a new day or the first
                        // reload after offline (when editing was blocked) restores text kept on the device.
                        restore = it.draft?.date != date.toString() || it.isOffline
                        val next = it.loaded(result, empty)
                        if (!restore) next else next.copy(newTask = "", draftStatus = DraftStatus.NONE, draftNotice = null, draftBehindSaved = false)
                    }
                    if (_uiState.value.hasConflict) return@launch
                    if (restore || discardLocal) {
                        restoreDrafts(account, date.toString(), empty, restoreCurrent = restore && !discardLocal, clearCurrent = discardLocal)
                    }
                }
                is CheckInLoadResult.Failure -> _uiState.update { it.copy(isLoading = false, error = result.message) }
            }
        }
    }

    private suspend fun restoreDrafts(account: String, date: String, empty: DailyCheckIn, restoreCurrent: Boolean, clearCurrent: Boolean) {
        val stored = try {
            drafts.read(account)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            if (userId == account) {
                _uiState.update { it.copy(draftNotice = "Unsaved text kept on this device couldn't be read.") }
            }
            return
        }
        if (userId != account) return
        val sorted = CheckInDraftRules.sort(stored, date)
        val storedCurrent = stored.current
        val movedAside = storedCurrent != null && storedCurrent.hasText && storedCurrent.date != date
        try {
            if (movedAside) drafts.writePrevious(account, sorted.previous)
            if (movedAside || clearCurrent) drafts.writeCurrent(account, null)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // Both copies stay readable; the next open sorts them again.
        }
        val current = sorted.current.takeIf { restoreCurrent }
        _uiState.update { state ->
            val restored = if (current != null && state.draft?.date == date && CheckInDraftRules.differs(current, state.saved)) {
                state.restored(current, empty)
            } else {
                state
            }
            restored.copy(previousDraft = sorted.previous)
        }
    }

    fun showEvening(value: Boolean) = _uiState.update { it.copy(isEvening = value) }

    fun setNewTask(value: String) {
        if (!_uiState.value.canEdit) return
        _uiState.update { it.copy(newTask = value.take(200), error = null) }
        scheduleDraftSave()
    }

    fun addTask() {
        val state = _uiState.value
        val text = state.newTask.trim()
        if (!state.canEdit) return
        if (text.isBlank()) return fail("Enter a priority for today.")
        if ((state.draft?.tasks?.size ?: 0) >= CheckInRules.MAX_TASKS) return fail("Keep at most seven priorities in your daily plan.")
        _uiState.update {
            it.copy(draft = it.draft?.let { record -> record.copy(tasks = record.tasks + CheckInTask(text)) }, newTask = "", error = null, message = null)
        }
        scheduleDraftSave()
    }

    fun setTaskDone(index: Int, done: Boolean) = edit {
        copy(tasks = tasks.mapIndexed { i, task -> if (i == index) task.copy(done = done) else task })
    }

    fun removeTask(index: Int) = edit { copy(tasks = tasks.filterIndexed { i, _ -> i != index }) }
    fun setEveningText(value: String) = edit { copy(eveningText = value.take(CheckInDraftRules.MAX_TEXT)) }

    /** Adds recognised speech after the evening notes already written. */
    fun appendDictation(heard: String) {
        val state = _uiState.value
        if (heard.isBlank()) return
        if (!state.canEdit) {
            _uiState.update { it.copy(voiceMessage = "What you said wasn't added because the check-in can't be edited right now.") }
            return
        }
        val current = state.draft?.eveningText.orEmpty()
        val full = DictationText.append(current, heard)
        val next = DictationText.append(current, heard, CheckInDraftRules.MAX_TEXT)
        edit { copy(eveningText = next) }
        _uiState.update {
            it.copy(voiceMessage = if (next.length < full.length) "Only part of what you said fit within the 10,000-character limit." else null)
        }
    }

    fun voiceUnavailable() = _uiState.update { it.copy(voiceMessage = VOICE_UNAVAILABLE) }

    fun discardPreviousDraft() {
        val account = userId ?: return
        _uiState.update { it.copy(previousDraft = null) }
        viewModelScope.launch {
            try {
                drafts.writePrevious(account, null)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                if (userId == account) _uiState.update { it.copy(error = "Those notes couldn't be removed from this device. Try again.") }
            }
        }
    }

    fun save() {
        val state = _uiState.value
        val draft = state.draft ?: return
        if (!state.canEdit || !state.hasChanges) return
        if (LocalDate.parse(draft.date) != today()) return fail("The account day changed. Reload before saving today's check-in.")
        val desired = CheckInRules.normalized(draft)
        CheckInRules.error(desired)?.let { return fail(it) }
        val account = userId ?: return
        draftJob?.cancel()
        _uiState.update { it.copy(isSaving = true, error = null, message = null, draftNotice = null) }
        viewModelScope.launch {
            // The draft stays on the device until the server confirms the save.
            persistDraft(account)
            val result = repository.save(desired, state.saved)
            if (_uiState.value.draft?.id != desired.id) return@launch
            when (result) {
                is CheckInSaveResult.Saved -> {
                    _uiState.update {
                        it.copy(draft = result.record, saved = result.record, isSaving = false, draftBehindSaved = false,
                            message = "Today's check-in was saved.")
                    }
                    // Clears the stored copy, unless a half-typed priority is still waiting.
                    persistDraft(account)
                }
                CheckInSaveResult.Conflict -> _uiState.update { it.copy(isSaving = false, hasConflict = true) }
                is CheckInSaveResult.Failure -> _uiState.update { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    /** Called when the screen stops, so the debounce never loses the last words typed. */
    fun flushDraft() {
        val account = userId ?: return
        if (draftJob?.isActive != true) return
        draftJob?.cancel()
        viewModelScope.launch { persistDraft(account) }
    }

    private suspend fun flushPendingDraft(account: String) {
        if (draftJob?.isActive != true) return
        draftJob?.cancel()
        persistDraft(account)
    }

    private fun edit(transform: DailyCheckIn.() -> DailyCheckIn) {
        if (!_uiState.value.canEdit) return
        _uiState.update { it.copy(draft = it.draft?.transform(), error = null, message = null) }
        scheduleDraftSave()
    }

    private fun scheduleDraftSave() {
        val account = userId ?: return
        draftJob?.cancel()
        _uiState.update { it.copy(draftStatus = if (it.hasUnsavedText) DraftStatus.PENDING else it.draftStatus) }
        draftJob = viewModelScope.launch {
            delay(DRAFT_DEBOUNCE_MILLIS)
            persistDraft(account)
        }
    }

    /** Writes the current unsaved text, or clears the stored copy when nothing differs from the saved record. */
    private suspend fun persistDraft(account: String) {
        val state = _uiState.value
        val record = state.draft ?: return
        if (userId != account) return
        val draft = CheckInDraft(
            date = record.date,
            recordId = record.id,
            baseUpdatedAt = state.saved?.updatedAt,
            tasks = record.tasks,
            eveningText = record.eveningText,
            newTask = state.newTask,
        )
        val keep = CheckInDraftRules.differs(draft, state.saved)
        val status = try {
            drafts.writeCurrent(account, draft.takeIf { keep })
            if (keep) DraftStatus.SAVED else DraftStatus.NONE
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            if (keep) DraftStatus.FAILED else DraftStatus.NONE
        }
        _uiState.update {
            if (userId == account && it.draft == record && it.newTask == state.newTask) it.copy(draftStatus = status) else it
        }
    }

    private fun fail(message: String) = _uiState.update { it.copy(error = message, message = null) }

    private fun today(): LocalDate = runCatching { AccountDate.today(timezone, clock) }.getOrElse { LocalDate.now(clock) }

    fun generateVerdict() {
        val state = _uiState.value
        val saved = state.saved ?: return
        if (!state.canGenerate) return
        if (LocalDate.parse(saved.date) != today()) return fail("The account day changed. Reload before requesting today's review.")
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
            if (LocalDate.parse(saved.date) != today()) refresh()
        }
    }

    private companion object {
        const val DRAFT_DEBOUNCE_MILLIS = 1_000L
        const val VOICE_UNAVAILABLE = "Voice input isn't available on this phone."
    }
}
