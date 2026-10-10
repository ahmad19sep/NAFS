package com.ahmad.nafs.core.health

import java.time.LocalDate

enum class HealthTrendMetric(val label: String) {
    WATER("Water"),
    STEPS("Steps"),
    SLEEP("Sleep"),
    WEIGHT("Weight"),
    MOOD("Mood"),
}

/** One day on a trend chart. [value] is null when nothing was saved for that signal on that day. */
data class HealthTrendPoint(val date: LocalDate, val value: Double?)

data class HealthTrendSummary(
    val days: Int,
    val loggedDays: Int,
    val average: Double?,
    val latest: HealthTrendPoint?,
    val first: HealthTrendPoint?,
    /** Days that met the goal (water, steps) or fell inside the 7–9 hour guide (sleep); null for other signals. */
    val goalDays: Int?,
) {
    /** Latest minus first logged value, when at least two days were logged. */
    val change: Double?
        get() {
            val from = first ?: return null
            val to = latest ?: return null
            val start = from.value ?: return null
            val end = to.value ?: return null
            return if (from.date == to.date) null else end - start
        }
}

object HealthTrends {
    const val MAX_DAYS = 30

    /**
     * The value a saved day contributes. Water is stored as 0 when it was never touched, so only a positive
     * count counts as logged; the other signals are optional columns and count whenever they are present.
     */
    fun value(log: HealthLog, metric: HealthTrendMetric): Double? = when (metric) {
        HealthTrendMetric.WATER -> log.waterGlasses.takeIf { it > 0 }?.toDouble()
        HealthTrendMetric.STEPS -> log.steps?.toDouble()
        HealthTrendMetric.SLEEP -> log.sleepHours?.takeIf { it > 0.0 && it.isFinite() }
        HealthTrendMetric.WEIGHT -> log.weightKg?.takeIf { it > 0.0 && it.isFinite() }
        HealthTrendMetric.MOOD -> log.mood?.toDouble()
    }

    /** One point per calendar day, oldest first, ending on [end]. Missing days stay empty, never zero. */
    fun series(logs: List<HealthLog>, metric: HealthTrendMetric, end: LocalDate, days: Int): List<HealthTrendPoint> {
        val span = days.coerceIn(1, MAX_DAYS)
        val byDate = logs.associateBy { it.date }
        return (span - 1 downTo 0).map { offset ->
            val date = end.minusDays(offset.toLong())
            HealthTrendPoint(date, byDate[date]?.let { value(it, metric) })
        }
    }

    fun summary(points: List<HealthTrendPoint>, metric: HealthTrendMetric, goals: HealthGoals): HealthTrendSummary {
        val logged = points.filter { it.value != null }
        val values = logged.mapNotNull { it.value }
        val goalDays = when (metric) {
            HealthTrendMetric.WATER -> values.count { it >= goals.waterGlasses }
            HealthTrendMetric.STEPS -> values.count { it >= goals.steps }
            HealthTrendMetric.SLEEP -> values.count { HealthGoalRules.inSleepGuide(it) }
            HealthTrendMetric.WEIGHT, HealthTrendMetric.MOOD -> null
        }
        return HealthTrendSummary(
            days = points.size,
            loggedDays = logged.size,
            average = values.takeIf { it.isNotEmpty() }?.average(),
            latest = logged.lastOrNull(),
            first = logged.firstOrNull(),
            goalDays = goalDays,
        )
    }

    /** Replaces (or adds) one saved day in a cached range, keeping it sorted and inside [from]..[to]. */
    fun merge(logs: List<HealthLog>, saved: HealthLog, from: LocalDate, to: LocalDate): List<HealthLog> {
        val others = logs.filterNot { it.date == saved.date }
        val next = if (saved.date in from..to) others + saved else others
        return next.sortedBy { it.date }
    }
}
