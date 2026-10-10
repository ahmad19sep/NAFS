package com.ahmad.nafs.ui.deen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmad.nafs.core.deen.DeenExtraRules
import com.ahmad.nafs.core.deen.Prayer
import com.ahmad.nafs.core.deen.PrayerStatus
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.ui.AccountDayRefreshEffects
import com.ahmad.nafs.ui.components.NafsHeroCard
import com.ahmad.nafs.ui.components.NafsSectionTitle
import kotlinx.serialization.json.JsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeenRoute(profile: UserProfile, viewModel: DeenViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(profile.userId, profile.timezone, profile.deenEnabled) {
        if (profile.deenEnabled) viewModel.initialize(profile.userId, profile.timezone)
    }
    AccountDayRefreshEffects(profile.timezone) { if (profile.deenEnabled) viewModel.refresh() }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Deen") }, actions = {
            if (profile.deenEnabled) TextButton(
                onClick = { viewModel.refresh(force = true) },
                enabled = !state.isLoading && !state.isSaving && (!state.hasChanges || state.hasConflict),
            ) { Text("Reload") }
        })
    }) { padding ->
        if (!profile.deenEnabled) {
            Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
                Text("Faith mode is off", style = MaterialTheme.typography.headlineSmall)
                Text("Enable faith mode in You to log prayers. Your saved history is retained.")
            }
        } else if (state.isLoading && state.draft == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (state.isOffline) item {
                    Text("Showing cached prayers. Reconnect and reload to make changes.", color = MaterialTheme.colorScheme.secondary)
                }
                state.error?.let { message -> item {
                    Text(message, color = MaterialTheme.colorScheme.error)
                    if (state.draft == null) TextButton(onClick = { viewModel.refresh(force = true) }) { Text("Retry") }
                } }
                state.message?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.primary) } }
                if (state.hasConflict) item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("This day changed on another device.", style = MaterialTheme.typography.titleMedium)
                            Text("Your entries are still shown. Reload the latest record before making more changes.")
                            Button(onClick = { viewModel.refresh(force = true) }, enabled = !state.isLoading) { Text("Reload latest record") }
                        }
                    }
                }
                state.draft?.let { log ->
                    item {
                        NafsHeroCard(
                            eyebrow = "${log.date} · ${profile.timezone}", title = "${log.prayedCount} of 5 prayers logged",
                            body = "${log.jamaatCount} in jamaat · ${log.prayerPoints}/10 prayer points",
                        )
                    }
                    if (state.saved == null) item {
                        Text("No saved record for this day yet. Choose each prayer's status and save when ready.", style = MaterialTheme.typography.bodySmall)
                    }
                    item { NafsSectionTitle("Daily prayers", supporting = "Alone earns 1 point; jamaat earns 2.") }
                    Prayer.entries.forEach { prayer -> item(key = prayer.name) {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Text(prayer.label, style = MaterialTheme.typography.titleMedium)
                                StatusChoices(log.status(prayer), state.canEdit) { viewModel.setPrayer(prayer, it) }
                            }
                        }
                    } }
                    item { NafsSectionTitle("Extra prayers & activities") }
                    log.extras.forEachIndexed { index, extra -> item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(DeenExtraRules.name(extra), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                    TextButton(onClick = { viewModel.removeExtra(index) }, enabled = state.canEdit) { Text("Remove") }
                                }
                                when (DeenExtraRules.type(extra)) {
                                    "boolean" -> Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Completed", Modifier.weight(1f))
                                        Switch(checked = DeenExtraRules.done(extra), onCheckedChange = { viewModel.setExtra(index, "done", JsonPrimitive(it)) }, enabled = state.canEdit)
                                    }
                                    "count" -> {
                                        val value = DeenExtraRules.number(extra, "value")
                                        val target = DeenExtraRules.number(extra, "target", 1.0)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            TextButton(onClick = { viewModel.setExtra(index, "value", JsonPrimitive((value - 1).coerceAtLeast(0.0))) }, enabled = state.canEdit && value > 0) { Text("−1") }
                                            Text("$value / $target", Modifier.weight(1f))
                                            TextButton(onClick = { viewModel.setExtra(index, "value", JsonPrimitive(value + 1)) }, enabled = state.canEdit && value < 1_000_000) { Text("+1") }
                                        }
                                    }
                                    null -> StatusChoices(DeenExtraRules.status(extra), state.canEdit) { viewModel.setExtra(index, "status", JsonPrimitive(it.value)) }
                                    else -> Text("Saved web activity. Its details are retained.", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    } }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DeenExtraRules.suggestions.keys.forEach { name ->
                                    val exists = log.extras.any { DeenExtraRules.name(it).equals(name, ignoreCase = true) }
                                    FilterChip(selected = exists, enabled = state.canEdit && !exists, onClick = { viewModel.addExtra(name) }, label = { Text(name) })
                                }
                            }
                            OutlinedTextField(value = state.extraName, onValueChange = viewModel::setExtraName, label = { Text("Custom extra prayer") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = state.canEdit)
                            TextButton(onClick = { viewModel.addExtra() }, enabled = state.canEdit && state.extraName.isNotBlank()) { Text("Add extra prayer") }
                        }
                    }
                    item {
                        Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth(), enabled = state.canEdit && state.hasChanges) {
                            Text(if (state.isSaving) "Saving…" else "Save today's prayers")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChoices(status: Int, enabled: Boolean, onSelect: (PrayerStatus) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PrayerStatus.entries.forEach { choice -> FilterChip(selected = status == choice.value, enabled = enabled, onClick = { onSelect(choice) }, label = { Text(choice.label) }) }
    }
}
