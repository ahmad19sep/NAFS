package com.ahmad.nafs.ui.checkin

import com.ahmad.nafs.core.checkin.CheckInDraft
import com.ahmad.nafs.core.checkin.CheckInDraftStore
import com.ahmad.nafs.core.checkin.CheckInDrafts
import com.ahmad.nafs.core.checkin.CheckInLoadResult
import com.ahmad.nafs.core.checkin.CheckInRepository
import com.ahmad.nafs.core.checkin.CheckInSaveResult
import com.ahmad.nafs.core.checkin.CheckInTask
import com.ahmad.nafs.core.checkin.DailyCheckIn
import com.ahmad.nafs.core.checkin.EveningVerdictIntent
import com.ahmad.nafs.core.checkin.EveningVerdictRepository
import com.ahmad.nafs.core.checkin.EveningVerdictResult
import com.ahmad.nafs.core.network.ApiFailure
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CheckInViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val evening = Clock.fixed(Instant.parse("2026-10-10T17:00:00Z"), ZoneOffset.UTC)

    private class Repository : CheckInRepository {
        var record: DailyCheckIn? = null
        var offline = false
        var saveResult: ((DailyCheckIn) -> CheckInSaveResult)? = null
        val saves = mutableListOf<DailyCheckIn>()
        override suspend fun load(userId: String, date: LocalDate) =
            CheckInLoadResult.Success(record?.takeIf { it.date == date.toString() }, offline)
        override suspend fun save(record: DailyCheckIn, expected: DailyCheckIn?): CheckInSaveResult {
            saves += record
            return saveResult?.invoke(record) ?: CheckInSaveResult.Saved(record.copy(updatedAt = "2026-10-10T17:30:00Z")).also {
                this.record = it.record
            }
        }
        override suspend fun cacheGeneratedReview(record: DailyCheckIn) = Unit
        override suspend fun clear(userId: String) = Unit
    }

    private class Verdicts : EveningVerdictRepository {
        override suspend fun generate(userId: String, intent: EveningVerdictIntent) =
            EveningVerdictResult.Failure(ApiFailure.NETWORK, "Unavailable")
    }

    private class Drafts : CheckInDraftStore {
        var current: CheckInDraft? = null
        var previous: CheckInDraft? = null
        var writes = 0
        var failWrites = false
        override suspend fun read(userId: String) = CheckInDrafts(current, previous)
        override suspend fun writeCurrent(userId: String, draft: CheckInDraft?) {
            check(!failWrites)
            writes++
            current = draft
        }
        override suspend fun writePrevious(userId: String, draft: CheckInDraft?) {
            check(!failWrites)
            previous = draft
        }
    }

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    private fun model(repository: Repository, drafts: Drafts, clock: Clock = evening) =
        CheckInViewModel(repository, Verdicts(), drafts).also { it.clock = clock }

    @Test fun `evening text autosaves once after a one second pause`() = runTest(dispatcher) {
        val drafts = Drafts()
        val model = model(Repository(), drafts)
        model.initialize("user", "UTC"); runCurrent()
        model.setEveningText("Long day"); runCurrent()
        assertEquals(DraftStatus.PENDING, model.uiState.value.draftStatus)
        advanceTimeBy(600); model.setEveningText("Long day, but calm"); runCurrent()
        advanceTimeBy(900); runCurrent()
        assertEquals(0, drafts.writes)
        advanceTimeBy(200); runCurrent()
        assertEquals(1, drafts.writes)
        assertEquals("Long day, but calm", drafts.current?.eveningText)
        assertEquals(DraftStatus.SAVED, model.uiState.value.draftStatus)
    }

    @Test fun `a killed app restores the unsaved draft on the next open`() = runTest(dispatcher) {
        val repository = Repository()
        val drafts = Drafts()
        val first = model(repository, drafts)
        first.initialize("user", "UTC"); runCurrent()
        first.setNewTask("Walk after Asr"); first.addTask()
        first.setEveningText("Notes I never saved")
        first.flushDraft(); runCurrent()
        val restarted = model(repository, drafts)
        restarted.initialize("user", "UTC"); runCurrent()
        val state = restarted.uiState.value
        assertEquals(listOf(CheckInTask("Walk after Asr")), state.draft?.tasks)
        assertEquals("Notes I never saved", state.draft?.eveningText)
        assertEquals("Your unsaved draft was restored.", state.draftNotice)
        assertTrue(state.hasChanges)
    }

    @Test fun `a successful save clears the stored draft`() = runTest(dispatcher) {
        val repository = Repository()
        val drafts = Drafts()
        val model = model(repository, drafts)
        model.initialize("user", "UTC"); runCurrent()
        model.setEveningText("Saved this time"); advanceTimeBy(1_100); runCurrent()
        assertNotNull(drafts.current)
        model.save(); runCurrent()
        assertEquals("Saved this time", repository.saves.single().eveningText)
        assertNull(drafts.current)
        assertEquals(DraftStatus.NONE, model.uiState.value.draftStatus)
    }

    @Test fun `a failed save keeps the draft on the device`() = runTest(dispatcher) {
        val repository = Repository().apply { saveResult = { CheckInSaveResult.Failure("Not saved") } }
        val drafts = Drafts()
        val model = model(repository, drafts)
        model.initialize("user", "UTC"); runCurrent()
        model.setEveningText("Keep me"); model.save(); runCurrent()
        assertEquals("Keep me", drafts.current?.eveningText)
        assertEquals("Not saved", model.uiState.value.error)
    }

    @Test fun `a draft from an earlier day is kept aside, not merged into today`() = runTest(dispatcher) {
        val drafts = Drafts().apply {
            current = CheckInDraft("2026-10-09", "old-record", eveningText = "Yesterday's unsaved notes")
        }
        val model = model(Repository(), drafts)
        model.initialize("user", "UTC"); runCurrent()
        val state = model.uiState.value
        assertEquals("2026-10-10", state.draft?.date)
        assertNull(state.draft?.eveningText)
        assertEquals("Yesterday's unsaved notes", state.previousDraft?.eveningText)
        assertNull(drafts.current)
        assertEquals("Yesterday's unsaved notes", drafts.previous?.eveningText)
        model.discardPreviousDraft(); runCurrent()
        assertNull(model.uiState.value.previousDraft)
        assertNull(drafts.previous)
    }

    @Test fun `a restored draft written on an older version warns before saving`() = runTest(dispatcher) {
        val repository = Repository().apply {
            record = DailyCheckIn("record", "user", "2026-10-10", eveningText = "Saved on the web", updatedAt = "2026-10-10T16:00:00Z")
        }
        val drafts = Drafts().apply {
            current = CheckInDraft("2026-10-10", "record", "2026-10-10T09:00:00Z", eveningText = "My phone draft")
        }
        val model = model(repository, drafts)
        model.initialize("user", "UTC"); runCurrent()
        val state = model.uiState.value
        assertEquals("My phone draft", state.draft?.eveningText)
        assertTrue(state.draftBehindSaved)
        assertEquals("record", state.draft?.id)
        assertEquals("2026-10-10T16:00:00Z", state.saved?.updatedAt)
    }

    @Test fun `dictation appends to the evening notes`() = runTest(dispatcher) {
        val model = model(Repository(), Drafts())
        model.initialize("user", "UTC"); runCurrent()
        model.setEveningText("Finished the report.")
        model.appendDictation("felt lighter after that")
        assertEquals("Finished the report. Felt lighter after that", model.uiState.value.draft?.eveningText)
        model.voiceUnavailable()
        assertEquals("Voice input isn't available on this phone.", model.uiState.value.voiceMessage)
    }

    @Test fun `a draft that cannot be stored is reported, not shown as saved`() = runTest(dispatcher) {
        val drafts = Drafts().apply { failWrites = true }
        val model = model(Repository(), drafts)
        model.initialize("user", "UTC"); runCurrent()
        model.setEveningText("Fragile"); advanceTimeBy(1_100); runCurrent()
        assertEquals(DraftStatus.FAILED, model.uiState.value.draftStatus)
    }
}
