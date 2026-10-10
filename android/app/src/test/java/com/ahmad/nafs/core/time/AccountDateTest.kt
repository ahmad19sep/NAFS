package com.ahmad.nafs.core.time

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountDateTest {
    private val fixedClock = Clock.fixed(
        Instant.parse("2026-10-09T20:30:00Z"),
        ZoneOffset.UTC,
    )

    @Test
    fun accountTimezoneDeterminesTheCalendarDay() {
        assertEquals(LocalDate.parse("2026-10-10"), AccountDate.today("Asia/Karachi", fixedClock))
        assertEquals(LocalDate.parse("2026-10-09"), AccountDate.today("America/Los_Angeles", fixedClock))
    }

    @Test
    fun shiftingUsesPureCalendarArithmetic() {
        assertEquals(
            LocalDate.parse("2028-02-29"),
            AccountDate.shift(LocalDate.parse("2028-02-28"), 1),
        )
        assertEquals(
            LocalDate.parse("2025-12-31"),
            AccountDate.shift(LocalDate.parse("2026-01-01"), -1),
        )
    }
}

