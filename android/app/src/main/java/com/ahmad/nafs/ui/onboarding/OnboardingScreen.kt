package com.ahmad.nafs.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.domain.onboarding.StarterHabit
import com.ahmad.nafs.domain.onboarding.starterHabits

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

@Composable
private fun OnboardingScreen(
    state: OnboardingUiState,
    onNameChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onToggleHabit: (String) -> Unit,
    onFinish: () -> Unit,
) {
    Scaffold(
        topBar = {
            Column {
                LinearProgressIndicator(
                    progress = { state.step / 2f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack, enabled = state.step > 1) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Previous step")
                    }
                    Spacer(Modifier.weight(1f))
                    Text("${state.step} / 2", style = MaterialTheme.typography.labelMedium)
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.step == 1) {
                WelcomeStep(
                    state = state,
                    onNameChange = onNameChange,
                    onNext = onNext,
                )
            } else {
                HabitStep(
                    state = state,
                    onToggleHabit = onToggleHabit,
                    onFinish = onFinish,
                )
            }
        }
    }
}

@Composable
private fun WelcomeStep(
    state: OnboardingUiState,
    onNameChange: (String) -> Unit,
    onNext: () -> Unit,
) {
    Text("Bismillah, let's set up NAFS", style = MaterialTheme.typography.headlineSmall)
    Text(
        "NAFS brings your habits, goals, daily reflection, and progress into one honest view.",
        style = MaterialTheme.typography.bodyLarge,
    )
    OutlinedTextField(
        value = state.name,
        onValueChange = onNameChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Your name") },
        supportingText = { Text("You can change this later in Profile.") },
        singleLine = true,
    )
    if (state.isFromCache) {
        StatusMessage("You are offline. You can review setup, but finishing requires a connection.")
    }
    state.errorMessage?.let { ErrorMessage(it) }
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
        Text("Choose starter habits")
    }
}

@Composable
private fun HabitStep(
    state: OnboardingUiState,
    onToggleHabit: (String) -> Unit,
    onFinish: () -> Unit,
) {
    Text("Pick your starter habits", style = MaterialTheme.typography.headlineSmall)
    Text(
        "Choose only what you intend to track. Retries are idempotent, so saving twice will not duplicate these habits.",
        style = MaterialTheme.typography.bodyMedium,
    )
    starterHabits.forEach { habit ->
        StarterHabitCard(
            habit = habit,
            selected = habit.key in state.selectedHabitKeys,
            onClick = { onToggleHabit(habit.key) },
        )
    }
    val selectedScore = starterHabits
        .filter { it.key in state.selectedHabitKeys }
        .sumOf(StarterHabit::scoreWeight)
    Text(
        "${state.selectedHabitKeys.size} selected · $selectedScore possible daily points",
        style = MaterialTheme.typography.labelLarge,
    )
    state.errorMessage?.let { ErrorMessage(it) }
    Button(
        onClick = onFinish,
        enabled = !state.isSaving,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (state.isSaving) {
            CircularProgressIndicator(
                modifier = Modifier.height(20.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Text(if (state.selectedHabitKeys.isEmpty()) "Skip for now" else "Finish setup")
        }
    }
}

@Composable
private fun StarterHabitCard(
    habit: StarterHabit,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                this.selected = selected
            }
            .clickable(role = Role.Checkbox, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(habit.emoji, style = MaterialTheme.typography.headlineSmall)
            Column(modifier = Modifier.weight(1f)) {
                Text(habit.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    buildString {
                        if (habit.timeTargetMinutes > 0) append("${habit.timeTargetMinutes} min · ")
                        append("${habit.scoreWeight} pt")
                        if (habit.scoreWeight != 1) append("s")
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = "Selected")
            }
        }
    }
}

@Composable
private fun ErrorMessage(message: String) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun StatusMessage(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
