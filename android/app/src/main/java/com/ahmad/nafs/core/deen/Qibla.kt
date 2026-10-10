package com.ahmad.nafs.core.deen

import com.ahmad.nafs.core.records.RecordRules
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.serialization.json.JsonObject

/** Direction and distance to the Kaaba, and the angle helpers a compass needs. All angles are degrees from true north. */
object QiblaMath {
    /** The Kaaba in Makkah. */
    val KAABA = Coordinates(21.4225, 39.8262)

    /** Keys under which a city-based prayer-times record keeps the position Aladhan found for the city. */
    const val CITY_LATITUDE = "qiblaLatitude"
    const val CITY_LONGITUDE = "qiblaLongitude"

    /** Facing within this many degrees of the Qibla counts as facing it. */
    const val FACING_TOLERANCE = 5.0

    /** Initial great-circle bearing from [from] to the Kaaba, in [0, 360). */
    fun bearing(from: Coordinates): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(KAABA.latitude)
        val dLon = Math.toRadians(KAABA.longitude - from.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return normalize(Math.toDegrees(atan2(y, x)))
    }

    fun distanceKm(from: Coordinates): Double = from.distanceKm(KAABA)

    fun normalize(degrees: Double): Double {
        val value = degrees % 360.0
        return if (value < 0) value + 360.0 else value
    }

    /** The signed shortest turn from [from] to [to], in (−180, 180]; positive turns clockwise. */
    fun delta(from: Double, to: Double): Double {
        val turn = normalize(to - from)
        return if (turn > 180.0) turn - 360.0 else turn
    }

    fun isFacing(heading: Double, bearing: Double, tolerance: Double = FACING_TOLERANCE): Boolean = abs(delta(heading, bearing)) <= tolerance

    /**
     * The heading of the top of the screen. [azimuth] is where the device's natural top edge points (from
     * SensorManager.getOrientation); [quarterTurns] is the display rotation (Surface.ROTATION_0 to ROTATION_270).
     */
    fun screenHeading(azimuth: Double, quarterTurns: Int): Double = normalize(azimuth + 90.0 * quarterTurns)

    /** A 16-point name such as "east-southeast". */
    fun compassPoint(degrees: Double): String = POINTS[((normalize(degrees) + 11.25) / 22.5).toInt() % POINTS.size]

    /**
     * Where to measure the Qibla from for a saved prayer-times record: the area itself for location-based times, or
     * the position Aladhan found for a chosen city.
     */
    fun origin(record: JsonObject?): Coordinates? {
        Coordinates.from(record)?.let { return it }
        val latitude = RecordRules.text(record, CITY_LATITUDE).toDoubleOrNull() ?: return null
        val longitude = RecordRules.text(record, CITY_LONGITUDE).toDoubleOrNull() ?: return null
        return Coordinates.of(latitude, longitude)
    }

    private val POINTS = listOf(
        "north", "north-northeast", "northeast", "east-northeast", "east", "east-southeast", "southeast", "south-southeast",
        "south", "south-southwest", "southwest", "west-southwest", "west", "west-northwest", "northwest", "north-northwest",
    )
}

/** Low-pass filter for compass headings that always turns the short way, so 359° to 1° passes through north. */
class HeadingSmoother(private val factor: Double = 0.18) {
    private var value: Double? = null

    fun update(heading: Double): Double {
        val current = value
        val next = if (current == null) QiblaMath.normalize(heading)
        else QiblaMath.normalize(current + factor * QiblaMath.delta(current, heading))
        value = next
        return next
    }

    fun reset() {
        value = null
    }
}

/**
 * Decides when to give the single "facing the Qibla" haptic: on entering the tolerance, then not again until the
 * phone has turned clearly away, so sensor jitter at the edge cannot buzz repeatedly.
 */
class QiblaAlignmentCue(private val enter: Double = QiblaMath.FACING_TOLERANCE, private val rearm: Double = 10.0) {
    private var armed = true

    /** Returns true exactly when the cue should fire for this [turn] (degrees left to turn, signed). */
    fun update(turn: Double): Boolean {
        val off = abs(turn)
        if (off > rearm) armed = true
        if (armed && off <= enter) {
            armed = false
            return true
        }
        return false
    }
}
