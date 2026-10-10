package com.ahmad.nafs.domain.reminders

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduledReminderRulesTest {
    @Test fun `daily summary fires today in the account zone, or tomorrow once passed`() {
        // 21:30 in Karachi (UTC+5) is 16:30 UTC.
        assertEquals(Instant.parse("2026-10-10T16:30:00Z"),
            ScheduledReminderRules.nextDaily(Instant.parse("2026-10-10T10:00:00Z"), "Asia/Karachi"))
        assertEquals(Instant.parse("2026-10-11T16:30:00Z"),
            ScheduledReminderRules.nextDaily(Instant.parse("2026-10-10T16:30:00Z"), "Asia/Karachi"))
    }

    @Test fun `daily summary uses the account date, not the UTC date`() {
        // 20:00 UTC on the 10th is already 01:00 on the 11th in Karachi.
        assertEquals(Instant.parse("2026-10-11T16:30:00Z"),
            ScheduledReminderRules.nextDaily(Instant.parse("2026-10-10T20:00:00Z"), "Asia/Karachi"))
    }

    @Test fun `daily summary keeps local wall time across a DST change`() {
        // London moves from BST (UTC+1) to GMT on 2026-10-25.
        assertEquals(Instant.parse("2026-10-24T20:30:00Z"),
            ScheduledReminderRules.nextDaily(Instant.parse("2026-10-24T12:00:00Z"), "Europe/London"))
        assertEquals(Instant.parse("2026-10-25T21:30:00Z"),
            ScheduledReminderRules.nextDaily(Instant.parse("2026-10-25T12:00:00Z"), "Europe/London"))
    }

    @Test fun `weekly report fires on Sunday evening and moves to the next Sunday once passed`() {
        // 2026-10-10 is a Saturday; Sunday 21:45 in Karachi is 16:45 UTC.
        assertEquals(Instant.parse("2026-10-11T16:45:00Z"),
            ScheduledReminderRules.nextWeekly(Instant.parse("2026-10-10T09:00:00Z"), "Asia/Karachi"))
        assertEquals(Instant.parse("2026-10-11T16:45:00Z"),
            ScheduledReminderRules.nextWeekly(Instant.parse("2026-10-11T16:00:00Z"), "Asia/Karachi"))
        assertEquals(Instant.parse("2026-10-18T16:45:00Z"),
            ScheduledReminderRules.nextWeekly(Instant.parse("2026-10-11T16:45:00Z"), "Asia/Karachi"))
    }

    @Test fun `prayer triggers keep only future, well-formed prayer starts in account order`() {
        val date = LocalDate.parse("2026-10-10")
        val times = mapOf("Fajr" to "04:55", "Dhuhr" to "11:58 (PKT)", "Asr" to "15:40", "Maghrib" to "bad", "Isha" to "19:10")
        val triggers = ScheduledReminderRules.prayerTriggers(date, times, "Asia/Karachi", Instant.parse("2026-10-10T07:00:00Z"))
        // 07:00 UTC is 12:00 in Karachi: Fajr and Dhuhr have passed, Maghrib is unreadable.
        assertEquals(listOf("Asr", "Isha"), triggers.map(PrayerTrigger::prayer))
        assertEquals(Instant.parse("2026-10-10T10:40:00Z"), triggers.first().at)
        assertEquals(LocalTime.of(15, 40), triggers.first().time)
    }

    @Test fun `an invalid zone schedules no prayers`() {
        assertTrue(ScheduledReminderRules.prayerTriggers(LocalDate.parse("2026-10-10"), mapOf("Asr" to "15:40"), "Not/AZone", Instant.EPOCH).isEmpty())
    }

    @Test fun `daily summary reports counts and what is still open`() {
        assertEquals("Tasks 3/5 · Habits 4/8 · Prayed 4/5. 6 still open — there is time to finish one.",
            ScheduledReminderRules.dailySummary(3, 5, 4, 8, 4))
        assertEquals("Tasks 2/2. Everything planned is done.", ScheduledReminderRules.dailySummary(2, 2, 0, 0, null))
        assertEquals("Nothing was planned for today. Open NAFS to plan tomorrow.", ScheduledReminderRules.dailySummary(0, 0, 0, 0, null))
    }

    @Test fun `a source that failed to load is unknown, never zero or done`() {
        // Habits failed: tasks are all done, but the summary must not claim everything is.
        assertEquals("Tasks 2/2. Some records could not be loaded. Open NAFS for the full picture.",
            ScheduledReminderRules.dailySummary(2, 2, null, null, null, incomplete = true))
        assertEquals("Open NAFS to review today and plan tomorrow.",
            ScheduledReminderRules.dailySummary(0, 0, null, null, null, incomplete = true))
        // No prayer record for the day: prayers are left out rather than shown as 0/5.
        assertEquals("Habits 1/2. 1 still open — there is time to finish one.", ScheduledReminderRules.dailySummary(0, 0, 1, 2, null))
    }

    @Test fun `weekly summary leaves unknown values out instead of showing zero`() {
        assertEquals("Score 72% · Tasks 18/25 · Habits 64% · Prayer points 52/70 · Recorded days 6/7. Tap for the full report.",
            ScheduledReminderRules.weeklySummary(6, "7", "72", "18", "25", "64", "52", "70"))
        assertEquals("Habits 50% · Recorded days 3/7. Tap for the full report.",
            ScheduledReminderRules.weeklySummary(3, "7", "", "0", "0", "50", null, null))
        assertEquals("No days were recorded this week. Open NAFS to see the full report.",
            ScheduledReminderRules.weeklySummary(0, "7", "", "", "", "", null, null))
    }

    @Test fun `weekly summary with failed sources says so and drops affected figures`() {
        // Recorded days are counted from every source, so a failed source makes that figure unknown too.
        assertEquals("Tasks 4/6. Some records could not be loaded. Tap for the full report.",
            ScheduledReminderRules.weeklySummary(0, "7", null, "4", "6", null, null, null, incomplete = true))
        assertEquals("Some records could not be loaded. Tap for the full report.",
            ScheduledReminderRules.weeklySummary(0, "7", null, "", null, null, null, null, incomplete = true))
    }
}
