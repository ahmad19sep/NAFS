package com.ahmad.nafs.ui.plans

import com.ahmad.nafs.core.plans.*
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecoveryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private class Repository : RecoveryRepository {
        var snapshot = RecoverySnapshot("user")
        var offline = false
        var failDraft = false
        var applyCalls = 0
        val requests = mutableListOf<RecoveryIntent>()
        var generateAction: suspend (RecoveryIntent) -> RecoveryGenerateResult = { intent ->
            snapshot = snapshot.copy(pending = intent); RecoveryGenerateResult.Failure("Retained request", true) }
        override suspend fun load(userId: String) = RecoveryLoaded(snapshot, offline)
        override suspend fun storeDraft(userId: String, draft: String) { check(!failDraft); snapshot = snapshot.copy(userId = userId, draft = draft) }
        override suspend fun generate(userId: String, intent: RecoveryIntent): RecoveryGenerateResult { requests += intent; return generateAction(intent) }
        override suspend fun select(userId: String, planId: String, index: Int, selected: Boolean): RecoverySnapshot {
            snapshot = snapshot.copy(preview = snapshot.preview!!.copy(steps = snapshot.preview!!.steps.mapIndexed { i, step -> if (i == index) step.copy(selected = selected) else step }))
            return snapshot
        }
        override suspend fun apply(userId: String, planId: String, today: LocalDate, timezone: String): RecoveryApplyResult { applyCalls++; return RecoveryApplyResult(snapshot, "Added") }
        override suspend fun discard(userId: String) { snapshot = snapshot.copy(pending = null, preview = null) }
        override suspend fun clear(userId: String) { snapshot = RecoverySnapshot(userId) }
    }
    private val repository = Repository()
    @Before fun mainDispatcher() { Dispatchers.setMain(dispatcher) }
    @After fun resetDispatcher() { Dispatchers.resetMain() }
    private fun initialize(model: RecoveryViewModel) { model.initialize("user", planToday, "Asia/Karachi") }

    @Test fun `draft preserves raw text and flushes before leaving`() = runTest(dispatcher) {
        val model = RecoveryViewModel(repository); initialize(model); runCurrent()
        model.editDraft("  My own words  ")
        assertFalse(model.uiState.value.draftSaved)
        var closed = false
        model.flushDraft { closed = true }; runCurrent()
        assertTrue(closed)
        assertEquals("  My own words  ", repository.snapshot.draft)
        assertTrue(model.uiState.value.draftSaved)
    }
    @Test fun `failed draft flush keeps the editor open and blocks provider calls`() = runTest(dispatcher) {
        val model = RecoveryViewModel(repository); initialize(model); runCurrent()
        model.editDraft("A real problem"); repository.failDraft = true
        var closed = false
        model.flushDraft { closed = true }; runCurrent()
        assertFalse(closed)
        assertNotNull(model.uiState.value.error)
        model.generate(); runCurrent()
        assertTrue(repository.requests.isEmpty())
    }
    @Test fun `oversized draft stays intact but cannot generate and offline prevents calls`() = runTest(dispatcher) {
        val model = RecoveryViewModel(repository); initialize(model); runCurrent()
        val text = "x".repeat(501)
        model.editDraft(text); advanceTimeBy(401); runCurrent()
        assertEquals(text, model.uiState.value.snapshot!!.draft)
        assertFalse(model.uiState.value.canGenerate)
        model.generate(); runCurrent(); assertTrue(repository.requests.isEmpty())
        model.editDraft("Valid problem"); advanceTimeBy(401); runCurrent()
        repository.offline = true; model.reload(); runCurrent()
        model.generate(); runCurrent()
        assertTrue(repository.requests.isEmpty())
    }
    @Test fun `duplicate taps use one request and restart retries the exact pending intent`() = runTest(dispatcher) {
        val model = RecoveryViewModel(repository); initialize(model); runCurrent()
        model.editDraft("A real problem"); model.generate(); model.generate(); runCurrent()
        assertEquals(1, repository.requests.size)
        val retained = repository.requests.single()
        val restarted = RecoveryViewModel(repository); initialize(restarted); runCurrent()
        restarted.generate(); runCurrent()
        assertEquals(listOf(retained, retained), repository.requests)
    }
    @Test fun `saved chat keeps exact source pointer across failed retries`() = runTest(dispatcher) {
        val model = RecoveryViewModel(repository)
        model.initialize("user", planToday, "UTC", planStepId, "2026-10-10T12:00:00.123456+00:00"); runCurrent()
        model.generate(fromChat = true); runCurrent()
        val first = repository.requests.single()
        assertEquals(planStepId, first.conversationId)
        assertEquals("2026-10-10T12:00:00.123456+00:00", first.expectedUpdatedAt)
        assertNull(first.intent)
        model.generate(); runCurrent()
        assertEquals(listOf(first, first), repository.requests)
    }
    @Test fun `loading or selecting a preview requires an explicit apply tap`() = runTest(dispatcher) {
        repository.snapshot = RecoverySnapshot("user", preview = savedPlan())
        val model = RecoveryViewModel(repository); initialize(model); runCurrent()
        model.select(1, false); runCurrent()
        assertEquals(0, repository.applyCalls)
        assertEquals(2, model.uiState.value.toAdd)
        model.apply(); model.apply(); runCurrent()
        assertEquals(1, repository.applyCalls)
    }
    @Test fun `late old-account responses cannot replace the active account state`() = runTest(dispatcher) {
        val reply = CompletableDeferred<Unit>()
        repository.generateAction = { withContext(NonCancellable) { reply.await(); RecoveryGenerateResult.Saved(RecoverySnapshot("user", preview = savedPlan())) } }
        val model = RecoveryViewModel(repository); initialize(model); runCurrent()
        model.editDraft("A real problem"); model.generate(); runCurrent()
        repository.snapshot = RecoverySnapshot("other")
        model.initialize("other", planToday, "UTC"); runCurrent()
        reply.complete(Unit); runCurrent()
        assertEquals("other", model.uiState.value.snapshot!!.userId)
        assertNull(model.uiState.value.preview)
    }
}
