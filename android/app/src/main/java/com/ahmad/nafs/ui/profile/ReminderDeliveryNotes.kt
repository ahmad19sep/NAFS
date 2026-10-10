package com.ahmad.nafs.ui.profile

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Some phone makers stop apps from starting in the background unless the user allows it, which silently
 * drops alarms after a restart or app update. Shown only when it applies to this phone.
 */
object ReminderDelivery {
    private const val FILE = "reminder_delivery"
    private val autostartMakers = setOf("xiaomi", "redmi", "poco")

    fun needsAutostart(): Boolean = Build.MANUFACTURER.lowercase() in autostartMakers || Build.BRAND.lowercase() in autostartMakers

    fun batteryRestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == false

    fun autostartConfirmed(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("autostart", false)
    fun confirmAutostart(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putBoolean("autostart", true) }

    /** Xiaomi's Autostart list, or this app's system settings page when that screen is unavailable. */
    fun openAutostart(context: Context) {
        val miui = Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(miui) }.onFailure { openAppSettings(context) }
    }

    fun openBatterySettings(context: Context) {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure { openAppSettings(context) }
    }

    private fun openAppSettings(context: Context) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

@Composable
fun ReminderDeliveryNotes() {
    val context = LocalContext.current
    var batteryRestricted by remember { mutableStateOf(ReminderDelivery.batteryRestricted(context)) }
    var autostartConfirmed by remember { mutableStateOf(ReminderDelivery.autostartConfirmed(context)) }
    // Returning from system settings may have changed either setting.
    LifecycleResumeEffect(Unit) {
        batteryRestricted = ReminderDelivery.batteryRestricted(context)
        onPauseOrDispose { }
    }
    val showAutostart = ReminderDelivery.needsAutostart() && !autostartConfirmed
    if (!showAutostart && !batteryRestricted) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Reliable delivery", style = MaterialTheme.typography.titleSmall)
        if (showAutostart) {
            Text("On this phone, allow Autostart for NAFS so reminders come back after a restart or an app update.",
                style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { ReminderDelivery.openAutostart(context) }) { Text("Open Autostart") }
                TextButton(onClick = { ReminderDelivery.confirmAutostart(context); autostartConfirmed = true }) { Text("I've allowed it") }
            }
        }
        if (batteryRestricted) {
            Text("Battery optimization can delay reminders while NAFS is closed. Set NAFS to unrestricted for on-time alerts.",
                style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { ReminderDelivery.openBatterySettings(context) }) { Text("Battery settings") }
        }
    }
}
