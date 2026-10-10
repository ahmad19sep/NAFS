package com.ahmad.nafs.ui.goals

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmad.nafs.core.goals.Goal
import com.ahmad.nafs.core.goals.GoalMilestone
import com.ahmad.nafs.core.habits.Habit
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.domain.goals.GoalCategory
import com.ahmad.nafs.domain.goals.GoalDates
import com.ahmad.nafs.domain.goals.GoalType
import com.ahmad.nafs.ui.AccountDayRefreshEffects
import com.ahmad.nafs.ui.components.NafsBanner
import com.ahmad.nafs.ui.components.NafsBannerKind
import com.ahmad.nafs.ui.components.NafsCard
import com.ahmad.nafs.ui.components.NafsChoiceChip
import com.ahmad.nafs.ui.components.NafsDivider
import com.ahmad.nafs.ui.components.NafsEmojiBadge
import com.ahmad.nafs.ui.components.NafsEmptyState
import com.ahmad.nafs.ui.components.NafsErrorState
import com.ahmad.nafs.ui.components.NafsEyebrow
import com.ahmad.nafs.ui.components.NafsHeroSurface
import com.ahmad.nafs.ui.components.NafsIconBadge
import com.ahmad.nafs.ui.components.NafsPageHeader
import com.ahmad.nafs.ui.components.NafsPill
import com.ahmad.nafs.ui.components.NafsPrimaryButton
import com.ahmad.nafs.ui.components.NafsProgressBar
import com.ahmad.nafs.ui.components.NafsProgressRing
import com.ahmad.nafs.ui.components.NafsSecondaryButton
import com.ahmad.nafs.ui.components.NafsSectionTitle
import com.ahmad.nafs.ui.components.NafsSpace
import com.ahmad.nafs.ui.components.NafsStat
import com.ahmad.nafs.ui.components.NafsTextField
import com.ahmad.nafs.ui.theme.Nafs
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
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
    val actions = remember(viewModel) {
        GoalActions(
            onRefresh = viewModel::refresh,
            onCreate = viewModel::openCreate,
            onSelectType = viewModel::selectType,
            onToggleExpanded = viewModel::toggleExpanded,
            onEdit = viewModel::openEdit,
            onRequestDelete = viewModel::requestDelete,
            onConfirmDelete = viewModel::confirmDelete,
            onCancelDelete = viewModel::cancelDelete,
            onOpenProgress = viewModel::openProgress,
            onProgressAmountChange = viewModel::setProgressAmount,
            onSaveProgress = viewModel::saveProgress,
            onCloseProgress = viewModel::closeProgress,
            onSetMilestoneDone = viewModel::setMilestoneDone,
            onDismissError = viewModel::dismissError,
            editor = GoalEditorActions(
                onClose = viewModel::closeEditor,
                onSave = viewModel::save,
                onTitleChange = viewModel::setTitle,
                onEmojiChange = viewModel::setEmoji,
                onDescriptionChange = viewModel::setDescription,
                onTypeChange = viewModel::setType,
                onCategoryChange = viewModel::setCategory,
                onDeadlineChange = viewModel::setDeadline,
                onNumericTargetChange = viewModel::setHasNumericTarget,
                onTargetValueChange = viewModel::setTargetValue,
                onCurrentValueChange = viewModel::setCurrentValue,
                onUnitChange = viewModel::setUnit,
                onMilestoneChange = viewModel::setMilestone,
                onAddMilestone = viewModel::addMilestone,
                onRemoveMilestone = viewModel::removeMilestone,
                onToggleLinkedHabit = viewModel::toggleLinkedHabit,
            ),
        )
    }
    GoalScreen(state, actions)
}

/** Everything the Goals screen can ask for, so the screen stays a pure function of its state. */
internal data class GoalActions(
    val onRefresh: () -> Unit = {},
    val onCreate: () -> Unit = {},
    val onSelectType: (GoalType?) -> Unit = {},
    val onToggleExpanded: (String) -> Unit = {},
    val onEdit: (Goal) -> Unit = {},
    val onRequestDelete: (Goal) -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onCancelDelete: () -> Unit = {},
    val onOpenProgress: (Goal) -> Unit = {},
    val onProgressAmountChange: (String) -> Unit = {},
    val onSaveProgress: () -> Unit = {},
    val onCloseProgress: () -> Unit = {},
    val onSetMilestoneDone: (Goal, String, Boolean) -> Unit = { _, _, _ -> },
    val onDismissError: () -> Unit = {},
    val editor: GoalEditorActions = GoalEditorActions(),
)

/** Field-level callbacks for the goal editor sheet. */
internal data class GoalEditorActions(
    val onClose: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onTitleChange: (String) -> Unit = {},
    val onEmojiChange: (String) -> Unit = {},
    val onDescriptionChange: (String) -> Unit = {},
    val onTypeChange: (GoalType) -> Unit = {},
    val onCategoryChange: (GoalCategory?) -> Unit = {},
    val onDeadlineChange: (String) -> Unit = {},
    val onNumericTargetChange: (Boolean) -> Unit = {},
    val onTargetValueChange: (String) -> Unit = {},
    val onCurrentValueChange: (String) -> Unit = {},
    val onUnitChange: (String) -> Unit = {},
    val onMilestoneChange: (Int, String) -> Unit = { _, _ -> },
    val onAddMilestone: () -> Unit = {},
    val onRemoveMilestone: (Int) -> Unit = {},
    val onToggleLinkedHabit: (String) -> Unit = {},
)

@Composable
internal fun GoalScreen(state: GoalUiState, actions: GoalActions) {
    when {
        state.isLoading -> Column(Modifier.fillMaxSize().statusBarsPadding()) {
            GoalHeader(state, actions.onRefresh, actions.onCreate, Modifier.gutter())
            GoalSkeleton()
        }

        state.loadError != null && state.goals.isEmpty() -> Column(Modifier.fillMaxSize().statusBarsPadding()) {
            GoalHeader(state, actions.onRefresh, actions.onCreate, Modifier.gutter())
            NafsErrorState(
                title = "Goals didn't load",
                message = state.loadError,
                onRetry = actions.onRefresh,
                modifier = Modifier.weight(1f),
            )
        }

        else -> GoalList(state, actions)
    }

    state.editor?.let { draft -> GoalEditorSheet(draft, state, actions.editor) }
    state.progressGoalId?.let { goalId ->
        state.goals.firstOrNull { it.id == goalId }?.let { goal ->
            GoalProgressDialog(
                goal = goal,
                amount = state.progressAmount,
                error = state.progressError,
                pending = goal.id in state.pendingGoalIds,
                onAmountChange = actions.onProgressAmountChange,
                onSave = actions.onSaveProgress,
                onDismiss = actions.onCloseProgress,
            )
        }
    }
    state.deleteCandidate?.let { candidate ->
        GoalDeleteDialog(candidate, onConfirm = actions.onConfirmDelete, onDismiss = actions.onCancelDelete)
    }
}

@Composable
private fun GoalList(state: GoalUiState, actions: GoalActions) {
    val visible = state.visibleGoals()
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "goals-header") { GoalHeader(state, actions.onRefresh, actions.onCreate, Modifier.gutter()) }

        if (state.isOffline) item(key = "goals-offline") {
            NafsBanner(
                message = "Showing cached goals. Changes need a connection.",
                modifier = Modifier.gutter(),
                kind = NafsBannerKind.OFFLINE,
                actionLabel = "Retry",
                onAction = actions.onRefresh,
            )
        }
        state.loadError?.let { message ->
            item(key = "goals-load-error") {
                NafsBanner(
                    message = message,
                    modifier = Modifier.gutter(),
                    kind = NafsBannerKind.WARNING,
                    title = "Couldn't refresh goals",
                    onDismiss = actions.onDismissError,
                )
            }
        }
        state.actionError?.let { message ->
            item(key = "goals-action-error") {
                NafsBanner(message = message, modifier = Modifier.gutter(), kind = NafsBannerKind.ERROR, onDismiss = actions.onDismissError)
            }
        }

        if (state.goals.isNotEmpty()) item(key = "goals-hero") { GoalHero(state.goals, state.today, Modifier.gutter()) }
        if (state.goals.isNotEmpty() || state.selectedType != null) item(key = "goals-filters") {
            GoalTypeFilters(state.selectedType, state.goals, actions.onSelectType)
        }

        if (visible.isEmpty()) {
            item(key = "goals-empty") { EmptyGoals(state.selectedType, actions.onCreate, Modifier.gutter()) }
        } else {
            item(key = "goals-section") {
                NafsSectionTitle(
                    title = state.selectedType?.let { "${it.displayName()} goals" } ?: "Your direction",
                    modifier = Modifier.gutter().padding(top = 4.dp),
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
                    linkedHabits = state.habits.filter { habit -> habit.id in goal.linkedHabitIds },
                    onToggleExpanded = { actions.onToggleExpanded(goal.id) },
                    onEdit = { actions.onEdit(goal) },
                    onDelete = { actions.onRequestDelete(goal) },
                    onUpdateProgress = { actions.onOpenProgress(goal) },
                    onSetMilestoneDone = { milestone, done -> actions.onSetMilestoneDone(goal, milestone.id, done) },
                    modifier = Modifier.gutter(),
                )
            }
        }
    }
}

// ─── Header, hero and filters ────────────────────────────────────────────────

@Composable
private fun GoalHeader(state: GoalUiState, onRefresh: () -> Unit, onCreate: () -> Unit, modifier: Modifier = Modifier) {
    NafsPageHeader(
        title = "Goals",
        modifier = modifier,
        eyebrow = "Your horizons",
        subtitle = goalSummary(state),
        actions = {
            IconButton(onClick = onRefresh, enabled = !state.isLoading && !state.isRefreshing) {
                if (state.isRefreshing) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh goals", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HeaderAddButton(description = "Create goal", onClick = onCreate)
        },
    )
}

@Composable
private fun HeaderAddButton(description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.padding(start = 4.dp).size(44.dp).semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, contentDescription = null) }
    }
}

@Composable
private fun GoalHero(goals: List<Goal>, today: LocalDate, modifier: Modifier = Modifier) {
    val c = Nafs.colors
    val average = goals.sumOf(Goal::progressPercent) / goals.size.coerceAtLeast(1)
    val complete = goals.count { it.progressPercent >= 100 }
    val open = goals.filter { it.progressPercent < 100 }
    val milestones = goals.flatMap(Goal::milestones)
    val milestonesDone = milestones.count(GoalMilestone::done)
    val overdue = open.count { goal -> (GoalDates.daysRemaining(goal.deadline, today) ?: 0L) < 0L }
    val dueSoon = open.count { goal -> GoalDates.daysRemaining(goal.deadline, today)?.let { days -> days in 0L..7L } == true }

    NafsHeroSurface(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                NafsProgressRing(
                    progress = average / 100f,
                    size = 104.dp,
                    stroke = 10.dp,
                    colors = listOf(c.heroGold, c.heroMint),
                    trackColor = Color.White.copy(alpha = 0.12f),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$average%", style = MaterialTheme.typography.headlineMedium, color = c.onHero)
                        Text("average", style = MaterialTheme.typography.labelSmall, color = c.onHeroMuted)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    NafsEyebrow("Direction with evidence", color = c.heroGold)
                    Text(
                        if (complete == goals.size) "Every goal complete. Name the next horizon."
                        else "$complete complete · ${open.size} still moving",
                        style = MaterialTheme.typography.titleLarge,
                        color = c.onHero,
                    )
                    Text(
                        if (milestones.isEmpty()) "Add milestones to make progress visible"
                        else "$milestonesDone of ${milestones.size} milestones done",
                        style = MaterialTheme.typography.labelMedium,
                        color = c.onHeroMuted,
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.10f)))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                HeroStat("${open.size}", "In motion", Modifier.weight(1f))
                HeroStat("$complete", "Complete", Modifier.weight(1f), valueColor = if (complete > 0) c.heroGold else c.onHero)
                if (overdue > 0) {
                    HeroStat("$overdue", "Overdue", Modifier.weight(1f))
                } else {
                    HeroStat("$dueSoon", "Due in 7 days", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String, modifier: Modifier = Modifier, valueColor: Color = Nafs.colors.onHero) {
    NafsStat(value = value, label = label, modifier = modifier, valueColor = valueColor, labelColor = Nafs.colors.onHeroMuted)
}

@Composable
private fun GoalTypeFilters(selected: GoalType?, goals: List<Goal>, onSelect: (GoalType?) -> Unit) {
    ChipRow {
        NafsChoiceChip("All · ${goals.size}", selected = selected == null, onClick = { onSelect(null) })
        GoalType.entries.forEach { type ->
            val count = goals.count { it.type == type }
            NafsChoiceChip(
                text = if (count > 0) "${type.displayName()} · $count" else type.displayName(),
                selected = selected == type,
                onClick = { onSelect(type) },
            )
        }
    }
}

// ─── Goal card ───────────────────────────────────────────────────────────────

@Composable
private fun GoalCard(
    goal: Goal,
    today: LocalDate,
    expanded: Boolean,
    pending: Boolean,
    pendingMilestones: Set<String>,
    linkedHabits: List<Habit>,
    onToggleExpanded: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onUpdateProgress: () -> Unit,
    onSetMilestoneDone: (GoalMilestone, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Nafs.colors
    val scheme = MaterialTheme.colorScheme
    val tint = c.goals
    val complete = goal.progressPercent >= 100
    val days = GoalDates.daysRemaining(goal.deadline, today)
    val deadlineTint = deadlineColor(days, complete)
    val milestoneCount = goal.milestones.size
    val milestonesDone = goal.milestones.count(GoalMilestone::done)
    val target = goal.targetValue

    NafsCard(
        modifier = modifier,
        color = if (complete) c.goldSoft else scheme.surfaceContainer,
        contentPadding = PaddingValues(0.dp),
    ) {
        Surface(onClick = onToggleExpanded, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(NafsSpace.card), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    NafsEmojiBadge(goal.emoji, tint, size = 48.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(goal.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            NafsPill(goal.type.displayName(), color = tint)
                            goal.category?.let { NafsPill(it.displayName(), color = scheme.onSurfaceVariant) }
                        }
                    }
                    Box(Modifier.heightIn(min = 28.dp), contentAlignment = Alignment.CenterEnd) {
                        when {
                            pending -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = tint)
                            complete -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = "Complete", tint = c.success, modifier = Modifier.size(20.dp))
                                Text("${goal.progressPercent}%", style = MaterialTheme.typography.titleLarge, color = c.success)
                            }
                            else -> Text("${goal.progressPercent}%", style = MaterialTheme.typography.titleLarge, color = tint)
                        }
                    }
                }

                goal.description?.takeIf { it.isNotBlank() }?.let { description ->
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = if (expanded) 8 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NafsProgressBar(goal.progressPercent / 100f, color = if (complete) c.success else tint)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when {
                                target != null -> numericLabel(goal.currentValue ?: 0.0, target, goal.unit)
                                milestoneCount > 0 -> "$milestonesDone of $milestoneCount milestones"
                                else -> "Progress estimate"
                            },
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        if (target != null && milestoneCount > 0) {
                            Text(
                                "$milestonesDone of $milestoneCount milestones",
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = deadlineTint, modifier = Modifier.size(16.dp))
                    Text(
                        deadlineText(goal.deadline, days),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = deadlineTint,
                    )
                    Icon(
                        if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "Collapse goal" else "Expand goal",
                        tint = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                Modifier.fillMaxWidth().padding(start = NafsSpace.card, end = NafsSpace.card, bottom = NafsSpace.card),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                NafsDivider(inset = 0.dp)
                if (target != null) NumericProgressWell(goal, target, pending, onUpdateProgress)
                if (milestoneCount > 0) MilestoneChecklist(goal.milestones, pendingMilestones, onSetMilestoneDone)
                if (goal.linkedHabitIds.isNotEmpty()) LinkedHabits(goal.linkedHabitIds.size, linkedHabits)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NafsSecondaryButton("Edit", onClick = onEdit, modifier = Modifier.weight(1f), enabled = !pending, icon = Icons.Rounded.Edit)
                    DangerButton("Delete", onClick = onDelete, enabled = !pending, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun NumericProgressWell(goal: Goal, target: Double, pending: Boolean, onUpdate: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                NafsEyebrow("Numeric progress")
                Text(numericLabel(goal.currentValue ?: 0.0, target, goal.unit), style = MaterialTheme.typography.titleMedium)
                if (goal.milestones.isNotEmpty()) {
                    Text(
                        "Milestones still set the percentage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Button(
                onClick = onUpdate,
                enabled = !pending,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.TrendingUp, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Update", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun MilestoneChecklist(
    milestones: List<GoalMilestone>,
    pendingMilestones: Set<String>,
    onSetMilestoneDone: (GoalMilestone, Boolean) -> Unit,
) {
    val doneCount = milestones.count(GoalMilestone::done)
    Column {
        NafsEyebrow("Milestones · $doneCount/${milestones.size}", Modifier.padding(bottom = 4.dp))
        milestones.forEach { milestone ->
            MilestoneRow(
                milestone = milestone,
                pending = milestone.id in pendingMilestones,
                onToggle = { checked -> onSetMilestoneDone(milestone, checked) },
            )
        }
    }
}

@Composable
private fun MilestoneRow(milestone: GoalMilestone, pending: Boolean, onToggle: (Boolean) -> Unit) {
    val tint = Nafs.colors.goals
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .toggleable(value = milestone.done, enabled = !pending, role = Role.Checkbox, onValueChange = onToggle)
            .padding(horizontal = 4.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
            if (pending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = tint)
            else RoundCheck(checked = milestone.done, tint = tint)
        }
        Text(
            milestone.title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = if (milestone.done) scheme.onSurfaceVariant else scheme.onSurface,
            textDecoration = if (milestone.done) TextDecoration.LineThrough else null,
        )
        milestone.targetDate?.let { date ->
            Text(date.format(SHORT_DATE), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LinkedHabits(linkedCount: Int, habits: List<Habit>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NafsEyebrow("Linked habits")
        if (habits.isEmpty()) {
            Text(
                if (linkedCount == 1) "1 linked habit" else "$linkedCount linked habits",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                habits.forEach { habit -> NafsPill("${habit.emoji} ${habit.name}", color = Nafs.colors.habits) }
            }
        }
    }
}

/** Round check used for milestones and linked habits. */
@Composable
private fun RoundCheck(checked: Boolean, tint: Color, size: Dp = 24.dp) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (checked) tint else Color.Transparent)
            .border(1.5.dp, if (checked) tint else scheme.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = scheme.surfaceContainerLowest, modifier = Modifier.size(size * 0.7f))
        }
    }
}

@Composable
private fun DangerButton(text: String, onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    val error = MaterialTheme.colorScheme.error
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, error.copy(alpha = if (enabled) 0.45f else 0.15f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = error),
    ) {
        Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun EmptyGoals(selected: GoalType?, onCreate: () -> Unit, modifier: Modifier = Modifier) {
    NafsEmptyState(
        icon = Icons.Rounded.Flag,
        title = if (selected == null) "Name the direction" else "No ${selected.displayName().lowercase()} goals yet",
        message = "A useful goal has a horizon, evidence, and a next milestone.",
        modifier = modifier,
        tint = Nafs.colors.goals,
        actionLabel = "Create goal",
        onAction = onCreate,
    )
}

@Composable
private fun GoalSkeleton() {
    val block = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = NafsSpace.screen, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(196.dp).clip(MaterialTheme.shapes.extraLarge).background(block))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(4) { Box(Modifier.width(76.dp).height(38.dp).clip(CircleShape).background(block)) }
        }
        repeat(3) { Box(Modifier.fillMaxWidth().height(136.dp).clip(MaterialTheme.shapes.large).background(block)) }
    }
}

// ─── Dialogs ─────────────────────────────────────────────────────────────────

@Composable
private fun GoalProgressDialog(
    goal: Goal,
    amount: String,
    error: String?,
    pending: Boolean,
    onAmountChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val unit = goal.unit?.trim().orEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { NafsEmojiBadge(goal.emoji, Nafs.colors.goals) },
        title = { Text("Update progress") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(goal.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                NafsTextField(
                    value = amount,
                    onValueChange = onAmountChange,
                    label = if (unit.isEmpty()) "Current" else "Current $unit",
                    supportingText = error ?: "Target: ${formatNumber(goal.targetValue ?: 0.0)} $unit".trim(),
                    isError = error != null,
                    enabled = !pending,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (goal.milestones.isNotEmpty()) {
                    Text(
                        "Milestones still determine the displayed percentage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = !pending) {
                if (pending) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !pending) { Text("Cancel") } },
    )
}

@Composable
private fun GoalDeleteDialog(goal: Goal, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = scheme.error) },
        title = { Text("Delete ${goal.title}?") },
        text = { Text("This permanently removes the goal and its milestones. This cannot be undone.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError),
            ) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ─── Editor sheet ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalEditorSheet(draft: GoalEditorDraft, state: GoalUiState, actions: GoalEditorActions) {
    val saving by rememberUpdatedState(state.isSaving)
    // While a save is in flight the sheet can't be swiped or tapped away, matching the old dialog.
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value -> value != SheetValue.Hidden || !saving },
    )
    ModalBottomSheet(
        onDismissRequest = actions.onClose,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        GoalEditorContent(draft, state, actions)
    }
}

/** The editor's body, separate from the sheet so it can be rendered on its own. */
@Composable
internal fun GoalEditorContent(draft: GoalEditorDraft, state: GoalUiState, actions: GoalEditorActions) {
    val scheme = MaterialTheme.colorScheme
    val tint = Nafs.colors.goals
    val enabled = !state.isSaving
    val isNew = draft.goalId == null
    val deadline = draft.deadline.trim().takeIf { it.isNotEmpty() }?.let { text -> runCatching { LocalDate.parse(text) }.getOrNull() }
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    val sentences = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
    var pickingDeadline by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Row(Modifier.gutter(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            NafsEmojiBadge(draft.emoji, tint, size = 52.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (isNew) "New goal" else "Edit goal", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Give it a horizon, evidence and a next milestone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }

        Column(Modifier.gutter(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NafsTextField(draft.emoji, actions.onEmojiChange, label = "Emoji", modifier = Modifier.width(88.dp), enabled = enabled)
                NafsTextField(
                    draft.title,
                    actions.onTitleChange,
                    label = "Goal title",
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    keyboardOptions = sentences,
                )
            }
            NafsTextField(
                draft.description,
                actions.onDescriptionChange,
                label = "Description (optional)",
                enabled = enabled,
                singleLine = false,
                minLines = 2,
                maxLines = 5,
                keyboardOptions = sentences,
            )
        }

        EditorGroup("Horizon", supporting = "Changing it suggests a matching deadline.") {
            ChipRow {
                GoalType.entries.forEach { type ->
                    NafsChoiceChip(type.displayName(), selected = draft.type == type, onClick = { actions.onTypeChange(type) }, enabled = enabled)
                }
            }
        }

        EditorGroup("Category") {
            ChipRow {
                NafsChoiceChip("General", selected = draft.category == null, onClick = { actions.onCategoryChange(null) }, enabled = enabled)
                GoalCategory.entries.forEach { category ->
                    NafsChoiceChip(
                        category.displayName(),
                        selected = draft.category == category,
                        onClick = { actions.onCategoryChange(category) },
                        enabled = enabled,
                    )
                }
            }
        }

        EditorGroup("Deadline") {
            DeadlineField(
                raw = draft.deadline,
                date = deadline,
                today = state.today,
                enabled = enabled,
                onPick = { pickingDeadline = true },
                onClear = { actions.onDeadlineChange("") },
            )
        }

        if (state.habits.isNotEmpty()) {
            EditorGroup("Linked habits", supporting = "Daily habits that move this goal forward.") {
                NafsCard(Modifier.gutter(), contentPadding = PaddingValues(vertical = 4.dp)) {
                    state.habits.forEachIndexed { index, habit ->
                        if (index > 0) NafsDivider()
                        HabitLinkRow(
                            habit = habit,
                            linked = habit.id in draft.linkedHabitIds,
                            enabled = enabled,
                            onToggle = { actions.onToggleLinkedHabit(habit.id) },
                        )
                    }
                }
            }
        }

        NafsCard(Modifier.gutter(), contentPadding = PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                NafsIconBadge(Icons.AutoMirrored.Rounded.TrendingUp, tint, size = 38.dp, iconSize = 20.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Numeric target", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Track an amount such as 10 books or 50,000 PKR.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Switch(checked = draft.hasNumericTarget, onCheckedChange = actions.onNumericTargetChange, enabled = enabled)
            }
            if (draft.hasNumericTarget) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NafsTextField(
                        draft.currentValue,
                        actions.onCurrentValueChange,
                        label = "Current",
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        keyboardOptions = decimal,
                    )
                    NafsTextField(
                        draft.targetValue,
                        actions.onTargetValueChange,
                        label = "Target",
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        keyboardOptions = decimal,
                    )
                    NafsTextField(draft.unit, actions.onUnitChange, label = "Unit", modifier = Modifier.weight(1f), enabled = enabled)
                }
            }
        }

        if (isNew) {
            EditorGroup(
                "Milestones",
                supporting = "Optional. When milestones exist, their completion ratio becomes the true goal progress.",
            ) {
                Column(Modifier.gutter(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    draft.milestones.forEachIndexed { index, milestone ->
                        MilestoneField(
                            index = index,
                            value = milestone,
                            removable = draft.milestones.size > 1,
                            enabled = enabled,
                            onChange = { text -> actions.onMilestoneChange(index, text) },
                            onRemove = { actions.onRemoveMilestone(index) },
                        )
                    }
                    TextButton(onClick = actions.onAddMilestone, enabled = enabled && draft.milestones.size < 20) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add milestone")
                    }
                }
            }
        } else if (state.goals.firstOrNull { it.id == draft.goalId }?.milestones?.isNotEmpty() == true) {
            NafsBanner(
                message = "Existing milestones remain attached. Complete them from the expanded goal card.",
                modifier = Modifier.gutter(),
                kind = NafsBannerKind.INFO,
            )
        }

        state.editorError?.let { message ->
            NafsBanner(message = message, modifier = Modifier.gutter(), kind = NafsBannerKind.ERROR)
        }

        Row(Modifier.gutter(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = actions.onClose, enabled = enabled, modifier = Modifier.weight(1f).height(54.dp)) { Text("Cancel") }
            NafsPrimaryButton(
                text = if (isNew) "Create goal" else "Save changes",
                onClick = actions.onSave,
                modifier = Modifier.weight(1f),
                loading = state.isSaving,
            )
        }
    }

    if (pickingDeadline) {
        GoalDatePickerDialog(
            initial = deadline,
            onDismiss = { pickingDeadline = false },
            onConfirm = { picked ->
                actions.onDeadlineChange(picked)
                pickingDeadline = false
            },
        )
    }
}

@Composable
private fun EditorGroup(title: String, supporting: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.gutter(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            supporting?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        content()
    }
}

/** Chips that scroll edge to edge while lining up with the screen gutter at rest. */
@Composable
private fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = NafsSpace.screen),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun DeadlineField(
    raw: String,
    date: LocalDate?,
    today: LocalDate,
    enabled: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onPick,
        enabled = enabled,
        modifier = Modifier.gutter().fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = scheme.surfaceContainer,
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Row(
            Modifier.heightIn(min = 60.dp).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                Icons.Rounded.CalendarMonth,
                contentDescription = null,
                tint = if (date != null) Nafs.colors.goals else scheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    when {
                        date != null -> date.format(LONG_DATE)
                        raw.isBlank() -> "No deadline"
                        else -> raw
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    when {
                        date != null -> relativeDay(date, today)
                        raw.isBlank() -> "Tap to choose a date"
                        else -> "Use the format YYYY-MM-DD"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            if (raw.isNotBlank()) {
                IconButton(onClick = onClear, enabled = enabled) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear deadline", tint = scheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalDatePickerDialog(initial: LocalDate?, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial?.let(::utcMillis))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                },
                enabled = pickerState.selectedDateMillis != null,
            ) { Text("Set deadline") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = pickerState)
    }
}

@Composable
private fun HabitLinkRow(habit: Habit, linked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val tint = Nafs.colors.habits
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = linked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        NafsEmojiBadge(habit.emoji, tint, size = 36.dp)
        Text(habit.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        RoundCheck(checked = linked, tint = tint)
    }
}

@Composable
private fun MilestoneField(
    index: Int,
    value: String,
    removable: Boolean,
    enabled: Boolean,
    onChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val label = "Milestone ${index + 1}"
    if (removable) {
        NafsTextField(
            value,
            onChange,
            label = label,
            enabled = enabled,
            trailingIcon = {
                IconButton(onClick = onRemove, enabled = enabled) {
                    Icon(Icons.Rounded.Close, contentDescription = "Remove milestone ${index + 1}")
                }
            },
        )
    } else {
        NafsTextField(value, onChange, label = label, enabled = enabled)
    }
}

// ─── Formatting ──────────────────────────────────────────────────────────────

private fun Modifier.gutter(): Modifier = padding(horizontal = NafsSpace.screen)

private fun goalSummary(state: GoalUiState): String {
    val goals = state.goals
    return when {
        state.isLoading -> "Loading your goals…"
        goals.isEmpty() -> "Turn direction into progress"
        else -> {
            val active = goals.count { it.progressPercent < 100 }
            val average = goals.sumOf(Goal::progressPercent) / goals.size
            "$active active · $average% average"
        }
    }
}

@Composable
private fun deadlineColor(days: Long?, complete: Boolean): Color {
    val overdue = !complete && days != null && days < 0L
    val soon = !complete && days != null && days in 0L..7L
    return when {
        overdue -> MaterialTheme.colorScheme.error
        soon -> Nafs.colors.warning
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private fun deadlineText(deadline: LocalDate?, days: Long?): String = when {
    deadline == null -> "No deadline"
    days == null -> deadline.toString()
    days < 0 -> "${deadline.format(DISPLAY_DATE)} · ${-days} days overdue"
    days == 0L -> "${deadline.format(DISPLAY_DATE)} · due today"
    days == 1L -> "${deadline.format(DISPLAY_DATE)} · 1 day left"
    else -> "${deadline.format(DISPLAY_DATE)} · $days days left"
}

private fun relativeDay(date: LocalDate, today: LocalDate): String {
    val days = date.toEpochDay() - today.toEpochDay()
    return when {
        days < -1L -> "${-days} days ago"
        days == -1L -> "Yesterday"
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        else -> "In $days days"
    }
}

private fun numericLabel(current: Double, target: Double, unit: String?): String {
    val suffix = unit?.trim()?.takeIf { it.isNotEmpty() }?.let { " $it" }.orEmpty()
    return "${formatNumber(current)} / ${formatNumber(target)}$suffix"
}

private fun utcMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

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
private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
private val LONG_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
