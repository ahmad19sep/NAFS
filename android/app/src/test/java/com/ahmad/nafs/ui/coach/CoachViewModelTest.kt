package com.ahmad.nafs.ui.coach

import com.ahmad.nafs.core.coach.CoachConversation
import com.ahmad.nafs.core.coach.CoachLoadResult
import com.ahmad.nafs.core.coach.CoachMessage
import com.ahmad.nafs.core.coach.CoachRepository
import com.ahmad.nafs.core.coach.CoachSendIntent
import com.ahmad.nafs.core.coach.CoachSendResult
import com.ahmad.nafs.core.coach.CoachSnapshot
import com.ahmad.nafs.core.network.ApiFailure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoachViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val version = "2026-10-10T09:00:00.123456+00:00"

    private class Repository : CoachRepository {
        var snapshot = CoachSnapshot()
        var offline = false
        val intents = mutableListOf<CoachSendIntent>()
        var loadFailure: String? = null
        var onLoad: suspend () -> Unit = {}
        var onSend: suspend (String, CoachSendIntent) -> CoachSendResult = { _, _ ->
            CoachSendResult.Failure(ApiFailure.NETWORK, "Retained for retry")
        }
        override suspend fun load(userId: String): CoachLoadResult {
            onLoad()
            return loadFailure?.let { CoachLoadResult.Failure(it) }
                ?: CoachLoadResult.Loaded(snapshot, offline)
        }
        override suspend fun send(userId: String, intent: CoachSendIntent): CoachSendResult {
            intents += intent
            snapshot = snapshot.copy(pending = intent)
            return onSend(userId, intent).also { result ->
                if (result is CoachSendResult.Saved) snapshot = CoachSnapshot(listOf(result.conversation))
            }
        }
        override suspend fun discardPending(userId: String) { snapshot = snapshot.copy(pending = null) }
        override suspend fun clear(userId: String) { snapshot = CoachSnapshot() }
    }

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun `network retry retains exactly the same intent and user message`() = runTest(dispatcher) {
        val repository = Repository()
        val model = CoachViewModel(repository)
        model.initialize("user"); runCurrent()
        model.updateDraft("  Help me plan  "); model.send(); runCurrent()
        assertEquals("Help me plan", model.uiState.value.messages.last().content)
        assertNotNull(model.uiState.value.pending)
        model.send(); runCurrent()
        assertEquals(2, repository.intents.size)
        assertEquals(repository.intents[0], repository.intents[1])
        assertFalse(model.uiState.value.messages.any { it.role == "assistant" })
    }

    @Test fun `duplicate taps and refreshes do not overlap an active send`() = runTest(dispatcher) {
        val repository = Repository()
        val reply = CompletableDeferred<CoachSendResult>()
        repository.onSend = { _, _ -> reply.await() }
        val model = CoachViewModel(repository)
        model.initialize("user"); runCurrent()
        model.updateDraft("Hello"); model.send(); runCurrent()
        model.send(); model.reload(); model.refresh(); model.newConversation(); runCurrent()
        assertEquals(1, repository.intents.size)
        assertTrue(model.uiState.value.isSending)
        reply.complete(CoachSendResult.Failure(ApiFailure.NETWORK, "Retained")); runCurrent()
        assertFalse(model.uiState.value.isSending)
    }

    @Test fun `a stalled send stops waiting and recovers the saved reply without a second send`() = runTest(dispatcher) {
        val repository = Repository()
        repository.onSend = { _, _ -> CompletableDeferred<CoachSendResult>().await() }
        val model = CoachViewModel(repository)
        model.initialize("user"); runCurrent()
        model.updateDraft("Keep my message"); model.send(); runCurrent()
        advanceTimeBy(70_000L); runCurrent()
        assertFalse(model.uiState.value.isSending)
        assertTrue(model.uiState.value.requiresReload)
        assertEquals("Keep my message", model.uiState.value.messages.last().content)
        model.send(); runCurrent()
        assertEquals(1, repository.intents.size)
        val pending = repository.intents.single()
        repository.snapshot = CoachSnapshot(listOf(CoachConversation(pending.conversationId, "user",
            pending.messages + CoachMessage("assistant", "Saved after timeout"), version, version)))
        model.reload(); runCurrent()
        assertNull(model.uiState.value.pending)
        assertEquals("Saved after timeout", model.uiState.value.messages.last().content)
        assertEquals(1, repository.intents.size)
    }

    @Test fun `a stalled reply check releases loading without losing the pending request`() = runTest(dispatcher) {
        val repository = Repository()
        val model = CoachViewModel(repository)
        model.initialize("user"); runCurrent()
        model.updateDraft("Retain me"); model.send(); runCurrent()
        val pending = model.uiState.value.pending
        repository.onLoad = { CompletableDeferred<Unit>().await() }
        model.reload(); runCurrent()
        advanceTimeBy(30_000L); runCurrent()
        assertFalse(model.uiState.value.isLoading)
        assertTrue(model.uiState.value.requiresReload)
        assertFalse(model.uiState.value.pendingReloaded)
        assertEquals(pending, model.uiState.value.pending)
        model.keepMessageAsDraft(); runCurrent()
        assertEquals(pending, model.uiState.value.pending)
        repository.onLoad = {}
        model.reload(); runCurrent()
        assertFalse(model.uiState.value.requiresReload)
        assertTrue(model.uiState.value.pendingReloaded)
    }

    @Test fun `returning to Coach checks an inactive pending request for an already saved reply`() = runTest(dispatcher) {
        val repository = Repository()
        val model = CoachViewModel(repository)
        model.initialize("user"); runCurrent()
        model.updateDraft("Retain me"); model.send(); runCurrent()
        val pending = repository.intents.single()
        repository.snapshot = CoachSnapshot(listOf(CoachConversation(pending.conversationId, "user",
            pending.messages + CoachMessage("assistant", "Recovered on return"), version, version)))
        model.refresh(); runCurrent()
        assertNull(model.uiState.value.pending)
        assertEquals("Recovered on return", model.uiState.value.messages.last().content)
        assertEquals(1, repository.intents.size)
    }

    @Test fun `process restart restores the encrypted pending request for exact retry`() = runTest(dispatcher) {
        val repository = Repository()
        val first = CoachViewModel(repository)
        first.initialize("user"); runCurrent()
        first.updateDraft("Help me"); first.send(); runCurrent()
        val restarted = CoachViewModel(repository)
        restarted.initialize("user"); runCurrent()
        assertEquals(first.uiState.value.pending, restarted.uiState.value.pending)
        restarted.send(); runCurrent()
        assertEquals(repository.intents[0], repository.intents[1])
    }

    @Test fun `conflict reload adopts saved history while keeping the failed message as a draft`() = runTest(dispatcher) {
        val repository = Repository()
        val original = conversation("user")
        repository.snapshot = CoachSnapshot(listOf(original))
        repository.onSend = { _, _ -> CoachSendResult.Failure(ApiFailure.CONFLICT, "Reload") }
        val model = CoachViewModel(repository)
        model.initialize("user"); runCurrent()
        model.updateDraft("My unsent follow-up"); model.send(); runCurrent()
        assertTrue(model.uiState.value.requiresReload)
        model.send(); runCurrent()
        assertEquals(1, repository.intents.size)
        repository.snapshot = repository.snapshot.copy(conversations = listOf(original.copy(updatedAt = "2026-10-10T10:00:00Z")))
        model.reload(); runCurrent()
        model.keepMessageAsDraft(); runCurrent()
        assertNull(model.uiState.value.pending)
        assertEquals("My unsent follow-up", model.uiState.value.draft)
        assertTrue(model.uiState.value.canSend)
        assertEquals(repository.snapshot.conversations, model.uiState.value.conversations)
    }

    @Test fun `failed offline reload never unlocks stale writes or drops a pending message`() = runTest(dispatcher) {
        val repository = Repository()
        repository.onSend = { _, _ -> CoachSendResult.Failure(ApiFailure.RESULT_UNCONFIRMED, "Reload") }
        val model = CoachViewModel(repository)
        model.initialize("user"); runCurrent()
        model.updateDraft("Retain me"); model.send(); runCurrent()
        repository.offline = true
        model.reload(); runCurrent(); model.keepMessageAsDraft(); model.send(); runCurrent()
        assertTrue(model.uiState.value.isOffline)
        assertNotNull(model.uiState.value.pending)
        assertEquals("Retain me", model.uiState.value.messages.last().content)
        assertEquals(1, repository.intents.size)
    }

    @Test fun `successful online reload recovers a saved reply without sending again`() = runTest(dispatcher) {
        val repository = Repository()
        val model = CoachViewModel(repository)
        model.initialize("user"); runCurrent()
        model.updateDraft("Retain me"); model.send(); runCurrent()
        val pending = repository.intents.single()
        repository.snapshot = CoachSnapshot(listOf(CoachConversation(pending.conversationId, "user",
            pending.messages + CoachMessage("assistant", "Already saved"), version, version)))
        model.reload(); runCurrent()
        assertNull(model.uiState.value.pending)
        assertEquals("Already saved", model.uiState.value.messages.last().content)
        assertEquals(1, repository.intents.size)
    }

    @Test fun `cache persistence failure does not drop the in-memory draft on reload`() = runTest(dispatcher) {
        val repository = Repository()
        repository.onSend = { _, _ ->
            repository.snapshot = CoachSnapshot()
            CoachSendResult.Failure(ApiFailure.UNAVAILABLE, "Cache unavailable")
        }
        val model = CoachViewModel(repository)
        model.initialize("user"); runCurrent()
        model.updateDraft("Keep this text"); model.send(); runCurrent()
        model.reload(); runCurrent()
        assertNotNull(model.uiState.value.pending)
        assertEquals("Keep this text", model.uiState.value.messages.last().content)
    }

    @Test fun `same-day refresh preserves ordinary drafts and a new chat can retain them`() = runTest(dispatcher) {
        val repository = Repository()
        repository.snapshot = CoachSnapshot(listOf(conversation("user")))
        val model = CoachViewModel(repository)
        model.initialize("user"); runCurrent()
        val originalId = model.uiState.value.selectedId
        model.updateDraft("Unsent text"); model.refresh(); runCurrent()
        assertEquals("Unsent text", model.uiState.value.draft)
        model.newConversation()
        assertFalse(originalId == model.uiState.value.selectedId)
        assertEquals("Unsent text", model.uiState.value.draft)
    }

    @Test fun `changing accounts removes visible previous messages and drafts`() = runTest(dispatcher) {
        val repository = Repository()
        repository.snapshot = CoachSnapshot(listOf(conversation("first")))
        val model = CoachViewModel(repository)
        model.initialize("first"); runCurrent()
        model.updateDraft("Private draft")
        repository.snapshot = CoachSnapshot()
        model.initialize("second"); advanceUntilIdle()
        assertTrue(model.uiState.value.messages.isEmpty())
        assertTrue(model.uiState.value.draft.isEmpty())
    }

    @Test fun `empty new chat remains selected during lifecycle refresh`() = runTest(dispatcher) {
        val repository = Repository(); repository.snapshot = CoachSnapshot(listOf(conversation("user")))
        val model = CoachViewModel(repository)
        model.initialize("user"); runCurrent(); model.newConversation()
        val newId = model.uiState.value.selectedId
        model.refresh(); runCurrent()
        assertEquals(newId, model.uiState.value.selectedId)
        assertTrue(model.uiState.value.messages.isEmpty())
    }

    @Test fun `a failed later reload cannot use an earlier read to discard a retry`() = runTest(dispatcher) {
        val repository = Repository()
        val model = CoachViewModel(repository)
        model.initialize("user"); runCurrent()
        model.updateDraft("Retain me"); model.send(); runCurrent(); model.reload(); runCurrent()
        assertTrue(model.uiState.value.pendingReloaded)
        repository.loadFailure = "Reconnect"; model.reload(); runCurrent(); model.keepMessageAsDraft(); runCurrent()
        assertFalse(model.uiState.value.pendingReloaded)
        assertNotNull(model.uiState.value.pending)
    }

    @Test fun `loading failure blocks sending until a successful reload`() = runTest(dispatcher) {
        val repository = Repository(); repository.loadFailure = "Reconnect"
        val model = CoachViewModel(repository)
        model.initialize("user"); runCurrent()
        model.updateDraft("A message")
        assertFalse(model.uiState.value.canSend)
        model.send(); runCurrent()
        assertTrue(repository.intents.isEmpty())
        repository.loadFailure = null; model.reload(); runCurrent()
        assertTrue(model.uiState.value.canSend)
    }

    @Test fun `oversized pasted drafts remain intact for editing and cannot be sent`() = runTest(dispatcher) {
        val repository = Repository(); val model = CoachViewModel(repository)
        model.initialize("user"); runCurrent()
        val draft = "x".repeat(3_999) + "\uD83D\uDE42"
        model.updateDraft(draft)
        assertEquals(draft, model.uiState.value.draft)
        assertFalse(model.uiState.value.canSend)
        model.send(); runCurrent()
        assertTrue(repository.intents.isEmpty())
    }

    private fun conversation(userId: String) = CoachConversation("12345678-1234-1234-1234-123456789abc", userId,
        listOf(CoachMessage("user", "Earlier question"), CoachMessage("assistant", "Earlier reply")), version, version)
}
