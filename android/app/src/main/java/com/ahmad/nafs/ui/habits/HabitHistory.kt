package com.ahmad.nafs.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahmad.nafs.core.habits.Habit
import com.ahmad.nafs.core.habits.HabitLog
import com.ahmad.nafs.domain.habits.HabitDayState
import com.ahmad.nafs.domain.habits.HabitHeatmap
import com.ahmad.nafs.domain.habits.HabitHeatmapDay
import com.ahmad.nafs.domain.habits.HabitHeatmapWeek
import com.ahmad.nafs.domain.habits.HabitStreak
import com.ahmad.nafs.domain.habits.HabitType
import com.ahmad.nafs.domain.habits.ScheduleKind
import com.ahmad.nafs.ui.components.NafsCard
import com.ahmad.nafs.ui.components.NafsEmojiBadge
import com.ahmad.nafs.ui.components.NafsEmptyState
import com.ahmad.nafs.ui.components.NafsEyebrow
import com.ahmad.nafs.ui.components.NafsPill
import com.ahmad.nafs.ui.components.NafsSectionTitle
import com.ahmad.nafs.ui.components.NafsSpace
import com.ahmad.nafs.ui.components.NafsStat
import com.ahmad.nafs.ui.theme.Nafs
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One habit's history: streaks, the last 12 weeks as a calendar heatmap (tap a day to add or correct its entry),
 * then every saved entry. Stateless so it can be rendered on its own.
 */
@Composable
internal fun HabitHistoryContent(
    habit: Habit,
    logs: List<HabitLog>,
    today: LocalDate,
    onEditEntry: (LocalDate) -> Unit,
    onClose: () -> Unit,
) {
    val schedule = habit.schedule()
    val results = remember(logs) { logs.map { it.toHabitResult() } }
    val weeks = remember(schedule, results, today) { HabitHeatmap.build(schedule, results, today) }
    val streak = remember(schedule, results, today, habit.longestStreak) {
        HabitStreak.summary(schedule, results, today, storedBest = habit.longestStreak)
    }
    val weekDone = remember(schedule, results, today) { HabitStreak.completedThisWeek(schedule, results, today) }
    val logsByDate = remember(logs) { logs.associateBy(HabitLog::date) }
    val perWeek = habit.scheduleKind == ScheduleKind.PER_WEEK

    LazyColumn(
        Modifier.fillMaxWidth().navigationBarsPadding(),
        contentPadding = PaddingValues(start = NafsSpace.screen, end = NafsSpace.screen, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "history-header") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    NafsEmojiBadge(habit.emoji, Nafs.colors.habits, size = 48.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        NafsEyebrow("History and notes", color = Nafs.colors.habits)
                        Text(
                            habit.name,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Close history",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item(key = "history-stats") {
            NafsCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    val unit = if (perWeek) "week" else "day"
                    NafsStat(count(streak.current, unit), "Current streak", Modifier.weight(1f))
                    NafsStat(count(streak.best, unit), "Best streak", Modifier.weight(1f))
                    if (perWeek) {
                        val target = HabitStreak.weeklyTarget(schedule)
                        NafsStat(
                            value = "$weekDone of $target",
                            label = "This week",
                            modifier = Modifier.weight(1f),
                            progress = (weekDone.toFloat() / target).coerceIn(0f, 1f),
                            color = Nafs.colors.habits,
                        )
                    } else {
                        val kept = weeks.sumOf { week -> week.days.count { it.state == HabitDayState.COMPLETE } }
                        NafsStat("$kept", "Kept in 12 weeks", Modifier.weight(1f))
                    }
                }
            }
        }
        item(key = "history-heatmap") {
            NafsCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Last ${weeks.size} weeks",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        weeks.firstOrNull()?.let { first ->
                            Text(
                                "${first.monday.format(RangeFormat)} – ${weeks.last().monday.plusDays(6).format(RangeFormat)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HeatmapGrid(weeks, habit, logsByDate, onEditEntry)
                    HeatmapLegend(weeks.flatMap { it.days }.map(HabitHeatmapDay::state).toSet())
                    Text(
                        if (perWeek) {
                            "Any ${HabitStreak.weeklyTarget(schedule)} days a week count. Tap a day to add or correct its entry."
                        } else {
                            "Tap a day to add or correct its entry."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item(key = "history-entries-title") {
            NafsSectionTitle("Saved entries", Modifier.padding(top = 8.dp), supporting = "Most recent 400 days")
        }
        if (logs.isEmpty()) {
            item(key = "history-empty") {
                NafsEmptyState(
                    icon = Icons.AutoMirrored.Rounded.EventNote,
                    title = "No saved entries",
                    message = "Entries appear here once you log this habit, including days you fill in later.",
                    tint = Nafs.colors.habits,
                )
            }
        } else {
            items(logs, key = { log -> "entry-${log.date}" }) { log ->
                HistoryEntry(habit, log, onEdit = { onEditEntry(log.date) })
            }
        }
    }
}

/** Week rows, Monday to Sunday, with a month label where a month begins. */
@Composable
private fun HeatmapGrid(
    weeks: List<HabitHeatmapWeek>,
    habit: Habit,
    logsByDate: Map<LocalDate, HabitLog>,
    onEditEntry: (LocalDate) -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(CellGap)) {
        // Every cell reads its full date aloud, so these labels are left out of accessibility.
        Row(horizontalArrangement = Arrangement.spacedBy(CellGap), modifier = Modifier.clearAndSetSemantics {}) {
            Spacer(Modifier.width(MonthColumn))
            DayOfWeek.entries.forEach { day ->
                Text(
                    day.name.take(1),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                )
            }
        }
        weeks.forEachIndexed { index, week ->
            Row(horizontalArrangement = Arrangement.spacedBy(CellGap), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    monthLabel(week, isFirstRow = index == 0).orEmpty(),
                    modifier = Modifier.width(MonthColumn).clearAndSetSemantics {},
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                    maxLines = 1,
                )
                week.days.forEach { day ->
                    HeatmapCell(
                        day = day,
                        description = cellDescription(day, logsByDate[day.date], habit),
                        editLabel = if (logsByDate[day.date] == null) "Add entry" else "Edit entry",
                        onEdit = { onEditEntry(day.date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeatmapCell(
    day: HabitHeatmapDay,
    description: String,
    editLabel: String,
    onEdit: () -> Unit,
    modifier: Modifier,
) {
    val style = cellStyle(day.state)
    val shape = RoundedCornerShape(8.dp)
    val ring = MaterialTheme.colorScheme.onSurface
    Box(
        modifier
            .height(CellHeight)
            .clip(shape)
            .background(style.fill)
            .then(style.border?.let { Modifier.border(1.dp, it, shape) } ?: Modifier)
            .then(if (day.isToday) Modifier.border(2.dp, ring, shape) else Modifier)
            .then(
                if (day.canEdit) {
                    Modifier.clickable(onClickLabel = editLabel, role = Role.Button, onClick = onEdit)
                } else {
                    Modifier
                },
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            day.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = style.text,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/** The states shown in this grid, plus the ring that marks today. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeatmapLegend(states: Set<HabitDayState>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HabitDayState.entries.filter { it in states }.forEach { state ->
            LegendItem(cellStyle(state), state.legendLabel(), ring = null)
        }
        LegendItem(CellStyle(Color.Transparent, null, Color.Transparent), "Today", ring = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun LegendItem(style: CellStyle, label: String, ring: Color?) {
    val shape = RoundedCornerShape(4.dp)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(12.dp)
                .clip(shape)
                .background(style.fill)
                .then(style.border?.let { Modifier.border(1.dp, it, shape) } ?: Modifier)
                .then(ring?.let { Modifier.border(2.dp, it, shape) } ?: Modifier),
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private data class CellStyle(val fill: Color, val border: Color?, val text: Color)

@Composable
private fun cellStyle(state: HabitDayState): CellStyle {
    val scheme = MaterialTheme.colorScheme
    val accent = Nafs.colors.habits
    val dark = Nafs.colors.isDark
    return when (state) {
        HabitDayState.COMPLETE -> CellStyle(accent, null, scheme.surface)
        HabitDayState.PARTIAL -> CellStyle(accent.copy(alpha = if (dark) 0.34f else 0.26f), null, scheme.onSurface)
        HabitDayState.MISSED -> CellStyle(scheme.error.copy(alpha = if (dark) 0.22f else 0.13f), null, scheme.error)
        HabitDayState.NO_ENTRY -> CellStyle(scheme.onSurface.copy(alpha = 0.06f), null, scheme.onSurfaceVariant)
        HabitDayState.REST -> CellStyle(Color.Transparent, scheme.outlineVariant, scheme.onSurfaceVariant.copy(alpha = 0.7f))
        HabitDayState.FUTURE -> CellStyle(Color.Transparent, null, scheme.onSurfaceVariant.copy(alpha = 0.35f))
    }
}

private fun HabitDayState.legendLabel(): String = when (this) {
    HabitDayState.COMPLETE -> "Complete"
    HabitDayState.PARTIAL -> "Partly done"
    HabitDayState.MISSED -> "Missed"
    HabitDayState.NO_ENTRY -> "No entry"
    HabitDayState.REST -> "Rest day"
    HabitDayState.FUTURE -> "Upcoming"
}

/** "Monday 5 October: partly done, 5 glasses" — what a screen reader says for one cell. */
private fun cellDescription(day: HabitHeatmapDay, log: HabitLog?, habit: Habit): String {
    val date = day.date.format(CellDateFormat) + if (day.isToday) ", today" else ""
    val amount = log?.takeIf { habit.type != HabitType.SIMPLE }?.let { ", ${entryAmount(habit, it)}" }.orEmpty()
    val status = when (day.state) {
        HabitDayState.COMPLETE -> "complete$amount"
        HabitDayState.PARTIAL -> "partly done$amount"
        HabitDayState.MISSED -> "missed"
        HabitDayState.NO_ENTRY -> if (day.isToday) "not logged yet" else "no entry"
        HabitDayState.REST -> "rest day"
        HabitDayState.FUTURE -> "upcoming"
    }
    return "$date: $status"
}

/** The month a row starts, where one begins in it (or for the top row). */
private fun monthLabel(week: HabitHeatmapWeek, isFirstRow: Boolean): String? {
    val date = week.days.firstOrNull { it.date.dayOfMonth == 1 }?.date
        ?: if (isFirstRow) week.monday else return null
    return date.format(MonthFormat)
}

@Composable
private fun HistoryEntry(habit: Habit, log: HabitLog, onEdit: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    NafsCard(contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(log.date.format(EntryDateFormat), style = MaterialTheme.typography.titleSmall)
                Text(entryAmount(habit, log), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            NafsPill(
                if (log.completed) "Complete" else "Recorded",
                color = if (log.completed) Nafs.colors.success else scheme.onSurfaceVariant,
                icon = if (log.completed) Icons.Rounded.Check else null,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        log.notes?.takeIf { it.isNotBlank() }?.let { note ->
            Text(note, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp, end = 8.dp))
        }
        TextButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Edit entry")
        }
    }
}

private fun count(value: Int, unit: String): String = "$value ${if (value == 1) unit else "${unit}s"}"

private val CellGap = 4.dp
private val CellHeight = 34.dp
private val MonthColumn = 28.dp
private val RangeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val MonthFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
private val CellDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)
private val EntryDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)
