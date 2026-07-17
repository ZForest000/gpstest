package com.example.gpstest.data.local

import com.example.gpstest.domain.model.AGpsInjectionRecord
import com.example.gpstest.domain.model.InjectionSource
import com.example.gpstest.domain.model.InjectionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AGpsInjectionHistoryCodecTest {
    private fun makeRecord(
        id: String = "1",
        type: InjectionType = InjectionType.XTRA,
        source: InjectionSource = InjectionSource.MANUAL,
        timestamp: Long = 1_000L,
        success: Boolean = true,
        errorMessage: String? = null,
    ): AGpsInjectionRecord =
        AGpsInjectionRecord(
            id = id,
            type = type,
            source = source,
            timestamp = timestamp,
            success = success,
            errorMessage = errorMessage,
        )

    @Test
    fun `encode and decode round-trip preserves records`() {
        val records =
            listOf(
                makeRecord(id = "a", type = InjectionType.TIME, source = InjectionSource.NETWORK),
                makeRecord(
                    id = "b",
                    type = InjectionType.EPHEMERIS,
                    source = InjectionSource.AUTO_DOWNLOAD,
                    success = false,
                    errorMessage = "timeout",
                ),
            )

        val encoded = AGpsInjectionHistoryStore.encodeHistory(records)
        val decoded = AGpsInjectionHistoryStore.decodeHistory(encoded)

        assertEquals(records, decoded)
    }

    @Test
    fun `encodeHistory caps at 50 records`() {
        val records = (1..60).map { makeRecord(id = it.toString(), timestamp = it.toLong()) }

        val encoded = AGpsInjectionHistoryStore.encodeHistory(records)
        val decoded = AGpsInjectionHistoryStore.decodeHistory(encoded)

        assertEquals(50, decoded.size)
        assertEquals("1", decoded.first().id)
        assertEquals("50", decoded.last().id)
    }

    @Test
    fun `decodeHistory returns empty list for invalid json`() {
        assertTrue(AGpsInjectionHistoryStore.decodeHistory("not-json").isEmpty())
        assertTrue(AGpsInjectionHistoryStore.decodeHistory("{").isEmpty())
        assertTrue(AGpsInjectionHistoryStore.decodeHistory("null").isEmpty())
    }

    @Test
    fun `decodeHistory returns empty list for empty array`() {
        assertEquals(emptyList<AGpsInjectionRecord>(), AGpsInjectionHistoryStore.decodeHistory("[]"))
    }

    @Test
    fun `decodeHistory preserves null errorMessage`() {
        val records = listOf(makeRecord(errorMessage = null))
        val decoded = AGpsInjectionHistoryStore.decodeHistory(AGpsInjectionHistoryStore.encodeHistory(records))
        assertEquals(1, decoded.size)
        assertEquals(null, decoded[0].errorMessage)
    }
}
