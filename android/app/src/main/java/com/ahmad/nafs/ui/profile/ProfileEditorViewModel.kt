package com.ahmad.nafs.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmad.nafs.core.auth.AuthActionResult
import com.ahmad.nafs.core.auth.AuthRepository
import com.ahmad.nafs.core.auth.SignedOutNotice
import com.ahmad.nafs.core.export.DataExporter
import com.ahmad.nafs.core.network.ApiFailure
import com.ahmad.nafs.core.network.NafsApiClient
import com.ahmad.nafs.core.network.NafsApiException
import com.ahmad.nafs.core.profile.ProfileActionResult
import com.ahmad.nafs.core.profile.ProfileRepository
import com.ahmad.nafs.core.profile.TimezoneCatalog
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.core.records.RecordMediaUploader
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Where a profile message is shown: at the top, under the timezone row, or in the delete dialog. */
enum class ProfileNoticeArea { PROFILE, TIMEZONE, DELETE }

data class ProfileNotice(val text: String, val isError: Boolean, val area: ProfileNoticeArea = ProfileNoticeArea.PROFILE)

@HiltViewModel
class ProfileEditorViewModel @Inject constructor(
    private val repository: ProfileRepository,
    private val uploader: RecordMediaUploader,
    private val api: NafsApiClient,
    private val auth: AuthRepository,
    private val exporter: DataExporter,
    private val signedOutNotice: SignedOutNotice,
) : ViewModel() {
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _notice = MutableStateFlow<ProfileNotice?>(null)
    val notice: StateFlow<ProfileNotice?> = _notice.asStateFlow()

    fun dismissNotice() {
        _notice.value = null
    }

    fun save(profile: UserProfile, name: String, timezone: String) = action(ProfileNoticeArea.PROFILE) {
        require(name.trim().length in 1..80) { "Enter a name between 1 and 80 characters." }
        require(TimezoneCatalog.isValid(timezone)) { "Choose a timezone from the list." }
        val identity = repository.updateIdentity(profile.userId, name, profile.avatarUrl)
        if (identity is ProfileActionResult.Failure) return@action failure(identity.message)
        if (timezone == profile.timezone) return@action success(PROFILE_SAVED)
        when (val zone = repository.updateTimezone(profile.userId, timezone)) {
            ProfileActionResult.Success -> success(PROFILE_SAVED)
            is ProfileActionResult.Failure -> failure("Name saved. ${zone.message}")
        }
    }

    /** From the timezone picker in Preferences: saved straight away. */
    fun saveTimezone(profile: UserProfile, timezone: String) = action(ProfileNoticeArea.TIMEZONE) {
        require(TimezoneCatalog.isValid(timezone)) { "Choose a timezone from the list." }
        if (timezone == profile.timezone) return@action success("Daily schedules already follow $timezone.", ProfileNoticeArea.TIMEZONE)
        when (val result = repository.updateTimezone(profile.userId, timezone)) {
            ProfileActionResult.Success -> success("Daily schedules now follow $timezone.", ProfileNoticeArea.TIMEZONE)
            is ProfileActionResult.Failure -> failure(result.message, ProfileNoticeArea.TIMEZONE)
        }
    }

    fun upload(profile: UserProfile, uri: Uri) = action(ProfileNoticeArea.PROFILE) {
        val url = uploader.upload(profile.userId, "avatars", uri)
        when (val result = repository.updateAvatar(profile.userId, url)) {
            ProfileActionResult.Success -> success("Profile photo saved.")
            is ProfileActionResult.Failure -> failure(result.message)
        }
    }

    fun removePhoto(profile: UserProfile) = action(ProfileNoticeArea.PROFILE) {
        if (profile.avatarUrl == null) return@action success("You don't have a profile photo.")
        when (val result = repository.updateAvatar(profile.userId, null)) {
            ProfileActionResult.Success -> success("Profile photo removed.")
            is ProfileActionResult.Failure -> failure(result.message)
        }
    }

    fun delete(profile: UserProfile, confirmation: String) = action(ProfileNoticeArea.DELETE) {
        require(confirmation == "DELETE") { "Type DELETE to confirm." }
        val response = try {
            api.post(profile.userId, "/api/user/delete", buildJsonObject { put("confirm", "DELETE") })
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: NafsApiException) {
            return@action failure(
                when (error.failure) {
                    ApiFailure.TIMEOUT, ApiFailure.RESULT_UNCONFIRMED ->
                        "NAFS couldn't confirm the deletion. Check your connection and try again."
                    else -> error.message ?: DELETE_FAILED
                },
                ProfileNoticeArea.DELETE,
            )
        }
        val deleted = (response["ok"] as? JsonPrimitive)?.booleanOrNull == true &&
            (response["authDeleted"] as? JsonPrimitive)?.booleanOrNull == true
        if (!deleted) return@action failure(DELETE_FAILED, ProfileNoticeArea.DELETE)

        // The account is gone. Whatever happens next, this phone must end up signed out, and the sign-in
        // screen (this screen is gone by then) says what happened.
        exporter.clearExports()
        signedOutNotice.post("Your NAFS account and its data were deleted.")
        val signedOut = auth.signOut() is AuthActionResult.Success || auth.clearLocalSession() is AuthActionResult.Success
        if (signedOut) {
            success("Your account was deleted.", ProfileNoticeArea.DELETE)
        } else {
            failure("Your account was deleted, but this phone couldn't finish signing out. Close NAFS and open it again.", ProfileNoticeArea.DELETE)
        }
    }

    private fun success(text: String, area: ProfileNoticeArea = ProfileNoticeArea.PROFILE) = ProfileNotice(text, isError = false, area)

    private fun failure(text: String, area: ProfileNoticeArea = ProfileNoticeArea.PROFILE) = ProfileNotice(text, isError = true, area)

    private fun action(area: ProfileNoticeArea, block: suspend () -> ProfileNotice) {
        if (_busy.value) return
        _busy.value = true
        _notice.value = null
        viewModelScope.launch {
            try {
                _notice.value = block()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: IllegalArgumentException) {
                _notice.value = failure(error.message ?: "Check the fields and retry.", area)
            } catch (_: Exception) {
                _notice.value = failure("This change could not be confirmed. Reload your profile before retrying.", area)
            } finally {
                _busy.value = false
            }
        }
    }

    private companion object {
        const val PROFILE_SAVED = "Profile saved."
        const val DELETE_FAILED = "Your account wasn't deleted. Try again."
    }
}
