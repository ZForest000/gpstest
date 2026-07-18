package com.example.gpstest.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gpstest.domain.model.NavigationMessageFrame
import com.example.gpstest.domain.repository.GnssRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NavigationMessageViewModel(
    application: Application,
    private val repository: GnssRepository,
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(NavigationMessageUiState())
    val uiState: StateFlow<NavigationMessageUiState> = _uiState.asStateFlow()

    private val buffer = ArrayDeque<NavigationMessageFrame>(MAX_BUFFER)
    private var collectionJob: Job? = null

    fun startListening() {
        if (collectionJob?.isActive == true) return
        collectionJob =
            viewModelScope.launch {
                try {
                    repository.getNavigationMessages().collect(::append)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // 部分设备不支持导航电文，不应影响主页面。
                }
            }
    }

    fun clear() {
        buffer.clear()
        _uiState.update { it.copy(frames = emptyList()) }
    }

    fun setSvidFilter(value: String) {
        _uiState.update { it.copy(svidFilter = value.filter(Char::isDigit)) }
    }

    private fun append(frame: NavigationMessageFrame) {
        while (buffer.size >= MAX_BUFFER) buffer.removeFirst()
        buffer.addLast(frame)
        _uiState.update { it.copy(frames = buffer.toList()) }
    }

    override fun onCleared() {
        collectionJob?.cancel()
        super.onCleared()
    }

    companion object {
        const val MAX_BUFFER = 200
    }
}

data class NavigationMessageUiState(
    val frames: List<NavigationMessageFrame> = emptyList(),
    val svidFilter: String = "",
) {
    val filteredFrames: List<NavigationMessageFrame>
        get() = svidFilter.toIntOrNull()?.let { svid -> frames.filter { it.svid == svid } } ?: frames
}
