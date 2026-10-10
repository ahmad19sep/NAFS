package com.ahmad.nafs.core.auth

import com.ahmad.nafs.core.network.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthRestException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

sealed interface PasswordUpdateOutcome {
    data object Saved : PasswordUpdateOutcome

    data class Rejected(val message: String) : PasswordUpdateOutcome
}

/** The Supabase calls behind a sign-in link, kept apart from [AuthCallbackController] so its state handling is testable. */
interface AuthCallbackGateway {
    /** Exchanges a PKCE code for a session and returns the id of the user now signed in. */
    suspend fun exchangeCode(code: String): String

    /** Sets a new password for [expectedUserId], who must be the user signed in. */
    suspend fun updatePassword(expectedUserId: String, password: String): PasswordUpdateOutcome
}

class SupabaseAuthCallbackGateway @Inject constructor(
    private val provider: SupabaseClientProvider,
) : AuthCallbackGateway {
    override suspend fun exchangeCode(code: String): String {
        val auth = checkNotNull(provider.client).auth
        auth.awaitInitialization()
        // The SDK exchanges against the locally retained PKCE verifier; raw token fragments are never imported.
        auth.exchangeCodeForSession(code)
        return checkNotNull(auth.currentUserOrNull()).id
    }

    override suspend fun updatePassword(expectedUserId: String, password: String): PasswordUpdateOutcome {
        val auth = checkNotNull(provider.client).auth
        check(auth.currentUserOrNull()?.id == expectedUserId)
        try {
            auth.updateUser { this.password = password }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: AuthRestException) {
            return PasswordUpdateOutcome.Rejected(AuthErrorMessages.passwordRejection(error))
        }
        check(auth.currentUserOrNull()?.id == expectedUserId)
        return PasswordUpdateOutcome.Saved
    }
}
