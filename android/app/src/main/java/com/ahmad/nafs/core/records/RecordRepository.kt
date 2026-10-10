package com.ahmad.nafs.core.records

import com.ahmad.nafs.core.network.SupabaseClientProvider
import com.ahmad.nafs.core.network.SupabaseApiSessionTokens
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Singleton
class RecordRepository @Inject constructor(private val provider: SupabaseClientProvider, private val sessions: SupabaseApiSessionTokens) {
    suspend fun load(userId: String, feature: RecordFeature, before: String? = null): List<JsonObject> {
        check(sessions.isCurrentUser(userId))
        val rows = checkNotNull(provider.client).from(feature.table).select {
            filter { eq("user_id", userId); if (before != null && feature.daily) lt("date", before) }
            order(if (feature.daily) "date" else "created_at", Order.DESCENDING)
            limit(if (feature == RecordFeature.DREAM) 1 else 90)
        }.decodeList<JsonObject>()
        check(sessions.isCurrentUser(userId))
        return rows
    }
    suspend fun save(userId: String, intent: RecordIntent): JsonObject {
        check(sessions.isCurrentUser(userId))
        require(RecordRules.validId(intent.id))
        val feature = RecordFeature.valueOf(intent.feature)
        val patch = intent.patch
        if (feature == RecordFeature.LOG) {
            val hours = RecordRules.text(patch, "dream_work_hours").toDouble() * 3 + RecordRules.text(patch, "study_hours").toDouble() * 1.5
            // Freeze derived values in the persisted intent before calling this method.
            require(patch["weighted_hours_today"] != null)
            require(RecordRules.text(patch, "weighted_hours_today").toDouble() == hours)
        }
        val result = checkNotNull(provider.client).postgrest.rpc("native_save_record", buildJsonObject {
            put("p_table", feature.table); put("p_id", intent.id); put("p_date", intent.date?.let { kotlinx.serialization.json.JsonPrimitive(it) } ?: JsonNull)
            put("p_patch", patch); put("p_expected", intent.expected ?: JsonNull)
        }).decodeAs<JsonObject>()
        check(sessions.isCurrentUser(userId))
        require(RecordRules.text(result, "user_id") == userId)
        return result
    }
    suspend fun enrichLog(userId: String, date: LocalDate, patch: JsonObject): JsonObject {
        val dream = load(userId, RecordFeature.DREAM).firstOrNull()
        val hours = RecordRules.text(patch, "dream_work_hours").toDouble() * 3 + RecordRules.text(patch, "study_hours").toDouble() * 1.5
        val target = RecordRules.text(dream, "dream_date").let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val required = if (target != null) RecordRules.text(dream, "total_hours_required").toDouble() * 1.8 /
            java.time.temporal.ChronoUnit.DAYS.between(date, target).coerceAtLeast(1) else 0.0
        return JsonObject(patch + mapOf("weighted_hours_today" to kotlinx.serialization.json.JsonPrimitive(hours),
            "todays_pull_days" to kotlinx.serialization.json.JsonPrimitive(if (required > 0) (hours - required) / required else 0.0)))
    }
}
