package com.ahmad.nafs.core.reports

interface ReportRepository {
    suspend fun load(userId: String, filter: ReportFilter, cursor: ReportCursor? = null): ReportLoadResult
    suspend fun detail(userId: String, reportId: String): ReportDetailResult
    suspend fun clear(userId: String)
}

interface ReportRemote {
    suspend fun list(userId: String, filter: ReportFilter, cursor: ReportCursor?): List<ReportSummary>
    suspend fun detail(userId: String, reportId: String): AiReport?
}

interface ReportCache {
    suspend fun read(userId: String): ReportSnapshot?
    suspend fun write(userId: String, snapshot: ReportSnapshot)
    suspend fun clear(userId: String)
}
