package com.example.gpstest.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gpstest.data.source.DumpsysGnssData
import com.example.gpstest.domain.model.GnssClockData
import com.example.gpstest.domain.model.GnssSatellite
import com.example.gpstest.domain.model.LocationInfo
import com.example.gpstest.domain.model.SatelliteGroup
import com.example.gpstest.domain.model.SatelliteHistorySnapshot
import com.example.gpstest.domain.repository.GnssRepository
import com.example.gpstest.domain.repository.SatelliteHistoryRepository
import com.example.gpstest.ui.components.SignalReading
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SatelliteViewModel(
    application: Application,
    private val repository: GnssRepository,
    private val historyRepository: SatelliteHistoryRepository? = null
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<SatelliteUiState>(SatelliteUiState.Loading)
    val uiState: StateFlow<SatelliteUiState> = _uiState.asStateFlow()

    private val _selectedSatellite = MutableStateFlow<GnssSatellite?>(null)
    val selectedSatellite: StateFlow<GnssSatellite?> = _selectedSatellite.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _historySnapshots = MutableStateFlow<List<SatelliteHistorySnapshot>>(emptyList())
    val historySnapshots: StateFlow<List<SatelliteHistorySnapshot>> = _historySnapshots.asStateFlow()

    private val _showHistory = MutableStateFlow(false)
    val showHistory: StateFlow<Boolean> = _showHistory.asStateFlow()

    private val _signalHistory = MutableStateFlow<Map<String, MutableList<SignalReading>>>(emptyMap())
    val signalHistory: StateFlow<Map<String, List<SignalReading>>> = _signalHistory.asStateFlow()

    private var lastSnapshotTime = 0L
    private val snapshotIntervalMs = 60_000L
    private var collectionJob: Job? = null

    private val maxSignalHistorySize = 60

    init {
        loadHistory()
    }

    fun startListening() {
        collectionJob?.cancel()
        collectionJob = viewModelScope.launch {
            try {
                repository.getGnssData().collect { gnssData ->
                    val satellites = gnssData.satellites
                    val grouped = satellites.groupBy { it.group }
                    _uiState.value = SatelliteUiState.Success(
                        usedInFix = grouped[SatelliteGroup.USED_IN_FIX].orEmpty(),
                        visibleOnly = grouped[SatelliteGroup.VISIBLE_ONLY].orEmpty(),
                        searching = grouped[SatelliteGroup.SEARCHING].orEmpty(),
                        totalCount = satellites.size,
                        location = gnssData.location,
                        clock = gnssData.clock,
                        dumpsysData = gnssData.dumpsysData
                    )
                    
                    updateSignalHistory(satellites)
                    maybeSaveSnapshot(satellites)
                }
            } catch (e: Exception) {
                _uiState.value = SatelliteUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun updateSignalHistory(satellites: List<GnssSatellite>) {
        val now = System.currentTimeMillis()
        val currentHistory = _signalHistory.value.toMutableMap()
        
        satellites.forEach { satellite ->
            val key = "${satellite.constellation.name}_${satellite.svid}"
            val readings = currentHistory.getOrPut(key) { mutableListOf() }
            
            readings.add(SignalReading(timestamp = now, cn0DbHz = satellite.cn0DbHz))
            
            while (readings.size > maxSignalHistorySize) {
                readings.removeAt(0)
            }
        }
        
        _signalHistory.value = currentHistory
    }

    fun getSignalHistoryForSatellite(satellite: GnssSatellite): List<SignalReading> {
        val key = "${satellite.constellation.name}_${satellite.svid}"
        return _signalHistory.value[key] ?: emptyList()
    }

    private fun maybeSaveSnapshot(satellites: List<GnssSatellite>) {
        val now = System.currentTimeMillis()
        if (now - lastSnapshotTime >= snapshotIntervalMs) {
            lastSnapshotTime = now
            viewModelScope.launch {
                historyRepository?.saveSnapshot(satellites)
            }
        }
    }

    fun saveSnapshotNow() {
        val state = _uiState.value
        if (state is SatelliteUiState.Success) {
            val allSatellites = state.usedInFix + state.visibleOnly + state.searching
            viewModelScope.launch {
                historyRepository?.saveSnapshot(allSatellites)
                loadHistory()
            }
        }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            historyRepository?.historySnapshots?.collect { snapshots ->
                _historySnapshots.value = snapshots
            }
        }
    }

    fun toggleHistory() {
        _showHistory.value = !_showHistory.value
    }

    fun setShowHistory(show: Boolean) {
        _showHistory.value = show
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyRepository?.clearHistory()
            _historySnapshots.value = emptyList()
        }
    }

    fun setPermissionDenied() {
        _uiState.value = SatelliteUiState.PermissionRequired
    }

    fun selectSatellite(satellite: GnssSatellite) {
        _selectedSatellite.value = satellite
    }

    fun clearSelection() {
        _selectedSatellite.value = null
    }

    fun refreshSatellites() {
        _isRefreshing.value = true
        startListening()
        _isRefreshing.value = false
    }

    val isLoading: Boolean
        get() = _uiState.value is SatelliteUiState.Loading

    fun endRefresh() {
        _isRefreshing.value = false
    }

    val hasLocationPermission: Boolean
        get() = _uiState.value is SatelliteUiState.Success

    override fun onCleared() {
        super.onCleared()
        collectionJob?.cancel()
    }
}

sealed interface SatelliteUiState {
    data object Loading : SatelliteUiState
    data object PermissionRequired : SatelliteUiState
    data class Success(
        val usedInFix: List<GnssSatellite>,
        val visibleOnly: List<GnssSatellite>,
        val searching: List<GnssSatellite>,
        val totalCount: Int,
        val location: LocationInfo? = null,
        val clock: GnssClockData? = null,
        val dumpsysData: DumpsysGnssData? = null
    ) : SatelliteUiState
    data class Error(val message: String) : SatelliteUiState
}
