package com.ahmad.nafs.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmad.nafs.BuildConfig
import com.ahmad.nafs.core.preferences.ThemeMode
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.ui.components.NafsAvatar
import com.ahmad.nafs.ui.components.NafsBanner
import com.ahmad.nafs.ui.components.NafsBannerKind
import com.ahmad.nafs.ui.components.NafsBrandMark
import com.ahmad.nafs.ui.components.NafsCard
import com.ahmad.nafs.ui.components.NafsChoiceChip
import com.ahmad.nafs.ui.components.NafsDivider
import com.ahmad.nafs.ui.components.NafsEyebrow
import com.ahmad.nafs.ui.components.NafsGroup
import com.ahmad.nafs.ui.components.NafsListRow
import com.ahmad.nafs.ui.components.NafsPill
import com.ahmad.nafs.ui.components.NafsPrimaryButton
import com.ahmad.nafs.ui.components.NafsSegmented
import com.ahmad.nafs.ui.components.NafsSpace
import com.ahmad.nafs.ui.components.NafsTextField
import com.ahmad.nafs.ui.components.NafsTopBar
import com.ahmad.nafs.ui.theme.Nafs
import java.time.ZoneId

@Composable
fun ProfileRoute(
    themeMode: ThemeMode,
    profile: UserProfile,
    onThemeModeChange: (ThemeMode) -> Unit,
    onSignOut: () -> Unit,
    profileMessage: String?,
    onUseDeviceTimezone: () -> Unit,
    onDismissProfileMessage: () -> Unit,
    onOpenRoute: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    editor: ProfileEditorViewModel = hiltViewModel(),
    faith: FaithModeViewModel = hiltViewModel(),
) {
    val busy by editor.busy.collectAsStateWithLifecycle()
    val editorMessage by editor.message.collectAsStateWithLifecycle()
    val faithSaving by faith.saving.collectAsStateWithLifecycle()
    val faithError by faith.error.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { editor.upload(profile, it) }
    }
    ProfileScreen(
        profile = profile,
        themeMode = themeMode,
        state = ProfileUiState(
            busy = busy,
            editorMessage = editorMessage,
            faithSaving = faithSaving,
            faithError = faithError,
            timezoneMessage = profileMessage,
            deviceTimezone = ZoneId.systemDefault().id,
        ),
        actions = ProfileActions(
            onBack = onBack,
            onThemeModeChange = onThemeModeChange,
            onSaveProfile = { name, zone -> editor.save(profile, name, zone) },
            onPickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onFaithChange = { faith.setEnabled(profile.userId, it) },
            onUseDeviceTimezone = onUseDeviceTimezone,
            onDismissTimezoneMessage = onDismissProfileMessage,
            onDeleteAccount = { confirmation -> editor.delete(profile, confirmation) },
            onSignOut = onSignOut,
            onOpenRoute = onOpenRoute,
            // Hosted by the web app. If no browser can open it, the tap does nothing rather than crash.
            onOpenPrivacyPolicy = { runCatching { uriHandler.openUri("${BuildConfig.API_BASE_URL}/privacy") } },
        ),
        notifications = {
            NotificationSettingsCard()
            ReminderOptionsCard(profile, onOpenPrayerTimes = { onOpenRoute("prayer-times") })
        },
    )
}

internal data class ProfileUiState(
    val busy: Boolean = false,
    val editorMessage: String? = null,
    val faithSaving: Boolean = false,
    val faithError: String? = null,
    val timezoneMessage: String? = null,
    val deviceTimezone: String = "UTC",
)

internal data class ProfileActions(
    val onBack: (() -> Unit)? = null,
    val onThemeModeChange: (ThemeMode) -> Unit = {},
    val onSaveProfile: (name: String, timezone: String) -> Unit = { _, _ -> },
    val onPickPhoto: () -> Unit = {},
    val onFaithChange: (Boolean) -> Unit = {},
    val onUseDeviceTimezone: () -> Unit = {},
    val onDismissTimezoneMessage: () -> Unit = {},
    val onDeleteAccount: (confirmation: String) -> Unit = {},
    val onSignOut: () -> Unit = {},
    val onOpenRoute: (String) -> Unit = {},
    val onOpenPrivacyPolicy: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileScreen(
    profile: UserProfile,
    themeMode: ThemeMode,
    state: ProfileUiState,
    actions: ProfileActions,
    notifications: @Composable () -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(NafsSpace.item),
    ) {
        item { NafsTopBar(title = "Profile", onBack = actions.onBack) }

        item {
            Column(Modifier.padding(horizontal = NafsSpace.screen), verticalArrangement = Arrangement.spacedBy(NafsSpace.item)) {
                IdentityCard(profile, state.busy, onEdit = { editing = true }, onPickPhoto = actions.onPickPhoto)
                if (profile.isFromCache) NafsBanner("Showing your saved offline profile.", kind = NafsBannerKind.OFFLINE)
                state.editorMessage?.takeIf { !deleting && !editing }?.let {
                    NafsBanner(it, kind = if (it.endsWith("saved.")) NafsBannerKind.SUCCESS else NafsBannerKind.INFO)
                }
            }
        }

        item {
            Section("Appearance") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Theme", style = MaterialTheme.typography.titleSmall)
                    NafsSegmented(
                        options = listOf("System", "Light", "Dark"),
                        selectedIndex = ThemeMode.entries.indexOf(themeMode),
                        onSelect = { actions.onThemeModeChange(ThemeMode.entries[it]) },
                    )
                }
            }
        }

        item {
            Section("Preferences") {
                NafsListRow(
                    title = "Faith mode",
                    subtitle = state.faithError ?: if (state.faithSaving) "Saving…" else "Daily prayers and Deen. Turning it off keeps your history.",
                    icon = Icons.Rounded.NightsStay,
                    tint = Nafs.colors.deen,
                    trailing = {
                        Switch(
                            checked = profile.deenEnabled,
                            onCheckedChange = actions.onFaithChange,
                            enabled = !state.faithSaving && !profile.isFromCache,
                            modifier = Modifier.semantics { contentDescription = "Faith mode" },
                        )
                    },
                )
                NafsDivider()
                NafsListRow(
                    title = "Daily timezone",
                    subtitle = if (profile.timezone == state.deviceTimezone) "${profile.timezone} · matches this device"
                    else "${profile.timezone} · this device uses ${state.deviceTimezone}",
                    icon = Icons.Rounded.AccessTime,
                    tint = Nafs.colors.tasks,
                    trailing = {
                        if (profile.timezone != state.deviceTimezone) {
                            TextButton(onClick = actions.onUseDeviceTimezone) { Text("Use device") }
                        }
                    },
                )
                state.timezoneMessage?.let {
                    Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        NafsBanner(it, kind = NafsBannerKind.INFO, onDismiss = actions.onDismissTimezoneMessage)
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = NafsSpace.screen), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NafsEyebrow("Notifications & reminders", Modifier.padding(start = 4.dp, top = 8.dp))
                notifications()
            }
        }

        item {
            Section("Your progress") {
                NafsListRow("Badges and levels", subtitle = "What you've earned so far", icon = Icons.Rounded.MilitaryTech,
                    tint = Nafs.colors.gold, onClick = { actions.onOpenRoute("achievements") })
                NafsDivider()
                NafsListRow("30-day history", subtitle = "Every area, day by day", icon = Icons.Rounded.Insights,
                    tint = Nafs.colors.tasks, onClick = { actions.onOpenRoute("history") })
                NafsDivider()
                NafsListRow("Weekly & monthly reports", subtitle = "Print or share a report", icon = Icons.Rounded.CalendarMonth,
                    tint = Nafs.colors.tasks, onClick = { actions.onOpenRoute("progress") })
            }
        }

        item {
            Section("Help & about") {
                NafsListRow("Report a bug or idea", subtitle = "Saved to your Bugs & ideas journal", icon = Icons.Rounded.BugReport,
                    tint = Nafs.colors.health, onClick = { actions.onOpenRoute("records/note") })
                NafsDivider()
                NafsListRow("Privacy & data", subtitle = "How NAFS protects your records", icon = Icons.Rounded.Lock,
                    tint = Nafs.colors.habits, onClick = { showAbout = true })
                NafsDivider()
                NafsListRow("Privacy policy", subtitle = "Opens in your browser", icon = Icons.Rounded.Policy,
                    tint = Nafs.colors.habits, onClick = actions.onOpenPrivacyPolicy)
                NafsDivider()
                NafsListRow("About NAFS", subtitle = "Version ${BuildConfig.VERSION_NAME}", icon = Icons.Rounded.Description,
                    tint = scheme.onSurfaceVariant, onClick = { showAbout = true })
            }
        }

        item {
            Section("Account") {
                NafsListRow("Sign out", subtitle = profile.email, icon = Icons.AutoMirrored.Rounded.Logout,
                    tint = scheme.primary, onClick = { confirmSignOut = true }, showChevron = false)
                NafsDivider()
                NafsListRow("Delete account", subtitle = "Permanently remove your account and data", icon = Icons.Rounded.DeleteForever,
                    tint = scheme.error, titleColor = scheme.error, onClick = { deleting = true }, showChevron = false)
            }
        }

        item {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                NafsBrandMark(size = 40.dp)
                Text("NAFS ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant)
            }
        }
    }

    if (editing) {
        ModalBottomSheet(
            onDismissRequest = { if (!state.busy) editing = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = scheme.surfaceContainerLow,
        ) {
            EditProfileSheet(profile, state, onSave = actions.onSaveProfile, onDone = { editing = false })
        }
    }

    if (confirmSignOut) AlertDialog(
        onDismissRequest = { confirmSignOut = false },
        icon = { Icon(Icons.AutoMirrored.Rounded.Logout, null) },
        title = { Text("Sign out of NAFS?") },
        text = { Text("Your records stay safe in your account. Reminders on this phone stop until you sign in again.") },
        confirmButton = { TextButton(onClick = { confirmSignOut = false; actions.onSignOut() }) { Text("Sign out") } },
        dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
    )

    if (deleting) DeleteAccountDialog(state, onDismiss = { deleting = false }, onDelete = actions.onDeleteAccount)

    if (showAbout) AlertDialog(
        onDismissRequest = { showAbout = false },
        icon = { NafsBrandMark(size = 44.dp) },
        title = { Text("NAFS ${BuildConfig.VERSION_NAME}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("A personal accountability app for habits, tasks, goals, health and faith, with a coach that tells you the truth.")
                Text("Privacy", style = MaterialTheme.typography.titleSmall)
                Text("Your session is encrypted with Android Keystore. Health notes, prayers and journals cached on this phone are " +
                    "encrypted too. Location is used only when you ask NAFS to find prayer times, rounded to about 1 km, and never in " +
                    "the background. AI requests go through the NAFS server; no AI keys live in the app.")
                Text("Open-source licences", style = MaterialTheme.typography.titleSmall)
                Text("Plus Jakarta Sans and Amiri are used under the SIL Open Font License 1.1. Licence texts ship inside the app.")
            }
        },
        confirmButton = { TextButton(onClick = { showAbout = false }) { Text("Close") } },
    )
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    NafsGroup(Modifier.padding(horizontal = NafsSpace.screen).padding(top = 8.dp), title = title, content = content)
}

@Composable
private fun IdentityCard(profile: UserProfile, busy: Boolean, onEdit: () -> Unit, onPickPhoto: () -> Unit) {
    NafsCard(contentPadding = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box {
                NafsAvatar(profile.name, profile.avatarUrl, size = 76.dp)
                Surface(
                    onClick = onPickPhoto,
                    enabled = !busy,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.BottomEnd).size(28.dp)
                        .border(2.dp, MaterialTheme.colorScheme.surfaceContainer, CircleShape)
                        .semantics { contentDescription = "Change profile photo" },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.PhotoCamera, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(15.dp))
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(profile.name.ifBlank { "Your name" }, style = MaterialTheme.typography.headlineSmall)
                Text(profile.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    NafsPill(profile.timezone.substringAfterLast('/').replace('_', ' '), color = Nafs.colors.tasks, icon = Icons.Rounded.Public)
                    if (profile.deenEnabled) NafsPill("Faith mode", color = Nafs.colors.deen)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Surface(
            onClick = onEdit,
            enabled = !busy,
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Edit, null, Modifier.size(16.dp))
                Spacer(Modifier.size(8.dp))
                Text("Edit profile", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun EditProfileSheet(profile: UserProfile, state: ProfileUiState, onSave: (String, String) -> Unit, onDone: () -> Unit) {
    var name by rememberSaveable(profile.userId, profile.name) { mutableStateOf(profile.name) }
    var timezone by rememberSaveable(profile.userId, profile.timezone) { mutableStateOf(profile.timezone) }
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Edit profile", style = MaterialTheme.typography.headlineSmall)
        NafsTextField(
            value = name,
            onValueChange = { if (it.length <= 80) name = it },
            label = "Name",
            leadingIcon = Icons.Rounded.Badge,
            enabled = !state.busy,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
        NafsTextField(
            value = timezone,
            onValueChange = { timezone = it.trim() },
            label = "Timezone",
            leadingIcon = Icons.Rounded.Public,
            supportingText = "Region/City, for example Asia/Karachi or Europe/London",
            enabled = !state.busy,
        )
        if (timezone != state.deviceTimezone) {
            NafsChoiceChip("Use ${state.deviceTimezone}", selected = false, onClick = { timezone = state.deviceTimezone }, enabled = !state.busy)
        }
        state.editorMessage?.let { NafsBanner(it, kind = if (it.endsWith("saved.")) NafsBannerKind.SUCCESS else NafsBannerKind.ERROR) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = onDone, enabled = !state.busy, modifier = Modifier.weight(1f).height(54.dp)) { Text("Close") }
            NafsPrimaryButton("Save", onClick = { onSave(name, timezone) }, loading = state.busy, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun DeleteAccountDialog(state: ProfileUiState, onDismiss: () -> Unit, onDelete: (String) -> Unit) {
    var confirmation by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!state.busy) onDismiss() },
        icon = { Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Delete your account?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("This permanently removes your sign-in, every saved record and your uploaded photos. It can't be undone.")
                NafsTextField(confirmation, { confirmation = it }, label = "Type DELETE to confirm", enabled = !state.busy)
                state.editorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDelete(confirmation) }, enabled = confirmation == "DELETE" && !state.busy) {
                Text("Delete permanently", color = if (confirmation == "DELETE") MaterialTheme.colorScheme.error else Color.Unspecified)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.busy) { Text("Cancel") } },
    )
}
