package com.ahmad.nafs.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmad.nafs.core.health.CustomHealthMetric
import com.ahmad.nafs.core.health.CustomMetricType
import com.ahmad.nafs.core.health.HealthActionResult
import com.ahmad.nafs.core.health.HealthDevicePreferences
import com.ahmad.nafs.core.health.HealthGoals
import com.ahmad.nafs.core.health.HealthLoadResult
import com.ahmad.nafs.core.health.HealthLog
import com.ahmad.nafs.core.health.HealthMeal
import com.ahmad.nafs.core.health.HealthMealRules
import com.ahmad.nafs.core.health.HealthMetrics
import com.ahmad.nafs.core.health.HealthPreferenceStore
import com.ahmad.nafs.core.health.HealthProfile
import com.ahmad.nafs.core.health.HealthRangeResult
import com.ahmad.nafs.core.health.HealthRepository
import com.ahmad.nafs.core.health.HealthTrendMetric
import com.ahmad.nafs.core.health.HealthTrends
import com.ahmad.nafs.core.health.HealthValidator
import com.ahmad.nafs.core.health.MealItem
import com.ahmad.nafs.core.health.SaveCustomMetricsIntent
import com.ahmad.nafs.core.health.SaveHealthLogIntent
import com.ahmad.nafs.core.health.SaveHealthProfileIntent
import com.ahmad.nafs.core.health.SleepSession
import com.ahmad.nafs.core.health.SleepSessionRules
import com.ahmad.nafs.core.time.AccountDate
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/** The editable values of one day, compared with what was last loaded or saved to find unsaved changes. */
data class HealthForm(
    val waterGlasses: Int,
    val steps: String,
    val sleepSessions: List<SleepSession>,
    val legacySleepHours: Double?,
    val meals: List<HealthMeal>,
    val metricNumberInputs: Map<String, String>,
    val metricBooleanValues: Map<String, Boolean>,
    val exerciseDone: Boolean,
    val exerciseMinutes: String,
    val dailyWeight: String,
    val mood: Int?,
    val notes: String,
)

/** The 30-day series behind the trend charts and the day switcher's "saved" marks. */
data class HealthTrendsState(
    val logs: List<HealthLog> = emptyList(),
    /** The last day of the loaded range; null until a range has loaded. */
    val end: LocalDate? = null,
    val isLoading: Boolean = false,
    val isOffline: Boolean = false,
    val error: String? = null,
    val rangeDays: Int = 7,
    val metric: HealthTrendMetric = HealthTrendMetric.WATER,
) {
    val hasLoaded: Boolean get() = end != null
}

data class HealthUiState(
    val userId: String? = null,
    val timezone: String = "UTC",
    val today: LocalDate = LocalDate.now(ZoneOffset.UTC),
    /** The day being viewed and edited: today or one of the previous six days. */
    val selectedDate: LocalDate = today,
    val profile: HealthProfile? = null,
    val log: HealthLog? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    /** A different day was chosen and its entry is still loading; its form is hidden until then. */
    val isLoadingDay: Boolean = false,
    /** The chosen day could not be loaded, so there is no trustworthy form to show or save. */
    val dayError: String? = null,
    val isOffline: Boolean = false,
    val isSaving: Boolean = false,
    val isSavingProfile: Boolean = false,
    val isSavingMetrics: Boolean = false,
    val hasSaveConflict: Boolean = false,
    val loadError: String? = null,
    val actionError: String? = null,
    val profileError: String? = null,
    val message: String? = null,
    val showProfileEditor: Boolean = false,
    val profileHeight: String = "",
    val profileWeight: String = "",
    val baselineSkipped: Boolean = false,
    val goals: HealthGoals = HealthGoals(),
    val showGoalEditor: Boolean = false,
    val goalWaterInput: Int = HealthGoals.DEFAULT_WATER,
    val goalStepsInput: Int = HealthGoals.DEFAULT_STEPS,
    val waterGlasses: Int = 0,
    val steps: String = "",
    val sleepSessions: List<SleepSession> = emptyList(),
    val legacySleepHours: Double? = null,
    val meals: List<HealthMeal> = emptyList(),
    val mealDrafts: Map<String, String> = emptyMap(),
    val persistedExtras: Map<String, JsonPrimitive> = emptyMap(),
    val metricNumberInputs: Map<String, String> = emptyMap(),
    val metricBooleanValues: Map<String, Boolean> = emptyMap(),
    val showMetricEditor: Boolean = false,
    val pendingMetricRemoval: CustomHealthMetric? = null,
    val newMetricName: String = "",
    val newMetricEmoji: String = "💊",
    val newMetricType: CustomMetricType = CustomMetricType.BOOLEAN,
    val newMetricTarget: String = "",
    val newMetricUnit: String = "",
    val exerciseDone: Boolean = false,
    val exerciseMinutes: String = "",
    val dailyWeight: String = "",
    val mood: Int? = null,
    val notes: String = "",
    /** The form as last loaded or saved; null while no day is loaded. */
    val savedForm: HealthForm? = null,
    /** A day (or a reload of this day) waiting for the user to confirm discarding unsaved changes. */
    val pendingDate: LocalDate? = null,
    val trends: HealthTrendsState = HealthTrendsState(),
) {
    /** Height or weight is missing, so BMI cannot be shown. Logging still works. */
    val baselineMissing: Boolean
        get() = profile != null && (profile.heightCm == null || profile.weightKg == null)

    /** The first-run baseline card, until the user saves or skips it. */
    val showBaselineSetup: Boolean
        get() = baselineMissing && !baselineSkipped

    val bmi: Double?
        get() = HealthMetrics.bmi(profile?.heightCm, profile?.weightKg)

    val isToday: Boolean get() = selectedDate == today

    /** Today and the previous six days, oldest first. */
    val days: List<LocalDate> get() = HealthDays.window(today)

    val form: HealthForm
        get() = HealthForm(
            waterGlasses = waterGlasses,
            steps = steps.trim(),
            sleepSessions = sleepSessions,
            legacySleepHours = legacySleepHours,
            meals = meals,
            metricNumberInputs = metricNumberInputs.filterValues(String::isNotBlank),
            metricBooleanValues = metricBooleanValues,
            exerciseDone = exerciseDone,
            exerciseMinutes = exerciseMinutes.trim(),
            dailyWeight = dailyWeight.trim(),
            mood = mood,
            notes = notes.trim(),
        )

    val hasChanges: Boolean get() = savedForm != null && form != savedForm

    val canEditDay: Boolean get() = profile != null && savedForm != null && !isLoadingDay && dayError == null

    val canSave: Boolean
        get() = canEditDay && !isSaving && !isOffline && !hasSaveConflict && selectedDate in days

    /** Days in the window with a saved entry, as far as the loaded range knows. */
    val savedDays: Set<LocalDate>
        get() = buildSet {
            if (trends.hasLoaded) trends.logs.forEach { add(it.date) }
            log?.let { add(it.date) }
        }
}

/** Account-day helpers for the seven-day switcher. */
object HealthDays {
    const val WINDOW = 7

    fun window(today: LocalDate): List<LocalDate> = (WINDOW - 1 downTo 0).map { today.minusDays(it.toLong()) }

    fun inWindow(date: LocalDate, today: LocalDate): Boolean = !date.isAfter(today) && !date.isBefore(today.minusDays(WINDOW - 1L))

    /** "Today", "Yesterday" or the weekday, for titles such as "Yesterday's check-in". */
    fun name(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(WEEKDAY)
    }

    fun possessive(date: LocalDate, today: LocalDate): String = "${name(date, today)}'s"

    fun long(date: LocalDate): String = date.format(LONG)

    private val WEEKDAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)
    private val LONG: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)
}

@HiltViewModel
class HealthViewModel @Inject constructor(
    private val repository: HealthRepository,
    private val preferences: HealthPreferenceStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HealthUiState())
    val uiState: StateFlow<HealthUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var trendJob: Job? = null

    /** The account day follows this clock in the account timezone; tests replace it. */
    internal var clock: Clock = Clock.systemUTC()

    fun initialize(userId: String, timezone: String) {
        if (_uiState.value.userId == userId && _uiState.value.timezone == timezone) return
        loadJob?.cancel()
        trendJob?.cancel()
        val today = accountToday(timezone, LocalDate.now(ZoneOffset.UTC))
        _uiState.value = HealthUiState(userId = userId, timezone = timezone, today = today, selectedDate = today)
        viewModelScope.launch {
            val stored = try {
                preferences.read(userId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                HealthDevicePreferences()
            }
            _uiState.update {
                if (it.userId != userId) it else it.copy(goals = stored.goals, baselineSkipped = stored.baselineSkipped)
            }
        }
        load(today, today)
        loadTrends(today)
    }

    /** Automatic refresh (returning to the app, a new account day). Never discards unsaved changes. */
    fun refresh() = refreshDay(userInitiated = false)

    /** The refresh button and retry actions. Asks before replacing unsaved changes. */
    fun reload() = refreshDay(userInitiated = true)

    private fun refreshDay(userInitiated: Boolean) {
        val state = _uiState.value
        if (state.userId == null || state.isSaving) return
        val today = accountToday(state.timezone, state.today)
        val date = when {
            state.selectedDate == state.today && !state.hasChanges -> today
            HealthDays.inWindow(state.selectedDate, today) -> state.selectedDate
            else -> today
        }
        if (state.hasChanges && date == state.selectedDate && !state.hasSaveConflict) {
            _uiState.update { it.copy(today = today, pendingDate = if (userInitiated) date else it.pendingDate) }
            loadTrends(today)
            return
        }
        if (!userInitiated && state.isRefreshing && date == state.selectedDate) return
        load(date, today)
        loadTrends(today)
    }

    fun selectDate(date: LocalDate) {
        val state = _uiState.value
        if (state.userId == null || state.isSaving || state.isLoading) return
        if (date == state.selectedDate || !HealthDays.inWindow(date, state.today)) return
        if (state.hasChanges) {
            _uiState.update { it.copy(pendingDate = date) }
            return
        }
        load(date, state.today)
    }

    fun confirmDiscardChanges() {
        val state = _uiState.value
        val date = state.pendingDate ?: return
        if (state.isSaving) return
        val today = accountToday(state.timezone, state.today)
        load(if (HealthDays.inWindow(date, today)) date else today, today)
    }

    fun cancelDiscardChanges() = _uiState.update { it.copy(pendingDate = null) }

    fun reloadAfterConflict() {
        val state = _uiState.value
        if (state.userId == null || state.isSaving) return
        val today = accountToday(state.timezone, state.today)
        load(if (HealthDays.inWindow(state.selectedDate, today)) state.selectedDate else today, today)
    }

    private fun load(date: LocalDate, today: LocalDate) {
        val userId = _uiState.value.userId ?: return
        loadJob?.cancel()
        _uiState.update {
            val switching = date != it.selectedDate || it.dayError != null
            val base = if (switching) it.withoutDay() else it
            base.copy(
                today = today,
                selectedDate = date,
                isLoading = it.profile == null,
                isRefreshing = it.profile != null,
                isLoadingDay = switching && it.profile != null,
                dayError = null,
                loadError = null,
                actionError = null,
                message = null,
                pendingDate = null,
            )
        }
        loadJob = viewModelScope.launch {
            val result = repository.load(userId, date)
            if (_uiState.value.userId != userId || _uiState.value.selectedDate != date) return@launch
            when (result) {
                is HealthLoadResult.Success -> _uiState.update { it.applyLoaded(result) }
                is HealthLoadResult.Failure -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingDay = false,
                        loadError = result.message.takeIf { _ -> it.profile == null || it.savedForm != null },
                        dayError = result.message.takeIf { _ -> it.profile != null && it.savedForm == null },
                    )
                }
            }
        }
    }

    private fun loadTrends(today: LocalDate) {
        val userId = _uiState.value.userId ?: return
        trendJob?.cancel()
        _uiState.update { it.copy(trends = it.trends.copy(isLoading = true, error = null)) }
        trendJob = viewModelScope.launch {
            val result = repository.loadRange(userId, today.minusDays(HealthTrends.MAX_DAYS - 1L), today)
            if (_uiState.value.userId != userId) return@launch
            _uiState.update {
                when (result) {
                    is HealthRangeResult.Success -> it.copy(
                        trends = it.trends.copy(
                            logs = result.logs,
                            end = today,
                            isLoading = false,
                            isOffline = result.isOffline,
                            error = null,
                        ),
                    )
                    is HealthRangeResult.Failure -> it.copy(
                        trends = it.trends.copy(isLoading = false, error = result.message),
                    )
                }
            }
        }
    }

    fun retryTrends() {
        val state = _uiState.value
        if (state.userId == null || state.trends.isLoading) return
        loadTrends(accountToday(state.timezone, state.today))
    }

    fun setTrendRange(days: Int) = _uiState.update {
        it.copy(trends = it.trends.copy(rangeDays = if (days >= HealthTrends.MAX_DAYS) HealthTrends.MAX_DAYS else HealthDays.WINDOW))
    }

    fun setTrendMetric(metric: HealthTrendMetric) = _uiState.update { it.copy(trends = it.trends.copy(metric = metric)) }

    // ─── Goals ───────────────────────────────────────────────────────────────

    fun openGoalEditor() = _uiState.update {
        it.copy(showGoalEditor = true, goalWaterInput = it.goals.waterGlasses, goalStepsInput = it.goals.steps)
    }

    fun closeGoalEditor() = _uiState.update { it.copy(showGoalEditor = false) }

    fun adjustWaterGoal(amount: Int) = _uiState.update {
        it.copy(goalWaterInput = (it.goalWaterInput + amount).coerceIn(HealthGoals.MIN_WATER, HealthGoals.MAX_WATER))
    }

    fun adjustStepsGoal(amount: Int) = _uiState.update {
        it.copy(goalStepsInput = HealthGoals(steps = it.goalStepsInput + amount).normalized().steps)
    }

    fun resetGoalInputs() = _uiState.update {
        it.copy(goalWaterInput = HealthGoals.DEFAULT_WATER, goalStepsInput = HealthGoals.DEFAULT_STEPS)
    }

    fun saveGoals() {
        val state = _uiState.value
        val goals = HealthGoals(state.goalWaterInput, state.goalStepsInput).normalized()
        _uiState.update {
            it.copy(goals = goals, showGoalEditor = false, actionError = null, message = "Daily goals updated on this device.")
        }
        persistPreferences()
    }

    fun skipBaseline() {
        _uiState.update { it.copy(baselineSkipped = true, profileError = null) }
        persistPreferences()
    }

    private fun persistPreferences() {
        val state = _uiState.value
        val userId = state.userId ?: return
        val stored = HealthDevicePreferences(state.goals, state.baselineSkipped)
        viewModelScope.launch {
            try {
                preferences.write(userId, stored)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _uiState.update {
                    if (it.userId != userId) it
                    else it.copy(message = null, actionError = "This choice applies now but could not be stored on this device.")
                }
            }
        }
    }

    // ─── Baseline ────────────────────────────────────────────────────────────

    fun openProfileEditor() = _uiState.update {
        it.copy(
            showProfileEditor = true,
            profileHeight = it.profile?.heightCm?.let(::formatNumber).orEmpty(),
            profileWeight = it.profile?.weightKg?.let(::formatNumber).orEmpty(),
            profileError = null,
        )
    }

    fun closeProfileEditor() {
        if (_uiState.value.isSavingProfile) return
        _uiState.update { it.copy(showProfileEditor = false, profileError = null) }
    }

    fun setProfileHeight(value: String) = _uiState.update {
        it.copy(profileHeight = value.take(10), profileError = null)
    }

    fun setProfileWeight(value: String) = _uiState.update {
        it.copy(profileWeight = value.take(10), profileError = null)
    }

    fun saveProfile() {
        val state = _uiState.value
        val userId = state.userId ?: return
        if (state.isSavingProfile) return
        val height = state.profileHeight.trim().toDoubleOrNull()
            ?: return failProfile("Enter your height in centimetres.")
        val weight = state.profileWeight.trim().toDoubleOrNull()
            ?: return failProfile("Enter your weight in kilograms.")
        val intent = SaveHealthProfileIntent(userId, height, weight)
        HealthValidator.profileError(intent)?.let { return failProfile(it) }
        _uiState.update { it.copy(isSavingProfile = true, profileError = null) }
        viewModelScope.launch {
            when (val result = repository.saveProfile(intent)) {
                is HealthActionResult.ProfileSaved -> _uiState.update {
                    it.copy(
                        profile = result.profile,
                        isSavingProfile = false,
                        showProfileEditor = false,
                        message = "Health baseline saved.",
                    )
                }
                is HealthActionResult.Failure -> _uiState.update {
                    it.copy(isSavingProfile = false, profileError = result.message)
                }
                is HealthActionResult.LogSaved -> Unit
                HealthActionResult.LogConflict -> Unit
            }
        }
    }

    // ─── Daily form ──────────────────────────────────────────────────────────

    fun setWater(value: Int) = edit { copy(waterGlasses = value.coerceIn(0, 100)) }

    fun setSteps(value: String) = edit { copy(steps = value) }
    fun addSleepSession() = edit {
        copy(
            sleepSessions = sleepSessions + SleepSession(
                id = UUID.randomUUID().toString(),
                start = "",
                end = "",
            ),
        )
    }

    fun updateSleepSession(id: String, start: String? = null, end: String? = null) = edit {
        copy(
            sleepSessions = sleepSessions.map { session ->
                if (session.id == id) {
                    session.copy(start = start ?: session.start, end = end ?: session.end)
                } else {
                    session
                }
            },
        )
    }

    fun removeSleepSession(id: String) = edit {
        copy(
            sleepSessions = sleepSessions.filterNot { it.id == id },
        )
    }

    fun setMealDraft(mealId: String, value: String) = edit {
        copy(mealDrafts = mealDrafts + (mealId to value.take(80)))
    }

    fun addMealItem(mealId: String) {
        val state = _uiState.value
        val name = state.mealDrafts[mealId].orEmpty().trim()
        if (name.isEmpty()) return fail("Enter a food or drink name.")
        edit {
            copy(
                meals = meals.map { meal ->
                    if (meal.id == mealId) {
                        meal.copy(
                            items = meal.items + MealItem(
                                id = "food-${UUID.randomUUID()}",
                                name = name,
                                emoji = "🍽️",
                            ),
                        )
                    } else {
                        meal
                    }
                },
                mealDrafts = mealDrafts - mealId,
            )
        }
    }

    fun removeMealItem(mealId: String, itemId: String) = edit {
        copy(
            meals = meals.map { meal ->
                if (meal.id == mealId) {
                    meal.copy(items = meal.items.filterNot { it.id == itemId })
                } else {
                    meal
                }
            },
        )
    }

    fun addExtraMeal(key: String) {
        val preset = HealthMealRules.extras.firstOrNull { it.key == key } ?: return
        edit {
            copy(
                meals = meals + HealthMeal(
                    id = "${preset.key}-${UUID.randomUUID()}",
                    key = preset.key,
                    label = preset.label,
                    emoji = preset.emoji,
                    items = emptyList(),
                ),
            )
        }
    }

    fun removeExtraMeal(mealId: String) = edit {
        copy(
            meals = meals.filterNot { it.id == mealId && !HealthMealRules.isCore(it.key) },
            mealDrafts = mealDrafts - mealId,
        )
    }

    fun setMetricBoolean(id: String, value: Boolean) = edit {
        copy(metricBooleanValues = metricBooleanValues + (id to value))
    }

    fun setMetricNumber(id: String, value: String) = edit {
        copy(metricNumberInputs = metricNumberInputs + (id to value.take(30)))
    }

    fun adjustMetricCounter(id: String, amount: Int) = edit {
        val current = metricNumberInputs[id]?.toDoubleOrNull() ?: 0.0
        copy(metricNumberInputs = metricNumberInputs + (id to formatNumber((current + amount).coerceAtLeast(0.0))))
    }

    fun openMetricEditor() = _uiState.update {
        it.copy(
            showMetricEditor = true,
            newMetricName = "",
            newMetricEmoji = "💊",
            newMetricType = CustomMetricType.BOOLEAN,
            newMetricTarget = "",
            newMetricUnit = "",
            actionError = null,
        )
    }

    fun closeMetricEditor() {
        if (_uiState.value.isSavingMetrics) return
        _uiState.update { it.copy(showMetricEditor = false, actionError = null) }
    }

    fun setNewMetricName(value: String) = _uiState.update {
        it.copy(newMetricName = value.take(60), actionError = null)
    }

    fun setNewMetricEmoji(value: String) = _uiState.update {
        it.copy(newMetricEmoji = value.take(16), actionError = null)
    }

    fun setNewMetricType(value: CustomMetricType) = _uiState.update {
        it.copy(
            newMetricType = value,
            newMetricTarget = if (value == CustomMetricType.BOOLEAN) "" else it.newMetricTarget,
            newMetricUnit = if (value == CustomMetricType.BOOLEAN) "" else it.newMetricUnit,
            actionError = null,
        )
    }

    fun setNewMetricTarget(value: String) = _uiState.update {
        it.copy(newMetricTarget = value.take(30), actionError = null)
    }

    fun setNewMetricUnit(value: String) = _uiState.update {
        it.copy(newMetricUnit = value.take(24), actionError = null)
    }

    fun saveNewMetric() {
        val state = _uiState.value
        val name = state.newMetricName.trim()
        if (name.isEmpty()) return fail("Enter a metric name.")
        val target = if (state.newMetricType == CustomMetricType.BOOLEAN || state.newMetricTarget.isBlank()) {
            null
        } else {
            state.newMetricTarget.toDoubleOrNull() ?: return fail("Enter a valid metric target.")
        }
        val slug = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        val metric = CustomHealthMetric(
            id = "${slug.ifEmpty { "metric" }}-${UUID.randomUUID().toString().take(8)}",
            name = name,
            emoji = state.newMetricEmoji.trim().ifEmpty { "⭐" },
            type = state.newMetricType,
            target = target,
            unit = state.newMetricUnit.trim().takeIf(String::isNotEmpty),
        )
        persistCustomMetrics(
            next = state.profile.orEmptyMetrics() + metric,
            message = "Custom health metric added.",
            closeEditor = true,
        )
    }

    fun requestMetricRemoval(metric: CustomHealthMetric) = _uiState.update {
        it.copy(pendingMetricRemoval = metric, actionError = null)
    }

    fun cancelMetricRemoval() = _uiState.update { it.copy(pendingMetricRemoval = null) }

    fun confirmMetricRemoval() {
        val state = _uiState.value
        val metric = state.pendingMetricRemoval ?: return
        persistCustomMetrics(
            next = state.profile.orEmptyMetrics().filterNot { it.id == metric.id },
            message = "${metric.name} was removed. Past saved entries remain in history.",
            removedId = metric.id,
        )
    }

    fun setExerciseDone(value: Boolean) = edit {
        copy(exerciseDone = value, exerciseMinutes = if (value) exerciseMinutes else "")
    }
    fun setExerciseMinutes(value: String) = edit { copy(exerciseMinutes = value) }
    fun setDailyWeight(value: String) = edit { copy(dailyWeight = value) }
    fun setMood(value: Int?) = edit { copy(mood = value) }
    fun setNotes(value: String) = edit { copy(notes = value) }

    fun saveLog() {
        val state = _uiState.value
        val userId = state.userId ?: return
        if (!state.canSave) return
        val today = accountToday(state.timezone, state.today)
        if (!HealthDays.inWindow(state.selectedDate, today)) {
            return fail("${HealthDays.long(state.selectedDate)} is no longer in the last seven days. Choose a recent day.")
        }
        val steps = if (state.steps.isBlank()) {
            null
        } else {
            state.steps.trim().toIntOrNull() ?: return fail("Enter a valid step count.")
        }
        val sleepHours = if (state.sleepSessions.isEmpty()) {
            state.legacySleepHours
        } else {
            SleepSessionRules.hours(state.sleepSessions)
        }
        val dailyWeight = if (state.dailyWeight.isBlank()) {
            null
        } else {
            state.dailyWeight.trim().toDoubleOrNull() ?: return fail("Enter a valid weight.")
        }
        val extras = state.persistedExtras.toMutableMap()
        state.profile.orEmptyMetrics().forEach { metric ->
            extras.remove(metric.id)
            when (metric.type) {
                CustomMetricType.BOOLEAN -> state.metricBooleanValues[metric.id]?.let {
                    extras[metric.id] = JsonPrimitive(it)
                }
                CustomMetricType.COUNTER,
                CustomMetricType.NUMBER,
                -> state.metricNumberInputs[metric.id]?.takeIf(String::isNotBlank)?.let { value ->
                    val number = value.trim().toDoubleOrNull()
                        ?: return fail("Enter a valid value for ${metric.name}.")
                    extras[metric.id] = JsonPrimitive(number)
                }
            }
        }
        val intent = SaveHealthLogIntent(
            userId = userId,
            date = state.selectedDate,
            waterGlasses = state.waterGlasses,
            steps = steps,
            sleepHours = sleepHours,
            sleepSessions = state.sleepSessions,
            meals = state.meals,
            extras = extras,
            exerciseDone = state.exerciseDone,
            exerciseMinutes = if (state.exerciseDone) {
                state.exerciseMinutes.trim().toIntOrNull()
                    ?: return fail("Enter exercise minutes.")
            } else {
                null
            },
            weightKg = dailyWeight,
            mood = state.mood,
            notes = state.notes,
            expectedId = state.log?.id,
            expectedUpdatedAt = state.log?.updatedAt,
        )
        HealthValidator.logError(intent)?.let { return fail(it) }
        _uiState.update { it.copy(isSaving = true, actionError = null, message = null, pendingDate = null) }
        viewModelScope.launch {
            val result = repository.saveLog(intent)
            if (_uiState.value.userId != userId) return@launch
            when (result) {
                is HealthActionResult.LogSaved -> _uiState.update {
                    val previousWeight = it.profile?.weightKg
                    val profile = result.profile ?: it.profile
                    val saved = it.withLog(result.log, profile ?: return@update it.copy(isSaving = false))
                    val trendEnd = it.trends.end
                    saved.copy(
                        isSaving = false,
                        isOffline = false,
                        hasSaveConflict = false,
                        trends = if (trendEnd == null) it.trends else it.trends.copy(
                            logs = HealthTrends.merge(
                                it.trends.logs,
                                result.log,
                                trendEnd.minusDays(HealthTrends.MAX_DAYS - 1L),
                                trendEnd,
                            ),
                        ),
                        message = savedMessage(it, result, previousWeight),
                    )
                }
                HealthActionResult.LogConflict -> _uiState.update {
                    it.copy(isSaving = false, hasSaveConflict = true, actionError = null, message = null)
                }
                is HealthActionResult.Failure -> _uiState.update {
                    it.copy(isSaving = false, actionError = result.message)
                }
                is HealthActionResult.ProfileSaved -> Unit
            }
        }
    }

    private fun savedMessage(state: HealthUiState, result: HealthActionResult.LogSaved, previousWeight: Double?): String {
        val day = if (state.selectedDate == state.today) "Today's health check-in" else "Your entry for ${HealthDays.long(state.selectedDate)}"
        val weight = result.profile?.weightKg
        return when {
            result.profileWeightFailed ->
                "$day was saved, but your BMI weight was not updated. Save again when you're online to retry."
            weight != null && weight != previousWeight ->
                "$day was saved. BMI now uses ${formatNumber(weight)} kg."
            else -> "$day was saved."
        }
    }

    fun dismissMessage() = _uiState.update {
        it.copy(message = null, actionError = null, loadError = null)
    }

    private fun edit(transform: HealthUiState.() -> HealthUiState) = _uiState.update {
        if (!it.canEditDay) it else it.transform().copy(actionError = null, message = null)
    }

    private fun fail(message: String) = _uiState.update { it.copy(actionError = message, message = null) }

    private fun failProfile(message: String) = _uiState.update { it.copy(profileError = message) }

    private fun accountToday(timezone: String, fallback: LocalDate): LocalDate =
        runCatching { AccountDate.today(timezone, clock) }.getOrElse { fallback }

    private fun persistCustomMetrics(
        next: List<CustomHealthMetric>,
        message: String,
        removedId: String? = null,
        closeEditor: Boolean = false,
    ) {
        val state = _uiState.value
        val userId = state.userId ?: return
        if (state.isSavingMetrics || state.isOffline) return
        val intent = SaveCustomMetricsIntent(userId, next)
        HealthValidator.customMetricsError(intent)?.let { return fail(it) }
        _uiState.update { it.copy(isSavingMetrics = true, actionError = null, message = null) }
        viewModelScope.launch {
            when (val result = repository.saveCustomMetrics(intent)) {
                is HealthActionResult.ProfileSaved -> _uiState.update {
                    it.copy(
                        profile = result.profile,
                        isSavingMetrics = false,
                        showMetricEditor = if (closeEditor) false else it.showMetricEditor,
                        pendingMetricRemoval = null,
                        metricNumberInputs = removedId?.let(it.metricNumberInputs::minus)
                            ?: it.metricNumberInputs,
                        metricBooleanValues = removedId?.let(it.metricBooleanValues::minus)
                            ?: it.metricBooleanValues,
                        persistedExtras = removedId?.let(it.persistedExtras::minus)
                            ?: it.persistedExtras,
                        savedForm = it.savedForm?.let { form ->
                            if (removedId == null) form else form.copy(
                                metricNumberInputs = form.metricNumberInputs - removedId,
                                metricBooleanValues = form.metricBooleanValues - removedId,
                            )
                        },
                        message = message,
                    )
                }
                is HealthActionResult.Failure -> _uiState.update {
                    it.copy(isSavingMetrics = false, actionError = result.message)
                }
                is HealthActionResult.LogSaved -> Unit
                HealthActionResult.LogConflict -> Unit
            }
        }
    }
}

/** Applies a loaded day: profile, log and the editable fields, which also become the saved form. */
private fun HealthUiState.applyLoaded(result: HealthLoadResult.Success): HealthUiState = withLog(result.log, result.profile).copy(
    isLoading = false,
    isRefreshing = false,
    isLoadingDay = false,
    dayError = null,
    isOffline = result.isOffline,
    hasSaveConflict = false,
    profileHeight = profileHeight.ifBlank { result.profile.heightCm?.let(::formatNumber).orEmpty() },
    profileWeight = profileWeight.ifBlank { result.profile.weightKg?.let(::formatNumber).orEmpty() },
)

private fun HealthUiState.withLog(log: HealthLog?, profile: HealthProfile): HealthUiState {
    val next = copy(
        profile = profile,
        log = log,
        waterGlasses = log?.waterGlasses ?: 0,
        steps = log?.steps?.toString().orEmpty(),
        sleepSessions = log?.sleepSessions.orEmpty(),
        legacySleepHours = log?.sleepHours?.takeIf { log.sleepSessions.isEmpty() },
        meals = HealthMealRules.ensureCore(log?.meals),
        mealDrafts = emptyMap(),
        persistedExtras = log?.extras.orEmpty(),
        metricBooleanValues = profile.customMetrics
            .filter { it.type == CustomMetricType.BOOLEAN }
            .mapNotNull { metric ->
                log?.extras?.get(metric.id)?.booleanOrNull?.let { metric.id to it }
            }.toMap(),
        metricNumberInputs = profile.customMetrics
            .filter { it.type != CustomMetricType.BOOLEAN }
            .mapNotNull { metric ->
                log?.extras?.get(metric.id)?.doubleOrNull?.let { metric.id to formatNumber(it) }
            }.toMap(),
        exerciseDone = log?.exerciseDone ?: false,
        exerciseMinutes = log?.exerciseMinutes?.toString().orEmpty(),
        dailyWeight = log?.weightKg?.let(::formatNumber).orEmpty(),
        mood = log?.mood,
        notes = log?.notes.orEmpty(),
    )
    return next.copy(savedForm = next.form)
}

/** Clears the previous day's values while another day loads, so they can never be saved under the wrong date. */
private fun HealthUiState.withoutDay(): HealthUiState = copy(
    log = null,
    savedForm = null,
    hasSaveConflict = false,
    waterGlasses = 0,
    steps = "",
    sleepSessions = emptyList(),
    legacySleepHours = null,
    meals = HealthMealRules.ensureCore(null),
    mealDrafts = emptyMap(),
    persistedExtras = emptyMap(),
    metricNumberInputs = emptyMap(),
    metricBooleanValues = emptyMap(),
    exerciseDone = false,
    exerciseMinutes = "",
    dailyWeight = "",
    mood = null,
    notes = "",
)

private fun HealthProfile?.orEmptyMetrics(): List<CustomHealthMetric> = this?.customMetrics.orEmpty()

private fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
