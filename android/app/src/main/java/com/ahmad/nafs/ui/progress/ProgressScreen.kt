package com.ahmad.nafs.ui.progress

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.core.records.RecordRules
import com.ahmad.nafs.ui.AccountDayRefreshEffects
import com.ahmad.nafs.ui.components.NafsBanner
import com.ahmad.nafs.ui.components.NafsBannerKind
import com.ahmad.nafs.ui.components.NafsCard
import com.ahmad.nafs.ui.components.NafsChoiceChip
import com.ahmad.nafs.ui.components.NafsDivider
import com.ahmad.nafs.ui.components.NafsEmojiBadge
import com.ahmad.nafs.ui.components.NafsEmptyState
import com.ahmad.nafs.ui.components.NafsEyebrow
import com.ahmad.nafs.ui.components.NafsGroup
import com.ahmad.nafs.ui.components.NafsHeroSurface
import com.ahmad.nafs.ui.components.NafsIconBadge
import com.ahmad.nafs.ui.components.NafsListRow
import com.ahmad.nafs.ui.components.NafsPill
import com.ahmad.nafs.ui.components.NafsPrimaryButton
import com.ahmad.nafs.ui.components.NafsProgressBar
import com.ahmad.nafs.ui.components.NafsProgressRing
import com.ahmad.nafs.ui.components.NafsSectionTitle
import com.ahmad.nafs.ui.components.NafsSegmented
import com.ahmad.nafs.ui.components.NafsSpace
import com.ahmad.nafs.ui.components.NafsTopBar
import com.ahmad.nafs.ui.reports.ReportMarkdown
import com.ahmad.nafs.ui.theme.Nafs
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/** Everything the progress screens can do, so the screen itself stays a pure function of its state. */
internal data class ProgressActions(
    val onBack: () -> Unit = {},
    val onReload: () -> Unit = {},
    val onPeriod: (String) -> Unit = {},
    val onShift: (Int) -> Unit = {},
    val onReview: () -> Unit = {},
    val onExport: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onPrint: () -> Unit = {},
    val onDismissExportMessage: () -> Unit = {},
)

@Composable
fun ProgressRoute(profile: UserProfile, history: Boolean, onBack: () -> Unit, onReview: (String, Int) -> Unit,
    weekStart: String? = null, viewModel: ProgressViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var exportLines by remember { mutableStateOf<List<String>>(emptyList()) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) scope.launch {
            exportMessage = runCatching { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { output -> NativeProgressPdf.write(exportLines, output) } ?: error("Cannot open document") }; "PDF exported." }.getOrElse { "PDF could not be exported." }
        }
    }
    LaunchedEffect(profile.userId, weekStart) {
        viewModel.initialize(profile.userId)
        // Opened from the weekly notification: show the week it reported, even after midnight.
        weekStart?.let { viewModel.openWeek(it, profile.timezone) }
    }
    AccountDayRefreshEffects(profile.timezone, viewModel::reload)
    val response = state.response
    ProgressScreen(
        state = state,
        history = history,
        exportMessage = exportMessage,
        actions = ProgressActions(
            onBack = onBack,
            onReload = viewModel::reload,
            onPeriod = viewModel::period,
            onShift = viewModel::shift,
            onReview = { onReview(state.period, state.offset) },
            onExport = {
                if (response != null) {
                    exportLines = ProgressText.lines(response, history)
                    export.launch("NAFS-${state.period}-${profile.userId.take(8)}.pdf")
                }
            },
            onShare = {
                if (response != null) scope.launch {
                    exportMessage = runCatching { NativeProgressPdf.share(context, ProgressText.lines(response, history)); null }.getOrElse { "PDF could not be shared." }
                }
            },
            onPrint = { if (response != null) NativeProgressPdf.print(context, ProgressText.lines(response, history)) },
            onDismissExportMessage = { exportMessage = null },
        ),
    )
}

/**
 * The 30-day history ([history] = true) and the weekly/monthly reports share one screen: a hero with the
 * honest score and coverage, a day-by-day chart, the areas behind the score, and export at the bottom.
 */
@Composable
internal fun ProgressScreen(
    state: ProgressState,
    history: Boolean,
    actions: ProgressActions,
    exportMessage: String? = null,
    listState: LazyListState = rememberLazyListState(),
) {
    val response = state.response
    var tab by rememberSaveable { mutableStateOf("overall") }
    var fullReport by rememberSaveable { mutableStateOf(false) }
    val timezone = RecordRules.text(response, "timezone")
    val subtitle = when {
        !history -> "Weekly and monthly, ready to print or share"
        timezone.isBlank() -> "Every area, day by day"
        else -> "Every area, day by day · $timezone"
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        NafsTopBar(
            title = if (history) "30-day history" else "Progress reports",
            onBack = actions.onBack,
            subtitle = subtitle,
            actions = { RefreshButton(state.loading, actions.onReload) },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = NafsSpace.screen, end = NafsSpace.screen, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(NafsSpace.item),
        ) {
            if (!history) item(key = "period") {
                PeriodControls(state, response?.get("report") as? JsonObject, actions.onPeriod, actions.onShift)
            }
            if (response == null) {
                item(key = "status") {
                    if (state.loading) LoadingBlocks()
                    else LoadFailed(state.error ?: "Nothing to show yet. Refresh to load your figures.", actions.onReload)
                }
            } else {
                statusItems(state, response, actions.onReload)
                if (history) {
                    historyItems(response, tab) { selected -> tab = selected }
                } else {
                    reportItems(response, state, fullReport, onToggleFullReport = { fullReport = !fullReport }, onReview = actions.onReview)
                }
                item(key = "export") { ExportGroup(actions.onExport, actions.onShare, actions.onPrint) }
                exportMessage?.let { message ->
                    item(key = "export-message") {
                        NafsBanner(
                            message,
                            kind = if (message == "PDF exported.") NafsBannerKind.SUCCESS else NafsBannerKind.ERROR,
                            onDismiss = actions.onDismissExportMessage,
                        )
                    }
                }
            }
        }
    }
}

// ─── Shared status ───────────────────────────────────────────────────────────

private fun LazyListScope.statusItems(state: ProgressState, response: JsonObject, onRetry: () -> Unit) {
    if (state.offline) {
        item(key = "offline") {
            NafsBanner(
                state.error ?: "Showing your last saved figures.",
                kind = NafsBannerKind.OFFLINE,
                title = "Offline saved copy",
                actionLabel = "Retry",
                onAction = onRetry.takeIf { !state.loading },
            )
        }
    } else {
        state.error?.let { error -> item(key = "error") { NafsBanner(error, kind = NafsBannerKind.WARNING) } }
    }
    val unavailable = (response["unavailable"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    if (unavailable.isNotEmpty()) item(key = "partial") {
        NafsBanner(
            "Partial report: ${unavailable.joinToString()} could not be loaded.",
            kind = NafsBannerKind.WARNING,
            title = "Some figures are missing",
        )
    }
}

@Composable
private fun RefreshButton(busy: Boolean, onRefresh: () -> Unit) {
    IconButton(onClick = onRefresh, enabled = !busy) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        else Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LoadingBlocks() {
    val block = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    Column(
        Modifier.fillMaxWidth().semantics { contentDescription = "Loading your figures" },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(176.dp).clip(MaterialTheme.shapes.extraLarge).background(block))
        Box(Modifier.fillMaxWidth().height(168.dp).clip(MaterialTheme.shapes.large).background(block))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(2) { Box(Modifier.weight(1f).height(112.dp).clip(MaterialTheme.shapes.large).background(block)) }
        }
    }
}

@Composable
private fun LoadFailed(message: String, onRetry: () -> Unit) {
    NafsEmptyState(
        icon = Icons.Rounded.CloudOff,
        title = "Your figures didn't load",
        message = message,
        tint = MaterialTheme.colorScheme.error,
        actionLabel = "Try again",
        onAction = onRetry,
    )
}

@Composable
private fun SectionTitle(title: String, supporting: String? = null) {
    NafsSectionTitle(title, Modifier.padding(top = 16.dp), supporting = supporting)
}

@Composable
private fun ExportGroup(onExport: () -> Unit, onShare: () -> Unit, onPrint: () -> Unit) {
    NafsGroup(Modifier.padding(top = 16.dp), title = "Save and share") {
        NafsListRow("Export PDF", subtitle = "Save a copy to your files", icon = Icons.Rounded.PictureAsPdf, onClick = onExport)
        NafsDivider()
        NafsListRow("Share PDF", subtitle = "Send it with another app", icon = Icons.Rounded.Share, onClick = onShare)
        NafsDivider()
        NafsListRow("Print report", subtitle = "Print, or save it from Android's print screen", icon = Icons.Rounded.Print, onClick = onPrint)
    }
}

// ─── Weekly and monthly reports ──────────────────────────────────────────────

private val periodKeys = listOf("weekly", "monthly")

private fun periodNoun(period: String) = if (period == "monthly") "month" else "week"

private fun relativePeriod(period: String, offset: Int): String {
    val noun = periodNoun(period)
    return when (offset) {
        0 -> "This $noun"
        1 -> "Last $noun"
        else -> "$offset ${noun}s ago"
    }
}

@Composable
private fun PeriodControls(state: ProgressState, report: JsonObject?, onPeriod: (String) -> Unit, onShift: (Int) -> Unit) {
    val noun = periodNoun(state.period)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NafsSegmented(
            options = periodKeys.map { ProgressText.label(it) },
            selectedIndex = periodKeys.indexOf(state.period).coerceAtLeast(0),
            onSelect = { index -> onPeriod(periodKeys[index]) },
        )
        NafsCard(contentPadding = PaddingValues(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onShift(1) }, enabled = state.offset < 60) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Earlier $noun")
                }
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(relativePeriod(state.period, state.offset), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(
                        RecordRules.text(report, "label").ifBlank { if (state.loading) "Loading…" else "No report loaded" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { onShift(-1) }, enabled = state.offset > 0) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Later $noun")
                }
            }
        }
    }
}

private fun LazyListScope.reportItems(
    response: JsonObject,
    state: ProgressState,
    fullReport: Boolean,
    onToggleFullReport: () -> Unit,
    onReview: () -> Unit,
) {
    val report = response["report"] as? JsonObject ?: return
    val noun = periodNoun(state.period)
    val today = RecordRules.text(response, "today")
    item(key = "report-hero") { ReportHero(report, today, noun) }
    item(key = "report-days") { ReportDaysCard(report, today, weekly = state.period != "monthly") }
    item(key = "report-areas-title") { SectionTitle("Where it came from", "Each area against what was possible") }
    item(key = "report-areas") { ReportAreaGrid(report) }

    val comparison = report.arr("comparison")
    if (comparison.isNotEmpty()) {
        item(key = "comparison-title") {
            SectionTitle("Compared with the previous $noun", RecordRules.text(report, "previousLabel").ifBlank { null })
        }
        item(key = "comparison") { ComparisonCard(comparison) }
    }

    val focus = report.arr("focus")
    if (focus.isNotEmpty()) {
        item(key = "focus-title") { SectionTitle("Work on this next", "Weakest and fastest-falling areas first") }
        focus.forEachIndexed { index, entry -> item(key = "focus-$index") { FocusCard(index + 1, entry) } }
    }

    listOf(
        Triple("habits", "Habits", report.arr("habits").map(::habitLine)),
        Triple("challenges", "Challenges", report.arr("challenges").map(::challengeLine)),
        Triple("goals", "Goals", report.arr("goals").map(::goalLine)),
    ).forEach { (area, title, lines) ->
        if (lines.isNotEmpty()) {
            item(key = "$area-title") { SectionTitle(title) }
            item(key = "$area-list") { DetailCard(lines, areaStyle(area).tint) }
        }
    }

    val health = report["health"] as? JsonObject
    val healthStats = healthFigures(health)
    if (healthStats.isNotEmpty()) {
        item(key = "health-title") {
            SectionTitle("Health", health.num("days_logged")?.let { "${it.display()} ${dayWord(it.roundToInt())} logged" })
        }
        item(key = "health") { FigureCard(healthStats) }
    }

    val insights = (report["insights"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.filter { it.isNotBlank() }
    if (insights.isNotEmpty()) item(key = "insights") { InsightsCard(insights) }

    val review = response["review"] as? JsonObject
    val reviewText = RecordRules.text(review, "content_md")
    if (reviewText.isNotBlank()) item(key = "review") { CoachReviewCard(reviewText, RecordRules.text(review, "model_used")) }

    item(key = "full-report") {
        val lines = remember(response) { runCatching { ProgressText.lines(response) }.getOrDefault(emptyList()) }
        FullReportCard(lines, fullReport, onToggleFullReport)
    }
    item(key = "review-action") {
        ReviewAction(enabled = !state.offline && !state.loading, offline = state.offline, noun = noun, onReview = onReview)
    }
}

@Composable
private fun ReportHero(report: JsonObject, today: String, noun: String) {
    val c = Nafs.colors
    val logged = report.int("days_logged") ?: 0
    val elapsed = report.int("days_elapsed") ?: 0
    val pending = if (report.arr("days").any { RecordRules.text(it, "date") == today && !it.flag("logged") }) 1 else 0
    val unrecorded = (elapsed - logged - pending).coerceAtLeast(0)
    val average = report.num("avg_score")?.takeIf { logged > 0 }
    val delta = report.num("score_delta")?.takeIf { logged > 0 }
    NafsHeroSurface {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                ScoreRing(average)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    NafsEyebrow(RecordRules.text(report, "shortLabel").ifBlank { RecordRules.text(report, "label") }, color = c.heroGold)
                    Text(reportHeadline(average, noun), style = MaterialTheme.typography.titleLarge, color = c.onHero)
                    delta?.let { HeroDelta(it, noun) }
                }
            }
            HeroRule()
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CoverageStat(logged, "Recorded", c.heroMint, Modifier.weight(1f))
                CoverageStat(pending, "Pending", c.heroGold, Modifier.weight(1f))
                CoverageStat(unrecorded, "Unrecorded", c.onHeroMuted, Modifier.weight(1f))
            }
            Text(
                "$logged of $elapsed ${dayWord(elapsed)} recorded so far. Only recorded days count toward the average.",
                style = MaterialTheme.typography.bodySmall,
                color = c.onHeroMuted,
            )
        }
    }
}

private fun reportHeadline(average: Double?, noun: String): String = when {
    average == null -> "No recorded data for this period."
    average >= 75 -> "A strong $noun. Keep the rhythm."
    average >= 50 -> "A steady $noun, with room to grow."
    else -> "A hard $noun. Start with one area."
}

@Composable
private fun HeroDelta(delta: Double, noun: String) {
    val c = Nafs.colors
    Row(
        Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.10f)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (delta != 0.0) {
            Icon(
                if (delta > 0) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                contentDescription = null,
                tint = if (delta > 0) c.heroMint else c.onHeroMuted,
                modifier = Modifier.size(13.dp),
            )
        }
        Text(
            if (delta == 0.0) "Level with the previous $noun" else "${signed(delta)} pts vs previous $noun",
            style = MaterialTheme.typography.labelMedium,
            color = c.onHero,
            maxLines = 1,
        )
    }
}

@Composable
private fun ReportDaysCard(report: JsonObject, today: String, weekly: Boolean) {
    val days = report.arr("days")
    val total = (report.int("days_in_period") ?: days.size).coerceAtLeast(days.size)
    val bars = days.map { day ->
        when {
            day.flag("logged") -> DayBar(DayKind.MEASURED, day.num("score") ?: 0.0)
            RecordRules.text(day, "date") == today -> DayBar(DayKind.PENDING)
            else -> DayBar(DayKind.UNRECORDED)
        }
    } + List(total - days.size) { DayBar(DayKind.FUTURE) }
    val startText = RecordRules.text(report, "start")
    val start = runCatching { LocalDate.parse(startText) }.getOrNull()
    val labels = if (weekly && start != null) List(bars.size) { index -> start.plusDays(index.toLong()).format(weekdayFormat) } else emptyList()
    NafsCard {
        Text("Day by day", style = MaterialTheme.typography.titleMedium)
        Text(
            "Daily score for each day of this period",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        ScoreBars(bars, "Daily score chart. Each day's figures are listed in the full report below.")
        Spacer(Modifier.height(8.dp))
        if (labels.isNotEmpty()) {
            Row(Modifier.fillMaxWidth()) {
                labels.forEach { label ->
                    Text(
                        label,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        } else {
            AxisLabels(prettyDate(startText), prettyDate(RecordRules.text(report, "end")))
        }
        Spacer(Modifier.height(14.dp))
        ChartLegend(showUnknown = false)
    }
}

private data class AreaFigure(
    val title: String,
    val value: String,
    val caption: String,
    val progress: Float?,
    val icon: ImageVector,
    val tint: Color,
)

@Composable
private fun ReportAreaGrid(report: JsonObject) {
    val c = Nafs.colors
    val tasksDone = report.num("tasks_completed") ?: 0.0
    val tasksTotal = report.num("tasks_total") ?: 0.0
    val habitPct = report.num("habit_completion_pct")
    val figures = buildList {
        add(AreaFigure("Tasks", "${tasksDone.display()}/${tasksTotal.display()}", "tasks completed",
            fraction(tasksDone, tasksTotal), Icons.Rounded.TaskAlt, c.tasks))
        add(AreaFigure("Habits", habitPct?.let { "${it.display()}%" } ?: "—", "habit completion",
            habitPct?.let { (it / 100).toFloat() }, Icons.Rounded.Autorenew, c.habits))
        if (report.flag("deenEnabled")) {
            val points = report.num("prayer_points") ?: 0.0
            val possible = report.num("prayer_points_max") ?: 0.0
            add(AreaFigure("Prayer", "${points.display()}/${possible.display()}", "prayer points",
                fraction(points, possible), Icons.Rounded.NightsStay, c.deen))
            add(AreaFigure("Quran", (report.num("quran_pages") ?: 0.0).display(), "pages read",
                null, Icons.AutoMirrored.Rounded.MenuBook, c.deen))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        figures.chunked(2).forEach { pair ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { figure -> AreaFigureTile(figure, Modifier.weight(1f).fillMaxHeight()) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AreaFigureTile(figure: AreaFigure, modifier: Modifier) {
    NafsCard(modifier.semantics(mergeDescendants = true) {}, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NafsIconBadge(figure.icon, figure.tint, size = 34.dp, iconSize = 18.dp)
            Text(figure.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(12.dp))
        Text(figure.value, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
        Text(figure.caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        figure.progress?.let {
            Spacer(Modifier.height(10.dp))
            NafsProgressBar(it, color = figure.tint, height = 6.dp)
        }
    }
}

@Composable
private fun ComparisonCard(rows: List<JsonObject>) {
    NafsCard(contentPadding = PaddingValues(vertical = 6.dp)) {
        rows.forEachIndexed { index, row ->
            if (index > 0) NafsDivider()
            ComparisonRow(row)
        }
    }
}

@Composable
private fun ComparisonRow(row: JsonObject) {
    val unit = RecordRules.text(row, "unit")
    val current = row.num("current")
    val before = RecordRules.text(row, "previousDetail")
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NafsEmojiBadge(RecordRules.text(row, "emoji"), areaStyle(RecordRules.text(row, "key")).tint, size = 38.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(RecordRules.text(row, "label"), style = MaterialTheme.typography.titleSmall)
            Text(RecordRules.text(row, "detail"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (before.isNotBlank()) {
                Text("Before: $before", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(current?.let { if (unit == "%") "${it.display()}%" else it.display() } ?: "—", style = MaterialTheme.typography.titleMedium)
            DeltaPill(row.num("delta"), unit)
        }
    }
}

@Composable
private fun DeltaPill(delta: Double?, unit: String) {
    if (delta == null) return
    val suffix = if (unit == "pages") " pages" else " pts"
    when {
        delta > 0 -> NafsPill("+${delta.display()}$suffix", color = Nafs.colors.success, icon = Icons.Rounded.ArrowUpward)
        delta < 0 -> NafsPill("−${abs(delta).display()}$suffix", color = MaterialTheme.colorScheme.error, icon = Icons.Rounded.ArrowDownward)
        else -> NafsPill("No change", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FocusCard(rank: Int, entry: JsonObject) {
    val tint = when (RecordRules.text(entry, "severity")) {
        "critical" -> MaterialTheme.colorScheme.error
        "serious" -> Nafs.colors.warning
        else -> Nafs.colors.tasks
    }
    val title = listOf(RecordRules.text(entry, "emoji"), RecordRules.text(entry, "title")).filter { it.isNotBlank() }.joinToString(" ")
    val meta = joinParts(RecordRules.text(entry, "area"), RecordRules.text(entry, "stat"))
    val why = RecordRules.text(entry, "why")
    val action = RecordRules.text(entry, "action")
    val severity = RecordRules.text(entry, "severityLabel")
    NafsCard(contentPadding = PaddingValues(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(tint.copy(alpha = if (Nafs.colors.isDark) 0.18f else 0.12f)),
                contentAlignment = Alignment.Center,
            ) { Text("$rank", style = MaterialTheme.typography.titleSmall, color = tint) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    if (severity.isNotBlank()) NafsPill(severity, color = tint)
                }
                if (meta.isNotBlank()) {
                    Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (why.isNotBlank()) Text(why, style = MaterialTheme.typography.bodyMedium)
                if (action.isNotBlank()) {
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Rounded.Lightbulb, contentDescription = null, tint = Nafs.colors.reflect, modifier = Modifier.size(18.dp))
                            Text(action, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

private data class DetailLine(val emoji: String, val title: String, val value: String, val progress: Float?, val caption: String)

private fun habitLine(row: JsonObject): DetailLine {
    val pct = row.num("pct")
    return DetailLine(
        emoji = RecordRules.text(row, "emoji"),
        title = RecordRules.text(row, "name"),
        value = pct?.let { "${it.display()}%" } ?: "—",
        progress = pct?.let { (it / 100).toFloat() },
        caption = joinParts(
            ratio(row, "done_days", "scheduled_days")?.let { "$it scheduled days" },
            row.num("current_streak")?.takeIf { it > 0 }?.let { "${it.display()}-day streak" },
        ),
    )
}

private fun challengeLine(row: JsonObject): DetailLine {
    val pct = row.num("pct")
    return DetailLine(
        emoji = RecordRules.text(row, "emoji"),
        title = RecordRules.text(row, "title"),
        value = pct?.let { "${it.display()}%" } ?: "—",
        progress = pct?.let { (it / 100).toFloat() },
        caption = joinParts(
            ratio(row, "checkins", "possible_days")?.let { "$it check-ins" },
            ratio(row, "day_of", "duration_days")?.let { "day $it" },
        ),
    )
}

private fun goalLine(row: JsonObject): DetailLine {
    val pct = row.num("progress_pct")
    val milestones = row.num("milestones_total")?.takeIf { it > 0 }
        ?.let { "${RecordRules.text(row, "milestones_done")}/${it.display()} milestones" }
    val left = row.num("days_left")?.let { daysLeft ->
        when {
            daysLeft < 0 -> "${abs(daysLeft).display()} days overdue"
            daysLeft == 0.0 -> "Due today"
            else -> "${daysLeft.display()} days left"
        }
    }
    return DetailLine(
        emoji = RecordRules.text(row, "emoji"),
        title = RecordRules.text(row, "title"),
        value = pct?.let { "${it.display()}%" } ?: "—",
        progress = pct?.let { (it / 100).toFloat() },
        caption = joinParts(milestones, left),
    )
}

@Composable
private fun DetailCard(lines: List<DetailLine>, tint: Color) {
    NafsCard(contentPadding = PaddingValues(vertical = 6.dp)) {
        lines.forEachIndexed { index, line ->
            if (index > 0) NafsDivider()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NafsEmojiBadge(line.emoji, tint, size = 38.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(line.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(line.value, style = MaterialTheme.typography.titleSmall, color = tint)
                    }
                    line.progress?.let { NafsProgressBar(it, color = tint, height = 6.dp) }
                    if (line.caption.isNotBlank()) {
                        Text(line.caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Figures from the report's health summary, as (value, label). Only what was measured is shown. */
private fun healthFigures(health: JsonObject?): List<Pair<String, String>> = buildList {
    health.num("avg_sleep")?.let { add("${it.display()} h" to "Average sleep") }
    health.num("avg_steps")?.let { add("%,d".format(Locale.ENGLISH, it.roundToInt()) to "Average steps") }
    health.num("avg_water")?.let { add(it.display() to "Glasses of water a day") }
    health.num("exercise_days")?.let { add(it.display() to "Days with exercise") }
    health.num("avg_mood")?.let { add("${it.display()}/5" to "Average mood") }
    health.num("weight_change")?.let { add("${signed(it)} kg" to "Weight change") }
}

@Composable
private fun FigureCard(figures: List<Pair<String, String>>) {
    NafsCard {
        figures.chunked(3).forEachIndexed { index, chunk ->
            if (index > 0) Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                chunk.forEach { (value, label) ->
                    Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                repeat(3 - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun InsightsCard(insights: List<String>) {
    val tint = Nafs.colors.coach
    NafsCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NafsIconBadge(Icons.Rounded.AutoAwesome, tint, size = 38.dp, iconSize = 20.dp)
            Text("What stood out", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            insights.forEach { insight ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(tint))
                    Text(insight, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun CoachReviewCard(text: String, model: String) {
    NafsCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NafsIconBadge(Icons.AutoMirrored.Rounded.Chat, Nafs.colors.coach, size = 38.dp, iconSize = 20.dp)
            Column(Modifier.weight(1f)) {
                Text("Coach review", style = MaterialTheme.typography.titleMedium)
                if (model.isNotBlank()) {
                    Text(model, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        ReportMarkdown(text)
    }
}

@Composable
private fun FullReportCard(lines: List<String>, expanded: Boolean, onToggle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    NafsCard(contentPadding = PaddingValues(0.dp)) {
        Surface(onClick = onToggle, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                NafsIconBadge(Icons.Rounded.Description, scheme.onSurfaceVariant, size = 38.dp, iconSize = 20.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Full report", style = MaterialTheme.typography.titleSmall)
                    Text("Every figure, as the PDF lists it", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                Text(if (expanded) "Hide" else "Show", style = MaterialTheme.typography.labelLarge, color = scheme.primary)
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = scheme.primary)
            }
        }
        if (expanded) {
            NafsDivider(inset = 0.dp)
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                lines.forEach { line -> ReportMarkdown(line) }
            }
        }
    }
}

@Composable
private fun ReviewAction(enabled: Boolean, offline: Boolean, noun: String, onReview: () -> Unit) {
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NafsPrimaryButton(
            "Review with Coach",
            onClick = onReview,
            enabled = enabled,
            icon = Icons.AutoMirrored.Rounded.Chat,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            if (offline) "Reconnect to ask Coach about this $noun." else "Coach reads this $noun's figures and suggests what to change first.",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ─── 30-day history ──────────────────────────────────────────────────────────

private fun LazyListScope.historyItems(response: JsonObject, tab: String, onTab: (String) -> Unit) {
    val histories = response["history"] as? JsonObject
    val areas = histories?.filter { (_, list) -> (list as? JsonArray)?.isNotEmpty() == true }?.keys?.toList().orEmpty()
    val rows = histories.arr(tab)
    if (areas.isNotEmpty()) item(key = "area-chips") { AreaChips(areas, tab, onTab) }
    if (rows.isEmpty()) {
        item(key = "history-empty") {
            NafsEmptyState(
                icon = Icons.Rounded.Insights,
                title = "Nothing recorded to chart",
                message = "Record a habit, task or health entry and your last 30 days will appear here, day by day.",
                tint = Nafs.colors.tasks,
            )
        }
        return
    }
    item(key = "history-hero") { HistoryHero(tab, rows) }
    item(key = "history-chart") { HistoryChartCard(rows) }
    item(key = "history-note") {
        Text(
            "Scores follow the existing web calculation. Only recorded days contribute to the total. Pending, unrecorded, and unavailable days are shown separately.",
            modifier = Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (areas.size > 1) {
        item(key = "areas-title") { SectionTitle("By area", "Recorded days only. Tap an area to chart it.") }
        item(key = "areas-grid") { HistoryAreaGrid(histories, areas, tab, onTab) }
    }
    item(key = "days-title") { SectionTitle("Day by day", "Newest first") }
    item(key = "days") { HistoryDayList(rows.asReversed()) }
}

@Composable
private fun AreaChips(areas: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        areas.forEach { area ->
            NafsChoiceChip(ProgressText.label(area), selected = area == selected, onClick = { onSelect(area) })
        }
    }
}

@Composable
private fun HistoryHero(tab: String, rows: List<JsonObject>) {
    val c = Nafs.colors
    val summary = coverageOf(rows)
    NafsHeroSurface {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                ScoreRing(summary.average?.toDouble())
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    NafsEyebrow("${ProgressText.label(tab)} · last ${rows.size} days", color = c.heroGold)
                    Text(
                        if (summary.measured == 0) "No measured days in this window yet."
                        else "${summary.measured} measured ${dayWord(summary.measured)}, best ${summary.best ?: 0}%",
                        style = MaterialTheme.typography.titleLarge,
                        color = c.onHero,
                    )
                    Text("Average of recorded days only", style = MaterialTheme.typography.labelMedium, color = c.onHeroMuted)
                }
            }
            HeroRule()
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CoverageStat(summary.recorded, "Recorded", c.heroMint, Modifier.weight(1f))
                CoverageStat(summary.pending, "Pending", c.heroGold, Modifier.weight(1f))
                CoverageStat(summary.unrecorded, "Unrecorded", c.onHeroMuted, Modifier.weight(1f))
            }
            if (summary.unknown > 0) {
                Text(
                    "${summary.unknown} ${dayWord(summary.unknown)} could not be read, so they are left out of the average.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.onHeroMuted,
                )
            }
        }
    }
}

@Composable
private fun HistoryChartCard(rows: List<JsonObject>) {
    val bars = rows.map(::historyBar)
    NafsCard {
        Text("Last ${rows.size} days", style = MaterialTheme.typography.titleMedium)
        Text("One bar per day, oldest on the left", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        ScoreBars(bars, "History chart. Daily values are listed below.")
        Spacer(Modifier.height(8.dp))
        AxisLabels(prettyDate(RecordRules.text(rows.firstOrNull(), "date")), prettyDate(RecordRules.text(rows.lastOrNull(), "date")))
        Spacer(Modifier.height(14.dp))
        ChartLegend(showUnknown = bars.any { it.kind == DayKind.UNKNOWN })
    }
}

@Composable
private fun HistoryAreaGrid(histories: JsonObject?, areas: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        areas.chunked(2).forEach { pair ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { area ->
                    HistoryAreaTile(area, histories.arr(area), area == selected, { onSelect(area) }, Modifier.weight(1f).fillMaxHeight())
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HistoryAreaTile(area: String, rows: List<JsonObject>, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val style = areaStyle(area)
    val summary = coverageOf(rows)
    val label = ProgressText.label(area)
    NafsCard(
        modifier,
        onClick = onClick,
        borderColor = if (isSelected) style.tint.copy(alpha = 0.7f) else Nafs.colors.cardBorder,
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NafsIconBadge(style.icon, style.tint, size = 34.dp, iconSize = 18.dp)
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(summary.average?.let { "$it%" } ?: "—", style = MaterialTheme.typography.headlineSmall)
            Text("average", Modifier.padding(bottom = 4.dp), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        ScoreBars(rows.map(::historyBar), "$label: ${summary.recorded} of ${rows.size} days recorded", height = 30.dp, guides = false)
        Spacer(Modifier.height(8.dp))
        Text("${summary.recorded} of ${rows.size} days recorded", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HistoryDayList(rows: List<JsonObject>) {
    NafsCard(contentPadding = PaddingValues(vertical = 4.dp)) {
        rows.forEachIndexed { index, row ->
            if (index > 0) NafsDivider()
            HistoryDayRow(row)
        }
    }
}

@Composable
private fun HistoryDayRow(row: JsonObject) {
    val coverage = RecordRules.text(row, "coverage")
    val max = row.num("max") ?: 0.0
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(prettyDate(RecordRules.text(row, "date"), dayFormat), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        when {
            coverage != "recorded" -> NafsPill(ProgressText.label(coverage), color = coverageTint(coverage))
            max == 0.0 -> Text("No measured opportunity", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> {
                Text("${RecordRules.text(row, "earned")}/${RecordRules.text(row, "max")}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${RecordRules.text(row, "pct")}%",
                    Modifier.width(52.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = scoreTint(row.num("pct") ?: 0.0),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

private data class CoverageSummary(
    val recorded: Int,
    val pending: Int,
    val unrecorded: Int,
    val unknown: Int,
    val measured: Int,
    val average: Int?,
    val best: Int?,
)

/** Mirrors the web History page: the average covers recorded days with something to measure, nothing else. */
private fun coverageOf(rows: List<JsonObject>): CoverageSummary {
    val kinds = rows.map { RecordRules.text(it, "coverage") }
    val measured = rows.filter { RecordRules.text(it, "coverage") == "recorded" && (it.num("max") ?: 0.0) > 0.0 }
        .map { it.num("pct") ?: 0.0 }
    return CoverageSummary(
        recorded = kinds.count { it == "recorded" },
        pending = kinds.count { it == "pending" },
        unrecorded = kinds.count { it == "unrecorded" },
        unknown = kinds.count { it !in setOf("recorded", "pending", "unrecorded") },
        measured = measured.size,
        average = if (measured.isEmpty()) null else (measured.sum() / measured.size).roundToInt(),
        best = measured.maxOrNull()?.roundToInt(),
    )
}

private fun historyBar(row: JsonObject): DayBar = when (RecordRules.text(row, "coverage")) {
    "recorded" -> if ((row.num("max") ?: 0.0) > 0.0) DayBar(DayKind.MEASURED, row.num("pct") ?: 0.0) else DayBar(DayKind.NO_OPPORTUNITY)
    "pending" -> DayBar(DayKind.PENDING)
    "unrecorded" -> DayBar(DayKind.UNRECORDED)
    else -> DayBar(DayKind.UNKNOWN)
}

@Composable
private fun coverageTint(coverage: String): Color = when (coverage) {
    "pending" -> MaterialTheme.colorScheme.primary
    "unrecorded" -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> Nafs.colors.warning
}

// ─── Hero pieces ─────────────────────────────────────────────────────────────

@Composable
private fun ScoreRing(score: Double?) {
    val c = Nafs.colors
    NafsProgressRing(
        progress = ((score ?: 0.0) / 100.0).toFloat(),
        size = 108.dp,
        stroke = 10.dp,
        colors = listOf(c.heroGold, c.heroMint),
        trackColor = Color.White.copy(alpha = 0.12f),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(score?.let { "${it.display()}%" } ?: "—", style = MaterialTheme.typography.headlineMedium, color = c.onHero)
            Text("average", style = MaterialTheme.typography.labelSmall, color = c.onHeroMuted)
        }
    }
}

@Composable
private fun HeroRule() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.10f)))
}

@Composable
private fun CoverageStat(value: Int, label: String, accent: Color, modifier: Modifier = Modifier) {
    val c = Nafs.colors
    Column(modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$value", style = MaterialTheme.typography.titleLarge, color = c.onHero, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
            Text(label, style = MaterialTheme.typography.labelMedium, color = c.onHeroMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ─── Chart ───────────────────────────────────────────────────────────────────

private enum class DayKind { MEASURED, NO_OPPORTUNITY, PENDING, UNRECORDED, UNKNOWN, FUTURE }

private data class DayBar(val kind: DayKind, val pct: Double = 0.0)

/** Rounded day bars coloured by score; days without a measured score sit as short quiet stubs. */
@Composable
private fun ScoreBars(bars: List<DayBar>, description: String, height: Dp = 128.dp, guides: Boolean = true) {
    val c = Nafs.colors
    val scheme = MaterialTheme.colorScheme
    val good = c.success
    val fair = c.gold
    val low = c.challenges
    val quiet = scheme.onSurface.copy(alpha = 0.12f)
    val waiting = scheme.primary.copy(alpha = 0.55f)
    val missing = c.warning.copy(alpha = 0.55f)
    val upcoming = scheme.onSurface.copy(alpha = 0.05f)
    val guide = scheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
        if (bars.isEmpty()) return@Canvas
        val slot = this.size.width / bars.size
        val barWidth = minOf(slot * 0.62f, 26.dp.toPx())
        val stub = 4.dp.toPx()
        if (guides) {
            val line = 1.dp.toPx()
            val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
            listOf(0.5f, 1f).forEach { level ->
                val y = maxOf(line / 2, this.size.height * (1f - level))
                drawLine(guide, Offset(0f, y), Offset(this.size.width, y), strokeWidth = line, pathEffect = dash)
            }
        }
        bars.forEachIndexed { index, bar ->
            val fill = when (bar.kind) {
                DayKind.MEASURED -> when {
                    bar.pct >= 75 -> good
                    bar.pct >= 50 -> fair
                    bar.pct > 0 -> low
                    else -> quiet
                }
                DayKind.NO_OPPORTUNITY, DayKind.UNRECORDED -> quiet
                DayKind.PENDING -> waiting
                DayKind.UNKNOWN -> missing
                DayKind.FUTURE -> upcoming
            }
            val barHeight = if (bar.kind == DayKind.MEASURED) {
                maxOf(stub, this.size.height * (bar.pct.coerceIn(0.0, 100.0) / 100.0).toFloat())
            } else stub
            val left = slot * index + (slot - barWidth) / 2f
            val radius = minOf(barWidth / 2f, barHeight / 2f)
            drawRoundRect(
                color = fill,
                topLeft = Offset(left, this.size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(radius, radius),
            )
        }
    }
}

@Composable
private fun AxisLabels(start: String, end: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(start, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(end, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChartLegend(showUnknown: Boolean) {
    val c = Nafs.colors
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LegendDot(c.success, "75%+")
        LegendDot(c.gold, "50–74%")
        LegendDot(c.challenges, "Under 50%")
        LegendDot(scheme.primary.copy(alpha = 0.55f), "Pending")
        LegendDot(scheme.onSurface.copy(alpha = 0.18f), "Not recorded")
        if (showUnknown) LegendDot(c.warning.copy(alpha = 0.55f), "Unavailable")
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun scoreTint(pct: Double): Color = when {
    pct >= 75 -> Nafs.colors.success
    pct >= 50 -> Nafs.colors.gold
    else -> Nafs.colors.challenges
}

// ─── Areas ───────────────────────────────────────────────────────────────────

private data class AreaStyle(val icon: ImageVector, val tint: Color)

@Composable
private fun areaStyle(area: String): AreaStyle {
    val c = Nafs.colors
    return when (area) {
        "habits" -> AreaStyle(Icons.Rounded.Autorenew, c.habits)
        "deen", "salah", "prayer" -> AreaStyle(Icons.Rounded.NightsStay, c.deen)
        "quran" -> AreaStyle(Icons.AutoMirrored.Rounded.MenuBook, c.deen)
        "tasks" -> AreaStyle(Icons.Rounded.TaskAlt, c.tasks)
        "health" -> AreaStyle(Icons.Rounded.Favorite, c.health)
        "challenges" -> AreaStyle(Icons.Rounded.Whatshot, c.challenges)
        "goals" -> AreaStyle(Icons.Rounded.Flag, c.goals)
        else -> AreaStyle(Icons.Rounded.Insights, c.gold)
    }
}

// ─── Small helpers ───────────────────────────────────────────────────────────

private val shortDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val dayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
private val weekdayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)

private fun prettyDate(value: String, format: DateTimeFormatter = shortDateFormat): String =
    runCatching { LocalDate.parse(value).format(format) }.getOrDefault(value)

private fun JsonObject?.arr(key: String): List<JsonObject> =
    (this?.get(key) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

private fun JsonObject?.num(key: String): Double? = RecordRules.text(this, key).toDoubleOrNull()?.takeIf { it.isFinite() }

private fun JsonObject?.int(key: String): Int? = num(key)?.roundToInt()

private fun JsonObject?.flag(key: String): Boolean = (this?.get(key) as? JsonPrimitive)?.booleanOrNull == true

private fun Double.display(): String = if (this % 1.0 == 0.0) toLong().toString() else "%.1f".format(Locale.ENGLISH, this)

private fun signed(value: Double): String = if (value >= 0) "+${value.display()}" else "−${abs(value).display()}"

private fun fraction(done: Double, total: Double): Float = if (total <= 0.0) 0f else (done / total).toFloat().coerceIn(0f, 1f)

private fun ratio(row: JsonObject, done: String, total: String): String? {
    val first = RecordRules.text(row, done)
    val second = RecordRules.text(row, total)
    return if (first.isBlank() || second.isBlank()) null else "$first/$second"
}

private fun joinParts(vararg parts: String?): String = parts.filterNotNull().filter { it.isNotBlank() }.joinToString(" · ")

private fun dayWord(count: Int) = if (count == 1) "day" else "days"
