package com.ahmad.nafs.ui.profile

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ahmad.nafs.ui.components.NafsBanner
import com.ahmad.nafs.ui.components.NafsBannerKind
import com.ahmad.nafs.ui.components.NafsCard
import com.ahmad.nafs.ui.components.NafsChoiceChip
import com.ahmad.nafs.ui.components.NafsDivider
import com.ahmad.nafs.ui.components.NafsListRow
import com.ahmad.nafs.ui.components.NafsSecondaryButton
import com.ahmad.nafs.ui.theme.Nafs
import com.ahmad.nafs.core.deen.PrayerAutoLocate
import com.ahmad.nafs.core.deen.PrayerLocateResult
import com.ahmad.nafs.core.deen.PrayerLocation
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.core.records.PrivateRecordStore
import com.ahmad.nafs.core.reminders.*
import com.ahmad.nafs.core.tasks.TaskRepository
import com.ahmad.nafs.core.tasks.TaskLoadResult
import com.ahmad.nafs.core.time.AccountDate
import com.ahmad.nafs.domain.reminders.ScheduledReminderRules
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ReminderOptionsViewModel @Inject constructor(private val preferences: ReminderPreferences, private val tasks: TaskRepository,
    private val taskScheduler: TaskReminderScheduler, private val activityScheduler: ActivityReminderScheduler,
    private val alarms: ReminderAlarmScheduler, private val records: PrivateRecordStore,
    private val autoLocate: PrayerAutoLocate, private val location: PrayerLocation) : ViewModel() {
    /** Where prayer times come from: "your current area", a city, or null when nothing is saved yet. */
    private val _prayerSource = MutableStateFlow<String?>(null)
    val prayerSource = _prayerSource.asStateFlow()
    private val _locating = MutableStateFlow(false)
    val locating = _locating.asStateFlow()
    fun read(user: String) = preferences.read(user)
    fun canScheduleExact() = alarms.canScheduleExact()
    fun hasLocationPermission() = location.hasPermission()
    fun loadPrayerSource(user: String) {
        viewModelScope.launch {
            _prayerSource.value = try { PrayerAutoLocate.label(records.read(user, PrayerAutoLocate.KEY) ?: records.read(user, "prayer-times-next")) }
                catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { null }
        }
    }
    /** Finds prayer times for the current area, then schedules them, and reports the outcome to the user. */
    fun locate(profile: UserProfile, onMessage: (String) -> Unit) {
        if (_locating.value) return
        _locating.value = true
        viewModelScope.launch {
            try {
                onMessage(when (autoLocate.run(profile.userId, profile.timezone)) {
                    is PrayerLocateResult.Saved -> { alarms.reschedule(profile.userId, profile.timezone, profile.deenEnabled); "Prayer times found for your current area." }
                    PrayerLocateResult.NoPermission -> "Location was not allowed. Choose a city in Prayer times instead."
                    PrayerLocateResult.LocationOff -> "Location is turned off. Turn it on, or choose a city in Prayer times."
                    PrayerLocateResult.Unavailable -> "Your area could not be found right now. Open Prayer times to retry or enter a city."
                    PrayerLocateResult.FetchFailed -> "Prayer times could not be fetched. Check your connection, then retry."
                })
            } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) {
                onMessage("Prayer times could not be set up. Open Prayer times to retry.")
            } finally { _locating.value = false; loadPrayerSource(profile.userId) }
        }
    }
    fun save(profile: UserProfile, options: ReminderOptions) {
        preferences.save(profile.userId, options)
        activityScheduler.schedule(profile.userId, profile.timezone)
        alarms.reschedule(profile.userId, profile.timezone, profile.deenEnabled)
        viewModelScope.launch {
            if (!options.tasks) taskScheduler.cancelAll(profile.userId)
            else { val result = tasks.load(profile.userId, AccountDate.today(profile.timezone)); if (result is TaskLoadResult.Success) taskScheduler.reconcile(profile.userId, result.tasks, profile.timezone) }
        }
    }
}
// POST_NOTIFICATIONS is only requested behind needsNotificationPermission(), which checks for Android 13+.
@SuppressLint("InlinedApi")
@Composable
fun ReminderOptionsCard(profile: UserProfile, onOpenPrayerTimes: () -> Unit, viewModel: ReminderOptionsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var options by remember(profile.userId) { mutableStateOf(viewModel.read(profile.userId)) }
    var message by remember { mutableStateOf<String?>(null) }
    var exact by remember { mutableStateOf(viewModel.canScheduleExact()) }
    val prayerSource by viewModel.prayerSource.collectAsStateWithLifecycle()
    val locating by viewModel.locating.collectAsStateWithLifecycle()
    // Returning from Android settings may have granted alarms or changed the saved prayer source.
    LifecycleResumeEffect(profile.userId) {
        exact = viewModel.canScheduleExact(); viewModel.loadPrayerSource(profile.userId)
        onPauseOrDispose { }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) message = "Notifications are off for NAFS. Allow them in Android settings to receive reminders."
    }
    fun needsNotificationPermission() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    // Requests are chained because Android shows one permission dialog at a time.
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.locate(profile) { message = it }
        else message = "Location was not allowed. Choose a city in Prayer times so NAFS can schedule prayer alerts."
        if (needsNotificationPermission()) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    fun update(value: ReminderOptions) {
        val before = options
        val turnedOn = listOf(value.tasks to options.tasks, value.habits to options.habits, value.letters to options.letters,
            value.prayers to options.prayers, value.daily to options.daily, value.weekly to options.weekly).any { (now, before) -> now && !before }
        runCatching { viewModel.save(profile, value) }
            .onSuccess {
                options = value; message = "Reminder settings saved on this device."
                val needsArea = value.prayers && !before.prayers && profile.deenEnabled && prayerSource == null
                when {
                    needsArea && viewModel.hasLocationPermission() -> {
                        viewModel.locate(profile) { message = it }
                        if (needsNotificationPermission()) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    needsArea -> locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    turnedOn && needsNotificationPermission() -> notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            .onFailure { message = "Reminder settings could not be saved. Retry." }
    }
    val clock = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    val anyTimed = options.tasks || options.prayers && profile.deenEnabled || options.daily || options.weekly
    val c = Nafs.colors
    NafsCard(contentPadding = PaddingValues(vertical = 6.dp)) {
        ReminderSwitch("Tasks", "30 minutes before each due time", Icons.Rounded.TaskAlt, c.tasks, options.tasks) { update(options.copy(tasks = it)) }
        NafsDivider()
        ReminderSwitch("Habits", "Nudges for unfinished scheduled habits", Icons.Rounded.Autorenew, c.habits, options.habits) { update(options.copy(habits = it)) }
        NafsDivider()
        ReminderSwitch("Letters", "When a future-self letter is ready", Icons.Rounded.MailOutline, c.coach, options.letters) { update(options.copy(letters = it)) }
        if (profile.deenEnabled) {
            NafsDivider()
            ReminderSwitch("Prayer times", "When each prayer begins", Icons.Rounded.NightsStay, c.deen, options.prayers) { update(options.copy(prayers = it)) }
            if (options.prayers) Column(Modifier.padding(start = 68.dp, end = 16.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(when {
                    locating -> "Finding prayer times for your area…"
                    prayerSource != null -> "Using prayer times for $prayerSource."
                    else -> "Prayer alerts need your area. Allow location, or choose a city in Prayer times."
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!locating) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (prayerSource == null) NafsChoiceChip("Use my location", selected = false, onClick = {
                        if (viewModel.hasLocationPermission()) viewModel.locate(profile) { message = it }
                        else locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    })
                    NafsChoiceChip("Prayer times", selected = false, onClick = onOpenPrayerTimes)
                }
            }
        }
        NafsDivider()
        ReminderSwitch("End-of-day summary", "Every day at ${ScheduledReminderRules.dailySummaryTime.format(clock)}", Icons.Rounded.Bedtime, c.reflect,
            options.daily) { update(options.copy(daily = it)) }
        NafsDivider()
        ReminderSwitch("Weekly report", "Sundays at ${ScheduledReminderRules.weeklyReportTime.format(clock)}", Icons.Rounded.CalendarMonth, c.gold,
            options.weekly) { update(options.copy(weekly = it)) }

        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (anyTimed && !exact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                NafsBanner(
                    "Android may deliver reminders several minutes late. Allow alarms for NAFS to get them on time.",
                    kind = NafsBannerKind.WARNING,
                    actionLabel = "Allow",
                    onAction = {
                        runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())) }
                            .onFailure { message = "Open Android settings → Apps → NAFS → Alarms & reminders." }
                    },
                )
            }
            if (anyTimed) ReminderDeliveryNotes()
            message?.let { NafsBanner(it, kind = NafsBannerKind.INFO, onDismiss = { message = null }) }
            NafsSecondaryButton(
                "Send a test notification",
                icon = Icons.Rounded.NotificationsActive,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    message = if (LocalReminderNotifications.show(context, "nafs-test", "NAFS test notification", "Reminders are available on this device.", "profile"))
                        "Test notification sent." else "Allow notifications in Android settings, then retry."
                },
            )
            Text(
                "Times follow your account timezone (${profile.timezone}). Habit and letter reminders are checked several times a day when " +
                    "you're online. Battery settings can delay delivery.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReminderSwitch(label: String, detail: String, icon: ImageVector, tint: Color, checked: Boolean, onChange: (Boolean) -> Unit) {
    NafsListRow(
        title = label,
        subtitle = detail,
        icon = icon,
        tint = tint,
        trailing = { Switch(checked, onChange, Modifier.semantics { contentDescription = "$label · $detail" }) },
    )
}
