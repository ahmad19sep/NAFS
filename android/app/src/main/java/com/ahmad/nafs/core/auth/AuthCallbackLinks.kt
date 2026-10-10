package com.ahmad.nafs.core.auth

/** What a `<applicationId>://auth` link asks for, read without importing anything from it. */
internal sealed interface AuthCallbackLink {
    /** A PKCE code to exchange; [reset] when it came from a password-reset email. */
    data class Code(val code: String, val reset: Boolean) : AuthCallbackLink

    /** Supabase redirected with an error, for example an expired or already-used link. */
    data class Rejected(val expired: Boolean) : AuthCallbackLink

    /** The first of the two links of a secure email change was accepted; the other address still has to confirm. */
    data object EmailChangeHalfConfirmed : AuthCallbackLink

    data object Invalid : AuthCallbackLink
}

internal object AuthCallbackLinks {
    private const val HOST = "auth"
    private val paths = setOf("", "/", "/reset")
    private const val MAX_CODE_LENGTH = 2048

    /** Supabase's own wording after the first link of a double confirmation; only this is recognised, never shown. */
    private const val HALF_CONFIRMED_PREFIX = "Confirmation link accepted"

    /** Returns null for links that aren't NAFS auth callbacks at all, so they're ignored. */
    fun parse(
        scheme: String?,
        host: String?,
        path: String?,
        query: (String) -> String?,
        fragment: String?,
        expectedScheme: String,
    ): AuthCallbackLink? {
        if (scheme != expectedScheme || host != HOST || path == null || path !in paths) return null
        val errorCode = query("error_code")
        if (errorCode != null || query("error") != null) return AuthCallbackLink.Rejected(expired = errorCode == "otp_expired")
        val code = query("code")
            ?: return if (query("message")?.startsWith(HALF_CONFIRMED_PREFIX) == true) {
                AuthCallbackLink.EmailChangeHalfConfirmed
            } else {
                AuthCallbackLink.Invalid
            }
        // A fragment means tokens from the implicit flow; they're refused rather than imported.
        if (!fragment.isNullOrBlank() || code.isBlank() || code.length > MAX_CODE_LENGTH) return AuthCallbackLink.Invalid
        return AuthCallbackLink.Code(code, reset = path == "/reset")
    }
}
