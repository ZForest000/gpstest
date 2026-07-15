package com.example.gpstest.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationInfoTest {
    @Test
    fun `optional accuracy fields default to null`() {
        val location =
            LocationInfo(
                latitude = 39.9,
                longitude = 116.4,
                altitude = 50.0,
                accuracy = 5f,
                speed = 0f,
                bearing = 0f,
                timestamp = 1_000L,
            )
        assertNull(location.verticalAccuracyMeters)
        assertNull(location.bearingAccuracyDegrees)
        assertNull(location.speedAccuracyMetersPerSecond)
        assertNull(location.barometricAltitude)
        assertNull(location.pressure)
    }

    @Test
    fun `optional accuracy fields retain provided values`() {
        val location =
            LocationInfo(
                latitude = 39.9,
                longitude = 116.4,
                altitude = 50.0,
                accuracy = 5f,
                speed = 1.5f,
                bearing = 90f,
                timestamp = 1_000L,
                barometricAltitude = 48.0,
                pressure = 1013.25f,
                verticalAccuracyMeters = 3.2f,
                bearingAccuracyDegrees = 2.5f,
                speedAccuracyMetersPerSecond = 0.4f,
            )
        assertEquals(3.2f, location.verticalAccuracyMeters!!, 0.001f)
        assertEquals(2.5f, location.bearingAccuracyDegrees!!, 0.001f)
        assertEquals(0.4f, location.speedAccuracyMetersPerSecond!!, 0.001f)
        assertEquals(48.0, location.barometricAltitude!!, 0.001)
        assertEquals(1013.25f, location.pressure!!, 0.001f)
    }
}
