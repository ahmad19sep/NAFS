package com.ahmad.nafs.ui.challenges

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmad.nafs.core.challenges.Challenge
import com.ahmad.nafs.core.challenges.ChallengeActionResult
import com.ahmad.nafs.core.challenges.ChallengeDefinitionValidator
import com.ahmad.nafs.core.challenges.ChallengeEvidenceReadResult
import com.ahmad.nafs.core.challenges.ChallengeEvidenceReader
import com.ahmad.nafs.core.challenges.ChallengeLoadResult
import com.ahmad.nafs.core.challenges.ChallengeRepository
import com.ahmad.nafs.core.challenges.SaveChallengeIntent
import com.ahmad.nafs.core.time.AccountDate
import com.ahmad.nafs.domain.challenges.ChallengeFrequency
import com.ahmad.nafs.domain.challenges.ChallengeStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
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

data class ChallengeEditorDraft(
    val challengeId: String? = null,
    val requestId: String? = null,
    val title: String = "",
    val emoji: String = "🎯",
    val description: String = "",
    val frequency: ChallengeFrequency = ChallengeFrequency.DAILY,
    val durationDays: String = "30",
    val requiresPhoto: Boolean = false,
    val sadqaAmount: String = "",
    val sadqaCurrency: String = "PKR",
)

/** A check-in waiting for confirmation. [date] is today, or yesterday for a daily challenge's grace day. */
data class ChallengeCheckInRequest(
    val challenge: Challenge,
    val completed: Boolean,
    val date: LocalDate,
    val sadqaPaid: Boolean = false,
)

/** A completion that needs a proof photo from the system picker. */
data class ChallengePhotoRequest(val challengeId: String, val date: LocalDate)

/** A proof photo opened full size. */
data class ChallengePhotoView(val url: String, val label: String)

sealed interface ChallengeUiEvent {
    /** A completion was confirmed by the server. */
    data object CheckInDone : ChallengeUiEvent
}

data class ChallengeUiState(
    val userId: String? = null,
    val timezone: String = "UTC",
    val today: LocalDate = LocalDate.now(ZoneOffset.UTC),
    val challenges: List<Challenge> = emptyList(),
    val selectedStatus: ChallengeStatus? = ChallengeStatus.ACTIVE,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isOffline: Boolean = false,
    val loadError: String? = null,
    val actionError: String? = null,
    val editor: ChallengeEditorDraft? = null,
    val editorError: String? = null,
    val isSaving: Boolean = false,
    val pendingChallengeIds: Set<String> = emptySet(),
    val photoRequest: ChallengePhotoRequest? = null,
    val checkInRequest: ChallengeCheckInRequest? = null,
    val deleteCandidate: Challenge? = null,
    /** Cards whose check-in history is open. */
    val expandedChallengeIds: Set<String> = emptySet(),
    val photoViewer: ChallengePhotoView? = null,
    /** A challenge the last check-in completed; it has left the Active list, so a banner points to it. */
    val completedNoticeId: String? = null,
)

@HiltViewModel
class ChallengeViewModel @Inject constructor(
    private val repository: ChallengeRepository,
    private val evidenceReader: ChallengeEvidenceReader,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChallengeUiState())
    val uiState: StateFlow<ChallengeUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<ChallengeUiEvent>(capacity = Channel.BUFFERED)
    val events: Flow<ChallengeUiEvent> = eventChannel.receiveAsFlow()

    fun initialize(userId: String, timezone: String) {
        if (_uiState.value.userId == userId && _uiState.value.timezone == timezone) return
        val today = runCatching { AccountDate.today(timezone) }
            .getOrElse { LocalDate.now(ZoneOffset.UTC) }
        _uiState.value = ChallengeUiState(userId = userId, timezone = timezone, today = today)
        refresh()
    }

    fun refresh() {
        val state = _uiState.value
        val userId = state.userId ?: return
        if (state.isRefreshing) return
        val today = accountToday(state)
        _uiState.update {
            it.copy(
                today = today,
                isLoading = it.challenges.isEmpty(),
                isRefreshing = it.challenges.isNotEmpty(),
                loadError = null,
                actionError = null,
            )
        }
        viewModelScope.launch {
            when (val result = repository.load(userId, today)) {
                is ChallengeLoadResult.Success -> _uiState.update {
                    it.copy(
                        challenges = result.challenges.sortedByDescending(Challenge::createdAt),
                        isLoading = false,
                        isRefreshing = false,
                        isOffline = result.isOffline,
                    )
                }
                is ChallengeLoadResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, loadError = result.message)
                }
            }
        }
    }

    fun selectStatus(status: ChallengeStatus?) = _uiState.update { it.copy(selectedStatus = status, completedNoticeId = null) }

    fun toggleHistory(challengeId: String) = _uiState.update {
        it.copy(
            expandedChallengeIds = if (challengeId in it.expandedChallengeIds) {
                it.expandedChallengeIds - challengeId
            } else {
                it.expandedChallengeIds + challengeId
            },
        )
    }

    fun openPhoto(url: String, label: String) = _uiState.update { it.copy(photoViewer = ChallengePhotoView(url, label)) }

    fun closePhoto() = _uiState.update { it.copy(photoViewer = null) }

    fun dismissCompletedNotice() = _uiState.update { it.copy(completedNoticeId = null) }

    fun openCreate() = _uiState.update {
        it.copy(editor = ChallengeEditorDraft(), editorError = null)
    }

    fun openEdit(challenge: Challenge) {
        if (challenge.id in _uiState.value.pendingChallengeIds) return
        _uiState.update {
            it.copy(
                editor = ChallengeEditorDraft(
                    challengeId = challenge.id,
                    requestId = challenge.id,
                    title = challenge.title,
                    emoji = challenge.emoji,
                    description = challenge.description,
                    frequency = challenge.frequency,
                    durationDays = challenge.durationDays.toString(),
                    requiresPhoto = challenge.requiresPhoto,
                    sadqaAmount = challenge.sadqaAmount?.let(::formatAmount).orEmpty(),
                    sadqaCurrency = challenge.sadqaCurrency,
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
    fun setDurationDays(value: String) = updateEditor { copy(durationDays = value) }
    fun setRequiresPhoto(value: Boolean) = updateEditor { copy(requiresPhoto = value) }
    fun setSadqaAmount(value: String) = updateEditor { copy(sadqaAmount = value) }
    fun setSadqaCurrency(value: String) = updateEditor { copy(sadqaCurrency = value) }

    fun setFrequency(value: ChallengeFrequency) = updateEditor {
        copy(
            frequency = value,
            durationDays = ChallengeDefinitionValidator.defaultDuration(value).toString(),
        )
    }

    fun save() {
        val state = _uiState.value
        val userId = state.userId ?: return
        val draft = state.editor ?: return
        if (state.isSaving) return
        val duration = draft.durationDays.toIntOrNull()
            ?: return editorFailed("Enter a whole number of days.")
        val sadqaAmount = if (draft.sadqaAmount.isBlank()) {
            null
        } else {
            draft.sadqaAmount.toDoubleOrNull()
                ?: return editorFailed("Enter a valid sadqa amount.")
        }
        val requestId = draft.challengeId ?: draft.requestId ?: UUID.randomUUID().toString()
        val startDate = draft.challengeId?.let { id ->
            state.challenges.firstOrNull { it.id == id }?.startDate
        } ?: state.today
        val intent = SaveChallengeIntent(
            requestId = requestId,
            userId = userId,
            challengeId = draft.challengeId,
            title = draft.title,
            emoji = draft.emoji,
            description = draft.description,
            frequency = draft.frequency,
            durationDays = duration,
            startDate = startDate,
            requiresPhoto = draft.requiresPhoto,
            sadqaAmount = sadqaAmount,
            sadqaCurrency = draft.sadqaCurrency,
        )
        ChallengeDefinitionValidator.validationError(intent)?.let { return editorFailed(it) }

        _uiState.update {
            it.copy(
                isSaving = true,
                editorError = null,
                editor = it.editor?.copy(requestId = requestId),
            )
        }
        viewModelScope.launch {
            val result = if (draft.challengeId == null) {
                repository.create(intent)
            } else {
                repository.update(intent)
            }
            when (result) {
                is ChallengeActionResult.Success -> _uiState.update {
                    it.copy(
                        // A new cadence or duration can change how the attempt stands, so judge it again.
                        challenges = result.challenge?.reconciled(it.today)?.let(it.challenges::upsert)
                            ?.sortedByDescending(Challenge::createdAt) ?: it.challenges,
                        isSaving = false,
                        isOffline = false,
                        editor = null,
                        editorError = null,
                    )
                }
                is ChallengeActionResult.Failure -> _uiState.update {
                    it.copy(isSaving = false, editorError = result.message)
                }
            }
        }
    }

    /** Starts a check-in for [date]: today, or yesterday while a daily challenge's grace day is open. */
    fun requestCheckIn(challenge: Challenge, completed: Boolean, date: LocalDate) {
        if (challenge.id in _uiState.value.pendingChallengeIds) return
        if (completed && challenge.requiresPhoto) {
            _uiState.update { it.copy(photoRequest = ChallengePhotoRequest(challenge.id, date), actionError = null) }
            return
        }
        _uiState.update { it.copy(checkInRequest = ChallengeCheckInRequest(challenge, completed, date)) }
    }

    fun cancelCheckIn() = _uiState.update { it.copy(checkInRequest = null) }

    fun setCheckInSadqaPaid(value: Boolean) = _uiState.update {
        it.copy(checkInRequest = it.checkInRequest?.copy(sadqaPaid = value))
    }

    fun cancelPhotoPicker() = _uiState.update { it.copy(photoRequest = null) }

    fun submitPhotoEvidence(uri: Uri) {
        val state = _uiState.value
        val userId = state.userId ?: return
        val request = state.photoRequest ?: return
        val challengeId = request.challengeId
        if (challengeId in state.pendingChallengeIds) return
        _uiState.update {
            it.copy(
                photoRequest = null,
                pendingChallengeIds = it.pendingChallengeIds + challengeId,
                actionError = null,
            )
        }
        viewModelScope.launch {
            when (val read = evidenceReader.read(uri)) {
                is ChallengeEvidenceReadResult.Failure -> mutationFailed(challengeId, read.message)
                is ChallengeEvidenceReadResult.Success -> {
                    val result = repository.checkIn(
                        userId = userId,
                        challengeId = challengeId,
                        date = request.date,
                        today = accountToday(_uiState.value),
                        completed = true,
                        evidence = read.evidence,
                    )
                    checkInFinished(challengeId, completed = true, result = result)
                }
            }
        }
    }

    fun confirmCheckIn() {
        val state = _uiState.value
        val userId = state.userId ?: return
        val request = state.checkInRequest ?: return
        val challengeId = request.challenge.id
        if (challengeId in state.pendingChallengeIds) return
        _uiState.update {
            it.copy(
                pendingChallengeIds = it.pendingChallengeIds + challengeId,
                checkInRequest = null,
                actionError = null,
            )
        }
        viewModelScope.launch {
            val result = repository.checkIn(
                userId = userId,
                challengeId = challengeId,
                date = request.date,
                today = accountToday(state),
                completed = request.completed,
                sadqaPaid = request.sadqaPaid,
            )
            checkInFinished(challengeId, completed = request.completed, result = result)
        }
    }

    fun setSadqaPaid(challenge: Challenge, date: LocalDate, paid: Boolean) {
        val state = _uiState.value
        val userId = state.userId ?: return
        if (challenge.id in state.pendingChallengeIds) return
        _uiState.update {
            it.copy(
                pendingChallengeIds = it.pendingChallengeIds + challenge.id,
                actionError = null,
            )
        }
        viewModelScope.launch {
            when (val result = repository.setSadqaPaid(userId, challenge.id, date, paid, accountToday(state))) {
                is ChallengeActionResult.Success -> mutationSucceeded(challenge.id, result.challenge)
                is ChallengeActionResult.Failure -> mutationFailed(challenge.id, result.message)
            }
        }
    }

    fun requestDelete(challenge: Challenge) {
        if (challenge.id in _uiState.value.pendingChallengeIds) return
        _uiState.update { it.copy(deleteCandidate = challenge) }
    }

    fun restart(challenge: Challenge) {
        val state = _uiState.value
        val userId = state.userId ?: return
        if (challenge.id in state.pendingChallengeIds) return
        _uiState.update { it.copy(pendingChallengeIds = it.pendingChallengeIds + challenge.id, actionError = null) }
        viewModelScope.launch {
            when (val result = repository.restart(userId, challenge, state.today)) {
                is ChallengeActionResult.Success -> mutationSucceeded(challenge.id, result.challenge)
                is ChallengeActionResult.Failure -> mutationFailed(challenge.id, result.message)
            }
        }
    }

    fun cancelDelete() = _uiState.update { it.copy(deleteCandidate = null) }

    fun confirmDelete() {
        val state = _uiState.value
        val userId = state.userId ?: return
        val challenge = state.deleteCandidate ?: return
        if (challenge.id in state.pendingChallengeIds) return
        _uiState.update {
            it.copy(
                pendingChallengeIds = it.pendingChallengeIds + challenge.id,
                deleteCandidate = null,
                actionError = null,
            )
        }
        viewModelScope.launch {
            when (val result = repository.delete(userId, challenge.id)) {
                is ChallengeActionResult.Success -> _uiState.update {
                    it.copy(
                        challenges = it.challenges.filterNot { item -> item.id == challenge.id },
                        pendingChallengeIds = it.pendingChallengeIds - challenge.id,
                        expandedChallengeIds = it.expandedChallengeIds - challenge.id,
                        completedNoticeId = it.completedNoticeId.takeUnless { id -> id == challenge.id },
                        isOffline = false,
                    )
                }
                is ChallengeActionResult.Failure -> mutationFailed(challenge.id, result.message)
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(actionError = null, loadError = null) }

    private fun checkInFinished(challengeId: String, completed: Boolean, result: ChallengeActionResult) {
        when (result) {
            is ChallengeActionResult.Success -> {
                val before = _uiState.value.challenges.firstOrNull { it.id == challengeId }?.status
                val justCompleted = result.challenge?.status == ChallengeStatus.COMPLETED && before != ChallengeStatus.COMPLETED
                mutationSucceeded(challengeId, result.challenge)
                if (justCompleted) _uiState.update { it.copy(completedNoticeId = challengeId) }
                if (completed) eventChannel.trySend(ChallengeUiEvent.CheckInDone)
            }
            is ChallengeActionResult.Failure -> mutationFailed(challengeId, result.message)
        }
    }

    private fun updateEditor(transform: ChallengeEditorDraft.() -> ChallengeEditorDraft) {
        _uiState.update {
            val editor = it.editor ?: return@update it
            val updated = editor.transform()
            it.copy(
                editor = updated.copy(
                    requestId = if (updated.challengeId == null) null else updated.challengeId,
                ),
                editorError = null,
            )
        }
    }

    private fun editorFailed(message: String) = _uiState.update { it.copy(editorError = message) }

    private fun mutationSucceeded(challengeId: String, challenge: Challenge?) = _uiState.update {
        it.copy(
            challenges = challenge?.let(it.challenges::upsert) ?: it.challenges,
            pendingChallengeIds = it.pendingChallengeIds - challengeId,
            isOffline = false,
        )
    }

    private fun mutationFailed(challengeId: String, message: String) = _uiState.update {
        it.copy(
            pendingChallengeIds = it.pendingChallengeIds - challengeId,
            actionError = message,
        )
    }

    /** The account's today right now; check-in windows are judged on it, not on a possibly stale screen date. */
    private fun accountToday(state: ChallengeUiState): LocalDate =
        runCatching { AccountDate.today(state.timezone) }.getOrElse { state.today }
}

/**
 * The challenges for the selected filter. "Active" also keeps attempts that ended unfinished, so an ended challenge
 * never silently disappears from the default view; they sort after the running ones until restarted or deleted.
 */
fun ChallengeUiState.visibleChallenges(): List<Challenge> = when (selectedStatus) {
    null -> challenges
    ChallengeStatus.ACTIVE -> challenges
        .filter { it.status != ChallengeStatus.COMPLETED }
        .sortedBy { it.status == ChallengeStatus.FAILED }
    else -> challenges.filter { it.status == selectedStatus }
}

/** Replaces the challenge in place (so a card doesn't jump after a check-in), or adds it. */
private fun List<Challenge>.upsert(challenge: Challenge): List<Challenge> =
    if (any { it.id == challenge.id }) map { if (it.id == challenge.id) challenge else it } else this + challenge

private fun formatAmount(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
