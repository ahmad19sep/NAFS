package com.ahmad.nafs.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmad.nafs.core.health.HealthActionResult
import com.ahmad.nafs.core.health.CustomHealthMetric
import com.ahmad.nafs.core.health.CustomMetricType
import com.ahmad.nafs.core.health.HealthLoadResult
import com.ahmad.nafs.core.health.HealthLog
import com.ahmad.nafs.core.health.HealthMeal
import com.ahmad.nafs.core.health.HealthMealRules
import com.ahmad.nafs.core.health.HealthMetrics
import com.ahmad.nafs.core.health.HealthProfile
import com.ahmad.nafs.core.health.HealthRepository
import com.ahmad.nafs.core.health.HealthValidator
import com.ahmad.nafs.core.health.MealItem
import com.ahmad.nafs.core.health.SaveCustomMetricsIntent
import com.ahmad.nafs.core.health.SaveHealthLogIntent
import com.ahmad.nafs.core.health.SaveHealthProfileIntent
import com.ahmad.nafs.core.health.SleepSession
import com.ahmad.nafs.core.health.SleepSessionRules
import com.ahmad.nafs.core.time.AccountDate
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

data class HealthUiState(
    val userId: String? = null,
    val timezone: String = "UTC",
    val today: LocalDate = LocalDate.now(ZoneOffset.UTC),
    val profile: HealthProfile? = null,
    val log: HealthLog? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isOffline: Boolean = false,
    val isSaving: Boolean = false,
    val isSavingProfile: Boolean = false,
    val isSavingMetrics: Boolean = false,
    val hasSaveConflict: Boolean = false,
    val loadError: String? = null,
    val actionError: String? = null,
    val message: String? = null,
    val showProfileEditor: Boolean = false,
    val profileHeight: String = "",
    val profileWeight: String = "",
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
) {
    val setupRequired: Boolean
        get() = profile?.heightCm == null || profile.weightKg == null

    val bmi: Double?
        get() = HealthMetrics.bmi(profile?.heightCm, profile?.weightKg)
}

@HiltViewModel
class HealthViewModel @Inject constructor(
    private val repository: HealthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HealthUiState())
    val uiState: StateFlow<HealthUiState> = _uiState.asStateFlow()

    fun initialize(userId: String, timezone: String) {
        if (_uiState.value.userId == userId && _uiState.value.timezone == timezone) return
        val today = runCatching { AccountDate.today(timezone) }
            .getOrElse { LocalDate.now(ZoneOffset.UTC) }
        _uiState.value = HealthUiState(userId = userId, timezone = timezone, today = today)
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
                isLoading = it.profile == null,
                isRefreshing = it.profile != null,
                loadError = null,
                actionError = null,
                message = null,
            )
        }
        viewModelScope.launch {
            when (val result = repository.load(userId, today)) {
                is HealthLoadResult.Success -> applyLoaded(result)
                is HealthLoadResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, loadError = result.message)
                }
            }
        }
    }

    fun openProfileEditor() = _uiState.update {
        it.copy(
            showProfileEditor = true,
            profileHeight = it.profile?.heightCm?.let(::formatNumber).orEmpty(),
            profileWeight = it.profile?.weightKg?.let(::formatNumber).orEmpty(),
            actionError = null,
        )
    }

    fun closeProfileEditor() {
        if (_uiState.value.isSavingProfile || _uiState.value.setupRequired) return
        _uiState.update { it.copy(showProfileEditor = false, actionError = null) }
    }

    fun setProfileHeight(value: String) = _uiState.update {
        it.copy(profileHeight = value, actionError = null)
    }

    fun setProfileWeight(value: String) = _uiState.update {
        it.copy(profileWeight = value, actionError = null)
    }

    fun saveProfile() {
        val state = _uiState.value
        val userId = state.userId ?: return
        if (state.isSavingProfile) return
        val height = state.profileHeight.toDoubleOrNull()
            ?: return fail("Enter your height in centimetres.")
        val weight = state.profileWeight.toDoubleOrNull()
            ?: return fail("Enter your weight in kilograms.")
        val intent = SaveHealthProfileIntent(userId, height, weight)
        HealthValidator.profileError(intent)?.let { return fail(it) }
        _uiState.update { it.copy(isSavingProfile = true, actionError = null) }
        viewModelScope.launch {
            when (val result = repository.saveProfile(intent)) {
                is HealthActionResult.ProfileSaved -> _uiState.update {
                    it.copy(
                        profile = result.profile,
                        isSavingProfile = false,
                        showProfileEditor = false,
                        isOffline = false,
                        message = "Health profile saved.",
                    )
                }
                is HealthActionResult.Failure -> _uiState.update {
                    it.copy(isSavingProfile = false, actionError = result.message)
                }
                is HealthActionResult.LogSaved -> Unit
                HealthActionResult.LogConflict -> Unit
            }
        }
    }

    fun setWater(value: Int) = _uiState.update {
        it.copy(waterGlasses = value.coerceIn(0, 100), actionError = null, message = null)
    }

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
        if (state.isSaving || state.setupRequired || state.hasSaveConflict) return
        val steps = if (state.steps.isBlank()) {
            null
        } else {
            state.steps.toIntOrNull() ?: return fail("Enter a valid step count.")
        }
        val sleepHours = if (state.sleepSessions.isEmpty()) {
            state.legacySleepHours
        } else {
            SleepSessionRules.hours(state.sleepSessions)
        }
        val dailyWeight = if (state.dailyWeight.isBlank()) {
            null
        } else {
            state.dailyWeight.toDoubleOrNull() ?: return fail("Enter a valid weight.")
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
                    val number = value.toDoubleOrNull()
                        ?: return fail("Enter a valid value for ${metric.name}.")
                    extras[metric.id] = JsonPrimitive(number)
                }
            }
        }
        val intent = SaveHealthLogIntent(
            userId = userId,
            date = state.today,
            waterGlasses = state.waterGlasses,
            steps = steps,
            sleepHours = sleepHours,
            sleepSessions = state.sleepSessions,
            meals = state.meals,
            extras = extras,
            exerciseDone = state.exerciseDone,
            exerciseMinutes = if (state.exerciseDone) {
                state.exerciseMinutes.toIntOrNull()
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
        _uiState.update { it.copy(isSaving = true, actionError = null, message = null) }
        viewModelScope.launch {
            when (val result = repository.saveLog(intent)) {
                is HealthActionResult.LogSaved -> _uiState.update {
                    it.copy(
                        log = result.log,
                        sleepSessions = result.log.sleepSessions,
                        meals = HealthMealRules.ensureCore(result.log.meals),
                        persistedExtras = result.log.extras,
                        legacySleepHours = result.log.sleepHours.takeIf {
                            result.log.sleepSessions.isEmpty()
                        },
                        isSaving = false,
                        isOffline = false,
                        hasSaveConflict = false,
                        message = "Today's health check-in was saved.",
                    )
                }
                HealthActionResult.LogConflict -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        hasSaveConflict = true,
                        actionError = null,
                        message = null,
                    )
                }
                is HealthActionResult.Failure -> _uiState.update {
                    it.copy(isSaving = false, actionError = result.message)
                }
                is HealthActionResult.ProfileSaved -> Unit
            }
        }
    }

    fun dismissMessage() = _uiState.update {
        it.copy(message = null, actionError = null, loadError = null)
    }

    fun reloadAfterConflict() {
        refresh()
    }

    private fun applyLoaded(result: HealthLoadResult.Success) = _uiState.update {
        val log = result.log
        it.copy(
            profile = result.profile,
            log = log,
            isLoading = false,
            isRefreshing = false,
            isOffline = result.isOffline,
            hasSaveConflict = false,
            showProfileEditor = result.profile.heightCm == null || result.profile.weightKg == null,
            profileHeight = result.profile.heightCm?.let(::formatNumber).orEmpty(),
            profileWeight = result.profile.weightKg?.let(::formatNumber).orEmpty(),
            waterGlasses = log?.waterGlasses ?: 0,
            steps = log?.steps?.toString().orEmpty(),
            sleepSessions = log?.sleepSessions.orEmpty(),
            legacySleepHours = log?.sleepHours?.takeIf { log.sleepSessions.isEmpty() },
            meals = HealthMealRules.ensureCore(log?.meals),
            mealDrafts = emptyMap(),
            persistedExtras = log?.extras.orEmpty(),
            metricBooleanValues = result.profile.customMetrics
                .filter { it.type == CustomMetricType.BOOLEAN }
                .mapNotNull { metric ->
                    log?.extras?.get(metric.id)?.booleanOrNull?.let { metric.id to it }
                }.toMap(),
            metricNumberInputs = result.profile.customMetrics
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
    }

    private fun edit(transform: HealthUiState.() -> HealthUiState) = _uiState.update {
        it.transform().copy(actionError = null, message = null)
    }

    private fun fail(message: String) = _uiState.update { it.copy(actionError = message) }

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

private fun HealthProfile?.orEmptyMetrics(): List<CustomHealthMetric> = this?.customMetrics.orEmpty()

private fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
