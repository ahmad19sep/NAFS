package com.ahmad.nafs.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmad.nafs.core.auth.AuthActionResult
import com.ahmad.nafs.core.auth.AuthRepository
import com.ahmad.nafs.core.auth.SignUpGender
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun setMode(mode: AuthMode) {
        _uiState.update {
            it.copy(
                mode = mode,
                password = "",
                passwordVisible = false,
                errorMessage = null,
                confirmationMessage = null,
            )
        }
    }

    fun setName(value: String) = updateInput { copy(name = value) }

    fun setGender(value: SignUpGender) = updateInput { copy(gender = value) }

    fun setEmail(value: String) = updateInput { copy(email = value) }

    fun setPassword(value: String) = updateInput { copy(password = value) }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    fun signInGoogle() {
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            val result = authRepository.signInGoogle()
            _uiState.update { it.copy(isSubmitting = false, errorMessage = (result as? AuthActionResult.Failure)?.message) }
        }
    }

    fun submit() {
        val current = _uiState.value
        if (current.isSubmitting) return

        val validationMessage = AuthInputValidator.validate(current)
        if (validationMessage != null) {
            _uiState.update { it.copy(errorMessage = validationMessage) }
            return
        }

        _uiState.update {
            it.copy(
                isSubmitting = true,
                errorMessage = null,
                confirmationMessage = null,
            )
        }
        viewModelScope.launch {
            val email = current.email.trim().lowercase()
            val result = when (current.mode) {
                AuthMode.SIGN_IN -> authRepository.signIn(email, current.password)
                AuthMode.SIGN_UP -> authRepository.signUp(
                    name = current.name.trim(),
                    gender = checkNotNull(current.gender),
                    email = email,
                    password = current.password,
                )
                AuthMode.FORGOT_PASSWORD -> authRepository.sendPasswordReset(email)
            }

            _uiState.update { state ->
                when (result) {
                    AuthActionResult.Success -> state.afterSuccess(email)
                    is AuthActionResult.Failure -> state.copy(
                        isSubmitting = false,
                        errorMessage = result.message,
                    )
                }
            }
        }
    }

    private fun AuthUiState.afterSuccess(email: String): AuthUiState = when (mode) {
        AuthMode.SIGN_IN -> copy(isSubmitting = false)
        AuthMode.SIGN_UP -> copy(
            isSubmitting = false,
            password = "",
            confirmationMessage = "Account created. If confirmation is required, check $email.",
        )
        AuthMode.FORGOT_PASSWORD -> copy(
            mode = AuthMode.SIGN_IN,
            isSubmitting = false,
            password = "",
            confirmationMessage = "If an account exists for $email, a reset link has been sent.",
        )
    }

    private inline fun updateInput(transform: AuthUiState.() -> AuthUiState) {
        _uiState.update {
            it.transform().copy(errorMessage = null, confirmationMessage = null)
        }
    }
}
