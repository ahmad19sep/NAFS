package com.ahmad.nafs.core.time

import java.time.Instant
import java.time.OffsetDateTime

/** Parse database offsets on Android as well as UTC Z timestamps, without changing the stored version. */
object ServerTimestamp {
    fun parse(value: String): Instant = OffsetDateTime.parse(value).toInstant()
}
