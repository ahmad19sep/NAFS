package com.ahmad.nafs.ui.advice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ahmad.nafs.core.network.NafsApiClient
import com.ahmad.nafs.core.network.SupabaseClientProvider
import com.ahmad.nafs.core.plans.AdviceProposals
import com.ahmad.nafs.core.plans.PlanProposal
import com.ahmad.nafs.core.plans.PlanStepState
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.core.records.PrivateRecordStore
import com.ahmad.nafs.core.records.RecordRules
import com.ahmad.nafs.ui.components.NafsBanner
import com.ahmad.nafs.ui.components.NafsBannerKind
import com.ahmad.nafs.ui.components.NafsCard
import com.ahmad.nafs.ui.components.NafsChoiceChip
import com.ahmad.nafs.ui.components.NafsDivider
import com.ahmad.nafs.ui.components.NafsEmojiBadge
import com.ahmad.nafs.ui.components.NafsEmptyState
import com.ahmad.nafs.ui.components.NafsEyebrow
import com.ahmad.nafs.ui.components.NafsIconBadge
import com.ahmad.nafs.ui.components.NafsPill
import com.ahmad.nafs.ui.components.NafsPrimaryButton
import com.ahmad.nafs.ui.components.NafsSecondaryButton
import com.ahmad.nafs.ui.components.NafsSectionTitle
import com.ahmad.nafs.ui.components.NafsSpace
import com.ahmad.nafs.ui.components.NafsTopBar
import com.ahmad.nafs.ui.progress.ProgressText
import com.ahmad.nafs.ui.reports.ReportMarkdown
import com.ahmad.nafs.ui.theme.Nafs
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.postgrest.from
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

enum class AdviceAction(val action: String, val label: String, val table: String? = null, val idKey: String? = null) {
    HABIT("habit-starter", "Habit starter advice", "habits", "habitId"),
    CHALLENGE("challenge-starter", "Challenge starter advice", "challenges", "challengeId"),
    GOAL_STARTER("goal-starter", "Goal starter advice", "goals", "goalId"),
    GOAL_ALIGNMENT("goal-alignment", "Goal alignment", "goals", "goalId"),
    GOAL_PLAN("goal-plan", "Goal plan", "goals"),
    HEALTH("health-recommend", "Health recommendation"),
    REVIEW("report-review", "Report review"),
}
data class AdviceState(val action: AdviceAction = AdviceAction.HABIT, val rows: List<JsonObject> = emptyList(),
    val selectedId: String? = null, val busy: Boolean = false, val request: JsonObject? = null, val result: JsonObject? = null, val error: String? = null)

@HiltViewModel
class AdviceViewModel @Inject constructor(private val api: NafsApiClient, private val store: PrivateRecordStore,
    private val provider: SupabaseClientProvider) : ViewModel() {
    private val _state = MutableStateFlow(AdviceState()); val state = _state.asStateFlow()
    private var zone = "UTC"
    private var account = ""; private var period = "weekly"; private var offset = 0
    private var storageReadable = true
    private fun key() = "advice/${_state.value.action.action}/$period/$offset"
    fun initialize(userId: String, timezone: String, initialPeriod: String?, initialOffset: Int) {
        zone = timezone
        if (account == userId) return
        account = userId; zone = timezone; period = initialPeriod ?: "weekly"; offset = initialOffset
        if (initialPeriod != null) _state.update { it.copy(action = AdviceAction.REVIEW) }
        reload()
    }
    fun select(action: AdviceAction) { if (_state.value.busy || _state.value.request != null) return; _state.value = AdviceState(action = action); reload() }
    fun selectId(id: String) {
        if (!_state.value.busy && _state.value.request == null && _state.value.selectedId != id)
            _state.update { it.copy(selectedId = id, result = null, error = null) }
    }
    fun reload() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        val userId = account; val action = _state.value.action
        viewModelScope.launch {
            try {
                val saved = store.read(userId, key())
                storageReadable = true
                val selectedId = RecordRules.text(saved, "selectedId").takeIf(String::isNotBlank)
                _state.update { it.copy(request = saved?.get("request") as? JsonObject,
                    result = (saved?.get("result") as? JsonObject)?.takeIf { action.table == null || selectedId != null }, selectedId = selectedId) }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { storageReadable = false; _state.update { it.copy(error = "Retained request storage is unreadable. Generation is blocked.") } }
            try {
                if (action.table != null) {
                    val rows = checkNotNull(provider.client).from(action.table).select { filter { eq("user_id", userId) }; limit(100) }.decodeList<JsonObject>()
                    if (account == userId) {
                        val request = _state.value.request
                        val payload = request?.get("payload") as? JsonObject
                        val targetId = action.idKey?.let { RecordRules.text(payload, it) }
                        val row = rows.find { RecordRules.text(it, "id") == targetId }
                        val field = if (action == AdviceAction.GOAL_ALIGNMENT) "ai_alignment" else "ai_starter_pack"
                        val pack = row?.get(field) as? JsonObject
                        if (request != null && pack != null && RecordRules.text(pack, "request_id") == RecordRules.text(request, "requestId")) {
                            val result = buildJsonObject { put(if (action == AdviceAction.GOAL_ALIGNMENT) "alignment" else "starter_pack", pack) }
                            store.write(userId, key(), buildJsonObject { put("result", result); put("selectedId", targetId) })
                            _state.update { it.copy(request = null, result = result, selectedId = targetId) }
                        }
                    }
                    if (account == userId) _state.update { it.copy(rows = rows, selectedId = it.selectedId?.takeIf { id -> rows.any { row -> RecordRules.text(row, "id") == id } } ?: rows.firstOrNull()?.let { row -> RecordRules.text(row, "id") }) }
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { _state.update { it.copy(error = "Unable to load saved records. Check your connection.") } }
            finally { _state.update { it.copy(busy = false) } }
        }
    }
    fun generate() {
        if (_state.value.busy || !storageReadable) return
        val state = _state.value; val userId = account
        if (state.action.table != null && state.selectedId == null && state.request == null) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val linkedHabits = if (state.action == AdviceAction.GOAL_PLAN && state.request == null) {
                    val row = state.rows.first { RecordRules.text(it, "id") == state.selectedId }
                    val ids = (row["linked_habit_ids"] as? JsonArray).orEmpty().map { (it as JsonPrimitive).content }
                    if (ids.isEmpty()) emptyList() else {
                        val habits = checkNotNull(provider.client).from("habits").select {
                            filter { eq("user_id", userId); isIn("id", ids) }; limit(100)
                        }.decodeList<JsonObject>()
                        require(habits.map { RecordRules.text(it, "id") }.toSet() == ids.toSet())
                        habits.map { RecordRules.text(it, "name") }
                    }
                } else emptyList()
                val request = state.request ?: buildJsonObject {
                    put("requestId", UUID.randomUUID().toString()); put("action", state.action.action)
                    put("payload", buildJsonObject {
                        if (state.action.idKey != null) put(state.action.idKey, checkNotNull(state.selectedId))
                        if (state.action == AdviceAction.GOAL_PLAN) {
                            val row = state.rows.first { RecordRules.text(it, "id") == state.selectedId }
                            put("title", RecordRules.text(row, "title")); put("description", RecordRules.text(row, "description")); put("deadline", RecordRules.text(row, "deadline")); put("linkedHabits", JsonArray(linkedHabits.map(::JsonPrimitive)))
                        }
                        if (state.action == AdviceAction.REVIEW) { put("period", period); put("offset", offset); put("anchorDate", com.ahmad.nafs.core.time.AccountDate.today(zone).toString()) }
                    })
                }
                store.write(userId, key(), buildJsonObject { put("request", request); state.selectedId?.let { put("selectedId", it) }; state.result?.let { put("result", it) } })
                _state.update { it.copy(request = request) }
                val response = api.post(userId, "/api/ai/native", request)
                val result = response["result"] as? JsonObject ?: error("Invalid response")
                if (account != userId) return@launch
                store.write(userId, key(), buildJsonObject { put("result", result); state.selectedId?.let { put("selectedId", it) } })
                _state.update { it.copy(result = result, request = null) }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { _state.update { it.copy(error = "Advice could not be confirmed. Your exact request is retained; retry or reload saved advice.") } }
            finally { if (account == userId) _state.update { it.copy(busy = false) } }
        }
    }
    fun clearRequest() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        // Explicitly releasing an unresolved generation permits another provider request.
        viewModelScope.launch {
            try { store.write(account, key(), buildJsonObject { _state.value.result?.let { put("result", it) }; _state.value.selectedId?.let { put("selectedId", it) } }); _state.update { it.copy(request = null) } }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { _state.update { it.copy(error = "Request could not be cleared on this device.") } }
            finally { _state.update { it.copy(busy = false) } }
        }
    }
}

private fun adviceText(value: JsonElement): String = when (value) {
    is JsonPrimitive -> if (value == JsonNull) "" else value.content
    is JsonArray -> value.joinToString("\n\n") { adviceText(it) }
    is JsonObject -> value.entries.filter { it.value != JsonNull && it.key !in setOf("request_id", "id", "user_id") }.joinToString("\n\n") { (key, child) -> "**${ProgressText.label(key)}**\n${adviceText(child)}" }
}

@Composable
fun AdviceRoute(profile: UserProfile, period: String?, offset: Int, onBack: () -> Unit, viewModel: AdviceViewModel = hiltViewModel(),
    applyViewModel: AdviceApplyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val applyState by applyViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(profile.userId, state.result) { state.result?.let { applyViewModel.initialize(profile, it) } }
    LaunchedEffect(profile.userId) { viewModel.initialize(profile.userId, profile.timezone, period, offset) }
    AdviceScreen(
        state = state,
        applyState = applyState,
        reviewMode = period != null,
        actions = AdviceActions(
            onBack = onBack,
            onSelectAction = viewModel::select,
            onSelectRow = viewModel::selectId,
            onGenerate = viewModel::generate,
            onClearRequest = viewModel::clearRequest,
            onAdd = { result, index -> applyViewModel.add(profile, result, index) },
        ),
    )
}

/** Everything the advice screen can ask its two ViewModels to do. */
internal data class AdviceActions(
    val onBack: () -> Unit = {},
    val onSelectAction: (AdviceAction) -> Unit = {},
    val onSelectRow: (String) -> Unit = {},
    val onGenerate: () -> Unit = {},
    val onClearRequest: () -> Unit = {},
    val onAdd: (result: JsonObject, index: Int) -> Unit = { _, _ -> },
)

@Composable
internal fun AdviceScreen(state: AdviceState, applyState: AdviceApplyState, reviewMode: Boolean, actions: AdviceActions) {
    var confirmClear by remember { mutableStateOf(false) }
    val choicesEnabled = !state.busy && !applyState.busy && state.request == null
    val canGenerate = !state.busy && !applyState.busy && (state.action.table == null || state.selectedId != null || state.request != null)

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        NafsTopBar(
            title = "Coach advice",
            onBack = actions.onBack,
            subtitle = if (reviewMode) "A coach review of your report" else "Suggestions you can act on",
        )
        Box(Modifier.fillMaxWidth().height(4.dp)) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = NafsSpace.screen, end = NafsSpace.screen, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(NafsSpace.item),
        ) {
            if (!reviewMode) item(key = "areas") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NafsEyebrow("What would you like help with?", Modifier.padding(start = 4.dp))
                    WrapRow {
                        AdviceAction.entries.filter { it != AdviceAction.REVIEW }.forEach { action ->
                            NafsChoiceChip(
                                action.label,
                                selected = state.action == action,
                                onClick = { actions.onSelectAction(action) },
                                enabled = choicesEnabled,
                            )
                        }
                    }
                }
            }
            if (state.action == AdviceAction.HEALTH) item(key = "health-note") {
                NafsBanner(
                    "Complete your Health baseline before requesting advice. Recommendations support daily habits and do not diagnose or replace medical care.",
                    kind = NafsBannerKind.INFO,
                )
            }
            state.error?.let { error -> item(key = "error") { NafsBanner(error, kind = NafsBannerKind.ERROR) } }
            if (state.action.table != null && state.rows.isEmpty() && !state.busy) item(key = "no-rows") {
                NafsEmptyState(
                    icon = adviceIcon(state.action),
                    title = "Nothing to advise on yet",
                    message = "Create a saved ${state.action.table.orEmpty().removeSuffix("s")} first.",
                    tint = Nafs.colors.coach,
                )
            }
            if (state.rows.isNotEmpty()) item(key = "rows-title") {
                NafsEyebrow("Choose one", Modifier.padding(start = 4.dp, top = 8.dp))
            }
            items(state.rows, key = { RecordRules.text(it, "id") }) { row ->
                val id = RecordRules.text(row, "id")
                RecordChoice(
                    label = RecordRules.text(row, if (state.action.table == "habits") "name" else "title"),
                    isSelected = state.selectedId == id,
                    enabled = choicesEnabled,
                    onClick = { actions.onSelectRow(id) },
                )
            }
            if (state.request != null) item(key = "retained") {
                NafsCard(borderColor = Nafs.colors.warning.copy(alpha = 0.4f), shape = MaterialTheme.shapes.medium, contentPadding = PaddingValues(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = Nafs.colors.warning, modifier = Modifier.size(20.dp))
                        Text("An unresolved request is retained on this device.", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    }
                    TextButton(onClick = { confirmClear = true }, enabled = !state.busy, modifier = Modifier.padding(top = 6.dp)) {
                        Text("Release retained request")
                    }
                }
            }
            state.result?.let { result ->
                item(key = "result") { AdviceResult(result) }
                val proposals = AdviceProposals.parse(result)
                if (proposals.isNotEmpty()) item(key = "suggestions-title") {
                    NafsSectionTitle(
                        "Suggestions",
                        Modifier.padding(top = 8.dp),
                        supporting = "Review suggestions, then add the ones you choose. Each button creates one item in your account.",
                    )
                }
                proposals.forEachIndexed { index, proposal ->
                    item(key = "proposal-$index") {
                        val step = applyState.steps.getOrNull(index)
                        ProposalCard(
                            proposal = proposal,
                            step = step,
                            enabled = applyState.ready && !applyState.busy && !state.busy && step?.added != true,
                            onAdd = { actions.onAdd(result, index) },
                        )
                    }
                }
                applyState.message?.let { message -> item(key = "apply-message") { NafsBanner(message, kind = NafsBannerKind.INFO) } }
            }
        }
        Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.navigationBarsPadding()) {
                NafsDivider(inset = 0.dp)
                NafsPrimaryButton(
                    if (state.request != null) "Retry exact request" else "Request advice",
                    onClick = actions.onGenerate,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = NafsSpace.screen, vertical = 12.dp),
                    enabled = canGenerate,
                    icon = Icons.Rounded.AutoAwesome,
                )
            }
        }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("Release this request?") },
        icon = { Icon(Icons.Rounded.WarningAmber, contentDescription = null) },
        text = { Text("The server may still finish it. Starting another request can generate a second answer. Saved feature records remain unchanged.") },
        confirmButton = { TextButton(onClick = { actions.onClearRequest(); confirmClear = false }) { Text("Release") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Keep request") } })
}

private fun adviceIcon(action: AdviceAction): ImageVector = when (action) {
    AdviceAction.HABIT -> Icons.Rounded.Autorenew
    AdviceAction.CHALLENGE -> Icons.Rounded.Whatshot
    AdviceAction.GOAL_STARTER, AdviceAction.GOAL_ALIGNMENT, AdviceAction.GOAL_PLAN -> Icons.Rounded.Flag
    AdviceAction.HEALTH -> Icons.Rounded.Favorite
    AdviceAction.REVIEW -> Icons.Rounded.Insights
}

@Composable
private fun RecordChoice(label: String, isSelected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    Surface(
        modifier = Modifier.fillMaxWidth().clip(shape)
            .selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        shape = shape,
        color = if (isSelected) scheme.primaryContainer else scheme.surfaceContainer,
        contentColor = if (isSelected) scheme.onPrimaryContainer else scheme.onSurface,
        border = BorderStroke(1.dp, if (isSelected) scheme.primary.copy(alpha = 0.5f) else Nafs.colors.cardBorder),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            RadioButton(selected = isSelected, onClick = null, enabled = enabled)
        }
    }
}

@Composable
private fun AdviceResult(result: JsonObject) {
    val coach = Nafs.colors.coach
    val text = remember(result) { adviceText(result) }
    NafsCard(contentPadding = PaddingValues(20.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            NafsIconBadge(Icons.Rounded.AutoAwesome, coach, size = 38.dp, iconSize = 20.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                NafsEyebrow("From your coach", color = coach)
                Text("Your advice", style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(12.dp))
        SelectionContainer { ReportMarkdown(text) }
    }
}

/** Label, icon and accent for each kind of suggested item. */
private data class KindStyle(val label: String, val icon: ImageVector, val tint: Color)

@Composable
private fun kindStyle(kind: String): KindStyle {
    val label = kind.replaceFirstChar(Char::uppercase)
    return when (kind) {
        "task" -> KindStyle(label, Icons.Rounded.TaskAlt, Nafs.colors.tasks)
        "habit" -> KindStyle(label, Icons.Rounded.Autorenew, Nafs.colors.habits)
        else -> KindStyle(label, Icons.Rounded.Whatshot, Nafs.colors.challenges)
    }
}

@Composable
private fun ProposalCard(proposal: PlanProposal, step: PlanStepState?, enabled: Boolean, onAdd: () -> Unit) {
    val kind = kindStyle(proposal.kind)
    val added = step?.added == true
    NafsCard(
        borderColor = if (added) Nafs.colors.success.copy(alpha = 0.45f) else Nafs.colors.cardBorder,
        contentPadding = PaddingValues(16.dp),
    ) {
        ProposalHeader(proposal, kind, added)
        Spacer(Modifier.height(14.dp))
        NafsSecondaryButton(
            text = if (added) "Added" else if (step?.applyDate != null) "Retry and confirm" else "Add ${proposal.kind}",
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            icon = if (added) Icons.Rounded.CheckCircle else Icons.Rounded.Add,
        )
    }
}

@Composable
private fun ProposalHeader(proposal: PlanProposal, kind: KindStyle, added: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        NafsEmojiBadge(proposal.emoji, kind.tint)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(proposal.title, style = MaterialTheme.typography.titleMedium)
            WrapRow(spacing = 6.dp) {
                NafsPill(kind.label, color = kind.tint, icon = kind.icon)
                proposal.description().split(" · ").filter { it.isNotBlank() }.forEach { detail ->
                    NafsPill(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (added) NafsPill("Added", color = Nafs.colors.success, icon = Icons.Rounded.CheckCircle)
            }
        }
    }
}

/** Wraps chips and labels onto as many lines as they need. */
@Composable
private fun WrapRow(modifier: Modifier = Modifier, spacing: Dp = 8.dp, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        val positions = ArrayList<IntOffset>(placeables.size)
        var x = 0
        var y = 0
        var rowHeight = 0
        var widest = 0
        placeables.forEach { placeable ->
            if (x > 0 && x + placeable.width > constraints.maxWidth) {
                x = 0
                y += rowHeight + gap
                rowHeight = 0
            }
            positions.add(IntOffset(x, y))
            widest = maxOf(widest, x + placeable.width)
            x += placeable.width + gap
            rowHeight = maxOf(rowHeight, placeable.height)
        }
        val width = widest.coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (y + rowHeight).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            placeables.forEachIndexed { index, placeable -> placeable.placeRelative(positions[index].x, positions[index].y) }
        }
    }
}
