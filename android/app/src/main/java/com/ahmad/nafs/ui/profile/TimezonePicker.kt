package com.ahmad.nafs.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ahmad.nafs.core.profile.TimezoneCatalog
import com.ahmad.nafs.core.profile.TimezoneOption
import com.ahmad.nafs.ui.components.NafsCard
import com.ahmad.nafs.ui.components.NafsDivider
import com.ahmad.nafs.ui.components.NafsIconBadge
import com.ahmad.nafs.ui.components.NafsTextField
import java.time.Instant

/** The picker in its own sheet, for the Daily timezone row: choosing a zone saves it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimezonePickerSheet(
    currentZone: String,
    deviceZone: String,
    use24Hour: Boolean,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        TimezonePickerPane(
            currentZone = currentZone,
            deviceZone = deviceZone,
            use24Hour = use24Hour,
            onSelect = onSelect,
            onClose = onDismiss,
            modifier = Modifier.fillMaxHeight().navigationBarsPadding().imePadding(),
        )
    }
}

/** Holds the search text; the zone list is described at the moment the picker opened. */
@Composable
internal fun TimezonePickerPane(
    currentZone: String,
    deviceZone: String,
    use24Hour: Boolean,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val now = remember { Instant.now() }
    val options = remember(currentZone, deviceZone, use24Hour) {
        TimezoneCatalog.options(now, use24Hour, alwaysInclude = listOf(currentZone, deviceZone))
    }
    val device = remember(deviceZone, use24Hour) { TimezoneCatalog.option(deviceZone, now, use24Hour) }
    TimezonePicker(query, { query = it }, options, currentZone, device, onSelect, onClose, modifier, onBack)
}

/**
 * Searchable list of Region/City zones with each one's UTC offset and local time, and a shortcut to this phone's
 * zone at the top. Stateless, so it can be rendered with a fixed list and search.
 */
@Composable
internal fun TimezonePicker(
    query: String,
    onQueryChange: (String) -> Unit,
    options: List<TimezoneOption>,
    currentZone: String,
    device: TimezoneOption?,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    val results = remember(options, query) { TimezoneCatalog.search(options, query) }
    val current = remember(options, currentZone) { options.firstOrNull { it.id == currentZone } }

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = if (onBack == null) 20.dp else 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to Edit profile") }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Daily timezone", style = MaterialTheme.typography.headlineSmall)
                Text(
                    current?.let { "Now ${it.shortLabel}. Your day, streaks and reminders turn over at midnight here." }
                        ?: "Your day, streaks and reminders turn over at midnight here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            if (onBack == null) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Close") }
            }
        }
        NafsTextField(
            value = query,
            onValueChange = { onQueryChange(it.take(60)) },
            label = "Search city or region",
            placeholder = "Lahore, London, Asia…",
            leadingIcon = Icons.Rounded.Search,
            trailingIcon = if (query.isEmpty()) null else {
                { IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Rounded.Close, contentDescription = "Clear search") } }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            device?.let { phone ->
                item(key = "device") {
                    DeviceShortcut(phone, selected = phone.id == currentZone, onSelect = onSelect)
                }
            }
            if (results.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "No timezone matches “${query.trim()}”. Try a city, such as Lahore, or a region, such as Europe.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 28.dp),
                    )
                }
            } else {
                item(key = "count") {
                    Text(
                        if (query.isBlank()) "All timezones, west to east" else "${results.size} ${if (results.size == 1) "match" else "matches"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(results, key = { it.id }) { option ->
                    ZoneRow(option, selected = option.id == currentZone, onSelect = onSelect)
                    NafsDivider(inset = 20.dp)
                }
            }
        }
    }
}

@Composable
private fun DeviceShortcut(device: TimezoneOption, selected: Boolean, onSelect: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    NafsCard(
        onClick = { onSelect(device.id) },
        modifier = Modifier.padding(horizontal = 20.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NafsIconBadge(Icons.Rounded.PhoneAndroid, scheme.primary, size = 38.dp, iconSize = 20.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Use this phone's timezone (${device.city})", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${device.offsetLabel} · ${device.localTime} there now" + if (selected) " · in use" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            if (selected) Icon(Icons.Rounded.CheckCircle, contentDescription = "In use", tint = scheme.primary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ZoneRow(option: TimezoneOption, selected: Boolean, onSelect: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(option.id) })
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(option.city, style = MaterialTheme.typography.titleSmall, color = if (selected) scheme.primary else scheme.onSurface)
            Text(
                listOf(option.region, option.offsetLabel).filter(String::isNotEmpty).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
        Text(option.localTime, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
        if (selected) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = "Selected", tint = scheme.primary, modifier = Modifier.size(20.dp))
        } else {
            Spacer(Modifier.size(20.dp))
        }
    }
}
