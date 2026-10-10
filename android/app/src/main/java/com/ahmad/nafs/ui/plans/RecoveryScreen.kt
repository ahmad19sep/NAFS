package com.ahmad.nafs.ui.plans

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Healing
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmad.nafs.core.plans.PlanPreview
import com.ahmad.nafs.core.plans.PlanProposal
import com.ahmad.nafs.core.plans.PlanStepState
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.core.time.AccountDate
import com.ahmad.nafs.ui.AccountDayRefreshEffects
import com.ahmad.nafs.ui.components.NafsBanner
import com.ahmad.nafs.ui.components.NafsBannerKind
import com.ahmad.nafs.ui.components.NafsCard
import com.ahmad.nafs.ui.components.NafsDivider
import com.ahmad.nafs.ui.components.NafsEmojiBadge
import com.ahmad.nafs.ui.components.NafsEyebrow
import com.ahmad.nafs.ui.components.NafsHeroSurface
import com.ahmad.nafs.ui.components.NafsIconBadge
import com.ahmad.nafs.ui.components.NafsPill
import com.ahmad.nafs.ui.components.NafsPrimaryButton
import com.ahmad.nafs.ui.components.NafsSecondaryButton
import com.ahmad.nafs.ui.components.NafsSectionTitle
import com.ahmad.nafs.ui.components.NafsSpace
import com.ahmad.nafs.ui.components.NafsTextField
import com.ahmad.nafs.ui.components.NafsTopBar
import com.ahmad.nafs.ui.theme.Nafs

@Composable
fun RecoveryRoute(profile: UserProfile, conversationId: String?, version: String?, onBack: () -> Unit,
    onOpenFeature: (String) -> Unit, viewModel: RecoveryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(profile.userId, profile.timezone, conversationId, version) {
        viewModel.initialize(profile.userId, AccountDate.today(profile.timezone), profile.timezone, conversationId, version)
    }
    AccountDayRefreshEffects(profile.timezone) { viewModel.refresh(AccountDate.today(profile.timezone)) }
    val idle = !state.isLoading && !state.isBusy
    val back = { if (idle) viewModel.flushDraft(onBack) }
    BackHandler { back() }
    RecoveryScreen(
        state = state,
        accountKey = profile.userId,
        fromChatAvailable = conversationId != null && version != null,
        actions = RecoveryActions(
            onBack = back,
            onReload = viewModel::reload,
            onEditDraft = viewModel::editDraft,
            onGenerate = { viewModel.generate() },
            onGenerateFromChat = { viewModel.generate(fromChat = true) },
            onSelect = viewModel::select,
            onApply = viewModel::apply,
            onDiscard = viewModel::discard,
            onOpenFeature = onOpenFeature,
        ),
    )
}

/** Everything the recovery plan screen can ask its ViewModel to do. */
internal data class RecoveryActions(
    val onBack: () -> Unit = {},
    val onReload: () -> Unit = {},
    val onEditDraft: (String) -> Unit = {},
    val onGenerate: () -> Unit = {},
    val onGenerateFromChat: () -> Unit = {},
    val onSelect: (index: Int, selected: Boolean) -> Unit = { _, _ -> },
    val onApply: () -> Unit = {},
    val onDiscard: () -> Unit = {},
    val onOpenFeature: (String) -> Unit = {},
)

@Composable
internal fun RecoveryScreen(state: RecoveryUiState, accountKey: String, fromChatAvailable: Boolean, actions: RecoveryActions) {
    var confirmDiscard by rememberSaveable(accountKey) { mutableStateOf(false) }
    val idle = !state.isLoading && !state.isBusy
    val preview = state.preview
    val retained = state.snapshot?.pending != null
    val showProblem = !state.isLoading && preview == null
    val hasBottomBar = preview != null || showProblem

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        NafsTopBar(
            title = "Recovery plan",
            onBack = actions.onBack,
            subtitle = if (preview != null) "Review the steps, then add the ones you want" else "A way back from a slip",
            actions = {
                IconButton(onClick = actions.onReload, enabled = idle) { Icon(Icons.Rounded.Refresh, "Check saved steps") }
            },
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().then(if (hasBottomBar) Modifier else Modifier.navigationBarsPadding()),
            contentPadding = PaddingValues(start = NafsSpace.screen, end = NafsSpace.screen, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(NafsSpace.item),
        ) {
            if (state.isOffline) item(key = "offline") {
                NafsBanner("Showing your saved plan. Reconnect and reload to generate or add steps.", kind = NafsBannerKind.OFFLINE)
            }
            state.error?.let { error ->
                item(key = "error") { NafsBanner(error, kind = if (retained) NafsBannerKind.WARNING else NafsBannerKind.ERROR) }
            }
            state.message?.let { message -> item(key = "message") { NafsBanner(message, kind = NafsBannerKind.INFO) } }
            if (state.isLoading || state.isBusy) item(key = "busy") {
                BusyRow(if (state.isLoading) "Checking your saved plan..." else "Saving your progress...")
            }

            if (showProblem) {
                item(key = "problem") { ProblemCard(state, actions.onEditDraft) }
                if (fromChatAvailable && !retained) item(key = "from-chat") {
                    NafsCard(contentPadding = PaddingValues(16.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            NafsIconBadge(Icons.AutoMirrored.Rounded.Chat, Nafs.colors.coach, size = 36.dp, iconSize = 18.dp)
                            Text("Use the saved conversation you opened from Coach. Any unsent message stays in Coach.",
                                Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(12.dp))
                        NafsSecondaryButton(
                            "Preview plan from saved chat",
                            onClick = actions.onGenerateFromChat,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = idle && !state.isOffline,
                        )
                    }
                }
                if (retained) item(key = "change-problem") {
                    TextButton(onClick = { confirmDiscard = true }, enabled = idle) { Text("Change the problem") }
                }
            }

            if (preview != null) {
                item(key = "summary") { PlanSummary(preview) }
                item(key = "steps-title") {
                    NafsSectionTitle("Suggested steps", Modifier.padding(top = 8.dp),
                        supporting = "Choose what to add. Nothing is created until you confirm.")
                }
                itemsIndexed(preview.response.recovery.steps, key = { index, _ -> preview.steps[index].id }) { index, proposal ->
                    StepCard(
                        proposal = proposal,
                        step = preview.steps[index],
                        idle = idle,
                        onToggle = { selected -> actions.onSelect(index, selected) },
                        onOpenFeature = actions.onOpenFeature,
                    )
                }
                item(key = "plan-actions") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = actions.onReload, enabled = idle) { Text("Check saved steps") }
                        TextButton(onClick = { confirmDiscard = true }, enabled = idle) { Text("Start another plan") }
                    }
                }
            }
        }
        if (hasBottomBar) {
            BottomActionBar {
                if (preview != null) {
                    NafsPrimaryButton(
                        "Add ${state.toAdd} selected ${if (state.toAdd == 1) "step" else "steps"}",
                        onClick = actions.onApply,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.canApply,
                        loading = state.isBusy,
                        icon = Icons.Rounded.Add,
                    )
                } else {
                    NafsPrimaryButton(
                        if (retained) "Retry retained request" else "Preview a plan",
                        onClick = actions.onGenerate,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.canGenerate,
                        loading = state.isBusy,
                        icon = Icons.Rounded.AutoAwesome,
                    )
                }
            }
        }
    }

    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false }, title = { Text("Start another plan?") },
        icon = { Icon(Icons.Rounded.Healing, contentDescription = null) },
        text = { Text(if (state.preview?.hasUnconfirmed == true)
            "Some items may already be saved. Reload to check them before starting another plan. Clearing this preview keeps any items already added."
            else "This clears the saved preview or planning request. Any items already added stay in their features.") },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; actions.onDiscard() }, enabled = idle) { Text("Clear preview") } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep plan") } })
}

@Composable
private fun ProblemCard(state: RecoveryUiState, onEditDraft: (String) -> Unit) {
    val draft = state.snapshot?.draft.orEmpty()
    NafsCard(contentPadding = PaddingValues(18.dp)) {
        NafsIconBadge(Icons.Rounded.Healing, Nafs.colors.coach)
        Spacer(Modifier.height(14.dp))
        Text("What is getting in your way?", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "Describe a setback. Coach will suggest up to four steps for you to review before adding them.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        NafsTextField(
            value = draft,
            onValueChange = onEditDraft,
            label = "Your problem",
            singleLine = false,
            minLines = 4,
            maxLines = 8,
            enabled = state.editable,
            supportingText = "${draft.trim().length}/500 · " + if (state.draftSaved) "Saved on this device" else "Saving draft...",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
    }
}

@Composable
private fun PlanSummary(preview: PlanPreview) {
    val c = Nafs.colors
    val response = preview.response
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NafsHeroSurface {
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NafsEyebrow("Your recovery plan", color = c.heroGold)
                    Text(response.recovery.problem, style = MaterialTheme.typography.titleLarge, color = c.onHero)
                    Text(response.recovery.approach, style = MaterialTheme.typography.bodyMedium, color = c.onHeroMuted)
                }
            }
        }
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PlanNote("Based on records for ${response.sourceDate}. New tasks and challenges start on your account day when first added.")
            if (response.fellBack) PlanNote("An alternate AI provider prepared this plan.")
            if (response.contextPartial) PlanNote("Some records were unavailable. Coach used the records it could load.")
            if (response.dropped > 0) PlanNote("${response.dropped} unusable steps were left out.")
        }
    }
}

@Composable
private fun PlanNote(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 1.dp).size(14.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Label, icon and accent for each kind of suggested step. */
private data class KindStyle(val label: String, val icon: ImageVector, val tint: Color)

@Composable
private fun kindStyle(kind: String): KindStyle {
    val label = kind.replaceFirstChar(Char::uppercase)
    return when (kind) {
        "task" -> KindStyle(label, Icons.Rounded.TaskAlt, Nafs.colors.tasks)
        "habit" -> KindStyle(label, Icons.Rounded.Autorenew, Nafs.colors.habits)
        else -> KindStyle(label, Icons.Rounded.Whatshot, Nafs.colors.challenges)
    }
}

private fun featureRoute(kind: String): String = when (kind) {
    "task" -> "tasks"
    "habit" -> "habits"
    else -> "challenges"
}

@Composable
private fun StepCard(
    proposal: PlanProposal,
    step: PlanStepState,
    idle: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpenFeature: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val kind = kindStyle(proposal.kind)
    val border = when {
        step.added -> Nafs.colors.success.copy(alpha = 0.45f)
        step.selected -> scheme.primary.copy(alpha = 0.55f)
        else -> Nafs.colors.cardBorder
    }
    NafsCard(borderColor = border, contentPadding = PaddingValues(0.dp)) {
        Row(
            Modifier.fillMaxWidth()
                .toggleable(value = step.selected, enabled = idle && !step.added, role = Role.Checkbox, onValueChange = onToggle)
                .padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            NafsEmojiBadge(proposal.emoji, kind.tint)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(proposal.title, style = MaterialTheme.typography.titleMedium)
                PillFlow {
                    NafsPill(kind.label, color = kind.tint, icon = kind.icon)
                    proposal.description().split(" · ").filter { it.isNotBlank() }.forEach { detail ->
                        NafsPill(detail, color = scheme.onSurfaceVariant)
                    }
                    if (step.added) NafsPill("Added", color = Nafs.colors.success, icon = Icons.Rounded.CheckCircle)
                }
            }
            Checkbox(checked = step.selected, onCheckedChange = null, enabled = idle && !step.added)
        }
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(scheme.surfaceContainerHigh).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                NafsEyebrow("Why this step")
                SelectionContainer { Text(proposal.reason, style = MaterialTheme.typography.bodyMedium) }
            }
            proposal.task?.note?.takeIf { it.isNotBlank() }?.let { note ->
                Text(note, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            step.applyDate?.let { date -> NafsPill("First add date: $date", color = scheme.onSurfaceVariant, icon = Icons.Rounded.CalendarMonth) }
            step.error?.let { message ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = scheme.error, modifier = Modifier.size(16.dp))
                    Text(message, style = MaterialTheme.typography.bodySmall, color = scheme.error)
                }
            }
            if (step.added) {
                val route = featureRoute(proposal.kind)
                TextButton(onClick = { onOpenFeature(route) }, enabled = idle) {
                    Text("Open $route", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun BusyRow(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Nafs.colors.coach)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun BottomActionBar(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.imePadding().navigationBarsPadding()) {
            NafsDivider(inset = 0.dp)
            Column(
                Modifier.padding(horizontal = NafsSpace.screen, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

/** Wraps small labels onto as many lines as they need. */
@Composable
private fun PillFlow(modifier: Modifier = Modifier, spacing: Dp = 6.dp, content: @Composable () -> Unit) {
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
