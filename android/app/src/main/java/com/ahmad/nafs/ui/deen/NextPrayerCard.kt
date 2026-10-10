package com.ahmad.nafs.ui.deen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahmad.nafs.core.deen.NextPrayer
import com.ahmad.nafs.core.deen.NextPrayerLookup
import com.ahmad.nafs.ui.components.NafsCard
import com.ahmad.nafs.ui.components.NafsEyebrow
import com.ahmad.nafs.ui.components.NafsIconBadge
import com.ahmad.nafs.ui.components.NafsProgressBar
import com.ahmad.nafs.ui.theme.Nafs
import java.time.ZonedDateTime

/**
 * Compact next prayer for Deen and Home: name, Arabic name, start time and a live countdown from [now]. After Isha it
 * shows tomorrow's Fajr. Tapping opens Prayer times.
 */
@Composable
fun NextPrayerCard(next: NextPrayer, now: ZonedDateTime, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Nafs.colors
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val remaining = next.remaining(now)
    val name = next.prayer.label
    val time = PrayerDisplay.time(next.time)
    val countdown = if (remaining.isZero) "Starting now" else "in ${PrayerDisplay.countdown(remaining)}"
    val spoken = buildString {
        append(if (next.isTomorrow) "Next prayer, tomorrow: " else "Next prayer: ")
        append("$name at $time, ")
        append(if (remaining.isZero) "starting now." else "in ${PrayerDisplay.spokenCountdown(remaining)}.")
        append(" Opens prayer times.")
    }
    NafsCard(
        modifier = modifier.semantics { contentDescription = spoken },
        onClick = onClick,
        contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 10.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            NafsIconBadge(PrayerDisplay.icon(next.prayer), c.deen)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                NafsEyebrow(if (next.isTomorrow) "Next prayer · tomorrow" else "Next prayer", color = c.deen)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(name, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                    Text(
                        PrayerDisplay.arabic(next.prayer),
                        style = PrayerDisplay.ArabicSmall,
                        color = c.deen,
                        maxLines = 1,
                        modifier = Modifier.padding(bottom = 2.dp).clearAndSetSemantics { },
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(time, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), maxLines = 1)
                Text(countdown, style = MaterialTheme.typography.labelMedium, color = muted, maxLines = 1)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = muted)
        }
        next.progress(now)?.let { fraction ->
            NafsProgressBar(fraction, color = c.deen, height = 4.dp, modifier = Modifier.padding(top = 12.dp, end = 6.dp))
        }
    }
}

/** Shown in place of [NextPrayerCard] while no area or city is saved. */
@Composable
fun PrayerTimesSetupCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    PrayerTimesPromptCard(
        icon = Icons.Rounded.AccessTime,
        title = "Set up prayer times",
        body = "Use your location or choose a city to see the next prayer here.",
        onClick = onClick,
        modifier = modifier,
    )
}

/** Shown when a place is saved but its times do not cover today, for example after some days offline. */
@Composable
fun PrayerTimesRefreshCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    PrayerTimesPromptCard(
        icon = Icons.Rounded.Refresh,
        title = "Update prayer times",
        body = "Today's times aren't saved yet. Open Prayer times to load them.",
        onClick = onClick,
        modifier = modifier,
    )
}

/** The right card for a [lookup]: the next prayer, a setup prompt or a refresh prompt. Nothing while it loads. */
@Composable
fun NextPrayerSlot(lookup: NextPrayerLookup?, now: ZonedDateTime, onOpenPrayerTimes: () -> Unit, modifier: Modifier = Modifier) {
    when (lookup) {
        null -> Unit
        is NextPrayerLookup.Found -> NextPrayerCard(lookup.next, now, onOpenPrayerTimes, modifier)
        NextPrayerLookup.NotSetUp -> PrayerTimesSetupCard(onOpenPrayerTimes, modifier)
        NextPrayerLookup.NeedsRefresh -> PrayerTimesRefreshCard(onOpenPrayerTimes, modifier)
    }
}

@Composable
private fun PrayerTimesPromptCard(icon: ImageVector, title: String, body: String, onClick: () -> Unit, modifier: Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    NafsCard(modifier = modifier, onClick = onClick, contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 10.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            NafsIconBadge(icon, Nafs.colors.deen)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = muted)
        }
    }
}
