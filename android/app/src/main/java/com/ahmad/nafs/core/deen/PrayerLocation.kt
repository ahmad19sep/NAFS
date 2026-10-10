package com.ahmad.nafs.core.deen

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import java.util.concurrent.TimeUnit
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.ahmad.nafs.core.records.PrivateRecordStore
import com.ahmad.nafs.core.records.RecordRules
import com.ahmad.nafs.core.time.AccountDate
import com.ahmad.nafs.ui.deen.PrayerTimeRules
import com.ahmad.nafs.ui.deen.PrayerTimesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** Rounded to two decimals (about 1 km): enough for prayer times, without storing a precise position. */
data class Coordinates(val latitude: Double, val longitude: Double) {
    fun distanceKm(other: Coordinates): Double {
        val radians = Math.PI / 180
        val a = sin((other.latitude - latitude) * radians / 2).pow(2) +
            cos(latitude * radians) * cos(other.latitude * radians) * sin((other.longitude - longitude) * radians / 2).pow(2)
        return 2 * 6371.0 * asin(sqrt(a))
    }

    companion object {
        fun of(latitude: Double, longitude: Double): Coordinates? {
            if (!latitude.isFinite() || !longitude.isFinite() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
            return Coordinates((latitude * 100).roundToLong() / 100.0, (longitude * 100).roundToLong() / 100.0)
        }
        fun from(record: JsonObject?): Coordinates? {
            val latitude = RecordRules.text(record, "latitude").toDoubleOrNull() ?: return null
            val longitude = RecordRules.text(record, "longitude").toDoubleOrNull() ?: return null
            return of(latitude, longitude)
        }
    }
}

/** Approximate, foreground-only location. No background location permission is requested. */
@Singleton
class PrayerLocation @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager = context.getSystemService(LocationManager::class.java)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun isEnabled(): Boolean = manager != null && LocationManagerCompat.isLocationEnabled(manager)

    @SuppressLint("MissingPermission")
    suspend fun current(): Coordinates? {
        if (manager == null || !hasPermission()) return null
        // Before Android 12, approximate permission alone cannot use GPS or passive locations.
        val gpsAllowed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager.hasProvider(LocationManager.FUSED_PROVIDER)) add(LocationManager.FUSED_PROVIDER)
            if (runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)) add(LocationManager.NETWORK_PROVIDER)
            if (gpsAllowed && runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)) add(LocationManager.GPS_PROVIDER)
        }
        for (provider in providers) {
            withTimeoutOrNull(15_000L) { request(provider) }?.let { return Coordinates.of(it.latitude, it.longitude) }
        }
        // Only a recent fix: an old one may be from another city.
        val now = SystemClock.elapsedRealtimeNanos()
        return (providers + listOfNotNull(LocationManager.PASSIVE_PROVIDER.takeIf { gpsAllowed })).distinct()
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .filter { now - it.elapsedRealtimeNanos in 0..MAX_FIX_AGE_NANOS }
            .maxByOrNull(Location::getElapsedRealtimeNanos)?.let { Coordinates.of(it.latitude, it.longitude) }
    }

    private companion object {
        val MAX_FIX_AGE_NANOS = TimeUnit.MINUTES.toNanos(30)
    }

    @SuppressLint("MissingPermission")
    private suspend fun request(provider: String): Location? = suspendCancellableCoroutine { continuation ->
        val signal = CancellationSignal()
        continuation.invokeOnCancellation { signal.cancel() }
        try {
            LocationManagerCompat.getCurrentLocation(manager!!, provider, signal, ContextCompat.getMainExecutor(context)) { location ->
                if (continuation.isActive) continuation.resume(location)
            }
        } catch (_: SecurityException) {
            if (continuation.isActive) continuation.resume(null)
        } catch (_: IllegalArgumentException) {
            if (continuation.isActive) continuation.resume(null)
        }
    }
}

sealed interface PrayerLocateResult {
    /** [refreshed] is false when the saved times were still current for this area. */
    data class Saved(val data: JsonObject, val refreshed: Boolean = true) : PrayerLocateResult
    data object NoPermission : PrayerLocateResult
    data object LocationOff : PrayerLocateResult
    data object Unavailable : PrayerLocateResult
    data object FetchFailed : PrayerLocateResult
}

/** Finds today's prayer times for the current area and saves them where Prayer times and reminders read them. */
@Singleton
class PrayerAutoLocate @Inject constructor(
    private val location: PrayerLocation,
    private val repository: PrayerTimesRepository,
    private val store: PrivateRecordStore,
) {
    /** With [onlyIfMoved], an existing city choice is kept and a same-day location result is reused unless the user moved over 5 km. */
    suspend fun run(user: String, zone: String, onlyIfMoved: Boolean = false, method: Int? = null, hanafi: Boolean? = null): PrayerLocateResult {
        val saved = try { store.read(user, KEY) } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { null }
        val savedHere = Coordinates.from(saved)
        if (onlyIfMoved && saved != null && savedHere == null) return PrayerLocateResult.Saved(saved, refreshed = false)
        if (!location.hasPermission()) return PrayerLocateResult.NoPermission
        if (!location.isEnabled()) return PrayerLocateResult.LocationOff
        val here = location.current() ?: return PrayerLocateResult.Unavailable
        val today = AccountDate.today(zone)
        if (onlyIfMoved && saved != null && savedHere != null && savedHere.distanceKm(here) < 5.0 &&
            RecordRules.text(saved, "date") == today.toString() && RecordRules.text(saved, "zone") == zone) return PrayerLocateResult.Saved(saved, refreshed = false)
        // Until the user chooses a convention, use the one most mosques in the account's region follow.
        val regional = PrayerCalculationDefaults.forZone(zone)
        val chosenMethod = method?.takeIf { it in PrayerTimeRules.methods }
            ?: RecordRules.text(saved, "method").toIntOrNull()?.takeIf { it in PrayerTimeRules.methods } ?: regional.method
        val chosenHanafi = hanafi ?: (saved?.get("hanafi") as? JsonPrimitive)?.booleanOrNull ?: regional.hanafi
        val data = try { repository.fetchAt(here, chosenMethod, chosenHanafi, zone, today) }
            catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { return PrayerLocateResult.FetchFailed }
        if (onlyIfMoved) {
            // The user may have chosen a city or refreshed while this ran; never replace a newer choice.
            if (!store.writeIf(user, KEY, data) { it == saved }) return PrayerLocateResult.Saved(store.read(user, KEY) ?: data, refreshed = false)
        } else store.write(user, KEY, data)
        return PrayerLocateResult.Saved(data)
    }

    companion object {
        const val KEY = "prayer-times"
        fun label(record: JsonObject?): String? = when {
            record == null -> null
            Coordinates.from(record) != null -> "your current area"
            else -> RecordRules.text(record, "city").takeIf { it.isNotBlank() }
        }
    }
}
