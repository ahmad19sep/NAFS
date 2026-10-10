package com.ahmad.nafs.core.challenges

import com.ahmad.nafs.domain.challenges.ChallengeFrequency
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChallengeDefinitionValidatorTest {
    private val valid = SaveChallengeIntent(
        requestId = "request-id",
        userId = "user-id",
        title = "Read with focus",
        emoji = "R",
        description = "I want to rebuild a calm reading rhythm.",
        frequency = ChallengeFrequency.DAILY,
        durationDays = 30,
        startDate = LocalDate.parse("2026-10-09"),
    )

    @Test
    fun `valid challenge passes`() {
        assertNull(ChallengeDefinitionValidator.validationError(valid))
    }

    @Test
    fun `a personal reason is required`() {
        assertEquals(
            "Add your reason for taking this challenge.",
            ChallengeDefinitionValidator.validationError(valid.copy(description = "   ")),
        )
    }

    @Test
    fun `duration respects cadence minimum`() {
        assertEquals(
            "Duration must be at least 30 days for this cadence.",
            ChallengeDefinitionValidator.validationError(
                valid.copy(frequency = ChallengeFrequency.MONTHLY, durationDays = 29),
            ),
        )
    }

    @Test
    fun `default durations stay inside allowed ranges`() {
        ChallengeFrequency.entries.forEach { frequency ->
            val duration = ChallengeDefinitionValidator.defaultDuration(frequency)
            assertNull(
                ChallengeDefinitionValidator.validationError(
                    valid.copy(frequency = frequency, durationDays = duration),
                ),
            )
        }
    }

    @Test
    fun `sadqa pledge must be positive when present`() {
        assertEquals(
            "Sadqa must be a positive amount.",
            ChallengeDefinitionValidator.validationError(valid.copy(sadqaAmount = 0.0)),
        )
        assertNull(
            ChallengeDefinitionValidator.validationError(
                valid.copy(sadqaAmount = 500.0, sadqaCurrency = "PKR"),
            ),
        )
    }

    @Test
    fun `sadqa currency is constrained`() {
        assertEquals(
            "Choose a supported sadqa currency.",
            ChallengeDefinitionValidator.validationError(
                valid.copy(sadqaAmount = 10.0, sadqaCurrency = "BTC"),
            ),
        )
    }
}
