package com.ahmad.nafs.core.reports

import com.ahmad.nafs.core.network.ApiSessionTokens
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class CachedReportRepository @Inject constructor(
    private val remote: ReportRemote,
    private val cache: ReportCache,
    private val sessions: ApiSessionTokens,
) : ReportRepository {
    private val mutex = Mutex()

    override suspend fun load(userId: String, filter: ReportFilter, cursor: ReportCursor?): ReportLoadResult = try {
        checkAccount(userId)
        require(cursor == null || ReportRules.validCursor(cursor))
        val page = ReportRules.page(userId, filter, cursor, remote.list(userId, filter, cursor))
        checkAccount(userId)
        if (cursor == null) updateCache(userId) { it.copy(pages = it.pages + (filter.cacheKey to page)) }
        checkAccount(userId)
        ReportLoadResult.Loaded(page)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        if (!sessions.isCurrentUser(userId)) ReportLoadResult.Failure("Sign in again to load your reports.")
        else {
            val saved = if (cursor == null) readCache(userId)?.pages?.get(filter.cacheKey) else null
            if (!sessions.isCurrentUser(userId)) ReportLoadResult.Failure("Sign in again to load your reports.")
            else if (saved != null) ReportLoadResult.Loaded(saved.copy(next = null), isOffline = true)
            else ReportLoadResult.Failure("Unable to load reports. Check your connection and retry.")
        }
    }

    override suspend fun detail(userId: String, reportId: String): ReportDetailResult = try {
        checkAccount(userId)
        require(ReportRules.validId(reportId))
        val report = remote.detail(userId, reportId)
        checkAccount(userId)
        if (report == null) {
            updateCache(userId) { snapshot ->
                snapshot.copy(details = snapshot.details.filterNot { it.id == reportId }, pages = snapshot.pages.mapValues { (_, page) ->
                    val remaining = page.reports.filterNot { it.id == reportId }
                    // An offline archive cannot page, and removal may invalidate its boundary.
                    page.copy(reports = remaining, next = null)
                })
            }
            checkAccount(userId)
            ReportDetailResult.NotFound
        } else {
            require(report.id == reportId && ReportRules.validReport(userId, report))
            updateCache(userId) { snapshot -> snapshot.copy(details =
                (listOf(report) + snapshot.details.filterNot { it.id == reportId }).take(ReportRules.MAX_CACHED_DETAILS)) }
            checkAccount(userId)
            ReportDetailResult.Loaded(report)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        if (!sessions.isCurrentUser(userId)) ReportDetailResult.Failure("Sign in again to load this report.")
        else {
            val saved = readCache(userId)?.details?.find { it.id == reportId }
            if (!sessions.isCurrentUser(userId)) ReportDetailResult.Failure("Sign in again to load this report.")
            else if (saved != null) ReportDetailResult.Loaded(saved, isOffline = true)
            else ReportDetailResult.Failure("Unable to load this report. Connect and retry; details are saved offline after you open them.")
        }
    }

    override suspend fun clear(userId: String) = mutex.withLock { cache.clear(userId) }

    private suspend fun readCache(userId: String): ReportSnapshot? = mutex.withLock { readUnlocked(userId) }

    private suspend fun readUnlocked(userId: String): ReportSnapshot? = try {
        checkAccount(userId)
        cache.read(userId)?.also { checkAccount(userId); require(ReportRules.validSnapshot(userId, it)) }
    } catch (cancellation: CancellationException) { throw cancellation } catch (_: Exception) { null }

    private suspend fun updateCache(userId: String, update: (ReportSnapshot) -> ReportSnapshot) {
        try {
            mutex.withLock {
                val snapshot = update(readUnlocked(userId) ?: ReportSnapshot())
                checkAccount(userId)
                cache.write(userId, snapshot)
            }
        } catch (cancellation: CancellationException) { throw cancellation } catch (_: Exception) { /* Online reads remain usable. */ }
    }

    private fun checkAccount(userId: String) = check(sessions.isCurrentUser(userId))
}
