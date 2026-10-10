package com.ahmad.nafs.core.letters

import com.ahmad.nafs.core.network.ApiSessionTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CachedLetterRepositoryTest {
    private class Sessions : ApiSessionTokens {
        var current = "user"
        override fun isCurrentUser(userId: String) = current == userId
        override suspend fun accessToken(userId: String, forceRefresh: Boolean) = "token"
    }
    private class Cache : LetterCache {
        var snapshot: LetterSnapshot? = null
        var failWrites = false
        override suspend fun read(userId: String) = snapshot
        override suspend fun write(userId: String, snapshot: LetterSnapshot) { check(!failWrites); this.snapshot = snapshot }
        override suspend fun clear(userId: String) { snapshot = null }
    }
    private class Remote : LetterRemote {
        val rows = mutableMapOf<String, FutureLetter>()
        val intents = mutableListOf<LetterCreateIntent>()
        var listAction: suspend () -> List<FutureLetter> = { rows.values.sortedWith(LetterRules.newestFirst).take(21) }
        var getAction: suspend (String) -> FutureLetter? = { rows[it] }
        var createAction: suspend (LetterCreateIntent) -> FutureLetter = { intent ->
            FutureLetter(intent.id, intent.userId, intent.content, savedLetter().writtenAt, intent.targetDate).also { rows[it.id] = it }
        }
        override suspend fun list(userId: String, cursor: LetterCursor?) = listAction()
        override suspend fun get(userId: String, letterId: String) = getAction(letterId)
        override suspend fun create(intent: LetterCreateIntent): FutureLetter { intents += intent; return createAction(intent) }
    }
    private val sessions = Sessions()
    private val cache = Cache()
    private val remote = Remote()
    private val replies = LetterReplyRemote { _, _ -> error("Unused reply service") }
    private val repository = CachedLetterRepository(remote, cache, sessions, replies)

    @Test fun `intent is persisted before a create and response loss recovers the same UUID`() = runTest {
        remote.createAction = { intent ->
            assertEquals(intent, cache.snapshot!!.pending)
            remote.rows[intent.id] = savedLetter()
            error("response lost")
        }
        val saved = repository.save("user", letterIntent(), letterToday) as LetterSaveResult.Saved
        assertEquals(savedLetter(), saved.letter)
        assertNull(cache.snapshot!!.pending)
        assertNull(cache.snapshot!!.draft)
        repository.save("user", letterIntent(), letterToday)
        assertEquals(1, remote.intents.size)
        assertEquals(1, remote.rows.size)
    }

    @Test fun `failed create retains its exact intent across repository recreation`() = runTest {
        remote.createAction = { error("offline") }
        val failed = repository.save("user", letterIntent(), letterToday) as LetterSaveResult.Failure
        assertTrue(failed.pending)
        assertEquals(letterIntent(), cache.snapshot!!.pending)
        val restarted = CachedLetterRepository(remote, cache, sessions, replies)
        remote.listAction = { error("offline") }
        val loaded = restarted.load("user") as LetterLoadResult.Loaded
        assertTrue(loaded.isOffline)
        assertEquals(letterIntent(), loaded.pending)
        remote.createAction = { savedLetter().also { remote.rows[it.id] = it } }
        assertTrue(restarted.save("user", loaded.pending!!, letterToday.plusYears(1)) is LetterSaveResult.Saved)
        assertEquals(listOf(letterIntent(), letterIntent()), remote.intents)
    }

    @Test fun `required local persistence failure prevents contacting the write service`() = runTest {
        cache.failWrites = true
        val result = repository.save("user", letterIntent(), letterToday) as LetterSaveResult.Failure
        assertFalse(result.pending)
        assertTrue(remote.intents.isEmpty())
        assertTrue(remote.rows.isEmpty())
    }

    @Test fun `new letter cannot use today but an exact retained retry can cross its revisit date`() = runTest {
        val intent = letterIntent().copy(targetDate = letterToday.toString())
        assertTrue(repository.save("user", intent, letterToday) is LetterSaveResult.Failure)
        assertTrue(remote.intents.isEmpty())
        cache.snapshot = LetterSnapshot(draft = intent.draft(), pending = intent)
        assertTrue(repository.save("user", intent, letterToday.plusDays(1)) is LetterSaveResult.Saved)
    }

    @Test fun `a different saved letter cannot be overwritten or falsely adopted`() = runTest {
        remote.rows[letterIntent().id] = savedLetter().copy(content = "Written on another device")
        val result = repository.save("user", letterIntent(), letterToday) as LetterSaveResult.Failure
        assertTrue(result.pending)
        assertTrue(remote.intents.isEmpty())
        assertEquals("Written on another device", remote.rows[letterIntent().id]!!.content)
        assertEquals(letterIntent(), cache.snapshot!!.pending)
    }

    @Test fun `saved reply and delivery metadata survive an idempotent retry`() = runTest {
        val delivered = savedLetter().copy(deliveredAt = "2027-04-10T12:00:00Z", aiReplyText = "Saved coach reply")
        remote.rows[delivered.id] = delivered
        val result = repository.save("user", letterIntent(), letterToday) as LetterSaveResult.Saved
        assertEquals(delivered, result.letter)
        assertTrue(remote.intents.isEmpty())
    }

    @Test fun `accepting a conflicting saved letter refreshes it and keeps its latest reply`() = runTest {
        cache.snapshot = LetterSnapshot(draft = letterIntent().draft(), pending = letterIntent())
        val different = savedLetter().copy(content = "A different saved letter")
        remote.rows[different.id] = different
        assertEquals(different, (repository.load("user") as LetterLoadResult.Loaded).different)
        val latest = different.copy(deliveredAt = "2027-04-10T00:00:00Z", aiReplyText = "A newly saved reply")
        remote.rows[latest.id] = latest
        val accepted = repository.acceptSaved("user", letterIntent()) as LetterSaveResult.Saved
        assertEquals(latest, accepted.letter)
        assertNull(cache.snapshot!!.pending)
        assertNull(cache.snapshot!!.draft)
        assertTrue(remote.intents.isEmpty())
    }

    @Test fun `missing or foreign saved version cannot clear a retained draft`() = runTest {
        cache.snapshot = LetterSnapshot(draft = letterIntent().draft(), pending = letterIntent())
        assertTrue(repository.acceptSaved("user", letterIntent()) is LetterSaveResult.Failure)
        remote.getAction = { savedLetter(user = "other") }
        assertTrue(repository.acceptSaved("user", letterIntent()) is LetterSaveResult.Failure)
        assertEquals(letterIntent(), cache.snapshot!!.pending)
        assertEquals(letterIntent().draft(), cache.snapshot!!.draft)
    }

    @Test fun `online reload confirms a pending save and clears its retained draft`() = runTest {
        cache.snapshot = LetterSnapshot(draft = letterIntent().draft(), pending = letterIntent())
        remote.rows[savedLetter().id] = savedLetter()
        val result = repository.load("user") as LetterLoadResult.Loaded
        assertEquals(savedLetter(), result.recovered)
        assertNull(result.pending)
        assertNull(result.draft)
        assertFalse(result.pendingMissing)
    }

    @Test fun `missing pending letter can resume editing with the same UUID after another online check`() = runTest {
        cache.snapshot = LetterSnapshot(draft = letterIntent().draft(), pending = letterIntent())
        assertTrue((repository.load("user") as LetterLoadResult.Loaded).pendingMissing)
        val resumed = repository.resumeDraft("user", letterIntent()) as LetterResumeResult.Editing
        assertEquals(letterIntent().draft(), resumed.draft)
        assertNull(cache.snapshot!!.pending)
        assertEquals(letterIntent().draft(), cache.snapshot!!.draft)
    }

    @Test fun `letter appearing between reload and resume is recovered instead of reopening the editor`() = runTest {
        cache.snapshot = LetterSnapshot(draft = letterIntent().draft(), pending = letterIntent())
        repository.load("user")
        remote.rows[savedLetter().id] = savedLetter()
        assertTrue(repository.resumeDraft("user", letterIntent()) is LetterResumeResult.Saved)
        assertNull(cache.snapshot!!.pending)
    }

    @Test fun `local cleanup failure still reports the authoritative saved letter and retains retry state`() = runTest {
        remote.createAction = { intent -> savedLetter().also { remote.rows[intent.id] = it; cache.failWrites = true } }
        val result = repository.save("user", letterIntent(), letterToday) as LetterSaveResult.Saved
        assertTrue(result.localCleanupFailed)
        assertEquals(letterIntent(), cache.snapshot!!.pending)
        cache.failWrites = false
        val refreshed = repository.load("user") as LetterLoadResult.Loaded
        assertEquals(savedLetter(), refreshed.recovered)
        assertNull(refreshed.pending)
    }

    @Test fun `offline drafts and recent letters remain readable without enabling older pages`() = runTest {
        repository.storeDraft("user", letterIntent().draft())
        remote.listAction = { error("offline") }
        val loaded = repository.load("user") as LetterLoadResult.Loaded
        assertTrue(loaded.isOffline)
        assertEquals(letterIntent().draft(), loaded.draft)
        assertTrue(loaded.page.letters.isEmpty())
        assertTrue(repository.load("user", LetterCursor(savedLetter().writtenAt, savedLetter().id)) is LetterLoadResult.Failure)
    }

    @Test fun `online empty archive removes stale rows while keeping the local draft`() = runTest {
        cache.snapshot = LetterSnapshot(recent = LetterPage(listOf(savedLetter())), draft = letterIntent(2).draft())
        val result = repository.load("user") as LetterLoadResult.Loaded
        assertTrue(result.page.letters.isEmpty())
        assertEquals(letterIntent(2).draft(), cache.snapshot!!.draft)
        assertTrue(cache.snapshot!!.recent!!.letters.isEmpty())
    }

    @Test fun `refresh protects concurrently confirmed creates and adjusts its page boundary`() = runTest {
        val oldRows = (1..21).map { savedLetter(it) }
        cache.snapshot = LetterSnapshot(recent = LetterRules.page("user", null, oldRows))
        val delayed = CompletableDeferred<List<FutureLetter>>()
        remote.listAction = { delayed.await() }
        val load = async { repository.load("user") }; runCurrent()
        val newLetter = savedLetter(22).copy(writtenAt = "2026-10-11T00:00:00Z")
        remote.createAction = { newLetter.also { remote.rows[it.id] = it } }
        repository.save("user", letterIntent(22), letterToday)
        delayed.complete(oldRows)
        val result = load.await() as LetterLoadResult.Loaded
        assertEquals(newLetter, result.page.letters.first())
        assertEquals(savedLetter(3).id, result.page.next!!.id)
        assertEquals(20, result.page.letters.size)
    }

    @Test fun `account change during a request returns neither old cached data nor a saved result`() = runTest {
        cache.snapshot = LetterSnapshot(draft = letterIntent().draft())
        remote.createAction = { sessions.current = "other"; savedLetter() }
        assertTrue(repository.save("user", letterIntent(), letterToday) is LetterSaveResult.Failure)
        assertTrue(repository.load("user") is LetterLoadResult.Failure)
        assertTrue(runCatching { repository.storeDraft("user", letterIntent().draft()) }.isFailure)
    }

    @Test fun `draft writes cannot discard a pending intent and cancellation propagates`() = runTest {
        cache.snapshot = LetterSnapshot(draft = letterIntent().draft(), pending = letterIntent())
        assertTrue(runCatching { repository.storeDraft("user", null) }.isFailure)
        assertEquals(letterIntent(), cache.snapshot!!.pending)
        remote.listAction = { throw CancellationException("cancelled") }
        assertTrue(runCatching { repository.load("user") }.exceptionOrNull() is CancellationException)
        repository.clear("user")
        assertNull(cache.snapshot)
    }
}
