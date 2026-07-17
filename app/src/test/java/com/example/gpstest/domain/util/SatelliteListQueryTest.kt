package com.example.gpstest.domain.util

import com.example.gpstest.domain.model.Constellation
import com.example.gpstest.domain.model.GnssSatellite
import com.example.gpstest.domain.model.PseudorangeStatus
import com.example.gpstest.domain.model.SatelliteSortMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SatelliteListQueryTest {
    private fun sat(
        svid: Int,
        constellation: Constellation,
        cn0: Float,
        elev: Float,
    ) = GnssSatellite(
        svid = svid,
        constellation = constellation,
        rawConstellationType = constellation.constellationType,
        cn0DbHz = cn0,
        azimuthDegrees = 0f,
        elevationDegrees = elev,
        hasAlmanac = false,
        hasEphemeris = false,
        usedInFix = false,
        carrierFrequencyHz = null,
        carrierCycles = null,
        dopplerShiftHz = null,
        timeNanos = 0L,
        pseudorangeStatus = PseudorangeStatus.MISSING_MEASUREMENT,
    )

    @Test
    fun `empty constellation set shows all`() {
        val list = listOf(sat(1, Constellation.GPS, 30f, 10f), sat(2, Constellation.BEIDOU, 40f, 20f))
        val result = SatelliteListQuery(constellations = emptySet()).applyTo(list)
        assertEquals(2, result.size)
    }

    @Test
    fun `filters by constellation set`() {
        val list = listOf(sat(1, Constellation.GPS, 30f, 10f), sat(2, Constellation.BEIDOU, 40f, 20f))
        val result = SatelliteListQuery(constellations = setOf(Constellation.GPS)).applyTo(list)
        assertEquals(listOf(1), result.map { it.svid })
    }

    @Test
    fun `filters by svid substring`() {
        val list = listOf(sat(12, Constellation.GPS, 30f, 10f), sat(2, Constellation.GPS, 40f, 20f))
        val result = SatelliteListQuery(svidQuery = "1").applyTo(list)
        assertEquals(listOf(12), result.map { it.svid })
    }

    @Test
    fun `sorts by cn0 descending`() {
        val list = listOf(sat(1, Constellation.GPS, 20f, 50f), sat(2, Constellation.GPS, 40f, 10f))
        val result = SatelliteListQuery(sortMode = SatelliteSortMode.CN0_DESC).applyTo(list)
        assertEquals(listOf(2, 1), result.map { it.svid })
    }

    @Test
    fun `sorts by elevation descending`() {
        val list = listOf(sat(1, Constellation.GPS, 40f, 10f), sat(2, Constellation.GPS, 20f, 50f))
        val result = SatelliteListQuery(sortMode = SatelliteSortMode.ELEVATION_DESC).applyTo(list)
        assertEquals(listOf(2, 1), result.map { it.svid })
    }

    @Test
    fun `sorts by svid ascending`() {
        val list = listOf(sat(5, Constellation.GPS, 40f, 10f), sat(2, Constellation.GPS, 20f, 50f))
        val result = SatelliteListQuery(sortMode = SatelliteSortMode.SVID_ASC).applyTo(list)
        assertEquals(listOf(2, 5), result.map { it.svid })
    }

    @Test
    fun `combines filter and sort`() {
        val list =
            listOf(
                sat(1, Constellation.GPS, 20f, 10f),
                sat(2, Constellation.BEIDOU, 50f, 10f),
                sat(3, Constellation.GPS, 40f, 10f),
            )
        val result =
            SatelliteListQuery(
                constellations = setOf(Constellation.GPS),
                sortMode = SatelliteSortMode.CN0_DESC,
            ).applyTo(list)
        assertEquals(listOf(3, 1), result.map { it.svid })
    }
}
