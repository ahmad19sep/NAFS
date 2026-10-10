package com.ahmad.nafs.domain.reminders

import com.ahmad.nafs.domain.habits.HabitResult
import com.ahmad.nafs.domain.habits.HabitSchedule
import com.ahmad.nafs.domain.habits.HabitStreak
import com.ahmad.nafs.domain.habits.HabitType
import com.ahmad.nafs.domain.habits.ScheduleKind
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/** What a habit reminder or the evening habit check needs to know about one habit today. */
data class HabitReminderFacts(
    val name: String,
    val emoji: String,
    val type: HabitType,
    val targetValue: Double = 1.0,
    val unit: String = "",
    val timeTargetMinutes: Int = 0,
    val subjectName: String? = null,
    val subjectPosition: Int = 0,
    val subjectTotal: Int? = null,
    val subjectUnit: String? = null,
    val currentStreak: Int = 0,
    val loggedValue: Double = 0.0,
    val loggedMinutes: Int = 0,
)

/** Timing and wording for per-habit reminders and the evening habit check, in the account timezone. */
object HabitReminderRules {
    val defaultHabitCheckTime: LocalTime = LocalTime.of(20, 0)

    /**
     * The first [time] after [now] on a day the habit is scheduled, within the next week. Paused habits get none.
     * A time that falls in a DST gap moves forward with the clock, never to the next day.
     */
    fun nextTrigger(time: LocalTime, schedule: HabitSchedule, zone: String, now: Instant): Instant? {
        val id = try { ZoneId.of(zone) } catch (_: DateTimeException) { return null }
        if (schedule.isPaused) return null
        val today = now.atZone(id).toLocalDate()
        for (offset in 0L..7L) {
            val date = today.plusDays(offset)
            if (!HabitStreak.isScheduled(schedule, date)) continue
            val at = date.atTime(time).atZone(id).toInstant()
            if (at.isAfter(now)) return at
        }
        return null
    }

    /** A per-week habit stops reminding once this week's (Monday to Sunday) target is reached. */
    fun weeklyTargetMet(schedule: HabitSchedule, results: List<HabitResult>, today: LocalDate): Boolean {
        if (schedule.scheduleKind != ScheduleKind.PER_WEEK) return false
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val target = schedule.weeklyTarget?.takeIf { it > 0 } ?: 1
        val daily = schedule.copy(scheduleKind = ScheduleKind.DAILY)
        val done = results.count { !it.date.isBefore(monday) && !it.date.isAfter(today) && HabitStreak.isComplete(daily, it) }
        return done >= target
    }

    /** One or two short sentences: what today's target is and, when there is one, the streak at stake. */
    fun reminderBody(facts: HabitReminderFacts): String {
        val target = when (facts.type) {
            HabitType.SIMPLE -> "A good moment for it. Tap Done when it's finished."
            HabitType.COUNTER -> {
                val unit = facts.unit.ifBlank { "times" }
                if (facts.loggedValue > 0.0) "${number(facts.loggedValue)} of ${number(facts.targetValue)} $unit so far today."
                else "Today's target: ${number(facts.targetValue)} $unit."
            }
            HabitType.DURATION ->
                if (facts.loggedMinutes > 0) "${facts.loggedMinutes} of ${facts.timeTargetMinutes} minutes so far today."
                else "Today's target: ${minutes(facts.timeTargetMinutes)}."
            HabitType.SUBJECT -> {
                val subject = facts.subjectName?.takeIf { it.isNotBlank() } ?: "your subject"
                val unit = facts.subjectUnit?.takeIf { it.isNotBlank() } ?: "pages"
                val total = facts.subjectTotal?.takeIf { it > 0 }
                if (total != null) "Continue $subject — ${facts.subjectPosition} of $total $unit so far."
                else "Continue $subject."
            }
        }
        return if (facts.currentStreak >= 2) "$target Keep your ${facts.currentStreak}-day streak going." else target
    }

    /**
     * The evening check: names the open habits (longest streak first) so the user knows what is left.
     * Returns null when nothing is open, so no notification is shown.
     */
    fun eveningCheck(open: List<HabitReminderFacts>): Pair<String, String>? {
        if (open.isEmpty()) return null
        val ranked = open.sortedByDescending { it.currentStreak }
        val atRisk = ranked.first().takeIf { it.currentStreak >= 3 }
        val title = when {
            atRisk != null -> "Keep your ${atRisk.currentStreak}-day ${atRisk.name} streak"
            open.size == 1 -> "${open.first().name} is still open today"
            else -> "${open.size} habits are still open today"
        }
        val names = ranked.take(3).map { "${it.emoji} ${it.name}".trim() }
        val list = when {
            ranked.size > 3 -> names.joinToString(", ") + " and ${ranked.size - 3} more"
            names.size > 1 -> names.dropLast(1).joinToString(", ") + " and " + names.last()
            else -> names.first()
        }
        val verb = if (ranked.size == 1) "is" else "are"
        return title to "$list $verb still open. There's still time tonight."
    }

    private fun number(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else "%.1f".format(Locale.US, value)

    private fun minutes(value: Int): String = when {
        value < 60 -> if (value == 1) "1 minute" else "$value minutes"
        value % 60 == 0 -> (value / 60).let { if (it == 1) "1 hour" else "$it hours" }
        else -> "${value / 60} h ${value % 60} min"
    }
}
