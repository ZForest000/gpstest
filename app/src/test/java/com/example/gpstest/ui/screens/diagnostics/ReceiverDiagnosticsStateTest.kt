package com.example.gpstest.ui.screens.diagnostics

import com.example.gpstest.viewmodel.SatelliteUiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiverDiagnosticsStateTest {
    @Test
    fun `enables RINEX export only when a live state has recorded epochs`() {
        val success =
            SatelliteUiState.Success(
                usedInFix = emptyList(),
                visibleOnly = emptyList(),
                searching = emptyList(),
                totalCount = 0,
            )

        assertFalse(canExportRinex(SatelliteUiState.Loading, epochCount = 1))
        assertFalse(canExportRinex(success, epochCount = 0))
        assertTrue(canExportRinex(success, epochCount = 1))
    }
}
