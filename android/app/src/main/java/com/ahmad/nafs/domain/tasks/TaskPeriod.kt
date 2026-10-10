package com.ahmad.nafs.domain.tasks

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class TaskPeriodType {
    DAILY,
    WEEKLY,
    MONTHLY,
}

object TaskPeriod {
    fun key(type: TaskPeriodType, date: LocalDate): LocalDate = when (type) {
        TaskPeriodType.DAILY -> date
        TaskPeriodType.WEEKLY -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        TaskPeriodType.MONTHLY -> date.withDayOfMonth(1)
    }

    fun isMissed(
        type: TaskPeriodType,
        periodDate: LocalDate,
        isCompleted: Boolean,
        today: LocalDate,
    ): Boolean = !isCompleted && periodDate < key(type, today)

    fun previousKey(type: TaskPeriodType, currentKey: LocalDate): LocalDate = when (type) {
        TaskPeriodType.DAILY -> currentKey.minusDays(1)
        TaskPeriodType.WEEKLY -> currentKey.minusWeeks(1)
        TaskPeriodType.MONTHLY -> currentKey.minusMonths(1)
    }

    fun nextKey(type: TaskPeriodType, currentKey: LocalDate): LocalDate = when (type) {
        TaskPeriodType.DAILY -> currentKey.plusDays(1)
        TaskPeriodType.WEEKLY -> currentKey.plusWeeks(1)
        TaskPeriodType.MONTHLY -> currentKey.plusMonths(1)
    }

    fun isCurrentOrGrace(
        type: TaskPeriodType,
        periodDate: LocalDate,
        today: LocalDate,
    ): Boolean {
        val currentKey = key(type, today)
        return periodDate == currentKey || periodDate == previousKey(type, currentKey)
    }

    /** Planned for a later period than the current one, so it is not on today's (or this week's) list yet. */
    fun isUpcoming(
        type: TaskPeriodType,
        periodDate: LocalDate,
        today: LocalDate,
    ): Boolean = periodDate > key(type, today)
}

