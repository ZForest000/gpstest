package com.example.gpstest.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gpstest.domain.model.AGpsInjectionRecord
import com.example.gpstest.domain.model.AGpsSettings
import com.example.gpstest.domain.model.AGpsStatus
import com.example.gpstest.domain.repository.AGpsRepository
import com.example.gpstest.domain.repository.FileValidationResult
import com.example.gpstest.service.AGpsUpdateWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A-GPS 管理界面 ViewModel。管理下载注入、时间同步、数据清除和自动更新设置。
 *
 * 状态机：[AGpsUiState] 表示操作过程：
 * Idle → Downloading/Injecting → Success(message) → Idle（UI 调用 [clearMessage]）
 *                      ↘ Error(message)  → Idle（UI 调用 [clearMessage]）
 */
class AGpsViewModel(
    application: Application,
    private val repository: AGpsRepository,
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow<AGpsUiState>(AGpsUiState.Idle)
    val uiState: StateFlow<AGpsUiState> = _uiState.asStateFlow()

    val status: StateFlow<AGpsStatus> by lazy {
        MutableStateFlow(AGpsStatus()).apply {
            viewModelScope.launch {
                repository.status.collect { value = it }
            }
        }
    }

    val settings: StateFlow<AGpsSettings> by lazy {
        MutableStateFlow(AGpsSettings()).apply {
            viewModelScope.launch {
                repository.settings.collect { value = it }
            }
        }
    }

    val injectionHistory: StateFlow<List<AGpsInjectionRecord>> by lazy {
        MutableStateFlow<List<AGpsInjectionRecord>>(emptyList()).apply {
            viewModelScope.launch {
                repository.injectionHistory.collect { value = it }
            }
        }
    }

    init {
        viewModelScope.launch {
            repository.hydrateHistory()
        }
    }

    private val _validationResult = MutableStateFlow<FileValidationResult?>(null)
    val validationResult: StateFlow<FileValidationResult?> = _validationResult.asStateFlow()

    fun downloadAndInject() {
        viewModelScope.launch {
            _uiState.value = AGpsUiState.Downloading
            val result = repository.downloadAndInject()
            _uiState.value =
                if (result.isSuccess) {
                    AGpsUiState.Success("A-GPS数据注入成功，请返回主界面查看卫星状态验证效果")
                } else {
                    AGpsUiState.Error(result.exceptionOrNull()?.message ?: "下载失败")
                }
        }
    }

    fun injectTime() {
        viewModelScope.launch {
            _uiState.value = AGpsUiState.Injecting
            val result = repository.injectTime()
            _uiState.value =
                if (result.isSuccess) {
                    AGpsUiState.Success("时间同步成功")
                } else {
                    AGpsUiState.Error(result.exceptionOrNull()?.message ?: "时间同步失败")
                }
        }
    }

    fun clearApsData() {
        viewModelScope.launch {
            _uiState.value = AGpsUiState.Injecting
            val result = repository.clearApsData()
            _uiState.value =
                if (result.isSuccess) {
                    AGpsUiState.Success("A-GPS数据已清除")
                } else {
                    AGpsUiState.Error(result.exceptionOrNull()?.message ?: "清除失败")
                }
        }
    }

    // 更新设置后立即调度或取消 WorkManager 周期性任务
    fun updateSettings(settings: AGpsSettings) {
        viewModelScope.launch {
            repository.updateSettings(settings)

            if (settings.autoUpdateEnabled) {
                AGpsUpdateWorker.schedule(getApplication(), settings.updateIntervalHours)
            } else {
                AGpsUpdateWorker.cancel(getApplication())
            }
        }
    }

    fun refreshStatus() {
        viewModelScope.launch {
            repository.refreshStatus()
        }
    }

    fun validateFile(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = AGpsUiState.Injecting
            val result = repository.validateFile(uri.toString())
            _validationResult.value = result
            _uiState.value =
                if (result.isValid) {
                    AGpsUiState.Success("文件验证通过")
                } else {
                    AGpsUiState.Error(result.errorMessage ?: "验证失败")
                }
        }
    }

    fun clearValidationResult() {
        _validationResult.value = null
    }

    fun validateCurrentSource() {
        viewModelScope.launch {
            _uiState.value = AGpsUiState.Downloading
            val result = repository.validateCurrentSource()
            _validationResult.value = result
            _uiState.value =
                if (result.isValid) {
                    AGpsUiState.Success("下载源验证通过")
                } else {
                    AGpsUiState.Error(result.errorMessage ?: "验证失败")
                }
        }
    }

    fun clearMessage() {
        _uiState.value = AGpsUiState.Idle
    }
}

/**
 * A-GPS 管理界面操作状态。
 * - Idle：空闲状态，无操作进行中
 * - Downloading：正在下载 XTRA 数据
 * - Injecting：正在执行注入/时间同步/清除等操作
 * - Success：操作成功完成，message 为提示文本
 * - Error：操作失败，message 为错误描述
 */
sealed interface AGpsUiState {
    data object Idle : AGpsUiState

    data object Downloading : AGpsUiState

    data object Injecting : AGpsUiState

    data class Success(
        val message: String,
    ) : AGpsUiState

    data class Error(
        val message: String,
    ) : AGpsUiState
}
