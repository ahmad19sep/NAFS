package com.ahmad.nafs.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ahmad.nafs.core.profile.ProfileActionResult
import com.ahmad.nafs.core.profile.ProfileRepository
import com.ahmad.nafs.core.profile.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class FaithModeViewModel @Inject constructor(private val repository: ProfileRepository) : ViewModel() {
    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun setEnabled(userId: String, enabled: Boolean) {
        if (_saving.value) return
        _saving.value = true
        _error.value = null
        viewModelScope.launch {
            _error.value = when (val result = repository.updateDeenEnabled(userId, enabled)) {
                ProfileActionResult.Success -> null
                is ProfileActionResult.Failure -> result.message
            }
            _saving.value = false
        }
    }
}

@Composable
fun FaithModeSettingsCard(profile: UserProfile, viewModel: FaithModeViewModel = hiltViewModel()) {
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Faith mode", style = MaterialTheme.typography.titleMedium)
                    Text("Show daily prayers and Deen. Turning this off keeps your saved history.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = profile.deenEnabled,
                    onCheckedChange = { viewModel.setEnabled(profile.userId, it) },
                    enabled = !saving && !profile.isFromCache,
                    modifier = Modifier.semantics { contentDescription = "Faith mode" },
                )
            }
            if (saving) Text("Saving faith preference…", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
