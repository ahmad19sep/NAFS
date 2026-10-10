package com.ahmad.nafs.ui.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.domain.onboarding.StarterHabit
import com.ahmad.nafs.domain.onboarding.starterHabits
import com.ahmad.nafs.ui.components.NafsBanner
import com.ahmad.nafs.ui.components.NafsBannerKind
import com.ahmad.nafs.ui.components.NafsBrandLockup
import com.ahmad.nafs.ui.components.NafsCard
import com.ahmad.nafs.ui.components.NafsEmojiBadge
import com.ahmad.nafs.ui.components.NafsEyebrow
import com.ahmad.nafs.ui.components.NafsIconBadge
import com.ahmad.nafs.ui.components.NafsPrimaryButton
import com.ahmad.nafs.ui.components.NafsTextField
import com.ahmad.nafs.ui.theme.Nafs

@Composable
fun OnboardingRoute(
    profile: UserProfile,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    LaunchedEffect(profile.userId) { viewModel.initialize(profile) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OnboardingScreen(
        state = state,
        onNameChange = viewModel::setName,
        onNext = viewModel::next,
        onBack = viewModel::back,
        onToggleHabit = viewModel::toggleHabit,
        onFinish = viewModel::finish,
    )
}

private const val STEPS = 2

/** Two calm steps before the app opens: who you are, then what you'll track. */
@Composable
internal fun OnboardingScreen(
    state: OnboardingUiState,
    onNameChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onToggleHabit: (String) -> Unit,
    onFinish: () -> Unit,
) {
    val c = Nafs.colors
    // Shown before the app shell, so this Surface supplies the page colour and content colour itself.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to c.heroTop.copy(alpha = if (c.isDark) 0.85f else 0.14f),
                        0.42f to Color.Transparent,
                    ),
                ),
        ) {
            Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
                StepHeader(step = state.step, onBack = onBack)
                if (state.step == 1) {
                    WelcomeStep(state, onNameChange, onContinue = onNext, modifier = Modifier.weight(1f))
                } else {
                    HabitStep(state, onToggleHabit, modifier = Modifier.weight(1f))
                }
                BottomActions(state, onNext = onNext, onFinish = onFinish)
            }
        }
    }
}

// ─── Step indicator ──────────────────────────────────────────────────────────

@Composable
private fun StepHeader(step: Int, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (step > 1) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Previous step")
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
        Column(
            Modifier.weight(1f).semantics(mergeDescendants = true) {},
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StepDots(step)
            Text("Step $step of $STEPS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.size(48.dp))
    }
}

@Composable
private fun StepDots(step: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..STEPS).forEach { index ->
            val dotWidth by animateDpAsState(if (index == step) 28.dp else 10.dp, label = "step-dot")
            Box(
                Modifier
                    .height(6.dp)
                    .width(dotWidth)
                    .clip(CircleShape)
                    .background(if (index <= step) scheme.primary else scheme.outlineVariant),
            )
        }
    }
}

// ─── Step 1: welcome ─────────────────────────────────────────────────────────

@Composable
private fun WelcomeStep(
    state: OnboardingUiState,
    onNameChange: (String) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        NafsBrandLockup(markSize = 80.dp)
        Spacer(Modifier.height(30.dp))
        Text("Bismillah, let's set up NAFS", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "NAFS brings your habits, goals, daily reflection, and progress into one honest view.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))
        FeatureStrip()
        Spacer(Modifier.height(24.dp))
        NafsTextField(
            value = state.name,
            onValueChange = onNameChange,
            label = "Your name",
            leadingIcon = Icons.Rounded.Badge,
            supportingText = "You can change this later in Profile.",
            isError = state.errorMessage != null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { onContinue() }),
        )
        if (state.isFromCache) {
            Spacer(Modifier.height(12.dp))
            NafsBanner("You are offline. You can review setup, but finishing requires a connection.", kind = NafsBannerKind.OFFLINE)
        }
        state.errorMessage?.let { message ->
            Spacer(Modifier.height(12.dp))
            NafsBanner(message, kind = NafsBannerKind.ERROR)
        }
        Spacer(Modifier.height(8.dp))
    }
}

private data class Feature(val icon: ImageVector, val label: String, val tint: Color)

@Composable
private fun FeatureStrip() {
    val c = Nafs.colors
    val features = listOf(
        Feature(Icons.Rounded.Autorenew, "Habits", c.habits),
        Feature(Icons.Rounded.Flag, "Goals", c.goals),
        Feature(Icons.Rounded.Insights, "Progress", c.tasks),
        Feature(Icons.Rounded.WbTwilight, "Reflection", c.reflect),
    )
    NafsCard(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            features.forEach { feature ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NafsIconBadge(feature.icon, feature.tint, size = 44.dp, iconSize = 21.dp)
                    Text(feature.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ─── Step 2: starter habits ──────────────────────────────────────────────────

@Composable
private fun HabitStep(
    state: OnboardingUiState,
    onToggleHabit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            NafsEyebrow("Starter pack", color = Nafs.colors.habits)
            Text("Pick your starter habits", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Choose only what you intend to track. Saving again never creates duplicates.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.isFromCache) {
            NafsBanner("You are offline. You can review setup, but finishing requires a connection.", kind = NafsBannerKind.OFFLINE)
        }
        starterHabits.forEach { habit ->
            HabitChoiceCard(
                habit = habit,
                selected = habit.key in state.selectedHabitKeys,
                onToggle = { onToggleHabit(habit.key) },
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun HabitChoiceCard(habit: StarterHabit, selected: Boolean, onToggle: () -> Unit) {
    val c = Nafs.colors
    val scheme = MaterialTheme.colorScheme
    val tint = c.habits
    val shape = MaterialTheme.shapes.large
    NafsCard(
        modifier = Modifier
            .clip(shape)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() }),
        color = if (selected) tint.copy(alpha = if (c.isDark) 0.12f else 0.08f).compositeOver(scheme.surfaceContainer) else scheme.surfaceContainer,
        borderColor = if (selected) tint.copy(alpha = 0.7f) else c.cardBorder,
        shape = shape,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            NafsEmojiBadge(habit.emoji, tint, size = 46.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(habit.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    habitSummary(habit),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SelectionMark(selected, tint)
        }
    }
}

@Composable
private fun SelectionMark(selected: Boolean, tint: Color) {
    Box(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(if (selected) tint else Color.Transparent)
            .border(2.dp, if (selected) tint else MaterialTheme.colorScheme.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(17.dp))
    }
}

/** One line from the habit's own targets, so it never drifts from what will be created. */
private fun habitSummary(habit: StarterHabit): String {
    val target = when (habit.type) {
        "duration" -> if (habit.targetValue >= 60 && habit.targetValue % 60 == 0) "${habit.targetValue / 60} h a day" else "${habit.targetValue} min a day"
        "counter" -> "${habit.targetValue} ${habit.unit.takeUnless { it.isBlank() || it == "count" } ?: "times"} a day"
        else -> if (habit.timeTargetMinutes > 0) "${habit.timeTargetMinutes} min a day" else "Every day"
    }
    val points = if (habit.scoreWeight == 1) "1 pt" else "${habit.scoreWeight} pts"
    return "$target · $points"
}

// ─── Pinned actions ──────────────────────────────────────────────────────────

/** Finishing setup saves this phone's timezone, so the last step says which one before it happens. */
@Composable
private fun TimezoneNote(label: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(scheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NafsIconBadge(Icons.Rounded.Schedule, Nafs.colors.tasks, size = 34.dp, iconSize = 18.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Your days follow $label", style = MaterialTheme.typography.titleSmall)
            Text(
                "Taken from this phone. You can change it in Profile.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BottomActions(state: OnboardingUiState, onNext: () -> Unit, onFinish: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.step == 1) {
            NafsPrimaryButton("Choose starter habits", onClick = onNext, modifier = Modifier.fillMaxWidth())
        } else {
            state.errorMessage?.let { NafsBanner(it, kind = NafsBannerKind.ERROR) }
            state.deviceTimezoneLabel?.let { TimezoneNote(it) }
            val count = state.selectedHabitKeys.size
            val points = starterHabits.filter { it.key in state.selectedHabitKeys }.sumOf(StarterHabit::scoreWeight)
            Text(
                if (count == 0) "Nothing selected. You can add habits any time." else "$count selected · $points possible daily points",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { contentDescription = if (count == 0) "No habits selected" else "$count habits selected, $points possible daily points" },
            )
            NafsPrimaryButton(
                text = if (count == 0) "Skip for now" else "Finish setup",
                onClick = onFinish,
                loading = state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
