package com.ahmad.nafs.core.auth

import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val isConfigured: Boolean
    val sessionState: Flow<SessionState>

    suspend fun signIn(email: String, password: String): AuthActionResult

    suspend fun signUp(
        name: String,
        gender: SignUpGender,
        email: String,
        password: String,
    ): AuthActionResult

    suspend fun sendPasswordReset(email: String): AuthActionResult

    /** Sends the sign-up confirmation email again, for an account that can't sign in until it is confirmed. */
    suspend fun resendSignUpConfirmation(email: String): AuthActionResult

    suspend fun retrySession(): AuthActionResult

    suspend fun signInGoogle(): AuthActionResult

    suspend fun signOut(): AuthActionResult

    /**
     * Forgets the session on this device without asking the server, for when [signOut] can't reach it but the
     * session must go anyway, such as right after the account was deleted.
     */
    suspend fun clearLocalSession(): AuthActionResult
}
