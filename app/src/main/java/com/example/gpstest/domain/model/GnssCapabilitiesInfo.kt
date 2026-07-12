package com.example.gpstest.domain.model

/**
 * 设备 GNSS 能力查询结果。
 *
 * 由 [com.example.gpstest.data.source.GnssDataSource.getGnssCapabilities] 从
 * [android.location.LocationManager.gnssCapabilities] 提取，用于解释"某类数据为何为空"：
 * 若设备不支持原始测量 / 导航电文 / 天线信息，则对应 UI 为空是正常的。
 *
 * @property hardwareModelName GNSS 硬件型号名（API 28+，可能为空）
 * @property yearOfHardware GNSS 硬件年份（API 28+，可能为空）
 * @property hasMeasurements 原始 GNSS 测量值支持状态（API 31+）
 * @property hasNavigationMessages 导航电文支持状态（API 31+）
 * @property hasAntennaInfo 天线相位中心信息支持状态（API 31+）
 * @property hasAccumulatedDeltaRange 累积距离(ADR)支持状态（API 34+）
 * @property hasMeasurementCorrections 测量修正支持状态（API 34+）
 * @property hasMeasurementCorrelationVectors 测量相关向量支持状态（API 34+）
 *
 * 能力字段使用 Android 原始的 capability result 整型编码：
 * - 1 = [CapabilityState.SUPPORTED]
 * - 0 = [CapabilityState.UNSUPPORTED]
 * - -1 = [CapabilityState.UNKNOWN]
 * 通过 [toCapabilityState] 转换为类型安全枚举。
 */
data class GnssCapabilitiesInfo(
    val hardwareModelName: String?,
    val yearOfHardware: String?,
    val hasMeasurements: Int?,
    val hasNavigationMessages: Int?,
    val hasAntennaInfo: Int?,
    val hasAccumulatedDeltaRange: Int?,
    val hasMeasurementCorrections: Int?,
    val hasMeasurementCorrelationVectors: Int?,
)

/**
 * GNSS 能力三态：支持 / 不支持 / 未知。
 *
 * 对应 [android.location.GnssCapabilities] 的 capability result 常量。
 */
enum class CapabilityState {
    SUPPORTED,
    UNSUPPORTED,
    UNKNOWN,
}

/**
 * 将 Android capability result 整型编码转换为 [CapabilityState]。
 * 非法值统一降级为 [CapabilityState.UNKNOWN]。
 */
fun Int.toCapabilityState(): CapabilityState =
    when (this) {
        CAPABILITY_SUPPORTED -> CapabilityState.SUPPORTED
        CAPABILITY_UNSUPPORTED -> CapabilityState.UNSUPPORTED
        CAPABILITY_UNKNOWN -> CapabilityState.UNKNOWN
        else -> CapabilityState.UNKNOWN
    }

// Android GnssCapabilities 常量镜像（领域层不引入 android.location，保持纯净）
private const val CAPABILITY_SUPPORTED = 1
private const val CAPABILITY_UNSUPPORTED = 0
private const val CAPABILITY_UNKNOWN = -1
