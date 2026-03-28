package com.example.gpstest.domain.repository

import com.example.gpstest.domain.model.AGpsInjectionRecord
import com.example.gpstest.domain.model.AGpsSettings
import com.example.gpstest.domain.model.AGpsStatus
import com.example.gpstest.domain.model.GnssSatellite
import kotlinx.coroutines.flow.Flow

interface AGpsRepository {
    val status: Flow<AGpsStatus>
    val settings: Flow<AGpsSettings>
    val injectionHistory: Flow<List<AGpsInjectionRecord>>
    
    suspend fun downloadAndInject(): Result<Unit>
    suspend fun injectFromFile(fileUri: String): Result<Unit>
    suspend fun injectTime(): Result<Unit>
    suspend fun clearApsData(): Result<Unit>
    suspend fun refreshStatus()
    suspend fun updateSettings(settings: AGpsSettings)
    suspend fun clearHistory()
    
    suspend fun verifyInjection(satellites: List<GnssSatellite>): InjectionVerification
    suspend fun validateFile(fileUri: String): FileValidationResult
    suspend fun validateCurrentSource(): FileValidationResult
}

data class InjectionVerification(
    val satellitesWithEphemeris: Int,
    val satellitesWithAlmanac: Int,
    val totalSatellites: Int,
    val ephemerisRatio: Float,
    val almanacRatio: Float,
    val isSuccess: Boolean
) {
    val summary: String
        get() = "星历: $satellitesWithEphemeris/$totalSatellites (${(ephemerisRatio * 100).toInt()}%), " +
                "历书: $satellitesWithAlmanac/$totalSatellites (${(almanacRatio * 100).toInt()}%)"
}

data class FileValidationResult(
    val isValid: Boolean,
    val fileSize: Int,
    val errorMessage: String? = null,
    val errorType: String? = null,
    val details: String? = null
) {
    val summary: String
        get() = when {
            isValid -> "✓ 文件有效 (${fileSize / 1024}KB)"
            errorMessage != null -> "✗ 验证失败: $errorMessage"
            else -> "✓ 文件有效 (${fileSize / 1024}KB)"
        }
}
