package com.ahmad.nafs.ui.challenges

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmad.nafs.core.challenges.Challenge
import com.ahmad.nafs.core.challenges.ChallengeDefinitionValidator
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.domain.challenges.ChallengeFrequency
import com.ahmad.nafs.domain.challenges.ChallengeCadence
import com.ahmad.nafs.domain.challenges.ChallengeStatus
import com.ahmad.nafs.domain.challenges.displayName
import com.ahmad.nafs.domain.challenges.unitName
import com.ahmad.nafs.ui.AccountDayRefreshEffects
import com.ahmad.nafs.ui.components.NafsHeroCard
import com.ahmad.nafs.ui.components.NafsSectionTitle
import com.ahmad.nafs.ui.components.NafsStatusPill
import java.time.LocalDate

@Composable
fun ChallengeRoute(
    profile: UserProfile,
    createOnOpen: Boolean = false,
    viewModel: ChallengeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(profile.userId, profile.timezone) {
        viewModel.initialize(profile.userId, profile.timezone)
        if (createOnOpen) viewModel.openCreate()
    }
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) viewModel.cancelPhotoPicker() else viewModel.submitPhotoEvidence(uri)
    }
    LaunchedEffect(state.photoPickerChallengeId) {
        if (state.photoPickerChallengeId != null) {
            photoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }
    }
    AccountDayRefreshEffects(profile.timezone, viewModel::refresh)
    ChallengeScreen(state, viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChallengeScreen(state: ChallengeUiState, viewModel: ChallengeViewModel) {
    val visible = state.visibleChallenges()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Challenges")
                        Text(
                            "Keep one promise at a time",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    IconButton(
                        onClick = viewModel::refresh,
                        enabled = !state.isLoading && !state.isRefreshing,
                    ) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh challenges")
                        }
                    }
                    IconButton(onClick = viewModel::openCreate) {
                        Icon(Icons.Rounded.Add, contentDescription = "Create challenge")
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

            state.loadError != null && state.challenges.isEmpty() -> ChallengeLoadFailure(
                state.loadError,
                viewModel::refresh,
                Modifier.padding(padding),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.isOffline) item { ChallengeOfflineBanner() }
                state.actionError?.let { message ->
                    item { ChallengeErrorBanner(message, viewModel::dismissError) }
                }
                item { ChallengeStatusFilters(state.selectedStatus, viewModel::selectStatus) }
                item { ChallengeHero(state.challenges, state.today) }
                if (visible.isEmpty()) {
                    item { EmptyChallenges(state.selectedStatus, viewModel::openCreate) }
                } else {
                    item {
                        NafsSectionTitle(
                            title = if (state.selectedStatus == ChallengeStatus.COMPLETED) {
                                "Promises kept"
                            } else {
                                "Your commitments"
                            },
                            supporting = "Check-ins follow your account timezone and chosen cadence.",
                        )
                    }
                    items(visible, key = Challenge::id) { challenge ->
                        ChallengeCard(
                            challenge = challenge,
                            today = state.today,
                            pending = challenge.id in state.pendingChallengeIds,
                            onEdit = { viewModel.openEdit(challenge) },
                            onDelete = { viewModel.requestDelete(challenge) },
                            onRestart = { viewModel.restart(challenge) },
                            onCheckIn = { completed ->
                                viewModel.requestCheckIn(challenge, completed)
                            },
                            onSadqaPaidChange = { date, paid ->
                                viewModel.setSadqaPaid(challenge, date, paid)
                            },
                        )
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }

    state.editor?.let { ChallengeEditorDialog(it, state, viewModel) }
    state.checkInRequest?.let { request ->
        AlertDialog(
            onDismissRequest = viewModel::cancelCheckIn,
            icon = {
                Icon(
                    if (request.completed) Icons.Rounded.Check else Icons.Rounded.Whatshot,
                    contentDescription = null,
                )
            },
            title = {
                Text(if (request.completed) "Promise kept?" else "Record a missed period?")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (request.completed) {
                            "Mark ${request.challenge.title} complete for this ${request.challenge.frequency.unitName()}?"
                        } else {
                            "This records an honest miss for today. You can still return and check in before the current period ends."
                        },
                    )
                    if (!request.completed && request.challenge.sadqaAmount != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Checkbox(
                                checked = request.sadqaPaid,
                                onCheckedChange = viewModel::setCheckInSadqaPaid,
                            )
                            Text(
                                "I have paid ${formatMoney(request.challenge.sadqaAmount, request.challenge.sadqaCurrency)} sadqa",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = viewModel::confirmCheckIn) {
                    Text(if (request.completed) "Mark done" else "Record miss")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelCheckIn) { Text("Cancel") }
            },
        )
    }
    state.deleteCandidate?.let { challenge ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("Delete ${challenge.title}?") },
            text = {
                Text("This permanently removes the challenge and every check-in. This cannot be undone.")
            },
            confirmButton = { Button(onClick = viewModel::confirmDelete) { Text("Delete") } },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ChallengeStatusFilters(
    selected: ChallengeStatus?,
    onSelect: (ChallengeStatus?) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("All") })
        FilterChip(
            selected = selected == ChallengeStatus.ACTIVE,
            onClick = { onSelect(ChallengeStatus.ACTIVE) },
            label = { Text("Active") },
        )
        FilterChip(
            selected = selected == ChallengeStatus.COMPLETED,
            onClick = { onSelect(ChallengeStatus.COMPLETED) },
            label = { Text("Completed") },
        )
    }
}

@Composable
private fun ChallengeHero(challenges: List<Challenge>, today: LocalDate) {
    val active = challenges.filter { it.status == ChallengeStatus.ACTIVE }
    val checked = active.count { it.checkedThisPeriod(today) }
    val bestStreak = challenges.maxOfOrNull(Challenge::longestStreak) ?: 0
    NafsHeroCard(
        eyebrow = "Consistency over intensity",
        title = "$checked of ${active.size} on track",
        body = "Best streak: $bestStreak periods. Every honest check-in protects the rhythm.",
    ) {
        LinearProgressIndicator(
            progress = { if (active.isEmpty()) 0f else checked.toFloat() / active.size },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f),
        )
    }
}

@Composable
private fun ChallengeCard(
    challenge: Challenge,
    today: LocalDate,
    pending: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRestart: () -> Unit,
    onCheckIn: (Boolean) -> Unit,
    onSadqaPaidChange: (LocalDate, Boolean) -> Unit,
) {
    val explicitMiss = challenge.currentCheckIns().any { it.date == today && !it.completed }
    val checked = challenge.checkedThisPeriod(today)
    val elapsed = challenge.elapsedUnits(today).coerceAtMost(challenge.totalUnits())
    val currentPeriodStart = ChallengeCadence.periodStart(challenge.frequency, today)
    val currentMiss = challenge.currentCheckIns().firstOrNull {
        !it.completed && it.date in currentPeriodStart..today
    }
    val completedCheckIn = challenge.currentCheckIns().firstOrNull {
        it.completed && it.date in currentPeriodStart..today
    }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (challenge.status == ChallengeStatus.COMPLETED) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(challenge.emoji, style = MaterialTheme.typography.headlineMedium)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NafsStatusPill(challenge.frequency.displayName())
                        if (challenge.requiresPhoto) NafsStatusPill("Photo proof", accent = false)
                        challenge.sadqaAmount?.let {
                            NafsStatusPill(formatMoney(it, challenge.sadqaCurrency), accent = false)
                        }
                        if (challenge.status == ChallengeStatus.COMPLETED) {
                            NafsStatusPill("Complete", accent = false)
                        }
                    }
                    Text(
                        challenge.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 7.dp),
                    )
                }
                IconButton(onClick = onEdit, enabled = !pending) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Edit ${challenge.title}")
                }
                IconButton(onClick = onDelete, enabled = !pending) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete ${challenge.title}")
                }
            }

            Text(
                challenge.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { challenge.progressPercent(today) / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "$elapsed / ${challenge.totalUnits()} ${challenge.frequency.unitName()}s",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    "${challenge.currentStreak} current · ${challenge.longestStreak} best",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (challenge.requiresPhoto && challenge.status == ChallengeStatus.ACTIVE && !checked) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                        Text(
                            "A photo from Android's system picker is required. NAFS does not request access to your full gallery.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            if (challenge.status != ChallengeStatus.ACTIVE) OutlinedButton(onClick = onRestart, enabled = !pending) { Text("Start a new attempt") }
            if (challenge.restartCount > 0) Text("Attempt ${challenge.restartCount + 1}. Previous activity is retained.", style = MaterialTheme.typography.bodySmall)
            if (challenge.status == ChallengeStatus.ACTIVE) {
                HorizontalDivider()
                if (challenge.needsRestart(today)) OutlinedButton(onClick = onRestart, enabled = !pending) { Text("Start a new attempt") }
                if (checked) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "Checked in for this ${challenge.frequency.unitName()}",
                                modifier = Modifier.padding(start = 8.dp),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        if (completedCheckIn?.photoUrl != null) {
                            Text(
                                "Proof photo uploaded",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    if (explicitMiss) {
                        Text(
                            "Miss recorded today. Return now if you still complete it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onCheckIn(true) },
                            enabled = !pending,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (pending) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    if (challenge.requiresPhoto) Icons.Rounded.CameraAlt else Icons.Rounded.Check,
                                    contentDescription = null,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(if (challenge.requiresPhoto) "Add proof" else "Done")
                            }
                        }
                        OutlinedButton(
                            onClick = { onCheckIn(false) },
                            enabled = !pending,
                        ) { Text("Missed") }
                    }
                    if (currentMiss != null && challenge.sadqaAmount != null) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            ),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    if (currentMiss.sadqaPaid) {
                                        "Sadqa recorded as paid"
                                    } else {
                                        "Sadqa due: ${formatMoney(challenge.sadqaAmount, challenge.sadqaCurrency)}"
                                    },
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                TextButton(
                                    onClick = {
                                        onSadqaPaidChange(currentMiss.date, !currentMiss.sadqaPaid)
                                    },
                                    enabled = !pending,
                                ) {
                                    Text(if (currentMiss.sadqaPaid) "Undo" else "Mark paid")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChallengeEditorDialog(
    draft: ChallengeEditorDraft,
    state: ChallengeUiState,
    viewModel: ChallengeViewModel,
) {
    AlertDialog(
        onDismissRequest = viewModel::closeEditor,
        title = { Text(if (draft.challengeId == null) "Start a challenge" else "Edit challenge") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = draft.emoji,
                    onValueChange = viewModel::setEmoji,
                    label = { Text("Emoji") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = viewModel::setTitle,
                    label = { Text("Challenge") },
                    supportingText = { Text("What promise are you making?") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.description,
                    onValueChange = viewModel::setDescription,
                    label = { Text("Your reason") },
                    supportingText = { Text("Required · make the reason specific and personal") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Cadence", style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChallengeFrequency.entries.forEach { frequency ->
                        FilterChip(
                            selected = draft.frequency == frequency,
                            onClick = { viewModel.setFrequency(frequency) },
                            label = { Text(frequency.displayName()) },
                        )
                    }
                }
                OutlinedTextField(
                    value = draft.durationDays,
                    onValueChange = viewModel::setDurationDays,
                    label = { Text("Duration in days") },
                    supportingText = {
                        Text(
                            "${ChallengeDefinitionValidator.minimumDuration(draft.frequency)}–" +
                                "${ChallengeDefinitionValidator.maximumDuration(draft.frequency)} days",
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Require photo proof", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "Uses Android's system photo picker; no gallery permission.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = draft.requiresPhoto,
                        onCheckedChange = viewModel::setRequiresPhoto,
                    )
                }
                OutlinedTextField(
                    value = draft.sadqaAmount,
                    onValueChange = viewModel::setSadqaAmount,
                    label = { Text("Sadqa pledge (optional)") },
                    supportingText = { Text("Recorded only when you mark a period missed") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (draft.sadqaAmount.isNotBlank()) {
                    Text("Currency", style = MaterialTheme.typography.labelLarge)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ChallengeDefinitionValidator.SUPPORTED_CURRENCIES.forEach { currency ->
                            FilterChip(
                                selected = draft.sadqaCurrency == currency,
                                onClick = { viewModel.setSadqaCurrency(currency) },
                                label = { Text(currency) },
                            )
                        }
                    }
                }
                state.editorError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = viewModel::save, enabled = !state.isSaving) {
                if (state.isSaving) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (draft.challengeId == null) "Start" else "Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::closeEditor, enabled = !state.isSaving) { Text("Cancel") }
        },
    )
}

private fun formatMoney(amount: Double, currency: String): String =
    "$currency ${if (amount % 1.0 == 0.0) amount.toLong() else amount}"

@Composable
private fun EmptyChallenges(status: ChallengeStatus?, onCreate: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.EmojiEvents, contentDescription = null, Modifier.size(40.dp))
            Text(
                if (status == ChallengeStatus.COMPLETED) "No completed challenges yet" else "Choose one honest promise",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                if (status == ChallengeStatus.COMPLETED) {
                    "Completed challenges will collect here."
                } else {
                    "Start small, choose the right cadence, and build evidence through check-ins."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (status != ChallengeStatus.COMPLETED) {
                Button(onClick = onCreate) { Text("Start a challenge") }
            }
        }
    }
}

@Composable
private fun ChallengeOfflineBanner() {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.CloudOff, contentDescription = null)
            Text("Showing cached challenges. Check-ins and changes still require a connection.")
        }
    }
}

@Composable
private fun ChallengeErrorBanner(message: String, onDismiss: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

@Composable
private fun ChallengeLoadFailure(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Challenges unavailable", style = MaterialTheme.typography.titleLarge)
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}

