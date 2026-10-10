package com.ahmad.nafs.domain.reminders

import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class PrayerTrigger(val prayer: String, val date: LocalDate, val time: LocalTime, val at: Instant)

/** Account-timezone timing and wording for prayer, end-of-day and weekly notifications. */
object ScheduledReminderRules {
    val prayers = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
    val dailySummaryTime: LocalTime = LocalTime.of(21, 30)
    val weeklyReportDay: DayOfWeek = DayOfWeek.SUNDAY
    val weeklyReportTime: LocalTime = LocalTime.of(21, 45)

    /** The next [at] in the account zone strictly after [now]. A DST gap moves it forward, never skips the day. */
    fun nextDaily(now: Instant, zone: String, at: LocalTime = dailySummaryTime): Instant {
        val id = ZoneId.of(zone)
        val today = now.atZone(id).toLocalDate()
        val candidate = today.atTime(at).atZone(id).toInstant()
        return if (candidate.isAfter(now)) candidate else today.plusDays(1).atTime(at).atZone(id).toInstant()
    }

    /** Weeks run Monday to Sunday, so Sunday evening closes the current report week. */
    fun nextWeekly(now: Instant, zone: String, day: DayOfWeek = weeklyReportDay, at: LocalTime = weeklyReportTime): Instant {
        val id = ZoneId.of(zone)
        val date = now.atZone(id).toLocalDate().with(TemporalAdjusters.nextOrSame(day))
        val candidate = date.atTime(at).atZone(id).toInstant()
        return if (candidate.isAfter(now)) candidate else date.plusWeeks(1).atTime(at).atZone(id).toInstant()
    }

    /** Future prayer starts for one account date; malformed or missing times are skipped. */
    fun prayerTriggers(date: LocalDate, times: Map<String, String>, zone: String, now: Instant): List<PrayerTrigger> {
        val id = try { ZoneId.of(zone) } catch (_: DateTimeException) { return emptyList() }
        return prayers.mapNotNull { prayer ->
            val raw = times[prayer] ?: return@mapNotNull null
            val time = runCatching { LocalTime.parse(raw.trim().take(5)) }.getOrNull() ?: return@mapNotNull null
            val at = date.atTime(time).atZone(id).toInstant()
            if (at.isAfter(now)) PrayerTrigger(prayer, date, time, at) else null
        }
    }

    /**
     * A null count is unknown (its source failed or was only an offline copy) and is left out.
     * With [incomplete], the summary never claims everything is done or that nothing was planned.
     */
    fun dailySummary(tasksDone: Int?, tasksTotal: Int?, habitsDone: Int?, habitsTotal: Int?, prayersDone: Int?, incomplete: Boolean = false): String {
        val parts = buildList {
            if (tasksTotal != null && tasksTotal > 0) add("Tasks ${tasksDone ?: 0}/$tasksTotal")
            if (habitsTotal != null && habitsTotal > 0) add("Habits ${habitsDone ?: 0}/$habitsTotal")
            prayersDone?.let { add("Prayed $it/5") }
        }
        val remaining = ((tasksTotal ?: 0) - (tasksDone ?: 0)).coerceAtLeast(0) + ((habitsTotal ?: 0) - (habitsDone ?: 0)).coerceAtLeast(0)
        if (parts.isEmpty()) return if (incomplete) "Open NAFS to review today and plan tomorrow." else "Nothing was planned for today. Open NAFS to plan tomorrow."
        val close = when {
            remaining > 0 -> "$remaining still open — there is time to finish one."
            incomplete -> "Some records could not be loaded. Open NAFS for the full picture."
            else -> "Everything planned is done."
        }
        return parts.joinToString(" · ") + ". " + close
    }

    /** Values come from the canonical weekly report; a null or blank value is unknown and is left out, not shown as zero. */
    fun weeklySummary(
        daysLogged: Int, daysElapsed: String, avgScore: String?, tasksDone: String, tasksTotal: String?,
        habitPct: String?, prayerPoints: String?, prayerMax: String?, incomplete: Boolean = false,
    ): String {
        if (daysLogged <= 0 && !incomplete) return "No days were recorded this week. Open NAFS to see the full report."
        val parts = buildList {
            avgScore?.takeIf { it.isNotBlank() }?.let { add("Score $it%") }
            if (!tasksTotal.isNullOrBlank() && tasksTotal != "0") add("Tasks $tasksDone/$tasksTotal")
            habitPct?.takeIf { it.isNotBlank() }?.let { add("Habits $it%") }
            if (!prayerPoints.isNullOrBlank() && !prayerMax.isNullOrBlank()) add("Prayer points $prayerPoints/$prayerMax")
        }
        val days = ("Recorded days $daysLogged" + (daysElapsed.takeIf { it.isNotBlank() }?.let { "/$it" } ?: "")).takeUnless { incomplete }
        if (incomplete) return (parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")?.plus(". ") ?: "") + "Some records could not be loaded. Tap for the full report."
        return (parts + listOfNotNull(days)).joinToString(" · ") + ". Tap for the full report."
    }
}
