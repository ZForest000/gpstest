package com.example.gpstest.domain.ephemeris

import com.example.gpstest.domain.model.Constellation
import com.example.gpstest.domain.model.NavigationMessageFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GpsLnavEphemerisStoreTest {
    @Test
    fun `returns ephemeris only after all three GPS L1CA subframes arrive`() {
        val store = GpsLnavEphemerisStore()

        fun frame(subframe: Int) =
            NavigationMessageFrame(
                constellation = Constellation.GPS,
                svid = 8,
                type = 0x0101,
                status = 1,
                messageId = -1,
                submessageId = subframe,
                data = ByteArray(40),
                timestampMs = subframe.toLong(),
            )

        assertNull(store.add(frame(1)))
        assertNull(store.add(frame(2)))

        assertEquals(8, store.add(frame(3))!!.svid)
    }
}
