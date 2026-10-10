package com.ahmad.nafs.core.tasks

import com.ahmad.nafs.core.database.CachedTaskDao
import com.ahmad.nafs.core.database.CachedTaskEntity
import com.ahmad.nafs.core.network.SupabaseClientProvider
import com.ahmad.nafs.domain.tasks.TaskCompletionStatus
import com.ahmad.nafs.domain.tasks.TaskPeriod
import com.ahmad.nafs.domain.tasks.TaskPeriodType
import com.ahmad.nafs.domain.tasks.TaskStatusRule
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Singleton
class SupabaseTaskRepository @Inject constructor(
    private val clientProvider: SupabaseClientProvider,
    private val cache: CachedTaskDao,
) : TaskRepository {
    override suspend fun load(userId: String, today: LocalDate): TaskLoadResult {
        val startDate = today.minusMonths(HISTORY_MONTHS)
        return try {
            val tasks = requireClient().from("tasks")
                .select(TASK_COLUMNS) {
                    filter {
                        eq("user_id", userId)
                        gte("period_date", startDate.toString())
                    }
                }
                .decodeList<TaskDto>()
                .map(TaskDto::toModel)
            val syncedAt = System.currentTimeMillis()
            cache.replaceForUser(userId, tasks.map(NafsTask::toEntity), syncedAt)
            TaskLoadResult.Success(tasks, isOffline = false, lastSyncedAtEpochMillis = syncedAt)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            loadCached(userId, startDate)
        }
    }

    override suspend fun create(intent: CreateTaskIntent): TaskActionResult {
        val normalized = intent.copy(
            title = intent.title.trim(),
            note = intent.note?.trim()?.takeIf(String::isNotEmpty),
            dueTime = intent.dueTime?.trim()?.takeIf(String::isNotEmpty),
        )
        val insert = normalized.toInsertDto()
        return try {
            val task = requireClient().from("tasks")
                .insert(insert) { select(TASK_COLUMNS) }
                .decodeSingle<TaskDto>()
                .toModel()
            cache.upsert(task.toEntity())
            TaskActionResult.Success(task)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            resolveCreateRetry(normalized)
        }
    }

    override suspend fun update(intent: EditTaskIntent): TaskActionResult = taskAction {
        val title = intent.title.trim()
        val note = intent.note?.trim()?.takeIf(String::isNotEmpty)
        val dueTime = intent.dueTime?.trim()?.takeIf(String::isNotEmpty)
            ?.takeIf { intent.type == TaskPeriodType.DAILY }
        val now = Instant.now().toString()
        val updated = requireClient().from("tasks")
            .update(
                EditTaskDto(
                    title = title,
                    note = note,
                    priority = intent.priority.toWireValue(),
                    dueTime = dueTime,
                    updatedAt = now,
                ),
            ) {
                select(TASK_COLUMNS)
                filter {
                    eq("id", intent.taskId)
                    eq("user_id", intent.userId)
                }
            }
            .decodeSingle<TaskDto>()
            .toModel()
        cache.upsert(updated.toEntity())
        TaskActionResult.Success(updated)
    }

    override suspend fun setCompleted(
        userId: String,
        taskId: String,
        completed: Boolean,
    ): TaskActionResult = taskAction {
        val current = findTask(userId, taskId)
            ?: return@taskAction TaskActionResult.Failure("That task is no longer available.")
        val currentTask = current.toModel()
        val decision = TaskStatusRule.resolve(currentTask.status, completed)
        if (!decision.changed) {
            cache.upsert(currentTask.toEntity())
            return@taskAction TaskActionResult.Success(currentTask)
        }

        val now = Instant.now().toString()
        val updated = requireClient().from("tasks")
            .update(
                TaskStatusUpdateDto(
                    status = decision.status.toWireValue(),
                    completedAt = if (completed) now else null,
                    updatedAt = now,
                ),
            ) {
                select(TASK_COLUMNS)
                filter {
                    eq("id", taskId)
                    eq("user_id", userId)
                }
            }
            .decodeSingle<TaskDto>()
            .toModel()
        cache.upsert(updated.toEntity())
        TaskActionResult.Success(updated)
    }

    override suspend fun softDelete(userId: String, taskId: String): TaskActionResult = taskAction {
        if (findTask(userId, taskId) == null) {
            return@taskAction TaskActionResult.Failure("That task is no longer available.")
        }
        requireClient().from("tasks").update(
            SoftDeleteDto(deletedAt = Instant.now().toString()),
        ) {
            filter {
                eq("id", taskId)
                eq("user_id", userId)
            }
        }
        cache.deleteById(userId, taskId)
        TaskActionResult.Success()
    }

    override suspend fun restore(userId: String, taskId: String): TaskActionResult = taskAction {
        val restored = requireClient().from("tasks")
            .update(
                RestoreTaskDto(
                    deletedAt = null,
                    updatedAt = Instant.now().toString(),
                ),
            ) {
                select(TASK_COLUMNS)
                filter {
                    eq("id", taskId)
                    eq("user_id", userId)
                }
            }
            .decodeSingle<TaskDto>()
            .toModel()
        cache.upsert(restored.toEntity())
        TaskActionResult.Success(restored)
    }

    override suspend fun clear(userId: String) {
        cache.clearForUser(userId)
    }

    private suspend fun loadCached(userId: String, startDate: LocalDate): TaskLoadResult = try {
        val metadata = cache.getMetadata(userId)
            ?: return TaskLoadResult.Failure(
                "Unable to load tasks. Check your connection and try again.",
            )
        val tasks = cache.getForUserSince(userId, startDate.toString()).map(CachedTaskEntity::toModel)
        TaskLoadResult.Success(
            tasks = tasks,
            isOffline = true,
            lastSyncedAtEpochMillis = metadata.lastSyncedAtEpochMillis,
        )
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        TaskLoadResult.Failure("Unable to load tasks. Check your connection and try again.")
    }

    private suspend fun resolveCreateRetry(intent: CreateTaskIntent): TaskActionResult = try {
        val existing = findTask(intent.userId, intent.requestId)
        when {
            existing == null -> TaskActionResult.Failure(
                "Task not saved. Check your connection and retry safely.",
            )
            existing.matches(intent) -> {
                val task = existing.toModel()
                cache.upsert(task.toEntity())
                TaskActionResult.Success(task)
            }
            else -> TaskActionResult.Failure(
                "This save request was already used for different task details.",
            )
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        TaskActionResult.Failure("Task not saved. Check your connection and retry safely.")
    }

    private suspend fun findTask(userId: String, taskId: String): TaskDto? =
        requireClient().from("tasks")
            .select(TASK_COLUMNS) {
                filter {
                    eq("id", taskId)
                    eq("user_id", userId)
                }
                limit(1)
            }
            .decodeList<TaskDto>()
            .firstOrNull()

    private suspend fun taskAction(block: suspend () -> TaskActionResult): TaskActionResult = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        TaskActionResult.Failure("The task was not changed. Check your connection and try again.")
    }

    private fun requireClient() = checkNotNull(clientProvider.client) {
        "Supabase is not configured."
    }

    private companion object {
        const val HISTORY_MONTHS = 6L
        val TASK_COLUMNS = Columns.list(
            "id",
            "user_id",
            "title",
            "note",
            "type",
            "priority",
            "status",
            "period_date",
            "due_time",
            "completed_at",
            "created_at",
            "updated_at",
        )
    }
}

@Serializable
private data class TaskDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val title: String,
    val note: String?,
    val type: String,
    val priority: String,
    val status: String,
    @SerialName("period_date") val periodDate: String,
    @SerialName("due_time") val dueTime: String?,
    @SerialName("completed_at") val completedAt: String?,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
) {
    fun toModel() = NafsTask(
        id = id,
        userId = userId,
        title = title,
        note = note,
        type = TaskPeriodType.valueOf(type.uppercase()),
        priority = TaskPriority.valueOf(priority.uppercase()),
        status = TaskCompletionStatus.valueOf(status.uppercase()),
        periodDate = LocalDate.parse(periodDate),
        dueTime = dueTime?.take(5),
        completedAt = completedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    fun matches(intent: CreateTaskIntent): Boolean =
        TaskCreateReplayRule.isSameIntent(
            existing = TaskCreatePayload(
                userId = userId,
                title = title,
                note = note,
                type = TaskPeriodType.valueOf(type.uppercase()),
                priority = TaskPriority.valueOf(priority.uppercase()),
                periodDate = LocalDate.parse(periodDate),
                dueTime = dueTime?.take(5),
            ),
            requested = TaskCreatePayload(
                userId = intent.userId,
                title = intent.title,
                note = intent.note,
                type = intent.type,
                priority = intent.priority,
                periodDate = TaskPeriod.key(intent.type, intent.today),
                dueTime = intent.dueTime,
            ),
        )
}

@Serializable
private data class TaskInsertDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val title: String,
    val note: String?,
    val type: String,
    val priority: String,
    val status: String,
    @SerialName("period_date") val periodDate: String,
    @SerialName("due_time") val dueTime: String?,
)

@Serializable
private data class TaskStatusUpdateDto(
    val status: String,
    @SerialName("completed_at") val completedAt: String?,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
private data class EditTaskDto(
    val title: String,
    val note: String?,
    val priority: String,
    @SerialName("due_time") val dueTime: String?,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
private data class SoftDeleteDto(
    @SerialName("deleted_at") val deletedAt: String,
)

@Serializable
private data class RestoreTaskDto(
    @SerialName("deleted_at") val deletedAt: String?,
    @SerialName("updated_at") val updatedAt: String,
)

private fun CreateTaskIntent.toInsertDto() = TaskInsertDto(
    id = requestId,
    userId = userId,
    title = title,
    note = note,
    type = type.toWireValue(),
    priority = priority.toWireValue(),
    status = TaskCompletionStatus.ACTIVE.toWireValue(),
    periodDate = TaskPeriod.key(type, today).toString(),
    dueTime = if (type == TaskPeriodType.DAILY) dueTime else null,
)

private fun NafsTask.toEntity() = CachedTaskEntity(
    id = id,
    userId = userId,
    title = title,
    note = note,
    type = type.name,
    priority = priority.name,
    status = status.name,
    periodDate = periodDate.toString(),
    dueTime = dueTime,
    completedAt = completedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun CachedTaskEntity.toModel() = NafsTask(
    id = id,
    userId = userId,
    title = title,
    note = note,
    type = TaskPeriodType.valueOf(type),
    priority = TaskPriority.valueOf(priority),
    status = TaskCompletionStatus.valueOf(status),
    periodDate = LocalDate.parse(periodDate),
    dueTime = dueTime,
    completedAt = completedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun TaskPeriodType.toWireValue() = name.lowercase()

private fun TaskPriority.toWireValue() = name.lowercase()

private fun TaskCompletionStatus.toWireValue() = name.lowercase()
