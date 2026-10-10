package com.ahmad.nafs.core.deen

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.serialization.Serializable

/** One day's five prayer statuses (0 missed, 1 alone, 2 jamaat), as history needs them. Extras are not loaded. */
@Serializable
data class PrayerHistoryDay(
    val date: String,
    val fajr: Int = 0,
    val dhuhr: Int = 0,
    val asr: Int = 0,
    val maghrib: Int = 0,
    val isha: Int = 0,
) {
    fun status(prayer: Prayer): Int = when (prayer) {
        Prayer.FAJR -> fajr
        Prayer.DHUHR -> dhuhr
        Prayer.ASR -> asr
        Prayer.MAGHRIB -> maghrib
        Prayer.ISHA -> isha
    }

    val prayedCount: Int get() = Prayer.entries.count { status(it) > 0 }
    val jamaatCount: Int get() = Prayer.entries.count { status(it) == 2 }

    /** All five prayed, alone or in jamaat: the day keeps the streak going. */
    val isComplete: Boolean get() = prayedCount == Prayer.entries.size

    companion object {
        fun of(log: PrayerLog) = PrayerHistoryDay(log.date, log.fajr, log.dhuhr, log.asr, log.maghrib, log.isha)
    }
}

sealed interface DeenHistoryResult {
    /**
     * Saved days between [from] and [through]; a date in that range without an entry has no record. [through] is the
     * requested end online, and the last day the saved copy covers when [isOffline].
     */
    data class Success(
        val days: List<PrayerHistoryDay>,
        val from: LocalDate,
        val through: LocalDate,
        val isOffline: Boolean,
    ) : DeenHistoryResult

    data class Failure(val message: String) : DeenHistoryResult
}

/** One heatmap square. [prayed] is null when the day has no record. */
data class PrayerHistoryCell(val date: LocalDate, val prayed: Int?, val jamaat: Int = 0)

/** How many of [days] days had [prayer] prayed. */
data class PrayerTally(val prayer: Prayer, val prayed: Int, val days: Int)

data class PrayerHistorySummary(
    /** The last day covered: today online, or the day a saved copy was made. */
    val through: LocalDate,
    val currentStreak: Int,
    /** The streak runs back to the first loaded day, so it may be longer than shown. */
    val streakMayBeLonger: Boolean,
    val longestStreak: Int,
    /** The last [PrayerHistoryRules.WINDOW_DAYS] days, oldest first. */
    val cells: List<PrayerHistoryCell>,
    val tallies: List<PrayerTally>,
    val completeDays: Int,
    val recordedDays: Int,
)

object PrayerHistoryRules {
    const val WINDOW_DAYS = 30

    /** How far back streaks are counted. */
    const val LOOKBACK_DAYS = 365L

    /**
     * Consecutive days with all five prayed, ending on [through]. That day only counts once it is complete, so an
     * unfinished today never breaks the streak (as in the web app).
     */
    fun currentStreak(byDate: Map<LocalDate, PrayerHistoryDay>, through: LocalDate, from: LocalDate): Int {
        var date = if (byDate[through]?.isComplete == true) through else through.minusDays(1)
        var streak = 0
        while (!date.isBefore(from) && byDate[date]?.isComplete == true) {
            streak++
            date = date.minusDays(1)
        }
        return streak
    }

    fun longestStreak(byDate: Map<LocalDate, PrayerHistoryDay>, from: LocalDate, through: LocalDate): Int {
        var longest = 0
        var run = 0
        var date = from
        while (!date.isAfter(through)) {
            run = if (byDate[date]?.isComplete == true) run + 1 else 0
            longest = maxOf(longest, run)
            date = date.plusDays(1)
        }
        return longest
    }

    fun summary(days: Collection<PrayerHistoryDay>, from: LocalDate, through: LocalDate, windowDays: Int = WINDOW_DAYS): PrayerHistorySummary {
        val byDate = days.mapNotNull { day -> runCatching { LocalDate.parse(day.date) }.getOrNull()?.let { it to day } }
            .filter { (date, _) -> !date.isBefore(from) && !date.isAfter(through) }
            .toMap()
        val current = currentStreak(byDate, through, from)
        val window = (windowDays - 1 downTo 0).map { through.minusDays(it.toLong()) }
        val covered = window.filter { !it.isBefore(from) }
        val cells = window.map { date -> byDate[date].let { PrayerHistoryCell(date, it?.prayedCount, it?.jamaatCount ?: 0) } }
        val firstCounted = if (byDate[through]?.isComplete == true) through else through.minusDays(1)
        return PrayerHistorySummary(
            through = through,
            currentStreak = current,
            streakMayBeLonger = current > 0 && ChronoUnit.DAYS.between(from, firstCounted) + 1 == current.toLong(),
            longestStreak = maxOf(current, longestStreak(byDate, from, through)),
            cells = cells,
            tallies = Prayer.entries.map { prayer ->
                PrayerTally(prayer, covered.count { date -> (byDate[date]?.status(prayer) ?: 0) > 0 }, covered.size)
            },
            completeDays = covered.count { byDate[it]?.isComplete == true },
            recordedDays = covered.count { it in byDate },
        )
    }
}
