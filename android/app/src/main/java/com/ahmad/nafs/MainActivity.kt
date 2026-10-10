package com.ahmad.nafs

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.ahmad.nafs.core.auth.AuthCallbackController
import com.ahmad.nafs.ui.auth.AuthCallbackDialog
import javax.inject.Inject
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmad.nafs.core.preferences.ThemeMode
import com.ahmad.nafs.ui.NafsApp
import com.ahmad.nafs.ui.RootViewModel
import com.ahmad.nafs.ui.theme.NafsTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var authCallbacks: AuthCallbackController
    private fun handleAuthIntent(intent: Intent?) { intent?.data?.let { uri -> lifecycleScope.launch { authCallbacks.handle(uri) } } }
    private val requestedDestination = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleAuthIntent(intent)
        requestedDestination.value = intent?.getStringExtra(EXTRA_DESTINATION)
        enableEdgeToEdge()
        setContent {
            val viewModel: RootViewModel = hiltViewModel()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val appState by viewModel.appState.collectAsStateWithLifecycle()
            val profileMessage by viewModel.profileMessage.collectAsStateWithLifecycle()
            val destination by requestedDestination.collectAsStateWithLifecycle()

            NafsTheme(themeMode = themeMode) {
                AuthCallbackDialog(authCallbacks)
                NafsApp(
                    themeMode = themeMode,
                    appState = appState,
                    onThemeModeChange = viewModel::setThemeMode,
                    onRetrySession = viewModel::retrySession,
                    onRetryProfile = viewModel::retryProfile,
                    onSignOut = viewModel::signOut,
                    requestedDestination = destination,
                    onDestinationHandled = { requestedDestination.value = null },
                    profileMessage = profileMessage,
                    onUseDeviceTimezone = viewModel::useDeviceTimezone,
                    onDismissProfileMessage = viewModel::dismissProfileMessage,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthIntent(intent)
        requestedDestination.value = intent.getStringExtra(EXTRA_DESTINATION)
    }

    companion object {
        const val EXTRA_DESTINATION = "com.ahmad.nafs.extra.DESTINATION"
        const val EXTRA_TASK_ID = "com.ahmad.nafs.extra.TASK_ID"
        const val DESTINATION_TASKS = "tasks"
    }
}

