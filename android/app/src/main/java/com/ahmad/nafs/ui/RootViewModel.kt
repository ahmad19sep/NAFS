package com.ahmad.nafs.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmad.nafs.core.preferences.AppPreferences
import com.ahmad.nafs.core.preferences.ThemeMode
import com.ahmad.nafs.core.auth.AuthRepository
import com.ahmad.nafs.core.auth.SessionState
import com.ahmad.nafs.core.challenges.ChallengeRepository
import com.ahmad.nafs.core.habits.HabitRepository
import com.ahmad.nafs.core.goals.GoalRepository
import com.ahmad.nafs.core.health.HealthRepository
import com.ahmad.nafs.core.deen.DeenRepository
import com.ahmad.nafs.core.checkin.CheckInRepository
import com.ahmad.nafs.core.coach.CoachRepository
import com.ahmad.nafs.core.reports.ReportRepository
import com.ahmad.nafs.core.letters.LetterRepository
import com.ahmad.nafs.core.plans.RecoveryRepository
import com.ahmad.nafs.core.records.RecordSyncScheduler
import com.ahmad.nafs.core.records.PrivateRecordStore
import com.ahmad.nafs.core.profile.ProfileActionResult
import com.ahmad.nafs.core.profile.ProfileRepository
import com.ahmad.nafs.core.profile.ProfileState
import com.ahmad.nafs.core.reminders.ActivityReminderScheduler
import com.ahmad.nafs.core.reminders.TaskReminderScheduler
import com.ahmad.nafs.core.tasks.TaskRepository
import com.ahmad.nafs.core.tasks.TaskLoadResult
import com.ahmad.nafs.core.time.AccountDate
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class RootViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val taskRepository: TaskRepository,
    private val habitRepository: HabitRepository,
    private val goalRepository: GoalRepository,
    private val challengeRepository: ChallengeRepository,
    private val healthRepository: HealthRepository,
    private val deenRepository: DeenRepository,
    private val checkInRepository: CheckInRepository,
    private val coachRepository: CoachRepository,
    private val reportRepository: ReportRepository,
    private val letterRepository: LetterRepository,
    private val recoveryRepository: RecoveryRepository,
    private val privateRecordStore: PrivateRecordStore,
    private val recordSync: RecordSyncScheduler,
    private val reminderScheduler: TaskReminderScheduler,
    private val activityReminders: ActivityReminderScheduler,
) : ViewModel() {
    private val _profileMessage = MutableStateFlow<String?>(null)
    val profileMessage: StateFlow<String?> = _profileMessage.asStateFlow()

    val themeMode: StateFlow<ThemeMode> = preferences.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = ThemeMode.SYSTEM,
    )

    private val sessionState: StateFlow<SessionState> = authRepository.sessionState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = if (authRepository.isConfigured) {
            SessionState.Initializing
        } else {
            SessionState.ConfigurationMissing
        },
    )

    val appState: StateFlow<RootAppState> = combine(
        sessionState,
        profileRepository.state,
        ::toRootState,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = if (authRepository.isConfigured) {
            RootAppState.RestoringSession
        } else {
            RootAppState.ConfigurationMissing
        },
    )

    init {
        viewModelScope.launch {
            var loadedUserId: String? = null
            sessionState.collectLatest { session ->
                when (session) {
                    is SessionState.SignedIn -> {
                        if (loadedUserId != session.userId) {
                            loadedUserId?.let {
                                activityReminders.cancel(it)
                                reminderScheduler.cancelAll(it)
                                recordSync.cancel(it)
                                taskRepository.clear(it)
                                habitRepository.clear(it)
                                goalRepository.clear(it)
                                challengeRepository.clear(it)
                                healthRepository.clear(it)
                                deenRepository.clear(it)
                                checkInRepository.clear(it)
                                privateRecordStore.clear(it)
                                coachRepository.clear(it)
                                reportRepository.clear(it)
                                letterRepository.clear(it)
                                recoveryRepository.clear(it)
                            }
                            loadedUserId = session.userId
                            profileRepository.refresh(session.userId, session.email)
                        }
                    }
                    SessionState.SignedOut,
                    SessionState.ConfigurationMissing,
                    -> {
                        _profileMessage.value = null
                        loadedUserId?.let {
                            activityReminders.cancel(it)
                            reminderScheduler.cancelAll(it)
                            recordSync.cancel(it)
                            taskRepository.clear(it)
                            habitRepository.clear(it)
                            goalRepository.clear(it)
                            challengeRepository.clear(it)
                            healthRepository.clear(it)
                            deenRepository.clear(it)
                            checkInRepository.clear(it)
                            coachRepository.clear(it)
                            reportRepository.clear(it)
                            letterRepository.clear(it)
                            recoveryRepository.clear(it)
                            privateRecordStore.clear(it)
                        }
                        profileRepository.clear(loadedUserId)
                        loadedUserId = null
                    }
                    SessionState.Initializing,
                    SessionState.RefreshFailed,
                    -> Unit
                }
            }
        }
        viewModelScope.launch {
            var reconciledContext: Pair<String, String>? = null
            combine(sessionState, profileRepository.state) { session, profile -> session to profile }
                .collectLatest { (session, profileState) ->
                    val signedIn = session as? SessionState.SignedIn
                    val profile = (profileState as? ProfileState.Loaded)?.profile
                    if (signedIn == null || profile == null || profile.userId != signedIn.userId) {
                        reconciledContext = null
                        return@collectLatest
                    }
                    activityReminders.schedule(profile.userId, profile.timezone)
                    recordSync.reconcile(profile.userId)
                    val context = profile.userId to profile.timezone
                    if (reconciledContext == context) return@collectLatest
                    val today = runCatching { AccountDate.today(profile.timezone) }.getOrNull()
                        ?: return@collectLatest
                    val result = taskRepository.load(profile.userId, today)
                    if (result is TaskLoadResult.Success) {
                        reminderScheduler.reconcile(profile.userId, result.tasks, profile.timezone)
                        reconciledContext = context
                    }
                }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun retrySession() {
        viewModelScope.launch { authRepository.retrySession() }
    }

    fun retryProfile() {
        val session = sessionState.value as? SessionState.SignedIn ?: return
        viewModelScope.launch { profileRepository.refresh(session.userId, session.email) }
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    fun useDeviceTimezone() {
        val session = sessionState.value as? SessionState.SignedIn ?: return
        val timezone = ZoneId.systemDefault().id
        viewModelScope.launch {
            _profileMessage.value = when (val result = profileRepository.updateTimezone(session.userId, timezone)) {
                ProfileActionResult.Success -> "Daily schedules now follow $timezone."
                is ProfileActionResult.Failure -> result.message
            }
        }
    }

    fun dismissProfileMessage() {
        _profileMessage.value = null
    }

    private fun toRootState(
        session: SessionState,
        profile: ProfileState,
    ): RootAppState = when (session) {
        SessionState.ConfigurationMissing -> RootAppState.ConfigurationMissing
        SessionState.Initializing -> RootAppState.RestoringSession
        SessionState.SignedOut -> RootAppState.SignedOut
        SessionState.RefreshFailed -> RootAppState.SessionRefreshFailed
        is SessionState.SignedIn -> when (profile) {
            ProfileState.Idle,
            ProfileState.Loading,
            -> RootAppState.LoadingProfile
            is ProfileState.Failure -> RootAppState.ProfileFailure(profile.message)
            is ProfileState.Loaded -> when {
                profile.profile.userId != session.userId -> RootAppState.LoadingProfile
                !profile.profile.onboardingComplete -> RootAppState.NeedsOnboarding(profile.profile)
                else -> RootAppState.Ready(profile.profile)
            }
        }
    }
}

