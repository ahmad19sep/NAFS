package com.ahmad.nafs.core.records

import java.time.LocalDate
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class RecordRulesTest {
    private val today = LocalDate.parse("2026-10-10")
    private fun defaults(feature: RecordFeature) = feature.fields.associate { it.key to it.initial }
    @Test fun `Quran accepts zero pages without fabricating a completed entry`() {
        val result = RecordRules.patch(RecordFeature.QURAN, defaults(RecordFeature.QURAN), today)
        assertEquals(0.0, result["pages_read"]!!.jsonPrimitive.double, 0.0)
        assertEquals(JsonNull, result["notes"])
    }
    @Test fun `Quran rejects a backwards ayah range`() {
        assertThrows(IllegalArgumentException::class.java) { RecordRules.patch(RecordFeature.QURAN, defaults(RecordFeature.QURAN) + mapOf("ayah_from" to "20", "ayah_to" to "10"), today) }
    }
    @Test fun `Quran requires both ends of an ayah range`() {
        assertThrows(IllegalArgumentException::class.java) { RecordRules.patch(RecordFeature.QURAN, defaults(RecordFeature.QURAN) + ("ayah_from" to "20"), today) }
    }
    @Test fun `daily log rejects impossible combined work and study time`() {
        assertThrows(IllegalArgumentException::class.java) { RecordRules.patch(RecordFeature.LOG, defaults(RecordFeature.LOG) + mapOf("dream_work_hours" to "18", "study_hours" to "12"), today) }
    }
    @Test fun `daily log retains fractional focused hours`() {
        val result = RecordRules.patch(RecordFeature.LOG, defaults(RecordFeature.LOG) + ("dream_work_hours" to "1.5"), today)
        assertEquals(1.5, result["dream_work_hours"]!!.jsonPrimitive.double, 0.0)
    }
    @Test fun `whole minute fields reject fractions`() {
        assertThrows(IllegalArgumentException::class.java) { RecordRules.patch(RecordFeature.LOG, defaults(RecordFeature.LOG) + ("screen_time_mins" to "3.5"), today) }
    }
    @Test fun `numbers reject NaN infinity negative values and out of bounds input`() {
        listOf("NaN", "Infinity", "-1", "1441").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { RecordRules.patch(RecordFeature.SCREEN, defaults(RecordFeature.SCREEN) + ("total_mins" to value), today) }
        }
    }
    @Test fun `screen apps support colon in a name and validate sum`() {
        val patch = RecordRules.patch(RecordFeature.SCREEN, defaults(RecordFeature.SCREEN) + mapOf("total_mins" to "60", "apps" to "Study: timer: 30\nMessages: 20"), today)
        assertEquals("Study: timer", patch["apps"]!!.jsonArray[0].jsonObject["app"]!!.jsonPrimitive.content)
        assertThrows(IllegalArgumentException::class.java) { RecordRules.patch(RecordFeature.SCREEN, defaults(RecordFeature.SCREEN) + mapOf("total_mins" to "5", "apps" to "Messages: 20"), today) }
    }
    @Test fun `screen app parser rejects malformed and oversized lists`() {
        listOf("No duration", "App: -1", "App: 3.5", ": 20").forEach { value -> assertThrows(IllegalArgumentException::class.java) { RecordRules.parseApps(value) } }
        assertThrows(IllegalArgumentException::class.java) { RecordRules.parseApps((1..41).joinToString("\n") { "App $it: 1" }) }
    }
    @Test fun `dream uses account calendar dates and requires a future target`() {
        val values = defaults(RecordFeature.DREAM) + mapOf("statement" to "Build a library", "dream_date" to "2026-10-11")
        assertEquals("2026-10-11", RecordRules.patch(RecordFeature.DREAM, values, today)["dream_date"]!!.jsonPrimitive.content)
        listOf("2026-10-10", "2026-02-30", "bad-date").forEach { value -> assertThrows(IllegalArgumentException::class.java) { RecordRules.patch(RecordFeature.DREAM, values + ("dream_date" to value), today) } }
    }
    @Test fun `times use exact twenty four hour format`() {
        val values = defaults(RecordFeature.LOG) + mapOf("sleep_start" to "23:30", "sleep_end" to "06:15")
        assertEquals("23:30", RecordRules.patch(RecordFeature.LOG, values, today)["sleep_start"]!!.jsonPrimitive.content)
        listOf("24:00", "6:15", "14:60").forEach { value -> assertThrows(IllegalArgumentException::class.java) { RecordRules.patch(RecordFeature.LOG, values + ("sleep_start" to value), today) } }
    }
    @Test fun `note enums length and required title are validated`() {
        val values = defaults(RecordFeature.NOTE) + ("title" to "Better reminders")
        assertEquals("idea", RecordRules.patch(RecordFeature.NOTE, values, today)["kind"]!!.jsonPrimitive.content)
        listOf(mapOf("title" to " "), mapOf("title" to "x".repeat(201)), mapOf("status" to "deleted"), mapOf("kind" to "news")).forEach { change ->
            assertThrows(IllegalArgumentException::class.java) { RecordRules.patch(RecordFeature.NOTE, values + change, today) }
        }
    }
    @Test fun `form never forwards user identity id server fields or unknown keys`() {
        val patch = RecordRules.patch(RecordFeature.QURAN, defaults(RecordFeature.QURAN) + mapOf("user_id" to "foreign", "id" to "foreign", "ai_summary" to "invented"), today)
        assertFalse(patch.containsKey("user_id")); assertFalse(patch.containsKey("id")); assertFalse(patch.containsKey("ai_summary"))
    }
    @Test fun `stable UUIDs reject shortened and malformed identifiers`() {
        assertTrue(RecordRules.validId("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"))
        assertFalse(RecordRules.validId("1-1-1-1-1")); assertFalse(RecordRules.validId("bad-id"))
    }
    @Test fun `pending save snapshot survives serialization without changing loaded version`() {
        val original = buildJsonObject { put("id", "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"); put("user_id", "owner"); put("notes", "old") }
        val patch = buildJsonObject { put("pages_read", 2); put("notes", "new") }
        val snapshot = RecordSnapshot(rows = listOf(original), draft = mapOf("notes" to "new"), editingId = RecordRules.text(original, "id"), date = "2026-10-09",
            expected = original, pending = RecordIntent("QURAN", RecordRules.text(original, "id"), "2026-10-09", patch, original))
        val serialized = Json.encodeToString(RecordSnapshot.serializer(), snapshot)
        assertEquals(snapshot, Json.decodeFromString(RecordSnapshot.serializer(), serialized))
    }
}
