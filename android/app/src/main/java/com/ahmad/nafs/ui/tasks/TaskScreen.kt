package com.ahmad.nafs.ui.tasks

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
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
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TaskUiEvent.Deleted -> {
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
            onSave = viewModel::save,
            onSetCompleted = viewModel::setCompleted,
            onDelete = viewModel::delete,
            onDismissActionError = viewModel::dismissActionError,
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
    val onSave: () -> Unit = {},
    val onSetCompleted: (NafsTask, Boolean) -> Unit = { _, _ -> },
    val onDelete: (NafsTask) -> Unit = {},
    val onDismissActionError: () -> Unit = {},
)

@Composable
internal fun TaskScreen(
    state: TaskUiState,
    actions: TaskActions,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val visible = visibleTasksFor(state)
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

                else -> LazyColumn(
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
                            NafsEmptyState(
                                icon = Icons.Rounded.TaskAlt,
                                title = "No ${state.selectedType.displayName().lowercase()} tasks yet",
                                message = "Add one clear promise you can keep this ${state.selectedType.periodNoun()}.",
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
                                    onSetCompleted = actions.onSetCompleted,
                                    onEdit = actions.onOpenEdit,
                                    onDelete = actions.onDelete,
                                )
                            }
                        }
                        if (graceTasks.isNotEmpty()) {
                            item(key = "grace-title") {
                                NafsSectionTitle(
                                    periodLabel(state.selectedType, current = false),
                                    Modifier.padding(top = 10.dp),
                                    supporting = "Grace window · visible for one more ${state.selectedType.periodNoun()}",
                                )
                            }
                            items(graceTasks, key = { "task-${it.id}" }) { task ->
                                TaskCard(
                                    task = task,
                                    today = state.today,
                                    isPending = task.id in state.pendingTaskIds,
                                    onSetCompleted = actions.onSetCompleted,
                                    onEdit = actions.onOpenEdit,
                                    onDelete = actions.onDelete,
                                )
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
            val canRefresh = !state.isLoading && !state.isRefreshing
            IconButton(onClick = actions.onRefresh, enabled = canRefresh) {
                if (state.isRefreshing) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = "Refresh tasks",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (canRefresh) 1f else 0.38f),
                    )
                }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskCard(
    task: NafsTask,
    today: LocalDate,
    isPending: Boolean,
    onSetCompleted: (NafsTask, Boolean) -> Unit,
    onEdit: (NafsTask) -> Unit,
    onDelete: (NafsTask) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = Nafs.colors.tasks
    val done = task.isCompleted
    val missed = TaskPeriod.isMissed(task.type, task.periodDate, done, today)
    NafsCard(
        borderColor = if (missed) scheme.error.copy(alpha = 0.4f) else Nafs.colors.cardBorder,
        contentPadding = PaddingValues(start = 6.dp, top = 8.dp, end = 2.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            CompletionCircle(
                checked = done,
                pending = isPending,
                accent = accent,
                idleRing = if (missed) scheme.error else scheme.outline,
                label = "Complete ${task.title}",
                onToggle = { completed -> onSetCompleted(task, completed) },
            )
            Column(
                Modifier.weight(1f).padding(start = 4.dp, top = 13.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (done) scheme.onSurfaceVariant else scheme.onSurface,
                    textDecoration = if (done || missed) TextDecoration.LineThrough else TextDecoration.None,
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
                    if (missed) NafsPill("Missed", color = scheme.error, icon = Icons.Rounded.ErrorOutline)
                    NafsPill(task.priority.displayName(), color = task.priority.color(), icon = Icons.Rounded.Flag)
                    task.dueTime?.takeIf { it.isNotBlank() }?.let { due ->
                        NafsPill("Due ${displayTime(due)}", color = accent, icon = Icons.Rounded.Schedule)
                    }
                }
            }
            TaskMenu(task = task, enabled = !isPending, onEdit = onEdit, onDelete = onDelete)
        }
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
    enabled: Boolean,
    onEdit: (NafsTask) -> Unit,
    onDelete: (NafsTask) -> Unit,
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
                    onEdit(task)
                },
                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = "Edit ${task.title}" },
            )
            DropdownMenuItem(
                text = { Text("Delete", color = scheme.error) },
                onClick = {
                    expanded = false
                    onDelete(task)
                },
                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = scheme.error) },
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = "Delete ${task.title}" },
            )
        }
    }
}

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
    var pickingTime by remember { mutableStateOf(false) }
    val draft = state.draft
    val saving = state.isSaving
    val isNew = state.editingTaskId == null
    val showErrors = state.formError != null
    val titleInvalid = showErrors && TaskDraftValidator.titleError(draft.title) != null
    val timeInvalid = showErrors && TaskDraftValidator.dueTimeError(state.selectedType, draft.dueTime) != null
    val accent = Nafs.colors.tasks

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
                        "For ${periodLabel(state.selectedType, current = true).lowercase()}"
                    } else {
                        "${state.selectedType.displayName()} task"
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
                    enabled = !saving,
                    isError = timeInvalid,
                    onPick = { pickingTime = true },
                    onClear = { actions.onDueTimeChange("") },
                )
                Text(
                    "Due times use your account timezone. NAFS reminds you 30 minutes before.",
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
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun DueTimeRow(
    value: String,
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
                    if (hasTime) displayTime(value) else "No due time",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isError) scheme.error else scheme.onSurface,
                )
                Text(
                    if (hasTime) "Reminder 30 minutes before" else "Optional · tap to pick a time",
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
    val initial = clockParts(current)
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
            TextButton(onClick = { onConfirm(clockText(pickerState.hour, pickerState.minute)) }) { Text("Set time") }
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

private val clockPattern = Regex("^(\\d{1,2}):(\\d{2})")

/** Reads "HH:MM" (or "HH:MM:SS") into hour and minute, or null when it isn't a valid clock time. */
private fun clockParts(raw: String): Pair<Int, Int>? {
    val match = clockPattern.find(raw.trim()) ?: return null
    val hour = match.groupValues[1].toInt()
    val minute = match.groupValues[2].toInt()
    return if (hour in 0..23 && minute in 0..59) hour to minute else null
}

/** 24-hour, zero-padded "HH:MM": the format the ViewModel validates and saves. */
private fun clockText(hour: Int, minute: Int): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

private fun displayTime(raw: String): String = clockParts(raw)?.let { (hour, minute) ->
    LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
} ?: raw

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
