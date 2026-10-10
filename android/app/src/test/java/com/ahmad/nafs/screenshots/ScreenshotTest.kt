package com.ahmad.nafs.screenshots

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ahmad.nafs.core.auth.SignUpGender
import com.ahmad.nafs.core.preferences.ThemeMode
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.ui.LibraryScreen
import com.ahmad.nafs.ui.QuickAddContent
import com.ahmad.nafs.ui.auth.AuthMode
import com.ahmad.nafs.ui.auth.AuthScreen
import com.ahmad.nafs.ui.auth.AuthUiState
import com.ahmad.nafs.ui.theme.NafsTheme
import com.github.takahirom.roborazzi.captureRoboImage
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
class ScreenshotTest {

    private fun shot(name: String, content: @Composable () -> Unit) {
        listOf(ThemeMode.DARK to "dark", ThemeMode.LIGHT to "light").forEach { (mode, suffix) ->
            captureRoboImage("build/outputs/roborazzi/${name}_$suffix.png") {
                NafsTheme(mode) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.fillMaxSize()) { content() }
                    }
                }
            }
        }
    }

    @Test fun authSignIn() = shot("01_auth_signin") {
        AuthScreen(AuthUiState(email = "ahmad@example.com", password = "secret12"), {}, {}, {}, {}, {}, {}, {}, showGoogle = true)
    }

    @Test fun authSignUp() = shot("02_auth_signup") {
        AuthScreen(
            AuthUiState(mode = AuthMode.SIGN_UP, name = "Ahmad Siddique", gender = SignUpGender.MALE, email = "ahmad@example.com",
                errorMessage = "Password must be at least 6 characters."),
            {}, {}, {}, {}, {}, {}, {},
        )
    }

    @Test fun authForgot() = shot("03_auth_forgot") {
        AuthScreen(AuthUiState(mode = AuthMode.FORGOT_PASSWORD, email = "ahmad@example.com"), {}, {}, {}, {}, {}, {}, {})
    }

    @Test fun explore() = shot("10_explore") { LibraryScreen(sampleProfile, onBack = {}) {} }

    @Test fun quickAdd() = shot("11_quick_add") { QuickAddContent {} }

    companion object {
        val sampleProfile = UserProfile(
            userId = "user-1", name = "Ahmad Siddique", email = "ahmad@example.com", timezone = "Asia/Karachi",
            avatarUrl = null, onboardingComplete = true, deenEnabled = true,
        )
    }
}
