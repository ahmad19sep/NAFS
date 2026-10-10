package com.ahmad.nafs.ui.progress

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
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.core.records.PrivateRecordStore
import com.ahmad.nafs.core.records.RecordRules
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

data class AchievementsState(val data: JsonObject? = null, val busy: Boolean = false, val error: String? = null, val offline: Boolean = false)
@HiltViewModel
class AchievementsViewModel @Inject constructor(private val api: NafsApiClient, private val store: PrivateRecordStore) : ViewModel() {
    private val _state = MutableStateFlow(AchievementsState()); val state = _state.asStateFlow()
    private var account = ""
    fun initialize(user: String) { if (account != user) { account = user; refresh() } }
    fun refresh() {
        if (_state.value.busy || account.isBlank()) return
        val user = account; _state.value = _state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val data = api.post(user, "/api/recognition", buildJsonObject {})
                require(data["badges"] is JsonArray && data["level"] is JsonObject)
                if (account != user) return@launch
                _state.value = AchievementsState(data = data)
                try { store.write(user, "recognition", data) }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { _state.value = _state.value.copy(error = "Current achievements loaded; the offline copy could not be saved.") }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) {
                val saved = runCatching { store.read(user, "recognition") }.getOrNull()
                _state.value = AchievementsState(data = saved, offline = saved != null, error = "Achievements could not be refreshed. Reconnect and retry.")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsRoute(profile: UserProfile, onBack: () -> Unit, viewModel: AchievementsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(profile.userId) { viewModel.initialize(profile.userId) }
    Scaffold(topBar = { TopAppBar(title = { Text("Badges and levels") }, navigationIcon = { TextButton(onBack) { Text("Back") } }, actions = { TextButton(viewModel::refresh, enabled = !state.busy) { Text("Refresh") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            if (state.offline) item { Text("Saved offline achievements") }
            state.data?.let { data ->
                val level = data["level"] as JsonObject; val current = level["current"] as? JsonObject; val next = level["next"] as? JsonObject
                item { Text(if (current == null) "Your first recorded day starts your level." else "Level ${RecordRules.text(current, "number")} · ${RecordRules.text(current, "name")}", style = MaterialTheme.typography.headlineSmall)
                    Text("${RecordRules.text(level, "daysLogged")} distinct days recorded across habits, prayer, and health.")
                    next?.let { Text("${RecordRules.text(level, "daysToNext")} more recorded days to ${RecordRules.text(it, "name")}") }
                    LinearProgressIndicator(progress = { (RecordRules.text(level, "pctToNext").toFloatOrNull() ?: 0f) / 100 }, modifier = Modifier.fillMaxWidth())
                }
                if ((data["unavailable"] as? JsonArray)?.isNotEmpty() == true) item { Text("Some recent activity was unavailable; the consistency badge was not reassessed.") }
                items((data["badges"] as JsonArray).map { it as JsonObject }, key = { RecordRules.text(it, "id") }) { badge ->
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                        Text("${RecordRules.text(badge, "emoji")} ${RecordRules.text(badge, "name")}", style = MaterialTheme.typography.titleMedium)
                        Text(RecordRules.text(badge, "description"))
                        Text("${ProgressText.label(RecordRules.text(badge, "tier"))} · ${if (RecordRules.text(badge, "earned") == "true") "Earned" else "Keep going"}")
                    } }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}
