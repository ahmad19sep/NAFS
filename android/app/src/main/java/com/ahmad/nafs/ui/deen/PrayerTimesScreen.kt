package com.ahmad.nafs.ui.deen

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.ahmad.nafs.core.deen.PrayerAutoLocate
import com.ahmad.nafs.core.deen.PrayerLocateResult
import com.ahmad.nafs.core.deen.PrayerLocation
import com.ahmad.nafs.core.reminders.ReminderAlarmScheduler
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
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import com.ahmad.nafs.core.deen.Coordinates
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
        val timings = timings(response, zone, date)
        return buildJsonObject {
            put("date", date.toString()); put("zone", zone); put("city", city.trim()); put("country", country.trim())
            put("method", method); put("hanafi", hanafi)
            put("times", buildJsonObject { PrayerTimeRules.names.forEach { name -> put(name, PrayerTimeRules.time(RecordRules.text(timings, name)).toString()) } })
        }
    }

    /** Coordinates are already rounded to about 1 km before they leave the device. */
    suspend fun fetchAt(at: Coordinates, method: Int, hanafi: Boolean, zone: String, date: LocalDate): JsonObject {
        require(method in PrayerTimeRules.methods)
        ZoneId.of(zone)
        val response = http.get("https://api.aladhan.com/v1/timings/${date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))}") {
            parameter("latitude", at.latitude); parameter("longitude", at.longitude); parameter("method", method)
            parameter("school", if (hanafi) 1 else 0); parameter("timezonestring", zone)
        }
        val timings = timings(response, zone, date)
        return buildJsonObject {
            put("date", date.toString()); put("zone", zone); put("city", ""); put("country", "")
            put("latitude", at.latitude); put("longitude", at.longitude); put("method", method); put("hanafi", hanafi)
            put("times", buildJsonObject { PrayerTimeRules.names.forEach { name -> put(name, PrayerTimeRules.time(RecordRules.text(timings, name)).toString()) } })
        }
    }

    private suspend fun timings(response: HttpResponse, zone: String, date: LocalDate): JsonObject {
        require(response.status.value == 200)
        val body = response.bodyAsText(); require(body.length <= 100_000)
        val data = (Json.parseToJsonElement(body) as JsonObject)["data"] as JsonObject
        val returnedDate = ((data["date"] as JsonObject)["gregorian"] as JsonObject)
        require(RecordRules.text(returnedDate, "date") == date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy")))
        require(RecordRules.text(data["meta"] as JsonObject, "timezone") == zone)
        return data["timings"] as JsonObject
    }
}

data class PrayerTimesState(val city: String = "", val country: String = "", val method: Int = 1, val hanafi: Boolean = true,
    val data: JsonObject? = null, val busy: Boolean = false, val cached: Boolean = false, val error: String? = null,
    /** "your current area" for location-based times, otherwise the saved city. */
    val area: String? = null)

@HiltViewModel
class PrayerTimesViewModel @Inject constructor(private val repository: PrayerTimesRepository, private val store: PrivateRecordStore,
    private val autoLocate: PrayerAutoLocate, private val location: PrayerLocation, private val alarms: ReminderAlarmScheduler) : ViewModel() {
    private val _state = MutableStateFlow(PrayerTimesState()); val state = _state.asStateFlow()
    private var account = ""; private var zone = "UTC"; private var deen = false
    fun initialize(profile: UserProfile) {
        if (account == profile.userId && zone == profile.timezone) return
        account = profile.userId; zone = profile.timezone; deen = profile.deenEnabled
        viewModelScope.launch {
            val saved = try { store.read(account, PrayerAutoLocate.KEY) } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { _state.update { it.copy(error = "Saved prayer times could not be read.") }; null }
            if (saved != null) _state.value = PrayerTimesState(RecordRules.text(saved, "city"), RecordRules.text(saved, "country"),
                RecordRules.text(saved, "method").toIntOrNull()?.takeIf { it in PrayerTimeRules.methods } ?: 1,
                RecordRules.text(saved, "hanafi") != "false",
                saved.takeIf { RecordRules.text(it, "date") == AccountDate.today(zone).toString() && RecordRules.text(it, "zone") == zone },
                cached = true, area = PrayerAutoLocate.label(saved))
            // Location-based times refresh on their own; a chosen city is kept until the user changes it.
            if ((saved == null || Coordinates.from(saved) != null) && location.hasPermission() && _state.value.data == null) useLocation()
        }
    }
    fun canUseLocation() = location.hasPermission()
    fun locationDenied() = _state.update { it.copy(error = "Location was not allowed. Enter a city below, or allow location for NAFS in Android settings.") }
    fun city(value: String) { if (!_state.value.busy && value.length <= 100) _state.update { it.copy(city = value) } }
    fun country(value: String) { if (!_state.value.busy && value.length <= 100) _state.update { it.copy(country = value) } }
    fun method(value: Int) { if (_state.value.busy) return; _state.update { it.copy(method = value) }; refreshSource() }
    fun hanafi(value: Boolean) { if (_state.value.busy) return; _state.update { it.copy(hanafi = value) }; refreshSource() }
    fun invalidateDate() { _state.update { it.copy(data = it.data?.takeIf { data -> RecordRules.text(data, "date") == AccountDate.today(zone).toString() }) } }

    /** A changed calculation setting refetches from the current source, so the times and alarms stay consistent. */
    private fun refreshSource() {
        val current = _state.value
        when {
            Coordinates.from(current.data) != null && location.hasPermission() -> useLocation()
            current.data != null && current.city.isNotBlank() && current.country.isNotBlank() -> fetch()
        }
    }

    fun useLocation() {
        if (_state.value.busy) return
        val input = _state.value; val userId = account; val timezone = zone
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val result = autoLocate.run(userId, timezone, method = input.method, hanafi = input.hanafi)
                if (account != userId || zone != timezone) return@launch
                when (result) {
                    is PrayerLocateResult.Saved -> {
                        _state.update { it.copy(data = result.data, cached = false, area = PrayerAutoLocate.label(result.data), city = "", country = "") }
                        alarms.reschedule(userId, timezone, deen)
                    }
                    PrayerLocateResult.NoPermission -> locationDenied()
                    PrayerLocateResult.LocationOff -> _state.update { it.copy(error = "Location is turned off. Turn it on from quick settings, or enter a city below.") }
                    PrayerLocateResult.Unavailable -> _state.update { it.copy(error = "Your area could not be found right now. Try again in a moment, or enter a city below.") }
                    PrayerLocateResult.FetchFailed -> _state.update { it.copy(error = "Prayer times could not be fetched. Check your connection and retry.") }
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { _state.update { it.copy(error = "Prayer times could not be saved. Retry.") } }
            finally { _state.update { it.copy(busy = false) } }
        }
    }

    fun fetch() {
        if (_state.value.busy) return
        val input = _state.value; val userId = account; val timezone = zone
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val data = repository.fetch(input.city, input.country, input.method, input.hanafi, timezone, AccountDate.today(timezone))
                if (account != userId || zone != timezone) return@launch
                _state.update { it.copy(data = data, cached = false, area = PrayerAutoLocate.label(data)) }
                store.write(userId, PrayerAutoLocate.KEY, data)
                alarms.reschedule(userId, timezone, deen)
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { _state.update { it.copy(error = "Prayer times could not be refreshed. Check the city and country, and your connection.") } }
            finally { _state.update { it.copy(busy = false) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PrayerTimesRoute(profile: UserProfile, onBack: () -> Unit, viewModel: PrayerTimesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(profile.userId, profile.timezone) { viewModel.initialize(profile) }
    AccountDayRefreshEffects(profile.timezone, viewModel::invalidateDate)
    var now by remember(profile.timezone) { mutableStateOf(ZonedDateTime.now(ZoneId.of(profile.timezone))) }
    LaunchedEffect(profile.timezone) { while (true) { now = ZonedDateTime.now(ZoneId.of(profile.timezone)); delay(1_000) } }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.useLocation() else viewModel.locationDenied()
    }
    val locate = { if (viewModel.canUseLocation()) viewModel.useLocation() else locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
    Scaffold(topBar = { TopAppBar(title = { Text("Prayer times") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val today = state.data?.takeIf { RecordRules.text(it, "date") == now.toLocalDate().toString() }
            if (today != null) item {
                val timings = (today["times"] as JsonObject).mapValues { (it.value as JsonPrimitive).content }
                val next = PrayerTimeRules.next(timings, now.toLocalTime())
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Today · ${state.area ?: "saved location"}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                        Text(next?.let { "${it.first} in ${it.second.toHours()}h ${it.second.toMinutes() % 60}m" } ?: "Today's prayer times have passed.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        PrayerTimeRules.names.forEach { name ->
                            val upcoming = next?.first == name
                            val color = if (upcoming) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            val weight = if (upcoming) FontWeight.Bold else FontWeight.Normal
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = weight, color = color)
                                Text(timings[name].orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = weight, color = color)
                            }
                        }
                        Text("${RecordRules.text(today, "date")} · ${profile.timezone}${if (state.cached) " · saved copy" else ""}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Button(onClick = locate, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(when { state.busy -> "Finding prayer times…"; Coordinates.from(state.data) != null -> "Update for my current area"; else -> "Use my location" })
                }
            }
            item { Text("Uses your approximate location (about 1 km), only while NAFS is open. It is sent to Aladhan to calculate the times and is not stored on NAFS servers.", style = MaterialTheme.typography.bodySmall) }
            state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            item {
                Text("Calculation method", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrayerTimeRules.methods.forEach { (method, name) -> FilterChip(state.method == method, onClick = { viewModel.method(method) }, enabled = !state.busy, label = { Text(name) }) }
                }
            }
            item { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(state.hanafi, viewModel::hanafi, enabled = !state.busy); Text("Hanafi Asr") } }
            item { Text("Or enter a city", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() }) }
            item { OutlinedTextField(state.city, viewModel::city, label = { Text("City") }, enabled = !state.busy, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(state.country, viewModel::country, label = { Text("Country") }, enabled = !state.busy, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedButton(viewModel::fetch, enabled = !state.busy && state.city.isNotBlank() && state.country.isNotBlank()) { Text("Use this city") } }
            item { Text("Source: Aladhan. Confirm the method and your local mosque's times for your practice.", style = MaterialTheme.typography.bodySmall) }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}
