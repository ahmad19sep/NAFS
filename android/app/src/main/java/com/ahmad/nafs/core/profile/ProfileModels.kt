package com.ahmad.nafs.core.profile

data class UserProfile(
    val userId: String,
    val name: String,
    val email: String,
    val timezone: String,
    val avatarUrl: String?,
    val onboardingComplete: Boolean,
    val isFromCache: Boolean = false,
    val deenEnabled: Boolean = false,
)

sealed interface ProfileState {
    data object Idle : ProfileState

    data object Loading : ProfileState

    data class Loaded(val profile: UserProfile) : ProfileState

    data class Failure(val message: String) : ProfileState
}

sealed interface ProfileActionResult {
    data object Success : ProfileActionResult

    data class Failure(val message: String) : ProfileActionResult
}
