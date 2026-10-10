package com.ahmad.nafs.ui.letters

import com.ahmad.nafs.core.letters.FutureLetter
import com.ahmad.nafs.core.letters.LetterCreateIntent
import com.ahmad.nafs.core.letters.LetterCursor
import com.ahmad.nafs.core.letters.LetterDraft
import com.ahmad.nafs.core.letters.LetterLoadResult
import com.ahmad.nafs.core.letters.LetterPage
import com.ahmad.nafs.core.letters.LetterRepository
import com.ahmad.nafs.core.letters.LetterResumeResult
import com.ahmad.nafs.core.letters.LetterSaveResult
import com.ahmad.nafs.core.letters.LetterSnapshot
import com.ahmad.nafs.core.letters.LetterReplyIntent
import com.ahmad.nafs.core.letters.LetterReplyResult
import com.ahmad.nafs.core.letters.letterIntent
import com.ahmad.nafs.core.letters.letterToday
import com.ahmad.nafs.core.letters.savedLetter
import com.ahmad.nafs.core.letters.dueLetter
import com.ahmad.nafs.core.letters.replyIntent
import com.ahmad.nafs.core.letters.repliedLetter
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LetterViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private class Repository : LetterRepository {
        var snapshot = LetterSnapshot()
        var offline = false
        var draftWrites = 0
        var failDraft = false
        val intents = mutableListOf<LetterCreateIntent>()
        var loadAction: suspend (String, LetterCursor?) -> LetterLoadResult = { _, _ -> LetterLoadResult.Loaded(snapshot.recent ?: LetterPage(emptyList()), snapshot.draft, snapshot.pending, offline) }
        var saveAction: suspend (LetterCreateIntent) -> LetterSaveResult = { LetterSaveResult.Failure("Retained", pending = true) }
        var resumeAction: suspend (LetterCreateIntent) -> LetterResumeResult = { LetterResumeResult.Editing(it.draft()) }
        val replyIntents = mutableListOf<LetterReplyIntent>()
        var replyAction: suspend (LetterReplyIntent) -> LetterReplyResult = { LetterReplyResult.Failure("Retained reply", true) }
        override suspend fun load(userId: String, cursor: LetterCursor?, detailId: String?) = loadAction(userId, cursor)
        override suspend fun generateReply(userId: String, intent: LetterReplyIntent, today: LocalDate): LetterReplyResult {
            replyIntents += intent
            return replyAction(intent)
        }
        override suspend fun save(userId: String, intent: LetterCreateIntent, today: LocalDate): LetterSaveResult {
            intents += intent
            snapshot = snapshot.copy(draft = intent.draft(), pending = intent)
            return saveAction(intent)
        }
        override suspend fun storeDraft(userId: String, draft: LetterDraft?) { check(!failDraft); draftWrites++; snapshot = snapshot.copy(draft = draft) }
        override suspend fun resumeDraft(userId: String, intent: LetterCreateIntent) = resumeAction(intent)
        override suspend fun acceptSaved(userId: String, intent: LetterCreateIntent): LetterSaveResult = LetterSaveResult.Saved(savedLetter())
        override suspend fun clear(userId: String) { snapshot = LetterSnapshot() }
    }
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun `new draft debounces encrypted persistence and restores across view models`() = runTest(dispatcher) {
        val repository = Repository()
        val model = LetterViewModel(repository)
        model.initialize("user", letterToday); runCurrent()
        model.newLetter(); model.setContent("First version"); runCurrent(); advanceTimeBy(200)
        model.setContent("Latest version"); runCurrent(); advanceTimeBy(401); runCurrent()
        assertEquals(1, repository.draftWrites)
        assertTrue(model.uiState.value.draftSaved)
        val restarted = LetterViewModel(repository)
        restarted.initialize("user", letterToday); runCurrent()
        assertEquals("Latest version", restarted.uiState.value.draft!!.content)
        assertEquals(model.uiState.value.draft!!.id, restarted.uiState.value.draft!!.id)
        assertEquals(LetterScreen.EDITOR, restarted.uiState.value.screen)
    }

    @Test fun `oversized draft remains intact and is unavailable for server saving`() = runTest(dispatcher) {
        val model = LetterViewModel(Repository())
        model.initialize("user", letterToday); runCurrent(); model.newLetter()
        val pasted = "personal writing ".repeat(1000)
        model.setContent(pasted)
        assertEquals(pasted, model.uiState.value.draft!!.content)
        assertFalse(model.uiState.value.canSave)
        advanceTimeBy(401); runCurrent()
        assertTrue(model.uiState.value.draftSaved)
    }

    @Test fun `save failure freezes the intent and retries preserve UUID and content`() = runTest(dispatcher) {
        val repository = Repository()
        val model = LetterViewModel(repository)
        model.initialize("user", letterToday); runCurrent(); model.newLetter(); model.setContent("  My message  ")
        model.save(); runCurrent()
        model.setContent("A different message"); model.newLetter(); model.discardDraft(); runCurrent()
        model.save(); runCurrent()
        assertEquals(2, repository.intents.size)
        assertEquals(repository.intents[0], repository.intents[1])
        assertEquals("My message", repository.intents[0].content)
        assertFalse(model.uiState.value.canEdit)
        assertTrue(model.uiState.value.draft!!.content.contains("My message"))
    }

    @Test fun `duplicate taps cannot overlap a save or reload its draft`() = runTest(dispatcher) {
        val repository = Repository()
        val delayed = CompletableDeferred<LetterSaveResult>()
        repository.saveAction = { delayed.await() }
        val model = LetterViewModel(repository)
        model.initialize("user", letterToday); runCurrent(); model.newLetter(); model.setContent("My letter")
        model.save(); runCurrent(); model.save(); model.reload(); runCurrent()
        assertEquals(1, repository.intents.size)
        assertTrue(model.uiState.value.isSaving)
        delayed.complete(LetterSaveResult.Failure("retained", true)); runCurrent()
        assertFalse(model.uiState.value.isSaving)
    }

    @Test fun `offline writing keeps the local draft but requires a connection to save`() = runTest(dispatcher) {
        val repository = Repository().apply { offline = true }
        val model = LetterViewModel(repository)
        model.initialize("user", letterToday); runCurrent(); model.newLetter(); model.setContent("Offline letter")
        advanceTimeBy(401); runCurrent()
        assertTrue(model.uiState.value.canEdit)
        assertFalse(model.uiState.value.canSave)
        assertEquals("Offline letter", repository.snapshot.draft!!.content)
        model.save(); runCurrent()
        assertTrue(repository.intents.isEmpty())
    }

    @Test fun `explicit pending reload recovers saved letter including its AI reply`() = runTest(dispatcher) {
        val repository = Repository().apply { snapshot = LetterSnapshot(draft = letterIntent().draft(), pending = letterIntent()) }
        val model = LetterViewModel(repository)
        model.initialize("user", letterToday); runCurrent()
        assertFalse(model.uiState.value.canEdit)
        val letter = savedLetter().copy(aiReplyText = "Saved answer")
        repository.loadAction = { _, _ -> LetterLoadResult.Loaded(LetterPage(listOf(letter)), recovered = letter) }
        model.reload(); runCurrent()
        assertEquals(LetterScreen.DETAIL, model.uiState.value.screen)
        assertNull(model.uiState.value.pending)
        assertNull(model.uiState.value.draft)
        assertEquals("Saved answer", model.uiState.value.selected!!.aiReplyText)
    }

    @Test fun `failed and offline reloads never unlock an unresolved save`() = runTest(dispatcher) {
        val repository = Repository().apply { snapshot = LetterSnapshot(draft = letterIntent().draft(), pending = letterIntent()) }
        val model = LetterViewModel(repository)
        model.initialize("user", letterToday); runCurrent()
        repository.loadAction = { _, _ -> LetterLoadResult.Failure("offline") }
        model.reload(); runCurrent(); model.keepEditing(); runCurrent()
        assertEquals(letterIntent(), model.uiState.value.pending)
        assertFalse(model.uiState.value.canEdit)
        assertFalse(model.uiState.value.pendingMissing)
    }

    @Test fun `saved letter with failed local cleanup stays visible and prevents a second draft`() = runTest(dispatcher) {
        val repository = Repository()
        repository.saveAction = { intent -> LetterSaveResult.Saved(FutureLetter(intent.id, intent.userId, intent.content,
            savedLetter().writtenAt, intent.targetDate), localCleanupFailed = true) }
        val model = LetterViewModel(repository)
        model.initialize("user", letterToday); runCurrent(); model.newLetter(); model.setContent("A saved letter")
        val id = model.uiState.value.draft!!.id
        model.save(); runCurrent()
        assertEquals(LetterScreen.DETAIL, model.uiState.value.screen)
        assertEquals(id, model.uiState.value.selected!!.id)
        assertEquals(id, model.uiState.value.pending!!.id)
        model.closeEditor(); model.newLetter(); runCurrent()
        assertEquals(id, model.uiState.value.draft!!.id)
        assertEquals(1, repository.intents.size)
    }

    @Test fun `checked missing save can resume the same draft without changing its identity`() = runTest(dispatcher) {
        val repository = Repository().apply { snapshot = LetterSnapshot(draft = letterIntent().draft(), pending = letterIntent()) }
        repository.loadAction = { _, _ -> LetterLoadResult.Loaded(LetterPage(emptyList()), letterIntent().draft(), letterIntent(), pendingMissing = true) }
        val model = LetterViewModel(repository)
        model.initialize("user", letterToday); runCurrent(); model.keepEditing(); runCurrent()
        assertNull(model.uiState.value.pending)
        assertEquals(letterIntent().id, model.uiState.value.draft!!.id)
        assertTrue(model.uiState.value.canEdit)
    }

    @Test fun `editor closing flushes the latest text and failed discard preserves it`() = runTest(dispatcher) {
        val repository = Repository()
        val model = LetterViewModel(repository)
        model.initialize("user", letterToday); runCurrent(); model.newLetter(); model.setContent("Latest unflushed letter")
        model.closeEditor(); runCurrent()
        assertEquals(LetterScreen.ARCHIVE, model.uiState.value.screen)
        assertEquals("Latest unflushed letter", repository.snapshot.draft!!.content)
        model.openDraft(); repository.failDraft = true; model.discardDraft(); runCurrent()
        assertEquals("Latest unflushed letter", model.uiState.value.draft!!.content)
        assertEquals(LetterScreen.EDITOR, model.uiState.value.screen)
        repository.failDraft = false; model.discardDraft(); runCurrent()
        assertNull(model.uiState.value.draft)
        assertNull(repository.snapshot.draft)
    }

    @Test fun `date rollover changes new draft validity and does not discard its text`() = runTest(dispatcher) {
        val repository = Repository()
        val model = LetterViewModel(repository)
        model.initialize("user", letterToday); runCurrent(); model.newLetter(); model.setContent("Keep my words")
        model.setTargetDate(letterToday.plusDays(1).toString())
        assertTrue(model.uiState.value.canSave)
        model.refresh(letterToday.plusDays(1)); runCurrent()
        assertFalse(model.uiState.value.canSave)
        assertEquals("Keep my words", model.uiState.value.draft!!.content)
        advanceTimeBy(401); runCurrent()
    }

    @Test fun `late old account result cannot replace the current account screen`() = runTest(dispatcher) {
        val repository = Repository()
        val delayed = CompletableDeferred<LetterLoadResult>()
        repository.loadAction = { user, _ -> if (user == "user") withContext(NonCancellable) { delayed.await() }
            else LetterLoadResult.Loaded(LetterPage(listOf(savedLetter(user = user)))) }
        val model = LetterViewModel(repository)
        model.initialize("user", letterToday); runCurrent()
        model.initialize("other", letterToday); runCurrent()
        delayed.complete(LetterLoadResult.Loaded(LetterPage(listOf(savedLetter())))); runCurrent()
        assertEquals(listOf("other"), model.uiState.value.letters.map { it.userId })
        assertFalse(model.uiState.value.isLoading)
    }

    @Test fun `reply action requires due date and online access`() = runTest(dispatcher) {
        val repository = Repository().apply { snapshot = LetterSnapshot(recent = LetterPage(listOf(dueLetter()))) }
        val model = LetterViewModel(repository)
        model.initialize("user", letterToday.minusDays(1)); runCurrent(); model.openLetter(dueLetter().id)
        assertFalse(model.uiState.value.canGenerateReply)
        model.generateReply(); runCurrent(); assertTrue(repository.replyIntents.isEmpty())
        model.refresh(letterToday); runCurrent()
        assertTrue(model.uiState.value.canGenerateReply)
        repository.offline = true; model.reload(); runCurrent()
        assertFalse(model.uiState.value.canGenerateReply)
        model.generateReply(); runCurrent(); assertTrue(repository.replyIntents.isEmpty())
    }

    @Test fun `duplicate reply taps are blocked and retry keeps the exact request`() = runTest(dispatcher) {
        val repository = Repository().apply { snapshot = LetterSnapshot(recent = LetterPage(listOf(dueLetter()))) }
        val finish = CompletableDeferred<LetterReplyResult>()
        repository.replyAction = { finish.await() }
        val model = LetterViewModel(repository)
        model.initialize("user", letterToday); runCurrent(); model.openLetter(dueLetter().id)
        model.generateReply(); runCurrent(); model.generateReply(); model.reload(); runCurrent()
        assertTrue(model.uiState.value.isSaving)
        assertEquals(1, repository.replyIntents.size)
        finish.complete(LetterReplyResult.Failure("Timed out", true)); runCurrent()
        val retained = model.uiState.value.pendingReply
        assertNull(model.uiState.value.selected!!.aiReplyText)
        model.generateReply(); runCurrent()
        assertEquals(listOf(retained, retained), repository.replyIntents)
    }

    @Test fun `restarted pending reply opens its letter and reload can recover the saved answer`() = runTest(dispatcher) {
        val repository = Repository()
        repository.loadAction = { _, _ -> LetterLoadResult.Loaded(LetterPage(listOf(dueLetter())), pendingReply = replyIntent(), detail = dueLetter()) }
        val model = LetterViewModel(repository)
        model.initialize("user", letterToday); runCurrent()
        assertEquals(LetterScreen.DETAIL, model.uiState.value.screen)
        assertEquals(replyIntent(), model.uiState.value.pendingReply)
        repository.loadAction = { _, _ -> LetterLoadResult.Loaded(LetterPage(listOf(repliedLetter())), detail = repliedLetter()) }
        model.reload(); runCurrent()
        assertEquals(repliedLetter(), model.uiState.value.selected)
        assertNull(model.uiState.value.pendingReply)
        assertFalse(model.uiState.value.canGenerateReply)
        assertTrue(repository.replyIntents.isEmpty())
    }

    @Test fun `successful reply displays partial context and history failure with the saved answer`() = runTest(dispatcher) {
        val repository = Repository().apply { snapshot = LetterSnapshot(recent = LetterPage(listOf(dueLetter()))) }
        repository.replyAction = { LetterReplyResult.Saved(repliedLetter(), contextPartial = true, historySaved = false) }
        val model = LetterViewModel(repository)
        model.initialize("user", letterToday); runCurrent(); model.openLetter(dueLetter().id); model.generateReply(); runCurrent()
        assertEquals(repliedLetter(), model.uiState.value.selected)
        assertNull(model.uiState.value.pendingReply)
        assertTrue(model.uiState.value.message!!.contains("unavailable"))
        assertTrue(model.uiState.value.message!!.contains("archive copy"))
        model.generateReply(); runCurrent(); assertEquals(1, repository.replyIntents.size)
    }
    @Test fun `a reply recovered during startup opens the saved answer directly`() = runTest(dispatcher) {
        val repository = Repository()
        repository.loadAction = { _, _ -> LetterLoadResult.Loaded(LetterPage(listOf(repliedLetter())), detail = repliedLetter()) }
        val model = LetterViewModel(repository)
        model.initialize("user", letterToday); runCurrent()
        assertEquals(LetterScreen.DETAIL, model.uiState.value.screen)
        assertEquals(repliedLetter(), model.uiState.value.selected)
        assertNull(model.uiState.value.pendingReply)
    }

    @Test fun `late reply from a previous account cannot appear in the new account`() = runTest(dispatcher) {
        val repository = Repository().apply { snapshot = LetterSnapshot(recent = LetterPage(listOf(dueLetter()))) }
        val finish = CompletableDeferred<LetterReplyResult>()
        repository.replyAction = { withContext(NonCancellable) { finish.await() } }
        val model = LetterViewModel(repository)
        model.initialize("user", letterToday); runCurrent(); model.openLetter(dueLetter().id); model.generateReply(); runCurrent()
        repository.loadAction = { _, _ -> LetterLoadResult.Loaded(LetterPage(emptyList())) }
        model.initialize("other", letterToday); runCurrent()
        finish.complete(LetterReplyResult.Saved(repliedLetter())); runCurrent()
        assertTrue(model.uiState.value.letters.isEmpty())
        assertNull(model.uiState.value.selected)
        assertNull(model.uiState.value.pendingReply)
    }
}
