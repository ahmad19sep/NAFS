package com.ahmad.nafs.domain.goals

import java.time.LocalDate
import kotlin.math.roundToInt

enum class GoalType(val defaultDays: Long) {
    WEEKLY(7),
    MONTHLY(30),
    QUARTERLY(90),
    YEARLY(365),
    LONG_TERM(1_095),
}

enum class GoalCategory {
    DEEN,
    CAREER,
    LEARNING,
    HEALTH,
    FINANCIAL,
    FAMILY,
    PERSONAL,
}

object GoalProgress {
    fun fromMilestones(completed: List<Boolean>): Int {
        if (completed.isEmpty()) return 0
        return (completed.count { it }.toDouble() / completed.size * 100).roundToInt()
    }

    fun fromNumeric(current: Double, target: Double): Int {
        if (!current.isFinite() || !target.isFinite() || target <= 0.0) return 0
        return (current.coerceAtLeast(0.0) / target * 100).roundToInt().coerceIn(0, 100)
    }

    fun resolve(
        storedPercent: Int,
        milestones: List<Boolean>,
        currentValue: Double?,
        targetValue: Double?,
    ): Int = when {
        milestones.isNotEmpty() -> fromMilestones(milestones)
        currentValue != null && targetValue != null -> fromNumeric(currentValue, targetValue)
        else -> storedPercent.coerceIn(0, 100)
    }
}

object GoalDates {
    fun defaultDeadline(type: GoalType, startDate: LocalDate): LocalDate =
        startDate.plusDays(type.defaultDays)

    fun daysRemaining(deadline: LocalDate?, today: LocalDate): Long? =
        deadline?.toEpochDay()?.minus(today.toEpochDay())
}

