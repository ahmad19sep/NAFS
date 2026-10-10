package com.ahmad.nafs.ui.deen

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ahmad.nafs.core.deen.Coordinates
import com.ahmad.nafs.core.deen.PrayerAutoLocate
import com.ahmad.nafs.core.deen.PrayerLocateResult
import com.ahmad.nafs.core.deen.PrayerLocation
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.core.records.PrivateRecordStore
import com.ahmad.nafs.core.records.RecordRules
import com.ahmad.nafs.core.reminders.ReminderAlarmScheduler
import com.ahmad.nafs.core.time.AccountDate
import com.ahmad.nafs.ui.AccountDayRefreshEffects
import com.ahmad.nafs.ui.components.NafsBanner
import com.ahmad.nafs.ui.components.NafsBannerKind
import com.ahmad.nafs.ui.components.NafsCard
import com.ahmad.nafs.ui.components.NafsChoiceChip
import com.ahmad.nafs.ui.components.NafsDivider
import com.ahmad.nafs.ui.components.NafsEmptyState
import com.ahmad.nafs.ui.components.NafsEyebrow
import com.ahmad.nafs.ui.components.NafsHeroSurface
import com.ahmad.nafs.ui.components.NafsIconBadge
import com.ahmad.nafs.ui.components.NafsListRow
import com.ahmad.nafs.ui.components.NafsPill
import com.ahmad.nafs.ui.components.NafsPrimaryButton
import com.ahmad.nafs.ui.components.NafsProgressBar
import com.ahmad.nafs.ui.components.NafsSecondaryButton
import com.ahmad.nafs.ui.components.NafsSectionTitle
import com.ahmad.nafs.ui.components.NafsSpace
import com.ahmad.nafs.ui.components.NafsTextField
import com.ahmad.nafs.ui.components.NafsTopBar
import com.ahmad.nafs.ui.theme.AmiriArabic
import com.ahmad.nafs.ui.theme.Nafs
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
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

    /**
     * A changed calculation setting refetches from the saved source (area or city), so the times and alarms stay
     * consistent. Saved coordinates need no location permission or new fix; a typed but unconfirmed city is ignored.
     */
    private fun refreshSource() {
        val input = _state.value; val userId = account; val timezone = zone
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val saved = store.read(userId, PrayerAutoLocate.KEY) ?: return@launch
                val today = AccountDate.today(timezone)
                val area = Coordinates.from(saved)
                val city = RecordRules.text(saved, "city"); val country = RecordRules.text(saved, "country")
                val data = when {
                    area != null -> repository.fetchAt(area, input.method, input.hanafi, timezone, today)
                    city.isNotBlank() && country.isNotBlank() -> repository.fetch(city, country, input.method, input.hanafi, timezone, today)
                    else -> return@launch
                }
                if (account != userId || zone != timezone) return@launch
                store.write(userId, PrayerAutoLocate.KEY, data)
                _state.update { it.copy(data = data, cached = false, area = PrayerAutoLocate.label(data)) }
                alarms.reschedule(userId, timezone, deen)
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { _state.update { it.copy(error = "The new calculation setting could not be applied. Check your connection and retry.") } }
            finally { _state.update { it.copy(busy = false) } }
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
    PrayerTimesScreen(
        state = state,
        now = now,
        timezone = profile.timezone,
        actions = PrayerTimesActions(
            onBack = onBack,
            onLocate = {
                if (viewModel.canUseLocation()) viewModel.useLocation()
                else locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            },
            onMethod = viewModel::method,
            onHanafi = viewModel::hanafi,
            onCityChange = viewModel::city,
            onCountryChange = viewModel::country,
            onUseCity = viewModel::fetch,
        ),
    )
}

/** Everything Prayer times can ask for, so the screen renders from plain state. */
internal data class PrayerTimesActions(
    val onBack: () -> Unit = {},
    val onLocate: () -> Unit = {},
    val onMethod: (Int) -> Unit = {},
    val onHanafi: (Boolean) -> Unit = {},
    val onCityChange: (String) -> Unit = {},
    val onCountryChange: (String) -> Unit = {},
    val onUseCity: () -> Unit = {},
)

@Composable
internal fun PrayerTimesScreen(state: PrayerTimesState, now: ZonedDateTime, timezone: String, actions: PrayerTimesActions) {
    val today = state.data?.takeIf { RecordRules.text(it, "date") == now.toLocalDate().toString() }
    val timings = today?.let { record -> (record["times"] as JsonObject).mapValues { (it.value as JsonPrimitive).content } }
    val next = timings?.let { PrayerTimeRules.next(it, now.toLocalTime()) }
    val usingArea = Coordinates.from(state.data) != null

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        NafsTopBar(title = "Prayer times", onBack = actions.onBack, subtitle = state.area?.let { "For $it" } ?: "Today's five times")
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = NafsSpace.screen, end = NafsSpace.screen, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (timings != null) {
                item {
                    NextPrayerHero(
                        timings = timings,
                        next = next,
                        now = now.toLocalTime(),
                        place = (state.area ?: "saved location").replaceFirstChar { it.uppercase() },
                        detail = buildString {
                            append(shortDate(RecordRules.text(today, "date")))
                            append(" · ")
                            append(timezone)
                            if (state.cached) append(" · saved copy")
                        },
                    )
                }
                item { TimesCard(timings, next?.first) }
            } else {
                item { NoTimesCard(state.busy) }
            }
            state.error?.let { message -> item { NafsBanner(message, kind = NafsBannerKind.ERROR) } }

            item { NafsSectionTitle("Location", supporting = "The quickest way to accurate times.") }
            item { LocationCard(state.busy, usingArea, actions.onLocate) }

            item { NafsSectionTitle("Calculation", supporting = "Match the convention your local mosque follows.") }
            item { CalculationCard(state.method, state.hanafi, state.busy, actions.onMethod, actions.onHanafi) }

            item { NafsSectionTitle("Or choose a city", supporting = "Times stay fixed to this city until you change it.") }
            item { CityCard(state.city, state.country, state.busy, actions) }

            item { SourceNote() }
        }
    }
}

// ─── Today ───────────────────────────────────────────────────────────────────

@Composable
private fun NextPrayerHero(timings: Map<String, String>, next: Pair<String, Duration>?, now: LocalTime, place: String, detail: String) {
    val c = Nafs.colors
    NafsHeroSurface {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            NafsEyebrow(if (next != null) "Next prayer" else "Today", color = c.heroGold)
            if (next != null) {
                val (name, remaining) = next
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(name, style = MaterialTheme.typography.headlineLarge, color = c.onHero, maxLines = 1)
                    Text(arabicName(name), style = HeroArabic, color = c.heroGold, maxLines = 1, modifier = Modifier.padding(bottom = 2.dp))
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(timings[name].orEmpty(), style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"), color = c.onHero)
                        Text("in ${countdown(remaining)}", style = MaterialTheme.typography.labelMedium, color = c.onHeroMuted)
                    }
                }
                sinceLastPrayer(timings, name, now)?.let { (previous, fraction) ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        NafsProgressBar(fraction, color = c.heroMint, trackColor = Color.White.copy(alpha = 0.12f), height = 6.dp)
                        Row {
                            Text(
                                "$previous ${timings[previous].orEmpty()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = c.onHeroMuted,
                                modifier = Modifier.weight(1f),
                            )
                            Text("$name ${timings[name].orEmpty()}", style = MaterialTheme.typography.labelSmall, color = c.onHeroMuted)
                        }
                    }
                }
            } else {
                Text("Today's prayer times have passed.", style = MaterialTheme.typography.headlineSmall, color = c.onHero)
                Text("May Allah accept your prayers.", style = MaterialTheme.typography.bodyMedium, color = c.onHeroMuted)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = c.onHeroMuted, modifier = Modifier.size(14.dp))
                Text("$place · $detail", style = MaterialTheme.typography.labelMedium, color = c.onHeroMuted, maxLines = 2)
            }
        }
    }
}

@Composable
private fun TimesCard(timings: Map<String, String>, next: String?) {
    val nextIndex = next?.let { PrayerTimeRules.names.indexOf(it) } ?: PrayerTimeRules.names.size
    NafsCard(contentPadding = PaddingValues(6.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            PrayerTimeRules.names.forEachIndexed { index, name ->
                TimeRow(name, timings[name].orEmpty(), isNext = index == nextIndex, passed = index < nextIndex)
            }
        }
    }
}

@Composable
private fun TimeRow(name: String, time: String, isNext: Boolean, passed: Boolean) {
    val c = Nafs.colors
    val scheme = MaterialTheme.colorScheme
    val textColor = when {
        isNext -> c.deen
        passed -> scheme.onSurfaceVariant
        else -> scheme.onSurface
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(if (isNext) c.deen.copy(alpha = if (c.isDark) 0.14f else 0.08f) else Color.Transparent)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NafsIconBadge(prayerIcon(name), if (isNext) c.deen else scheme.onSurfaceVariant, size = 38.dp, iconSize = 19.dp)
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = if (passed) scheme.onSurfaceVariant else scheme.onSurface)
            Text(arabicName(name), style = RowArabic, color = if (isNext) c.deen else scheme.onSurfaceVariant)
        }
        if (isNext) NafsPill("Next", color = c.deen)
        Text(
            time.ifBlank { "—" },
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            color = textColor,
        )
    }
}

@Composable
private fun NoTimesCard(busy: Boolean) {
    if (busy) {
        NafsCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp), color = Nafs.colors.deen, strokeWidth = 2.5.dp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Finding today's prayer times…", style = MaterialTheme.typography.titleSmall)
                    Text("Hold on a moment.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    } else {
        NafsEmptyState(
            icon = Icons.Rounded.AccessTime,
            title = "No times for today yet",
            message = "Use your location or choose a city below to see today's prayer times.",
            tint = Nafs.colors.deen,
        )
    }
}

// ─── Settings ────────────────────────────────────────────────────────────────

@Composable
private fun LocationCard(busy: Boolean, usingArea: Boolean, onLocate: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    NafsCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                NafsIconBadge(Icons.Rounded.MyLocation, Nafs.colors.deen)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (usingArea) "Using your current area" else "Use your location", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (busy) "Finding prayer times…" else "Approximate, about 1 km. Only while NAFS is open.",
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                    )
                }
            }
            NafsPrimaryButton(
                text = if (usingArea) "Update for my current area" else "Use my location",
                onClick = onLocate,
                enabled = !busy,
                loading = busy,
                icon = Icons.Rounded.MyLocation,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Shield, contentDescription = null, tint = muted, modifier = Modifier.padding(top = 1.dp).size(14.dp))
                Text(
                    "Uses your approximate location (about 1 km), only while NAFS is open. It is sent to Aladhan to calculate " +
                        "the times and is not stored on NAFS servers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalculationCard(method: Int, hanafi: Boolean, busy: Boolean, onMethod: (Int) -> Unit, onHanafi: (Boolean) -> Unit) {
    val deen = Nafs.colors.deen
    NafsCard(contentPadding = PaddingValues(vertical = 4.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                NafsIconBadge(Icons.Rounded.Tune, deen, size = 38.dp, iconSize = 20.dp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Calculation method", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Sets the sun angles used for Fajr and Isha.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrayerTimeRules.methods.forEach { (id, label) ->
                    NafsChoiceChip(text = label, selected = method == id, onClick = { onMethod(id) }, enabled = !busy)
                }
            }
        }
        NafsDivider()
        NafsListRow(
            title = "Hanafi Asr",
            subtitle = if (hanafi) "Later Asr time, as in the Hanafi school" else "Standard Asr time (Shafi'i, Maliki, Hanbali)",
            icon = Icons.Rounded.LightMode,
            tint = deen,
            trailing = {
                Switch(
                    checked = hanafi,
                    onCheckedChange = onHanafi,
                    enabled = !busy,
                    modifier = Modifier.semantics { contentDescription = "Hanafi Asr" },
                )
            },
        )
    }
}

@Composable
private fun CityCard(city: String, country: String, busy: Boolean, actions: PrayerTimesActions) {
    val canUseCity = !busy && city.isNotBlank() && country.isNotBlank()
    NafsCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NafsTextField(
                value = city,
                onValueChange = actions.onCityChange,
                label = "City",
                placeholder = "For example, Lahore",
                leadingIcon = Icons.Rounded.LocationCity,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            )
            NafsTextField(
                value = country,
                onValueChange = actions.onCountryChange,
                label = "Country",
                placeholder = "For example, Pakistan",
                leadingIcon = Icons.Rounded.Public,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canUseCity) actions.onUseCity() }),
            )
            NafsSecondaryButton(
                "Use this city",
                onClick = actions.onUseCity,
                enabled = canUseCity,
                icon = Icons.Rounded.LocationOn,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SourceNote() {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.Info, contentDescription = null, tint = muted, modifier = Modifier.padding(top = 1.dp).size(14.dp))
        Text(
            "Source: Aladhan. Confirm the method and your local mosque's times for your practice.",
            style = MaterialTheme.typography.bodySmall,
            color = muted,
        )
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private val HeroArabic = TextStyle(fontFamily = AmiriArabic, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 34.sp)
private val RowArabic = TextStyle(fontFamily = AmiriArabic, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp)

private fun arabicName(name: String): String = when (name) {
    "Fajr" -> "الفجر"
    "Dhuhr" -> "الظهر"
    "Asr" -> "العصر"
    "Maghrib" -> "المغرب"
    "Isha" -> "العشاء"
    else -> ""
}

private fun prayerIcon(name: String): ImageVector = when (name) {
    "Fajr", "Maghrib" -> Icons.Rounded.WbTwilight
    "Dhuhr" -> Icons.Rounded.WbSunny
    "Asr" -> Icons.Rounded.LightMode
    else -> Icons.Rounded.NightsStay
}

private fun countdown(remaining: Duration): String {
    val hours = remaining.toHours()
    val minutes = remaining.toMinutes() % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "$minutes min"
        else -> "under a minute"
    }
}

/** The prayer before [next] and how far the day has moved from it towards [next], when both times are known. */
private fun sinceLastPrayer(timings: Map<String, String>, next: String, now: LocalTime): Pair<String, Float>? {
    val parsed = PrayerTimeRules.names.mapNotNull { prayer ->
        timings[prayer]?.let { raw -> runCatching { PrayerTimeRules.time(raw) }.getOrNull() }?.let { time -> prayer to time }
    }
    val index = parsed.indexOfFirst { it.first == next }
    if (index <= 0) return null
    val (previous, start) = parsed[index - 1]
    val total = Duration.between(start, parsed[index].second).seconds.toFloat()
    if (total <= 0f) return null
    return previous to (Duration.between(start, now).seconds / total).coerceIn(0f, 1f)
}

private fun shortDate(raw: String): String =
    runCatching { LocalDate.parse(raw).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)) }.getOrDefault(raw)
