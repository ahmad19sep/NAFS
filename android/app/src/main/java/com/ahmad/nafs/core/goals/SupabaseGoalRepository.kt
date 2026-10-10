package com.ahmad.nafs.core.goals

import com.ahmad.nafs.core.database.CachedGoalDao
import com.ahmad.nafs.core.database.CachedGoalEntity
import com.ahmad.nafs.core.database.CachedGoalMilestoneEntity
import com.ahmad.nafs.core.network.SupabaseClientProvider
import com.ahmad.nafs.domain.goals.GoalCategory
import com.ahmad.nafs.domain.goals.GoalProgress
import com.ahmad.nafs.domain.goals.GoalType
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Singleton
class SupabaseGoalRepository @Inject constructor(
    private val clientProvider: SupabaseClientProvider,
    private val cache: CachedGoalDao,
) : GoalRepository {
    override suspend fun load(userId: String): GoalLoadResult = try {
        val goalDtos = requireClient().from("goals").select(GOAL_COLUMNS) {
            filter { eq("user_id", userId) }
        }.decodeList<GoalDto>()
        val milestoneDtos = requireClient().from("goal_milestones")
            .select(MILESTONE_COLUMNS)
            .decodeList<GoalMilestoneDto>()
        val goals = assembleGoals(goalDtos, milestoneDtos)
        cache.replaceForUser(
            userId,
            goals.map(Goal::toEntity),
            goals.flatMap { goal -> goal.milestones.map { it.toEntity(userId) } },
            System.currentTimeMillis(),
        )
        GoalLoadResult.Success(goals.sortedByDescending(Goal::createdAt), isOffline = false)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        loadCached(userId)
    }

    override suspend fun create(intent: SaveGoalIntent): GoalActionResult {
        val normalized = intent.normalized()
        GoalDefinitionValidator.validationError(normalized)?.let {
            return GoalActionResult.Failure(it)
        }
        return try {
            validateLinks(normalized.userId, normalized.linkedHabitIds)
            val created = requireClient().from("goals")
                .insert(normalized.toInsertDto()) { select(GOAL_COLUMNS) }
                .decodeSingle<GoalDto>()
            finishCreate(normalized, created)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            resolveCreateRetry(normalized)
        }
    }

    override suspend fun update(intent: SaveGoalIntent): GoalActionResult {
        val normalized = intent.normalized()
        GoalDefinitionValidator.validationError(normalized)?.let {
            return GoalActionResult.Failure(it)
        }
        val goalId = normalized.goalId
            ?: return GoalActionResult.Failure("That goal is no longer available.")
        return goalAction {
            validateLinks(normalized.userId, normalized.linkedHabitIds)
            val current = fetchGoal(normalized.userId, goalId)
                ?: return@goalAction GoalActionResult.Failure("That goal is no longer available.")
            val progress = when {
                current.milestones.isNotEmpty() -> current.progressPercent
                normalized.targetValue != null -> GoalProgress.fromNumeric(
                    normalized.currentValue ?: 0.0,
                    normalized.targetValue,
                )
                else -> 0
            }
            val updatedDto = requireClient().from("goals")
                .update(normalized.copy(linkedHabitIds = normalized.linkedHabitIds ?: current.linkedHabitIds).toUpdateDto(progress)) {
                    select(GOAL_COLUMNS)
                    filter {
                        eq("id", goalId)
                        eq("user_id", normalized.userId)
                    }
                }
                .decodeSingle<GoalDto>()
            val goal = updatedDto.toModel(current.milestones)
            cache.replaceGoal(
                normalized.userId,
                goal.toEntity(),
                goal.milestones.map { it.toEntity(normalized.userId) },
            )
            GoalActionResult.Success(goal)
        }
    }

    override suspend fun setMilestoneDone(
        userId: String,
        goalId: String,
        milestoneId: String,
        done: Boolean,
    ): GoalActionResult = goalAction {
        requireClient().from("goal_milestones").update(
            MilestoneStatusDto(done, if (done) Instant.now().toString() else null),
        ) {
            filter {
                eq("id", milestoneId)
                eq("goal_id", goalId)
            }
        }
        val milestoneDtos = fetchMilestoneDtos(goalId)
        val progress = GoalProgress.fromMilestones(milestoneDtos.map(GoalMilestoneDto::done))
        requireClient().from("goals").update(
            GoalProgressDto(progress, Instant.now().toString()),
        ) {
            filter {
                eq("id", goalId)
                eq("user_id", userId)
            }
        }
        val goal = fetchGoal(userId, goalId)
            ?: return@goalAction GoalActionResult.Failure("That goal is no longer available.")
        cache.replaceGoal(
            userId,
            goal.toEntity(),
            goal.milestones.map { it.toEntity(userId) },
        )
        GoalActionResult.Success(goal)
    }

    override suspend fun setCurrentValue(
        userId: String,
        goalId: String,
        value: Double,
    ): GoalActionResult = goalAction {
        if (!value.isFinite() || value < 0.0) {
            return@goalAction GoalActionResult.Failure("Enter progress of zero or more.")
        }
        val current = fetchGoal(userId, goalId)
            ?: return@goalAction GoalActionResult.Failure("That goal is no longer available.")
        val target = current.targetValue
            ?: return@goalAction GoalActionResult.Failure("Add a numeric target before updating progress.")
        val progress = if (current.milestones.isNotEmpty()) {
            current.progressPercent
        } else {
            GoalProgress.fromNumeric(value, target)
        }
        val updatedDto = requireClient().from("goals").update(
            NumericProgressDto(value, progress, Instant.now().toString()),
        ) {
            select(GOAL_COLUMNS)
            filter {
                eq("id", goalId)
                eq("user_id", userId)
            }
        }.decodeSingle<GoalDto>()
        val goal = updatedDto.toModel(current.milestones)
        cache.replaceGoal(
            userId,
            goal.toEntity(),
            goal.milestones.map { it.toEntity(userId) },
        )
        GoalActionResult.Success(goal)
    }

    override suspend fun delete(userId: String, goalId: String): GoalActionResult = goalAction {
        requireClient().from("goals").delete {
            filter {
                eq("id", goalId)
                eq("user_id", userId)
            }
        }
        cache.deleteGoal(userId, goalId)
        GoalActionResult.Success()
    }

    override suspend fun clear(userId: String) = cache.clearForUser(userId)

    private suspend fun finishCreate(intent: SaveGoalIntent, goalDto: GoalDto): GoalActionResult {
        val milestoneDtos = intent.toMilestoneInsertDtos(goalDto.id)
        if (milestoneDtos.isNotEmpty()) {
            requireClient().from("goal_milestones").upsert(milestoneDtos) {
                onConflict = "id"
            }
        }
        val goal = fetchGoal(intent.userId, goalDto.id)
            ?: return GoalActionResult.Failure("The goal was saved but could not be reloaded.")
        cache.replaceGoal(
            intent.userId,
            goal.toEntity(),
            goal.milestones.map { it.toEntity(intent.userId) },
        )
        return GoalActionResult.Success(goal)
    }

    private suspend fun resolveCreateRetry(intent: SaveGoalIntent): GoalActionResult = try {
        val existing = findGoalDto(intent.userId, intent.requestId)
            ?: return GoalActionResult.Failure("Goal not saved. Check your connection and retry safely.")
        val existingMilestones = fetchMilestoneDtos(existing.id)
        if (!existing.matches(intent) || !existingMilestones.compatibleWith(intent)) {
            return GoalActionResult.Failure(
                "This save request was already used for different goal details.",
            )
        }
        finishCreate(intent, existing)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        GoalActionResult.Failure("Goal not saved. Check your connection and retry safely.")
    }

    private suspend fun fetchGoal(userId: String, goalId: String): Goal? {
        val goalDto = findGoalDto(userId, goalId) ?: return null
        return goalDto.toModel(fetchMilestoneDtos(goalId).map(GoalMilestoneDto::toModel))
    }

    private suspend fun findGoalDto(userId: String, goalId: String): GoalDto? =
        requireClient().from("goals").select(GOAL_COLUMNS) {
            filter {
                eq("id", goalId)
                eq("user_id", userId)
            }
            limit(1)
        }.decodeList<GoalDto>().firstOrNull()

    private suspend fun fetchMilestoneDtos(goalId: String): List<GoalMilestoneDto> =
        requireClient().from("goal_milestones").select(MILESTONE_COLUMNS) {
            filter { eq("goal_id", goalId) }
        }.decodeList()

    private suspend fun loadCached(userId: String): GoalLoadResult = try {
        if (cache.getMetadata(userId) == null) {
            GoalLoadResult.Failure("Unable to load goals. Check your connection and try again.")
        } else {
            val milestones = cache.getMilestones(userId).map(CachedGoalMilestoneEntity::toModel)
            val byGoal = milestones.groupBy(GoalMilestone::goalId)
            val goals = cache.getGoals(userId).map { it.toModel(byGoal[it.id].orEmpty()) }
            GoalLoadResult.Success(goals, isOffline = true)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        GoalLoadResult.Failure("Unable to load goals. Check your connection and try again.")
    }

    private suspend fun goalAction(block: suspend () -> GoalActionResult): GoalActionResult = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        GoalActionResult.Failure("The goal was not changed. Check your connection and try again.")
    }

    private suspend fun validateLinks(userId: String, ids: List<String>?) {
        if (ids.isNullOrEmpty()) return
        val owned = requireClient().from("habits").select(Columns.list("id")) { filter { eq("user_id", userId); isIn("id", ids) } }.decodeList<kotlinx.serialization.json.JsonObject>()
        require(owned.map { com.ahmad.nafs.core.records.RecordRules.text(it, "id") }.toSet() == ids.toSet())
    }

    private fun requireClient() = checkNotNull(clientProvider.client) { "Supabase is not configured." }

    private companion object {
        val GOAL_COLUMNS = Columns.list(
            "id", "user_id", "title", "emoji", "description", "goal_type", "category",
            "start_date", "deadline", "progress_pct", "target_value", "current_value", "unit",
            "linked_habit_ids", "created_at", "updated_at",
        )
        val MILESTONE_COLUMNS = Columns.list(
            "id", "goal_id", "title", "done", "done_at", "target_date", "created_at",
        )
    }
}

@Serializable
private data class GoalDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val title: String,
    val emoji: String = "⭐",
    val description: String? = null,
    @SerialName("goal_type") val goalType: String = "monthly",
    val category: String? = null,
    @SerialName("start_date") val startDate: String,
    val deadline: String? = null,
    @SerialName("progress_pct") val progressPercent: Int = 0,
    @SerialName("target_value") val targetValue: Double? = null,
    @SerialName("current_value") val currentValue: Double? = null,
    val unit: String? = null,
    @SerialName("linked_habit_ids") val linkedHabitIds: List<String> = emptyList(),
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
private data class GoalMilestoneDto(
    val id: String,
    @SerialName("goal_id") val goalId: String,
    val title: String,
    val done: Boolean = false,
    @SerialName("done_at") val doneAt: String? = null,
    @SerialName("target_date") val targetDate: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
private data class GoalInsertDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val title: String,
    val emoji: String,
    val description: String?,
    @SerialName("goal_type") val goalType: String,
    val category: String?,
    @SerialName("start_date") val startDate: String,
    val deadline: String?,
    @SerialName("progress_pct") val progressPercent: Int,
    @SerialName("target_value") val targetValue: Double?,
    @SerialName("current_value") val currentValue: Double?,
    val unit: String?,
    @SerialName("linked_habit_ids") val linkedHabitIds: List<String>,
)

@Serializable
private data class GoalUpdateDto(
    val title: String,
    val emoji: String,
    val description: String?,
    @SerialName("goal_type") val goalType: String,
    val category: String?,
    val deadline: String?,
    @SerialName("progress_pct") val progressPercent: Int,
    @SerialName("target_value") val targetValue: Double?,
    @SerialName("current_value") val currentValue: Double?,
    val unit: String?,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("linked_habit_ids") val linkedHabitIds: List<String>,
)

@Serializable
private data class GoalMilestoneInsertDto(
    val id: String,
    @SerialName("goal_id") val goalId: String,
    val title: String,
    val done: Boolean,
)

@Serializable
private data class MilestoneStatusDto(
    val done: Boolean,
    @SerialName("done_at") val doneAt: String?,
)

@Serializable
private data class GoalProgressDto(
    @SerialName("progress_pct") val progressPercent: Int,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
private data class NumericProgressDto(
    @SerialName("current_value") val currentValue: Double,
    @SerialName("progress_pct") val progressPercent: Int,
    @SerialName("updated_at") val updatedAt: String,
)

private fun assembleGoals(
    goals: List<GoalDto>,
    milestones: List<GoalMilestoneDto>,
): List<Goal> {
    val byGoal = milestones.map(GoalMilestoneDto::toModel).groupBy(GoalMilestone::goalId)
    return goals.map { it.toModel(byGoal[it.id].orEmpty()) }
}

private fun GoalDto.toModel(milestones: List<GoalMilestone>) = Goal(
    id = id,
    userId = userId,
    title = title,
    emoji = emoji,
    description = description,
    type = goalType(goalType),
    category = category?.let(::goalCategory),
    startDate = LocalDate.parse(startDate),
    deadline = deadline?.let(LocalDate::parse),
    storedProgressPercent = progressPercent.coerceIn(0, 100),
    targetValue = targetValue,
    currentValue = currentValue,
    unit = unit,
    linkedHabitIds = linkedHabitIds,
    milestones = milestones.sortedBy(GoalMilestone::createdAt),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun GoalMilestoneDto.toModel() = GoalMilestone(
    id,
    goalId,
    title,
    done,
    doneAt,
    targetDate?.let(LocalDate::parse),
    createdAt,
)

private fun SaveGoalIntent.toInsertDto() = GoalInsertDto(
    id = requestId,
    userId = userId,
    title = title,
    emoji = emoji,
    description = description,
    goalType = type.name.lowercase(),
    category = category?.name?.lowercase(),
    startDate = startDate.toString(),
    deadline = deadline?.toString(),
    progressPercent = if (milestoneTitles.isEmpty() && targetValue != null) {
        GoalProgress.fromNumeric(currentValue ?: 0.0, targetValue)
    } else {
        0
    },
    targetValue = targetValue,
    currentValue = currentValue,
    unit = unit,
    linkedHabitIds = linkedHabitIds.orEmpty(),
)

private fun SaveGoalIntent.toUpdateDto(progress: Int) = GoalUpdateDto(
    title,
    emoji,
    description,
    type.name.lowercase(),
    category?.name?.lowercase(),
    deadline?.toString(),
    progress,
    targetValue,
    currentValue,
    unit,
    Instant.now().toString(),
    linkedHabitIds.orEmpty(),
)

private fun SaveGoalIntent.toMilestoneInsertDtos(goalId: String): List<GoalMilestoneInsertDto> =
    milestoneTitles.mapIndexed { index, title ->
        GoalMilestoneInsertDto(
            id = milestoneId(requestId, index),
            goalId = goalId,
            title = title,
            done = false,
        )
    }

private fun GoalDto.matches(intent: SaveGoalIntent): Boolean =
    userId == intent.userId &&
        title == intent.title &&
        emoji == intent.emoji &&
        description == intent.description &&
        goalType(goalType) == intent.type &&
        category?.let(::goalCategory) == intent.category &&
        startDate == intent.startDate.toString() &&
        deadline == intent.deadline?.toString() &&
        targetValue == intent.targetValue &&
        currentValue == intent.currentValue &&
        unit == intent.unit && linkedHabitIds.toSet() == intent.linkedHabitIds.orEmpty().toSet()

private fun List<GoalMilestoneDto>.compatibleWith(intent: SaveGoalIntent): Boolean {
    val intended = intent.milestoneTitles.mapIndexed { index, title ->
        milestoneId(intent.requestId, index) to title
    }.toMap()
    return all { intended[it.id] == it.title }
}

private fun SaveGoalIntent.normalized(): SaveGoalIntent = copy(
    linkedHabitIds = linkedHabitIds?.distinct(),
    title = title.trim(),
    emoji = emoji.trim().ifEmpty { "⭐" },
    description = description.normalized(),
    unit = if (targetValue == null) null else unit.normalized(),
    currentValue = if (targetValue == null) null else currentValue ?: 0.0,
    milestoneTitles = milestoneTitles.map(String::trim).filter(String::isNotEmpty).distinct(),
)

private fun milestoneId(requestId: String, index: Int): String = UUID.nameUUIDFromBytes(
    "$requestId:milestone:$index".toByteArray(StandardCharsets.UTF_8),
).toString()

private fun Goal.toEntity() = CachedGoalEntity(
    id,
    userId,
    title,
    emoji,
    description,
    type.name,
    category?.name,
    startDate.toString(),
    deadline?.toString(),
    storedProgressPercent,
    targetValue,
    currentValue,
    unit,
    linkedHabitIds.joinToString(","),
    createdAt,
    updatedAt,
)

private fun GoalMilestone.toEntity(userId: String) = CachedGoalMilestoneEntity(
    id,
    userId,
    goalId,
    title,
    done,
    doneAt,
    targetDate?.toString(),
    createdAt,
)

private fun CachedGoalEntity.toModel(milestones: List<GoalMilestone>) = Goal(
    id,
    userId,
    title,
    emoji,
    description,
    goalType(type),
    category?.let(::goalCategory),
    LocalDate.parse(startDate),
    deadline?.let(LocalDate::parse),
    storedProgressPercent,
    targetValue,
    currentValue,
    unit,
    linkedHabitIds.split(',').filter(String::isNotEmpty),
    milestones,
    createdAt,
    updatedAt,
)

private fun CachedGoalMilestoneEntity.toModel() = GoalMilestone(
    id,
    goalId,
    title,
    done,
    doneAt,
    targetDate?.let(LocalDate::parse),
    createdAt,
)

private fun goalType(value: String): GoalType =
    runCatching { GoalType.valueOf(value.uppercase()) }.getOrDefault(GoalType.MONTHLY)

private fun goalCategory(value: String): GoalCategory? =
    runCatching { GoalCategory.valueOf(value.uppercase()) }.getOrNull()

private fun String?.normalized(): String? = this?.trim()?.takeIf(String::isNotEmpty)
