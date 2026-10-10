package com.ahmad.nafs.domain.tasks

enum class TaskCompletionStatus {
    ACTIVE,
    COMPLETED,
}

data class TaskStatusDecision(
    val status: TaskCompletionStatus,
    val changed: Boolean,
)

object TaskStatusRule {
    fun resolve(
        current: TaskCompletionStatus,
        completed: Boolean,
    ): TaskStatusDecision {
        val desired = if (completed) TaskCompletionStatus.COMPLETED else TaskCompletionStatus.ACTIVE
        return TaskStatusDecision(status = desired, changed = desired != current)
    }
}

object TaskDraftValidator {
    private val timePattern = Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$")

    fun titleError(title: String): String? =
        if (title.trim().isEmpty()) "Enter a task title." else null

    fun dueTimeError(type: TaskPeriodType, dueTime: String): String? = when {
        dueTime.isBlank() -> null
        type != TaskPeriodType.DAILY -> "Only daily tasks can have a due time."
        !timePattern.matches(dueTime) -> "Use a 24-hour time such as 09:30."
        else -> null
    }
}
