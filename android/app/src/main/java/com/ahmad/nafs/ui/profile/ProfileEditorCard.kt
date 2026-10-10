package com.ahmad.nafs.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ahmad.nafs.ui.components.PublicPhoto
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

@Composable
fun ProfileEditorCard(profile: UserProfile, viewModel: ProfileEditorViewModel = hiltViewModel()) {
    val busy by viewModel.busy.collectAsStateWithLifecycle(); val message by viewModel.message.collectAsStateWithLifecycle()
    var name by rememberSaveable(profile.userId, profile.name) { mutableStateOf(profile.name) }
    var timezone by rememberSaveable(profile.userId, profile.timezone) { mutableStateOf(profile.timezone) }
    var deleting by rememberSaveable { mutableStateOf(false) }; var confirmation by rememberSaveable { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { viewModel.upload(profile, it) } }
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Profile", style = MaterialTheme.typography.titleMedium)
            PublicPhoto(profile.avatarUrl, "Profile photo", Modifier.size(96.dp))
            Text(profile.name.split(' ').mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString(""), style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(name, { if (it.length <= 80) name = it }, label = { Text("Name") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(timezone, { timezone = it }, label = { Text("IANA timezone") }, supportingText = { Text("For example Asia/Karachi or Europe/London") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
            Button(onClick = { viewModel.save(profile, name, timezone) }, enabled = !busy) { Text("Save profile") }
            OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy) { Text("Change profile photo") }
            Text("Profile photos use your existing public avatar bucket.", style = MaterialTheme.typography.bodySmall)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it) }
            TextButton(onClick = { deleting = true }, enabled = !busy) { Text("Delete account", color = MaterialTheme.colorScheme.error) }
        }
    }
    if (deleting) AlertDialog(onDismissRequest = { if (!busy) deleting = false }, title = { Text("Delete your account?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("This permanently removes your sign-in account, saved records, and uploaded files. Type DELETE to continue.")
            OutlinedTextField(confirmation, { confirmation = it }, label = { Text("Confirmation") }, enabled = !busy)
            message?.let { Text(it) }
        } }, confirmButton = { TextButton(onClick = { viewModel.delete(profile, confirmation) }, enabled = confirmation == "DELETE" && !busy) { Text("Delete permanently") } },
        dismissButton = { TextButton(onClick = { deleting = false }, enabled = !busy) { Text("Cancel") } })
}
