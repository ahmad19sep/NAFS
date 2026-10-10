package com.ahmad.nafs.domain.tasks

import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class PlannedTaskReminder(
    val minutesBefore: Int,
    val triggerAt: Instant,
)

object TaskReminderPlanner {
    const val DEFAULT_LEAD_MINUTES = 30

    /** One alert [leadMinutes] before the due time in the account timezone, unless that moment has passed. */
    fun plan(
        periodDate: LocalDate,
        dueTime: String,
        timezone: String,
        now: Instant,
        leadMinutes: Int = DEFAULT_LEAD_MINUTES,
    ): List<PlannedTaskReminder> = try {
        val dueAt = periodDate
            .atTime(LocalTime.parse(dueTime.take(5)))
            .atZone(ZoneId.of(timezone))
            .toInstant()
        listOf(leadMinutes.coerceIn(1, 24 * 60)).mapNotNull { minutesBefore ->
            val triggerAt = dueAt.minusSeconds(minutesBefore * 60L)
            if (triggerAt > now) {
                PlannedTaskReminder(minutesBefore = minutesBefore, triggerAt = triggerAt)
            } else {
                null
            }
        }
    } catch (_: DateTimeException) {
        emptyList()
    }
}

object TaskReminderDeliveryRule {
    fun minutesRemaining(dueAt: Instant, now: Instant): Int? {
        if (!now.isBefore(dueAt)) return null
        return Duration.between(now, dueAt).toMinutes().coerceAtLeast(1).toInt()
    }

    /**
     * Wording for a reminder shown at [now]: "Due in 25 minutes", "Due now" or, for a snoozed reminder,
     * "Overdue by 10 minutes". Null once it is more than an hour overdue, when a reminder no longer helps.
     */
    fun dueText(dueAt: Instant, now: Instant): String? {
        minutesRemaining(dueAt, now)?.let { return if (it == 1) "Due in 1 minute" else "Due in $it minutes" }
        val late = Duration.between(dueAt, now).toMinutes()
        return when {
            late < 1 -> "Due now"
            late <= 60 -> if (late == 1L) "Overdue by 1 minute" else "Overdue by $late minutes"
            else -> null
        }
    }
}
