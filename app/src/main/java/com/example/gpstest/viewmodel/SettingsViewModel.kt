package com.example.gpstest.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gpstest.data.local.SettingsStore
import com.example.gpstest.domain.model.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    application: Application,
    private val settingsStore: SettingsStore,
) : AndroidViewModel(application) {
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        viewModelScope.launch {
            settingsStore.settings.collect { _settings.value = it }
        }
    }

    fun updateSettings(settings: AppSettings) {
        viewModelScope.launch {
            settingsStore.updateSettings(settings)
        }
    }
}
