package com.ahmad.nafs.ui.records

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.core.records.FieldKind
import com.ahmad.nafs.core.records.RecordFeature
import com.ahmad.nafs.core.records.RecordField
import com.ahmad.nafs.core.records.RecordRules
import com.ahmad.nafs.ui.AccountDayRefreshEffects
import com.ahmad.nafs.ui.components.NafsBanner
import com.ahmad.nafs.ui.components.NafsBannerKind
import com.ahmad.nafs.ui.components.NafsCard
import com.ahmad.nafs.ui.components.NafsChoiceChip
import com.ahmad.nafs.ui.components.NafsEmptyState
import com.ahmad.nafs.ui.components.NafsIconBadge
import com.ahmad.nafs.ui.components.NafsPill
import com.ahmad.nafs.ui.components.NafsPrimaryButton
import com.ahmad.nafs.ui.components.NafsProgressBar
import com.ahmad.nafs.ui.components.NafsSecondaryButton
import com.ahmad.nafs.ui.components.NafsSectionTitle
import com.ahmad.nafs.ui.components.NafsSpace
import com.ahmad.nafs.ui.components.NafsTextField
import com.ahmad.nafs.ui.components.NafsTopBar
import com.ahmad.nafs.ui.components.PublicPhoto
import com.ahmad.nafs.ui.reports.ReportMarkdown
import com.ahmad.nafs.ui.theme.Nafs
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Everything a journal screen can do, so the screen itself stays a pure function of its state. */
internal data class RecordActions(
    val onBack: () -> Unit = {},
    val onReload: () -> Unit = {},
    val onEdit: (JsonObject?) -> Unit = {},
    val onChange: (String, String) -> Unit = { _, _ -> },
    val onSave: () -> Unit = {},
    val onDiscard: () -> Unit = {},
    val onDiscardPending: () -> Unit = {},
    val onAttachPhoto: () -> Unit = {},
    val onAnalyze: () -> Unit = {},
    val onLoadMore: () -> Unit = {},
)

@Composable
fun RecordRoute(profile: UserProfile, feature: RecordFeature, onBack: () -> Unit, viewModel: RecordViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(profile.userId, profile.timezone, feature) { viewModel.initialize(profile.userId, profile.timezone, feature) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flushDraft() }
    AccountDayRefreshEffects(profile.timezone, viewModel::reload)
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(viewModel::addPhoto) }
    RecordScreen(
        profile = profile,
        feature = feature,
        state = state,
        actions = RecordActions(
            onBack = onBack,
            onReload = viewModel::reload,
            onEdit = viewModel::edit,
            onChange = viewModel::change,
            onSave = viewModel::save,
            onDiscard = viewModel::closeEditor,
            onDiscardPending = viewModel::discardPending,
            onAttachPhoto = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onAnalyze = viewModel::analyzeScreen,
            onLoadMore = viewModel::loadMore,
        ),
    )
}

/**
 * One screen for every journal (dreams, daily log, Quran, screen time, bugs and ideas). The form and the saved
 * cards are generated from [RecordFeature.fields], so a new field shows up in both without screen changes.
 */
@Composable
internal fun RecordScreen(
    profile: UserProfile,
    feature: RecordFeature,
    state: RecordUiState,
    actions: RecordActions,
    listState: LazyListState = rememberLazyListState(),
) {
    val look = featureLook(feature)
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        NafsTopBar(
            title = feature.title,
            onBack = actions.onBack,
            subtitle = recordSubtitle(feature, profile.timezone),
            actions = {
                IconButton(onClick = actions.onReload, enabled = !state.busy) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
        // A fixed slot, so the list doesn't jump when work starts or ends.
        Box(Modifier.fillMaxWidth().height(4.dp).padding(horizontal = NafsSpace.screen)) {
            if (state.loading || state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = NafsSpace.screen, end = NafsSpace.screen, top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(NafsSpace.item),
        ) {
            if (state.offline) item(key = "offline") {
                NafsBanner(
                    "Offline · showing saved records and your draft.",
                    kind = NafsBannerKind.OFFLINE,
                    actionLabel = "Retry",
                    onAction = actions.onReload.takeIf { !state.busy },
                )
            }
            state.error?.let { error -> item(key = "error") { NafsBanner(error, kind = NafsBannerKind.ERROR) } }
            state.message?.let { message ->
                item(key = "message") {
                    if (isStatusMessage(message)) NafsBanner(message, kind = NafsBannerKind.SUCCESS)
                    else AnalysisCard(message, if (feature == RecordFeature.SCREEN) "Screen-time analysis" else "Update")
                }
            }
            if (state.editing) {
                if (state.pending) item(key = "pending") {
                    PendingNotice(canKeepDraft = !state.offline && !state.busy, onKeepDraft = actions.onDiscardPending)
                }
                item(key = "form") { RecordForm(profile, feature, state, actions, look) }
            } else {
                item(key = "intro") { RecordIntro(feature, state, actions, look) }
                if (!state.loading && state.rows.isEmpty()) item(key = "empty") {
                    NafsEmptyState(look.icon, "No saved entries yet", "Add your first entry above.", tint = look.tint)
                }
                if (state.rows.isNotEmpty()) item(key = "saved-title") {
                    NafsSectionTitle("Saved entries", Modifier.padding(top = 16.dp), supporting = savedSupporting(feature, state.rows.size))
                }
                state.rows.forEach { row ->
                    item(key = "row-${RecordRules.text(row, "id")}") { RecordCard(feature, row, state.busy, actions.onEdit, look) }
                }
            }
            if (state.hasMore && !state.editing) item(key = "more") {
                NafsSecondaryButton("Load earlier records", onClick = actions.onLoadMore, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

// ─── Not editing: intro and saved entries ────────────────────────────────────

@Composable
private fun RecordIntro(feature: RecordFeature, state: RecordUiState, actions: RecordActions, look: FeatureLook) {
    NafsCard(contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            NafsIconBadge(look.icon, look.tint, size = 48.dp, iconSize = 24.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(look.headline, style = MaterialTheme.typography.titleMedium)
                Text(look.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(16.dp))
        NafsPrimaryButton(
            text = when (feature) {
                RecordFeature.NOTE -> "New note"
                RecordFeature.DREAM -> "Edit dream"
                else -> "Log today"
            },
            onClick = { actions.onEdit(null) },
            enabled = !state.busy && !state.loading,
            icon = if (feature == RecordFeature.NOTE) Icons.Rounded.Add else Icons.Rounded.Edit,
            modifier = Modifier.fillMaxWidth(),
        )
        if (feature == RecordFeature.SCREEN && state.rows.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            NafsSecondaryButton(
                "Analyze today's saved screen time",
                onClick = actions.onAnalyze,
                enabled = !state.busy && !state.offline,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RecordCard(feature: RecordFeature, row: JsonObject, busy: Boolean, onEdit: (JsonObject?) -> Unit, look: FeatureLook) {
    val scheme = MaterialTheme.colorScheme
    val headerKey = when {
        feature.daily -> "date"
        feature == RecordFeature.DREAM -> "statement"
        else -> "title"
    }
    val header = RecordRules.text(row, headerKey)
    val hidden = if (feature == RecordFeature.NOTE) setOf(headerKey, "kind", "status") else setOf(headerKey)
    val shown = feature.fields.filter { it.key !in hidden && RecordRules.text(row, it.key).isNotBlank() }
    val figures = shown.filter { it.isFigure() }.map { field -> displayValue(field, RecordRules.text(row, field.key)) to shortLabel(field) } +
        if (feature == RecordFeature.LOG) logExtras(row) else emptyList<Pair<String, String>>()
    val passages = shown.filterNot { it.isFigure() }
    val added = if (feature.daily) null else prettyDate(RecordRules.text(row, "created_at").take(10), fullDate).takeIf { it.isNotBlank() }
    NafsCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (feature.daily) DateBadge(header, look.tint) else NafsIconBadge(look.icon, look.tint, size = 44.dp, iconSize = 22.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (feature.daily) prettyDate(header, longDate) else header.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.titleMedium,
                )
                added?.let { Text("Added $it", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant) }
                if (feature == RecordFeature.NOTE) NoteLabels(RecordRules.text(row, "kind"), RecordRules.text(row, "status"))
            }
        }
        if (figures.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            FigureGrid(figures)
        }
        passages.forEach { field ->
            Spacer(Modifier.height(14.dp))
            Text(shortLabel(field), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(RecordRules.text(row, field.key), style = MaterialTheme.typography.bodyMedium)
        }
        if (feature == RecordFeature.LOG) {
            val photos = (row["photo_urls"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.filter { it.isNotBlank() }
            if (photos.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    photos.forEachIndexed { index, url -> PhotoThumb(url, "Log photo ${index + 1}", 104.dp) }
                }
            }
        }
        if (feature == RecordFeature.SCREEN) {
            val shot = RecordRules.text(row, "screenshot_url")
            if (shot.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier.fillMaxWidth().height(220.dp).clip(MaterialTheme.shapes.medium).background(scheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) { PublicPhoto(shot, "Screen-time screenshot", Modifier.fillMaxSize()) }
            }
            val apps = (row["apps"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            if (apps.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                AppList(apps, RecordRules.text(row, "total_mins").toDoubleOrNull(), look.tint)
            }
            RecordRules.text(row, "ai_summary").takeIf { it.isNotBlank() }?.let { summary ->
                Spacer(Modifier.height(14.dp))
                SummaryWell(summary)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { onEdit(row) }, enabled = !busy) {
                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Open / edit")
            }
        }
    }
}

@Composable
private fun NoteLabels(kind: String, status: String) {
    val scheme = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (kind.isNotBlank()) {
            NafsPill(
                capitalized(kind),
                color = if (kind == "bug") Nafs.colors.health else Nafs.colors.reflect,
                icon = if (kind == "bug") Icons.Rounded.BugReport else Icons.Rounded.Lightbulb,
            )
        }
        if (status.isNotBlank()) {
            NafsPill(
                capitalized(status),
                color = when (status) {
                    "done" -> Nafs.colors.success
                    "dismissed" -> scheme.onSurfaceVariant
                    else -> scheme.primary
                },
            )
        }
    }
}

@Composable
private fun DateBadge(date: String, tint: Color) {
    val parsed = remember(date) { runCatching { LocalDate.parse(date) }.getOrNull() }
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(tint.copy(alpha = if (Nafs.colors.isDark) 0.16f else 0.12f))
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        if (parsed == null) {
            Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${parsed.dayOfMonth}", style = MaterialTheme.typography.titleMedium, color = tint)
                Text(parsed.format(monthFormat).uppercase(Locale.ENGLISH), style = MaterialTheme.typography.labelSmall, color = tint)
            }
        }
    }
}

/** Short values in tonal wells, three to a row. */
@Composable
private fun FigureGrid(figures: List<Pair<String, String>>) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        figures.chunked(3).forEach { chunk ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chunk.forEach { (value, label) ->
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(MaterialTheme.shapes.small)
                            .background(scheme.surfaceContainerHigh)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .semantics(mergeDescendants = true) {},
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(label, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 2)
                    }
                }
                repeat(3 - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun AppList(apps: List<JsonObject>, total: Double?, tint: Color) {
    val minutes = apps.map { RecordRules.text(it, "minutes").toDoubleOrNull() ?: 0.0 }
    val scale = maxOf(total ?: 0.0, minutes.maxOrNull() ?: 0.0)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Apps", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        apps.forEachIndexed { index, app ->
            Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(RecordRules.text(app, "app"), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${RecordRules.text(app, "minutes")} minutes", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                NafsProgressBar(if (scale > 0.0) (minutes[index] / scale).toFloat() else 0f, color = tint, height = 5.dp)
            }
        }
    }
}

@Composable
private fun SummaryWell(summary: String) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Nafs.colors.coach, modifier = Modifier.size(16.dp))
                Text("Analysis", style = MaterialTheme.typography.labelLarge)
            }
            ReportMarkdown(summary)
        }
    }
}

@Composable
private fun AnalysisCard(message: String, title: String) {
    NafsCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NafsIconBadge(Icons.Rounded.AutoAwesome, Nafs.colors.coach, size = 38.dp, iconSize = 20.dp)
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(10.dp))
        ReportMarkdown(message)
    }
}

// ─── Editing: the generated form ─────────────────────────────────────────────

@Composable
private fun PendingNotice(canKeepDraft: Boolean, onKeepDraft: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NafsBanner(
            "This save is awaiting confirmation. Retry preserves the original request.",
            kind = NafsBannerKind.WARNING,
            title = "Waiting for confirmation",
        )
        NafsSecondaryButton("Keep draft and edit after reload", onClick = onKeepDraft, enabled = canKeepDraft, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun RecordForm(profile: UserProfile, feature: RecordFeature, state: RecordUiState, actions: RecordActions, look: FeatureLook) {
    val scheme = MaterialTheme.colorScheme
    val editable = !state.busy && !state.pending
    var picking by remember { mutableStateOf<RecordField?>(null) }
    NafsCard(contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NafsIconBadge(look.icon, look.tint, size = 40.dp, iconSize = 20.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(formTitle(feature, state.date), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (feature.daily) "Entry date: ${state.date} · ${profile.timezone}" else "Saved to your account when you tap Save",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            if (state.pending) NafsPill("Queued", color = Nafs.colors.warning, icon = Icons.Rounded.Schedule)
            else NafsPill("Draft", color = scheme.onSurfaceVariant, icon = Icons.Rounded.Edit)
        }
        feature.fields.forEach { field ->
            Spacer(Modifier.height(16.dp))
            FieldInput(
                field = field,
                value = state.values[field.key].orEmpty(),
                enabled = editable,
                onChange = { value -> actions.onChange(field.key, value) },
                onPick = { picking = field },
            )
        }
        if (feature == RecordFeature.LOG || feature == RecordFeature.SCREEN) {
            Spacer(Modifier.height(20.dp))
            PhotoSection(feature, state.values, editable, actions.onAttachPhoto)
        }
        Spacer(Modifier.height(22.dp))
        NafsPrimaryButton(
            if (state.pending) "Retry exact save" else "Save entry",
            onClick = actions.onSave,
            enabled = !state.busy,
            icon = if (state.pending) Icons.Rounded.Refresh else Icons.Rounded.Check,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = actions.onDiscard, enabled = editable, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("Discard draft")
        }
        Text(
            "Your draft stays on this device until you save or discard it.",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
    val pickingField = picking
    if (pickingField != null) {
        val current = state.values[pickingField.key].orEmpty()
        val onPicked: (String) -> Unit = { value -> actions.onChange(pickingField.key, value) }
        when (pickingField.kind) {
            FieldKind.DATE -> DateDialog(current, onDismiss = { picking = null }, onPick = onPicked)
            FieldKind.TIME -> TimeDialog(shortLabel(pickingField), current, defaultHour = if (pickingField.key == "sleep_end") 6 else 22,
                onDismiss = { picking = null }, onPick = onPicked)
            else -> {}
        }
    }
}

@Composable
private fun FieldInput(field: RecordField, value: String, enabled: Boolean, onChange: (String) -> Unit, onPick: () -> Unit) {
    val (title, hint) = splitLabel(field.label)
    when (field.key) {
        "kind" -> ChoiceField(title, listOf("bug" to "Bug", "idea" to "Idea"), value, enabled, onChange)
        "status" -> ChoiceField(title, listOf("open" to "Open", "done" to "Done", "dismissed" to "Dismissed"), value, enabled, onChange)
        "mood" -> MoodField(title, value, enabled, onChange)
        else -> {
            val multiline = field.kind == FieldKind.APPS || field.key in longTextKeys || (field.kind == FieldKind.TEXT && field.max > 200)
            val keyboardType = when (field.kind) {
                FieldKind.NUMBER -> if (field.key in wholeNumberKeys) KeyboardType.Number else KeyboardType.Decimal
                else -> KeyboardType.Text
            }
            val writing = field.kind == FieldKind.TEXT || field.kind == FieldKind.APPS
            val pickerIcon: (@Composable () -> Unit)? = if (field.kind == FieldKind.DATE || field.kind == FieldKind.TIME) {
                {
                    IconButton(onClick = onPick, enabled = enabled) {
                        Icon(
                            if (field.kind == FieldKind.DATE) Icons.Rounded.CalendarMonth else Icons.Rounded.Schedule,
                            contentDescription = "Choose ${title.lowercase(Locale.ENGLISH)}",
                        )
                    }
                }
            } else null
            NafsTextField(
                value = value,
                onValueChange = onChange,
                label = if (field.kind == FieldKind.APPS) "Apps" else title,
                trailingIcon = pickerIcon,
                supportingText = supportingFor(field, hint, value),
                enabled = enabled,
                singleLine = !multiline,
                minLines = when {
                    !multiline -> 1
                    field.kind == FieldKind.APPS -> 4
                    field.key in longTextKeys -> 3
                    else -> 2
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = if (writing) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
                    keyboardType = keyboardType,
                    imeAction = if (multiline) ImeAction.Default else ImeAction.Next,
                ),
            )
        }
    }
}

@Composable
private fun ChoiceField(title: String, options: List<Pair<String, String>>, value: String, enabled: Boolean, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (option, label) ->
                NafsChoiceChip(label, selected = value.trim() == option, onClick = { onChange(option) }, enabled = enabled)
            }
        }
    }
}

@Composable
private fun MoodField(title: String, value: String, enabled: Boolean, onChange: (String) -> Unit) {
    val current = value.trim().toDoubleOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { level ->
                val chosen = current == level.toDouble()
                NafsChoiceChip("$level", selected = chosen, onClick = { onChange(if (chosen) "" else "$level") }, enabled = enabled)
            }
        }
        Text(
            "Optional · 1 is low, 5 is great. Tap your choice again to clear it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PhotoSection(feature: RecordFeature, values: Map<String, String>, enabled: Boolean, onAttach: () -> Unit) {
    val log = feature == RecordFeature.LOG
    val raw = values[if (log) "photo_urls" else "screenshot_url"].orEmpty()
    val urls = remember(raw) { photoUrls(raw) }
    val attached = raw.isNotBlank() && raw != "[]" && raw != "null"
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(if (log) "Photos" else "Screenshot", style = MaterialTheme.typography.titleSmall)
            Text(
                when {
                    log -> "${urls.size} of 6 attached"
                    attached -> "Attached. A new one replaces it."
                    else -> "Optional · your phone's screen-time summary"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            urls.forEachIndexed { index, url -> PhotoThumb(url, if (log) "Log photo ${index + 1}" else "Screen-time screenshot", 88.dp) }
            AttachTile(enabled, onAttach)
        }
        if (attached) {
            Text(
                "Photo attached. Uploaded photos use the existing public storage bucket.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AttachTile(enabled: Boolean, onAttach: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onAttach,
        enabled = enabled,
        modifier = Modifier.size(88.dp),
        shape = MaterialTheme.shapes.medium,
        color = scheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column(
            Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            Icon(Icons.Rounded.AddAPhoto, contentDescription = null, tint = if (enabled) scheme.primary else scheme.onSurfaceVariant)
            Text("Attach photo", style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PhotoThumb(url: String, description: String, size: Dp) {
    Box(
        Modifier.size(size).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        PublicPhoto(url, description, Modifier.fillMaxSize(), crop = true)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(value: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val initial = remember(value) {
        runCatching { LocalDate.parse(value.trim()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
    }
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    // The picker works in UTC midnights; the form keeps the same YYYY-MM-DD text it validates.
                    pickerState.selectedDateMillis?.let { millis -> onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                    onDismiss()
                },
                enabled = pickerState.selectedDateMillis != null,
            ) { Text("Use date") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = pickerState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(title: String, value: String, defaultHour: Int, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val parts = value.trim().split(':')
    val hour = parts.getOrNull(0)?.toIntOrNull()?.takeIf { it in 0..23 } ?: defaultHour
    val minute = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in 0..59 } ?: 0
    val pickerState = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = pickerState) },
        confirmButton = {
            TextButton(onClick = {
                // Same HH:mm text the form validates, padded and free of locale digits.
                onPick("${pickerState.hour.toString().padStart(2, '0')}:${pickerState.minute.toString().padStart(2, '0')}")
                onDismiss()
            }) { Text("Use time") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ─── Feature identity and text ───────────────────────────────────────────────

private data class FeatureLook(val icon: ImageVector, val tint: Color, val headline: String, val description: String)

@Composable
private fun featureLook(feature: RecordFeature): FeatureLook {
    val c = Nafs.colors
    return when (feature) {
        RecordFeature.DREAM -> FeatureLook(Icons.Rounded.Stars, c.goals, "Map your long game",
            "Name what you're building, why it matters, and the hours it will take.")
        RecordFeature.LOG -> FeatureLook(Icons.AutoMirrored.Rounded.EventNote, c.reflect, "How did today go?",
            "Focused hours, study, sleep and mood, in under a minute.")
        RecordFeature.QURAN -> FeatureLook(Icons.AutoMirrored.Rounded.MenuBook, c.deen, "Your Quran journal",
            "Pages, ayahs and a line on what stayed with you.")
        RecordFeature.SCREEN -> FeatureLook(Icons.Rounded.PhoneAndroid, c.challenges, "Where did the minutes go?",
            "Total screen time and your top apps, with an optional screenshot.")
        RecordFeature.NOTE -> FeatureLook(Icons.Rounded.BugReport, c.health, "Bugs and ideas",
            "Note what broke, or what could be better, while it's fresh.")
    }
}

private fun recordSubtitle(feature: RecordFeature, timezone: String): String = when (feature) {
    RecordFeature.DREAM -> "Your long game, mapped to hours"
    RecordFeature.NOTE -> "Bugs to fix and ideas to try"
    else -> "One entry a day · $timezone"
}

private fun savedSupporting(feature: RecordFeature, count: Int): String? = when {
    feature == RecordFeature.DREAM -> null
    feature.daily -> "Newest first · $count shown"
    else -> "$count saved, newest first"
}

private fun formTitle(feature: RecordFeature, date: String): String = when {
    feature.daily -> prettyDate(date, longDate).ifBlank { "Today's entry" }
    feature == RecordFeature.DREAM -> "Your dream"
    else -> "Bug or idea"
}

private fun isStatusMessage(message: String) =
    message.startsWith("Saved to your account") || message == "Queued entry synced to your account."

private val wholeNumberKeys = setOf("mood", "ayah_from", "ayah_to", "screen_time_mins", "total_mins")
private val longTextKeys = setOf("reflection_text", "notes", "detail", "apps")

/** "Sleep start (HH:mm)" → "Sleep start" and "HH:mm". */
private fun splitLabel(label: String): Pair<String, String?> {
    val open = label.indexOf(" (")
    return if (open > 0 && label.endsWith(")")) label.substring(0, open) to label.substring(open + 2, label.length - 1) else label to null
}

private fun shortLabel(field: RecordField): String = if (field.kind == FieldKind.APPS) "Apps" else splitLabel(field.label).first

private fun supportingFor(field: RecordField, hint: String?, value: String): String {
    val need = if (field.required) "Required" else "Optional"
    return when (field.kind) {
        FieldKind.NUMBER -> "$need · ${rangeNumber(field.min)} to ${rangeNumber(field.max)}" +
            if (field.key in wholeNumberKeys) ", whole numbers" else ""
        FieldKind.DATE -> "$need · a future date as ${hint ?: "YYYY-MM-DD"}, or pick one"
        FieldKind.TIME -> "$need · 24-hour time as ${hint ?: "HH:mm"}, or pick one"
        FieldKind.APPS -> "One app per line, as App name: minutes"
        FieldKind.TEXT -> if (field.max >= 200) "$need · ${value.length}/${field.max.toInt()}" else need
    }
}

/** Short values sit in the figure wells; longer writing reads as a passage. */
private fun RecordField.isFigure() = kind != FieldKind.TEXT || max <= 100.0

private fun displayValue(field: RecordField, value: String): String = when {
    field.kind == FieldKind.DATE -> prettyDate(value, fullDate)
    field.key == "mood" -> "$value/5"
    else -> value
}

private fun logExtras(row: JsonObject): List<Pair<String, String>> = listOfNotNull(
    RecordRules.text(row, "weighted_hours_today").takeIf { it.isNotBlank() }?.let { tidy(it) to "Weighted hours" },
    RecordRules.text(row, "todays_pull_days").takeIf { it.isNotBlank() }?.let { tidy(it) to "Pull days" },
)

private fun tidy(value: String): String {
    val number = value.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return value
    return if (number % 1.0 == 0.0) number.toLong().toString()
    else "%.2f".format(Locale.ENGLISH, number).trimEnd('0').trimEnd('.')
}

private fun rangeNumber(value: Double): String =
    if (value % 1.0 == 0.0) "%,d".format(Locale.ENGLISH, value.toLong()) else value.toString()

private fun capitalized(value: String) = value.replaceFirstChar { it.titlecase(Locale.ENGLISH) }

private fun photoUrls(raw: String): List<String> {
    val element = runCatching { Json.parseToJsonElement(raw) }.getOrNull() ?: return emptyList()
    val items = if (element is JsonArray) element else listOf(element)
    return items.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.filter { it.isNotBlank() }
}

private val longDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)
private val fullDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
private val monthFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)

private fun prettyDate(value: String, format: DateTimeFormatter): String =
    runCatching { LocalDate.parse(value).format(format) }.getOrDefault(value)
