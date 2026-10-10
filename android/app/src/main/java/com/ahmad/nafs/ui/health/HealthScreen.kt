package com.ahmad.nafs.ui.health

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmad.nafs.core.health.CustomHealthMetric
import com.ahmad.nafs.core.health.CustomMetricType
import com.ahmad.nafs.core.health.HealthMealRules
import com.ahmad.nafs.core.health.SleepSession
import com.ahmad.nafs.core.health.SleepSessionRules
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.ui.AccountDayRefreshEffects
import com.ahmad.nafs.ui.components.NafsBanner
import com.ahmad.nafs.ui.components.NafsBannerKind
import com.ahmad.nafs.ui.components.NafsCard
import com.ahmad.nafs.ui.components.NafsDivider
import com.ahmad.nafs.ui.components.NafsEmojiBadge
import com.ahmad.nafs.ui.components.NafsErrorState
import com.ahmad.nafs.ui.components.NafsEyebrow
import com.ahmad.nafs.ui.components.NafsHeroSurface
import com.ahmad.nafs.ui.components.NafsIconBadge
import com.ahmad.nafs.ui.components.NafsLoadingState
import com.ahmad.nafs.ui.components.NafsPageHeader
import com.ahmad.nafs.ui.components.NafsPill
import com.ahmad.nafs.ui.components.NafsPrimaryButton
import com.ahmad.nafs.ui.components.NafsProgressBar
import com.ahmad.nafs.ui.components.NafsProgressRing
import com.ahmad.nafs.ui.components.NafsSectionTitle
import com.ahmad.nafs.ui.components.NafsSpace
import com.ahmad.nafs.ui.components.NafsTextField
import com.ahmad.nafs.ui.theme.Nafs
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    HealthScreen(
        state = state,
        actions = HealthActions(
            onRefresh = viewModel::refresh,
            onDismissMessage = viewModel::dismissMessage,
            onReloadAfterConflict = viewModel::reloadAfterConflict,
            onOpenProfileEditor = viewModel::openProfileEditor,
            onCloseProfileEditor = viewModel::closeProfileEditor,
            onProfileHeightChange = viewModel::setProfileHeight,
            onProfileWeightChange = viewModel::setProfileWeight,
            onSaveProfile = viewModel::saveProfile,
            onWaterChange = viewModel::setWater,
            onStepsChange = viewModel::setSteps,
            onAddSleepSession = viewModel::addSleepSession,
            onSleepStartChange = { id, value ->
                if (value.length <= 5) viewModel.updateSleepSession(id, start = value)
            },
            onSleepEndChange = { id, value ->
                if (value.length <= 5) viewModel.updateSleepSession(id, end = value)
            },
            onRemoveSleepSession = viewModel::removeSleepSession,
            onMealDraftChange = viewModel::setMealDraft,
            onAddMealItem = viewModel::addMealItem,
            onRemoveMealItem = viewModel::removeMealItem,
            onAddExtraMeal = viewModel::addExtraMeal,
            onRemoveExtraMeal = viewModel::removeExtraMeal,
            onMetricBooleanChange = viewModel::setMetricBoolean,
            onMetricNumberChange = viewModel::setMetricNumber,
            onAdjustMetricCounter = viewModel::adjustMetricCounter,
            onOpenMetricEditor = viewModel::openMetricEditor,
            onCloseMetricEditor = viewModel::closeMetricEditor,
            onNewMetricNameChange = viewModel::setNewMetricName,
            onNewMetricEmojiChange = viewModel::setNewMetricEmoji,
            onNewMetricTypeChange = viewModel::setNewMetricType,
            onNewMetricTargetChange = viewModel::setNewMetricTarget,
            onNewMetricUnitChange = viewModel::setNewMetricUnit,
            onSaveNewMetric = viewModel::saveNewMetric,
            onRequestMetricRemoval = viewModel::requestMetricRemoval,
            onCancelMetricRemoval = viewModel::cancelMetricRemoval,
            onConfirmMetricRemoval = viewModel::confirmMetricRemoval,
            onExerciseDoneChange = viewModel::setExerciseDone,
            onExerciseMinutesChange = viewModel::setExerciseMinutes,
            onDailyWeightChange = viewModel::setDailyWeight,
            onMoodChange = viewModel::setMood,
            onNotesChange = viewModel::setNotes,
            onSaveLog = viewModel::saveLog,
        ),
    )
}

/** Everything the Health screen can ask of its ViewModel, so the screen stays a pure function of its state. */
internal data class HealthActions(
    val onRefresh: () -> Unit = {},
    val onDismissMessage: () -> Unit = {},
    val onReloadAfterConflict: () -> Unit = {},
    val onOpenProfileEditor: () -> Unit = {},
    val onCloseProfileEditor: () -> Unit = {},
    val onProfileHeightChange: (String) -> Unit = {},
    val onProfileWeightChange: (String) -> Unit = {},
    val onSaveProfile: () -> Unit = {},
    val onWaterChange: (Int) -> Unit = {},
    val onStepsChange: (String) -> Unit = {},
    val onAddSleepSession: () -> Unit = {},
    val onSleepStartChange: (id: String, value: String) -> Unit = { _, _ -> },
    val onSleepEndChange: (id: String, value: String) -> Unit = { _, _ -> },
    val onRemoveSleepSession: (id: String) -> Unit = {},
    val onMealDraftChange: (mealId: String, value: String) -> Unit = { _, _ -> },
    val onAddMealItem: (mealId: String) -> Unit = {},
    val onRemoveMealItem: (mealId: String, itemId: String) -> Unit = { _, _ -> },
    val onAddExtraMeal: (key: String) -> Unit = {},
    val onRemoveExtraMeal: (mealId: String) -> Unit = {},
    val onMetricBooleanChange: (id: String, value: Boolean) -> Unit = { _, _ -> },
    val onMetricNumberChange: (id: String, value: String) -> Unit = { _, _ -> },
    val onAdjustMetricCounter: (id: String, amount: Int) -> Unit = { _, _ -> },
    val onOpenMetricEditor: () -> Unit = {},
    val onCloseMetricEditor: () -> Unit = {},
    val onNewMetricNameChange: (String) -> Unit = {},
    val onNewMetricEmojiChange: (String) -> Unit = {},
    val onNewMetricTypeChange: (CustomMetricType) -> Unit = {},
    val onNewMetricTargetChange: (String) -> Unit = {},
    val onNewMetricUnitChange: (String) -> Unit = {},
    val onSaveNewMetric: () -> Unit = {},
    val onRequestMetricRemoval: (CustomHealthMetric) -> Unit = {},
    val onCancelMetricRemoval: () -> Unit = {},
    val onConfirmMetricRemoval: () -> Unit = {},
    val onExerciseDoneChange: (Boolean) -> Unit = {},
    val onExerciseMinutesChange: (String) -> Unit = {},
    val onDailyWeightChange: (String) -> Unit = {},
    val onMoodChange: (Int?) -> Unit = {},
    val onNotesChange: (String) -> Unit = {},
    val onSaveLog: () -> Unit = {},
)

@Composable
internal fun HealthScreen(
    state: HealthUiState,
    actions: HealthActions,
    listState: LazyListState = rememberLazyListState(),
) {
    when {
        state.isLoading -> NafsLoadingState(Modifier.statusBarsPadding())
        state.loadError != null && state.profile == null -> NafsErrorState(
            title = "Health data unavailable",
            message = state.loadError,
            onRetry = actions.onRefresh,
            modifier = Modifier.statusBarsPadding(),
        )
        else -> HealthContent(state, actions, listState)
    }

    // While the baseline is missing it is collected inline by the setup card instead of a dialog.
    if (state.showProfileEditor && !state.setupRequired) HealthProfileDialog(state, actions)
    CustomMetricDialogs(state, actions)
}

@Composable
private fun HealthContent(state: HealthUiState, actions: HealthActions, listState: LazyListState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding().imePadding(),
        state = listState,
        contentPadding = PaddingValues(start = NafsSpace.screen, end = NafsSpace.screen, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(NafsSpace.item),
    ) {
        item { HealthHeader(state, actions.onRefresh) }

        if (state.isOffline) item {
            NafsBanner("Showing cached health data. Saving requires a connection.", kind = NafsBannerKind.OFFLINE)
        }
        if (state.hasSaveConflict) item { ConflictCard(state.isRefreshing, actions.onReloadAfterConflict) }
        // During setup the baseline error sits inside the setup card, next to the fields it is about.
        if (!state.setupRequired) state.actionError?.let { error ->
            item { NafsBanner(error, kind = NafsBannerKind.ERROR, onDismiss = actions.onDismissMessage) }
        }
        state.loadError?.let { error ->
            item { NafsBanner(error, kind = NafsBannerKind.ERROR, onDismiss = actions.onDismissMessage) }
        }
        state.message?.let { note ->
            item { NafsBanner(note, kind = NafsBannerKind.SUCCESS, onDismiss = actions.onDismissMessage) }
        }

        if (state.setupRequired) {
            item { BaselineSetupCard(state, actions) }
        } else {
            item { TodayHero(state) }
            item { BaselineCard(state, actions.onOpenProfileEditor) }
            item {
                NafsSectionTitle(
                    "Today's check-in",
                    Modifier.padding(top = 16.dp),
                    supporting = "Save partial data; only track what is useful.",
                )
            }
            item {
                TileRow {
                    WaterTile(state.waterGlasses, actions.onWaterChange, Modifier.weight(1f).fillMaxHeight())
                    StepsTile(state.steps, actions.onStepsChange, Modifier.weight(1f).fillMaxHeight())
                }
            }
            item {
                TileRow {
                    ExerciseTile(
                        done = state.exerciseDone,
                        minutes = state.exerciseMinutes,
                        onDoneChange = actions.onExerciseDoneChange,
                        onMinutesChange = actions.onExerciseMinutesChange,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    WeightTile(
                        weight = state.dailyWeight,
                        baselineKg = state.profile?.weightKg,
                        onWeightChange = actions.onDailyWeightChange,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
            item { SleepCard(state, actions) }
            item { MoodCard(state.mood, actions.onMoodChange) }
            healthMealItems(state, actions)
            customMetricItems(state, actions)
            item { NotesCard(state.notes, actions.onNotesChange) }
            item { SaveCheckIn(state, actions.onSaveLog) }
        }

        item { HealthDisclaimer() }
    }
}

// ─── Header and messages ─────────────────────────────────────────────────────

@Composable
private fun HealthHeader(state: HealthUiState, onRefresh: () -> Unit) {
    NafsPageHeader(
        title = "Health",
        eyebrow = state.today.format(HeaderDateFormat),
        subtitle = "Private daily signals, not medical advice",
        actions = {
            IconButton(onClick = onRefresh, enabled = !state.isLoading && !state.isRefreshing) {
                if (state.isRefreshing) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = "Refresh health data",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

@Composable
private fun ConflictCard(isRefreshing: Boolean, onReload: () -> Unit) {
    val c = Nafs.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = c.warningContainer,
        contentColor = c.onWarningContainer,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.WarningAmber, contentDescription = null, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Today's health entry changed elsewhere", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Another device saved after this screen loaded. Reload the latest entry before saving so " +
                            "neither version is silently overwritten.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Surface(
                onClick = onReload,
                enabled = !isRefreshing,
                modifier = Modifier.padding(start = 32.dp),
                shape = CircleShape,
                color = c.onWarningContainer.copy(alpha = 0.10f),
                contentColor = c.onWarningContainer,
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = c.onWarningContainer, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Text("Reload latest", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

// ─── Baseline (setup and BMI) ────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BaselineSetupCard(state: HealthUiState, actions: HealthActions) {
    val c = Nafs.colors
    val scheme = MaterialTheme.colorScheme
    NafsCard(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 26.dp)) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NafsIconBadge(Icons.Rounded.Favorite, c.health, size = 64.dp, iconSize = 30.dp)
            Spacer(Modifier.height(4.dp))
            NafsEyebrow("Before your first check-in", color = c.health)
            Text("Set your health baseline", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                "Height and current weight are used only for your personal BMI baseline. " +
                    "Add them once before saving daily health signals.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(22.dp))
        BaselineFields(state, actions)
        state.actionError?.let { error ->
            Spacer(Modifier.height(10.dp))
            Text(error, color = scheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(18.dp))
        NafsPrimaryButton(
            text = "Save baseline",
            onClick = actions.onSaveProfile,
            modifier = Modifier.fillMaxWidth(),
            loading = state.isSavingProfile,
            icon = Icons.Rounded.Check,
        )
        Spacer(Modifier.height(22.dp))
        NafsDivider(inset = 0.dp)
        Spacer(Modifier.height(18.dp))
        NafsEyebrow("Then, each day, log what helps")
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NafsPill("Water", color = c.tasks, icon = Icons.Rounded.WaterDrop)
            NafsPill("Sleep", color = c.deen, icon = Icons.Rounded.Bedtime)
            NafsPill("Meals", color = c.reflect, icon = Icons.Rounded.Restaurant)
            NafsPill("Movement", color = c.habits, icon = Icons.Rounded.FitnessCenter)
            NafsPill("Mood", color = c.reflect, icon = Icons.Rounded.Mood)
        }
    }
}

@Composable
private fun BaselineFields(state: HealthUiState, actions: HealthActions) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NafsTextField(
            value = state.profileHeight,
            onValueChange = actions.onProfileHeightChange,
            label = "Height",
            leadingIcon = Icons.Rounded.Straighten,
            trailingIcon = { UnitSuffix("cm") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        )
        NafsTextField(
            value = state.profileWeight,
            onValueChange = actions.onProfileWeightChange,
            label = "Current weight",
            leadingIcon = Icons.Rounded.MonitorWeight,
            trailingIcon = { UnitSuffix("kg") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        )
    }
}

@Composable
private fun UnitSuffix(unit: String) {
    Text(
        unit,
        Modifier.padding(end = 12.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun BaselineCard(state: HealthUiState, onEdit: () -> Unit) {
    val profile = state.profile
    val bmi = state.bmi
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    NafsCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NafsIconBadge(Icons.Rounded.MonitorWeight, Nafs.colors.gold, size = 40.dp, iconSize = 20.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Body baseline", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${profile?.heightCm?.let(::formatHealthNumber) ?: "—"} cm  ·  " +
                        "${profile?.weightKg?.let(::formatHealthNumber) ?: "—"} kg",
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
            }
            HealthPillButton(
                text = "Edit",
                icon = Icons.Rounded.Edit,
                tint = MaterialTheme.colorScheme.primary,
                onClick = onEdit,
                enabled = profile != null,
            )
        }
        Spacer(Modifier.height(18.dp))
        if (bmi != null) {
            val band = BmiBand.of(bmi)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f)) {
                    Text(formatHealthNumber(bmi), Modifier.alignByBaseline(), style = MaterialTheme.typography.displaySmall)
                    Text(
                        "BMI",
                        Modifier.alignByBaseline().padding(start = 8.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = muted,
                    )
                }
                NafsPill(band.label, color = band.color())
            }
            Spacer(Modifier.height(14.dp))
            BmiScale(bmi)
            Spacer(Modifier.height(8.dp))
            Text(
                "Healthy range 18.5–24.9 on the adult WHO scale.",
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
        } else {
            Text("BMI appears once height and weight are saved.", style = MaterialTheme.typography.bodyMedium, color = muted)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Use these measurements as a personal log. For diagnosis or treatment, speak with a qualified clinician.",
            style = MaterialTheme.typography.bodySmall,
            color = muted,
        )
    }
}

private enum class BmiBand(val label: String, val from: Double, val to: Double) {
    UNDER("Underweight", BMI_SCALE_MIN, 18.5),
    HEALTHY("Healthy range", 18.5, 25.0),
    OVER("Overweight", 25.0, 30.0),
    OBESE("Obesity range", 30.0, BMI_SCALE_MAX);

    companion object {
        fun of(bmi: Double): BmiBand = BmiBand.entries.firstOrNull { bmi < it.to } ?: BmiBand.OBESE
    }
}

@Composable
private fun BmiBand.color(): Color = when (this) {
    BmiBand.UNDER -> Nafs.colors.tasks
    BmiBand.HEALTHY -> Nafs.colors.success
    BmiBand.OVER -> Nafs.colors.warning
    BmiBand.OBESE -> Nafs.colors.health
}

/** A quiet four-band scale with a marker at the current BMI. */
@Composable
private fun BmiScale(bmi: Double) {
    val active = BmiBand.of(bmi)
    val bands = BmiBand.entries.map { band -> band to band.color() }
    val marker = MaterialTheme.colorScheme.onSurface
    val halo = MaterialTheme.colorScheme.surfaceContainer
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(18.dp)
            .semantics { contentDescription = "BMI ${formatHealthNumber(bmi)}, ${active.label}" },
    ) {
        val barHeight = 6.dp.toPx()
        val gap = 3.dp.toPx()
        val barTop = (size.height - barHeight) / 2f
        val span = (BMI_SCALE_MAX - BMI_SCALE_MIN).toFloat()
        bands.forEach { (band, bandColor) ->
            val startX = (band.from - BMI_SCALE_MIN).toFloat() / span * size.width +
                if (band == BmiBand.UNDER) 0f else gap / 2f
            val endX = (band.to - BMI_SCALE_MIN).toFloat() / span * size.width -
                if (band == BmiBand.OBESE) 0f else gap / 2f
            drawRoundRect(
                color = bandColor.copy(alpha = if (band == active) 1f else 0.3f),
                topLeft = Offset(startX, barTop),
                size = Size(endX - startX, barHeight),
                cornerRadius = CornerRadius(barHeight / 2f),
            )
        }
        val haloRadius = 8.dp.toPx()
        val markerX = ((bmi - BMI_SCALE_MIN).toFloat() / span * size.width)
            .coerceAtMost(size.width - haloRadius)
            .coerceAtLeast(haloRadius)
        val markerCenter = Offset(markerX, size.height / 2f)
        drawCircle(halo, radius = haloRadius, center = markerCenter)
        drawCircle(marker, radius = 5.dp.toPx(), center = markerCenter)
    }
}

// ─── Today hero ──────────────────────────────────────────────────────────────

private class HealthSignal(val label: String, val logged: Boolean)

private fun healthSignals(state: HealthUiState): List<HealthSignal> = listOf(
    HealthSignal("Water", state.waterGlasses > 0),
    HealthSignal("Steps", state.steps.isNotBlank()),
    HealthSignal("Sleep", state.sleepSessions.isNotEmpty() || state.legacySleepHours != null),
    HealthSignal("Meals", HealthMealRules.eatenCount(state.meals) > 0),
    HealthSignal("Exercise", state.exerciseDone),
    HealthSignal("Weight", state.dailyWeight.isNotBlank()),
    HealthSignal("Mood", state.mood != null),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodayHero(state: HealthUiState) {
    val c = Nafs.colors
    val signals = healthSignals(state)
    val logged = signals.count { it.logged }
    val headline = when (logged) {
        0 -> "A fresh page. Log only what helps."
        signals.size -> "Every signal logged today."
        else -> "Good start. Log what's useful, skip the rest."
    }
    val status = when {
        state.isSaving -> "Saving…"
        state.log == null -> "Not saved yet today"
        else -> "Saved · update any time"
    }
    NafsHeroSurface {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                NafsProgressRing(
                    progress = logged.toFloat() / signals.size,
                    size = 104.dp,
                    stroke = 10.dp,
                    colors = listOf(c.heroGold, c.heroMint),
                    trackColor = Color.White.copy(alpha = 0.12f),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$logged/${signals.size}", style = MaterialTheme.typography.headlineMedium, color = c.onHero)
                        Text("logged", style = MaterialTheme.typography.labelSmall, color = c.onHeroMuted)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    NafsEyebrow("Today's check-in", color = c.heroGold)
                    Text(headline, style = MaterialTheme.typography.titleLarge, color = c.onHero)
                    Text(status, style = MaterialTheme.typography.labelMedium, color = c.onHeroMuted)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.10f)))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                signals.forEach { signal -> HeroSignalPill(signal) }
            }
        }
    }
}

@Composable
private fun HeroSignalPill(signal: HealthSignal) {
    val c = Nafs.colors
    Row(
        Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (signal.logged) 0.14f else 0.06f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .clearAndSetSemantics {
                contentDescription = "${signal.label}, ${if (signal.logged) "logged" else "not logged yet"}"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (signal.logged) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = c.heroMint, modifier = Modifier.size(13.dp))
        }
        Text(
            signal.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (signal.logged) c.onHero else c.onHeroMuted,
        )
    }
}

// ─── Metric tiles ────────────────────────────────────────────────────────────

@Composable
private fun TileRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun MetricTile(
    title: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    NafsCard(modifier = modifier, contentPadding = PaddingValues(16.dp)) {
        NafsIconBadge(icon, tint, size = 36.dp, iconSize = 18.dp)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun WaterTile(glasses: Int, onChange: (Int) -> Unit, modifier: Modifier) {
    val tint = Nafs.colors.tasks
    MetricTile("Water", Icons.Rounded.WaterDrop, tint, modifier) {
        Row {
            Text("$glasses", Modifier.alignByBaseline(), style = MaterialTheme.typography.headlineMedium)
            Text(
                if (glasses == 1) "glass" else "glasses",
                Modifier.alignByBaseline().padding(start = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        GlassDots(glasses, tint)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WaterStepButton(
                icon = Icons.Rounded.Remove,
                description = "Remove one glass",
                enabled = glasses > 0,
                filled = false,
                tint = tint,
                onClick = { onChange(glasses - 1) },
                modifier = Modifier.weight(1f),
            )
            WaterStepButton(
                icon = Icons.Rounded.Add,
                description = "Add one glass",
                enabled = glasses < 100,
                filled = true,
                tint = tint,
                onClick = { onChange(glasses + 1) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** One small cup per glass for the first eight, then a count. */
@Composable
private fun GlassDots(glasses: Int, tint: Color) {
    val empty = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val cup = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp, bottomStart = 5.dp, bottomEnd = 5.dp)
    Row(
        modifier = Modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(WATER_DOTS) { index ->
            Box(Modifier.size(width = 9.dp, height = 14.dp).clip(cup).background(if (index < glasses) tint else empty))
        }
        if (glasses > WATER_DOTS) {
            Text("+${glasses - WATER_DOTS}", style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
        }
    }
}

@Composable
private fun WaterStepButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    filled: Boolean,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val container = when {
        !enabled -> scheme.onSurface.copy(alpha = 0.05f)
        filled -> tint
        else -> scheme.surfaceContainerHigh
    }
    val foreground = when {
        !enabled -> scheme.onSurface.copy(alpha = 0.3f)
        filled -> scheme.surface
        else -> scheme.onSurface
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(40.dp),
        shape = MaterialTheme.shapes.small,
        color = container,
        contentColor = foreground,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun StepsTile(steps: String, onStepsChange: (String) -> Unit, modifier: Modifier) {
    val tint = Nafs.colors.habits
    val count = steps.trim().toIntOrNull()?.coerceAtLeast(0)
    MetricTile("Steps", Icons.AutoMirrored.Rounded.DirectionsWalk, tint, modifier) {
        HealthNumberInput(
            value = steps,
            onValueChange = onStepsChange,
            unit = "steps",
            label = "Steps",
            keyboardType = KeyboardType.Number,
        )
        Spacer(Modifier.height(14.dp))
        NafsProgressBar((count ?: 0) / STEP_REFERENCE.toFloat(), color = tint, height = 6.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            if (count == null) "Reference: 10k steps" else "${count.coerceAtMost(1_000_000) * 100L / STEP_REFERENCE}% of a 10k day",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ExerciseTile(
    done: Boolean,
    minutes: String,
    onDoneChange: (Boolean) -> Unit,
    onMinutesChange: (String) -> Unit,
    modifier: Modifier,
) {
    MetricTile("Exercise", Icons.Rounded.FitnessCenter, Nafs.colors.habits, modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (done) "Done" else "Not yet", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            Switch(
                checked = done,
                onCheckedChange = onDoneChange,
                modifier = Modifier.semantics { contentDescription = "Exercise done today" },
            )
        }
        Spacer(Modifier.height(8.dp))
        if (done) {
            HealthNumberInput(
                value = minutes,
                onValueChange = onMinutesChange,
                unit = "min",
                label = "Exercise duration in minutes",
                keyboardType = KeyboardType.Number,
            )
        } else {
            Text(
                "Record intentional movement",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeightTile(weight: String, baselineKg: Double?, onWeightChange: (String) -> Unit, modifier: Modifier) {
    MetricTile("Today's weight", Icons.Rounded.MonitorWeight, Nafs.colors.gold, modifier) {
        HealthNumberInput(
            value = weight,
            onValueChange = onWeightChange,
            unit = "kg",
            label = "Today's weight in kilograms",
            keyboardType = KeyboardType.Decimal,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            baselineKg?.let { "Baseline ${formatHealthNumber(it)} kg" } ?: "Optional",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─── Sleep ───────────────────────────────────────────────────────────────────

@Composable
private fun SleepCard(state: HealthUiState, actions: HealthActions) {
    val tint = Nafs.colors.deen
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val sessions = state.sleepSessions
    val legacy = state.legacySleepHours
    val durations = sessions.map(SleepSessionRules::minutes)
    val total = if (durations.all { it != null && it > 0 }) {
        SleepSessionRules.formatMinutes(durations.filterNotNull().sum())
    } else {
        null
    }
    val summary = when {
        sessions.isNotEmpty() && total != null -> "$total total"
        sessions.isNotEmpty() -> "Set each start and end time"
        legacy != null -> "Saved total: ${formatHealthNumber(legacy)} hours"
        else -> "Not recorded"
    }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingStart by rememberSaveable { mutableStateOf(true) }

    NafsCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NafsIconBadge(Icons.Rounded.Bedtime, tint, size = 40.dp, iconSize = 20.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Sleep", style = MaterialTheme.typography.titleMedium)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = muted)
            }
            HealthPillButton(text = "Add", icon = Icons.Rounded.Add, tint = tint, onClick = actions.onAddSleepSession)
        }
        if (sessions.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sessions.forEachIndexed { index, session ->
                    SleepPeriodRow(
                        index = index,
                        session = session,
                        tint = tint,
                        onEditStart = {
                            editingId = session.id
                            editingStart = true
                        },
                        onEditEnd = {
                            editingId = session.id
                            editingStart = false
                        },
                        onRemove = { actions.onRemoveSleepSession(session.id) },
                    )
                }
            }
        } else {
            Spacer(Modifier.height(12.dp))
            Text(
                if (legacy != null) {
                    "Add a timed period to replace the older total-only entry."
                } else {
                    "Add each stretch of sleep, naps included, with when it started and ended."
                },
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
        }
    }

    val editing = sessions.firstOrNull { it.id == editingId }
    if (editing != null) {
        key(editing.id, editingStart) {
            SleepTimeDialog(
                title = if (editingStart) "Fell asleep at" else "Woke up at",
                value = if (editingStart) editing.start else editing.end,
                defaultMinutes = if (editingStart) DEFAULT_SLEEP_START else DEFAULT_SLEEP_END,
                onDismiss = { editingId = null },
                onConfirm = { time ->
                    if (editingStart) {
                        actions.onSleepStartChange(editing.id, time)
                    } else {
                        actions.onSleepEndChange(editing.id, time)
                    }
                    editingId = null
                },
            )
        }
    }
}

@Composable
private fun SleepPeriodRow(
    index: Int,
    session: SleepSession,
    tint: Color,
    onEditStart: () -> Unit,
    onEditEnd: () -> Unit,
    onRemove: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val duration = SleepSessionRules.minutes(session)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(scheme.surfaceContainerHigh)
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeChip("Start", session.start, onEditStart, Modifier.weight(1f))
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                TimeChip("End", session.end, onEditEnd, Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Period ${index + 1}", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                when {
                    duration != null && duration > 0 -> NafsPill(SleepSessionRules.formatMinutes(duration), color = tint)
                    duration == 0 -> Text(
                        "Start and end must differ",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.error,
                    )
                    else -> Text(
                        "Set start and end times",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Remove sleep period ${index + 1}",
                tint = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TimeChip(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val shown = clockLabel(value)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = scheme.surfaceContainer,
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            Text(
                shown ?: "Set time",
                style = MaterialTheme.typography.titleSmall,
                color = if (shown == null) scheme.primary else scheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

/** Material time picker; hands back the exact "HH:MM" 24-hour string the sleep rules expect. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimeDialog(
    title: String,
    value: String,
    defaultMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val initial = SleepSessionRules.parseMinutes(value) ?: defaultMinutes
    val pickerState = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = pickerState) },
        confirmButton = {
            TextButton(onClick = { onConfirm(clockValue(pickerState.hour, pickerState.minute)) }) { Text("Set time") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun clockValue(hour: Int, minute: Int): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

/** "22:30" → "10:30 PM"; an unreadable older entry is shown as typed; blank → null. */
private fun clockLabel(value: String): String? {
    val minutes = SleepSessionRules.parseMinutes(value) ?: return value.trim().takeIf(String::isNotEmpty)
    val hour = minutes / 60
    val twelveHour = if (hour % 12 == 0) 12 else hour % 12
    return "$twelveHour:${(minutes % 60).toString().padStart(2, '0')} ${if (hour < 12) "AM" else "PM"}"
}

// ─── Mood, notes, save ───────────────────────────────────────────────────────

@Composable
private fun MoodCard(mood: Int?, onMoodChange: (Int?) -> Unit) {
    val tint = Nafs.colors.reflect
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    NafsCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (mood != null) {
                NafsEmojiBadge(moodFace(mood), tint, size = 40.dp)
            } else {
                NafsIconBadge(Icons.Rounded.Mood, tint, size = 40.dp, iconSize = 20.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Mood", style = MaterialTheme.typography.titleMedium)
                Text(
                    mood?.let { "$it / 10 · ${moodWord(it)}" } ?: "Not recorded",
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..10).chunked(5).forEach { values ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    values.forEach { value ->
                        MoodOption(
                            value = value,
                            isSelected = mood == value,
                            tint = tint,
                            onClick = { onMoodChange(if (mood == value) null else value) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            Text("Low", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = muted)
            Text("Great", style = MaterialTheme.typography.labelSmall, color = muted)
        }
    }
}

@Composable
private fun MoodOption(value: Int, isSelected: Boolean, tint: Color, onClick: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(44.dp)
            .semantics {
                selected = isSelected
                contentDescription = "Mood $value of 10"
            },
        shape = MaterialTheme.shapes.small,
        color = if (isSelected) tint else scheme.surfaceContainerHigh,
        contentColor = if (isSelected) scheme.surface else scheme.onSurface,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("$value", style = MaterialTheme.typography.titleSmall)
        }
    }
}

private fun moodFace(mood: Int): String = when {
    mood <= 2 -> "😞"
    mood <= 4 -> "😕"
    mood <= 6 -> "😐"
    mood <= 8 -> "🙂"
    else -> "😄"
}

private fun moodWord(mood: Int): String = when {
    mood <= 2 -> "Very low"
    mood <= 4 -> "Low"
    mood <= 6 -> "Okay"
    mood <= 8 -> "Good"
    else -> "Great"
}

@Composable
private fun NotesCard(notes: String, onNotesChange: (String) -> Unit) {
    NafsCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NafsIconBadge(Icons.AutoMirrored.Rounded.Notes, Nafs.colors.health, size = 40.dp, iconSize = 20.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Notes", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Optional · energy, pain, recovery, or context",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        NafsTextField(
            value = notes,
            onValueChange = onNotesChange,
            label = "Today's note",
            singleLine = false,
            minLines = 3,
        )
    }
}

@Composable
private fun SaveCheckIn(state: HealthUiState, onSave: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NafsPrimaryButton(
            text = if (state.log == null) "Save today's check-in" else "Update today's check-in",
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.setupRequired && !state.isOffline && !state.hasSaveConflict,
            loading = state.isSaving,
            icon = Icons.Rounded.Check,
        )
        val hint = when {
            state.isOffline -> "Saving needs a connection."
            state.hasSaveConflict -> "Reload the latest entry above before saving."
            else -> null
        }
        hint?.let {
            Text(
                it,
                Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun HealthDisclaimer() {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Rounded.Shield, contentDescription = null, tint = muted, modifier = Modifier.padding(top = 2.dp).size(14.dp))
        Text(
            "NAFS stores these entries in your account for personal tracking. It does not provide medical diagnosis, " +
                "emergency guidance, or treatment recommendations.",
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = muted,
        )
    }
}

@Composable
private fun HealthProfileDialog(state: HealthUiState, actions: HealthActions) {
    AlertDialog(
        onDismissRequest = actions.onCloseProfileEditor,
        icon = { Icon(Icons.Rounded.Favorite, contentDescription = null, tint = Nafs.colors.health) },
        title = { Text("Edit health baseline") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Height and current weight are used only for your personal BMI baseline.")
                BaselineFields(state, actions)
                state.actionError?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = actions.onSaveProfile, enabled = !state.isSavingProfile) {
                if (state.isSavingProfile) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Save baseline")
                }
            }
        },
        dismissButton = { TextButton(onClick = actions.onCloseProfileEditor) { Text("Cancel") } },
    )
}

// ─── Shared health building blocks ───────────────────────────────────────────

/** Compact tonal number well: the value, with its unit on the right. Used in tiles and metric rows. */
@Composable
internal fun HealthNumberInput(
    value: String,
    onValueChange: (String) -> Unit,
    unit: String,
    label: String,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val textStyle = (if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge)
        .copy(color = scheme.onSurface)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().semantics { contentDescription = label },
        textStyle = textStyle,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        singleLine = true,
        cursorBrush = SolidColor(scheme.primary),
        decorationBox = { innerTextField ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(scheme.surfaceContainerHigh)
                    .padding(horizontal = if (compact) 10.dp else 12.dp, vertical = if (compact) 8.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text("—", style = textStyle, color = scheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                    innerTextField()
                }
                if (unit.isNotEmpty()) {
                    Text(
                        unit,
                        Modifier.padding(start = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        },
    )
}

/** Small tinted pill action, for "Add" and "Edit" next to a card title. */
@Composable
internal fun HealthPillButton(
    text: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (enabled) tint.copy(alpha = if (Nafs.colors.isDark) 0.16f else 0.11f) else scheme.onSurface.copy(alpha = 0.06f),
        contentColor = if (enabled) tint else scheme.onSurface.copy(alpha = 0.38f),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

internal fun formatHealthNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

private val HeaderDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)
private const val STEP_REFERENCE = 10_000
private const val WATER_DOTS = 8
private const val BMI_SCALE_MIN = 15.0
private const val BMI_SCALE_MAX = 40.0
private const val DEFAULT_SLEEP_START = 22 * 60 + 30
private const val DEFAULT_SLEEP_END = 6 * 60 + 30
