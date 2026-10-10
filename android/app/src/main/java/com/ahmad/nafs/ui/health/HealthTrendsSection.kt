package com.ahmad.nafs.ui.health

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ahmad.nafs.core.health.HealthGoalRules
import com.ahmad.nafs.core.health.HealthGoals
import com.ahmad.nafs.core.health.HealthTrendMetric
import com.ahmad.nafs.core.health.HealthTrendPoint
import com.ahmad.nafs.core.health.HealthTrendSummary
import com.ahmad.nafs.core.health.HealthTrends
import com.ahmad.nafs.ui.components.NafsCard
import com.ahmad.nafs.ui.components.NafsChoiceChip
import com.ahmad.nafs.ui.components.NafsIconBadge
import com.ahmad.nafs.ui.components.NafsSecondaryButton
import com.ahmad.nafs.ui.components.NafsSectionTitle
import com.ahmad.nafs.ui.components.NafsSegmented
import com.ahmad.nafs.ui.theme.Nafs
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Trends: a 7/30-day switch, the signal to show, and one chart of saved days. */
@OptIn(ExperimentalLayoutApi::class)
internal fun LazyListScope.healthTrendItems(state: HealthUiState, actions: HealthActions) {
    item(key = "trends-title") {
        NafsSectionTitle(
            "Trends",
            Modifier.padding(top = 16.dp),
            supporting = "Only saved days are shown. Tap a day to read its value.",
        )
    }
    item(key = "trends-range") {
        NafsSegmented(
            options = listOf("7 days", "30 days"),
            selectedIndex = if (state.trends.rangeDays == HealthTrends.MAX_DAYS) 1 else 0,
            onSelect = { index -> actions.onTrendRangeChange(if (index == 1) HealthTrends.MAX_DAYS else HealthDays.WINDOW) },
        )
    }
    item(key = "trends-metric") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HealthTrendMetric.entries.forEach { metric ->
                NafsChoiceChip(
                    text = metric.label,
                    selected = state.trends.metric == metric,
                    onClick = { actions.onTrendMetricChange(metric) },
                )
            }
        }
    }
    item(key = "trends-chart") { TrendCard(state, actions.onRetryTrends) }
}

private class TrendLook(val icon: ImageVector, val tint: Color, val bars: Boolean)

@Composable
private fun trendLook(metric: HealthTrendMetric): TrendLook {
    val c = Nafs.colors
    return when (metric) {
        HealthTrendMetric.WATER -> TrendLook(Icons.Rounded.WaterDrop, c.tasks, bars = true)
        HealthTrendMetric.STEPS -> TrendLook(Icons.AutoMirrored.Rounded.DirectionsWalk, c.habits, bars = true)
        HealthTrendMetric.SLEEP -> TrendLook(Icons.Rounded.Bedtime, c.deen, bars = true)
        HealthTrendMetric.WEIGHT -> TrendLook(Icons.Rounded.MonitorWeight, c.gold, bars = false)
        HealthTrendMetric.MOOD -> TrendLook(Icons.Rounded.Mood, c.reflect, bars = false)
    }
}

@Composable
private fun TrendCard(state: HealthUiState, onRetry: () -> Unit) {
    val trends = state.trends
    val metric = trends.metric
    val look = trendLook(metric)
    val end = trends.end ?: state.today
    val points = remember(trends.logs, metric, end, trends.rangeDays) {
        HealthTrends.series(trends.logs, metric, end, trends.rangeDays)
    }
    val summary = remember(points, metric, state.goals) { HealthTrends.summary(points, metric, state.goals) }
    var selected by remember(metric, trends.rangeDays, end) { mutableStateOf<Int?>(null) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val rangeLabel = if (trends.rangeDays == HealthTrends.MAX_DAYS) "Last 30 days" else "Last 7 days"

    NafsCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NafsIconBadge(look.icon, look.tint, size = 40.dp, iconSize = 20.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(metric.label, style = MaterialTheme.typography.titleMedium)
                Text(rangeLabel, style = MaterialTheme.typography.bodySmall, color = muted)
            }
            headline(metric, summary)?.let { (value, label) ->
                Column(horizontalAlignment = Alignment.End) {
                    Text(value, style = MaterialTheme.typography.headlineSmall)
                    Text(label, style = MaterialTheme.typography.labelSmall, color = muted)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        when {
            !trends.hasLoaded && trends.error == null -> Box(
                Modifier
                    .fillMaxWidth()
                    .height(CHART_HEIGHT)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                    .semantics { contentDescription = "Loading trends" },
            )
            !trends.hasLoaded -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TrendNote(Icons.Rounded.CloudOff, trends.error.orEmpty())
                NafsSecondaryButton("Try again", onClick = onRetry, modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.Refresh)
            }
            summary.loggedDays == 0 -> Box(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    emptyText(metric, trends.rangeDays, trends.isOffline),
                    Modifier.padding(horizontal = 24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                    textAlign = TextAlign.Center,
                )
            }
            else -> {
                Text(
                    selected?.let { index -> pointText(metric, points[index]) } ?: summaryText(metric, summary, state.goals),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
                Spacer(Modifier.height(10.dp))
                TrendChart(
                    metric = metric,
                    points = points,
                    goals = state.goals,
                    tint = look.tint,
                    bars = look.bars,
                    selected = selected,
                    onSelect = { index -> selected = if (selected == index) null else index },
                    description = "${metric.label}, ${rangeLabel.lowercase(Locale.ENGLISH)}. ${summaryText(metric, summary, state.goals)}",
                )
                Spacer(Modifier.height(6.dp))
                AxisDates(points, state.today)
                TrendKey(metric, state.goals, look.tint)
            }
        }
        if (trends.hasLoaded && trends.isOffline) {
            Spacer(Modifier.height(12.dp))
            TrendNote(Icons.Rounded.CloudOff, "Offline · showing only days saved on this device.")
        } else if (trends.hasLoaded && trends.error != null) {
            Spacer(Modifier.height(12.dp))
            TrendNote(Icons.Rounded.CloudOff, "Trends couldn't refresh. Showing the days loaded earlier.")
        }
    }
}

@Composable
private fun TrendNote(icon: ImageVector, text: String) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = muted, modifier = Modifier.size(15.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = muted)
    }
}

/**
 * One series per chart, so no legend box: bars for daily amounts, a 2 dp line with ringed dots for weight and
 * mood. Grid and reference lines are hairlines; tapping a day selects it and the line above reads its value.
 */
@Composable
private fun TrendChart(
    metric: HealthTrendMetric,
    points: List<HealthTrendPoint>,
    goals: HealthGoals,
    tint: Color,
    bars: Boolean,
    selected: Int?,
    onSelect: (Int) -> Unit,
    description: String,
) {
    val scheme = MaterialTheme.colorScheme
    val scale = remember(points, metric, goals) { trendScale(metric, points, goals) }
    val grid = scheme.outlineVariant.copy(alpha = 0.7f)
    val reference = scheme.onSurfaceVariant.copy(alpha = 0.55f)
    val ring = scheme.surfaceContainer
    val band = tint.copy(alpha = if (Nafs.colors.isDark) 0.14f else 0.10f)
    val currentOnSelect by rememberUpdatedState(onSelect)
    Box(Modifier.fillMaxWidth().height(CHART_HEIGHT)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = description }
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        val left = AXIS_WIDTH.toPx()
                        val slot = (size.width - left) / points.size
                        if (offset.x >= left && slot > 0f) {
                            currentOnSelect(((offset.x - left) / slot).toInt().coerceIn(0, points.lastIndex))
                        }
                    }
                },
        ) {
            val left = AXIS_WIDTH.toPx()
            val top = LABEL_SPACE.toPx()
            val bottom = size.height - LABEL_SPACE.toPx()
            val slot = (size.width - left) / points.size
            val hairline = 1.dp.toPx()
            fun y(value: Double): Float =
                bottom - ((value - scale.low) / (scale.high - scale.low)).toFloat().coerceIn(0f, 1f) * (bottom - top)

            drawLine(grid, Offset(left, top), Offset(size.width, top), hairline)
            drawLine(grid, Offset(left, (top + bottom) / 2f), Offset(size.width, (top + bottom) / 2f), hairline)
            drawLine(grid, Offset(left, bottom), Offset(size.width, bottom), hairline)

            if (metric == HealthTrendMetric.SLEEP) {
                val bandTop = y(HealthGoals.SLEEP_MAX_MINUTES / 60.0)
                val bandBottom = y(HealthGoals.SLEEP_MIN_MINUTES / 60.0)
                drawRect(band, Offset(left, bandTop), Size(size.width - left, bandBottom - bandTop))
            }
            goalValue(metric, goals)?.let { goal ->
                val goalY = y(goal)
                drawLine(reference, Offset(left, goalY), Offset(size.width, goalY), hairline)
            }
            selected?.let { index ->
                val x = left + slot * (index + 0.5f)
                drawLine(reference, Offset(x, top), Offset(x, bottom), hairline)
            }

            if (bars) {
                val barWidth = min(slot * 0.62f, 24.dp.toPx())
                val radius = 4.dp.toPx()
                points.forEachIndexed { index, point ->
                    val value = point.value ?: return@forEachIndexed
                    val centre = left + slot * (index + 0.5f)
                    val barTop = min(y(value), bottom - 2.dp.toPx())
                    val corner = CornerRadius(min(radius, min(barWidth / 2f, bottom - barTop)))
                    val path = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = centre - barWidth / 2f,
                                top = barTop,
                                right = centre + barWidth / 2f,
                                bottom = bottom,
                                topLeftCornerRadius = corner,
                                topRightCornerRadius = corner,
                                bottomRightCornerRadius = CornerRadius.Zero,
                                bottomLeftCornerRadius = CornerRadius.Zero,
                            ),
                        )
                    }
                    drawPath(path, if (selected == null || selected == index) tint else tint.copy(alpha = 0.35f))
                }
            } else {
                val logged = points.withIndex().filter { it.value.value != null }
                val line = Path()
                logged.forEachIndexed { order, indexed ->
                    val x = left + slot * (indexed.index + 0.5f)
                    val pointY = y(indexed.value.value ?: 0.0)
                    if (order == 0) line.moveTo(x, pointY) else line.lineTo(x, pointY)
                }
                if (logged.size > 1) {
                    drawPath(line, tint, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                val dot = if (points.size > HealthDays.WINDOW) 3.dp.toPx() else 4.dp.toPx()
                logged.forEach { indexed ->
                    val centre = Offset(left + slot * (indexed.index + 0.5f), y(indexed.value.value ?: 0.0))
                    val radius = if (selected == indexed.index) dot + 1.5.dp.toPx() else dot
                    drawCircle(ring, radius + 2.dp.toPx(), centre)
                    drawCircle(tint, radius, centre)
                }
            }
        }
        Text(
            axisLabel(metric, scale.high),
            Modifier.align(Alignment.TopStart).width(AXIS_WIDTH - 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            axisLabel(metric, scale.low),
            Modifier.align(Alignment.BottomStart).width(AXIS_WIDTH - 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun AxisDates(points: List<HealthTrendPoint>, today: LocalDate) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.labelSmall
    Row(Modifier.fillMaxWidth().padding(start = AXIS_WIDTH)) {
        if (points.size <= HealthDays.WINDOW) {
            points.forEach { point ->
                Text(
                    if (point.date == today) "Today" else point.date.format(SHORT_WEEKDAY),
                    Modifier.weight(1f),
                    style = style,
                    color = muted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        } else {
            val first = points.first().date
            val middle = points[points.size / 2].date
            val last = points.last().date
            Text(first.format(DAY_MONTH), style = style, color = muted)
            Spacer(Modifier.weight(1f))
            Text(middle.format(DAY_MONTH), style = style, color = muted)
            Spacer(Modifier.weight(1f))
            Text(if (last == today) "Today" else last.format(DAY_MONTH), style = style, color = muted)
        }
    }
}

/** A key only for the reference marks; the single series is named by the card title. */
@Composable
private fun TrendKey(metric: HealthTrendMetric, goals: HealthGoals, tint: Color) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val label = when (metric) {
        HealthTrendMetric.WATER -> "Goal ${goals.waterGlasses} glasses"
        HealthTrendMetric.STEPS -> "Goal ${formatCount(goals.steps)} steps"
        HealthTrendMetric.SLEEP -> "7–9 hour guide"
        HealthTrendMetric.WEIGHT, HealthTrendMetric.MOOD -> return
    }
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (metric == HealthTrendMetric.SLEEP) {
            Box(Modifier.size(width = 16.dp, height = 10.dp).clip(MaterialTheme.shapes.extraSmall).background(tint.copy(alpha = 0.18f)))
        } else {
            Box(Modifier.size(width = 16.dp, height = 1.dp).background(muted))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = muted)
    }
}

// ─── Scale and text ──────────────────────────────────────────────────────────

private data class TrendScale(val low: Double, val high: Double)

private fun goalValue(metric: HealthTrendMetric, goals: HealthGoals): Double? = when (metric) {
    HealthTrendMetric.WATER -> goals.waterGlasses.toDouble()
    HealthTrendMetric.STEPS -> goals.steps.toDouble()
    else -> null
}

private fun trendScale(metric: HealthTrendMetric, points: List<HealthTrendPoint>, goals: HealthGoals): TrendScale {
    val values = points.mapNotNull { it.value }
    val highest = values.maxOrNull() ?: 0.0
    return when (metric) {
        HealthTrendMetric.WATER -> TrendScale(0.0, roundUp(max(highest, goals.waterGlasses.toDouble()), 2.0))
        HealthTrendMetric.STEPS -> TrendScale(0.0, roundUp(max(highest, goals.steps.toDouble()), 2_000.0))
        HealthTrendMetric.SLEEP -> TrendScale(0.0, roundUp(max(highest, HealthGoals.SLEEP_MAX_MINUTES / 60.0 + 1.0), 2.0))
        HealthTrendMetric.MOOD -> TrendScale(0.0, 10.0)
        HealthTrendMetric.WEIGHT -> {
            val lowest = values.minOrNull() ?: 0.0
            val low = floor(lowest - 1.0).coerceAtLeast(0.0)
            TrendScale(low, max(ceil(highest + 1.0), low + 2.0))
        }
    }
}

private fun roundUp(value: Double, step: Double): Double = max(step, ceil(value / step) * step)

private fun headline(metric: HealthTrendMetric, summary: HealthTrendSummary): Pair<String, String>? = when (metric) {
    HealthTrendMetric.WEIGHT -> summary.latest?.value?.let { oneDecimal(it) to "latest kg" }
    HealthTrendMetric.WATER -> summary.average?.let { oneDecimal(it) to "avg glasses" }
    HealthTrendMetric.STEPS -> summary.average?.let { formatCount(it.roundToInt()) to "avg steps" }
    HealthTrendMetric.SLEEP -> summary.average?.let { oneDecimal(it) to "avg hours" }
    HealthTrendMetric.MOOD -> summary.average?.let { oneDecimal(it) to "avg of 10" }
}

private fun summaryText(metric: HealthTrendMetric, summary: HealthTrendSummary, goals: HealthGoals): String {
    val logged = "${summary.loggedDays} of ${summary.days} days logged"
    val goalDays = summary.goalDays ?: 0
    return when (metric) {
        HealthTrendMetric.WATER -> "Goal of ${goals.waterGlasses} met on ${days(goalDays)} · $logged"
        HealthTrendMetric.STEPS -> "Goal of ${formatCount(goals.steps)} met on ${days(goalDays)} · $logged"
        HealthTrendMetric.SLEEP -> "In the 7–9 hour guide on ${days(goalDays)} · $logged"
        HealthTrendMetric.WEIGHT -> {
            val change = summary.change
            val since = summary.first?.date?.format(SHORT_DATE)
            if (change == null || since == null) {
                logged
            } else {
                val sign = when {
                    change > 0.05 -> "+"
                    change < -0.05 -> "−"
                    else -> "±"
                }
                "$sign${oneDecimal(abs(change))} kg since $since · $logged"
            }
        }
        HealthTrendMetric.MOOD -> logged
    }
}

private fun pointText(metric: HealthTrendMetric, point: HealthTrendPoint): String {
    val day = point.date.format(SHORT_DATE)
    val value = point.value ?: return "$day · nothing saved"
    return "$day · ${valueText(metric, value)}"
}

private fun valueText(metric: HealthTrendMetric, value: Double): String = when (metric) {
    HealthTrendMetric.WATER -> value.roundToInt().let { "$it ${if (it == 1) "glass" else "glasses"}" }
    HealthTrendMetric.STEPS -> "${formatCount(value.roundToInt())} steps"
    HealthTrendMetric.SLEEP -> "${oneDecimal(value)} hours" +
        if (HealthGoalRules.inSleepGuide(value)) " · in the guide" else ""
    HealthTrendMetric.WEIGHT -> "${oneDecimal(value)} kg"
    HealthTrendMetric.MOOD -> "${value.roundToInt()} of 10"
}

private fun emptyText(metric: HealthTrendMetric, rangeDays: Int, offline: Boolean): String {
    val what = when (metric) {
        HealthTrendMetric.WATER -> "No water"
        HealthTrendMetric.STEPS -> "No steps"
        HealthTrendMetric.SLEEP -> "No sleep"
        HealthTrendMetric.WEIGHT -> "No weight"
        HealthTrendMetric.MOOD -> "No mood"
    }
    val place = if (offline) " on this device" else ""
    return "$what saved$place in the last $rangeDays days. Days you log will appear here."
}

private fun days(count: Int): String = if (count == 1) "1 day" else "$count days"

private fun axisLabel(metric: HealthTrendMetric, value: Double): String = when {
    metric == HealthTrendMetric.STEPS && value >= 1_000.0 -> "${oneDecimal(value / 1_000.0)}K"
    else -> oneDecimal(value)
}

private fun oneDecimal(value: Double): String {
    val rounded = (value * 10.0).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else String.format(Locale.ENGLISH, "%.1f", rounded)
}

private val CHART_HEIGHT = 168.dp
private val AXIS_WIDTH = 34.dp
private val LABEL_SPACE = 16.dp
private val SHORT_WEEKDAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
