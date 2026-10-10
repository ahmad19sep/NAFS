package com.ahmad.nafs.core.goals

import com.ahmad.nafs.domain.goals.GoalCategory
import com.ahmad.nafs.domain.goals.GoalProgress
import com.ahmad.nafs.domain.goals.GoalType
import java.time.LocalDate

data class GoalMilestone(
    val id: String,
    val goalId: String,
    val title: String,
    val done: Boolean,
    val doneAt: String?,
    val targetDate: LocalDate?,
    val createdAt: String,
)

data class Goal(
    val id: String,
    val userId: String,
    val title: String,
    val emoji: String,
    val description: String?,
    val type: GoalType,
    val category: GoalCategory?,
    val startDate: LocalDate,
    val deadline: LocalDate?,
    val storedProgressPercent: Int,
    val targetValue: Double?,
    val currentValue: Double?,
    val unit: String?,
    val linkedHabitIds: List<String>,
    val milestones: List<GoalMilestone>,
    val createdAt: String,
    val updatedAt: String,
) {
    val progressPercent: Int
        get() = GoalProgress.resolve(
            storedProgressPercent,
            milestones.map(GoalMilestone::done),
            currentValue,
            targetValue,
        )
}

data class SaveGoalIntent(
    val requestId: String,
    val userId: String,
    val goalId: String? = null,
    val title: String,
    val emoji: String,
    val description: String?,
    val type: GoalType,
    val category: GoalCategory?,
    val startDate: LocalDate,
    val deadline: LocalDate?,
    val targetValue: Double?,
    val currentValue: Double?,
    val unit: String?,
    val milestoneTitles: List<String> = emptyList(),
    val linkedHabitIds: List<String>? = null,
)

object GoalDefinitionValidator {
    fun validationError(intent: SaveGoalIntent): String? = when {
        intent.title.trim().isEmpty() -> "Enter a goal title."
        intent.title.trim().length > 120 -> "Keep the goal title under 120 characters."
        intent.emoji.trim().isEmpty() -> "Choose an emoji for the goal."
        intent.description?.trim().orEmpty().length > 1_000 ->
            "Keep the description under 1,000 characters."
        intent.deadline != null && intent.deadline < intent.startDate ->
            "Deadline cannot be before the start date."
        intent.targetValue != null &&
            (!intent.targetValue.isFinite() || intent.targetValue <= 0.0) ->
            "Numeric target must be greater than zero."
        intent.currentValue != null &&
            (!intent.currentValue.isFinite() || intent.currentValue < 0.0) ->
            "Current progress cannot be negative."
        intent.targetValue != null && intent.unit?.trim().isNullOrEmpty() ->
            "Enter a unit for the numeric target."
        intent.targetValue == null && intent.currentValue != null ->
            "Set a numeric target before current progress."
        intent.milestoneTitles.count { it.trim().isNotEmpty() } > 20 ->
            "Use no more than 20 milestones."
        intent.milestoneTitles.any { it.trim().length > 160 } ->
            "Keep each milestone under 160 characters."
        intent.linkedHabitIds != null && (intent.linkedHabitIds.size > 100 || intent.linkedHabitIds.any { id -> runCatching { java.util.UUID.fromString(id).toString() == id.lowercase() }.getOrDefault(false).not() }) ->
            "Choose valid habits from your account."
        else -> null
    }
}

sealed interface GoalLoadResult {
    data class Success(val goals: List<Goal>, val isOffline: Boolean) : GoalLoadResult
    data class Failure(val message: String) : GoalLoadResult
}

sealed interface GoalActionResult {
    data class Success(val goal: Goal? = null) : GoalActionResult
    data class Failure(val message: String) : GoalActionResult
}
