package com.ahmad.nafs.core.health

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Personal daily targets. The web uses fixed values (8 glasses, 8,000 steps, 7–9 hours of sleep);
 * there is no server column for them, so on Android they are adjustable and kept on this device.
 */
data class HealthGoals(
    val waterGlasses: Int = DEFAULT_WATER,
    val steps: Int = DEFAULT_STEPS,
) {
    /** Clamps to the supported ranges and rounds steps to the nearest [STEP_INCREMENT]. */
    fun normalized(): HealthGoals = HealthGoals(
        waterGlasses = waterGlasses.coerceIn(MIN_WATER, MAX_WATER),
        steps = ((steps.coerceIn(MIN_STEPS, MAX_STEPS) / STEP_INCREMENT.toDouble()).roundToInt() * STEP_INCREMENT)
            .coerceIn(MIN_STEPS, MAX_STEPS),
    )

    companion object {
        const val DEFAULT_WATER = 8
        const val MIN_WATER = 4
        const val MAX_WATER = 16
        const val DEFAULT_STEPS = 8_000
        const val MIN_STEPS = 1_000
        const val MAX_STEPS = 30_000
        const val STEP_INCREMENT = 500
        const val SLEEP_MIN_MINUTES = 7 * 60
        const val SLEEP_MAX_MINUTES = 9 * 60
    }
}

/** How a night's total compares with the 7–9 hour guide. Feedback is gentle; it is a guide, not a score. */
enum class SleepFeedback { NONE, SHORT, IN_RANGE, LONG }

object HealthGoalRules {
    fun progress(value: Double?, target: Double): Float =
        if (value == null || target <= 0.0 || !value.isFinite()) 0f else (value / target).toFloat().coerceIn(0f, 1f)

    fun met(value: Double?, target: Double): Boolean = value != null && target > 0.0 && value >= target

    /** Whole percent of the goal, capped so a typo of a huge number stays readable. */
    fun percent(value: Int, target: Int): Int =
        if (target <= 0) 0 else (value.coerceIn(0, 1_000_000).toLong() * 100L / target).toInt()

    fun sleepFeedback(totalMinutes: Int?): SleepFeedback = when {
        totalMinutes == null || totalMinutes <= 0 -> SleepFeedback.NONE
        totalMinutes < HealthGoals.SLEEP_MIN_MINUTES -> SleepFeedback.SHORT
        totalMinutes > HealthGoals.SLEEP_MAX_MINUTES -> SleepFeedback.LONG
        else -> SleepFeedback.IN_RANGE
    }

    fun inSleepGuide(hours: Double?): Boolean =
        hours != null && sleepFeedback((hours * 60.0).roundToInt()) == SleepFeedback.IN_RANGE
}

/** Device-only choices for one account. */
data class HealthDevicePreferences(
    val goals: HealthGoals = HealthGoals(),
    val baselineSkipped: Boolean = false,
)

interface HealthPreferenceStore {
    suspend fun read(userId: String): HealthDevicePreferences
    suspend fun write(userId: String, preferences: HealthDevicePreferences)
}

@Singleton
class SharedHealthPreferenceStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : HealthPreferenceStore {
    override suspend fun read(userId: String): HealthDevicePreferences = withContext(Dispatchers.IO) {
        val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        HealthDevicePreferences(
            goals = HealthGoals(
                waterGlasses = preferences.getInt(key(userId, WATER), HealthGoals.DEFAULT_WATER),
                steps = preferences.getInt(key(userId, STEPS), HealthGoals.DEFAULT_STEPS),
            ).normalized(),
            baselineSkipped = preferences.getBoolean(key(userId, BASELINE_SKIPPED), false),
        )
    }

    override suspend fun write(userId: String, preferences: HealthDevicePreferences) = withContext(Dispatchers.IO) {
        val goals = preferences.goals.normalized()
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit(commit = true) {
            putInt(key(userId, WATER), goals.waterGlasses)
            putInt(key(userId, STEPS), goals.steps)
            putBoolean(key(userId, BASELINE_SKIPPED), preferences.baselineSkipped)
        }
    }

    private fun key(userId: String, name: String) = "$userId/$name"

    private companion object {
        const val FILE = "nafs_health_goals"
        const val WATER = "water_glasses"
        const val STEPS = "steps"
        const val BASELINE_SKIPPED = "baseline_skipped"
    }
}
