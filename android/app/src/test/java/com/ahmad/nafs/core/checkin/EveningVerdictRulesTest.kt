package com.ahmad.nafs.core.checkin

import com.ahmad.nafs.ui.checkin.CheckInUiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EveningVerdictRulesTest {
    private val record = DailyCheckIn("id", "user", "2026-10-09", listOf(CheckInTask("Read", true)), "Reflection", "Review", "2026-10-09T04:00:00Z")
    private val intent = EveningVerdictIntent("id", "2026-10-09T03:00:00Z", "stable-id", record.date, "Reflection", record.tasks)

    @Test fun `only an owned saved review matching the intent and a newer version is accepted`() {
        assertTrue(EveningVerdictRules.matches("user", intent, record, "Review"))
        assertFalse(EveningVerdictRules.matches("other", intent, record, "Review"))
        assertFalse(EveningVerdictRules.matches("user", intent, record.copy(id = "other"), "Review"))
        assertFalse(EveningVerdictRules.matches("user", intent, record.copy(eveningText = "Changed"), "Review"))
        assertFalse(EveningVerdictRules.matches("user", intent, record.copy(updatedAt = intent.expectedUpdatedAt), "Review"))
        assertFalse(EveningVerdictRules.matches("user", intent, record, "Invented response"))
    }

    @Test fun `generation requires saved notes and locks editing while active`() {
        val state = CheckInUiState(draft = record, saved = record, isLoading = false)
        assertTrue(state.canGenerate)
        assertFalse(state.copy(isGenerating = true).canGenerate)
        assertFalse(state.copy(isGenerating = true).canEdit)
        assertFalse(state.copy(draft = record.copy(eveningText = "Unsaved")).canGenerate)
        assertFalse(state.copy(saved = null).canGenerate)
        assertFalse(state.copy(isOffline = true).canGenerate)
        assertFalse(state.copy(generatedVersion = record.updatedAt).canGenerate)
    }
}
