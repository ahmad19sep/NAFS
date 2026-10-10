package com.ahmad.nafs.ui.tasks

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.ui.AccountDayRefreshEffects
import com.ahmad.nafs.ui.components.NafsHeroCard
import com.ahmad.nafs.core.tasks.NafsTask
import com.ahmad.nafs.core.tasks.TaskPriority
import com.ahmad.nafs.domain.tasks.TaskPeriod
import com.ahmad.nafs.domain.tasks.TaskPeriodType
import java.time.LocalDate
import kotlinx.coroutines.flow.collect

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
        snackbarHostState = snackbarHostState,
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
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskScreen(
    state: TaskUiState,
    snackbarHostState: SnackbarHostState,
    onRefresh: () -> Unit,
    onSelectType: (TaskPeriodType) -> Unit,
    onOpenCreate: () -> Unit,
    onOpenEdit: (NafsTask) -> Unit,
    onCloseCreate: () -> Unit,
    onTitleChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onPriorityChange: (TaskPriority) -> Unit,
    onDueTimeChange: (String) -> Unit,
    onSave: () -> Unit,
    onSetCompleted: (NafsTask, Boolean) -> Unit,
    onDelete: (NafsTask) -> Unit,
    onDismissActionError: () -> Unit,
) {
    val visible = visibleTasksFor(state)
    val currentKey = TaskPeriod.key(state.selectedType, state.today)
    val previousKey = TaskPeriod.previousKey(state.selectedType, currentKey)
    val currentTasks = visible.filter { it.periodDate == currentKey }
    val graceTasks = visible.filter { it.periodDate == previousKey }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Tasks")
                        Text(
                            "Make the next move clear",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    IconButton(onClick = onRefresh, enabled = !state.isLoading && !state.isRefreshing) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh tasks")
                        }
                    }
                    IconButton(onClick = onOpenCreate) {
                        Icon(Icons.Rounded.Add, contentDescription = "Create task")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.isLoading -> LoadingTasks(modifier = Modifier.padding(padding))
            state.loadError != null && state.tasks.isEmpty() -> TaskLoadFailure(
                message = state.loadError,
                onRetry = onRefresh,
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.isOffline) {
                    item { OfflineTaskBanner() }
                }
                if (state.actionError != null) {
                    item {
                        ErrorBanner(
                            message = state.actionError,
                            onDismiss = onDismissActionError,
                        )
                    }
                }
                item {
                    TaskTypePicker(selected = state.selectedType, onSelected = onSelectType)
                }
                item { TaskProgressCard(tasks = currentTasks, type = state.selectedType) }
                if (visible.isEmpty()) {
                    item { EmptyTasks(type = state.selectedType, onCreate = onOpenCreate) }
                } else {
                    if (currentTasks.isNotEmpty()) {
                        item { PeriodHeading(periodLabel(state.selectedType, current = true)) }
                        items(currentTasks, key = NafsTask::id) { task ->
                            TaskRow(
                                task = task,
                                today = state.today,
                                isPending = task.id in state.pendingTaskIds,
                                onSetCompleted = onSetCompleted,
                                onEdit = onOpenEdit,
                                onDelete = onDelete,
                            )
                        }
                    }
                    if (graceTasks.isNotEmpty()) {
                        item {
                            PeriodHeading("${periodLabel(state.selectedType, current = false)} · grace window")
                        }
                        items(graceTasks, key = NafsTask::id) { task ->
                            TaskRow(
                                task = task,
                                today = state.today,
                                isPending = task.id in state.pendingTaskIds,
                                onSetCompleted = onSetCompleted,
                                onEdit = onOpenEdit,
                                onDelete = onDelete,
                            )
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
    }

    if (state.showCreateDialog) {
        TaskEditorDialog(
            state = state,
            onDismiss = onCloseCreate,
            onTitleChange = onTitleChange,
            onNoteChange = onNoteChange,
            onPriorityChange = onPriorityChange,
            onDueTimeChange = onDueTimeChange,
            onSave = onSave,
        )
    }
}

@Composable
private fun TaskTypePicker(
    selected: TaskPeriodType,
    onSelected: (TaskPeriodType) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TaskPeriodType.entries.forEach { type ->
            FilterChip(
                selected = selected == type,
                onClick = { onSelected(type) },
                label = { Text(type.displayName()) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TaskProgressCard(tasks: List<NafsTask>, type: TaskPeriodType) {
    val complete = tasks.count(NafsTask::isCompleted)
    val progress = if (tasks.isEmpty()) 0f else complete.toFloat() / tasks.size
    NafsHeroCard(
        eyebrow = "${type.currentLabel()} progress",
        title = "${(progress * 100).toInt()}% complete",
        body = "$complete of ${tasks.size} promises kept",
    ) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f),
            )
    }
}

@Composable
private fun TaskRow(
    task: NafsTask,
    today: LocalDate,
    isPending: Boolean,
    onSetCompleted: (NafsTask, Boolean) -> Unit,
    onEdit: (NafsTask) -> Unit,
    onDelete: (NafsTask) -> Unit,
) {
    val missed = TaskPeriod.isMissed(task.type, task.periodDate, task.isCompleted, today)
    val containerColor = when {
        task.isCompleted -> MaterialTheme.colorScheme.secondaryContainer
        missed -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (isPending) {
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            } else {
                Checkbox(
                    checked = task.isCompleted,
                    onCheckedChange = { onSetCompleted(task, it) },
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleSmall,
                    textDecoration = if (task.isCompleted || missed) {
                        TextDecoration.LineThrough
                    } else {
                        TextDecoration.None
                    },
                )
                task.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        task.priority.displayName(),
                        color = task.priority.color(),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    task.dueTime?.let {
                        Text("Due $it", style = MaterialTheme.typography.labelSmall)
                    }
                    if (missed) {
                        Text(
                            "MISSED",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            Column {
                IconButton(onClick = { onEdit(task) }, enabled = !isPending) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Edit ${task.title}")
                }
                IconButton(onClick = { onDelete(task) }, enabled = !isPending) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete ${task.title}")
                }
            }
        }
    }
}

@Composable
private fun TaskEditorDialog(
    state: TaskUiState,
    onDismiss: () -> Unit,
    onTitleChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onPriorityChange: (TaskPriority) -> Unit,
    onDueTimeChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (state.editingTaskId == null) {
                    "New ${state.selectedType.displayName().lowercase()} task"
                } else {
                    "Edit task"
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = state.draft.title,
                    onValueChange = onTitleChange,
                    label = { Text("Task") },
                    singleLine = true,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.draft.note,
                    onValueChange = onNoteChange,
                    label = { Text("Note (optional)") },
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Priority", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TaskPriority.entries.reversed().forEach { priority ->
                        FilterChip(
                            selected = state.draft.priority == priority,
                            onClick = { onPriorityChange(priority) },
                            label = { Text(priority.displayName()) },
                            enabled = !state.isSaving,
                        )
                    }
                }
                if (state.selectedType == TaskPeriodType.DAILY) {
                    OutlinedTextField(
                        value = state.draft.dueTime,
                        onValueChange = onDueTimeChange,
                        label = { Text("Due time (HH:MM, optional)") },
                        singleLine = true,
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Due times use your account timezone. Android delivers best-effort alerts 1 hour and 30 minutes before.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                state.formError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = !state.isSaving) {
                if (state.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (state.editingTaskId == null) "Create" else "Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isSaving) { Text("Cancel") }
        },
    )
}

@Composable
private fun OfflineTaskBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.CloudOff, contentDescription = null)
            Text("Showing the last synced tasks. Changes require a connection.")
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

@Composable
private fun EmptyTasks(type: TaskPeriodType, onCreate: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("No ${type.displayName().lowercase()} tasks yet", style = MaterialTheme.typography.titleMedium)
        Text("Create one for this ${type.periodNoun()}.", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onCreate) { Text("Create task") }
    }
}

@Composable
private fun TaskLoadFailure(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Tasks unavailable", style = MaterialTheme.typography.titleLarge)
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
private fun LoadingTasks(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun PeriodHeading(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium)
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

private fun periodLabel(type: TaskPeriodType, current: Boolean): String = when (type) {
    TaskPeriodType.DAILY -> if (current) "Today" else "Yesterday"
    TaskPeriodType.WEEKLY -> if (current) "This week" else "Last week"
    TaskPeriodType.MONTHLY -> if (current) "This month" else "Last month"
}

private fun TaskPriority.displayName() = name.lowercase().replaceFirstChar(Char::uppercase)

@Composable
private fun TaskPriority.color(): Color = when (this) {
    TaskPriority.HIGH -> MaterialTheme.colorScheme.error
    TaskPriority.MEDIUM -> MaterialTheme.colorScheme.primary
    TaskPriority.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
}
