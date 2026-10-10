package com.ahmad.nafs.core.auth

import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.exception.AuthWeakPasswordException

/** Plain-language messages for Supabase Auth errors. Server wording is never shown as is. */
internal object AuthErrorMessages {
    const val OFFLINE = "Unable to reach NAFS. Check your connection and try again."
    const val TOO_MANY_ATTEMPTS = "Too many attempts. Wait a moment and try again."
    const val TOO_MANY_EMAILS = "Too many emails were sent. Wait a few minutes and try again."

    fun isRateLimit(error: AuthRestException): Boolean = error.errorCode == AuthErrorCode.OverRequestRateLimit ||
        error.errorCode == AuthErrorCode.OverEmailSendRateLimit

    /** Why Supabase refused a new password, for the reset link and the change-password sheet. */
    fun passwordRejection(error: AuthRestException): String = when {
        error is AuthWeakPasswordException -> weakPassword(error.reasons)
        error.errorCode == AuthErrorCode.WeakPassword -> weakPassword(emptyList())
        error.errorCode == AuthErrorCode.SamePassword -> AccountRules.SAME_PASSWORD
        error.errorCode == AuthErrorCode.ReauthenticationNeeded || error.errorCode == AuthErrorCode.ReauthenticationNotValid ->
            "For your security, sign in again and then change your password."
        // Supabase refuses passwords it can't store, for example ones that are too long for its hashing.
        error.errorCode == AuthErrorCode.ValidationFailed -> "NAFS couldn't accept that password. Try a shorter or different one."
        isRateLimit(error) -> TOO_MANY_ATTEMPTS
        else -> "Your password wasn't changed. Try again."
    }

    /** Supabase lists why a password is weak: "length", "characters" or "pwned" (found in a known breach). */
    fun weakPassword(reasons: List<String>): String = when {
        "pwned" in reasons -> "That password has appeared in a data breach. Choose a different one."
        "characters" in reasons -> "Choose a stronger password with a mix of upper- and lowercase letters, numbers and symbols."
        "length" in reasons -> "That password is too short for NAFS's security settings. Choose a longer one."
        else -> "Choose a stronger password."
    }

    fun emailChangeRejection(error: AuthRestException): String = when (error.errorCode) {
        AuthErrorCode.EmailExists, AuthErrorCode.UserAlreadyExists, AuthErrorCode.Conflict ->
            "Another account already uses that email. Choose a different address."
        AuthErrorCode.EmailAddressInvalid, AuthErrorCode.ValidationFailed -> "Enter a valid email address."
        AuthErrorCode.EmailAddressNotAuthorized -> "NAFS can't send email to that address yet. Try a different one."
        AuthErrorCode.OverEmailSendRateLimit -> TOO_MANY_EMAILS
        AuthErrorCode.OverRequestRateLimit -> TOO_MANY_ATTEMPTS
        AuthErrorCode.ReauthenticationNeeded, AuthErrorCode.SessionNotFound, AuthErrorCode.SessionExpired ->
            "Sign in again, then change your email."
        else -> "Your email wasn't changed. Try again."
    }
}
