package com.ahmad.nafs.ui.health

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahmad.nafs.core.health.CustomHealthMetric
import com.ahmad.nafs.core.health.CustomMetricType
import com.ahmad.nafs.core.health.HealthMeal
import com.ahmad.nafs.core.health.HealthMealRules
import com.ahmad.nafs.core.health.MealItem
import com.ahmad.nafs.ui.components.NafsCard
import com.ahmad.nafs.ui.components.NafsChoiceChip
import com.ahmad.nafs.ui.components.NafsDivider
import com.ahmad.nafs.ui.components.NafsEmojiBadge
import com.ahmad.nafs.ui.components.NafsEyebrow
import com.ahmad.nafs.ui.components.NafsIconBadge
import com.ahmad.nafs.ui.components.NafsProgressBar
import com.ahmad.nafs.ui.components.NafsSectionTitle
import com.ahmad.nafs.ui.components.NafsTextField
import com.ahmad.nafs.ui.theme.Nafs

// ─── Meals ───────────────────────────────────────────────────────────────────

/** Meals section: one card per meal (core first, then extras) and the extra-meal picker. */
internal fun LazyListScope.healthMealItems(state: HealthUiState, actions: HealthActions) {
    item {
        NafsSectionTitle(
            "Meals",
            Modifier.padding(top = 16.dp),
            supporting = "${HealthMealRules.eatenCount(state.meals)} recorded · add only what you ate",
        )
    }
    state.meals.forEach { meal ->
        item {
            MealCard(
                meal = meal,
                draft = state.mealDrafts[meal.id].orEmpty(),
                onDraftChange = { value -> actions.onMealDraftChange(meal.id, value) },
                onAddItem = { actions.onAddMealItem(meal.id) },
                onRemoveItem = { itemId -> actions.onRemoveMealItem(meal.id, itemId) },
                onRemoveMeal = { actions.onRemoveExtraMeal(meal.id) },
            )
        }
    }
    item { ExtraMealPicker(actions.onAddExtraMeal) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MealCard(
    meal: HealthMeal,
    draft: String,
    onDraftChange: (String) -> Unit,
    onAddItem: () -> Unit,
    onRemoveItem: (String) -> Unit,
    onRemoveMeal: () -> Unit,
) {
    val tint = Nafs.colors.reflect
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val canAdd = draft.isNotBlank()
    NafsCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NafsEmojiBadge(meal.emoji, tint, size = 40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(meal.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    when (meal.items.size) {
                        0 -> "Nothing logged yet"
                        1 -> "1 item"
                        else -> "${meal.items.size} items"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
            }
            if (!HealthMealRules.isCore(meal.key)) {
                IconButton(onClick = onRemoveMeal) {
                    Icon(Icons.Rounded.Close, contentDescription = "Remove ${meal.label}", tint = muted)
                }
            }
        }
        if (meal.items.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                meal.items.forEach { food -> FoodChip(food, onRemove = { onRemoveItem(food.id) }) }
            }
        }
        Spacer(Modifier.height(12.dp))
        NafsTextField(
            value = draft,
            onValueChange = onDraftChange,
            label = "Add food or drink",
            placeholder = "e.g. daal, roti, chai",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (canAdd) onAddItem() }),
            trailingIcon = {
                IconButton(onClick = onAddItem, enabled = canAdd) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = "Add food to ${meal.label}",
                        tint = if (canAdd) tint else muted.copy(alpha = 0.5f),
                    )
                }
            },
        )
    }
}

@Composable
private fun FoodChip(food: MealItem, onRemove: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.clip(CircleShape).background(scheme.surfaceContainerHigh).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${food.emoji} ${food.name}",
            Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Remove ${food.name}",
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExtraMealPicker(onAdd: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NafsEyebrow("Add another meal", Modifier.padding(start = 4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            HealthMealRules.extras.forEach { preset ->
                NafsChoiceChip(
                    text = preset.label,
                    selected = false,
                    onClick = { onAdd(preset.key) },
                    leading = preset.emoji,
                )
            }
        }
    }
}

// ─── Custom metrics ──────────────────────────────────────────────────────────

/** "Your metrics" section: header with Add, then every custom tracker in one grouped card. */
internal fun LazyListScope.customMetricItems(state: HealthUiState, actions: HealthActions) {
    val metrics = state.profile?.customMetrics.orEmpty()
    val canEdit = !state.isOffline && !state.isSavingMetrics
    item {
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NafsSectionTitle(
                "Your metrics",
                Modifier.weight(1f),
                supporting = "Optional trackers stored with today's check-in",
            )
            HealthPillButton(
                text = "Add",
                icon = Icons.Rounded.Add,
                tint = Nafs.colors.health,
                onClick = actions.onOpenMetricEditor,
                enabled = canEdit && metrics.size < 20,
            )
        }
    }
    item {
        if (metrics.isEmpty()) {
            EmptyMetricsCard()
        } else {
            NafsCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                metrics.forEachIndexed { index, metric ->
                    if (index > 0) NafsDivider()
                    CustomMetricRow(metric, state, actions, canEdit)
                }
            }
        }
    }
}

@Composable
private fun EmptyMetricsCard() {
    NafsCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            NafsIconBadge(Icons.Rounded.Tune, Nafs.colors.health, size = 40.dp, iconSize = 20.dp)
            Text(
                "Add a yes/no, counter, or number metric such as vitamins, caffeine, or pain level.",
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CustomMetricRow(
    metric: CustomHealthMetric,
    state: HealthUiState,
    actions: HealthActions,
    canEdit: Boolean,
) {
    val tint = Nafs.colors.health
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val numberInput = state.metricNumberInputs[metric.id]
    val target = metric.target
    val current = numberInput?.toDoubleOrNull()
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        NafsEmojiBadge(metric.emoji, tint, size = 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(metric.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                if (target != null) {
                    "Target ${formatHealthNumber(target)}${metric.unit?.let { unit -> " $unit" }.orEmpty()}"
                } else {
                    metric.type.label()
                },
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
            if (target != null && target > 0.0 && current != null && metric.type != CustomMetricType.BOOLEAN) {
                NafsProgressBar((current / target).toFloat(), Modifier.padding(top = 2.dp), color = tint, height = 4.dp)
            }
        }
        when (metric.type) {
            CustomMetricType.BOOLEAN -> Switch(
                checked = state.metricBooleanValues[metric.id] == true,
                onCheckedChange = { checked -> actions.onMetricBooleanChange(metric.id, checked) },
                modifier = Modifier.semantics { contentDescription = metric.name },
            )
            CustomMetricType.COUNTER -> CounterStepper(
                value = numberInput ?: "0",
                name = metric.name,
                tint = tint,
                onDecrement = { actions.onAdjustMetricCounter(metric.id, -1) },
                onIncrement = { actions.onAdjustMetricCounter(metric.id, 1) },
            )
            CustomMetricType.NUMBER -> HealthNumberInput(
                value = numberInput.orEmpty(),
                onValueChange = { value -> actions.onMetricNumberChange(metric.id, value) },
                unit = metric.unit.orEmpty(),
                label = metric.name,
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.width(104.dp),
                compact = true,
            )
        }
        IconButton(onClick = { actions.onRequestMetricRemoval(metric) }, enabled = canEdit) {
            Icon(
                Icons.Rounded.Delete,
                contentDescription = "Remove ${metric.name}",
                tint = if (canEdit) muted else muted.copy(alpha = 0.38f),
            )
        }
    }
}

@Composable
private fun CounterStepper(
    value: String,
    name: String,
    tint: Color,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        RoundStepButton(Icons.Rounded.Remove, "Decrease $name", scheme.surfaceContainerHigh, scheme.onSurface, onDecrement)
        Text(
            value,
            Modifier.widthIn(min = 24.dp),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        RoundStepButton(
            Icons.Rounded.Add,
            "Increase $name",
            tint.copy(alpha = if (Nafs.colors.isDark) 0.18f else 0.12f),
            tint,
            onIncrement,
        )
    }
}

@Composable
private fun RoundStepButton(
    icon: ImageVector,
    description: String,
    container: Color,
    foreground: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(32.dp),
        shape = CircleShape,
        color = container,
        contentColor = foreground,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(18.dp))
        }
    }
}

private fun CustomMetricType.label(): String = when (this) {
    CustomMetricType.BOOLEAN -> "Yes/No"
    CustomMetricType.COUNTER -> "Counter"
    CustomMetricType.NUMBER -> "Number"
}

private fun CustomMetricType.hint(): String = when (this) {
    CustomMetricType.BOOLEAN -> "A daily yes or no, like vitamins taken."
    CustomMetricType.COUNTER -> "Tap + and − through the day, like cups of coffee."
    CustomMetricType.NUMBER -> "Type one value a day, like a pain level."
}

// ─── Custom metric dialogs ───────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CustomMetricDialogs(state: HealthUiState, actions: HealthActions) {
    if (state.showMetricEditor) {
        AlertDialog(
            onDismissRequest = actions.onCloseMetricEditor,
            icon = { Icon(Icons.Rounded.Tune, contentDescription = null, tint = Nafs.colors.health) },
            title = { Text("Add health metric") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NafsTextField(
                            value = state.newMetricEmoji,
                            onValueChange = actions.onNewMetricEmojiChange,
                            label = "Emoji",
                            modifier = Modifier.width(88.dp),
                        )
                        NafsTextField(
                            value = state.newMetricName,
                            onValueChange = actions.onNewMetricNameChange,
                            label = "Metric name",
                            placeholder = "Vitamins",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    NafsEyebrow("Type")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CustomMetricType.entries.forEach { type ->
                            NafsChoiceChip(
                                text = type.label(),
                                selected = state.newMetricType == type,
                                onClick = { actions.onNewMetricTypeChange(type) },
                            )
                        }
                    }
                    Text(
                        state.newMetricType.hint(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.newMetricType != CustomMetricType.BOOLEAN) {
                        NafsTextField(
                            value = state.newMetricTarget,
                            onValueChange = actions.onNewMetricTargetChange,
                            label = "Target (optional)",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        NafsTextField(
                            value = state.newMetricUnit,
                            onValueChange = actions.onNewMetricUnitChange,
                            label = "Unit (optional)",
                            placeholder = "mg, cups",
                        )
                    }
                    state.actionError?.let { error ->
                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = actions.onSaveNewMetric,
                    enabled = state.newMetricName.isNotBlank() && !state.isSavingMetrics,
                ) {
                    if (state.isSavingMetrics) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Add metric")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = actions.onCloseMetricEditor, enabled = !state.isSavingMetrics) {
                    Text("Cancel")
                }
            },
        )
    }

    state.pendingMetricRemoval?.let { metric ->
        AlertDialog(
            onDismissRequest = actions.onCancelMetricRemoval,
            icon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Remove ${metric.name}?") },
            text = { Text("The tracker will be removed. Values already saved in older daily entries remain in history.") },
            confirmButton = {
                TextButton(onClick = actions.onConfirmMetricRemoval, enabled = !state.isSavingMetrics) {
                    if (state.isSavingMetrics) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = actions.onCancelMetricRemoval, enabled = !state.isSavingMetrics) {
                    Text("Cancel")
                }
            },
        )
    }
}
