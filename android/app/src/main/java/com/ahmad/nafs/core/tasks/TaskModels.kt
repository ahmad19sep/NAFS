package com.ahmad.nafs.core.tasks

import com.ahmad.nafs.domain.tasks.TaskCompletionStatus
import com.ahmad.nafs.domain.tasks.TaskPeriodType
import java.time.LocalDate

enum class TaskPriority(val rank: Int) {
    HIGH(0),
    MEDIUM(1),
    LOW(2),
}

data class NafsTask(
    val id: String,
    val userId: String,
    val title: String,
    val note: String?,
    val type: TaskPeriodType,
    val priority: TaskPriority,
    val status: TaskCompletionStatus,
    val periodDate: LocalDate,
    val dueTime: String?,
    val completedAt: String?,
    val createdAt: String,
    val updatedAt: String,
) {
    val isCompleted: Boolean
        get() = status == TaskCompletionStatus.COMPLETED
}

data class CreateTaskIntent(
    val requestId: String,
    val userId: String,
    val title: String,
    val note: String?,
    val type: TaskPeriodType,
    val priority: TaskPriority,
    val dueTime: String?,
    val today: LocalDate,
)

data class EditTaskIntent(
    val userId: String,
    val taskId: String,
    val title: String,
    val note: String?,
    val type: TaskPeriodType,
    val priority: TaskPriority,
    val dueTime: String?,
)

data class TaskCreatePayload(
    val userId: String,
    val title: String,
    val note: String?,
    val type: TaskPeriodType,
    val priority: TaskPriority,
    val periodDate: LocalDate,
    val dueTime: String?,
)

object TaskCreateReplayRule {
    fun isSameIntent(existing: TaskCreatePayload, requested: TaskCreatePayload): Boolean =
        existing == requested
}

sealed interface TaskLoadResult {
    data class Success(
        val tasks: List<NafsTask>,
        val isOffline: Boolean,
        val lastSyncedAtEpochMillis: Long,
    ) : TaskLoadResult

    data class Failure(val message: String) : TaskLoadResult
}

sealed interface TaskActionResult {
    data class Success(val task: NafsTask? = null) : TaskActionResult

    data class Failure(val message: String) : TaskActionResult
}
