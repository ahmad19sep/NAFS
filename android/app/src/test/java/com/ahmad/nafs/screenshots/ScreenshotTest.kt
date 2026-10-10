package com.ahmad.nafs.screenshots

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ahmad.nafs.core.auth.SignUpGender
import com.ahmad.nafs.core.preferences.ThemeMode
import com.ahmad.nafs.ui.LibraryScreen
import com.ahmad.nafs.ui.NafsBottomBar
import com.ahmad.nafs.ui.QuickAddContent
import com.ahmad.nafs.ui.auth.AuthMode
import com.ahmad.nafs.ui.auth.AuthScreen
import com.ahmad.nafs.ui.auth.AuthUiState
import com.ahmad.nafs.ui.components.NafsLoadingState
import com.ahmad.nafs.ui.dashboard.DashboardActions
import com.ahmad.nafs.ui.dashboard.DashboardScreen
import com.ahmad.nafs.ui.dashboard.DashboardUiState
import com.ahmad.nafs.ui.profile.ProfileActions
import com.ahmad.nafs.ui.profile.ProfileScreen
import com.ahmad.nafs.ui.profile.ProfileUiState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders screens with fixed sample state so the design can be reviewed without a device.
 * Images are written only by `./gradlew :app:recordRoborazziDebug`; plain unit test runs skip them.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A plain Application: the real one schedules WorkManager jobs that have no place in a render test.
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xhdpi", application = Application::class)
class ScreenshotTest : ScreenshotTestBase() {

    @Test fun authSignIn() = captureScreen("01_auth_signin") {
        AuthScreen(AuthUiState(email = "ahmad@example.com", password = "secret12"), {}, {}, {}, {}, {}, {}, {}, showGoogle = true)
    }

    @Test fun authSignUp() = captureScreen("02_auth_signup") {
        AuthScreen(
            AuthUiState(mode = AuthMode.SIGN_UP, name = "Ahmad Siddique", gender = SignUpGender.MALE, email = "ahmad@example.com",
                errorMessage = "Use 8–128 characters for your password."),
            {}, {}, {}, {}, {}, {}, {},
        )
    }

    @Test fun authForgot() = captureScreen("03_auth_forgot") {
        AuthScreen(AuthUiState(mode = AuthMode.FORGOT_PASSWORD, email = "ahmad@example.com"), {}, {}, {}, {}, {}, {}, {})
    }

    private val dashboardState = DashboardUiState(
        userId = SampleData.USER, timezone = "Asia/Karachi", today = SampleData.today,
        tasks = SampleData.tasks, habits = SampleData.habits, habitLogs = SampleData.habitLogs, goals = SampleData.goals,
        isLoading = false,
    )

    @Test fun home() = captureScreen("04_home") {
        Column {
            Box(Modifier.weight(1f)) { DashboardScreen(SampleData.profile, dashboardState, DashboardActions()) }
            NafsBottomBar(currentRoute = "dashboard", onSelect = {}, onQuickAdd = {})
        }
    }

    @Test fun homeEmpty() = captureScreen("05_home_empty") {
        DashboardScreen(SampleData.profile.copy(deenEnabled = false), DashboardUiState(isLoading = false, today = SampleData.today), DashboardActions())
    }

    @Test fun loading() = captureScreen("06_loading") { NafsLoadingState(Modifier.statusBarsPadding()) }

    @Test fun profile() = captureScreen("07_profile") {
        ProfileScreen(SampleData.profile, ThemeMode.SYSTEM, ProfileUiState(deviceTimezone = "Asia/Karachi"), ProfileActions(onBack = {}), notifications = {})
    }

    @Test fun explore() = captureScreen("10_explore") { LibraryScreen(SampleData.profile, onBack = {}) {} }

    @Test fun quickAdd() = captureScreen("11_quick_add") { QuickAddContent {} }
}
