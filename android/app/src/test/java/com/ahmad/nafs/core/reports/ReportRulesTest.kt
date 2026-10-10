package com.ahmad.nafs.core.reports

import com.ahmad.nafs.ui.reports.ReportText
import com.ahmad.nafs.ui.reports.ReportTextKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal fun savedReport(index: Int = 1, user: String = "user", type: String = "growth") = AiReport(
    id = "00000000-0000-4000-8000-${index.toString().padStart(12, '0')}", userId = user, type = type,
    contentMd = "**Your progress**\n\n- Keep going", generatedAt = "2026-10-10T22:30:00.123456+00:00", modelUsed = "saved-model",
)

class ReportRulesTest {
    @Test fun `timestamp ties use UUID ordering and preserve the exact paging boundary`() {
        val rows = (1..21).map { savedReport(it).summary }
        val page = ReportRules.page("user", ReportFilter.ALL, null, rows)
        assertEquals((21 downTo 2).map { savedReport(it).id }, page.reports.map { it.id })
        assertEquals(ReportCursor(rows[1].generatedAt, rows[1].id), page.next)
        val boundary = checkNotNull(page.next)
        assertTrue(ReportRules.isAfterCursor(rows[0], boundary))
        assertFalse(ReportRules.isAfterCursor(rows[1], boundary))
        assertFalse(ReportRules.isAfterCursor(rows[2], boundary))
    }

    @Test fun `paging compares instants and retains sub millisecond precision`() {
        val report = savedReport().summary.copy(generatedAt = "2026-10-11T03:30:00.123455+05:00")
        val cursor = ReportCursor("2026-10-10T22:30:00.123456Z", savedReport(2).id)
        assertTrue(ReportRules.isAfterCursor(report, cursor))
        assertFalse(ReportRules.isAfterCursor(report.copy(generatedAt = "2026-10-10T22:30:00.123457Z"), cursor))
    }

    @Test fun `unknown future report types remain visible in the all archive`() {
        val report = savedReport(type = "monthly_reflection")
        assertEquals("Monthly reflection", report.summary.title)
        assertEquals(listOf(report.summary), ReportRules.page("user", ReportFilter.ALL, null, listOf(report.summary)).reports)
        assertTrue(runCatching { ReportRules.page("user", ReportFilter.GROWTH, null, listOf(report.summary)) }.isFailure)
    }

    @Test fun `invalid or foreign rows cannot enter a cached archive`() {
        val report = savedReport().summary
        listOf(report.copy(userId = "another-user"), report.copy(id = "not-a-uuid"),
            report.copy(generatedAt = "not-a-date"), report.copy(weekStart = "2026-02-30")).forEach {
            assertFalse(ReportRules.validSummary("user", it))
        }
        assertTrue(runCatching { ReportRules.page("user", ReportFilter.ALL, null, listOf(report, report)) }.isFailure)
        assertFalse(ReportRules.validSnapshot("user", ReportSnapshot(details = listOf(savedReport(user = "other")))))
        assertFalse(ReportRules.validReport("user", savedReport().copy(contentMd = "x".repeat(180_001))))
    }

    @Test fun `written dates follow the account zone while week starts remain calendar dates`() {
        val summary = savedReport().summary.copy(weekStart = "2026-10-05")
        assertTrue(summary.writtenAt("Asia/Karachi").contains("11"))
        assertTrue(summary.writtenAt("America/Los_Angeles").contains("10"))
        assertTrue(summary.weekLabel()!!.contains("5"))
        assertEquals(summary.writtenAt("UTC"), summary.writtenAt("invalid-zone"))
    }

    @Test fun `markdown handles headings lists blank lines and literal unsafe markup`() {
        val lines = ReportText.lines("## **Progress**\r\n\r\n- First\n2. Second\n> A quote\n<script>literal</script>")
        assertEquals(listOf(ReportTextKind.HEADING, ReportTextKind.SPACE, ReportTextKind.BULLET, ReportTextKind.BULLET,
            ReportTextKind.QUOTE, ReportTextKind.PARAGRAPH), lines.map { it.kind })
        assertEquals("2.", lines[3].marker)
        assertEquals("<script>literal</script>", lines.last().text)
        assertEquals(listOf("Before ", "bold", " after **unfinished"), ReportText.runs("Before **bold** after **unfinished").map { it.text })
        assertEquals(listOf(false, true, false), ReportText.runs("Before **bold** after **unfinished").map { it.bold })
    }

    @Test fun `code fences retain their content and do not reinterpret markdown`() {
        val lines = ReportText.lines("```text\n**literal**\n# still code\n```\n# Heading")
        assertEquals(listOf(ReportTextKind.CODE, ReportTextKind.CODE, ReportTextKind.CODE, ReportTextKind.CODE, ReportTextKind.HEADING), lines.map { it.kind })
        assertEquals("**literal**", lines[1].text)
    }
}
