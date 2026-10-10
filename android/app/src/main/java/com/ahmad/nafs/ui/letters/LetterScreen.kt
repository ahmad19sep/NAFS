package com.ahmad.nafs.ui.letters

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmad.nafs.core.letters.FutureLetter
import com.ahmad.nafs.core.letters.LetterDraft
import com.ahmad.nafs.core.letters.LetterReplyRules
import com.ahmad.nafs.core.letters.LetterRules
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.core.time.AccountDate
import com.ahmad.nafs.ui.AccountDayRefreshEffects
import com.ahmad.nafs.ui.components.NafsBanner
import com.ahmad.nafs.ui.components.NafsBannerKind
import com.ahmad.nafs.ui.components.NafsCard
import com.ahmad.nafs.ui.components.NafsChoiceChip
import com.ahmad.nafs.ui.components.NafsDivider
import com.ahmad.nafs.ui.components.NafsEmptyState
import com.ahmad.nafs.ui.components.NafsEyebrow
import com.ahmad.nafs.ui.components.NafsHeroCard
import com.ahmad.nafs.ui.components.NafsIconBadge
import com.ahmad.nafs.ui.components.NafsPill
import com.ahmad.nafs.ui.components.NafsPrimaryButton
import com.ahmad.nafs.ui.components.NafsSecondaryButton
import com.ahmad.nafs.ui.components.NafsSectionTitle
import com.ahmad.nafs.ui.components.NafsSpace
import com.ahmad.nafs.ui.components.NafsTextField
import com.ahmad.nafs.ui.components.NafsTopBar
import com.ahmad.nafs.ui.reports.ReportMarkdown
import com.ahmad.nafs.ui.theme.Nafs
import java.time.LocalDate

@Composable
fun LetterRoute(profile: UserProfile, onBack: () -> Unit, viewModel: LetterViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(profile.userId, profile.timezone) { viewModel.initialize(profile.userId, AccountDate.today(profile.timezone)) }
    AccountDayRefreshEffects(profile.timezone) { viewModel.refresh(AccountDate.today(profile.timezone)) }
    val back = {
        if (state.screen == LetterScreen.ARCHIVE) onBack() else viewModel.closeEditor()
    }
    BackHandler(enabled = state.screen != LetterScreen.ARCHIVE || state.isSaving) { if (!state.isSaving) back() }
    LettersScreen(
        state = state,
        profile = profile,
        actions = LetterActions(
            onBack = back,
            onReload = viewModel::reload,
            onLoadMore = viewModel::loadMore,
            onNewLetter = viewModel::newLetter,
            onOpenDraft = viewModel::openDraft,
            onOpenLetter = viewModel::openLetter,
            onOpenPendingReply = viewModel::openPendingReply,
            onContentChange = viewModel::setContent,
            onTargetDateChange = viewModel::setTargetDate,
            onSave = viewModel::save,
            onDiscardDraft = viewModel::discardDraft,
            onKeepEditing = viewModel::keepEditing,
            onAcceptSaved = viewModel::acceptSaved,
            onGenerateReply = viewModel::generateReply,
        ),
    )
}

/** Everything the letters screen can ask its ViewModel to do. */
internal data class LetterActions(
    val onBack: () -> Unit = {},
    val onReload: () -> Unit = {},
    val onLoadMore: () -> Unit = {},
    val onNewLetter: () -> Unit = {},
    val onOpenDraft: () -> Unit = {},
    val onOpenLetter: (String) -> Unit = {},
    val onOpenPendingReply: () -> Unit = {},
    val onContentChange: (String) -> Unit = {},
    val onTargetDateChange: (String) -> Unit = {},
    val onSave: () -> Unit = {},
    val onDiscardDraft: () -> Unit = {},
    val onKeepEditing: () -> Unit = {},
    val onAcceptSaved: () -> Unit = {},
    val onGenerateReply: () -> Unit = {},
)

@Composable
internal fun LettersScreen(state: LetterUiState, profile: UserProfile, actions: LetterActions) {
    var confirmDiscard by rememberSaveable(profile.userId) { mutableStateOf(false) }
    var confirmSaved by rememberSaveable(profile.userId) { mutableStateOf(false) }
    val draft = state.draft
    val selected = state.selected
    val replyAvailable = selected?.let { letter ->
        LetterReplyRules.eligible(letter, state.today) || state.pendingReply?.letter?.id == letter.id
    } == true
    val hasBottomBar = when (state.screen) {
        LetterScreen.ARCHIVE -> state.draft == null && state.pending == null
        LetterScreen.EDITOR -> draft != null
        LetterScreen.DETAIL -> replyAvailable
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        NafsTopBar(
            title = when (state.screen) {
                LetterScreen.ARCHIVE -> "Future-self letters"
                LetterScreen.EDITOR -> "Write a letter"
                LetterScreen.DETAIL -> "Your letter"
            },
            onBack = { if (!state.isSaving && !state.isLoadingMore) actions.onBack() },
            subtitle = when (state.screen) {
                LetterScreen.ARCHIVE -> "Words for the person you’re becoming"
                LetterScreen.EDITOR -> "Choose a day to revisit your words"
                LetterScreen.DETAIL -> "Future-self letters"
            },
            actions = {
                IconButton(onClick = actions.onReload, enabled = !state.isLoading && !state.isSaving && !state.isLoadingMore) {
                    Icon(Icons.Rounded.Refresh, "Reload saved letters")
                }
            },
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().then(if (hasBottomBar) Modifier else Modifier.navigationBarsPadding()),
            contentPadding = PaddingValues(start = NafsSpace.screen, end = NafsSpace.screen, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(NafsSpace.item),
        ) {
            if (state.isOffline) item(key = "offline") {
                NafsBanner("Showing saved letters. Drafts stay on this device until you save online.", kind = NafsBannerKind.OFFLINE)
            }
            state.error?.let { error ->
                item(key = "error") { LetterIssueCard(error, state, actions, onUseSaved = { confirmSaved = true }) }
            }
            state.message?.let { message -> item(key = "message") { NafsBanner(message, kind = NafsBannerKind.SUCCESS) } }
            if (state.isLoading) item(key = "loading") { LoadingRow() }
            when (state.screen) {
                LetterScreen.ARCHIVE -> archiveItems(state, profile.timezone, actions)
                LetterScreen.EDITOR -> draft?.let { editing -> editorItems(state, editing, profile.timezone, actions) }
                LetterScreen.DETAIL -> selected?.let { letter -> detailItems(state, letter, profile.timezone, replyAvailable) }
            }
        }
        if (hasBottomBar) {
            Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.imePadding().navigationBarsPadding()) {
                    NafsDivider(inset = 0.dp)
                    Column(
                        Modifier.padding(horizontal = NafsSpace.screen, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        when (state.screen) {
                            LetterScreen.ARCHIVE -> NafsPrimaryButton(
                                "Write a letter",
                                onClick = actions.onNewLetter,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = state.canEdit,
                                icon = Icons.Rounded.Edit,
                            )
                            LetterScreen.EDITOR -> EditorActions(state, actions, onDiscard = { confirmDiscard = true })
                            LetterScreen.DETAIL -> NafsPrimaryButton(
                                text = if (state.isSaving) "Requesting reply…"
                                    else if (state.pendingReply?.letter?.id == selected?.id) "Retry reply request"
                                    else "Request coach reply",
                                onClick = actions.onGenerateReply,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = state.canGenerateReply,
                                loading = state.isSaving,
                                icon = Icons.Rounded.AutoAwesome,
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        icon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
        title = { Text("Discard this draft?") }, text = { Text("This removes the unsaved letter from this device.") },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; actions.onDiscardDraft() }, enabled = state.canEdit) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep writing") } },
    )
    if (confirmSaved) AlertDialog(
        onDismissRequest = { confirmSaved = false },
        icon = { Icon(Icons.Rounded.MailOutline, contentDescription = null) },
        title = { Text("Use the saved letter?") }, text = { Text("This removes the retained draft and opens the letter already saved in your account.") },
        confirmButton = {
            TextButton(onClick = { confirmSaved = false; actions.onAcceptSaved() }, enabled = !state.isSaving && !state.isLoading && !state.isOffline) {
                Text("Use saved letter")
            }
        },
        dismissButton = { TextButton(onClick = { confirmSaved = false }) { Text("Keep draft") } },
    )
}

// ─── Archive ─────────────────────────────────────────────────────────────────

private fun LazyListScope.archiveItems(state: LetterUiState, timezone: String, actions: LetterActions) {
    item(key = "hero") {
        NafsHeroCard("Your future self", "Remember what matters now", "Write to the person you’re becoming, and choose a day to revisit your words.")
    }
    if (state.pendingReply != null) item(key = "pending-reply") {
        ResumeCard(
            icon = Icons.Rounded.Autorenew,
            title = "Continue retained reply request",
            body = "Open the letter to retry the same request or check for its reply.",
            enabled = !state.isSaving,
            onClick = actions.onOpenPendingReply,
        )
    }
    state.draft?.let { draft -> item(key = "draft") { DraftCard(draft, retained = state.pending != null, onClick = actions.onOpenDraft) } }
    if (state.letters.isEmpty() && !state.isLoading && state.error == null) item(key = "empty") {
        NafsEmptyState(
            icon = Icons.Rounded.MailOutline,
            title = if (state.isOffline) "No letters on this device" else "No letters yet",
            message = if (state.isOffline) "No letters are saved on this device. Connect and reload your archive." else "Your saved letters will appear here.",
            tint = Nafs.colors.coach,
        )
    }
    if (state.letters.isNotEmpty()) item(key = "letters-title") {
        NafsSectionTitle("Your letters", Modifier.padding(top = 10.dp), supporting = "Newest first. Open one to read it again.")
    }
    items(state.letters, key = { it.id }) { letter ->
        LetterCard(letter, state.today, timezone, onClick = { actions.onOpenLetter(letter.id) })
    }
    if (state.isLoadingMore) item(key = "more-loading") { LoadingRow() }
    else if (state.next != null && !state.isOffline && !state.isLoading) item(key = "more") {
        NafsSecondaryButton("Load older letters", onClick = actions.onLoadMore, modifier = Modifier.fillMaxWidth())
    }
}

private enum class LetterTone { REPLIED, DELIVERED, READY, WAITING }

private fun letterTone(letter: FutureLetter, today: LocalDate): LetterTone = when {
    !letter.aiReplyText.isNullOrBlank() -> LetterTone.REPLIED
    letter.deliveredAt != null -> LetterTone.DELIVERED
    runCatching { LocalDate.parse(letter.targetDate) <= today }.getOrDefault(false) -> LetterTone.READY
    else -> LetterTone.WAITING
}

@Composable
private fun toneColor(tone: LetterTone): Color = when (tone) {
    LetterTone.REPLIED -> Nafs.colors.success
    LetterTone.DELIVERED -> MaterialTheme.colorScheme.primary
    LetterTone.READY -> Nafs.colors.gold
    LetterTone.WAITING -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun toneIcon(tone: LetterTone): ImageVector = when (tone) {
    LetterTone.REPLIED -> Icons.Rounded.CheckCircle
    LetterTone.DELIVERED -> Icons.Rounded.MailOutline
    LetterTone.READY -> Icons.Rounded.Stars
    LetterTone.WAITING -> Icons.Rounded.Schedule
}

/** Status first, then the target date unless the status already names it. */
@Composable
private fun LetterPills(letter: FutureLetter, today: LocalDate) {
    val tone = letterTone(letter, today)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        NafsPill(letter.status(today), color = toneColor(tone), icon = toneIcon(tone))
        if (tone != LetterTone.WAITING) {
            NafsPill("For ${letter.targetLabel}", color = MaterialTheme.colorScheme.onSurfaceVariant, icon = Icons.Rounded.CalendarMonth)
        }
    }
}

@Composable
private fun LetterCard(letter: FutureLetter, today: LocalDate, timezone: String, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    NafsCard(onClick = onClick) {
        LetterPills(letter, today)
        Spacer(Modifier.height(12.dp))
        Text(
            letter.content.ifBlank { "This letter has no written content." },
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Edit, contentDescription = null, tint = muted, modifier = Modifier.size(13.dp))
            Text("Written ${letter.writtenLabel(timezone)}", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = muted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun DraftCard(draft: LetterDraft, retained: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    NafsCard(onClick = onClick, borderColor = primary.copy(alpha = 0.35f)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            NafsIconBadge(Icons.Rounded.Edit, primary, size = 40.dp, iconSize = 20.dp)
            Text(if (retained) "Continue retained save" else "Continue your draft", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            draft.content.ifBlank { "Your letter is ready to write." },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ResumeCard(icon: ImageVector, title: String, body: String, enabled: Boolean, onClick: () -> Unit) {
    NafsCard(onClick = onClick, enabled = enabled, contentPadding = PaddingValues(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            NafsIconBadge(icon, Nafs.colors.coach, size = 40.dp, iconSize = 20.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─── Editor ──────────────────────────────────────────────────────────────────

private fun LazyListScope.editorItems(state: LetterUiState, draft: LetterDraft, timezone: String, actions: LetterActions) {
    item(key = "prompt") {
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            NafsIconBadge(Icons.Rounded.Lightbulb, Nafs.colors.reflect, size = 36.dp, iconSize = 18.dp)
            Text(
                "What do you hope to remember? Write about your goals, worries, promises, or the person you want to become.",
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    item(key = "content") {
        NafsTextField(
            value = draft.content,
            onValueChange = actions.onContentChange,
            label = "Dear future me…",
            singleLine = false,
            minLines = 10,
            maxLines = 18,
            enabled = state.canEdit,
            isError = draft.content.trim().length > LetterRules.MAX_CONTENT,
            supportingText = "${draft.content.length}/10,000 characters",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
    }
    item(key = "date") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NafsEyebrow("Revisit on", Modifier.padding(start = 4.dp, top = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3L to "3 months", 6L to "6 months", 12L to "1 year").forEach { (months, label) ->
                    val date = state.today.plusMonths(months).toString()
                    NafsChoiceChip(
                        text = label,
                        selected = draft.targetDate == date,
                        onClick = { actions.onTargetDateChange(date) },
                        enabled = state.canEdit,
                    )
                }
            }
            NafsTextField(
                value = draft.targetDate,
                onValueChange = actions.onTargetDateChange,
                label = "Revisit date (YYYY-MM-DD)",
                leadingIcon = Icons.Rounded.CalendarMonth,
                supportingText = "After today · $timezone",
                enabled = state.canEdit,
            )
        }
    }
    if (state.pending == null) LetterRules.error(draft, state.today)?.let { hint ->
        item(key = "validation") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EditorActions(state: LetterUiState, actions: LetterActions, onDiscard: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (state.draftSaved) Icons.Rounded.CheckCircle else Icons.Rounded.Schedule,
            contentDescription = null,
            tint = if (state.draftSaved) Nafs.colors.success else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Text(
            if (state.draftSaved) "Draft saved on this device." else "Draft has not been saved on this device yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onDiscard, enabled = state.canEdit, modifier = Modifier.height(54.dp)) { Text("Discard draft") }
        NafsPrimaryButton(
            text = when {
                state.isSaving -> "Saving…"
                state.pending != null -> "Retry same save"
                else -> "Save letter"
            },
            onClick = actions.onSave,
            modifier = Modifier.weight(1f),
            enabled = state.canSave,
            loading = state.isSaving,
        )
    }
}

// ─── Reading ─────────────────────────────────────────────────────────────────

private fun LazyListScope.detailItems(state: LetterUiState, letter: FutureLetter, timezone: String, replyAvailable: Boolean) {
    item(key = "reading-${letter.id}") { LetterReading(letter, state.today, timezone) }
    if (replyAvailable) item(key = "reply-ask") {
        NafsCard(contentPadding = PaddingValues(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                NafsIconBadge(Icons.Rounded.AutoAwesome, Nafs.colors.coach, size = 36.dp, iconSize = 18.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Ask the coach to reflect on this letter using your recent recorded progress.", style = MaterialTheme.typography.bodyMedium)
                    if (state.isOffline) {
                        Text("Connect and reload this letter to request a reply.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun LetterReading(letter: FutureLetter, today: LocalDate, timezone: String) {
    val coach = Nafs.colors.coach
    SelectionContainer {
        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LetterPills(letter, today)
                Text("Letter to your future self", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Text("Written ${letter.writtenLabel(timezone)}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            NafsCard(contentPadding = PaddingValues(22.dp)) {
                Text(
                    letter.content.ifBlank { "This letter has no written content." },
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 27.sp),
                )
            }
            if (!letter.aiReplyText.isNullOrBlank()) {
                NafsCard(color = coach.copy(alpha = if (Nafs.colors.isDark) 0.10f else 0.06f), borderColor = coach.copy(alpha = 0.3f),
                    contentPadding = PaddingValues(20.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        NafsIconBadge(Icons.Rounded.AutoAwesome, coach, size = 38.dp, iconSize = 20.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            NafsEyebrow("From your coach", color = coach)
                            Text("Your saved coach reply", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    ReportMarkdown(letter.aiReplyText)
                }
            }
        }
    }
}

// ─── States ──────────────────────────────────────────────────────────────────

/** Errors, plus the retained-save recovery choices the letter rules allow. */
@Composable
private fun LetterIssueCard(message: String, state: LetterUiState, actions: LetterActions, onUseSaved: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val retained = state.pending != null
    val accent = if (retained) Nafs.colors.warning else scheme.error
    NafsCard(borderColor = accent.copy(alpha = 0.4f), shape = MaterialTheme.shapes.medium, contentPadding = PaddingValues(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Icon(if (retained) Icons.Rounded.WarningAmber else Icons.Rounded.ErrorOutline, contentDescription = null, tint = accent,
                modifier = Modifier.size(20.dp))
            Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(10.dp))
        if (retained) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SmallTonalButton("Retry save", actions.onSave, enabled = state.canSave)
                TextButton(onClick = actions.onReload, enabled = !state.isLoading && !state.isSaving) { Text("Check saved letter") }
            }
            if (state.pendingMissing && !state.isOffline) {
                TextButton(onClick = actions.onKeepEditing, enabled = !state.isSaving) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Keep editing")
                }
            }
            state.different?.takeUnless { state.isOffline }?.let { different ->
                Spacer(Modifier.height(6.dp))
                Column(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(scheme.surfaceContainerHigh).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    NafsEyebrow("Saved in your account")
                    Text(different.content, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(10.dp))
                SmallTonalButton("Use saved letter", onUseSaved, enabled = !state.isSaving)
            }
        } else {
            TextButton(onClick = actions.onReload, enabled = !state.isLoading && !state.isSaving) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Reload")
            }
        }
    }
}

@Composable
private fun SmallTonalButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 40.dp),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer),
        contentPadding = PaddingValues(horizontal = 14.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LoadingRow() {
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Nafs.colors.coach)
            Text("Checking your letters…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
