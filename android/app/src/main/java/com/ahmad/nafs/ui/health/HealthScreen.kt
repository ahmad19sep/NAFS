package com.ahmad.nafs.ui.health

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.core.health.SleepSession
import com.ahmad.nafs.core.health.SleepSessionRules
import com.ahmad.nafs.ui.AccountDayRefreshEffects
import com.ahmad.nafs.ui.components.NafsHeroCard
import com.ahmad.nafs.ui.components.NafsSectionTitle

@Composable
fun HealthRoute(
    profile: UserProfile,
    viewModel: HealthViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(profile.userId, profile.timezone) {
        viewModel.initialize(profile.userId, profile.timezone)
    }
    AccountDayRefreshEffects(profile.timezone, viewModel::refresh)
    HealthScreen(state, viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HealthScreen(state: HealthUiState, viewModel: HealthViewModel) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Health")
                        Text(
                            "Private daily signals, not medical advice",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    IconButton(onClick = viewModel::openProfileEditor, enabled = state.profile != null) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit health profile")
                    }
                    IconButton(
                        onClick = viewModel::refresh,
                        enabled = !state.isLoading && !state.isRefreshing,
                    ) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh health data")
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.loadError != null && state.profile == null -> HealthLoadFailure(
                state.loadError,
                viewModel::refresh,
                Modifier.padding(padding),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (state.isOffline) item { HealthOfflineBanner() }
                if (state.hasSaveConflict) {
                    item { HealthConflictBanner(state.isRefreshing, viewModel::reloadAfterConflict) }
                }
                state.actionError?.let {
                    item { HealthMessage(it, error = true, onDismiss = viewModel::dismissMessage) }
                }
                state.loadError?.let {
                    item { HealthMessage(it, error = true, onDismiss = viewModel::dismissMessage) }
                }
                state.message?.let {
                    item { HealthMessage(it, error = false, onDismiss = viewModel::dismissMessage) }
                }
                item {
                    NafsHeroCard(
                        eyebrow = state.today.toString(),
                        title = state.bmi?.let { "BMI $it" } ?: "Set your baseline",
                        body = if (state.bmi == null) {
                            "Add height and current weight before saving daily health signals."
                        } else {
                            "Use these measurements as a personal log. For diagnosis or treatment, speak with a qualified clinician."
                        },
                    ) {
                        state.profile?.let { healthProfile ->
                            Text(
                                "${healthProfile.heightCm?.let(::formatHealthNumber) ?: "—"} cm  ·  " +
                                    "${healthProfile.weightKg?.let(::formatHealthNumber) ?: "—"} kg",
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
                item {
                    NafsSectionTitle(
                        "Today's check-in",
                        supporting = "Save partial data; only track what is useful.",
                    )
                }
                item { WaterCard(state.waterGlasses, viewModel::setWater) }
                item {
                    MetricField(
                        label = "Steps",
                        value = state.steps,
                        onValueChange = viewModel::setSteps,
                        suffix = "steps",
                        keyboardType = KeyboardType.Number,
                    )
                }
                item { SleepSessionsCard(state, viewModel) }
                item { HealthMealsCard(state, viewModel) }
                item { ExerciseCard(state, viewModel) }
                item {
                    MetricField(
                        label = "Today's weight",
                        value = state.dailyWeight,
                        onValueChange = viewModel::setDailyWeight,
                        suffix = "kg",
                        keyboardType = KeyboardType.Decimal,
                    )
                }
                item { MoodCard(state.mood, viewModel::setMood) }
                item { CustomMetricsCard(state, viewModel) }
                item {
                    OutlinedTextField(
                        value = state.notes,
                        onValueChange = viewModel::setNotes,
                        label = { Text("Notes") },
                        supportingText = { Text("Optional · energy, pain, recovery, or context") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Button(
                        onClick = viewModel::saveLog,
                        enabled = !state.isSaving && !state.setupRequired && !state.isOffline &&
                            !state.hasSaveConflict,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (state.log == null) "Save today's check-in" else "Update today's check-in")
                    }
                }
                item {
                    Text(
                        "NAFS stores these entries in your account for personal tracking. It does not provide medical diagnosis, emergency guidance, or treatment recommendations.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (state.showProfileEditor) HealthProfileDialog(state, viewModel)
    CustomMetricDialogs(state, viewModel)
}

@Composable
private fun SleepSessionsCard(state: HealthUiState, viewModel: HealthViewModel) {
    val durations = state.sleepSessions.map(SleepSessionRules::minutes)
    val total = if (durations.all { it != null && it > 0 }) {
        SleepSessionRules.formatMinutes(durations.filterNotNull().sum())
    } else {
        null
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Sleep periods", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            state.sleepSessions.isNotEmpty() && total != null -> "$total total"
                            state.sleepSessions.isNotEmpty() -> "Enter each start and end time"
                            state.legacySleepHours != null ->
                                "Saved total: ${formatHealthNumber(state.legacySleepHours)} hours"
                            else -> "Not recorded"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = viewModel::addSleepSession) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add")
                }
            }
            state.sleepSessions.forEachIndexed { index, session ->
                SleepSessionRow(
                    index = index,
                    session = session,
                    onStartChange = { value ->
                        if (value.length <= 5) viewModel.updateSleepSession(session.id, start = value)
                    },
                    onEndChange = { value ->
                        if (value.length <= 5) viewModel.updateSleepSession(session.id, end = value)
                    },
                    onRemove = { viewModel.removeSleepSession(session.id) },
                )
            }
            if (state.legacySleepHours != null && state.sleepSessions.isEmpty()) {
                Text(
                    "Add a timed period to replace the older total-only entry.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SleepSessionRow(
    index: Int,
    session: SleepSession,
    onStartChange: (String) -> Unit,
    onEndChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val duration = SleepSessionRules.minutes(session)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = session.start,
                onValueChange = onStartChange,
                label = { Text("Start") },
                placeholder = { Text("22:30") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = session.end,
                onValueChange = onEndChange,
                label = { Text("End") },
                placeholder = { Text("06:30") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.Close, contentDescription = "Remove sleep period ${index + 1}")
            }
        }
        Text(
            if (duration != null && duration > 0) {
                "Period ${index + 1} · ${SleepSessionRules.formatMinutes(duration)}"
            } else {
                "Period ${index + 1} · use 24-hour time"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WaterCard(glasses: Int, onChange: (Int) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Water", style = MaterialTheme.typography.titleMedium)
                Text("$glasses glasses", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { onChange(glasses - 1) }, enabled = glasses > 0) {
                Icon(Icons.Rounded.Remove, contentDescription = "Remove one glass")
            }
            IconButton(onClick = { onChange(glasses + 1) }, enabled = glasses < 100) {
                Icon(Icons.Rounded.Add, contentDescription = "Add one glass")
            }
        }
    }
}

@Composable
private fun MetricField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    suffix: String,
    keyboardType: KeyboardType,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        suffix = { Text(suffix) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ExerciseCard(state: HealthUiState, viewModel: HealthViewModel) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Exercise", style = MaterialTheme.typography.titleMedium)
                    Text("Record intentional movement", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = state.exerciseDone, onCheckedChange = viewModel::setExerciseDone)
            }
            if (state.exerciseDone) {
                MetricField(
                    label = "Exercise duration",
                    value = state.exerciseMinutes,
                    onValueChange = viewModel::setExerciseMinutes,
                    suffix = "minutes",
                    keyboardType = KeyboardType.Number,
                )
            }
        }
    }
}

@Composable
private fun MoodCard(mood: Int?, onMoodChange: (Int?) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Mood", style = MaterialTheme.typography.titleMedium)
            Text(
                mood?.let { "$it / 10" } ?: "Not recorded",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (1..10).forEach { value ->
                    FilterChip(
                        selected = mood == value,
                        onClick = { onMoodChange(if (mood == value) null else value) },
                        label = { Text(value.toString()) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HealthProfileDialog(state: HealthUiState, viewModel: HealthViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::closeProfileEditor,
        icon = { Icon(Icons.Rounded.Favorite, contentDescription = null) },
        title = { Text(if (state.setupRequired) "Set your health baseline" else "Edit health baseline") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Height and current weight are used only for your personal BMI baseline.")
                MetricField(
                    label = "Height",
                    value = state.profileHeight,
                    onValueChange = viewModel::setProfileHeight,
                    suffix = "cm",
                    keyboardType = KeyboardType.Decimal,
                )
                MetricField(
                    label = "Current weight",
                    value = state.profileWeight,
                    onValueChange = viewModel::setProfileWeight,
                    suffix = "kg",
                    keyboardType = KeyboardType.Decimal,
                )
                state.actionError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = viewModel::saveProfile, enabled = !state.isSavingProfile) {
                if (state.isSavingProfile) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Save baseline")
                }
            }
        },
        dismissButton = {
            if (!state.setupRequired) {
                TextButton(onClick = viewModel::closeProfileEditor) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun HealthOfflineBanner() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.CloudOff, contentDescription = null)
            Text("Showing cached health data. Saving requires a connection.")
        }
    }
}

@Composable
private fun HealthConflictBanner(isRefreshing: Boolean, onReload: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Today's health entry changed elsewhere", style = MaterialTheme.typography.titleSmall)
            Text(
                "Another device saved after this screen loaded. Reload the latest entry before saving so neither version is silently overwritten.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = onReload, enabled = !isRefreshing) {
                if (isRefreshing) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Reload latest")
            }
        }
    }
}

@Composable
private fun HealthMessage(message: String, error: Boolean, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (error) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

@Composable
private fun HealthLoadFailure(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Health data unavailable", style = MaterialTheme.typography.titleLarge)
            Text(message)
            OutlinedButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

private fun formatHealthNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
