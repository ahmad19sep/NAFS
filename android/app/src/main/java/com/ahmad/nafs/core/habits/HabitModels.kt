package com.ahmad.nafs.core.habits

import com.ahmad.nafs.domain.habits.HabitSchedule
import com.ahmad.nafs.domain.habits.HabitType
import com.ahmad.nafs.domain.habits.ScheduleKind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

data class Habit(
    val id: String,
    val userId: String,
    val name: String,
    val emoji: String,
    val type: HabitType,
    val targetValue: Double,
    val unit: String,
    val timeTargetMinutes: Int,
    val subjectName: String?,
    val subjectTotal: Int?,
    val subjectPosition: Int,
    val subjectUnit: String?,
    val scheduleKind: ScheduleKind,
    val scheduleDays: Set<DayOfWeek>,
    val weeklyTarget: Int?,
    val isPaused: Boolean,
    val sortOrder: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val reminderTime: LocalTime? = null,
) {
    fun schedule() = HabitSchedule(
        type = type,
        targetValue = targetValue,
        timeTargetMinutes = timeTargetMinutes,
        isPaused = isPaused,
        scheduleKind = scheduleKind,
        scheduleDays = scheduleDays,
        weeklyTarget = weeklyTarget,
    )
}

data class HabitLog(
    val userId: String,
    val habitId: String,
    val date: LocalDate,
    val completed: Boolean,
    val value: Double,
    val durationMinutes: Int,
    val notes: String?,
)

data class LogHabitIntent(
    val userId: String,
    val habit: Habit,
    val date: LocalDate,
    val value: Double = 0.0,
    val durationMinutes: Int = 0,
    val completed: Boolean = false,
    val notes: String? = null,
)

data class SaveHabitIntent(
    val requestId: String,
    val userId: String,
    val habitId: String? = null,
    val name: String,
    val emoji: String,
    val type: HabitType,
    val targetValue: Double,
    val unit: String,
    val timeTargetMinutes: Int,
    val subjectName: String?,
    val subjectTotal: Int?,
    val subjectPosition: Int,
    val subjectUnit: String?,
    val scheduleKind: ScheduleKind,
    val scheduleDays: Set<DayOfWeek>,
    val weeklyTarget: Int?,
    /** A daily nudge on scheduled days; null for none. */
    val reminderTime: LocalTime? = null,
)

data class HabitDefinitionPayload(
    val userId: String,
    val name: String,
    val emoji: String,
    val type: HabitType,
    val targetValue: Double,
    val unit: String,
    val timeTargetMinutes: Int,
    val subjectName: String?,
    val subjectTotal: Int?,
    val subjectPosition: Int,
    val subjectUnit: String?,
    val scheduleKind: ScheduleKind,
    val scheduleDays: Set<DayOfWeek>,
    val weeklyTarget: Int?,
    val reminderTime: LocalTime? = null,
)

/**
 * A create retry is safe only when the row the server already holds for the request id carries exactly the details
 * being saved, the reminder time included (both sides at minute precision).
 */
object HabitDefinitionReplayRule {
    fun isSameIntent(
        existing: HabitDefinitionPayload,
        requested: HabitDefinitionPayload,
    ): Boolean = existing == requested
}

/** `habits.reminder_time` is a Postgres TIME: it reads back as "HH:MM:SS" and the web app writes "HH:MM". */
object HabitReminderTime {
    private val pattern = Regex("""^(\d{1,2}):(\d{2})(?::(\d{2})(?:\.\d+)?)?$""")

    /** The time to the minute, or null for a blank or unreadable value (which then reads as "no reminder"). */
    fun parse(raw: String?): LocalTime? {
        val match = pattern.matchEntire(raw?.trim().orEmpty()) ?: return null
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        val second = match.groupValues[3].ifEmpty { "0" }.toInt()
        if (hour !in 0..23 || minute !in 0..59 || second !in 0..59) return null
        return LocalTime.of(hour, minute)
    }

    /** "HH:MM" with ASCII digits whatever the device locale: the format the web app writes. */
    fun format(time: LocalTime): String =
        "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
}

/** The stored streak columns after they were recomputed from the server's own log history. */
data class HabitStreakUpdate(val currentStreak: Int, val longestStreak: Int)

object HabitDefinitionValidator {
    fun validationError(intent: SaveHabitIntent): String? = when {
        intent.name.trim().isEmpty() -> "Enter a habit name."
        intent.name.trim().length > 80 -> "Keep the habit name under 80 characters."
        intent.emoji.trim().isEmpty() -> "Choose an emoji for the habit."
        intent.type == HabitType.COUNTER &&
            (!intent.targetValue.isFinite() || intent.targetValue <= 0.0) ->
            "Counter target must be greater than zero."
        intent.type == HabitType.COUNTER && intent.unit.trim().isEmpty() ->
            "Enter a unit for the counter, such as glasses or pages."
        intent.type == HabitType.DURATION && intent.timeTargetMinutes !in 1..1_440 ->
            "Duration target must be between 1 and 1,440 minutes."
        intent.type == HabitType.SUBJECT && intent.subjectName?.trim().isNullOrEmpty() ->
            "Enter the book, course, or subject name."
        intent.type == HabitType.SUBJECT && (intent.subjectTotal ?: 0) <= 0 ->
            "Subject total must be greater than zero."
        intent.type == HabitType.SUBJECT && intent.subjectPosition < 0 ->
            "Current subject position cannot be negative."
        intent.type == HabitType.SUBJECT &&
            intent.subjectPosition > (intent.subjectTotal ?: 0) ->
            "Current position cannot be greater than the subject total."
        intent.type == HabitType.SUBJECT && intent.subjectUnit?.trim().isNullOrEmpty() ->
            "Enter a subject unit, such as pages or lessons."
        intent.scheduleKind == ScheduleKind.WEEKDAYS && intent.scheduleDays.isEmpty() ->
            "Select at least one scheduled day."
        intent.scheduleKind == ScheduleKind.PER_WEEK && (intent.weeklyTarget ?: 0) !in 1..7 ->
            "Weekly target must be between 1 and 7."
        else -> null
    }
}

sealed interface HabitLoadResult {
    data class Success(
        val habits: List<Habit>,
        val logs: List<HabitLog>,
        val isOffline: Boolean,
    ) : HabitLoadResult
    data class Failure(val message: String) : HabitLoadResult
}

sealed interface HabitActionResult {
    data class Success(val log: HabitLog, val subjectPosition: Int? = null) : HabitActionResult
    data class Failure(val message: String) : HabitActionResult
}

sealed interface HabitDefinitionActionResult {
    data class Success(val habit: Habit) : HabitDefinitionActionResult
    data class Failure(val message: String) : HabitDefinitionActionResult
}
