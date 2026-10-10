package com.ahmad.nafs.ui.profile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.ZoneId

@Composable
fun NotificationSettingsCard() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    fun notificationsEnabled(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED)

    var enabled by remember { mutableStateOf(notificationsEnabled()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { enabled = notificationsEnabled() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) enabled = notificationsEnabled()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    IntegrationCard(
        icon = { Icon(Icons.Rounded.NotificationsActive, contentDescription = null) },
        title = "Notifications",
        status = if (enabled) "Enabled" else "Needs attention",
        description = if (enabled) {
            "Task reminders can alert you before daily due times. Timing is battery-friendly and may vary slightly."
        } else {
            "Enable notifications so task reminders can reach you when the app is closed."
        },
    ) {
        if (!enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Button(onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                Text("Allow notifications")
            }
        }
        OutlinedButton(onClick = {
            val settingsIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            } else {
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    "package:${context.packageName}".toUri(),
                )
            }
            context.startActivity(settingsIntent)
        }) { Text("System settings") }
    }
}

@Composable
fun TimezoneSettingsCard(
    accountTimezone: String,
    message: String?,
    onUseDeviceTimezone: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    val deviceTimezone = ZoneId.systemDefault().id
    IntegrationCard(
        icon = { Icon(Icons.Rounded.AccessTime, contentDescription = null) },
        title = "Daily timezone",
        status = accountTimezone,
        description = "Daily records, streaks, and reminders use your account timezone. Your device currently uses $deviceTimezone.",
    ) {
        if (accountTimezone != deviceTimezone) {
            Button(onClick = onUseDeviceTimezone) { Text("Use device timezone") }
        }
    }
    message?.let {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            onClick = onDismissMessage,
        ) {
            Text(it, Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun IntegrationCard(
    icon: @Composable () -> Unit,
    title: String,
    status: String,
    description: String,
    actions: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                icon()
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(status, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
            }
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = { actions() })
        }
    }
}
