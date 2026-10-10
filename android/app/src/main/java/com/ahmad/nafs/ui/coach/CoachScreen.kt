package com.ahmad.nafs.ui.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.core.coach.CoachRules
import com.ahmad.nafs.core.coach.CoachConversation
import com.ahmad.nafs.core.plans.RecoveryRules
import com.ahmad.nafs.ui.AccountDayRefreshEffects
import com.ahmad.nafs.ui.components.NafsHeroCard

@Composable
fun CoachRoute(profile: UserProfile, onBack: () -> Unit, onOpenReports: () -> Unit, onOpenLetters: () -> Unit,
    onOpenRecovery: () -> Unit, onPlanFromChat: (CoachConversation) -> Unit, viewModel: CoachViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(profile.userId) { viewModel.initialize(profile.userId) }
    AccountDayRefreshEffects(profile.timezone, viewModel::refresh)
    CoachScreen(state, onBack, onOpenReports, onOpenLetters, onOpenRecovery, {
        state.conversation?.takeIf { RecoveryRules.canUseChat(profile.userId, it) }?.let(onPlanFromChat)
    }, viewModel::reload, viewModel::newConversation,
        viewModel::selectConversation, viewModel::updateDraft, viewModel::send, viewModel::keepMessageAsDraft)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CoachScreen(
    state: CoachUiState,
    onBack: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenLetters: () -> Unit,
    onOpenRecovery: () -> Unit,
    onPlanFromChat: () -> Unit,
    onReload: () -> Unit,
    onNew: () -> Unit,
    onSelect: (String) -> Unit,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onKeepDraft: () -> Unit,
) {
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val send = {
        focus.clearFocus()
        keyboard?.hide()
        onSend()
    }
    LaunchedEffect(state.messages.size, state.isSending) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text("Coach") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = onNew, enabled = !state.isLoading && !state.isSending && state.pending == null) {
                        Icon(Icons.Rounded.Add, "New conversation")
                    }
                    IconButton(onClick = onReload, enabled = !state.isSending && !state.isLoading) {
                        Icon(Icons.Rounded.Refresh, "Reload saved conversations")
                    }
                },
            )
        },
        bottomBar = {
            if (state.pending != null) {
                Card(Modifier.fillMaxWidth().padding(12.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(when {
                            state.isSending -> "Coach is replying..."
                            state.isLoading -> "Checking for a saved reply..."
                            else -> "Your message is kept for retry"
                        }, style = MaterialTheme.typography.titleSmall)
                        if (!state.isSending && !state.isLoading) {
                            Text(state.error ?: "Check for a saved reply or retry your message.",
                                style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = send, enabled = !state.requiresReload && !state.isOffline) {
                                    Text("Retry message")
                                }
                                TextButton(onClick = onReload) { Text("Check for reply") }
                            }
                            if (state.pendingReloaded && !state.isOffline) {
                                TextButton(onClick = onKeepDraft) { Text("Edit message") }
                            }
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = state.draft, onValueChange = onDraft, modifier = Modifier.weight(1f),
                        enabled = !state.isLoading && !state.isSending,
                        label = { Text("Message your coach") },
                        supportingText = {
                            Text(if (state.draft.isNotBlank()) CoachRules.messageError(state.conversation?.messages.orEmpty(), state.draft)
                                ?: "${state.draft.length}/4,000" else "${state.draft.length}/4,000")
                        }, maxLines = 4,
                    )
                    IconButton(onClick = send, enabled = state.canSend) { Icon(Icons.AutoMirrored.Rounded.Send, "Send message") }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { TextButton(onClick = onOpenReports) { Text("Reports") } }
                item { TextButton(onClick = onOpenLetters) { Text("Future-self letters") } }
                item { TextButton(onClick = onOpenRecovery) { Text("Recovery plan") } }
            }
            if (state.conversation?.messages?.isNotEmpty() == true) {
                TextButton(onClick = onPlanFromChat, modifier = Modifier.padding(horizontal = 12.dp),
                    enabled = !state.isLoading && !state.isSending && !state.isOffline && state.pending == null && state.draft.isBlank()) {
                    Text("Build a plan from this saved chat")
                }
            }
            if (state.conversations.isNotEmpty()) {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.conversations, key = { it.id }) { conversation ->
                        FilterChip(
                            selected = state.selectedId == conversation.id, onClick = { onSelect(conversation.id) },
                            enabled = !state.isSending && !state.isLoading && state.pending == null && state.draft.isBlank(),
                            label = { Text(conversation.title.take(32), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
            state.error?.takeIf { state.pending == null }?.let { error ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(error, style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onReload, enabled = !state.isSending && !state.isLoading) { Text("Reload") }
                        }
                    }
                }
            }
            if (state.contextPartial) {
                Text("Some records were unavailable for this reply.", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
            if (state.isLoading) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (state.messages.isEmpty()) {
                Box(Modifier.weight(1f).padding(18.dp), contentAlignment = Alignment.Center) {
                    NafsHeroCard("A conversation about your life", "What is on your mind?",
                        "Ask about your progress, talk through a setback, or explore your next step. Replies use your saved records and faith preference.")
                }
            } else {
                LazyColumn(
                    state = listState, modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(state.messages) { index, message ->
                        val fromUser = message.role == "user"
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(start = if (fromUser) 24.dp else 0.dp, end = if (fromUser) 0.dp else 24.dp),
                            colors = CardDefaults.cardColors(containerColor = if (fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer),
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(if (fromUser) "You" else "Coach", style = MaterialTheme.typography.labelMedium)
                                SelectionContainer { Text(message.content, style = MaterialTheme.typography.bodyLarge) }
                                if (state.pending != null && index == state.messages.lastIndex) {
                                    Text(if (state.isSending) "Waiting for a saved reply..." else "Reply not confirmed", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    if (state.isSending) item {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Coach is replying...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (state.messages.size >= 80) item {
                        Text("This conversation is full. Start a new conversation to continue.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
