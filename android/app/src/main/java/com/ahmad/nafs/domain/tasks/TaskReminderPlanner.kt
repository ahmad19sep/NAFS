package com.ahmad.nafs.domain.tasks

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class PlannedTaskReminder(
    val minutesBefore: Int,
    val triggerAt: Instant,
)

object TaskReminderPlanner {
    private val reminderOffsetsMinutes = listOf(30)

    fun plan(
        periodDate: LocalDate,
        dueTime: String,
        timezone: String,
        now: Instant,
    ): List<PlannedTaskReminder> = try {
        val dueAt = periodDate
            .atTime(LocalTime.parse(dueTime.take(5)))
            .atZone(ZoneId.of(timezone))
            .toInstant()
        reminderOffsetsMinutes.mapNotNull { minutesBefore ->
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
        return java.time.Duration.between(now, dueAt).toMinutes().coerceAtLeast(1).toInt()
    }
}
