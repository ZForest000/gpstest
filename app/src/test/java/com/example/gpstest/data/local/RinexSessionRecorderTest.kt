package com.example.gpstest.data.local

import com.example.gpstest.domain.model.Constellation
import com.example.gpstest.domain.model.GnssData
import com.example.gpstest.domain.model.GnssSatellite
import com.example.gpstest.domain.model.MultipathIndicator
import com.example.gpstest.domain.model.PseudorangeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant

class RinexSessionRecorderTest {
    @Test
    fun `records available pseudorange ADR and doppler as one epoch`() {
        val recorder = RinexSessionRecorder()
        recorder.record(
            GnssData(
                satellites =
                    listOf(
                        GnssSatellite(
                            svid = 3,
                            constellation = Constellation.GPS,
                            rawConstellationType = 1,
                            cn0DbHz = 40f,
                            azimuthDegrees = 10f,
                            elevationDegrees = 45f,
                            hasAlmanac = true,
                            hasEphemeris = true,
                            usedInFix = true,
                            carrierFrequencyHz = 1_575_420_000f,
                            carrierCycles = null,
                            dopplerShiftHz = -1234.5,
                            timeNanos = 0L,
                            multipathIndicator = MultipathIndicator.NOT_DETECTED,
                            accumulatedDeltaRangeMeters = 19.029367,
                            accumulatedDeltaRangeState = 1,
                            pseudorangeMeters = 20_000_000.0,
                            pseudorangeStatus = PseudorangeStatus.AVAILABLE,
                        ),
                    ),
            ),
            Instant.parse("2026-07-18T01:02:03Z"),
        )

        val observation =
            recorder.epochs
                .single()
                .observations
                .single()
        assertEquals(20_000_000.0, observation.pseudorangeMeters!!, 0.0)
        assertNotNull(observation.carrierPhaseCycles)
        assertEquals(-1234.5, observation.dopplerHz!!, 0.0)
    }
}
