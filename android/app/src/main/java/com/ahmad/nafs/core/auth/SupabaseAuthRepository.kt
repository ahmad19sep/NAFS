package com.ahmad.nafs.core.auth

import com.ahmad.nafs.core.network.SupabaseClientProvider
import com.ahmad.nafs.BuildConfig
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.exception.AuthWeakPasswordException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Singleton
class SupabaseAuthRepository @Inject constructor(
    private val clientProvider: SupabaseClientProvider,
) : AuthRepository {
    override val isConfigured: Boolean = clientProvider.isConfigured

    override val sessionState: Flow<SessionState> = clientProvider.client
        ?.auth
        ?.sessionStatus
        ?.map(::mapSessionStatus)
        ?: flowOf(SessionState.ConfigurationMissing)

    override suspend fun signIn(email: String, password: String): AuthActionResult =
        authAction {
            requireClient().auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
        }

    override suspend fun signUp(
        name: String,
        gender: SignUpGender,
        email: String,
        password: String,
    ): AuthActionResult = authAction {
        requireClient().auth.signUpWith(Email) {
            this.email = email
            this.password = password
            data = buildJsonObject {
                put("name", name)
                put("gender", gender.wireValue)
            }
        }
    }

    override suspend fun sendPasswordReset(email: String): AuthActionResult = authAction {
        requireClient().auth.resetPasswordForEmail(email, redirectUrl = "${BuildConfig.APPLICATION_ID}://auth/reset")
    }

    override suspend fun resendSignUpConfirmation(email: String): AuthActionResult = authAction {
        // Like sign-up, the new link comes back to this app (PKCE), where AuthCallbackController finishes it.
        requireClient().auth.resendEmail(OtpType.Email.SIGNUP, email)
    }

    override suspend fun retrySession(): AuthActionResult = authAction {
        requireClient().auth.refreshCurrentSession()
    }

    override suspend fun signInGoogle(): AuthActionResult = authAction {
        check(BuildConfig.GOOGLE_AUTH_ENABLED)
        requireClient().auth.signInWith(Google)
    }

    override suspend fun signOut(): AuthActionResult = authAction {
        requireClient().auth.signOut()
    }

    override suspend fun clearLocalSession(): AuthActionResult = try {
        requireClient().auth.clearSession()
        AuthActionResult.Success
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        AuthActionResult.Failure("NAFS couldn't sign this phone out.")
    }

    private fun requireClient() = checkNotNull(clientProvider.client) {
        "Supabase is not configured."
    }

    private suspend fun authAction(block: suspend () -> Unit): AuthActionResult = try {
        block()
        AuthActionResult.Success
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: AuthRestException) {
        AuthActionResult.Failure(error.toUserMessage(), error.toFailureReason())
    } catch (_: Exception) {
        AuthActionResult.Failure(AuthErrorMessages.OFFLINE, AuthFailureReason.NETWORK)
    }

    private fun mapSessionStatus(status: SessionStatus): SessionState = when (status) {
        SessionStatus.Initializing -> SessionState.Initializing
        is SessionStatus.NotAuthenticated -> SessionState.SignedOut
        is SessionStatus.Authenticated -> status.session.user?.let { user ->
            SessionState.SignedIn(
                userId = user.id,
                email = user.email,
            )
        } ?: SessionState.RefreshFailed
        is SessionStatus.RefreshFailure -> SessionState.RefreshFailed
    }

    private fun AuthRestException.toFailureReason(): AuthFailureReason = when {
        errorCode == AuthErrorCode.EmailNotConfirmed -> AuthFailureReason.EMAIL_NOT_CONFIRMED
        AuthErrorMessages.isRateLimit(this) -> AuthFailureReason.RATE_LIMITED
        else -> AuthFailureReason.OTHER
    }

    private fun AuthRestException.toUserMessage(): String = if (this is AuthWeakPasswordException) {
        AuthErrorMessages.weakPassword(reasons)
    } else when (errorCode) {
        AuthErrorCode.InvalidCredentials -> "Email or password is incorrect."
        AuthErrorCode.EmailNotConfirmed -> "Confirm your email before signing in. Open the link we emailed you, then try again."
        AuthErrorCode.EmailAddressInvalid -> "Enter a valid email address."
        AuthErrorCode.WeakPassword -> AuthErrorMessages.weakPassword(emptyList())
        AuthErrorCode.UserAlreadyExists, AuthErrorCode.EmailExists -> "An account with this email already exists. Sign in instead."
        AuthErrorCode.SignupDisabled -> "New account registration is currently unavailable."
        AuthErrorCode.UserBanned -> "This account is currently unavailable."
        AuthErrorCode.OverEmailSendRateLimit -> AuthErrorMessages.TOO_MANY_EMAILS
        AuthErrorCode.OverRequestRateLimit -> AuthErrorMessages.TOO_MANY_ATTEMPTS
        else -> "Authentication failed. Try again."
    }
}
