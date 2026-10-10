package com.ahmad.nafs.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmad.nafs.core.auth.AccountChangeResult
import com.ahmad.nafs.core.auth.AccountEmail
import com.ahmad.nafs.core.auth.AccountRepository
import com.ahmad.nafs.core.auth.AccountRules
import com.ahmad.nafs.core.auth.AuthActionResult
import com.ahmad.nafs.core.auth.AuthRepository
import com.ahmad.nafs.core.auth.EmailRules
import com.ahmad.nafs.core.auth.PasswordField
import com.ahmad.nafs.core.profile.ProfileRepository
import com.ahmad.nafs.core.profile.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PasswordChangeUiState(
    val saving: Boolean = false,
    val error: String? = null,
    /** The field [error] belongs to; null for problems such as a lost connection. */
    val errorField: PasswordField? = null,
    val done: String? = null,
    val sendingResetLink: Boolean = false,
    val resetLinkMessage: String? = null,
)

data class EmailChangeUiState(
    val saving: Boolean = false,
    val error: String? = null,
    /** Where the confirmation link went once Supabase accepted the request; null when it applied at once. */
    val sentTo: String? = null,
    val done: String? = null,
)

/** Password and email changes for the signed-in account, from Profile → Account. */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val account: AccountRepository,
    private val auth: AuthRepository,
    private val profiles: ProfileRepository,
) : ViewModel() {
    val email: StateFlow<AccountEmail> = account.accountEmail
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountEmail())

    private val _password = MutableStateFlow(PasswordChangeUiState())
    val password: StateFlow<PasswordChangeUiState> = _password.asStateFlow()
    private val _emailChange = MutableStateFlow(EmailChangeUiState())
    val emailChange: StateFlow<EmailChangeUiState> = _emailChange.asStateFlow()

    private var refreshed = false
    private var syncedEmail: String? = null

    /** Once per visit: picks up an email change that was confirmed on another device. */
    fun refreshOnce() {
        if (refreshed) return
        refreshed = true
        viewModelScope.launch { account.refresh() }
    }

    fun resetPasswordChange() {
        if (!_password.value.saving) _password.value = PasswordChangeUiState()
    }

    fun changePassword(userId: String, current: String, new: String, confirmation: String) {
        if (_password.value.saving) return
        AccountRules.passwordChangeProblem(current, new, confirmation)?.let {
            _password.value = PasswordChangeUiState(error = it.message, errorField = it.field)
            return
        }
        _password.value = PasswordChangeUiState(saving = true)
        viewModelScope.launch {
            _password.value = when (val result = account.changePassword(userId, current, new)) {
                is AccountChangeResult.Done -> PasswordChangeUiState(done = result.message)
                is AccountChangeResult.Failed -> PasswordChangeUiState(error = result.message, errorField = result.field)
            }
        }
    }

    /** For a forgotten current password: the reset link opens NAFS and asks for a new one. */
    fun sendResetLink(email: String?) {
        val address = email?.takeIf { EmailRules.isValid(it) } ?: return
        if (_password.value.saving || _password.value.sendingResetLink) return
        _password.value = _password.value.copy(sendingResetLink = true, resetLinkMessage = null)
        viewModelScope.launch {
            val message = when (val result = auth.sendPasswordReset(EmailRules.normalize(address))) {
                AuthActionResult.Success -> "We emailed a reset link to $address. Open it on this phone to choose a new password."
                is AuthActionResult.Failure -> result.message
            }
            _password.value = _password.value.copy(sendingResetLink = false, resetLinkMessage = message)
        }
    }

    fun resetEmailChange() {
        if (!_emailChange.value.saving) _emailChange.value = EmailChangeUiState()
    }

    fun changeEmail(userId: String, newEmail: String, currentEmail: String?) {
        if (_emailChange.value.saving) return
        AccountRules.emailChangeProblem(newEmail, currentEmail)?.let {
            _emailChange.value = EmailChangeUiState(error = it)
            return
        }
        val target = EmailRules.normalize(newEmail)
        _emailChange.value = EmailChangeUiState(saving = true)
        viewModelScope.launch {
            _emailChange.value = when (val result = account.changeEmail(userId, target)) {
                is AccountChangeResult.Done -> EmailChangeUiState(done = result.message, sentTo = target.takeUnless { result.applied })
                is AccountChangeResult.Failed -> EmailChangeUiState(error = result.message)
            }
        }
    }

    /**
     * Keeps the profile's copy of the email (shown on the profile card and used by the web app) in step with the
     * confirmed sign-in email after a change. Best effort: a failure is retried on the next visit.
     */
    fun syncProfileEmail(profile: UserProfile, email: AccountEmail) {
        val confirmed = email.current ?: return
        if (email.userId != profile.userId || profile.isFromCache) return
        if (EmailRules.normalize(confirmed) == EmailRules.normalize(profile.email) || syncedEmail == confirmed) return
        syncedEmail = confirmed
        viewModelScope.launch { profiles.updateEmail(profile.userId, confirmed) }
    }
}
