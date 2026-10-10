package com.ahmad.nafs.core.letters

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LetterDatesTest {
    private val today = LocalDate.parse("2026-10-10")

    @Test fun `picker millis round-trip through UTC midnight`() {
        val date = LocalDate.parse("2027-04-10")
        assertEquals(1_807_315_200_000L, LetterDates.toPickerMillis(date))
        assertEquals(date, LetterDates.fromPickerMillis(LetterDates.toPickerMillis(date)))
        // Any time during that UTC day is still that calendar day.
        assertEquals(date, LetterDates.fromPickerMillis(LetterDates.toPickerMillis(date) + 23 * 3_600_000L))
    }

    @Test fun `only days after the account today can be chosen`() {
        assertFalse(LetterDates.selectable(today, today))
        assertFalse(LetterDates.selectable(today.minusDays(1), today))
        assertTrue(LetterDates.selectable(today.plusDays(1), today))
        assertFalse(LetterDates.selectable(today.plusYears(51), today))
        assertFalse(LetterDates.selectableYear(2025, today))
        assertTrue(LetterDates.selectableYear(2026, today))
    }

    @Test fun `the picker opens on a valid choice or tomorrow`() {
        assertEquals(LocalDate.parse("2027-04-10"), LetterDates.initial("2027-04-10", today))
        assertEquals(today.plusDays(1), LetterDates.initial("2026-10-01", today))
        assertEquals(today.plusDays(1), LetterDates.initial("not a date", today))
    }
}
