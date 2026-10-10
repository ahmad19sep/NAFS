package com.ahmad.nafs.core.health

import java.time.LocalDate
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

@Serializable
data class SleepSession(
    val id: String,
    val start: String,
    val end: String,
)

@Serializable
data class MealItem(
    val id: String,
    val name: String,
    val emoji: String,
)

@Serializable
data class HealthMeal(
    val id: String,
    val key: String,
    val label: String,
    val emoji: String,
    val items: List<MealItem>,
)

@Serializable
enum class CustomMetricType {
    @SerialName("boolean")
    BOOLEAN,

    @SerialName("counter")
    COUNTER,

    @SerialName("number")
    NUMBER,
}

@Serializable
data class CustomHealthMetric(
    val id: String,
    val name: String,
    val emoji: String,
    val type: CustomMetricType,
    val target: Double? = null,
    val unit: String? = null,
)

data class HealthProfile(
    val userId: String,
    val heightCm: Double?,
    val weightKg: Double?,
    val customMetrics: List<CustomHealthMetric>,
)

data class HealthLog(
    val id: String,
    val userId: String,
    val date: LocalDate,
    val waterGlasses: Int,
    val steps: Int?,
    val sleepHours: Double?,
    val sleepSessions: List<SleepSession>,
    val meals: List<HealthMeal>,
    val extras: Map<String, JsonPrimitive>,
    val exerciseDone: Boolean,
    val exerciseMinutes: Int?,
    val weightKg: Double?,
    val mood: Int?,
    val notes: String?,
    val createdAt: String,
    val updatedAt: String,
)

data class SaveHealthProfileIntent(
    val userId: String,
    val heightCm: Double,
    val weightKg: Double,
)

data class SaveCustomMetricsIntent(
    val userId: String,
    val metrics: List<CustomHealthMetric>,
)

data class SaveHealthLogIntent(
    val userId: String,
    val date: LocalDate,
    val waterGlasses: Int,
    val steps: Int?,
    val sleepHours: Double?,
    val sleepSessions: List<SleepSession>,
    val meals: List<HealthMeal>,
    val extras: Map<String, JsonPrimitive>,
    val exerciseDone: Boolean,
    val exerciseMinutes: Int?,
    val weightKg: Double?,
    val mood: Int?,
    val notes: String?,
    val expectedId: String? = null,
    val expectedUpdatedAt: String? = null,
)

object HealthValidator {
    fun profileError(intent: SaveHealthProfileIntent): String? = when {
        !intent.heightCm.isFinite() || intent.heightCm !in 80.0..250.0 ->
            "Height must be between 80 and 250 cm."
        !intent.weightKg.isFinite() || intent.weightKg !in 25.0..400.0 ->
            "Weight must be between 25 and 400 kg."
        else -> null
    }

    fun logError(intent: SaveHealthLogIntent): String? {
        val mealError = HealthMealRules.error(intent.meals)
        return when {
            intent.waterGlasses !in 0..100 -> "Water must be between 0 and 100 glasses."
            intent.steps != null && intent.steps !in 0..200_000 ->
                "Steps must be between 0 and 200,000."
            intent.sleepSessions.any { SleepSessionRules.minutes(it) == null } ->
                "Use a valid 24-hour time for every sleep period."
            intent.sleepSessions.any { SleepSessionRules.minutes(it) == 0 } ->
                "Sleep start and end times must be different."
            SleepSessionRules.totalMinutes(intent.sleepSessions) > 1_440 ->
                "Total sleep cannot exceed 24 hours."
            intent.sleepHours != null &&
                (!intent.sleepHours.isFinite() || intent.sleepHours !in 0.0..24.0) ->
                "Sleep must be between 0 and 24 hours."
            mealError != null -> mealError
            intent.extras.size > 20 -> "Track no more than 20 custom health metrics."
            intent.extras.any { (id, value) ->
                id.isBlank() || id.length > 80 ||
                    (value.booleanOrNull == null &&
                        (value.doubleOrNull == null || !value.doubleOrNull!!.isFinite()))
            } -> "Custom metric values must be valid numbers or yes/no values."
            intent.exerciseDone &&
                (intent.exerciseMinutes == null || intent.exerciseMinutes !in 1..1_440) ->
                "Enter exercise minutes between 1 and 1,440."
            !intent.exerciseDone && intent.exerciseMinutes != null ->
                "Exercise minutes require exercise to be marked done."
            intent.weightKg != null &&
                (!intent.weightKg.isFinite() || intent.weightKg !in 25.0..400.0) ->
                "Weight must be between 25 and 400 kg."
            intent.mood != null && intent.mood !in 1..10 -> "Mood must be between 1 and 10."
            intent.notes != null && intent.notes.length > 2_000 ->
                "Keep health notes under 2,000 characters."
            (intent.expectedId == null) != (intent.expectedUpdatedAt == null) ->
                "Health log version is incomplete. Reload today's entry."
            else -> null
        }
    }

    fun customMetricsError(intent: SaveCustomMetricsIntent): String? = when {
        intent.metrics.size > 20 -> "Track no more than 20 custom health metrics."
        intent.metrics.map { it.id }.distinct().size != intent.metrics.size ->
            "Custom health metric IDs must be unique."
        intent.metrics.any { it.id.isBlank() || it.id.length > 80 } ->
            "Custom health metric IDs are invalid."
        intent.metrics.any { it.name.isBlank() || it.name.length > 60 } ->
            "Metric names must be between 1 and 60 characters."
        intent.metrics.any { it.emoji.isBlank() || it.emoji.length > 16 } ->
            "Choose one short emoji for each metric."
        intent.metrics.any { it.target != null && (!it.target.isFinite() || it.target <= 0.0) } ->
            "Metric targets must be greater than zero."
        intent.metrics.any { it.unit != null && it.unit.length > 24 } ->
            "Metric units must be 24 characters or fewer."
        intent.metrics.any {
            it.type == CustomMetricType.BOOLEAN && (it.target != null || it.unit != null)
        } -> "Yes/no metrics cannot have a target or unit."
        else -> null
    }
}

object HealthWriteRules {
    fun isDuplicateKeyError(message: String?): Boolean =
        message?.contains("23505", ignoreCase = true) == true ||
            DUPLICATE_PATTERN.containsMatchIn(message.orEmpty())

    private val DUPLICATE_PATTERN = Regex(
        "duplicate key|unique constraint|already exists",
        RegexOption.IGNORE_CASE,
    )
}

object HealthMealRules {
    data class Preset(val key: String, val label: String, val emoji: String)

    val core = listOf(
        Preset("breakfast", "Breakfast", "🌅"),
        Preset("lunch", "Lunch", "☀️"),
        Preset("dinner", "Dinner", "🌙"),
    )
    val extras = listOf(
        Preset("snack", "Snack", "🍿"),
        Preset("brunch", "Brunch", "🥐"),
        Preset("tea", "Tea time", "🫖"),
        Preset("sehri", "Sehri", "🌒"),
        Preset("iftar", "Iftar", "🌆"),
        Preset("latenight", "Late night", "🌃"),
    )

    fun ensureCore(
        stored: List<HealthMeal>?,
        idFactory: (String) -> String = { key -> "$key-${UUID.randomUUID()}" },
    ): List<HealthMeal> {
        val existing = stored.orEmpty()
        val required = core.map { preset ->
            existing.firstOrNull { it.key == preset.key }?.copy(
                label = preset.label,
                emoji = preset.emoji,
            ) ?: HealthMeal(
                id = idFactory(preset.key),
                key = preset.key,
                label = preset.label,
                emoji = preset.emoji,
                items = emptyList(),
            )
        }
        return required + existing.filterNot { meal -> core.any { it.key == meal.key } }
    }

    fun isCore(key: String): Boolean = core.any { it.key == key }

    fun eatenCount(meals: List<HealthMeal>): Int = meals.count { it.items.isNotEmpty() }

    fun error(meals: List<HealthMeal>): String? = when {
        meals.size > 20 -> "Track no more than 20 meals in one day."
        meals.map { it.id }.distinct().size != meals.size -> "Meal IDs must be unique."
        meals.any {
            it.id.isBlank() || it.id.length > 80 || it.key.isBlank() || it.key.length > 40 ||
                it.label.isBlank() || it.label.length > 40 || it.emoji.isBlank() || it.emoji.length > 16
        } -> "Meal details are invalid."
        meals.any { it.items.size > 50 } -> "A meal can contain no more than 50 food items."
        meals.flatMap { it.items }.any {
            it.id.isBlank() || it.id.length > 80 || it.name.isBlank() || it.name.length > 80 ||
                it.emoji.isBlank() || it.emoji.length > 16
        } -> "Food names must be between 1 and 80 characters."
        else -> null
    }
}

object HealthMetrics {
    fun bmi(heightCm: Double?, weightKg: Double?): Double? {
        if (heightCm == null || weightKg == null || heightCm <= 0.0 || weightKg <= 0.0) return null
        val heightMeters = heightCm / 100.0
        return ((weightKg / (heightMeters * heightMeters)) * 10.0).roundToInt() / 10.0
    }
}

object SleepSessionRules {
    fun parseMinutes(value: String): Int? {
        val match = TIME_PATTERN.find(value.trim()) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    fun minutes(session: SleepSession): Int? {
        val start = parseMinutes(session.start) ?: return null
        val end = parseMinutes(session.end) ?: return null
        if (end == start) return 0
        return if (end > start) end - start else end + 1_440 - start
    }

    fun totalMinutes(sessions: List<SleepSession>): Int = sessions.sumOf { minutes(it) ?: 0 }

    fun hours(sessions: List<SleepSession>): Double? {
        if (sessions.isEmpty()) return null
        return (totalMinutes(sessions).toDouble() / 6.0).roundToInt() / 10.0
    }

    fun formatMinutes(minutes: Int): String {
        val hours = minutes / 60
        val remainder = minutes % 60
        return when {
            hours == 0 -> "${remainder}m"
            remainder == 0 -> "${hours}h"
            else -> "${hours}h ${remainder}m"
        }
    }

    private val TIME_PATTERN = Regex("^(\\d{1,2}):(\\d{2})(?::\\d{2})?$")
}

sealed interface HealthLoadResult {
    data class Success(
        val profile: HealthProfile,
        val log: HealthLog?,
        val isOffline: Boolean,
    ) : HealthLoadResult

    data class Failure(val message: String) : HealthLoadResult
}

sealed interface HealthActionResult {
    data class ProfileSaved(val profile: HealthProfile) : HealthActionResult
    data class LogSaved(val log: HealthLog) : HealthActionResult
    data object LogConflict : HealthActionResult
    data class Failure(val message: String) : HealthActionResult
}
