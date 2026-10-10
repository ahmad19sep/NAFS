package com.ahmad.nafs.core.letters

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Revisit dates are plain calendar days in the account timezone. The Material date picker speaks in UTC
 * midnight milliseconds, so conversions go through UTC and never through the device timezone.
 */
object LetterDates {
    const val MAX_YEARS_AHEAD = 50

    fun toPickerMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun fromPickerMillis(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

    /** Only days after the account's today can be chosen, up to [MAX_YEARS_AHEAD] years out. */
    fun selectable(date: LocalDate, today: LocalDate): Boolean =
        date.isAfter(today) && date.year <= today.year + MAX_YEARS_AHEAD

    fun selectableYear(year: Int, today: LocalDate): Boolean = year in today.year..(today.year + MAX_YEARS_AHEAD)

    /** The day the picker opens on: the current choice when it is still selectable, otherwise tomorrow. */
    fun initial(targetDate: String, today: LocalDate): LocalDate =
        runCatching { LocalDate.parse(targetDate) }.getOrNull()?.takeIf { selectable(it, today) } ?: today.plusDays(1)
}
