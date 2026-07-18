package com.example.gpstest.ui.components

import com.example.gpstest.domain.model.DopInfo
import com.example.gpstest.domain.model.LocationInfo
import com.example.gpstest.viewmodel.TtffState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FixStatusSummaryStateTest {
    @Test
    fun `reports searching without a location or DOP`() {
        val state =
            buildFixStatusSummaryState(
                location = null,
                dopInfo = null,
                ttffState = TtffState.Measuring(startTime = 0L),
            )

        assertFalse(state.hasFix)
        assertNull(state.ttffSeconds)
        assertNull(state.pdop)
    }

    @Test
    fun `reports available metrics after a fix`() {
        val state =
            buildFixStatusSummaryState(
                location =
                    LocationInfo(
                        latitude = 31.2,
                        longitude = 121.5,
                        altitude = 8.0,
                        accuracy = 4.2f,
                        speed = 0f,
                        bearing = 0f,
                        timestamp = 1L,
                    ),
                dopInfo =
                    DopInfo(
                        pdop = 1.7,
                        hdop = 1.0,
                        vdop = 1.3,
                        satelliteCount = 7,
                    ),
                ttffState = TtffState.Completed(ttffMs = 12_345L),
            )

        assertTrue(state.hasFix)
        assertEquals(12.345, state.ttffSeconds!!, 0.0001)
        assertEquals(1.7, state.pdop!!, 0.0001)
    }
}
