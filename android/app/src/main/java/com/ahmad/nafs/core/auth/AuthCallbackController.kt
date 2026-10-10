package com.ahmad.nafs.core.auth

import android.net.Uri
import com.ahmad.nafs.BuildConfig
import com.ahmad.nafs.core.network.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AuthCallbackState(val processing: Boolean = false, val resetUserId: String? = null, val error: String? = null, val message: String? = null)

@Singleton
class AuthCallbackController @Inject constructor(private val provider: SupabaseClientProvider) {
    private val _state = MutableStateFlow(AuthCallbackState()); val state = _state.asStateFlow()
    suspend fun handle(uri: Uri) {
        if (uri.scheme != BuildConfig.APPLICATION_ID || uri.host != "auth" || uri.path !in listOf("", "/", "/reset")) return
        if (_state.value.processing) return
        val code = uri.getQueryParameter("code")
        if (!uri.fragment.isNullOrBlank() || code.isNullOrBlank() || code.length > 2048) {
            _state.value = AuthCallbackState(error = "This sign-in link is invalid or expired. Request a new link from this device."); return
        }
        _state.value = AuthCallbackState(processing = true)
        try {
            val auth = checkNotNull(provider.client).auth
            auth.awaitInitialization()
            // SDK exchanges against the locally retained PKCE verifier; raw token fragments are never imported.
            auth.exchangeCodeForSession(code)
            val userId = checkNotNull(auth.currentUserOrNull()).id
            _state.value = AuthCallbackState(resetUserId = if (uri.path == "/reset") userId else null)
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { _state.value = AuthCallbackState(error = "This sign-in link could not be verified. Open the newest link on the device that requested it.") }
    }
    suspend fun updatePassword(password: String, confirmation: String) {
        val expected = _state.value.resetUserId ?: return
        if (_state.value.processing) return
        if (password.length !in 8..128 || password != confirmation) {
            _state.value = _state.value.copy(error = "Use 8–128 characters and enter the same password twice."); return
        }
        _state.value = _state.value.copy(processing = true, error = null)
        try {
            val auth = checkNotNull(provider.client).auth
            check(auth.currentUserOrNull()?.id == expected)
            auth.updateUser { this.password = password }
            check(auth.currentUserOrNull()?.id == expected)
            _state.value = AuthCallbackState(message = "Password updated.")
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { _state.value = AuthCallbackState(resetUserId = expected, error = "Password could not be updated. Retry, or request a fresh reset link.") }
    }
    fun dismiss() { if (!_state.value.processing) _state.value = AuthCallbackState() }
}
