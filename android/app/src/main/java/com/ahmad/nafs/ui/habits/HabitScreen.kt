package com.ahmad.nafs.ui.habits

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.AlarmAdd
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmad.nafs.core.habits.Habit
import com.ahmad.nafs.core.habits.HabitLog
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.domain.habits.HabitStreak
import com.ahmad.nafs.domain.habits.HabitTimerRules
import com.ahmad.nafs.domain.habits.HabitTimerState
import com.ahmad.nafs.domain.habits.HabitType
import com.ahmad.nafs.domain.habits.ScheduleKind
import com.ahmad.nafs.ui.AccountDayRefreshEffects
import com.ahmad.nafs.ui.components.NafsBanner
import com.ahmad.nafs.ui.components.NafsBannerKind
import com.ahmad.nafs.ui.components.NafsCard
import com.ahmad.nafs.ui.components.NafsChoiceChip
import com.ahmad.nafs.ui.components.NafsEmojiBadge
import com.ahmad.nafs.ui.components.NafsEmptyState
import com.ahmad.nafs.ui.components.NafsErrorState
import com.ahmad.nafs.ui.components.NafsEyebrow
import com.ahmad.nafs.ui.components.NafsHeroSurface
import com.ahmad.nafs.ui.components.NafsLoadingState
import com.ahmad.nafs.ui.components.NafsPageHeader
import com.ahmad.nafs.ui.components.NafsPill
import com.ahmad.nafs.ui.components.NafsPrimaryButton
import com.ahmad.nafs.ui.components.NafsProgressBar
import com.ahmad.nafs.ui.components.NafsProgressRing
import com.ahmad.nafs.ui.components.NafsSecondaryButton
import com.ahmad.nafs.ui.components.NafsSectionTitle
import com.ahmad.nafs.ui.components.NafsSegmented
import com.ahmad.nafs.ui.components.NafsSpace
import com.ahmad.nafs.ui.components.NafsStat
import com.ahmad.nafs.ui.components.NafsTextField
import com.ahmad.nafs.ui.theme.Nafs
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HabitRoute(
    profile: UserProfile,
    createOnOpen: Boolean = false,
    viewModel: HabitViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(profile.userId, profile.timezone) {
        viewModel.initialize(profile.userId, profile.timezone)
        if (createOnOpen) viewModel.openCreateDefinition()
    }
    AccountDayRefreshEffects(profile.timezone, viewModel::refresh)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is HabitUiEvent.Completed -> haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                is HabitUiEvent.FocusSaved -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    // Its own coroutine, so a snackbar on screen never holds back the next event's haptic.
                    launch { snackbarHostState.showSnackbar("Added ${event.minutes} min to ${event.name}.") }
                }
                is HabitUiEvent.Archived -> launch {
                    val result = snackbarHostState.showSnackbar(
                        message = "Archived ${event.name}",
                        actionLabel = "Undo",
                        withDismissAction = true,
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.restoreArchived(event.habitId, event.name)
                }
            }
        }
    }
    HabitScreen(
        state = state,
        actions = HabitActions(
            onRefresh = viewModel::refresh,
            onOpenCreate = viewModel::openCreateDefinition,
            onDismissError = viewModel::dismissError,
            onToggleSimple = viewModel::toggleSimple,
            onAddOne = viewModel::addOne,
            onOpenLog = viewModel::openLog,
            onOpenTimer = viewModel::openTimer,
            onOpenEdit = viewModel::openEditDefinition,
            onOpenHistory = viewModel::openHistory,
            onCloseHistory = viewModel::closeHistory,
            onOpenDatedLog = viewModel::openDatedLog,
            onSetPaused = viewModel::setPaused,
            onArchive = viewModel::requestArchive,
            onConfirmArchive = viewModel::confirmArchive,
            onDismissArchive = viewModel::dismissArchive,
            onRetryRestore = viewModel::retryRestore,
            onDismissRestore = viewModel::dismissRestoreFailure,
            onCloseLog = viewModel::closeLog,
            onAmountChange = viewModel::setAmount,
            onNotesChange = viewModel::setNotes,
            onSaveLog = viewModel::saveEditor,
        ),
        editorActions = HabitEditorActions(
            onClose = viewModel::closeDefinition,
            onSave = viewModel::saveDefinition,
            onNameChange = viewModel::setDefinitionName,
            onEmojiChange = viewModel::setDefinitionEmoji,
            onTypeChange = viewModel::setDefinitionType,
            onTargetChange = viewModel::setDefinitionTarget,
            onUnitChange = viewModel::setDefinitionUnit,
            onDurationChange = viewModel::setDefinitionDuration,
            onSubjectNameChange = viewModel::setDefinitionSubjectName,
            onSubjectTotalChange = viewModel::setDefinitionSubjectTotal,
            onSubjectPositionChange = viewModel::setDefinitionSubjectPosition,
            onSubjectUnitChange = viewModel::setDefinitionSubjectUnit,
            onScheduleChange = viewModel::setDefinitionSchedule,
            onWeeklyTargetChange = viewModel::setDefinitionWeeklyTarget,
            onToggleDay = viewModel::toggleDefinitionDay,
            onReminderChange = viewModel::setDefinitionReminder,
        ),
        timerActions = HabitTimerActions(
            onStart = viewModel::startTimer,
            onPause = viewModel::pauseTimer,
            onFinish = viewModel::finishTimer,
            onDiscard = viewModel::discardTimer,
            onClose = viewModel::closeTimer,
        ),
        snackbarHostState = snackbarHostState,
    )
}

/** List, logging and history actions for the Habits screen. */
internal data class HabitActions(
    val onRefresh: () -> Unit = {},
    val onOpenCreate: () -> Unit = {},
    val onDismissError: () -> Unit = {},
    val onToggleSimple: (Habit) -> Unit = {},
    val onAddOne: (Habit) -> Unit = {},
    val onOpenLog: (Habit) -> Unit = {},
    val onOpenTimer: (Habit) -> Unit = {},
    val onOpenEdit: (Habit) -> Unit = {},
    val onOpenHistory: (Habit) -> Unit = {},
    val onCloseHistory: () -> Unit = {},
    val onOpenDatedLog: (Habit, LocalDate) -> Unit = { _, _ -> },
    val onSetPaused: (Habit, Boolean) -> Unit = { _, _ -> },
    val onArchive: (Habit) -> Unit = {},
    val onConfirmArchive: () -> Unit = {},
    val onDismissArchive: () -> Unit = {},
    val onRetryRestore: () -> Unit = {},
    val onDismissRestore: () -> Unit = {},
    val onCloseLog: () -> Unit = {},
    val onAmountChange: (String) -> Unit = {},
    val onNotesChange: (String) -> Unit = {},
    val onSaveLog: () -> Unit = {},
)

/** Actions for the create / edit habit sheet. */
internal data class HabitEditorActions(
    val onClose: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onNameChange: (String) -> Unit = {},
    val onEmojiChange: (String) -> Unit = {},
    val onTypeChange: (HabitType) -> Unit = {},
    val onTargetChange: (String) -> Unit = {},
    val onUnitChange: (String) -> Unit = {},
    val onDurationChange: (String) -> Unit = {},
    val onSubjectNameChange: (String) -> Unit = {},
    val onSubjectTotalChange: (String) -> Unit = {},
    val onSubjectPositionChange: (String) -> Unit = {},
    val onSubjectUnitChange: (String) -> Unit = {},
    val onScheduleChange: (ScheduleKind) -> Unit = {},
    val onWeeklyTargetChange: (String) -> Unit = {},
    val onToggleDay: (DayOfWeek) -> Unit = {},
    val onReminderChange: (LocalTime?) -> Unit = {},
)

/** Focus timer controls; starting again after a pause resumes the same session. */
internal data class HabitTimerActions(
    val onStart: () -> Unit = {},
    val onPause: () -> Unit = {},
    val onFinish: () -> Unit = {},
    val onDiscard: () -> Unit = {},
    val onClose: () -> Unit = {},
)

/** A per-week habit's progress through the current Monday-to-Sunday week. */
private data class WeekProgress(val done: Int, val target: Int) {
    val met: Boolean get() = done >= target
}

@Composable
internal fun HabitScreen(
    state: HabitUiState,
    actions: HabitActions,
    editorActions: HabitEditorActions,
    timerActions: HabitTimerActions = HabitTimerActions(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val scheduled = state.scheduledHabits()
    val resting = state.restingHabits()
    val paused = state.pausedHabits()
    val weeks = remember(state.habits, state.logs, state.today) {
        state.habits.filter { it.scheduleKind == ScheduleKind.PER_WEEK }.associate { habit ->
            habit.id to WeekProgress(state.weekCount(habit), HabitStreak.weeklyTarget(habit.schedule()))
        }
    }
    val completed = scheduled.count { state.todayLog(it.id)?.completed == true || weeks[it.id]?.met == true }
    val streaks = remember(state.habits, state.logs, state.today) {
        state.habits.filterNot(Habit::isPaused).associate { habit -> habit.id to state.liveStreak(habit) }
    }
    val topStreak = streaks.values.maxOrNull() ?: 0
    val summary = if (scheduled.isEmpty()) {
        "Build a rhythm that lasts"
    } else {
        "$completed of ${scheduled.size} done today"
    }
    val blockingError = state.error?.takeIf { state.habits.isEmpty() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).statusBarsPadding()) {
            when {
                state.isLoading -> Column(Modifier.fillMaxSize()) {
                    HabitsHeader(state, summary, actions, Modifier.padding(horizontal = NafsSpace.screen))
                    NafsLoadingState(Modifier.weight(1f), cards = 4)
                }

                blockingError != null -> Column(Modifier.fillMaxSize()) {
                    HabitsHeader(state, summary, actions, Modifier.padding(horizontal = NafsSpace.screen))
                    NafsErrorState(
                        title = "Habits didn't load",
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
                    item(key = "header") { HabitsHeader(state, summary, actions) }

                    if (state.isOffline) {
                        item(key = "offline") {
                            NafsBanner(
                                "Showing cached habits. Changes need a connection.",
                                kind = NafsBannerKind.OFFLINE,
                                actionLabel = "Retry",
                                onAction = actions.onRefresh,
                            )
                        }
                    }
                    state.error?.let { message ->
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
                            NafsBanner(message, kind = NafsBannerKind.ERROR, onDismiss = actions.onDismissError)
                        }
                    }
                    state.restoreFailed?.let { failed ->
                        item(key = "restore-failed") {
                            NafsBanner(
                                "${failed.name} is still archived, with its history kept. Try again once you're online.",
                                kind = NafsBannerKind.ERROR,
                                title = "Couldn't restore",
                                actionLabel = "Retry",
                                onAction = actions.onRetryRestore,
                                onDismiss = actions.onDismissRestore,
                            )
                        }
                    }

                    if (state.habits.isEmpty()) {
                        item(key = "empty") {
                            NafsEmptyState(
                                icon = Icons.Rounded.Autorenew,
                                title = "Start one small rhythm",
                                message = "Choose something clear enough to repeat even on a busy day.",
                                tint = Nafs.colors.habits,
                                actionLabel = "Create first habit",
                                onAction = actions.onOpenCreate,
                            )
                        }
                    } else {
                        item(key = "hero") {
                            HabitHero(
                                completed = completed,
                                scheduledCount = scheduled.size,
                                topStreak = topStreak,
                                offToday = resting.size + paused.size,
                            )
                        }
                        item(key = "today-title") {
                            NafsSectionTitle(
                                "Today",
                                Modifier.padding(top = 10.dp),
                                supporting = if (scheduled.isEmpty()) {
                                    "No habits are scheduled today."
                                } else {
                                    "${scheduled.size} ${if (scheduled.size == 1) "habit" else "habits"} in your plan"
                                },
                            )
                        }
                        items(scheduled, key = { "habit-${it.id}" }) { habit ->
                            HabitCard(
                                habit = habit,
                                log = state.todayLog(habit.id),
                                streak = streaks[habit.id] ?: 0,
                                pending = habit.id in state.pendingHabitIds,
                                canLog = true,
                                week = weeks[habit.id],
                                timer = state.timer?.takeIf { it.habitId == habit.id },
                                actions = actions,
                            )
                        }

                        if (resting.isNotEmpty()) {
                            item(key = "resting-title") {
                                NafsSectionTitle(
                                    "Resting today",
                                    Modifier.padding(top = 10.dp),
                                    supporting = "Still editable; these return on their scheduled days.",
                                )
                            }
                            items(resting, key = { "habit-${it.id}" }) { habit ->
                                HabitCard(
                                    habit = habit,
                                    log = null,
                                    streak = streaks[habit.id] ?: 0,
                                    pending = habit.id in state.pendingHabitIds,
                                    canLog = false,
                                    week = null,
                                    timer = null,
                                    actions = actions,
                                )
                            }
                        }

                        if (paused.isNotEmpty()) {
                            item(key = "paused-title") {
                                NafsSectionTitle(
                                    "Paused",
                                    Modifier.padding(top = 10.dp),
                                    supporting = "Paused habits do not build or break streaks.",
                                )
                            }
                            items(paused, key = { "habit-${it.id}" }) { habit ->
                                HabitCard(
                                    habit = habit,
                                    log = null,
                                    streak = 0,
                                    pending = habit.id in state.pendingHabitIds,
                                    canLog = false,
                                    week = null,
                                    timer = null,
                                    actions = actions,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    state.confirmArchiveHabitId?.let { archiveId ->
        state.habits.firstOrNull { it.id == archiveId }?.let { habit ->
            ArchiveDialog(habit, onConfirm = actions.onConfirmArchive, onDismiss = actions.onDismissArchive)
        }
    }
    state.historyHabitId?.let { historyId ->
        state.habits.firstOrNull { it.id == historyId }?.let { habit ->
            HabitSheet(busy = false, onDismiss = actions.onCloseHistory) { close ->
                HabitHistoryContent(
                    habit = habit,
                    logs = state.logs.filter { log -> log.habitId == historyId }.sortedByDescending(HabitLog::date),
                    today = state.today,
                    onEditEntry = { date -> actions.onOpenDatedLog(habit, date) },
                    onClose = close,
                )
            }
        }
    }
    state.editingHabitId?.let { editingId ->
        state.habits.firstOrNull { it.id == editingId }?.let { habit ->
            HabitSheet(busy = state.isSaving, onDismiss = actions.onCloseLog) { close ->
                HabitLogContent(habit = habit, state = state, actions = actions, onCancel = close)
            }
        }
    }
    state.timerHabitId?.let { timerId ->
        state.habits.firstOrNull { it.id == timerId }?.let { habit ->
            HabitSheet(busy = state.isFinishingTimer, onDismiss = timerActions.onClose) { close ->
                val session = state.timer?.takeIf { it.habitId == habit.id }
                HabitTimerContent(
                    habit = habit,
                    session = session,
                    loggedMinutes = state.todayLog(habit.id)?.durationMinutes ?: 0,
                    nowMillis = rememberTimerNow(session),
                    isFinishing = state.isFinishingTimer,
                    error = state.timerError,
                    notice = state.timerNotice,
                    actions = timerActions,
                    onClose = close,
                )
            }
        }
    }
    state.definitionDraft?.let { draft ->
        HabitSheet(busy = state.isSavingDefinition, onDismiss = editorActions.onClose) { close ->
            HabitEditorContent(
                draft = draft,
                isSaving = state.isSavingDefinition,
                error = state.definitionError,
                actions = editorActions,
                onCancel = close,
            )
        }
    }
}

@Composable
private fun HabitsHeader(
    state: HabitUiState,
    summary: String,
    actions: HabitActions,
    modifier: Modifier = Modifier,
) {
    NafsPageHeader(
        title = "Habits",
        modifier = modifier,
        eyebrow = state.today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)),
        subtitle = summary,
        actions = {
            val canRefresh = !state.isLoading && !state.isRefreshing
            IconButton(onClick = actions.onRefresh, enabled = canRefresh) {
                if (state.isRefreshing) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = "Refresh habits",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (canRefresh) 1f else 0.38f),
                    )
                }
            }
            Surface(
                onClick = actions.onOpenCreate,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(start = 2.dp).size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, contentDescription = "Create habit", modifier = Modifier.size(24.dp))
                }
            }
        },
    )
}

@Composable
private fun HabitHero(completed: Int, scheduledCount: Int, topStreak: Int, offToday: Int) {
    val c = Nafs.colors
    val progress = if (scheduledCount == 0) 0f else completed.toFloat() / scheduledCount
    val left = scheduledCount - completed
    val headline = when {
        scheduledCount == 0 -> "A rest day. Nothing is scheduled."
        left == 0 -> "Every habit kept today."
        left == 1 -> "One to go. Close the loop."
        else -> "$left to go. Small and steady."
    }
    NafsHeroSurface {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                NafsProgressRing(
                    progress = progress,
                    size = 100.dp,
                    stroke = 10.dp,
                    colors = listOf(c.heroGold, c.heroMint),
                    trackColor = Color.White.copy(alpha = 0.12f),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.headlineSmall, color = c.onHero)
                        Text("complete", style = MaterialTheme.typography.labelSmall, color = c.onHeroMuted)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    NafsEyebrow("Today's rhythm", color = c.heroGold)
                    Text(headline, style = MaterialTheme.typography.titleLarge, color = c.onHero)
                    Text(
                        "$completed of $scheduledCount scheduled habits done",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.onHeroMuted,
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.10f)))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                HeroStat("$completed/$scheduledCount", "Done", Modifier.weight(1f))
                HeroStat("$topStreak", "Top streak", Modifier.weight(1f))
                HeroStat("$offToday", "Off today", Modifier.weight(1f))
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

@Composable
private fun HabitCard(
    habit: Habit,
    log: HabitLog?,
    streak: Int,
    pending: Boolean,
    canLog: Boolean,
    week: WeekProgress?,
    timer: HabitTimerState?,
    actions: HabitActions,
) {
    val scheme = MaterialTheme.colorScheme
    NafsCard(
        color = if (habit.isPaused) scheme.surfaceContainerLow else scheme.surfaceContainer,
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NafsEmojiBadge(habit.emoji, if (habit.isPaused) scheme.onSurfaceVariant else Nafs.colors.habits, size = 46.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    habit.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (habit.isPaused) scheme.onSurfaceVariant else scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${habit.type.displayName()} · ${habit.scheduleLabel()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // Paused habits don't remind, so the bell would promise something that won't happen.
                    if (!habit.isPaused) habit.reminderTime?.let { time -> ReminderMark(time) }
                }
            }
            if (streak > 0) StreakPill(streak, perWeek = habit.scheduleKind == ScheduleKind.PER_WEEK)
            HabitMenu(habit = habit, pending = pending, actions = actions)
        }
        Column(
            Modifier.padding(top = 14.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (canLog) {
                LoggableBody(habit, log, pending, week, timer, actions)
            } else {
                RestingBody(habit, pending, actions)
            }
            log?.notes?.takeIf { it.isNotBlank() }?.let { note -> NoteLine(note) }
        }
    }
}

/** The small bell and time on a card with a reminder. */
@Composable
private fun ReminderMark(time: LocalTime) {
    val label = clockLabel(time)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.clearAndSetSemantics { contentDescription = "Reminder at $label" },
    ) {
        Icon(
            Icons.Rounded.NotificationsActive,
            contentDescription = null,
            tint = Nafs.colors.habits,
            modifier = Modifier.size(13.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun LoggableBody(
    habit: Habit,
    log: HabitLog?,
    pending: Boolean,
    week: WeekProgress?,
    timer: HabitTimerState?,
    actions: HabitActions,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = Nafs.colors.habits
    val done = log?.completed == true
    if (habit.type == HabitType.SIMPLE) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    week?.let { "${it.done} of ${it.target} this week" } ?: progressText(habit, log),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (done || week?.met == true) accent else scheme.onSurface,
                )
                Text(
                    when {
                        done -> "Logged for today."
                        week?.met == true -> "Done for this week. Extra days still count."
                        else -> "Tap the circle once it's done."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            CompletionCircle(
                checked = done,
                pending = pending,
                accent = accent,
                idleRing = scheme.outline,
                label = "${habit.name} done today",
                onToggle = { actions.onToggleSimple(habit) },
            )
        }
    } else {
        val measure = habit.measure(log)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f)) {
                Text(
                    measure.value,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.alignByBaseline(),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    measure.rest,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            if (pending) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = accent)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (habit.type) {
                        HabitType.COUNTER -> QuickActionButton(
                            description = "Add one to ${habit.name}",
                            onClick = { actions.onAddOne(habit) },
                        ) {
                            Text("+1", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                        }
                        HabitType.DURATION -> QuickActionButton(
                            description = if (timer == null) "Start timer for ${habit.name}" else "Open timer for ${habit.name}",
                            onClick = { actions.onOpenTimer(habit) },
                        ) {
                            Icon(Icons.Rounded.Timer, contentDescription = null, modifier = Modifier.size(22.dp))
                        }
                        else -> Unit
                    }
                    PillButton(
                        text = if (log == null) "Log" else "Edit log",
                        icon = if (log == null) Icons.Rounded.Add else Icons.Rounded.Edit,
                        description = if (log == null) "Log ${habit.name}" else "Edit log for ${habit.name}",
                        onClick = { actions.onOpenLog(habit) },
                    )
                }
            }
        }
        NafsProgressBar(measure.fraction, color = accent, height = 6.dp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                measure.caption,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            timer?.let { session -> TimerMark(session) }
            when {
                week?.met == true -> NafsPill("Done this week", color = Nafs.colors.success, icon = Icons.Rounded.Check)
                week != null -> NafsPill("${week.done}/${week.target} this week", color = accent)
                done -> NafsPill("Done today", color = Nafs.colors.success, icon = Icons.Rounded.Check)
            }
        }
    }
}

/** A focus session in progress on this habit, ticking while it runs. */
@Composable
private fun TimerMark(session: HabitTimerState) {
    val now = rememberTimerNow(session)
    val clock = HabitTimerRules.clock(session.elapsedMillis(now))
    NafsPill(
        text = if (session.isRunning) clock else "$clock paused",
        color = Nafs.colors.habits,
        icon = if (session.isRunning) Icons.Rounded.Timer else Icons.Rounded.Pause,
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = if (session.isRunning) "Timer running, $clock" else "Timer paused at $clock"
        },
    )
}

/** Round tonal button for the one-tap action beside Log: +1 for counters, the timer for durations. */
@Composable
private fun QuickActionButton(description: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    val accent = Nafs.colors.habits
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = accent.copy(alpha = if (Nafs.colors.isDark) 0.18f else 0.12f),
        contentColor = accent,
        modifier = Modifier.size(44.dp).semantics {
            contentDescription = description
            role = Role.Button
        },
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun RestingBody(habit: Habit, pending: Boolean, actions: HabitActions) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            habit.targetSummary(),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        when {
            pending -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Nafs.colors.habits)
            }
            habit.isPaused -> PillButton(
                text = "Resume",
                icon = Icons.Rounded.PlayArrow,
                description = "Resume ${habit.name}",
                onClick = { actions.onSetPaused(habit, false) },
            )
            else -> NafsPill("Rest day", color = scheme.onSurfaceVariant, icon = Icons.Rounded.Bedtime)
        }
    }
}

@Composable
private fun NoteLine(note: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Icon(
            Icons.AutoMirrored.Rounded.EventNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 1.dp).size(15.dp),
        )
        Text(
            note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StreakPill(streak: Int, perWeek: Boolean) {
    val unit = if (perWeek) "week" else "day"
    NafsPill(
        text = "$streak ${if (streak == 1) unit else "${unit}s"}",
        color = Nafs.colors.gold,
        icon = Icons.Rounded.Whatshot,
        modifier = Modifier.clearAndSetSemantics { contentDescription = "$streak $unit streak" },
    )
}

@Composable
private fun PillButton(text: String, icon: ImageVector, description: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = scheme.primaryContainer,
            contentColor = scheme.onPrimaryContainer,
        ),
        contentPadding = PaddingValues(start = 14.dp, end = 18.dp),
        modifier = Modifier.height(40.dp).semantics { contentDescription = description },
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun HabitMenu(habit: Habit, pending: Boolean, actions: HabitActions) {
    var expanded by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "Options for ${habit.name}", tint = scheme.onSurfaceVariant)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.medium,
            containerColor = scheme.surfaceContainerHigh,
        ) {
            HabitMenuItem(
                text = "Edit habit",
                icon = Icons.Rounded.Edit,
                description = "Edit ${habit.name}",
                enabled = !pending,
                onClick = {
                    expanded = false
                    actions.onOpenEdit(habit)
                },
            )
            HabitMenuItem(
                text = if (habit.isPaused) "Resume habit" else "Pause habit",
                icon = if (habit.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                description = if (habit.isPaused) "Resume ${habit.name}" else "Pause ${habit.name}",
                enabled = !pending,
                onClick = {
                    expanded = false
                    actions.onSetPaused(habit, !habit.isPaused)
                },
            )
            HabitMenuItem(
                text = "History and notes",
                icon = Icons.AutoMirrored.Rounded.EventNote,
                description = "History and notes for ${habit.name}",
                enabled = true,
                onClick = {
                    expanded = false
                    actions.onOpenHistory(habit)
                },
            )
            HabitMenuItem(
                text = "Archive",
                icon = Icons.Rounded.Archive,
                description = "Archive ${habit.name}",
                enabled = !pending,
                onClick = {
                    expanded = false
                    actions.onArchive(habit)
                },
            )
        }
    }
}

@Composable
private fun HabitMenuItem(text: String, icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        onClick = onClick,
        leadingIcon = { Icon(icon, contentDescription = null) },
        enabled = enabled,
        modifier = Modifier.semantics { contentDescription = description },
    )
}

@Composable
private fun ArchiveDialog(habit: Habit, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Archive, contentDescription = null) },
        title = { Text("Archive ${habit.name}?") },
        text = {
            Text(
                "It leaves your habit list" +
                    (if (habit.reminderTime != null) " and its reminder stops" else "") +
                    ". Its history stays in your reports.",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Archive") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Round, animated check: fills with the habit colour when done and shows a spinner while saving. */
@Composable
private fun CompletionCircle(
    checked: Boolean,
    pending: Boolean,
    accent: Color,
    idleRing: Color,
    label: String,
    onToggle: (Boolean) -> Unit,
) {
    val fill by animateColorAsState(if (checked) accent else Color.Transparent, label = "habit-check-fill")
    val ring by animateColorAsState(if (checked) accent else idleRing, label = "habit-check-ring")
    val markScale by animateFloatAsState(if (checked) 1f else 0f, label = "habit-check-mark")
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .toggleable(value = checked, enabled = !pending, role = Role.Checkbox, onValueChange = onToggle)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (pending) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = accent)
        } else {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(fill).border(2.dp, ring, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(20.dp).scale(markScale),
                )
            }
        }
    }
}

/** Modal sheet that refuses to close while a save is in flight, matching the ViewModel's own guards. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HabitSheet(
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

/**
 * The wall clock while [session] runs, ticking as its elapsed seconds change; a paused or absent session needs no
 * clock, so nothing ticks (and renders stay still).
 */
@Composable
internal fun rememberTimerNow(session: HabitTimerState?): Long {
    val now by produceState(System.currentTimeMillis(), session) {
        value = System.currentTimeMillis()
        val running = session?.takeIf { it.isRunning } ?: return@produceState
        while (true) {
            delay(1_000L - running.elapsedMillis(value) % 1_000L)
            value = System.currentTimeMillis()
        }
    }
    return now
}

// ─── Log sheet ───────────────────────────────────────────────────────────────

/** The entry editor for one habit and one date. Stateless so it can be rendered on its own. */
@Composable
internal fun HabitLogContent(habit: Habit, state: HabitUiState, actions: HabitActions, onCancel: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val saving = state.isSaving
    val entryDate = state.editingDate ?: state.today
    val label = when (habit.type) {
        HabitType.DURATION -> "Minutes"
        HabitType.SUBJECT -> habit.subjectUnit ?: "Amount today"
        else -> habit.unit.ifBlank { "Amount" }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = NafsSpace.screen)
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            NafsEmojiBadge(habit.emoji, Nafs.colors.habits, size = 48.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                NafsEyebrow(
                    "Entry date: " + if (entryDate == state.today) {
                        "today"
                    } else {
                        entryDate.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH))
                    },
                    color = Nafs.colors.habits,
                )
                Text(
                    "Log ${habit.name}",
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (habit.type == HabitType.SIMPLE) {
            val current = state.amount.toDoubleOrNull()
            NafsSegmented(
                options = listOf("Not done", "Done"),
                selectedIndex = when {
                    current == null -> -1
                    current >= 1.0 -> 1
                    else -> 0
                },
                onSelect = { index -> if (!saving) actions.onAmountChange(if (index == 1) "1" else "0") },
            )
        } else {
            AmountStepper(
                amount = state.amount,
                step = if (habit.type == HabitType.DURATION) 5.0 else 1.0,
                unit = label.lowercase(),
                caption = habit.logTargetCaption(),
                enabled = !saving,
                onChange = actions.onAmountChange,
            )
            NafsTextField(
                value = state.amount,
                onValueChange = actions.onAmountChange,
                label = label,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = state.formError != null,
                enabled = !saving,
            )
        }

        NafsTextField(
            value = state.notes,
            onValueChange = actions.onNotesChange,
            label = "Notes (optional)",
            leadingIcon = Icons.AutoMirrored.Rounded.EventNote,
            enabled = !saving,
            singleLine = false,
            minLines = 2,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )

        if (habit.type == HabitType.SUBJECT) {
            Text(
                "Enter today's absolute progress. Safe retries replace today's amount instead of double-counting it.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }

        state.formError?.let { message -> NafsBanner(message, kind = NafsBannerKind.ERROR) }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel, enabled = !saving, modifier = Modifier.weight(1f).height(54.dp)) {
                Text("Cancel")
            }
            NafsPrimaryButton(
                text = "Save",
                onClick = actions.onSaveLog,
                loading = saving,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Big number between round − and + buttons. Writes the same text the amount field holds. */
@Composable
private fun AmountStepper(
    amount: String,
    step: Double,
    unit: String,
    caption: String?,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val current = amount.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
    NafsCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton(
                icon = Icons.Rounded.Remove,
                description = "Decrease $unit",
                enabled = enabled && current > 0.0,
                onClick = { onChange(formatProgress((current - step).coerceAtLeast(0.0))) },
            )
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    amount.ifBlank { "0" },
                    style = MaterialTheme.typography.displaySmall,
                    color = if (amount.isBlank()) scheme.onSurfaceVariant else scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(unit, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                caption?.let { text ->
                    Text(text, style = MaterialTheme.typography.bodySmall, color = Nafs.colors.habits)
                }
            }
            StepButton(
                icon = Icons.Rounded.Add,
                description = "Increase $unit",
                enabled = enabled,
                onClick = { onChange(formatProgress(current + step)) },
            )
        }
    }
}

@Composable
private fun StepButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val accent = Nafs.colors.habits
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (enabled) accent.copy(alpha = if (Nafs.colors.isDark) 0.18f else 0.12f) else scheme.onSurface.copy(alpha = 0.05f),
        contentColor = if (enabled) accent else scheme.onSurfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.size(56.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(26.dp))
        }
    }
}

// ─── Habit editor sheet ──────────────────────────────────────────────────────

private val suggestedEmoji = listOf("⭐", "📖", "💧", "🏃", "🧘", "🕌", "✍️", "🍎", "💤", "🏋️", "🎯", "🙏")

/** Where the time picker starts when a habit has no reminder yet. */
private val defaultReminderTime: LocalTime = LocalTime.of(9, 0)

/** Create / edit habit form. Stateless so it can be rendered on its own. */
@Composable
internal fun HabitEditorContent(
    draft: HabitDefinitionDraftState,
    isSaving: Boolean,
    error: String?,
    actions: HabitEditorActions,
    onCancel: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val numeric = KeyboardOptions(keyboardType = KeyboardType.Number)
    val isNew = draft.habitId == null

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
            NafsEmojiBadge(draft.emoji, Nafs.colors.habits, size = 52.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (isNew) "Create habit" else "Edit habit", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Small enough to repeat on a busy day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NafsTextField(
                    value = draft.emoji,
                    onValueChange = actions.onEmojiChange,
                    label = "Emoji",
                    modifier = Modifier.width(96.dp),
                    enabled = !isSaving,
                )
                NafsTextField(
                    value = draft.name,
                    onValueChange = actions.onNameChange,
                    label = "Habit name",
                    modifier = Modifier.weight(1f),
                    enabled = !isSaving,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                suggestedEmoji.forEach { emoji ->
                    EmojiOption(
                        emoji = emoji,
                        isSelected = draft.emoji == emoji,
                        enabled = !isSaving,
                        onClick = { actions.onEmojiChange(emoji) },
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FieldLabel("Habit type")
            HabitType.entries.chunked(2).forEach { pair ->
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { type ->
                        TypeTile(
                            type = type,
                            isSelected = draft.type == type,
                            enabled = !isSaving,
                            onClick = { actions.onTypeChange(type) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
            when (draft.type) {
                HabitType.SIMPLE -> Text(
                    "A simple yes-or-no habit completed once per scheduled day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                HabitType.COUNTER -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NafsTextField(
                        value = draft.targetValue,
                        onValueChange = actions.onTargetChange,
                        label = "Daily target",
                        modifier = Modifier.weight(1f),
                        keyboardOptions = numeric,
                        enabled = !isSaving,
                    )
                    NafsTextField(
                        value = draft.unit,
                        onValueChange = actions.onUnitChange,
                        label = "Unit",
                        placeholder = "glasses",
                        modifier = Modifier.weight(1f),
                        enabled = !isSaving,
                    )
                }
                HabitType.DURATION -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NafsTextField(
                        value = draft.timeTargetMinutes,
                        onValueChange = actions.onDurationChange,
                        label = "Target minutes",
                        keyboardOptions = numeric,
                        enabled = !isSaving,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 30, 45, 60).forEach { minutes ->
                            NafsChoiceChip(
                                text = "$minutes min",
                                selected = draft.timeTargetMinutes.trim() == minutes.toString(),
                                onClick = { actions.onDurationChange(minutes.toString()) },
                                enabled = !isSaving,
                            )
                        }
                    }
                }
                HabitType.SUBJECT -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NafsTextField(
                        value = draft.subjectName,
                        onValueChange = actions.onSubjectNameChange,
                        label = "Book, course, or subject",
                        enabled = !isSaving,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NafsTextField(
                            value = draft.subjectPosition,
                            onValueChange = actions.onSubjectPositionChange,
                            label = "Now at",
                            modifier = Modifier.weight(1f),
                            keyboardOptions = numeric,
                            enabled = !isSaving,
                        )
                        NafsTextField(
                            value = draft.subjectTotal,
                            onValueChange = actions.onSubjectTotalChange,
                            label = "Total",
                            modifier = Modifier.weight(1f),
                            keyboardOptions = numeric,
                            enabled = !isSaving,
                        )
                        NafsTextField(
                            value = draft.subjectUnit,
                            onValueChange = actions.onSubjectUnitChange,
                            label = "Unit",
                            modifier = Modifier.weight(1f),
                            enabled = !isSaving,
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FieldLabel("Schedule")
            NafsSegmented(
                options = ScheduleKind.entries.map { it.displayName() },
                selectedIndex = ScheduleKind.entries.indexOf(draft.scheduleKind),
                onSelect = { index -> if (!isSaving) actions.onScheduleChange(ScheduleKind.entries[index]) },
            )
            when (draft.scheduleKind) {
                ScheduleKind.DAILY -> Text(
                    "Every day counts toward the streak.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                ScheduleKind.WEEKDAYS -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        RoundToggle(
                            label = day.shortLabel(),
                            description = day.fullLabel(),
                            isSelected = day in draft.scheduleDays,
                            enabled = !isSaving,
                            onClick = { actions.onToggleDay(day) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                ScheduleKind.PER_WEEK -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..7).forEach { count ->
                            RoundToggle(
                                label = "$count",
                                description = "$count times per week",
                                isSelected = draft.weeklyTarget.trim() == count.toString(),
                                enabled = !isSaving,
                                onClick = { actions.onWeeklyTargetChange(count.toString()) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Text(
                        "Times per week (1–7), on any days you choose.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FieldLabel("Reminder")
            ReminderSetting(time = draft.reminderTime, enabled = !isSaving, onChange = actions.onReminderChange)
        }

        error?.let { message -> NafsBanner(message, kind = NafsBannerKind.ERROR) }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel, enabled = !isSaving, modifier = Modifier.weight(1f).height(54.dp)) {
                Text("Cancel")
            }
            NafsPrimaryButton(
                text = if (isNew) "Create habit" else "Save changes",
                onClick = actions.onSave,
                loading = isSaving,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Off by default; once set, the time with ways to change or remove it. */
@Composable
private fun ReminderSetting(time: LocalTime?, enabled: Boolean, onChange: (LocalTime?) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var picking by rememberSaveable { mutableStateOf(false) }
    if (time == null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NafsSecondaryButton(
                text = "Add reminder",
                onClick = { picking = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                icon = Icons.Rounded.AlarmAdd,
            )
            Text(
                "Off. Set a time to get a nudge on scheduled days while the habit is still open.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    } else {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = scheme.surfaceContainer,
            border = BorderStroke(1.dp, scheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Rounded.NotificationsActive,
                    contentDescription = null,
                    tint = Nafs.colors.habits,
                    modifier = Modifier.size(22.dp),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Remind me at ${clockLabel(time)}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Scheduled days, until it's done",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { picking = true }, enabled = enabled) { Text("Change") }
                IconButton(onClick = { onChange(null) }, enabled = enabled) {
                    Icon(Icons.Rounded.Close, contentDescription = "Remove reminder", tint = scheme.onSurfaceVariant)
                }
            }
        }
    }
    if (picking) {
        ReminderTimeDialog(
            initial = time ?: defaultReminderTime,
            onDismiss = { picking = false },
            onConfirm = { picked ->
                picking = false
                onChange(picked)
            },
        )
    }
}

/** The Material time picker, as on the Health and Tasks screens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val pickerState = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = false,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reminder time") },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = pickerState)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(pickerState.hour, pickerState.minute)) }) {
                Text("Set reminder")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun EmojiOption(emoji: String, isSelected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) scheme.primaryContainer else scheme.surfaceContainer,
        border = BorderStroke(1.dp, if (isSelected) scheme.primary else scheme.outlineVariant),
        modifier = Modifier.size(46.dp).semantics {
            contentDescription = "Use $emoji"
            selected = isSelected
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun TypeTile(
    type: HabitType,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) scheme.primaryContainer else scheme.surfaceContainer,
        contentColor = if (isSelected) scheme.onPrimaryContainer else scheme.onSurface,
        border = BorderStroke(1.dp, if (isSelected) scheme.primary else scheme.outlineVariant),
        modifier = modifier.semantics {
            selected = isSelected
            role = Role.RadioButton
        },
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                type.icon(),
                contentDescription = null,
                tint = if (isSelected) scheme.primary else scheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Text(type.displayName(), style = MaterialTheme.typography.titleSmall)
            Text(type.caption(), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}

/** Pill toggle used for weekdays and the weekly target, sized to share a row of seven. */
@Composable
private fun RoundToggle(
    label: String,
    description: String,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (isSelected) scheme.primary else scheme.surfaceContainer,
        contentColor = if (isSelected) scheme.onPrimary else scheme.onSurface,
        border = if (isSelected) null else BorderStroke(1.dp, scheme.outlineVariant),
        modifier = modifier.height(44.dp).semantics {
            contentDescription = if (isSelected) "$description, selected" else description
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

// ─── Text helpers ────────────────────────────────────────────────────────────

private data class HabitMeasure(val value: String, val rest: String, val fraction: Float, val caption: String)

private fun Habit.measure(log: HabitLog?): HabitMeasure = when (type) {
    HabitType.COUNTER -> {
        val current = log?.value ?: 0.0
        HabitMeasure(
            value = formatProgress(current),
            rest = "/ ${formatProgress(targetValue)} $unit".trimEnd(),
            fraction = fractionOf(current, targetValue),
            caption = if (current >= targetValue) {
                "Target reached"
            } else {
                "${formatProgress(targetValue - current)} $unit to go".replace("  ", " ")
            },
        )
    }
    HabitType.DURATION -> {
        val minutes = log?.durationMinutes ?: 0
        HabitMeasure(
            value = "$minutes",
            rest = "/ $timeTargetMinutes minutes",
            fraction = fractionOf(minutes.toDouble(), timeTargetMinutes.toDouble()),
            caption = if (minutes >= timeTargetMinutes) "Target reached" else "${timeTargetMinutes - minutes} minutes to go",
        )
    }
    HabitType.SUBJECT -> {
        val units = subjectUnit ?: "units"
        val today = log?.let { entry -> "${formatProgress(entry.value)} $units today" }
        HabitMeasure(
            value = "$subjectPosition",
            rest = "/ ${subjectTotal ?: "?"} $units",
            fraction = fractionOf(subjectPosition.toDouble(), (subjectTotal ?: 0).toDouble()),
            caption = listOfNotNull(subjectName?.takeIf { name -> name.isNotBlank() }, today)
                .joinToString(" · ")
                .ifEmpty { "Nothing logged today" },
        )
    }
    HabitType.SIMPLE -> HabitMeasure(
        value = if (log?.completed == true) "1" else "0",
        rest = "/ 1",
        fraction = if (log?.completed == true) 1f else 0f,
        caption = progressText(this, log),
    )
}

private fun fractionOf(done: Double, total: Double): Float =
    if (total <= 0.0) 0f else (done / total).toFloat().coerceIn(0f, 1f)

private fun progressText(habit: Habit, log: HabitLog?): String = when (habit.type) {
    HabitType.SIMPLE -> if (log?.completed == true) "Done today" else "Not done yet"
    HabitType.COUNTER -> "${formatProgress(log?.value ?: 0.0)} / ${formatProgress(habit.targetValue)} ${habit.unit}"
    HabitType.DURATION -> "${log?.durationMinutes ?: 0} / ${habit.timeTargetMinutes} minutes"
    HabitType.SUBJECT -> "${habit.subjectPosition} / ${habit.subjectTotal ?: "?"} ${habit.subjectUnit ?: "units"}"
}

/** What a resting or paused habit asks for, without pretending today's progress is zero. */
private fun Habit.targetSummary(): String = when (type) {
    HabitType.SIMPLE -> "Once per scheduled day"
    HabitType.COUNTER -> "Target ${formatProgress(targetValue)} $unit".trimEnd()
    HabitType.DURATION -> "Target $timeTargetMinutes minutes"
    HabitType.SUBJECT -> "$subjectPosition / ${subjectTotal ?: "?"} ${subjectUnit ?: "units"}"
}

private fun Habit.logTargetCaption(): String? = when (type) {
    HabitType.SIMPLE -> null
    HabitType.COUNTER -> "Target ${formatProgress(targetValue)}"
    HabitType.DURATION -> "Target $timeTargetMinutes minutes"
    HabitType.SUBJECT -> "Now at $subjectPosition of ${subjectTotal ?: "?"}"
}

internal fun entryAmount(habit: Habit, log: HabitLog): String = when (habit.type) {
    HabitType.SIMPLE -> if (log.completed) "Marked done" else "Not done"
    HabitType.COUNTER -> "${formatProgress(log.value)} ${habit.unit}".trimEnd()
    HabitType.DURATION -> "${log.durationMinutes} minutes"
    HabitType.SUBJECT -> "${formatProgress(log.value)} ${habit.subjectUnit ?: "units"}"
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

private fun HabitType.caption(): String = when (this) {
    HabitType.SIMPLE -> "Done or not, once a day"
    HabitType.COUNTER -> "Count toward a target"
    HabitType.DURATION -> "Minutes spent"
    HabitType.SUBJECT -> "Work through a book or course"
}

private fun HabitType.icon(): ImageVector = when (this) {
    HabitType.SIMPLE -> Icons.Rounded.CheckCircle
    HabitType.COUNTER -> Icons.Rounded.AddCircle
    HabitType.DURATION -> Icons.Rounded.Schedule
    HabitType.SUBJECT -> Icons.AutoMirrored.Rounded.MenuBook
}

private fun ScheduleKind.displayName(): String = when (this) {
    ScheduleKind.DAILY -> "Daily"
    ScheduleKind.WEEKDAYS -> "Set days"
    ScheduleKind.PER_WEEK -> "Per week"
}

private fun DayOfWeek.shortLabel(): String = name.take(3).lowercase().replaceFirstChar(Char::uppercase)

private fun DayOfWeek.fullLabel(): String = name.lowercase().replaceFirstChar(Char::uppercase)

/** "8:30 PM", the way times read elsewhere in the app. */
internal fun clockLabel(time: LocalTime): String =
    time.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))

internal fun formatProgress(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
