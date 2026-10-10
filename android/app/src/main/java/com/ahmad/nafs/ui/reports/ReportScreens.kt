package com.ahmad.nafs.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.core.reports.ReportFilter
import com.ahmad.nafs.ui.components.NafsHeroCard

@Composable
fun ReportListRoute(
    profile: UserProfile,
    onBack: () -> Unit,
    onOpenReport: (String) -> Unit,
    viewModel: ReportListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(profile.userId) { viewModel.initialize(profile.userId) }
    val listState = rememberLazyListState()
    LaunchedEffect(state.filter) { listState.scrollToItem(0) }
    Scaffold(topBar = { ReportToolbar("Coach reports", onBack, viewModel::refresh, !state.isLoading && !state.isLoadingMore) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ReportFilter.entries, key = { it.cacheKey }) { filter ->
                    FilterChip(selected = state.filter == filter, onClick = { viewModel.selectFilter(filter) }, label = { Text(filter.label) })
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(), state = listState,
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.isOffline) item {
                    Text("Showing saved reports. Details are available offline for reports you’ve opened.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
                state.error?.let { error -> item { ReportError(error, viewModel::refresh, !state.isLoading) } }
                if (state.isLoading) item { ReportLoading() }
                if (state.reports.isEmpty() && !state.isLoading && state.error == null) item {
                    NafsHeroCard(
                        eyebrow = state.filter.label,
                        title = if (state.isOffline) "No reports saved here" else "No reports yet",
                        body = if (state.isOffline) "Connect and reload to see your report archive."
                            else "Saved ${if (state.filter == ReportFilter.ALL) "coach reports" else state.filter.label.lowercase()} will appear here.",
                    )
                }
                items(state.reports, key = { it.id }) { report ->
                    Card(onClick = { onOpenReport(report.id) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(report.title, style = MaterialTheme.typography.titleMedium)
                                Text(report.writtenAt(profile.timezone), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                report.weekLabel()?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
                        }
                    }
                }
                state.pagingError?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) } }
                if (state.isLoadingMore) item { ReportLoading() }
                else if (state.next != null && !state.isOffline && !state.isLoading) item {
                    TextButton(onClick = viewModel::loadMore, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.pagingError != null) "Retry older reports" else "Load older reports")
                    }
                }
            }
        }
    }
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
    val report = state.report
    val lines = remember(report?.contentMd) { report?.let { ReportText.lines(it.contentMd) }.orEmpty() }
    Scaffold(topBar = { ReportToolbar("Coach report", onBack, viewModel::refresh, !state.isLoading) }) { padding ->
        SelectionContainer {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.isOffline) item { Text("Showing your saved copy.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
                state.error?.let { error -> item { ReportError(error, viewModel::refresh, !state.isLoading) } }
                if (state.isLoading) item { ReportLoading() }
                if (state.notFound) item {
                    NafsHeroCard("Report unavailable", "This report is no longer available", "Return to the archive and reload your reports.")
                }
                if (report != null) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(report.summary.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                            Text(report.summary.writtenAt(profile.timezone), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            report.summary.weekLabel()?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                            if (report.modelUsed.isNotBlank()) Text("Written by ${report.modelUsed}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    if (report.contentMd.isBlank()) item { Text("This saved report has no written content.") }
                    else itemsIndexed(lines) { _, line -> ReportLine(line) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportToolbar(title: String, onBack: () -> Unit, onReload: () -> Unit, enabled: Boolean) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
        actions = { IconButton(onClick = onReload, enabled = enabled) { Icon(Icons.Rounded.Refresh, "Reload reports") } },
    )
}

@Composable
private fun ReportError(message: String, onRetry: () -> Unit, enabled: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRetry, enabled = enabled) { Text("Retry") }
        }
    }
}

@Composable
private fun ReportLoading() {
    Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
internal fun ReportLine(line: ReportTextLine) {
    if (line.kind == ReportTextKind.SPACE) {
        Spacer(Modifier.height(4.dp))
        return
    }
    val text = remember(line) {
        buildAnnotatedString {
            if (line.kind == ReportTextKind.CODE) append(line.text)
            else ReportText.runs(line.text).forEach { run ->
                if (run.bold) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(run.text) }
                else append(run.text)
            }
        }
    }
    val style = when (line.kind) {
        ReportTextKind.HEADING -> if (line.headingLevel <= 2) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium
        ReportTextKind.CODE -> MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
        else -> MaterialTheme.typography.bodyLarge
    }
    Row(Modifier.fillMaxWidth().padding(top = if (line.kind == ReportTextKind.HEADING) 8.dp else 0.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        line.marker?.let { Text(it, style = style, color = MaterialTheme.colorScheme.primary) }
        if (line.kind == ReportTextKind.QUOTE) Text("│", color = MaterialTheme.colorScheme.primary)
        Text(text, style = style, modifier = Modifier.weight(1f).then(
            if (line.kind == ReportTextKind.HEADING) Modifier.semantics { heading() } else Modifier,
        ))
    }
}
