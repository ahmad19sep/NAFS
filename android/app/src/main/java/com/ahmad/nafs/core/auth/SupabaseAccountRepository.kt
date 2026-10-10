package com.ahmad.nafs.core.auth

import com.ahmad.nafs.core.network.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Singleton
class SupabaseAccountRepository @Inject constructor(
    private val clientProvider: SupabaseClientProvider,
) : AccountRepository {
    override val accountEmail: Flow<AccountEmail> = clientProvider.client
        ?.auth
        ?.sessionStatus
        ?.map { status ->
            val user = (status as? SessionStatus.Authenticated)?.session?.user
            AccountEmail(
                userId = user?.id,
                current = user?.email?.takeIf { it.isNotBlank() },
                pending = user?.newEmail?.takeIf { it.isNotBlank() },
            )
        }
        ?.distinctUntilChanged()
        ?: flowOf(AccountEmail())

    override suspend fun refresh() {
        val auth = clientProvider.client?.auth ?: return
        if (auth.currentSessionOrNull() == null) return
        try {
            // Updates the stored session's user, which [accountEmail] follows.
            auth.retrieveUserForCurrentSession(updateSession = true)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // Offline or not reachable: the session's own copy stays on screen.
        }
    }

    override suspend fun changePassword(userId: String, currentPassword: String, newPassword: String): AccountChangeResult {
        AccountRules.passwordChangeProblem(currentPassword, newPassword, newPassword)?.let {
            return AccountChangeResult.Failed(it.message, it.field)
        }
        val auth = clientProvider.client?.auth ?: return AccountChangeResult.Failed(NOT_CONFIGURED)
        val user = auth.currentUserOrNull()
        val email = user?.email
        if (user?.id != userId || email.isNullOrBlank()) {
            return AccountChangeResult.Failed("Password change works for accounts that sign in with email. Sign in again and retry.")
        }

        // Step 1: prove it's still you through the normal sign-in path. Supabase replaces the session with a fresh
        // one for the same account, which also satisfies its "secure password change" recency check.
        try {
            auth.signInWith(Email) {
                this.email = email
                password = currentPassword
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: AuthRestException) {
            return when {
                error.errorCode == AuthErrorCode.InvalidCredentials ->
                    AccountChangeResult.Failed("That isn't your current password.", PasswordField.CURRENT)
                AuthErrorMessages.isRateLimit(error) -> AccountChangeResult.Failed(AuthErrorMessages.TOO_MANY_ATTEMPTS)
                else -> AccountChangeResult.Failed("We couldn't confirm your current password. Try again.")
            }
        } catch (_: Exception) {
            return AccountChangeResult.Failed("$OFFLINE Your password is unchanged.")
        }
        if (auth.currentUserOrNull()?.id != userId) {
            return AccountChangeResult.Failed("Sign in again, then change your password.")
        }

        // Step 2: the change itself. The current password goes along for projects that require it.
        try {
            auth.updateUser {
                password = newPassword
                this.currentPassword = currentPassword
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: AuthRestException) {
            return AccountChangeResult.Failed(AuthErrorMessages.passwordRejection(error), PasswordField.NEW)
        } catch (_: Exception) {
            // The request may or may not have reached Supabase, so don't claim either outcome.
            return AccountChangeResult.Failed(
                "NAFS couldn't confirm the change. Check your connection, then try again. If your new password already works, it went through.",
            )
        }
        return AccountChangeResult.Done("Password changed. Other devices may ask you to sign in again.")
    }

    override suspend fun changeEmail(userId: String, newEmail: String): AccountChangeResult {
        val auth = clientProvider.client?.auth ?: return AccountChangeResult.Failed(NOT_CONFIGURED)
        val user = auth.currentUserOrNull()
        if (user?.id != userId) return AccountChangeResult.Failed("Sign in again, then change your email.")
        val target = EmailRules.normalize(newEmail)
        AccountRules.emailChangeProblem(target, user.email)?.let { return AccountChangeResult.Failed(it) }

        val updated = try {
            // The confirmation links come back to this app (PKCE), where AuthCallbackController finishes them.
            auth.updateUser { this.email = target }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: AuthRestException) {
            return AccountChangeResult.Failed(AuthErrorMessages.emailChangeRejection(error))
        } catch (_: Exception) {
            return AccountChangeResult.Failed("NAFS couldn't confirm the request. Check your connection and try again.")
        }
        return if (updated.email.equals(target, ignoreCase = true)) {
            // Projects without email confirmation apply the change at once.
            AccountChangeResult.Done("Your sign-in email is now $target.", applied = true)
        } else {
            AccountChangeResult.Done("Check $target for a confirmation link.", applied = false)
        }
    }

    private companion object {
        const val NOT_CONFIGURED = "NAFS isn't connected to its server in this build."
        const val OFFLINE = "Unable to reach NAFS. Check your connection and try again."
    }
}
