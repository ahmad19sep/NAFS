package com.ahmad.nafs.ui

import com.ahmad.nafs.core.profile.UserProfile

sealed interface RootAppState {
    data object ConfigurationMissing : RootAppState

    data object RestoringSession : RootAppState

    data object SignedOut : RootAppState

    data object SessionRefreshFailed : RootAppState

    data object LoadingProfile : RootAppState

    data class ProfileFailure(val message: String) : RootAppState

    data class NeedsOnboarding(val profile: UserProfile) : RootAppState

    data class Ready(val profile: UserProfile) : RootAppState
}
