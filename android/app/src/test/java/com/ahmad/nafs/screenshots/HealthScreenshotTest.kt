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
import com.ahmad.nafs.core.health.HealthTrendMetric
import com.ahmad.nafs.core.health.MealItem
import com.ahmad.nafs.core.health.SleepSession
import com.ahmad.nafs.ui.health.HealthActions
import com.ahmad.nafs.ui.health.HealthScreen
import com.ahmad.nafs.ui.health.HealthTrendsState
import com.ahmad.nafs.ui.health.HealthUiState
import kotlin.math.roundToInt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Health screen with fixed sample state: first-run baseline setup (skippable), a skipped baseline, an empty
 * (offline) day and a filled-in day scrolled to its tiles, meals, custom metrics and trends, plus a past
 * day being edited and the 30-day weight trend.
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
        HealthScreen(HealthSamples.todayState, HealthActions(), rememberLazyListState(initialFirstVisibleItemIndex = 4))
    }

    @Test fun healthTodayMeals() = captureScreen("60_health_today_meals") {
        HealthScreen(HealthSamples.todayState, HealthActions(), rememberLazyListState(initialFirstVisibleItemIndex = 9))
    }

    @Test fun healthTodayMetrics() = captureScreen("60_health_today_metrics") {
        HealthScreen(HealthSamples.todayState, HealthActions(), rememberLazyListState(initialFirstVisibleItemIndex = 15))
    }

    @Test fun healthBaselineSkipped() = captureScreen("60_health_baseline_skipped") {
        HealthScreen(HealthSamples.skippedState, HealthActions())
    }

    @Test fun healthPastDay() = captureScreen("60_health_past_day") {
        HealthScreen(HealthSamples.pastDayState, HealthActions())
    }

    @Test fun healthTrendsWater() = captureScreen("60_health_trends_water") {
        HealthScreen(HealthSamples.todayState, HealthActions(), rememberLazyListState(initialFirstVisibleItemIndex = 19))
    }

    @Test fun healthTrendsWeight() = captureScreen("60_health_trends_weight") {
        HealthScreen(HealthSamples.weightTrendState, HealthActions(), rememberLazyListState(initialFirstVisibleItemIndex = 19))
    }

    @Test fun healthTrendsSleep() = captureScreen("60_health_trends_sleep") {
        HealthScreen(HealthSamples.sleepTrendState, HealthActions(), rememberLazyListState(initialFirstVisibleItemIndex = 19))
    }

    @Test fun healthTrendsOfflineEmpty() = captureScreen("60_health_trends_offline_empty") {
        HealthScreen(HealthSamples.emptyDayState, HealthActions(), rememberLazyListState(initialFirstVisibleItemIndex = 19))
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

    /** A month of realistic, uneven logging: some days skipped, steps and weight not logged every day. */
    private val trendLogs: List<HealthLog> = (29 downTo 1).filter { it % 7 != 3 }.map { ago ->
        val week = ago % 7
        log.copy(
            id = "log-$ago",
            date = SampleData.today.minusDays(ago.toLong()),
            waterGlasses = listOf(6, 8, 5, 9, 7, 4, 8)[week],
            steps = listOf(6_420, 9_100, 4_300, 8_800, 10_200, 7_600, 5_100)[week].takeIf { ago % 5 != 2 },
            sleepHours = listOf(7.5, 6.2, 8.1, 7.0, 5.8, 8.6, 7.3)[week],
            sleepSessions = emptyList(),
            meals = emptyList(),
            weightKg = ((74.1 - (29 - ago) * 0.06) * 10).roundToInt().div(10.0).takeIf { ago % 3 == 0 },
            mood = listOf(7, 6, 8, 5, 7, 8, 6)[week],
            notes = null,
        )
    } + log

    private val trends = HealthTrendsState(logs = trendLogs, end = SampleData.today)

    val setupState = HealthUiState(
        userId = SampleData.USER,
        timezone = "Asia/Karachi",
        today = SampleData.today,
        profile = HealthProfile(userId = SampleData.USER, heightCm = null, weightKg = null, customMetrics = emptyList()),
        isLoading = false,
        profileHeight = "175",
        meals = emptyMeals,
    ).saved()

    val skippedState = setupState.copy(
        profile = HealthProfile(userId = SampleData.USER, heightCm = null, weightKg = 72.5, customMetrics = emptyList()),
        baselineSkipped = true,
        trends = HealthTrendsState(logs = emptyList(), end = SampleData.today),
    )

    val emptyDayState = HealthUiState(
        userId = SampleData.USER,
        timezone = "Asia/Karachi",
        today = SampleData.today,
        profile = profile.copy(customMetrics = emptyList()),
        isLoading = false,
        isOffline = true,
        meals = emptyMeals,
        trends = HealthTrendsState(logs = emptyList(), end = SampleData.today, isOffline = true),
    ).saved()

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
        trends = trends,
    ).saved()

    val weightTrendState = todayState.copy(trends = trends.copy(rangeDays = 30, metric = HealthTrendMetric.WEIGHT))

    val sleepTrendState = todayState.copy(trends = trends.copy(metric = HealthTrendMetric.SLEEP))

    /** Thursday's entry, opened from the day switcher, with one change not yet saved. */
    val pastDayState: HealthUiState = trendLogs.first { it.date == SampleData.today.minusDays(2) }.let { day ->
        HealthUiState(
            userId = SampleData.USER,
            timezone = "Asia/Karachi",
            today = SampleData.today,
            selectedDate = day.date,
            profile = profile.copy(customMetrics = emptyList()),
            log = day,
            isLoading = false,
            waterGlasses = day.waterGlasses,
            steps = day.steps?.toString().orEmpty(),
            legacySleepHours = day.sleepHours,
            meals = emptyMeals,
            dailyWeight = "",
            mood = day.mood,
            trends = trends,
        ).saved().copy(waterGlasses = day.waterGlasses + 1)
    }

    /** Marks the shown values as the loaded entry, as the ViewModel does after a load. */
    private fun HealthUiState.saved(): HealthUiState = copy(savedForm = form)
}
