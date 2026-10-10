package com.ahmad.nafs.core.deen

import java.time.LocalDate
import kotlin.math.floor
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

enum class Prayer(val label: String) {
    FAJR("Fajr"), DHUHR("Dhuhr"), ASR("Asr"), MAGHRIB("Maghrib"), ISHA("Isha"),
}

enum class PrayerStatus(val value: Int, val label: String) {
    MISSED(0, "Missed"), ALONE(1, "Alone"), JAMAAT(2, "Jamaat"),
}

@Serializable
data class PrayerLog(
    val id: String,
    @SerialName("user_id") val userId: String,
    val date: String,
    val fajr: Int = 0,
    val dhuhr: Int = 0,
    val asr: Int = 0,
    val maghrib: Int = 0,
    val isha: Int = 0,
    @SerialName("extra_prayers") val extras: List<JsonObject> = emptyList(),
) {
    fun status(prayer: Prayer): Int = when (prayer) {
        Prayer.FAJR -> fajr
        Prayer.DHUHR -> dhuhr
        Prayer.ASR -> asr
        Prayer.MAGHRIB -> maghrib
        Prayer.ISHA -> isha
    }

    fun withStatus(prayer: Prayer, status: PrayerStatus): PrayerLog = when (prayer) {
        Prayer.FAJR -> copy(fajr = status.value)
        Prayer.DHUHR -> copy(dhuhr = status.value)
        Prayer.ASR -> copy(asr = status.value)
        Prayer.MAGHRIB -> copy(maghrib = status.value)
        Prayer.ISHA -> copy(isha = status.value)
    }

    val prayedCount: Int get() = Prayer.entries.count { status(it) > 0 }
    val jamaatCount: Int get() = Prayer.entries.count { status(it) == 2 }
    val prayerPoints: Int get() = Prayer.entries.sumOf(::status)
}

/** An activity the web app suggests. A counter has a [target] and [unit]; the rest are yes/no. */
data class DeenExtraSuggestion(val name: String, val emoji: String, val weight: Int, val target: Int? = null, val unit: String? = null) {
    val isCounter: Boolean get() = target != null
}

/** Keep the original JSON object when changing a field, including fields added by the web app. */
object DeenExtraRules {
    /** The web app's suggestions with its emoji, weights, targets and units, in its order. */
    val catalog = listOf(
        DeenExtraSuggestion("Tahajjud", "🌃", 3),
        DeenExtraSuggestion("Witr", "🤲", 2),
        DeenExtraSuggestion("Sunnah (Rawatib)", "✨", 2),
        DeenExtraSuggestion("Duha", "🌅", 2),
        DeenExtraSuggestion("Tarawih", "🕌", 3),
        DeenExtraSuggestion("Quran", "📖", 3, target = 5, unit = "pages"),
        DeenExtraSuggestion("Dhikr", "📿", 2, target = 100, unit = "count"),
        DeenExtraSuggestion("Hadith", "📜", 1, target = 1, unit = "hadith"),
        DeenExtraSuggestion("Islamic Learning", "🎓", 2, target = 15, unit = "mins"),
        DeenExtraSuggestion("Listen to Quran", "🎧", 1, target = 10, unit = "mins"),
        DeenExtraSuggestion("Sadaqah", "💝", 2),
        DeenExtraSuggestion("Sunnah Fast", "🌙", 3),
        DeenExtraSuggestion("Helped someone", "🤝", 1),
        DeenExtraSuggestion("Istighfar (100x)", "🤲", 1),
    )

    /** Yes/no suggestions and their weights. */
    val suggestions: LinkedHashMap<String, Int> =
        catalog.filterNot { it.isCounter }.associateTo(LinkedHashMap()) { it.name to it.weight }

    /** The highest counter value the validator accepts. */
    const val MAX_COUNT = 1_000_000.0

    fun name(item: JsonObject): String = (item["name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
    fun type(item: JsonObject): String? = (item["type"] as? JsonPrimitive)?.contentOrNull
    fun number(item: JsonObject, field: String, fallback: Double = 0.0): Double =
        (item[field] as? JsonPrimitive)?.doubleOrNull ?: fallback

    fun done(item: JsonObject): Boolean = (item["done"] as? JsonPrimitive)?.booleanOrNull == true
    fun status(item: JsonObject): Int = (item["status"] as? JsonPrimitive)?.intOrNull ?: 0
    fun unit(item: JsonObject): String = (item["unit"] as? JsonPrimitive)?.contentOrNull.orEmpty().trim()
    fun patch(item: JsonObject, field: String, value: JsonPrimitive): JsonObject = JsonObject(item + (field to value))

    fun newPrayer(name: String): JsonObject = JsonObject(
        mapOf(
            "name" to JsonPrimitive(name.trim()),
            "type" to JsonPrimitive("boolean"),
            "emoji" to JsonPrimitive(catalog.firstOrNull { it.name == name.trim() && !it.isCounter }?.emoji ?: "✨"),
            "score_weight" to JsonPrimitive(suggestions[name.trim()] ?: 1),
            "done" to JsonPrimitive(false),
        ),
    )

    /** A counter in the shape the web app writes: type "count" with value, target and unit. */
    fun newCounter(suggestion: DeenExtraSuggestion): JsonObject = JsonObject(
        mapOf(
            "name" to JsonPrimitive(suggestion.name),
            "type" to JsonPrimitive("count"),
            "emoji" to JsonPrimitive(suggestion.emoji),
            "score_weight" to JsonPrimitive(suggestion.weight),
            "value" to JsonPrimitive(0),
            "target" to JsonPrimitive(suggestion.target ?: 1),
            "unit" to JsonPrimitive(suggestion.unit.orEmpty()),
        ),
    )

    fun newExtra(suggestion: DeenExtraSuggestion): JsonObject =
        if (suggestion.isCounter) newCounter(suggestion) else newPrayer(suggestion.name)

    /** A counter after one −/+ step: a whole number from 0 to the validator's limit; other fields are kept. */
    fun step(item: JsonObject, by: Int): JsonObject {
        val next = (floor(number(item, "value")) + by).coerceIn(0.0, MAX_COUNT)
        return patch(item, "value", JsonPrimitive(next.toLong()))
    }

    fun points(item: JsonObject): Pair<Int, Int> = when (type(item)) {
        "boolean" -> number(item, "score_weight", 1.0).toInt().let { weight -> (if (done(item)) weight else 0) to weight }
        "count" -> number(item, "score_weight", 1.0).toInt().let { weight ->
            (if (number(item, "value") >= number(item, "target", 1.0)) weight else 0) to weight
        }
        null -> status(item) to 2
        else -> 0 to 0
    }
}

object DeenValidator {
    fun error(log: PrayerLog): String? {
        if (log.userId.isBlank() || log.id.isBlank()) return "Your prayer record has no account identity. Reload and retry."
        if (runCatching { LocalDate.parse(log.date) }.isFailure) return "Choose a valid account date."
        if (Prayer.entries.any { log.status(it) !in 0..2 }) return "Prayer status must be missed, alone, or jamaat."
        if (log.extras.size > 50) return "Keep at most 50 extra activities in a day."
        return try {
            for (item in log.extras) {
                val name = DeenExtraRules.name(item)
                if (name.isBlank() || name.length > 80) return "Extra activity names must contain 1–80 characters."
                when (DeenExtraRules.type(item)) {
                    "boolean", "count" -> {
                        val weight = DeenExtraRules.number(item, "score_weight", 1.0)
                        if (!weight.isFinite() || weight !in 1.0..10.0 || weight % 1.0 != 0.0) return "Extra activity weights must be 1–10."
                        if (DeenExtraRules.type(item) == "count") {
                            val value = DeenExtraRules.number(item, "value")
                            val target = DeenExtraRules.number(item, "target", 1.0)
                            if (!value.isFinite() || value !in 0.0..1_000_000.0 || !target.isFinite() || target !in 0.000001..1_000_000.0) {
                                return "Enter a nonnegative value and a positive target for ${name}."
                            }
                        }
                    }
                    null -> if (DeenExtraRules.status(item) !in 0..2) return "Extra prayer status must be missed, alone, or jamaat."
                    // Preserve future web activity types without modifying their payload.
                    else -> Unit
                }
            }
            null
        } catch (_: IllegalArgumentException) {
            "An extra activity could not be read. Reload your record."
        }
    }
}

sealed interface DeenLoadResult {
    data class Success(val log: PrayerLog?, val isOffline: Boolean) : DeenLoadResult
    data class Failure(val message: String) : DeenLoadResult
}

sealed interface DeenSaveResult {
    data class Saved(val log: PrayerLog) : DeenSaveResult
    data object Conflict : DeenSaveResult
    data class Failure(val message: String) : DeenSaveResult
}
