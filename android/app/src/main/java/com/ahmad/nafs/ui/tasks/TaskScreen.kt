package com.ahmad.nafs.ui.tasks

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Upcoming
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.core.tasks.NafsTask
import com.ahmad.nafs.core.tasks.TaskPriority
import com.ahmad.nafs.domain.tasks.TaskDraftValidator
import com.ahmad.nafs.domain.tasks.TaskPeriod
import com.ahmad.nafs.domain.tasks.TaskPeriodType
import com.ahmad.nafs.domain.tasks.TaskPlanning
import com.ahmad.nafs.ui.AccountDayRefreshEffects
import com.ahmad.nafs.ui.components.NafsBanner
import com.ahmad.nafs.ui.components.NafsBannerKind
import com.ahmad.nafs.ui.components.NafsCard
import com.ahmad.nafs.ui.components.NafsChoiceChip
import com.ahmad.nafs.ui.components.NafsEmptyState
import com.ahmad.nafs.ui.components.NafsErrorState
import com.ahmad.nafs.ui.components.NafsEyebrow
import com.ahmad.nafs.ui.components.NafsHeroSurface
import com.ahmad.nafs.ui.components.NafsIconBadge
import com.ahmad.nafs.ui.components.NafsLoadingState
import com.ahmad.nafs.ui.components.NafsPageHeader
import com.ahmad.nafs.ui.components.NafsPill
import com.ahmad.nafs.ui.components.NafsPrimaryButton
import com.ahmad.nafs.ui.components.NafsProgressRing
import com.ahmad.nafs.ui.components.NafsSectionTitle
import com.ahmad.nafs.ui.components.NafsSegmented
import com.ahmad.nafs.ui.components.NafsSpace
import com.ahmad.nafs.ui.components.NafsStat
import com.ahmad.nafs.ui.components.NafsTextField
import com.ahmad.nafs.ui.theme.Nafs
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun TaskRoute(
    profile: UserProfile,
    createOnOpen: Boolean = false,
    viewModel: TaskViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = viewModel::onNotificationPermissionResult,
    )

    LaunchedEffect(profile.userId, profile.timezone) {
        viewModel.initialize(profile.userId, profile.timezone)
        if (createOnOpen) viewModel.openCreate()
    }
    AccountDayRefreshEffects(profile.timezone, viewModel::refresh)
    BackHandler(enabled = state.isSearching, onBack = viewModel::closeSearch)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                // Snackbars wait their turn in the host; launching keeps later events (a permission prompt) prompt.
                is TaskUiEvent.Deleted -> launch {
                    val result = snackbarHostState.showSnackbar(
                        message = "Deleted ${event.title}",
                        actionLabel = "Undo",
                        withDismissAction = true,
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.restore(event.taskId)
                    }
                }
                is TaskUiEvent.Notice -> launch {
                    snackbarHostState.showSnackbar(message = event.message, withDismissAction = true)
                }
                TaskUiEvent.RequestNotificationPermission -> {
                    val alreadyGranted =
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) == PackageManager.PERMISSION_GRANTED
                    if (!alreadyGranted) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
        }
    }

    TaskScreen(
        state = state,
        actions = TaskActions(
            onRefresh = viewModel::refresh,
            onSelectType = viewModel::selectType,
            onOpenCreate = viewModel::openCreate,
            onOpenEdit = viewModel::openEdit,
            onCloseCreate = viewModel::closeCreate,
            onTitleChange = viewModel::setTitle,
            onNoteChange = viewModel::setNote,
            onPriorityChange = viewModel::setPriority,
            onDueTimeChange = viewModel::setDueTime,
            onPlannedDateChange = viewModel::setPlannedDate,
            onSave = viewModel::save,
            onSetCompleted = viewModel::setCompleted,
            onMoveTask = viewModel::moveTask,
            onDelete = viewModel::delete,
            onDismissActionError = viewModel::dismissActionError,
            onOpenSearch = viewModel::openSearch,
            onSearchQueryChange = viewModel::setSearchQuery,
            onCloseSearch = viewModel::closeSearch,
        ),
        snackbarHostState = snackbarHostState,
    )
}

/** Everything the Tasks screen can ask for, so the screen stays a pure function of its state. */
internal data class TaskActions(
    val onRefresh: () -> Unit = {},
    val onSelectType: (TaskPeriodType) -> Unit = {},
    val onOpenCreate: () -> Unit = {},
    val onOpenEdit: (NafsTask) -> Unit = {},
    val onCloseCreate: () -> Unit = {},
    val onTitleChange: (String) -> Unit = {},
    val onNoteChange: (String) -> Unit = {},
    val onPriorityChange: (TaskPriority) -> Unit = {},
    val onDueTimeChange: (String) -> Unit = {},
    val onPlannedDateChange: (LocalDate?) -> Unit = {},
    val onSave: () -> Unit = {},
    val onSetCompleted: (NafsTask, Boolean) -> Unit = { _, _ -> },
    val onMoveTask: (NafsTask, LocalDate) -> Unit = { _, _ -> },
    val onDelete: (NafsTask) -> Unit = {},
    val onDismissActionError: () -> Unit = {},
    val onOpenSearch: () -> Unit = {},
    val onSearchQueryChange: (String) -> Unit = {},
    val onCloseSearch: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskScreen(
    state: TaskUiState,
    actions: TaskActions,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val visible = visibleTasksFor(state)
    val upcoming = upcomingTasksFor(state)
    val currentKey = TaskPeriod.key(state.selectedType, state.today)
    val previousKey = TaskPeriod.previousKey(state.selectedType, currentKey)
    val currentTasks = visible.filter { it.periodDate == currentKey }
    val graceTasks = visible.filter { it.periodDate == previousKey }
    val doneCount = currentTasks.count(NafsTask::isCompleted)
    val openCount = currentTasks.size - doneCount
    val missedCount = graceTasks.count { !it.isCompleted }
    val summary = taskSummary(state.selectedType, openCount, doneCount)
    val blockingError = state.loadError?.takeIf { state.tasks.isEmpty() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).statusBarsPadding()) {
            when {
                state.isLoading -> Column(Modifier.fillMaxSize()) {
                    TasksHeader(state, summary, actions, Modifier.padding(horizontal = NafsSpace.screen))
                    NafsLoadingState(Modifier.weight(1f), cards = 4)
                }

                blockingError != null -> Column(Modifier.fillMaxSize()) {
                    TasksHeader(state, summary, actions, Modifier.padding(horizontal = NafsSpace.screen))
                    NafsErrorState(
                        title = "Tasks didn't load",
                        message = blockingError,
                        onRetry = actions.onRefresh,
                        modifier = Modifier.weight(1f),
                    )
                }

                state.isSearching -> TaskSearchContent(state, actions)

                else -> PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = actions.onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = NafsSpace.screen,
                            end = NafsSpace.screen,
                            top = 8.dp,
                            bottom = 28.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item(key = "header") { TasksHeader(state, summary, actions) }

                        if (state.isOffline) {
                            item(key = "offline") {
                                NafsBanner(
                                    "Showing your last synced tasks. Changes need a connection.",
                                    kind = NafsBannerKind.OFFLINE,
                                    actionLabel = "Retry",
                                    onAction = actions.onRefresh,
                                )
                            }
                        }
                        state.loadError?.let { message ->
                            item(key = "refresh-error") {
                                NafsBanner(
                                    message,
                                    kind = NafsBannerKind.WARNING,
                                    title = "Couldn't refresh",
                                    actionLabel = "Retry",
                                    onAction = actions.onRefresh,
                                )
                            }
                        }
                        state.actionError?.let { message ->
                            item(key = "action-error") {
                                NafsBanner(message, kind = NafsBannerKind.ERROR, onDismiss = actions.onDismissActionError)
                            }
                        }

                        item(key = "period") {
                            NafsSegmented(
                                options = TaskPeriodType.entries.map { it.displayName() },
                                selectedIndex = TaskPeriodType.entries.indexOf(state.selectedType),
                                onSelect = { index -> actions.onSelectType(TaskPeriodType.entries[index]) },
                            )
                        }

                        if (visible.isEmpty()) {
                            item(key = "empty") {
                                val planned = upcoming.isNotEmpty()
                                NafsEmptyState(
                                    icon = Icons.Rounded.TaskAlt,
                                    title = if (planned) {
                                        "Nothing for ${state.selectedType.periodPhrase()} yet"
                                    } else {
                                        "No ${state.selectedType.displayName().lowercase()} tasks yet"
                                    },
                                    message = if (planned) {
                                        "Your planned tasks are below. Add one for ${state.selectedType.periodPhrase()} if it matters."
                                    } else {
                                        "Add one clear promise you can keep this ${state.selectedType.periodNoun()}."
                                    },
                                    tint = Nafs.colors.tasks,
                                    actionLabel = "Create task",
                                    onAction = actions.onOpenCreate,
                                )
                            }
                        } else {
                            item(key = "hero") {
                                TaskHero(
                                    type = state.selectedType,
                                    done = doneCount,
                                    total = currentTasks.size,
                                    missed = missedCount,
                                )
                            }
                            if (currentTasks.isNotEmpty()) {
                                item(key = "current-title") {
                                    NafsSectionTitle(
                                        periodLabel(state.selectedType, current = true),
                                        Modifier.padding(top = 10.dp),
                                        supporting = "$openCount open · $doneCount done",
                                    )
                                }
                                items(currentTasks, key = { "task-${it.id}" }) { task ->
                                    TaskCard(
                                        task = task,
                                        today = state.today,
                                        isPending = task.id in state.pendingTaskIds,
                                        actions = actions,
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                            }
                            if (graceTasks.isNotEmpty()) {
                                item(key = "grace-title") {
                                    NafsSectionTitle(
                                        periodLabel(state.selectedType, current = false),
                                        Modifier.padding(top = 10.dp),
                                        supporting = if (missedCount > 0) {
                                            val them = if (missedCount == 1) "it" else "them"
                                            "$missedCount missed · move $them forward, or tick $them off late"
                                        } else {
                                            "Grace window · visible for one more ${state.selectedType.periodNoun()}"
                                        },
                                    )
                                }
                                items(graceTasks, key = { "task-${it.id}" }) { task ->
                                    TaskCard(
                                        task = task,
                                        today = state.today,
                                        isPending = task.id in state.pendingTaskIds,
                                        actions = actions,
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                            }
                        }

                        if (upcoming.isNotEmpty()) {
                            item(key = "upcoming-title") {
                                val count = upcoming.sumOf { it.tasks.size }
                                NafsSectionTitle(
                                    "Upcoming",
                                    Modifier.padding(top = 10.dp),
                                    supporting = "$count planned ahead · each joins the list on its ${state.selectedType.periodNoun()}",
                                )
                            }
                            upcoming.forEach { group ->
                                item(key = "upcoming-${group.periodDate}") {
                                    UpcomingDateHeading(state.selectedType, group.periodDate, state.today, group.tasks.size)
                                }
                                items(group.tasks, key = { "task-${it.id}" }) { task ->
                                    TaskCard(
                                        task = task,
                                        today = state.today,
                                        isPending = task.id in state.pendingTaskIds,
                                        actions = actions,
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (state.showCreateDialog) {
        TaskSheet(busy = state.isSaving, onDismiss = actions.onCloseCreate) { close ->
            TaskEditorContent(state = state, actions = actions, onCancel = close)
        }
    }
}

@Composable
private fun TasksHeader(
    state: TaskUiState,
    summary: String,
    actions: TaskActions,
    modifier: Modifier = Modifier,
) {
    NafsPageHeader(
        title = "Tasks",
        modifier = modifier,
        eyebrow = periodEyebrow(state.selectedType, state.today),
        subtitle = summary,
        actions = {
            val scheme = MaterialTheme.colorScheme
            // Search covers what is loaded, so it appears once there is something to search.
            if (!state.isLoading && state.tasks.isNotEmpty()) {
                IconButton(onClick = actions.onOpenSearch) {
                    Icon(Icons.Rounded.Search, contentDescription = "Search tasks", tint = scheme.onSurfaceVariant)
                }
            }
            // The pull-to-refresh indicator shows progress; this stays for those who can't pull.
            val canRefresh = !state.isLoading && !state.isRefreshing
            IconButton(onClick = actions.onRefresh, enabled = canRefresh) {
                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = "Refresh tasks",
                    tint = scheme.onSurfaceVariant.copy(alpha = if (canRefresh) 1f else 0.38f),
                )
            }
            HeaderAddButton(description = "Create task", onClick = actions.onOpenCreate)
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
        modifier = Modifier.padding(start = 2.dp).size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, contentDescription = description, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun TaskHero(type: TaskPeriodType, done: Int, total: Int, missed: Int) {
    val c = Nafs.colors
    val progress = if (total == 0) 0f else done.toFloat() / total
    val open = total - done
    NafsHeroSurface {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                NafsProgressRing(
                    progress = progress,
                    size = 100.dp,
                    stroke = 10.dp,
                    colors = listOf(c.heroGold, c.heroSky),
                    trackColor = Color.White.copy(alpha = 0.12f),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.headlineSmall, color = c.onHero)
                        Text("done", style = MaterialTheme.typography.labelSmall, color = c.onHeroMuted)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    NafsEyebrow("${type.currentLabel()} progress", color = c.heroGold)
                    Text(heroHeadline(type, open, total), style = MaterialTheme.typography.titleLarge, color = c.onHero)
                    Text("$done of $total promises kept", style = MaterialTheme.typography.bodySmall, color = c.onHeroMuted)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.10f)))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                HeroStat("$open", "Open", Modifier.weight(1f))
                HeroStat("$done", "Done", Modifier.weight(1f))
                HeroStat("$missed", "Missed", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String, modifier: Modifier) {
    NafsStat(
        value = value,
        label = label,
        modifier = modifier,
        valueColor = Nafs.colors.onHero,
        labelColor = Nafs.colors.onHeroMuted,
    )
}

/**
 * One task. A missed task (still open, its period over) gets a warning tint and ways to move it forward,
 * never the struck-through look of a completed one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskCard(
    task: NafsTask,
    today: LocalDate,
    isPending: Boolean,
    actions: TaskActions,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val c = Nafs.colors
    val accent = c.tasks
    val done = task.isCompleted
    val state = taskStateOf(task, today)
    val missed = state == TaskState.MISSED
    NafsCard(
        modifier = modifier,
        color = if (missed) {
            c.warning.copy(alpha = if (c.isDark) 0.08f else 0.06f).compositeOver(scheme.surfaceContainer)
        } else {
            scheme.surfaceContainer
        },
        borderColor = if (missed) c.warning.copy(alpha = 0.45f) else c.cardBorder,
        contentPadding = PaddingValues(start = 6.dp, top = 8.dp, end = 2.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            CompletionCircle(
                checked = done,
                pending = isPending,
                accent = accent,
                idleRing = if (missed) c.warning else scheme.outline,
                label = "Complete ${task.title}",
                onToggle = { completed -> actions.onSetCompleted(task, completed) },
            )
            Column(
                Modifier.weight(1f).padding(start = 4.dp, top = 13.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (done) scheme.onSurfaceVariant else scheme.onSurface,
                    textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                task.note?.takeIf { it.isNotBlank() }?.let { note ->
                    Text(
                        note,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FlowRow(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (missed) NafsPill(TaskLabels.missed(task.type), color = c.warning, icon = Icons.Rounded.EventBusy)
                    NafsPill(task.priority.displayName(), color = task.priority.color(), icon = Icons.Rounded.Flag)
                    task.dueTime?.takeIf { it.isNotBlank() }?.let { due ->
                        NafsPill("Due ${TaskLabels.time(due)}", color = accent, icon = Icons.Rounded.Schedule)
                    }
                }
                if (missed) {
                    MoveChoices(task = task, today = today, enabled = !isPending, onMove = actions.onMoveTask)
                }
            }
            TaskMenu(
                task = task,
                today = today,
                enabled = !isPending,
                // An upcoming task can be pulled into the current period; a missed one has its own buttons.
                pullForward = state == TaskState.UPCOMING,
                actions = actions,
            )
        }
    }
}

/** "Move to [Today] [Tomorrow]" for a daily task, "Move to [This week]" / "[This month]" otherwise. */
@Composable
private fun MoveChoices(
    task: NafsTask,
    today: LocalDate,
    enabled: Boolean,
    onMove: (NafsTask, LocalDate) -> Unit,
) {
    Row(
        Modifier.padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Move to", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TaskPlanning.moveTargets(task.type, today).forEach { target ->
            val label = TaskLabels.period(task.type, target, today)
            MoveChip(
                text = label,
                description = "Move ${task.title} to ${label.lowercase(Locale.ENGLISH)}",
                enabled = enabled,
                onClick = { onMove(task, target) },
            )
        }
    }
}

@Composable
private fun MoveChip(text: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    val c = Nafs.colors
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = c.warningContainer,
        contentColor = c.onWarningContainer,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

@Composable
private fun UpcomingDateHeading(type: TaskPeriodType, periodDate: LocalDate, today: LocalDate, count: Int) {
    val label = TaskLabels.period(type, periodDate, today)
    val relative = when (type) {
        TaskPeriodType.DAILY -> {
            val days = ChronoUnit.DAYS.between(today, periodDate)
            if (days == 1L) periodDate.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)) else "in $days days"
        }
        TaskPeriodType.WEEKLY, TaskPeriodType.MONTHLY -> null
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp, start = 4.dp).semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.Event, contentDescription = null, tint = Nafs.colors.tasks, modifier = Modifier.size(16.dp))
        NafsEyebrow(listOfNotNull(label, relative).joinToString(" · "), color = Nafs.colors.tasks)
        Spacer(Modifier.weight(1f))
        Text(
            if (count == 1) "1 task" else "$count tasks",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Round, animated check: fills with the area colour when done and shows a spinner while saving. */
@Composable
private fun CompletionCircle(
    checked: Boolean,
    pending: Boolean,
    accent: Color,
    idleRing: Color,
    label: String,
    onToggle: (Boolean) -> Unit,
) {
    val fill by animateColorAsState(if (checked) accent else Color.Transparent, label = "check-fill")
    val ring by animateColorAsState(if (checked) accent else idleRing, label = "check-ring")
    val markScale by animateFloatAsState(if (checked) 1f else 0f, label = "check-mark")
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .toggleable(value = checked, enabled = !pending, role = Role.Checkbox, onValueChange = onToggle)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (pending) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = accent)
        } else {
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(fill).border(2.dp, ring, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(17.dp).scale(markScale),
                )
            }
        }
    }
}

@Composable
private fun TaskMenu(
    task: NafsTask,
    today: LocalDate,
    enabled: Boolean,
    pullForward: Boolean,
    actions: TaskActions,
) {
    var expanded by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "Options for ${task.title}", tint = scheme.onSurfaceVariant)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.medium,
            containerColor = scheme.surfaceContainerHigh,
        ) {
            DropdownMenuItem(
                text = { Text("Edit") },
                onClick = {
                    expanded = false
                    actions.onOpenEdit(task)
                },
                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = "Edit ${task.title}" },
            )
            if (pullForward) {
                val target = TaskPeriod.key(task.type, today)
                val label = TaskLabels.moveTo(task.type, target, today)
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        actions.onMoveTask(task, target)
                    },
                    leadingIcon = { Icon(Icons.Rounded.Today, contentDescription = null) },
                    enabled = enabled,
                    modifier = Modifier.semantics { contentDescription = "$label: ${task.title}" },
                )
            }
            DropdownMenuItem(
                text = { Text("Delete", color = scheme.error) },
                onClick = {
                    expanded = false
                    actions.onDelete(task)
                },
                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = scheme.error) },
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = "Delete ${task.title}" },
            )
        }
    }
}

// ─── Search ──────────────────────────────────────────────────────────────────

/** Search across everything loaded: current lists, missed, upcoming and the last six months. */
@Composable
private fun TaskSearchContent(state: TaskUiState, actions: TaskActions) {
    val results = remember(state.tasks, state.searchQuery) { searchTasks(state.tasks, state.searchQuery) }
    val query = state.searchQuery.trim()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        // A fresh search starts typing at once; a restored one (rotation) keeps its results in view.
        if (state.searchQuery.isEmpty()) focusRequester.requestFocus()
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        // Outside the list so it stays in reach while results scroll, and is composed before focus is requested.
        Row(
            Modifier.fillMaxWidth().padding(start = NafsSpace.screen, end = 8.dp, top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            NafsTextField(
                value = state.searchQuery,
                onValueChange = actions.onSearchQueryChange,
                label = "Search tasks",
                placeholder = "Title or note",
                leadingIcon = Icons.Rounded.Search,
                trailingIcon = if (state.searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { actions.onSearchQueryChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                        }
                    }
                } else {
                    null
                },
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            )
            TextButton(onClick = actions.onCloseSearch) { Text("Close") }
        }
        SearchResults(state, query, results, actions, Modifier.weight(1f))
    }
}

@Composable
private fun SearchResults(
    state: TaskUiState,
    query: String,
    results: List<NafsTask>,
    actions: TaskActions,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = NafsSpace.screen, end = NafsSpace.screen, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            query.isEmpty() -> item(key = "search-hint") {
                Text(
                    "Find any task by its title or note, from the last six months to the days you've planned ahead.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            results.isEmpty() -> item(key = "search-empty") {
                NafsEmptyState(
                    icon = Icons.Rounded.SearchOff,
                    title = "No tasks match",
                    message = "Nothing from the last six months or planned ahead mentions “$query”.",
                    tint = Nafs.colors.tasks,
                )
            }

            else -> {
                item(key = "search-count") {
                    Text(
                        if (results.size == 1) "1 task found" else "${results.size} tasks found",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                items(results, key = { "result-${it.id}" }) { task ->
                    SearchResultCard(task, state.today, onOpen = actions.onOpenEdit)
                }
            }
        }
    }
}

/** A search hit with its date, period type and state, so it reads without opening it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchResultCard(task: NafsTask, today: LocalDate, onOpen: (NafsTask) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val c = Nafs.colors
    val state = taskStateOf(task, today)
    val (icon, tint) = when (state) {
        TaskState.DONE -> Icons.Rounded.CheckCircle to c.success
        TaskState.MISSED -> Icons.Rounded.EventBusy to c.warning
        TaskState.UPCOMING -> Icons.Rounded.Upcoming to c.tasks
        TaskState.OPEN -> Icons.Rounded.RadioButtonUnchecked to c.tasks
    }
    NafsCard(onClick = { onOpen(task) }, contentPadding = PaddingValues(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            NafsIconBadge(icon, tint, size = 40.dp, iconSize = 20.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (state == TaskState.DONE) scheme.onSurfaceVariant else scheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                task.note?.takeIf { it.isNotBlank() }?.let { note ->
                    Text(note, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    NafsPill(state.label, color = tint)
                    NafsPill(TaskLabels.period(task.type, task.periodDate, today), color = scheme.onSurfaceVariant, icon = Icons.Rounded.Event)
                    if (task.type != TaskPeriodType.DAILY) {
                        NafsPill(task.type.displayName(), color = scheme.onSurfaceVariant)
                    }
                    task.dueTime?.takeIf { it.isNotBlank() }?.let { due ->
                        NafsPill(TaskLabels.time(due), color = scheme.onSurfaceVariant, icon = Icons.Rounded.Schedule)
                    }
                }
            }
        }
    }
}

// ─── Editor ──────────────────────────────────────────────────────────────────

/** Modal sheet that refuses to close while a save is in flight, matching the ViewModel's own guard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskSheet(
    busy: Boolean,
    onDismiss: () -> Unit,
    content: @Composable (close: () -> Unit) -> Unit,
) {
    val isBusy by rememberUpdatedState(busy)
    val allowHide = remember { { target: SheetValue -> target != SheetValue.Hidden || !isBusy } }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = allowHide)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        content(close)
    }
}

/** The task form shown inside the editor sheet. Stateless so it can be rendered on its own. */
@Composable
internal fun TaskEditorContent(state: TaskUiState, actions: TaskActions, onCancel: () -> Unit) {
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    val draft = state.draft
    val saving = state.isSaving
    val isNew = state.editingTaskId == null
    val showErrors = state.formError != null
    val titleInvalid = showErrors && TaskDraftValidator.titleError(draft.title) != null
    val timeInvalid = showErrors && TaskDraftValidator.dueTimeError(state.selectedType, draft.dueTime) != null
    val canPlan = isNew && state.selectedType == TaskPeriodType.DAILY
    val plannedFor = draft.plannedDate?.takeIf { canPlan } ?: TaskPeriod.key(state.selectedType, state.today)
    val accent = Nafs.colors.tasks
    val editedTask = state.editingTaskId?.let { id -> state.tasks.firstOrNull { it.id == id } }

    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = NafsSpace.screen)
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            NafsIconBadge(Icons.Rounded.TaskAlt, accent)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                NafsEyebrow(
                    if (isNew) {
                        "For ${TaskLabels.periodPhrase(state.selectedType, plannedFor, state.today)}"
                    } else {
                        listOfNotNull(
                            "${state.selectedType.displayName()} task",
                            editedTask?.let { TaskLabels.period(it.type, it.periodDate, state.today) },
                        ).joinToString(" · ")
                    },
                    color = accent,
                )
                Text(
                    if (isNew) "New ${state.selectedType.displayName().lowercase()} task" else "Edit task",
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NafsTextField(
                value = draft.title,
                onValueChange = actions.onTitleChange,
                label = "Task",
                placeholder = "What will you do?",
                leadingIcon = Icons.Rounded.TaskAlt,
                isError = titleInvalid,
                enabled = !saving,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
            )
            NafsTextField(
                value = draft.note,
                onValueChange = actions.onNoteChange,
                label = "Note (optional)",
                leadingIcon = Icons.AutoMirrored.Rounded.EventNote,
                enabled = !saving,
                singleLine = false,
                minLines = 2,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        }

        if (canPlan) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FieldLabel("When")
                PlanDateChoices(
                    planned = draft.plannedDate,
                    today = state.today,
                    enabled = !saving,
                    onChoose = actions.onPlannedDateChange,
                    onPick = { pickingDate = true },
                )
                Text(
                    if (plannedFor != state.today) {
                        "It waits under Upcoming and joins that day's list when the day arrives."
                    } else {
                        "Plan up to ${TaskPlanning.MAX_DAYS_AHEAD} days ahead. Days follow your account timezone."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FieldLabel("Priority")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskPriority.entries.reversed().forEach { priority ->
                    NafsChoiceChip(
                        text = priority.displayName(),
                        selected = draft.priority == priority,
                        onClick = { actions.onPriorityChange(priority) },
                        enabled = !saving,
                    )
                }
            }
        }

        if (state.selectedType == TaskPeriodType.DAILY) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FieldLabel("Due time")
                DueTimeRow(
                    value = draft.dueTime,
                    reminderLeadMinutes = state.reminderLeadMinutes,
                    enabled = !saving,
                    isError = timeInvalid,
                    onPick = { pickingTime = true },
                    onClear = { actions.onDueTimeChange("") },
                )
                Text(
                    state.reminderLeadMinutes?.let { lead ->
                        "Due times use your account timezone. NAFS reminds you $lead minutes before."
                    } ?: "Due times use your account timezone. Task reminders are off on this device; turn them on in Profile.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        state.formError?.let { message -> NafsBanner(message, kind = NafsBannerKind.ERROR) }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel, enabled = !saving, modifier = Modifier.weight(1f).height(54.dp)) {
                Text("Cancel")
            }
            NafsPrimaryButton(
                text = if (isNew) "Create task" else "Save changes",
                onClick = actions.onSave,
                loading = saving,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (pickingTime) {
        DueTimeDialog(
            current = draft.dueTime,
            onDismiss = { pickingTime = false },
            onConfirm = { time ->
                actions.onDueTimeChange(time)
                pickingTime = false
            },
            onClear = {
                actions.onDueTimeChange("")
                pickingTime = false
            },
        )
    }
    if (pickingDate) {
        PlanDateDialog(
            current = draft.plannedDate,
            today = state.today,
            onDismiss = { pickingDate = false },
            onConfirm = { date ->
                actions.onPlannedDateChange(date.takeIf { it != state.today })
                pickingDate = false
            },
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Today / Tomorrow / a picked day, all in the account's timezone. */
@Composable
private fun PlanDateChoices(
    planned: LocalDate?,
    today: LocalDate,
    enabled: Boolean,
    onChoose: (LocalDate?) -> Unit,
    onPick: () -> Unit,
) {
    val tomorrow = today.plusDays(1)
    val day = planned ?: today
    val picked = day != today && day != tomorrow
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NafsChoiceChip(text = "Today", selected = day == today, onClick = { onChoose(null) }, enabled = enabled)
        NafsChoiceChip(text = "Tomorrow", selected = day == tomorrow, onClick = { onChoose(tomorrow) }, enabled = enabled)
        NafsChoiceChip(
            text = if (picked) TaskLabels.day(day, today) else "Pick a date",
            selected = picked,
            onClick = onPick,
            enabled = enabled,
        )
    }
}

@Composable
private fun DueTimeRow(
    value: String,
    reminderLeadMinutes: Int?,
    enabled: Boolean,
    isError: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val hasTime = value.isNotBlank()
    Surface(
        onClick = onPick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = scheme.surfaceContainer,
        border = BorderStroke(1.dp, if (isError) scheme.error else scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            NafsIconBadge(Icons.Rounded.Schedule, Nafs.colors.tasks, size = 38.dp, iconSize = 20.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (hasTime) TaskLabels.time(value) else "No due time",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isError) scheme.error else scheme.onSurface,
                )
                Text(
                    when {
                        !hasTime -> "Optional · tap to pick a time"
                        reminderLeadMinutes != null -> "Reminder $reminderLeadMinutes minutes before"
                        else -> "Reminders are off on this device"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            if (hasTime) {
                IconButton(onClick = onClear, enabled = enabled) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear due time", tint = scheme.onSurfaceVariant)
                }
            } else {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 10.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DueTimeDialog(
    current: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onClear: () -> Unit,
) {
    val initial = TaskLabels.clockParts(current)
    val pickerState = rememberTimePickerState(
        initialHour = initial?.first ?: 9,
        initialMinute = initial?.second ?: 0,
        is24Hour = false,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Due time") },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = pickerState)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(TaskLabels.clockText(pickerState.hour, pickerState.minute)) }) {
                Text("Set time")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) { Text("Clear") }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/**
 * Picks a day from today up to [TaskPlanning.MAX_DAYS_AHEAD] days ahead. The picker works in UTC midnights;
 * the range is anchored on the account's today, so the choice follows the account timezone, not the phone's.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanDateDialog(
    current: LocalDate?,
    today: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val last = TaskPlanning.latestPlannableDate(today)
    val selectable = remember(today) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                TaskPlanning.isPlannable(utcDate(utcTimeMillis), today)

            override fun isSelectableYear(year: Int): Boolean = year in today.year..last.year
        }
    }
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = current?.let(::utcMillis),
        initialDisplayedMonthMillis = utcMillis(current ?: today),
        yearRange = today.year..last.year,
        selectableDates = selectable,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { pickerState.selectedDateMillis?.let { onConfirm(utcDate(it)) } },
                enabled = pickerState.selectedDateMillis != null,
            ) { Text("Plan for this day") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = pickerState)
    }
}

private fun utcMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun utcDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

private fun periodEyebrow(type: TaskPeriodType, today: LocalDate): String = when (type) {
    TaskPeriodType.DAILY -> today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH))
    TaskPeriodType.WEEKLY ->
        "Week of " + TaskPeriod.key(type, today).format(DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH))
    TaskPeriodType.MONTHLY -> today.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
}

private fun taskSummary(type: TaskPeriodType, open: Int, done: Int): String = when {
    open + done == 0 -> "Make the next move clear"
    open == 0 -> "All $done done ${type.periodPhrase()}"
    else -> "$open open · $done done ${type.periodPhrase()}"
}

private fun heroHeadline(type: TaskPeriodType, open: Int, total: Int): String = when {
    total == 0 -> "Nothing planned for ${type.periodPhrase()} yet."
    open == 0 -> "Every promise kept."
    open == 1 -> "One left. Finish strong."
    else -> "$open still open. One at a time."
}

private fun TaskPeriodType.displayName() = name.lowercase().replaceFirstChar(Char::uppercase)

private fun TaskPeriodType.currentLabel() = when (this) {
    TaskPeriodType.DAILY -> "Today's"
    TaskPeriodType.WEEKLY -> "This week's"
    TaskPeriodType.MONTHLY -> "This month's"
}

private fun TaskPeriodType.periodNoun() = when (this) {
    TaskPeriodType.DAILY -> "day"
    TaskPeriodType.WEEKLY -> "week"
    TaskPeriodType.MONTHLY -> "month"
}

private fun TaskPeriodType.periodPhrase() = when (this) {
    TaskPeriodType.DAILY -> "today"
    TaskPeriodType.WEEKLY -> "this week"
    TaskPeriodType.MONTHLY -> "this month"
}

private fun periodLabel(type: TaskPeriodType, current: Boolean): String = when (type) {
    TaskPeriodType.DAILY -> if (current) "Today" else "Yesterday"
    TaskPeriodType.WEEKLY -> if (current) "This week" else "Last week"
    TaskPeriodType.MONTHLY -> if (current) "This month" else "Last month"
}

private fun TaskPriority.displayName() = name.lowercase().replaceFirstChar(Char::uppercase)

@Composable
private fun TaskPriority.color(): Color = when (this) {
    TaskPriority.HIGH -> MaterialTheme.colorScheme.error
    TaskPriority.MEDIUM -> Nafs.colors.warning
    TaskPriority.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
}
