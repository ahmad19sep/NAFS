package com.ahmad.nafs.core.records

import com.ahmad.nafs.ui.deen.PrayerTimeRules
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class PrayerTimeRulesTest {
    @Test fun acceptsProviderTimezoneSuffixAndRejectsInvalidTime() {
        assertEquals(LocalTime.of(5, 12), PrayerTimeRules.time("05:12 (PKT)"))
        assertTrue(runCatching { PrayerTimeRules.time("25:00") }.isFailure)
    }
    @Test fun countdownDoesNotFabricateTomorrowsTime() {
        val times = mapOf("Fajr" to "05:00", "Dhuhr" to "12:00", "Asr" to "16:00", "Maghrib" to "18:00", "Isha" to "20:00")
        assertEquals("Dhuhr", PrayerTimeRules.next(times, LocalTime.of(5, 0))?.first)
        assertNull(PrayerTimeRules.next(times, LocalTime.of(21, 0)))
    }
}
