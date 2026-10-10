package com.ahmad.nafs.ui.letters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmad.nafs.core.letters.FutureLetter
import com.ahmad.nafs.core.letters.LetterCreateIntent
import com.ahmad.nafs.core.letters.LetterCursor
import com.ahmad.nafs.core.letters.LetterDraft
import com.ahmad.nafs.core.letters.LetterLoadResult
import com.ahmad.nafs.core.letters.LetterRepository
import com.ahmad.nafs.core.letters.LetterResumeResult
import com.ahmad.nafs.core.letters.LetterRules
import com.ahmad.nafs.core.letters.LetterSaveResult
import com.ahmad.nafs.core.letters.LetterReplyIntent
import com.ahmad.nafs.core.letters.LetterReplyResult
import com.ahmad.nafs.core.letters.LetterReplyRules
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class LetterScreen { ARCHIVE, EDITOR, DETAIL }
data class LetterUiState(
    val screen: LetterScreen = LetterScreen.ARCHIVE,
    val today: LocalDate = LocalDate.of(1970, 1, 1),
    val letters: List<FutureLetter> = emptyList(),
    val next: LetterCursor? = null,
    val selected: FutureLetter? = null,
    val draft: LetterDraft? = null,
    val draftSaved: Boolean = false,
    val pending: LetterCreateIntent? = null,
    val pendingMissing: Boolean = false,
    val different: FutureLetter? = null,
    val pendingReply: LetterReplyIntent? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isOffline: Boolean = false,
    val error: String? = null,
    val message: String? = null,
) {
    val canEdit: Boolean get() = !isLoading && !isLoadingMore && !isSaving && pending == null
    val canSave: Boolean get() = !isOffline && !isLoading && !isLoadingMore && !isSaving &&
        (pending != null || draft?.let { LetterRules.error(it, today) == null } == true)
    val canGenerateReply: Boolean get() = !isOffline && !isLoading && !isLoadingMore && !isSaving && pending == null &&
        selected?.let { letter -> if (pendingReply != null) pendingReply.letter.id == letter.id
            else LetterReplyRules.eligible(letter, today) } == true
}

@HiltViewModel
class LetterViewModel @Inject constructor(private val repository: LetterRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(LetterUiState())
    val uiState = _uiState.asStateFlow()
    private var userId: String? = null
    private var action: Job? = null
    private var draftWrite: Job? = null
    private var generation = 0

    fun initialize(accountId: String, today: LocalDate) {
        if (accountId == userId) { _uiState.value = _uiState.value.copy(today = today); return }
        action?.cancel(); draftWrite?.cancel()
        generation++
        userId = accountId
        _uiState.value = LetterUiState(today = today)
        load(initial = true)
    }

    fun refresh(today: LocalDate) {
        _uiState.value = _uiState.value.copy(today = today)
        if (_uiState.value.screen != LetterScreen.EDITOR) reload()
    }
    fun reload() { if (action?.isActive != true) load() }
    fun loadMore() {
        val state = _uiState.value
        if (action?.isActive == true || state.isOffline || state.next == null) return
        load(older = true)
    }

    private fun load(initial: Boolean = false, older: Boolean = false) {
        val account = userId ?: return
        val cursor = _uiState.value.next.takeIf { older }
        val detailId = _uiState.value.selected?.id.takeUnless { older }
        val request = ++generation
        _uiState.value = _uiState.value.copy(isLoading = !older, isLoadingMore = older, error = null, message = null)
        action = viewModelScope.launch {
            val result = repository.load(account, cursor, detailId)
            if (request != generation || account != userId) return@launch
            val state = _uiState.value
            when (result) {
                is LetterLoadResult.Loaded -> {
                    val letters = if (older) (result.page.letters + state.letters).distinctBy { it.id }.sortedWith(LetterRules.newestFirst) else result.page.letters
                    _uiState.value = if (older) state.copy(letters = letters, next = result.page.next,
                        isLoadingMore = false, isOffline = result.isOffline)
                    else state.copy(letters = letters, next = result.page.next.takeUnless { result.isOffline },
                        draft = result.pending?.draft() ?: state.draft ?: result.draft,
                        pending = result.pending, pendingMissing = result.pendingMissing, different = result.different,
                        pendingReply = result.pendingReply,
                        selected = if (result.detailMissing) null else result.detail ?: state.selected,
                        draftSaved = result.pending != null || (result.draft != null && (state.draft == null || state.draft == result.draft)) || state.draftSaved,
                        screen = when {
                            result.detailMissing -> LetterScreen.ARCHIVE
                            initial && (result.detail != null || result.pendingReply != null) -> LetterScreen.DETAIL
                            initial && result.draft != null -> LetterScreen.EDITOR
                            else -> state.screen
                        },
                        isLoading = false, isOffline = result.isOffline)
                    if (!older && result.recovered != null) adoptSaved(LetterSaveResult.Saved(result.recovered, localCleanupFailed = result.pending != null))
                    else if (!older && result.pending != null) _uiState.value = _uiState.value.copy(
                        error = if (result.pendingMissing) "The letter is not saved yet. Retry this save or keep editing."
                            else if (result.different != null) "This letter already has a different saved version. Read it before continuing."
                            else "Your last save is retained. Retry it or reload to check your saved letter.")
                    else if (!older && result.pendingReply != null) _uiState.value = _uiState.value.copy(
                        error = "Your reply request is retained. Reload to check for a saved reply, or retry the same request.")
                    else if (!older && result.detailMissing) _uiState.value = _uiState.value.copy(error = "This letter is no longer available.")
                }
                is LetterLoadResult.Failure -> _uiState.value = state.copy(isLoading = false, isLoadingMore = false,
                    isOffline = if (older) state.isOffline else state.letters.isNotEmpty() || state.draft != null,
                    next = state.next.takeIf { older }, error = result.message, pendingMissing = false)
            }
        }
    }

    fun newLetter() {
        val account = userId ?: return
        val state = _uiState.value
        if (!state.canEdit || state.draft != null) return
        _uiState.value = state.copy(screen = LetterScreen.EDITOR, draft = LetterDraft(UUID.randomUUID().toString(), account, "", state.today.plusMonths(6).toString()),
            draftSaved = false, selected = null, error = null, message = null)
        persistDraft()
    }
    fun openDraft() { if (action?.isActive != true && _uiState.value.draft != null) _uiState.value = _uiState.value.copy(screen = LetterScreen.EDITOR, selected = null, message = null) }
    fun openLetter(id: String) {
        if (action?.isActive == true) return
        val letter = _uiState.value.letters.find { it.id == id } ?: return
        _uiState.value = _uiState.value.copy(screen = LetterScreen.DETAIL, selected = letter, message = null)
    }
    fun openPendingReply() {
        if (action?.isActive == true) return
        val intent = _uiState.value.pendingReply ?: return
        val letter = _uiState.value.letters.find { it.id == intent.letter.id } ?: intent.letter
        _uiState.value = _uiState.value.copy(screen = LetterScreen.DETAIL, selected = letter, message = null)
    }

    fun generateReply() {
        val account = userId ?: return
        val state = _uiState.value
        if (action?.isActive == true || !state.canGenerateReply) return
        val letter = state.selected ?: return
        val intent = state.pendingReply ?: LetterReplyIntent(letter, UUID.randomUUID().toString())
        val request = ++generation
        _uiState.value = state.copy(isSaving = true, pendingReply = intent, error = null, message = null)
        action = viewModelScope.launch {
            val result = repository.generateReply(account, intent, state.today)
            if (account != userId || request != generation) return@launch
            val latest = _uiState.value
            when (result) {
                is LetterReplyResult.Saved -> _uiState.value = latest.copy(selected = result.letter,
                    letters = (listOf(result.letter) + latest.letters.filterNot { it.id == result.letter.id }).sortedWith(LetterRules.newestFirst),
                    pendingReply = intent.takeIf { result.localCleanupFailed }, isSaving = false, isOffline = false,
                    error = if (result.localCleanupFailed) "Your reply is saved. Reload to finish updating this device." else null,
                    message = buildString {
                        append("Coach reply saved.")
                        if (result.contextPartial) append(" Some account records were unavailable to the coach.")
                        if (result.historySaved == false) append(" The report archive copy could not be saved; your reply is in this letter.")
                    })
                is LetterReplyResult.Failure -> _uiState.value = latest.copy(isSaving = false,
                    pendingReply = intent.takeIf { result.pending }, error = result.message)
            }
        }
    }
    fun setContent(value: String) = edit { it.copy(content = value) }
    fun setTargetDate(value: String) = edit { it.copy(targetDate = value) }
    private fun edit(update: (LetterDraft) -> LetterDraft) {
        val state = _uiState.value
        if (!state.canEdit || state.draft == null) return
        _uiState.value = state.copy(draft = update(state.draft), draftSaved = false, error = null, message = null)
        persistDraft()
    }

    private fun persistDraft() {
        val account = userId ?: return
        val draft = _uiState.value.draft ?: return
        draftWrite?.cancel()
        draftWrite = viewModelScope.launch {
            delay(400)
            try {
                repository.storeDraft(account, draft)
                if (account == userId && _uiState.value.draft == draft) _uiState.value = _uiState.value.copy(draftSaved = true)
            } catch (cancellation: CancellationException) { throw cancellation }
            catch (_: Exception) {
                if (account == userId && _uiState.value.draft == draft) _uiState.value = _uiState.value.copy(
                    draftSaved = false, error = "This draft could not be saved on your device. Keep the editor open and retry.")
            }
        }
    }

    fun closeEditor() = changeDraft(discard = false)
    fun discardDraft() = changeDraft(discard = true)
    private fun changeDraft(discard: Boolean) {
        val account = userId ?: return
        val state = _uiState.value
        if (action?.isActive == true || (discard && state.pending != null)) return
        if (state.screen == LetterScreen.DETAIL || state.pending != null) {
            _uiState.value = state.copy(screen = LetterScreen.ARCHIVE, selected = null)
            return
        }
        val request = ++generation
        _uiState.value = state.copy(isSaving = true)
        action = viewModelScope.launch {
            try {
                draftWrite?.cancelAndJoin()
                repository.storeDraft(account, state.draft.takeUnless { discard })
                if (account != userId || request != generation) return@launch
                _uiState.value = _uiState.value.copy(screen = LetterScreen.ARCHIVE, selected = null,
                    draft = state.draft.takeUnless { discard }, draftSaved = !discard, isSaving = false, error = null)
            } catch (cancellation: CancellationException) { throw cancellation }
            catch (_: Exception) {
                if (account == userId && request == generation) _uiState.value = _uiState.value.copy(isSaving = false,
                    error = "The local draft could not be updated. Your text is still here; retry.")
            }
        }
    }

    fun save() {
        val account = userId ?: return
        val state = _uiState.value
        if (action?.isActive == true || !state.canSave) return
        val intent = state.pending ?: state.draft?.intent() ?: return
        val request = ++generation
        _uiState.value = state.copy(isSaving = true, pending = intent, pendingMissing = false, error = null, message = null)
        action = viewModelScope.launch {
            draftWrite?.cancelAndJoin()
            val result = repository.save(account, intent, state.today)
            if (account != userId || request != generation) return@launch
            when (result) {
                is LetterSaveResult.Saved -> adoptSaved(result)
                is LetterSaveResult.Failure -> _uiState.value = _uiState.value.copy(isSaving = false,
                    pending = intent.takeIf { result.pending }, error = result.message, draftSaved = result.pending || _uiState.value.draftSaved)
            }
        }
    }

    fun keepEditing() {
        val account = userId ?: return
        val state = _uiState.value
        val pending = state.pending ?: return
        if (action?.isActive == true || state.isOffline || !state.pendingMissing) return
        val request = ++generation
        _uiState.value = state.copy(isSaving = true, error = null)
        action = viewModelScope.launch {
            val result = repository.resumeDraft(account, pending)
            if (account != userId || request != generation) return@launch
            when (result) {
                is LetterResumeResult.Editing -> _uiState.value = _uiState.value.copy(draft = result.draft, pending = null,
                    pendingMissing = false, isSaving = false, draftSaved = true, screen = LetterScreen.EDITOR)
                is LetterResumeResult.Saved -> adoptSaved(result.result)
                is LetterResumeResult.Failure -> _uiState.value = _uiState.value.copy(isSaving = false, pendingMissing = false, error = result.message)
            }
        }
    }

    fun acceptSaved() {
        val account = userId ?: return
        val state = _uiState.value
        val pending = state.pending ?: return
        if (action?.isActive == true || state.isOffline || state.different == null) return
        val request = ++generation
        _uiState.value = state.copy(isSaving = true, error = null)
        action = viewModelScope.launch {
            val result = repository.acceptSaved(account, pending)
            if (account != userId || request != generation) return@launch
            when (result) {
                is LetterSaveResult.Saved -> adoptSaved(result)
                is LetterSaveResult.Failure -> _uiState.value = _uiState.value.copy(isSaving = false, error = result.message)
            }
        }
    }

    private fun adoptSaved(result: LetterSaveResult.Saved) {
        val state = _uiState.value
        _uiState.value = state.copy(screen = LetterScreen.DETAIL, selected = result.letter,
            letters = (listOf(result.letter) + state.letters.filterNot { it.id == result.letter.id }).sortedWith(LetterRules.newestFirst),
            draft = state.draft.takeIf { result.localCleanupFailed }, pending = state.pending.takeIf { result.localCleanupFailed },
            pendingMissing = false, different = null, isSaving = false, isLoading = false, isOffline = false,
            error = if (result.localCleanupFailed) "Your letter is saved. Reload to finish updating the local draft." else null,
            message = "Letter saved for ${result.letter.targetLabel}.")
    }
}
