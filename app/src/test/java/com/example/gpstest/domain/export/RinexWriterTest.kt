package com.example.gpstest.domain.export

import com.example.gpstest.domain.model.Constellation
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RinexWriterTest {
    @Test
    fun `writes a RINEX 3 header and GPS observation epoch`() {
        val output =
            RinexWriter.write(
                header = RinexHeader(markerName = "GpsTest", approximatePositionXyz = doubleArrayOf(1.0, 2.0, 3.0)),
                epochs =
                    listOf(
                        RinexEpoch(
                            timestamp = Instant.parse("2026-07-18T01:02:03Z"),
                            observations =
                                listOf(
                                    RinexObservation(
                                        constellation = Constellation.GPS,
                                        svid = 3,
                                        pseudorangeMeters = 20_000_000.0,
                                        carrierPhaseCycles = 100.0,
                                        dopplerHz = -1234.5,
                                    ),
                                ),
                        ),
                    ),
            )

        assertTrue(output.contains("     3.04           OBSERVATION DATA    M"))
        assertTrue(output.contains("GpsTest"))
        assertTrue(output.contains("G    3 C1C L1C D1C"))
        assertTrue(output.contains("> 2026 07 18 01 02  3.0000000  0  1"))
        assertTrue(output.contains("G03"))
    }
}
