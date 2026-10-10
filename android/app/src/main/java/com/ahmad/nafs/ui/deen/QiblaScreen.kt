package com.ahmad.nafs.ui.deen

import android.Manifest
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ahmad.nafs.core.deen.Coordinates
import com.ahmad.nafs.core.deen.HeadingSmoother
import com.ahmad.nafs.core.deen.NextPrayerRules
import com.ahmad.nafs.core.deen.PrayerCalculationDefaults
import com.ahmad.nafs.core.deen.PrayerLocation
import com.ahmad.nafs.core.deen.QiblaAlignmentCue
import com.ahmad.nafs.core.deen.QiblaMath
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.core.records.PrivateRecordStore
import com.ahmad.nafs.core.records.RecordRules
import com.ahmad.nafs.core.time.AccountDate
import com.ahmad.nafs.ui.components.NafsBanner
import com.ahmad.nafs.ui.components.NafsBannerKind
import com.ahmad.nafs.ui.components.NafsCard
import com.ahmad.nafs.ui.components.NafsDivider
import com.ahmad.nafs.ui.components.NafsEyebrow
import com.ahmad.nafs.ui.components.NafsHeroSurface
import com.ahmad.nafs.ui.components.NafsIconBadge
import com.ahmad.nafs.ui.components.NafsListRow
import com.ahmad.nafs.ui.components.NafsPrimaryButton
import com.ahmad.nafs.ui.components.NafsSecondaryButton
import com.ahmad.nafs.ui.components.NafsSpace
import com.ahmad.nafs.ui.components.NafsTopBar
import com.ahmad.nafs.ui.theme.Nafs
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import android.view.Surface as DisplaySurface

/** Where the Qibla is measured from, and how the screen names that place. */
data class QiblaOrigin(val at: Coordinates, val label: String) {
    val bearing: Double get() = QiblaMath.bearing(at)
    val distanceKm: Double get() = QiblaMath.distanceKm(at)
}

data class QiblaUiState(
    val loading: Boolean = true,
    val origin: QiblaOrigin? = null,
    /** A chosen city whose position is not saved yet (saved before NAFS kept it) and could not be looked up. */
    val savedCity: String? = null,
    val locating: Boolean = false,
    val error: String? = null,
)

/** What the compass sensor reports, in the form the screen draws. */
data class QiblaCompass(
    /** Degrees from true north that the top of the screen points to; null before the first reading. */
    val heading: Double? = null,
    /** A SensorManager.SENSOR_STATUS_* value; null until the sensor reports one. */
    val accuracy: Int? = null,
    val hasSensor: Boolean = true,
) {
    val needsCalibration: Boolean get() = hasSensor && accuracy != null && accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
}

@HiltViewModel
class QiblaViewModel @Inject constructor(
    private val store: PrivateRecordStore,
    private val location: PrayerLocation,
    private val repository: PrayerTimesRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(QiblaUiState())
    val state = _state.asStateFlow()
    private var account: String? = null
    private var zone = "UTC"

    fun initialize(userId: String, timezone: String) {
        if (account == userId && zone == timezone) return
        account = userId
        zone = timezone
        _state.value = QiblaUiState()
        viewModelScope.launch { loadSaved(userId, timezone) }
    }

    fun canUseLocation() = location.hasPermission()

    fun locationDenied() = _state.update {
        it.copy(locating = false, error = "Location was not allowed. Allow it for NAFS in Android settings, or choose a city in Prayer times.")
    }

    /** The current approximate position, used on this screen only; the saved prayer-times place is not changed. */
    fun useLocation() {
        val user = account ?: return
        if (_state.value.locating) return
        if (!location.hasPermission()) return locationDenied()
        if (!location.isEnabled()) {
            _state.update { it.copy(error = "Location is turned off. Turn it on from quick settings and try again.") }
            return
        }
        _state.update { it.copy(locating = true, error = null) }
        viewModelScope.launch {
            val here = try { location.current() } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { null }
            if (account != user) return@launch
            _state.update {
                if (here == null) it.copy(locating = false, error = "Your location could not be found right now. Try again in a moment.")
                else it.copy(locating = false, loading = false, origin = QiblaOrigin(here, "your current location"), error = null)
            }
        }
    }

    private suspend fun loadSaved(userId: String, timezone: String) {
        val saved = try {
            store.read(userId, NextPrayerRules.TODAY_KEY) ?: store.read(userId, NextPrayerRules.NEXT_KEY)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            null
        }
        val city = RecordRules.text(saved, "city")
        QiblaMath.origin(saved)?.let { at ->
            val label = if (Coordinates.from(saved) != null) "your prayer-times area" else city
            _state.update { it.copy(loading = false, origin = QiblaOrigin(at, label)) }
            return
        }
        val country = RecordRules.text(saved, "country")
        if (saved == null || city.isBlank() || country.isBlank()) {
            _state.update { it.copy(loading = false) }
            return
        }
        // A city chosen before NAFS kept its position: look it up once and keep it with the saved times.
        val refreshed = try { fetchCity(saved, city, country, timezone) } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { null }
        val at = QiblaMath.origin(refreshed)
        if (refreshed != null && at != null) {
            try {
                store.writeIf(userId, NextPrayerRules.TODAY_KEY, refreshed) { it == saved }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                // The direction still shows; it is looked up again next time.
            }
        }
        if (account != userId) return
        _state.update { it.copy(loading = false, origin = at?.let { place -> QiblaOrigin(place, city) }, savedCity = city.takeIf { at == null }) }
    }

    private suspend fun fetchCity(saved: JsonObject, city: String, country: String, timezone: String): JsonObject {
        val regional = PrayerCalculationDefaults.forZone(timezone)
        val method = RecordRules.text(saved, "method").toIntOrNull()?.takeIf { it in PrayerTimeRules.methods } ?: regional.method
        val hanafi = (saved["hanafi"] as? JsonPrimitive)?.booleanOrNull ?: regional.hanafi
        return repository.fetch(city, country, method, hanafi, timezone, AccountDate.today(timezone))
    }
}

@Composable
fun QiblaRoute(profile: UserProfile, onBack: () -> Unit, onOpenPrayerTimes: () -> Unit, viewModel: QiblaViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(profile.userId, profile.timezone) { viewModel.initialize(profile.userId, profile.timezone) }
    val compass = rememberQiblaCompass(state.origin?.at)
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.useLocation() else viewModel.locationDenied()
    }
    // A single confirming haptic each time the phone comes round to the Qibla.
    val haptics = LocalHapticFeedback.current
    val bearing = state.origin?.bearing
    val turn by rememberUpdatedState(compass.heading?.let { heading -> bearing?.let { QiblaMath.delta(heading, it) } })
    LaunchedEffect(bearing) {
        val cue = QiblaAlignmentCue()
        snapshotFlow { turn }.collect { value ->
            if (value != null && cue.update(value)) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        }
    }
    QiblaScreen(
        state = state,
        compass = compass,
        actions = QiblaActions(
            onBack = onBack,
            onUseLocation = {
                if (viewModel.canUseLocation()) viewModel.useLocation()
                else locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            },
            onOpenPrayerTimes = onOpenPrayerTimes,
        ),
    )
}

/**
 * Heading from the rotation-vector sensor, corrected from magnetic to true north for [at] and smoothed. The listener
 * runs only while the screen is resumed and is always removed when it leaves the composition.
 */
@Composable
private fun rememberQiblaCompass(at: Coordinates?): QiblaCompass {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val manager = remember(context) { context.getSystemService(SensorManager::class.java) }
    val sensor = remember(manager) { manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) }
    var reading by remember(sensor) { mutableStateOf(QiblaCompass(hasSensor = sensor != null)) }
    val declination = remember(at) {
        at?.let { GeomagneticField(it.latitude.toFloat(), it.longitude.toFloat(), 0f, System.currentTimeMillis()).declination.toDouble() } ?: 0.0
    }
    DisposableEffect(manager, sensor, lifecycle, declination) {
        if (manager == null || sensor == null) return@DisposableEffect onDispose { }
        val smoother = HeadingSmoother()
        val matrix = FloatArray(9)
        val orientation = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // Some devices send more values than getRotationMatrixFromVector accepts; the first four are enough.
                SensorManager.getRotationMatrixFromVector(matrix, event.values.copyOf(minOf(event.values.size, 4)))
                SensorManager.getOrientation(matrix, orientation)
                val turns = view.display?.rotation ?: DisplaySurface.ROTATION_0
                val magnetic = QiblaMath.screenHeading(Math.toDegrees(orientation[0].toDouble()), turns)
                reading = reading.copy(heading = smoother.update(magnetic + declination), accuracy = event.accuracy)
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                reading = reading.copy(accuracy = accuracy)
            }
        }
        var registered = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (!registered) {
                    registered = manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
                }
                Lifecycle.Event.ON_PAUSE -> if (registered) {
                    manager.unregisterListener(listener)
                    registered = false
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (registered) manager.unregisterListener(listener)
        }
    }
    return reading
}

internal data class QiblaActions(
    val onBack: () -> Unit = {},
    val onUseLocation: () -> Unit = {},
    val onOpenPrayerTimes: () -> Unit = {},
)

@Composable
internal fun QiblaScreen(state: QiblaUiState, compass: QiblaCompass, actions: QiblaActions) {
    val origin = state.origin
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        NafsTopBar(
            title = "Qibla",
            onBack = actions.onBack,
            subtitle = origin?.let { "From ${it.label}" } ?: "Direction of the Kaaba",
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = NafsSpace.screen, end = NafsSpace.screen, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            state.error?.let { NafsBanner(it, kind = NafsBannerKind.ERROR) }
            when {
                state.loading -> QiblaLoadingCard()
                origin == null -> NoOriginCard(state.savedCity, state.locating, actions)
                else -> {
                    if (compass.needsCalibration) CalibrationCard()
                    QiblaHero(origin, compass)
                    QiblaDetails(origin, state.locating, actions.onUseLocation)
                    QiblaNote()
                }
            }
        }
    }
}

// ─── Compass ─────────────────────────────────────────────────────────────────

@Composable
private fun QiblaHero(origin: QiblaOrigin, compass: QiblaCompass) {
    val c = Nafs.colors
    val bearing = origin.bearing
    val heading = compass.heading.takeIf { compass.hasSensor }
    val turn = heading?.let { QiblaMath.delta(it, bearing) }
    val facing = turn != null && abs(turn) <= QiblaMath.FACING_TOLERANCE
    val atKaaba = origin.distanceKm < 1.0
    val degrees = bearing.roundToInt() % 360
    // The headline changes only when the instruction does (not with every degree), so it can be announced politely.
    val status = when {
        atKaaba -> "You're at the Masjid al-Haram"
        !compass.hasSensor -> "Face $degrees° from north"
        turn == null -> "Finding your heading…"
        facing -> "You're facing the Qibla"
        turn > 0 -> "Turn right"
        else -> "Turn left"
    }
    val detail = when {
        atKaaba -> "Face the Kaaba directly."
        !compass.hasSensor -> "This phone has no compass sensor. The Qibla is $degrees° clockwise from north, towards the " +
            "${QiblaMath.compassPoint(bearing)}."
        turn == null -> "Hold your phone flat, with the screen facing up."
        facing -> "Within ${QiblaMath.FACING_TOLERANCE.roundToInt()}° of the Kaaba's direction."
        else -> "${abs(turn).roundToInt()}° to go. Turn until the Kaaba reaches the gold marker at the top."
    }
    NafsHeroSurface {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            NafsEyebrow("Qibla · $degrees° from north", color = c.heroGold)
            QiblaDial(bearing = bearing, heading = heading, facing = facing)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (facing) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = c.heroMint, modifier = Modifier.size(22.dp))
                    Text(
                        status,
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (facing) c.heroMint else c.onHero,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = c.onHeroMuted, textAlign = TextAlign.Center)
            }
        }
    }
}

/**
 * The dial turns with the phone so north stays north; the Kaaba sits on it at the Qibla bearing and the fixed gold
 * marker at the top shows where the phone points. Without a heading the dial stays north-up.
 */
@Composable
private fun QiblaDial(bearing: Double, heading: Double?, facing: Boolean) {
    val c = Nafs.colors
    val dialTurn = -(heading ?: 0.0).toFloat()
    val ring = if (facing) c.heroMint else Color.White.copy(alpha = 0.22f)
    val description = when (heading) {
        null -> "Compass showing the Qibla at ${bearing.roundToInt()} degrees from north."
        else -> "Compass. The phone points ${heading.roundToInt()} degrees from north; the Qibla is ${bearing.roundToInt()} degrees."
    }
    Box(Modifier.size(260.dp).clearAndSetSemantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().rotate(dialTurn)) {
            Canvas(Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2
                drawCircle(Color.White.copy(alpha = 0.05f), radius = radius)
                drawCircle(ring, radius = radius - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
                for (step in 0 until 72) {
                    val major = step % 6 == 0
                    rotate(step * 5f) {
                        drawLine(
                            color = Color.White.copy(alpha = if (major) 0.55f else 0.22f),
                            start = Offset(center.x, center.y - radius + 8.dp.toPx()),
                            end = Offset(center.x, center.y - radius + (if (major) 20.dp else 14.dp).toPx()),
                            strokeWidth = (if (major) 2.dp else 1.dp).toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
                // The Qibla line from the centre towards the Kaaba.
                rotate(bearing.toFloat()) {
                    drawLine(
                        color = c.heroGold.copy(alpha = 0.75f),
                        start = center,
                        end = Offset(center.x, center.y - radius + 62.dp.toPx()),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
            listOf(0f to "N", 90f to "E", 180f to "S", 270f to "W").forEach { (angle, letter) ->
                Box(Modifier.fillMaxSize().rotate(angle), contentAlignment = Alignment.TopCenter) {
                    Text(
                        letter,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (letter == "N") c.heroGold else c.onHeroMuted,
                        // Upright on screen whichever way the dial has turned.
                        modifier = Modifier.padding(top = 26.dp).rotate(-(angle + dialTurn)),
                    )
                }
            }
            Box(Modifier.fillMaxSize().rotate(bearing.toFloat()), contentAlignment = Alignment.TopCenter) {
                // Kept upright whichever way the dial turns.
                KaabaMarker(Modifier.padding(top = 24.dp).rotate(-(bearing.toFloat() + dialTurn)))
            }
        }
        // Fixed: where the top of the phone points.
        Canvas(Modifier.fillMaxSize()) {
            val pointer = Path().apply {
                moveTo(center.x, 0f)
                lineTo(center.x - 9.dp.toPx(), 16.dp.toPx())
                lineTo(center.x + 9.dp.toPx(), 16.dp.toPx())
                close()
            }
            drawPath(pointer, color = if (facing) c.heroMint else c.heroGold)
            drawCircle(c.onHero, radius = 6.dp.toPx())
            drawCircle(if (facing) c.heroMint else c.heroGold, radius = 3.dp.toPx())
        }
    }
}

/** A small Kaaba: a dark cube with a gold band. */
@Composable
private fun KaabaMarker(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(5.dp)
    Box(
        modifier
            .size(30.dp)
            .clip(shape)
            .background(Color(0xFF14171A))
            .border(1.dp, Nafs.colors.heroGold.copy(alpha = 0.8f), shape),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(Modifier.padding(top = 7.dp).fillMaxWidth().height(4.dp).background(Nafs.colors.heroGold))
    }
}

// ─── States and details ──────────────────────────────────────────────────────

@Composable
private fun QiblaLoadingCard() {
    NafsCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator(Modifier.size(22.dp), color = Nafs.colors.deen, strokeWidth = 2.5.dp)
            Text("Finding the Qibla from your saved place…", style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun NoOriginCard(savedCity: String?, locating: Boolean, actions: QiblaActions) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    NafsCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NafsIconBadge(Icons.Rounded.Explore, Nafs.colors.deen, size = 64.dp, iconSize = 30.dp)
            Spacer(Modifier.height(4.dp))
            Text("Find the Qibla", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                if (savedCity != null) "Connect to the internet once to find the direction from $savedCity, or use your current location."
                else "Use your location, or choose your city in Prayer times, to see the direction of the Kaaba.",
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            NafsPrimaryButton(
                "Use my location",
                onClick = actions.onUseLocation,
                loading = locating,
                icon = Icons.Rounded.MyLocation,
                modifier = Modifier.fillMaxWidth(),
            )
            NafsSecondaryButton("Set up prayer times", onClick = actions.onOpenPrayerTimes, modifier = Modifier.fillMaxWidth())
            Text(
                "Your approximate location (about 1 km) is used on this phone only and is not saved.",
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CalibrationCard() {
    val c = Nafs.colors
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = c.warningContainer,
        contentColor = c.onWarningContainer,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(c.onWarningContainer.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.AllInclusive, contentDescription = null, modifier = Modifier.size(26.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Calibrate your compass", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Move your phone in a figure-8 a few times, away from metal and magnets, until this note goes away.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun QiblaDetails(origin: QiblaOrigin, locating: Boolean, onUseLocation: () -> Unit) {
    val deen = Nafs.colors.deen
    val bearing = origin.bearing
    NafsCard(contentPadding = PaddingValues(vertical = 4.dp)) {
        NafsListRow(
            title = "${bearing.roundToInt() % 360}° from true north",
            subtitle = "Towards the ${QiblaMath.compassPoint(bearing)}",
            icon = Icons.Rounded.Explore,
            tint = deen,
        )
        NafsDivider()
        NafsListRow(
            title = "${"%,d".format(Locale.ENGLISH, origin.distanceKm.roundToInt())} km to the Kaaba",
            subtitle = "Great-circle distance from ${origin.label}",
            icon = Icons.Rounded.Straighten,
            tint = deen,
        )
        NafsDivider()
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NafsSecondaryButton(
                if (locating) "Finding your location…" else "Use my current location",
                onClick = onUseLocation,
                enabled = !locating,
                icon = Icons.Rounded.MyLocation,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun QiblaNote() {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.Info, contentDescription = null, tint = muted, modifier = Modifier.padding(top = 1.dp).size(14.dp))
        Text(
            "Phone compasses can be thrown off by cases, magnets and metal nearby. When in doubt, follow your mosque's mihrab.",
            style = MaterialTheme.typography.bodySmall,
            color = muted,
        )
    }
}
