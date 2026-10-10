package com.ahmad.nafs.core.checkin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckInDraftRulesTest {
    private val saved = DailyCheckIn(
        "record", "account", "2026-10-10", tasks = listOf(CheckInTask("Read", true)), eveningText = "Calm day",
        updatedAt = "2026-10-10T06:00:00Z",
    )
    private val draft = CheckInDraft("2026-10-10", "record", saved.updatedAt, saved.tasks, saved.eveningText)

    @Test fun `a draft matching the saved record is not worth keeping`() {
        assertFalse(CheckInDraftRules.differs(draft, saved))
        assertFalse(CheckInDraftRules.differs(draft.copy(eveningText = "  Calm day  "), saved))
        assertTrue(CheckInDraftRules.differs(draft.copy(eveningText = "Calm day, then a long call"), saved))
        assertTrue(CheckInDraftRules.differs(draft.copy(newTask = "Walk"), saved))
        assertTrue(CheckInDraftRules.differs(draft.copy(tasks = listOf(CheckInTask("Read", false))), saved))
    }

    @Test fun `with nothing saved any text is worth keeping`() {
        val empty = CheckInDraft("2026-10-10", "record")
        assertFalse(CheckInDraftRules.differs(empty, null))
        assertTrue(CheckInDraftRules.differs(empty.copy(eveningText = "Notes"), null))
    }

    @Test fun `todays draft is restored and an older one is kept aside`() {
        val today = CheckInDrafts(current = draft.copy(eveningText = "Unsaved"))
        assertEquals(today.current, CheckInDraftRules.sort(today, "2026-10-10").current)
        val stale = CheckInDraftRules.sort(today, "2026-10-11")
        assertNull(stale.current)
        assertEquals(today.current, stale.previous)
        val previous = draft.copy(date = "2026-10-08", eveningText = "Older")
        val both = CheckInDraftRules.sort(CheckInDrafts(draft.copy(eveningText = "Now"), previous), "2026-10-10")
        assertEquals("Now", both.current?.eveningText)
        assertEquals(previous, both.previous)
        assertNull(CheckInDraftRules.sort(CheckInDrafts(current = CheckInDraft("2026-10-10", "record")), "2026-10-10").current)
    }

    @Test fun `stored drafts outside the save limits are rejected`() {
        assertTrue(CheckInDraftRules.valid("account", draft))
        assertFalse(CheckInDraftRules.valid("account", draft.copy(date = "2026-02-30")))
        assertFalse(CheckInDraftRules.valid("account", draft.copy(tasks = List(8) { CheckInTask("Task $it") })))
        assertFalse(CheckInDraftRules.valid("account", draft.copy(eveningText = "x".repeat(10_001))))
        assertFalse(CheckInDraftRules.valid("", draft))
    }
}
