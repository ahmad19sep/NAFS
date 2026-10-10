package com.ahmad.nafs.ui.goals

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmad.nafs.core.goals.Goal
import com.ahmad.nafs.core.goals.GoalMilestone
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.domain.goals.GoalCategory
import com.ahmad.nafs.domain.goals.GoalDates
import com.ahmad.nafs.domain.goals.GoalType
import com.ahmad.nafs.ui.AccountDayRefreshEffects
import com.ahmad.nafs.ui.components.NafsHeroCard
import com.ahmad.nafs.ui.components.NafsSectionTitle
import com.ahmad.nafs.ui.components.NafsStatusPill
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun GoalRoute(
    profile: UserProfile,
    createOnOpen: Boolean = false,
    viewModel: GoalViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(profile.userId, profile.timezone) {
        viewModel.initialize(profile.userId, profile.timezone)
        if (createOnOpen) viewModel.openCreate()
    }
    AccountDayRefreshEffects(profile.timezone, viewModel::refresh)
    GoalScreen(state, viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalScreen(state: GoalUiState, viewModel: GoalViewModel) {
    val visible = state.visibleGoals()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Goals")
                        Text(
                            "Turn direction into progress",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    IconButton(
                        onClick = viewModel::refresh,
                        enabled = !state.isLoading && !state.isRefreshing,
                    ) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh goals")
                        }
                    }
                    IconButton(onClick = viewModel::openCreate) {
                        Icon(Icons.Rounded.Add, contentDescription = "Create goal")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.loadError != null && state.goals.isEmpty() -> GoalLoadFailure(
                state.loadError,
                viewModel::refresh,
                Modifier.padding(padding),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.isOffline) item { GoalOfflineBanner() }
                state.actionError?.let { message ->
                    item { GoalErrorBanner(message, viewModel::dismissError) }
                }
                item { GoalTypeFilters(state.selectedType, viewModel::selectType) }
                item { GoalProgressHero(visible) }
                if (visible.isEmpty()) {
                    item { EmptyGoals(state.selectedType, viewModel::openCreate) }
                } else {
                    item {
                        NafsSectionTitle(
                            title = if (state.selectedType == null) "Your direction" else state.selectedType.displayName(),
                            supporting = "Milestones override estimates so progress stays honest.",
                        )
                    }
                    items(visible, key = Goal::id) { goal ->
                        GoalCard(
                            goal = goal,
                            today = state.today,
                            expanded = goal.id in state.expandedGoalIds,
                            pending = goal.id in state.pendingGoalIds,
                            pendingMilestones = state.pendingMilestoneIds,
                            onToggleExpanded = { viewModel.toggleExpanded(goal.id) },
                            onEdit = { viewModel.openEdit(goal) },
                            onDelete = { viewModel.requestDelete(goal) },
                            onUpdateProgress = { viewModel.openProgress(goal) },
                            onSetMilestoneDone = { milestone, done ->
                                viewModel.setMilestoneDone(goal, milestone.id, done)
                            },
                        )
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }

    state.editor?.let { GoalEditorDialog(it, state, viewModel) }
    state.progressGoalId?.let { goalId ->
        state.goals.firstOrNull { it.id == goalId }?.let { goal ->
            GoalProgressDialog(goal, state, viewModel)
        }
    }
    state.deleteCandidate?.let { goal ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("Delete ${goal.title}?") },
            text = { Text("This permanently removes the goal and its milestones. This cannot be undone.") },
            confirmButton = {
                Button(onClick = viewModel::confirmDelete) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun GoalTypeFilters(selected: GoalType?, onSelect: (GoalType?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("All") })
        GoalType.entries.forEach { type ->
            FilterChip(
                selected = selected == type,
                onClick = { onSelect(type) },
                label = { Text(type.displayName()) },
            )
        }
    }
}

@Composable
private fun GoalProgressHero(goals: List<Goal>) {
    val average = if (goals.isEmpty()) 0 else goals.sumOf(Goal::progressPercent) / goals.size
    val complete = goals.count { it.progressPercent >= 100 }
    NafsHeroCard(
        eyebrow = "Direction with evidence",
        title = "$average% overall progress",
        body = "$complete complete · ${goals.size - complete} still moving",
    ) {
        LinearProgressIndicator(
            progress = { average / 100f },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f),
        )
    }
}

@Composable
private fun GoalCard(
    goal: Goal,
    today: LocalDate,
    expanded: Boolean,
    pending: Boolean,
    pendingMilestones: Set<String>,
    onToggleExpanded: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onUpdateProgress: () -> Unit,
    onSetMilestoneDone: (GoalMilestone, Boolean) -> Unit,
) {
    val days = GoalDates.daysRemaining(goal.deadline, today)
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (goal.progressPercent >= 100) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(goal.emoji, style = MaterialTheme.typography.headlineMedium)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NafsStatusPill(goal.type.displayName())
                        goal.category?.let { NafsStatusPill(it.displayName(), accent = false) }
                    }
                    Text(
                        goal.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 7.dp),
                    )
                    goal.description?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (expanded) 8 else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (pending) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        "${goal.progressPercent}%",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            LinearProgressIndicator(
                progress = { goal.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = deadlineColor(days),
                )
                Text(
                    deadlineText(goal.deadline, days),
                    modifier = Modifier.weight(1f).padding(start = 7.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = deadlineColor(days),
                )
                IconButton(onClick = onToggleExpanded) {
                    Icon(
                        if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "Collapse goal" else "Expand goal",
                    )
                }
            }

            if (expanded) {
                HorizontalDivider()
                if (goal.targetValue != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Numeric progress", style = MaterialTheme.typography.labelLarge)
                            Text(
                                "${formatNumber(goal.currentValue ?: 0.0)} / ${formatNumber(goal.targetValue)} ${goal.unit.orEmpty()}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        OutlinedButton(onClick = onUpdateProgress, enabled = !pending) { Text("Update") }
                    }
                }

                if (goal.milestones.isNotEmpty()) {
                    Text(
                        "Milestones · ${goal.milestones.count(GoalMilestone::done)}/${goal.milestones.size}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    goal.milestones.forEach { milestone ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (milestone.id in pendingMilestones) {
                                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                }
                            } else {
                                Checkbox(
                                    checked = milestone.done,
                                    onCheckedChange = { onSetMilestoneDone(milestone, it) },
                                )
                            }
                            Text(
                                milestone.title,
                                modifier = Modifier.weight(1f),
                                textDecoration = if (milestone.done) TextDecoration.LineThrough else null,
                                color = if (milestone.done) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onEdit, enabled = !pending) {
                        Icon(Icons.Rounded.Edit, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Edit")
                    }
                    TextButton(onClick = onDelete, enabled = !pending) {
                        Icon(Icons.Rounded.Delete, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Delete")
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalEditorDialog(
    draft: GoalEditorDraft,
    state: GoalUiState,
    viewModel: GoalViewModel,
) {
    val numberKeyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    AlertDialog(
        onDismissRequest = viewModel::closeEditor,
        title = { Text(if (draft.goalId == null) "Create goal" else "Edit goal") },
        text = {
            Column(
                Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        draft.emoji,
                        viewModel::setEmoji,
                        label = { Text("Emoji") },
                        modifier = Modifier.width(84.dp),
                        singleLine = true,
                        enabled = !state.isSaving,
                    )
                    OutlinedTextField(
                        draft.title,
                        viewModel::setTitle,
                        label = { Text("Goal title") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        enabled = !state.isSaving,
                    )
                }
                OutlinedTextField(
                    draft.description,
                    viewModel::setDescription,
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    enabled = !state.isSaving,
                )

                GoalEditorLabel("Horizon")
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    GoalType.entries.forEach { type ->
                        FilterChip(
                            selected = draft.type == type,
                            onClick = { viewModel.setType(type) },
                            label = { Text(type.displayName()) },
                            enabled = !state.isSaving,
                        )
                    }
                }

                GoalEditorLabel("Category")
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    FilterChip(
                        selected = draft.category == null,
                        onClick = { viewModel.setCategory(null) },
                        label = { Text("General") },
                        enabled = !state.isSaving,
                    )
                    GoalCategory.entries.forEach { category ->
                        FilterChip(
                            selected = draft.category == category,
                            onClick = { viewModel.setCategory(category) },
                            label = { Text(category.displayName()) },
                            enabled = !state.isSaving,
                        )
                    }
                }

                OutlinedTextField(
                    draft.deadline,
                    viewModel::setDeadline,
                    label = { Text("Deadline · YYYY-MM-DD") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.isSaving,
                )

                if (state.habits.isNotEmpty()) {
                    Text("Linked habits", style = MaterialTheme.typography.titleMedium)
                    state.habits.forEach { habit ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = habit.id in draft.linkedHabitIds, onCheckedChange = { viewModel.toggleLinkedHabit(habit.id) }, enabled = !state.isSaving)
                            Text("${habit.emoji} ${habit.name}")
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Numeric target", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Track an amount such as 10 books or 50,000 PKR.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = draft.hasNumericTarget,
                        onCheckedChange = viewModel::setHasNumericTarget,
                        enabled = !state.isSaving,
                    )
                }
                if (draft.hasNumericTarget) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            draft.currentValue,
                            viewModel::setCurrentValue,
                            label = { Text("Current") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = numberKeyboard,
                            singleLine = true,
                            enabled = !state.isSaving,
                        )
                        OutlinedTextField(
                            draft.targetValue,
                            viewModel::setTargetValue,
                            label = { Text("Target") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = numberKeyboard,
                            singleLine = true,
                            enabled = !state.isSaving,
                        )
                        OutlinedTextField(
                            draft.unit,
                            viewModel::setUnit,
                            label = { Text("Unit") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            enabled = !state.isSaving,
                        )
                    }
                }

                if (draft.goalId == null) {
                    GoalEditorLabel("Milestones (optional)")
                    Text(
                        "When milestones exist, their completion ratio becomes the true goal progress.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    draft.milestones.forEachIndexed { index, milestone ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                milestone,
                                { viewModel.setMilestone(index, it) },
                                label = { Text("Milestone ${index + 1}") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                enabled = !state.isSaving,
                            )
                            if (draft.milestones.size > 1) {
                                IconButton(
                                    onClick = { viewModel.removeMilestone(index) },
                                    enabled = !state.isSaving,
                                ) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Remove milestone")
                                }
                            }
                        }
                    }
                    TextButton(
                        onClick = viewModel::addMilestone,
                        enabled = !state.isSaving && draft.milestones.size < 20,
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add milestone")
                    }
                } else if (state.goals.firstOrNull { it.id == draft.goalId }?.milestones?.isNotEmpty() == true) {
                    Text(
                        "Existing milestones remain attached. Complete them from the expanded goal card.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                state.editorError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = viewModel::save, enabled = !state.isSaving) {
                if (state.isSaving) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (draft.goalId == null) "Create" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::closeEditor, enabled = !state.isSaving) { Text("Cancel") }
        },
    )
}

@Composable
private fun GoalProgressDialog(goal: Goal, state: GoalUiState, viewModel: GoalViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::closeProgress,
        icon = { Icon(Icons.Rounded.EmojiEvents, contentDescription = null) },
        title = { Text("Update ${goal.title}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    state.progressAmount,
                    viewModel::setProgressAmount,
                    label = { Text("Current ${goal.unit.orEmpty()}") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    enabled = goal.id !in state.pendingGoalIds,
                )
                Text(
                    "Target: ${formatNumber(goal.targetValue ?: 0.0)} ${goal.unit.orEmpty()}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (goal.milestones.isNotEmpty()) {
                    Text(
                        "Milestones still determine the displayed percentage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                state.progressError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = viewModel::saveProgress,
                enabled = goal.id !in state.pendingGoalIds,
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(
                onClick = viewModel::closeProgress,
                enabled = goal.id !in state.pendingGoalIds,
            ) { Text("Cancel") }
        },
    )
}

@Composable
private fun GoalEditorLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun GoalOfflineBanner() {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.CloudOff, contentDescription = null)
            Text("Showing cached goals. Changes need a connection.")
        }
    }
}

@Composable
private fun GoalErrorBanner(message: String, onDismiss: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

@Composable
private fun EmptyGoals(selected: GoalType?, onCreate: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (selected == null) "Name the direction" else "No ${selected.displayName().lowercase()} goals yet",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                "A useful goal has a horizon, evidence, and a next milestone.",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Button(onClick = onCreate) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Create goal")
            }
        }
    }
}

@Composable
private fun GoalLoadFailure(message: String, retry: () -> Unit, modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message)
            Button(onClick = retry, modifier = Modifier.padding(top = 12.dp)) { Text("Retry") }
        }
    }
}

@Composable
private fun deadlineColor(days: Long?) = when {
    days != null && days < 0 -> MaterialTheme.colorScheme.error
    days != null && days <= 7 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun deadlineText(deadline: LocalDate?, days: Long?): String = when {
    deadline == null -> "No deadline"
    days == null -> deadline.toString()
    days < 0 -> "${deadline.format(DISPLAY_DATE)} · ${-days} days overdue"
    days == 0L -> "${deadline.format(DISPLAY_DATE)} · due today"
    days == 1L -> "${deadline.format(DISPLAY_DATE)} · 1 day left"
    else -> "${deadline.format(DISPLAY_DATE)} · $days days left"
}

private fun GoalType.displayName(): String = when (this) {
    GoalType.WEEKLY -> "Weekly"
    GoalType.MONTHLY -> "Monthly"
    GoalType.QUARTERLY -> "Quarterly"
    GoalType.YEARLY -> "Yearly"
    GoalType.LONG_TERM -> "Long-term"
}

private fun GoalCategory.displayName(): String = when (this) {
    GoalCategory.DEEN -> "Deen"
    GoalCategory.CAREER -> "Career"
    GoalCategory.LEARNING -> "Learning"
    GoalCategory.HEALTH -> "Health"
    GoalCategory.FINANCIAL -> "Financial"
    GoalCategory.FAMILY -> "Family"
    GoalCategory.PERSONAL -> "Personal"
}

private fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

private val DISPLAY_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
