package com.ahmad.nafs.core.auth

sealed interface SessionState {
    data object ConfigurationMissing : SessionState

    data object Initializing : SessionState

    data object SignedOut : SessionState

    data class SignedIn(
        val userId: String,
        val email: String?,
    ) : SessionState

    data object RefreshFailed : SessionState
}

sealed interface AuthActionResult {
    data object Success : AuthActionResult

    data class Failure(val message: String) : AuthActionResult
}

enum class SignUpGender(val wireValue: String) {
    MALE("male"),
    FEMALE("female"),
}
