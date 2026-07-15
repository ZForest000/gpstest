package com.example.gpstest.domain.model

/**
 * GNSS 接收机时钟模型。
 *
 * 描述接收机硬件时钟相对于 GPS 系统时间的偏差和漂移。
 * 时钟偏差用于修正伪距测量：corrected_pseudorange = measured - totalBiasNanos * speedOfLight
 * 时钟漂移用于修正多普勒/伪距率。
 *
 * 这些数据来自 [GnssMeasurementsEvent] 的 [GnssClock]，
 * 各字段在 Android 24+ 的可用性不同（bias/biasUncertainty 需要 API 26+）。
 */
data class GnssClockData(
    // 接收机硬件时钟读数（自启动以来的纳秒数）
    val timeNanos: Long,
    // 亚纳秒级时钟偏差（API 26+），与 fullBiasNanos 合并后得到总偏差
    val biasNanos: Double?,
    // 完整时钟偏差的整数部分（纳秒），和 biasNanos 构成 totalBiasNanos
    val fullBiasNanos: Long?,
    // 时钟漂移率（纳秒/秒），反映接收机晶振频率偏移
    val driftNanosPerSecond: Double?,
    // 时钟偏差的 1-sigma 不确定度（API 26+）
    val biasUncertaintyNanos: Double?,
    // 时钟漂移的 1-sigma 不确定度（API 26+）
    val driftUncertaintyNanosPerSecond: Double?,
    // 硬件时钟重置计数。非零值表示时钟曾跳变，连续 ADR 测量值在此之后不可靠
    val hardwareClockDiscontinuityCount: Int,
    // GPS 时与 UTC 之间的闰秒差（秒），用于 GPS 时↔UTC 换算
    val leapSecond: Int? = null,
) {
    val totalBiasNanos: Double?
        get() {
            return if (fullBiasNanos != null && biasNanos != null) {
                fullBiasNanos.toDouble() + biasNanos
            } else {
                null
            }
        }

    val totalBiasMicroseconds: Double?
        get() = totalBiasNanos?.div(1000.0)

    val driftMicrosecondsPerSecond: Double?
        get() = driftNanosPerSecond?.div(1000.0)
}
