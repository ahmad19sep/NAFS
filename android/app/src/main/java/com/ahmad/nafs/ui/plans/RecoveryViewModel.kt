package com.ahmad.nafs.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmad.nafs.core.plans.*
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

data class RecoveryUiState(val snapshot: RecoverySnapshot? = null, val today: LocalDate = LocalDate.of(1970, 1, 1),
    val timezone: String = "UTC", val isLoading: Boolean = true, val isBusy: Boolean = false, val isOffline: Boolean = false,
    val draftSaved: Boolean = false, val error: String? = null, val message: String? = null) {
    val preview: PlanPreview? get() = snapshot?.preview
    val editable: Boolean get() = !isLoading && !isBusy && preview == null && snapshot?.pending == null
    val canGenerate: Boolean get() = !isLoading && !isBusy && !isOffline && preview == null &&
        (snapshot?.pending != null || snapshot?.draft?.trim()?.length in 3..500)
    val toAdd: Int get() = preview?.steps?.count { it.selected && !it.added } ?: 0
    val canApply: Boolean get() = !isLoading && !isBusy && !isOffline && toAdd > 0
}

@HiltViewModel
class RecoveryViewModel @Inject constructor(private val repository: RecoveryRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(RecoveryUiState())
    val uiState = _uiState.asStateFlow()
    private var userId: String? = null
    private var source: Pair<String, String>? = null
    private var action: Job? = null
    private var draftWrite: Job? = null
    private var generation = 0

    fun initialize(user: String, today: LocalDate, timezone: String, chatId: String? = null, version: String? = null) {
        source = if (chatId != null && version != null) chatId to version else null
        if (user == userId) { _uiState.value = _uiState.value.copy(today = today, timezone = timezone); return }
        action?.cancel(); draftWrite?.cancel(); generation++
        userId = user; _uiState.value = RecoveryUiState(today = today, timezone = timezone)
        reload()
    }
    fun refresh(today: LocalDate) { _uiState.value = _uiState.value.copy(today = today); if (_uiState.value.preview != null || _uiState.value.isOffline) reload() }
    fun reload() {
        val account = userId ?: return
        if (action?.isActive == true) return
        val request = ++generation
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        action = viewModelScope.launch {
            try {
                draftWrite?.join()
                val result = repository.load(account)
                if (account != userId || request != generation) return@launch
                _uiState.value = _uiState.value.copy(snapshot = result.snapshot, draftSaved = true, isLoading = false,
                    isOffline = result.isOffline, message = result.message, error = if (result.snapshot.pending != null)
                        "Your planning request is retained. Retry it to recover the preview." else null)
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) {
                if (account == userId && request == generation) _uiState.value = _uiState.value.copy(isLoading = false,
                    isOffline = true, error = "Your plan could not be loaded. Reconnect and retry.")
            }
        }
    }
    fun editDraft(value: String) {
        val account = userId ?: return
        val state = _uiState.value
        if (!state.editable) return
        _uiState.value = state.copy(snapshot = (state.snapshot ?: RecoverySnapshot(account)).copy(draft = value), draftSaved = false, error = null)
        draftWrite?.cancel()
        draftWrite = viewModelScope.launch {
            delay(400)
            try {
                repository.storeDraft(account, value)
                if (account == userId && _uiState.value.snapshot?.draft == value) _uiState.value = _uiState.value.copy(draftSaved = true)
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) {
                if (account == userId && _uiState.value.snapshot?.draft == value) _uiState.value = _uiState.value.copy(error = "This draft is not saved on your device. Keep the screen open and retry.")
            }
        }
    }
    fun generate(fromChat: Boolean = false) {
        val account = userId ?: return
        val state = _uiState.value
        if (action?.isActive == true || state.isOffline || state.isLoading || state.isBusy || state.preview != null) return
        val intent = state.snapshot?.pending ?: if (fromChat) {
            val chat = source ?: return
            RecoveryIntent(UUID.randomUUID().toString(), conversationId = chat.first, expectedUpdatedAt = chat.second)
        } else RecoveryIntent(UUID.randomUUID().toString(), state.snapshot?.draft?.trim())
        if (!RecoveryRules.validIntent(intent)) return
        val request = ++generation
        _uiState.value = state.copy(isBusy = true, error = null, message = null)
        action = viewModelScope.launch {
            try {
                draftWrite?.cancelAndJoin()
                if (state.snapshot?.pending == null) repository.storeDraft(account, state.snapshot?.draft.orEmpty())
                when (val result = repository.generate(account, intent)) {
                    is RecoveryGenerateResult.Saved -> if (account == userId && request == generation) _uiState.value = _uiState.value.copy(
                        snapshot = result.snapshot, isBusy = false, draftSaved = true)
                    is RecoveryGenerateResult.Failure -> if (account == userId && request == generation) _uiState.value = _uiState.value.copy(
                        snapshot = (_uiState.value.snapshot ?: RecoverySnapshot(account)).copy(pending = intent.takeIf { result.pending }),
                        isBusy = false, error = result.message)
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { failed(account, request, "Your request could not be protected on this device. Retry when storage is available.") }
        }
    }
    fun select(index: Int, selected: Boolean) {
        val preview = _uiState.value.preview ?: return
        if (index !in preview.steps.indices || preview.steps[index].added) return
        update { account, state -> repository.select(account, state.preview!!.id, index, selected) }
    }
    fun apply() {
        val state = _uiState.value
        if (!state.canApply) return
        update { account, current ->
            val result = repository.apply(account, current.preview!!.id, current.today, current.timezone)
            if (account == userId) _uiState.value = _uiState.value.copy(message = result.message)
            result.snapshot
        }
    }
    fun discard() = update { account, _ -> repository.discard(account); repository.load(account).snapshot }
    private fun update(work: suspend (String, RecoveryUiState) -> RecoverySnapshot) {
        val account = userId ?: return
        val state = _uiState.value
        if (action?.isActive == true || state.isLoading || state.isBusy) return
        val request = ++generation
        _uiState.value = state.copy(isBusy = true, error = null, message = null)
        action = viewModelScope.launch {
            try {
                val snapshot = work(account, state)
                if (account == userId && request == generation) _uiState.value = _uiState.value.copy(snapshot = snapshot, isBusy = false)
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { failed(account, request, "The local plan could not be updated. Reload before continuing; saved items are retained.") }
        }
    }
    fun flushDraft(onDone: () -> Unit) {
        val account = userId ?: run { onDone(); return }
        val state = _uiState.value
        if (action?.isActive == true) return
        if (state.snapshot?.pending != null || state.preview != null) { onDone(); return }
        val request = ++generation
        _uiState.value = state.copy(isBusy = true)
        action = viewModelScope.launch {
            try {
                draftWrite?.cancelAndJoin(); repository.storeDraft(account, state.snapshot?.draft.orEmpty())
                if (account == userId && request == generation) { _uiState.value = _uiState.value.copy(isBusy = false, draftSaved = true); onDone() }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { failed(account, request, "Your latest text could not be saved. Keep the screen open and retry.") }
        }
    }
    private fun failed(account: String, request: Int, message: String) {
        if (account == userId && request == generation) _uiState.value = _uiState.value.copy(isBusy = false, error = message)
    }
}
