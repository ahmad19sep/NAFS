package com.ahmad.nafs.core.profile

import kotlinx.coroutines.flow.StateFlow

interface ProfileRepository {
    val state: StateFlow<ProfileState>

    suspend fun refresh(userId: String, authEmail: String?)

    suspend fun completeOnboarding(
        userId: String,
        name: String,
        selectedHabitKeys: Set<String>,
    ): ProfileActionResult

    suspend fun updateTimezone(userId: String, timezone: String): ProfileActionResult

    suspend fun updateDeenEnabled(userId: String, enabled: Boolean): ProfileActionResult

    suspend fun updateIdentity(userId: String, name: String, avatarUrl: String?): ProfileActionResult

    suspend fun clear(userId: String?)
}
