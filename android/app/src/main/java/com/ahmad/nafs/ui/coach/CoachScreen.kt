package com.ahmad.nafs.ui.coach

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Healing
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmad.nafs.core.coach.CoachConversation
import com.ahmad.nafs.core.coach.CoachMessage
import com.ahmad.nafs.core.coach.CoachRules
import com.ahmad.nafs.core.coach.CoachText
import com.ahmad.nafs.core.plans.RecoveryRules
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.core.time.ServerTimestamp
import com.ahmad.nafs.ui.AccountDayRefreshEffects
import com.ahmad.nafs.ui.components.NafsBanner
import com.ahmad.nafs.ui.components.NafsBannerKind
import com.ahmad.nafs.ui.components.NafsCard
import com.ahmad.nafs.ui.components.NafsChoiceChip
import com.ahmad.nafs.ui.components.NafsDivider
import com.ahmad.nafs.ui.components.NafsEyebrow
import com.ahmad.nafs.ui.components.NafsHeroSurface
import com.ahmad.nafs.ui.components.NafsIconBadge
import com.ahmad.nafs.ui.components.NafsSpace
import com.ahmad.nafs.ui.components.NafsTopBar
import com.ahmad.nafs.ui.checkin.rememberSpeechInput
import com.ahmad.nafs.ui.reports.ReportText
import com.ahmad.nafs.ui.reports.ReportTextKind
import com.ahmad.nafs.ui.reports.ReportTextLine
import com.ahmad.nafs.ui.theme.Nafs
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

private const val COPIED_MILLIS = 2_000L

@Composable
fun CoachRoute(profile: UserProfile, onBack: () -> Unit, onOpenReports: () -> Unit, onOpenLetters: () -> Unit,
    onOpenRecovery: () -> Unit, onPlanFromChat: (CoachConversation) -> Unit, viewModel: CoachViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(profile.userId) { viewModel.initialize(profile.userId) }
    AccountDayRefreshEffects(profile.timezone, viewModel::refresh)
    val speech = rememberSpeechInput(
        prompt = "Speak your message",
        onResult = viewModel::appendDictation,
        onUnavailable = viewModel::voiceUnavailable,
    )
    CoachScreen(state, onBack, onOpenReports, onOpenLetters, onOpenRecovery, {
        state.conversation?.takeIf { RecoveryRules.canUseChat(profile.userId, it) }?.let(onPlanFromChat)
    }, viewModel::reload, viewModel::newConversation,
        viewModel::selectConversation, viewModel::updateDraft, viewModel::send, viewModel::keepMessageAsDraft,
        onCopyReply = { reply -> copyPlainText(context, "Coach reply", CoachText.plain(reply)) },
        onShareReply = { reply -> sharePlainText(context, "Share coach reply", CoachText.plain(reply)) },
        voiceAvailable = speech.isAvailable,
        onVoice = speech::start,
    )
}

/** One row of the chat timeline, so the list and its auto-scroll agree on which row is last. */
private sealed interface ChatRow {
    val key: String

    data class Started(val label: String) : ChatRow {
        override val key: String get() = "started"
    }

    data class Bubble(val index: Int, val message: CoachMessage) : ChatRow {
        override val key: String get() = "message-$index"
    }

    data object Pending : ChatRow {
        override val key: String get() = "pending"
    }

    data object Typing : ChatRow {
        override val key: String get() = "typing"
    }

    data object PartialContext : ChatRow {
        override val key: String get() = "partial"
    }

    data object Full : ChatRow {
        override val key: String get() = "full"
    }

    data object PlanFromChat : ChatRow {
        override val key: String get() = "plan"
    }
}

private fun chatRows(state: CoachUiState): List<ChatRow> = buildList {
    if (state.messages.isNotEmpty()) {
        state.conversation?.let { chat -> startedLabel(chat.createdAt)?.let { label -> add(ChatRow.Started(label)) } }
    }
    state.messages.forEachIndexed { index, message -> add(ChatRow.Bubble(index, message)) }
    if (state.pending != null && !state.isSending) add(ChatRow.Pending)
    if (state.isSending) add(ChatRow.Typing)
    if (state.contextPartial) add(ChatRow.PartialContext)
    if (state.messages.size >= CoachRules.MAX_MESSAGES) add(ChatRow.Full)
    if (state.pending == null && !state.isSending && state.conversation?.messages?.isNotEmpty() == true) add(ChatRow.PlanFromChat)
}

private fun startedLabel(createdAt: String): String? = runCatching {
    ServerTimestamp.parse(createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
}.getOrNull()

private fun chipTitle(title: String): String {
    val flat = title.replace('\n', ' ').trim()
    return if (flat.length > 32) flat.take(31).trimEnd() + "…" else flat
}

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
    /** Copies a reply as plain text; returns whether the clipboard accepted it, so "Copied" is never shown falsely. */
    onCopyReply: (String) -> Boolean = { false },
    /** Opens the share sheet for a reply; returns false when no app can receive it. */
    onShareReply: (String) -> Boolean = { false },
    voiceAvailable: Boolean = false,
    onVoice: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    var copiedIndex by remember { mutableStateOf<Int?>(null) }
    var replyActionError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(copiedIndex) {
        if (copiedIndex != null) {
            delay(COPIED_MILLIS)
            copiedIndex = null
        }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val send = {
        focus.clearFocus()
        keyboard?.hide()
        onSend()
    }
    val rows = chatRows(state)
    LaunchedEffect(state.messages.size, state.isSending) {
        if (state.messages.isNotEmpty() && rows.isNotEmpty()) listState.animateScrollToItem(rows.lastIndex)
    }
    val busy = state.isLoading || state.isSending
    val canStartNew = !busy && state.pending == null
    val canSwitch = !busy && state.pending == null && state.draft.isBlank()
    val composerEnabled = !busy && state.pending == null

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        NafsTopBar(
            title = "Coach",
            onBack = onBack,
            subtitle = if (state.isOffline) "Offline · showing saved history" else "Private · uses your saved records",
            actions = {
                IconButton(onClick = onNew, enabled = canStartNew) { Icon(Icons.Rounded.Add, "New conversation") }
                IconButton(onClick = onReload, enabled = !busy) {
                    if (state.isLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Refresh, "Reload saved conversations")
                }
            },
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { ShortcutChip(Icons.Rounded.Inventory2, "Reports", onOpenReports) }
            item { ShortcutChip(Icons.Rounded.MailOutline, "Future-self letters", onOpenLetters) }
            item { ShortcutChip(Icons.Rounded.Healing, "Recovery plan", onOpenRecovery) }
        }
        if (state.conversations.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item {
                    Text("Recent", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 2.dp))
                }
                items(state.conversations, key = { it.id }) { conversation ->
                    NafsChoiceChip(
                        text = chipTitle(conversation.title),
                        selected = state.selectedId == conversation.id,
                        onClick = { onSelect(conversation.id) },
                        enabled = canSwitch,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        NafsDivider(inset = 0.dp)
        if (state.isLoading && state.messages.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.takeIf { state.pending == null }?.let { error ->
            NafsBanner(
                error,
                Modifier.padding(horizontal = 16.dp).padding(top = 10.dp),
                kind = if (state.isOffline) NafsBannerKind.OFFLINE else NafsBannerKind.ERROR,
                actionLabel = "Reload".takeIf { !busy },
                onAction = onReload.takeIf { !busy },
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.messages.isNotEmpty() -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(rows, key = { it.key }) { row ->
                        when (row) {
                            is ChatRow.Started -> StartedLabel(row.label)
                            is ChatRow.Bubble -> {
                                if (row.message.role == "user") {
                                    UserBubble(row.message.content)
                                } else {
                                    CoachBubble(
                                        text = row.message.content,
                                        copied = copiedIndex == row.index,
                                        onCopy = {
                                            if (onCopyReply(row.message.content)) {
                                                copiedIndex = row.index
                                                replyActionError = null
                                            } else {
                                                replyActionError = "This reply couldn't be copied. Select the text to copy it instead."
                                            }
                                        },
                                        onShare = {
                                            replyActionError = if (onShareReply(row.message.content)) null
                                            else "No app on this phone can share text."
                                        },
                                    )
                                }
                            }
                            ChatRow.Pending -> PendingMessage(state, onRetry = send, onReload = onReload, onKeepDraft = onKeepDraft)
                            ChatRow.Typing -> TypingBubble()
                            ChatRow.PartialContext -> PartialContextNote()
                            ChatRow.Full -> FullConversationNote(canStartNew, onNew)
                            ChatRow.PlanFromChat -> PlanFromChatCard(
                                enabled = !busy && !state.isOffline && state.pending == null && state.draft.isBlank(),
                                hint = when {
                                    state.draft.isNotBlank() -> "Send or clear your message first."
                                    state.isOffline -> "Reconnect to turn this chat into steps."
                                    else -> "Turn this conversation into steps you can review."
                                },
                                onClick = onPlanFromChat,
                            )
                        }
                    }
                }
                state.isLoading -> Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp, color = Nafs.colors.coach)
                    Text("Loading your conversations…", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> CoachWelcome(enabled = composerEnabled, onPrompt = onDraft)
            }
        }

        replyActionError?.let { error ->
            NafsBanner(
                error,
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                kind = NafsBannerKind.WARNING,
                onDismiss = { replyActionError = null },
            )
        }
        CoachComposer(state, composerEnabled, onDraft, send, voiceAvailable, onVoice)
    }
}

// ─── Header ──────────────────────────────────────────────────────────────────

@Composable
private fun ShortcutChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = scheme.surfaceContainer,
        contentColor = scheme.onSurface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = Nafs.colors.coach, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

// ─── Timeline ────────────────────────────────────────────────────────────────

@Composable
private fun CoachAvatar() {
    NafsIconBadge(Icons.Rounded.AutoAwesome, Nafs.colors.coach, size = 30.dp, iconSize = 16.dp)
}

@Composable
private fun StartedLabel(label: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            "Started $label",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun UserBubble(text: String) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(start = 52.dp), horizontalArrangement = Arrangement.End) {
        Surface(
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 6.dp, bottomStart = 22.dp),
            color = scheme.primaryContainer,
            contentColor = scheme.onPrimaryContainer,
        ) {
            SelectionContainer {
                Text(text, Modifier.padding(horizontal = 16.dp, vertical = 11.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun CoachBubble(text: String, copied: Boolean, onCopy: () -> Unit, onShare: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(end = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CoachAvatar()
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Surface(
                shape = RoundedCornerShape(topStart = 6.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, Nafs.colors.cardBorder),
            ) {
                SelectionContainer { CoachRichText(text, Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                ReplyAction(
                    icon = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                    label = if (copied) "Copied" else "Copy",
                    description = if (copied) "Reply copied" else "Copy reply",
                    highlighted = copied,
                    onClick = onCopy,
                )
                ReplyAction(
                    icon = Icons.Rounded.Share,
                    label = "Share",
                    description = "Share reply",
                    highlighted = false,
                    onClick = onShare,
                )
            }
        }
    }
}

/** A quiet text action under a coach reply; 48 dp tall to keep the tap target comfortable. */
@Composable
private fun ReplyAction(icon: ImageVector, label: String, description: String, highlighted: Boolean, onClick: () -> Unit) {
    val tint = if (highlighted) Nafs.colors.success else MaterialTheme.colorScheme.onSurfaceVariant
    TextButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp).semantics {
            contentDescription = description
            liveRegion = LiveRegionMode.Polite
        },
        contentPadding = PaddingValues(horizontal = 10.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = tint),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/** Coach replies use light Markdown; headings, lists and bold render the way the report reader shows them. */
@Composable
private fun CoachRichText(text: String, modifier: Modifier = Modifier) {
    val lines = remember(text) {
        ReportText.lines(text.trim()).filterNot { it.kind == ReportTextKind.CODE && it.text.trim().startsWith("```") }
    }
    val body = MaterialTheme.typography.bodyLarge
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        lines.forEach { line ->
            when (line.kind) {
                ReportTextKind.SPACE -> Spacer(Modifier.height(6.dp))
                ReportTextKind.HEADING -> Text(styledLine(line), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.titleSmall)
                ReportTextKind.BULLET -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(line.marker.orEmpty(), style = body, color = Nafs.colors.coach)
                    Text(styledLine(line), style = body)
                }
                ReportTextKind.QUOTE -> Text(styledLine(line), style = body.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                ReportTextKind.CODE -> Text(line.text, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                ReportTextKind.PARAGRAPH -> Text(styledLine(line), style = body)
            }
        }
    }
}

private fun styledLine(line: ReportTextLine): AnnotatedString = buildAnnotatedString {
    ReportText.runs(line.text).forEach { run ->
        if (run.bold) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(run.text) }
        else append(run.text)
    }
}

@Composable
private fun TypingBubble() {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        CoachAvatar()
        Surface(
            shape = RoundedCornerShape(topStart = 6.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 22.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, Nafs.colors.cardBorder),
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TypingDots()
                Text("Coach is replying...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TypingDots() {
    val transition = rememberInfiniteTransition(label = "typing")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween<Float>(durationMillis = 1200, easing = LinearEasing)),
        label = "typingPhase",
    )
    val tint = Nafs.colors.coach
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { dot ->
            Box(Modifier.size(7.dp).graphicsLayer { alpha = dotAlpha(phase, dot) }.clip(CircleShape).background(tint))
        }
    }
}

private fun dotAlpha(phase: Float, dot: Int): Float {
    val local = (phase - dot * 0.18f + 1f) % 1f
    val wave = if (local < 0.5f) local * 2f else (1f - local) * 2f
    return 0.3f + 0.7f * wave
}

/** The retained message: what happened, and the ways forward the recovery rules allow. */
@Composable
private fun PendingMessage(state: CoachUiState, onRetry: () -> Unit, onReload: () -> Unit, onKeepDraft: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    if (state.isLoading) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp)
            Text("Checking for a saved reply...", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        }
        return
    }
    NafsCard(borderColor = Nafs.colors.warning.copy(alpha = 0.4f), shape = MaterialTheme.shapes.medium, contentPadding = PaddingValues(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = Nafs.colors.warning, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("Your message is kept for retry", style = MaterialTheme.typography.titleSmall)
                Text("Reply not confirmed", style = MaterialTheme.typography.labelSmall, color = Nafs.colors.warning)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            state.error ?: "Check for a saved reply or retry your message.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallTonalButton("Retry message", onRetry, Modifier.weight(1f), enabled = !state.requiresReload && !state.isOffline)
            SmallTonalButton("Check for reply", onReload, Modifier.weight(1f))
        }
        if (state.pendingReloaded && !state.isOffline) {
            TextButton(onClick = onKeepDraft, modifier = Modifier.padding(top = 4.dp)) {
                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Edit message", style = MaterialTheme.typography.labelLarge)
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
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PartialContextNote() {
    Row(
        Modifier.fillMaxWidth().padding(start = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        Text("Some records were unavailable for this reply.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FullConversationNote(canStartNew: Boolean, onNew: () -> Unit) {
    NafsCard(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, contentPadding = PaddingValues(14.dp)) {
        Text("This conversation is full. Start a new conversation to continue.", style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onNew, enabled = canStartNew) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("New conversation", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun PlanFromChatCard(enabled: Boolean, hint: String, onClick: () -> Unit) {
    NafsCard(
        modifier = Modifier.padding(top = 4.dp).alpha(if (enabled) 1f else 0.6f),
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            NafsIconBadge(Icons.Rounded.Healing, Nafs.colors.coach, size = 38.dp, iconSize = 20.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Build a plan from this saved chat", style = MaterialTheme.typography.titleSmall)
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─── Empty chat ──────────────────────────────────────────────────────────────

@Composable
private fun CoachWelcome(enabled: Boolean, onPrompt: (String) -> Unit) {
    val c = Nafs.colors
    val prompts = listOf(
        Icons.Rounded.Insights to "How did my week really go?",
        Icons.Rounded.Healing to "I slipped on a habit. Help me reset.",
        Icons.Rounded.Flag to "What should I focus on tomorrow?",
        Icons.Rounded.Lightbulb to "Break my biggest goal into steps.",
    )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = NafsSpace.screen, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NafsHeroSurface {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NafsIconBadge(Icons.Rounded.AutoAwesome, c.heroSky, size = 48.dp, iconSize = 24.dp)
                Spacer(Modifier.height(2.dp))
                NafsEyebrow("A conversation about your life", color = c.heroGold)
                Text("What is on your mind?", style = MaterialTheme.typography.headlineSmall, color = c.onHero)
                Text(
                    "Ask about your progress, talk through a setback, or explore your next step. " +
                        "Replies use your saved records and faith preference.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.onHeroMuted,
                )
            }
        }
        NafsEyebrow("Try asking", Modifier.padding(start = 4.dp, top = 10.dp))
        prompts.chunked(2).forEach { pair ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { (icon, prompt) ->
                    PromptTile(icon, prompt, enabled, Modifier.weight(1f).fillMaxHeight()) { onPrompt(prompt) }
                }
            }
        }
        Text(
            "A prompt only fills the message box. Nothing is sent until you press send.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

@Composable
private fun PromptTile(icon: ImageVector, prompt: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    NafsCard(modifier = modifier, onClick = onClick, enabled = enabled, shape = MaterialTheme.shapes.medium, contentPadding = PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NafsIconBadge(icon, Nafs.colors.coach, size = 34.dp, iconSize = 18.dp)
            Text(prompt, style = MaterialTheme.typography.titleSmall)
        }
    }
}

// ─── Composer ────────────────────────────────────────────────────────────────

@Composable
private fun CoachComposer(
    state: CoachUiState,
    enabled: Boolean,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    voiceAvailable: Boolean,
    onVoice: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val hint = if (state.draft.isNotBlank()) CoachRules.messageError(state.conversation?.messages.orEmpty(), state.draft) else null
    Surface(Modifier.fillMaxWidth(), color = scheme.surfaceContainerLow) {
        Column(Modifier.imePadding().navigationBarsPadding()) {
            NafsDivider(inset = 0.dp)
            Row(
                Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = if (state.draft.isNotBlank()) 2.dp else 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = state.draft,
                    onValueChange = onDraft,
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    placeholder = { Text(if (state.pending != null) "Waiting on your last message" else "Message your coach") },
                    maxLines = 5,
                    trailingIcon = if (voiceAvailable) {
                        {
                            IconButton(onClick = onVoice, enabled = enabled) {
                                Icon(Icons.Rounded.Mic, contentDescription = "Dictate a message")
                            }
                        }
                    } else {
                        null
                    },
                    shape = RoundedCornerShape(26.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = scheme.surfaceContainer,
                        unfocusedContainerColor = scheme.surfaceContainer,
                        disabledContainerColor = scheme.surfaceContainer,
                        unfocusedBorderColor = scheme.outlineVariant,
                        focusedBorderColor = scheme.primary,
                        disabledBorderColor = scheme.outlineVariant.copy(alpha = 0.5f),
                    ),
                )
                FilledIconButton(onClick = onSend, enabled = state.canSend, modifier = Modifier.padding(bottom = 2.dp).size(52.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send message")
                }
            }
            if (state.draft.isNotBlank()) {
                Row(Modifier.fillMaxWidth().padding(start = 28.dp, end = 76.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(hint.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = scheme.error)
                    Text("${state.draft.length}/4,000", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
            state.voiceMessage?.let { message ->
                Text(
                    message,
                    Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, bottom = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}
