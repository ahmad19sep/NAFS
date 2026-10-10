package com.ahmad.nafs.core.health

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthGoalsTest {
    private val today = LocalDate.parse("2026-10-10")

    private fun log(date: LocalDate, water: Int = 0, steps: Int? = null, sleep: Double? = null, weight: Double? = null, mood: Int? = null) =
        HealthLog(
            id = "log-$date", userId = "user", date = date, waterGlasses = water, steps = steps, sleepHours = sleep,
            sleepSessions = emptyList(), meals = emptyList(), extras = emptyMap(), exerciseDone = false,
            exerciseMinutes = null, weightKg = weight, mood = mood, notes = null,
            createdAt = "2026-10-10T06:00:00Z", updatedAt = "2026-10-10T06:00:00Z",
        )

    @Test fun `defaults match the web goals`() {
        val goals = HealthGoals()
        assertEquals(8, goals.waterGlasses)
        assertEquals(8_000, goals.steps)
    }

    @Test fun `goals are clamped to their ranges and steps round to 500`() {
        assertEquals(HealthGoals(4, 1_000), HealthGoals(1, 10).normalized())
        assertEquals(HealthGoals(16, 30_000), HealthGoals(40, 90_000).normalized())
        assertEquals(8_500, HealthGoals(steps = 8_260).normalized().steps)
        assertEquals(8_000, HealthGoals(steps = 8_240).normalized().steps)
    }

    @Test fun `sleep feedback follows the seven to nine hour guide`() {
        assertEquals(SleepFeedback.NONE, HealthGoalRules.sleepFeedback(null))
        assertEquals(SleepFeedback.NONE, HealthGoalRules.sleepFeedback(0))
        assertEquals(SleepFeedback.SHORT, HealthGoalRules.sleepFeedback(6 * 60 + 59))
        assertEquals(SleepFeedback.IN_RANGE, HealthGoalRules.sleepFeedback(7 * 60))
        assertEquals(SleepFeedback.IN_RANGE, HealthGoalRules.sleepFeedback(9 * 60))
        assertEquals(SleepFeedback.LONG, HealthGoalRules.sleepFeedback(9 * 60 + 1))
        assertTrue(HealthGoalRules.inSleepGuide(7.5))
        assertFalse(HealthGoalRules.inSleepGuide(5.0))
    }

    @Test fun `progress is capped and percent stays readable for typos`() {
        assertEquals(0.5f, HealthGoalRules.progress(4.0, 8.0))
        assertEquals(1f, HealthGoalRules.progress(12.0, 8.0))
        assertEquals(0f, HealthGoalRules.progress(null, 8.0))
        assertEquals(80, HealthGoalRules.percent(6_400, 8_000))
        assertEquals(12_500, HealthGoalRules.percent(Int.MAX_VALUE, 8_000))
    }

    @Test fun `series has one point per day and leaves unsaved days empty`() {
        val logs = listOf(log(today.minusDays(6), water = 5), log(today, water = 9), log(today.minusDays(2), water = 0, steps = 3_000))
        val water = HealthTrends.series(logs, HealthTrendMetric.WATER, today, 7)
        assertEquals(7, water.size)
        assertEquals(today.minusDays(6), water.first().date)
        assertEquals(today, water.last().date)
        assertEquals(5.0, water.first().value)
        // A saved day where water was never touched is not a zero-water day.
        assertNull(water[4].value)
        assertEquals(3_000.0, HealthTrends.series(logs, HealthTrendMetric.STEPS, today, 7)[4].value)
        assertEquals(30, HealthTrends.series(logs, HealthTrendMetric.WATER, today, 30).size)
    }

    @Test fun `summary averages logged days and counts goal days`() {
        val logs = listOf(
            log(today.minusDays(2), water = 8, steps = 9_000, sleep = 7.5),
            log(today.minusDays(1), water = 4, steps = 2_000, sleep = 5.0),
            log(today, water = 10, sleep = 9.5),
        )
        val goals = HealthGoals()
        val water = HealthTrends.summary(HealthTrends.series(logs, HealthTrendMetric.WATER, today, 7), HealthTrendMetric.WATER, goals)
        assertEquals(3, water.loggedDays)
        assertEquals(22.0 / 3.0, water.average!!, 0.0001)
        assertEquals(2, water.goalDays)
        val steps = HealthTrends.summary(HealthTrends.series(logs, HealthTrendMetric.STEPS, today, 7), HealthTrendMetric.STEPS, goals)
        assertEquals(2, steps.loggedDays)
        assertEquals(1, steps.goalDays)
        val sleep = HealthTrends.summary(HealthTrends.series(logs, HealthTrendMetric.SLEEP, today, 7), HealthTrendMetric.SLEEP, goals)
        assertEquals(1, sleep.goalDays)
    }

    @Test fun `weight change runs from the first to the latest weigh-in`() {
        val logs = listOf(log(today.minusDays(5), weight = 73.0), log(today.minusDays(1), weight = 72.2))
        val summary = HealthTrends.summary(HealthTrends.series(logs, HealthTrendMetric.WEIGHT, today, 7), HealthTrendMetric.WEIGHT, HealthGoals())
        assertEquals(-0.8, summary.change!!, 0.0001)
        assertEquals(72.2, summary.latest?.value)
        assertNull(summary.goalDays)
        val single = HealthTrends.summary(HealthTrends.series(logs.take(1), HealthTrendMetric.WEIGHT, today, 7), HealthTrendMetric.WEIGHT, HealthGoals())
        assertNull(single.change)
    }

    @Test fun `merging a saved day replaces it and drops days outside the range`() {
        val logs = listOf(log(today.minusDays(1), water = 2), log(today, water = 3))
        val merged = HealthTrends.merge(logs, log(today.minusDays(1), water = 6), today.minusDays(29), today)
        assertEquals(listOf(6, 3), merged.map { it.waterGlasses })
        assertEquals(logs, HealthTrends.merge(logs, log(today.minusDays(40), water = 1), today.minusDays(29), today))
    }
}
