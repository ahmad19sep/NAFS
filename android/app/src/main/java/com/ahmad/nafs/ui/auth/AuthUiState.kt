package com.ahmad.nafs.ui.auth

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
)

object AuthInputValidator {
    private val emailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun validate(state: AuthUiState): String? {
        val email = state.email.trim()
        if (email.isEmpty()) return "Enter your email address."
        if (!emailPattern.matches(email)) return "Enter a valid email address."

        return when (state.mode) {
            AuthMode.SIGN_IN -> {
                if (state.password.isEmpty()) "Enter your password." else null
            }
            AuthMode.SIGN_UP -> when {
                state.name.trim().isEmpty() -> "Enter your full name."
                state.gender == null -> "Select your gender."
                state.password.length < MINIMUM_PASSWORD_LENGTH ->
                    "Password must be at least $MINIMUM_PASSWORD_LENGTH characters."
                else -> null
            }
            AuthMode.FORGOT_PASSWORD -> null
        }
    }

    private const val MINIMUM_PASSWORD_LENGTH = 6
}
