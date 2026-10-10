package com.ahmad.nafs.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmad.nafs.core.goals.Goal
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.core.tasks.NafsTask
import com.ahmad.nafs.ui.AccountDayRefreshEffects
import com.ahmad.nafs.ui.components.NafsHeroCard
import com.ahmad.nafs.ui.components.NafsQuickAction
import com.ahmad.nafs.ui.components.NafsSectionTitle
import com.ahmad.nafs.ui.components.NafsStatusPill
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
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
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardScreen(
    profile: UserProfile,
    state: DashboardUiState,
    onRefresh: () -> Unit,
    onDismissMessage: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenHabits: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenChallenges: () -> Unit,
    onOpenHealth: () -> Unit,
    onOpenDeen: () -> Unit,
    onOpenCheckIn: () -> Unit,
    onOpenCoach: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val zone = runCatching { ZoneId.of(profile.timezone) }.getOrDefault(ZoneId.of("UTC"))
    val now by produceState(initialValue = ZonedDateTime.now(zone), zone) {
        while (true) {
            value = ZonedDateTime.now(zone)
            delay(30_000L)
        }
    }
    val today = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))
    val liveTime = now.format(DateTimeFormatter.ofPattern("h:mm a"))
    val metrics = state.metrics()
    val nextTask = state.nextTask()
    val leadingGoals = state.leadingGoals()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("NAFS", style = MaterialTheme.typography.titleLarge)
                        Text(
                            today,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    androidx.compose.material3.TextButton(onClick = onOpenLibrary) { Text("Explore") }
                    NafsStatusPill(
                        text = when {
                            state.isRefreshing -> "Refreshing"
                            state.isOffline || profile.isFromCache -> "Offline"
                            else -> "Live"
                        },
                        accent = !state.isOffline && !profile.isFromCache,
                    )
                    IconButton(
                        onClick = onRefresh,
                        enabled = !state.isLoading && !state.isRefreshing,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    ) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh dashboard")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                NafsHeroCard(
                    eyebrow = "Your day, honestly",
                    title = "Assalamu alaikum, ${profile.name.substringBefore(' ')}",
                    body = dashboardHeroBody(metrics),
                ) {
                    Text(
                        "$liveTime · ${profile.timezone}",
                        color = Color.White.copy(alpha = 0.82f),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            if (state.isOffline) item { DashboardOfflineBanner() }
            state.message?.let { message ->
                item { DashboardInsightCard(profile, onOpenLibrary, onOpenCoach) }
                item { DashboardMessage(message, onDismissMessage) }
            }

            item {
                NafsSectionTitle(
                    "Today at a glance",
                    supporting = "Live progress from your real tasks, habits, and goals.",
                )
            }
            item { MetricsCard(metrics) }

            item {
                NafsQuickAction(
                    icon = Icons.Rounded.CheckCircle,
                    title = "Daily check-in",
                    subtitle = "Plan your priorities and reflect on the day",
                    onClick = onOpenCheckIn,
                )
            }

            nextTask?.let { task ->
                item { NextTaskCard(task, onOpenTasks) }
            }

            item {
                NafsQuickAction(
                    icon = Icons.AutoMirrored.Rounded.Chat,
                    title = "Coach",
                    subtitle = "Talk through your progress and next step",
                    onClick = onOpenCoach,
                )
            }

            item {
                NafsQuickAction(
                    icon = Icons.Rounded.CheckCircle,
                    title = "Tasks",
                    subtitle = "${metrics.completedTasks} of ${metrics.totalTasks} completed today",
                    onClick = onOpenTasks,
                )
            }
            item {
                NafsQuickAction(
                    icon = Icons.Rounded.Loop,
                    title = "Habits",
                    subtitle = "${metrics.completedHabits} of ${metrics.totalHabits} scheduled habits logged",
                    onClick = onOpenHabits,
                )
            }
            item {
                NafsQuickAction(
                    icon = Icons.Rounded.Flag,
                    title = "Goals",
                    subtitle = "${metrics.averageGoalProgress}% average · ${metrics.completedGoals} complete",
                    onClick = onOpenGoals,
                )
            }
            item {
                NafsQuickAction(
                    icon = Icons.Rounded.EmojiEvents,
                    title = "Challenges",
                    subtitle = "Build a streak with daily, weekly, monthly, or yearly check-ins",
                    onClick = onOpenChallenges,
                )
            }
            item {
                NafsQuickAction(
                    icon = Icons.Rounded.Favorite,
                    title = "Health",
                    subtitle = "Log sleep, movement, hydration, mood, and body metrics",
                    onClick = onOpenHealth,
                )
            }

            if (profile.deenEnabled) item {
                NafsQuickAction(
                    icon = Icons.Rounded.NightsStay,
                    title = "Deen",
                    subtitle = "Log daily prayers, jamaat, and extra activities",
                    onClick = onOpenDeen,
                )
            }

            if (leadingGoals.isNotEmpty()) {
                item {
                    NafsSectionTitle(
                        "Goals in motion",
                        supporting = "The nearest unfinished horizons.",
                    )
                }
                leadingGoals.forEach { goal ->
                    item(key = "dashboard-goal-${goal.id}") {
                        DashboardGoalCard(goal, onOpenGoals)
                    }
                }
            }

            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Row(
                        Modifier.padding(18.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column {
                            Text("Private by design", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Your account timezone drives daily boundaries. NAFS does not continuously track your location.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricsCard(metrics: DashboardMetrics) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            MetricStat(
                value = "${metrics.completedTasks}/${metrics.totalTasks}",
                label = "Tasks",
                progress = fraction(metrics.completedTasks, metrics.totalTasks),
            )
            MetricStat(
                value = "${metrics.completedHabits}/${metrics.totalHabits}",
                label = "Habits",
                progress = fraction(metrics.completedHabits, metrics.totalHabits),
            )
            MetricStat(
                value = "${metrics.averageGoalProgress}%",
                label = "Goals",
                progress = metrics.averageGoalProgress / 100f,
            )
        }
    }
}

@Composable
private fun MetricStat(value: String, label: String, progress: Float) {
    Column(
        Modifier.width(88.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NextTaskCard(task: NafsTask, onOpenTasks: () -> Unit) {
    Card(
        onClick = onOpenTasks,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Row(
            Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Column(Modifier.weight(1f)) {
                Text("Next up", style = MaterialTheme.typography.labelLarge)
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    task.dueTime?.let { "Due $it" } ?: "No fixed time · ${task.priority.name.lowercase()} priority",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f),
                )
            }
        }
    }
}

@Composable
private fun DashboardGoalCard(goal: Goal, onOpenGoals: () -> Unit) {
    Card(onClick = onOpenGoals, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(goal.emoji, style = MaterialTheme.typography.titleLarge)
                Text(
                    goal.title,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${goal.progressPercent}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            LinearProgressIndicator(
                progress = { goal.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DashboardOfflineBanner() {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.CloudOff, contentDescription = null)
            Text("Showing cached dashboard data. Changes still require a connection.")
        }
    }
}

@Composable
private fun DashboardMessage(message: String, onDismiss: () -> Unit) {
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

private fun dashboardHeroBody(metrics: DashboardMetrics): String = when {
    metrics.totalTasks == 0 && metrics.totalHabits == 0 ->
        "Your day is clear. Add one deliberate task or habit to create momentum."
    metrics.completedTasks == metrics.totalTasks &&
        metrics.completedHabits == metrics.totalHabits ->
        "Today's scheduled promises are complete. Protect the rhythm."
    else ->
        "${metrics.completedTasks} tasks and ${metrics.completedHabits} habits completed so far."
}

private fun fraction(done: Int, total: Int): Float =
    if (total <= 0) 0f else done.toFloat() / total
