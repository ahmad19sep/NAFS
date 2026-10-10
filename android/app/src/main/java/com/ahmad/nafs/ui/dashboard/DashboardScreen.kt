package com.ahmad.nafs.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmad.nafs.core.goals.Goal
import com.ahmad.nafs.core.habits.Habit
import com.ahmad.nafs.core.habits.HabitLog
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.core.tasks.NafsTask
import com.ahmad.nafs.core.tasks.TaskPriority
import com.ahmad.nafs.domain.habits.HabitType
import com.ahmad.nafs.domain.habits.ScheduleKind
import com.ahmad.nafs.ui.AccountDayRefreshEffects
import com.ahmad.nafs.ui.components.NafsAvatar
import com.ahmad.nafs.ui.components.NafsBanner
import com.ahmad.nafs.ui.components.NafsBannerKind
import com.ahmad.nafs.ui.components.NafsCard
import com.ahmad.nafs.ui.components.NafsCelebration
import com.ahmad.nafs.ui.components.NafsDivider
import com.ahmad.nafs.ui.components.NafsEmojiBadge
import com.ahmad.nafs.ui.components.NafsEyebrow
import com.ahmad.nafs.ui.components.NafsGroup
import com.ahmad.nafs.ui.components.NafsHeroSurface
import com.ahmad.nafs.ui.components.NafsIconBadge
import com.ahmad.nafs.ui.components.NafsListRow
import com.ahmad.nafs.ui.components.NafsLoadingState
import com.ahmad.nafs.ui.components.NafsPill
import com.ahmad.nafs.ui.components.NafsProgressBar
import com.ahmad.nafs.ui.components.NafsProgressRing
import com.ahmad.nafs.ui.components.NafsSectionTitle
import com.ahmad.nafs.ui.components.NafsSpace
import com.ahmad.nafs.ui.components.NafsStat
import com.ahmad.nafs.ui.tasks.TaskLabels
import com.ahmad.nafs.ui.theme.Nafs
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun DashboardRoute(
    profile: UserProfile,
    onOpenTasks: () -> Unit,
    onOpenHabits: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenChallenges: () -> Unit,
    onOpenHealth: () -> Unit,
    onOpenDeen: () -> Unit,
    onOpenCheckIn: () -> Unit,
    onOpenCoach: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenProfile: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Counts bursts asked for while Home is on screen; starting from 0 again (rotation) never replays one.
    var celebration by remember { mutableIntStateOf(0) }
    LaunchedEffect(profile.userId, profile.timezone) {
        viewModel.initialize(profile.userId, profile.timezone)
    }
    AccountDayRefreshEffects(profile.timezone, viewModel::refresh)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                DashboardUiEvent.DayComplete -> celebration += 1
            }
        }
    }
    DashboardScreen(
        profile = profile,
        state = state,
        actions = DashboardActions(
            onRefresh = viewModel::refresh,
            onDismissMessage = viewModel::dismissMessage,
            onDismissActionError = viewModel::dismissActionError,
            onSetTaskCompleted = viewModel::setTaskCompleted,
            onSetHabitCompleted = viewModel::setHabitCompleted,
            onOpenTasks = onOpenTasks,
            onOpenHabits = onOpenHabits,
            onOpenGoals = onOpenGoals,
            onOpenChallenges = onOpenChallenges,
            onOpenHealth = onOpenHealth,
            onOpenDeen = onOpenDeen,
            onOpenCheckIn = onOpenCheckIn,
            onOpenCoach = onOpenCoach,
            onOpenLibrary = onOpenLibrary,
            onOpenProfile = onOpenProfile,
        ),
        insight = { DashboardInsightCard(profile, onOpenLibrary, onOpenCoach) },
        nudge = { NotificationNudge() },
        celebration = celebration,
    )
}

/** Everything Home can open or change, grouped so the screen stays a pure function of its state. */
internal data class DashboardActions(
    val onRefresh: () -> Unit = {},
    val onDismissMessage: () -> Unit = {},
    val onDismissActionError: () -> Unit = {},
    val onSetTaskCompleted: (NafsTask, Boolean) -> Unit = { _, _ -> },
    val onSetHabitCompleted: (Habit, Boolean) -> Unit = { _, _ -> },
    val onOpenTasks: () -> Unit = {},
    val onOpenHabits: () -> Unit = {},
    val onOpenGoals: () -> Unit = {},
    val onOpenChallenges: () -> Unit = {},
    val onOpenHealth: () -> Unit = {},
    val onOpenDeen: () -> Unit = {},
    val onOpenCheckIn: () -> Unit = {},
    val onOpenCoach: () -> Unit = {},
    val onOpenLibrary: () -> Unit = {},
    val onOpenProfile: () -> Unit = {},
)

/** How many tasks or habits Home lists before pointing to the full screen. */
private const val TODAY_ROWS = 6

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardScreen(
    profile: UserProfile,
    state: DashboardUiState,
    actions: DashboardActions,
    insight: @Composable () -> Unit = {},
    nudge: @Composable () -> Unit = {},
    /** Changes each time the last open item for today is completed here; plays a short burst. */
    celebration: Int = 0,
) {
    val zone = runCatching { ZoneId.of(profile.timezone) }.getOrDefault(ZoneId.of("UTC"))
    val now by produceState(initialValue = ZonedDateTime.now(zone), zone) {
        while (true) {
            value = ZonedDateTime.now(zone)
            delay(30_000L)
        }
    }
    val metrics = state.metrics()
    val nextTask = state.nextTask()
    val leadingGoals = state.leadingGoals()
    val agenda = state.todayAgenda()
    val todayHabits = state.todayHabits()

    if (state.isLoading) {
        NafsLoadingState(Modifier.statusBarsPadding())
        return
    }

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = actions.onRefresh,
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = NafsSpace.screen, end = NafsSpace.screen, top = 8.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "header") {
                    HomeHeader(
                        profile = profile,
                        now = now,
                        canRefresh = !state.isLoading && !state.isRefreshing,
                        onRefresh = actions.onRefresh,
                        onOpenProfile = actions.onOpenProfile,
                    )
                }

                if (state.isOffline || profile.isFromCache) item(key = "offline") {
                    NafsBanner(
                        "Showing your saved data. Changes need a connection.",
                        kind = NafsBannerKind.OFFLINE,
                        actionLabel = "Retry",
                        onAction = actions.onRefresh,
                    )
                }
                state.message?.let { message ->
                    item(key = "message") {
                        NafsBanner(message, kind = NafsBannerKind.WARNING, onDismiss = actions.onDismissMessage)
                    }
                }

                item(key = "hero") {
                    // The nudge renders nothing once notifications work, so it shares the hero's item.
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        nudge()
                        TodayHero(metrics, now, profile.timezone, onOpenTasks = actions.onOpenTasks)
                    }
                }

                nextTask?.let { task -> item(key = "next-up") { NextUpCard(task, actions.onOpenTasks) } }

                if (agenda.isNotEmpty() || todayHabits.isNotEmpty()) {
                    item(key = "today-title") {
                        val open = (metrics.totalTasks - metrics.completedTasks) + (metrics.totalHabits - metrics.completedHabits)
                        NafsSectionTitle(
                            "Today's list",
                            supporting = if (open == 0) "Everything for today is done." else "$open open · tick them off as you go",
                            actionLabel = "All tasks",
                            onAction = actions.onOpenTasks,
                        )
                    }
                    state.actionError?.let { error ->
                        item(key = "today-error") {
                            NafsBanner(
                                error.message,
                                kind = NafsBannerKind.ERROR,
                                title = error.title,
                                onDismiss = actions.onDismissActionError,
                            )
                        }
                    }
                    if (agenda.isNotEmpty()) item(key = "today-tasks") { TodayTasks(agenda, state, actions) }
                    if (todayHabits.isNotEmpty()) item(key = "today-habits") { TodayHabits(todayHabits, state, actions) }
                }

                item(key = "shortcuts") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ShortcutTile(
                            title = "Check-in",
                            subtitle = if (now.hour < 15) "Plan today" else "Reflect",
                            icon = Icons.Rounded.WbTwilight,
                            tint = Nafs.colors.reflect,
                            onClick = actions.onOpenCheckIn,
                            modifier = Modifier.weight(1f),
                        )
                        ShortcutTile(
                            title = "Coach",
                            subtitle = "Talk it through",
                            icon = Icons.AutoMirrored.Rounded.Chat,
                            tint = Nafs.colors.coach,
                            onClick = actions.onOpenCoach,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                item(key = "areas-title") { NafsSectionTitle("Your areas", supporting = "Tap any area to log or plan.") }
                item(key = "areas") { AreaGrid(profile, metrics, state.challengeSummary(), actions) }

                item(key = "insight") { insight() }

                if (leadingGoals.isNotEmpty()) {
                    item(key = "goals-title") {
                        NafsSectionTitle(
                            "Goals in motion",
                            supporting = "Your nearest unfinished horizons.",
                            actionLabel = "All goals",
                            onAction = actions.onOpenGoals,
                        )
                    }
                    leadingGoals.forEach { goal ->
                        item(key = "dashboard-goal-${goal.id}") { GoalRow(goal, actions.onOpenGoals) }
                    }
                }

                item(key = "footer") {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Shield, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        Text(
                            "Private by design · days follow ${profile.timezone}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        NafsCelebration(trigger = celebration, modifier = Modifier.matchParentSize())
    }
}

@Composable
private fun HomeHeader(
    profile: UserProfile,
    now: ZonedDateTime,
    canRefresh: Boolean,
    onRefresh: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    val firstName = profile.name.trim().substringBefore(' ').ifBlank { "there" }
    val greeting = if (profile.deenEnabled) "Assalamu alaikum" else when (now.hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Good night"
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            NafsEyebrow(now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)), color = MaterialTheme.colorScheme.primary)
            Text("$greeting,", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(firstName, style = MaterialTheme.typography.headlineLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // Pull to refresh shows progress; the button stays for anyone who can't pull.
        IconButton(onClick = onRefresh, enabled = canRefresh) {
            Icon(
                Icons.Rounded.Refresh,
                contentDescription = "Refresh dashboard",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (canRefresh) 1f else 0.38f),
            )
        }
        Surface(
            onClick = onOpenProfile,
            shape = CircleShape,
            color = Color.Transparent,
            modifier = Modifier.semantics { contentDescription = "Your profile and settings" },
        ) { NafsAvatar(profile.name, profile.avatarUrl, size = 46.dp) }
    }
}

@Composable
private fun TodayHero(metrics: DashboardMetrics, now: ZonedDateTime, timezone: String, onOpenTasks: () -> Unit) {
    val done = metrics.completedTasks + metrics.completedHabits
    val total = metrics.totalTasks + metrics.totalHabits
    val progress = if (total == 0) 0f else done.toFloat() / total
    val c = Nafs.colors
    NafsHeroSurface {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                NafsProgressRing(
                    progress = progress,
                    size = 108.dp,
                    stroke = 10.dp,
                    colors = listOf(c.heroGold, c.heroMint),
                    trackColor = Color.White.copy(alpha = 0.12f),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.headlineMedium, color = c.onHero)
                        Text("today", style = MaterialTheme.typography.labelSmall, color = c.onHeroMuted)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    NafsEyebrow("Your day, honestly", color = c.heroGold)
                    Text(heroHeadline(metrics), style = MaterialTheme.typography.titleLarge, color = c.onHero)
                    Text(
                        "${now.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))} · $timezone",
                        style = MaterialTheme.typography.labelMedium,
                        color = c.onHeroMuted,
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.10f)))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                HeroStat("Tasks", "${metrics.completedTasks}/${metrics.totalTasks}", fraction(metrics.completedTasks, metrics.totalTasks), c.heroSky, Modifier.weight(1f))
                HeroStat("Habits", "${metrics.completedHabits}/${metrics.totalHabits}", fraction(metrics.completedHabits, metrics.totalHabits), c.heroMint, Modifier.weight(1f))
                HeroStat("Goals", "${metrics.averageGoalProgress}%", metrics.averageGoalProgress / 100f, c.heroGold, Modifier.weight(1f))
            }
            if (total == 0) {
                Surface(onClick = onOpenTasks, shape = MaterialTheme.shapes.medium, color = Color.White.copy(alpha = 0.08f)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Add one deliberate task to start the day", Modifier.weight(1f), color = c.onHero,
                            style = MaterialTheme.typography.labelLarge)
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.onHero)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, progress: Float, color: Color, modifier: Modifier) {
    NafsStat(
        value = value,
        label = label,
        progress = progress,
        color = color,
        valueColor = Nafs.colors.onHero,
        labelColor = Nafs.colors.onHeroMuted,
        modifier = modifier,
    )
}

@Composable
private fun NextUpCard(task: NafsTask, onOpenTasks: () -> Unit) {
    val tint = Nafs.colors.tasks
    NafsCard(onClick = onOpenTasks) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            NafsIconBadge(Icons.Rounded.Schedule, tint)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                NafsEyebrow("Next up", color = tint)
                Text(task.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NafsPill(task.dueTime?.let { "Due ${TaskLabels.time(it)}" } ?: "No fixed time", color = tint)
                    NafsPill(task.priority.label(), color = task.priority.color())
                }
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─── Today's list ────────────────────────────────────────────────────────────

@Composable
private fun TodayTasks(tasks: List<NafsTask>, state: DashboardUiState, actions: DashboardActions) {
    val shown = tasks.take(TODAY_ROWS)
    NafsGroup(title = "Tasks · ${tasks.count(NafsTask::isCompleted)} of ${tasks.size} done") {
        shown.forEachIndexed { index, task ->
            if (index > 0) NafsDivider(inset = 18.dp)
            TodayCheckRow(
                title = task.title,
                subtitle = listOfNotNull(
                    task.dueTime?.let { "Due ${TaskLabels.time(it)}" },
                    task.priority.label().takeIf { task.priority == TaskPriority.HIGH },
                ).joinToString(" · ").ifEmpty { null },
                checked = task.isCompleted,
                pending = taskItemKey(task.id) in state.pendingItems,
                accent = Nafs.colors.tasks,
                onToggle = { completed -> actions.onSetTaskCompleted(task, completed) },
            )
        }
        if (tasks.size > shown.size) {
            NafsDivider(inset = 18.dp)
            NafsListRow(
                title = "${tasks.size - shown.size} more in Tasks",
                titleColor = MaterialTheme.colorScheme.primary,
                onClick = actions.onOpenTasks,
            )
        }
    }
}

@Composable
private fun TodayHabits(habits: List<Habit>, state: DashboardUiState, actions: DashboardActions) {
    val shown = habits.take(TODAY_ROWS)
    val done = habits.count { habit -> isHabitDone(habit, state.todayLog(habit.id)) }
    NafsGroup(title = "Habits · $done of ${habits.size} done") {
        shown.forEachIndexed { index, habit ->
            if (index > 0) NafsDivider(inset = 18.dp)
            val log = state.todayLog(habit.id)
            if (habit.type == HabitType.SIMPLE) {
                val streak = state.liveStreak(habit)
                val unit = if (habit.scheduleKind == ScheduleKind.PER_WEEK) "week" else "day"
                TodayCheckRow(
                    title = habit.name,
                    subtitle = if (streak > 0) "$streak-$unit streak" else null,
                    checked = log?.completed == true,
                    pending = habitItemKey(habit.id) in state.pendingItems,
                    accent = Nafs.colors.habits,
                    leading = habit.emoji,
                    onToggle = { completed -> actions.onSetHabitCompleted(habit, completed) },
                )
            } else {
                // Counters, timers and reading progress need an amount, so they open the Habits screen.
                TodayProgressRow(habit, log, onOpen = actions.onOpenHabits)
            }
        }
        if (habits.size > shown.size) {
            NafsDivider(inset = 18.dp)
            NafsListRow(
                title = "${habits.size - shown.size} more in Habits",
                titleColor = MaterialTheme.colorScheme.primary,
                onClick = actions.onOpenHabits,
            )
        }
    }
}

/** A whole-row checkbox: the row is the touch target, and completing one gives a confirm haptic. */
@Composable
private fun TodayCheckRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    pending: Boolean,
    accent: Color,
    onToggle: (Boolean) -> Unit,
    leading: String? = null,
) {
    val haptics = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = !pending, role = Role.Checkbox) { completed ->
                if (completed) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onToggle(completed)
            }
            .padding(start = 6.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckMark(checked = checked, pending = pending, accent = accent)
        leading?.takeIf { it.isNotBlank() }?.let { emoji ->
            Text(emoji, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 10.dp))
        }
        Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (checked) scheme.onSurfaceVariant else scheme.onSurface,
                textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The check fills at once (optimistic); a thin ring turns around it until the save is confirmed. */
@Composable
private fun CheckMark(checked: Boolean, pending: Boolean, accent: Color) {
    val fill by animateColorAsState(if (checked) accent else Color.Transparent, label = "home-check-fill")
    val ring by animateColorAsState(if (checked) accent else MaterialTheme.colorScheme.outline, label = "home-check-ring")
    val markScale by animateFloatAsState(if (checked) 1f else 0f, label = "home-check-mark")
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
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
        if (pending) CircularProgressIndicator(Modifier.size(36.dp), strokeWidth = 2.dp, color = accent)
    }
}

@Composable
private fun TodayProgressRow(habit: Habit, log: HabitLog?, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val tint = Nafs.colors.habits
    val (progress, detail) = habitProgress(habit, log)
    Surface(onClick = onOpen, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 6.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                NafsProgressRing(progress = progress, size = 28.dp, stroke = 3.dp, colors = listOf(tint, tint))
            }
            if (habit.emoji.isNotBlank()) {
                Text(habit.emoji, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 10.dp))
            }
            Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(habit.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Log ${habit.name} in Habits",
                tint = scheme.onSurfaceVariant)
        }
    }
}

private fun isHabitDone(habit: Habit, log: HabitLog?): Boolean = when (habit.type) {
    HabitType.SIMPLE, HabitType.SUBJECT -> log?.completed == true
    HabitType.COUNTER -> log?.completed == true && log.value >= habit.targetValue
    HabitType.DURATION -> log?.completed == true && log.durationMinutes >= habit.timeTargetMinutes
}

/** Today's progress for a habit that is logged with an amount, and a line saying how far along it is. */
private fun habitProgress(habit: Habit, log: HabitLog?): Pair<Float, String> = when (habit.type) {
    HabitType.COUNTER -> {
        val value = log?.value ?: 0.0
        val target = habit.targetValue
        val share = if (target > 0.0) (value / target).toFloat() else 0f
        share.coerceIn(0f, 1f) to "${amount(value)} of ${amount(target)} ${habit.unit}".trim()
    }
    HabitType.DURATION -> {
        val minutes = log?.durationMinutes ?: 0
        val target = habit.timeTargetMinutes
        val share = if (target > 0) minutes.toFloat() / target else 0f
        share.coerceIn(0f, 1f) to "$minutes of $target min"
    }
    HabitType.SUBJECT -> if (log?.completed == true) {
        1f to "Logged today"
    } else {
        0f to "Log today's ${habit.subjectUnit?.takeIf { it.isNotBlank() } ?: "progress"}"
    }
    HabitType.SIMPLE -> if (log?.completed == true) 1f to "Done" else 0f to "Not yet"
}

private fun amount(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.ENGLISH, "%.1f", value)

// ─── Shortcuts and areas ─────────────────────────────────────────────────────

@Composable
private fun ShortcutTile(title: String, subtitle: String, icon: ImageVector, tint: Color, onClick: () -> Unit, modifier: Modifier) {
    NafsCard(modifier = modifier, onClick = onClick, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NafsIconBadge(icon, tint, size = 40.dp, iconSize = 20.dp)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private data class Area(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tint: Color,
    val progress: Float?,
    val onClick: () -> Unit,
)

@Composable
private fun AreaGrid(profile: UserProfile, metrics: DashboardMetrics, challenges: ChallengeSummary?, actions: DashboardActions) {
    val c = Nafs.colors
    val areas = buildList {
        add(Area("Tasks", "${metrics.completedTasks} of ${metrics.totalTasks} done", Icons.Rounded.TaskAlt, c.tasks,
            fraction(metrics.completedTasks, metrics.totalTasks), actions.onOpenTasks))
        add(Area("Habits", "${metrics.completedHabits} of ${metrics.totalHabits} logged", Icons.Rounded.Autorenew, c.habits,
            fraction(metrics.completedHabits, metrics.totalHabits), actions.onOpenHabits))
        add(Area("Goals", "${metrics.averageGoalProgress}% avg · ${metrics.completedGoals} done", Icons.Rounded.Flag, c.goals,
            metrics.averageGoalProgress / 100f, actions.onOpenGoals))
        add(Area("Challenges", challengeSubtitle(challenges), Icons.Rounded.Whatshot, c.challenges,
            challengeProgress(challenges), actions.onOpenChallenges))
        add(Area("Health", "Sleep, water, meals", Icons.Rounded.Favorite, c.health, null, actions.onOpenHealth))
        if (profile.deenEnabled) add(Area("Deen", "Prayers and jamaat", Icons.Rounded.NightsStay, c.deen, null, actions.onOpenDeen))
        add(Area("Explore", "Reports, journals, more", Icons.Rounded.Apps, MaterialTheme.colorScheme.onSurfaceVariant, null, actions.onOpenLibrary))
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        areas.chunked(2).forEach { pair ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { area -> AreaTile(area, Modifier.weight(1f).fillMaxHeight()) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Live numbers when challenges loaded; otherwise a plain description rather than a guess. */
private fun challengeSubtitle(summary: ChallengeSummary?): String {
    if (summary == null) return "Check-ins and streaks"
    val running = summary.checkedIn + summary.needCheckIn
    return when {
        summary.active == 0 -> "No active challenge"
        running == 0 -> "${summary.active} active · not started yet"
        summary.needCheckIn == 0 -> "${summary.active} active · all checked in"
        else -> "${summary.active} active · ${summary.needCheckIn} to check in"
    }
}

private fun challengeProgress(summary: ChallengeSummary?): Float? {
    val running = (summary ?: return null).checkedIn + summary.needCheckIn
    return if (running == 0) null else summary.checkedIn.toFloat() / running
}

@Composable
private fun AreaTile(area: Area, modifier: Modifier) {
    NafsCard(modifier = modifier, onClick = area.onClick, contentPadding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NafsIconBadge(area.icon, area.tint, size = 40.dp, iconSize = 20.dp)
                Spacer(Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(area.title, style = MaterialTheme.typography.titleMedium)
                Text(area.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            area.progress?.let { NafsProgressBar(it, color = area.tint, height = 6.dp) }
        }
    }
}

@Composable
private fun GoalRow(goal: Goal, onOpenGoals: () -> Unit) {
    val tint = Nafs.colors.goals
    NafsCard(onClick = onOpenGoals, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            NafsEmojiBadge(goal.emoji, tint, size = 44.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(goal.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${goal.progressPercent}%", style = MaterialTheme.typography.titleSmall, color = tint)
                }
                NafsProgressBar(goal.progressPercent / 100f, color = tint, height = 6.dp)
                goal.deadline?.let {
                    Text("Due ${it.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun heroHeadline(metrics: DashboardMetrics): String = when {
    metrics.totalTasks == 0 && metrics.totalHabits == 0 -> "A clear day. Choose what matters."
    metrics.completedTasks == metrics.totalTasks && metrics.completedHabits == metrics.totalHabits ->
        "Every promise kept today."
    else -> {
        val left = (metrics.totalTasks - metrics.completedTasks) + (metrics.totalHabits - metrics.completedHabits)
        "$left still open. One at a time."
    }
}

private fun TaskPriority.label() = when (this) {
    TaskPriority.HIGH -> "High priority"
    TaskPriority.MEDIUM -> "Medium"
    TaskPriority.LOW -> "Low"
}

@Composable
private fun TaskPriority.color(): Color = when (this) {
    TaskPriority.HIGH -> MaterialTheme.colorScheme.error
    TaskPriority.MEDIUM -> Nafs.colors.warning
    TaskPriority.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun fraction(done: Int, total: Int): Float = if (total <= 0) 0f else done.toFloat() / total
