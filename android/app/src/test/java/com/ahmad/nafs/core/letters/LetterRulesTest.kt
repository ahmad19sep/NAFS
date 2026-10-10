package com.ahmad.nafs.core.letters

import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal val letterToday: LocalDate = LocalDate.of(2026, 10, 10)
internal fun letterIntent(index: Int = 1, user: String = "user") = LetterCreateIntent(
    "00000000-0000-4000-8000-${index.toString().padStart(12, '0')}", user, "Remember why you started.", "2027-04-10",
)
internal fun savedLetter(index: Int = 1, user: String = "user") = letterIntent(index, user).let {
    FutureLetter(it.id, it.userId, it.content, "2026-10-10T22:30:00.123456+00:00", it.targetDate)
}

class LetterRulesTest {
    @Test fun `creation payload contains only author controlled fields`() {
        val payload = Json.encodeToJsonElement(letterIntent()).jsonObject
        assertEquals(setOf("id", "user_id", "content", "target_deliver_date"), payload.keys)
        assertEquals("Remember why you started.", letterIntent().draft().copy(content = "  Remember why you started.  ").intent().content)
    }

    @Test fun `new letter requires real content and a future account calendar day`() {
        val draft = letterIntent().draft()
        assertNull(LetterRules.error(draft, letterToday))
        assertTrue(LetterRules.error(draft.copy(content = "   "), letterToday)!!.contains("Write"))
        assertTrue(LetterRules.error(draft.copy(targetDate = "2026-02-30"), letterToday)!!.contains("valid date"))
        assertTrue(LetterRules.error(draft.copy(targetDate = "2026-10-10"), letterToday)!!.contains("after today"))
        assertTrue(LetterRules.error(draft.copy(content = "a".repeat(10_001)), letterToday)!!.contains("10,000"))
    }

    @Test fun `revisit status is a calendar comparison and saved replies retain priority`() {
        val letter = savedLetter().copy(targetDate = "2026-10-10")
        assertEquals("Ready to revisit", letter.status(letterToday))
        assertTrue(letter.status(letterToday.minusDays(1)).startsWith("Revisit on"))
        assertEquals("Reply saved", letter.copy(aiReplyText = "Your saved answer").status(letterToday))
        assertEquals("Delivered", letter.copy(deliveredAt = letter.writtenAt).status(letterToday))
        assertTrue(letter.writtenLabel("Asia/Karachi").contains("11"))
        assertTrue(letter.writtenLabel("America/Los_Angeles").contains("10"))
        assertEquals(letter.targetLabel, letter.copy(writtenAt = "2026-10-11T03:30:00.123456+05:00").targetLabel)
    }

    @Test fun `paging preserves precise timestamp and UUID tie boundaries`() {
        val rows = (1..21).map { savedLetter(it) }
        val page = LetterRules.page("user", null, rows)
        assertEquals((21 downTo 2).map { savedLetter(it).id }, page.letters.map { it.id })
        assertEquals(LetterCursor(rows[1].writtenAt, rows[1].id), page.next)
        assertEquals(listOf(rows[0]), LetterRules.page("user", page.next, listOf(rows[0])).letters)
        assertTrue(runCatching { LetterRules.page("user", page.next, listOf(rows[1])) }.isFailure)
        assertNull(LetterRules.page("user", null, rows.take(20)).next)
    }

    @Test fun `recovery matches authored content while preserving new server replies`() {
        val letter = savedLetter().copy(deliveredAt = "2027-04-10T00:00:00Z", aiReplyText = "A reply")
        assertTrue(LetterRules.matches("user", letterIntent(), letter))
        assertFalse(LetterRules.matches("user", letterIntent(), letter.copy(content = "Different letter")))
        assertFalse(LetterRules.matches("user", letterIntent(), letter.copy(targetDate = "2028-04-10")))
        assertFalse(LetterRules.matches("user", letterIntent(), letter.copy(userId = "other")))
    }

    @Test fun `invalid and foreign cached data cannot be restored`() {
        assertFalse(LetterRules.validLetter("user", savedLetter(user = "other")))
        assertFalse(LetterRules.validLetter("user", savedLetter().copy(deliveredAt = "not-a-date")))
        assertFalse(LetterRules.validSnapshot("user", LetterSnapshot(draft = letterIntent(user = "other").draft())))
        assertFalse(LetterRules.validSnapshot("user", LetterSnapshot(draft = letterIntent(2).draft(), pending = letterIntent())))
        assertFalse(LetterRules.validSnapshot("user", LetterSnapshot(recent = LetterPage((1..21).map { savedLetter(it) }))))
        assertFalse(LetterRules.validIntent("user", letterIntent().copy(content = " not normalized ")))
    }
}
