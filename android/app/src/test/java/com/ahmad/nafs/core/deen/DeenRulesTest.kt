package com.ahmad.nafs.core.deen

import com.ahmad.nafs.ui.deen.DeenUiState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeenRulesTest {
    private val empty = PrayerLog("record", "account", "2026-10-09")

    @Test fun `resetting prayer statuses and removing all extras sends explicit zero and empty values`() {
        val payload = DeenWriteRules.payload(empty)
        assertEquals(JsonPrimitive(0), payload["fajr"])
        assertEquals(JsonPrimitive(0), payload["isha"])
        assertEquals("[]", payload["extra_prayers"].toString())
    }

    @Test fun `mixed prayer statuses keep completion separate from jamaat quality`() {
        val log = empty.copy(fajr = 2, dhuhr = 1, asr = 0, maghrib = 2, isha = 1)
        assertEquals(4, log.prayedCount)
        assertEquals(2, log.jamaatCount)
        assertEquals(6, log.prayerPoints)
        assertNull(DeenValidator.error(log))
    }

    @Test fun `invalid calendar dates and status values cannot be persisted`() {
        assertNotNull(DeenValidator.error(empty.copy(date = "2026-02-29")))
        assertNull(DeenValidator.error(empty.copy(date = "2028-02-29")))
        assertNotNull(DeenValidator.error(empty.copy(fajr = 3)))
        assertNotNull(DeenValidator.error(empty.copy(isha = -1)))
    }

    @Test fun `editing web extras preserves nested unknown fields and legacy entries`() {
        val item = Json.decodeFromString<JsonObject>("""{"name":"Tahajjud","type":"boolean","done":false,"score_weight":3,"metadata":{"source":"web"}}""")
        val updated = DeenExtraRules.patch(item, "done", JsonPrimitive(true))
        assertEquals(item["metadata"], updated["metadata"])
        assertEquals(3 to 3, DeenExtraRules.points(updated))
        val legacy = Json.decodeFromString<JsonObject>("""{"name":"Witr","status":2}""")
        assertEquals(2 to 2, DeenExtraRules.points(legacy))
        assertNull(DeenValidator.error(empty.copy(extras = listOf(updated, legacy))))
    }

    @Test fun `count extras earn their configured weight only at their target`() {
        val item = Json.decodeFromString<JsonObject>("""{"name":"Dhikr","type":"count","value":99,"target":100,"score_weight":2}""")
        assertEquals(0 to 2, DeenExtraRules.points(item))
        val complete = DeenExtraRules.patch(item, "value", JsonPrimitive(100))
        assertEquals(2 to 2, DeenExtraRules.points(complete))
        assertNotNull(DeenValidator.error(empty.copy(extras = listOf(DeenExtraRules.patch(item, "value", JsonPrimitive(-1))))))
        assertNotNull(DeenValidator.error(empty.copy(extras = listOf(DeenExtraRules.patch(item, "target", JsonPrimitive(0))))))
    }

    @Test fun `a replay can adopt server identity but never another day or another account`() {
        val desired = empty.copy(fajr = 2)
        assertTrue(DeenWriteRules.sameContent(desired.copy(id = "server-id"), desired))
        assertFalse(DeenWriteRules.sameContent(desired.copy(dhuhr = 1), desired))
        assertFalse(DeenWriteRules.sameContent(desired.copy(userId = "other"), desired))
        assertFalse(DeenWriteRules.sameContent(desired.copy(date = "2026-10-08"), desired))
    }

    @Test fun `new and loaded unchanged days are clean and cached conflicts cannot be edited`() {
        assertFalse(DeenUiState(draft = empty, isLoading = false).hasChanges)
        assertTrue(DeenUiState(draft = empty, isLoading = false, hasEdited = true).hasChanges)
        assertFalse(DeenUiState(draft = empty, saved = empty, isLoading = false).hasChanges)
        val changed = DeenUiState(draft = empty.copy(asr = 1), saved = empty, isLoading = false)
        assertTrue(changed.hasChanges)
        assertTrue(changed.canEdit)
        assertFalse(changed.copy(hasConflict = true).canEdit)
        assertFalse(changed.copy(isOffline = true).canEdit)
        assertFalse(changed.copy(isSaving = true).canEdit)
    }

    @Test fun `future activity types are kept intact when another prayer changes`() {
        val future = Json.decodeFromString<JsonObject>("""{"name":"Future activity","type":"future","details":{"a":1}}""")
        val log = empty.copy(extras = listOf(future)).withStatus(Prayer.FAJR, PrayerStatus.ALONE)
        assertNull(DeenValidator.error(log))
        assertEquals(future, log.extras.single())
    }
}
