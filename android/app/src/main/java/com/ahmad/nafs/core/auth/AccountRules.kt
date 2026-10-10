package com.ahmad.nafs.core.auth

/**
 * The one rule for choosing a password: sign-up, the reset link and changing it while signed in.
 * Signing in accepts any non-empty password, because older accounts may predate this rule.
 */
object PasswordRules {
    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 128
    const val HINT = "8–128 characters"
    const val LENGTH_MESSAGE = "Use 8–128 characters for your password."
    const val MISMATCH_MESSAGE = "The two passwords don't match."

    fun isValidLength(password: String): Boolean = password.length in MIN_LENGTH..MAX_LENGTH

    /** What is wrong with a new password and its confirmation, or null when both are fine. */
    fun newPasswordProblem(password: String, confirmation: String): String? = when {
        !isValidLength(password) -> LENGTH_MESSAGE
        password != confirmation -> MISMATCH_MESSAGE
        else -> null
    }
}

object EmailRules {
    private val pattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    private const val MAX_LENGTH = 254

    fun normalize(email: String): String = email.trim().lowercase()

    fun isValid(email: String): Boolean = email.trim().let { it.length <= MAX_LENGTH && pattern.matches(it) }
}

/** The field a change-password problem belongs to, so the sheet can mark it. */
enum class PasswordField { CURRENT, NEW, CONFIRM }

data class AccountProblem(val message: String, val field: PasswordField? = null)

/** Checks for the account sheets, run before anything is sent to Supabase. */
object AccountRules {
    const val SAME_PASSWORD = "Choose a new password that's different from your current one."

    fun passwordChangeProblem(current: String, new: String, confirmation: String): AccountProblem? = when {
        current.isEmpty() -> AccountProblem("Enter your current password.", PasswordField.CURRENT)
        !PasswordRules.isValidLength(new) -> AccountProblem(PasswordRules.LENGTH_MESSAGE, PasswordField.NEW)
        new == current -> AccountProblem(SAME_PASSWORD, PasswordField.NEW)
        new != confirmation -> AccountProblem("The new passwords don't match.", PasswordField.CONFIRM)
        else -> null
    }

    fun emailChangeProblem(newEmail: String, currentEmail: String?): String? = when {
        newEmail.isBlank() -> "Enter your new email address."
        !EmailRules.isValid(newEmail) -> "Enter a valid email address."
        currentEmail != null && EmailRules.normalize(newEmail) == EmailRules.normalize(currentEmail) ->
            "That's already your sign-in email."
        else -> null
    }
}
