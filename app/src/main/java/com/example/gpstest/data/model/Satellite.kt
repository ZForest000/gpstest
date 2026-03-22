package com.example.gpstest.data.model

import com.example.gpstest.domain.model.Constellation
import com.example.gpstest.domain.model.GnssSatellite

/**
 * Alias for GnssSatellite to maintain compatibility with UI components
 * that reference data.model.Satellite
 */
typealias Satellite = GnssSatellite

/**
 * Extension property for constellation type (Android API int)
 */
val GnssSatellite.constellationType: Int
    get() = when (constellation) {
        Constellation.GPS -> 1
        Constellation.SBAS -> 2
        Constellation.GLONASS -> 3
        Constellation.QZSS -> 4
        Constellation.BEIDOU -> 5
        Constellation.GALILEO -> 6
        Constellation.UNKNOWN -> 0
    }

/**
 * Extension property for SNR/CN0 (alias for cn0DbHz)
 */
val GnssSatellite.snrCn0: Float
    get() = cn0DbHz

// Add SBAS to Constellation enum for completeness
// SBAS is now part of the Constellation enum

/**
 * Additional extension properties for compatibility
 */
val GnssSatellite.almanac: Boolean
    get() = hasAlmanac

val GnssSatellite.ephemeris: Boolean
    get() = hasEphemeris

val GnssSatellite.azimuth: Float
    get() = azimuthDegrees

val GnssSatellite.elevation: Float
    get() = elevationDegrees
