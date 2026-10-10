package com.ahmad.nafs.core.habits

import com.ahmad.nafs.core.database.CachedHabitDao
import com.ahmad.nafs.core.database.CachedHabitEntity
import com.ahmad.nafs.core.database.CachedHabitLogEntity
import com.ahmad.nafs.core.network.SupabaseClientProvider
import com.ahmad.nafs.domain.habits.HabitResult
import com.ahmad.nafs.domain.habits.HabitStreak
import com.ahmad.nafs.domain.habits.HabitType
import com.ahmad.nafs.domain.habits.ScheduleKind
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

@Singleton
class SupabaseHabitRepository @Inject constructor(
    private val clientProvider: SupabaseClientProvider,
    private val cache: CachedHabitDao,
) : HabitRepository {
    /** One streak recount at a time, so an older recount can never land after a newer one. */
    private val streakLock = Mutex()

    override suspend fun load(userId: String, today: LocalDate): HabitLoadResult {
        val startDate = today.minusDays(LOOKBACK_DAYS)
        return try {
            val habits = requireClient().from("habits").select(HABIT_COLUMNS) {
                filter { eq("user_id", userId); eq("is_active", true) }
            }.decodeList<HabitDto>().map(HabitDto::toModel)
            val logs = fetchLogs(userId, startDate)
            cache.replaceForUser(
                userId,
                habits.map(Habit::toEntity),
                logs.map(HabitLog::toEntity),
                System.currentTimeMillis(),
            )
            HabitLoadResult.Success(habits, logs, isOffline = false)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            loadCached(userId, startDate)
        }
    }

    override suspend fun create(intent: SaveHabitIntent): HabitDefinitionActionResult {
        val normalized = intent.normalized()
        HabitDefinitionValidator.validationError(normalized)?.let {
            return HabitDefinitionActionResult.Failure(it)
        }
        return try {
            val nextOrder =
                (cache.getHabits(normalized.userId).maxOfOrNull(CachedHabitEntity::sortOrder) ?: 0) + 1
            val habit = requireClient().from("habits")
                .insert(normalized.toInsertDto(nextOrder)) { select(HABIT_COLUMNS) }
                .decodeSingle<HabitDto>()
                .toModel()
            cache.upsertHabit(habit.toEntity())
            HabitDefinitionActionResult.Success(habit)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            resolveCreateRetry(normalized)
        }
    }

    override suspend fun update(intent: SaveHabitIntent): HabitDefinitionActionResult {
        val normalized = intent.normalized()
        HabitDefinitionValidator.validationError(normalized)?.let {
            return HabitDefinitionActionResult.Failure(it)
        }
        val habitId = normalized.habitId
            ?: return HabitDefinitionActionResult.Failure("That habit is no longer available.")
        return habitDefinitionAction {
            val habit = requireClient().from("habits")
                .update(normalized.toUpdateDto()) {
                    select(HABIT_COLUMNS)
                    filter {
                        eq("id", habitId)
                        eq("user_id", normalized.userId)
                    }
                }
                .decodeSingle<HabitDto>()
                .toModel()
            cache.upsertHabit(habit.toEntity())
            HabitDefinitionActionResult.Success(habit)
        }
    }

    override suspend fun setPaused(
        userId: String,
        habitId: String,
        paused: Boolean,
    ): HabitDefinitionActionResult = habitDefinitionAction {
        val habit = requireClient().from("habits")
            .update(PauseHabitDto(paused)) {
                select(HABIT_COLUMNS)
                filter {
                    eq("id", habitId)
                    eq("user_id", userId)
                    eq("is_active", true)
                }
            }
            .decodeSingle<HabitDto>()
            .toModel()
        cache.upsertHabit(habit.toEntity())
        HabitDefinitionActionResult.Success(habit)
    }

    override suspend fun archive(userId: String, habitId: String): HabitDefinitionActionResult =
        habitDefinitionAction {
            val habit = setActive(userId, habitId, active = false)
            // The server has it; a local cache hiccup must not report the archive as failed.
            bestEffort { cache.deleteHabit(userId, habitId) }
            HabitDefinitionActionResult.Success(habit)
        }

    override suspend fun restore(userId: String, habitId: String): HabitDefinitionActionResult =
        habitDefinitionAction {
            val habit = setActive(userId, habitId, active = true)
            bestEffort { cache.upsertHabit(habit.toEntity()) }
            HabitDefinitionActionResult.Success(habit)
        }

    override suspend fun log(intent: LogHabitIntent): HabitActionResult = try {
        val log = intent.toLog()
        val subjectPosition = if (intent.habit.type == HabitType.SUBJECT) {
            val parameters = buildJsonObject {
                put("p_habit_id", JsonPrimitive(intent.habit.id))
                put("p_date", JsonPrimitive(intent.date.toString()))
                put("p_new_delta", JsonPrimitive(intent.value.toInt().coerceAtLeast(0)))
                put("p_duration_mins", JsonPrimitive(intent.durationMinutes.coerceAtLeast(0)))
                put("p_notes", intent.notes.normalized()?.let(::JsonPrimitive) ?: JsonNull)
            }
            val result = requireClient().postgrest.rpc("log_subject_habit", parameters)
                .decodeList<SubjectLogResult>().firstOrNull()
                ?: return HabitActionResult.Failure("Reading progress was not returned by the server.")
            result.subjectPosition
        } else {
            requireClient().from("habit_logs").upsert(log.toDto()) {
                onConflict = "user_id,habit_id,date"
            }
            null
        }
        cache.upsertLog(log.toEntity())
        subjectPosition?.let {
            cache.upsertHabit(intent.habit.copy(subjectPosition = it).toEntity())
        }
        HabitActionResult.Success(log, subjectPosition)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        val message = if (
            intent.habit.type == HabitType.SUBJECT &&
            error.message.orEmpty().contains("log_subject_habit", ignoreCase = true)
        ) {
            "Reading progress needs the atomic database function before it can be saved safely."
        } else {
            "Habit progress was not saved. Check your connection and try again."
        }
        HabitActionResult.Failure(message)
    }

    /**
     * The web log route's recount (`api/habits/log`): current streak from the server's last 400 days, longest kept
     * at its stored high so runs older than the lookback still count. Reads the row and logs fresh, so a log made
     * on another device is included.
     */
    override suspend fun refreshStreak(
        userId: String,
        habitId: String,
        today: LocalDate,
    ): HabitStreakUpdate? = streakLock.withLock {
        try {
            val stored = requireClient().from("habits").select(HABIT_COLUMNS) {
                filter {
                    eq("id", habitId)
                    eq("user_id", userId)
                    eq("is_active", true)
                }
                limit(1)
            }.decodeList<HabitDto>().firstOrNull()?.toModel() ?: return@withLock null
            // Paused habits neither build nor break a streak, so a backfilled entry leaves the stored values alone.
            if (stored.isPaused) return@withLock HabitStreakUpdate(stored.currentStreak, stored.longestStreak)
            val results = fetchLogs(userId, today.minusDays(LOOKBACK_DAYS), habitId).map { log ->
                HabitResult(log.date, log.completed, log.value, log.durationMinutes)
            }
            val streak = HabitStreak.summary(stored.schedule(), results, today, storedBest = stored.longestStreak)
            val habit = if (streak.current == stored.currentStreak && streak.best == stored.longestStreak) {
                stored
            } else {
                requireClient().from("habits")
                    .update(StreakDto(streak.current, streak.best)) {
                        select(HABIT_COLUMNS)
                        filter {
                            eq("id", habitId)
                            eq("user_id", userId)
                            eq("is_active", true)
                        }
                    }
                    .decodeSingle<HabitDto>()
                    .toModel()
            }
            bestEffort { cache.upsertHabit(habit.toEntity()) }
            HabitStreakUpdate(habit.currentStreak, habit.longestStreak)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun clear(userId: String) = cache.clearForUser(userId)

    /**
     * Every log since [startDate], a page at a time: the API returns at most 1,000 rows per request, and a few
     * habits logged daily pass that well within the lookback. A stable order keeps pages from overlapping.
     */
    private suspend fun fetchLogs(userId: String, startDate: LocalDate, habitId: String? = null): List<HabitLog> {
        val logs = mutableListOf<HabitLog>()
        for (page in 0 until MAX_LOG_PAGES) {
            val from = page * LOG_PAGE_SIZE
            val rows = requireClient().from("habit_logs").select(LOG_COLUMNS) {
                filter {
                    eq("user_id", userId)
                    if (habitId != null) eq("habit_id", habitId)
                    gte("date", startDate.toString())
                }
                order("date", Order.DESCENDING)
                order("habit_id", Order.ASCENDING)
                range(from, from + LOG_PAGE_SIZE - 1)
            }.decodeList<HabitLogDto>()
            rows.mapTo(logs, HabitLogDto::toModel)
            if (rows.size < LOG_PAGE_SIZE) break
        }
        return logs
    }

    private suspend fun setActive(userId: String, habitId: String, active: Boolean): Habit =
        requireClient().from("habits")
            .update(ActiveHabitDto(active)) {
                select(HABIT_COLUMNS)
                filter {
                    eq("id", habitId)
                    eq("user_id", userId)
                }
            }
            .decodeSingle<HabitDto>()
            .toModel()

    private suspend fun loadCached(userId: String, startDate: LocalDate): HabitLoadResult = try {
        if (cache.getMetadata(userId) == null) {
            HabitLoadResult.Failure("Unable to load habits. Check your connection and try again.")
        } else {
            HabitLoadResult.Success(
                cache.getHabits(userId).map(CachedHabitEntity::toModel),
                cache.getLogsSince(userId, startDate.toString()).map(CachedHabitLogEntity::toModel),
                isOffline = true,
            )
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        HabitLoadResult.Failure("Unable to load habits. Check your connection and try again.")
    }

    private suspend fun resolveCreateRetry(intent: SaveHabitIntent): HabitDefinitionActionResult = try {
        val existing = findHabit(intent.userId, intent.requestId)
        when {
            existing == null -> HabitDefinitionActionResult.Failure(
                "Habit not saved. Check your connection and retry safely.",
            )
            HabitDefinitionReplayRule.isSameIntent(existing.toModel().toPayload(), intent.toPayload()) -> {
                val habit = existing.toModel()
                cache.upsertHabit(habit.toEntity())
                HabitDefinitionActionResult.Success(habit)
            }
            else -> HabitDefinitionActionResult.Failure(
                "This save request was already used for different habit details.",
            )
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        HabitDefinitionActionResult.Failure("Habit not saved. Check your connection and retry safely.")
    }

    private suspend fun findHabit(userId: String, habitId: String): HabitDto? =
        requireClient().from("habits").select(HABIT_COLUMNS) {
            filter {
                eq("id", habitId)
                eq("user_id", userId)
            }
            limit(1)
        }.decodeList<HabitDto>().firstOrNull()

    private suspend fun habitDefinitionAction(
        block: suspend () -> HabitDefinitionActionResult,
    ): HabitDefinitionActionResult = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        HabitDefinitionActionResult.Failure(
            "The habit was not changed. Check your connection and try again.",
        )
    }

    /** For local cache writes after the server already accepted a change: the next online load rewrites the cache. */
    private suspend fun bestEffort(block: suspend () -> Unit) {
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // Kept quiet on purpose; see above.
        }
    }

    private fun requireClient() = checkNotNull(clientProvider.client) { "Supabase is not configured." }

    private companion object {
        const val LOOKBACK_DAYS = 400L
        const val LOG_PAGE_SIZE = 1_000L
        const val MAX_LOG_PAGES = 20
        val HABIT_COLUMNS = Columns.list(
            "id", "user_id", "name", "emoji", "type", "target_value", "unit",
            "time_target_mins", "subject_name", "subject_total", "subject_position",
            "subject_unit", "schedule_kind", "schedule_days", "weekly_target",
            "is_paused", "sort_order", "current_streak", "longest_streak", "reminder_time",
        )
        val LOG_COLUMNS = Columns.list(
            "user_id", "habit_id", "date", "completed", "value", "duration_mins", "notes",
        )
    }
}

@Serializable
private data class HabitDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val emoji: String = "✓",
    val type: String,
    @SerialName("target_value") val targetValue: Double = 1.0,
    val unit: String = "times",
    @SerialName("time_target_mins") val timeTargetMinutes: Int = 0,
    @SerialName("subject_name") val subjectName: String? = null,
    @SerialName("subject_total") val subjectTotal: Int? = null,
    @SerialName("subject_position") val subjectPosition: Int = 0,
    @SerialName("subject_unit") val subjectUnit: String? = null,
    @SerialName("schedule_kind") val scheduleKind: String = "daily",
    @SerialName("schedule_days") val scheduleDays: List<String>? = null,
    @SerialName("weekly_target") val weeklyTarget: Int? = null,
    @SerialName("is_paused") val isPaused: Boolean = false,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("current_streak") val currentStreak: Int = 0,
    @SerialName("longest_streak") val longestStreak: Int = 0,
    @SerialName("reminder_time") val reminderTime: String? = null,
)

// Insert and update rows declare every field without a default, so a cleared reminder is sent as an explicit null.
@Serializable
private data class HabitInsertDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val emoji: String,
    val type: String,
    @SerialName("target_value") val targetValue: Double,
    val unit: String,
    @SerialName("time_target_mins") val timeTargetMinutes: Int,
    @SerialName("subject_name") val subjectName: String?,
    @SerialName("subject_total") val subjectTotal: Int?,
    @SerialName("subject_position") val subjectPosition: Int,
    @SerialName("subject_unit") val subjectUnit: String?,
    @SerialName("schedule_kind") val scheduleKind: String,
    @SerialName("schedule_days") val scheduleDays: List<String>?,
    @SerialName("weekly_target") val weeklyTarget: Int?,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("is_paused") val isPaused: Boolean,
    @SerialName("sort_order") val sortOrder: Int,
    val category: String,
    @SerialName("score_weight") val scoreWeight: Int,
    @SerialName("reminder_time") val reminderTime: String?,
)

@Serializable
private data class HabitUpdateDto(
    val name: String,
    val emoji: String,
    val type: String,
    @SerialName("target_value") val targetValue: Double,
    val unit: String,
    @SerialName("time_target_mins") val timeTargetMinutes: Int,
    @SerialName("subject_name") val subjectName: String?,
    @SerialName("subject_total") val subjectTotal: Int?,
    @SerialName("subject_position") val subjectPosition: Int,
    @SerialName("subject_unit") val subjectUnit: String?,
    @SerialName("schedule_kind") val scheduleKind: String,
    @SerialName("schedule_days") val scheduleDays: List<String>?,
    @SerialName("weekly_target") val weeklyTarget: Int?,
    @SerialName("reminder_time") val reminderTime: String?,
)

@Serializable
private data class PauseHabitDto(
    @SerialName("is_paused") val isPaused: Boolean,
)

@Serializable
private data class ActiveHabitDto(
    @SerialName("is_active") val isActive: Boolean,
)

@Serializable
private data class StreakDto(
    @SerialName("current_streak") val currentStreak: Int,
    @SerialName("longest_streak") val longestStreak: Int,
)

@Serializable
private data class HabitLogDto(
    @SerialName("user_id") val userId: String,
    @SerialName("habit_id") val habitId: String,
    val date: String,
    val completed: Boolean,
    val value: Double = 0.0,
    @SerialName("duration_mins") val durationMinutes: Int = 0,
    val notes: String? = null,
)

@Serializable
private data class SubjectLogResult(
    @SerialName("subject_position") val subjectPosition: Int,
)

private fun HabitDto.toModel() = Habit(
    id, userId, name, emoji, habitType(type), targetValue, unit,
    timeTargetMinutes, subjectName, subjectTotal, subjectPosition, subjectUnit,
    scheduleKind(scheduleKind), scheduleDays.orEmpty().mapNotNull(::dayOfWeek).toSet(),
    weeklyTarget, isPaused, sortOrder, currentStreak, longestStreak,
    reminderTime = HabitReminderTime.parse(reminderTime),
)

private fun HabitLogDto.toModel() = HabitLog(
    userId, habitId, LocalDate.parse(date), completed, value, durationMinutes, notes,
)

private fun LogHabitIntent.toLog(): HabitLog {
    val safeValue = value.coerceAtLeast(0.0)
    val safeDuration = durationMinutes.coerceAtLeast(0)
    val isComplete = when (habit.type) {
        HabitType.SIMPLE -> completed
        HabitType.COUNTER -> safeValue >= habit.targetValue
        HabitType.DURATION -> safeDuration >= habit.timeTargetMinutes
        HabitType.SUBJECT -> safeValue > 0.0
    }
    return HabitLog(userId, habit.id, date, isComplete, safeValue, safeDuration, notes.normalized())
}

private fun HabitLog.toDto() = HabitLogDto(
    userId, habitId, date.toString(), completed, value, durationMinutes, notes,
)

private fun SaveHabitIntent.toInsertDto(sortOrder: Int) = HabitInsertDto(
    id = requestId,
    userId = userId,
    name = name,
    emoji = emoji,
    type = type.name.lowercase(),
    targetValue = targetValue,
    unit = unit,
    timeTargetMinutes = timeTargetMinutes,
    subjectName = subjectName,
    subjectTotal = subjectTotal,
    subjectPosition = subjectPosition,
    subjectUnit = subjectUnit,
    scheduleKind = scheduleKind.name.lowercase(),
    scheduleDays = scheduleDays.takeIf(Set<DayOfWeek>::isNotEmpty)
        ?.sortedBy(DayOfWeek::getValue)
        ?.map(DayOfWeek::toWireValue),
    weeklyTarget = weeklyTarget,
    isActive = true,
    isPaused = false,
    sortOrder = sortOrder,
    category = "custom",
    scoreWeight = 1,
    reminderTime = reminderTime?.let(HabitReminderTime::format),
)

private fun SaveHabitIntent.toUpdateDto() = HabitUpdateDto(
    name = name,
    emoji = emoji,
    type = type.name.lowercase(),
    targetValue = targetValue,
    unit = unit,
    timeTargetMinutes = timeTargetMinutes,
    subjectName = subjectName,
    subjectTotal = subjectTotal,
    subjectPosition = subjectPosition,
    subjectUnit = subjectUnit,
    scheduleKind = scheduleKind.name.lowercase(),
    scheduleDays = scheduleDays.takeIf(Set<DayOfWeek>::isNotEmpty)
        ?.sortedBy(DayOfWeek::getValue)
        ?.map(DayOfWeek::toWireValue),
    weeklyTarget = weeklyTarget,
    reminderTime = reminderTime?.let(HabitReminderTime::format),
)

private fun Habit.toEntity() = CachedHabitEntity(
    id, userId, name, emoji, type.name, targetValue, unit, timeTargetMinutes, subjectName,
    subjectTotal, subjectPosition, subjectUnit, scheduleKind.name,
    scheduleDays.joinToString(",") { it.name }, weeklyTarget, isPaused, sortOrder,
    currentStreak, longestStreak, reminderTime?.let(HabitReminderTime::format),
)

private fun CachedHabitEntity.toModel() = Habit(
    id, userId, name, emoji, habitType(type), targetValue, unit, timeTargetMinutes,
    subjectName, subjectTotal, subjectPosition, subjectUnit, scheduleKind(scheduleKind),
    scheduleDays.orEmpty().split(',').mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }.toSet(),
    weeklyTarget, isPaused, sortOrder, currentStreak, longestStreak,
    reminderTime = HabitReminderTime.parse(reminderTime),
)

private fun HabitLog.toEntity() = CachedHabitLogEntity(
    userId, habitId, date.toString(), completed, value, durationMinutes, notes,
)

private fun CachedHabitLogEntity.toModel() = HabitLog(
    userId, habitId, LocalDate.parse(date), completed, value, durationMinutes, notes,
)

private fun dayOfWeek(code: String): DayOfWeek? = when (code.lowercase()) {
    "mon" -> DayOfWeek.MONDAY; "tue" -> DayOfWeek.TUESDAY; "wed" -> DayOfWeek.WEDNESDAY
    "thu" -> DayOfWeek.THURSDAY; "fri" -> DayOfWeek.FRIDAY; "sat" -> DayOfWeek.SATURDAY
    "sun" -> DayOfWeek.SUNDAY; else -> null
}

private fun DayOfWeek.toWireValue(): String = name.take(3).lowercase()

private fun habitType(value: String): HabitType = when (value.lowercase()) {
    "boolean" -> HabitType.SIMPLE
    "count" -> HabitType.COUNTER
    else -> runCatching { HabitType.valueOf(value.uppercase()) }.getOrDefault(HabitType.SIMPLE)
}

private fun scheduleKind(value: String): ScheduleKind =
    runCatching { ScheduleKind.valueOf(value.uppercase()) }.getOrDefault(ScheduleKind.DAILY)

private fun SaveHabitIntent.normalized(): SaveHabitIntent = copy(
    name = name.trim(),
    emoji = emoji.trim().ifEmpty { "⭐" },
    targetValue = if (type == HabitType.COUNTER) targetValue else 1.0,
    unit = if (type == HabitType.COUNTER) unit.trim() else "",
    timeTargetMinutes = if (type == HabitType.DURATION) timeTargetMinutes else 0,
    subjectName = if (type == HabitType.SUBJECT) subjectName.normalized() else null,
    subjectTotal = if (type == HabitType.SUBJECT) subjectTotal else null,
    subjectPosition = if (type == HabitType.SUBJECT) subjectPosition else 0,
    subjectUnit = if (type == HabitType.SUBJECT) subjectUnit.normalized() else null,
    scheduleDays = if (scheduleKind == ScheduleKind.WEEKDAYS) scheduleDays else emptySet(),
    weeklyTarget = if (scheduleKind == ScheduleKind.PER_WEEK) weeklyTarget else null,
    // The column keeps minutes only; matching that here keeps a replayed create comparable.
    reminderTime = reminderTime?.truncatedTo(ChronoUnit.MINUTES),
)

private fun Habit.toPayload() = HabitDefinitionPayload(
    userId, name, emoji, type, targetValue, unit, timeTargetMinutes, subjectName,
    subjectTotal, subjectPosition, subjectUnit, scheduleKind, scheduleDays, weeklyTarget, reminderTime,
)

private fun SaveHabitIntent.toPayload() = HabitDefinitionPayload(
    userId, name, emoji, type, targetValue, unit, timeTargetMinutes, subjectName,
    subjectTotal, subjectPosition, subjectUnit, scheduleKind, scheduleDays, weeklyTarget, reminderTime,
)

private fun String?.normalized(): String? = this?.trim()?.takeIf(String::isNotEmpty)
