package com.example.gpstest.domain.repository

import android.net.Uri
import android.util.Log
import com.example.gpstest.data.local.AGpsFileHandler
import com.example.gpstest.data.local.AGpsSettingsStore
import com.example.gpstest.data.source.AGpsDataSource
import com.example.gpstest.data.source.AGpsDownloader
import com.example.gpstest.data.validator.XtraDataValidator
import com.example.gpstest.domain.model.AGpsInjectionRecord
import com.example.gpstest.domain.model.AGpsSettings
import com.example.gpstest.domain.model.AGpsStatus
import com.example.gpstest.domain.model.DataStatus
import com.example.gpstest.domain.model.GnssSatellite
import com.example.gpstest.domain.model.InjectionSource
import com.example.gpstest.domain.model.InjectionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

class AGpsRepositoryImpl(
    private val dataSource: AGpsDataSource,
    private val downloader: AGpsDownloader,
    private val fileHandler: AGpsFileHandler,
    private val settingsStore: AGpsSettingsStore,
    private val validator: XtraDataValidator = XtraDataValidator()
) : AGpsRepository {

    companion object {
        private const val TAG = "AGpsRepository"
        private const val EPHEMERIS_VALID_HOURS = 4L
        private const val ALMANAC_VALID_DAYS = 30L
        private const val TIME_VALID_HOURS = 24L
        private const val MIN_SUCCESS_RATIO = 0.5f
    }

    private val _status = MutableStateFlow(AGpsStatus())
    override val status: Flow<AGpsStatus> = _status.asStateFlow()

    override val settings: Flow<AGpsSettings> = settingsStore.settings

    private val _injectionHistory = MutableStateFlow<List<AGpsInjectionRecord>>(emptyList())
    override val injectionHistory: Flow<List<AGpsInjectionRecord>> = _injectionHistory.asStateFlow()

    override suspend fun downloadAndInject(): Result<Unit> {
        Log.d(TAG, "downloadAndInject: Starting...")
        val currentSettings = settings.first()
        val urls = (listOf(currentSettings.downloadUrl) + downloader.getDefaultUrls())
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        val errors = mutableListOf<String>()
        
        for (url in urls) {
            Log.d(TAG, "downloadAndInject: Verifying download source: $url")
            val downloadResult = downloader.download(url)

            if (downloadResult.isFailure) {
                val error = downloadResult.exceptionOrNull()?.message ?: "Unknown error"
                Log.w(TAG, "downloadAndInject: Download failed: $error")
                errors.add("Download($url): $error")
                continue
            }

            val data = downloadResult.getOrThrow()
            if (data.isEmpty()) {
                val error = "Empty data"
                Log.w(TAG, "downloadAndInject: Download returned empty data from $url")
                errors.add("Download($url): $error")
                continue
            }

            Log.d(TAG, "downloadAndInject: Download verified (${data.size} bytes), injecting via URL")
            val injectResult = dataSource.injectXtraFromUrl(url)

            if (injectResult.isSuccess) {
                Log.d(TAG, "downloadAndInject: URL injection command accepted")
                addRecord(InjectionType.XTRA, InjectionSource.AUTO_DOWNLOAD, true)
                updateStatusAfterInjection()
                return Result.success(Unit)
            } else {
                val error = injectResult.exceptionOrNull()?.message ?: "Unknown error"
                Log.w(TAG, "downloadAndInject: URL injection failed: $error")
                errors.add("Inject($url): $error")
            }
        }

        val allErrors = errors.joinToString("; ")
        Log.e(TAG, "downloadAndInject: All methods failed: $allErrors")
        addRecord(InjectionType.XTRA, InjectionSource.AUTO_DOWNLOAD, false, allErrors)
        return Result.failure(Exception("All download and injection methods failed: $allErrors"))
    }

    override suspend fun injectFromFile(fileUri: String): Result<Unit> {
        Log.d(TAG, "injectFromFile: $fileUri")
        val uri = Uri.parse(fileUri)
        val readResult = fileHandler.readFile(uri)
        
        if (readResult.isFailure) {
            val error = readResult.exceptionOrNull()?.message ?: "Failed to read file"
            Log.e(TAG, "injectFromFile: Failed to read file: $error")
            addRecord(InjectionType.XTRA, InjectionSource.MANUAL, false, error)
            return Result.failure(readResult.exceptionOrNull() ?: Exception("Failed to read file"))
        }
        
        val data = readResult.getOrThrow()
        Log.d(TAG, "injectFromFile: File read succeeded, size: ${data.size} bytes")
        
        val injectResult = dataSource.injectXtraData(data)
        
        addRecord(
            InjectionType.XTRA, 
            InjectionSource.MANUAL, 
            injectResult.isSuccess,
            injectResult.exceptionOrNull()?.message
        )
        
        if (injectResult.isSuccess) {
            updateStatusAfterInjection()
        }
        
        return injectResult
    }

    override suspend fun injectTime(): Result<Unit> {
        Log.d(TAG, "injectTime: Starting...")
        val result = dataSource.injectTime(System.currentTimeMillis())
        
        addRecord(
            InjectionType.TIME,
            InjectionSource.MANUAL,
            result.isSuccess,
            result.exceptionOrNull()?.message
        )
        
        if (result.isSuccess) {
            updateTimeStatusAfterInjection()
        }
        
        Log.d(TAG, "injectTime: Result: ${if (result.isSuccess) "success" else result.exceptionOrNull()?.message}")
        return result
    }

    override suspend fun clearApsData(): Result<Unit> {
        Log.d(TAG, "clearApsData: Starting...")
        val result = dataSource.clearApsData()
        
        if (result.isSuccess) {
            _status.update {
                it.copy(
                    timeStatus = DataStatus.UNKNOWN,
                    ephemerisStatus = DataStatus.UNKNOWN,
                    almanacStatus = DataStatus.UNKNOWN,
                    lastInjectionTime = null
                )
            }
            Log.d(TAG, "clearApsData: Success, status reset to UNKNOWN")
        } else {
            Log.e(TAG, "clearApsData: Failed: ${result.exceptionOrNull()?.message}")
        }
        
        return result
    }

    override suspend fun verifyInjection(satellites: List<GnssSatellite>): InjectionVerification {
        if (satellites.isEmpty()) {
            Log.d(TAG, "verifyInjection: No satellites to verify")
            return InjectionVerification(
                satellitesWithEphemeris = 0,
                satellitesWithAlmanac = 0,
                totalSatellites = 0,
                ephemerisRatio = 0f,
                almanacRatio = 0f,
                isSuccess = false
            )
        }
        
        val withEphemeris = satellites.count { it.hasEphemeris }
        val withAlmanac = satellites.count { it.hasAlmanac }
        val total = satellites.size
        
        val ephemerisRatio = withEphemeris.toFloat() / total
        val almanacRatio = withAlmanac.toFloat() / total
        
        val isSuccess = ephemerisRatio >= MIN_SUCCESS_RATIO || almanacRatio >= MIN_SUCCESS_RATIO
        
        Log.d(TAG, "verifyInjection: ephemeris=$withEphemeris/$total (${(ephemerisRatio*100).toInt()}%), " +
                "almanac=$withAlmanac/$total (${(almanacRatio*100).toInt()}%), success=$isSuccess")
        
        val newStatus = _status.value.copy(
            ephemerisStatus = when {
                ephemerisRatio >= 0.7f -> DataStatus.VALID
                ephemerisRatio >= 0.3f -> DataStatus.PARTIAL
                total > 0 -> DataStatus.EXPIRED
                else -> DataStatus.UNKNOWN
            },
            almanacStatus = when {
                almanacRatio >= 0.7f -> DataStatus.VALID
                almanacRatio >= 0.3f -> DataStatus.PARTIAL
                total > 0 -> DataStatus.EXPIRED
                else -> DataStatus.UNKNOWN
            }
        )
        _status.value = newStatus
        
        return InjectionVerification(
            satellitesWithEphemeris = withEphemeris,
            satellitesWithAlmanac = withAlmanac,
            totalSatellites = total,
            ephemerisRatio = ephemerisRatio,
            almanacRatio = almanacRatio,
            isSuccess = isSuccess
        )
    }

    override suspend fun refreshStatus() {
        val currentStatus = _status.value
        val now = System.currentTimeMillis()
        val timeReference = listOfNotNull(currentStatus.lastUpdateTime, currentStatus.lastInjectionTime).maxOrNull()
        val timeStatus = if (timeReference != null) {
            val elapsedHours = (now - timeReference) / (1000 * 60 * 60)
            if (elapsedHours < TIME_VALID_HOURS) DataStatus.VALID else DataStatus.EXPIRED
        } else {
            DataStatus.UNKNOWN
        }
        val ephemerisStatus = if (currentStatus.lastInjectionTime != null) {
            val elapsedHours = (now - currentStatus.lastInjectionTime) / (1000 * 60 * 60)
            when {
                elapsedHours < EPHEMERIS_VALID_HOURS -> DataStatus.VALID
                elapsedHours < EPHEMERIS_VALID_HOURS * 2 -> DataStatus.PARTIAL
                else -> DataStatus.EXPIRED
            }
        } else {
            DataStatus.UNKNOWN
        }
        val almanacStatus = if (currentStatus.lastInjectionTime != null) {
            val elapsedHours = (now - currentStatus.lastInjectionTime) / (1000 * 60 * 60)
            if (elapsedHours < ALMANAC_VALID_DAYS * 24) DataStatus.VALID else DataStatus.EXPIRED
        } else {
            DataStatus.UNKNOWN
        }
        val newStatus = currentStatus.copy(
            timeStatus = timeStatus,
            ephemerisStatus = ephemerisStatus,
            almanacStatus = almanacStatus
        )
        _status.value = newStatus
    }

    override suspend fun updateSettings(settings: AGpsSettings) {
        settingsStore.updateSettings(settings)
    }

    override suspend fun clearHistory() {
        _injectionHistory.value = emptyList()
    }

    private fun addRecord(
        type: InjectionType,
        source: InjectionSource,
        success: Boolean,
        errorMessage: String? = null
    ) {
        val record = AGpsInjectionRecord(
            id = System.currentTimeMillis().toString(),
            type = type,
            source = source,
            timestamp = System.currentTimeMillis(),
            success = success,
            errorMessage = errorMessage
        )
        
        _injectionHistory.update { listOf(record) + it.take(49) }
    }

    private fun updateStatusAfterInjection() {
        val now = System.currentTimeMillis()
        _status.update { 
            it.copy(
                timeStatus = DataStatus.VALID,
                ephemerisStatus = DataStatus.VALID,
                almanacStatus = DataStatus.VALID,
                lastUpdateTime = now,
                lastInjectionTime = now
            )
        }
    }

    private fun updateTimeStatusAfterInjection() {
        val now = System.currentTimeMillis()
        _status.update { 
            it.copy(
                timeStatus = DataStatus.VALID,
                lastUpdateTime = now
            )
        }
    }

    override suspend fun validateFile(fileUri: String): FileValidationResult {
        Log.d(TAG, "validateFile: $fileUri")
        
        val uri = Uri.parse(fileUri)
        val readResult = fileHandler.readFile(uri)
        
        if (readResult.isFailure) {
            val error = readResult.exceptionOrNull()?.message ?: "无法读取文件"
            Log.e(TAG, "validateFile: Failed to read file: $error")
            return FileValidationResult(
                isValid = false,
                fileSize = 0,
                errorMessage = error,
                errorType = "FILE_READ_ERROR"
            )
        }
        
        val data = readResult.getOrThrow()
        Log.d(TAG, "validateFile: File read succeeded, size: ${data.size} bytes")
        
        val validationResult = validator.validate(data, sourceUrl = fileUri)
        
        if (!validationResult.isValid) {
            Log.e(TAG, "validateFile: Validation failed: ${validationResult.details}")
            return FileValidationResult(
                isValid = false,
                fileSize = data.size,
                errorMessage = validationResult.details,
                errorType = validationResult.errorType?.name ?: "UNKNOWN",
                details = validator.getSizeStatistics(data)
            )
        }
        
        Log.i(TAG, "validateFile: File is valid | ${validator.getSizeStatistics(data)}")
        return FileValidationResult(
            isValid = true,
            fileSize = data.size,
            details = validator.getSizeStatistics(data)
        )
    }

    override suspend fun validateCurrentSource(): FileValidationResult {
        Log.d(TAG, "validateCurrentSource: Starting...")
        val currentSettings = settings.first()
        val url = currentSettings.downloadUrl.trim()
        
        if (url.isEmpty()) {
            Log.e(TAG, "validateCurrentSource: Download URL is empty")
            return FileValidationResult(
                isValid = false,
                fileSize = 0,
                errorMessage = "下载地址为空",
                errorType = "EMPTY_URL"
            )
        }
        
        Log.d(TAG, "validateCurrentSource: Downloading from $url")
        val downloadResult = downloader.download(url)
        
        if (downloadResult.isFailure) {
            val error = downloadResult.exceptionOrNull()?.message ?: "下载失败"
            Log.e(TAG, "validateCurrentSource: Download failed: $error")
            return FileValidationResult(
                isValid = false,
                fileSize = 0,
                errorMessage = error,
                errorType = "DOWNLOAD_ERROR"
            )
        }
        
        val data = downloadResult.getOrThrow()
        
        if (data.isEmpty()) {
            Log.e(TAG, "validateCurrentSource: Downloaded data is empty")
            return FileValidationResult(
                isValid = false,
                fileSize = 0,
                errorMessage = "下载的数据为空",
                errorType = "EMPTY_DATA"
            )
        }
        
        Log.d(TAG, "validateCurrentSource: Downloaded ${data.size} bytes, validating...")
        val validationResult = validator.validate(data, sourceUrl = url)
        
        if (!validationResult.isValid) {
            Log.e(TAG, "validateCurrentSource: Validation failed: ${validationResult.details}")
            return FileValidationResult(
                isValid = false,
                fileSize = data.size,
                errorMessage = validationResult.details,
                errorType = validationResult.errorType?.name ?: "UNKNOWN",
                details = validator.getSizeStatistics(data)
            )
        }
        
        Log.i(TAG, "validateCurrentSource: Source is valid | ${validator.getSizeStatistics(data)}")
        return FileValidationResult(
            isValid = true,
            fileSize = data.size,
            details = validator.getSizeStatistics(data)
        )
    }
}
