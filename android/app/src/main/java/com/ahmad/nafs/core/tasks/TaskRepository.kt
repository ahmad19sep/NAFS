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

    suspend fun softDelete(userId: String, taskId: String): TaskActionResult

    suspend fun restore(userId: String, taskId: String): TaskActionResult

    suspend fun clear(userId: String)
}
