package com.example.gpstest.ui.screens.satellite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.gpstest.R
import com.example.gpstest.domain.model.Constellation
import com.example.gpstest.domain.model.GnssSatellite
import com.example.gpstest.domain.model.SatelliteSortMode
import com.example.gpstest.domain.util.SatelliteListQuery
import com.example.gpstest.ui.components.ClockInfoCard
import com.example.gpstest.ui.components.ConstellationHealthSummaryCard
import com.example.gpstest.ui.components.ConstellationStatCard
import com.example.gpstest.ui.components.DopCard
import com.example.gpstest.ui.components.ErrorContent
import com.example.gpstest.ui.components.GnssCapabilitiesCard
import com.example.gpstest.ui.components.LocationCard
import com.example.gpstest.ui.components.PermissionRequiredContent
import com.example.gpstest.ui.components.SatelliteCard
import com.example.gpstest.ui.components.SatelliteDetailSheet
import com.example.gpstest.ui.components.SatelliteFilterBar
import com.example.gpstest.ui.components.StatBar
import com.example.gpstest.ui.components.TtffCard
import com.example.gpstest.viewmodel.SatelliteUiState
import com.example.gpstest.viewmodel.SatelliteViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SatelliteListScreen(
    viewModel: SatelliteViewModel,
    permissionState: com.example.gpstest.PermissionState,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenDrawer: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val ttffState by viewModel.ttffState.collectAsState()
    val gnssCapabilities by viewModel.gnssCapabilities.collectAsState()
    var selectedSatellite by remember { mutableStateOf<GnssSatellite?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedConstellations by rememberSaveable {
        mutableStateOf(emptySet<String>())
    }
    var sortModeName by rememberSaveable {
        mutableStateOf(SatelliteSortMode.CN0_DESC.name)
    }
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
                .mapNotNull { name ->
                    runCatching { Constellation.valueOf(name) }.getOrNull()
                }.toSet()
        }
    val listQuery =
        remember(selectedConstellationEnums, svidQuery, sortMode) {
            SatelliteListQuery(
                constellations = selectedConstellationEnums.takeIf { it.isNotEmpty() },
                svidQuery = svidQuery,
                sortMode = sortMode,
            )
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            imageVector = Icons.Filled.Menu,
                            contentDescription = stringResource(R.string.cd_menu),
                        )
                    }
                },
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            val snapshot = frozenSuccess
            val liveState = uiState
            when {
                frozen && snapshot != null -> {
                    val allSatellites =
                        snapshot.usedInFix + snapshot.visibleOnly + snapshot.searching
                    SatelliteListContent(
                        usedInFix = snapshot.usedInFix,
                        visibleOnly = snapshot.visibleOnly,
                        searching = snapshot.searching,
                        totalCount = snapshot.totalCount,
                        allSatellites = allSatellites,
                        location = snapshot.location,
                        clock = snapshot.clock,
                        dumpsysData = snapshot.dumpsysData,
                        dopInfo = snapshot.dopInfo,
                        gnssCapabilities = gnssCapabilities,
                        ttffState = ttffState,
                        listQuery = listQuery,
                        selectedConstellations = selectedConstellationEnums,
                        sortMode = sortMode,
                        svidQuery = svidQuery,
                        frozen = frozen,
                        onConstellationToggle = { constellation ->
                            selectedConstellations =
                                if (constellation.name in selectedConstellations) {
                                    selectedConstellations - constellation.name
                                } else {
                                    selectedConstellations + constellation.name
                                }
                        },
                        onSortModeChange = { sortModeName = it.name },
                        onSvidQueryChange = { svidQuery = it },
                        onFrozenChange = { frozen = it },
                        onTtffReset = { viewModel.resetTtff() },
                        onSatelliteClick = { selectedSatellite = it },
                    )
                }
                liveState is SatelliteUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                liveState is SatelliteUiState.PermissionRequired -> {
                    PermissionRequiredContent(
                        permissionState = permissionState,
                        onRequestPermission = onRequestPermission,
                        onOpenAppSettings = onOpenAppSettings,
                    )
                }
                liveState is SatelliteUiState.Success -> {
                    val allSatellites =
                        liveState.usedInFix + liveState.visibleOnly + liveState.searching
                    SatelliteListContent(
                        usedInFix = liveState.usedInFix,
                        visibleOnly = liveState.visibleOnly,
                        searching = liveState.searching,
                        totalCount = liveState.totalCount,
                        allSatellites = allSatellites,
                        location = liveState.location,
                        clock = liveState.clock,
                        dumpsysData = liveState.dumpsysData,
                        dopInfo = liveState.dopInfo,
                        gnssCapabilities = gnssCapabilities,
                        ttffState = ttffState,
                        listQuery = listQuery,
                        selectedConstellations = selectedConstellationEnums,
                        sortMode = sortMode,
                        svidQuery = svidQuery,
                        frozen = frozen,
                        onConstellationToggle = { constellation ->
                            selectedConstellations =
                                if (constellation.name in selectedConstellations) {
                                    selectedConstellations - constellation.name
                                } else {
                                    selectedConstellations + constellation.name
                                }
                        },
                        onSortModeChange = { sortModeName = it.name },
                        onSvidQueryChange = { svidQuery = it },
                        onFrozenChange = { frozen = it },
                        onTtffReset = { viewModel.resetTtff() },
                        onSatelliteClick = { selectedSatellite = it },
                    )
                }
                liveState is SatelliteUiState.Error -> {
                    ErrorContent(
                        message = liveState.message,
                        onRetry = { viewModel.startListening() },
                    )
                }
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
    totalCount: Int,
    allSatellites: List<GnssSatellite>,
    location: com.example.gpstest.domain.model.LocationInfo?,
    clock: com.example.gpstest.domain.model.GnssClockData?,
    dumpsysData: com.example.gpstest.data.source.DumpsysGnssData?,
    dopInfo: com.example.gpstest.domain.model.DopInfo?,
    gnssCapabilities: com.example.gpstest.domain.model.GnssCapabilitiesInfo?,
    ttffState: com.example.gpstest.viewmodel.TtffState,
    listQuery: SatelliteListQuery,
    selectedConstellations: Set<Constellation>,
    sortMode: SatelliteSortMode,
    svidQuery: String,
    frozen: Boolean,
    onConstellationToggle: (Constellation) -> Unit,
    onSortModeChange: (SatelliteSortMode) -> Unit,
    onSvidQueryChange: (String) -> Unit,
    onFrozenChange: (Boolean) -> Unit,
    onTtffReset: () -> Unit,
    onSatelliteClick: (GnssSatellite) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleCount = usedInFix.size + visibleOnly.size
    val filteredUsedInFix = remember(usedInFix, listQuery) { listQuery.applyTo(usedInFix) }
    val filteredVisibleOnly = remember(visibleOnly, listQuery) { listQuery.applyTo(visibleOnly) }
    val filteredSearching = remember(searching, listQuery) { listQuery.applyTo(searching) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            TtffCard(
                ttffState = ttffState,
                onReset = onTtffReset,
            )
        }

        item {
            LocationCard(location = location)
        }

        item {
            ConstellationStatCard(usedInFix = usedInFix)
        }

        item {
            DopCard(dopInfo = dopInfo)
        }

        item {
            ClockInfoCard(
                gnssData =
                    com.example.gpstest.domain.model.GnssData(
                        satellites = allSatellites,
                        location = location,
                        clock = clock,
                        dumpsysData = dumpsysData,
                    ),
            )
        }

        item {
            ConstellationHealthSummaryCard(
                usedInFix = usedInFix,
                allSatellites = allSatellites,
            )
        }

        item {
            StatBar(
                usedInFixCount = usedInFix.size,
                visibleCount = visibleCount,
                totalCount = totalCount,
                satellites = allSatellites,
            )
        }

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

        if (gnssCapabilities != null) {
            item {
                GnssCapabilitiesCard(capabilities = gnssCapabilities)
            }
        }

        if (filteredUsedInFix.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.used_in_fix),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            itemsIndexed(
                items = filteredUsedInFix,
                key = { index, satellite ->
                    "used_${satellite.constellation.name}_${satellite.svid}_${satellite.carrierFrequencyHz ?: -1f}_$index"
                },
            ) { _, satellite ->
                SatelliteCard(
                    satellite = satellite,
                    onClick = { onSatelliteClick(satellite) },
                )
            }
        }

        if (filteredVisibleOnly.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.visible_not_in_fix),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            itemsIndexed(
                items = filteredVisibleOnly,
                key = { index, satellite ->
                    "visible_${satellite.constellation.name}_${satellite.svid}_${satellite.carrierFrequencyHz ?: -1f}_$index"
                },
            ) { _, satellite ->
                SatelliteCard(
                    satellite = satellite,
                    onClick = { onSatelliteClick(satellite) },
                )
            }
        }

        if (filteredSearching.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.searching),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            itemsIndexed(
                items = filteredSearching,
                key = { index, satellite ->
                    "searching_${satellite.constellation.name}_${satellite.svid}_${satellite.carrierFrequencyHz ?: -1f}_$index"
                },
            ) { _, satellite ->
                SatelliteCard(
                    satellite = satellite,
                    onClick = { onSatelliteClick(satellite) },
                )
            }
        }
    }
}
