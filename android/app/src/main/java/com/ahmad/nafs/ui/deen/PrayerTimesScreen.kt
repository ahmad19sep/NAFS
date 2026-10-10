package com.ahmad.nafs.ui.deen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.core.records.PrivateRecordStore
import com.ahmad.nafs.core.records.RecordRules
import com.ahmad.nafs.core.time.AccountDate
import com.ahmad.nafs.ui.AccountDayRefreshEffects
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

object PrayerTimeRules {
    val methods = mapOf(1 to "Karachi", 2 to "ISNA", 3 to "Muslim World League", 4 to "Umm al-Qura", 5 to "Egypt", 13 to "Turkey")
    val names = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
    fun time(raw: String): LocalTime {
        require(Regex("^\\d{2}:\\d{2}(?:\\s.*)?$").matches(raw))
        return LocalTime.parse(raw.take(5))
    }
    fun next(times: Map<String, String>, now: LocalTime): Pair<String, Duration>? = names.mapNotNull { name ->
        times[name]?.let { name to time(it) }
    }.firstOrNull { it.second.isAfter(now) }?.let { it.first to Duration.between(now, it.second) }
}

@Singleton
class PrayerTimesRepository @Inject constructor() {
    // A dedicated public client: account tokens are never sent to the prayer provider.
    private val http = HttpClient(Android) { install(HttpTimeout) { requestTimeoutMillis = 15_000; connectTimeoutMillis = 10_000 }; followRedirects = false }
    suspend fun fetch(city: String, country: String, method: Int, hanafi: Boolean, zone: String, date: LocalDate): JsonObject {
        require(city.trim().length in 1..100 && country.trim().length in 1..100 && method in PrayerTimeRules.methods)
        ZoneId.of(zone)
        val response = http.get("https://api.aladhan.com/v1/timingsByCity/${date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))}") {
            parameter("city", city.trim()); parameter("country", country.trim()); parameter("method", method)
            parameter("school", if (hanafi) 1 else 0); parameter("timezonestring", zone)
        }
        require(response.status.value == 200)
        val body = response.bodyAsText(); require(body.length <= 100_000)
        val data = (Json.parseToJsonElement(body) as JsonObject)["data"] as JsonObject
        val returnedDate = ((data["date"] as JsonObject)["gregorian"] as JsonObject)
        require(RecordRules.text(returnedDate, "date") == date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy")))
        require(RecordRules.text(data["meta"] as JsonObject, "timezone") == zone)
        val timings = data["timings"] as JsonObject
        return buildJsonObject {
            put("date", date.toString()); put("zone", zone); put("city", city.trim()); put("country", country.trim())
            put("method", method); put("hanafi", hanafi)
            put("times", buildJsonObject { PrayerTimeRules.names.forEach { name -> put(name, PrayerTimeRules.time(RecordRules.text(timings, name)).toString()) } })
        }
    }
}

data class PrayerTimesState(val city: String = "", val country: String = "", val method: Int = 1, val hanafi: Boolean = true,
    val data: JsonObject? = null, val busy: Boolean = false, val cached: Boolean = false, val error: String? = null)

@HiltViewModel
class PrayerTimesViewModel @Inject constructor(private val repository: PrayerTimesRepository, private val store: PrivateRecordStore) : ViewModel() {
    private val _state = MutableStateFlow(PrayerTimesState()); val state = _state.asStateFlow()
    private var account = ""; private var zone = "UTC"
    fun initialize(profile: UserProfile) {
        if (account == profile.userId && zone == profile.timezone) return
        account = profile.userId; zone = profile.timezone
        viewModelScope.launch {
            try {
                val saved = store.read(account, "prayer-times")
                if (saved != null) _state.value = PrayerTimesState(RecordRules.text(saved, "city"), RecordRules.text(saved, "country"),
                    RecordRules.text(saved, "method").toIntOrNull()?.takeIf { it in PrayerTimeRules.methods } ?: 1,
                    RecordRules.text(saved, "hanafi") == "true", saved.takeIf { RecordRules.text(it, "date") == AccountDate.today(zone).toString() && RecordRules.text(it, "zone") == zone }, cached = true)
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { _state.update { it.copy(error = "Saved prayer times could not be read.") } }
        }
    }
    fun city(value: String) { if (!_state.value.busy && value.length <= 100) _state.update { it.copy(city = value, data = null) } }
    fun country(value: String) { if (!_state.value.busy && value.length <= 100) _state.update { it.copy(country = value, data = null) } }
    fun method(value: Int) { if (!_state.value.busy) _state.update { it.copy(method = value, data = null) } }
    fun hanafi(value: Boolean) { if (!_state.value.busy) _state.update { it.copy(hanafi = value, data = null) } }
    fun invalidateDate() { _state.update { it.copy(data = it.data?.takeIf { data -> RecordRules.text(data, "date") == AccountDate.today(zone).toString() }) } }
    fun fetch() {
        if (_state.value.busy) return
        val input = _state.value; val userId = account; val timezone = zone
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val data = repository.fetch(input.city, input.country, input.method, input.hanafi, timezone, AccountDate.today(timezone))
                if (account != userId || zone != timezone) return@launch
                _state.update { it.copy(data = data, cached = false) }
                store.write(userId, "prayer-times", data)
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { _state.update { it.copy(error = "Prayer times could not be refreshed. A saved copy is usable only for its displayed date and calculation settings.") } }
            finally { _state.update { it.copy(busy = false) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerTimesRoute(profile: UserProfile, onBack: () -> Unit, viewModel: PrayerTimesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(profile.userId, profile.timezone) { viewModel.initialize(profile) }
    AccountDayRefreshEffects(profile.timezone, viewModel::invalidateDate)
    var now by remember(profile.timezone) { mutableStateOf(ZonedDateTime.now(ZoneId.of(profile.timezone))) }
    LaunchedEffect(profile.timezone) { while (true) { now = ZonedDateTime.now(ZoneId.of(profile.timezone)); delay(1_000) } }
    Scaffold(topBar = { TopAppBar(title = { Text("Prayer times") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Enter a city and country. Fetch sends these values and your account timezone to Aladhan; no location permission is needed.") }
            item { OutlinedTextField(state.city, viewModel::city, label = { Text("City") }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(state.country, viewModel::country, label = { Text("Country") }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) }
            item { Text("Calculation method"); PrayerTimeRules.methods.forEach { (method, name) -> FilterChip(state.method == method, onClick = { viewModel.method(method) }, enabled = !state.busy, label = { Text(name) }) } }
            item { Row { Checkbox(state.hanafi, viewModel::hanafi, enabled = !state.busy); Text("Hanafi Asr") } }
            item { Button(viewModel::fetch, enabled = !state.busy && state.city.isNotBlank() && state.country.isNotBlank()) { Text(if (state.busy) "Fetching…" else "Fetch today's times") } }
            state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            state.data?.takeIf { RecordRules.text(it, "date") == now.toLocalDate().toString() }?.let { data ->
                val timings = (data["times"] as JsonObject).mapValues { (it.value as JsonPrimitive).content }
                item { Text("${RecordRules.text(data, "date")} · ${profile.timezone}${if (state.cached) " · saved offline copy" else ""}") }
                PrayerTimeRules.names.forEach { name -> item { Text("$name: ${timings[name]}", style = MaterialTheme.typography.titleMedium) } }
                item { val next = PrayerTimeRules.next(timings, now.toLocalTime()); Text(next?.let { "${it.first} in ${it.second.toHours()}h ${it.second.toMinutes() % 60}m" } ?: "Today's listed prayer times have passed.") }
                item { Text("Source: Aladhan. Confirm the method and local mosque times for your practice.", style = MaterialTheme.typography.bodySmall) }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}
