package com.ahmad.nafs.screenshots

import android.app.Application
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ahmad.nafs.core.health.CustomHealthMetric
import com.ahmad.nafs.core.health.CustomMetricType
import com.ahmad.nafs.core.health.HealthLog
import com.ahmad.nafs.core.health.HealthMeal
import com.ahmad.nafs.core.health.HealthMealRules
import com.ahmad.nafs.core.health.HealthProfile
import com.ahmad.nafs.core.health.MealItem
import com.ahmad.nafs.core.health.SleepSession
import com.ahmad.nafs.ui.health.HealthActions
import com.ahmad.nafs.ui.health.HealthScreen
import com.ahmad.nafs.ui.health.HealthUiState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Health screen with fixed sample state: first-run baseline setup, an empty (offline) day and a
 * filled-in day, the last one also scrolled to its check-in tiles, meals and custom metrics.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A plain Application: the real one schedules WorkManager jobs that have no place in a render test.
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xhdpi", application = Application::class)
class HealthScreenshotTest : ScreenshotTestBase() {

    @Test fun healthSetup() = captureScreen("60_health_setup") {
        HealthScreen(HealthSamples.setupState, HealthActions())
    }

    @Test fun healthEmptyDay() = captureScreen("60_health_empty_day") {
        HealthScreen(HealthSamples.emptyDayState, HealthActions())
    }

    @Test fun healthToday() = captureScreen("60_health_today") {
        HealthScreen(HealthSamples.todayState, HealthActions())
    }

    @Test fun healthTodayCheckIn() = captureScreen("60_health_today_checkin") {
        HealthScreen(HealthSamples.todayState, HealthActions(), rememberLazyListState(initialFirstVisibleItemIndex = 3))
    }

    @Test fun healthTodayMeals() = captureScreen("60_health_today_meals") {
        HealthScreen(HealthSamples.todayState, HealthActions(), rememberLazyListState(initialFirstVisibleItemIndex = 8))
    }

    @Test fun healthTodayMetrics() = captureScreen("60_health_today_metrics") {
        HealthScreen(HealthSamples.todayState, HealthActions(), rememberLazyListState(initialFirstVisibleItemIndex = 14))
    }
}

/** Realistic health records for one day, private to these renders. */
private object HealthSamples {
    private val metrics = listOf(
        CustomHealthMetric(id = "vitamin-d", name = "Vitamin D", emoji = "💊", type = CustomMetricType.BOOLEAN),
        CustomHealthMetric(
            id = "coffee", name = "Coffee", emoji = "☕", type = CustomMetricType.COUNTER, target = 2.0, unit = "cups",
        ),
        CustomHealthMetric(id = "back-pain", name = "Back pain", emoji = "🩹", type = CustomMetricType.NUMBER, unit = "/10"),
    )

    private val profile = HealthProfile(
        userId = SampleData.USER,
        heightCm = 175.0,
        weightKg = 72.5,
        customMetrics = metrics,
    )

    private fun food(id: String, name: String) = MealItem(id = id, name = name, emoji = "🍽️")

    private val meals = listOf(
        HealthMeal(
            id = "breakfast-1", key = "breakfast", label = "Breakfast", emoji = "🌅",
            items = listOf(food("f1", "Paratha"), food("f2", "Chai"), food("f3", "Boiled egg")),
        ),
        HealthMeal(
            id = "lunch-1", key = "lunch", label = "Lunch", emoji = "☀️",
            items = listOf(food("f4", "Daal chawal"), food("f5", "Kachumber salad")),
        ),
        HealthMeal(id = "dinner-1", key = "dinner", label = "Dinner", emoji = "🌙", items = emptyList()),
        HealthMeal(id = "tea-1", key = "tea", label = "Tea time", emoji = "🫖", items = listOf(food("f6", "Dates"))),
    )

    private val sleep = listOf(
        SleepSession(id = "s1", start = "23:15", end = "06:05"),
        SleepSession(id = "s2", start = "14:00", end = "14:40"),
    )

    private val notes = "Slept well; a little stiff after the morning run."

    private val log = HealthLog(
        id = "log-1",
        userId = SampleData.USER,
        date = SampleData.today,
        waterGlasses = 5,
        steps = 6420,
        sleepHours = 7.5,
        sleepSessions = sleep,
        meals = meals,
        extras = emptyMap(),
        exerciseDone = true,
        exerciseMinutes = 35,
        weightKg = 72.2,
        mood = 7,
        notes = notes,
        createdAt = "2026-10-10T06:30:00Z",
        updatedAt = "2026-10-10T18:05:00Z",
    )

    private val emptyMeals = HealthMealRules.ensureCore(null) { key -> "$key-sample" }

    val setupState = HealthUiState(
        userId = SampleData.USER,
        timezone = "Asia/Karachi",
        today = SampleData.today,
        profile = HealthProfile(userId = SampleData.USER, heightCm = null, weightKg = null, customMetrics = emptyList()),
        isLoading = false,
        showProfileEditor = true,
        profileHeight = "175",
        meals = emptyMeals,
    )

    val emptyDayState = HealthUiState(
        userId = SampleData.USER,
        timezone = "Asia/Karachi",
        today = SampleData.today,
        profile = profile.copy(customMetrics = emptyList()),
        isLoading = false,
        isOffline = true,
        meals = emptyMeals,
    )

    val todayState = HealthUiState(
        userId = SampleData.USER,
        timezone = "Asia/Karachi",
        today = SampleData.today,
        profile = profile,
        log = log,
        isLoading = false,
        waterGlasses = 5,
        steps = "6420",
        sleepSessions = sleep,
        meals = meals,
        mealDrafts = mapOf("dinner-1" to "Chicken karahi"),
        metricBooleanValues = mapOf("vitamin-d" to true),
        metricNumberInputs = mapOf("coffee" to "1", "back-pain" to "3"),
        exerciseDone = true,
        exerciseMinutes = "35",
        dailyWeight = "72.2",
        mood = 7,
        notes = notes,
    )
}
