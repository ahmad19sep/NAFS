package com.ahmad.nafs.ui.advice

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ahmad.nafs.core.network.NafsApiClient
import com.ahmad.nafs.core.network.SupabaseClientProvider
import com.ahmad.nafs.core.records.PrivateRecordStore
import com.ahmad.nafs.core.records.RecordRules
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.core.plans.AdviceProposals
import com.ahmad.nafs.ui.progress.ProgressText
import com.ahmad.nafs.ui.reports.ReportMarkdown
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdviceRoute(profile: UserProfile, period: String?, offset: Int, onBack: () -> Unit, viewModel: AdviceViewModel = hiltViewModel(),
    applyViewModel: AdviceApplyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val applyState by applyViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(profile.userId, state.result) { state.result?.let { applyViewModel.initialize(profile, it) } }
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(profile.userId) { viewModel.initialize(profile.userId, profile.timezone, period, offset) }
    Scaffold(topBar = { TopAppBar(title = { Text("Coach advice") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (period == null) items(AdviceAction.entries.filter { it != AdviceAction.REVIEW }) { action ->
                FilterChip(selected = state.action == action, onClick = { viewModel.select(action) }, enabled = !state.busy && !applyState.busy && state.request == null, label = { Text(action.label) })
            }
            if (state.action == AdviceAction.HEALTH) item { Text("Complete your Health baseline before requesting advice. Recommendations support daily habits and do not diagnose or replace medical care.") }
            state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            if (state.action.table != null && state.rows.isEmpty() && !state.busy) item { Text("Create a saved ${state.action.table.orEmpty().removeSuffix("s")} first.") }
            items(state.rows, key = { RecordRules.text(it, "id") }) { row ->
                FilterChip(selected = state.selectedId == RecordRules.text(row, "id"), onClick = { viewModel.selectId(RecordRules.text(row, "id")) },
                    enabled = !state.busy && !applyState.busy && state.request == null, label = { Text(RecordRules.text(row, if (state.action.table == "habits") "name" else "title")) })
            }
            item { Button(onClick = viewModel::generate, enabled = !state.busy && !applyState.busy && (state.action.table == null || state.selectedId != null || state.request != null)) { Text(if (state.request != null) "Retry exact request" else "Request advice") } }
            if (state.request != null) item {
                Text("An unresolved request is retained on this device.")
                TextButton(onClick = { confirmClear = true }, enabled = !state.busy) { Text("Release retained request") }
            }
            state.result?.let { result ->
                item { ReportMarkdown(adviceText(result)) }
                val proposals = AdviceProposals.parse(result)
                if (proposals.isNotEmpty()) item { Text("Review suggestions, then add the ones you choose. Each button creates one item in your account.") }
                proposals.forEachIndexed { index, proposal -> item {
                    Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${proposal.emoji} ${proposal.title}", style = MaterialTheme.typography.titleMedium)
                        Text(proposal.description())
                        val step = applyState.steps.getOrNull(index)
                        OutlinedButton(onClick = { applyViewModel.add(profile, result, index) },
                            enabled = applyState.ready && !applyState.busy && !state.busy && step?.added != true) {
                            Text(if (step?.added == true) "Added" else if (step?.applyDate != null) "Retry and confirm" else "Add ${proposal.kind}")
                        }
                    } }
                } }
                applyState.message?.let { message -> item { Text(message) } }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("Release this request?") },
        text = { Text("The server may still finish it. Starting another request can generate a second answer. Saved feature records remain unchanged.") },
        confirmButton = { TextButton(onClick = { viewModel.clearRequest(); confirmClear = false }) { Text("Release") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Keep request") } })
}
