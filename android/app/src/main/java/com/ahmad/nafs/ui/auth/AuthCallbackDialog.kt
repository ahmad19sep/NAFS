package com.ahmad.nafs.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmad.nafs.core.auth.AuthCallbackController
import com.ahmad.nafs.core.auth.PasswordRules
import com.ahmad.nafs.ui.components.NafsBanner
import com.ahmad.nafs.ui.components.NafsBannerKind
import com.ahmad.nafs.ui.components.NafsTextField
import com.ahmad.nafs.ui.theme.Nafs

/**
 * Shows the progress and result of an auth link. The work itself runs in [AuthCallbackController]'s own scope, so
 * this dialog can be recreated (rotation) at any point and simply shows the latest state.
 */
@Composable
fun AuthCallbackDialog(controller: AuthCallbackController) {
    val state by controller.state.collectAsStateWithLifecycle()
    // Passwords live only as long as this dialog; they are never saved to instance state.
    var password by remember(state.resetUserId) { mutableStateOf("") }
    var confirmation by remember(state.resetUserId) { mutableStateOf("") }
    var showPasswords by remember(state.resetUserId) { mutableStateOf(false) }
    if (state.processing || state.resetUserId != null || state.error != null || state.message != null || state.followUp != null) {
        val resetting = state.resetUserId != null
        val submit: () -> Unit = {
            if (resetting) controller.updatePassword(password, confirmation) else controller.dismiss()
        }
        val mismatch = confirmation.isNotEmpty() && confirmation != password
        val transformation = if (showPasswords) VisualTransformation.None else PasswordVisualTransformation()
        val scheme = MaterialTheme.colorScheme
        AlertDialog(
            onDismissRequest = controller::dismiss,
            icon = {
                when {
                    resetting -> Icon(Icons.Rounded.Key, contentDescription = null, tint = scheme.primary)
                    state.processing -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    state.error != null -> Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = scheme.error)
                    state.followUp != null -> Icon(Icons.Rounded.MarkEmailRead, contentDescription = null, tint = scheme.primary)
                    else -> Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Nafs.colors.success)
                }
            },
            title = {
                Text(
                    when {
                        resetting -> "Set a new password"
                        state.processing -> "Verifying your link"
                        state.error != null -> "This link didn't work"
                        state.followUp != null -> "One more step"
                        else -> "You're all set"
                    },
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    when {
                        resetting -> Text(
                            "Use ${PasswordRules.HINT} and enter the same password twice.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                        state.processing -> Text(
                            "Confirming your sign-in link. This takes a moment.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    if (state.processing && resetting) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("Updating your password…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    state.error?.let { NafsBanner(it, kind = NafsBannerKind.ERROR) }
                    state.message?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                    state.followUp?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                    if (resetting) {
                        NafsTextField(
                            value = password,
                            onValueChange = { password = it.take(PasswordRules.MAX_LENGTH) },
                            label = "New password",
                            leadingIcon = Icons.Rounded.Lock,
                            enabled = !state.processing,
                            supportingText = PasswordRules.HINT,
                            visualTransformation = transformation,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                            trailingIcon = {
                                IconButton(onClick = { showPasswords = !showPasswords }) {
                                    Icon(
                                        if (showPasswords) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = if (showPasswords) "Hide passwords" else "Show passwords",
                                    )
                                }
                            },
                        )
                        NafsTextField(
                            value = confirmation,
                            onValueChange = { confirmation = it.take(PasswordRules.MAX_LENGTH) },
                            label = "Confirm password",
                            leadingIcon = Icons.Rounded.Lock,
                            enabled = !state.processing,
                            isError = mismatch,
                            supportingText = if (mismatch) "The passwords don't match yet." else null,
                            visualTransformation = transformation,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { if (!state.processing) submit() }),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = submit, enabled = !state.processing) {
                    Text(if (resetting) "Update password" else "Close")
                }
            },
            dismissButton = {
                if (resetting) TextButton(onClick = controller::dismiss, enabled = !state.processing) { Text("Cancel") }
            },
        )
    }
}
