package com.ahmad.nafs.core.auth

import android.net.Uri
import com.ahmad.nafs.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class AuthCallbackState(
    val processing: Boolean = false,
    val resetUserId: String? = null,
    val error: String? = null,
    val message: String? = null,
    /** The link worked but one more step is needed, such as the second link of a secure email change. */
    val followUp: String? = null,
)

/**
 * Finishes `<applicationId>://auth` links: sign-up and email-change confirmations, and password resets.
 *
 * The code exchange and the password update run on this singleton's own scope, never a screen's, so an activity
 * recreated mid-request (rotation, dark mode) neither cancels them nor leaves the dialog waiting forever: every run
 * ends by publishing an outcome, even when it is cancelled.
 */
@Singleton
class AuthCallbackController internal constructor(
    private val gateway: AuthCallbackGateway,
    private val scope: CoroutineScope,
) {
    @Inject
    constructor(gateway: AuthCallbackGateway) : this(gateway, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))

    private val _state = MutableStateFlow(AuthCallbackState())
    val state: StateFlow<AuthCallbackState> = _state.asStateFlow()

    private var work: Job? = null

    /** The last code exchanged successfully; the same link handed over again must not replace that result. */
    private var exchangedCode: String? = null

    fun handle(uri: Uri) {
        handle(AuthCallbackLinks.parse(uri.scheme, uri.host, uri.path, uri::getQueryParameter, uri.fragment, BuildConfig.APPLICATION_ID))
    }

    internal fun handle(link: AuthCallbackLink?) {
        if (link == null || work?.isActive == true) return
        when (link) {
            AuthCallbackLink.Invalid -> _state.value = AuthCallbackState(error = INVALID_LINK)
            is AuthCallbackLink.Rejected -> _state.value = AuthCallbackState(error = if (link.expired) EXPIRED_LINK else INVALID_LINK)
            AuthCallbackLink.EmailChangeHalfConfirmed -> _state.value = AuthCallbackState(followUp = EMAIL_CHANGE_HALF_DONE)
            is AuthCallbackLink.Code -> exchange(link)
        }
    }

    private fun exchange(link: AuthCallbackLink.Code) {
        if (link.code == exchangedCode) return
        _state.value = AuthCallbackState(processing = true)
        work = scope.launch {
            var outcome = AuthCallbackState(error = UNVERIFIED_LINK)
            try {
                val userId = withTimeoutOrNull(REQUEST_TIMEOUT_MS) { gateway.exchangeCode(link.code) }
                if (userId != null) {
                    exchangedCode = link.code
                    outcome = if (link.reset) AuthCallbackState(resetUserId = userId) else AuthCallbackState(message = SIGNED_IN)
                }
            } catch (cancellation: CancellationException) {
                outcome = AuthCallbackState(error = INTERRUPTED)
                throw cancellation
            } catch (_: Exception) {
                // The outcome already says the link couldn't be verified; nothing from it is logged.
            } finally {
                _state.value = outcome
            }
        }
    }

    fun updatePassword(password: String, confirmation: String) {
        val current = _state.value
        val expected = current.resetUserId ?: return
        if (current.processing || work?.isActive == true) return
        PasswordRules.newPasswordProblem(password, confirmation)?.let { problem ->
            _state.value = current.copy(error = problem)
            return
        }
        _state.value = current.copy(processing = true, error = null)
        work = scope.launch {
            var outcome = AuthCallbackState(resetUserId = expected, error = UPDATE_FAILED)
            try {
                outcome = when (val result = withTimeoutOrNull(REQUEST_TIMEOUT_MS) { gateway.updatePassword(expected, password) }) {
                    PasswordUpdateOutcome.Saved -> AuthCallbackState(message = PASSWORD_UPDATED)
                    is PasswordUpdateOutcome.Rejected -> AuthCallbackState(resetUserId = expected, error = result.message)
                    null -> AuthCallbackState(resetUserId = expected, error = UPDATE_UNCONFIRMED)
                }
            } catch (cancellation: CancellationException) {
                outcome = AuthCallbackState(resetUserId = expected, error = UPDATE_UNCONFIRMED)
                throw cancellation
            } catch (_: Exception) {
                // Keep the reset form open with UPDATE_FAILED so the user can retry.
            } finally {
                _state.value = outcome
            }
        }
    }

    fun dismiss() {
        if (!_state.value.processing) _state.value = AuthCallbackState()
    }

    internal companion object {
        const val REQUEST_TIMEOUT_MS = 30_000L
        const val INVALID_LINK = "This sign-in link is invalid or expired. Request a new link from this device."
        const val EXPIRED_LINK = "This link has expired or was already used. Request a new one from this device."
        const val UNVERIFIED_LINK = "This sign-in link could not be verified. Open the newest link on the device that requested it."
        const val INTERRUPTED = "Checking the link was interrupted. Open the link again."
        const val SIGNED_IN = "Your link is confirmed and you're signed in."
        const val EMAIL_CHANGE_HALF_DONE =
            "One confirmation done. Open the link we sent to your other email address to finish changing it."
        const val UPDATE_FAILED = "Password could not be updated. Retry, or request a fresh reset link."
        const val UPDATE_UNCONFIRMED =
            "NAFS couldn't confirm the update. Try signing in with your new password, or request a fresh reset link."
        const val PASSWORD_UPDATED = "Password updated. Use it the next time you sign in."
    }
}
