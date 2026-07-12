package com.example.gpstest.domain.repository

import com.example.gpstest.domain.model.AGpsInjectionRecord
import com.example.gpstest.domain.model.AGpsSettings
import com.example.gpstest.domain.model.AGpsStatus
import com.example.gpstest.domain.model.GnssSatellite
import kotlinx.coroutines.flow.Flow

/**
 * A-GPS 辅助数据仓库接口，管理完整生命周期：下载 → 注入 → 验证 → 状态跟踪。
 *
 * 核心流程与时效模型：
 * 1. [downloadAndInject] — 从备用地址下载 XTRA 预测数据并注入 GPS 硬件
 * 2. [verifyInjection] — 注入后通过统计可见卫星的星历/历书覆盖率间接验证成功
 * 3. [refreshStatus] — 基于时间衰减模型更新数据状态（不需查询硬件）
 *
 * 时效模型（基于 GNSS 数据物理特性）：
 * - 时间注入：24h 有效（接收机晶振漂移 ~1μs/天）
 * - 星历数据：4h 有效，4-8h 部分有效（GPS 每 2h 广播一次新星历）
 * - 历书数据：30 天有效（粗轨道信息，变化缓慢）
 *
 * 设计假设：设备使用 Qualcomm GPS 硬件（支持 sendExtraCommand 注入方式）
 * 和 izatcloud.net XTRA 数据格式。
 */
interface AGpsRepository {
    /** 当前 A-GPS 数据状态流（时间/星历/历书各自状态）。 */
    val status: Flow<AGpsStatus>

    /** 自动更新设置流。 */
    val settings: Flow<AGpsSettings>

    /** 注入历史记录流，最多保留最近 50 条。 */
    val injectionHistory: Flow<List<AGpsInjectionRecord>>

    /**
     * 从多个备选地址下载 XTRA 数据并注入 GPS 硬件。
     * 回落策略：先尝试用户配置的 URL，失败后依次尝试 3 个 Qualcomm 默认地址。
     * 注入成功时通过 [LocationManager.sendExtraCommand] 传递 URL 给 GPS HAL 内部处理。
     */
    suspend fun downloadAndInject(): Result<Unit>

    /** 将当前系统时间注入 GPS 接收机，用于加速首次定位（TTFF）。 */
    suspend fun injectTime(): Result<Unit>

    /** 清除 GPS 硬件中缓存的辅助数据。在数据损坏时使用。 */
    suspend fun clearApsData(): Result<Unit>

    /** 基于时间衰减模型刷新数据状态，不查询硬件。 */
    suspend fun refreshStatus()

    /** 更新自动下载设置并持久化到 DataStore。 */
    suspend fun updateSettings(settings: AGpsSettings)

    /**
     * 注入完成后，统计可见卫星中拥有有效星历/历书的比例来间接验证。
     * [MIN_SUCCESS_RATIO] = 0.5：任一指标 >=50% 即视为成功。
     */
    suspend fun verifyInjection(satellites: List<GnssSatellite>): InjectionVerification

    /** 验证本地文件是否为有效的 XTRA 数据文件。 */
    suspend fun validateFile(fileUri: String): FileValidationResult

    /** 从当前配置的 URL 下载并验证数据源是否有效。 */
    suspend fun validateCurrentSource(): FileValidationResult
}

/**
 * 注入验证结果。
 * @property isSuccess ephemerisRatio 或 almanacRatio >= 0.5 时为 true
 */
data class InjectionVerification(
    val satellitesWithEphemeris: Int,
    val satellitesWithAlmanac: Int,
    val totalSatellites: Int,
    val ephemerisRatio: Float,
    val almanacRatio: Float,
    val isSuccess: Boolean,
) {
    val summary: String
        get() =
            "星历: $satellitesWithEphemeris/$totalSatellites (${(ephemerisRatio * 100).toInt()}%), " +
                "历书: $satellitesWithAlmanac/$totalSatellites (${(almanacRatio * 100).toInt()}%)"
}

/** 文件验证结果。@property errorType 分类：FILE_READ_ERROR / DOWNLOAD_ERROR / EMPTY_DATA 等。 */
data class FileValidationResult(
    val isValid: Boolean,
    val fileSize: Int,
    val errorMessage: String? = null,
    val errorType: String? = null,
    val details: String? = null,
) {
    val summary: String
        get() =
            when {
                isValid -> "✓ 文件有效 (${fileSize / 1024}KB)"
                errorMessage != null -> "✗ 验证失败: $errorMessage"
                else -> "✓ 文件有效 (${fileSize / 1024}KB)"
            }
}
