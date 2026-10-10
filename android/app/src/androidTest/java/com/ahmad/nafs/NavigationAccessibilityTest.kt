package com.ahmad.nafs

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.ui.LibraryScreen
import com.ahmad.nafs.ui.QuickAddSheet
import com.ahmad.nafs.ui.navigateTopDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ahmad.nafs.ui.theme.NafsTheme
import com.ahmad.nafs.core.preferences.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NavigationAccessibilityTest {
    @get:Rule val compose = createComposeRule()
    @Test fun libraryRoutesAtLargeFont() {
        var route = ""
        compose.setContent {
            KeepScreenAwakeForTest()
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                NafsTheme(ThemeMode.LIGHT) {
                    LibraryScreen(UserProfile("test", "Test", "test@example.com", "Asia/Karachi", null, true)) { route = it }
                }
            }
        }
        compose.onNodeWithText("30-day history").performScrollTo().performClick()
        assertEquals("history", route)
    }
    @Test fun disabledFaithHidesFaithNavigationInDarkTheme() {
        compose.setContent {
            KeepScreenAwakeForTest()
            NafsTheme(ThemeMode.DARK) {
                LibraryScreen(UserProfile("test", "Test", "test@example.com", "Asia/Karachi", null, true, deenEnabled = false)) { }
            }
        }
        compose.onNodeWithText("Prayer times").assertDoesNotExist()
        compose.onNodeWithText("Deen").assertDoesNotExist()
        compose.onNodeWithText("Weekly & monthly reports").performScrollTo().assertExists()
    }
    @Test fun quickAddOpensTheSelectedNativeEditor() {
        var route = ""
        compose.setContent { KeepScreenAwakeForTest(); NafsTheme(ThemeMode.LIGHT) { QuickAddSheet({}, { route = it }) } }
        compose.onNodeWithText("New goal").performClick()
        assertEquals("goals/new", route)
    }
    @Test fun homeReturnsToDashboardInsteadOfRestoringItsReportChild() {
        compose.setContent {
            KeepScreenAwakeForTest()
            NafsTheme(ThemeMode.LIGHT) {
                val nav = rememberNavController()
                Column(Modifier.fillMaxSize()) {
                    Row {
                        Button(onClick = { nav.navigateTopDestination("dashboard") }) { Text("Home tab") }
                        Button(onClick = { nav.navigateTopDestination("tasks") }) { Text("Tasks tab") }
                    }
                    NavHost(nav, startDestination = "dashboard", modifier = Modifier.weight(1f)) {
                        composable("dashboard") { Button(onClick = { nav.navigate("progress") }) { Text("Open report") } }
                        composable("progress") { Text("Report detail") }
                        composable("tasks") { Text("Task list") }
                    }
                }
            }
        }
        compose.onNodeWithText("Open report").performClick()
        compose.onNodeWithText("Report detail").assertExists()
        compose.onNodeWithText("Home tab").performClick()
        compose.onNodeWithText("Open report").assertExists()
        compose.onNodeWithText("Report detail").assertDoesNotExist()
        compose.onNodeWithText("Open report").performClick()
        compose.onNodeWithText("Tasks tab").performClick()
        compose.onNodeWithText("Task list").assertExists()
        compose.onNodeWithText("Home tab").performClick()
        compose.onNodeWithText("Open report").assertExists()
        compose.onNodeWithText("Report detail").assertDoesNotExist()
    }
}

@Composable
private fun KeepScreenAwakeForTest() {
    val view = LocalView.current
    DisposableEffect(view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }
}
