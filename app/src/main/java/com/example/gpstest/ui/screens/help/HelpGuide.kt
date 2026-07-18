package com.example.gpstest.ui.screens.help

enum class HelpGuideSection {
    QUICK_DIAGNOSTICS,
    SCREEN_GUIDE,
    METRICS_REFERENCE,
    AGPS_AND_ADVANCED,
    ;

    companion object {
        val readingOrder = listOf(
            QUICK_DIAGNOSTICS,
            SCREEN_GUIDE,
            METRICS_REFERENCE,
            AGPS_AND_ADVANCED,
        )
    }
}
