package com.ahmad.nafs.core.tasks

import com.ahmad.nafs.domain.tasks.TaskPeriodType
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskCreateReplayRuleTest {
    private val payload = TaskCreatePayload(
        userId = "user-1",
        title = "Read",
        note = null,
        type = TaskPeriodType.DAILY,
        priority = TaskPriority.MEDIUM,
        periodDate = LocalDate.parse("2026-10-09"),
        dueTime = "21:30",
    )

    @Test
    fun exactCreateRetryIsRecognizedAsTheSameIntent() {
        assertTrue(TaskCreateReplayRule.isSameIntent(payload, payload.copy()))
    }

    @Test
    fun reusedRequestIdWithDifferentContentIsAConflict() {
        assertFalse(
            TaskCreateReplayRule.isSameIntent(
                existing = payload,
                requested = payload.copy(title = "Exercise"),
            ),
        )
    }
}
