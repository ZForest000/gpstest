package com.example.gpstest.ui.screens.satellite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.gpstest.R
import com.example.gpstest.domain.model.GnssSatellite
import com.example.gpstest.ui.components.LocationCard
import com.example.gpstest.ui.components.SatelliteCard
import com.example.gpstest.ui.components.SatelliteDetailSheet
import com.example.gpstest.ui.components.StatBar
import com.example.gpstest.viewmodel.SatelliteViewModel
import com.example.gpstest.viewmodel.SatelliteUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SatelliteListScreen(
    viewModel: SatelliteViewModel,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onNavigateToHistory: () -> Unit = {},
    onNavigateToAGps: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedSatellite by remember { mutableStateOf<GnssSatellite?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onNavigateToAGps) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.agps_manager)
                        )
                    }
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = stringResource(R.string.history_title)
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is SatelliteUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is SatelliteUiState.PermissionRequired -> {
                    PermissionRequiredContent(
                        onRequestPermission = onRequestPermission
                    )
                }
                is SatelliteUiState.Success -> {
                    val allSatellites = state.usedInFix + state.visibleOnly + state.searching
                    SatelliteListContent(
                        usedInFix = state.usedInFix,
                        visibleOnly = state.visibleOnly,
                        searching = state.searching,
                        totalCount = state.totalCount,
                        allSatellites = allSatellites,
                        location = state.location,
                        onSatelliteClick = { selectedSatellite = it }
                    )
                }
                is SatelliteUiState.Error -> {
                    ErrorContent(
                        message = state.message,
                        onRetry = { viewModel.startListening() }
                    )
                }
            }
        }
    }

    selectedSatellite?.let { satellite ->
        val signalHistory = viewModel.getSignalHistoryForSatellite(satellite)
        
        ModalBottomSheet(
            onDismissRequest = { selectedSatellite = null },
            sheetState = sheetState
        ) {
            SatelliteDetailSheet(
                satellite = satellite,
                signalHistory = signalHistory
            )
        }
    }
}

@Composable
private fun SatelliteListContent(
    usedInFix: List<GnssSatellite>,
    visibleOnly: List<GnssSatellite>,
    searching: List<GnssSatellite>,
    totalCount: Int,
    allSatellites: List<GnssSatellite>,
    location: com.example.gpstest.domain.model.LocationInfo?,
    onSatelliteClick: (GnssSatellite) -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleCount = usedInFix.size + visibleOnly.size

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            LocationCard(location = location)
        }
        
        item {
            StatBar(
                usedInFixCount = usedInFix.size,
                visibleCount = visibleCount,
                totalCount = totalCount,
                satellites = allSatellites
            )
        }

        if (usedInFix.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.used_in_fix),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            itemsIndexed(
                items = usedInFix,
                key = { index, satellite ->
                    "used_${satellite.constellation.name}_${satellite.svid}_${satellite.carrierFrequencyHz ?: -1f}_$index"
                }
            ) { _, satellite ->
                SatelliteCard(
                    satellite = satellite,
                    onClick = { onSatelliteClick(satellite) }
                )
            }
        }

        if (visibleOnly.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.visible_not_in_fix),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            itemsIndexed(
                items = visibleOnly,
                key = { index, satellite ->
                    "visible_${satellite.constellation.name}_${satellite.svid}_${satellite.carrierFrequencyHz ?: -1f}_$index"
                }
            ) { _, satellite ->
                SatelliteCard(
                    satellite = satellite,
                    onClick = { onSatelliteClick(satellite) }
                )
            }
        }

        if (searching.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.searching),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            itemsIndexed(
                items = searching,
                key = { index, satellite ->
                    "searching_${satellite.constellation.name}_${satellite.svid}_${satellite.carrierFrequencyHz ?: -1f}_$index"
                }
            ) { _, satellite ->
                SatelliteCard(
                    satellite = satellite,
                    onClick = { onSatelliteClick(satellite) }
                )
            }
        }
    }
}

@Composable
private fun PermissionRequiredContent(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.permission_required),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.permission_message),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRequestPermission) {
            Text(stringResource(R.string.grant))
        }
    }
}

@Composable
private fun PermissionDialog(
    onRequestPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.permission_required)) },
        text = { Text(stringResource(R.string.permission_message)) },
        confirmButton = {
            Button(onClick = onRequestPermission) {
                Text(stringResource(R.string.grant))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.error_occurred),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text(stringResource(R.string.retry))
        }
    }
}
