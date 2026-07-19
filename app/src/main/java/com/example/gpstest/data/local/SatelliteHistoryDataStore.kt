package com.example.gpstest.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.gpstest.domain.model.SatelliteHistorySnapshot
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.historyDataStore: DataStore<Preferences> by preferencesDataStore(name = "satellite_history")

/** 旧版 DataStore JSON 的适配器；迁移决策由 SatelliteHistoryPersistence 协调。 */
internal class SatelliteHistoryDataStore(
    private val context: Context,
) : LegacySatelliteHistoryStore {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun readLegacyHistory(): LegacySatelliteHistory {
        val preferences = context.historyDataStore.data.first()
        return LegacySatelliteHistory(
            snapshots = decodeSnapshots(preferences[SNAPSHOTS_KEY]),
            markerWritten = preferences[ROOM_MIGRATED_KEY] == true,
        )
    }

    override suspend fun markRoomMigrationComplete() {
        context.historyDataStore.edit { preferences ->
            preferences[ROOM_MIGRATED_KEY] = true
        }
    }

    override suspend fun clearLegacyHistory() {
        context.historyDataStore.edit { preferences ->
            preferences[SNAPSHOTS_KEY] = "[]"
            preferences[ROOM_MIGRATED_KEY] = true
        }
    }

    private fun decodeSnapshots(jsonString: String?): List<SatelliteHistorySnapshot> =
        try {
            json.decodeFromString(
                ListSerializer(SatelliteHistorySnapshot.serializer()),
                jsonString ?: "[]",
            )
        } catch (_: Exception) {
            emptyList()
        }

    private companion object {
        val SNAPSHOTS_KEY = stringPreferencesKey("snapshots_history")
        val ROOM_MIGRATED_KEY = booleanPreferencesKey("room_history_migrated")
    }
}
