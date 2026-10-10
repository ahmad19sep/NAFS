package com.ahmad.nafs.core.tasks

import java.time.LocalDate

interface TaskRepository {
    suspend fun load(userId: String, today: LocalDate): TaskLoadResult

    suspend fun create(intent: CreateTaskIntent): TaskActionResult

    suspend fun update(intent: EditTaskIntent): TaskActionResult

    suspend fun setCompleted(
        userId: String,
        taskId: String,
        completed: Boolean,
    ): TaskActionResult

    /**
     * Moves an open task to the period that contains [periodDate] (for example a missed task to today or
     * tomorrow). The task stays active; a completed task is not moved.
     */
    suspend fun reschedule(
        userId: String,
        taskId: String,
        periodDate: LocalDate,
    ): TaskActionResult

    suspend fun softDelete(userId: String, taskId: String): TaskActionResult

    suspend fun restore(userId: String, taskId: String): TaskActionResult

    suspend fun clear(userId: String)
}
