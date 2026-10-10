package com.ahmad.nafs.ui.profile

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ahmad.nafs.ui.components.NafsChoiceChip
import com.ahmad.nafs.ui.theme.Nafs

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

/**
 * A compact warning block inside the reminders card, styled like a warning banner, with chip actions. Renders
 * nothing once reminders can be delivered reliably on this phone.
 */
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
    val colors = Nafs.colors
    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = colors.warningContainer,
        contentColor = colors.onWarningContainer,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(20.dp))
                Text("Reliable delivery", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            }
            if (showAutostart) {
                Text("On this phone, allow Autostart for NAFS so reminders come back after a restart or an app update.",
                    style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NafsChoiceChip("Open Autostart", selected = false, onClick = { ReminderDelivery.openAutostart(context) })
                    NafsChoiceChip("I've allowed it", selected = false, onClick = {
                        ReminderDelivery.confirmAutostart(context); autostartConfirmed = true
                    })
                }
            }
            if (showAutostart && batteryRestricted) {
                Box(Modifier.fillMaxWidth().padding(vertical = 2.dp).height(1.dp).background(colors.onWarningContainer.copy(alpha = 0.12f)))
            }
            if (batteryRestricted) {
                Text("Battery optimization can delay reminders while NAFS is closed. Set NAFS to unrestricted for on-time alerts.",
                    style = MaterialTheme.typography.bodySmall)
                NafsChoiceChip("Battery settings", selected = false, onClick = { ReminderDelivery.openBatterySettings(context) })
            }
        }
    }
}
