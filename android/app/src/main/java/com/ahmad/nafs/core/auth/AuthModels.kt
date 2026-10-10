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

    data class Failure(
        val message: String,
        val reason: AuthFailureReason = AuthFailureReason.OTHER,
    ) : AuthActionResult
}

/** Why an auth action failed, for the cases where the screen offers a specific next step. */
enum class AuthFailureReason {
    /** Sign-in was refused because the sign-up email was never confirmed. */
    EMAIL_NOT_CONFIRMED,

    /** Supabase asked us to slow down; retrying straight away will fail again. */
    RATE_LIMITED,

    /** The request never got a reply. */
    NETWORK,

    OTHER,
}

enum class SignUpGender(val wireValue: String) {
    MALE("male"),
    FEMALE("female"),
}
