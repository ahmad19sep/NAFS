package com.ahmad.nafs.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmad.nafs.core.auth.AuthCallbackController
import kotlinx.coroutines.launch

@Composable
fun AuthCallbackDialog(controller: AuthCallbackController) {
    val state by controller.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var password by remember(state.resetUserId) { mutableStateOf("") }
    var confirmation by remember(state.resetUserId) { mutableStateOf("") }
    if (state.processing || state.resetUserId != null || state.error != null || state.message != null) {
        AlertDialog(onDismissRequest = controller::dismiss, title = { Text(if (state.resetUserId != null) "Set a new password" else "Account sign-in") },
            text = { Column {
                if (state.processing) CircularProgressIndicator()
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.message?.let { Text(it) }
                if (state.resetUserId != null) {
                    OutlinedTextField(password, { password = it.take(128) }, label = { Text("New password") }, visualTransformation = PasswordVisualTransformation(), enabled = !state.processing)
                    OutlinedTextField(confirmation, { confirmation = it.take(128) }, label = { Text("Confirm password") }, visualTransformation = PasswordVisualTransformation(), enabled = !state.processing)
                }
            } },
            confirmButton = { TextButton(onClick = { if (state.resetUserId != null) scope.launch { controller.updatePassword(password, confirmation) } else controller.dismiss() }, enabled = !state.processing) { Text(if (state.resetUserId != null) "Update password" else "Close") } },
            dismissButton = { if (state.resetUserId != null) TextButton(controller::dismiss, enabled = !state.processing) { Text("Cancel") } })
    }
}
