package com.ahmad.nafs.ui.plans

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.core.time.AccountDate
import com.ahmad.nafs.ui.AccountDayRefreshEffects

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoveryRoute(profile: UserProfile, conversationId: String?, version: String?, onBack: () -> Unit,
    onOpenFeature: (String) -> Unit, viewModel: RecoveryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(profile.userId, profile.timezone, conversationId, version) {
        viewModel.initialize(profile.userId, AccountDate.today(profile.timezone), profile.timezone, conversationId, version)
    }
    AccountDayRefreshEffects(profile.timezone) { viewModel.refresh(AccountDate.today(profile.timezone)) }
    var confirmDiscard by rememberSaveable(profile.userId) { mutableStateOf(false) }
    val idle = !state.isLoading && !state.isBusy
    val back = { if (idle) viewModel.flushDraft(onBack) }
    BackHandler { back() }
    Scaffold(modifier = Modifier.imePadding(), topBar = {
        TopAppBar(title = { Text("Recovery plan") }, navigationIcon = {
            IconButton(onClick = back, enabled = idle) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        }, actions = {
            IconButton(onClick = viewModel::reload, enabled = idle) { Icon(Icons.Rounded.Refresh, "Check saved steps") }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (state.isOffline) item { Text("Showing your saved plan. Reconnect and reload to generate or add steps.", style = MaterialTheme.typography.bodySmall) }
            state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
            state.message?.let { message -> item { Text(message, style = MaterialTheme.typography.bodyMedium) } }
            if (state.isLoading || state.isBusy) item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text(if (state.isLoading) "Checking your saved plan..." else "Saving your progress...")
                }
            }
            val preview = state.preview
            if (!state.isLoading && preview == null) {
                item {
                    Text("What is getting in your way?", style = MaterialTheme.typography.titleLarge)
                    Text("Describe a setback. Coach will suggest up to four steps for you to review before adding them.")
                }
                item {
                    val draft = state.snapshot?.draft.orEmpty()
                    OutlinedTextField(value = draft, onValueChange = viewModel::editDraft, enabled = state.editable,
                        label = { Text("Your problem") }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 8,
                        supportingText = { Text("${draft.trim().length}/500 · " + if (state.draftSaved) "Saved on this device" else "Saving draft...") })
                }
                item {
                    Button(onClick = { viewModel.generate() }, enabled = state.canGenerate, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.snapshot?.pending != null) "Retry retained request" else "Preview a plan")
                    }
                }
                if (conversationId != null && version != null && state.snapshot?.pending == null) item {
                    Text("Use the saved conversation you opened from Coach. Any unsent message stays in Coach.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { viewModel.generate(fromChat = true) }, enabled = idle && !state.isOffline,
                        modifier = Modifier.fillMaxWidth()) { Text("Preview plan from saved chat") }
                }
                if (state.snapshot?.pending != null) item {
                    TextButton(onClick = { confirmDiscard = true }, enabled = idle) { Text("Change the problem") }
                }
            }
            if (preview != null) {
                item {
                    SelectionContainer { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(preview.response.recovery.problem, style = MaterialTheme.typography.titleLarge)
                        Text(preview.response.recovery.approach)
                    } }
                    Text("Based on records for ${preview.response.sourceDate}. New tasks and challenges start on your account day when first added.", style = MaterialTheme.typography.bodySmall)
                    if (preview.response.fellBack) Text("An alternate AI provider prepared this plan.", style = MaterialTheme.typography.bodySmall)
                    if (preview.response.contextPartial) Text("Some records were unavailable. Coach used the records it could load.", style = MaterialTheme.typography.bodySmall)
                    if (preview.response.dropped > 0) Text("${preview.response.dropped} unusable steps were left out.", style = MaterialTheme.typography.bodySmall)
                }
                itemsIndexed(preview.response.recovery.steps, key = { index, _ -> preview.steps[index].id }) { index, proposal ->
                    val step = preview.steps[index]
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row {
                                Checkbox(checked = step.selected, onCheckedChange = { viewModel.select(index, it) }, enabled = idle && !step.added)
                                Column(Modifier.weight(1f)) {
                                    Text("${proposal.emoji} ${proposal.title}", style = MaterialTheme.typography.titleMedium)
                                    Text(proposal.kind.replaceFirstChar(Char::uppercase) + " · " + proposal.description(), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            SelectionContainer { Text(proposal.reason) }
                            proposal.task?.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            step.applyDate?.let { Text("First add date: $it", style = MaterialTheme.typography.labelMedium) }
                            if (step.added) Text("Added", color = MaterialTheme.colorScheme.primary)
                            step.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            if (step.added) TextButton(onClick = { onOpenFeature(when (proposal.kind) { "task" -> "tasks"; "habit" -> "habits"; else -> "challenges" }) }, enabled = idle) {
                                Text("Open ${when (proposal.kind) { "task" -> "tasks"; "habit" -> "habits"; else -> "challenges" }}")
                            }
                        }
                    }
                }
                item {
                    Button(onClick = viewModel::apply, enabled = state.canApply, modifier = Modifier.fillMaxWidth()) {
                        Text("Add ${state.toAdd} selected ${if (state.toAdd == 1) "step" else "steps"}")
                    }
                    TextButton(onClick = viewModel::reload, enabled = idle) { Text("Check saved steps") }
                    TextButton(onClick = { confirmDiscard = true }, enabled = idle) { Text("Start another plan") }
                }
            }
        }
    }
    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false }, title = { Text("Start another plan?") },
        text = { Text(if (state.preview?.hasUnconfirmed == true)
            "Some items may already be saved. Reload to check them before starting another plan. Clearing this preview keeps any items already added."
            else "This clears the saved preview or planning request. Any items already added stay in their features.") },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; viewModel.discard() }, enabled = idle) { Text("Clear preview") } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep plan") } })
}
