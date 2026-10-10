package com.ahmad.nafs.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmad.nafs.core.auth.AuthActionResult
import com.ahmad.nafs.core.auth.AuthFailureReason
import com.ahmad.nafs.core.auth.AuthRepository
import com.ahmad.nafs.core.auth.SignUpGender
import com.ahmad.nafs.core.auth.SignedOutNotice
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val signedOutNotice: SignedOutNotice,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()
    private var cooldown: Job? = null

    init {
        // For example "Your account was deleted": the screen that signed out is gone, so the message is shown here.
        viewModelScope.launch {
            signedOutNotice.message.filterNotNull().collect { text ->
                signedOutNotice.clear()
                _uiState.update {
                    it.copy(
                        mode = AuthMode.SIGN_IN,
                        password = "",
                        passwordVisible = false,
                        errorMessage = null,
                        confirmationMessage = text,
                        pendingConfirmationEmail = null,
                    )
                }
            }
        }
    }

    fun setMode(mode: AuthMode) {
        _uiState.update {
            it.copy(
                mode = mode,
                password = "",
                passwordVisible = false,
                errorMessage = null,
                confirmationMessage = null,
                pendingConfirmationEmail = null,
            )
        }
    }

    fun setName(value: String) = updateInput { copy(name = value) }

    fun setGender(value: SignUpGender) = updateInput { copy(gender = value) }

    fun setEmail(value: String) = updateInput {
        // A resend offer belongs to the address that was refused, not to whatever is typed next.
        copy(email = value, pendingConfirmationEmail = pendingConfirmationEmail?.takeIf { it == value.trim().lowercase() })
    }

    fun setPassword(value: String) = updateInput { copy(password = value) }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    fun signInGoogle() {
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            val result = authRepository.signInGoogle()
            _uiState.update { it.copy(isSubmitting = false, errorMessage = (result as? AuthActionResult.Failure)?.message) }
        }
    }

    fun submit() {
        val current = _uiState.value
        if (current.isSubmitting) return

        val validationMessage = AuthInputValidator.validate(current)
        if (validationMessage != null) {
            _uiState.update { it.copy(errorMessage = validationMessage) }
            return
        }

        _uiState.update {
            it.copy(
                isSubmitting = true,
                errorMessage = null,
                confirmationMessage = null,
            )
        }
        viewModelScope.launch {
            val email = current.email.trim().lowercase()
            val result = when (current.mode) {
                AuthMode.SIGN_IN -> authRepository.signIn(email, current.password)
                AuthMode.SIGN_UP -> authRepository.signUp(
                    name = current.name.trim(),
                    gender = checkNotNull(current.gender),
                    email = email,
                    password = current.password,
                )
                AuthMode.FORGOT_PASSWORD -> authRepository.sendPasswordReset(email)
            }

            _uiState.update { state ->
                when (result) {
                    AuthActionResult.Success -> state.afterSuccess(email)
                    is AuthActionResult.Failure -> state.copy(
                        isSubmitting = false,
                        errorMessage = result.message,
                        pendingConfirmationEmail = email.takeIf {
                            current.mode == AuthMode.SIGN_IN && result.reason == AuthFailureReason.EMAIL_NOT_CONFIRMED
                        },
                    )
                }
            }
            // Sign-up has just sent a confirmation email; Supabase refuses another one for about a minute.
            if (result == AuthActionResult.Success && current.mode == AuthMode.SIGN_UP) startCooldown()
        }
    }

    /** Sends the sign-up confirmation email again, at most once a minute. */
    fun resendConfirmation() {
        val current = _uiState.value
        val email = current.pendingConfirmationEmail ?: return
        if (current.isResending || current.resendCooldownSeconds > 0) return
        _uiState.update { it.copy(isResending = true, errorMessage = null, confirmationMessage = null) }
        viewModelScope.launch {
            val result = authRepository.resendSignUpConfirmation(email)
            _uiState.update {
                when (result) {
                    AuthActionResult.Success -> it.copy(
                        isResending = false,
                        confirmationMessage = "We sent a new confirmation link to $email. Open it, then sign in.",
                    )
                    is AuthActionResult.Failure -> it.copy(isResending = false, errorMessage = result.message)
                }
            }
            if (result == AuthActionResult.Success || (result as? AuthActionResult.Failure)?.reason == AuthFailureReason.RATE_LIMITED) {
                startCooldown()
            }
        }
    }

    private fun startCooldown() {
        cooldown?.cancel()
        cooldown = viewModelScope.launch {
            for (left in RESEND_COOLDOWN_SECONDS downTo 1) {
                _uiState.update { it.copy(resendCooldownSeconds = left) }
                delay(1_000)
            }
            _uiState.update { it.copy(resendCooldownSeconds = 0) }
        }
    }

    private fun AuthUiState.afterSuccess(email: String): AuthUiState = when (mode) {
        // The password isn't needed once signed in, so it doesn't stay in memory.
        AuthMode.SIGN_IN -> copy(isSubmitting = false, password = "", passwordVisible = false, pendingConfirmationEmail = null)
        AuthMode.SIGN_UP -> copy(
            isSubmitting = false,
            password = "",
            confirmationMessage = "Account created. If confirmation is required, check $email.",
            pendingConfirmationEmail = email,
        )
        AuthMode.FORGOT_PASSWORD -> copy(
            mode = AuthMode.SIGN_IN,
            isSubmitting = false,
            password = "",
            confirmationMessage = "If an account exists for $email, a reset link has been sent.",
        )
    }

    private inline fun updateInput(transform: AuthUiState.() -> AuthUiState) {
        _uiState.update {
            it.transform().copy(errorMessage = null, confirmationMessage = null)
        }
    }

    companion object {
        const val RESEND_COOLDOWN_SECONDS = 60
    }
}
