package com.ahmad.nafs.core.habits

import com.ahmad.nafs.domain.habits.HabitType
import com.ahmad.nafs.domain.habits.ScheduleKind
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitDefinitionValidatorTest {
    @Test
    fun validSimpleHabitPasses() {
        assertNull(HabitDefinitionValidator.validationError(intent()))
    }

    @Test
    fun weekdayScheduleNeedsAtLeastOneDay() {
        val error = HabitDefinitionValidator.validationError(
            intent(scheduleKind = ScheduleKind.WEEKDAYS),
        )

        assertEquals("Select at least one scheduled day.", error)
    }

    @Test
    fun selectedWeekdaySchedulePasses() {
        val error = HabitDefinitionValidator.validationError(
            intent(
                scheduleKind = ScheduleKind.WEEKDAYS,
                scheduleDays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            ),
        )

        assertNull(error)
    }

    @Test
    fun perWeekTargetStaysWithinCalendarWeek() {
        val error = HabitDefinitionValidator.validationError(
            intent(scheduleKind = ScheduleKind.PER_WEEK, weeklyTarget = 8),
        )

        assertEquals("Weekly target must be between 1 and 7.", error)
    }

    @Test
    fun subjectPositionCannotExceedTotal() {
        val error = HabitDefinitionValidator.validationError(
            intent(
                type = HabitType.SUBJECT,
                subjectName = "A useful book",
                subjectTotal = 200,
                subjectPosition = 201,
                subjectUnit = "pages",
            ),
        )

        assertEquals("Current position cannot be greater than the subject total.", error)
    }

    @Test
    fun exactCreateReplayIsSafeButChangedPayloadIsRejected() {
        val original = payload(name = "Read")
        val changed = payload(name = "Exercise")

        assertTrue(HabitDefinitionReplayRule.isSameIntent(original, original.copy()))
        assertEquals(false, HabitDefinitionReplayRule.isSameIntent(original, changed))
    }

    private fun intent(
        type: HabitType = HabitType.SIMPLE,
        scheduleKind: ScheduleKind = ScheduleKind.DAILY,
        scheduleDays: Set<DayOfWeek> = emptySet(),
        weeklyTarget: Int? = null,
        subjectName: String? = null,
        subjectTotal: Int? = null,
        subjectPosition: Int = 0,
        subjectUnit: String? = null,
    ) = SaveHabitIntent(
        requestId = "request-id",
        userId = "user-id",
        name = "Read",
        emoji = "📖",
        type = type,
        targetValue = 1.0,
        unit = "times",
        timeTargetMinutes = 30,
        subjectName = subjectName,
        subjectTotal = subjectTotal,
        subjectPosition = subjectPosition,
        subjectUnit = subjectUnit,
        scheduleKind = scheduleKind,
        scheduleDays = scheduleDays,
        weeklyTarget = weeklyTarget,
    )

    private fun payload(name: String) = HabitDefinitionPayload(
        userId = "user-id",
        name = name,
        emoji = "⭐",
        type = HabitType.SIMPLE,
        targetValue = 1.0,
        unit = "",
        timeTargetMinutes = 0,
        subjectName = null,
        subjectTotal = null,
        subjectPosition = 0,
        subjectUnit = null,
        scheduleKind = ScheduleKind.DAILY,
        scheduleDays = emptySet(),
        weeklyTarget = null,
    )
}
