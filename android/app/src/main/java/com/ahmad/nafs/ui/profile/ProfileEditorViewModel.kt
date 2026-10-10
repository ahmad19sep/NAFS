package com.ahmad.nafs.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmad.nafs.core.auth.AuthRepository
import com.ahmad.nafs.core.network.NafsApiClient
import com.ahmad.nafs.core.profile.*
import com.ahmad.nafs.core.records.RecordMediaUploader
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@HiltViewModel
class ProfileEditorViewModel @Inject constructor(private val repository: ProfileRepository,
    private val uploader: RecordMediaUploader, private val api: NafsApiClient, private val auth: AuthRepository) : ViewModel() {
    private val _busy = MutableStateFlow(false); val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null); val message = _message.asStateFlow()
    fun save(profile: UserProfile, name: String, timezone: String) = action {
        require(name.trim().length in 1..80) { "Enter a name between 1 and 80 characters." }
        java.time.ZoneId.of(timezone.trim())
        val identity = repository.updateIdentity(profile.userId, name, profile.avatarUrl)
        if (identity is ProfileActionResult.Failure) return@action identity.message
        val zone = repository.updateTimezone(profile.userId, timezone.trim())
        if (zone is ProfileActionResult.Failure) "Name saved. ${zone.message}" else "Profile saved."
    }
    fun upload(profile: UserProfile, uri: Uri) = action {
        val url = uploader.upload(profile.userId, "avatars", uri)
        when (val result = repository.updateIdentity(profile.userId, profile.name, url)) {
            ProfileActionResult.Success -> "Profile photo saved."
            is ProfileActionResult.Failure -> result.message
        }
    }
    fun delete(profile: UserProfile, confirmation: String) = action {
        require(confirmation == "DELETE") { "Type DELETE to confirm." }
        val response = api.post(profile.userId, "/api/user/delete", buildJsonObject { put("confirm", "DELETE") })
        check((response["ok"] as? JsonPrimitive)?.booleanOrNull == true && (response["authDeleted"] as? JsonPrimitive)?.booleanOrNull == true)
        auth.signOut()
        "Your account was deleted."
    }
    private fun action(block: suspend () -> String) {
        if (_busy.value) return
        _busy.value = true; _message.value = null
        viewModelScope.launch {
            try { _message.value = block() }
            catch (cancel: CancellationException) { throw cancel }
            catch (error: IllegalArgumentException) { _message.value = error.message ?: "Check the fields and retry." }
            catch (_: Exception) { _message.value = "This change could not be confirmed. Reload your profile before retrying." }
            finally { _busy.value = false }
        }
    }
}
