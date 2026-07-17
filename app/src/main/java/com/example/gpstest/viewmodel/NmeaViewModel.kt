package com.example.gpstest.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gpstest.data.local.SettingsStore
import com.example.gpstest.domain.model.NmeaParsedSnapshot
import com.example.gpstest.domain.model.NmeaSentence
import com.example.gpstest.domain.repository.GnssRepository
import com.example.gpstest.domain.util.NmeaParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * NMEA 实时流 ViewModel。
 *
 * - 环形缓冲区最多 [MAX_BUFFER] 条
 * - freeze 时取消 collect（暂停写入）
 * - 导出始终使用完整缓冲，不受类型过滤影响
 * - 监听权限与 [AppSettings.nmeaEnabled]
 */
class NmeaViewModel(
    application: Application,
    private val repository: GnssRepository,
    private val settingsStore: SettingsStore,
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(NmeaUiState())
    val uiState: StateFlow<NmeaUiState> = _uiState.asStateFlow()

    private val buffer = ArrayDeque<NmeaSentence>(MAX_BUFFER)
    private val recentTimestamps = ArrayDeque<Long>(64)
    private var collectionJob: Job? = null
    private var permissionGranted = false
    private var nmeaEnabled = true

    init {
        observeSettings()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsStore.settings.collect { settings ->
                nmeaEnabled = settings.nmeaEnabled
                _uiState.update { it.copy(enabled = nmeaEnabled) }
                syncCollection()
            }
        }
    }

    fun onPermissionChanged(granted: Boolean) {
        permissionGranted = granted
        _uiState.update { it.copy(permissionGranted = granted) }
        syncCollection()
    }

    fun setFrozen(frozen: Boolean) {
        _uiState.update { it.copy(frozen = frozen) }
        syncCollection()
    }

    fun clear() {
        buffer.clear()
        recentTimestamps.clear()
        _uiState.update {
            it.copy(
                sentences = emptyList(),
                bufferSize = 0,
                rateHz = 0f,
                typeCounts = emptyMap(),
                parsed = NmeaParsedSnapshot(),
            )
        }
    }

    fun setTypeFilter(type: String) {
        _uiState.update { it.copy(typeFilter = type) }
    }

    /** 导出用：完整缓冲（不受 typeFilter 影响）。 */
    fun getExportSentences(): List<NmeaSentence> = buffer.toList()

    private fun syncCollection() {
        val shouldCollect =
            permissionGranted && nmeaEnabled && !_uiState.value.frozen
        if (shouldCollect) {
            startCollecting()
        } else {
            collectionJob?.cancel()
            collectionJob = null
        }
    }

    private fun startCollecting() {
        if (collectionJob?.isActive == true) return
        collectionJob =
            viewModelScope.launch {
                try {
                    repository.getNmeaSentences().collect { sentence ->
                        appendSentence(sentence)
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // 监听失败时保持当前缓冲，不崩溃
                }
            }
    }

    private fun appendSentence(sentence: NmeaSentence) {
        while (buffer.size >= MAX_BUFFER) {
            buffer.removeFirst()
        }
        buffer.addLast(sentence)

        val now = sentence.timestampMs
        recentTimestamps.addLast(now)
        while (recentTimestamps.isNotEmpty() && now - recentTimestamps.first() > 1000L) {
            recentTimestamps.removeFirst()
        }

        val typeCounts = buffer.groupingBy { it.type }.eachCount()
        val parsed = NmeaParser.updateSnapshot(_uiState.value.parsed, sentence)

        _uiState.update {
            it.copy(
                sentences = buffer.toList(),
                bufferSize = buffer.size,
                rateHz = recentTimestamps.size.toFloat(),
                typeCounts = typeCounts,
                parsed = parsed,
            )
        }
    }

    companion object {
        const val MAX_BUFFER = 500
    }
}

data class NmeaUiState(
    val sentences: List<NmeaSentence> = emptyList(),
    val bufferSize: Int = 0,
    val maxBufferSize: Int = NmeaViewModel.MAX_BUFFER,
    val frozen: Boolean = false,
    val typeFilter: String = FILTER_ALL,
    val rateHz: Float = 0f,
    val typeCounts: Map<String, Int> = emptyMap(),
    val parsed: NmeaParsedSnapshot = NmeaParsedSnapshot(),
    val enabled: Boolean = true,
    val permissionGranted: Boolean = false,
) {
    val filteredSentences: List<NmeaSentence>
        get() =
            if (typeFilter == FILTER_ALL) {
                sentences
            } else {
                sentences.filter { it.type == typeFilter }
            }

    companion object {
        const val FILTER_ALL = "ALL"
    }
}
