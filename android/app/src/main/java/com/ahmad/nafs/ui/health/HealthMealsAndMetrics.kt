package com.ahmad.nafs.ui.health

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ahmad.nafs.core.health.CustomHealthMetric
import com.ahmad.nafs.core.health.CustomMetricType
import com.ahmad.nafs.core.health.HealthMeal
import com.ahmad.nafs.core.health.HealthMealRules

@Composable
internal fun HealthMealsCard(state: HealthUiState, viewModel: HealthViewModel) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column {
                Text("Meals", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${HealthMealRules.eatenCount(state.meals)} recorded · add only what you ate",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.meals.forEach { meal ->
                MealEditor(
                    meal = meal,
                    draft = state.mealDrafts[meal.id].orEmpty(),
                    onDraftChange = { viewModel.setMealDraft(meal.id, it) },
                    onAddItem = { viewModel.addMealItem(meal.id) },
                    onRemoveItem = { viewModel.removeMealItem(meal.id, it) },
                    onRemoveMeal = { viewModel.removeExtraMeal(meal.id) },
                )
            }
            Text("Add another meal", style = MaterialTheme.typography.labelLarge)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HealthMealRules.extras.forEach { preset ->
                    FilterChip(
                        selected = false,
                        onClick = { viewModel.addExtraMeal(preset.key) },
                        label = { Text("${preset.emoji} ${preset.label}") },
                    )
                }
            }
        }
    }
}

@Composable
private fun MealEditor(
    meal: HealthMeal,
    draft: String,
    onDraftChange: (String) -> Unit,
    onAddItem: () -> Unit,
    onRemoveItem: (String) -> Unit,
    onRemoveMeal: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${meal.emoji} ${meal.label}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            if (!HealthMealRules.isCore(meal.key)) {
                IconButton(onClick = onRemoveMeal, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Remove ${meal.label}")
                }
            }
        }
        meal.items.forEach { item ->
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${item.emoji} ${item.name}", Modifier.weight(1f))
                IconButton(onClick = { onRemoveItem(item.id) }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Remove ${item.name}")
                }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                label = { Text("Food or drink") },
                placeholder = { Text("e.g. daal, roti, chai") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onAddItem, enabled = draft.isNotBlank()) {
                Icon(Icons.Rounded.Add, contentDescription = "Add food to ${meal.label}")
            }
        }
    }
}

@Composable
internal fun CustomMetricsCard(state: HealthUiState, viewModel: HealthViewModel) {
    val metrics = state.profile?.customMetrics.orEmpty()
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Your metrics", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Optional trackers stored with today's check-in",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = viewModel::openMetricEditor,
                    enabled = !state.isOffline && !state.isSavingMetrics && metrics.size < 20,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add")
                }
            }
            if (metrics.isEmpty()) {
                Text(
                    "Add a yes/no, counter, or number metric such as vitamins, caffeine, or pain level.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            metrics.forEach { metric ->
                CustomMetricEditor(metric, state, viewModel)
            }
        }
    }
}

@Composable
private fun CustomMetricEditor(
    metric: CustomHealthMetric,
    state: HealthUiState,
    viewModel: HealthViewModel,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(metric.emoji, style = MaterialTheme.typography.titleLarge)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(metric.name, style = MaterialTheme.typography.titleSmall)
                metric.target?.let {
                    Text(
                        "Target ${formatMetricNumber(it)}${metric.unit?.let { unit -> " $unit" }.orEmpty()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when (metric.type) {
                CustomMetricType.BOOLEAN -> Switch(
                    checked = state.metricBooleanValues[metric.id] == true,
                    onCheckedChange = { viewModel.setMetricBoolean(metric.id, it) },
                )
                CustomMetricType.COUNTER -> {
                    IconButton(onClick = { viewModel.adjustMetricCounter(metric.id, -1) }) {
                        Text("−", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(state.metricNumberInputs[metric.id] ?: "0")
                    IconButton(onClick = { viewModel.adjustMetricCounter(metric.id, 1) }) {
                        Text("+", style = MaterialTheme.typography.titleLarge)
                    }
                }
                CustomMetricType.NUMBER -> OutlinedTextField(
                    value = state.metricNumberInputs[metric.id].orEmpty(),
                    onValueChange = { viewModel.setMetricNumber(metric.id, it) },
                    suffix = metric.unit?.let { unit -> { Text(unit) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(128.dp),
                )
            }
            IconButton(
                onClick = { viewModel.requestMetricRemoval(metric) },
                enabled = !state.isOffline && !state.isSavingMetrics,
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = "Remove ${metric.name}")
            }
        }
    }
}

@Composable
internal fun CustomMetricDialogs(state: HealthUiState, viewModel: HealthViewModel) {
    if (state.showMetricEditor) {
        AlertDialog(
            onDismissRequest = viewModel::closeMetricEditor,
            title = { Text("Add health metric") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.newMetricEmoji,
                            onValueChange = viewModel::setNewMetricEmoji,
                            label = { Text("Emoji") },
                            singleLine = true,
                            modifier = Modifier.width(88.dp),
                        )
                        OutlinedTextField(
                            value = state.newMetricName,
                            onValueChange = viewModel::setNewMetricName,
                            label = { Text("Metric name") },
                            placeholder = { Text("Vitamins") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CustomMetricType.entries.forEach { type ->
                            FilterChip(
                                selected = state.newMetricType == type,
                                onClick = { viewModel.setNewMetricType(type) },
                                label = {
                                    Text(
                                        when (type) {
                                            CustomMetricType.BOOLEAN -> "Yes/No"
                                            CustomMetricType.COUNTER -> "Counter"
                                            CustomMetricType.NUMBER -> "Number"
                                        },
                                    )
                                },
                            )
                        }
                    }
                    if (state.newMetricType != CustomMetricType.BOOLEAN) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = state.newMetricTarget,
                                onValueChange = viewModel::setNewMetricTarget,
                                label = { Text("Target (optional)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = state.newMetricUnit,
                                onValueChange = viewModel::setNewMetricUnit,
                                label = { Text("Unit (optional)") },
                                placeholder = { Text("mg, cups") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    state.actionError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = viewModel::saveNewMetric,
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
                TextButton(onClick = viewModel::closeMetricEditor, enabled = !state.isSavingMetrics) {
                    Text("Cancel")
                }
            },
        )
    }

    state.pendingMetricRemoval?.let { metric ->
        AlertDialog(
            onDismissRequest = viewModel::cancelMetricRemoval,
            title = { Text("Remove ${metric.name}?") },
            text = { Text("The tracker will be removed. Values already saved in older daily entries remain in history.") },
            confirmButton = {
                Button(onClick = viewModel::confirmMetricRemoval, enabled = !state.isSavingMetrics) {
                    if (state.isSavingMetrics) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Remove")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelMetricRemoval, enabled = !state.isSavingMetrics) {
                    Text("Cancel")
                }
            },
        )
    }
}

private fun formatMetricNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
