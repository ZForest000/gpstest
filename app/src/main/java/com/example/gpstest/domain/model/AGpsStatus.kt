package com.example.gpstest.domain.model

import kotlinx.serialization.Serializable

/**
 * A-GPS 辅助数据在设备上的有效性状态。
 *
 * 分别追踪时间、星历（ephemeris）和历书（almanac）三种数据的状态，
 * 因为它们有效期不同：时间注入约 24h、星历约 4h、历书可达 30 天。
 */
data class AGpsStatus(
    val timeStatus: DataStatus = DataStatus.UNKNOWN,
    val ephemerisStatus: DataStatus = DataStatus.UNKNOWN,
    val almanacStatus: DataStatus = DataStatus.UNKNOWN,
    val lastUpdateTime: Long? = null,
    val lastInjectionTime: Long? = null,
)

/**
 * A-GPS 数据新鲜度状态。
 * - VALID：在有效期内，可直接使用
 * - EXPIRED：超过有效期，需要重新下载或注入
 * - PARTIAL：部分卫星有有效数据（如部分星历覆盖）
 * - MISSING：确认无数据（从未注入或已清除）
 * - UNKNOWN：尚未检查状态
 */
enum class DataStatus {
    VALID,
    EXPIRED,
    PARTIAL,
    MISSING,
    UNKNOWN,
}

/** 单次 A-GPS 注入操作的日志记录。id 使用时间戳以保证唯一性。 */
@Serializable
data class AGpsInjectionRecord(
    val id: String,
    val type: InjectionType,
    val source: InjectionSource,
    val timestamp: Long,
    val success: Boolean,
    val errorMessage: String? = null,
)

/** 注入数据类型：TIME 时间同步、EPHEMERIS 星历、ALMANAC 历书、XTRA 预测数据。 */
@Serializable
enum class InjectionType {
    TIME,
    EPHEMERIS,
    ALMANAC,
    XTRA,
}

/** 注入触发来源：MANUAL 用户手动、AUTO_DOWNLOAD 自动下载、NETWORK 网络时间同步。 */
@Serializable
enum class InjectionSource {
    MANUAL,
    AUTO_DOWNLOAD,
    NETWORK,
}

/** A-GPS 自动更新设置。默认 24 小时间隔，使用 Qualcomm izatcloud.net XTRA 数据源。 */
data class AGpsSettings(
    val autoUpdateEnabled: Boolean = false,
    val updateIntervalHours: Int = 24,
    val lastAutoUpdateTime: Long? = null,
    val downloadUrl: String = DEFAULT_XTRA_URL,
) {
    companion object {
        // Qualcomm 官方 XTRA 数据服务器，适用于骁龙平台的 GPS 硬件
        const val DEFAULT_XTRA_URL = "https://xtrapath1.izatcloud.net/xtra3grc.bin"
    }
}
