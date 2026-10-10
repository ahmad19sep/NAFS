package com.ahmad.nafs.ui.challenges

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmad.nafs.core.challenges.Challenge
import com.ahmad.nafs.core.challenges.ChallengeCheckIn
import com.ahmad.nafs.core.challenges.ChallengeDefinitionValidator
import com.ahmad.nafs.core.challenges.outcomeSummary
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.domain.challenges.ChallengeCadence
import com.ahmad.nafs.domain.challenges.ChallengeFrequency
import com.ahmad.nafs.domain.challenges.ChallengePeriod
import com.ahmad.nafs.domain.challenges.ChallengePeriodStatus
import com.ahmad.nafs.domain.challenges.ChallengeStatus
import com.ahmad.nafs.domain.challenges.ChallengeTimeline
import com.ahmad.nafs.domain.challenges.displayName
import com.ahmad.nafs.domain.challenges.unitName
import com.ahmad.nafs.ui.AccountDayRefreshEffects
import com.ahmad.nafs.ui.components.NafsBanner
import com.ahmad.nafs.ui.components.NafsBannerKind
import com.ahmad.nafs.ui.components.NafsCard
import com.ahmad.nafs.ui.components.NafsChoiceChip
import com.ahmad.nafs.ui.components.NafsDivider
import com.ahmad.nafs.ui.components.NafsEmojiBadge
import com.ahmad.nafs.ui.components.NafsEmptyState
import com.ahmad.nafs.ui.components.NafsErrorState
import com.ahmad.nafs.ui.components.NafsEyebrow
import com.ahmad.nafs.ui.components.NafsHeroSurface
import com.ahmad.nafs.ui.components.NafsIconBadge
import com.ahmad.nafs.ui.components.NafsPageHeader
import com.ahmad.nafs.ui.components.NafsPill
import com.ahmad.nafs.ui.components.NafsPrimaryButton
import com.ahmad.nafs.ui.components.NafsProgressBar
import com.ahmad.nafs.ui.components.NafsProgressRing
import com.ahmad.nafs.ui.components.NafsSecondaryButton
import com.ahmad.nafs.ui.components.NafsSectionTitle
import com.ahmad.nafs.ui.components.NafsSegmented
import com.ahmad.nafs.ui.components.NafsSpace
import com.ahmad.nafs.ui.components.NafsStat
import com.ahmad.nafs.ui.components.NafsTextField
import com.ahmad.nafs.ui.theme.Nafs
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
    LaunchedEffect(state.photoRequest) {
        if (state.photoRequest != null) {
            photoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }
    }
    // A completion is confirmed with a haptic only once the server has accepted it.
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ChallengeUiEvent.CheckInDone -> haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            }
        }
    }
    AccountDayRefreshEffects(profile.timezone, viewModel::refresh)
    val actions = remember(viewModel) {
        ChallengeActions(
            onRefresh = viewModel::refresh,
            onCreate = viewModel::openCreate,
            onSelectStatus = viewModel::selectStatus,
            onEdit = viewModel::openEdit,
            onRequestDelete = viewModel::requestDelete,
            onConfirmDelete = viewModel::confirmDelete,
            onCancelDelete = viewModel::cancelDelete,
            onRestart = viewModel::restart,
            onCheckIn = viewModel::requestCheckIn,
            onConfirmCheckIn = viewModel::confirmCheckIn,
            onCancelCheckIn = viewModel::cancelCheckIn,
            onCheckInSadqaPaidChange = viewModel::setCheckInSadqaPaid,
            onSadqaPaidChange = viewModel::setSadqaPaid,
            onToggleHistory = viewModel::toggleHistory,
            onOpenPhoto = viewModel::openPhoto,
            onClosePhoto = viewModel::closePhoto,
            onDismissCompletedNotice = viewModel::dismissCompletedNotice,
            onDismissError = viewModel::dismissError,
            editor = ChallengeEditorActions(
                onClose = viewModel::closeEditor,
                onSave = viewModel::save,
                onTitleChange = viewModel::setTitle,
                onEmojiChange = viewModel::setEmoji,
                onDescriptionChange = viewModel::setDescription,
                onFrequencyChange = viewModel::setFrequency,
                onDurationChange = viewModel::setDurationDays,
                onRequiresPhotoChange = viewModel::setRequiresPhoto,
                onSadqaAmountChange = viewModel::setSadqaAmount,
                onSadqaCurrencyChange = viewModel::setSadqaCurrency,
            ),
        )
    }
    ChallengeScreen(state, actions)
}

/** Everything the Challenges screen can ask for, so the screen stays a pure function of its state. */
internal data class ChallengeActions(
    val onRefresh: () -> Unit = {},
    val onCreate: () -> Unit = {},
    val onSelectStatus: (ChallengeStatus?) -> Unit = {},
    val onEdit: (Challenge) -> Unit = {},
    val onRequestDelete: (Challenge) -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onCancelDelete: () -> Unit = {},
    val onRestart: (Challenge) -> Unit = {},
    /** Challenge, done (or missed), and the date checked in for: today, or yesterday on a daily grace day. */
    val onCheckIn: (Challenge, Boolean, LocalDate) -> Unit = { _, _, _ -> },
    val onConfirmCheckIn: () -> Unit = {},
    val onCancelCheckIn: () -> Unit = {},
    val onCheckInSadqaPaidChange: (Boolean) -> Unit = {},
    val onSadqaPaidChange: (Challenge, LocalDate, Boolean) -> Unit = { _, _, _ -> },
    val onToggleHistory: (String) -> Unit = {},
    /** Photo URL and its label. */
    val onOpenPhoto: (String, String) -> Unit = { _, _ -> },
    val onClosePhoto: () -> Unit = {},
    val onDismissCompletedNotice: () -> Unit = {},
    val onDismissError: () -> Unit = {},
    val editor: ChallengeEditorActions = ChallengeEditorActions(),
)

/** Field-level callbacks for the challenge editor sheet. */
internal data class ChallengeEditorActions(
    val onClose: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onTitleChange: (String) -> Unit = {},
    val onEmojiChange: (String) -> Unit = {},
    val onDescriptionChange: (String) -> Unit = {},
    val onFrequencyChange: (ChallengeFrequency) -> Unit = {},
    val onDurationChange: (String) -> Unit = {},
    val onRequiresPhotoChange: (Boolean) -> Unit = {},
    val onSadqaAmountChange: (String) -> Unit = {},
    val onSadqaCurrencyChange: (String) -> Unit = {},
)

@Composable
internal fun ChallengeScreen(state: ChallengeUiState, actions: ChallengeActions) {
    when {
        state.isLoading -> Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ChallengeHeader(state, actions.onRefresh, actions.onCreate, Modifier.gutter())
            ChallengeSkeleton()
        }

        state.loadError != null && state.challenges.isEmpty() -> Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ChallengeHeader(state, actions.onRefresh, actions.onCreate, Modifier.gutter())
            NafsErrorState(
                title = "Challenges unavailable",
                message = state.loadError,
                onRetry = actions.onRefresh,
                modifier = Modifier.weight(1f),
            )
        }

        else -> ChallengeList(state, actions)
    }

    state.editor?.let { draft -> ChallengeEditorSheet(draft, state, actions.editor) }
    state.checkInRequest?.let { request ->
        CheckInDialog(
            request = request,
            today = state.today,
            onConfirm = actions.onConfirmCheckIn,
            onDismiss = actions.onCancelCheckIn,
            onSadqaPaidChange = actions.onCheckInSadqaPaidChange,
        )
    }
    state.deleteCandidate?.let { candidate ->
        ChallengeDeleteDialog(candidate, onConfirm = actions.onConfirmDelete, onDismiss = actions.onCancelDelete)
    }
    state.photoViewer?.let { photo -> ProofPhotoDialog(photo, onDismiss = actions.onClosePhoto) }
}

@Composable
private fun ChallengeList(state: ChallengeUiState, actions: ChallengeActions) {
    val visible = state.visibleChallenges()
    val justCompleted = state.completedNoticeId
        ?.takeIf { state.selectedStatus != ChallengeStatus.COMPLETED }
        ?.let { id -> state.challenges.firstOrNull { it.id == id } }
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "challenges-header") { ChallengeHeader(state, actions.onRefresh, actions.onCreate, Modifier.gutter()) }

        if (state.isOffline) item(key = "challenges-offline") {
            NafsBanner(
                message = "Showing cached challenges. Check-ins and changes still require a connection.",
                modifier = Modifier.gutter(),
                kind = NafsBannerKind.OFFLINE,
                actionLabel = "Retry",
                onAction = actions.onRefresh,
            )
        }
        state.loadError?.let { message ->
            item(key = "challenges-load-error") {
                NafsBanner(
                    message = message,
                    modifier = Modifier.gutter(),
                    kind = NafsBannerKind.WARNING,
                    title = "Couldn't refresh challenges",
                    onDismiss = actions.onDismissError,
                )
            }
        }
        state.actionError?.let { message ->
            item(key = "challenges-action-error") {
                NafsBanner(message = message, modifier = Modifier.gutter(), kind = NafsBannerKind.ERROR, onDismiss = actions.onDismissError)
            }
        }
        justCompleted?.let { challenge ->
            item(key = "challenges-completed") {
                NafsBanner(
                    message = "${challenge.title} · ${challenge.outcomeSummary(challenge.timeline(state.today))}",
                    modifier = Modifier.gutter(),
                    kind = NafsBannerKind.SUCCESS,
                    title = "Challenge complete",
                    actionLabel = "View",
                    onAction = { actions.onSelectStatus(ChallengeStatus.COMPLETED) },
                    onDismiss = actions.onDismissCompletedNotice,
                )
            }
        }

        if (state.challenges.isNotEmpty()) item(key = "challenges-hero") {
            ChallengeHero(state.challenges, state.today, Modifier.gutter())
        }
        if (state.challenges.isNotEmpty() || state.selectedStatus != ChallengeStatus.ACTIVE) item(key = "challenges-filter") {
            ChallengeStatusFilter(state.selectedStatus, actions.onSelectStatus, Modifier.gutter())
        }

        if (visible.isEmpty()) {
            item(key = "challenges-empty") { EmptyChallenges(state.selectedStatus, actions.onCreate, Modifier.gutter()) }
        } else {
            item(key = "challenges-section") {
                NafsSectionTitle(
                    title = if (state.selectedStatus == ChallengeStatus.COMPLETED) "Promises kept" else "Your commitments",
                    modifier = Modifier.gutter().padding(top = 4.dp),
                    supporting = "Check-ins follow your account timezone and chosen cadence.",
                )
            }
            items(visible, key = Challenge::id) { challenge ->
                ChallengeCard(
                    challenge = challenge,
                    today = state.today,
                    pending = challenge.id in state.pendingChallengeIds,
                    historyOpen = challenge.id in state.expandedChallengeIds,
                    onEdit = { actions.onEdit(challenge) },
                    onDelete = { actions.onRequestDelete(challenge) },
                    onRestart = { actions.onRestart(challenge) },
                    onCheckIn = { completed, date -> actions.onCheckIn(challenge, completed, date) },
                    onSadqaPaidChange = { date, paid -> actions.onSadqaPaidChange(challenge, date, paid) },
                    onToggleHistory = { actions.onToggleHistory(challenge.id) },
                    onOpenPhoto = actions.onOpenPhoto,
                    modifier = Modifier.gutter(),
                )
            }
        }
    }
}

// ─── Header, hero and filter ─────────────────────────────────────────────────

@Composable
private fun ChallengeHeader(state: ChallengeUiState, onRefresh: () -> Unit, onCreate: () -> Unit, modifier: Modifier = Modifier) {
    NafsPageHeader(
        title = "Challenges",
        modifier = modifier,
        eyebrow = "Streaks with stakes",
        subtitle = challengeSummary(state),
        actions = {
            IconButton(onClick = onRefresh, enabled = !state.isLoading && !state.isRefreshing) {
                if (state.isRefreshing) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh challenges", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Surface(
                onClick = onCreate,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(start = 4.dp).size(44.dp).semantics { contentDescription = "Create challenge" },
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, contentDescription = null) }
            }
        },
    )
}

@Composable
private fun ChallengeHero(challenges: List<Challenge>, today: LocalDate, modifier: Modifier = Modifier) {
    val c = Nafs.colors
    val active = challenges.filter { it.status == ChallengeStatus.ACTIVE }
    val checked = active.count { it.checkedThisPeriod(today) }
    val bestStreak = challenges.maxOfOrNull(Challenge::longestStreak) ?: 0
    val kept = challenges.count { it.status == ChallengeStatus.COMPLETED }
    val progress = if (active.isEmpty()) 0f else checked.toFloat() / active.size
    val headline = when {
        active.isEmpty() -> "No active promises right now."
        checked == active.size -> "Every promise kept this period."
        else -> "${active.size - checked} still waiting on you."
    }

    NafsHeroSurface(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                NafsProgressRing(
                    progress = progress,
                    size = 104.dp,
                    stroke = 10.dp,
                    colors = listOf(c.heroGold, c.heroMint),
                    trackColor = Color.White.copy(alpha = 0.12f),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$checked/${active.size}", style = MaterialTheme.typography.headlineMedium, color = c.onHero)
                        Text("checked in", style = MaterialTheme.typography.labelSmall, color = c.onHeroMuted)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    NafsEyebrow("Consistency over intensity", color = c.heroGold)
                    Text(headline, style = MaterialTheme.typography.titleLarge, color = c.onHero)
                    Text(
                        "Every honest check-in protects the rhythm.",
                        style = MaterialTheme.typography.labelMedium,
                        color = c.onHeroMuted,
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.10f)))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                HeroStat("${active.size}", "Active", Modifier.weight(1f))
                HeroStat("$kept", "Completed", Modifier.weight(1f))
                HeroStat("$bestStreak", "Best streak", Modifier.weight(1f), valueColor = if (bestStreak > 0) c.heroGold else c.onHero)
            }
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String, modifier: Modifier = Modifier, valueColor: Color = Nafs.colors.onHero) {
    NafsStat(value = value, label = label, modifier = modifier, valueColor = valueColor, labelColor = Nafs.colors.onHeroMuted)
}

@Composable
private fun ChallengeStatusFilter(
    selected: ChallengeStatus?,
    onSelect: (ChallengeStatus?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(ChallengeStatus.ACTIVE, ChallengeStatus.COMPLETED, null)
    NafsSegmented(
        options = listOf("Active", "Completed", "All"),
        selectedIndex = options.indexOf(selected),
        onSelect = { index -> onSelect(options[index]) },
        modifier = modifier,
    )
}

// ─── Challenge card ──────────────────────────────────────────────────────────

@Composable
private fun ChallengeCard(
    challenge: Challenge,
    today: LocalDate,
    pending: Boolean,
    historyOpen: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRestart: () -> Unit,
    onCheckIn: (Boolean, LocalDate) -> Unit,
    onSadqaPaidChange: (LocalDate, Boolean) -> Unit,
    onToggleHistory: () -> Unit,
    onOpenPhoto: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Nafs.colors
    val scheme = MaterialTheme.colorScheme
    val tint = c.challenges
    val unit = challenge.frequency.unitName()
    val timeline = remember(challenge, today) { challenge.timeline(today) }
    val total = timeline.periods.size
    val elapsed = challenge.elapsedUnits(today).coerceAtMost(total)
    val percent = challenge.progressPercent(today)
    val active = challenge.status == ChallengeStatus.ACTIVE
    // Streaks shown are derived from the check-ins, so a stored value can never go stale on screen.
    val streak = timeline.currentStreak
    val best = maxOf(challenge.longestStreak, timeline.longestStreak)
    val currentProof = timeline.current?.let { period ->
        challenge.currentCheckIns().firstOrNull { it.completed && it.date in period && !it.photoUrl.isNullOrBlank() }
    }

    NafsCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                NafsEmojiBadge(challenge.emoji, tint, size = 48.dp)
                Column(Modifier.weight(1f).padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(challenge.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (challenge.restartCount > 0) {
                            "Attempt ${challenge.restartCount + 1} · previous activity is retained"
                        } else {
                            "${challenge.durationDays}-day promise"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                ChallengeMenu(challenge.title, pending, onEdit, onDelete)
            }

            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NafsPill(challenge.frequency.displayName(), color = tint)
                if (active && streak > 0) {
                    NafsPill("$streak-$unit streak", color = c.gold, icon = Icons.Rounded.Whatshot)
                }
                when (challenge.status) {
                    ChallengeStatus.COMPLETED -> NafsPill("Complete", color = c.success, icon = Icons.Rounded.CheckCircle)
                    ChallengeStatus.FAILED -> NafsPill("Ended", color = c.warning, icon = Icons.Rounded.Flag)
                    ChallengeStatus.ACTIVE -> {}
                }
            }

            Text(challenge.description, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (timeline.hasStarted) {
                            "${unit.replaceFirstChar(Char::uppercase)} $elapsed of $total"
                        } else {
                            "Starts ${dayLabel(challenge.startDate, today)}"
                        },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text("$percent%", style = MaterialTheme.typography.labelLarge, color = tint)
                }
                NafsProgressBar(percent / 100f, color = tint)
                Row {
                    Text(
                        "Started ${shortDate(challenge.startDate, today)} · ends ${shortDate(timeline.endDate, today)}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                    Text("Best $best", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }

            if (challenge.requiresPhoto || challenge.sadqaAmount != null) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (challenge.requiresPhoto) {
                        NafsPill(
                            if (currentProof != null) "Proof uploaded" else "Photo proof",
                            color = if (currentProof != null) c.success else scheme.onSurfaceVariant,
                            icon = Icons.Rounded.CameraAlt,
                        )
                    }
                    challenge.sadqaAmount?.let { amount ->
                        NafsPill(
                            "${formatMoney(amount, challenge.sadqaCurrency)} sadqa per miss",
                            color = c.deen,
                            icon = Icons.Rounded.Favorite,
                        )
                    }
                }
            }

            when (challenge.status) {
                ChallengeStatus.ACTIVE -> {
                    NafsDivider(inset = 0.dp)
                    CheckInArea(challenge, timeline, pending, currentProof != null, onRestart, onCheckIn, onSadqaPaidChange)
                }
                ChallengeStatus.COMPLETED, ChallengeStatus.FAILED -> {
                    ChallengeOutcomeWell(challenge, timeline, today)
                    NafsSecondaryButton(
                        if (challenge.status == ChallengeStatus.COMPLETED) "Start it again" else "Start a new attempt",
                        onClick = onRestart,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !pending,
                        icon = Icons.Rounded.Autorenew,
                    )
                }
            }

            Column {
                NafsDivider(inset = 0.dp)
                ChallengeHistoryToggle(timeline, historyOpen, onToggleHistory)
                AnimatedVisibility(visible = historyOpen) {
                    Box(Modifier.padding(top = 8.dp)) {
                        ChallengeHistoryPanel(challenge, timeline, pending, onSadqaPaidChange, onOpenPhoto)
                    }
                }
            }
        }
    }
}

/** What can be checked in right now: the current period and, for a daily challenge, an open yesterday. */
@Composable
private fun CheckInArea(
    challenge: Challenge,
    timeline: ChallengeTimeline,
    pending: Boolean,
    proofUploaded: Boolean,
    onRestart: () -> Unit,
    onCheckIn: (Boolean, LocalDate) -> Unit,
    onSadqaPaidChange: (LocalDate, Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val today = timeline.today
    val unit = challenge.frequency.unitName()
    val current = timeline.current
    val grace = timeline.grace
    if (!timeline.hasStarted) {
        NoticeWell(
            icon = Icons.Rounded.Schedule,
            title = "Starts ${dayLabel(challenge.startDate, today)}",
            body = "Check-ins open on the first day.",
        )
    } else {
        if (timeline.needsRestart) RestartNotice(pending, onRestart)
        if (current != null) {
            val isFinal = current.index == timeline.last.index
            if (current.status == ChallengePeriodStatus.DONE) {
                CheckedInNotice(unit, proofUploaded, isFinal)
            } else {
                if (challenge.requiresPhoto) PhotoPrivacyNote()
                if (current.missRecorded) {
                    Text(
                        "Miss recorded ${checkInTarget(today, today, challenge.frequency)}. If you still do it, mark it done " +
                            "before ${windowCloses(current.end, challenge.frequency, today)}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.error,
                    )
                } else if (isFinal) {
                    Text(
                        "Final $unit of this challenge.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                CheckInButtons(
                    doneLabel = when {
                        challenge.requiresPhoto -> "Add proof"
                        challenge.frequency == ChallengeFrequency.DAILY -> "Done today"
                        else -> "Done this $unit"
                    },
                    requiresPhoto = challenge.requiresPhoto,
                    pending = pending,
                    showMissed = !current.missRecorded,
                    onCheckIn = { done -> onCheckIn(done, today) },
                )
                challenge.sadqaAmount?.let { amount ->
                    challenge.currentCheckIns()
                        .firstOrNull { !it.completed && it.date in current }
                        ?.let { miss -> SadqaNotice(amount, challenge.sadqaCurrency, miss, pending, onSadqaPaidChange) }
                }
            }
        }
        grace?.let { yesterday ->
            GraceCheckIn(
                period = yesterday,
                today = today,
                isFinal = yesterday.index == timeline.last.index,
                requiresPhoto = challenge.requiresPhoto,
                pending = pending,
                onCheckIn = { done -> onCheckIn(done, yesterday.start) },
            )
        }
    }
}

/** A daily challenge's yesterday, still open until the end of today (account timezone). */
@Composable
private fun GraceCheckIn(
    period: ChallengePeriod,
    today: LocalDate,
    isFinal: Boolean,
    requiresPhoto: Boolean,
    pending: Boolean,
    onCheckIn: (Boolean) -> Unit,
) {
    val c = Nafs.colors
    val scheme = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.medium, color = scheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                NafsIconBadge(Icons.Rounded.Schedule, c.challenges, size = 36.dp, iconSize = 18.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        if (isFinal) "Yesterday was the last day" else "Yesterday is still open",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        when {
                            period.missRecorded -> "${dayLabel(period.start, today)} · miss recorded. Did it after all? Mark it done until the end of today."
                            else -> "${dayLabel(period.start, today)} · check in until the end of today."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            CheckInButtons(
                doneLabel = if (requiresPhoto) "Add proof for yesterday" else "Done yesterday",
                requiresPhoto = requiresPhoto,
                pending = pending,
                showMissed = !period.missRecorded,
                onCheckIn = onCheckIn,
                height = 48.dp,
            )
        }
    }
}

@Composable
private fun ChallengeMenu(title: String, pending: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Box {
        IconButton(onClick = { menuOpen = true }, enabled = !pending, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "Edit or delete $title", tint = scheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Edit") },
                onClick = {
                    menuOpen = false
                    onEdit()
                },
                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
            )
            DropdownMenuItem(
                text = { Text("Delete", color = scheme.error) },
                onClick = {
                    menuOpen = false
                    onDelete()
                },
                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = scheme.error) },
            )
        }
    }
}

@Composable
private fun CheckInButtons(
    doneLabel: String,
    requiresPhoto: Boolean,
    pending: Boolean,
    showMissed: Boolean,
    onCheckIn: (Boolean) -> Unit,
    height: Dp = 54.dp,
) {
    val c = Nafs.colors
    val scheme = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = { onCheckIn(true) },
            enabled = !pending,
            modifier = Modifier.weight(1.4f).height(height),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = c.success, contentColor = scheme.onPrimary),
            contentPadding = PaddingValues(horizontal = 14.dp),
        ) {
            if (pending) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
            } else {
                Icon(if (requiresPhoto) Icons.Rounded.CameraAlt else Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    doneLabel,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (showMissed) {
            Button(
                onClick = { onCheckIn(false) },
                enabled = !pending,
                modifier = Modifier.weight(1f).height(height),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = scheme.errorContainer, contentColor = scheme.onErrorContainer),
                contentPadding = PaddingValues(horizontal = 14.dp),
            ) {
                Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Missed", style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun RestartNotice(pending: Boolean, onRestart: () -> Unit) {
    val c = Nafs.colors
    Surface(shape = MaterialTheme.shapes.medium, color = c.warningContainer, contentColor = c.onWarningContainer) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Autorenew, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    "The rhythm broke. Start a new attempt — earlier check-ins stay on record.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(
                onClick = onRestart,
                enabled = !pending,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, c.onWarningContainer.copy(alpha = 0.35f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = c.onWarningContainer),
            ) { Text("Start a new attempt", style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
private fun CheckedInNotice(unit: String, proofUploaded: Boolean, isFinal: Boolean) {
    val c = Nafs.colors
    Surface(shape = MaterialTheme.shapes.medium, color = c.successContainer, contentColor = c.onSuccessContainer) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (unit == "day") "Checked in for today" else "Checked in for this $unit",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    when {
                        isFinal -> "Final $unit done. Settle yesterday below to finish the challenge."
                        proofUploaded -> "Proof photo uploaded"
                        else -> "Promise kept. Come back ${if (unit == "day") "tomorrow" else "next $unit"}."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun NoticeWell(icon: ImageVector, title: String, body: String) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.medium, color = scheme.surfaceContainerHigh) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = Nafs.colors.challenges, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PhotoPrivacyNote() {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Rounded.Lock, contentDescription = null, tint = muted, modifier = Modifier.padding(top = 1.dp).size(16.dp))
        Text(
            "A photo from Android's system picker is required. NAFS does not request access to your full gallery.",
            style = MaterialTheme.typography.bodySmall,
            color = muted,
        )
    }
}

@Composable
private fun SadqaNotice(
    amount: Double,
    currency: String,
    miss: ChallengeCheckIn,
    pending: Boolean,
    onSadqaPaidChange: (LocalDate, Boolean) -> Unit,
) {
    val c = Nafs.colors
    val container = if (miss.sadqaPaid) c.successContainer else c.warningContainer
    val content = if (miss.sadqaPaid) c.onSuccessContainer else c.onWarningContainer
    Surface(shape = MaterialTheme.shapes.medium, color = container, contentColor = content) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.Favorite, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                if (miss.sadqaPaid) "Sadqa recorded as paid" else "Sadqa due: ${formatMoney(amount, currency)}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(
                onClick = { onSadqaPaidChange(miss.date, !miss.sadqaPaid) },
                enabled = !pending,
                colors = ButtonDefaults.textButtonColors(contentColor = content),
            ) { Text(if (miss.sadqaPaid) "Undo" else "Mark paid", style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
private fun EmptyChallenges(status: ChallengeStatus?, onCreate: () -> Unit, modifier: Modifier = Modifier) {
    val completedView = status == ChallengeStatus.COMPLETED
    NafsEmptyState(
        icon = Icons.Rounded.Whatshot,
        title = if (completedView) "No completed challenges yet" else "Choose one honest promise",
        message = if (completedView) {
            "Completed challenges will collect here."
        } else {
            "Start small, choose the right cadence, and build evidence through check-ins."
        },
        modifier = modifier,
        tint = Nafs.colors.challenges,
        actionLabel = if (completedView) null else "Start a challenge",
        onAction = if (completedView) null else onCreate,
    )
}

@Composable
private fun ChallengeSkeleton() {
    val block = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = NafsSpace.screen, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(196.dp).clip(MaterialTheme.shapes.extraLarge).background(block))
        Box(Modifier.fillMaxWidth().height(46.dp).clip(CircleShape).background(block))
        repeat(2) { Box(Modifier.fillMaxWidth().height(220.dp).clip(MaterialTheme.shapes.large).background(block)) }
    }
}

// ─── Dialogs ─────────────────────────────────────────────────────────────────

@Composable
private fun CheckInDialog(
    request: ChallengeCheckInRequest,
    today: LocalDate,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onSadqaPaidChange: (Boolean) -> Unit,
) {
    val c = Nafs.colors
    val scheme = MaterialTheme.colorScheme
    val challenge = request.challenge
    val frequency = challenge.frequency
    val target = checkInTarget(request.date, today, frequency)
    val timeline = remember(challenge, today) { challenge.timeline(today) }
    val period = timeline.periodOn(request.date)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            NafsIconBadge(
                if (request.completed) Icons.Rounded.Check else Icons.Rounded.Whatshot,
                if (request.completed) c.success else scheme.error,
            )
        },
        title = { Text(if (request.completed) "Promise kept?" else "Record a missed ${frequency.unitName()}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (request.completed) {
                        "Mark ${challenge.title} done for $target?"
                    } else {
                        buildString {
                            append("This records an honest miss for $target.")
                            if (period != null) {
                                append(" If you still do it, you can mark it done until ${windowCloses(period.end, frequency, today)}.")
                            }
                            if (period != null && period.index == timeline.last.index) {
                                append(" It's the challenge's final ${frequency.unitName()}: if it stays missed, the challenge ends unfinished.")
                            }
                        }
                    },
                )
                if (!request.completed) {
                    challenge.sadqaAmount?.let { amount ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .toggleable(value = request.sadqaPaid, role = Role.Checkbox, onValueChange = onSadqaPaidChange)
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Checkbox(checked = request.sadqaPaid, onCheckedChange = null)
                            Text(
                                "I have paid ${formatMoney(amount, challenge.sadqaCurrency)} sadqa",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (request.completed) {
                    ButtonDefaults.buttonColors(containerColor = c.success, contentColor = scheme.onPrimary)
                } else {
                    ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError)
                },
            ) { Text(if (request.completed) "Mark done" else "Record miss") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ChallengeDeleteDialog(challenge: Challenge, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = scheme.error) },
        title = { Text("Delete ${challenge.title}?") },
        text = { Text("This permanently removes the challenge and every check-in. This cannot be undone.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError),
            ) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ─── Editor sheet ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChallengeEditorSheet(draft: ChallengeEditorDraft, state: ChallengeUiState, actions: ChallengeEditorActions) {
    val saving by rememberUpdatedState(state.isSaving)
    // While a save is in flight the sheet can't be swiped or tapped away, matching the old dialog.
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value -> value != SheetValue.Hidden || !saving },
    )
    ModalBottomSheet(
        onDismissRequest = actions.onClose,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        ChallengeEditorContent(draft, state, actions)
    }
}

/** The editor's body, separate from the sheet so it can be rendered on its own. */
@Composable
internal fun ChallengeEditorContent(draft: ChallengeEditorDraft, state: ChallengeUiState, actions: ChallengeEditorActions) {
    val c = Nafs.colors
    val scheme = MaterialTheme.colorScheme
    val enabled = !state.isSaving
    val isNew = draft.challengeId == null
    val frequencies = ChallengeFrequency.entries
    val sentences = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
    // An edit keeps the challenge's start; a new challenge starts today.
    val startDate = draft.challengeId?.let { id -> state.challenges.firstOrNull { it.id == id }?.startDate } ?: state.today

    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Row(Modifier.gutter(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            NafsEmojiBadge(draft.emoji, c.challenges, size = 52.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (isNew) "Start a challenge" else "Edit challenge", style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (isNew) "One honest promise, at a cadence you can keep." else "Your streak and past check-ins are kept.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }

        Column(Modifier.gutter(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NafsTextField(draft.emoji, actions.onEmojiChange, label = "Emoji", modifier = Modifier.width(88.dp), enabled = enabled)
                NafsTextField(
                    draft.title,
                    actions.onTitleChange,
                    label = "Challenge",
                    modifier = Modifier.weight(1f),
                    supportingText = "What promise are you making?",
                    enabled = enabled,
                    keyboardOptions = sentences,
                )
            }
            NafsTextField(
                draft.description,
                actions.onDescriptionChange,
                label = "Your reason",
                supportingText = "Required · make the reason specific and personal",
                enabled = enabled,
                singleLine = false,
                minLines = 3,
                maxLines = 6,
                keyboardOptions = sentences,
            )
        }

        EditorGroup("Cadence", supporting = "Changing it resets the duration to a sensible default.") {
            NafsSegmented(
                options = frequencies.map { it.displayName() },
                selectedIndex = frequencies.indexOf(draft.frequency),
                onSelect = { index -> if (enabled) actions.onFrequencyChange(frequencies[index]) },
                modifier = Modifier.gutter(),
            )
            NafsTextField(
                draft.durationDays,
                actions.onDurationChange,
                label = "Duration in days",
                modifier = Modifier.gutter(),
                supportingText = durationHint(draft, startDate, state.today),
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }

        NafsCard(Modifier.gutter(), contentPadding = PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                NafsIconBadge(Icons.Rounded.CameraAlt, c.challenges, size = 38.dp, iconSize = 20.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Require photo proof", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Uses Android's system photo picker; no gallery permission.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Switch(checked = draft.requiresPhoto, onCheckedChange = actions.onRequiresPhotoChange, enabled = enabled)
            }
        }

        EditorGroup("Sadqa pledge", supporting = "Optional stakes for a missed period.") {
            NafsTextField(
                draft.sadqaAmount,
                actions.onSadqaAmountChange,
                label = "Amount (optional)",
                modifier = Modifier.gutter(),
                leadingIcon = Icons.Rounded.Favorite,
                supportingText = "Owed for each missed period; mark it paid from the check-in history",
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            if (draft.sadqaAmount.isNotBlank()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = NafsSpace.screen),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChallengeDefinitionValidator.SUPPORTED_CURRENCIES.forEach { currency ->
                        NafsChoiceChip(
                            currency,
                            selected = draft.sadqaCurrency == currency,
                            onClick = { actions.onSadqaCurrencyChange(currency) },
                            enabled = enabled,
                        )
                    }
                }
            }
        }

        state.editorError?.let { message ->
            NafsBanner(message = message, modifier = Modifier.gutter(), kind = NafsBannerKind.ERROR)
        }

        Row(Modifier.gutter(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = actions.onClose, enabled = enabled, modifier = Modifier.weight(1f).height(54.dp)) { Text("Cancel") }
            NafsPrimaryButton(
                text = if (isNew) "Start challenge" else "Save changes",
                onClick = actions.onSave,
                modifier = Modifier.weight(1f),
                loading = state.isSaving,
            )
        }
    }
}

@Composable
private fun EditorGroup(title: String, supporting: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.gutter(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            supporting?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        content()
    }
}

// ─── Formatting ──────────────────────────────────────────────────────────────

private fun Modifier.gutter(): Modifier = padding(horizontal = NafsSpace.screen)

private fun challengeSummary(state: ChallengeUiState): String {
    val active = state.challenges.filter { it.status == ChallengeStatus.ACTIVE }
    return when {
        state.isLoading -> "Loading your challenges…"
        active.isEmpty() -> "Keep one promise at a time"
        else -> "${active.size} active · ${active.count { it.checkedThisPeriod(state.today) }} checked in"
    }
}

/** "7–365 days · ends 18 Nov · 40 daily check-ins": the range, and what the typed duration means from the start date. */
private fun durationHint(draft: ChallengeEditorDraft, startDate: LocalDate, today: LocalDate): String {
    val min = ChallengeDefinitionValidator.minimumDuration(draft.frequency)
    val max = ChallengeDefinitionValidator.maximumDuration(draft.frequency)
    val range = "$min–$max days"
    val days = draft.durationDays.trim().toIntOrNull()?.takeIf { it in min..max } ?: return range
    val count = ChallengeCadence.periodCount(draft.frequency, startDate, days)
    val end = ChallengeCadence.endDate(startDate, days)
    val cadence = draft.frequency.displayName().lowercase()
    return "$range · ends ${shortDate(end, today)} · $count $cadence check-in${if (count == 1) "" else "s"}"
}
