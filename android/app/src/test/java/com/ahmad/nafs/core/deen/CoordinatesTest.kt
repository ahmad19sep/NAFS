package com.ahmad.nafs.core.deen

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinatesTest {
    @Test fun `positions are rounded to about one kilometre before use`() {
        assertEquals(Coordinates(31.55, 74.34), Coordinates.of(31.5497, 74.3436))
        assertEquals(Coordinates(-33.87, 151.21), Coordinates.of(-33.8688, 151.2093))
    }

    @Test fun `impossible positions are rejected`() {
        assertNull(Coordinates.of(91.0, 10.0))
        assertNull(Coordinates.of(10.0, -181.0))
        assertNull(Coordinates.of(Double.NaN, 10.0))
    }

    @Test fun `distance separates a move across town from a move between cities`() {
        val lahore = Coordinates(31.55, 74.34)
        assertTrue(lahore.distanceKm(Coordinates(31.52, 74.36)) < 5.0)
        // Lahore to Karachi is roughly 1,000 km.
        assertTrue(lahore.distanceKm(Coordinates(24.86, 67.01)) in 950.0..1100.0)
    }

    @Test fun `saved records carry coordinates only for location-based times`() {
        assertEquals(Coordinates(31.55, 74.34), Coordinates.from(buildJsonObject { put("latitude", 31.55); put("longitude", 74.34) }))
        assertNull(Coordinates.from(buildJsonObject { put("city", "Lahore"); put("country", "Pakistan") }))
        assertEquals("your current area", PrayerAutoLocate.label(buildJsonObject { put("latitude", 31.55); put("longitude", 74.34) }))
        assertEquals("Lahore", PrayerAutoLocate.label(buildJsonObject { put("city", "Lahore") }))
        assertNull(PrayerAutoLocate.label(null))
    }
}
