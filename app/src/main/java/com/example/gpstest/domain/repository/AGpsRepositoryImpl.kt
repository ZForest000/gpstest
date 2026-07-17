package com.example.gpstest.domain.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.gpstest.R
import com.example.gpstest.data.local.AGpsFileHandler
import com.example.gpstest.data.local.AGpsInjectionHistoryStore
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A-GPS 仓库实现。核心编排器：下载 → 验证 → 注入 → 状态跟踪。
 *
 * 实现多 URL 回落策略：用户配置 URL → 3 个 Qualcomm 默认地址。
 * 通过 [LocationManager.sendExtraCommand] 将 XTRA 数据的 URL 直接传递给
 * GPS HAL（硬件层内部处理下载），避免在 Java/Kotlin 层复制大二进制数据。
 *
 * 注入历史采用写穿缓存：内存 [MutableStateFlow] + [AGpsInjectionHistoryStore] 持久化。
 * 调用方应在启动时调用 [hydrateHistory] 加载已保存记录。
 */
class AGpsRepositoryImpl(
    private val context: Context,
    private val dataSource: AGpsDataSource,
    private val downloader: AGpsDownloader,
    private val fileHandler: AGpsFileHandler,
    private val settingsStore: AGpsSettingsStore,
    private val historyStore: AGpsInjectionHistoryStore,
    private val validator: XtraDataValidator = XtraDataValidator(),
) : AGpsRepository {
    companion object {
        private const val TAG = "AGpsRepository"

        // GPS 广播星历每 2 小时更新，有效期约 4 小时；超期后卫星钟差预报发散，精度下降
        private const val EPHEMERIS_VALID_HOURS = 4L

        // 历书为粗轨道信息，有效期数周至数月，保守用 30 天
        private const val ALMANAC_VALID_DAYS = 30L

        // 时间注入后接收机内部时钟漂移约 1μs/天，24h 内可接受
        private const val TIME_VALID_HOURS = 24L

        // 至少 50% 可见卫星有有效星历或历书才认为注入成功
        // 低于此比例常意味着数据过期或下载了错误文件（如 HTML 错误页面）
        private const val MIN_SUCCESS_RATIO = 0.5f

        private const val IMPORT_CACHE_FILE_NAME = "agps_import_xtra.bin"
    }

    private val _status = MutableStateFlow(AGpsStatus())
    override val status: Flow<AGpsStatus> = _status.asStateFlow()

    override val settings: Flow<AGpsSettings> = settingsStore.settings

    private val _injectionHistory = MutableStateFlow<List<AGpsInjectionRecord>>(emptyList())
    override val injectionHistory: Flow<List<AGpsInjectionRecord>> = _injectionHistory.asStateFlow()

    // 串行化历史读写，避免 Worker 空内存 / hydrate 竞态把 DataStore 历史覆盖成单条
    private val historyMutex = Mutex()

    // 回落策略：先尝试用户配置的 URL，失败后依次尝试 3 个默认地址
    // 每种 URL 的下载 + 注入均失败后才切换下一个
    override suspend fun downloadAndInject(): Result<Unit> {
        Log.d(TAG, "downloadAndInject: Starting...")
        val currentSettings = settings.first()
        val urls =
            (listOf(currentSettings.downloadUrl) + downloader.getDefaultUrls())
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

    override suspend fun injectTime(): Result<Unit> {
        Log.d(TAG, "injectTime: Starting...")
        val result = dataSource.injectTime(System.currentTimeMillis())

        addRecord(
            InjectionType.TIME,
            InjectionSource.MANUAL,
            result.isSuccess,
            result.exceptionOrNull()?.message,
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
                    lastInjectionTime = null,
                )
            }
            Log.d(TAG, "clearApsData: Success, status reset to UNKNOWN")
        } else {
            Log.e(TAG, "clearApsData: Failed: ${result.exceptionOrNull()?.message}")
        }

        return result
    }

    // 间接验证法：Android LocationManager API 不提供"注入是否成功"的反馈，
    // 因此改为统计注入后可见卫星中 hasEphemeris/hasAlmanac 的比例来推断
    override suspend fun verifyInjection(satellites: List<GnssSatellite>): InjectionVerification {
        if (satellites.isEmpty()) {
            Log.d(TAG, "verifyInjection: No satellites to verify")
            return InjectionVerification(
                satellitesWithEphemeris = 0,
                satellitesWithAlmanac = 0,
                totalSatellites = 0,
                ephemerisRatio = 0f,
                almanacRatio = 0f,
                isSuccess = false,
            )
        }

        val withEphemeris = satellites.count { it.hasEphemeris }
        val withAlmanac = satellites.count { it.hasAlmanac }
        val total = satellites.size

        val ephemerisRatio = withEphemeris.toFloat() / total
        val almanacRatio = withAlmanac.toFloat() / total

        val isSuccess = ephemerisRatio >= MIN_SUCCESS_RATIO || almanacRatio >= MIN_SUCCESS_RATIO

        Log.d(
            TAG,
            "verifyInjection: ephemeris=$withEphemeris/$total (${(ephemerisRatio * 100).toInt()}%), " +
                "almanac=$withAlmanac/$total (${(almanacRatio * 100).toInt()}%), success=$isSuccess",
        )

        val newStatus =
            _status.value.copy(
                ephemerisStatus =
                    when {
                        ephemerisRatio >= 0.7f -> DataStatus.VALID
                        ephemerisRatio >= 0.3f -> DataStatus.PARTIAL
                        total > 0 -> DataStatus.EXPIRED
                        else -> DataStatus.UNKNOWN
                    },
                almanacStatus =
                    when {
                        almanacRatio >= 0.7f -> DataStatus.VALID
                        almanacRatio >= 0.3f -> DataStatus.PARTIAL
                        total > 0 -> DataStatus.EXPIRED
                        else -> DataStatus.UNKNOWN
                    },
            )
        _status.value = newStatus

        return InjectionVerification(
            satellitesWithEphemeris = withEphemeris,
            satellitesWithAlmanac = withAlmanac,
            totalSatellites = total,
            ephemerisRatio = ephemerisRatio,
            almanacRatio = almanacRatio,
            isSuccess = isSuccess,
        )
    }

    // 基于时间衰减模型更新数据状态，不查询 GPS 硬件
    // 星历 4h 内 VALID，4-8h PARTIAL，之后 EXPIRED
    // 历书 30 天内 VALID，之后 EXPIRED
    override suspend fun refreshStatus() {
        val currentStatus = _status.value
        val now = System.currentTimeMillis()
        val timeReference = listOfNotNull(currentStatus.lastUpdateTime, currentStatus.lastInjectionTime).maxOrNull()
        val timeStatus =
            if (timeReference != null) {
                val elapsedHours = (now - timeReference) / (1000 * 60 * 60)
                if (elapsedHours < TIME_VALID_HOURS) DataStatus.VALID else DataStatus.EXPIRED
            } else {
                DataStatus.UNKNOWN
            }
        val ephemerisStatus =
            if (currentStatus.lastInjectionTime != null) {
                val elapsedHours = (now - currentStatus.lastInjectionTime) / (1000 * 60 * 60)
                when {
                    elapsedHours < EPHEMERIS_VALID_HOURS -> DataStatus.VALID
                    elapsedHours < EPHEMERIS_VALID_HOURS * 2 -> DataStatus.PARTIAL
                    else -> DataStatus.EXPIRED
                }
            } else {
                DataStatus.UNKNOWN
            }
        val almanacStatus =
            if (currentStatus.lastInjectionTime != null) {
                val elapsedHours = (now - currentStatus.lastInjectionTime) / (1000 * 60 * 60)
                if (elapsedHours < ALMANAC_VALID_DAYS * 24) DataStatus.VALID else DataStatus.EXPIRED
            } else {
                DataStatus.UNKNOWN
            }
        val newStatus =
            currentStatus.copy(
                timeStatus = timeStatus,
                ephemerisStatus = ephemerisStatus,
                almanacStatus = almanacStatus,
            )
        _status.value = newStatus
    }

    override suspend fun updateSettings(settings: AGpsSettings) {
        settingsStore.updateSettings(settings)
    }

    override suspend fun hydrateHistory() {
        historyMutex.withLock {
            _injectionHistory.value = historyStore.history.first()
        }
    }

    override suspend fun clearInjectionHistory() {
        historyMutex.withLock {
            _injectionHistory.value = emptyList()
            historyStore.clear()
        }
    }

    override suspend fun importAndInject(fileUri: String): Result<Unit> {
        Log.d(TAG, "importAndInject: $fileUri")

        val uri = Uri.parse(fileUri)
        val readResult = fileHandler.readFile(uri)
        if (readResult.isFailure) {
            val error =
                readResult.exceptionOrNull()?.message
                    ?: context.getString(R.string.agps_file_read_fail)
            Log.e(TAG, "importAndInject: read failed: $error")
            addRecord(InjectionType.XTRA, InjectionSource.MANUAL, false, error)
            return Result.failure(readResult.exceptionOrNull() ?: Exception(error))
        }

        val data = readResult.getOrThrow()
        val validationResult = validator.validate(data, sourceUrl = fileUri)
        if (!validationResult.isValid) {
            val error =
                validationResult.details
                    ?: context.getString(R.string.agps_validation_fail)
            Log.e(TAG, "importAndInject: validation failed: $error")
            addRecord(InjectionType.XTRA, InjectionSource.MANUAL, false, error)
            return Result.failure(Exception(error))
        }

        val writeResult = fileHandler.writeCacheFile(IMPORT_CACHE_FILE_NAME, data)
        if (writeResult.isFailure) {
            val error =
                writeResult.exceptionOrNull()?.message
                    ?: context.getString(R.string.agps_cache_write_fail)
            Log.e(TAG, "importAndInject: cache write failed: $error")
            addRecord(InjectionType.XTRA, InjectionSource.MANUAL, false, error)
            return Result.failure(writeResult.exceptionOrNull() ?: Exception(error))
        }

        val file = writeResult.getOrThrow()
        val injectUrl = "file://${file.absolutePath}"
        Log.d(TAG, "importAndInject: injecting via $injectUrl")
        val injectResult = dataSource.injectXtraFromUrl(injectUrl)

        if (injectResult.isSuccess) {
            Log.d(TAG, "importAndInject: success")
            addRecord(InjectionType.XTRA, InjectionSource.MANUAL, true)
            updateStatusAfterInjection()
            return Result.success(Unit)
        }

        val error =
            injectResult.exceptionOrNull()?.message
                ?: context.getString(R.string.agps_inject_fail)
        Log.e(TAG, "importAndInject: inject failed: $error")
        addRecord(InjectionType.XTRA, InjectionSource.MANUAL, false, error)
        return Result.failure(injectResult.exceptionOrNull() ?: Exception(error))
    }

    private suspend fun addRecord(
        type: InjectionType,
        source: InjectionSource,
        success: Boolean,
        errorMessage: String? = null,
    ) {
        val record =
            AGpsInjectionRecord(
                id = System.currentTimeMillis().toString(),
                type = type,
                source = source,
                timestamp = System.currentTimeMillis(),
                success = success,
                errorMessage = errorMessage,
            )

        historyMutex.withLock {
            // 始终以 store 为权威源合并，避免未 hydrate 的空内存写穿抹掉已有记录
            val existing = historyStore.history.first()
            val updated = (listOf(record) + existing).take(50)
            historyStore.replaceAll(updated)
            _injectionHistory.value = updated
        }
    }

    private fun updateStatusAfterInjection() {
        val now = System.currentTimeMillis()
        _status.update {
            it.copy(
                timeStatus = DataStatus.VALID,
                ephemerisStatus = DataStatus.VALID,
                almanacStatus = DataStatus.VALID,
                lastUpdateTime = now,
                lastInjectionTime = now,
            )
        }
    }

    private fun updateTimeStatusAfterInjection() {
        val now = System.currentTimeMillis()
        _status.update {
            it.copy(
                timeStatus = DataStatus.VALID,
                lastUpdateTime = now,
            )
        }
    }

    override suspend fun validateFile(fileUri: String): FileValidationResult {
        Log.d(TAG, "validateFile: $fileUri")

        val uri = Uri.parse(fileUri)
        val readResult = fileHandler.readFile(uri)

        if (readResult.isFailure) {
            val error =
                readResult.exceptionOrNull()?.message
                    ?: context.getString(R.string.agps_file_read_fail)
            Log.e(TAG, "validateFile: Failed to read file: $error")
            return FileValidationResult(
                isValid = false,
                fileSize = 0,
                errorMessage = error,
                errorType = "FILE_READ_ERROR",
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
                details = validator.getSizeStatistics(data),
            )
        }

        Log.i(TAG, "validateFile: File is valid | ${validator.getSizeStatistics(data)}")
        return FileValidationResult(
            isValid = true,
            fileSize = data.size,
            details = validator.getSizeStatistics(data),
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
                errorType = "EMPTY_URL",
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
                errorType = "DOWNLOAD_ERROR",
            )
        }

        val data = downloadResult.getOrThrow()

        if (data.isEmpty()) {
            Log.e(TAG, "validateCurrentSource: Downloaded data is empty")
            return FileValidationResult(
                isValid = false,
                fileSize = 0,
                errorMessage = "下载的数据为空",
                errorType = "EMPTY_DATA",
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
                details = validator.getSizeStatistics(data),
            )
        }

        Log.i(TAG, "validateCurrentSource: Source is valid | ${validator.getSizeStatistics(data)}")
        return FileValidationResult(
            isValid = true,
            fileSize = data.size,
            details = validator.getSizeStatistics(data),
        )
    }
}
