package com.example.gpstest.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.gpstest.domain.model.AGpsSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.agpsDataStore: DataStore<Preferences> by preferencesDataStore(name = "agps_settings")

class AGpsSettingsStore(private val context: Context) {
    
    companion object {
        private val AUTO_UPDATE_ENABLED = booleanPreferencesKey("auto_update_enabled")
        private val UPDATE_INTERVAL_HOURS = intPreferencesKey("update_interval_hours")
        private val LAST_AUTO_UPDATE_TIME = longPreferencesKey("last_auto_update_time")
        private val DOWNLOAD_URL = stringPreferencesKey("download_url")
    }
    
    val settings: Flow<AGpsSettings> = context.agpsDataStore.data.map { prefs ->
        AGpsSettings(
            autoUpdateEnabled = prefs[AUTO_UPDATE_ENABLED] ?: false,
            updateIntervalHours = prefs[UPDATE_INTERVAL_HOURS] ?: 24,
            lastAutoUpdateTime = prefs[LAST_AUTO_UPDATE_TIME],
            downloadUrl = prefs[DOWNLOAD_URL] ?: AGpsSettings.DEFAULT_XTRA_URL
        )
    }
    
    suspend fun updateSettings(settings: AGpsSettings) {
        context.agpsDataStore.edit { prefs ->
            prefs[AUTO_UPDATE_ENABLED] = settings.autoUpdateEnabled
            prefs[UPDATE_INTERVAL_HOURS] = settings.updateIntervalHours
            settings.lastAutoUpdateTime?.let { prefs[LAST_AUTO_UPDATE_TIME] = it }
            prefs[DOWNLOAD_URL] = settings.downloadUrl
        }
    }
    
    suspend fun updateLastAutoUpdateTime(time: Long) {
        context.agpsDataStore.edit { prefs ->
            prefs[LAST_AUTO_UPDATE_TIME] = time
        }
    }
}
