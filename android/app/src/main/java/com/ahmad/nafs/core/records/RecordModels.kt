package com.ahmad.nafs.core.records

import java.time.LocalDate
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

enum class FieldKind { TEXT, NUMBER, DATE, TIME, APPS }
data class RecordField(val key: String, val label: String, val kind: FieldKind = FieldKind.TEXT,
    val required: Boolean = false, val min: Double = 0.0, val max: Double = 2000.0, val initial: String = "")

enum class RecordFeature(val table: String, val title: String, val daily: Boolean, val fields: List<RecordField>) {
    DREAM("dreams", "Dreams & mapping", false, listOf(
        RecordField("statement", "What are you building toward?", required = true, max = 2000.0),
        RecordField("why", "Why it matters", max = 2000.0),
        RecordField("dream_date", "Target date (YYYY-MM-DD)", FieldKind.DATE, true),
        RecordField("total_hours_required", "Estimated hours", FieldKind.NUMBER, true, 1.0, 1_000_000.0, "1000"),
    )),
    LOG("daily_logs", "Daily log", true, listOf(
        RecordField("dream_work_hours", "Focused work hours", FieldKind.NUMBER, true, 0.0, 24.0, "0"),
        RecordField("study_hours", "Study hours", FieldKind.NUMBER, true, 0.0, 24.0, "0"),
        RecordField("screen_time_mins", "Screen time minutes", FieldKind.NUMBER, true, 0.0, 1440.0, "0"),
        RecordField("sleep_start", "Sleep start (HH:mm)", FieldKind.TIME),
        RecordField("sleep_end", "Sleep end (HH:mm)", FieldKind.TIME),
        RecordField("mood", "Mood (1–5)", FieldKind.NUMBER, false, 1.0, 5.0),
        RecordField("reflection_text", "Reflection", max = 10000.0),
    )),
    QURAN("quran_log", "Quran journal", true, listOf(
        RecordField("pages_read", "Pages read", FieldKind.NUMBER, true, 0.0, 604.0, "0"),
        RecordField("surah", "Surah", max = 100.0),
        RecordField("ayah_from", "From ayah", FieldKind.NUMBER, false, 1.0, 286.0),
        RecordField("ayah_to", "To ayah", FieldKind.NUMBER, false, 1.0, 286.0),
        RecordField("notes", "Reflection", max = 5000.0),
    )),
    SCREEN("screentime_logs", "Screen time", true, listOf(
        RecordField("total_mins", "Total minutes", FieldKind.NUMBER, true, 0.0, 1440.0, "0"),
        RecordField("apps", "One app per line: App name: minutes", FieldKind.APPS),
    )),
    NOTE("app_notes", "Bugs & ideas", false, listOf(
        RecordField("kind", "Kind (bug or idea)", required = true, max = 4.0, initial = "idea"),
        RecordField("title", "Title", required = true, max = 200.0),
        RecordField("detail", "Details", max = 2000.0),
        RecordField("where_seen", "Where you saw it", max = 80.0),
        RecordField("status", "Status (open, done, dismissed)", required = true, max = 9.0, initial = "open"),
    ));
    companion object { fun fromRoute(route: String) = entries.firstOrNull { it.name.lowercase() == route } }
}

@Serializable
data class RecordIntent(val feature: String, val id: String, val date: String?, val patch: JsonObject, val expected: JsonObject?)
@Serializable
data class RecordSnapshot(val rows: List<JsonObject> = emptyList(), val draft: Map<String, String>? = null,
    val editingId: String? = null, val date: String? = null, val expected: JsonObject? = null, val pending: RecordIntent? = null)

object RecordRules {
    fun text(row: JsonObject?, key: String) = (row?.get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
    fun patch(feature: RecordFeature, values: Map<String, String>, today: LocalDate): JsonObject {
        val patch = buildJsonObject {
            feature.fields.forEach { field ->
                val value = values[field.key].orEmpty().trim()
                require(!field.required || value.isNotBlank()) { "${field.label} is required." }
                if (value.isBlank()) { put(field.key, if (field.kind == FieldKind.APPS) JsonArray(emptyList()) else JsonNull); return@forEach }
                when (field.kind) {
                    FieldKind.NUMBER -> {
                        val number = value.toDoubleOrNull()
                        require(number != null && number.isFinite() && number in field.min..field.max) { "${field.label} must be between ${field.min} and ${field.max}." }
                        if (field.key in setOf("mood", "ayah_from", "ayah_to", "screen_time_mins", "total_mins")) {
                            require(number % 1.0 == 0.0) { "${field.label} must be a whole number." }
                            put(field.key, number.toInt())
                        } else put(field.key, number)
                    }
                    FieldKind.DATE -> { val date = runCatching { LocalDate.parse(value) }.getOrNull(); require(date != null && date > today) { "Choose a future target date." }; put(field.key, value) }
                    FieldKind.TIME -> { require(Regex("^(?:[01][0-9]|2[0-3]):[0-5][0-9]$").matches(value)) { "Use HH:mm for ${field.label}." }; put(field.key, value) }
                    FieldKind.APPS -> put(field.key, parseApps(value))
                    FieldKind.TEXT -> { require(value.length <= field.max.toInt()) { "${field.label} is too long." }; put(field.key, value) }
                }
            }
        }
        if (feature == RecordFeature.NOTE) {
            require(text(patch, "kind") in setOf("bug", "idea")) { "Choose bug or idea." }
            require(text(patch, "status") in setOf("open", "done", "dismissed")) { "Choose open, done, or dismissed." }
        }
        if (feature == RecordFeature.QURAN) {
            val from = text(patch, "ayah_from").toIntOrNull(); val to = text(patch, "ayah_to").toIntOrNull()
            require((from == null) == (to == null) && (from == null || to!! >= from)) { "Enter a valid start and end ayah." }
        }
        if (feature == RecordFeature.LOG) {
            require(text(patch, "dream_work_hours").toDouble() + text(patch, "study_hours").toDouble() <= 24) { "Work and study must total at most 24 hours." }
        }
        if (feature == RecordFeature.SCREEN) {
            val apps = patch["apps"] as JsonArray
            require(apps.sumOf { text(it as JsonObject, "minutes").toInt() } <= text(patch, "total_mins").toInt()) { "App minutes cannot exceed total screen time." }
        }
        return patch
    }
    fun parseApps(value: String): JsonArray {
        val lines = value.lines().filter { it.isNotBlank() }
        require(lines.size <= 40) { "Enter at most 40 apps." }
        return JsonArray(lines.map { line ->
            val name = line.substringBeforeLast(':').trim(); val minutes = line.substringAfterLast(':').trim().toIntOrNull()
            require(':' in line && name.length in 1..80 && minutes != null && minutes in 0..1440) { "Use App name: minutes on each line." }
            buildJsonObject { put("app", name); put("minutes", minutes); put("category", "other") }
        })
    }
    fun validId(id: String) = runCatching { UUID.fromString(id).toString() == id.lowercase() }.getOrDefault(false)
}
