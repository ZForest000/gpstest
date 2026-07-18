package com.example.gpstest.ui.screens.diagnostics

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.gpstest.PermissionState
import com.example.gpstest.R
import com.example.gpstest.data.local.RinexExportHelper
import com.example.gpstest.domain.model.GnssData
import com.example.gpstest.ui.components.AntennaInfoCard
import com.example.gpstest.ui.components.ClockInfoCard
import com.example.gpstest.ui.components.GnssCapabilitiesCard
import com.example.gpstest.ui.screens.satellite.SatelliteScreenScaffold
import com.example.gpstest.ui.screens.satellite.SatelliteStateContent
import com.example.gpstest.viewmodel.SatelliteUiState
import com.example.gpstest.viewmodel.SatelliteViewModel

fun canExportRinex(
    uiState: SatelliteUiState,
    epochCount: Int,
): Boolean = uiState is SatelliteUiState.Success && epochCount > 0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiverDiagnosticsScreen(
    viewModel: SatelliteViewModel,
    permissionState: PermissionState,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val gnssCapabilities by viewModel.gnssCapabilities.collectAsState()
    val antennaInfos by viewModel.antennaInfos.collectAsState()
    val context = LocalContext.current
    val rinexShareTitle = stringResource(R.string.rinex_share_chooser)
    val rinexShareFailed = stringResource(R.string.rinex_export_failed)
    val rinexEpochs = viewModel.getRinexEpochs()
    val canExport = canExportRinex(uiState = uiState, epochCount = rinexEpochs.size)

    SatelliteScreenScaffold(
        title = stringResource(R.string.nav_receiver_diagnostics),
        onOpenDrawer = onOpenDrawer,
        modifier = modifier,
        actions = {
            IconButton(
                enabled = canExport,
                onClick = {
                    val location = (uiState as? SatelliteUiState.Success)?.location
                    val shared =
                        RinexExportHelper.share(
                            context = context,
                            epochs = rinexEpochs,
                            location = location,
                            chooserTitle = rinexShareTitle,
                        )
                    if (!shared) {
                        Toast.makeText(context, rinexShareFailed, Toast.LENGTH_SHORT).show()
                    }
                },
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = stringResource(R.string.rinex_export),
                )
            }
        },
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
                    ClockInfoCard(
                        gnssData =
                            GnssData(
                                satellites = allSatellites,
                                location = state.location,
                                clock = state.clock,
                                dumpsysData = state.dumpsysData,
                            ),
                    )
                }
                gnssCapabilities?.let { capabilities ->
                    item {
                        GnssCapabilitiesCard(capabilities = capabilities)
                    }
                }
                if (antennaInfos.isNotEmpty()) {
                    item {
                        AntennaInfoCard(infos = antennaInfos)
                    }
                }
            }
        }
    }
}
