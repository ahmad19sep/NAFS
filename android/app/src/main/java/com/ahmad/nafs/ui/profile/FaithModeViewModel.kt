package com.ahmad.nafs.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmad.nafs.core.profile.ProfileActionResult
import com.ahmad.nafs.core.profile.ProfileRepository
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
