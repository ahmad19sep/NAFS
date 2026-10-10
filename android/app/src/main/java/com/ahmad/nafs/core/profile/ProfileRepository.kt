package com.ahmad.nafs.core.profile

import kotlinx.coroutines.flow.StateFlow

interface ProfileRepository {
    val state: StateFlow<ProfileState>

    suspend fun refresh(userId: String, authEmail: String?)

    /** Finishes setup in one update. [timezone], when given, replaces the account default (Asia/Karachi). */
    suspend fun completeOnboarding(
        userId: String,
        name: String,
        selectedHabitKeys: Set<String>,
        timezone: String? = null,
    ): ProfileActionResult

    suspend fun updateTimezone(userId: String, timezone: String): ProfileActionResult

    suspend fun updateDeenEnabled(userId: String, enabled: Boolean): ProfileActionResult

    suspend fun updateIdentity(userId: String, name: String, avatarUrl: String?): ProfileActionResult

    /** Sets or, with null, removes the profile photo without touching anything else. */
    suspend fun updateAvatar(userId: String, avatarUrl: String?): ProfileActionResult

    /** Copies the confirmed sign-in email onto the profile row after an email change. */
    suspend fun updateEmail(userId: String, email: String): ProfileActionResult

    suspend fun clear(userId: String?)
}
