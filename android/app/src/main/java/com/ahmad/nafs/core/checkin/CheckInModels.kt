package com.ahmad.nafs.core.checkin

import com.ahmad.nafs.core.time.ServerTimestamp
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

@Serializable
data class CheckInTask(val text: String, val done: Boolean = false)

@Serializable
data class DailyCheckIn(
    val id: String,
    @SerialName("user_id") val userId: String,
    val date: String,
    val tasks: List<CheckInTask> = emptyList(),
    @SerialName("evening_text") val eveningText: String? = null,
    @SerialName("ai_verdict") val aiVerdict: String? = null,
    @SerialName("updated_at") val updatedAt: String,
)

object CheckInRules {
    const val MAX_TASKS = 7
    private val json = Json { encodeDefaults = true }

    fun error(record: DailyCheckIn): String? = when {
        record.id.isBlank() || record.userId.isBlank() -> "Reload your account before saving a check-in."
        runCatching { LocalDate.parse(record.date) }.isFailure -> "Choose a valid account day."
        runCatching { ServerTimestamp.parse(record.updatedAt) }.isFailure -> "Reload the latest check-in before saving."
        record.tasks.size > MAX_TASKS -> "Keep at most seven priorities in your daily plan."
        record.tasks.any { it.text.isBlank() || it.text.length > 200 } -> "Each priority must contain 1–200 characters."
        (record.eveningText?.length ?: 0) > 10_000 -> "Keep your evening review to 10,000 characters or fewer."
        else -> null
    }

    fun normalized(record: DailyCheckIn): DailyCheckIn = record.copy(
        tasks = record.tasks.map { it.copy(text = it.text.trim()) },
        eveningText = record.eveningText?.trim()?.takeIf(String::isNotEmpty),
    )

    /** AI results are owned by the server and must never be replaced by a client save. */
    fun payload(record: DailyCheckIn, now: Instant = Instant.now()): JsonObject {
        val previous = ServerTimestamp.parse(record.updatedAt)
        val next = if (now > previous) now else previous.plusMillis(1)
        return JsonObject(json.encodeToJsonElement(normalized(record).copy(updatedAt = next.toString())).jsonObject - "ai_verdict")
    }

    fun sameContent(current: DailyCheckIn, desired: DailyCheckIn): Boolean =
        current.userId == desired.userId && current.date == desired.date &&
            current.tasks == desired.tasks && current.eveningText == desired.eveningText
}

sealed interface CheckInLoadResult {
    data class Success(val record: DailyCheckIn?, val isOffline: Boolean) : CheckInLoadResult
    data class Failure(val message: String) : CheckInLoadResult
}

sealed interface CheckInSaveResult {
    data class Saved(val record: DailyCheckIn) : CheckInSaveResult
    data object Conflict : CheckInSaveResult
    data class Failure(val message: String) : CheckInSaveResult
}
