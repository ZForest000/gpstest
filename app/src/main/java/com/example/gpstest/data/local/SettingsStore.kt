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
import com.example.gpstest.domain.model.AppSettings
import com.example.gpstest.domain.model.DarkModeConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

class SettingsStore(
    private val context: Context,
) {
    companion object {
        private val DARK_MODE = stringPreferencesKey("dark_mode")
        private val AUTO_SAVE_ENABLED = booleanPreferencesKey("auto_save_enabled")
        private val SNAPSHOT_INTERVAL_MS = longPreferencesKey("snapshot_interval_ms")
        private val MAX_SNAPSHOTS = intPreferencesKey("max_snapshots")
        private val RETENTION_DAYS = intPreferencesKey("retention_days")
        private val NMEA_ENABLED = booleanPreferencesKey("nmea_enabled")
    }

    val settings: Flow<AppSettings> =
        context.settingsDataStore.data.map { prefs ->
            AppSettings(
                darkMode =
                    runCatching {
                        DarkModeConfig.valueOf(prefs[DARK_MODE] ?: DarkModeConfig.SYSTEM.name)
                    }.getOrDefault(DarkModeConfig.SYSTEM),
                autoSaveEnabled = prefs[AUTO_SAVE_ENABLED] ?: true,
                snapshotIntervalMs =
                    prefs[SNAPSHOT_INTERVAL_MS] ?: AppSettings.DEFAULT_SNAPSHOT_INTERVAL_MS,
                maxSnapshots = prefs[MAX_SNAPSHOTS] ?: AppSettings.DEFAULT_MAX_SNAPSHOTS,
                retentionDays = prefs[RETENTION_DAYS] ?: AppSettings.DEFAULT_RETENTION_DAYS,
                nmeaEnabled = prefs[NMEA_ENABLED] ?: true,
            )
        }

    suspend fun updateSettings(settings: AppSettings) {
        context.settingsDataStore.edit { prefs ->
            prefs[DARK_MODE] = settings.darkMode.name
            prefs[AUTO_SAVE_ENABLED] = settings.autoSaveEnabled
            prefs[SNAPSHOT_INTERVAL_MS] = settings.snapshotIntervalMs
            prefs[MAX_SNAPSHOTS] = settings.maxSnapshots
            prefs[RETENTION_DAYS] = settings.retentionDays
            prefs[NMEA_ENABLED] = settings.nmeaEnabled
        }
    }
}
