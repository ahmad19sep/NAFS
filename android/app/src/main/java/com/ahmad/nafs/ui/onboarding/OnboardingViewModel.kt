package com.ahmad.nafs.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmad.nafs.core.profile.ProfileActionResult
import com.ahmad.nafs.core.profile.ProfileRepository
import com.ahmad.nafs.core.profile.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val userId: String? = null,
    val step: Int = 1,
    val name: String = "",
    val selectedHabitKeys: Set<String> = emptySet(),
    val isFromCache: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun initialize(profile: UserProfile) {
        if (_uiState.value.userId == profile.userId) return
        _uiState.value = OnboardingUiState(
            userId = profile.userId,
            name = profile.name,
            isFromCache = profile.isFromCache,
        )
    }

    fun setName(value: String) {
        _uiState.update { it.copy(name = value, errorMessage = null) }
    }

    fun next() {
        if (_uiState.value.name.trim().isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Enter the name you want NAFS to use.") }
        } else {
            _uiState.update { it.copy(step = 2, errorMessage = null) }
        }
    }

    fun back() {
        _uiState.update { it.copy(step = 1, errorMessage = null) }
    }

    fun toggleHabit(key: String) {
        _uiState.update { state ->
            val selected = state.selectedHabitKeys.toMutableSet()
            if (!selected.add(key)) selected.remove(key)
            state.copy(selectedHabitKeys = selected, errorMessage = null)
        }
    }

    fun finish() {
        val current = _uiState.value
        val userId = current.userId ?: return
        if (current.isSaving) return
        if (current.name.trim().isEmpty()) {
            _uiState.update { it.copy(step = 1, errorMessage = "Enter your name before continuing.") }
            return
        }

        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            when (
                val result = profileRepository.completeOnboarding(
                    userId = userId,
                    name = current.name,
                    selectedHabitKeys = current.selectedHabitKeys,
                )
            ) {
                ProfileActionResult.Success -> _uiState.update { it.copy(isSaving = false) }
                is ProfileActionResult.Failure -> _uiState.update {
                    it.copy(isSaving = false, errorMessage = result.message)
                }
            }
        }
    }
}
