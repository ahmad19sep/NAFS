package com.ahmad.nafs.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmad.nafs.core.coach.CoachConversation
import com.ahmad.nafs.core.coach.CoachLoadResult
import com.ahmad.nafs.core.coach.CoachMessage
import com.ahmad.nafs.core.coach.CoachRepository
import com.ahmad.nafs.core.coach.CoachRules
import com.ahmad.nafs.core.coach.CoachSendIntent
import com.ahmad.nafs.core.coach.CoachSendResult
import com.ahmad.nafs.core.coach.CoachErrors
import com.ahmad.nafs.core.network.ApiFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class CoachUiState(
    val conversations: List<CoachConversation> = emptyList(),
    val selectedId: String = UUID.randomUUID().toString(),
    val isNewConversation: Boolean = false,
    val draft: String = "",
    val pending: CoachSendIntent? = null,
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val isOffline: Boolean = false,
    val error: String? = null,
    val requiresReload: Boolean = false,
    val pendingReloaded: Boolean = false,
    val contextPartial: Boolean = false,
) {
    val conversation: CoachConversation? get() = conversations.find { it.id == selectedId }
    val messages: List<CoachMessage> get() = pending?.messages ?: conversation?.messages.orEmpty()
    val canSend: Boolean get() = !isLoading && !isSending && !isOffline && !requiresReload && pending == null &&
        CoachRules.messageError(conversation?.messages.orEmpty(), draft) == null
}

@HiltViewModel
class CoachViewModel @Inject constructor(private val repository: CoachRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(CoachUiState())
    val uiState = _uiState.asStateFlow()
    private var userId: String? = null
    private var action: Job? = null

    fun initialize(userId: String) {
        if (this.userId == userId) return
        action?.cancel()
        this.userId = userId
        _uiState.value = CoachUiState()
        reload()
    }

    fun refresh() {
        val state = _uiState.value
        if (!state.isSending && !state.isLoading && (state.pending != null || state.draft.isBlank())) reload()
    }

    fun reload() {
        val account = userId ?: return
        if (action?.isActive == true) return
        _uiState.update { it.copy(isLoading = true, error = null, pendingReloaded = false) }
        action = viewModelScope.launch {
            val result = withTimeoutOrNull(30_000L) { repository.load(account) }
                ?: CoachLoadResult.Failure("Checking for a saved reply timed out. Your message is retained; check your connection and try again.")
            when (result) {
                is CoachLoadResult.Loaded -> _uiState.update { state ->
                    val pending = result.snapshot.pending ?: state.pending?.takeUnless { intent ->
                        result.snapshot.conversations.any { CoachRules.recovered(account, intent, it) }
                    }
                    val recoveredId = state.pending?.conversationId?.takeIf { pending == null }
                    val selected = pending?.conversationId ?: recoveredId ?: state.selectedId.takeIf { id ->
                        state.isNewConversation || state.draft.isNotBlank() || result.snapshot.conversations.any { it.id == id }
                    } ?: result.snapshot.conversations.firstOrNull()?.id ?: state.selectedId
                    val saved = result.snapshot.conversations.find { it.id == pending?.conversationId }
                    val changed = pending != null && (saved?.updatedAt != pending.expectedUpdatedAt)
                    state.copy(
                        conversations = result.snapshot.conversations, selectedId = selected, pending = pending,
                        isNewConversation = result.snapshot.conversations.none { it.id == selected },
                        isLoading = false, isOffline = result.isOffline, requiresReload = changed,
                        pendingReloaded = pending != null && !result.isOffline,
                        error = when {
                            result.isOffline -> "Showing encrypted offline history. Reconnect and reload to send."
                            changed -> "This conversation changed. Use the saved chat and keep your message as a draft."
                            pending != null -> "Your last message is retained. Retry the same request or reload to recover its reply."
                            else -> null
                        },
                    )
                }
                is CoachLoadResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, error = result.message, requiresReload = true)
                }
            }
        }
    }

    fun updateDraft(value: String) {
        if (_uiState.value.pending == null && !_uiState.value.isSending) {
            _uiState.update { it.copy(draft = value) }
        }
    }

    fun selectConversation(id: String) {
        val state = _uiState.value
        if (state.isLoading || state.isSending || state.pending != null || state.draft.isNotBlank()) return
        if (state.conversations.none { it.id == id }) return
        _uiState.update { it.copy(selectedId = id, isNewConversation = false, error = null, contextPartial = false) }
    }

    fun newConversation() {
        val state = _uiState.value
        if (state.isLoading || state.isSending || state.pending != null) return
        _uiState.update { it.copy(selectedId = UUID.randomUUID().toString(), isNewConversation = true, error = null, contextPartial = false, requiresReload = false) }
    }

    fun send() {
        val account = userId ?: return
        val state = _uiState.value
        if (action?.isActive == true || state.isLoading || state.isSending || state.isOffline || state.requiresReload) return
        val intent = state.pending ?: run {
            CoachRules.messageError(state.conversation?.messages.orEmpty(), state.draft)?.let { error ->
                _uiState.update { it.copy(error = error) }
                return
            }
            CoachSendIntent(
                messages = state.conversation?.messages.orEmpty() + CoachMessage("user", state.draft.trim()),
                conversationId = state.selectedId,
                expectedUpdatedAt = state.conversation?.updatedAt,
            )
        }
        _uiState.update { it.copy(pending = intent, draft = "", isSending = true, error = null, pendingReloaded = false) }
        action = viewModelScope.launch {
            // Bound the whole operation, including session/cache work outside the HTTP timeout.
            val result = withTimeoutOrNull(70_000L) { repository.send(account, intent) }
                ?: CoachSendResult.Failure(ApiFailure.TIMEOUT, CoachErrors.message(ApiFailure.TIMEOUT))
            when (result) {
                is CoachSendResult.Saved -> _uiState.update {
                    it.copy(
                        conversations = (listOf(result.conversation) + it.conversations.filter { row -> row.id != result.conversation.id }).take(20),
                        pending = null, isNewConversation = false, isSending = false, contextPartial = result.contextPartial, requiresReload = false,
                    )
                }
                is CoachSendResult.Failure -> _uiState.update {
                    it.copy(isSending = false, error = result.message, requiresReload = result.failure in listOf(
                        ApiFailure.CONFLICT, ApiFailure.RESULT_UNCONFIRMED, ApiFailure.INVALID_RESPONSE, ApiFailure.TIMEOUT,
                    ))
                }
            }
        }
    }

    /** Explicitly resolve a failed retry only after an online read of the saved chat. */
    fun keepMessageAsDraft() {
        val account = userId ?: return
        val state = _uiState.value
        val pending = state.pending ?: return
        if (action?.isActive == true || !state.pendingReloaded || state.isOffline) return
        _uiState.update { it.copy(isLoading = true) }
        action = viewModelScope.launch {
            try {
                repository.discardPending(account)
                _uiState.update {
                    it.copy(pending = null, draft = pending.messages.last().content, requiresReload = false,
                        pendingReloaded = false, isLoading = false, error = null)
                }
            } catch (cancellation: kotlinx.coroutines.CancellationException) { throw cancellation } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Your retry could not be cleared. Reload and try again.") }
            }
        }
    }
}
