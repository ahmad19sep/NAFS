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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.TouchApp
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmad.nafs.core.deen.DeenExtraRules
import com.ahmad.nafs.core.deen.DeenExtraSuggestion
import com.ahmad.nafs.core.deen.HijriDate
import com.ahmad.nafs.core.deen.Prayer
import com.ahmad.nafs.core.deen.PrayerHistoryCell
import com.ahmad.nafs.core.deen.PrayerHistorySummary
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
import com.ahmad.nafs.ui.components.NafsGroup
import com.ahmad.nafs.ui.components.NafsHeroSurface
import com.ahmad.nafs.ui.components.NafsIconBadge
import com.ahmad.nafs.ui.components.NafsListRow
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
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Composable
fun DeenRoute(
    profile: UserProfile,
    onOpenRoute: (String) -> Unit,
    viewModel: DeenViewModel = hiltViewModel(),
    clockViewModel: PrayerClockViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val clock by clockViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(profile.userId, profile.timezone, profile.deenEnabled) {
        if (profile.deenEnabled) {
            viewModel.initialize(profile.userId, profile.timezone)
            clockViewModel.initialize(profile.userId, profile.timezone)
        }
    }
    // Also on return from Prayer times, which may have saved new times.
    AccountDayRefreshEffects(profile.timezone) {
        if (profile.deenEnabled) {
            viewModel.refresh()
            clockViewModel.refresh()
        }
    }
    RefreshWhenPrayerBegins(clock.next, clockViewModel::refresh)
    val now = rememberAccountNow(profile.timezone)
    DeenScreen(
        profile = profile,
        state = state,
        actions = DeenActions(
            onReload = { viewModel.refresh(force = true) },
            onSelectDay = viewModel::selectDay,
            onSetPrayer = viewModel::setPrayer,
            onExtraNameChange = viewModel::setExtraName,
            onAddSuggestion = { name -> viewModel.addExtra(name) },
            onAddCustom = { viewModel.addExtra() },
            onRemoveExtra = viewModel::removeExtra,
            onSetExtra = viewModel::setExtra,
            onStepExtra = viewModel::stepExtra,
            onSave = viewModel::save,
            onRetryHistory = viewModel::retryHistory,
            onOpenPrayerTimes = { onOpenRoute("prayer-times") },
            onOpenQibla = { onOpenRoute("qibla") },
            onOpenTasbih = { onOpenRoute("tasbih") },
        ),
        clock = clock,
        now = now,
    )
}

/** Everything the Deen screen can ask the ViewModel to do, so the screen stays a pure function of its state. */
internal data class DeenActions(
    val onReload: () -> Unit = {},
    val onSelectDay: (DeenDay) -> Unit = {},
    val onSetPrayer: (Prayer, PrayerStatus) -> Unit = { _, _ -> },
    val onExtraNameChange: (String) -> Unit = {},
    val onAddSuggestion: (String) -> Unit = {},
    val onAddCustom: () -> Unit = {},
    val onRemoveExtra: (Int) -> Unit = {},
    val onSetExtra: (Int, String, JsonPrimitive) -> Unit = { _, _, _ -> },
    val onStepExtra: (Int, Int) -> Unit = { _, _ -> },
    val onSave: () -> Unit = {},
    val onRetryHistory: () -> Unit = {},
    val onOpenPrayerTimes: () -> Unit = {},
    val onOpenQibla: () -> Unit = {},
    val onOpenTasbih: () -> Unit = {},
)

/**
 * [clock] carries the next prayer and the Hijri adjustment; [now] (account time) drives the countdown. Without [now]
 * the next-prayer card is left out.
 */
@Composable
internal fun DeenScreen(
    profile: UserProfile,
    state: DeenUiState,
    actions: DeenActions,
    clock: PrayerClockState = PrayerClockState(),
    now: ZonedDateTime? = null,
) {
    if (profile.deenEnabled && state.isLoading && state.draft == null && state.today == null) {
        NafsLoadingState(Modifier.statusBarsPadding())
        return
    }
    val log = state.draft.takeIf { profile.deenEnabled }
    val canReload = !state.isLoading && !state.isSaving && (!state.hasChanges || state.hasConflict)
    val shownDate = state.selectedDate ?: log?.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = NafsSpace.screen, end = NafsSpace.screen, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                NafsPageHeader(
                    title = "Deen",
                    eyebrow = dayLabel(shownDate),
                    subtitle = if (profile.deenEnabled) null else "Your five prayers and extra worship.",
                    actions = {
                        if (profile.deenEnabled) {
                            IconButton(onClick = actions.onReload, enabled = canReload) {
                                if (state.isLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Rounded.Refresh, contentDescription = "Reload prayers")
                            }
                        }
                    },
                )
            }
            if (profile.deenEnabled) {
                item(key = "day") { DayHeader(state, shownDate, clock.hijriAdjustment, actions.onSelectDay) }
                deenItems(state, log, profile.timezone, actions, clock, now)
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
                day = state.day,
                saving = state.isSaving,
                enabled = state.canEdit && state.hasChanges,
                error = state.error,
                onSave = actions.onSave,
            )
        }
    }
}

private fun LazyListScope.deenItems(
    state: DeenUiState,
    log: PrayerLog?,
    timezone: String,
    actions: DeenActions,
    clock: PrayerClockState,
    now: ZonedDateTime?,
) {
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
    if (now != null && clock.next != null) item(key = "next-prayer") {
        NextPrayerSlot(clock.next, now, actions.onOpenPrayerTimes)
    }

    if (log == null) {
        if (state.isLoading) item(key = "loading") { DayLoadingCard() }
    } else {
        item { PrayerHero(log, timezone, state.day) }
        if (state.saved == null) item {
            DeenHint("No saved record for this day yet. Choose each prayer's status and save when ready.")
        }

        item { NafsSectionTitle("Daily prayers", supporting = "Alone earns 1 point · jamaat earns 2") }
        item(key = "prayers") { PrayersCard(log, state.canEdit, actions.onSetPrayer) }

        item {
            NafsSectionTitle(
                "Extra prayers & activities",
                supporting = if (log.extras.isEmpty()) "Sunnah, nafl, Quran, dhikr and your own acts of worship."
                else "Each earns its own weight in points.",
            )
        }
        log.extras.forEachIndexed { index, extra ->
            item(key = "extra-$index") {
                ExtraCard(
                    extra = extra,
                    enabled = state.canEdit,
                    onRemove = { actions.onRemoveExtra(index) },
                    onSet = { field, value -> actions.onSetExtra(index, field, value) },
                    onStep = { by -> actions.onStepExtra(index, by) },
                )
            }
        }
        item(key = "add-extra") { AddExtraCard(log.extras, state.extraName, state.canEdit, actions) }
    }

    historyItems(state.history, actions.onRetryHistory)

    item { NafsSectionTitle("Tools") }
    item(key = "tools") {
        NafsGroup {
            NafsListRow(
                title = "Prayer times",
                subtitle = "Today's five times for your area",
                icon = Icons.Rounded.AccessTime,
                tint = Nafs.colors.deen,
                onClick = actions.onOpenPrayerTimes,
            )
            NafsDivider()
            NafsListRow(
                title = "Qibla",
                subtitle = "Find the direction of the Kaaba",
                icon = Icons.Rounded.Explore,
                tint = Nafs.colors.deen,
                onClick = actions.onOpenQibla,
            )
            NafsDivider()
            NafsListRow(
                title = "Tasbih",
                subtitle = "Count dhikr after salah",
                icon = Icons.Rounded.TouchApp,
                tint = Nafs.colors.deen,
                onClick = actions.onOpenTasbih,
            )
        }
    }
}

// ─── Day and Hijri date ──────────────────────────────────────────────────────

@Composable
private fun DayHeader(state: DeenUiState, date: LocalDate?, hijriAdjustment: Int, onSelect: (DeenDay) -> Unit) {
    val hijri = remember(date, hijriAdjustment) { date?.let { runCatching { HijriDate.of(it, hijriAdjustment) }.getOrNull() } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        hijri?.let { HijriDateLine(it) }
        if (state.today != null) {
            DaySwitch(selected = state.day, enabled = state.canSwitchDay, onSelect = onSelect)
            if (state.hasChanges && !state.isSaving) {
                Text(
                    "Save your changes to switch days.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

/** Today / Yesterday, like NafsSegmented but with a disabled state while entries are unsaved. */
@Composable
private fun DaySwitch(selected: DeenDay, enabled: Boolean, onSelect: (DeenDay) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.6f)
            .clip(CircleShape)
            .background(scheme.surfaceContainerHigh)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(DeenDay.TODAY to "Today", DeenDay.YESTERDAY to "Yesterday").forEach { (day, label) ->
            val isSelected = selected == day
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .selectable(selected = isSelected, enabled = enabled, role = Role.Tab, onClick = { onSelect(day) }),
                shape = CircleShape,
                color = if (isSelected) scheme.surfaceContainerLowest else Color.Transparent,
                contentColor = if (isSelected) scheme.onSurface else scheme.onSurfaceVariant,
                shadowElevation = if (isSelected) 1.dp else 0.dp,
            ) {
                Text(
                    label,
                    Modifier.padding(vertical = 10.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun DayLoadingCard() {
    NafsCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator(Modifier.size(22.dp), color = Nafs.colors.deen, strokeWidth = 2.5.dp)
            Text("Loading this day's prayers…", style = MaterialTheme.typography.titleSmall)
        }
    }
}

// ─── Hero ────────────────────────────────────────────────────────────────────

@Composable
private fun PrayerHero(log: PrayerLog, timezone: String, day: DeenDay) {
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
                    NafsEyebrow(
                        when (day) {
                            DeenDay.TODAY -> "Today's salah"
                            DeenDay.YESTERDAY -> "Yesterday's salah"
                            DeenDay.EARLIER -> "Salah"
                        },
                        color = c.heroGold,
                    )
                    Text(heroHeadline(log, day), style = MaterialTheme.typography.titleLarge, color = c.onHero)
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

private fun heroHeadline(log: PrayerLog, day: DeenDay): String {
    val word = when (day) {
        DeenDay.TODAY -> " today"
        DeenDay.YESTERDAY -> " yesterday"
        DeenDay.EARLIER -> ""
    }
    return when {
        log.prayedCount == 0 -> "No prayers logged$word yet."
        log.jamaatCount == 5 -> "All five in jamaat. Alhamdulillah."
        log.prayedCount == 5 -> "All five prayed. Alhamdulillah."
        else -> "${log.prayedCount} of 5 prayed$word."
    }
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
                    Text(PrayerDisplay.arabic(prayer), style = ArabicLabel, color = Nafs.colors.deen, maxLines = 1)
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

private val ArabicLabel = TextStyle(fontFamily = AmiriArabic, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 22.sp)

// ─── Extras ──────────────────────────────────────────────────────────────────

@Composable
private fun ExtraCard(
    extra: JsonObject,
    enabled: Boolean,
    onRemove: () -> Unit,
    onSet: (String, JsonPrimitive) -> Unit,
    onStep: (Int) -> Unit,
) {
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
                    unit = DeenExtraRules.unit(extra),
                    enabled = enabled,
                    onStep = onStep,
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
private fun CountStepper(name: String, value: Double, target: Double, unit: String, enabled: Boolean, onStep: (Int) -> Unit) {
    val shownUnit = unit.takeIf { it.isNotBlank() && !it.equals("count", ignoreCase = true) }
    val reached = value >= target
    Row(
        Modifier.fillMaxWidth().padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StepButton(Icons.Rounded.Remove, "Decrease $name", enabled && value > 0) { onStep(-1) }
        Column(
            Modifier.weight(1f).semantics(mergeDescendants = true) {},
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                buildString {
                    append("${value.pretty()} / ${target.pretty()}")
                    shownUnit?.let { append(" $it") }
                    if (reached) append(" ✓")
                },
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            )
            NafsProgressBar(if (target > 0) (value / target).toFloat() else 0f, color = Nafs.colors.deen, height = 5.dp)
        }
        StepButton(Icons.Rounded.Add, "Increase $name", enabled && value < DeenExtraRules.MAX_COUNT) { onStep(1) }
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

@Composable
private fun AddExtraCard(extras: List<JsonObject>, name: String, enabled: Boolean, actions: DeenActions) {
    val canAdd = enabled && name.isNotBlank()
    val (counters, yesNo) = DeenExtraRules.catalog.partition { it.isCounter }
    NafsCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Add to this day", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Tap a suggestion or name your own.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SuggestionGroup("Prayers & good deeds", yesNo, extras, enabled, actions.onAddSuggestion)
            SuggestionGroup("Counters", counters, extras, enabled, actions.onAddSuggestion)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestionGroup(
    title: String,
    suggestions: List<DeenExtraSuggestion>,
    extras: List<JsonObject>,
    enabled: Boolean,
    onAdd: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NafsEyebrow(title)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEach { suggestion ->
                val exists = extras.any { DeenExtraRules.name(it).equals(suggestion.name, ignoreCase = true) }
                NafsChoiceChip(
                    text = suggestion.chipLabel(),
                    selected = exists,
                    onClick = { onAdd(suggestion.name) },
                    enabled = enabled && !exists,
                    leading = if (exists) "✓" else suggestion.emoji,
                )
            }
        }
    }
}

private fun DeenExtraSuggestion.chipLabel(): String {
    val goal = target ?: return name
    val shownUnit = unit?.takeIf { it.isNotBlank() && !it.equals("count", ignoreCase = true) }
    return if (shownUnit != null) "$name · $goal $shownUnit" else "$name · $goal"
}

// ─── History ─────────────────────────────────────────────────────────────────

private fun LazyListScope.historyItems(history: DeenHistoryState?, onRetry: () -> Unit) {
    if (history == null) return
    item { NafsSectionTitle("Consistency", supporting = "A day counts when all five are prayed, alone or in jamaat.") }
    when (history) {
        DeenHistoryState.Loading -> item(key = "history-loading") {
            NafsCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = Nafs.colors.deen, strokeWidth = 2.5.dp)
                    Text("Loading your prayer history…", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        is DeenHistoryState.Failed -> item(key = "history-error") {
            NafsBanner(history.message, kind = NafsBannerKind.ERROR, actionLabel = "Retry", onAction = onRetry)
        }
        is DeenHistoryState.Ready -> {
            item(key = "streak") { StreakCard(history.summary, history.isOffline) }
            item(key = "heatmap") { HeatmapCard(history.summary) }
            item(key = "by-prayer") { PrayerTallyCard(history.summary) }
        }
    }
}

@Composable
private fun StreakCard(summary: PrayerHistorySummary, offline: Boolean) {
    val c = Nafs.colors
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val streak = summary.currentStreak
    val streakText = if (summary.streakMayBeLonger) "$streak+" else "$streak"
    val lastDay = summary.cells.lastOrNull()
    val note = when {
        offline -> "Saved copy through ${shortDate(summary.through)}. Reconnect to update."
        streak == 0 && lastDay?.prayed == 5 -> "All five prayed. Your streak has started."
        streak == 0 -> "Pray all five in a day to start a streak."
        lastDay?.prayed == 5 -> "Today is complete. Keep it going tomorrow."
        else -> "Today joins the streak once all five are prayed."
    }
    NafsCard(Modifier.semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            NafsIconBadge(Icons.Rounded.LocalFireDepartment, c.gold, size = 52.dp, iconSize = 28.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                NafsEyebrow("Prayer streak", color = c.gold)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(streakText, style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"), color = c.gold)
                    Text(days(streak), style = MaterialTheme.typography.titleSmall, color = muted, modifier = Modifier.padding(bottom = 5.dp))
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Longest", style = MaterialTheme.typography.labelMedium, color = muted)
                Text("${summary.longestStreak} ${days(summary.longestStreak)}", style = MaterialTheme.typography.titleMedium)
                Text("in the past year", style = MaterialTheme.typography.labelSmall, color = muted)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(note, style = MaterialTheme.typography.bodySmall, color = muted)
    }
}

@Composable
private fun HeatmapCard(summary: PrayerHistorySummary) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val cells = summary.cells
    val partial = cells.count { (it.prayed ?: 0) in 1..4 }
    val unrecorded = cells.count { it.prayed == null }
    val description = "Last ${cells.size} days: ${summary.completeDays} with all five prayed, $partial partly prayed, " +
        "${cells.size - summary.completeDays - partial - unrecorded} with none prayed and $unrecorded without a record."
    NafsCard(Modifier.clearAndSetSemantics { contentDescription = description }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Last ${cells.size} days", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("${summary.completeDays} complete", style = MaterialTheme.typography.labelMedium, color = muted)
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            cells.chunked(10).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { cell -> HeatCell(cell, highlight = cell.date == summary.through, modifier = Modifier.weight(1f)) }
                    repeat(10 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row {
            Text(cells.firstOrNull()?.let { shortDate(it.date) }.orEmpty(), style = MaterialTheme.typography.labelSmall, color = muted,
                modifier = Modifier.weight(1f))
            Text(shortDate(summary.through), style = MaterialTheme.typography.labelSmall, color = muted)
        }
        Spacer(Modifier.height(12.dp))
        HeatLegend()
    }
}

@Composable
private fun HeatCell(cell: PrayerHistoryCell, highlight: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(6.dp)
    val prayed = cell.prayed
    val fill = if (prayed == null) Color.Transparent else when {
        prayed == 5 && cell.jamaat == 5 -> Nafs.colors.gold
        prayed == 0 -> scheme.surfaceContainerHigh
        else -> heatColor(prayed)
    }
    val border = when {
        highlight -> scheme.onSurface.copy(alpha = 0.7f)
        prayed == null -> scheme.outlineVariant
        else -> Color.Transparent
    }
    Box(modifier.aspectRatio(1f).clip(shape).background(fill).border(if (highlight) 2.dp else 1.dp, border, shape))
}

@Composable
private fun heatColor(prayed: Int): Color = Nafs.colors.deen.copy(alpha = 0.22f + 0.78f * (prayed.coerceIn(1, 5) - 1) / 4f)

@Composable
private fun HeatLegend() {
    val scheme = MaterialTheme.colorScheme
    val muted = scheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        LegendSwatch(Color.Transparent, scheme.outlineVariant)
        Text("No record", style = MaterialTheme.typography.labelSmall, color = muted, modifier = Modifier.padding(end = 8.dp))
        LegendSwatch(scheme.surfaceContainerHigh)
        (1..5).forEach { LegendSwatch(heatColor(it)) }
        Text("0–5 prayed", style = MaterialTheme.typography.labelSmall, color = muted, modifier = Modifier.padding(end = 8.dp))
        LegendSwatch(Nafs.colors.gold)
        Text("Jamaat", style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1)
    }
}

@Composable
private fun LegendSwatch(fill: Color, border: Color = Color.Transparent) {
    val shape = RoundedCornerShape(3.dp)
    Box(Modifier.size(10.dp).clip(shape).background(fill).border(1.dp, border, shape))
}

@Composable
private fun PrayerTallyCard(summary: PrayerHistorySummary) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    NafsCard {
        Text("Prayer by prayer", style = MaterialTheme.typography.titleSmall)
        Text("Days each prayer was prayed in the last ${summary.cells.size}", style = MaterialTheme.typography.bodySmall, color = muted)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            summary.tallies.forEach { tally ->
                Row(
                    Modifier.clearAndSetSemantics { contentDescription = "${tally.prayer.label}: ${tally.prayed} of ${tally.days} days" },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(tally.prayer.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(68.dp))
                    NafsProgressBar(
                        if (tally.days > 0) tally.prayed / tally.days.toFloat() else 0f,
                        color = Nafs.colors.deen,
                        height = 6.dp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${tally.prayed}/${tally.days}",
                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(48.dp),
                    )
                }
            }
        }
    }
}

private fun days(count: Int) = if (count == 1) "day" else "days"

// ─── Save bar and small pieces ───────────────────────────────────────────────

/** Pinned above the tab bar while there is something to save, so the action never scrolls away. */
@Composable
private fun SaveBar(log: PrayerLog, day: DeenDay, saving: Boolean, enabled: Boolean, error: String?, onSave: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val which = when (day) {
        DeenDay.TODAY -> "today's"
        DeenDay.YESTERDAY -> "yesterday's"
        DeenDay.EARLIER -> "this day's"
    }
    Surface(color = scheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column {
            NafsDivider(inset = 0.dp)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = NafsSpace.screen, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (saving) "Saving $which prayers…" else "Unsaved changes", style = MaterialTheme.typography.titleSmall)
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

private fun dayLabel(date: LocalDate?): String =
    date?.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)) ?: "Today"

private fun shortDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
