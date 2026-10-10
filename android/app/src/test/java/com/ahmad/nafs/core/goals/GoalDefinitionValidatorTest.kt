package com.ahmad.nafs.core.goals

import com.ahmad.nafs.domain.goals.GoalType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoalDefinitionValidatorTest {
    private val today = LocalDate.parse("2026-10-09")

    @Test
    fun validGoalPasses() {
        assertNull(GoalDefinitionValidator.validationError(intent()))
    }

    @Test
    fun deadlineCannotPrecedeStartDate() {
        val error = GoalDefinitionValidator.validationError(
            intent(deadline = today.minusDays(1)),
        )

        assertEquals("Deadline cannot be before the start date.", error)
    }

    @Test
    fun numericGoalRequiresPositiveTargetAndUnit() {
        assertEquals(
            "Numeric target must be greater than zero.",
            GoalDefinitionValidator.validationError(intent(targetValue = 0.0, unit = "books")),
        )
        assertEquals(
            "Enter a unit for the numeric target.",
            GoalDefinitionValidator.validationError(intent(targetValue = 10.0, unit = "")),
        )
    }

    @Test
    fun milestoneCountIsBounded() {
        val error = GoalDefinitionValidator.validationError(
            intent(milestones = List(21) { "Milestone $it" }),
        )

        assertEquals("Use no more than 20 milestones.", error)
    }

    private fun intent(
        deadline: LocalDate? = today.plusDays(30),
        targetValue: Double? = null,
        unit: String? = null,
        milestones: List<String> = emptyList(),
    ) = SaveGoalIntent(
        requestId = "request-id",
        userId = "user-id",
        title = "Read consistently",
        emoji = "📚",
        description = null,
        type = GoalType.MONTHLY,
        category = null,
        startDate = today,
        deadline = deadline,
        targetValue = targetValue,
        currentValue = targetValue?.let { 0.0 },
        unit = unit,
        milestoneTitles = milestones,
    )
}
