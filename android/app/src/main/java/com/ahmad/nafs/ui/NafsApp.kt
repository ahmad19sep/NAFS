package com.ahmad.nafs.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ahmad.nafs.core.preferences.ThemeMode
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.core.records.RecordFeature
import com.ahmad.nafs.ui.advice.AdviceRoute
import com.ahmad.nafs.ui.auth.AuthRoute
import com.ahmad.nafs.ui.challenges.ChallengeRoute
import com.ahmad.nafs.ui.checkin.CheckInRoute
import com.ahmad.nafs.ui.coach.CoachRoute
import com.ahmad.nafs.ui.components.NafsBrandMark
import com.ahmad.nafs.ui.components.NafsErrorState
import com.ahmad.nafs.ui.components.NafsIconBadge
import com.ahmad.nafs.ui.components.isOnline
import com.ahmad.nafs.ui.dashboard.DashboardRoute
import com.ahmad.nafs.ui.deen.DeenRoute
import com.ahmad.nafs.ui.deen.PrayerTimesRoute
import com.ahmad.nafs.ui.goals.GoalRoute
import com.ahmad.nafs.ui.habits.HabitRoute
import com.ahmad.nafs.ui.health.HealthRoute
import com.ahmad.nafs.ui.letters.LetterRoute
import com.ahmad.nafs.ui.onboarding.OnboardingRoute
import com.ahmad.nafs.ui.plans.RecoveryRoute
import com.ahmad.nafs.ui.profile.ProfileRoute
import com.ahmad.nafs.ui.progress.AchievementsRoute
import com.ahmad.nafs.ui.progress.ProgressRoute
import com.ahmad.nafs.ui.records.RecordRoute
import com.ahmad.nafs.ui.reports.ReportDetailRoute
import com.ahmad.nafs.ui.reports.ReportListRoute
import com.ahmad.nafs.ui.tasks.TaskRoute

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
        RootAppState.RestoringSession -> LoadingScreen("Restoring your session…")
        RootAppState.SignedOut -> AuthRoute()
        RootAppState.SessionRefreshFailed -> NafsErrorState(
            title = "We couldn't refresh your session",
            message = "Check your connection and try again. You stay signed out until your session is confirmed.",
            onRetry = onRetrySession,
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
        )
        RootAppState.LoadingProfile -> LoadingScreen("Loading your profile…")
        is RootAppState.ProfileFailure -> NafsErrorState(
            title = "Your profile didn't load",
            message = appState.message,
            onRetry = onRetryProfile,
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
        )
        is RootAppState.NeedsOnboarding -> OnboardingRoute(profile = appState.profile)
        is RootAppState.Ready -> key(appState.profile.userId) {
            AuthenticatedNafsApp(
                themeMode = themeMode,
                profile = appState.profile,
                onThemeModeChange = onThemeModeChange,
                onSignOut = onSignOut,
                requestedDestination = requestedDestination,
                onDestinationHandled = onDestinationHandled,
                profileMessage = profileMessage,
                onUseDeviceTimezone = onUseDeviceTimezone,
                onDismissProfileMessage = onDismissProfileMessage,
            )
        }
    }
}

/** Screens a notification, app shortcut or home-screen widget may open. Anything else is ignored. */
internal val externalDestinations = setOf(
    "coach", "coach/letters", "deen", "prayer-times", "qibla", "tasbih", "progress", "checkin", "health",
    "challenges", "library", "tasks/new", "habits/new",
)

internal fun isExternalDestination(route: String): Boolean =
    route in tabRoutes || route in externalDestinations || Regex("^progress\\?weekStart=\\d{4}-\\d{2}-\\d{2}$").matches(route)

/** Full-screen flows where the tab bar would crowd a composer or editor. */
private fun hidesBottomBar(route: String?): Boolean =
    route != null && (route == "coach" || route.startsWith("coach/") || route.startsWith("advice"))

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
    val currentRoute = backStackEntry?.destination?.route
    var showQuickAdd by rememberSaveable { mutableStateOf(false) }
    val online = isOnline()
    val open: (String) -> Unit = { route -> navController.navigate(route) { launchSingleTop = true } }

    if (showQuickAdd) QuickAddSheet(
        onDismiss = { showQuickAdd = false },
        onNavigate = { route -> showQuickAdd = false; open(route) },
    )

    LaunchedEffect(requestedDestination) {
        val route = requestedDestination?.takeIf(::isExternalDestination) ?: return@LaunchedEffect
        // A stale shortcut or notification must never crash the app; at worst it leaves Home open.
        runCatching {
            if (route in tabRoutes) navController.navigateTopDestination(route)
            // A notification opens a fresh screen (for example this week's report), never an older saved one.
            else navController.navigate(route) { popUpTo("dashboard") { saveState = true }; launchSingleTop = true }
        }
        onDestinationHandled()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AnimatedVisibility(
                visible = !hidesBottomBar(currentRoute),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                NafsBottomBar(
                    currentRoute = currentRoute,
                    onSelect = navController::navigateTopDestination,
                    onQuickAdd = { showQuickAdd = true },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            NavHost(navController = navController, startDestination = "dashboard", modifier = Modifier.fillMaxSize()) {
                composable("dashboard") {
                    DashboardRoute(
                        profile = profile,
                        onOpenTasks = { open("tasks") },
                        onOpenHabits = { open("habits") },
                        onOpenGoals = { open("goals") },
                        onOpenChallenges = { open("challenges") },
                        onOpenHealth = { open("health") },
                        onOpenDeen = { open("deen") },
                        onOpenCheckIn = { open("checkin") },
                        onOpenCoach = { open("coach") },
                        onOpenLibrary = { open("library") },
                        onOpenProfile = { navController.navigateTopDestination("profile") },
                    )
                }
                composable("tasks") { TaskRoute(profile = profile) }
                composable("tasks/new") { TaskRoute(profile = profile, createOnOpen = true) }
                composable("habits/new") { HabitRoute(profile = profile, createOnOpen = true) }
                composable("goals/new") { GoalRoute(profile = profile, createOnOpen = true) }
                composable("challenges/new") { ChallengeRoute(profile = profile, createOnOpen = true) }
                composable("habits") { HabitRoute(profile = profile) }
                composable("goals") { GoalRoute(profile = profile) }
                composable("challenges") { ChallengeRoute(profile = profile) }
                composable("health") { HealthRoute(profile = profile) }
                composable("deen") { DeenRoute(profile = profile) }
                composable("checkin") { CheckInRoute(profile = profile) }
                composable("prayer-times") { if (profile.deenEnabled) PrayerTimesRoute(profile, { navController.popBackStack() }) }
                composable("achievements") { AchievementsRoute(profile, { navController.popBackStack() }) }
                composable("library") { LibraryScreen(profile, onBack = { navController.popBackStack() }, onNavigate = open) }
                RecordFeature.entries.forEach { feature ->
                    composable("records/${feature.name.lowercase()}") { RecordRoute(profile, feature, onBack = { navController.popBackStack() }) }
                }
                composable("history") {
                    ProgressRoute(profile, true, onBack = { navController.popBackStack() },
                        onReview = { period, offset -> navController.navigate("advice?period=$period&offset=$offset") })
                }
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
                )) { entry ->
                    AdviceRoute(profile, entry.arguments?.getString("period"), entry.arguments?.getInt("offset") ?: 0,
                        onBack = { navController.popBackStack() })
                }
                composable("coach") {
                    CoachRoute(profile = profile, onBack = { navController.popBackStack() },
                        onOpenReports = { open("coach/reports") },
                        onOpenLetters = { open("coach/letters") },
                        onOpenRecovery = { open("coach/plans") },
                        onPlanFromChat = { chat -> open("coach/plans?conversationId=${chat.id}&version=${Uri.encode(chat.updatedAt)}") })
                }
                composable("coach/plans?conversationId={conversationId}&version={version}", arguments = listOf(
                    navArgument("conversationId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("version") { type = NavType.StringType; nullable = true; defaultValue = null },
                )) { entry ->
                    RecoveryRoute(profile, entry.arguments?.getString("conversationId"), entry.arguments?.getString("version"),
                        onBack = { navController.popBackStack() }, onOpenFeature = open)
                }
                composable("coach/letters") { LetterRoute(profile = profile, onBack = { navController.popBackStack() }) }
                composable("coach/reports") {
                    ReportListRoute(profile = profile, onBack = { navController.popBackStack() },
                        onOpenReport = { id -> open("coach/reports/$id") })
                }
                composable("coach/reports/{reportId}") { entry ->
                    ReportDetailRoute(profile = profile, reportId = entry.arguments?.getString("reportId").orEmpty(),
                        onBack = { navController.popBackStack() })
                }
                composable("profile") {
                    ProfileRoute(
                        themeMode = themeMode,
                        profile = profile,
                        onThemeModeChange = onThemeModeChange,
                        onSignOut = onSignOut,
                        profileMessage = profileMessage,
                        onUseDeviceTimezone = onUseDeviceTimezone,
                        onDismissProfileMessage = onDismissProfileMessage,
                        onOpenRoute = open,
                        onBack = { navController.popBackStack() },
                    )
                }
            }

            // Offline notice floats above the tab bar instead of pushing content around.
            AnimatedVisibility(
                visible = !online,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.inverseSurface, shadowElevation = 6.dp) {
                    androidx.compose.foundation.layout.Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.CloudOff, null, tint = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.size(16.dp))
                        Text("Offline · your saved records stay available", color = MaterialTheme.colorScheme.inverseOnSurface,
                            style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingScreen(message: String) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            NafsBrandMark(size = 88.dp)
            CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.5.dp)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ConfigurationMissingScreen() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NafsIconBadge(Icons.Rounded.Key, MaterialTheme.colorScheme.secondary, size = 72.dp, iconSize = 34.dp)
            Text("Connect NAFS", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Add NAFS_SUPABASE_URL and NAFS_SUPABASE_PUBLISHABLE_KEY to your Gradle configuration, then rebuild the app. " +
                    "No service-role or AI provider key belongs in Android.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
