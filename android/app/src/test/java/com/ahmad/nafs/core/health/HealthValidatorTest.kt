package com.ahmad.nafs.core.health

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

class HealthValidatorTest {
    private val validLog = SaveHealthLogIntent(
        userId = "user-id",
        date = LocalDate.parse("2026-10-09"),
        waterGlasses = 8,
        steps = 7_500,
        sleepHours = 7.5,
        sleepSessions = listOf(SleepSession("night", "23:00", "06:30")),
        meals = HealthMealRules.ensureCore(emptyList()) { "meal-$it" },
        extras = mapOf("vitamins" to JsonPrimitive(true)),
        exerciseDone = true,
        exerciseMinutes = 30,
        weightKg = 72.4,
        mood = 7,
        notes = "Steady energy.",
    )

    @Test
    fun `valid profile and daily log pass`() {
        assertNull(HealthValidator.profileError(SaveHealthProfileIntent("user-id", 175.0, 72.0)))
        assertNull(HealthValidator.logError(validLog))
    }

    @Test
    fun `profile rejects implausible body measurements`() {
        assertEquals(
            "Height must be between 80 and 250 cm.",
            HealthValidator.profileError(SaveHealthProfileIntent("user-id", 50.0, 72.0)),
        )
        assertEquals(
            "Weight must be between 25 and 400 kg.",
            HealthValidator.profileError(SaveHealthProfileIntent("user-id", 175.0, 500.0)),
        )
    }

    @Test
    fun `exercise minutes agree with completion state`() {
        assertEquals(
            "Enter exercise minutes between 1 and 1,440.",
            HealthValidator.logError(validLog.copy(exerciseMinutes = null)),
        )
        assertEquals(
            "Exercise minutes require exercise to be marked done.",
            HealthValidator.logError(validLog.copy(exerciseDone = false, exerciseMinutes = 30)),
        )
    }

    @Test
    fun `daily ranges reject impossible values`() {
        assertEquals(
            "Sleep must be between 0 and 24 hours.",
            HealthValidator.logError(validLog.copy(sleepHours = 25.0)),
        )
        assertEquals(
            "Mood must be between 1 and 10.",
            HealthValidator.logError(validLog.copy(mood = 11)),
        )
    }

    @Test
    fun `sleep periods calculate daytime and cross-midnight durations`() {
        assertEquals(450, SleepSessionRules.minutes(SleepSession("night", "23:00", "06:30")))
        assertEquals(75, SleepSessionRules.minutes(SleepSession("nap", "13:00", "14:15")))
        assertEquals(
            8.8,
            SleepSessionRules.hours(
                listOf(
                    SleepSession("night", "23:00", "06:30"),
                    SleepSession("nap", "13:00", "14:15"),
                ),
            ) ?: 0.0,
            0.0,
        )
        assertEquals("8h 45m", SleepSessionRules.formatMinutes(525))
    }

    @Test
    fun `sleep periods reject invalid and equal times`() {
        assertNull(SleepSessionRules.minutes(SleepSession("bad", "25:00", "06:30")))
        assertEquals(
            "Use a valid 24-hour time for every sleep period.",
            HealthValidator.logError(
                validLog.copy(sleepSessions = listOf(SleepSession("bad", "later", "06:30"))),
            ),
        )
        assertEquals(
            "Sleep start and end times must be different.",
            HealthValidator.logError(
                validLog.copy(sleepSessions = listOf(SleepSession("same", "08:00", "08:00"))),
            ),
        )
    }

    @Test
    fun `combined sleep periods cannot exceed one day`() {
        val excessive = listOf(
            SleepSession("first", "00:00", "20:00"),
            SleepSession("second", "21:00", "17:00"),
        )
        assertEquals(
            "Total sleep cannot exceed 24 hours.",
            HealthValidator.logError(
                validLog.copy(sleepHours = 40.0, sleepSessions = excessive),
            ),
        )
    }

    @Test
    fun `core meals are restored before saved extras`() {
        val snack = HealthMeal("snack-1", "snack", "Snack", "🍿", emptyList())
        val meals = HealthMealRules.ensureCore(listOf(snack)) { "core-$it" }

        assertEquals(listOf("breakfast", "lunch", "dinner", "snack"), meals.map { it.key })
        assertTrue(HealthMealRules.isCore("breakfast"))
        assertFalse(HealthMealRules.isCore("snack"))
    }

    @Test
    fun `meal validation counts only meals with food`() {
        val meals = HealthMealRules.ensureCore(emptyList()) { "core-$it" }.mapIndexed { index, meal ->
            if (index == 0) {
                meal.copy(items = listOf(MealItem("food-1", "Daal", "🍽️")))
            } else {
                meal
            }
        }

        assertEquals(1, HealthMealRules.eatenCount(meals))
        assertNull(HealthMealRules.error(meals))
        assertEquals(
            "Food names must be between 1 and 80 characters.",
            HealthMealRules.error(
                meals.mapIndexed { index, meal ->
                    if (index == 0) meal.copy(items = listOf(MealItem("food-1", "", "🍽️"))) else meal
                },
            ),
        )
    }

    @Test
    fun `custom metric definitions validate their type fields`() {
        val valid = SaveCustomMetricsIntent(
            "user-id",
            listOf(CustomHealthMetric("water-1", "Extra water", "💧", CustomMetricType.COUNTER, 3.0, "cups")),
        )
        assertNull(HealthValidator.customMetricsError(valid))
        assertEquals(
            "Yes/no metrics cannot have a target or unit.",
            HealthValidator.customMetricsError(
                valid.copy(
                    metrics = listOf(
                        CustomHealthMetric("meds-1", "Medicine", "💊", CustomMetricType.BOOLEAN, 1.0, null),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `daily extras accept primitives and reject invalid identifiers`() {
        assertNull(HealthValidator.logError(validLog))
        assertEquals(
            "Custom metric values must be valid numbers or yes/no values.",
            HealthValidator.logError(validLog.copy(extras = mapOf("" to JsonPrimitive(2.0)))),
        )
    }

    @Test
    fun `custom health JSON matches the existing web contract`() {
        val metricJson = Json.encodeToString(
            CustomHealthMetric("caffeine-1", "Caffeine", "☕", CustomMetricType.COUNTER, 2.0, "cups"),
        )
        val extrasJson = Json.encodeToString(
            linkedMapOf("medicine-1" to JsonPrimitive(true), "pain-1" to JsonPrimitive(4.5)),
        )

        assertTrue(metricJson.contains("\"type\":\"counter\""))
        assertEquals("{\"medicine-1\":true,\"pain-1\":4.5}", extrasJson)
    }

    @Test
    fun `health log versions must include both row id and timestamp`() {
        assertNull(
            HealthValidator.logError(
                validLog.copy(expectedId = "log-id", expectedUpdatedAt = "2026-10-09T10:00:00Z"),
            ),
        )
        assertEquals(
            "Health log version is incomplete. Reload today's entry.",
            HealthValidator.logError(validLog.copy(expectedId = "log-id")),
        )
    }

    @Test
    fun `duplicate insert errors are classified as save conflicts`() {
        assertTrue(HealthWriteRules.isDuplicateKeyError("Postgres 23505 unique_violation"))
        assertTrue(HealthWriteRules.isDuplicateKeyError("duplicate key violates unique constraint"))
        assertFalse(HealthWriteRules.isDuplicateKeyError("network timeout"))
        assertFalse(HealthWriteRules.isDuplicateKeyError(null))
    }

    @Test
    fun `bmi uses metric height and rounds to one decimal`() {
        assertEquals(23.5, HealthMetrics.bmi(175.0, 72.0) ?: 0.0, 0.0)
        assertNull(HealthMetrics.bmi(null, 72.0))
    }
}
