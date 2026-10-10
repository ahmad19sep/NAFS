package com.ahmad.nafs.domain.habits

/**
 * A focus session for a duration habit, kept as timestamps rather than a ticking counter, so leaving the app or the
 * process being restarted never loses time: [accumulatedMillis] from earlier stretches plus the stretch running
 * since [runningSinceMillis] (epoch milliseconds; null while paused).
 */
data class HabitTimerState(
    val userId: String,
    val habitId: String,
    val accumulatedMillis: Long = 0L,
    val runningSinceMillis: Long? = null,
) {
    val isRunning: Boolean get() = runningSinceMillis != null

    /** Never negative, even if the device clock was moved back while the timer ran. */
    fun elapsedMillis(nowMillis: Long): Long {
        val running = runningSinceMillis?.let { (nowMillis - it).coerceAtLeast(0L) } ?: 0L
        return accumulatedMillis.coerceAtLeast(0L) + running
    }

    /** Starts or resumes; a running timer is left as it is. */
    fun start(nowMillis: Long): HabitTimerState = if (isRunning) this else copy(runningSinceMillis = nowMillis)

    fun pause(nowMillis: Long): HabitTimerState =
        if (isRunning) copy(accumulatedMillis = elapsedMillis(nowMillis), runningSinceMillis = null) else this

    /** Whole minutes: the seconds of an unfinished minute are not logged. */
    fun elapsedMinutes(nowMillis: Long): Int =
        (elapsedMillis(nowMillis) / MILLIS_PER_MINUTE).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

object HabitTimerRules {
    /** No day holds more than 24 hours. */
    const val MAX_MINUTES_PER_DAY = 1_440

    /** The day's total after a session: what was already logged plus the session, capped at a full day. */
    fun minutesAfterSession(loggedMinutes: Int, sessionMinutes: Int): Int =
        (loggedMinutes.coerceAtLeast(0).toLong() + sessionMinutes.coerceAtLeast(0))
            .coerceAtMost(MAX_MINUTES_PER_DAY.toLong())
            .toInt()

    /** "04:07" under an hour, "1:04:07" from an hour on. */
    fun clock(elapsedMillis: Long): String {
        val totalSeconds = elapsedMillis.coerceAtLeast(0L) / 1_000L
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) "$hours:${two(minutes)}:${two(seconds)}" else "${two(minutes)}:${two(seconds)}"
    }

    /** Progress toward the day's minutes target, counting minutes already logged today. */
    fun progress(loggedMinutes: Int, elapsedMillis: Long, targetMinutes: Int): Float {
        if (targetMinutes <= 0) return 0f
        val done = loggedMinutes.coerceAtLeast(0) * MILLIS_PER_MINUTE.toDouble() + elapsedMillis.coerceAtLeast(0L)
        return (done / (targetMinutes * MILLIS_PER_MINUTE.toDouble())).toFloat().coerceIn(0f, 1f)
    }

    private fun two(value: Long): String = value.toString().padStart(2, '0')
}

private const val MILLIS_PER_MINUTE = 60_000L
