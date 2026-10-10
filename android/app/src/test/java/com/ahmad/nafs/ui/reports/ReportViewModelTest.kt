package com.ahmad.nafs.ui.reports

import com.ahmad.nafs.core.reports.ReportCursor
import com.ahmad.nafs.core.reports.ReportDetailResult
import com.ahmad.nafs.core.reports.ReportFilter
import com.ahmad.nafs.core.reports.ReportLoadResult
import com.ahmad.nafs.core.reports.ReportPage
import com.ahmad.nafs.core.reports.ReportRepository
import com.ahmad.nafs.core.reports.savedReport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
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
class ReportViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private class Repository : ReportRepository {
        val requests = mutableListOf<Triple<String, ReportFilter, ReportCursor?>>()
        var detailCalls = 0
        var loadAction: suspend (String, ReportFilter, ReportCursor?) -> ReportLoadResult = { _, _, _ -> ReportLoadResult.Loaded(ReportPage(emptyList())) }
        var detailAction: suspend (String, String) -> ReportDetailResult = { _, _ -> ReportDetailResult.Loaded(savedReport()) }
        override suspend fun load(userId: String, filter: ReportFilter, cursor: ReportCursor?): ReportLoadResult {
            requests += Triple(userId, filter, cursor)
            return loadAction(userId, filter, cursor)
        }
        override suspend fun detail(userId: String, reportId: String): ReportDetailResult { detailCalls++; return detailAction(userId, reportId) }
        override suspend fun clear(userId: String) = Unit
    }
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun `late response from a previous filter cannot replace the selected archive`() = runTest(dispatcher) {
        val repository = Repository()
        val delayed = CompletableDeferred<ReportLoadResult>()
        repository.loadAction = { _, filter, _ ->
            if (filter == ReportFilter.ALL) withContext(NonCancellable) { delayed.await() }
            else ReportLoadResult.Loaded(ReportPage(listOf(savedReport(type = "pull").summary)))
        }
        val model = ReportListViewModel(repository)
        model.initialize("user"); runCurrent()
        model.selectFilter(ReportFilter.PULL); runCurrent()
        delayed.complete(ReportLoadResult.Loaded(ReportPage(listOf(savedReport().summary)))); runCurrent()
        assertEquals(ReportFilter.PULL, model.uiState.value.filter)
        assertEquals(listOf("pull"), model.uiState.value.reports.map { it.type })
        assertFalse(model.uiState.value.isLoading)
    }

    @Test fun `paging failure retains the archive and retries the exact cursor`() = runTest(dispatcher) {
        val repository = Repository()
        val report = savedReport(2).summary
        val cursor = ReportCursor(report.generatedAt, report.id)
        repository.loadAction = { _, _, next -> if (next == null) ReportLoadResult.Loaded(ReportPage(listOf(report), cursor)) else ReportLoadResult.Failure("offline") }
        val model = ReportListViewModel(repository)
        model.initialize("user"); runCurrent(); model.loadMore(); runCurrent()
        assertEquals(listOf(report), model.uiState.value.reports)
        assertEquals(cursor, model.uiState.value.next)
        assertEquals("offline", model.uiState.value.pagingError)
        repository.loadAction = { _, _, _ -> ReportLoadResult.Loaded(ReportPage(listOf(savedReport().summary))) }
        model.loadMore(); runCurrent()
        assertEquals(listOf(report, savedReport().summary), model.uiState.value.reports)
        assertEquals(cursor, repository.requests.last().third)
        assertNull(model.uiState.value.pagingError)
        assertNull(model.uiState.value.next)
    }

    @Test fun `offline archive disables paging and successful refresh restores online history`() = runTest(dispatcher) {
        val repository = Repository()
        val report = savedReport().summary
        repository.loadAction = { _, _, _ -> ReportLoadResult.Loaded(ReportPage(listOf(report), ReportCursor(report.generatedAt, report.id)), true) }
        val model = ReportListViewModel(repository)
        model.initialize("user"); runCurrent(); model.loadMore(); runCurrent()
        assertEquals(1, repository.requests.size)
        assertTrue(model.uiState.value.isOffline)
        assertNull(model.uiState.value.next)
        repository.loadAction = { _, _, _ -> ReportLoadResult.Loaded(ReportPage(emptyList())) }
        model.refresh(); runCurrent()
        assertFalse(model.uiState.value.isOffline)
        assertTrue(model.uiState.value.reports.isEmpty())
    }

    @Test fun `duplicate taps cannot overlap a page request`() = runTest(dispatcher) {
        val repository = Repository()
        val delayed = CompletableDeferred<ReportLoadResult>()
        repository.loadAction = { _, _, _ -> delayed.await() }
        val model = ReportListViewModel(repository)
        model.initialize("user"); runCurrent(); model.refresh(); model.loadMore(); runCurrent()
        assertEquals(1, repository.requests.size)
        delayed.complete(ReportLoadResult.Loaded(ReportPage(emptyList()))); runCurrent()
        assertFalse(model.uiState.value.isLoading)
    }

    @Test fun `failed refresh retains visible reports but prevents stale paging`() = runTest(dispatcher) {
        val repository = Repository()
        val report = savedReport().summary
        repository.loadAction = { _, _, _ -> ReportLoadResult.Loaded(ReportPage(listOf(report), ReportCursor(report.generatedAt, report.id))) }
        val model = ReportListViewModel(repository)
        model.initialize("user"); runCurrent()
        repository.loadAction = { _, _, _ -> ReportLoadResult.Failure("refresh failed") }
        model.refresh(); runCurrent()
        assertEquals(listOf(report), model.uiState.value.reports)
        assertTrue(model.uiState.value.isOffline)
        assertNull(model.uiState.value.next)
        assertEquals("refresh failed", model.uiState.value.error)
    }

    @Test fun `account change clears old visible reports before the replacement request`() = runTest(dispatcher) {
        val repository = Repository()
        repository.loadAction = { _, _, _ -> ReportLoadResult.Loaded(ReportPage(listOf(savedReport().summary))) }
        val model = ReportListViewModel(repository)
        model.initialize("user"); runCurrent()
        val delayed = CompletableDeferred<ReportLoadResult>()
        repository.loadAction = { _, _, _ -> delayed.await() }
        model.initialize("other"); runCurrent()
        assertTrue(model.uiState.value.reports.isEmpty())
        assertTrue(model.uiState.value.isLoading)
        delayed.complete(ReportLoadResult.Loaded(ReportPage(emptyList()))); runCurrent()
        assertEquals("other", repository.requests.last().first)
    }

    @Test fun `missing detail clears a previously displayed report`() = runTest(dispatcher) {
        val repository = Repository()
        val model = ReportDetailViewModel(repository)
        model.initialize("user", savedReport().id); runCurrent()
        repository.detailAction = { _, _ -> ReportDetailResult.NotFound }
        model.refresh(); runCurrent()
        assertTrue(model.uiState.value.notFound)
        assertNull(model.uiState.value.report)
        assertFalse(model.uiState.value.isLoading)
    }

    @Test fun `failed detail refresh retains the saved content and discloses its offline state`() = runTest(dispatcher) {
        val repository = Repository()
        val model = ReportDetailViewModel(repository)
        model.initialize("user", savedReport().id); runCurrent()
        repository.detailAction = { _, _ -> ReportDetailResult.Failure("offline") }
        model.refresh(); runCurrent()
        assertEquals(savedReport(), model.uiState.value.report)
        assertTrue(model.uiState.value.isOffline)
        assertEquals("offline", model.uiState.value.error)
    }

    @Test fun `late detail cannot appear after opening another report or account`() = runTest(dispatcher) {
        val repository = Repository()
        val delayed = CompletableDeferred<ReportDetailResult>()
        repository.detailAction = { user, _ -> if (user == "user") withContext(NonCancellable) { delayed.await() }
            else ReportDetailResult.Loaded(savedReport(2, user = user)) }
        val model = ReportDetailViewModel(repository)
        model.initialize("user", savedReport().id); runCurrent()
        model.initialize("other", savedReport(2).id); runCurrent()
        delayed.complete(ReportDetailResult.Loaded(savedReport())); runCurrent()
        assertEquals("other", model.uiState.value.report!!.userId)
        assertEquals(savedReport(2).id, model.uiState.value.report!!.id)
        assertFalse(model.uiState.value.isLoading)
    }
}
