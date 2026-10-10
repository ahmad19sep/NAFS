package com.ahmad.nafs.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Autorenew
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmad.nafs.core.goals.Goal
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.core.tasks.NafsTask
import com.ahmad.nafs.core.tasks.TaskPriority
import com.ahmad.nafs.ui.AccountDayRefreshEffects
import com.ahmad.nafs.ui.components.NafsAvatar
import com.ahmad.nafs.ui.components.NafsBanner
import com.ahmad.nafs.ui.components.NafsBannerKind
import com.ahmad.nafs.ui.components.NafsCard
import com.ahmad.nafs.ui.components.NafsEmojiBadge
import com.ahmad.nafs.ui.components.NafsEyebrow
import com.ahmad.nafs.ui.components.NafsHeroSurface
import com.ahmad.nafs.ui.components.NafsIconBadge
import com.ahmad.nafs.ui.components.NafsLoadingState
import com.ahmad.nafs.ui.components.NafsPill
import com.ahmad.nafs.ui.components.NafsProgressBar
import com.ahmad.nafs.ui.components.NafsProgressRing
import com.ahmad.nafs.ui.components.NafsSectionTitle
import com.ahmad.nafs.ui.components.NafsSpace
import com.ahmad.nafs.ui.components.NafsStat
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
    LaunchedEffect(profile.userId, profile.timezone) {
        viewModel.initialize(profile.userId, profile.timezone)
    }
    AccountDayRefreshEffects(profile.timezone, viewModel::refresh)
    DashboardScreen(
        profile = profile,
        state = state,
        actions = DashboardActions(
            onRefresh = viewModel::refresh,
            onDismissMessage = viewModel::dismissMessage,
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
    )
}

/** Everything Home can open, grouped so the screen stays a pure function of its state. */
internal data class DashboardActions(
    val onRefresh: () -> Unit = {},
    val onDismissMessage: () -> Unit = {},
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

@Composable
internal fun DashboardScreen(
    profile: UserProfile,
    state: DashboardUiState,
    actions: DashboardActions,
    insight: @Composable () -> Unit = {},
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

    if (state.isLoading) {
        NafsLoadingState(Modifier.statusBarsPadding())
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = NafsSpace.screen, end = NafsSpace.screen, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HomeHeader(
                profile = profile,
                now = now,
                refreshing = state.isRefreshing,
                canRefresh = !state.isLoading && !state.isRefreshing,
                onRefresh = actions.onRefresh,
                onOpenProfile = actions.onOpenProfile,
            )
        }

        if (state.isOffline || profile.isFromCache) item {
            NafsBanner(
                "Showing your saved data. Changes need a connection.",
                kind = NafsBannerKind.OFFLINE,
                actionLabel = "Retry",
                onAction = actions.onRefresh,
            )
        }
        state.message?.let { message ->
            item { NafsBanner(message, kind = NafsBannerKind.WARNING, onDismiss = actions.onDismissMessage) }
        }

        item { TodayHero(metrics, now, profile.timezone, onOpenTasks = actions.onOpenTasks) }

        nextTask?.let { task -> item { NextUpCard(task, actions.onOpenTasks) } }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShortcutTile(
                    title = "Check-in",
                    subtitle = if (now.hour < 15) "Set today's priorities" else "Reflect on today",
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

        item { NafsSectionTitle("Your areas", supporting = "Tap any area to log or plan.") }
        item { AreaGrid(profile, metrics, actions) }

        item { insight() }

        if (leadingGoals.isNotEmpty()) {
            item {
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

        item {
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

@Composable
private fun HomeHeader(
    profile: UserProfile,
    now: ZonedDateTime,
    refreshing: Boolean,
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
        IconButton(onClick = onRefresh, enabled = canRefresh) {
            if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(Icons.Rounded.Refresh, contentDescription = "Refresh dashboard", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    colors = listOf(c.gold, MaterialTheme.colorScheme.primary),
                    trackColor = Color.White.copy(alpha = 0.12f),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.headlineMedium, color = c.onHero)
                        Text("today", style = MaterialTheme.typography.labelSmall, color = c.onHeroMuted)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    NafsEyebrow("Your day, honestly", color = c.gold)
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
                HeroStat("Tasks", "${metrics.completedTasks}/${metrics.totalTasks}", fraction(metrics.completedTasks, metrics.totalTasks), c.tasks, Modifier.weight(1f))
                HeroStat("Habits", "${metrics.completedHabits}/${metrics.totalHabits}", fraction(metrics.completedHabits, metrics.totalHabits), c.habits, Modifier.weight(1f))
                HeroStat("Goals", "${metrics.averageGoalProgress}%", metrics.averageGoalProgress / 100f, c.gold, Modifier.weight(1f))
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
                    NafsPill(task.dueTime?.let { "Due ${it.take(5)}" } ?: "No fixed time", color = tint)
                    NafsPill(task.priority.label(), color = task.priority.color())
                }
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

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
private fun AreaGrid(profile: UserProfile, metrics: DashboardMetrics, actions: DashboardActions) {
    val c = Nafs.colors
    val areas = buildList {
        add(Area("Tasks", "${metrics.completedTasks} of ${metrics.totalTasks} done", Icons.Rounded.TaskAlt, c.tasks,
            fraction(metrics.completedTasks, metrics.totalTasks), actions.onOpenTasks))
        add(Area("Habits", "${metrics.completedHabits} of ${metrics.totalHabits} logged", Icons.Rounded.Autorenew, c.habits,
            fraction(metrics.completedHabits, metrics.totalHabits), actions.onOpenHabits))
        add(Area("Goals", "${metrics.averageGoalProgress}% avg · ${metrics.completedGoals} done", Icons.Rounded.Flag, c.goals,
            metrics.averageGoalProgress / 100f, actions.onOpenGoals))
        add(Area("Challenges", "Keep the streak", Icons.Rounded.Whatshot, c.challenges, null, actions.onOpenChallenges))
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
