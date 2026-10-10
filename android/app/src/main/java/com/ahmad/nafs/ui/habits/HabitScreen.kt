package com.ahmad.nafs.ui.habits

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmad.nafs.core.habits.Habit
import com.ahmad.nafs.core.habits.HabitLog
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.domain.habits.HabitType
import com.ahmad.nafs.domain.habits.ScheduleKind
import com.ahmad.nafs.ui.AccountDayRefreshEffects
import com.ahmad.nafs.ui.components.NafsHeroCard
import com.ahmad.nafs.ui.components.NafsSectionTitle
import com.ahmad.nafs.ui.components.NafsStatusPill
import java.time.DayOfWeek

@Composable
fun HabitRoute(
    profile: UserProfile,
    createOnOpen: Boolean = false,
    viewModel: HabitViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(profile.userId, profile.timezone) {
        viewModel.initialize(profile.userId, profile.timezone)
        if (createOnOpen) viewModel.openCreateDefinition()
    }
    AccountDayRefreshEffects(profile.timezone, viewModel::refresh)
    HabitScreen(state, viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HabitScreen(
    state: HabitUiState,
    viewModel: HabitViewModel,
) {
    val scheduled = state.scheduledHabits()
    val resting = state.restingHabits()
    val paused = state.pausedHabits()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Habits")
                        Text(
                            "Build a rhythm that lasts",
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
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh habits")
                        }
                    }
                    IconButton(onClick = viewModel::openCreateDefinition) {
                        Icon(Icons.Rounded.Add, contentDescription = "Create habit")
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

            state.error != null && state.habits.isEmpty() -> ErrorState(
                state.error,
                viewModel::refresh,
                Modifier.padding(padding),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { HabitProgressCard(state, scheduled) }

                if (state.isOffline) item { OfflineBanner() }

                state.actionError?.let { message ->
                    item { ErrorBanner(message, viewModel::dismissError) }
                }

                if (state.habits.isEmpty()) {
                    item { EmptyHabits(viewModel::openCreateDefinition) }
                } else {
                    item {
                        NafsSectionTitle(
                            title = "Today",
                            supporting = if (scheduled.isEmpty()) {
                                "No habits are scheduled today."
                            } else {
                                "${scheduled.size} ${if (scheduled.size == 1) "habit" else "habits"} in your plan"
                            },
                        )
                    }
                    items(scheduled, key = Habit::id) { habit ->
                        HabitRow(
                            habit = habit,
                            log = state.todayLog(habit.id),
                            streak = state.liveStreak(habit),
                            pending = habit.id in state.pendingHabitIds,
                            canLog = true,
                            onToggleSimple = viewModel::toggleSimple,
                            onOpenLog = viewModel::openLog,
                            onEdit = viewModel::openEditDefinition,
                                onHistory = viewModel::openHistory,
                            onSetPaused = viewModel::setPaused,
                        )
                    }

                    if (resting.isNotEmpty()) {
                        item {
                            NafsSectionTitle(
                                title = "Resting today",
                                supporting = "Still editable; these return on their scheduled days.",
                            )
                        }
                        items(resting, key = Habit::id) { habit ->
                            HabitRow(
                                habit = habit,
                                log = null,
                                streak = state.liveStreak(habit),
                                pending = habit.id in state.pendingHabitIds,
                                canLog = false,
                                onToggleSimple = viewModel::toggleSimple,
                                onOpenLog = viewModel::openLog,
                                onEdit = viewModel::openEditDefinition,
                                onHistory = viewModel::openHistory,
                                onSetPaused = viewModel::setPaused,
                            )
                        }
                    }

                    if (paused.isNotEmpty()) {
                        item {
                            NafsSectionTitle(
                                title = "Paused",
                                supporting = "Paused habits do not build or break streaks.",
                            )
                        }
                        items(paused, key = Habit::id) { habit ->
                            HabitRow(
                                habit = habit,
                                log = null,
                                streak = 0,
                                pending = habit.id in state.pendingHabitIds,
                                canLog = false,
                                onToggleSimple = viewModel::toggleSimple,
                                onOpenLog = viewModel::openLog,
                                onEdit = viewModel::openEditDefinition,
                                onHistory = viewModel::openHistory,
                                onSetPaused = viewModel::setPaused,
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }

    state.historyHabitId?.let { id ->
        val habit = state.habits.firstOrNull { it.id == id }
        if (habit != null) AlertDialog(onDismissRequest = viewModel::closeHistory, title = { Text("${habit.name} ? history") }, text = {
            LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text("Most recent 400 days. Open a saved entry to correct its amount or notes.") }
                val logs = state.logs.filter { it.habitId == id }.sortedByDescending { it.date }
                if (logs.isEmpty()) item { Text("No saved entries.") }
                items(logs) { log -> Column { Text("${log.date}: ${if (log.completed) "Complete" else "Recorded"} ? ${log.value} ? ${log.durationMinutes} minutes"); log.notes?.let { Text(it) }; TextButton(onClick = { viewModel.openDatedLog(habit, log.date) }) { Text("Edit entry") } } }
            }
        }, confirmButton = { TextButton(viewModel::closeHistory) { Text("Close") } })
    }
    state.editingHabitId?.let { id ->
        state.habits.firstOrNull { it.id == id }?.let { habit ->
            LogDialog(
                habit,
                state,
                viewModel::closeLog,
                viewModel::setAmount,
                viewModel::setNotes,
                viewModel::saveEditor,
            )
        }
    }
    state.definitionDraft?.let { draft ->
        HabitDefinitionDialog(draft, state, viewModel)
    }
}

@Composable
private fun HabitProgressCard(state: HabitUiState, scheduled: List<Habit>) {
    val completed = scheduled.count { state.todayLog(it.id)?.completed == true }
    val progress = if (scheduled.isEmpty()) 0f else completed.toFloat() / scheduled.size
    NafsHeroCard(
        eyebrow = "Today's rhythm",
        title = "${(progress * 100).toInt()}% complete",
        body = "$completed of ${scheduled.size} scheduled habits logged",
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
private fun HabitRow(
    habit: Habit,
    log: HabitLog?,
    streak: Int,
    pending: Boolean,
    canLog: Boolean,
    onToggleSimple: (Habit) -> Unit,
    onOpenLog: (Habit) -> Unit,
    onEdit: (Habit) -> Unit,
    onHistory: (Habit) -> Unit,
    onSetPaused: (Habit, Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (habit.isPaused) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(habit.emoji, style = MaterialTheme.typography.headlineSmall)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        habit.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${habit.type.displayName()} · ${habit.scheduleLabel()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onEdit(habit) }, enabled = !pending) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Edit ${habit.name}")
                }
                IconButton(
                    onClick = { onSetPaused(habit, !habit.isPaused) },
                    enabled = !pending,
                ) {
                    Icon(
                        if (habit.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        contentDescription = if (habit.isPaused) {
                            "Resume ${habit.name}"
                        } else {
                            "Pause ${habit.name}"
                        },
                    )
                }
            }
            TextButton(onClick = { onHistory(habit) }) { Text("Notes and log history") }
            log?.notes?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(progressText(habit, log), style = MaterialTheme.typography.bodyMedium)
                    if (streak > 0) {
                        Text(
                            "$streak ${if (habit.scheduleKind == ScheduleKind.PER_WEEK) "week" else "day"} streak",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                when {
                    pending -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    !canLog -> NafsStatusPill(if (habit.isPaused) "Paused" else "Rest day", accent = false)
                    habit.type == HabitType.SIMPLE -> Checkbox(
                        checked = log?.completed == true,
                        onCheckedChange = { onToggleSimple(habit) },
                    )
                    else -> Button(onClick = { onOpenLog(habit) }) {
                        Text(if (log == null) "Log" else "Edit log")
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitDefinitionDialog(
    draft: HabitDefinitionDraftState,
    state: HabitUiState,
    viewModel: HabitViewModel,
) {
    val numericKeyboard = KeyboardOptions(keyboardType = KeyboardType.Number)
    AlertDialog(
        onDismissRequest = viewModel::closeDefinition,
        title = { Text(if (draft.habitId == null) "Create habit" else "Edit habit") },
        text = {
            Column(
                Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = draft.emoji,
                        onValueChange = viewModel::setDefinitionEmoji,
                        label = { Text("Emoji") },
                        modifier = Modifier.width(84.dp),
                        singleLine = true,
                        enabled = !state.isSavingDefinition,
                    )
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = viewModel::setDefinitionName,
                        label = { Text("Habit name") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        enabled = !state.isSavingDefinition,
                    )
                }

                EditorLabel("Habit type")
                HabitType.entries.chunked(2).forEach { types ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        types.forEach { type ->
                            FilterChip(
                                selected = draft.type == type,
                                onClick = { viewModel.setDefinitionType(type) },
                                label = { Text(type.displayName()) },
                                modifier = Modifier.weight(1f),
                                enabled = !state.isSavingDefinition,
                            )
                        }
                    }
                }

                when (draft.type) {
                    HabitType.SIMPLE -> Text(
                        "A simple yes-or-no habit completed once per scheduled day.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HabitType.COUNTER -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            draft.targetValue,
                            viewModel::setDefinitionTarget,
                            label = { Text("Daily target") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = numericKeyboard,
                            singleLine = true,
                            enabled = !state.isSavingDefinition,
                        )
                        OutlinedTextField(
                            draft.unit,
                            viewModel::setDefinitionUnit,
                            label = { Text("Unit") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            enabled = !state.isSavingDefinition,
                        )
                    }
                    HabitType.DURATION -> OutlinedTextField(
                        draft.timeTargetMinutes,
                        viewModel::setDefinitionDuration,
                        label = { Text("Target minutes") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = numericKeyboard,
                        singleLine = true,
                        enabled = !state.isSavingDefinition,
                    )
                    HabitType.SUBJECT -> {
                        OutlinedTextField(
                            draft.subjectName,
                            viewModel::setDefinitionSubjectName,
                            label = { Text("Book, course, or subject") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !state.isSavingDefinition,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                draft.subjectPosition,
                                viewModel::setDefinitionSubjectPosition,
                                label = { Text("Now at") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = numericKeyboard,
                                singleLine = true,
                                enabled = !state.isSavingDefinition,
                            )
                            OutlinedTextField(
                                draft.subjectTotal,
                                viewModel::setDefinitionSubjectTotal,
                                label = { Text("Total") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = numericKeyboard,
                                singleLine = true,
                                enabled = !state.isSavingDefinition,
                            )
                            OutlinedTextField(
                                draft.subjectUnit,
                                viewModel::setDefinitionSubjectUnit,
                                label = { Text("Unit") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                enabled = !state.isSavingDefinition,
                            )
                        }
                    }
                }

                EditorLabel("Schedule")
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ScheduleKind.entries.forEach { kind ->
                        FilterChip(
                            selected = draft.scheduleKind == kind,
                            onClick = { viewModel.setDefinitionSchedule(kind) },
                            label = { Text(kind.displayName()) },
                            modifier = Modifier.weight(1f),
                            enabled = !state.isSavingDefinition,
                        )
                    }
                }
                if (draft.scheduleKind == ScheduleKind.WEEKDAYS) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        DayOfWeek.entries.forEach { day ->
                            FilterChip(
                                selected = day in draft.scheduleDays,
                                onClick = { viewModel.toggleDefinitionDay(day) },
                                label = { Text(day.shortLabel()) },
                                enabled = !state.isSavingDefinition,
                            )
                        }
                    }
                }
                if (draft.scheduleKind == ScheduleKind.PER_WEEK) {
                    OutlinedTextField(
                        draft.weeklyTarget,
                        viewModel::setDefinitionWeeklyTarget,
                        label = { Text("Times per week (1–7)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = numericKeyboard,
                        singleLine = true,
                        enabled = !state.isSavingDefinition,
                    )
                }
                state.definitionError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = viewModel::saveDefinition, enabled = !state.isSavingDefinition) {
                if (state.isSavingDefinition) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (draft.habitId == null) "Create" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::closeDefinition, enabled = !state.isSavingDefinition) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun EditorLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun LogDialog(
    habit: Habit,
    state: HabitUiState,
    onDismiss: () -> Unit,
    onAmountChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    val label = when (habit.type) {
        HabitType.DURATION -> "Minutes"
        HabitType.SUBJECT -> habit.subjectUnit ?: "Amount today"
        else -> habit.unit.ifBlank { "Amount" }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log ${habit.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Entry date: ${state.editingDate ?: state.today}")
                OutlinedTextField(
                    state.amount,
                    onAmountChange,
                    label = { Text(label) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !state.isSaving,
                    singleLine = true,
                )
                OutlinedTextField(
                    state.notes,
                    onNotesChange,
                    label = { Text("Notes (optional)") },
                    enabled = !state.isSaving,
                )
                if (habit.type == HabitType.SUBJECT) {
                    Text(
                        "Enter today's absolute progress. Safe retries replace today's amount instead of double-counting it.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                state.formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = !state.isSaving) {
                Text(if (state.isSaving) "Saving…" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isSaving) { Text("Cancel") }
        },
    )
}

@Composable
private fun OfflineBanner() {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.CloudOff, contentDescription = null)
            Text("Showing cached habits. Changes need a connection.")
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
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
private fun EmptyHabits(onCreate: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Start one small rhythm", style = MaterialTheme.typography.titleLarge)
            Text(
                "Choose something clear enough to repeat even on a busy day.",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Button(onClick = onCreate) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Create first habit")
            }
        }
    }
}

private fun progressText(habit: Habit, log: HabitLog?): String = when (habit.type) {
    HabitType.SIMPLE -> if (log?.completed == true) "Done today" else "Not done yet"
    HabitType.COUNTER -> "${formatProgress(log?.value ?: 0.0)} / ${formatProgress(habit.targetValue)} ${habit.unit}"
    HabitType.DURATION -> "${log?.durationMinutes ?: 0} / ${habit.timeTargetMinutes} minutes"
    HabitType.SUBJECT -> "${habit.subjectPosition} / ${habit.subjectTotal ?: "?"} ${habit.subjectUnit ?: "units"}"
}

private fun Habit.scheduleLabel(): String = when (scheduleKind) {
    ScheduleKind.DAILY -> "Daily"
    ScheduleKind.WEEKDAYS -> scheduleDays.sortedBy(DayOfWeek::getValue).joinToString(" · ") {
        it.shortLabel()
    }
    ScheduleKind.PER_WEEK -> "${weeklyTarget ?: 1}× per week"
}

private fun HabitType.displayName(): String = when (this) {
    HabitType.SIMPLE -> "Yes / No"
    HabitType.COUNTER -> "Counter"
    HabitType.DURATION -> "Duration"
    HabitType.SUBJECT -> "Subject"
}

private fun ScheduleKind.displayName(): String = when (this) {
    ScheduleKind.DAILY -> "Daily"
    ScheduleKind.WEEKDAYS -> "Days"
    ScheduleKind.PER_WEEK -> "Per week"
}

private fun DayOfWeek.shortLabel(): String = name.take(3).lowercase().replaceFirstChar(Char::uppercase)

private fun formatProgress(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

@Composable
private fun ErrorState(message: String, retry: () -> Unit, modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message)
            Button(onClick = retry, modifier = Modifier.padding(top = 12.dp)) { Text("Retry") }
        }
    }
}
