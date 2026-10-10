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

    suspend fun retrySession(): AuthActionResult

    suspend fun signInGoogle(): AuthActionResult

    suspend fun signOut(): AuthActionResult
}
