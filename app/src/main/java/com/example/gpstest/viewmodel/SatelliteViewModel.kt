package com.example.gpstest.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gpstest.data.local.SettingsStore
import com.example.gpstest.data.source.DumpsysGnssData
import com.example.gpstest.domain.model.AppSettings
import com.example.gpstest.domain.model.DopInfo
import com.example.gpstest.domain.model.GnssCapabilitiesInfo
import com.example.gpstest.domain.model.GnssClockData
import com.example.gpstest.domain.model.GnssSatellite
import com.example.gpstest.domain.model.LocationInfo
import com.example.gpstest.domain.model.SatelliteGroup
import com.example.gpstest.domain.model.SatelliteHistorySnapshot
import com.example.gpstest.domain.repository.GnssRepository
import com.example.gpstest.domain.repository.SatelliteHistoryRepository
import com.example.gpstest.domain.util.DopCalculator
import com.example.gpstest.ui.components.SignalReading
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 卫星监控 ViewModel。管理 GNSS 数据收集、信号历史、TTFF 跟踪和快照自动保存。
 *
 * 数据流：GNSS 数据流 → 分组过滤 → UI State + DOP 计算 + 信号历史 + 快照存储。
 * 信号历史：每颗卫星维护一个 60 秒环形缓冲区（[maxSignalHistorySize]），用于信号图表。
 * 快照：按 [AppSettings] 配置的间隔自动保存卫星状态到 DataStore。
 */
class SatelliteViewModel(
    application: Application,
    private val repository: GnssRepository,
    private val historyRepository: SatelliteHistoryRepository? = null,
    private val settingsStore: SettingsStore? = null,
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow<SatelliteUiState>(SatelliteUiState.Loading)
    val uiState: StateFlow<SatelliteUiState> = _uiState.asStateFlow()

    private val _historySnapshots = MutableStateFlow<List<SatelliteHistorySnapshot>>(emptyList())
    val historySnapshots: StateFlow<List<SatelliteHistorySnapshot>> = _historySnapshots.asStateFlow()

    private val _signalHistory = MutableStateFlow<Map<String, MutableList<SignalReading>>>(emptyMap())
    val signalHistory: StateFlow<Map<String, List<SignalReading>>> = _signalHistory.asStateFlow()

    private val _ttffState = MutableStateFlow<TtffState>(TtffState.Measuring(System.currentTimeMillis()))
    val ttffState: StateFlow<TtffState> = _ttffState.asStateFlow()

    private val _gnssCapabilities = MutableStateFlow<GnssCapabilitiesInfo?>(null)
    val gnssCapabilities: StateFlow<GnssCapabilitiesInfo?> = _gnssCapabilities.asStateFlow()

    private var lastSnapshotTime = 0L
    private var autoSaveEnabled = true
    private var snapshotIntervalMs = AppSettings.DEFAULT_SNAPSHOT_INTERVAL_MS
    private var collectionJob: Job? = null

    private val maxSignalHistorySize = 60 // 每颗卫星保留 60 秒历史数据

    init {
        loadHistory()
        loadCapabilities()
        observeSettings()
    }

    private fun observeSettings() {
        val store = settingsStore ?: return
        viewModelScope.launch {
            store.settings.collect { settings ->
                autoSaveEnabled = settings.autoSaveEnabled
                snapshotIntervalMs = settings.snapshotIntervalMs
            }
        }
    }

    // GNSS 能力查询不依赖定位权限，在 init 中执行，权限拒绝时也能展示
    private fun loadCapabilities() {
        viewModelScope.launch {
            try {
                _gnssCapabilities.value = repository.getGnssCapabilities()
            } catch (e: Exception) {
                _gnssCapabilities.value = null
            }
        }
    }

    fun startListening() {
        collectionJob?.cancel()
        collectionJob =
            viewModelScope.launch {
                try {
                    repository.getGnssData().collect { gnssData ->
                        val satellites = gnssData.satellites
                        val grouped = satellites.groupBy { it.group }
                        val usedInFixList = grouped[SatelliteGroup.USED_IN_FIX].orEmpty()
                        val dopInfo = DopCalculator.calculate(usedInFixList)
                        _uiState.value =
                            SatelliteUiState.Success(
                                usedInFix = usedInFixList,
                                visibleOnly = grouped[SatelliteGroup.VISIBLE_ONLY].orEmpty(),
                                searching = grouped[SatelliteGroup.SEARCHING].orEmpty(),
                                totalCount = satellites.size,
                                location = gnssData.location,
                                clock = gnssData.clock,
                                dumpsysData = gnssData.dumpsysData,
                                dopInfo = dopInfo,
                            )

                        updateTtffState(gnssData.location)
                        updateSignalHistory(satellites)
                        maybeSaveSnapshot(satellites)
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.value = SatelliteUiState.Error(e.message ?: "Unknown error")
                }
            }
    }

    // TTFF（Time To First Fix）：从 startListening 到首次有效位置的时间差
    private fun updateTtffState(location: LocationInfo?) {
        val currentState = _ttffState.value
        if (location != null && currentState is TtffState.Measuring) {
            val ttffMs = System.currentTimeMillis() - currentState.startTime
            _ttffState.value = TtffState.Completed(ttffMs)
        }
    }

    fun resetTtff() {
        _ttffState.value = TtffState.Measuring(System.currentTimeMillis())
    }

    // 按"星座名称_SVID"为键，为每颗卫星维护信号历史环形缓冲区
    // 超过 maxSignalHistorySize 的旧数据从头部移除
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

    // 自动保存开启且距离上次快照超过 snapshotIntervalMs 时异步保存
    private fun maybeSaveSnapshot(satellites: List<GnssSatellite>) {
        if (!autoSaveEnabled) return
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

    fun clearHistory() {
        viewModelScope.launch {
            historyRepository?.clearHistory()
            _historySnapshots.value = emptyList()
        }
    }

    fun setPermissionDenied() {
        _uiState.value = SatelliteUiState.PermissionRequired
    }

    // 取消收集 Job 防止 ViewModel 销毁后仍在发射值
    override fun onCleared() {
        super.onCleared()
        collectionJob?.cancel()
    }
}

/**
 * 卫星列表界面状态。
 * - Loading：初始加载中
 * - PermissionRequired：缺少定位权限
 * - Success：数据正常，包含分组后的卫星列表、位置、时钟、DOP 信息
 * - Error：GNSS 数据流异常中断
 */
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
        val dumpsysData: DumpsysGnssData? = null,
        val dopInfo: DopInfo? = null,
    ) : SatelliteUiState

    data class Error(
        val message: String,
    ) : SatelliteUiState
}

/** 首次定位时间（Time To First Fix）状态：Measuring 测量中 / Completed 已完成。 */
sealed interface TtffState {
    data class Measuring(
        val startTime: Long,
    ) : TtffState

    data class Completed(
        val ttffMs: Long,
    ) : TtffState
}
