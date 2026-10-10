package com.ahmad.nafs.domain.habits

import java.time.LocalDate

/** How one calendar day reads in a habit's history heatmap. */
enum class HabitDayState {
    /** The day's target was met. */
    COMPLETE,

    /** Something was logged, short of the target. */
    PARTIAL,

    /** A scheduled day that has ended with nothing logged, on or after the habit's first entry. */
    MISSED,

    /**
     * Scheduled, nothing saved, and "missed" would overstate it: today so far, days before the first entry (the
     * habit may not have existed yet), any open day of a per-week habit, or a habit that is paused now (when it was
     * paused is not recorded).
     */
    NO_ENTRY,

    /** Not one of the habit's scheduled days. */
    REST,

    /** After today. */
    FUTURE,
}

data class HabitHeatmapDay(
    val date: LocalDate,
    val state: HabitDayState,
    val isToday: Boolean,
    /** Whether the dated log sheet may open for this day (the same rule the log sheet enforces). */
    val canEdit: Boolean,
)

/** Monday to Sunday. */
data class HabitHeatmapWeek(val monday: LocalDate, val days: List<HabitHeatmapDay>)

object HabitHeatmap {
    const val WEEKS = 12

    /** Entries can be added or corrected for the most recent 400 days, today included. */
    const val EDITABLE_DAYS = 400

    /** [weeks] calendar weeks ending with the one that holds [today]; [results] are this habit's entries. */
    fun build(
        habit: HabitSchedule,
        results: List<HabitResult>,
        today: LocalDate,
        weeks: Int = WEEKS,
    ): List<HabitHeatmapWeek> {
        val count = weeks.coerceAtLeast(1)
        val byDate = results.associateBy(HabitResult::date)
        val firstEntry = results.minOfOrNull(HabitResult::date)
        val firstMonday = HabitStreak.weekStart(today).minusWeeks((count - 1).toLong())
        return (0 until count).map { week ->
            val monday = firstMonday.plusWeeks(week.toLong())
            HabitHeatmapWeek(
                monday = monday,
                days = (0L..6L).map { offset ->
                    val date = monday.plusDays(offset)
                    HabitHeatmapDay(
                        date = date,
                        state = state(habit, byDate[date], date, today, firstEntry),
                        isToday = date == today,
                        canEdit = canEdit(habit, date, today),
                    )
                },
            )
        }
    }

    fun state(
        habit: HabitSchedule,
        result: HabitResult?,
        date: LocalDate,
        today: LocalDate,
        firstEntry: LocalDate?,
    ): HabitDayState {
        if (date.isAfter(today)) return HabitDayState.FUTURE
        if (HabitStreak.isComplete(habit, result)) return HabitDayState.COMPLETE
        if (result != null && hasProgress(result)) return HabitDayState.PARTIAL
        if (!HabitStreak.isScheduled(habit.copy(isPaused = false), date)) return HabitDayState.REST
        val counted = !habit.isPaused &&
            habit.scheduleKind != ScheduleKind.PER_WEEK &&
            date.isBefore(today) &&
            firstEntry != null &&
            !date.isBefore(firstEntry)
        return if (counted) HabitDayState.MISSED else HabitDayState.NO_ENTRY
    }

    /** Past days within the lookback; today only while the habit is active and scheduled. */
    fun canEdit(habit: HabitSchedule, date: LocalDate, today: LocalDate): Boolean = when {
        date.isAfter(today) -> false
        date.isBefore(today.minusDays((EDITABLE_DAYS - 1).toLong())) -> false
        date == today -> HabitStreak.isScheduled(habit, today)
        else -> true
    }

    private fun hasProgress(result: HabitResult): Boolean =
        result.completed || result.value > 0.0 || result.durationMinutes > 0
}
