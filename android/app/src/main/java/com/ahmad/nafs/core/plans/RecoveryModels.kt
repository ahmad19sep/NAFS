package com.ahmad.nafs.core.plans

import com.ahmad.nafs.core.time.ServerTimestamp
import com.ahmad.nafs.core.coach.CoachConversation
import com.ahmad.nafs.core.coach.CoachRules
import com.ahmad.nafs.core.reports.ReportRules
import java.time.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class PlanTask(val priority: String, @SerialName("due_time") val dueTime: String? = null, val note: String? = null)
@Serializable data class PlanHabit(val type: String, @SerialName("target_value") val targetValue: Double? = null,
    val unit: String? = null, @SerialName("time_target_mins") val minutes: Int? = null,
    @SerialName("schedule_kind") val schedule: String, @SerialName("schedule_days") val days: List<String>? = null)
@Serializable data class PlanChallenge(val frequency: String, @SerialName("duration_days") val duration: Int,
    @SerialName("requires_photo") val requiresPhoto: Boolean)
@Serializable data class PlanProposal(val kind: String, val title: String, val emoji: String, val reason: String,
    val task: PlanTask? = null, val habit: PlanHabit? = null, val challenge: PlanChallenge? = null) {
    fun description(): String = when (kind) {
        "task" -> "${task!!.priority} priority" + (task.dueTime?.let { " · due $it" } ?: "")
        "habit" -> when (habit!!.type) {
            "duration" -> "${habit.minutes} minutes"
            "counter" -> "${habit.targetValue} ${habit.unit}"
            else -> "Yes/no"
        } + " · " + if (habit.schedule == "daily") "daily" else habit.days.orEmpty().joinToString(", ")
        else -> "${challenge!!.duration} days · ${challenge.frequency} check-in" + if (challenge.requiresPhoto) " · photo proof" else ""
    }
}
@Serializable data class RecoveryPlan(val problem: String, val approach: String, val steps: List<PlanProposal>)
@Serializable data class RecoveryIntent(@SerialName("request_id") val requestId: String,
    val intent: String? = null, @SerialName("conversation_id") val conversationId: String? = null,
    @SerialName("expected_updated_at") val expectedUpdatedAt: String? = null) {
    val fromChat: Boolean get() = conversationId != null
    companion object {
        fun chat(requestId: String, conversation: CoachConversation) = RecoveryIntent(requestId,
            conversationId = conversation.id, expectedUpdatedAt = conversation.updatedAt)
    }
}
@Serializable data class RecoveryResponse(val recovery: RecoveryPlan, val dropped: Int = 0, val provider: String,
    val fellBack: Boolean = false, @SerialName("context_partial") val contextPartial: Boolean = false,
    @SerialName("source_date") val sourceDate: String, @SerialName("request_id") val requestId: String)
@Serializable data class PlanStepState(val id: String, val selected: Boolean = true, val applyDate: String? = null,
    val added: Boolean = false, val error: String? = null)
@Serializable data class PlanPreview(val response: RecoveryResponse, val steps: List<PlanStepState>) {
    val id: String get() = response.requestId
    val hasUnconfirmed: Boolean get() = steps.any { it.applyDate != null && !it.added }
}
@Serializable data class RecoverySnapshot(val userId: String, val draft: String = "", val pending: RecoveryIntent? = null,
    val preview: PlanPreview? = null)

data class RecoveryLoaded(val snapshot: RecoverySnapshot, val isOffline: Boolean, val message: String? = null)
sealed interface RecoveryGenerateResult {
    data class Saved(val snapshot: RecoverySnapshot) : RecoveryGenerateResult
    data class Failure(val message: String, val pending: Boolean) : RecoveryGenerateResult
}
data class RecoveryApplyResult(val snapshot: RecoverySnapshot, val message: String? = null)
enum class PlanItemState { MISSING, ADDED, CONFLICT }

object RecoveryRules {
    val days = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
    fun validIntent(intent: RecoveryIntent): Boolean = ReportRules.validId(intent.requestId) &&
        if (intent.fromChat) intent.intent == null && ReportRules.validId(intent.conversationId!!) &&
            intent.expectedUpdatedAt?.let { runCatching { ServerTimestamp.parse(it) }.isSuccess } == true
        else intent.conversationId == null && intent.expectedUpdatedAt == null && intent.intent?.let {
            it == it.trim() && it.length in 3..500 } == true
    fun validStep(p: PlanProposal): Boolean = p.title == p.title.trim() && p.title.length in 3..120 &&
        p.emoji.isNotBlank() && p.emoji.length <= 8 && p.reason.trim().length in 10..300 && when (p.kind) {
            "task" -> p.task != null && p.habit == null && p.challenge == null && p.task.priority in listOf("low", "medium", "high") &&
                (p.task.note?.length ?: 0) <= 300 && (p.task.dueTime == null || Regex("^(?:[01][0-9]|2[0-3]):[0-5][0-9]$").matches(p.task.dueTime))
            "habit" -> p.habit != null && p.task == null && p.challenge == null && p.title.length <= 80 &&
                p.habit.type in listOf("simple", "counter", "duration") && p.habit.schedule in listOf("daily", "weekdays") &&
                (p.habit.unit?.length ?: 0) <= 24 && (p.habit.targetValue == null || p.habit.targetValue.isFinite() && p.habit.targetValue in 1.0..1000.0) &&
                (p.habit.minutes == null || p.habit.minutes in 1..1440) &&
                (p.habit.type != "counter" || p.habit.targetValue != null && !p.habit.unit.isNullOrBlank()) &&
                (p.habit.type != "duration" || p.habit.minutes != null) &&
                (p.habit.days == null || p.habit.days.size <= 7 && p.habit.days.all { it in days }) &&
                (p.habit.schedule != "weekdays" || !p.habit.days.isNullOrEmpty())
            "challenge" -> p.challenge != null && p.task == null && p.habit == null && p.challenge.frequency in listOf("daily", "weekly", "monthly", "yearly") &&
                p.challenge.duration in (when (p.challenge.frequency) { "yearly" -> 365; "monthly" -> 30; else -> 7 })..(if (p.challenge.frequency == "yearly") 1825 else 365)
            else -> false
        }
    fun validResponse(intent: RecoveryIntent, response: RecoveryResponse): Boolean = response.requestId == intent.requestId &&
        validPreviewResponse(response)
    private fun validPreviewResponse(response: RecoveryResponse): Boolean = response.provider in listOf("gemini", "anthropic", "cloudflare") &&
        response.dropped in 0..4 && runCatching { LocalDate.parse(response.sourceDate) }.isSuccess &&
        response.recovery.problem.trim().length in 5..200 && response.recovery.approach.trim().length in 20..500 &&
        response.recovery.steps.size in 1..4 && response.recovery.steps.count { it.kind == "challenge" } <= 1 && response.recovery.steps.all(::validStep)
    fun validSnapshot(userId: String, snapshot: RecoverySnapshot): Boolean = snapshot.userId == userId && snapshot.draft.length <= 180_000 &&
        (snapshot.pending == null || snapshot.preview == null) &&
        (snapshot.pending == null || validIntent(snapshot.pending)) && (snapshot.preview == null || snapshot.preview.let { preview ->
            ReportRules.validId(preview.id) && validPreviewResponse(preview.response) && preview.steps.size == preview.response.recovery.steps.size &&
                preview.steps.map { it.id }.distinct().size == preview.steps.size && preview.steps.all {
                    ReportRules.validId(it.id) && (it.error?.length ?: 0) <= 500 &&
                        (it.applyDate == null || runCatching { LocalDate.parse(it.applyDate) }.isSuccess) && (!it.added || it.applyDate != null)
                }
        })
    fun canUseChat(userId: String, chat: CoachConversation): Boolean = CoachRules.validConversation(userId, chat) && chat.messages.isNotEmpty()
}
