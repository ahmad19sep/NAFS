package com.ahmad.nafs.core.profile

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** One choice in the timezone picker, described at a given moment (offsets change with daylight saving). */
data class TimezoneOption(
    val id: String,
    val city: String,
    /** "Asia", or "America · Argentina" for three-part ids; empty for ids such as "UTC". */
    val region: String,
    val offsetSeconds: Int,
    /** "UTC+5", "UTC−3:30" or "UTC". */
    val offsetLabel: String,
    val localTime: String,
) {
    /** "Karachi, UTC+5" */
    val shortLabel: String get() = "$city, $offsetLabel"
}

/** The timezones NAFS offers, how they're described, and how the picker's search matches them. */
object TimezoneCatalog {
    private val regions = setOf("Africa", "America", "Antarctica", "Arctic", "Asia", "Atlantic", "Australia", "Europe", "Indian", "Pacific")
    private val segment = Regex("[A-Za-z][A-Za-z0-9_\\-]*")
    private val whitespace = Regex("\\s+")

    /**
     * Old names the timezone database keeps as aliases of current ones (Calcutta for Kolkata, Kiev for Kyiv).
     * Listing both would show the same place twice. A profile that already uses one still sees it.
     */
    private val legacyAliases = setOf(
        "Africa/Asmera", "Africa/Timbuktu", "America/Argentina/ComodRivadavia", "America/Atka", "America/Buenos_Aires",
        "America/Catamarca", "America/Coral_Harbour", "America/Cordoba", "America/Ensenada", "America/Fort_Wayne",
        "America/Godthab", "America/Indianapolis", "America/Jujuy", "America/Knox_IN", "America/Louisville",
        "America/Mendoza", "America/Montreal", "America/Porto_Acre", "America/Rosario", "America/Santa_Isabel",
        "America/Shiprock", "America/Virgin", "Antarctica/South_Pole", "Asia/Ashkhabad", "Asia/Calcutta",
        "Asia/Chongqing", "Asia/Chungking", "Asia/Dacca", "Asia/Harbin", "Asia/Istanbul", "Asia/Kashgar", "Asia/Katmandu",
        "Asia/Macao", "Asia/Rangoon", "Asia/Saigon", "Asia/Tel_Aviv", "Asia/Thimbu", "Asia/Ujung_Pandang",
        "Asia/Ulan_Bator", "Atlantic/Faeroe", "Atlantic/Jan_Mayen", "Australia/ACT", "Australia/Canberra",
        "Australia/LHI", "Australia/NSW", "Australia/North", "Australia/Queensland", "Australia/South",
        "Australia/Tasmania", "Australia/Victoria", "Australia/West", "Australia/Yancowinna", "Europe/Belfast",
        "Europe/Kiev", "Europe/Nicosia", "Europe/Tiraspol", "Pacific/Enderbury", "Pacific/Johnston", "Pacific/Ponape",
        "Pacific/Samoa", "Pacific/Truk", "Pacific/Yap",
    )

    fun isValid(id: String): Boolean = id.isNotBlank() && runCatching { ZoneId.of(id) }.isSuccess

    /** Region/City ids such as "Asia/Karachi" or "America/Argentina/Buenos_Aires", not "UTC", "Etc/GMT+5" or "US/Eastern". */
    fun isRegionCity(id: String): Boolean {
        val parts = id.split('/')
        return parts.size in 2..3 && parts[0] in regions && parts.drop(1).all(segment::matches)
    }

    /**
     * Every Region/City zone, sorted west to east and then by city, plus [alwaysInclude] (the profile's and the
     * phone's zones) even when they fall outside that set.
     */
    fun options(
        now: Instant,
        use24Hour: Boolean,
        alwaysInclude: Collection<String> = emptyList(),
        locale: Locale = Locale.getDefault(),
        available: Set<String> = ZoneId.getAvailableZoneIds(),
    ): List<TimezoneOption> {
        val ids = available.filterTo(mutableSetOf()) { isRegionCity(it) && it !in legacyAliases }
        alwaysInclude.filterTo(ids, ::isValid)
        return ids.mapNotNull { option(it, now, use24Hour, locale) }
            .sortedWith(compareBy<TimezoneOption>({ it.offsetSeconds }, { it.city }, { it.id }))
    }

    fun option(id: String, now: Instant, use24Hour: Boolean, locale: Locale = Locale.getDefault()): TimezoneOption? {
        val zone = runCatching { ZoneId.of(id) }.getOrNull() ?: return null
        val offset = zone.rules.getOffset(now).totalSeconds
        val clock = DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a", locale)
        return TimezoneOption(
            id = id,
            city = cityName(id),
            region = regionName(id),
            offsetSeconds = offset,
            offsetLabel = offsetLabel(offset),
            localTime = clock.format(now.atZone(zone)),
        )
    }

    fun cityName(id: String): String = id.substringAfterLast('/').replace('_', ' ')

    private fun regionName(id: String): String = id.split('/').dropLast(1).joinToString(" · ") { it.replace('_', ' ') }

    fun offsetLabel(totalSeconds: Int): String {
        if (totalSeconds == 0) return "UTC"
        val sign = if (totalSeconds < 0) "−" else "+"
        val seconds = abs(totalSeconds)
        val hours = seconds / 3600
        val minutes = seconds % 3600 / 60
        return if (minutes == 0) "UTC$sign$hours" else "UTC$sign$hours:${minutes.toString().padStart(2, '0')}"
    }

    /**
     * Options matching every word of [query] in the city, region, id or offset ("lahore", "europe", "new york",
     * "+5:30"). Cities that start with the query come first; otherwise the west-to-east order is kept.
     */
    fun search(options: List<TimezoneOption>, query: String): List<TimezoneOption> {
        val normalizedQuery = normalize(query)
        val words = normalizedQuery.split(' ').filter(String::isNotEmpty)
        if (words.isEmpty()) return options
        return options.mapNotNull { option ->
            val haystack = searchText(option)
            if (words.any { it !in haystack }) return@mapNotNull null
            val city = normalize(option.city)
            val rank = when {
                city.startsWith(normalizedQuery) -> 0
                city.split(' ').any { it.startsWith(words.first()) } -> 1
                haystack.split(' ').any { it.startsWith(words.first()) } -> 2
                else -> 3
            }
            rank to option
        }.sortedBy { it.first }.map { it.second }
    }

    private fun searchText(option: TimezoneOption): String {
        val offset = normalize(option.offsetLabel)
        return normalize("${option.id} ${option.city} ${option.region}") + " $offset ${offset.replace("utc", "gmt")}"
    }

    private fun normalize(text: String): String = text.lowercase(Locale.ROOT)
        .replace('_', ' ').replace('/', ' ').replace('·', ' ').replace('−', '-')
        .replace(whitespace, " ").trim()
}
