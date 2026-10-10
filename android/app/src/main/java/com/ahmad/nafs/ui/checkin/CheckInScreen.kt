package com.ahmad.nafs.ui.checkin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmad.nafs.core.checkin.CheckInRules
import com.ahmad.nafs.core.checkin.CheckInTask
import com.ahmad.nafs.core.checkin.DailyCheckIn
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.ui.AccountDayRefreshEffects
import com.ahmad.nafs.ui.components.NafsBanner
import com.ahmad.nafs.ui.components.NafsBannerKind
import com.ahmad.nafs.ui.components.NafsCard
import com.ahmad.nafs.ui.components.NafsDivider
import com.ahmad.nafs.ui.components.NafsEyebrow
import com.ahmad.nafs.ui.components.NafsHeroSurface
import com.ahmad.nafs.ui.components.NafsIconBadge
import com.ahmad.nafs.ui.components.NafsLoadingState
import com.ahmad.nafs.ui.components.NafsPageHeader
import com.ahmad.nafs.ui.components.NafsPill
import com.ahmad.nafs.ui.components.NafsPrimaryButton
import com.ahmad.nafs.ui.components.NafsProgressRing
import com.ahmad.nafs.ui.components.NafsSecondaryButton
import com.ahmad.nafs.ui.components.NafsSectionTitle
import com.ahmad.nafs.ui.components.NafsSegmented
import com.ahmad.nafs.ui.components.NafsSpace
import com.ahmad.nafs.ui.components.NafsTextField
import com.ahmad.nafs.ui.theme.Nafs
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CheckInRoute(profile: UserProfile, viewModel: CheckInViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(profile.userId, profile.timezone) { viewModel.initialize(profile.userId, profile.timezone) }
    AccountDayRefreshEffects(profile.timezone) { viewModel.refresh() }
    CheckInScreen(
        profile = profile,
        state = state,
        actions = CheckInActions(
            onReload = { viewModel.refresh(force = true) },
            onShowEvening = viewModel::showEvening,
            onNewTaskChange = viewModel::setNewTask,
            onAddTask = viewModel::addTask,
            onTaskDone = viewModel::setTaskDone,
            onRemoveTask = viewModel::removeTask,
            onEveningTextChange = viewModel::setEveningText,
            onSave = viewModel::save,
            onGenerateVerdict = viewModel::generateVerdict,
        ),
    )
}

/** Everything the check-in can ask the ViewModel to do, so the screen renders from plain state. */
internal data class CheckInActions(
    val onReload: () -> Unit = {},
    val onShowEvening: (Boolean) -> Unit = {},
    val onNewTaskChange: (String) -> Unit = {},
    val onAddTask: () -> Unit = {},
    val onTaskDone: (Int, Boolean) -> Unit = { _, _ -> },
    val onRemoveTask: (Int) -> Unit = {},
    val onEveningTextChange: (String) -> Unit = {},
    val onSave: () -> Unit = {},
    val onGenerateVerdict: () -> Unit = {},
)

@Composable
internal fun CheckInScreen(profile: UserProfile, state: CheckInUiState, actions: CheckInActions) {
    if (state.isLoading && state.draft == null) {
        NafsLoadingState(Modifier.statusBarsPadding())
        return
    }
    val record = state.draft
    val canReload = !state.isLoading && !state.isSaving && !state.isGenerating && (!state.hasChanges || state.hasConflict)

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = NafsSpace.screen, end = NafsSpace.screen, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            NafsPageHeader(
                title = "Daily check-in",
                eyebrow = dayLabel(record?.date),
                subtitle = if (state.isEvening) "Look back on today with honesty." else "Decide what today is for.",
                actions = {
                    IconButton(onClick = actions.onReload, enabled = canReload) {
                        if (state.isLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.Refresh, contentDescription = "Reload check-in")
                    }
                },
            )
        }
        statusItems(state, actions)
        if (record != null) recordItems(profile, state, record, actions)
    }
}

private fun LazyListScope.statusItems(state: CheckInUiState, actions: CheckInActions) {
    if (state.isOffline) item {
        NafsBanner("Showing your cached check-in. Reconnect and reload to make changes.", kind = NafsBannerKind.OFFLINE)
    }
    if (state.hasConflict) item {
        NafsBanner(
            "Your entries are still shown. Reloading replaces them with the latest saved record.",
            kind = NafsBannerKind.WARNING,
            title = "This check-in changed on another device",
            actionLabel = "Reload",
            onAction = actions.onReload.takeIf { !state.isLoading },
        )
    }
    state.error?.let { message ->
        item {
            NafsBanner(
                message,
                kind = NafsBannerKind.ERROR,
                actionLabel = if (state.draft == null) "Retry" else null,
                onAction = actions.onReload.takeIf { state.draft == null },
            )
        }
    }
    state.message?.let { message -> item { NafsBanner(message, kind = NafsBannerKind.SUCCESS) } }
}

private fun LazyListScope.recordItems(profile: UserProfile, state: CheckInUiState, record: DailyCheckIn, actions: CheckInActions) {
    item {
        NafsSegmented(
            options = listOf("Morning plan", "Evening review"),
            selectedIndex = if (state.isEvening) 1 else 0,
            onSelect = { actions.onShowEvening(it == 1) },
        )
    }
    item { CheckInHero(record, state.isEvening, profile.timezone) }

    item {
        NafsSectionTitle(
            if (state.isEvening) "Review your priorities" else "Today's priorities",
            supporting = "Aim for 3–5 priorities. Your plan can hold up to seven.",
        )
    }
    item(key = "priorities") { PrioritiesCard(record.tasks, state, actions) }

    if (state.isEvening) {
        item { NafsSectionTitle("Evening notes", supporting = "What happened, what got in the way, and how it felt.") }
        item(key = "evening-notes") {
            NafsTextField(
                value = record.eveningText.orEmpty(),
                onValueChange = actions.onEveningTextChange,
                label = "How did your day go?",
                placeholder = "What did you do, what got in the way, and how did you feel?",
                singleLine = false,
                minLines = 5,
                enabled = state.canEdit,
                supportingText = "${record.eveningText?.length ?: 0}/10,000 characters",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        }
    }

    item {
        NafsPrimaryButton(
            text = if (state.isEvening) "Save evening review" else "Save morning plan",
            onClick = actions.onSave,
            enabled = state.canEdit && state.hasChanges,
            loading = state.isSaving,
            icon = Icons.Rounded.Check,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (state.isEvening) {
        item { NafsSectionTitle("Coach's review", supporting = "An honest read of today, written from your saved notes.") }
        record.aiVerdict?.takeIf(String::isNotBlank)?.let { verdict ->
            item(key = "verdict") { CoachNote(verdict, stale = state.generatedVersion != record.updatedAt || state.hasChanges) }
        }
        item { VerdictAction(state, actions.onGenerateVerdict) }
    }
}

// ─── Hero ────────────────────────────────────────────────────────────────────

@Composable
private fun CheckInHero(record: DailyCheckIn, evening: Boolean, timezone: String) {
    val c = Nafs.colors
    val done = record.tasks.count { it.done }
    val total = record.tasks.size
    NafsHeroSurface {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            NafsProgressRing(
                progress = if (total == 0) 0f else done.toFloat() / total,
                size = 96.dp,
                stroke = 9.dp,
                colors = listOf(c.heroGold, c.heroMint),
                trackColor = Color.White.copy(alpha = 0.12f),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$done/$total", style = MaterialTheme.typography.headlineSmall, color = c.onHero)
                    Text("done", style = MaterialTheme.typography.labelSmall, color = c.onHeroMuted)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                NafsEyebrow(if (evening) "Evening review" else "Morning plan", color = c.heroGold)
                Text(
                    if (evening) "Reflect on your day" else "Give your day direction",
                    style = MaterialTheme.typography.titleLarge,
                    color = c.onHero,
                )
                Text("$done of $total priorities completed", style = MaterialTheme.typography.bodyMedium, color = c.onHeroMuted)
                Text("Day follows $timezone", style = MaterialTheme.typography.labelSmall, color = c.onHeroMuted)
            }
        }
    }
}

// ─── Priorities ──────────────────────────────────────────────────────────────

@Composable
private fun PrioritiesCard(tasks: List<CheckInTask>, state: CheckInUiState, actions: CheckInActions) {
    val canAdd = state.canEdit && state.newTask.isNotBlank()
    NafsCard(contentPadding = PaddingValues(vertical = 6.dp)) {
        if (tasks.isEmpty()) {
            Text(
                "No priorities planned yet. Add the things that matter today.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
        tasks.forEachIndexed { index, task ->
            if (index > 0) NafsDivider()
            PriorityRow(
                task = task,
                enabled = state.canEdit,
                removable = !state.isEvening,
                onDone = { actions.onTaskDone(index, it) },
                onRemove = { actions.onRemoveTask(index) },
            )
        }
        if (!state.isEvening && tasks.size < CheckInRules.MAX_TASKS) {
            if (tasks.isNotEmpty()) NafsDivider()
            Box(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 6.dp)) {
                NafsTextField(
                    value = state.newTask,
                    onValueChange = actions.onNewTaskChange,
                    label = "Add a priority",
                    placeholder = "One clear, finishable thing",
                    enabled = state.canEdit,
                    supportingText = "${tasks.size} of ${CheckInRules.MAX_TASKS} planned",
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (canAdd) actions.onAddTask() }),
                    trailingIcon = {
                        IconButton(onClick = actions.onAddTask, enabled = canAdd) {
                            Icon(Icons.Rounded.Add, contentDescription = "Add to today's plan")
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PriorityRow(task: CheckInTask, enabled: Boolean, removable: Boolean, onDone: (Boolean) -> Unit, onRemove: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = task.done, enabled = enabled, role = Role.Checkbox, onValueChange = onDone)
            .padding(start = 16.dp, end = if (removable) 4.dp else 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CheckMark(task.done, enabled)
        Text(
            task.text,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = if (task.done) scheme.onSurfaceVariant else scheme.onSurface,
            textDecoration = if (task.done) TextDecoration.LineThrough else null,
        )
        if (removable) {
            IconButton(onClick = onRemove, enabled = enabled) {
                Icon(Icons.Rounded.Close, contentDescription = "Remove ${task.text}")
            }
        }
    }
}

@Composable
private fun CheckMark(checked: Boolean, enabled: Boolean) {
    val tint = Nafs.colors.reflect
    val scheme = MaterialTheme.colorScheme
    val ring = if (enabled) scheme.outline else scheme.outlineVariant
    Box(
        Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (checked) tint else Color.Transparent)
            .border(2.dp, if (checked) tint else ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Icons.Rounded.Check, contentDescription = null, tint = scheme.onSecondary, modifier = Modifier.size(16.dp))
    }
}

// ─── Coach review ────────────────────────────────────────────────────────────

/** The saved AI review, set like a note from the coach: accent rule, author line, then the words. */
@Composable
private fun CoachNote(verdict: String, stale: Boolean) {
    val c = Nafs.colors
    NafsCard(contentPadding = PaddingValues(0.dp)) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(c.coach))
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NafsIconBadge(Icons.Rounded.AutoAwesome, c.coach, size = 38.dp, iconSize = 19.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("AI evening review", style = MaterialTheme.typography.titleMedium)
                        Text("From your coach", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    NafsPill(if (stale) "Earlier" else "Up to date", color = if (stale) c.warning else c.success)
                }
                if (stale) {
                    Text(
                        "Previously saved review. New entries may need a fresh review.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(verdict, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun VerdictAction(state: CheckInUiState, onGenerate: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val alreadySaved = state.generatedVersion == state.saved?.updatedAt && state.generatedVersion != null
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NafsSecondaryButton(
            text = when {
                state.isGenerating -> "Generating review…"
                alreadySaved -> "AI review saved"
                else -> "Get AI evening review"
            },
            onClick = onGenerate,
            enabled = state.canGenerate,
            icon = Icons.Rounded.AutoAwesome,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.isGenerating) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), color = Nafs.colors.coach, strokeWidth = 2.dp)
                Text("Reading your notes and writing an honest review…", style = MaterialTheme.typography.bodySmall, color = muted)
            }
        } else if (state.hasChanges || state.saved?.eveningText.isNullOrBlank()) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = muted, modifier = Modifier.padding(top = 1.dp).size(15.dp))
                Text("Save your evening notes before requesting a review.", style = MaterialTheme.typography.bodySmall, color = muted)
            }
        }
    }
}

private fun dayLabel(date: String?): String =
    date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)) ?: "Today"
