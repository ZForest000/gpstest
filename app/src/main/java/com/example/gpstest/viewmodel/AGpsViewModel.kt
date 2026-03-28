package com.example.gpstest.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gpstest.domain.model.AGpsInjectionRecord
import com.example.gpstest.domain.model.AGpsSettings
import com.example.gpstest.domain.model.AGpsStatus
import com.example.gpstest.domain.model.GnssSatellite
import com.example.gpstest.domain.repository.AGpsRepository
import com.example.gpstest.domain.repository.FileValidationResult
import com.example.gpstest.domain.repository.InjectionVerification
import com.example.gpstest.service.AGpsUpdateWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AGpsViewModel(
    application: Application,
    private val repository: AGpsRepository
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

    private val _verification = MutableStateFlow<InjectionVerification?>(null)
    val verification: StateFlow<InjectionVerification?> = _verification.asStateFlow()

    private val _validationResult = MutableStateFlow<FileValidationResult?>(null)
    val validationResult: StateFlow<FileValidationResult?> = _validationResult.asStateFlow()

    fun downloadAndInject() {
        viewModelScope.launch {
            _uiState.value = AGpsUiState.Downloading
            val result = repository.downloadAndInject()
            _uiState.value = if (result.isSuccess) {
                AGpsUiState.Success("A-GPS数据注入成功，请返回主界面查看卫星状态验证效果")
            } else {
                AGpsUiState.Error(result.exceptionOrNull()?.message ?: "下载失败")
            }
        }
    }

    fun injectFromFile(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = AGpsUiState.Injecting
            val result = repository.injectFromFile(uri.toString())
            _uiState.value = if (result.isSuccess) {
                AGpsUiState.Success("文件注入成功")
            } else {
                AGpsUiState.Error(result.exceptionOrNull()?.message ?: "注入失败")
            }
        }
    }

    fun injectTime() {
        viewModelScope.launch {
            _uiState.value = AGpsUiState.Injecting
            val result = repository.injectTime()
            _uiState.value = if (result.isSuccess) {
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
            _uiState.value = if (result.isSuccess) {
                AGpsUiState.Success("A-GPS数据已清除")
            } else {
                AGpsUiState.Error(result.exceptionOrNull()?.message ?: "清除失败")
            }
        }
    }

    fun verifyInjection(satellites: List<GnssSatellite>) {
        viewModelScope.launch {
            val result = repository.verifyInjection(satellites)
            _verification.value = result
        }
    }

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

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun validateFile(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = AGpsUiState.Injecting
            val result = repository.validateFile(uri.toString())
            _validationResult.value = result
            _uiState.value = if (result.isValid) {
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
            _uiState.value = if (result.isValid) {
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

sealed interface AGpsUiState {
    data object Idle : AGpsUiState
    data object Downloading : AGpsUiState
    data object Injecting : AGpsUiState
    data class Success(val message: String) : AGpsUiState
    data class Error(val message: String) : AGpsUiState
}
