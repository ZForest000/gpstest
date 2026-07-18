package com.example.gpstest.ui.screens.satellite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.gpstest.PermissionState
import com.example.gpstest.R
import com.example.gpstest.domain.model.Constellation
import com.example.gpstest.domain.model.GnssSatellite
import com.example.gpstest.domain.model.SatelliteSortMode
import com.example.gpstest.domain.util.SatelliteListQuery
import com.example.gpstest.ui.components.SatelliteCard
import com.example.gpstest.ui.components.SatelliteDetailSheet
import com.example.gpstest.ui.components.SatelliteFilterBar
import com.example.gpstest.viewmodel.SatelliteUiState
import com.example.gpstest.viewmodel.SatelliteViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SatelliteListScreen(
    viewModel: SatelliteViewModel,
    permissionState: PermissionState,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenDrawer: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedSatellite by remember { mutableStateOf<GnssSatellite?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedConstellations by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var sortModeName by rememberSaveable { mutableStateOf(SatelliteSortMode.CN0_DESC.name) }
    var svidQuery by rememberSaveable { mutableStateOf("") }
    var frozen by rememberSaveable { mutableStateOf(false) }
    var frozenSuccess by remember { mutableStateOf<SatelliteUiState.Success?>(null) }

    LaunchedEffect(frozen, uiState) {
        if (frozen) {
            val success = uiState as? SatelliteUiState.Success
            if (frozenSuccess == null && success != null) {
                frozenSuccess = success
            }
        } else {
            frozenSuccess = null
        }
    }

    val sortMode =
        remember(sortModeName) {
            runCatching { SatelliteSortMode.valueOf(sortModeName) }
                .getOrDefault(SatelliteSortMode.CN0_DESC)
        }
    val selectedConstellationEnums =
        remember(selectedConstellations) {
            selectedConstellations
                .mapNotNull { name -> runCatching { Constellation.valueOf(name) }.getOrNull() }
                .toSet()
        }
    val listQuery =
        remember(selectedConstellationEnums, svidQuery, sortMode) {
            SatelliteListQuery(
                constellations = selectedConstellationEnums.takeIf { it.isNotEmpty() },
                svidQuery = svidQuery,
                sortMode = sortMode,
            )
        }
    val onConstellationToggle: (Constellation) -> Unit = { constellation ->
        selectedConstellations =
            if (constellation.name in selectedConstellations) {
                selectedConstellations - constellation.name
            } else {
                selectedConstellations + constellation.name
            }
    }

    SatelliteScreenScaffold(
        title = stringResource(R.string.nav_satellite_list),
        onOpenDrawer = onOpenDrawer,
        modifier = modifier,
    ) { paddingValues ->
        val snapshot = frozenSuccess
        if (frozen && snapshot != null) {
            SatelliteListContent(
                usedInFix = snapshot.usedInFix,
                visibleOnly = snapshot.visibleOnly,
                searching = snapshot.searching,
                listQuery = listQuery,
                selectedConstellations = selectedConstellationEnums,
                sortMode = sortMode,
                svidQuery = svidQuery,
                frozen = frozen,
                onConstellationToggle = onConstellationToggle,
                onSortModeChange = { sortModeName = it.name },
                onSvidQueryChange = { svidQuery = it },
                onFrozenChange = { frozen = it },
                onSatelliteClick = { selectedSatellite = it },
                modifier = Modifier.padding(paddingValues),
            )
        } else {
            SatelliteStateContent(
                uiState = uiState,
                permissionState = permissionState,
                onRequestPermission = onRequestPermission,
                onOpenAppSettings = onOpenAppSettings,
                onRetry = viewModel::startListening,
                modifier = Modifier.padding(paddingValues),
            ) { state ->
                SatelliteListContent(
                    usedInFix = state.usedInFix,
                    visibleOnly = state.visibleOnly,
                    searching = state.searching,
                    listQuery = listQuery,
                    selectedConstellations = selectedConstellationEnums,
                    sortMode = sortMode,
                    svidQuery = svidQuery,
                    frozen = frozen,
                    onConstellationToggle = onConstellationToggle,
                    onSortModeChange = { sortModeName = it.name },
                    onSvidQueryChange = { svidQuery = it },
                    onFrozenChange = { frozen = it },
                    onSatelliteClick = { selectedSatellite = it },
                )
            }
        }
    }

    selectedSatellite?.let { satellite ->
        val signalHistory = viewModel.getSignalHistoryForSatellite(satellite)
        ModalBottomSheet(
            onDismissRequest = { selectedSatellite = null },
            sheetState = sheetState,
        ) {
            SatelliteDetailSheet(
                satellite = satellite,
                signalHistory = signalHistory,
            )
        }
    }
}

@Composable
private fun SatelliteListContent(
    usedInFix: List<GnssSatellite>,
    visibleOnly: List<GnssSatellite>,
    searching: List<GnssSatellite>,
    listQuery: SatelliteListQuery,
    selectedConstellations: Set<Constellation>,
    sortMode: SatelliteSortMode,
    svidQuery: String,
    frozen: Boolean,
    onConstellationToggle: (Constellation) -> Unit,
    onSortModeChange: (SatelliteSortMode) -> Unit,
    onSvidQueryChange: (String) -> Unit,
    onFrozenChange: (Boolean) -> Unit,
    onSatelliteClick: (GnssSatellite) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filteredUsedInFix = remember(usedInFix, listQuery) { listQuery.applyTo(usedInFix) }
    val filteredVisibleOnly = remember(visibleOnly, listQuery) { listQuery.applyTo(visibleOnly) }
    val filteredSearching = remember(searching, listQuery) { listQuery.applyTo(searching) }
    val usedInFixTitle = stringResource(R.string.used_in_fix)
    val visibleOnlyTitle = stringResource(R.string.visible_not_in_fix)
    val searchingTitle = stringResource(R.string.searching)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SatelliteFilterBar(
                selectedConstellations = selectedConstellations,
                sortMode = sortMode,
                svidQuery = svidQuery,
                frozen = frozen,
                onConstellationToggle = onConstellationToggle,
                onSortModeChange = onSortModeChange,
                onSvidQueryChange = onSvidQueryChange,
                onFrozenChange = onFrozenChange,
            )
        }
        satelliteGroup(
            title = usedInFixTitle,
            satellites = filteredUsedInFix,
            keyPrefix = "used",
            onSatelliteClick = onSatelliteClick,
        )
        satelliteGroup(
            title = visibleOnlyTitle,
            satellites = filteredVisibleOnly,
            keyPrefix = "visible",
            onSatelliteClick = onSatelliteClick,
        )
        satelliteGroup(
            title = searchingTitle,
            satellites = filteredSearching,
            keyPrefix = "searching",
            onSatelliteClick = onSatelliteClick,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.satelliteGroup(
    title: String,
    satellites: List<GnssSatellite>,
    keyPrefix: String,
    onSatelliteClick: (GnssSatellite) -> Unit,
) {
    if (satellites.isEmpty()) return

    item {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
    }
    itemsIndexed(
        items = satellites,
        key = { index, satellite ->
            "${keyPrefix}_${satellite.constellation.name}_${satellite.svid}_${satellite.carrierFrequencyHz ?: -1f}_$index"
        },
    ) { _, satellite ->
        SatelliteCard(
            satellite = satellite,
            onClick = { onSatelliteClick(satellite) },
        )
    }
}
