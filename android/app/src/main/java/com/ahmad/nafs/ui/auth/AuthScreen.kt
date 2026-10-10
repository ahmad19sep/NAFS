package com.ahmad.nafs.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.MarkEmailUnread
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmad.nafs.BuildConfig
import com.ahmad.nafs.core.auth.PasswordRules
import com.ahmad.nafs.core.auth.SignUpGender
import com.ahmad.nafs.ui.components.NafsBanner
import com.ahmad.nafs.ui.components.NafsBannerKind
import com.ahmad.nafs.ui.components.NafsBrandLockup
import com.ahmad.nafs.ui.components.NafsCard
import com.ahmad.nafs.ui.components.NafsChoiceChip
import com.ahmad.nafs.ui.components.NafsPrimaryButton
import com.ahmad.nafs.ui.components.NafsSecondaryButton
import com.ahmad.nafs.ui.components.NafsSegmented
import com.ahmad.nafs.ui.components.NafsTextField
import com.ahmad.nafs.ui.theme.Nafs

@Composable
fun AuthRoute(viewModel: AuthViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AuthScreen(
        state = state,
        onModeChange = viewModel::setMode,
        onNameChange = viewModel::setName,
        onGenderChange = viewModel::setGender,
        onEmailChange = viewModel::setEmail,
        onPasswordChange = viewModel::setPassword,
        onTogglePasswordVisibility = viewModel::togglePasswordVisibility,
        onSubmit = viewModel::submit,
        onGoogle = viewModel::signInGoogle,
        onResendConfirmation = viewModel::resendConfirmation,
    )
}

@Composable
fun AuthScreen(
    state: AuthUiState,
    onModeChange: (AuthMode) -> Unit,
    onNameChange: (String) -> Unit,
    onGenderChange: (SignUpGender) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onSubmit: () -> Unit,
    onGoogle: () -> Unit = {},
    showGoogle: Boolean = BuildConfig.GOOGLE_AUTH_ENABLED,
    onResendConfirmation: () -> Unit = {},
) {
    val focus = LocalFocusManager.current
    val scheme = MaterialTheme.colorScheme
    val submit = { focus.clearFocus(); if (!state.isSubmitting) onSubmit() }
    val next = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) })

    Box(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
            .background(
                Brush.verticalGradient(
                    0f to Nafs.colors.heroTop.copy(alpha = if (Nafs.colors.isDark) 0.9f else 0.16f),
                    0.45f to Color.Transparent,
                ),
            ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(20.dp))
            NafsBrandLockup()
            Spacer(Modifier.height(32.dp))

            NafsCard(
                Modifier.widthIn(max = 480.dp).animateContentSize(),
                color = scheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.extraLarge,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(22.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (state.mode == AuthMode.FORGOT_PASSWORD) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onModeChange(AuthMode.SIGN_IN) }, enabled = !state.isSubmitting) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to sign in")
                            }
                            Text("Reset password", style = MaterialTheme.typography.titleLarge)
                        }
                    } else {
                        NafsSegmented(
                            options = listOf("Sign in", "Create account"),
                            selectedIndex = if (state.mode == AuthMode.SIGN_UP) 1 else 0,
                            onSelect = { onModeChange(if (it == 1) AuthMode.SIGN_UP else AuthMode.SIGN_IN) },
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            when (state.mode) {
                                AuthMode.SIGN_IN -> "Welcome back"
                                AuthMode.SIGN_UP -> "Start with intention"
                                AuthMode.FORGOT_PASSWORD -> "Forgot your password?"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            when (state.mode) {
                                AuthMode.SIGN_IN -> "Sign in to pick up where you left off."
                                AuthMode.SIGN_UP -> "Create your account in under a minute."
                                AuthMode.FORGOT_PASSWORD -> "Enter your account email and we'll send a secure reset link."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }

                    if (state.mode == AuthMode.SIGN_UP) {
                        NafsTextField(
                            value = state.name,
                            onValueChange = onNameChange,
                            label = "Full name",
                            leadingIcon = Icons.Rounded.Badge,
                            enabled = !state.isSubmitting,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                            keyboardActions = next,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Gender", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SignUpGender.entries.forEach { gender ->
                                    NafsChoiceChip(
                                        text = gender.label(),
                                        selected = state.gender == gender,
                                        onClick = { onGenderChange(gender) },
                                        enabled = !state.isSubmitting,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }

                    NafsTextField(
                        value = state.email,
                        onValueChange = onEmailChange,
                        label = "Email",
                        placeholder = "you@example.com",
                        leadingIcon = Icons.Rounded.Mail,
                        enabled = !state.isSubmitting,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = if (state.mode == AuthMode.FORGOT_PASSWORD) ImeAction.Done else ImeAction.Next,
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focus.moveFocus(FocusDirection.Down) },
                            onDone = { submit() },
                        ),
                    )

                    if (state.mode != AuthMode.FORGOT_PASSWORD) {
                        NafsTextField(
                            value = state.password,
                            onValueChange = onPasswordChange,
                            label = "Password",
                            leadingIcon = Icons.Rounded.Lock,
                            enabled = !state.isSubmitting,
                            supportingText = if (state.mode == AuthMode.SIGN_UP) PasswordRules.HINT else null,
                            visualTransformation = if (state.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submit() }),
                            trailingIcon = {
                                IconButton(onClick = onTogglePasswordVisibility) {
                                    Icon(
                                        if (state.passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = if (state.passwordVisible) "Hide password" else "Show password",
                                    )
                                }
                            },
                        )
                        if (state.mode == AuthMode.SIGN_IN) {
                            TextButton(
                                onClick = { onModeChange(AuthMode.FORGOT_PASSWORD) },
                                enabled = !state.isSubmitting,
                                modifier = Modifier.align(Alignment.End),
                            ) { Text("Forgot password?") }
                        }
                    }

                    AnimatedVisibility(state.errorMessage != null) {
                        NafsBanner(state.errorMessage.orEmpty(), kind = NafsBannerKind.ERROR)
                    }
                    AnimatedVisibility(state.confirmationMessage != null) {
                        NafsBanner(state.confirmationMessage.orEmpty(), kind = NafsBannerKind.SUCCESS)
                    }
                    AnimatedVisibility(state.pendingConfirmationEmail != null && state.mode != AuthMode.FORGOT_PASSWORD) {
                        ResendConfirmationButton(state, onResendConfirmation)
                    }

                    NafsPrimaryButton(
                        text = when (state.mode) {
                            AuthMode.SIGN_IN -> "Sign in"
                            AuthMode.SIGN_UP -> "Create account"
                            AuthMode.FORGOT_PASSWORD -> "Send reset link"
                        },
                        onClick = submit,
                        loading = state.isSubmitting,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (showGoogle && state.mode == AuthMode.SIGN_IN) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            HorizontalDivider(Modifier.weight(1f), color = scheme.outlineVariant)
                            Text("or", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                            HorizontalDivider(Modifier.weight(1f), color = scheme.outlineVariant)
                        }
                        NafsSecondaryButton(
                            "Continue with Google",
                            onClick = onGoogle,
                            enabled = !state.isSubmitting,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (state.mode == AuthMode.FORGOT_PASSWORD) {
                        TextButton(
                            onClick = { onModeChange(AuthMode.SIGN_IN) },
                            enabled = !state.isSubmitting,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) { Text("Back to sign in") }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.Shield, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Text(
                    "Your session is encrypted on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Offered when sign-in was refused for an unconfirmed email, and after sign-up. Supabase allows one email a minute. */
@Composable
private fun ResendConfirmationButton(state: AuthUiState, onResend: () -> Unit) {
    val waiting = state.resendCooldownSeconds > 0
    NafsSecondaryButton(
        text = when {
            state.isResending -> "Sending…"
            waiting -> "Resend available in ${state.resendCooldownSeconds} s"
            else -> "Resend confirmation email"
        },
        onClick = onResend,
        enabled = !state.isResending && !waiting && !state.isSubmitting,
        icon = Icons.Rounded.MarkEmailUnread,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun SignUpGender.label() = when (this) {
    SignUpGender.MALE -> "Male"
    SignUpGender.FEMALE -> "Female"
}
