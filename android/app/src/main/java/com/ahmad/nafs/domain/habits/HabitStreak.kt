package com.ahmad.nafs.domain.habits

import java.time.DayOfWeek
import java.time.LocalDate

enum class HabitType {
    SIMPLE,
    COUNTER,
    DURATION,
    SUBJECT,
}

enum class ScheduleKind {
    DAILY,
    WEEKDAYS,
    PER_WEEK,
}

data class HabitSchedule(
    val type: HabitType = HabitType.SIMPLE,
    val targetValue: Double = 1.0,
    val timeTargetMinutes: Int = 0,
    val isPaused: Boolean = false,
    val scheduleKind: ScheduleKind = ScheduleKind.DAILY,
    val scheduleDays: Set<DayOfWeek> = emptySet(),
    val weeklyTarget: Int? = null,
)

data class HabitResult(
    val date: LocalDate,
    val completed: Boolean,
    val value: Double = 0.0,
    val durationMinutes: Int = 0,
)

object HabitStreak {
    fun isScheduled(habit: HabitSchedule, date: LocalDate): Boolean {
        if (habit.isPaused) return false
        return when (habit.scheduleKind) {
            ScheduleKind.WEEKDAYS -> date.dayOfWeek in habit.scheduleDays
            ScheduleKind.DAILY,
            ScheduleKind.PER_WEEK,
            -> true
        }
    }

    fun isComplete(habit: HabitSchedule, result: HabitResult?): Boolean {
        if (result?.completed != true) return false
        return when (habit.type) {
            HabitType.COUNTER,
            -> result.value >= habit.targetValue
            HabitType.DURATION -> result.durationMinutes >= habit.timeTargetMinutes
            HabitType.SIMPLE,
            HabitType.SUBJECT,
            -> true
        }
    }

    fun current(
        habit: HabitSchedule,
        results: List<HabitResult>,
        today: LocalDate,
        maxLookbackDays: Int = 400,
    ): Int {
        if (habit.scheduleKind == ScheduleKind.PER_WEEK) {
            return currentWeekly(habit, results, today, maxLookbackDays)
        }
        val byDate = results.associateBy(HabitResult::date)
        var streak = 0

        repeat(maxLookbackDays) { offset ->
            val date = today.minusDays(offset.toLong())
            if (!isScheduled(habit, date)) return@repeat
            if (isComplete(habit, byDate[date])) {
                streak += 1
            } else if (offset != 0) {
                return streak
            }
        }
        return streak
    }

    private fun currentWeekly(
        habit: HabitSchedule,
        results: List<HabitResult>,
        today: LocalDate,
        maxLookbackDays: Int,
    ): Int {
        if (habit.isPaused) return 0
        val target = habit.weeklyTarget?.takeIf { it > 0 } ?: 1
        val completedByWeek = results.asSequence()
            .filter { isComplete(habit.copy(scheduleKind = ScheduleKind.DAILY), it) }
            .groupingBy { it.date.minusDays((it.date.dayOfWeek.value - 1).toLong()) }
            .eachCount()
        val currentMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val maxWeeks = (maxLookbackDays / 7).coerceAtLeast(1)
        var streak = 0
        repeat(maxWeeks) { offset ->
            val week = currentMonday.minusWeeks(offset.toLong())
            if ((completedByWeek[week] ?: 0) >= target) {
                streak += 1
            } else if (offset != 0) {
                return streak
            }
        }
        return streak
    }
}

