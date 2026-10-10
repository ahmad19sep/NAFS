package com.ahmad.nafs.ui.checkin

import org.junit.Assert.assertEquals
import org.junit.Test

class DictationTextTest {
    @Test fun `first words start a sentence`() {
        assertEquals("Got the report out", DictationText.append("", "  got the report   out "))
    }

    @Test fun `speech joins typed text with one space`() {
        assertEquals("Long day and meetings ran late", DictationText.append("Long day", "and meetings ran late"))
        assertEquals("Long day and more", DictationText.append("Long day ", "and more"))
    }

    @Test fun `a new sentence after a full stop is capitalised`() {
        assertEquals("Felt calm. Prayed on time", DictationText.append("Felt calm.", "prayed on time"))
        assertEquals("Line one\nNext line", DictationText.append("Line one\n", "next line"))
    }

    @Test fun `blank speech changes nothing`() {
        assertEquals("Notes", DictationText.append("Notes", "   "))
    }

    @Test fun `appended speech stops at the limit without splitting an emoji`() {
        assertEquals("abc def", DictationText.append("abc", "def ghi", maxLength = 7))
        assertEquals("abcdefghij", DictationText.append("abcdefghij", "more", maxLength = 10))
        assertEquals("ab", DictationText.append("ab", "🙂", maxLength = 4))
    }
}
