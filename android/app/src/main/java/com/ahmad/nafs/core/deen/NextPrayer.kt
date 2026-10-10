package com.ahmad.nafs.core.deen

import com.ahmad.nafs.core.records.PrivateRecordStore
import com.ahmad.nafs.core.records.RecordRules
import com.ahmad.nafs.ui.deen.PrayerTimeRules
import java.time.DateTimeException
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** The next of the five daily prayers, taken from saved prayer times. */
data class NextPrayer(
    val prayer: Prayer,
    /** Clock time in the account timezone, as the prayer-times provider gave it. */
    val time: LocalTime,
    val at: ZonedDateTime,
    /** True once today's five have passed and this is tomorrow's Fajr. */
    val isTomorrow: Boolean,
    /** The prayer before this one and when it began; unknown for today's Fajr. */
    val previous: Prayer? = null,
    val previousAt: ZonedDateTime? = null,
) {
    /** Time left until the prayer begins, never negative. */
    fun remaining(now: ZonedDateTime): Duration = Duration.between(now, at).let { if (it.isNegative) Duration.ZERO else it }

    /** How far [now] has moved from the previous prayer towards this one, or null when the previous one is unknown. */
    fun progress(now: ZonedDateTime): Float? {
        val start = previousAt ?: return null
        val total = Duration.between(start, at).seconds.toFloat()
        if (total <= 0f) return null
        return (Duration.between(start, now).seconds / total).coerceIn(0f, 1f)
    }
}

sealed interface NextPrayerLookup {
    data class Found(val next: NextPrayer) : NextPrayerLookup

    /** No area or city has been chosen in Prayer times yet. */
    data object NotSetUp : NextPrayerLookup

    /** A place is saved, but the saved times do not cover now (for example after a few days offline). */
    data object NeedsRefresh : NextPrayerLookup
}

/** Pure selection from the records Prayer times and the prayer reminders save. */
object NextPrayerRules {
    /** Today's times for the chosen area or city; Prayer times also writes every new choice here. */
    const val TODAY_KEY = "prayer-times"

    /** Tomorrow's times for the same place, saved by the prayer reminders and by Prayer times. */
    const val NEXT_KEY = "prayer-times-next"

    fun lookup(todayRecord: JsonObject?, nextRecord: JsonObject?, zone: String, now: ZonedDateTime): NextPrayerLookup {
        val source = todayRecord ?: nextRecord ?: return NextPrayerLookup.NotSetUp
        if (!hasPlace(source)) return NextPrayerLookup.NotSetUp
        val local = try { now.withZoneSameInstant(ZoneId.of(zone)) } catch (_: DateTimeException) { return NextPrayerLookup.NeedsRefresh }
        val records = listOfNotNull(todayRecord, nextRecord)
        val date = local.toLocalDate()
        val today = times(dayRecord(records, source, date, zone))
        val tomorrow = times(dayRecord(records, source, date.plusDays(1), zone))
        return select(local, today, tomorrow)?.let { NextPrayerLookup.Found(it) } ?: NextPrayerLookup.NeedsRefresh
    }

    /**
     * The first prayer after [now]: one of today's when any is left, otherwise tomorrow's Fajr. Tomorrow's Fajr is
     * only used once today's Isha is known to have passed, and a missing time is never invented.
     */
    fun select(now: ZonedDateTime, today: Map<Prayer, LocalTime>, tomorrow: Map<Prayer, LocalTime>): NextPrayer? {
        val todayAt = instants(now.toLocalDate(), now.zone, today)
        val index = todayAt.indexOfFirst { it.second.isAfter(now) }
        if (index >= 0) {
            val (prayer, at) = todayAt[index]
            val previous = todayAt.getOrNull(index - 1)
            return NextPrayer(prayer, today.getValue(prayer), at, isTomorrow = false, previous = previous?.first, previousAt = previous?.second)
        }
        val isha = todayAt.lastOrNull()?.takeIf { it.first == Prayer.ISHA } ?: return null
        val fajr = tomorrow[Prayer.FAJR] ?: return null
        val fajrAt = now.toLocalDate().plusDays(1).atTime(fajr).atZone(now.zone)
        if (!fajrAt.isAfter(now)) return null
        return NextPrayer(Prayer.FAJR, fajr, fajrAt, isTomorrow = true, previous = Prayer.ISHA, previousAt = isha.second)
    }

    /** The saved day for [date] in [zone], calculated for the same place and method as [source]. */
    fun dayRecord(records: List<JsonObject>, source: JsonObject, date: LocalDate, zone: String): JsonObject? = records.firstOrNull {
        RecordRules.text(it, "date") == date.toString() && RecordRules.text(it, "zone") == zone && sameSource(it, source)
    }

    /** Same area or city and the same calculation settings, as the reminders require before reusing a saved day. */
    fun sameSource(a: JsonObject, b: JsonObject): Boolean = Coordinates.from(a) == Coordinates.from(b) &&
        listOf("city", "country", "method", "hanafi").all { RecordRules.text(a, it) == RecordRules.text(b, it) }

    fun hasPlace(record: JsonObject?): Boolean = Coordinates.from(record) != null ||
        (RecordRules.text(record, "city").isNotBlank() && RecordRules.text(record, "country").isNotBlank())

    /** The readable times of a saved day; a malformed time is left out rather than guessed. */
    fun times(record: JsonObject?): Map<Prayer, LocalTime> {
        val times = record?.get("times") as? JsonObject ?: return emptyMap()
        return Prayer.entries.mapNotNull { prayer ->
            val raw = (times[prayer.label] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            runCatching { PrayerTimeRules.time(raw) }.getOrNull()?.let { prayer to it }
        }.toMap()
    }

    /** Prayers in order with their start; a time earlier than the one before it (Isha after midnight) is the next day. */
    private fun instants(date: LocalDate, zone: ZoneId, times: Map<Prayer, LocalTime>): List<Pair<Prayer, ZonedDateTime>> {
        var last: ZonedDateTime? = null
        return Prayer.entries.mapNotNull { prayer ->
            val time = times[prayer] ?: return@mapNotNull null
            var at = date.atTime(time).atZone(zone)
            if (last?.let { !at.isAfter(it) } == true) at = date.plusDays(1).atTime(time).atZone(zone)
            last = at
            prayer to at
        }
    }
}

/** Reads the next prayer from the encrypted copies of today's and tomorrow's prayer times. Makes no network calls. */
class NextPrayerReader @Inject constructor(private val store: PrivateRecordStore) {
    suspend fun read(userId: String, zone: String, now: ZonedDateTime): NextPrayer? =
        (lookup(userId, zone, now) as? NextPrayerLookup.Found)?.next

    /** Like [read], but says whether prayer times still need to be set up or refreshed. */
    suspend fun lookup(userId: String, zone: String, now: ZonedDateTime): NextPrayerLookup {
        val (today, next) = try {
            store.read(userId, NextPrayerRules.TODAY_KEY) to store.read(userId, NextPrayerRules.NEXT_KEY)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            // An unreadable copy is fixed the same way as an outdated one: Prayer times fetches and saves again.
            return NextPrayerLookup.NeedsRefresh
        }
        return NextPrayerRules.lookup(today, next, zone, now)
    }
}
