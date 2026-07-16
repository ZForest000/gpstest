package com.example.gpstest.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.gpstest.domain.model.AppSettings
import com.example.gpstest.domain.model.SatelliteHistorySnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.historyDataStore: DataStore<Preferences> by preferencesDataStore(name = "satellite_history")

class SatelliteHistoryDataStore(
    private val context: Context,
    private val settingsStore: SettingsStore? = null,
) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private val SNAPSHOTS_KEY = stringPreferencesKey("snapshots_history")
        private const val MS_PER_DAY = 24L * 60 * 60 * 1000
    }

    val snapshots: Flow<List<SatelliteHistorySnapshot>> =
        context.historyDataStore.data
            .map { preferences ->
                val jsonString = preferences[SNAPSHOTS_KEY] ?: "[]"
                try {
                    json.decodeFromString(ListSerializer(SatelliteHistorySnapshot.serializer()), jsonString)
                } catch (e: Exception) {
                    emptyList()
                }
            }

    suspend fun saveSnapshot(snapshot: SatelliteHistorySnapshot) {
        val appSettings = settingsStore?.settings?.first() ?: AppSettings()
        val maxSnapshots = appSettings.maxSnapshots
        val retentionCutoff =
            System.currentTimeMillis() - appSettings.retentionDays * MS_PER_DAY

        context.historyDataStore.edit { preferences ->
            val currentList =
                try {
                    val jsonString = preferences[SNAPSHOTS_KEY] ?: "[]"
                    json.decodeFromString(ListSerializer(SatelliteHistorySnapshot.serializer()), jsonString)
                } catch (e: Exception) {
                    emptyList()
                }.toMutableList()

            currentList.add(0, snapshot)
            currentList.removeAll { it.timestamp < retentionCutoff }

            while (currentList.size > maxSnapshots) {
                currentList.removeAt(currentList.size - 1)
            }

            preferences[SNAPSHOTS_KEY] =
                json.encodeToString(
                    ListSerializer(SatelliteHistorySnapshot.serializer()),
                    currentList,
                )
        }
    }

    suspend fun deleteSnapshot(timestamp: Long) {
        context.historyDataStore.edit { preferences ->
            val currentList =
                try {
                    val jsonString = preferences[SNAPSHOTS_KEY] ?: "[]"
                    json.decodeFromString(ListSerializer(SatelliteHistorySnapshot.serializer()), jsonString)
                } catch (e: Exception) {
                    emptyList()
                }
            val updated = currentList.filterNot { it.timestamp == timestamp }
            preferences[SNAPSHOTS_KEY] =
                json.encodeToString(
                    ListSerializer(SatelliteHistorySnapshot.serializer()),
                    updated,
                )
        }
    }

    suspend fun clearHistory() {
        context.historyDataStore.edit { preferences ->
            preferences[SNAPSHOTS_KEY] = "[]"
        }
    }
}
