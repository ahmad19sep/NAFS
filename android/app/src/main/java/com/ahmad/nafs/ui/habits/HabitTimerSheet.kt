package com.ahmad.nafs.ui.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahmad.nafs.core.habits.Habit
import com.ahmad.nafs.domain.habits.HabitTimerRules
import com.ahmad.nafs.domain.habits.HabitTimerState
import com.ahmad.nafs.ui.components.NafsBanner
import com.ahmad.nafs.ui.components.NafsBannerKind
import com.ahmad.nafs.ui.components.NafsEmojiBadge
import com.ahmad.nafs.ui.components.NafsEyebrow
import com.ahmad.nafs.ui.components.NafsPrimaryButton
import com.ahmad.nafs.ui.components.NafsProgressRing
import com.ahmad.nafs.ui.components.NafsSecondaryButton
import com.ahmad.nafs.ui.components.NafsSpace
import com.ahmad.nafs.ui.theme.Nafs

/**
 * Focus timer for a duration habit: a ring toward the day's minutes target, the session's elapsed time, and start,
 * pause, resume and finish. Time comes from the session's timestamps at [nowMillis], so it is stateless and a paused
 * session renders the same every time.
 */
@Composable
internal fun HabitTimerContent(
    habit: Habit,
    session: HabitTimerState?,
    loggedMinutes: Int,
    nowMillis: Long,
    isFinishing: Boolean,
    error: String?,
    notice: String?,
    actions: HabitTimerActions,
    onClose: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = Nafs.colors.habits
    val elapsed = session?.elapsedMillis(nowMillis) ?: 0L
    val sessionMinutes = session?.elapsedMinutes(nowMillis) ?: 0
    val running = session?.isRunning == true
    val target = habit.timeTargetMinutes
    val clock = HabitTimerRules.clock(elapsed)
    val todayTotal = HabitTimerRules.minutesAfterSession(loggedMinutes, sessionMinutes)
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = NafsSpace.screen)
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            NafsEmojiBadge(habit.emoji, accent, size = 48.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                NafsEyebrow("Focus timer", color = accent)
                Text(
                    habit.name,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onClose, enabled = !isFinishing) {
                Icon(Icons.Rounded.Close, contentDescription = "Close timer", tint = scheme.onSurfaceVariant)
            }
        }

        notice?.let { message -> NafsBanner(message, kind = NafsBannerKind.INFO) }

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            NafsProgressRing(
                progress = HabitTimerRules.progress(loggedMinutes, elapsed, target),
                size = 224.dp,
                stroke = 14.dp,
                colors = listOf(accent.copy(alpha = 0.55f), accent),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = "${status(session)}, $clock this session, $todayTotal of $target minutes today"
                    },
                ) {
                    Text(
                        status(session),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (running) accent else scheme.onSurfaceVariant,
                    )
                    Text(
                        clock,
                        style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"),
                        maxLines = 1,
                    )
                    Text(
                        "$todayTotal of $target min today",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        Text(
            when {
                session == null && loggedMinutes > 0 -> "$loggedMinutes min already logged today. Start when you begin."
                session == null -> "Start when you begin; the session is added to today's minutes when you finish."
                loggedMinutes > 0 -> "Finish adds this session's whole minutes to the $loggedMinutes already logged today."
                else -> "Finish adds this session's whole minutes to today's log."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        error?.let { message -> NafsBanner(message, kind = NafsBannerKind.ERROR) }

        if (session == null) {
            NafsPrimaryButton(
                text = "Start",
                onClick = actions.onStart,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.PlayArrow,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                NafsSecondaryButton(
                    text = if (running) "Pause" else "Resume",
                    onClick = if (running) actions.onPause else actions.onStart,
                    modifier = Modifier.weight(1f),
                    enabled = !isFinishing,
                    icon = if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                )
                NafsPrimaryButton(
                    text = if (sessionMinutes >= 1) "Finish · $sessionMinutes min" else "Finish",
                    onClick = actions.onFinish,
                    modifier = Modifier.weight(1f),
                    enabled = sessionMinutes >= 1,
                    loading = isFinishing,
                )
            }
            if (sessionMinutes < 1) {
                Text(
                    "Finish logs whole minutes, so it opens once the first minute is up.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = { confirmDiscard = true },
                enabled = !isFinishing,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("Discard session")
            }
        }

        Text(
            "Keeps counting while you use other apps or lock your phone.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (confirmDiscard && session != null) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard this session?") },
            text = { Text("The $clock timed so far won't be logged.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiscard = false
                        actions.onDiscard()
                    },
                ) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep timing") } },
        )
    }
}

private fun status(session: HabitTimerState?): String = when {
    session == null -> "Ready"
    session.isRunning -> "Running"
    else -> "Paused"
}
