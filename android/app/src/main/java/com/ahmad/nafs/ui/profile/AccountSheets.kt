package com.ahmad.nafs.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ahmad.nafs.core.auth.PasswordField
import com.ahmad.nafs.core.auth.PasswordRules
import com.ahmad.nafs.ui.components.NafsBanner
import com.ahmad.nafs.ui.components.NafsBannerKind
import com.ahmad.nafs.ui.components.NafsIconBadge
import com.ahmad.nafs.ui.components.NafsPrimaryButton
import com.ahmad.nafs.ui.components.NafsProgressBar
import com.ahmad.nafs.ui.components.NafsSecondaryButton
import com.ahmad.nafs.ui.components.NafsTextField
import com.ahmad.nafs.ui.theme.Nafs
import kotlin.math.roundToLong

// ─── Sheets (state that never leaves the screen) ─────────────────────────────

/** A modal sheet for one account task; it can't be swiped away while [locked] (a request is running). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountSheet(locked: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val isLocked by rememberUpdatedState(locked)
    ModalBottomSheet(
        onDismissRequest = { if (!isLocked) onDismiss() },
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { it != SheetValue.Hidden || !isLocked },
        ),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.navigationBarsPadding().imePadding().verticalScroll(rememberScrollState())) { content() }
    }
}

/** The typed passwords stay in memory only while the sheet is open; they are never saved to instance state. */
@Composable
internal fun ChangePasswordSheet(
    state: PasswordChangeUiState,
    accountEmail: String?,
    onSubmit: (current: String, new: String, confirmation: String) -> Unit,
    onSendResetLink: () -> Unit,
    onDismiss: () -> Unit,
) {
    var fields by remember { mutableStateOf(PasswordFields()) }
    AccountSheet(locked = state.saving, onDismiss = onDismiss) {
        ChangePasswordContent(
            state = state,
            fields = fields,
            accountEmail = accountEmail,
            onFieldsChange = { fields = it },
            onSubmit = { onSubmit(fields.current, fields.new, fields.confirmation) },
            onSendResetLink = onSendResetLink,
            onClose = onDismiss,
        )
    }
}

@Composable
internal fun ChangeEmailSheet(
    state: EmailChangeUiState,
    currentEmail: String,
    pendingEmail: String?,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newEmail by rememberSaveable { mutableStateOf("") }
    AccountSheet(locked = state.saving, onDismiss = onDismiss) {
        ChangeEmailContent(
            state = state,
            currentEmail = currentEmail,
            pendingEmail = pendingEmail,
            newEmail = newEmail,
            onNewEmailChange = { newEmail = it.take(254) },
            onSubmit = { onSubmit(newEmail) },
            onClose = onDismiss,
        )
    }
}

@Composable
internal fun DataExportSheet(
    state: DataExportState,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onShare: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Closing the sheet keeps a running export going; the Account row shows its progress.
    AccountSheet(locked = false, onDismiss = onDismiss) {
        DataExportContent(state, onStart = onStart, onCancel = onCancel, onShare = onShare, onClose = onDismiss)
    }
}

// ─── Stateless content (rendered by screenshot tests) ────────────────────────

internal data class PasswordFields(
    val current: String = "",
    val new: String = "",
    val confirmation: String = "",
    val visible: Boolean = false,
)

@Composable
internal fun ChangePasswordContent(
    state: PasswordChangeUiState,
    fields: PasswordFields,
    accountEmail: String?,
    onFieldsChange: (PasswordFields) -> Unit,
    onSubmit: () -> Unit,
    onSendResetLink: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transformation = if (fields.visible) VisualTransformation.None else PasswordVisualTransformation()
    val liveMismatch = fields.confirmation.isNotEmpty() && fields.confirmation != fields.new
    fun errorFor(field: PasswordField) = state.error.takeIf { state.errorField == field }

    SheetColumn(modifier) {
        SheetHeader(Icons.Rounded.Password, "Change password", "First confirm it's you with your current password.")
        val done = state.done
        if (done != null) {
            NafsBanner(done, kind = NafsBannerKind.SUCCESS)
            NafsPrimaryButton("Done", onClick = onClose, modifier = Modifier.fillMaxWidth())
            return@SheetColumn
        }
        NafsTextField(
            value = fields.current,
            onValueChange = { onFieldsChange(fields.copy(current = it.take(PasswordRules.MAX_LENGTH))) },
            label = "Current password",
            leadingIcon = Icons.Rounded.Lock,
            enabled = !state.saving,
            isError = errorFor(PasswordField.CURRENT) != null,
            supportingText = errorFor(PasswordField.CURRENT),
            visualTransformation = transformation,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            trailingIcon = {
                IconButton(onClick = { onFieldsChange(fields.copy(visible = !fields.visible)) }) {
                    Icon(
                        if (fields.visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (fields.visible) "Hide passwords" else "Show passwords",
                    )
                }
            },
        )
        NafsTextField(
            value = fields.new,
            onValueChange = { onFieldsChange(fields.copy(new = it.take(PasswordRules.MAX_LENGTH))) },
            label = "New password",
            leadingIcon = Icons.Rounded.Key,
            enabled = !state.saving,
            isError = errorFor(PasswordField.NEW) != null,
            supportingText = errorFor(PasswordField.NEW) ?: PasswordRules.HINT,
            visualTransformation = transformation,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
        )
        NafsTextField(
            value = fields.confirmation,
            onValueChange = { onFieldsChange(fields.copy(confirmation = it.take(PasswordRules.MAX_LENGTH))) },
            label = "Confirm new password",
            leadingIcon = Icons.Rounded.Key,
            enabled = !state.saving,
            isError = errorFor(PasswordField.CONFIRM) != null || liveMismatch,
            supportingText = errorFor(PasswordField.CONFIRM) ?: if (liveMismatch) "The passwords don't match yet." else null,
            visualTransformation = transformation,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (!state.saving) onSubmit() }),
        )
        state.error?.takeIf { state.errorField == null }?.let { NafsBanner(it, kind = NafsBannerKind.ERROR) }
        state.resetLinkMessage?.let { NafsBanner(it, kind = NafsBannerKind.INFO) }
        SheetButtons(primary = "Change password", busy = state.saving, onPrimary = onSubmit, onCancel = onClose)
        if (accountEmail != null) {
            TextButton(
                onClick = onSendResetLink,
                enabled = !state.saving && !state.sendingResetLink,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text(if (state.sendingResetLink) "Sending a reset link…" else "Forgot your current password?") }
        }
    }
}

@Composable
internal fun ChangeEmailContent(
    state: EmailChangeUiState,
    currentEmail: String,
    pendingEmail: String?,
    newEmail: String,
    onNewEmailChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    SheetColumn(modifier) {
        SheetHeader(Icons.Rounded.AlternateEmail, "Change email", "You sign in with $currentEmail.")
        val sentTo = state.sentTo
        val done = state.done
        when {
            sentTo != null -> {
                NafsBanner(
                    title = "Check your inbox",
                    message = "We sent a confirmation link to $sentTo. You may also get one at $currentEmail: open every " +
                        "link you receive. Until then you keep signing in with $currentEmail.",
                    kind = NafsBannerKind.SUCCESS,
                )
                Text(
                    "Open the links on this phone if you can, so NAFS finishes the change straight away.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                NafsPrimaryButton("Done", onClick = onClose, modifier = Modifier.fillMaxWidth())
            }
            done != null -> {
                NafsBanner(done, kind = NafsBannerKind.SUCCESS)
                NafsPrimaryButton("Done", onClick = onClose, modifier = Modifier.fillMaxWidth())
            }
            else -> {
                pendingEmail?.let {
                    NafsBanner(
                        title = "Waiting for confirmation",
                        message = "A change to $it is waiting for the links we emailed. A new request replaces it.",
                        kind = NafsBannerKind.INFO,
                    )
                }
                NafsTextField(
                    value = newEmail,
                    onValueChange = onNewEmailChange,
                    label = "New email",
                    placeholder = "you@example.com",
                    leadingIcon = Icons.Rounded.Mail,
                    enabled = !state.saving,
                    isError = state.error != null,
                    supportingText = state.error,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (!state.saving) onSubmit() }),
                )
                Text(
                    "NAFS emails a confirmation link to the new address and may send one to your current address too. " +
                        "Your sign-in email changes only once the links are opened.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                SheetButtons(primary = "Send link", busy = state.saving, onPrimary = onSubmit, onCancel = onClose)
            }
        }
    }
}

@Composable
internal fun DataExportContent(
    state: DataExportState,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onShare: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    SheetColumn(modifier) {
        SheetHeader(Icons.Rounded.Download, "Download my data", "Everything saved to your NAFS account, in one file you can keep.")
        when (state) {
            DataExportState.Idle -> {
                Text(
                    "Your profile, tasks, habits, goals, challenges, check-ins, health, prayers, journals, letters and coach " +
                        "conversations, as a JSON file. Photos are included as links.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                NafsBanner(
                    "The file is your full record, private notes included. Share it only with apps and people you trust.",
                    kind = NafsBannerKind.WARNING,
                )
                NafsPrimaryButton("Prepare my file", onClick = onStart, icon = Icons.Rounded.Download, modifier = Modifier.fillMaxWidth())
            }
            is DataExportState.Running -> {
                val progress = state.progress
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Collecting ${progress.currentLabel.ifEmpty { "your records" }}…", style = MaterialTheme.typography.titleSmall)
                    NafsProgressBar(progress.fraction, color = Nafs.colors.tasks)
                    Text(
                        "${minOf(progress.tablesDone + 1, progress.tablesTotal)} of ${progress.tablesTotal} sections · " +
                            "${progress.rowsSoFar} records so far",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Text(
                    "You can close this sheet; the export keeps going while Profile is open.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                NafsSecondaryButton("Cancel export", onClick = onCancel, modifier = Modifier.fillMaxWidth())
            }
            is DataExportState.Ready -> {
                NafsBanner(
                    title = "Your file is ready",
                    message = "${state.fileName} · ${formatBytes(state.sizeBytes)} · ${state.rowCount} records",
                    kind = NafsBannerKind.SUCCESS,
                )
                if (state.unavailable.isNotEmpty()) {
                    NafsBanner(
                        title = "Not included",
                        message = state.unavailable.joinToString("\n") { "${it.label}: ${it.reason}" },
                        kind = NafsBannerKind.WARNING,
                    )
                }
                NafsPrimaryButton("Save or share", onClick = { onShare(state.filePath) }, icon = Icons.Rounded.Share, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Done") }
            }
            is DataExportState.Failed -> {
                NafsBanner(title = "The export didn't finish", message = state.message, kind = NafsBannerKind.ERROR)
                NafsPrimaryButton("Try again", onClick = onStart, icon = Icons.Rounded.Refresh, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

// ─── Shared pieces ───────────────────────────────────────────────────────────

@Composable
private fun SheetColumn(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
private fun SheetHeader(icon: ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        NafsIconBadge(icon, MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SheetButtons(primary: String, busy: Boolean, onPrimary: () -> Unit, onCancel: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TextButton(onClick = onCancel, enabled = !busy, modifier = Modifier.weight(1f).height(54.dp)) { Text("Cancel") }
        NafsPrimaryButton(primary, onClick = onPrimary, loading = busy, modifier = Modifier.weight(1f))
    }
}

/** "820 B", "14.2 KB", "3.1 MB", with ASCII digits whatever the locale. */
internal fun formatBytes(bytes: Long): String {
    fun oneDecimal(value: Double): String = (value * 10).roundToLong().let { "${it / 10}.${it % 10}" }
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024L * 1024 -> "${oneDecimal(bytes / 1024.0)} KB"
        else -> "${oneDecimal(bytes / (1024.0 * 1024.0))} MB"
    }
}
