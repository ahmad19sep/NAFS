package com.ahmad.nafs.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.core.reports.AiReport
import com.ahmad.nafs.core.reports.ReportFilter
import com.ahmad.nafs.core.reports.ReportSummary
import com.ahmad.nafs.core.time.ServerTimestamp
import com.ahmad.nafs.ui.components.NafsBanner
import com.ahmad.nafs.ui.components.NafsBannerKind
import com.ahmad.nafs.ui.components.NafsCard
import com.ahmad.nafs.ui.components.NafsChoiceChip
import com.ahmad.nafs.ui.components.NafsEmptyState
import com.ahmad.nafs.ui.components.NafsEyebrow
import com.ahmad.nafs.ui.components.NafsIconBadge
import com.ahmad.nafs.ui.components.NafsPill
import com.ahmad.nafs.ui.components.NafsSecondaryButton
import com.ahmad.nafs.ui.components.NafsSpace
import com.ahmad.nafs.ui.components.NafsTopBar
import com.ahmad.nafs.ui.theme.Nafs
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ReportListRoute(
    profile: UserProfile,
    onBack: () -> Unit,
    onOpenReport: (String) -> Unit,
    viewModel: ReportListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(profile.userId) { viewModel.initialize(profile.userId) }
    ReportListScreen(
        state = state,
        timezone = profile.timezone,
        onBack = onBack,
        onOpenReport = onOpenReport,
        onRefresh = viewModel::refresh,
        onSelectFilter = viewModel::selectFilter,
        onLoadMore = viewModel::loadMore,
    )
}

@Composable
fun ReportDetailRoute(
    profile: UserProfile,
    reportId: String,
    onBack: () -> Unit,
    viewModel: ReportDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(profile.userId, reportId) { viewModel.initialize(profile.userId, reportId) }
    ReportDetailScreen(state = state, timezone = profile.timezone, onBack = onBack, onRefresh = viewModel::refresh)
}

// ─── Archive ─────────────────────────────────────────────────────────────────

@Composable
internal fun ReportListScreen(
    state: ReportListUiState,
    timezone: String,
    onBack: () -> Unit,
    onOpenReport: (String) -> Unit,
    onRefresh: () -> Unit,
    onSelectFilter: (ReportFilter) -> Unit,
    onLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.filter) { listState.scrollToItem(0) }
    val zone = remember(timezone) { runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.of("UTC")) }
    val months = remember(state.reports, zone) { state.reports.groupBy { monthLabel(it.generatedAt, zone) } }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val scheme = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        NafsTopBar(
            title = "Coach reports",
            onBack = onBack,
            subtitle = "Verdicts, reviews and replies you've saved",
            actions = {
                IconButton(onClick = onRefresh, enabled = !state.isLoading && !state.isLoadingMore) {
                    Icon(Icons.Rounded.Refresh, "Reload reports")
                }
            },
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = NafsSpace.screen),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(ReportFilter.entries, key = { it.cacheKey }) { filter ->
                NafsChoiceChip(filter.label, selected = state.filter == filter, onClick = { onSelectFilter(filter) })
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(start = NafsSpace.screen, end = NafsSpace.screen, top = 16.dp, bottom = 28.dp + bottomInset),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.isOffline) item(key = "offline") {
                NafsBanner("Showing saved reports. Details are available offline for reports you’ve opened.", kind = NafsBannerKind.OFFLINE)
            }
            state.error?.let { error ->
                item(key = "error") {
                    NafsBanner(
                        error,
                        kind = NafsBannerKind.ERROR,
                        actionLabel = "Retry".takeIf { !state.isLoading },
                        onAction = onRefresh.takeIf { !state.isLoading },
                    )
                }
            }
            if (state.isLoading) item(key = "loading") { ReportListSkeleton() }
            if (state.reports.isEmpty() && !state.isLoading && state.error == null) item(key = "empty") {
                NafsEmptyState(
                    icon = Icons.Rounded.Inventory2,
                    title = if (state.isOffline) "No reports saved here" else "No reports yet",
                    message = if (state.isOffline) "Connect and reload to see your report archive."
                        else "Saved ${if (state.filter == ReportFilter.ALL) "coach reports" else state.filter.label.lowercase()} will appear here.",
                    tint = Nafs.colors.coach,
                )
            }
            months.forEach { (month, reports) ->
                item(key = "month-$month") {
                    NafsEyebrow(month, Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp))
                }
                items(reports, key = { it.id }) { report ->
                    ReportRow(report, timezone, onClick = { onOpenReport(report.id) })
                }
            }
            state.pagingError?.let { error -> item(key = "paging-error") { NafsBanner(error, kind = NafsBannerKind.ERROR) } }
            if (state.isLoadingMore) item(key = "loading-more") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = scheme.primary)
                }
            } else if (state.next != null && !state.isOffline && !state.isLoading) item(key = "more") {
                NafsSecondaryButton(
                    if (state.pagingError != null) "Retry older reports" else "Load older reports",
                    onClick = onLoadMore,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
        }
    }
}

private fun monthLabel(generatedAt: String, zone: ZoneId): String = runCatching {
    ServerTimestamp.parse(generatedAt).atZone(zone).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
}.getOrDefault("Earlier")

/** Icon, colour and a one-line description for each kind of saved report. */
private data class ReportStyle(val icon: ImageVector, val tint: Color, val blurb: String)

@Composable
private fun reportStyle(type: String): ReportStyle {
    val c = Nafs.colors
    return when (type) {
        "pull" -> ReportStyle(Icons.Rounded.WbTwilight, c.reflect, "Your verdict on how the day really went.")
        "tribunal" -> ReportStyle(Icons.Rounded.Gavel, c.challenges, "A weekly hearing on promises kept and missed.")
        "gap" -> ReportStyle(Icons.Rounded.Insights, c.tasks, "Where your plans and your days drifted apart.")
        "letter_reply" -> ReportStyle(Icons.Rounded.MailOutline, c.coach, "The coach's reply to a future-self letter.")
        "growth" -> ReportStyle(Icons.AutoMirrored.Rounded.TrendingUp, c.habits, "How you have grown across your areas.")
        else -> ReportStyle(Icons.Rounded.Description, MaterialTheme.colorScheme.onSurfaceVariant, "A saved coach report.")
    }
}

@Composable
private fun ReportRow(report: ReportSummary, timezone: String, onClick: () -> Unit) {
    val style = reportStyle(report.type)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    NafsCard(onClick = onClick, contentPadding = PaddingValues(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            NafsIconBadge(style.icon, style.tint)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(report.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(report.weekLabel() ?: style.blurb, style = MaterialTheme.typography.bodySmall, color = muted,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(
                    Modifier.padding(top = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Schedule, contentDescription = null, tint = muted, modifier = Modifier.size(13.dp))
                    Text(report.writtenAt(timezone), style = MaterialTheme.typography.labelMedium, color = muted, maxLines = 1)
                }
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = muted)
        }
    }
}

@Composable
private fun ReportListSkeleton() {
    val block = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.width(110.dp).height(12.dp).clip(CircleShape).background(block))
        repeat(4) { Box(Modifier.fillMaxWidth().height(88.dp).clip(MaterialTheme.shapes.large).background(block)) }
    }
}

// ─── Reader ──────────────────────────────────────────────────────────────────

@Composable
internal fun ReportDetailScreen(
    state: ReportDetailUiState,
    timezone: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    val report = state.report
    val lines = remember(report?.contentMd) { report?.let { ReportText.lines(it.contentMd) }.orEmpty() }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        NafsTopBar(
            title = "Coach report",
            onBack = onBack,
            actions = {
                IconButton(onClick = onRefresh, enabled = !state.isLoading) { Icon(Icons.Rounded.Refresh, "Reload reports") }
            },
        )
        SelectionContainer(Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 40.dp + bottomInset),
            ) {
                if (state.isOffline) item { NafsBanner("Showing your saved copy.", Modifier.padding(bottom = 12.dp), kind = NafsBannerKind.OFFLINE) }
                state.error?.let { error ->
                    item {
                        NafsBanner(
                            error,
                            Modifier.padding(bottom = 12.dp),
                            kind = NafsBannerKind.ERROR,
                            actionLabel = "Retry".takeIf { !state.isLoading },
                            onAction = onRefresh.takeIf { !state.isLoading },
                        )
                    }
                }
                if (state.isLoading) item {
                    if (report == null) ReportDetailSkeleton()
                    else LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 12.dp))
                }
                if (state.notFound) item {
                    NafsEmptyState(
                        icon = Icons.Rounded.Inventory2,
                        title = "This report is no longer available",
                        message = "Return to the archive and reload your reports.",
                        tint = Nafs.colors.coach,
                        actionLabel = "Back to reports",
                        onAction = onBack,
                    )
                }
                if (report != null) {
                    item { ReportHeader(report, timezone) }
                    if (report.contentMd.isBlank()) item {
                        Text("This saved report has no written content.", style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else itemsIndexed(lines) { _, line -> ReportLine(line) }
                }
            }
        }
    }
}

@Composable
private fun ReportHeader(report: AiReport, timezone: String) {
    val summary = report.summary
    val style = reportStyle(summary.type)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NafsIconBadge(style.icon, style.tint, size = 52.dp, iconSize = 26.dp)
        Text(summary.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        PillFlow {
            NafsPill(summary.writtenAt(timezone), color = style.tint, icon = Icons.Rounded.Schedule)
            summary.weekLabel()?.let { NafsPill(it, color = muted, icon = Icons.Rounded.CalendarMonth) }
        }
        if (report.modelUsed.isNotBlank()) {
            Text("Written by ${report.modelUsed}", style = MaterialTheme.typography.bodySmall, color = muted)
        }
        HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    }
}

@Composable
private fun ReportDetailSkeleton() {
    val block = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(52.dp).clip(MaterialTheme.shapes.medium).background(block))
        Box(Modifier.width(220.dp).height(28.dp).clip(CircleShape).background(block))
        Box(Modifier.width(160.dp).height(22.dp).clip(CircleShape).background(block))
        Spacer(Modifier.height(8.dp))
        repeat(6) { Box(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape).background(block)) }
    }
}

/** Wraps small labels onto as many lines as they need. */
@Composable
private fun PillFlow(modifier: Modifier = Modifier, spacing: Dp = 8.dp, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        val positions = ArrayList<IntOffset>(placeables.size)
        var x = 0
        var y = 0
        var rowHeight = 0
        var widest = 0
        placeables.forEach { placeable ->
            if (x > 0 && x + placeable.width > constraints.maxWidth) {
                x = 0
                y += rowHeight + gap
                rowHeight = 0
            }
            positions.add(IntOffset(x, y))
            widest = maxOf(widest, x + placeable.width)
            x += placeable.width + gap
            rowHeight = maxOf(rowHeight, placeable.height)
        }
        val width = widest.coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (y + rowHeight).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            placeables.forEachIndexed { index, placeable -> placeable.placeRelative(positions[index].x, positions[index].y) }
        }
    }
}

/**
 * One line of saved coach Markdown. Shared by the report reader and every screen that shows saved
 * coach text, so it carries its own spacing: comfortable reading measure, generous line height.
 */
@Composable
internal fun ReportLine(line: ReportTextLine) {
    if (line.kind == ReportTextKind.SPACE) {
        Spacer(Modifier.height(8.dp))
        return
    }
    val scheme = MaterialTheme.colorScheme
    val text = remember(line) {
        buildAnnotatedString {
            if (line.kind == ReportTextKind.CODE) append(line.text)
            else ReportText.runs(line.text).forEach { run ->
                if (run.bold) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(run.text) }
                else append(run.text)
            }
        }
    }
    val body = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp)
    when (line.kind) {
        ReportTextKind.HEADING -> Text(
            text,
            style = if (line.headingLevel <= 2) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth()
                .padding(top = if (line.headingLevel <= 2) 16.dp else 12.dp, bottom = 4.dp)
                .semantics { heading() },
        )
        ReportTextKind.BULLET -> Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(line.marker.orEmpty(), style = body.copy(fontWeight = FontWeight.SemiBold), color = scheme.primary,
                modifier = Modifier.widthIn(min = 14.dp))
            Text(text, style = body, modifier = Modifier.weight(1f))
        }
        ReportTextKind.QUOTE -> Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.width(3.dp).fillMaxHeight().clip(CircleShape).background(scheme.primary.copy(alpha = 0.55f)))
            Text(text, style = body.copy(fontStyle = FontStyle.Italic), color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        }
        ReportTextKind.CODE -> Text(
            text,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth().background(scheme.surfaceContainerHigh).padding(horizontal = 12.dp, vertical = 2.dp),
        )
        else -> Text(text, style = body, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp))
    }
}
