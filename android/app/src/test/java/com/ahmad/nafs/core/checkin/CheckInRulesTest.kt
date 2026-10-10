package com.ahmad.nafs.core.checkin

import com.ahmad.nafs.ui.checkin.CheckInUiState
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckInRulesTest {
    private val empty = DailyCheckIn("record", "account", "2026-10-09", updatedAt = "2026-10-09T03:00:00Z")

    @Test fun `morning task serialization matches the existing web contract including incomplete tasks`() {
        val record = empty.copy(tasks = listOf(CheckInTask("Read", true), CheckInTask("Walk")))
        val payload = CheckInRules.payload(record)
        assertEquals(Json.parseToJsonElement("""[{"text":"Read","done":true},{"text":"Walk","done":false}]"""), payload["tasks"])
        assertNull(CheckInRules.error(record))
    }

    @Test fun `clearing a plan and evening note sends an empty array and explicit null`() {
        val payload = CheckInRules.payload(empty)
        assertEquals("[]", payload["tasks"].toString())
        assertEquals("null", payload["evening_text"].toString())
    }

    @Test fun `client writes never replace stored AI verdicts`() {
        val record = empty.copy(eveningText = "A new reflection", aiVerdict = "A previous review")
        assertFalse(CheckInRules.payload(record).containsKey("ai_verdict"))
        assertEquals("A previous review", CheckInRules.normalized(record).aiVerdict)
    }

    @Test fun `validation rejects invalid account dates versions and oversized plans`() {
        assertNotNull(CheckInRules.error(empty.copy(date = "2026-02-29")))
        assertNotNull(CheckInRules.error(empty.copy(updatedAt = "yesterday")))
        assertNotNull(CheckInRules.error(empty.copy(tasks = List(8) { CheckInTask("Priority $it") })))
        assertNull(CheckInRules.error(empty.copy(tasks = List(7) { CheckInTask("Priority $it") })))
        assertNotNull(CheckInRules.error(empty.copy(tasks = listOf(CheckInTask(" ")))))
        assertNotNull(CheckInRules.error(empty.copy(eveningText = "x".repeat(10_001))))
    }

    @Test fun `version advances even if the device clock moved backwards`() {
        val payload = CheckInRules.payload(empty, Instant.parse("2026-10-08T00:00:00Z"))
        assertEquals(JsonPrimitive("2026-10-09T03:00:00.001Z"), payload["updated_at"])
    }

    @Test fun `lost response retries adopt returned versions but do not hide peer changes`() {
        val desired = empty.copy(tasks = listOf(CheckInTask("Read", true)), eveningText = "Done")
        assertTrue(CheckInRules.sameContent(desired.copy(id = "server-record", updatedAt = "2026-10-09T04:00:00Z", aiVerdict = "New server review"), desired))
        assertFalse(CheckInRules.sameContent(desired.copy(eveningText = "Another client"), desired))
        assertFalse(CheckInRules.sameContent(desired.copy(tasks = listOf(CheckInTask("Read", false))), desired))
        assertFalse(CheckInRules.sameContent(desired.copy(userId = "other"), desired))
        assertFalse(CheckInRules.sameContent(desired.copy(date = "2026-10-08"), desired))
    }

    @Test fun `normalization trims user entries while keeping completion and AI content`() {
        val record = empty.copy(tasks = listOf(CheckInTask("  Read  ", true)), eveningText = "  Reflection  ", aiVerdict = "  Original  ")
        val normalized = CheckInRules.normalized(record)
        assertEquals(listOf(CheckInTask("Read", true)), normalized.tasks)
        assertEquals("Reflection", normalized.eveningText)
        assertEquals("  Original  ", normalized.aiVerdict)
    }

    @Test fun `failed conflict reload preserves the local form and keeps stale writes disabled`() {
        val local = empty.copy(eveningText = "Unsaved local review")
        val state = CheckInUiState(draft = local, saved = empty, isLoading = true, hasConflict = true)
        val offline = state.loaded(CheckInLoadResult.Success(empty, isOffline = true), empty)
        assertEquals(local, offline.draft)
        assertTrue(offline.hasConflict)
        assertFalse(offline.canEdit)
        assertNotNull(offline.error)
        val fresh = empty.copy(eveningText = "Peer review", updatedAt = "2026-10-09T04:00:00Z")
        val reloaded = offline.loaded(CheckInLoadResult.Success(fresh, isOffline = false), empty)
        assertEquals(fresh, reloaded.draft)
        assertFalse(reloaded.hasConflict)
        assertTrue(reloaded.canEdit)
        assertFalse(reloaded.hasChanges)
    }

    @Test fun `no saved row is clean while a new plan or evening review is unsaved`() {
        val state = CheckInUiState(draft = empty, isLoading = false)
        assertFalse(state.hasChanges)
        assertTrue(state.copy(draft = empty.copy(tasks = listOf(CheckInTask("Read")))).hasChanges)
        assertTrue(state.copy(draft = empty.copy(eveningText = "Reflection")).hasChanges)
        assertFalse(state.copy(isOffline = true).canEdit)
        assertFalse(state.copy(isSaving = true).canEdit)
    }
}
