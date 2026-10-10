package com.ahmad.nafs.ui.checkin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmad.nafs.core.checkin.CheckInRules
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.ui.AccountDayRefreshEffects
import com.ahmad.nafs.ui.components.NafsHeroCard
import com.ahmad.nafs.ui.components.NafsSectionTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInRoute(profile: UserProfile, viewModel: CheckInViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(profile.userId, profile.timezone) { viewModel.initialize(profile.userId, profile.timezone) }
    AccountDayRefreshEffects(profile.timezone) { viewModel.refresh() }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Daily check-in") }, actions = {
            TextButton(
                onClick = { viewModel.refresh(force = true) },
                enabled = !state.isLoading && !state.isSaving && !state.isGenerating && (!state.hasChanges || state.hasConflict),
            ) { Text("Reload") }
        })
    }) { padding ->
        if (state.isLoading && state.draft == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (state.isOffline) item {
                    Text("Showing your cached check-in. Reconnect and reload to make changes.", color = MaterialTheme.colorScheme.secondary)
                }
                state.error?.let { message -> item {
                    Text(message, color = MaterialTheme.colorScheme.error)
                    if (state.draft == null) TextButton(onClick = { viewModel.refresh(force = true) }) { Text("Retry") }
                } }
                state.message?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.primary) } }
                if (state.hasConflict) item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("This check-in changed on another device.", style = MaterialTheme.typography.titleMedium)
                            Text("Your entries are still shown. Reloading replaces them with the latest saved record.")
                            Button(onClick = { viewModel.refresh(force = true) }, enabled = !state.isLoading) { Text("Reload latest record") }
                        }
                    }
                }
                state.draft?.let { record ->
                    item {
                        NafsHeroCard(
                            eyebrow = "${record.date} · ${profile.timezone}",
                            title = if (state.isEvening) "Reflect on your day" else "Give your day direction",
                            body = "${record.tasks.count { it.done }} of ${record.tasks.size} priorities completed",
                        )
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = !state.isEvening, onClick = { viewModel.showEvening(false) }, label = { Text("Morning plan") })
                            FilterChip(selected = state.isEvening, onClick = { viewModel.showEvening(true) }, label = { Text("Evening review") })
                        }
                    }
                    item {
                        NafsSectionTitle(
                            if (state.isEvening) "Review your priorities" else "Today's priorities",
                            supporting = "Aim for 3–5 priorities. Your plan can hold up to seven.",
                        )
                    }
                    if (record.tasks.isEmpty()) item {
                        Text("No priorities planned yet. Add the things that matter today.", style = MaterialTheme.typography.bodyMedium)
                    }
                    record.tasks.forEachIndexed { index, task -> item(key = "priority-$index") {
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = task.done,
                                    onCheckedChange = { viewModel.setTaskDone(index, it) },
                                    enabled = state.canEdit,
                                    modifier = Modifier.semantics { contentDescription = "Complete ${task.text}" },
                                )
                                Text(task.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                if (!state.isEvening) TextButton(onClick = { viewModel.removeTask(index) }, enabled = state.canEdit) { Text("Remove") }
                            }
                        }
                    } }
                    if (!state.isEvening && record.tasks.size < CheckInRules.MAX_TASKS) item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = state.newTask, onValueChange = viewModel::setNewTask,
                                label = { Text("Add a priority") }, singleLine = true,
                                modifier = Modifier.fillMaxWidth(), enabled = state.canEdit,
                            )
                            TextButton(onClick = viewModel::addTask, enabled = state.canEdit && state.newTask.isNotBlank()) { Text("Add to today's plan") }
                        }
                    }
                    if (state.isEvening) item {
                        OutlinedTextField(
                            value = record.eveningText.orEmpty(), onValueChange = viewModel::setEveningText,
                            label = { Text("How did your day go?") },
                            placeholder = { Text("What did you do, what got in the way, and how did you feel?") },
                            minLines = 5, modifier = Modifier.fillMaxWidth(), enabled = state.canEdit,
                            supportingText = { Text("${record.eveningText?.length ?: 0}/10,000 characters") },
                        )
                    }
                    item {
                        Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth(), enabled = state.canEdit && state.hasChanges) {
                            Text(when {
                                state.isSaving -> "Saving…"
                                state.isEvening -> "Save evening review"
                                else -> "Save morning plan"
                            })
                        }
                    }
                    if (state.isEvening) item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = viewModel::generateVerdict, modifier = Modifier.fillMaxWidth(), enabled = state.canGenerate) {
                                Text(when {
                                    state.isGenerating -> "Generating review…"
                                    state.generatedVersion == state.saved?.updatedAt && state.generatedVersion != null -> "AI review saved"
                                    else -> "Get AI evening review"
                                })
                            }
                            if (state.hasChanges || state.saved?.eveningText.isNullOrBlank()) {
                                Text("Save your evening notes before requesting a review.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (state.isEvening) record.aiVerdict?.takeIf(String::isNotBlank)?.let { verdict -> item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("AI evening review", style = MaterialTheme.typography.titleMedium)
                                if (state.generatedVersion != record.updatedAt || state.hasChanges) {
                                    Text("Previously saved review. New entries may need a fresh review.", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(verdict)
                            }
                        }
                    } }
                }
            }
        }
    }
}
