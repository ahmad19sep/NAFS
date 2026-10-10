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

/** The running streak and the best one, in days (or weeks for a per-week habit). */
data class StreakSummary(val current: Int, val best: Int)

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

    /**
     * The longest run within [maxLookbackDays]: scheduled days in a row that were complete or, for a per-week
     * habit, weeks in a row that met the target. Today (or this week) still in progress never ends a run.
     * Pauses are not recorded, so history is read as if the habit had always been active.
     */
    fun longest(
        habit: HabitSchedule,
        results: List<HabitResult>,
        today: LocalDate,
        maxLookbackDays: Int = 400,
    ): Int {
        val active = habit.copy(isPaused = false)
        if (active.scheduleKind == ScheduleKind.PER_WEEK) {
            return longestWeekly(active, results, today, maxLookbackDays)
        }
        val byDate = results.associateBy(HabitResult::date)
        var best = 0
        var run = 0
        for (offset in maxLookbackDays - 1 downTo 0) {
            val date = today.minusDays(offset.toLong())
            if (!isScheduled(active, date)) continue
            if (isComplete(active, byDate[date])) {
                run += 1
                best = maxOf(best, run)
            } else if (offset != 0) {
                run = 0
            }
        }
        return best
    }

    /**
     * [current] with the best streak beside it. The best never reads lower than [storedBest] (runs older than the
     * loaded history live only there) or than the current run.
     */
    fun summary(
        habit: HabitSchedule,
        results: List<HabitResult>,
        today: LocalDate,
        storedBest: Int = 0,
    ): StreakSummary {
        val current = current(habit, results, today)
        return StreakSummary(current, maxOf(longest(habit, results, today), storedBest, current))
    }

    /** Completed days in the Monday-to-Sunday week that holds [today], counting up to [today]. */
    fun completedThisWeek(habit: HabitSchedule, results: List<HabitResult>, today: LocalDate): Int {
        val monday = weekStart(today)
        return results.count { !it.date.isBefore(monday) && !it.date.isAfter(today) && isComplete(habit, it) }
    }

    /** Times per week a per-week habit asks for; at least one. */
    fun weeklyTarget(habit: HabitSchedule): Int = habit.weeklyTarget?.takeIf { it > 0 } ?: 1

    /** The Monday that starts [date]'s week. */
    fun weekStart(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())

    private fun currentWeekly(
        habit: HabitSchedule,
        results: List<HabitResult>,
        today: LocalDate,
        maxLookbackDays: Int,
    ): Int {
        if (habit.isPaused) return 0
        val target = weeklyTarget(habit)
        val completedByWeek = completedByWeek(habit, results)
        val currentMonday = weekStart(today)
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

    private fun longestWeekly(
        habit: HabitSchedule,
        results: List<HabitResult>,
        today: LocalDate,
        maxLookbackDays: Int,
    ): Int {
        val target = weeklyTarget(habit)
        val completedByWeek = completedByWeek(habit, results)
        val currentMonday = weekStart(today)
        val maxWeeks = (maxLookbackDays / 7).coerceAtLeast(1)
        var best = 0
        var run = 0
        for (offset in maxWeeks - 1 downTo 0) {
            val week = currentMonday.minusWeeks(offset.toLong())
            if ((completedByWeek[week] ?: 0) >= target) {
                run += 1
                best = maxOf(best, run)
            } else if (offset != 0) {
                run = 0
            }
        }
        return best
    }

    private fun completedByWeek(habit: HabitSchedule, results: List<HabitResult>): Map<LocalDate, Int> =
        results.asSequence()
            .filter { isComplete(habit.copy(scheduleKind = ScheduleKind.DAILY), it) }
            .groupingBy { weekStart(it.date) }
            .eachCount()
}

