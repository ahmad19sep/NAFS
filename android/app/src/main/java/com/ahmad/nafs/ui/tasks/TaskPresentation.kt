package com.ahmad.nafs.ui.tasks

import com.ahmad.nafs.core.tasks.NafsTask
import com.ahmad.nafs.domain.tasks.TaskPeriod
import com.ahmad.nafs.domain.tasks.TaskPeriodType
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How a task reads at a glance. */
enum class TaskState(val label: String) {
    OPEN("Open"),
    DONE("Done"),

    /** Still open, but its period has passed. */
    MISSED("Missed"),

    /** Planned for a later period. */
    UPCOMING("Upcoming"),
}

fun taskStateOf(task: NafsTask, today: LocalDate): TaskState = when {
    task.isCompleted -> TaskState.DONE
    TaskPeriod.isUpcoming(task.type, task.periodDate, today) -> TaskState.UPCOMING
    TaskPeriod.isMissed(task.type, task.periodDate, isCompleted = false, today = today) -> TaskState.MISSED
    else -> TaskState.OPEN
}

/** Open before done; then the earliest due time (untimed last), the higher priority, the newest. */
val taskListOrder: Comparator<NafsTask> = compareBy<NafsTask> { it.isCompleted }
    .thenBy(nullsLast<String>()) { it.dueTime?.take(5) }
    .thenBy { it.priority.rank }
    .thenByDescending { it.createdAt }

/** Tasks planned for one later period. */
data class TaskDateGroup(val periodDate: LocalDate, val tasks: List<NafsTask>)

/** Future-dated tasks of the selected type, nearest period first. They never appear in the current lists. */
fun upcomingTasksFor(state: TaskUiState): List<TaskDateGroup> = state.tasks
    .filter { it.type == state.selectedType && TaskPeriod.isUpcoming(it.type, it.periodDate, state.today) }
    .groupBy(NafsTask::periodDate)
    .toSortedMap()
    .map { (date, tasks) -> TaskDateGroup(date, tasks.sortedWith(taskListOrder)) }

private val whitespace = Regex("\\s+")

/**
 * Every loaded task (all types: current, missed, upcoming and the last six months) whose title or note
 * contains each word of [query], ignoring case. Latest period first.
 */
fun searchTasks(tasks: List<NafsTask>, query: String): List<NafsTask> {
    val terms = query.trim().lowercase().split(whitespace).filter(String::isNotEmpty)
    if (terms.isEmpty()) return emptyList()
    return tasks
        .filter { task ->
            val text = "${task.title}\n${task.note.orEmpty()}".lowercase()
            terms.all { it in text }
        }
        .sortedWith(compareByDescending<NafsTask> { it.periodDate }.then(taskListOrder))
}

/** Words for task dates and times, shared by Tasks and Home so both read the same. */
internal object TaskLabels {
    private val clockPattern = Regex("^(\\d{1,2}):(\\d{2})")
    private val clock: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val weekdayDayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
    private val weekdayDayMonthYear: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)
    private val dayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val dayMonthYear: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val monthYear: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

    /** Reads "HH:MM" (or "HH:MM:SS") into hour and minute, or null when it isn't a valid clock time. */
    fun clockParts(raw: String): Pair<Int, Int>? {
        val match = clockPattern.find(raw.trim()) ?: return null
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        return if (hour in 0..23 && minute in 0..59) hour to minute else null
    }

    /** 24-hour, zero-padded "HH:MM": the format the ViewModel validates and saves. */
    fun clockText(hour: Int, minute: Int): String =
        "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

    /** 12-hour time such as "5:00 PM"; anything unreadable is shown as stored. */
    fun time(raw: String): String = clockParts(raw)?.let { (hour, minute) ->
        LocalTime.of(hour, minute).format(clock)
    } ?: raw

    /** "Today", "Tomorrow", "Yesterday", or a short date such as "Tue, 13 Oct". */
    fun day(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(if (date.year == today.year) weekdayDayMonth else weekdayDayMonthYear)
    }

    /** The period a task belongs to, relative to today: a day, a week ("Week of 5 Oct") or a month. */
    fun period(type: TaskPeriodType, periodDate: LocalDate, today: LocalDate): String {
        val current = TaskPeriod.key(type, today)
        return when (type) {
            TaskPeriodType.DAILY -> day(periodDate, today)
            TaskPeriodType.WEEKLY -> when (periodDate) {
                current -> "This week"
                TaskPeriod.previousKey(type, current) -> "Last week"
                TaskPeriod.nextKey(type, current) -> "Next week"
                else -> "Week of " + periodDate.format(if (periodDate.year == today.year) dayMonth else dayMonthYear)
            }
            TaskPeriodType.MONTHLY -> when (periodDate) {
                current -> "This month"
                TaskPeriod.previousKey(type, current) -> "Last month"
                TaskPeriod.nextKey(type, current) -> "Next month"
                else -> periodDate.format(monthYear)
            }
        }
    }

    /** "today", "tomorrow", "this week", "Tue, 13 Oct": for sentences such as "Moved to tomorrow". */
    fun periodPhrase(type: TaskPeriodType, periodDate: LocalDate, today: LocalDate): String {
        val current = TaskPeriod.key(type, today)
        return when (periodDate) {
            current, TaskPeriod.nextKey(type, current), TaskPeriod.previousKey(type, current) ->
                period(type, periodDate, today).lowercase(Locale.ENGLISH)
            else -> period(type, periodDate, today)
        }
    }

    /** Label for moving a task into the period that starts on [target]. */
    fun moveTo(type: TaskPeriodType, target: LocalDate, today: LocalDate): String =
        "Move to ${periodPhrase(type, target, today)}"

    /** Label for an open task left over from the previous period. */
    fun missed(type: TaskPeriodType): String = when (type) {
        TaskPeriodType.DAILY -> "Missed yesterday"
        TaskPeriodType.WEEKLY -> "Missed last week"
        TaskPeriodType.MONTHLY -> "Missed last month"
    }
}
