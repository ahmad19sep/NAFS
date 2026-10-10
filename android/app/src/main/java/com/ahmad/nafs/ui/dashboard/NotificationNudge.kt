package com.ahmad.nafs.ui.dashboard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ahmad.nafs.ui.components.NafsCard
import com.ahmad.nafs.ui.components.NafsIconBadge
import com.ahmad.nafs.ui.components.NafsPrimaryButton
import com.ahmad.nafs.ui.theme.Nafs

private const val PREFS = "nafs_ui"
private const val DISMISSED = "notification_nudge_dismissed"

private fun notificationsAllowed(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

/**
 * Asks once, on Home, for the notification permission reminders depend on. Hidden when
 * notifications already work or the user said "Not now"; Profile keeps the full controls.
 */
@Composable
fun NotificationNudge() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var allowed by remember { mutableStateOf(notificationsAllowed(context)) }
    var dismissed by remember { mutableStateOf(prefs.getBoolean(DISMISSED, false)) }
    var asked by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        allowed = notificationsAllowed(context)
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        asked = true
        allowed = notificationsAllowed(context)
    }
    if (allowed || dismissed) return

    val openSettings = {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null))
        }
        runCatching { context.startActivity(intent) }
        Unit
    }

    NafsCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                NafsIconBadge(Icons.Rounded.NotificationsActive, Nafs.colors.gold)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Never miss what matters", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Turn on notifications for task deadlines, prayer times and your end-of-day summary.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                NafsPrimaryButton(
                    text = "Turn on",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        // After a denial Android stops showing the dialog; settings is the only way back.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !asked &&
                            NotificationManagerCompat.from(context).areNotificationsEnabled()
                        ) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else openSettings()
                    },
                )
                TextButton(onClick = {
                    prefs.edit { putBoolean(DISMISSED, true) }
                    dismissed = true
                }) { Text("Not now") }
            }
        }
    }
}
