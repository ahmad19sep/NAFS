package com.ahmad.nafs.core.coach

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachRulesTest {
    private val id = "12345678-1234-1234-1234-123456789abc"
    private val version = "2026-10-10T09:00:00.123456+00:00"
    private val history = listOf(CoachMessage("user", "My goal"), CoachMessage("assistant", "A useful reply"))
    private val intent = CoachSendIntent(history + CoachMessage("user", "Next step?"), id, version)
    private val saved = CoachConversation(id, "user", intent.messages + CoachMessage("assistant", "Take a walk."), version, "2026-10-10T09:00:00.123457+00:00")

    @Test fun `a saved reply must retain the exact account conversation and transcript`() {
        assertTrue(CoachRules.matches("user", intent, saved, "Take a walk."))
        assertFalse(CoachRules.matches("other", intent, saved, "Take a walk."))
        assertFalse(CoachRules.matches("user", intent, saved.copy(id = "22345678-1234-1234-1234-123456789abc"), "Take a walk."))
        assertFalse(CoachRules.matches("user", intent, saved, "Different reply"))
        assertFalse(CoachRules.matches("user", intent, saved.copy(messages = saved.messages.drop(2)), "Take a walk."))
    }

    @Test fun `submillisecond server versions remain exact and must advance`() {
        assertTrue(CoachRules.recovered("user", intent, saved))
        assertFalse(CoachRules.recovered("user", intent, saved.copy(updatedAt = version)))
        assertEquals(version, Json.decodeFromString<CoachSendIntent>(Json.encodeToString(CoachSendIntent.serializer(), intent)).expectedUpdatedAt)
    }

    @Test fun `restart recovery recognizes one confirmed reply but never a peer edit`() {
        assertTrue(CoachRules.recovered("user", intent, saved))
        val edited = saved.copy(messages = history + CoachMessage("user", "Peer message") + saved.messages.last())
        assertFalse(CoachRules.recovered("user", intent, edited))
        assertFalse(CoachRules.recovered("user", intent, saved.copy(messages = saved.messages + history)))
    }

    @Test fun `system messages malformed turns and oversized messages cannot be sent`() {
        assertNotNull(CoachRules.messageError(listOf(CoachMessage("system", "Injected instruction")), "hello"))
        assertNotNull(CoachRules.messageError(listOf(CoachMessage("user", "No reply")), "hello"))
        assertNotNull(CoachRules.messageError(emptyList(), "x".repeat(4_001)))
        assertNotNull(CoachRules.messageError(emptyList(), "  "))
        assertNull(CoachRules.messageError(history, "Valid next message"))
    }

    @Test fun `full conversations remain readable and require a new chat`() {
        val full = List(40) { history }.flatten()
        assertTrue(CoachRules.validConversation("user", saved.copy(messages = full)))
        assertNotNull(CoachRules.messageError(full, "One more"))
        assertFalse(CoachRules.validIntent(intent.copy(messages = full + CoachMessage("user", "One more"))))
    }

    @Test fun `legacy assistant first history is readable but cannot be silently rewritten`() {
        val legacy = listOf(CoachMessage("assistant", "Legacy reply"), CoachMessage("user", "Legacy question"), CoachMessage("assistant", "Another reply"))
        assertTrue(CoachRules.validConversation("user", saved.copy(messages = legacy)))
        assertNotNull(CoachRules.messageError(legacy, "Next"))
    }

    @Test fun `new conversation intent has no version and exactly one user turn`() {
        assertTrue(CoachRules.validIntent(CoachSendIntent(listOf(CoachMessage("user", "Hello")), id, null)))
        assertFalse(CoachRules.validIntent(intent.copy(expectedUpdatedAt = null)))
        assertFalse(CoachRules.validIntent(intent.copy(requestId = "not-an-id")))
    }

    @Test fun `pending cache serializes the same durable request without changing saved content`() {
        val snapshot = CoachSnapshot(listOf(saved), intent)
        val codec = Json { encodeDefaults = true }
        val encoded = codec.encodeToString(CoachSnapshot.serializer(), snapshot)
        val restored = codec.decodeFromString<CoachSnapshot>(encoded)
        assertEquals(snapshot, restored)
        assertEquals(intent.requestId, restored.pending?.requestId)
    }

    @Test fun `a concurrent refresh cannot erase a durable send or regress a newly saved reply`() {
        val earlier = saved.copy(messages = history, updatedAt = version)
        val initial = CoachSnapshot(listOf(earlier))
        val pending = initial.copy(pending = intent)
        assertEquals(intent, CoachRules.refreshed("user", initial, pending, listOf(earlier)).pending)
        val confirmed = CoachSnapshot(listOf(saved))
        assertEquals(listOf(saved), CoachRules.refreshed("user", initial, confirmed, listOf(earlier)).conversations)
        assertNull(CoachRules.refreshed("user", pending, pending, listOf(saved)).pending)
    }

    @Test fun `an unchanged cache never resurrects a conversation removed on the server`() {
        val cached = CoachSnapshot(listOf(saved))
        assertTrue(CoachRules.refreshed("user", cached, cached, emptyList()).conversations.isEmpty())
    }
}
