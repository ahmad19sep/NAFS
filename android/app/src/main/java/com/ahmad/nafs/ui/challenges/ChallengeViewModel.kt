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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChallengeEditorDraft(
    val challengeId: String? = null,
    val requestId: String? = null,
    val title: String = "",
    val emoji: String = "\uD83C\uDFAF",
    val description: String = "",
    val frequency: ChallengeFrequency = ChallengeFrequency.DAILY,
    val durationDays: String = "30",
    val requiresPhoto: Boolean = false,
    val sadqaAmount: String = "",
    val sadqaCurrency: String = "PKR",
)

data class ChallengeCheckInRequest(
    val challenge: Challenge,
    val completed: Boolean,
    val sadqaPaid: Boolean = false,
)

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
    val photoPickerChallengeId: String? = null,
    val checkInRequest: ChallengeCheckInRequest? = null,
    val deleteCandidate: Challenge? = null,
)

@HiltViewModel
class ChallengeViewModel @Inject constructor(
    private val repository: ChallengeRepository,
    private val evidenceReader: ChallengeEvidenceReader,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChallengeUiState())
    val uiState: StateFlow<ChallengeUiState> = _uiState.asStateFlow()

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
        val today = runCatching { AccountDate.today(state.timezone) }.getOrElse { state.today }
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
            when (val result = repository.load(userId)) {
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

    fun selectStatus(status: ChallengeStatus?) = _uiState.update { it.copy(selectedStatus = status) }

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
                        challenges = result.challenge?.let(it.challenges::upsert)
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

    fun requestCheckIn(challenge: Challenge, completed: Boolean) {
        if (challenge.id in _uiState.value.pendingChallengeIds) return
        if (completed && challenge.requiresPhoto) {
            _uiState.update { it.copy(photoPickerChallengeId = challenge.id, actionError = null) }
            return
        }
        _uiState.update { it.copy(checkInRequest = ChallengeCheckInRequest(challenge, completed)) }
    }

    fun cancelCheckIn() = _uiState.update { it.copy(checkInRequest = null) }

    fun setCheckInSadqaPaid(value: Boolean) = _uiState.update {
        it.copy(checkInRequest = it.checkInRequest?.copy(sadqaPaid = value))
    }

    fun cancelPhotoPicker() = _uiState.update { it.copy(photoPickerChallengeId = null) }

    fun submitPhotoEvidence(uri: Uri) {
        val state = _uiState.value
        val userId = state.userId ?: return
        val challengeId = state.photoPickerChallengeId ?: return
        if (challengeId in state.pendingChallengeIds) return
        _uiState.update {
            it.copy(
                photoPickerChallengeId = null,
                pendingChallengeIds = it.pendingChallengeIds + challengeId,
                actionError = null,
            )
        }
        viewModelScope.launch {
            when (val read = evidenceReader.read(uri)) {
                is ChallengeEvidenceReadResult.Failure -> mutationFailed(challengeId, read.message)
                is ChallengeEvidenceReadResult.Success -> {
                    when (
                        val result = repository.checkIn(
                            userId = userId,
                            challengeId = challengeId,
                            date = state.today,
                            completed = true,
                            evidence = read.evidence,
                        )
                    ) {
                        is ChallengeActionResult.Success -> {
                            mutationSucceeded(challengeId, result.challenge)
                        }
                        is ChallengeActionResult.Failure -> {
                            mutationFailed(challengeId, result.message)
                        }
                    }
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
            when (
                val result = repository.checkIn(
                    userId,
                    challengeId,
                    state.today,
                    request.completed,
                    sadqaPaid = request.sadqaPaid,
                )
            ) {
                is ChallengeActionResult.Success -> mutationSucceeded(challengeId, result.challenge)
                is ChallengeActionResult.Failure -> mutationFailed(challengeId, result.message)
            }
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
            when (val result = repository.setSadqaPaid(userId, challenge.id, date, paid)) {
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
                        isOffline = false,
                    )
                }
                is ChallengeActionResult.Failure -> mutationFailed(challenge.id, result.message)
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(actionError = null, loadError = null) }

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
}

fun ChallengeUiState.visibleChallenges(): List<Challenge> = selectedStatus?.let { status ->
    challenges.filter { it.status == status }
} ?: challenges

private fun List<Challenge>.upsert(challenge: Challenge): List<Challenge> =
    filterNot { it.id == challenge.id } + challenge

private fun formatAmount(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
