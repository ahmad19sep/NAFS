package com.ahmad.nafs.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmad.nafs.BuildConfig
import com.ahmad.nafs.core.auth.SignUpGender

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
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (BuildConfig.GOOGLE_AUTH_ENABLED && state.mode == AuthMode.SIGN_IN) {
                OutlinedButton(onClick = onGoogle, enabled = !state.isSubmitting) { Text("Continue with Google") }
            }
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text(
                    text = "ن",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.headlineLarge,
                    fontFamily = FontFamily.Serif,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("NAFS", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Build a life you can trust.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (state.mode != AuthMode.FORGOT_PASSWORD) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ModeButton(
                            label = "Sign in",
                            selected = state.mode == AuthMode.SIGN_IN,
                            onClick = { onModeChange(AuthMode.SIGN_IN) },
                            modifier = Modifier.weight(1f),
                        )
                        ModeButton(
                            label = "Sign up",
                            selected = state.mode == AuthMode.SIGN_UP,
                            onClick = { onModeChange(AuthMode.SIGN_UP) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    Text("Reset your password", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Enter your email and we'll send a reset link.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                if (state.mode == AuthMode.SIGN_UP) {
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = onNameChange,
                        label = { Text("Full name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    )
                    Text("Gender", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SignUpGender.entries.forEach { gender ->
                            FilterChip(
                                selected = state.gender == gender,
                                onClick = { onGenderChange(gender) },
                                label = {
                                    Text(gender.name.lowercase().replaceFirstChar(Char::uppercase))
                                },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = state.email,
                    onValueChange = onEmailChange,
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = if (state.mode == AuthMode.FORGOT_PASSWORD) {
                            ImeAction.Done
                        } else {
                            ImeAction.Next
                        },
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (!state.isSubmitting) onSubmit() },
                    ),
                )

                if (state.mode != AuthMode.FORGOT_PASSWORD) {
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = onPasswordChange,
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (state.passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { if (!state.isSubmitting) onSubmit() },
                        ),
                        trailingIcon = {
                            IconButton(onClick = onTogglePasswordVisibility) {
                                Icon(
                                    imageVector = if (state.passwordVisible) {
                                        Icons.Rounded.VisibilityOff
                                    } else {
                                        Icons.Rounded.Visibility
                                    },
                                    contentDescription = if (state.passwordVisible) {
                                        "Hide password"
                                    } else {
                                        "Show password"
                                    },
                                )
                            }
                        },
                    )
                }

                state.errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                state.confirmationMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Button(
                    onClick = onSubmit,
                    enabled = !state.isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Rounded.Lock, contentDescription = null)
                        Text(
                            text = when (state.mode) {
                                AuthMode.SIGN_IN -> "  Sign in"
                                AuthMode.SIGN_UP -> "  Create account"
                                AuthMode.FORGOT_PASSWORD -> "  Send reset link"
                            },
                        )
                    }
                }

                when (state.mode) {
                    AuthMode.SIGN_IN -> TextButton(
                        onClick = { onModeChange(AuthMode.FORGOT_PASSWORD) },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("Forgot password?")
                    }
                    AuthMode.FORGOT_PASSWORD -> TextButton(
                        onClick = { onModeChange(AuthMode.SIGN_IN) },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text("Back to sign in")
                    }
                    AuthMode.SIGN_UP -> Unit
                }
            }
        }
    }
}

@Composable
private fun ModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
    }
}
