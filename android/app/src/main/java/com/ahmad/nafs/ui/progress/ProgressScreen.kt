package com.ahmad.nafs.ui.progress

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.core.records.RecordRules
import com.ahmad.nafs.ui.AccountDayRefreshEffects
import com.ahmad.nafs.ui.reports.ReportMarkdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressRoute(profile: UserProfile, history: Boolean, onBack: () -> Unit, onReview: (String, Int) -> Unit, viewModel: ProgressViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var exportLines by remember { mutableStateOf<List<String>>(emptyList()) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) scope.launch {
            exportMessage = runCatching { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { output -> NativeProgressPdf.write(exportLines, output) } ?: error("Cannot open document") }; "PDF exported." }.getOrElse { "PDF could not be exported." }
        }
    }
    LaunchedEffect(profile.userId) { viewModel.initialize(profile.userId) }
    AccountDayRefreshEffects(profile.timezone, viewModel::reload)
    var tab by rememberSaveable { mutableStateOf("overall") }
    val response = state.response
    Scaffold(topBar = { TopAppBar(title = { Text(if (history) "30-day history" else "Progress reports") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }, actions = { TextButton(onClick = viewModel::reload, enabled = !state.loading) { Text("Refresh") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            if (state.offline && response != null) item { Text("Offline saved copy") }
            if (!history) item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("weekly", "monthly").forEach { period -> FilterChip(selected = state.period == period, onClick = { viewModel.period(period) }, label = { Text(ProgressText.label(period)) }) } }
                Row { TextButton(onClick = { viewModel.shift(1) }, enabled = state.offset < 60) { Text("Earlier") }; TextButton(onClick = { viewModel.shift(-1) }, enabled = state.offset > 0) { Text("Later") } }
            }
            if (response != null) {
                val unavailable = response["unavailable"] as? JsonArray
                if (!unavailable.isNullOrEmpty()) item { Text("Partial report: ${unavailable.joinToString { (it as JsonPrimitive).content }} could not be loaded.") }
                if (history) {
                    val histories = response["history"] as JsonObject
                    item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        histories.filter { (_, rows) -> (rows as JsonArray).isNotEmpty() }.keys.forEach { key -> FilterChip(selected = tab == key, onClick = { tab = key }, label = { Text(ProgressText.label(key)) }) }
                    } }
                    val rows = (histories[tab] as? JsonArray).orEmpty().map { it as JsonObject }
                    item { HistoryBars(rows) }
                    item { Text("Scores follow the existing web calculation. Only recorded days contribute to the total. Pending, unrecorded, and unavailable days are shown separately.", style = MaterialTheme.typography.bodySmall) }
                    items(rows) { row ->
                        val max = RecordRules.text(row, "max").toDoubleOrNull() ?: 0.0
                        Text("${RecordRules.text(row, "date")} · ${if (RecordRules.text(row, "coverage") != "recorded") ProgressText.label(RecordRules.text(row, "coverage")) else if (max == 0.0) "No measured opportunity" else "${RecordRules.text(row, "pct")}% · ${RecordRules.text(row, "earned")}/${RecordRules.text(row, "max")}"}")
                    }
                } else {
                    items(ProgressText.lines(response)) { line -> ReportMarkdown(line) }
                    item { OutlinedButton(onClick = { onReview(state.period, state.offset) }, enabled = !state.offline && !state.loading) { Text("Request Coach review") } }
                }
                item {
                    Button(onClick = { exportLines = ProgressText.lines(response, history); export.launch("NAFS-${state.period}-${profile.userId.take(8)}.pdf") }) { Text("Export PDF") }
                    OutlinedButton(onClick = { scope.launch { exportMessage = runCatching { NativeProgressPdf.share(context, ProgressText.lines(response, history)); null }.getOrElse { "PDF could not be shared." } } }) { Text("Share PDF") }
                    OutlinedButton(onClick = { NativeProgressPdf.print(context, ProgressText.lines(response, history)) }) { Text("Print report") }
                    exportMessage?.let { Text(it) }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun HistoryBars(rows: List<JsonObject>) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = "History chart. Daily values are listed below." }) {
        if (rows.isEmpty()) return@Canvas
        val width = size.width / rows.size
        rows.forEachIndexed { i, row ->
            val max = RecordRules.text(row, "max").toDoubleOrNull() ?: 0.0
            if (max > 0 && RecordRules.text(row, "coverage") == "recorded") { val pct = RecordRules.text(row, "pct").toFloatOrNull()?.coerceIn(0f, 100f) ?: 0f
                drawLine(color, Offset(width * (i + .5f), size.height), Offset(width * (i + .5f), size.height * (1 - pct / 100)), strokeWidth = width * .6f)
            }
        }
    }
}
