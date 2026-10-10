package com.ahmad.nafs.ui.auth

import com.ahmad.nafs.core.auth.EmailRules
import com.ahmad.nafs.core.auth.PasswordRules
import com.ahmad.nafs.core.auth.SignUpGender

enum class AuthMode {
    SIGN_IN,
    SIGN_UP,
    FORGOT_PASSWORD,
}

data class AuthUiState(
    val mode: AuthMode = AuthMode.SIGN_IN,
    val name: String = "",
    val gender: SignUpGender? = null,
    val email: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val confirmationMessage: String? = null,
    /** An address whose sign-up still needs confirming; the screen then offers to send the email again. */
    val pendingConfirmationEmail: String? = null,
    val isResending: Boolean = false,
    /** Seconds until another confirmation email may be requested; 0 when it can be sent now. */
    val resendCooldownSeconds: Int = 0,
)

object AuthInputValidator {
    fun validate(state: AuthUiState): String? {
        val email = state.email.trim()
        if (email.isEmpty()) return "Enter your email address."
        if (!EmailRules.isValid(email)) return "Enter a valid email address."

        return when (state.mode) {
            // Any non-empty password: accounts created before the 8-character rule must still be able to sign in.
            AuthMode.SIGN_IN -> {
                if (state.password.isEmpty()) "Enter your password." else null
            }
            AuthMode.SIGN_UP -> when {
                state.name.trim().isEmpty() -> "Enter your full name."
                state.gender == null -> "Select your gender."
                !PasswordRules.isValidLength(state.password) -> PasswordRules.LENGTH_MESSAGE
                else -> null
            }
            AuthMode.FORGOT_PASSWORD -> null
        }
    }
}
