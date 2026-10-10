package com.ahmad.nafs.ui.deen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmad.nafs.core.deen.DeenExtraRules
import com.ahmad.nafs.core.deen.DeenHistoryResult
import com.ahmad.nafs.core.deen.DeenLoadResult
import com.ahmad.nafs.core.deen.DeenRepository
import com.ahmad.nafs.core.deen.DeenSaveResult
import com.ahmad.nafs.core.deen.DeenValidator
import com.ahmad.nafs.core.deen.Prayer
import com.ahmad.nafs.core.deen.PrayerHistoryDay
import com.ahmad.nafs.core.deen.PrayerHistoryRules
import com.ahmad.nafs.core.deen.PrayerHistorySummary
import com.ahmad.nafs.core.deen.PrayerLog
import com.ahmad.nafs.core.deen.PrayerStatus
import com.ahmad.nafs.core.time.AccountDate
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

/** The day being logged, relative to the account's today. Late Isha after midnight is logged on Yesterday. */
enum class DeenDay { TODAY, YESTERDAY, EARLIER }

sealed interface DeenHistoryState {
    data object Loading : DeenHistoryState
    data class Ready(val summary: PrayerHistorySummary, val isOffline: Boolean) : DeenHistoryState
    data class Failed(val message: String) : DeenHistoryState
}

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
    /** The account's today at the last refresh; null until known. */
    val today: LocalDate? = null,
    /** The day shown and edited: today or yesterday. */
    val selectedDate: LocalDate? = null,
    /** Streak, heatmap and per-prayer counts; null until first requested. */
    val history: DeenHistoryState? = null,
) {
    val canEdit: Boolean get() = draft != null && !isLoading && !isSaving && !isOffline && !hasConflict
    val hasChanges: Boolean get() = draft != null && if (saved == null) hasEdited else draft != saved

    /** Unsaved entries stay with their day, so switching waits until they are saved. */
    val canSwitchDay: Boolean get() = today != null && !isSaving && !hasChanges && !hasConflict

    val day: DeenDay
        get() {
            val reference = today ?: return DeenDay.TODAY
            val date = selectedDate ?: draft?.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return DeenDay.TODAY
            return when (date) {
                reference -> DeenDay.TODAY
                reference.minusDays(1) -> DeenDay.YESTERDAY
                else -> DeenDay.EARLIER
            }
        }
}

@HiltViewModel
class DeenViewModel @Inject constructor(private val repository: DeenRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(DeenUiState())
    val uiState = _uiState.asStateFlow()
    private var userId: String? = null
    private var timezone = "UTC"
    private var loadJob: Job? = null
    private var historyJob: Job? = null

    /** Saved days from the last history read, kept in step with days loaded online or saved since. */
    private var historyDays: Map<String, PrayerHistoryDay> = emptyMap()
    private var historyFrom: LocalDate? = null
    private var historyThrough: LocalDate? = null
    private var historyOffline = false

    /** The clock for the account day; tests replace it. */
    internal var clock: Clock = Clock.systemUTC()

    fun initialize(userId: String, timezone: String) {
        if (this.userId == userId && this.timezone == timezone) return
        this.userId = userId
        this.timezone = timezone
        loadJob?.cancel()
        historyJob?.cancel()
        historyDays = emptyMap()
        historyFrom = null
        historyThrough = null
        _uiState.value = DeenUiState()
        refresh(force = true)
    }

    /**
     * Reloads the day on screen. Unsaved entries are kept unless [force]: a day that has just rolled over becomes
     * Yesterday and can still be saved. Without unsaved entries a new account day opens on Today.
     */
    fun refresh(force: Boolean = false) {
        val account = userId ?: return
        val state = _uiState.value
        if (state.isSaving) return
        if (!force && state.hasConflict) return
        val today = AccountDate.today(timezone, clock)
        val dayChanged = state.today != null && state.today != today
        val selected = state.selectedDate
        val editable = selected != null && editableOn(selected, today)
        if (!force && !state.hasConflict && state.hasChanges && editable) {
            if (dayChanged) {
                _uiState.update { it.copy(today = today) }
                loadHistory(account, today)
            }
            return
        }
        val target = if (selected == null || dayChanged || !editable) today else selected
        loadDay(account, target, today, keepEntries = target == selected)
        if (selected != null && !editable && state.hasChanges) {
            val day = selected.format(DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH))
            _uiState.update { it.copy(error = "Unsaved entries for $day were cleared: that day can no longer be changed here.") }
        }
        if (force || dayChanged || state.history == null || state.history is DeenHistoryState.Failed) loadHistory(account, today)
    }

    /** Switches between today and yesterday. Ignored while entries are unsaved, saving or in conflict. */
    fun selectDay(day: DeenDay) {
        val account = userId ?: return
        val state = _uiState.value
        if (!state.canSwitchDay) return
        val today = AccountDate.today(timezone, clock)
        val date = when (day) {
            DeenDay.TODAY -> today
            DeenDay.YESTERDAY -> today.minusDays(1)
            DeenDay.EARLIER -> return
        }
        if (state.selectedDate == date && state.today == today && state.draft?.date == date.toString() && !state.isLoading) return
        loadDay(account, date, today, keepEntries = false)
        if (state.today != today) loadHistory(account, today)
    }

    fun retryHistory() {
        val account = userId ?: return
        loadHistory(account, AccountDate.today(timezone, clock))
    }

    fun setPrayer(prayer: Prayer, status: PrayerStatus) = edit { withStatus(prayer, status) }
    fun setExtraName(value: String) = _uiState.update { it.copy(extraName = value.take(80)) }

    /** Adds a web suggestion by name (a yes/no activity or a counter), or a custom yes/no activity. */
    fun addExtra(name: String = _uiState.value.extraName) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return fail("Enter an extra prayer name.")
        if (_uiState.value.draft?.extras?.any { DeenExtraRules.name(it).equals(trimmed, ignoreCase = true) } == true) {
            return fail("That activity is already on this day's list.")
        }
        val suggestion = DeenExtraRules.catalog.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
        val item = if (suggestion != null) DeenExtraRules.newExtra(suggestion) else DeenExtraRules.newPrayer(trimmed)
        edit { copy(extras = extras + item) }
        _uiState.update { it.copy(extraName = "") }
    }

    fun removeExtra(index: Int) = edit { copy(extras = extras.filterIndexed { i, _ -> i != index }) }
    fun setExtra(index: Int, field: String, value: JsonPrimitive) = edit {
        copy(extras = extras.mapIndexed { i, item -> if (i == index) DeenExtraRules.patch(item, field, value) else item })
    }

    /** One −/+ step on a counter extra. */
    fun stepExtra(index: Int, by: Int) = edit {
        copy(extras = extras.mapIndexed { i, item -> if (i == index) DeenExtraRules.step(item, by) else item })
    }

    fun save() {
        val state = _uiState.value
        val draft = state.draft ?: return
        if (!state.canEdit || !state.hasChanges) return
        val today = AccountDate.today(timezone, clock)
        val date = runCatching { LocalDate.parse(draft.date) }.getOrNull()
        if (date == null || !editableOn(date, today)) {
            return fail("That day can no longer be changed here. Reload to log today's prayers.")
        }
        DeenValidator.error(draft)?.let { return fail(it) }
        _uiState.update { it.copy(isSaving = true, error = null, message = null, today = today) }
        viewModelScope.launch {
            val result = repository.save(draft, state.saved)
            if (_uiState.value.draft?.id != draft.id) return@launch
            when (result) {
                is DeenSaveResult.Saved -> {
                    val message = if (date == today) "Today's prayers were saved." else "Yesterday's prayers were saved."
                    _uiState.update {
                        it.copy(draft = result.log, saved = result.log, hasEdited = false, isSaving = false, message = message)
                    }
                    recordDay(date, result.log)
                }
                DeenSaveResult.Conflict -> _uiState.update { it.copy(isSaving = false, hasConflict = true) }
                is DeenSaveResult.Failure -> _uiState.update { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    /**
     * Loads [date]. With [keepEntries] (a reload of the same day) the current entries stay on screen until the result
     * arrives; otherwise the new day shows as loading rather than under the previous day's entries.
     */
    private fun loadDay(account: String, date: LocalDate, today: LocalDate, keepEntries: Boolean) {
        loadJob?.cancel()
        _uiState.update {
            if (keepEntries) it.copy(isLoading = true, error = null, message = null, today = today, selectedDate = date)
            else it.copy(
                draft = null, saved = null, hasEdited = false, isOffline = false, hasConflict = false, extraName = "",
                isLoading = true, error = null, message = null, today = today, selectedDate = date,
            )
        }
        loadJob = viewModelScope.launch {
            val result = repository.load(account, date)
            if (userId != account || _uiState.value.selectedDate != date) return@launch
            when (result) {
                is DeenLoadResult.Success -> {
                    _uiState.update {
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
                    if (!result.isOffline) recordDay(date, result.log)
                }
                is DeenLoadResult.Failure -> _uiState.update { it.copy(isLoading = false, error = result.message) }
            }
        }
    }

    private fun loadHistory(account: String, today: LocalDate) {
        historyJob?.cancel()
        if (_uiState.value.history !is DeenHistoryState.Ready) _uiState.update { it.copy(history = DeenHistoryState.Loading) }
        historyJob = viewModelScope.launch {
            val result = repository.history(account, today.minusDays(PrayerHistoryRules.LOOKBACK_DAYS), today)
            if (userId != account) return@launch
            when (result) {
                is DeenHistoryResult.Success -> {
                    historyDays = result.days.associateBy { it.date }
                    historyFrom = result.from
                    historyThrough = result.through
                    historyOffline = result.isOffline
                    // The day on screen may have been loaded or saved after the server answered this read.
                    val state = _uiState.value
                    val shown = state.saved?.takeIf { !state.isOffline }
                    shown?.let { log -> runCatching { LocalDate.parse(log.date) }.getOrNull()?.let { recordDay(it, log, publish = false) } }
                    publishHistory()
                }
                // The last good history stays on screen; only a first failure replaces the loading state.
                is DeenHistoryResult.Failure -> if (_uiState.value.history !is DeenHistoryState.Ready) {
                    _uiState.update { it.copy(history = DeenHistoryState.Failed(result.message)) }
                }
            }
        }
    }

    /** Keeps history in step with a day just loaded online or saved. */
    private fun recordDay(date: LocalDate, log: PrayerLog?, publish: Boolean = true) {
        val from = historyFrom ?: return
        val through = historyThrough ?: return
        if (date.isBefore(from) || date.isAfter(through)) return
        val key = date.toString()
        historyDays = if (log == null) historyDays - key else historyDays + (key to PrayerHistoryDay.of(log))
        if (publish) publishHistory()
    }

    private fun publishHistory() {
        val from = historyFrom ?: return
        val through = historyThrough ?: return
        val summary = PrayerHistoryRules.summary(historyDays.values, from, through)
        _uiState.update { it.copy(history = DeenHistoryState.Ready(summary, historyOffline)) }
    }

    private fun edit(transform: PrayerLog.() -> PrayerLog) {
        if (!_uiState.value.canEdit) return
        _uiState.update { it.copy(draft = it.draft?.transform(), hasEdited = true, error = null, message = null) }
    }

    private fun fail(message: String) = _uiState.update { it.copy(error = message, message = null) }

    private fun editableOn(date: LocalDate, today: LocalDate) = !date.isAfter(today) && !date.isBefore(today.minusDays(1))
}
