package com.example.gpstest.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.gpstest.domain.model.SatelliteHistorySnapshot
import com.example.gpstest.domain.model.SatelliteHistoryConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.historyDataStore: DataStore<Preferences> by preferencesDataStore(name = "satellite_history")

class SatelliteHistoryDataStore(private val context: Context) {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    companion object {
        private val SNAPSHOTS_KEY = stringPreferencesKey("snapshots_history")
        private const val MAX_SNAPSHOTS = 100
    }
    
    val snapshots: Flow<List<SatelliteHistorySnapshot>> = context.historyDataStore.data
        .map { preferences ->
            val jsonString = preferences[SNAPSHOTS_KEY] ?: "[]"
            try {
                json.decodeFromString(ListSerializer(SatelliteHistorySnapshot.serializer()), jsonString)
            } catch (e: Exception) {
                emptyList()
            }
        }
    
    suspend fun saveSnapshot(snapshot: SatelliteHistorySnapshot) {
        context.historyDataStore.edit { preferences ->
            val currentList = try {
                val jsonString = preferences[SNAPSHOTS_KEY] ?: "[]"
                json.decodeFromString(ListSerializer(SatelliteHistorySnapshot.serializer()), jsonString)
            } catch (e: Exception) {
                emptyList()
            }.toMutableList()
            
            currentList.add(0, snapshot)
            
            while (currentList.size > MAX_SNAPSHOTS) {
                currentList.removeAt(currentList.size - 1)
            }
            
            preferences[SNAPSHOTS_KEY] = json.encodeToString(
                ListSerializer(SatelliteHistorySnapshot.serializer()),
                currentList
            )
        }
    }
    
    suspend fun clearHistory() {
        context.historyDataStore.edit { preferences ->
            preferences[SNAPSHOTS_KEY] = "[]"
        }
    }
    
    suspend fun clearOldSnapshots(olderThanTimestamp: Long) {
        context.historyDataStore.edit { preferences ->
            val currentList = try {
                val jsonString = preferences[SNAPSHOTS_KEY] ?: "[]"
                json.decodeFromString(ListSerializer(SatelliteHistorySnapshot.serializer()), jsonString)
            } catch (e: Exception) {
                emptyList()
            }
            
            val filteredList = currentList.filter { it.timestamp >= olderThanTimestamp }
            
            preferences[SNAPSHOTS_KEY] = json.encodeToString(
                ListSerializer(SatelliteHistorySnapshot.serializer()),
                filteredList
            )
        }
    }
}
