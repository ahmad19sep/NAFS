package com.ahmad.nafs.core.time

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

object AccountDate {
    fun today(timezone: String, clock: Clock = Clock.systemUTC()): LocalDate =
        LocalDate.now(clock.withZone(ZoneId.of(timezone)))

    fun shift(date: LocalDate, days: Long): LocalDate = date.plusDays(days)
}

