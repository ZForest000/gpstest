package com.example.gpstest.ui.screens.positioning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.gpstest.PermissionState
import com.example.gpstest.R
import com.example.gpstest.ui.components.DopCard
import com.example.gpstest.ui.components.DopTrendChart
import com.example.gpstest.ui.components.LocalPositionCard
import com.example.gpstest.ui.components.LocationCard
import com.example.gpstest.ui.components.TtffCard
import com.example.gpstest.ui.screens.satellite.SatelliteScreenScaffold
import com.example.gpstest.ui.screens.satellite.SatelliteStateContent
import com.example.gpstest.viewmodel.SatelliteViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PositioningScreen(
    viewModel: SatelliteViewModel,
    permissionState: PermissionState,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val ttffState by viewModel.ttffState.collectAsState()
    val dopHistory by viewModel.dopHistory.collectAsState()
    val localPositionSolution by viewModel.localPositionSolution.collectAsState()
    val localPositionDiagnostics by viewModel.localPositionDiagnostics.collectAsState()
    val externalEphemerisResult by viewModel.externalEphemerisResult.collectAsState()

    SatelliteScreenScaffold(
        title = stringResource(R.string.nav_positioning),
        onOpenDrawer = onOpenDrawer,
        modifier = modifier,
    ) { paddingValues ->
        SatelliteStateContent(
            uiState = uiState,
            permissionState = permissionState,
            onRequestPermission = onRequestPermission,
            onOpenAppSettings = onOpenAppSettings,
            onRetry = viewModel::startListening,
            modifier = Modifier.padding(paddingValues),
        ) { state ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    TtffCard(
                        ttffState = ttffState,
                        onReset = viewModel::resetTtff,
                    )
                }
                item {
                    LocationCard(location = state.location)
                }
                item {
                    DopCard(dopInfo = state.dopInfo)
                }
                if (dopHistory.size >= 2) {
                    item {
                        DopTrendChart(history = dopHistory)
                    }
                }
                if (localPositionSolution != null) {
                    item {
                        LocalPositionCard(
                            solution = localPositionSolution,
                            diagnostics = localPositionDiagnostics,
                            externalEphemerisResult = externalEphemerisResult,
                        )
                    }
                }
            }
        }
    }
}
