package com.ahmad.nafs.domain.tasks

import java.time.LocalDate

/** Planning tasks ahead and moving open ones between periods. Dates are account-timezone days. */
object TaskPlanning {
    /** How far ahead a daily task can be planned. */
    const val MAX_DAYS_AHEAD = 60L

    fun latestPlannableDate(today: LocalDate): LocalDate = today.plusDays(MAX_DAYS_AHEAD)

    fun isPlannable(date: LocalDate, today: LocalDate): Boolean =
        !date.isBefore(today) && !date.isAfter(latestPlannableDate(today))

    /**
     * Where an open task from an earlier period can be moved: today or tomorrow for a daily task, the current
     * week or month otherwise. Each target is already a period key, so it can be written to `period_date` as is.
     */
    fun moveTargets(type: TaskPeriodType, today: LocalDate): List<LocalDate> {
        val current = TaskPeriod.key(type, today)
        return if (type == TaskPeriodType.DAILY) {
            listOf(current, TaskPeriod.nextKey(type, current))
        } else {
            listOf(current)
        }
    }
}
