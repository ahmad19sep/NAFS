package com.ahmad.nafs.ui.challenges

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ahmad.nafs.core.challenges.Challenge
import com.ahmad.nafs.core.challenges.ChallengeCheckIn
import com.ahmad.nafs.core.challenges.ChallengeHistoryEntry
import com.ahmad.nafs.core.challenges.history
import com.ahmad.nafs.core.challenges.outcomeSummary
import com.ahmad.nafs.core.challenges.sadqaTally
import com.ahmad.nafs.domain.challenges.ChallengeFrequency
import com.ahmad.nafs.domain.challenges.ChallengePeriod
import com.ahmad.nafs.domain.challenges.ChallengePeriodStatus
import com.ahmad.nafs.domain.challenges.ChallengeStatus
import com.ahmad.nafs.domain.challenges.ChallengeTimeline
import com.ahmad.nafs.domain.challenges.unitCount
import com.ahmad.nafs.domain.challenges.unitName
import com.ahmad.nafs.ui.components.NafsEyebrow
import com.ahmad.nafs.ui.components.PublicPhoto
import com.ahmad.nafs.ui.theme.Nafs
import java.time.LocalDate

/** At most this many missed periods are listed with their sadqa; the totals always cover all of them. */
private const val SADQA_ROWS = 10

/** The row that opens and closes a card's check-in history. */
@Composable
internal fun ChallengeHistoryToggle(timeline: ChallengeTimeline, open: Boolean, onToggle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onToggle,
        shape = MaterialTheme.shapes.small,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Row(
            Modifier.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Text("Check-in history", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text(
                "${timeline.doneCount} of ${timeline.frequency.unitCount(timeline.periods.size)} done",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
            Icon(
                if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (open) "Hide check-in history" else "Show check-in history",
                tint = scheme.onSurfaceVariant,
            )
        }
    }
}

/** Every period of the attempt, its proof photos and, for a pledged challenge, the sadqa of each missed period. */
@Composable
internal fun ChallengeHistoryPanel(
    challenge: Challenge,
    timeline: ChallengeTimeline,
    pending: Boolean,
    onSadqaPaidChange: (LocalDate, Boolean) -> Unit,
    onOpenPhoto: (String, String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val entries = remember(challenge, timeline) { challenge.history(timeline) }
    val proofs = remember(entries) { entries.flatMap(ChallengeHistoryEntry::proofs).sortedByDescending(ChallengeCheckIn::date) }
    val today = timeline.today
    val frequency = challenge.frequency
    val units = frequency.unitName() + "s"
    val tally = buildList {
        add("${timeline.doneCount} done")
        if (timeline.missedCount > 0) add("${timeline.missedCount} missed")
        if (timeline.openCount > 0) add("${timeline.openCount} open")
        if (timeline.futureCount > 0) add("${timeline.futureCount} to go")
    }.joinToString(" · ")

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PeriodGrid(timeline.periods, frequency, description = "Check-in history: $tally")
        HistoryLegend()
        Text(
            when {
                !timeline.hasStarted -> "Starts ${dayLabel(challenge.startDate, today)}. Nothing to check in yet."
                timeline.doneCount + timeline.missedCount == 0 -> "No check-ins yet. Your first ${frequency.unitName()} is open."
                else -> tally
            },
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        if (proofs.isNotEmpty()) {
            ProofStrip(proofs, today, onOpenPhoto)
        } else if (challenge.requiresPhoto && timeline.hasStarted) {
            Text("Proof photos appear here after each check-in.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        challenge.sadqaAmount?.let { amount ->
            SadqaHistory(entries, amount, challenge.sadqaCurrency, frequency, units, today, pending, onSadqaPaidChange)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeriodGrid(periods: List<ChallengePeriod>, frequency: ChallengeFrequency, description: String) {
    // Few periods (weeks, months, years) get labelled tiles; a long daily challenge gets a compact grid.
    val tile = when {
        periods.size <= 16 -> 36.dp
        periods.size <= 120 -> 22.dp
        else -> 14.dp
    }
    val gap = if (tile > 14.dp) 6.dp else 4.dp
    FlowRow(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        periods.forEach { period ->
            PeriodTile(period.status, tile, label = if (tile >= 36.dp) periodTileLabel(period, frequency) else null)
        }
    }
}

@Composable
private fun PeriodTile(status: ChallengePeriodStatus, size: Dp, label: String?) {
    val c = Nafs.colors
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(size * 0.28f)
    val (fill, content) = when (status) {
        ChallengePeriodStatus.DONE -> c.success to scheme.onPrimary
        ChallengePeriodStatus.MISSED -> scheme.errorContainer to scheme.onErrorContainer
        ChallengePeriodStatus.PENDING -> Color.Transparent to c.challenges
        ChallengePeriodStatus.FUTURE -> scheme.onSurface.copy(alpha = 0.06f) to scheme.onSurfaceVariant
    }
    val outline = if (status == ChallengePeriodStatus.PENDING) {
        Modifier.border(2.dp, c.challenges, shape)
    } else {
        Modifier
    }
    Box(Modifier.size(size).clip(shape).background(fill).then(outline), contentAlignment = Alignment.Center) {
        label?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = content, maxLines = 1) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryLegend() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LegendItem(ChallengePeriodStatus.DONE, "Done")
        LegendItem(ChallengePeriodStatus.MISSED, "Missed")
        LegendItem(ChallengePeriodStatus.PENDING, "Open")
        LegendItem(ChallengePeriodStatus.FUTURE, "Upcoming")
    }
}

@Composable
private fun LegendItem(status: ChallengePeriodStatus, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PeriodTile(status, 12.dp, label = null)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProofStrip(proofs: List<ChallengeCheckIn>, today: LocalDate, onOpenPhoto: (String, String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NafsEyebrow("Proof photos · ${proofs.size}")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(proofs, key = ChallengeCheckIn::id) { proof ->
                proof.photoUrl?.let { url -> ProofThumb(url, proof.date, today, onOpenPhoto) }
            }
        }
    }
}

@Composable
private fun ProofThumb(url: String, date: LocalDate, today: LocalDate, onOpenPhoto: (String, String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val label = "Proof · ${dayLabel(date, today)}"
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            onClick = { onOpenPhoto(url, label) },
            shape = MaterialTheme.shapes.medium,
            color = scheme.surfaceContainerHigh,
            modifier = Modifier.size(72.dp).semantics { contentDescription = "Open proof photo for ${dayLabel(date, today)}" },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Image, contentDescription = null, tint = scheme.onSurfaceVariant.copy(alpha = 0.5f))
                PublicPhoto(url, "Proof photo", Modifier.fillMaxSize(), crop = true)
            }
        }
        Text(shortDate(date, today), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
    }
}

@Composable
private fun SadqaHistory(
    entries: List<ChallengeHistoryEntry>,
    amount: Double,
    currency: String,
    frequency: ChallengeFrequency,
    units: String,
    today: LocalDate,
    pending: Boolean,
    onSadqaPaidChange: (LocalDate, Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val missed = entries.filter(ChallengeHistoryEntry::isMissed).sortedByDescending { it.period.start }
    val tally = entries.sadqaTally()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        NafsEyebrow("Sadqa · ${formatMoney(amount, currency)} per missed ${frequency.unitName()}")
        if (missed.isEmpty()) {
            Text("No missed $units, so no sadqa is due.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        } else {
            Text(
                "${formatMoney(amount * tally.owed, currency)} owed · ${formatMoney(amount * tally.paid, currency)} paid",
                style = MaterialTheme.typography.titleSmall,
            )
            missed.take(SADQA_ROWS).forEach { entry ->
                SadqaRow(
                    label = periodLabel(entry.period, frequency, today),
                    amount = formatMoney(amount, currency),
                    paid = entry.sadqaPaid,
                    pending = pending,
                    // A recorded miss carries the sadqa; a period that just closed gets a miss recorded on its first day.
                    onToggle = { paid -> onSadqaPaidChange(entry.miss?.date ?: entry.period.start, paid) },
                )
            }
            if (missed.size > SADQA_ROWS) {
                Text(
                    "Showing the latest $SADQA_ROWS of ${missed.size} missed $units.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SadqaRow(label: String, amount: String, paid: Boolean, pending: Boolean, onToggle: (Boolean) -> Unit) {
    val c = Nafs.colors
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Rounded.Favorite, contentDescription = null, tint = if (paid) c.success else c.deen, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (paid) "Paid · $amount" else "Owed · $amount",
                style = MaterialTheme.typography.bodySmall,
                color = if (paid) c.success else scheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = { onToggle(!paid) },
            enabled = !pending,
            colors = ButtonDefaults.textButtonColors(contentColor = if (paid) scheme.onSurfaceVariant else c.deen),
        ) { Text(if (paid) "Undo" else "Mark paid", style = MaterialTheme.typography.labelLarge) }
    }
}

/** The finished state of a card: complete, or ended with the final period missed. */
@Composable
internal fun ChallengeOutcomeWell(challenge: Challenge, timeline: ChallengeTimeline, today: LocalDate) {
    val c = Nafs.colors
    val completed = challenge.status == ChallengeStatus.COMPLETED
    val unit = challenge.frequency.unitName()
    val title = when {
        completed && timeline.doneCount == timeline.periods.size -> "Every $unit kept"
        completed -> "Challenge complete"
        timeline.last.status == ChallengePeriodStatus.MISSED -> "Ended ${shortDate(timeline.endDate, today)} · final $unit missed"
        else -> "This attempt has ended"
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (completed) c.successContainer else c.warningContainer,
        contentColor = if (completed) c.onSuccessContainer else c.onWarningContainer,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (completed) Icons.Rounded.MilitaryTech else Icons.Rounded.Flag, contentDescription = null, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(challenge.outcomeSummary(timeline), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** A proof photo at full size. While it loads only the placeholder shows; if it fails, "Photo unavailable" sits below it. */
@Composable
internal fun ProofPhotoDialog(photo: ChallengePhotoView, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(photo.label) },
        text = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(scheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Image,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(bottom = 56.dp).size(40.dp),
                )
                PublicPhoto(photo.url, photo.label, Modifier.fillMaxWidth().heightIn(max = 460.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
