package com.example.gpstest.domain.ephemeris

import org.junit.Assert.assertEquals
import org.junit.Test

class RinexGpsNavigationParserTest {
    @Test
    fun `parses a RINEX 3 GPS broadcast ephemeris record`() {
        val text =
            """
            |     3.04           NAVIGATION DATA     M                   RINEX VERSION / TYPE
            |                                                            END OF HEADER
            |G01 2026 07 18 00 00 001.000000000000D-06 0.000000000000D+00 0.000000000000D+00
            |    1.000000000000D+00 2.000000000000D+01 3.000000000000D-09 4.000000000000D-01
            |    5.000000000000D-06 1.000000000000D-02 6.000000000000D-06 5.153795477500D+03
            |    1.000000000000D+05 7.000000000000D-07 8.000000000000D-01 9.000000000000D-07
            |    9.000000000000D-01 1.000000000000D+02 1.100000000000D+00 2.000000000000D-09
            |    3.000000000000D-10 0.000000000000D+00 2.300000000000D+03 0.000000000000D+00
            |    0.000000000000D+00 0.000000000000D+00 4.000000000000D-09 1.000000000000D+00
            |    1.000000000000D+05 0.000000000000D+00 0.000000000000D+00 0.000000000000D+00
            """.trimMargin()

        val ephemeris = RinexGpsNavigationParser.parse(text).single()

        assertEquals(1, ephemeris.svid)
        assertEquals(2300, ephemeris.weekNumber)
        assertEquals(100_000.0, ephemeris.toeSeconds, 0.0)
        assertEquals(5153.7954775, ephemeris.sqrtA, 0.0)
        assertEquals(0.01, ephemeris.eccentricity, 0.0)
        assertEquals(1e-6, ephemeris.af0Seconds, 0.0)
    }
}
