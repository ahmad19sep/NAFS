package com.ahmad.nafs

import com.ahmad.nafs.core.checkin.CheckInRules
import com.ahmad.nafs.core.checkin.DailyCheckIn
import com.ahmad.nafs.core.coach.CoachConversation
import com.ahmad.nafs.core.coach.CoachMessage
import com.ahmad.nafs.core.coach.CoachRules
import com.ahmad.nafs.core.coach.CoachSendIntent
import com.ahmad.nafs.core.plans.RecoveryIntent
import com.ahmad.nafs.core.plans.RecoveryRules
import com.ahmad.nafs.core.reports.ReportCursor
import com.ahmad.nafs.core.reports.ReportRules
import com.ahmad.nafs.core.time.ServerTimestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercises the Android time implementation with synthetic records; no Auth, network or account writes. */
class ServerTimestampInstrumentedTest {
    private val id = "12345678-1234-1234-1234-123456789abc"
    private val version = "2026-10-10T09:00:00.123456+00:00"

    @Test fun databaseOffsetsParseAndRetainMicrosecondsOnAndroid() {
        val instant = ServerTimestamp.parse(version)
        assertEquals(ServerTimestamp.parse("2026-10-10T14:00:00.123456+05:00"), instant)
        assertEquals(ServerTimestamp.parse("2026-10-10T09:00:00.123456Z"), instant)
        assertEquals(123456000, instant.nano)
        assertTrue(runCatching { ServerTimestamp.parse("2026-10-10T09:00:00") }.isFailure)
    }

    @Test fun savedCoachHistoryLoadsAndExactReplyVersionsStillAdvance() {
        val history = listOf(CoachMessage("user", "A synthetic question"), CoachMessage("assistant", "A synthetic answer"))
        val conversation = CoachConversation(id, "test-account", history, version, version)
        assertTrue(CoachRules.validConversation("test-account", conversation))
        assertFalse(CoachRules.validConversation("other-account", conversation))
        val intent = CoachSendIntent(history + CoachMessage("user", "Next step?"), id, version)
        val saved = conversation.copy(messages = intent.messages + CoachMessage("assistant", "A next step"),
            updatedAt = "2026-10-10T09:00:00.123457+00:00")
        assertTrue(CoachRules.recovered("test-account", intent, saved))
        assertFalse(CoachRules.recovered("test-account", intent, saved.copy(updatedAt = version)))
        assertEquals(version, intent.expectedUpdatedAt)
    }

    @Test fun relatedDatabaseVersionValidatorsAcceptOffsetsOnAndroid() {
        assertNull(CheckInRules.error(DailyCheckIn(id, "test-account", "2026-10-10", updatedAt = version)))
        assertTrue(RecoveryRules.validIntent(RecoveryIntent(id, conversationId = id, expectedUpdatedAt = version)))
        assertTrue(ReportRules.validCursor(ReportCursor(version, id)))
    }
}
