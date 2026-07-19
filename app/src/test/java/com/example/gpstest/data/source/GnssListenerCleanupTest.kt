package com.example.gpstest.data.source

import org.junit.Assert.assertEquals
import org.junit.Test

class GnssListenerCleanupTest {
    @Test
    fun `cleanup continues after one listener unregister throws`() {
        val completed = mutableListOf<String>()

        runGnssListenerCleanup(
            {
                completed += "status"
                throw IllegalStateException("OEM callback already removed")
            },
            { completed += "measurements" },
            { completed += "location" },
            { completed += "pressure" },
        )

        assertEquals(listOf("status", "measurements", "location", "pressure"), completed)
    }
}
