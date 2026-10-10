package com.ahmad.nafs.core.plans

import com.ahmad.nafs.domain.habits.ScheduleKind
import com.ahmad.nafs.domain.tasks.TaskPeriodType
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.*
import org.junit.Test

internal const val planRequestId = "00000000-0000-4000-8000-000000000001"
internal const val planStepId = "00000000-0000-4000-8000-000000000002"
internal val planToday = LocalDate.of(2026, 10, 10)
internal fun planIntent() = RecoveryIntent(planRequestId, "I keep scrolling at night")
internal fun taskProposal() = PlanProposal("task", "Move the charger", "🔌", "Move the trigger away from where you relax.", task = PlanTask("high", "22:30", "Kitchen counter"))
internal fun habitProposal() = PlanProposal("habit", "Read a book", "📖", "A small replacement for the scrolling slot.",
    habit = PlanHabit("duration", minutes = 20, schedule = "weekdays", days = listOf("mon", "fri")))
internal fun challengeProposal() = PlanProposal("challenge", "Read for two weeks", "📖", "A fourteen day commitment to get started.", challenge = PlanChallenge("daily", 14, true))
internal fun planResponse(steps: List<PlanProposal> = listOf(taskProposal(), habitProposal(), challengeProposal())) = RecoveryResponse(
    RecoveryPlan("Too much evening scrolling", "Move the phone out of reach and start with a small replacement.", steps),
    provider = "cloudflare", sourceDate = planToday.toString(), requestId = planRequestId)
internal fun savedPlan() = PlanPreview(planResponse(), listOf(planStepId, "00000000-0000-4000-8000-000000000003", "00000000-0000-4000-8000-000000000004").map(::PlanStepState))

class RecoveryRulesTest {
    @Test fun `Gemini plans can be accepted and restored while unknown providers are rejected`() {
        val response = planResponse().copy(provider = "gemini")
        assertTrue(RecoveryRules.validResponse(planIntent(), response))
        assertTrue(RecoveryRules.validSnapshot("user", RecoverySnapshot("user", preview = savedPlan().copy(response = response))))
        assertFalse(RecoveryRules.validResponse(planIntent(), response.copy(provider = "unknown")))
    }

    @Test fun `intent accepts only trimmed text or a saved version pointer`() {
        assertTrue(RecoveryRules.validIntent(planIntent()))
        assertFalse(RecoveryRules.validIntent(planIntent().copy(intent = "  text  ")))
        assertFalse(RecoveryRules.validIntent(planIntent().copy(intent = "x".repeat(501))))
        val chat = RecoveryIntent(planRequestId, conversationId = planStepId, expectedUpdatedAt = "2026-10-10T12:00:00.123456+00:00")
        assertTrue(RecoveryRules.validIntent(chat))
        assertFalse(RecoveryRules.validIntent(chat.copy(intent = "Injected text")))
        assertFalse(RecoveryRules.validIntent(chat.copy(expectedUpdatedAt = null)))
        assertFalse(RecoveryRules.validIntent(chat.copy(conversationId = "bad")))
    }
    @Test fun `response requires matching request and native usable shape`() {
        assertTrue(RecoveryRules.validResponse(planIntent(), planResponse()))
        assertTrue(RecoveryRules.validResponse(planIntent(), planResponse().copy(provider = "anthropic", fellBack = true)))
        assertFalse(RecoveryRules.validResponse(planIntent(), planResponse().copy(requestId = planStepId)))
        assertFalse(RecoveryRules.validResponse(planIntent(), planResponse().copy(provider = "unknown")))
        assertFalse(RecoveryRules.validResponse(planIntent(), planResponse(listOf(challengeProposal(), challengeProposal()))))
        assertFalse(RecoveryRules.validResponse(planIntent(), planResponse(emptyList())))
    }
    @Test fun `invalid targets schedules and times cannot reach native creates`() {
        assertFalse(RecoveryRules.validStep(taskProposal().copy(task = PlanTask("high", "25:00"))))
        assertFalse(RecoveryRules.validStep(habitProposal().copy(title = "h".repeat(81))))
        assertFalse(RecoveryRules.validStep(habitProposal().copy(habit = PlanHabit("counter", 3.0, schedule = "daily"))))
        assertFalse(RecoveryRules.validStep(habitProposal().copy(habit = PlanHabit("counter", Double.NaN, "pages", schedule = "daily"))))
        assertFalse(RecoveryRules.validStep(habitProposal().copy(habit = PlanHabit("duration", minutes = 20, schedule = "weekdays", days = emptyList()))))
        assertFalse(RecoveryRules.validStep(habitProposal().copy(habit = PlanHabit("duration", minutes = 1441, schedule = "daily"))))
        assertFalse(RecoveryRules.validStep(challengeProposal().copy(challenge = PlanChallenge("monthly", 14, false))))
        assertFalse(RecoveryRules.validStep(challengeProposal().copy(challenge = PlanChallenge("weekly", 366, false))))
        assertTrue(RecoveryRules.validStep(challengeProposal().copy(challenge = PlanChallenge("yearly", 1825, false))))
    }
    @Test fun `persisted preview must be owned have distinct IDs and preserve attempted dates`() {
        val snapshot = RecoverySnapshot("user", preview = savedPlan())
        assertTrue(RecoveryRules.validSnapshot("user", snapshot))
        assertFalse(RecoveryRules.validSnapshot("another", snapshot))
        assertFalse(RecoveryRules.validSnapshot("user", snapshot.copy(pending = planIntent())))
        assertFalse(RecoveryRules.validSnapshot("user", snapshot.copy(preview = savedPlan().copy(steps = List(3) { PlanStepState(planStepId) }))))
        assertFalse(RecoveryRules.validSnapshot("user", snapshot.copy(preview = savedPlan().copy(steps = savedPlan().steps.map { it.copy(added = true) }))))
    }
    @Test fun `mapping preserves task note due time and the frozen account date`() {
        val task = PlanMappings.task("user", taskProposal(), planStepId, planToday)
        assertEquals(planStepId, task.requestId)
        assertEquals(planToday, task.today)
        assertEquals(TaskPeriodType.DAILY, task.type)
        assertEquals("22:30", task.dueTime)
        assertEquals("Kitchen counter", task.note)
    }
    @Test fun `mapping preserves duration weekdays and photo proof without a pledge`() {
        val habit = PlanMappings.habit("user", habitProposal(), planStepId)
        assertEquals(20, habit.timeTargetMinutes)
        assertEquals(ScheduleKind.WEEKDAYS, habit.scheduleKind)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), habit.scheduleDays)
        val challenge = PlanMappings.challenge("user", challengeProposal(), planStepId, planToday)
        assertEquals(planToday, challenge.startDate)
        assertTrue(challenge.requiresPhoto)
        assertNull(challenge.sadqaAmount)
    }
    @Test fun `inspection accepts only the matching owned active task without overwriting changes`() {
        val row = Json.decodeFromString<JsonObject>("""{"id":"$planStepId","user_id":"user","title":"Move the charger","type":"daily","priority":"high","period_date":"2026-10-10","due_time":"22:30","note":"Kitchen counter","deleted_at":null,"status":"done"}""")
        val step = PlanStepState(planStepId, applyDate = planToday.toString())
        assertTrue(PlanItemRules.matches("user", taskProposal(), step, row))
        assertFalse(PlanItemRules.matches("foreign", taskProposal(), step, row))
        assertFalse(PlanItemRules.matches("user", taskProposal(), step.copy(applyDate = "2026-10-11"), row))
        assertFalse(PlanItemRules.matches("user", taskProposal().copy(title = "Changed title"), step, row))
    }
}
