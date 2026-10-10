package com.ahmad.nafs.core.reports

import com.ahmad.nafs.core.time.ServerTimestamp
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class ReportFilter(val type: String?, val label: String) {
    ALL(null, "All"), PULL("pull", "Daily verdicts"), TRIBUNAL("tribunal", "Weekly tribunals"),
    GAP("gap", "Gap analyses"), LETTER("letter_reply", "Letter replies"), GROWTH("growth", "Growth reviews");
    val cacheKey: String get() = type ?: "all"
}

@Serializable
data class ReportSummary(
    val id: String,
    @SerialName("user_id") val userId: String,
    val type: String,
    @SerialName("week_start") val weekStart: String? = null,
    @SerialName("generated_at") val generatedAt: String,
    @SerialName("model_used") val modelUsed: String,
) {
    val title: String get() = when (type) {
        "pull" -> "Daily verdict"
        "tribunal" -> "Weekly tribunal"
        "gap" -> "Gap analysis"
        "letter_reply" -> "Letter reply"
        "growth" -> "Growth review"
        else -> type.replace('_', ' ').replaceFirstChar(Char::uppercase)
    }
    fun writtenAt(timezone: String): String = ServerTimestamp.parse(generatedAt)
        .atZone(runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.of("UTC")))
        .format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.getDefault()))
    fun weekLabel(): String? = weekStart?.let {
        "Week of ${LocalDate.parse(it).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))}"
    }
}

@Serializable
data class AiReport(
    val id: String,
    @SerialName("user_id") val userId: String,
    val type: String,
    @SerialName("week_start") val weekStart: String? = null,
    @SerialName("content_md") val contentMd: String,
    @SerialName("generated_at") val generatedAt: String,
    @SerialName("model_used") val modelUsed: String,
) {
    val summary: ReportSummary get() = ReportSummary(id, userId, type, weekStart, generatedAt, modelUsed)
}

@Serializable
data class ReportCursor(val generatedAt: String, val id: String)

@Serializable
data class ReportPage(val reports: List<ReportSummary>, val next: ReportCursor? = null)

@Serializable
data class ReportSnapshot(
    val pages: Map<String, ReportPage> = emptyMap(),
    val details: List<AiReport> = emptyList(),
)

sealed interface ReportLoadResult {
    data class Loaded(val page: ReportPage, val isOffline: Boolean = false) : ReportLoadResult
    data class Failure(val message: String) : ReportLoadResult
}

sealed interface ReportDetailResult {
    data class Loaded(val report: AiReport, val isOffline: Boolean = false) : ReportDetailResult
    data object NotFound : ReportDetailResult
    data class Failure(val message: String) : ReportDetailResult
}

object ReportRules {
    const val PAGE_SIZE = 20
    const val MAX_CACHED_DETAILS = 20
    private val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    val newestFirst: Comparator<ReportSummary> = compareByDescending<ReportSummary> { ServerTimestamp.parse(it.generatedAt) }
        .thenByDescending { it.id.lowercase(Locale.ROOT) }

    fun validId(id: String): Boolean = uuid.matches(id)
    fun validCursor(cursor: ReportCursor): Boolean = validId(cursor.id) && runCatching { ServerTimestamp.parse(cursor.generatedAt) }.isSuccess
    fun validSummary(userId: String, report: ReportSummary): Boolean = report.userId == userId &&
        validId(report.id) && report.type.isNotBlank() && report.type.length <= 80 && report.modelUsed.length <= 200 &&
        runCatching { ServerTimestamp.parse(report.generatedAt) }.isSuccess &&
        (report.weekStart == null || runCatching { LocalDate.parse(report.weekStart) }.isSuccess)

    fun validReport(userId: String, report: AiReport): Boolean = validSummary(userId, report.summary) && report.contentMd.length <= 180_000

    fun isAfterCursor(report: ReportSummary, cursor: ReportCursor): Boolean {
        val time = ServerTimestamp.parse(report.generatedAt).compareTo(ServerTimestamp.parse(cursor.generatedAt))
        return time < 0 || (time == 0 && report.id.lowercase(Locale.ROOT) < cursor.id.lowercase(Locale.ROOT))
    }

    fun page(userId: String, filter: ReportFilter, cursor: ReportCursor?, rows: List<ReportSummary>): ReportPage {
        require(cursor == null || validCursor(cursor))
        require(rows.size <= PAGE_SIZE + 1 && rows.map { it.id }.distinct().size == rows.size)
        require(rows.all { validSummary(userId, it) && (filter.type == null || it.type == filter.type) &&
            (cursor == null || isAfterCursor(it, cursor)) })
        val reports = rows.sortedWith(newestFirst).take(PAGE_SIZE)
        return ReportPage(reports, reports.lastOrNull()?.takeIf { rows.size > PAGE_SIZE }?.let { ReportCursor(it.generatedAt, it.id) })
    }

    fun validSnapshot(userId: String, snapshot: ReportSnapshot): Boolean =
        snapshot.pages.all { (key, page) ->
            val filter = ReportFilter.entries.find { it.cacheKey == key }
            filter != null && page.reports.size <= PAGE_SIZE &&
                runCatching { page(userId, filter, null, page.reports) }.isSuccess &&
                (page.next == null || (validCursor(page.next) && page.reports.lastOrNull()?.let {
                    ReportCursor(it.generatedAt, it.id) == page.next
                } == true))
        } && snapshot.details.size <= MAX_CACHED_DETAILS && snapshot.details.all { validReport(userId, it) }
}
