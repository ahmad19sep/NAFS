package com.ahmad.nafs.core.auth

import kotlinx.coroutines.flow.Flow

/** The signed-in account's email as Supabase Auth knows it, and a change still waiting for confirmation. */
data class AccountEmail(val userId: String? = null, val current: String? = null, val pending: String? = null)

sealed interface AccountChangeResult {
    /** Supabase accepted the change. [applied] is false while it still waits for the emailed confirmation links. */
    data class Done(val message: String, val applied: Boolean = true) : AccountChangeResult

    data class Failed(val message: String, val field: PasswordField? = null) : AccountChangeResult
}

/** Changes to the sign-in itself while signed in: password and email. */
interface AccountRepository {
    val accountEmail: Flow<AccountEmail>

    /** Re-reads the account from Supabase so a change confirmed on another device shows up. Failures keep what is known. */
    suspend fun refresh()

    suspend fun changePassword(userId: String, currentPassword: String, newPassword: String): AccountChangeResult

    suspend fun changeEmail(userId: String, newEmail: String): AccountChangeResult
}
