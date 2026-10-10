package com.ahmad.nafs.ui.progress

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.*
import org.junit.Test

class ProgressTextTest {
    private fun response() = Json.parseToJsonElement("""{"timezone":"Asia/Karachi","report":{"label":"This week","start":"2026-10-05","end":"2026-10-11","days_logged":0,"days_elapsed":6,"deenEnabled":false,"quran_pages":12,"health":{"sleep":{"avg_hours":7.5},"meals":{"logged":3}},"days":[]},"history":{"overall":[{"date":"2026-10-10","earned":0,"max":0,"pct":0,"coverage":"pending"},{"date":"2026-10-09","earned":0,"max":0,"pct":0,"coverage":"unknown"}]}}""") as JsonObject
    @Test fun historyExportsPreservePendingAndUnknownCoverage() {
        val lines = ProgressText.lines(response(), history = true)
        assertTrue(lines.contains("2026-10-10: Pending"))
        assertTrue(lines.contains("2026-10-09: Unknown"))
        assertFalse(lines.any { it.contains("0/0") })
    }
    @Test fun reportsRetainNestedHealthAndHideDisabledFaithFields() {
        val lines = ProgressText.lines(response())
        assertTrue(lines.contains("Sleep: Avg hours: 7.5"))
        assertTrue(lines.contains("Meals: Logged: 3"))
        assertFalse(lines.any { it.contains("Quran pages") || it.contains("Average score") })
        assertTrue(lines.contains("No recorded data for this period."))
    }
}
