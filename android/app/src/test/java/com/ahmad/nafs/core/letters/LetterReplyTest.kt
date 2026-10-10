package com.ahmad.nafs.core.letters

import com.ahmad.nafs.core.network.ApiFailure
import com.ahmad.nafs.core.network.ApiSessionTokens
import com.ahmad.nafs.core.network.NafsApiClient
import com.ahmad.nafs.core.network.NafsApiException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

internal fun dueLetter() = savedLetter().copy(targetDate = letterToday.toString())
internal fun replyIntent() = LetterReplyIntent(dueLetter(), "00000000-0000-4000-8000-000000000099")
internal fun repliedLetter() = dueLetter().copy(aiReplyText = "A saved coach reply.", deliveredAt = "2026-10-10T23:00:00Z")

class LetterReplyTest {
    private class Sessions : ApiSessionTokens {
        var current = "user"
        override fun isCurrentUser(userId: String) = current == userId
        override suspend fun accessToken(userId: String, forceRefresh: Boolean) = "verified-token"
    }
    private class Cache : LetterCache {
        var snapshot: LetterSnapshot? = LetterSnapshot(recent = LetterPage(listOf(dueLetter())))
        var failWrites = false
        override suspend fun read(userId: String) = snapshot
        override suspend fun write(userId: String, snapshot: LetterSnapshot) { check(!failWrites); this.snapshot = snapshot }
        override suspend fun clear(userId: String) { snapshot = null }
    }
    private class Remote : LetterRemote {
        var row: FutureLetter? = dueLetter()
        var offline = false
        var getAction: suspend () -> FutureLetter? = { check(!offline); row }
        override suspend fun list(userId: String, cursor: LetterCursor?): List<FutureLetter> { check(!offline); return listOfNotNull(row) }
        override suspend fun get(userId: String, letterId: String): FutureLetter? = getAction()
        override suspend fun create(intent: LetterCreateIntent): FutureLetter = error("Reply must not create a letter")
    }
    private val sessions = Sessions()
    private val cache = Cache()
    private val remote = Remote()
    private val intents = mutableListOf<LetterReplyIntent>()
    private var generate: suspend (LetterReplyIntent) -> LetterReplyResponse = {
        LetterReplyResponse(repliedLetter().aiReplyText!!, repliedLetter()).also { remote.row = it.letter }
    }
    private val replies = LetterReplyRemote { _, intent ->
        assertEquals(intent, cache.snapshot!!.pendingReply)
        intents += intent
        generate(intent)
    }
    private val repository = CachedLetterRepository(remote, cache, sessions, replies)

    @Test fun `UTF8 fingerprint agrees with Node and preserves raw timestamp precision`() {
        val letter = dueLetter().copy(content = "Dear me: امید 🌱\nKeep going.")
        assertEquals("4f0b538a4af26256b79aeba1290840986807b0b6775cc3c154a486a227493427", LetterReplyRules.fingerprint(letter))
        assertNotEquals(LetterReplyRules.fingerprint(letter), LetterReplyRules.fingerprint(letter.copy(writtenAt = "2026-10-10T22:30:00.123457+00:00")))
        assertEquals(LetterReplyRules.fingerprint(letter), LetterReplyRules.fingerprint(letter.copy(aiReplyText = "Saved", deliveredAt = "2026-10-10T23:00:00Z")))
    }
    @Test fun `only due original letters are eligible and replies match all author fields`() {
        assertTrue(LetterReplyRules.eligible(dueLetter(), letterToday))
        assertFalse(LetterReplyRules.eligible(dueLetter(), letterToday.minusDays(1)))
        assertFalse(LetterReplyRules.eligible(repliedLetter(), letterToday))
        assertTrue(LetterReplyRules.matches("user", replyIntent(), repliedLetter()))
        listOf(repliedLetter().copy(userId = "other"), repliedLetter().copy(content = "Different"),
            repliedLetter().copy(aiReplyText = " "), repliedLetter().copy(deliveredAt = null)).forEach {
            assertFalse(LetterReplyRules.matches("user", replyIntent(), it))
        }
    }
    @Test fun `request persists before generation and success clears only the reply intent`() = runTest {
        cache.snapshot = cache.snapshot!!.copy(draft = letterIntent(2).draft())
        val result = repository.generateReply("user", replyIntent(), letterToday) as LetterReplyResult.Saved
        assertEquals(repliedLetter(), result.letter)
        assertNull(cache.snapshot!!.pendingReply)
        assertEquals(letterIntent(2).draft(), cache.snapshot!!.draft)
        assertEquals(repliedLetter(), cache.snapshot!!.recent!!.letters.single())
    }
    @Test fun `local persistence failure or a future revisit prevents any provider request`() = runTest {
        cache.failWrites = true
        assertFalse((repository.generateReply("user", replyIntent(), letterToday) as LetterReplyResult.Failure).pending)
        cache.failWrites = false
        assertFalse((repository.generateReply("user", replyIntent(), letterToday.minusDays(1)) as LetterReplyResult.Failure).pending)
        assertTrue(intents.isEmpty())
    }
    @Test fun `timeout retains exact request across process recreation and offline loading`() = runTest {
        generate = { throw NafsApiException(ApiFailure.TIMEOUT) }
        assertTrue((repository.generateReply("user", replyIntent(), letterToday) as LetterReplyResult.Failure).pending)
        remote.offline = true
        val restarted = CachedLetterRepository(remote, cache, sessions, replies)
        val load = restarted.load("user") as LetterLoadResult.Loaded
        assertTrue(load.isOffline)
        assertEquals(replyIntent(), load.pendingReply)
        assertEquals(dueLetter(), load.detail)
        remote.offline = false
        generate = { LetterReplyResponse(repliedLetter().aiReplyText!!, repliedLetter()) }
        assertTrue(restarted.generateReply("user", load.pendingReply!!, letterToday) is LetterReplyResult.Saved)
        assertEquals(listOf(replyIntent(), replyIntent()), intents)
    }
    @Test fun `clock rollback never discards an already retained request`() = runTest {
        cache.snapshot = cache.snapshot!!.copy(pendingReply = replyIntent())
        val result = repository.generateReply("user", replyIntent(), letterToday.minusDays(1)) as LetterReplyResult.Failure
        assertTrue(result.pending)
        assertEquals(replyIntent(), cache.snapshot!!.pendingReply)
        assertTrue(intents.isEmpty())
    }
    @Test fun `a dropped response reloads a committed reply without regeneration`() = runTest {
        generate = { remote.row = repliedLetter(); throw NafsApiException(ApiFailure.RESULT_UNCONFIRMED) }
        repository.generateReply("user", replyIntent(), letterToday)
        val load = repository.load("user") as LetterLoadResult.Loaded
        assertEquals(repliedLetter(), load.detail)
        assertNull(load.pendingReply)
        assertNull(cache.snapshot!!.pendingReply)
        assertEquals(1, intents.size)
    }
    @Test fun `an old detail refresh cannot erase a reply confirmed while it was running`() = runTest {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var calls = 0
        remote.getAction = {
            if (++calls == 1) { started.complete(Unit); finish.await(); dueLetter() } else remote.row
        }
        val refresh = async { repository.load("user", detailId = dueLetter().id) }
        started.await()
        assertTrue(repository.generateReply("user", replyIntent(), letterToday) is LetterReplyResult.Saved)
        finish.complete(Unit)
        val loaded = refresh.await() as LetterLoadResult.Loaded
        assertEquals(repliedLetter(), loaded.detail)
        assertEquals(repliedLetter(), loaded.page.letters.single())
        assertEquals(repliedLetter(), cache.snapshot!!.recent!!.letters.single())
        assertNull(loaded.pendingReply)
    }
    @Test fun `old encrypted snapshot JSON still decodes with no reply intent`() {
        val old = Json.encodeToJsonElement(LetterSnapshot(recent = LetterPage(listOf(dueLetter())))).toString()
        assertFalse(old.contains("pendingReply"))
        assertNull(Json.decodeFromString<LetterSnapshot>(old).pendingReply)
    }
    @Test fun `retry adopts an existing saved reply without contacting the provider`() = runTest {
        cache.snapshot = cache.snapshot!!.copy(pendingReply = replyIntent())
        remote.row = repliedLetter()
        val result = repository.generateReply("user", replyIntent(), letterToday) as LetterReplyResult.Saved
        assertEquals(repliedLetter(), result.letter)
        assertTrue(intents.isEmpty())
    }
    @Test fun `peer edit blocks generation and online reload clears the obsolete intent`() = runTest {
        remote.row = dueLetter().copy(content = "Changed elsewhere")
        val result = repository.generateReply("user", replyIntent(), letterToday) as LetterReplyResult.Failure
        assertTrue(result.pending)
        assertTrue(intents.isEmpty())
        val load = repository.load("user", detailId = dueLetter().id) as LetterLoadResult.Loaded
        assertEquals("Changed elsewhere", load.detail!!.content)
        assertNull(load.pendingReply)
    }
    @Test fun `online missing record clears cached detail while offline cannot unlock a retained request`() = runTest {
        cache.snapshot = cache.snapshot!!.copy(pendingReply = replyIntent())
        remote.offline = true
        assertEquals(replyIntent(), (repository.load("user") as LetterLoadResult.Loaded).pendingReply)
        remote.offline = false; remote.row = null
        val missing = repository.load("user") as LetterLoadResult.Loaded
        assertTrue(missing.detailMissing)
        assertNull(missing.pendingReply)
        assertTrue(cache.snapshot!!.recent!!.letters.isEmpty())
    }
    @Test fun `cache cleanup failure preserves a confirmed reply and retained request`() = runTest {
        generate = { cache.failWrites = true; LetterReplyResponse(repliedLetter().aiReplyText!!, repliedLetter()) }
        val saved = repository.generateReply("user", replyIntent(), letterToday) as LetterReplyResult.Saved
        assertTrue(saved.localCleanupFailed)
        assertEquals(repliedLetter(), saved.letter)
        assertEquals(replyIntent(), cache.snapshot!!.pendingReply)
    }
    @Test fun `malformed or cross account result is never shown as a saved reply`() = runTest {
        generate = { LetterReplyResponse("Other", repliedLetter().copy(userId = "other", aiReplyText = "Other")) }
        assertTrue(repository.generateReply("user", replyIntent(), letterToday) is LetterReplyResult.Failure)
        assertEquals(dueLetter(), cache.snapshot!!.recent!!.letters.single())
    }
    @Test fun `account switch and cancellation cannot cache late server results`() = runTest {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<LetterReplyResponse>()
        generate = { started.complete(Unit); finish.await() }
        val action = async { repository.generateReply("user", replyIntent(), letterToday) }
        started.await(); sessions.current = "other"; finish.complete(LetterReplyResponse(repliedLetter().aiReplyText!!, repliedLetter()))
        assertTrue(action.await() is LetterReplyResult.Failure)
        assertEquals(dueLetter(), cache.snapshot!!.recent!!.letters.single())
        sessions.current = "user"; generate = { throw CancellationException() }
        assertTrue(runCatching { repository.generateReply("user", replyIntent(), letterToday) }.exceptionOrNull() is CancellationException)
    }
    @Test fun `API sends only fingerprint and stable IDs under the verified bearer token`() = runBlocking {
        val engine = MockEngine { request ->
            assertEquals("/api/ai/future-self", request.url.encodedPath)
            assertEquals("Bearer verified-token", request.headers[HttpHeaders.Authorization])
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals(setOf("letter_id", "expected_fingerprint", "request_id"), body.keys)
            assertEquals(LetterReplyRules.fingerprint(dueLetter()), body["expected_fingerprint"]!!.jsonPrimitive.content)
            assertEquals(replyIntent().requestId, body["request_id"]!!.jsonPrimitive.content)
            respond(Json.encodeToJsonElement(LetterReplyResponse(repliedLetter().aiReplyText!!, repliedLetter())).toString())
        }
        HttpClient(engine) { followRedirects = false }.use {
            assertEquals(repliedLetter(), ApiLetterReplyRemote(NafsApiClient(it, sessions, "https://nafs.example")).generate("user", replyIntent()).letter)
        }
    }
}
