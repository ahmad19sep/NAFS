package com.ahmad.nafs.ui

import android.net.Uri

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.ahmad.nafs.core.preferences.ThemeMode
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.ui.auth.AuthRoute
import com.ahmad.nafs.ui.challenges.ChallengeRoute
import com.ahmad.nafs.ui.dashboard.DashboardRoute
import com.ahmad.nafs.ui.habits.HabitRoute
import com.ahmad.nafs.ui.goals.GoalRoute
import com.ahmad.nafs.ui.health.HealthRoute
import com.ahmad.nafs.ui.deen.PrayerTimesRoute
import com.ahmad.nafs.ui.deen.DeenRoute
import com.ahmad.nafs.ui.checkin.CheckInRoute
import com.ahmad.nafs.ui.coach.CoachRoute
import com.ahmad.nafs.ui.reports.ReportListRoute
import com.ahmad.nafs.ui.reports.ReportDetailRoute
import com.ahmad.nafs.ui.letters.LetterRoute
import com.ahmad.nafs.ui.plans.RecoveryRoute
import com.ahmad.nafs.ui.profile.FaithModeSettingsCard
import com.ahmad.nafs.ui.onboarding.OnboardingRoute
import com.ahmad.nafs.ui.tasks.TaskRoute
import com.ahmad.nafs.ui.profile.ReminderOptionsCard
import com.ahmad.nafs.ui.profile.NotificationSettingsCard
import com.ahmad.nafs.ui.profile.TimezoneSettingsCard
import com.ahmad.nafs.ui.components.NafsHeroCard
import com.ahmad.nafs.ui.components.NafsSectionTitle
import com.ahmad.nafs.ui.components.NetworkStatus
import com.ahmad.nafs.ui.profile.ProfileEditorCard
import com.ahmad.nafs.ui.records.RecordRoute
import com.ahmad.nafs.core.records.RecordFeature
import com.ahmad.nafs.ui.progress.AchievementsRoute
import com.ahmad.nafs.ui.progress.ProgressRoute
import com.ahmad.nafs.ui.advice.AdviceRoute

private data class TopDestination(
    val label: String,
    val route: String,
    val icon: ImageVector,
)

private val topDestinations = listOf(
    TopDestination("Home", "dashboard", Icons.Rounded.Home),
    TopDestination("Tasks", "tasks", Icons.Rounded.CheckCircle),
    TopDestination("Habits", "habits", Icons.Rounded.Loop),
    TopDestination("Goals", "goals", Icons.Rounded.Flag),
    TopDestination("You", "profile", Icons.Rounded.Person),
)

@Composable
fun NafsApp(
    themeMode: ThemeMode,
    appState: RootAppState,
    onThemeModeChange: (ThemeMode) -> Unit,
    onRetrySession: () -> Unit,
    onRetryProfile: () -> Unit,
    onSignOut: () -> Unit,
    requestedDestination: String?,
    onDestinationHandled: () -> Unit,
    profileMessage: String?,
    onUseDeviceTimezone: () -> Unit,
    onDismissProfileMessage: () -> Unit,
) {
    when (appState) {
        RootAppState.ConfigurationMissing -> ConfigurationMissingScreen()
        RootAppState.RestoringSession -> LoadingScreen("Restoring your session...")
        RootAppState.SignedOut -> AuthRoute()
        RootAppState.SessionRefreshFailed -> RefreshFailedScreen(onRetrySession = onRetrySession)
        RootAppState.LoadingProfile -> LoadingScreen("Loading your profile...")
        is RootAppState.ProfileFailure -> ProfileFailedScreen(
            message = appState.message,
            onRetryProfile = onRetryProfile,
        )
        is RootAppState.NeedsOnboarding -> OnboardingRoute(profile = appState.profile)
        is RootAppState.Ready -> key(appState.profile.userId) { AuthenticatedNafsApp(
            themeMode = themeMode,
            profile = appState.profile,
            onThemeModeChange = onThemeModeChange,
            onSignOut = onSignOut,
            requestedDestination = requestedDestination,
            onDestinationHandled = onDestinationHandled,
            profileMessage = profileMessage,
            onUseDeviceTimezone = onUseDeviceTimezone,
            onDismissProfileMessage = onDismissProfileMessage,
        ) }
    }
}

@Composable
private fun AuthenticatedNafsApp(
    themeMode: ThemeMode,
    profile: UserProfile,
    onThemeModeChange: (ThemeMode) -> Unit,
    onSignOut: () -> Unit,
    requestedDestination: String?,
    onDestinationHandled: () -> Unit,
    profileMessage: String?,
    onUseDeviceTimezone: () -> Unit,
    onDismissProfileMessage: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    var showQuickAdd by rememberSaveable { mutableStateOf(false) }
    if (showQuickAdd) QuickAddSheet(
        onDismiss = { showQuickAdd = false },
        onNavigate = { route -> showQuickAdd = false; navController.navigate(route) { launchSingleTop = true } },
    )

    LaunchedEffect(requestedDestination) {
        val route = requestedDestination?.takeIf { requested ->
            topDestinations.any { it.route == requested } || requested in setOf("coach/letters", "deen", "progress") ||
                Regex("^progress\\?weekStart=\\d{4}-\\d{2}-\\d{2}$").matches(requested)
        } ?: return@LaunchedEffect
        if (topDestinations.any { it.route == route }) navController.navigateTopDestination(route)
        // A notification opens a fresh screen (for example this week's report), never an older saved one.
        else navController.navigate(route) { popUpTo("dashboard") { saveState = true }; launchSingleTop = true }
        onDestinationHandled()
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showQuickAdd = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "Quick Add")
            }
        },
        topBar = { NetworkStatus() },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                topDestinations.forEach { item ->
                    NavigationBarItem(
                        selected = destination?.route == item.route,
                        onClick = {
                            navController.navigateTopDestination(item.route)
                        },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.padding(padding),
        ) {
            composable("dashboard") {
                DashboardRoute(
                    profile = profile,
                    onOpenTasks = { navController.navigate("tasks") { launchSingleTop = true } },
                    onOpenHabits = { navController.navigate("habits") { launchSingleTop = true } },
                    onOpenGoals = { navController.navigate("goals") { launchSingleTop = true } },
                    onOpenChallenges = {
                        navController.navigate("challenges") { launchSingleTop = true }
                    },
                    onOpenHealth = {
                        navController.navigate("health") { launchSingleTop = true }
                    },
                    onOpenDeen = { navController.navigate("deen") { launchSingleTop = true } },
                    onOpenCheckIn = { navController.navigate("checkin") { launchSingleTop = true } },
                    onOpenCoach = { navController.navigate("coach") { launchSingleTop = true } },
                    onOpenLibrary = { navController.navigate("library") { launchSingleTop = true } },
                )
            }
            composable("tasks") {
                TaskRoute(profile = profile)
            }
            composable("tasks/new") { TaskRoute(profile = profile, createOnOpen = true) }
            composable("habits/new") { HabitRoute(profile = profile, createOnOpen = true) }
            composable("goals/new") { GoalRoute(profile = profile, createOnOpen = true) }
            composable("challenges/new") { ChallengeRoute(profile = profile, createOnOpen = true) }
            composable("habits") {
                HabitRoute(profile = profile)
            }
            composable("goals") {
                GoalRoute(profile = profile)
            }
            composable("challenges") {
                ChallengeRoute(profile = profile)
            }
            composable("health") {
                HealthRoute(profile = profile)
            }
            composable("deen") { DeenRoute(profile = profile) }
            composable("checkin") { CheckInRoute(profile = profile) }
            composable("prayer-times") { if (profile.deenEnabled) PrayerTimesRoute(profile, { navController.popBackStack() }) }
            composable("achievements") { AchievementsRoute(profile, { navController.popBackStack() }) }
            composable("library") { LibraryScreen(profile) { route -> navController.navigate(route) { launchSingleTop = true } } }
            RecordFeature.entries.forEach { feature ->
                composable("records/${feature.name.lowercase()}") { RecordRoute(profile, feature, onBack = { navController.popBackStack() }) }
            }
            composable("history") { ProgressRoute(profile, true, onBack = { navController.popBackStack() }, onReview = { period, offset -> navController.navigate("advice?period=$period&offset=$offset") }) }
            composable("progress?weekStart={weekStart}", arguments = listOf(
                navArgument("weekStart") { type = NavType.StringType; nullable = true; defaultValue = null },
            )) { entry ->
                ProgressRoute(profile, false, onBack = { navController.popBackStack() },
                    onReview = { period, offset -> navController.navigate("advice?period=$period&offset=$offset") },
                    weekStart = entry.arguments?.getString("weekStart"))
            }
            composable("advice?period={period}&offset={offset}", arguments = listOf(
                navArgument("period") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("offset") { type = NavType.IntType; defaultValue = 0 },
            )) { entry -> AdviceRoute(profile, entry.arguments?.getString("period"), entry.arguments?.getInt("offset") ?: 0, onBack = { navController.popBackStack() }) }
            composable("coach") {
                CoachRoute(profile = profile, onBack = { navController.popBackStack() },
                    onOpenReports = { navController.navigate("coach/reports") { launchSingleTop = true } },
                    onOpenLetters = { navController.navigate("coach/letters") { launchSingleTop = true } },
                    onOpenRecovery = { navController.navigate("coach/plans") { launchSingleTop = true } },
                    onPlanFromChat = { chat -> navController.navigate("coach/plans?conversationId=${chat.id}&version=${Uri.encode(chat.updatedAt)}") { launchSingleTop = true } })
            }
            composable("coach/plans?conversationId={conversationId}&version={version}", arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("version") { type = NavType.StringType; nullable = true; defaultValue = null },
            )) { entry ->
                RecoveryRoute(profile, entry.arguments?.getString("conversationId"), entry.arguments?.getString("version"),
                    onBack = { navController.popBackStack() }, onOpenFeature = { route -> navController.navigate(route) { launchSingleTop = true } })
            }
            composable("coach/letters") { LetterRoute(profile = profile, onBack = { navController.popBackStack() }) }
            composable("coach/reports") {
                ReportListRoute(profile = profile, onBack = { navController.popBackStack() },
                    onOpenReport = { id -> navController.navigate("coach/reports/$id") { launchSingleTop = true } })
            }
            composable("coach/reports/{reportId}") { entry ->
                ReportDetailRoute(profile = profile, reportId = entry.arguments?.getString("reportId").orEmpty(),
                    onBack = { navController.popBackStack() })
            }
            composable("profile") {
                ProfileFoundationScreen(
                    themeMode = themeMode,
                    profile = profile,
                    onThemeModeChange = onThemeModeChange,
                    onSignOut = onSignOut,
                    profileMessage = profileMessage,
                    onUseDeviceTimezone = onUseDeviceTimezone,
                    onDismissProfileMessage = onDismissProfileMessage,
                    onOpenPrayerTimes = { navController.navigate("prayer-times") { launchSingleTop = true } },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoundationScreen(title: String, message: String) {
    Scaffold(topBar = { TopAppBar(title = { Text(title) }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Milestone 3", style = MaterialTheme.typography.labelLarge)
            Text(message, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileFoundationScreen(
    themeMode: ThemeMode,
    profile: UserProfile,
    onThemeModeChange: (ThemeMode) -> Unit,
    onSignOut: () -> Unit,
    profileMessage: String?,
    onUseDeviceTimezone: () -> Unit,
    onDismissProfileMessage: () -> Unit,
    onOpenPrayerTimes: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("You") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                NafsHeroCard(
                    eyebrow = "Your NAFS account",
                    title = profile.name,
                    body = profile.email,
                )
            }
            if (profile.isFromCache) {
                item { Text("Showing your offline profile cache", color = MaterialTheme.colorScheme.secondary) }
            }
            item { NafsSectionTitle("App experience", supporting = "Control appearance, time, and reminders.") }
            item { ProfileEditorCard(profile) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Appearance", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { onThemeModeChange(mode) },
                                label = { Text(mode.name.lowercase().replaceFirstChar(Char::uppercase)) },
                            )
                        }
                    }
                }
            }
            item { NotificationSettingsCard() }
            item { ReminderOptionsCard(profile, onOpenPrayerTimes) }
            item { FaithModeSettingsCard(profile) }
            item {
                TimezoneSettingsCard(
                    accountTimezone = profile.timezone,
                    message = profileMessage,
                    onUseDeviceTimezone = onUseDeviceTimezone,
                    onDismissMessage = onDismissProfileMessage,
                )
            }
            item { NafsSectionTitle("Account & privacy") }
            item {
                Text(
                    "Your session is encrypted with Android Keystore. Location is not collected for daily scheduling.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Sign out") } }
        }
    }
}

@Composable
private fun LoadingScreen(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(
                text = message,
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ProfileFailedScreen(
    message: String,
    onRetryProfile: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Profile unavailable", style = MaterialTheme.typography.titleLarge)
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onRetryProfile) { Text("Retry") }
        }
    }
}

@Composable
private fun ConfigurationMissingScreen() {
    FoundationScreen(
        title = "Connect NAFS",
        message = "Add NAFS_SUPABASE_URL and NAFS_SUPABASE_PUBLISHABLE_KEY to your Gradle configuration, then rebuild the app. No service-role or AI provider key belongs in Android.",
    )
}

@Composable
private fun RefreshFailedScreen(onRetrySession: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Session refresh failed", style = MaterialTheme.typography.titleLarge)
            Text(
                "Check your connection and retry. Your encrypted local session has not been presented as signed in.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onRetrySession) { Text("Retry") }
        }
    }
}

