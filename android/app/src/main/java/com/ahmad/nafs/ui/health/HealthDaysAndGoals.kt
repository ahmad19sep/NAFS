package com.ahmad.nafs.ui.health

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ahmad.nafs.core.health.HealthGoals
import com.ahmad.nafs.ui.components.NafsCard
import com.ahmad.nafs.ui.components.NafsIconBadge
import com.ahmad.nafs.ui.components.NafsSecondaryButton
import com.ahmad.nafs.ui.theme.Nafs
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// ─── Day switcher ────────────────────────────────────────────────────────────

/** Today and the six days before it. A dot marks days with a saved entry. */
@Composable
internal fun HealthDaySwitcher(state: HealthUiState, onSelect: (LocalDate) -> Unit) {
    val saved = state.savedDays
    val enabled = !state.isSaving && !state.isLoading
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        state.days.forEach { date ->
            DayCell(
                date = date,
                isToday = date == state.today,
                isSelected = date == state.selectedDate,
                hasEntry = date in saved,
                enabled = enabled,
                onClick = { onSelect(date) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    hasEntry: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val content = if (isSelected) scheme.onPrimary else scheme.onSurface
    val muted = if (isSelected) scheme.onPrimary.copy(alpha = 0.8f) else scheme.onSurfaceVariant
    val description = buildString {
        append(if (isToday) "Today, " else "")
        append(date.format(SPOKEN_DAY))
        append(if (hasEntry) ", entry saved" else ", no saved entry")
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.semantics {
            selected = isSelected
            role = Role.Tab
            contentDescription = description
        },
        shape = MaterialTheme.shapes.small,
        color = if (isSelected) scheme.primary else scheme.surfaceContainer,
        contentColor = content,
        border = if (isSelected) null else BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column(
            Modifier.padding(vertical = 8.dp).clearAndSetSemantics {},
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                if (isToday) "Today" else date.format(SHORT_WEEKDAY),
                style = MaterialTheme.typography.labelSmall,
                color = muted,
                maxLines = 1,
            )
            Text("${date.dayOfMonth}", style = MaterialTheme.typography.titleMedium, color = content)
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            !hasEntry -> Color.Transparent
                            isSelected -> scheme.onPrimary
                            else -> Nafs.colors.health
                        },
                    ),
            )
        }
    }
}

/** Shown in place of the form while another day's entry loads, so stale values can't be edited or saved. */
@Composable
internal fun HealthDayLoading(state: HealthUiState) {
    NafsCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Nafs.colors.health)
            Text(
                "Loading ${HealthDays.long(state.selectedDate)}…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun HealthDayError(state: HealthUiState, message: String, onRetry: () -> Unit) {
    NafsCard {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NafsIconBadge(Icons.Rounded.CloudOff, MaterialTheme.colorScheme.error, size = 40.dp, iconSize = 20.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${HealthDays.long(state.selectedDate)} couldn't be opened", style = MaterialTheme.typography.titleSmall)
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(14.dp))
        NafsSecondaryButton("Try again", onClick = onRetry, modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.Refresh)
    }
}

@Composable
internal fun DiscardChangesDialog(state: HealthUiState, date: LocalDate, actions: HealthActions) {
    val sameDay = date == state.selectedDate
    AlertDialog(
        onDismissRequest = actions.onCancelDiscard,
        title = { Text("Discard unsaved changes?") },
        text = {
            Text(
                if (sameDay) {
                    "Reloading replaces what you changed on ${HealthDays.long(state.selectedDate)} with the saved entry."
                } else {
                    "Your changes to ${HealthDays.long(state.selectedDate)} haven't been saved. " +
                        "Opening ${HealthDays.long(date)} discards them."
                },
            )
        },
        confirmButton = {
            TextButton(onClick = actions.onConfirmDiscard) {
                Text("Discard", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = actions.onCancelDiscard) { Text("Keep editing") } },
    )
}

// ─── Goals ───────────────────────────────────────────────────────────────────

@Composable
internal fun HealthGoalDialog(state: HealthUiState, actions: HealthActions) {
    val c = Nafs.colors
    AlertDialog(
        onDismissRequest = actions.onCloseGoalEditor,
        icon = { Icon(Icons.Rounded.Flag, contentDescription = null, tint = c.health) },
        title = { Text("Daily goals") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "Goals guide the progress on your tiles and trends. They're kept on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                GoalStepper(
                    icon = Icons.Rounded.WaterDrop,
                    tint = c.tasks,
                    title = "Water",
                    value = "${state.goalWaterInput} glasses",
                    hint = "${HealthGoals.MIN_WATER}–${HealthGoals.MAX_WATER} glasses · 8 is about 2 litres",
                    canDecrease = state.goalWaterInput > HealthGoals.MIN_WATER,
                    canIncrease = state.goalWaterInput < HealthGoals.MAX_WATER,
                    onDecrease = { actions.onWaterGoalChange(-1) },
                    onIncrease = { actions.onWaterGoalChange(1) },
                )
                GoalStepper(
                    icon = Icons.AutoMirrored.Rounded.DirectionsWalk,
                    tint = c.habits,
                    title = "Steps",
                    value = formatCount(state.goalStepsInput),
                    hint = "Changes by ${HealthGoals.STEP_INCREMENT} · default ${formatCount(HealthGoals.DEFAULT_STEPS)}",
                    canDecrease = state.goalStepsInput > HealthGoals.MIN_STEPS,
                    canIncrease = state.goalStepsInput < HealthGoals.MAX_STEPS,
                    onDecrease = { actions.onStepsGoalChange(-HealthGoals.STEP_INCREMENT) },
                    onIncrease = { actions.onStepsGoalChange(HealthGoals.STEP_INCREMENT) },
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NafsIconBadge(Icons.Rounded.Bedtime, c.deen, size = 36.dp, iconSize = 18.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Sleep", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "A 7–9 hour guide for most adults; feedback stays gentle.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(onClick = actions.onResetGoals, modifier = Modifier.padding(top = 2.dp)) {
                    Text("Reset to defaults")
                }
            }
        },
        confirmButton = { TextButton(onClick = actions.onSaveGoals) { Text("Save goals") } },
        dismissButton = { TextButton(onClick = actions.onCloseGoalEditor) { Text("Cancel") } },
    )
}

@Composable
private fun GoalStepper(
    icon: ImageVector,
    tint: Color,
    title: String,
    value: String,
    hint: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NafsIconBadge(icon, tint, size = 36.dp, iconSize = 18.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepperButton(Icons.Rounded.Remove, "Lower $title goal", canDecrease, onDecrease)
            Text(
                value,
                Modifier.widthIn(min = 56.dp).semantics { contentDescription = "$title goal $value" },
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            StepperButton(Icons.Rounded.Add, "Raise $title goal", canIncrease, onIncrease)
        }
    }
}

@Composable
private fun StepperButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(36.dp),
        shape = CircleShape,
        color = if (enabled) scheme.surfaceContainerHigh else scheme.onSurface.copy(alpha = 0.05f),
        contentColor = if (enabled) scheme.onSurface else scheme.onSurface.copy(alpha = 0.3f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(18.dp))
        }
    }
}

private val SHORT_WEEKDAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
private val SPOKEN_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)
