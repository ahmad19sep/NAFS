package com.ahmad.nafs

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ahmad.nafs.core.coach.CoachMessage
import com.ahmad.nafs.core.coach.CoachSendIntent
import com.ahmad.nafs.core.preferences.ThemeMode
import com.ahmad.nafs.ui.coach.CoachScreen
import com.ahmad.nafs.ui.coach.CoachUiState
import com.ahmad.nafs.ui.theme.NafsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Synthetic UI only; no account, network or AI calls. */
class CoachRecoveryTest {
    @get:Rule val compose = createComposeRule()
    private val pending = CoachSendIntent(listOf(CoachMessage("user", "A synthetic question")),
        "12345678-1234-1234-1234-123456789abc", null)

    @Test fun retainedMessageAlwaysShowsRetryEvenWithoutAnError() {
        var sends = 0
        compose.setContent {
            NafsTheme(ThemeMode.LIGHT) {
                CoachScreen(CoachUiState(pending = pending, isLoading = false), {}, {}, {}, {}, {}, {}, {}, {}, {}, { sends++ }, {})
            }
        }
        compose.onNodeWithText("Retry message").assertIsDisplayed().assertIsEnabled().performClick()
        assertEquals(1, sends)
        compose.onNodeWithText("Check for reply").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Edit message").assertDoesNotExist()
        compose.onNodeWithText("Coach is replying...").assertDoesNotExist()
    }

    @Test fun unconfirmedReplyRequiresCheckingSavedHistoryBeforeRetryOrEditing() {
        val state = mutableStateOf(CoachUiState(pending = pending, isLoading = false, requiresReload = true))
        var edits = 0
        compose.setContent {
            NafsTheme(ThemeMode.LIGHT) {
                CoachScreen(state.value, {}, {}, {}, {}, {}, {
                    state.value = state.value.copy(requiresReload = false, pendingReloaded = true)
                }, {}, {}, {}, {}, { edits++ })
            }
        }
        compose.onNodeWithText("Retry message").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("Edit message").assertDoesNotExist()
        compose.onNodeWithText("Check for reply").assertIsDisplayed().performClick()
        compose.onNodeWithText("Retry message").assertIsEnabled()
        compose.onNodeWithText("Edit message").assertIsDisplayed().performClick()
        assertEquals(1, edits)
    }
}
