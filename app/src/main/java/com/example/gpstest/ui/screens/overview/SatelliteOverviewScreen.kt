package com.example.gpstest.ui.screens.overview

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
import com.example.gpstest.ui.components.ConstellationHealthSummaryCard
import com.example.gpstest.ui.components.ConstellationStatCard
import com.example.gpstest.ui.components.FixStatusSummary
import com.example.gpstest.ui.components.SignalBarChart
import com.example.gpstest.ui.components.StatBar
import com.example.gpstest.ui.screens.satellite.SatelliteScreenScaffold
import com.example.gpstest.ui.screens.satellite.SatelliteStateContent
import com.example.gpstest.viewmodel.SatelliteViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SatelliteOverviewScreen(
    viewModel: SatelliteViewModel,
    permissionState: PermissionState,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val ttffState by viewModel.ttffState.collectAsState()

    SatelliteScreenScaffold(
        title = stringResource(R.string.nav_overview),
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
            val allSatellites = state.usedInFix + state.visibleOnly + state.searching
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    StatBar(
                        usedInFixCount = state.usedInFix.size,
                        visibleCount = state.usedInFix.size + state.visibleOnly.size,
                        totalCount = state.totalCount,
                        satellites = allSatellites,
                    )
                }
                item {
                    FixStatusSummary(
                        location = state.location,
                        dopInfo = state.dopInfo,
                        ttffState = ttffState,
                    )
                }
                if (state.usedInFix.isNotEmpty()) {
                    item {
                        ConstellationStatCard(usedInFix = state.usedInFix)
                    }
                }
                if (allSatellites.isNotEmpty()) {
                    item {
                        SignalBarChart(satellites = allSatellites)
                    }
                    item {
                        ConstellationHealthSummaryCard(
                            usedInFix = state.usedInFix,
                            allSatellites = allSatellites,
                        )
                    }
                }
            }
        }
    }
}
