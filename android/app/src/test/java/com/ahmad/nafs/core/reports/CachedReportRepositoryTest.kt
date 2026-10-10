package com.ahmad.nafs.core.reports

import com.ahmad.nafs.core.network.ApiSessionTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CachedReportRepositoryTest {
    private class Sessions : ApiSessionTokens {
        var current = "user"
        override fun isCurrentUser(userId: String) = current == userId
        override suspend fun accessToken(userId: String, forceRefresh: Boolean) = "token"
    }
    private class Cache : ReportCache {
        var snapshot: ReportSnapshot? = null
        var failReads = false
        var failWrites = false
        val cleared = mutableListOf<String>()
        override suspend fun read(userId: String): ReportSnapshot? { check(!failReads); return snapshot }
        override suspend fun write(userId: String, snapshot: ReportSnapshot) { check(!failWrites); this.snapshot = snapshot }
        override suspend fun clear(userId: String) { cleared += userId; snapshot = null }
    }
    private class Remote : ReportRemote {
        var calls = 0
        var rows = listOf(savedReport().summary)
        var report: AiReport? = savedReport()
        var listAction: suspend () -> List<ReportSummary> = { rows }
        var detailAction: suspend () -> AiReport? = { report }
        override suspend fun list(userId: String, filter: ReportFilter, cursor: ReportCursor?): List<ReportSummary> { calls++; return listAction() }
        override suspend fun detail(userId: String, reportId: String): AiReport? { calls++; return detailAction() }
    }
    private val cache = Cache()
    private val remote = Remote()
    private val sessions = Sessions()
    private val repository = CachedReportRepository(remote, cache, sessions)

    @Test fun `offline archives use their own filter and cannot page into unknown history`() = runTest {
        val page = ReportPage(listOf(savedReport().summary), ReportCursor(savedReport().generatedAt, savedReport().id))
        cache.snapshot = ReportSnapshot(pages = mapOf("growth" to page))
        remote.listAction = { error("offline") }
        val result = repository.load("user", ReportFilter.GROWTH) as ReportLoadResult.Loaded
        assertTrue(result.isOffline)
        assertEquals(null, result.page.next)
        assertTrue(repository.load("user", ReportFilter.PULL) is ReportLoadResult.Failure)
        assertTrue(repository.load("user", ReportFilter.GROWTH, page.next) is ReportLoadResult.Failure)
    }

    @Test fun `online empty archive replaces a previously cached nonempty page`() = runTest {
        cache.snapshot = ReportSnapshot(pages = mapOf("all" to ReportPage(listOf(savedReport().summary))))
        remote.rows = emptyList()
        val online = repository.load("user", ReportFilter.ALL) as ReportLoadResult.Loaded
        assertFalse(online.isOffline)
        assertTrue(online.page.reports.isEmpty())
        remote.listAction = { error("offline") }
        assertTrue((repository.load("user", ReportFilter.ALL) as ReportLoadResult.Loaded).page.reports.isEmpty())
    }

    @Test fun `later pages do not replace the cached recent archive`() = runTest {
        repository.load("user", ReportFilter.ALL)
        val initial = cache.snapshot
        remote.rows = listOf(savedReport().summary.copy(generatedAt = "2026-10-09T00:00:00Z"))
        assertTrue(repository.load("user", ReportFilter.ALL, ReportCursor(savedReport().generatedAt, savedReport().id)) is ReportLoadResult.Loaded)
        assertEquals(initial, cache.snapshot)
    }

    @Test fun `opened report survives network failure with truthful offline state`() = runTest {
        assertFalse((repository.detail("user", savedReport().id) as ReportDetailResult.Loaded).isOffline)
        remote.detailAction = { error("offline") }
        val result = repository.detail("user", savedReport().id) as ReportDetailResult.Loaded
        assertTrue(result.isOffline)
        assertEquals(savedReport(), result.report)
        assertTrue(repository.detail("user", savedReport(2).id) is ReportDetailResult.Failure)
    }

    @Test fun `authoritative missing report removes its offline copy and archive entry`() = runTest {
        repository.load("user", ReportFilter.ALL)
        repository.detail("user", savedReport().id)
        remote.report = null
        assertEquals(ReportDetailResult.NotFound, repository.detail("user", savedReport().id))
        assertTrue(cache.snapshot!!.details.isEmpty())
        assertTrue(cache.snapshot!!.pages["all"]!!.reports.isEmpty())
        remote.detailAction = { error("offline") }
        assertTrue(repository.detail("user", savedReport().id) is ReportDetailResult.Failure)
    }

    @Test fun `account change during a remote request discards the response and cached fallback`() = runTest {
        cache.snapshot = ReportSnapshot(details = listOf(savedReport()))
        remote.detailAction = { sessions.current = "other"; savedReport() }
        assertTrue(repository.detail("user", savedReport().id) is ReportDetailResult.Failure)
        assertEquals(listOf(savedReport()), cache.snapshot!!.details)
        assertTrue(repository.load("user", ReportFilter.ALL) is ReportLoadResult.Failure)
        assertEquals(1, remote.calls)
    }

    @Test fun `wrong owner and wrong report identity never appear in native detail`() = runTest {
        remote.report = savedReport(user = "other")
        assertTrue(repository.detail("user", savedReport().id) is ReportDetailResult.Failure)
        remote.report = savedReport(2)
        assertTrue(repository.detail("user", savedReport().id) is ReportDetailResult.Failure)
        remote.rows = listOf(savedReport(user = "other").summary)
        assertTrue(repository.load("user", ReportFilter.ALL) is ReportLoadResult.Failure)
        assertEquals(null, cache.snapshot)
    }

    @Test fun `corrupt cache can be rebuilt online and local write failures do not hide saved reports`() = runTest {
        cache.failReads = true
        assertTrue(repository.load("user", ReportFilter.ALL) is ReportLoadResult.Loaded)
        assertEquals(listOf(savedReport().summary), cache.snapshot!!.pages["all"]!!.reports)
        cache.failWrites = true
        assertTrue(repository.detail("user", savedReport().id) is ReportDetailResult.Loaded)
        cache.failReads = false
        cache.snapshot = ReportSnapshot(details = listOf(savedReport(user = "other")))
        remote.detailAction = { error("offline") }
        assertTrue(repository.detail("user", savedReport().id) is ReportDetailResult.Failure)
    }

    @Test fun `offline detail cache is bounded by the most recently opened reports`() = runTest {
        for (index in 1..25) {
            remote.report = savedReport(index)
            repository.detail("user", savedReport(index).id)
        }
        assertEquals((25 downTo 6).map { savedReport(it).id }, cache.snapshot!!.details.map { it.id })
        repository.clear("user")
        assertEquals(listOf("user"), cache.cleared)
        assertEquals(null, cache.snapshot)
    }

    @Test fun `cancellation propagates instead of silently presenting an offline result`() = runTest {
        remote.listAction = { throw CancellationException("cancelled") }
        assertTrue(runCatching { repository.load("user", ReportFilter.ALL) }.exceptionOrNull() is CancellationException)
        remote.detailAction = { throw CancellationException("cancelled") }
        assertTrue(runCatching { repository.detail("user", savedReport().id) }.exceptionOrNull() is CancellationException)
    }
}
