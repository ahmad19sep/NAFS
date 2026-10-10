package com.ahmad.nafs.core.plans

import com.ahmad.nafs.core.network.ApiSessionTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CachedRecoveryRepositoryTest {
    private class Sessions : ApiSessionTokens {
        var current = "user"
        override fun isCurrentUser(userId: String) = current == userId
        override suspend fun accessToken(userId: String, forceRefresh: Boolean) = "token"
    }
    private class Cache : RecoveryCache {
        var snapshot: RecoverySnapshot? = null
        var writes = 0
        var failAt = Int.MAX_VALUE
        var readFailure = false
        override suspend fun read(userId: String): RecoverySnapshot? { check(!readFailure); return snapshot }
        override suspend fun write(userId: String, snapshot: RecoverySnapshot) { writes++; check(writes != failAt); this.snapshot = snapshot }
        override suspend fun clear(userId: String) { snapshot = null }
    }
    private class Items : PlanItemWriter {
        var offline = false
        val rows = mutableMapOf<String, PlanItemState>()
        val attempts = mutableListOf<PlanStepState>()
        var createAction: suspend (PlanStepState) -> Boolean = { rows[it.id] = PlanItemState.ADDED; true }
        override suspend fun checkOnline(userId: String) { check(!offline) }
        override suspend fun inspect(userId: String, proposal: PlanProposal, step: PlanStepState) = rows[step.id] ?: PlanItemState.MISSING
        override suspend fun create(userId: String, proposal: PlanProposal, step: PlanStepState, timezone: String): Boolean { attempts += step; return createAction(step) }
    }
    private val sessions = Sessions()
    private val cache = Cache()
    private val items = Items()
    private val requests = mutableListOf<RecoveryIntent>()
    private var generate: suspend (RecoveryIntent) -> RecoveryResponse = { planResponse() }
    private val remote = RecoveryRemote { _, intent -> requests += intent; generate(intent) }
    private val repository = CachedRecoveryRepository(cache, remote, items, sessions)
    private fun preview() { cache.snapshot = RecoverySnapshot("user", preview = savedPlan()) }

    @Test fun `generation is protected before provider and never creates feature items`() = runTest {
        generate = { assertEquals(it, cache.snapshot!!.pending); planResponse() }
        val saved = repository.generate("user", planIntent()) as RecoveryGenerateResult.Saved
        assertNull(saved.snapshot.pending)
        assertNotNull(saved.snapshot.preview)
        assertEquals(3, saved.snapshot.preview!!.steps.map { it.id }.distinct().size)
        assertTrue(items.attempts.isEmpty())
    }
    @Test fun `failed local protection prevents generation and unreadable retries are not replaced`() = runTest {
        cache.failAt = 1
        assertFalse((repository.generate("user", planIntent()) as RecoveryGenerateResult.Failure).pending)
        assertTrue(requests.isEmpty())
        cache.failAt = Int.MAX_VALUE; cache.readFailure = true
        assertFalse((repository.generate("user", planIntent()) as RecoveryGenerateResult.Failure).pending)
        assertTrue(requests.isEmpty())
    }
    @Test fun `timeout retains exact request across restart and stable IDs recover a replay`() = runTest {
        generate = { error("Lost response") }
        assertTrue((repository.generate("user", planIntent()) as RecoveryGenerateResult.Failure).pending)
        assertEquals(planIntent(), cache.snapshot!!.pending)
        generate = { planResponse() }
        val restarted = CachedRecoveryRepository(cache, remote, items, sessions)
        val first = (restarted.generate("user", cache.snapshot!!.pending!!) as RecoveryGenerateResult.Saved).snapshot.preview!!
        cache.snapshot = RecoverySnapshot("user", pending = planIntent())
        val replay = (repository.generate("user", planIntent()) as RecoveryGenerateResult.Saved).snapshot.preview!!
        assertEquals(first.steps, replay.steps)
        assertEquals(listOf(planIntent(), planIntent(), planIntent()), requests)
    }
    @Test fun `failed preview persistence exposes no applicable preview`() = runTest {
        cache.failAt = 2
        val failed = repository.generate("user", planIntent()) as RecoveryGenerateResult.Failure
        assertTrue(failed.pending)
        assertNull(cache.snapshot!!.preview)
        assertEquals(planIntent(), cache.snapshot!!.pending)
        assertTrue(items.attempts.isEmpty())
    }
    @Test fun `selection alone makes no creates and apply sends only selected steps`() = runTest {
        preview()
        repository.select("user", planRequestId, 1, false)
        repository.select("user", planRequestId, 2, false)
        assertTrue(items.attempts.isEmpty())
        val applied = repository.apply("user", planRequestId, planToday, "Asia/Karachi")
        assertEquals(listOf(planStepId), items.attempts.map { it.id })
        val steps = checkNotNull(applied.snapshot.preview).steps
        assertTrue(steps[0].added)
        assertFalse(steps[1].added)
    }
    @Test fun `each write is preceded by durable stable UUID and date`() = runTest {
        preview()
        items.createAction = { step ->
            val persisted = cache.snapshot!!.preview!!.steps.single { it.id == step.id }
            assertEquals(step, persisted)
            assertEquals(planToday.toString(), persisted.applyDate)
            items.rows[step.id] = PlanItemState.ADDED; true
        }
        repository.apply("user", planRequestId, planToday, "UTC")
        assertEquals(3, items.attempts.size)
    }
    @Test fun `partial apply retries only missing items with original IDs and day`() = runTest {
        preview()
        val failedId = savedPlan().steps[1].id
        items.createAction = { step -> if (step.id == failedId) false else { items.rows[step.id] = PlanItemState.ADDED; true } }
        val first = repository.apply("user", planRequestId, planToday, "UTC")
        assertEquals(listOf(true, false, true), first.snapshot.preview!!.steps.map { it.added })
        items.createAction = { step -> items.rows[step.id] = PlanItemState.ADDED; true }
        CachedRecoveryRepository(cache, remote, items, sessions).apply("user", planRequestId, planToday.plusDays(2), "UTC")
        assertEquals(4, items.attempts.size)
        assertEquals(failedId, items.attempts.last().id)
        assertEquals(planToday.toString(), items.attempts.last().applyDate)
    }
    @Test fun `committed response loss is recovered by reload with no duplicate create`() = runTest {
        preview()
        items.createAction = { step -> items.rows[step.id] = PlanItemState.ADDED; error("Response lost") }
        repository.apply("user", planRequestId, planToday, "UTC")
        assertTrue(cache.snapshot!!.preview!!.steps.all { !it.added })
        val loaded = repository.load("user")
        assertTrue(loaded.snapshot.preview!!.steps.all { it.added })
        repository.apply("user", planRequestId, planToday.plusDays(1), "UTC")
        assertEquals(3, items.attempts.size)
    }
    @Test fun `conflicting records are never overwritten or called added`() = runTest {
        preview()
        savedPlan().steps.forEach { items.rows[it.id] = PlanItemState.CONFLICT }
        val result = repository.apply("user", planRequestId, planToday, "UTC")
        assertTrue(result.snapshot.preview!!.steps.all { !it.added && it.error != null })
        assertTrue(items.attempts.isEmpty())
    }
    @Test fun `failed per-step protection sends no further steps`() = runTest {
        preview(); cache.failAt = 1
        repository.apply("user", planRequestId, planToday, "UTC")
        assertTrue(items.attempts.isEmpty())
        assertTrue(cache.snapshot!!.preview!!.steps.all { it.applyDate == null })
    }
    @Test fun `failed progress cleanup can recover from protected attempt after restart`() = runTest {
        preview(); cache.failAt = 2
        val result = repository.apply("user", planRequestId, planToday, "UTC")
        assertTrue(result.snapshot.preview!!.steps[0].added)
        assertFalse(cache.snapshot!!.preview!!.steps[0].added)
        assertEquals(1, items.attempts.size)
        cache.failAt = Int.MAX_VALUE
        val loaded = CachedRecoveryRepository(cache, remote, items, sessions).load("user")
        assertTrue(loaded.snapshot.preview!!.steps[0].added)
        repository.apply("user", planRequestId, planToday.plusDays(1), "UTC")
        assertEquals(3, items.attempts.size)
    }
    @Test fun `offline plan can be read but adding cannot create items`() = runTest {
        preview(); items.offline = true
        assertTrue(repository.load("user").isOffline)
        repository.apply("user", planRequestId, planToday, "UTC")
        assertTrue(items.attempts.isEmpty())
    }
    @Test fun `account changes discard late generation and never cache its preview`() = runTest {
        val reply = CompletableDeferred<RecoveryResponse>()
        generate = { reply.await() }
        val work = async { repository.generate("user", planIntent()) }
        runCurrent(); sessions.current = "other"; reply.complete(planResponse())
        assertTrue(work.await() is RecoveryGenerateResult.Failure)
        assertNull(cache.snapshot!!.preview)
    }
    @Test fun `cancellation leaves a protected request for process restoration`() = runTest {
        generate = { throw CancellationException() }
        try { repository.generate("user", planIntent()); fail("Expected cancellation") } catch (_: CancellationException) { }
        assertEquals(planIntent(), cache.snapshot!!.pending)
    }
    @Test fun `discard removes only the local preview and keeps saved feature records`() = runTest {
        preview(); repository.apply("user", planRequestId, planToday, "UTC")
        repository.discard("user")
        assertNull(cache.snapshot!!.preview)
        assertEquals(3, items.rows.size)
    }
}
