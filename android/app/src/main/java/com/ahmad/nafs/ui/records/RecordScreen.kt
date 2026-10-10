package com.ahmad.nafs.ui.records

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.core.records.*
import com.ahmad.nafs.ui.AccountDayRefreshEffects
import com.ahmad.nafs.ui.components.PublicPhoto
import kotlinx.serialization.json.*
import com.ahmad.nafs.ui.reports.ReportMarkdown

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordRoute(profile: UserProfile, feature: RecordFeature, onBack: () -> Unit, viewModel: RecordViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(profile.userId, profile.timezone, feature) { viewModel.initialize(profile.userId, profile.timezone, feature) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flushDraft() }
    AccountDayRefreshEffects(profile.timezone, viewModel::reload)
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(viewModel::addPhoto) }
    Scaffold(topBar = { TopAppBar(title = { Text(feature.title) }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
        actions = { TextButton(onClick = viewModel::reload, enabled = !state.busy) { Text("Refresh") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.loading || state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (state.offline) item { Text("Offline · showing saved records and your draft.") }
            state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            state.message?.let { item { ReportMarkdown(it) } }
            if (state.editing) {
                if (feature.daily) item { Text("Entry date: ${state.date} · ${profile.timezone}") }
                if (state.pending) item {
                    Text("This save is awaiting confirmation. Retry preserves the original request.")
                    TextButton(onClick = viewModel::discardPending, enabled = !state.offline && !state.busy) { Text("Keep draft and edit after reload") }
                }
                items(feature.fields, key = { it.key }) { field ->
                    OutlinedTextField(value = state.values[field.key].orEmpty(), onValueChange = { viewModel.change(field.key, it) },
                        label = { Text(field.label) }, modifier = Modifier.fillMaxWidth(), enabled = !state.busy && !state.pending,
                        keyboardOptions = KeyboardOptions(keyboardType = if (field.kind == FieldKind.NUMBER) KeyboardType.Decimal else KeyboardType.Text),
                        minLines = if (field.key in setOf("reflection_text", "notes", "detail", "apps")) 3 else 1)
                }
                if (feature in setOf(RecordFeature.LOG, RecordFeature.SCREEN)) item {
                    OutlinedButton(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !state.busy && !state.pending) { Text("Attach photo") }
                    val photoValue = state.values[if (feature == RecordFeature.LOG) "photo_urls" else "screenshot_url"].orEmpty()
                    if (photoValue.isNotBlank() && photoValue != "[]" && photoValue != "null") Text("Photo attached. Uploaded photos use the existing public storage bucket.", style = MaterialTheme.typography.bodySmall)
                }
                item {
                    Button(onClick = viewModel::save, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(if (state.pending) "Retry exact save" else "Save entry") }
                    TextButton(onClick = viewModel::closeEditor, enabled = !state.busy && !state.pending) { Text("Discard draft") }
                }
            } else {
                item { Button(onClick = { viewModel.edit() }, enabled = !state.busy && !state.loading) { Text(if (feature == RecordFeature.NOTE) "New note" else if (feature == RecordFeature.DREAM) "Edit dream" else "Log today") } }
                if (feature == RecordFeature.SCREEN && state.rows.isNotEmpty()) item { OutlinedButton(onClick = viewModel::analyzeScreen, enabled = !state.busy && !state.offline) { Text("Analyze today's saved screen time") } }
                if (!state.loading && state.rows.isEmpty()) item { Text("No saved entries yet. Add your first entry above.") }
                items(state.rows, key = { RecordRules.text(it, "id") }) { row ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(RecordRules.text(row, if (feature.daily) "date" else if (feature == RecordFeature.DREAM) "statement" else "title"), style = MaterialTheme.typography.titleMedium)
                            feature.fields.forEach { field -> val value = RecordRules.text(row, field.key); if (value.isNotBlank()) Text("${field.label}: $value") }
                            if (feature == RecordFeature.LOG) Text("Weighted hours: ${RecordRules.text(row, "weighted_hours_today")} · Pull days: ${RecordRules.text(row, "todays_pull_days")}")
                            (row["photo_urls"] as? JsonArray)?.forEach { value -> PublicPhoto((value as? JsonPrimitive)?.content, "Log photo", Modifier.fillMaxWidth().height(200.dp)) }
                            if (feature == RecordFeature.SCREEN) {
                                PublicPhoto(RecordRules.text(row, "screenshot_url"), "Screen-time screenshot", Modifier.fillMaxWidth().height(220.dp))
                                (row["apps"] as? JsonArray)?.forEach { item -> (item as? JsonObject)?.let { Text("${RecordRules.text(it, "app")}: ${RecordRules.text(it, "minutes")} minutes") } }
                                RecordRules.text(row, "ai_summary").takeIf { it.isNotBlank() }?.let { ReportMarkdown(it) }
                            }
                            TextButton(onClick = { viewModel.edit(row) }, enabled = !state.busy) { Text("Open / edit") }
                        }
                    }
                }
            }
            if (state.hasMore && !state.editing) item { OutlinedButton(viewModel::loadMore, enabled = !state.busy) { Text("Load earlier records") } }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}
