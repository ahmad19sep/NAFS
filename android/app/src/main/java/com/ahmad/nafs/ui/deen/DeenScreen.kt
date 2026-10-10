package com.ahmad.nafs.ui.deen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmad.nafs.core.deen.DeenExtraRules
import com.ahmad.nafs.core.deen.Prayer
import com.ahmad.nafs.core.deen.PrayerLog
import com.ahmad.nafs.core.deen.PrayerStatus
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.ui.AccountDayRefreshEffects
import com.ahmad.nafs.ui.components.NafsBanner
import com.ahmad.nafs.ui.components.NafsBannerKind
import com.ahmad.nafs.ui.components.NafsCard
import com.ahmad.nafs.ui.components.NafsChoiceChip
import com.ahmad.nafs.ui.components.NafsDivider
import com.ahmad.nafs.ui.components.NafsEmojiBadge
import com.ahmad.nafs.ui.components.NafsEmptyState
import com.ahmad.nafs.ui.components.NafsEyebrow
import com.ahmad.nafs.ui.components.NafsHeroSurface
import com.ahmad.nafs.ui.components.NafsLoadingState
import com.ahmad.nafs.ui.components.NafsPageHeader
import com.ahmad.nafs.ui.components.NafsPrimaryButton
import com.ahmad.nafs.ui.components.NafsProgressBar
import com.ahmad.nafs.ui.components.NafsProgressRing
import com.ahmad.nafs.ui.components.NafsSectionTitle
import com.ahmad.nafs.ui.components.NafsSpace
import com.ahmad.nafs.ui.components.NafsStat
import com.ahmad.nafs.ui.components.NafsTextField
import com.ahmad.nafs.ui.theme.AmiriArabic
import com.ahmad.nafs.ui.theme.Nafs
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Composable
fun DeenRoute(profile: UserProfile, viewModel: DeenViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(profile.userId, profile.timezone, profile.deenEnabled) {
        if (profile.deenEnabled) viewModel.initialize(profile.userId, profile.timezone)
    }
    AccountDayRefreshEffects(profile.timezone) { if (profile.deenEnabled) viewModel.refresh() }
    DeenScreen(
        profile = profile,
        state = state,
        actions = DeenActions(
            onReload = { viewModel.refresh(force = true) },
            onSetPrayer = viewModel::setPrayer,
            onExtraNameChange = viewModel::setExtraName,
            onAddSuggestion = { name -> viewModel.addExtra(name) },
            onAddCustom = { viewModel.addExtra() },
            onRemoveExtra = viewModel::removeExtra,
            onSetExtra = viewModel::setExtra,
            onSave = viewModel::save,
        ),
    )
}

/** Everything the Deen screen can ask the ViewModel to do, so the screen stays a pure function of its state. */
internal data class DeenActions(
    val onReload: () -> Unit = {},
    val onSetPrayer: (Prayer, PrayerStatus) -> Unit = { _, _ -> },
    val onExtraNameChange: (String) -> Unit = {},
    val onAddSuggestion: (String) -> Unit = {},
    val onAddCustom: () -> Unit = {},
    val onRemoveExtra: (Int) -> Unit = {},
    val onSetExtra: (Int, String, JsonPrimitive) -> Unit = { _, _, _ -> },
    val onSave: () -> Unit = {},
)

@Composable
internal fun DeenScreen(profile: UserProfile, state: DeenUiState, actions: DeenActions) {
    if (profile.deenEnabled && state.isLoading && state.draft == null) {
        NafsLoadingState(Modifier.statusBarsPadding())
        return
    }
    val log = state.draft.takeIf { profile.deenEnabled }
    val canReload = !state.isLoading && !state.isSaving && (!state.hasChanges || state.hasConflict)

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = NafsSpace.screen, end = NafsSpace.screen, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                NafsPageHeader(
                    title = "Deen",
                    eyebrow = dayLabel(log?.date),
                    subtitle = "Your five prayers and extra worship today.",
                    actions = {
                        if (profile.deenEnabled) {
                            IconButton(onClick = actions.onReload, enabled = canReload) {
                                if (state.isLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Rounded.Refresh, contentDescription = "Reload today's prayers")
                            }
                        }
                    },
                )
            }
            if (profile.deenEnabled) {
                deenItems(state, log, profile.timezone, actions)
            } else {
                item {
                    NafsEmptyState(
                        icon = Icons.Rounded.NightsStay,
                        title = "Faith mode is off",
                        message = "Turn on faith mode in Profile to log prayers. Your saved history is kept.",
                        tint = Nafs.colors.deen,
                    )
                }
            }
        }
        if (log != null && (state.hasChanges || state.isSaving)) {
            SaveBar(
                log = log,
                saving = state.isSaving,
                enabled = state.canEdit && state.hasChanges,
                error = state.error,
                onSave = actions.onSave,
            )
        }
    }
}

private fun LazyListScope.deenItems(state: DeenUiState, log: PrayerLog?, timezone: String, actions: DeenActions) {
    if (state.isOffline) item {
        NafsBanner("Showing cached prayers. Reconnect and reload to make changes.", kind = NafsBannerKind.OFFLINE)
    }
    if (state.hasConflict) item {
        NafsBanner(
            "Your entries are still shown. Reload the latest record before making more changes.",
            kind = NafsBannerKind.WARNING,
            title = "This day changed on another device",
            actionLabel = "Reload",
            onAction = actions.onReload.takeIf { !state.isLoading },
        )
    }
    state.error?.let { message ->
        item {
            NafsBanner(
                message,
                kind = NafsBannerKind.ERROR,
                actionLabel = if (log == null) "Retry" else null,
                onAction = actions.onReload.takeIf { log == null },
            )
        }
    }
    state.message?.let { message -> item { NafsBanner(message, kind = NafsBannerKind.SUCCESS) } }
    if (log == null) return

    item { PrayerHero(log, timezone) }
    if (state.saved == null) item {
        DeenHint("No saved record for this day yet. Choose each prayer's status and save when ready.")
    }

    item { NafsSectionTitle("Daily prayers", supporting = "Alone earns 1 point · jamaat earns 2") }
    item(key = "prayers") { PrayersCard(log, state.canEdit, actions.onSetPrayer) }

    item {
        NafsSectionTitle(
            "Extra prayers & activities",
            supporting = if (log.extras.isEmpty()) "Sunnah, nafl and your own acts of worship." else "Each earns its own weight in points.",
        )
    }
    log.extras.forEachIndexed { index, extra ->
        item(key = "extra-$index") {
            ExtraCard(
                extra = extra,
                enabled = state.canEdit,
                onRemove = { actions.onRemoveExtra(index) },
                onSet = { field, value -> actions.onSetExtra(index, field, value) },
            )
        }
    }
    item(key = "add-extra") { AddExtraCard(log.extras, state.extraName, state.canEdit, actions) }
}

// ─── Hero ────────────────────────────────────────────────────────────────────

@Composable
private fun PrayerHero(log: PrayerLog, timezone: String) {
    val c = Nafs.colors
    val extras = log.extras.map(DeenExtraRules::points)
    val extraEarned = extras.sumOf { it.first }
    val extraPossible = extras.sumOf { it.second }
    NafsHeroSurface {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                NafsProgressRing(
                    progress = log.prayerPoints / 10f,
                    size = 104.dp,
                    stroke = 10.dp,
                    colors = listOf(c.heroMint, c.heroGold),
                    trackColor = Color.White.copy(alpha = 0.12f),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${log.prayerPoints}", style = MaterialTheme.typography.headlineMedium, color = c.onHero)
                        Text("of 10 pts", style = MaterialTheme.typography.labelSmall, color = c.onHeroMuted)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    NafsEyebrow("Today's salah", color = c.heroGold)
                    Text(heroHeadline(log), style = MaterialTheme.typography.titleLarge, color = c.onHero)
                    Text("Day follows $timezone", style = MaterialTheme.typography.labelMedium, color = c.onHeroMuted)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.10f)))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                HeroStat("${log.prayedCount}/5", "Prayed", log.prayedCount / 5f, c.heroMint, Modifier.weight(1f))
                HeroStat("${log.jamaatCount}/5", "In jamaat", log.jamaatCount / 5f, c.heroGold, Modifier.weight(1f))
                HeroStat(
                    value = if (extraPossible > 0) "$extraEarned/$extraPossible" else "0",
                    label = "Extra points",
                    progress = if (extraPossible > 0) extraEarned.toFloat() / extraPossible else 0f,
                    color = c.heroSky,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String, progress: Float, color: Color, modifier: Modifier) {
    NafsStat(
        value = value,
        label = label,
        modifier = modifier,
        progress = progress,
        color = color,
        valueColor = Nafs.colors.onHero,
        labelColor = Nafs.colors.onHeroMuted,
    )
}

private fun heroHeadline(log: PrayerLog): String = when {
    log.prayedCount == 0 -> "No prayers logged yet today."
    log.jamaatCount == 5 -> "All five in jamaat. Alhamdulillah."
    log.prayedCount == 5 -> "All five prayed. Alhamdulillah."
    else -> "${log.prayedCount} of 5 prayed so far."
}

// ─── Daily prayers ───────────────────────────────────────────────────────────

@Composable
private fun PrayersCard(log: PrayerLog, enabled: Boolean, onSet: (Prayer, PrayerStatus) -> Unit) {
    NafsCard(contentPadding = PaddingValues(vertical = 4.dp)) {
        Prayer.entries.forEachIndexed { index, prayer ->
            if (index > 0) NafsDivider()
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(prayer.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(prayer.arabic(), style = ArabicLabel, color = Nafs.colors.deen, maxLines = 1)
                }
                StatusSelector(log.status(prayer), enabled, onSelect = { onSet(prayer, it) })
            }
        }
    }
}

/** Compact three-way control: missed (error), alone (primary), jamaat (gold). */
@Composable
private fun StatusSelector(status: Int, enabled: Boolean, onSelect: (PrayerStatus) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val fill = if (Nafs.colors.isDark) 0.22f else 0.13f
    Row(
        modifier
            .alpha(if (enabled) 1f else 0.6f)
            .clip(CircleShape)
            .background(scheme.surfaceContainerHigh)
            .padding(3.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        PrayerStatus.entries.forEach { choice ->
            val selected = status == choice.value
            val tint = choice.tint()
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(if (selected) tint.copy(alpha = fill) else Color.Transparent)
                    .border(1.dp, if (selected) tint.copy(alpha = 0.5f) else Color.Transparent, CircleShape)
                    .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(choice) })
                    .padding(horizontal = 11.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    choice.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) tint else scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun PrayerStatus.tint(): Color = when (this) {
    PrayerStatus.MISSED -> MaterialTheme.colorScheme.error
    PrayerStatus.ALONE -> MaterialTheme.colorScheme.primary
    PrayerStatus.JAMAAT -> Nafs.colors.gold
}

private fun Prayer.arabic(): String = when (this) {
    Prayer.FAJR -> "الفجر"
    Prayer.DHUHR -> "الظهر"
    Prayer.ASR -> "العصر"
    Prayer.MAGHRIB -> "المغرب"
    Prayer.ISHA -> "العشاء"
}

private val ArabicLabel = TextStyle(fontFamily = AmiriArabic, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 22.sp)

// ─── Extras ──────────────────────────────────────────────────────────────────

@Composable
private fun ExtraCard(extra: JsonObject, enabled: Boolean, onRemove: () -> Unit, onSet: (String, JsonPrimitive) -> Unit) {
    val name = DeenExtraRules.name(extra)
    val type = DeenExtraRules.type(extra)
    val (earned, possible) = DeenExtraRules.points(extra)
    val scheme = MaterialTheme.colorScheme
    NafsCard(contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NafsEmojiBadge(extraEmoji(extra), Nafs.colors.deen, size = 40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    if (possible > 0) "$earned of $possible pts" else "Kept from the web app",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            if (type == "boolean") {
                Switch(
                    checked = DeenExtraRules.done(extra),
                    onCheckedChange = { onSet("done", JsonPrimitive(it)) },
                    enabled = enabled,
                    modifier = Modifier.semantics { contentDescription = "$name completed" },
                )
            }
            IconButton(onClick = onRemove, enabled = enabled) {
                Icon(Icons.Rounded.Close, contentDescription = "Remove $name")
            }
        }
        when (type) {
            "boolean" -> {}
            "count" -> {
                Spacer(Modifier.height(10.dp))
                CountStepper(
                    name = name,
                    value = DeenExtraRules.number(extra, "value"),
                    target = DeenExtraRules.number(extra, "target", 1.0),
                    enabled = enabled,
                    onChange = { onSet("value", JsonPrimitive(it)) },
                )
            }
            null -> {
                Spacer(Modifier.height(10.dp))
                StatusSelector(DeenExtraRules.status(extra), enabled, onSelect = { onSet("status", JsonPrimitive(it.value)) })
            }
            else -> {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Saved web activity. Its details are retained.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun CountStepper(name: String, value: Double, target: Double, enabled: Boolean, onChange: (Double) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StepButton(Icons.Rounded.Remove, "Decrease $name", enabled && value > 0) { onChange((value - 1).coerceAtLeast(0.0)) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${value.pretty()} / ${target.pretty()}", style = MaterialTheme.typography.titleMedium)
            NafsProgressBar(if (target > 0) (value / target).toFloat() else 0f, color = Nafs.colors.deen, height = 5.dp)
        }
        StepButton(Icons.Rounded.Add, "Increase $name", enabled && value < 1_000_000) { onChange(value + 1) }
    }
}

@Composable
private fun StepButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.size(44.dp).semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp).alpha(if (enabled) 1f else 0.38f))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddExtraCard(extras: List<JsonObject>, name: String, enabled: Boolean, actions: DeenActions) {
    val canAdd = enabled && name.isNotBlank()
    NafsCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Add to today", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Tap a suggestion or name your own.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DeenExtraRules.suggestions.keys.forEach { suggestion ->
                    val exists = extras.any { DeenExtraRules.name(it).equals(suggestion, ignoreCase = true) }
                    NafsChoiceChip(
                        text = suggestion,
                        selected = exists,
                        onClick = { actions.onAddSuggestion(suggestion) },
                        enabled = enabled && !exists,
                        leading = if (exists) "✓" else "+",
                    )
                }
            }
            NafsTextField(
                value = name,
                onValueChange = actions.onExtraNameChange,
                label = "Custom extra prayer",
                placeholder = "For example, Ishraq",
                enabled = enabled,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canAdd) actions.onAddCustom() }),
                trailingIcon = {
                    IconButton(onClick = actions.onAddCustom, enabled = canAdd) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add extra prayer")
                    }
                },
            )
        }
    }
}

// ─── Save bar and small pieces ───────────────────────────────────────────────

/** Pinned above the tab bar while there is something to save, so the action never scrolls away. */
@Composable
private fun SaveBar(log: PrayerLog, saving: Boolean, enabled: Boolean, error: String?, onSave: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column {
            NafsDivider(inset = 0.dp)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = NafsSpace.screen, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (saving) "Saving today's prayers…" else "Unsaved changes", style = MaterialTheme.typography.titleSmall)
                    Text(
                        error ?: "${log.prayedCount} of 5 prayed · ${log.prayerPoints}/10 points",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (error != null) scheme.error else scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                NafsPrimaryButton("Save prayers", onClick = onSave, enabled = enabled, loading = saving, icon = Icons.Rounded.Check)
            }
        }
    }
}

@Composable
private fun DeenHint(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Rounded.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 1.dp).size(15.dp),
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun extraEmoji(item: JsonObject): String =
    (item["emoji"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: "✨"

private fun Double.pretty(): String =
    if (isFinite() && this % 1.0 == 0.0 && abs(this) < 1e15) toLong().toString() else toString()

private fun dayLabel(date: String?): String =
    date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)) ?: "Today"
