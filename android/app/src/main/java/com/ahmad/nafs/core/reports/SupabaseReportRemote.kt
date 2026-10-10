package com.ahmad.nafs.core.reports

import com.ahmad.nafs.core.network.SupabaseClientProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject

class SupabaseReportRemote @Inject constructor(private val provider: SupabaseClientProvider) : ReportRemote {
    override suspend fun list(userId: String, filter: ReportFilter, cursor: ReportCursor?): List<ReportSummary> =
        ReportQueries.list(checkNotNull(provider.client), userId, filter, cursor)

    override suspend fun detail(userId: String, reportId: String): AiReport? =
        ReportQueries.detail(checkNotNull(provider.client), userId, reportId)
}

internal object ReportQueries {
    private val summaryColumns = Columns.list("id", "user_id", "type", "week_start", "generated_at", "model_used")
    private val detailColumns = Columns.list("id", "user_id", "type", "week_start", "generated_at", "model_used", "content_md")

    suspend fun list(client: SupabaseClient, userId: String, selected: ReportFilter, cursor: ReportCursor?): List<ReportSummary> =
        client.from("ai_reports").select(summaryColumns) {
            filter {
                eq("user_id", userId)
                selected.type?.let { eq("type", it) }
                cursor?.let {
                    or {
                        lt("generated_at", it.generatedAt)
                        and { eq("generated_at", it.generatedAt); lt("id", it.id) }
                    }
                }
            }
            order("generated_at", Order.DESCENDING)
            order("id", Order.DESCENDING)
            limit((ReportRules.PAGE_SIZE + 1).toLong())
        }.decodeList<ReportSummary>()

    suspend fun detail(client: SupabaseClient, userId: String, reportId: String): AiReport? {
        val rows = client.from("ai_reports").select(detailColumns) {
            filter { eq("user_id", userId); eq("id", reportId) }
            limit(1)
        }.decodeList<AiReport>()
        require(rows.size <= 1)
        return rows.singleOrNull()
    }
}
