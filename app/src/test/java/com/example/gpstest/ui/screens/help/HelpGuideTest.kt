package com.example.gpstest.ui.screens.help

import org.junit.Assert.assertEquals
import org.junit.Test

class HelpGuideTest {
    @Test
    fun `guide contains every GNSS diagnostic section in reading order`() {
        assertEquals(
            listOf(
                HelpGuideSection.QUICK_DIAGNOSTICS,
                HelpGuideSection.SCREEN_GUIDE,
                HelpGuideSection.METRICS_REFERENCE,
                HelpGuideSection.AGPS_AND_ADVANCED,
            ),
            HelpGuideSection.readingOrder,
        )
    }
}
