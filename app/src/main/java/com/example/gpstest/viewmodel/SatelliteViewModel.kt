package com.example.gpstest.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gpstest.domain.model.GnssSatellite
import com.example.gpstest.domain.model.SatelliteGroup
import com.example.gpstest.domain.repository.GnssRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SatelliteViewModel(
    application: Application,
    private val repository: GnssRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<SatelliteUiState>(SatelliteUiState.Loading)
    val uiState: StateFlow<SatelliteUiState> = _uiState.asStateFlow()

    private val _selectedSatellite = MutableStateFlow<GnssSatellite?>(null)
    val selectedSatellite: StateFlow<GnssSatellite?> = _selectedSatellite.asStateFlow()

    // Enhanced state for pull-to-refresh
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun startListening() {
        viewModelScope.launch {
            try {
                repository.getSatellites().collect { satellites ->
                    val grouped = satellites.groupBy { it.group }
                    _uiState.value = SatelliteUiState.Success(
                        usedInFix = grouped[SatelliteGroup.USED_IN_FIX].orEmpty(),
                        visibleOnly = grouped[SatelliteGroup.VISIBLE_ONLY].orEmpty(),
                        searching = grouped[SatelliteGroup.SEARCHING].orEmpty(),
                        totalCount = satellites.size
                    )
                }
            } catch (e: Exception) {
                _uiState.value = SatelliteUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun selectSatellite(satellite: GnssSatellite) {
        _selectedSatellite.value = satellite
    }

    fun clearSelection() {
        _selectedSatellite.value = null
    }

    // Additional methods for enhanced UI
    fun refreshSatellites() {
        _isRefreshing.value = true
        // Re-start listening (this will trigger the flow again)
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
}

sealed interface SatelliteUiState {
    data object Loading : SatelliteUiState
    data class Success(
        val usedInFix: List<GnssSatellite>,
        val visibleOnly: List<GnssSatellite>,
        val searching: List<GnssSatellite>,
        val totalCount: Int
    ) : SatelliteUiState
    data class Error(val message: String) : SatelliteUiState
}
