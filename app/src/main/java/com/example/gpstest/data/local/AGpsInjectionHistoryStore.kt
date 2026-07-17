package com.example.gpstest.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.gpstest.domain.model.AGpsInjectionRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.agpsInjectionHistoryDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "agps_injection_history",
)

class AGpsInjectionHistoryStore(
    private val context: Context,
) {
    companion object {
        private val HISTORY_KEY = stringPreferencesKey("injection_history")
        private const val MAX_RECORDS = 50

        private val json =
            Json {
                ignoreUnknownKeys = true
            }

        fun encodeHistory(records: List<AGpsInjectionRecord>): String =
            json.encodeToString(
                ListSerializer(AGpsInjectionRecord.serializer()),
                records.take(MAX_RECORDS),
            )

        fun decodeHistory(jsonString: String): List<AGpsInjectionRecord> =
            try {
                json.decodeFromString(
                    ListSerializer(AGpsInjectionRecord.serializer()),
                    jsonString,
                )
            } catch (_: Exception) {
                emptyList()
            }
    }

    val history: Flow<List<AGpsInjectionRecord>> =
        context.agpsInjectionHistoryDataStore.data.map { preferences ->
            val jsonString = preferences[HISTORY_KEY] ?: "[]"
            decodeHistory(jsonString)
        }

    suspend fun replaceAll(records: List<AGpsInjectionRecord>) {
        context.agpsInjectionHistoryDataStore.edit { preferences ->
            preferences[HISTORY_KEY] = encodeHistory(records)
        }
    }

    suspend fun clear() {
        context.agpsInjectionHistoryDataStore.edit { preferences ->
            preferences[HISTORY_KEY] = "[]"
        }
    }
}
