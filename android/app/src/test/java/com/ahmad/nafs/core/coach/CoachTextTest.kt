package com.ahmad.nafs.core.coach

import org.junit.Assert.assertEquals
import org.junit.Test

class CoachTextTest {
    @Test fun `light markdown becomes plain text for copying and sharing`() {
        val reply = "## The verdict\nYou kept **4 of 6** promises.\n\n\n\n- Fajr on time\n* Reading streak\n> Keep going.\n```\ncode fence\n```"
        assertEquals(
            "The verdict\nYou kept 4 of 6 promises.\n\n• Fajr on time\n• Reading streak\nKeep going.\ncode fence",
            CoachText.plain(reply),
        )
    }

    @Test fun `plain prose and numbered steps are kept as written`() {
        assertEquals("1. Rest\n2. Walk after Asr", CoachText.plain("1. Rest\r\n2. Walk after Asr  \n"))
    }
}
