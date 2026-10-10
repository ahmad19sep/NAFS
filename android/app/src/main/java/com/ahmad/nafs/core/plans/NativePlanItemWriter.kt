package com.ahmad.nafs.core.plans

import com.ahmad.nafs.core.challenges.ChallengeActionResult
import com.ahmad.nafs.core.challenges.ChallengeRepository
import com.ahmad.nafs.core.challenges.SaveChallengeIntent
import com.ahmad.nafs.core.habits.HabitDefinitionActionResult
import com.ahmad.nafs.core.habits.HabitRepository
import com.ahmad.nafs.core.habits.SaveHabitIntent
import com.ahmad.nafs.core.network.ApiSessionTokens
import com.ahmad.nafs.core.network.SupabaseClientProvider
import com.ahmad.nafs.core.reminders.TaskReminderScheduler
import com.ahmad.nafs.core.tasks.CreateTaskIntent
import com.ahmad.nafs.core.tasks.TaskActionResult
import com.ahmad.nafs.core.tasks.TaskPriority
import com.ahmad.nafs.core.tasks.TaskRepository
import com.ahmad.nafs.core.tasks.TaskLoadResult
import com.ahmad.nafs.domain.challenges.ChallengeFrequency
import com.ahmad.nafs.domain.habits.HabitType
import com.ahmad.nafs.domain.habits.ScheduleKind
import com.ahmad.nafs.domain.tasks.TaskPeriodType
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

class NativePlanItemWriter @Inject constructor(private val provider: SupabaseClientProvider,
    private val tasks: TaskRepository, private val habits: HabitRepository, private val challenges: ChallengeRepository,
    private val sessions: ApiSessionTokens, private val reminders: TaskReminderScheduler) : PlanItemWriter {
    override suspend fun checkOnline(userId: String) {
        check(sessions.isCurrentUser(userId))
        check(PlanItemQueries.online(requireNotNull(provider.client), userId) && sessions.isCurrentUser(userId))
    }
    override suspend fun inspect(userId: String, proposal: PlanProposal, step: PlanStepState): PlanItemState {
        check(sessions.isCurrentUser(userId))
        val result = PlanItemQueries.inspect(requireNotNull(provider.client), userId, proposal, step)
        check(sessions.isCurrentUser(userId))
        return result
    }
    override suspend fun create(userId: String, proposal: PlanProposal, step: PlanStepState, timezone: String): Boolean {
        check(sessions.isCurrentUser(userId)); require(RecoveryRules.validStep(proposal))
        val date = LocalDate.parse(step.applyDate)
        val success = when (proposal.kind) {
            "task" -> {
                val result = tasks.create(PlanMappings.task(userId, proposal, step.id, date))
                if (result is TaskActionResult.Success && result.task?.id == step.id && result.task.userId == userId) {
                    try {
                        if (result.task.dueTime != null) {
                            val loaded = tasks.load(userId, date)
                            if (loaded is TaskLoadResult.Success && sessions.isCurrentUser(userId)) reminders.reconcile(userId, loaded.tasks, timezone)
                        }
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { /* The saved task remains authoritative. */ }
                    true
                } else false
            }
            "habit" -> {
                val result = habits.create(PlanMappings.habit(userId, proposal, step.id))
                result is HabitDefinitionActionResult.Success && result.habit.id == step.id && result.habit.userId == userId
            }
            else -> {
                val result = challenges.create(PlanMappings.challenge(userId, proposal, step.id, date))
                result is ChallengeActionResult.Success && result.challenge?.id == step.id && result.challenge.userId == userId
            }
        }
        check(sessions.isCurrentUser(userId))
        return success
    }
}

object PlanMappings {
    fun task(userId: String, p: PlanProposal, id: String, date: LocalDate) = CreateTaskIntent(id, userId, p.title,
        p.task!!.note?.trim()?.takeIf { it.isNotEmpty() }, TaskPeriodType.DAILY, TaskPriority.valueOf(p.task.priority.uppercase()), p.task.dueTime, date)
    fun habit(userId: String, p: PlanProposal, id: String): SaveHabitIntent {
        val h = p.habit!!
        return SaveHabitIntent(requestId = id, userId = userId, name = p.title, emoji = p.emoji,
            type = HabitType.valueOf(h.type.uppercase()), targetValue = if (h.type == "counter") h.targetValue!! else 1.0,
            unit = if (h.type == "counter") h.unit!!.trim() else "", timeTargetMinutes = if (h.type == "duration") h.minutes!! else 0,
            subjectName = null, subjectTotal = null, subjectPosition = 0, subjectUnit = null,
            scheduleKind = if (h.schedule == "weekdays") ScheduleKind.WEEKDAYS else ScheduleKind.DAILY,
            scheduleDays = if (h.schedule == "weekdays") h.days.orEmpty().map { DayOfWeek.of(RecoveryRules.days.indexOf(it) + 1) }.toSet() else emptySet(), weeklyTarget = null)
    }
    fun challenge(userId: String, p: PlanProposal, id: String, date: LocalDate) = SaveChallengeIntent(requestId = id,
        userId = userId, title = p.title, emoji = p.emoji, description = p.reason,
        frequency = ChallengeFrequency.valueOf(p.challenge!!.frequency.uppercase()), durationDays = p.challenge.duration,
        startDate = date, requiresPhoto = p.challenge.requiresPhoto)
}

private fun JsonObject.string(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull
private fun JsonObject.number(key: String) = (get(key) as? JsonPrimitive)?.doubleOrNull
object PlanItemRules {
    fun matches(userId: String, p: PlanProposal, step: PlanStepState, row: JsonObject): Boolean {
        if (row.string("id") != step.id || row.string("user_id") != userId) return false
        return when (p.kind) {
            "task" -> row.string("title") == p.title && row.string("type") == "daily" && row.string("priority") == p.task!!.priority &&
                row.string("period_date") == step.applyDate && row.string("due_time") == p.task.dueTime &&
                row.string("note") == p.task.note?.trim()?.takeIf { it.isNotEmpty() } && row["deleted_at"] == JsonNull
            "habit" -> {
                val h = p.habit!!
                val rowDays = (row["schedule_days"] as? JsonArray)?.map { (it as? JsonPrimitive)?.contentOrNull }?.toSet().orEmpty()
                row.string("name") == p.title && row.string("emoji") == p.emoji && row.string("type") in (if (h.type == "simple") listOf("simple", "boolean") else listOf(h.type)) &&
                    row.number("target_value") == (if (h.type == "counter") h.targetValue else 1.0) && row.string("unit").orEmpty() == (if (h.type == "counter") h.unit!!.trim() else "") &&
                    (row.number("time_target_mins") ?: 0.0) == (if (h.type == "duration") h.minutes!!.toDouble() else 0.0) &&
                    row.string("schedule_kind") == h.schedule && (h.schedule != "weekdays" || rowDays == h.days.orEmpty().toSet()) && row.string("is_active") == "true"
            }
            "challenge" -> row.string("title") == p.title && row.string("emoji") == p.emoji && row.string("description") == p.reason &&
                row.string("frequency") == p.challenge!!.frequency && row.number("duration_days") == p.challenge.duration.toDouble() &&
                row.string("start_date") == step.applyDate && row.string("requires_photo") == p.challenge.requiresPhoto.toString() && row["sadqa_amount"] == JsonNull
            else -> false
        }
    }
}
