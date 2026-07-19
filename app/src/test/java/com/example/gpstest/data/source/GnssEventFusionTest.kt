package com.example.gpstest.data.source

import com.example.gpstest.domain.model.Constellation
import com.example.gpstest.domain.model.GnssClockData
import com.example.gpstest.domain.model.GnssSatellite
import com.example.gpstest.domain.model.LocationInfo
import com.example.gpstest.domain.model.PseudorangeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GnssEventFusionTest {
    @Test
    fun `fusion module is available without Android runtime`() {
        val fusionClass = Class.forName("com.example.gpstest.data.source.GnssEventFusion")

        assertNotNull(fusionClass.getDeclaredConstructor().newInstance())
    }

    @Test
    fun `measurements enrich the latest matching satellite and clock`() {
        val fusion = GnssEventFusion()
        val satellite = makeSatellite()
        val clock = makeClock()

        fusion.onEvent(GnssAcquisitionEvent.SatelliteStatus(listOf(satellite)))
        val result =
            fusion.onEvent(
                GnssAcquisitionEvent.Measurements(
                    clock = clock,
                    extrasBySatellite =
                        mapOf(
                            GnssSatelliteKey(1, satellite.svid) to
                                GnssMeasurementExtras(carrierCycles = 42L, dopplerShiftHz = 12.5),
                        ),
                ),
            )

        assertEquals(clock, result?.clock)
        assertEquals(42L, result?.satellites?.single()?.carrierCycles)
        assertEquals(12.5, result?.satellites?.single()?.dopplerShiftHz)
    }

    @Test
    fun `new measurement batch replaces measurements absent from the latest batch`() {
        val fusion = GnssEventFusion()
        val satellite = makeSatellite()

        fusion.onEvent(
            GnssAcquisitionEvent.Measurements(
                clock = makeClock(),
                extrasBySatellite = mapOf(GnssSatelliteKey(1, satellite.svid) to GnssMeasurementExtras(dopplerShiftHz = 8.0)),
            ),
        )
        fusion.onEvent(GnssAcquisitionEvent.SatelliteStatus(listOf(satellite)))
        val result = fusion.onEvent(GnssAcquisitionEvent.Measurements(makeClock(), emptyMap()))

        assertNull(result?.satellites?.single()?.dopplerShiftHz)
    }

    @Test
    fun `pressure is retained for the next location but does not emit data by itself`() {
        val fusion = GnssEventFusion()
        val location = makeLocation()

        assertNull(fusion.onEvent(GnssAcquisitionEvent.Pressure(pressure = 1000f, barometricAltitude = 111.0)))
        val result = fusion.onEvent(GnssAcquisitionEvent.Location(location))

        assertEquals(1000f, result?.location?.pressure)
        assertEquals(111.0, result?.location?.barometricAltitude)
    }

    @Test
    fun `dumpsys update emits the latest data snapshot`() {
        val fusion = GnssEventFusion()
        val satellite = makeSatellite()
        val dumpsys = DumpsysGnssData(avgBasebandCn0 = 31f, measurementCount = 7, usedInFixConstellations = listOf("GPS"))

        fusion.onEvent(GnssAcquisitionEvent.SatelliteStatus(listOf(satellite)))
        val result = fusion.onEvent(GnssAcquisitionEvent.Dumpsys(dumpsys))

        assertEquals(listOf(satellite), result?.satellites)
        assertEquals(dumpsys, result?.dumpsysData)
    }

    @Test
    fun `a new fusion does not retain a previous sessions measurements`() {
        val satellite = makeSatellite()
        val firstSession = GnssEventFusion()

        firstSession.onEvent(
            GnssAcquisitionEvent.Measurements(
                clock = makeClock(),
                extrasBySatellite = mapOf(GnssSatelliteKey(1, satellite.svid) to GnssMeasurementExtras(carrierCycles = 42L)),
            ),
        )
        firstSession.onEvent(GnssAcquisitionEvent.SatelliteStatus(listOf(satellite)))

        val freshSession = GnssEventFusion()
        val result = freshSession.onEvent(GnssAcquisitionEvent.SatelliteStatus(listOf(satellite)))

        assertNull(result?.satellites?.single()?.carrierCycles)
    }

    private fun makeSatellite() =
        GnssSatellite(
            svid = 3,
            constellation = Constellation.GPS,
            rawConstellationType = 1,
            cn0DbHz = 30f,
            azimuthDegrees = 90f,
            elevationDegrees = 40f,
            hasAlmanac = true,
            hasEphemeris = true,
            usedInFix = true,
            carrierFrequencyHz = null,
            carrierCycles = null,
            dopplerShiftHz = null,
            timeNanos = 1L,
            pseudorangeStatus = PseudorangeStatus.MISSING_MEASUREMENT,
        )

    private fun makeClock() =
        GnssClockData(
            timeNanos = 10L,
            biasNanos = null,
            fullBiasNanos = null,
            driftNanosPerSecond = null,
            biasUncertaintyNanos = null,
            driftUncertaintyNanosPerSecond = null,
            hardwareClockDiscontinuityCount = 0,
        )

    private fun makeLocation() =
        LocationInfo(
            latitude = 1.0,
            longitude = 2.0,
            altitude = 3.0,
            accuracy = 4f,
            speed = 5f,
            bearing = 6f,
            timestamp = 7L,
        )
}
