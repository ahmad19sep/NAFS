package com.ahmad.nafs.ui.health

import com.ahmad.nafs.core.health.HealthActionResult
import com.ahmad.nafs.core.health.HealthDevicePreferences
import com.ahmad.nafs.core.health.HealthGoals
import com.ahmad.nafs.core.health.HealthLoadResult
import com.ahmad.nafs.core.health.HealthLog
import com.ahmad.nafs.core.health.HealthPreferenceStore
import com.ahmad.nafs.core.health.HealthProfile
import com.ahmad.nafs.core.health.HealthRangeResult
import com.ahmad.nafs.core.health.HealthRepository
import com.ahmad.nafs.core.health.SaveCustomMetricsIntent
import com.ahmad.nafs.core.health.SaveHealthLogIntent
import com.ahmad.nafs.core.health.SaveHealthProfileIntent
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HealthViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.parse("2026-10-10")
    private val clock = Clock.fixed(Instant.parse("2026-10-10T08:00:00Z"), ZoneOffset.UTC)

    private class Repository : HealthRepository {
        var profile = HealthProfile("user", heightCm = 175.0, weightKg = 72.5, customMetrics = emptyList())
        val logs = mutableMapOf<LocalDate, HealthLog>()
        val saved = mutableListOf<SaveHealthLogIntent>()
        val loaded = mutableListOf<LocalDate>()
        var offline = false
        var failDay: LocalDate? = null
        var rangeFailure: String? = null

        override suspend fun load(userId: String, date: LocalDate): HealthLoadResult {
            loaded += date
            if (date == failDay) return HealthLoadResult.Failure("Unable to load health data.")
            return HealthLoadResult.Success(profile, logs[date], offline)
        }

        override suspend fun loadRange(userId: String, from: LocalDate, to: LocalDate): HealthRangeResult =
            rangeFailure?.let { HealthRangeResult.Failure(it) }
                ?: HealthRangeResult.Success(logs.values.filter { it.date in from..to }.sortedBy { it.date }, offline)

        override suspend fun saveProfile(intent: SaveHealthProfileIntent): HealthActionResult {
            profile = profile.copy(heightCm = intent.heightCm, weightKg = intent.weightKg)
            return HealthActionResult.ProfileSaved(profile)
        }

        override suspend fun saveCustomMetrics(intent: SaveCustomMetricsIntent): HealthActionResult =
            HealthActionResult.ProfileSaved(profile.copy(customMetrics = intent.metrics))

        override suspend fun saveLog(intent: SaveHealthLogIntent): HealthActionResult {
            saved += intent
            val log = HealthLog(
                id = intent.expectedId ?: "log-${intent.date}", userId = intent.userId, date = intent.date,
                waterGlasses = intent.waterGlasses, steps = intent.steps, sleepHours = intent.sleepHours,
                sleepSessions = intent.sleepSessions, meals = intent.meals, extras = intent.extras,
                exerciseDone = intent.exerciseDone, exerciseMinutes = intent.exerciseMinutes, weightKg = intent.weightKg,
                mood = intent.mood, notes = intent.notes?.trim()?.takeIf(String::isNotEmpty),
                createdAt = "2026-10-10T06:00:00Z", updatedAt = "2026-10-10T09:00:00Z",
            )
            logs[intent.date] = log
            val weight = intent.weightKg ?: return HealthActionResult.LogSaved(log)
            profile = profile.copy(weightKg = weight)
            return HealthActionResult.LogSaved(log, profile = profile)
        }

        override suspend fun clear(userId: String) = Unit
    }

    private class Preferences : HealthPreferenceStore {
        val stored = mutableMapOf<String, HealthDevicePreferences>()
        override suspend fun read(userId: String) = stored[userId] ?: HealthDevicePreferences()
        override suspend fun write(userId: String, preferences: HealthDevicePreferences) {
            stored[userId] = preferences
        }
    }

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    private fun model(repository: Repository, preferences: Preferences = Preferences()) =
        HealthViewModel(repository, preferences).also { it.clock = clock }

    @Test fun `goals default to the web values and are kept per account on the device`() = runTest(dispatcher) {
        val preferences = Preferences()
        val first = model(Repository(), preferences)
        first.initialize("user", "UTC"); runCurrent()
        assertEquals(HealthGoals(8, 8_000), first.uiState.value.goals)
        first.openGoalEditor()
        repeat(20) { first.adjustWaterGoal(1) }
        first.adjustStepsGoal(-500)
        first.saveGoals(); runCurrent()
        assertEquals(HealthGoals(16, 7_500), first.uiState.value.goals)
        val restarted = model(Repository(), preferences)
        restarted.initialize("user", "UTC"); runCurrent()
        assertEquals(HealthGoals(16, 7_500), restarted.uiState.value.goals)
        val other = model(Repository(), preferences)
        other.initialize("someone-else", "UTC"); runCurrent()
        assertEquals(HealthGoals(), other.uiState.value.goals)
    }

    @Test fun `a missing baseline never blocks logging and can be skipped`() = runTest(dispatcher) {
        val repository = Repository().apply { profile = profile.copy(heightCm = null, weightKg = null) }
        val preferences = Preferences()
        val model = model(repository, preferences)
        model.initialize("user", "UTC"); runCurrent()
        assertTrue(model.uiState.value.showBaselineSetup)
        assertNull(model.uiState.value.bmi)
        assertTrue(model.uiState.value.canSave)
        model.setWater(3); model.saveLog(); runCurrent()
        assertEquals(1, repository.saved.size)
        model.skipBaseline(); runCurrent()
        assertFalse(model.uiState.value.showBaselineSetup)
        assertTrue(preferences.stored.getValue("user").baselineSkipped)
    }

    @Test fun `saving a weight moves the profile weight so BMI follows it`() = runTest(dispatcher) {
        val repository = Repository()
        val model = model(repository)
        model.initialize("user", "UTC"); runCurrent()
        val before = model.uiState.value.bmi
        model.setDailyWeight("70"); model.saveLog(); runCurrent()
        val state = model.uiState.value
        assertEquals(70.0, state.profile?.weightKg)
        assertNotNull(before)
        assertTrue(state.bmi!! < before!!)
        assertEquals("Today's health check-in was saved. BMI now uses 70 kg.", state.message)
        assertFalse(state.hasChanges)
    }

    @Test fun `a past day loads and saves under its own date`() = runTest(dispatcher) {
        val repository = Repository()
        val model = model(repository)
        model.initialize("user", "UTC"); runCurrent()
        val yesterday = today.minusDays(1)
        model.selectDate(yesterday)
        assertTrue(model.uiState.value.isLoadingDay)
        assertFalse(model.uiState.value.canSave)
        runCurrent()
        assertEquals(yesterday, model.uiState.value.selectedDate)
        assertFalse(model.uiState.value.isLoadingDay)
        model.setWater(6); model.saveLog(); runCurrent()
        assertEquals(yesterday, repository.saved.single().date)
        assertTrue(yesterday in model.uiState.value.savedDays)
        assertEquals(6, model.uiState.value.trends.logs.single { it.date == yesterday }.waterGlasses)
    }

    @Test fun `days outside the last week cannot be opened`() = runTest(dispatcher) {
        val repository = Repository()
        val model = model(repository)
        model.initialize("user", "UTC"); runCurrent()
        model.selectDate(today.minusDays(7))
        model.selectDate(today.plusDays(1))
        runCurrent()
        assertEquals(today, model.uiState.value.selectedDate)
        assertEquals(listOf(today), repository.loaded)
        assertEquals(7, model.uiState.value.days.size)
    }

    @Test fun `switching day with unsaved changes asks first and keeps them until confirmed`() = runTest(dispatcher) {
        val repository = Repository()
        val model = model(repository)
        model.initialize("user", "UTC"); runCurrent()
        model.setWater(4)
        assertTrue(model.uiState.value.hasChanges)
        model.selectDate(today.minusDays(2)); runCurrent()
        assertEquals(today.minusDays(2), model.uiState.value.pendingDate)
        assertEquals(today, model.uiState.value.selectedDate)
        model.cancelDiscardChanges()
        assertEquals(4, model.uiState.value.waterGlasses)
        // Returning to the app never discards them either.
        model.refresh(); runCurrent()
        assertEquals(4, model.uiState.value.waterGlasses)
        model.selectDate(today.minusDays(2)); model.confirmDiscardChanges(); runCurrent()
        assertEquals(today.minusDays(2), model.uiState.value.selectedDate)
        assertEquals(0, model.uiState.value.waterGlasses)
    }

    @Test fun `a day that fails to load shows an error instead of an empty form`() = runTest(dispatcher) {
        val repository = Repository().apply { failDay = today.minusDays(3) }
        val model = model(repository)
        model.initialize("user", "UTC"); runCurrent()
        model.selectDate(today.minusDays(3)); runCurrent()
        val state = model.uiState.value
        assertNotNull(state.dayError)
        assertFalse(state.canSave)
        model.setWater(5)
        assertEquals(0, model.uiState.value.waterGlasses)
    }

    @Test fun `offline trends say so and failures keep earlier data`() = runTest(dispatcher) {
        val repository = Repository().apply { offline = true }
        val model = model(repository)
        model.initialize("user", "UTC"); runCurrent()
        assertTrue(model.uiState.value.trends.isOffline)
        assertTrue(model.uiState.value.trends.hasLoaded)
        repository.rangeFailure = "Trends need a connection."
        model.retryTrends(); runCurrent()
        assertEquals("Trends need a connection.", model.uiState.value.trends.error)
        assertTrue(model.uiState.value.trends.hasLoaded)
    }
}
