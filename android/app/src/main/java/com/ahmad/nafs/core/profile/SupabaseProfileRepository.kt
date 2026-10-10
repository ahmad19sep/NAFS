package com.ahmad.nafs.core.profile

import com.ahmad.nafs.core.auth.EmailRules
import com.ahmad.nafs.core.database.CachedProfileDao
import com.ahmad.nafs.core.database.CachedProfileEntity
import com.ahmad.nafs.core.network.SupabaseClientProvider
import com.ahmad.nafs.domain.onboarding.deterministicStarterHabitId
import com.ahmad.nafs.domain.onboarding.starterHabits
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Singleton
class SupabaseProfileRepository @Inject constructor(
    private val clientProvider: SupabaseClientProvider,
    private val profileDao: CachedProfileDao,
) : ProfileRepository {
    private val _state = MutableStateFlow<ProfileState>(ProfileState.Idle)
    override val state: StateFlow<ProfileState> = _state.asStateFlow()

    override suspend fun refresh(userId: String, authEmail: String?) {
        _state.value = ProfileState.Loading
        try {
            val client = checkNotNull(clientProvider.client)
            val rows = client.from("users")
                .select(
                    columns = PROFILE_COLUMNS,
                ) {
                    filter { eq("id", userId) }
                    limit(1)
                }
                .decodeList<ProfileDto>()

            val dto = rows.firstOrNull() ?: createMissingProfile(
                userId = userId,
                authEmail = authEmail.orEmpty(),
            )
            val profile = dto.toProfile(isFromCache = false)
            profileDao.upsert(profile.toEntity())
            _state.value = ProfileState.Loaded(profile)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            val cached = profileDao.get(userId)
            _state.value = if (cached != null) {
                ProfileState.Loaded(cached.toProfile(isFromCache = true))
            } else {
                ProfileState.Failure("Unable to load your profile. Check your connection and retry.")
            }
        }
    }

    override suspend fun completeOnboarding(
        userId: String,
        name: String,
        selectedHabitKeys: Set<String>,
        timezone: String?,
    ): ProfileActionResult = try {
        val client = checkNotNull(clientProvider.client)
        val zone = timezone?.trim()?.takeIf { it.isNotEmpty() }
        require(zone == null || TimezoneCatalog.isValid(zone))
        val selectedHabits = starterHabits.filter { it.key in selectedHabitKeys }
        val unselectedIds = starterHabits
            .filterNot { it.key in selectedHabitKeys }
            .map { deterministicStarterHabitId(userId, it.key) }
        if (selectedHabits.isNotEmpty()) {
            val rows = selectedHabits.mapIndexed { index, habit ->
                StarterHabitDto(
                    id = deterministicStarterHabitId(userId, habit.key),
                    userId = userId,
                    name = habit.name,
                    emoji = habit.emoji,
                    type = habit.type,
                    targetValue = habit.targetValue,
                    timeTargetMinutes = habit.timeTargetMinutes,
                    unit = habit.unit,
                    category = habit.category,
                    scoreWeight = habit.scoreWeight,
                    sortOrder = index,
                )
            }
            client.from("habits").upsert(rows) {
                onConflict = "id"
            }
        }
        if (unselectedIds.isNotEmpty()) {
            client.from("habits").delete {
                filter {
                    eq("user_id", userId)
                    isIn("id", unselectedIds)
                }
            }
        }

        val users = client.from("users")
        // One update, so the timezone can't be left behind if setup finishes and the app moves on.
        val result = if (zone == null) {
            users.update(CompleteProfileDto(name = name.trim(), onboardingComplete = true)) {
                select(PROFILE_COLUMNS)
                filter { eq("id", userId) }
            }
        } else {
            users.update(CompleteProfileWithTimezoneDto(name = name.trim(), onboardingComplete = true, timezone = zone)) {
                select(PROFILE_COLUMNS)
                filter { eq("id", userId) }
            }
        }
        val updated = result.decodeSingle<ProfileDto>().toProfile(isFromCache = false)

        profileDao.upsert(updated.toEntity())
        _state.value = ProfileState.Loaded(updated)
        ProfileActionResult.Success
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        ProfileActionResult.Failure("Setup is still incomplete. Check your connection and retry safely.")
    }

    override suspend fun clear(userId: String?) {
        if (userId != null) profileDao.deleteForUser(userId)
        _state.value = ProfileState.Idle
    }

    override suspend fun updateIdentity(userId: String, name: String, avatarUrl: String?): ProfileActionResult = try {
        require(name.trim().length in 1..80)
        require(avatarUrl == null || (avatarUrl.startsWith("https://") && avatarUrl.length <= 2048))
        val updated = checkNotNull(clientProvider.client).from("users").update(UpdateIdentityDto(name.trim(), avatarUrl)) {
            select(PROFILE_COLUMNS)
            filter { eq("id", userId) }
        }.decodeSingle<ProfileDto>().toProfile(isFromCache = false)
        profileDao.upsert(updated.toEntity())
        _state.value = ProfileState.Loaded(updated)
        ProfileActionResult.Success
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        ProfileActionResult.Failure("Profile was not changed. Check your connection and retry.")
    }

    override suspend fun updateAvatar(userId: String, avatarUrl: String?): ProfileActionResult = try {
        require(avatarUrl == null || (avatarUrl.startsWith("https://") && avatarUrl.length <= 2048))
        val updated = checkNotNull(clientProvider.client).from("users").update(UpdateAvatarDto(avatarUrl)) {
            select(PROFILE_COLUMNS)
            filter { eq("id", userId) }
        }.decodeSingle<ProfileDto>().toProfile(isFromCache = false)
        // The returned row proves the change; an update that matched nothing would fail above.
        check(updated.avatarUrl == avatarUrl)
        profileDao.upsert(updated.toEntity())
        _state.value = ProfileState.Loaded(updated)
        ProfileActionResult.Success
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        ProfileActionResult.Failure("Your photo wasn't changed. Check your connection and retry.")
    }

    override suspend fun updateEmail(userId: String, email: String): ProfileActionResult = try {
        require(EmailRules.isValid(email))
        val updated = checkNotNull(clientProvider.client).from("users").update(UpdateEmailDto(email.trim())) {
            select(PROFILE_COLUMNS)
            filter { eq("id", userId) }
        }.decodeSingle<ProfileDto>().toProfile(isFromCache = false)
        profileDao.upsert(updated.toEntity())
        _state.value = ProfileState.Loaded(updated)
        ProfileActionResult.Success
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        ProfileActionResult.Failure("Your profile email wasn't updated. It will be retried next time.")
    }

    override suspend fun updateTimezone(userId: String, timezone: String): ProfileActionResult = try {
        ZoneId.of(timezone)
        val updated = checkNotNull(clientProvider.client).from("users").update(
            UpdateTimezoneDto(timezone),
        ) {
            select(PROFILE_COLUMNS)
            filter { eq("id", userId) }
        }.decodeSingle<ProfileDto>().toProfile(isFromCache = false)
        profileDao.upsert(updated.toEntity())
        _state.value = ProfileState.Loaded(updated)
        ProfileActionResult.Success
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        ProfileActionResult.Failure("Timezone was not changed. Check your connection and try again.")
    }

    override suspend fun updateDeenEnabled(userId: String, enabled: Boolean): ProfileActionResult = try {
        val updated = checkNotNull(clientProvider.client).from("users").update(UpdateDeenDto(enabled)) {
            select(PROFILE_COLUMNS)
            filter { eq("id", userId) }
        }.decodeSingle<ProfileDto>().toProfile(isFromCache = false)
        profileDao.upsert(updated.toEntity())
        _state.value = ProfileState.Loaded(updated)
        ProfileActionResult.Success
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        ProfileActionResult.Failure("Faith mode was not changed. Check your connection and retry.")
    }

    private suspend fun createMissingProfile(userId: String, authEmail: String): ProfileDto {
        val fallbackName = authEmail.substringBefore('@').ifBlank { "Friend" }
        val created = ProfileDto(
            id = userId,
            name = fallbackName,
            email = authEmail,
            timezone = DEFAULT_TIMEZONE,
            avatarUrl = null,
            onboardingComplete = false,
        )
        checkNotNull(clientProvider.client).from("users").upsert(created) {
            onConflict = "id"
        }
        return created
    }

    private fun ProfileDto.toProfile(isFromCache: Boolean) = UserProfile(
        userId = id,
        name = name,
        email = email,
        timezone = timezone,
        avatarUrl = avatarUrl,
        onboardingComplete = onboardingComplete,
        isFromCache = isFromCache,
        deenEnabled = deenEnabled,
    )

    private fun UserProfile.toEntity() = CachedProfileEntity(
        userId = userId,
        name = name,
        email = email,
        timezone = timezone,
        avatarUrl = avatarUrl,
        onboardingComplete = onboardingComplete,
        serverUpdatedAt = null,
        cachedAtEpochMillis = System.currentTimeMillis(),
        deenEnabled = deenEnabled,
    )

    private fun CachedProfileEntity.toProfile(isFromCache: Boolean) = UserProfile(
        userId = userId,
        name = name,
        email = email,
        timezone = timezone,
        avatarUrl = avatarUrl,
        onboardingComplete = onboardingComplete,
        isFromCache = isFromCache,
        deenEnabled = deenEnabled,
    )

    private companion object {
        const val DEFAULT_TIMEZONE = "Asia/Karachi"
        val PROFILE_COLUMNS = Columns.list(
            "id",
            "name",
            "email",
            "timezone",
            "avatar_url",
            "onboarding_complete",
            "deen_enabled",
        )
    }
}

@Serializable
private data class ProfileDto(
    val id: String,
    val name: String,
    val email: String,
    val timezone: String,
    @SerialName("avatar_url") val avatarUrl: String?,
    @SerialName("onboarding_complete") val onboardingComplete: Boolean,
    @SerialName("deen_enabled") val deenEnabled: Boolean = false,
)

@Serializable
private data class CompleteProfileDto(
    val name: String,
    @SerialName("onboarding_complete") val onboardingComplete: Boolean,
)

@Serializable
private data class CompleteProfileWithTimezoneDto(
    val name: String,
    @SerialName("onboarding_complete") val onboardingComplete: Boolean,
    val timezone: String,
)

@Serializable
private data class UpdateTimezoneDto(val timezone: String)

/** No default value, so a null is sent as `"avatar_url": null` and really clears the photo. */
@Serializable
private data class UpdateAvatarDto(@SerialName("avatar_url") val avatarUrl: String?)

@Serializable
private data class UpdateEmailDto(val email: String)

@Serializable
private data class UpdateDeenDto(@SerialName("deen_enabled") val enabled: Boolean)

@Serializable
private data class UpdateIdentityDto(val name: String, @SerialName("avatar_url") val avatarUrl: String?)

@Serializable
private data class StarterHabitDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val emoji: String,
    val type: String,
    @SerialName("target_value") val targetValue: Int,
    @SerialName("time_target_mins") val timeTargetMinutes: Int,
    val unit: String,
    val category: String,
    @SerialName("score_weight") val scoreWeight: Int,
    @SerialName("sort_order") val sortOrder: Int,
)
