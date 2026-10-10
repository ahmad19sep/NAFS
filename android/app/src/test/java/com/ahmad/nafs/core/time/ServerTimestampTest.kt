package com.ahmad.nafs.core.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerTimestampTest {
    @Test fun `numeric offsets and UTC represent the same instant`() {
        val utc = ServerTimestamp.parse("2026-10-10T09:00:00.123456Z")
        assertEquals(utc, ServerTimestamp.parse("2026-10-10T09:00:00.123456+00:00"))
        assertEquals(utc, ServerTimestamp.parse("2026-10-10T14:00:00.123456+05:00"))
        assertEquals(utc, ServerTimestamp.parse("2026-10-10T05:30:00.123456-03:30"))
    }

    @Test fun `database microseconds preserve optimistic concurrency ordering`() {
        val earlier = ServerTimestamp.parse("2026-10-10T09:00:00.123456+00:00")
        val later = ServerTimestamp.parse("2026-10-10T09:00:00.123457+00:00")
        assertEquals(123456000, earlier.nano)
        assertEquals(1000, later.nano - earlier.nano)
        assertTrue(later > earlier)
    }

    @Test fun `missing timezone and malformed values remain invalid`() {
        listOf("", "2026-10-10", "2026-10-10T09:00:00", "2026-10-10T09:00:00+25:00", "not-a-time").forEach {
            assertTrue(runCatching { ServerTimestamp.parse(it) }.isFailure)
        }
    }
}
