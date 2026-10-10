package com.ahmad.nafs.domain.tasks

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskPeriodTest {
    private val friday = LocalDate.parse("2026-10-09")

    @Test
    fun keysMatchDailyWeeklyAndMonthlyPeriods() {
        assertEquals(friday, TaskPeriod.key(TaskPeriodType.DAILY, friday))
        assertEquals(LocalDate.parse("2026-10-05"), TaskPeriod.key(TaskPeriodType.WEEKLY, friday))
        assertEquals(LocalDate.parse("2026-10-01"), TaskPeriod.key(TaskPeriodType.MONTHLY, friday))
    }

    @Test
    fun unfinishedPastPeriodIsMissedForEveryTaskType() {
        val yesterday = friday.minusDays(1)
        assertTrue(TaskPeriod.isMissed(TaskPeriodType.DAILY, yesterday, false, friday))
        assertFalse(TaskPeriod.isMissed(TaskPeriodType.DAILY, yesterday, true, friday))
        assertTrue(
            TaskPeriod.isMissed(
                TaskPeriodType.WEEKLY,
                LocalDate.parse("2026-09-28"),
                false,
                friday,
            ),
        )
        assertTrue(
            TaskPeriod.isMissed(
                TaskPeriodType.MONTHLY,
                LocalDate.parse("2026-09-01"),
                false,
                friday,
            ),
        )
        assertFalse(TaskPeriod.isMissed(TaskPeriodType.DAILY, friday, false, friday))
    }

    @Test
    fun currentAndPreviousPeriodFormTheGraceWindow() {
        assertTrue(TaskPeriod.isCurrentOrGrace(TaskPeriodType.DAILY, friday, friday))
        assertTrue(TaskPeriod.isCurrentOrGrace(TaskPeriodType.DAILY, friday.minusDays(1), friday))
        assertFalse(TaskPeriod.isCurrentOrGrace(TaskPeriodType.DAILY, friday.minusDays(2), friday))

        assertEquals(
            LocalDate.parse("2026-09-28"),
            TaskPeriod.previousKey(TaskPeriodType.WEEKLY, LocalDate.parse("2026-10-05")),
        )
        assertEquals(
            LocalDate.parse("2026-09-01"),
            TaskPeriod.previousKey(TaskPeriodType.MONTHLY, LocalDate.parse("2026-10-01")),
        )
    }
}

