package com.ahmad.nafs.ui.deen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmad.nafs.core.deen.DeenExtraRules
import com.ahmad.nafs.core.deen.DeenLoadResult
import com.ahmad.nafs.core.deen.DeenRepository
import com.ahmad.nafs.core.deen.DeenSaveResult
import com.ahmad.nafs.core.deen.DeenValidator
import com.ahmad.nafs.core.deen.Prayer
import com.ahmad.nafs.core.deen.PrayerLog
import com.ahmad.nafs.core.deen.PrayerStatus
import com.ahmad.nafs.core.time.AccountDate
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

data class DeenUiState(
    val draft: PrayerLog? = null,
    val saved: PrayerLog? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isOffline: Boolean = false,
    val hasConflict: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val extraName: String = "",
    val hasEdited: Boolean = false,
) {
    val canEdit: Boolean get() = draft != null && !isLoading && !isSaving && !isOffline && !hasConflict
    val hasChanges: Boolean get() = draft != null && if (saved == null) hasEdited else draft != saved
}

@HiltViewModel
class DeenViewModel @Inject constructor(private val repository: DeenRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(DeenUiState())
    val uiState = _uiState.asStateFlow()
    private var userId: String? = null
    private var timezone = "UTC"
    private var loadJob: Job? = null

    fun initialize(userId: String, timezone: String) {
        if (this.userId == userId && this.timezone == timezone) return
        this.userId = userId
        this.timezone = timezone
        _uiState.value = DeenUiState()
        refresh(force = true)
    }

    fun refresh(force: Boolean = false) {
        val account = userId ?: return
        val state = _uiState.value
        if (state.isSaving) return
        if (!force && state.hasConflict) return
        val date = AccountDate.today(timezone)
        if (!force && state.draft?.date == date.toString() && state.hasChanges && !state.hasConflict) return
        loadJob?.cancel()
        _uiState.update { it.copy(isLoading = true, error = null, message = null) }
        loadJob = viewModelScope.launch {
            when (val result = repository.load(account, date)) {
                is DeenLoadResult.Success -> _uiState.update {
                    if (result.isOffline && it.hasConflict) it.copy(
                        isLoading = false,
                        isOffline = true,
                        error = "Reconnect to reload the latest record. Your unsaved entries are still shown.",
                    ) else it.copy(
                        draft = result.log ?: PrayerLog(UUID.randomUUID().toString(), account, date.toString()),
                        saved = result.log,
                        hasEdited = false,
                        isLoading = false,
                        isOffline = result.isOffline,
                        hasConflict = false,
                        error = null,
                    )
                }
                is DeenLoadResult.Failure -> _uiState.update { it.copy(isLoading = false, error = result.message) }
            }
        }
    }

    fun setPrayer(prayer: Prayer, status: PrayerStatus) = edit { withStatus(prayer, status) }
    fun setExtraName(value: String) = _uiState.update { it.copy(extraName = value.take(80)) }

    fun addExtra(name: String = _uiState.value.extraName) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return fail("Enter an extra prayer name.")
        if (_uiState.value.draft?.extras?.any { DeenExtraRules.name(it).equals(trimmed, ignoreCase = true) } == true) {
            return fail("That activity is already on today's list.")
        }
        edit { copy(extras = extras + DeenExtraRules.newPrayer(trimmed)) }
        _uiState.update { it.copy(extraName = "") }
    }

    fun removeExtra(index: Int) = edit { copy(extras = extras.filterIndexed { i, _ -> i != index }) }
    fun setExtra(index: Int, field: String, value: JsonPrimitive) = edit {
        copy(extras = extras.mapIndexed { i, item -> if (i == index) DeenExtraRules.patch(item, field, value) else item })
    }

    fun save() {
        val state = _uiState.value
        val draft = state.draft ?: return
        if (!state.canEdit || !state.hasChanges) return
        if (LocalDate.parse(draft.date) != AccountDate.today(timezone)) {
            return fail("The account day changed. Reload before logging today's prayers.")
        }
        DeenValidator.error(draft)?.let { return fail(it) }
        _uiState.update { it.copy(isSaving = true, error = null, message = null) }
        viewModelScope.launch {
            val result = repository.save(draft, state.saved)
            if (_uiState.value.draft?.id != draft.id) return@launch
            when (result) {
                is DeenSaveResult.Saved -> _uiState.update {
                    it.copy(draft = result.log, saved = result.log, hasEdited = false, isSaving = false, message = "Today's prayers were saved.")
                }
                DeenSaveResult.Conflict -> _uiState.update { it.copy(isSaving = false, hasConflict = true) }
                is DeenSaveResult.Failure -> _uiState.update { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    private fun edit(transform: PrayerLog.() -> PrayerLog) {
        if (!_uiState.value.canEdit) return
        _uiState.update { it.copy(draft = it.draft?.transform(), hasEdited = true, error = null, message = null) }
    }

    private fun fail(message: String) = _uiState.update { it.copy(error = message, message = null) }
}
