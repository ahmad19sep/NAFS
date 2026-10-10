package com.ahmad.nafs.ui.letters

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmad.nafs.core.letters.FutureLetter
import com.ahmad.nafs.core.letters.LetterRules
import com.ahmad.nafs.core.letters.LetterReplyRules
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.core.time.AccountDate
import com.ahmad.nafs.ui.AccountDayRefreshEffects
import com.ahmad.nafs.ui.components.NafsHeroCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LetterRoute(profile: UserProfile, onBack: () -> Unit, viewModel: LetterViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(profile.userId, profile.timezone) { viewModel.initialize(profile.userId, AccountDate.today(profile.timezone)) }
    AccountDayRefreshEffects(profile.timezone) { viewModel.refresh(AccountDate.today(profile.timezone)) }
    var confirmDiscard by rememberSaveable(profile.userId) { mutableStateOf(false) }
    var confirmSaved by rememberSaveable(profile.userId) { mutableStateOf(false) }
    val back = {
        if (state.screen == LetterScreen.ARCHIVE) onBack() else viewModel.closeEditor()
    }
    BackHandler(enabled = state.screen != LetterScreen.ARCHIVE || state.isSaving) { if (!state.isSaving) back() }
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(if (state.screen == LetterScreen.EDITOR) "Write a letter" else "Future-self letters") },
                navigationIcon = { IconButton(onClick = back, enabled = !state.isSaving && !state.isLoadingMore) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = viewModel::reload, enabled = !state.isLoading && !state.isSaving && !state.isLoadingMore) { Icon(Icons.Rounded.Refresh, "Reload saved letters") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.isOffline) item {
                Text("Showing saved letters. Drafts stay on this device until you save online.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
            state.error?.let { error -> item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(error, style = MaterialTheme.typography.bodyMedium)
                        if (state.pending != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = viewModel::save, enabled = state.canSave) { Text("Retry save") }
                                TextButton(onClick = viewModel::reload, enabled = !state.isLoading && !state.isSaving) { Text("Check saved letter") }
                            }
                            if (state.pendingMissing && !state.isOffline) TextButton(onClick = viewModel::keepEditing, enabled = !state.isSaving) { Text("Keep editing") }
                            state.different?.takeUnless { state.isOffline }?.let { different ->
                                Text(different.content, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { confirmSaved = true }, enabled = !state.isSaving) { Text("Use saved letter") }
                            }
                        } else TextButton(onClick = viewModel::reload, enabled = !state.isLoading && !state.isSaving) { Text("Reload") }
                    }
                }
            } }
            state.message?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.primary) } }
            if (state.isLoading) item { CircularProgressIndicator(Modifier.padding(12.dp)) }
            when (state.screen) {
                LetterScreen.ARCHIVE -> {
                    item {
                        NafsHeroCard("Your future self", "Remember what matters now", "Write to the person you’re becoming, and choose a day to revisit your words.")
                    }
                    if (state.pendingReply != null) item {
                        TextButton(onClick = viewModel::openPendingReply, enabled = !state.isSaving) { Text("Continue retained reply request") }
                    }
                    state.draft?.let { draft -> item {
                        Card(onClick = viewModel::openDraft, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(if (state.pending != null) "Continue retained save" else "Continue your draft", style = MaterialTheme.typography.titleMedium)
                                Text(draft.content.ifBlank { "Your letter is ready to write." }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    } }
                    if (state.draft == null && state.pending == null) item {
                        Button(onClick = viewModel::newLetter, enabled = state.canEdit, modifier = Modifier.fillMaxWidth()) { Text("Write a letter") }
                    }
                    if (state.letters.isEmpty() && !state.isLoading && state.error == null) item {
                        Text(if (state.isOffline) "No letters are saved on this device. Connect and reload your archive." else "Your saved letters will appear here.")
                    }
                    items(state.letters, key = { it.id }) { letter ->
                        Card(onClick = { viewModel.openLetter(letter.id) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(letter.status(state.today), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                Text(letter.writtenLabel(profile.timezone), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(letter.content.ifBlank { "This letter has no written content." }, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                Text("For ${letter.targetLabel}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (state.isLoadingMore) item { CircularProgressIndicator(Modifier.padding(12.dp)) }
                    else if (state.next != null && !state.isOffline && !state.isLoading) item {
                        TextButton(onClick = viewModel::loadMore, modifier = Modifier.fillMaxWidth()) { Text("Load older letters") }
                    }
                }
                LetterScreen.EDITOR -> state.draft?.let { draft ->
                    item { Text("What do you hope to remember? Write about your goals, worries, promises, or the person you want to become.", style = MaterialTheme.typography.bodyMedium) }
                    item {
                        OutlinedTextField(
                            value = draft.content, onValueChange = viewModel::setContent, label = { Text("Dear future me…") },
                            modifier = Modifier.fillMaxWidth(), minLines = 8, maxLines = 16, enabled = state.canEdit,
                            isError = draft.content.trim().length > LetterRules.MAX_CONTENT,
                            supportingText = { Text("${draft.content.length}/10,000 characters") },
                        )
                    }
                    item {
                        OutlinedTextField(value = draft.targetDate, onValueChange = viewModel::setTargetDate,
                            label = { Text("Revisit date (YYYY-MM-DD)") }, singleLine = true, enabled = state.canEdit,
                            modifier = Modifier.fillMaxWidth(), supportingText = { Text("After today · ${profile.timezone}") })
                    }
                    if (state.pending == null) LetterRules.error(draft, state.today)?.let { error -> item { Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                    item {
                        Text(if (state.draftSaved) "Draft saved on this device." else "Draft has not been saved on this device yet.", style = MaterialTheme.typography.bodySmall)
                    }
                    item {
                        Button(onClick = viewModel::save, enabled = state.canSave, modifier = Modifier.fillMaxWidth()) {
                            Text(when { state.isSaving -> "Saving…"; state.pending != null -> "Retry same save"; else -> "Save letter" })
                        }
                    }
                    item { TextButton(onClick = { confirmDiscard = true }, enabled = state.canEdit) { Text("Discard draft") } }
                }
                LetterScreen.DETAIL -> state.selected?.let { letter ->
                    item { LetterReading(letter, profile.timezone, letter.status(state.today)) }
                    if (LetterReplyRules.eligible(letter, state.today) || state.pendingReply?.letter?.id == letter.id) item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Ask the coach to reflect on this letter using your recent recorded progress.", style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = viewModel::generateReply, enabled = state.canGenerateReply, modifier = Modifier.fillMaxWidth()) {
                                Text(if (state.isSaving) "Requesting reply…" else if (state.pendingReply?.letter?.id == letter.id) "Retry reply request" else "Request coach reply")
                            }
                            if (state.isOffline) Text("Connect and reload this letter to request a reply.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text("Discard this draft?") }, text = { Text("This removes the unsaved letter from this device.") },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; viewModel.discardDraft() }, enabled = state.canEdit) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep writing") } },
    )
    if (confirmSaved) AlertDialog(
        onDismissRequest = { confirmSaved = false },
        title = { Text("Use the saved letter?") }, text = { Text("This removes the retained draft and opens the letter already saved in your account.") },
        confirmButton = { TextButton(onClick = { confirmSaved = false; viewModel.acceptSaved() }, enabled = !state.isSaving && !state.isLoading && !state.isOffline) { Text("Use saved letter") } },
        dismissButton = { TextButton(onClick = { confirmSaved = false }) { Text("Keep draft") } },
    )
}

@Composable
private fun LetterReading(letter: FutureLetter, timezone: String, status: String) {
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Letter to your future self", style = MaterialTheme.typography.headlineSmall)
            Text("Written ${letter.writtenLabel(timezone)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("For ${letter.targetLabel} · $status", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Text(letter.content.ifBlank { "This letter has no written content." }, style = MaterialTheme.typography.bodyLarge)
            if (!letter.aiReplyText.isNullOrBlank()) {
                Text("Your saved coach reply", style = MaterialTheme.typography.titleMedium)
                Text(letter.aiReplyText, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
