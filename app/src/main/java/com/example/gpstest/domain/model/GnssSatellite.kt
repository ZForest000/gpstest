package com.example.gpstest.domain.model

import android.location.GnssMeasurement

/**
 * 多路径效应指示器。
 *
 * GNSS 信号经建筑物等表面反射后到达接收机，使测距产生偏差。
 * - DETECTED：测量值可能受多路径干扰，伪距精度下降
 * - NOT_DETECTED：信号直接到达，测量值可信
 * - UNKNOWN：接收机无法判断
 */
enum class MultipathIndicator {
    UNKNOWN,
    DETECTED,
    NOT_DETECTED,
    ;

    companion object {
        fun fromInt(value: Int): MultipathIndicator =
            when (value) {
                1 -> DETECTED
                2 -> NOT_DETECTED
                else -> UNKNOWN
            }
    }
}

/**
 * 单颗 GNSS 卫星在某一时刻的快照数据。
 *
 * 合并自两个 Android 平台回调：
 * - [GnssStatus.Callback]：基础信息（星座、CN0、方位角、仰角、星历/历书标志）
 * - [GnssMeasurementsEvent.Callback]：原始测量值（多普勒频移、多路径、累积
 *   载波相位 ADR、伪距率）
 *
 * 合并策略：测量回调先触发，由 [GnssEventFusion] 暂存测量数据；状态回调随后触发时，
 * 通过 "星座_SVID" 键将两者合并。
 */

data class GnssSatellite(
    val svid: Int,
    val constellation: Constellation,
    val rawConstellationType: Int,
    val cn0DbHz: Float,
    val azimuthDegrees: Float,
    val elevationDegrees: Float,
    val hasAlmanac: Boolean,
    val hasEphemeris: Boolean,
    val usedInFix: Boolean,
    val carrierFrequencyHz: Float?,
    val carrierCycles: Long?,
    val dopplerShiftHz: Double?,
    val timeNanos: Long,
    val agcLevelDb: Double? = null,
    val multipathIndicator: MultipathIndicator? = null,
    val basebandCn0DbHz: Float? = null,
    val accumulatedDeltaRangeMeters: Double? = null,
    // 位掩码：ADR_STATE_VALID(bit 0)、ADR_STATE_CYCLE_SLIP(bit 2) 等
    val accumulatedDeltaRangeState: Int? = null,
    val accumulatedDeltaRangeUncertaintyMeters: Double? = null,
    val receivedSvTimeNanos: Long? = null,
    val receivedSvTimeUncertaintyNanos: Double? = null,
    val pseudorangeRateMetersPerSecond: Double? = null,
    // 位掩码：STATE_TOW_DECODED、STATE_CODE_LOCK、STATE_BIT_SYNC、STATE_SUBFRAME_SYNC
    val measurementState: Int? = null,
    val measurementCn0DbHz: Double? = null,
    val fullCarrierPhaseCycleCount: Long? = null,
    val pseudorangeMeters: Double? = null,
    val pseudorangeUncertaintyMeters: Double? = null,
    val pseudorangeStatus: PseudorangeStatus = PseudorangeStatus.MISSING_MEASUREMENT,
) {
    // 分类逻辑：正在参与定位解算 → USED_IN_FIX；有信号但未参与 → VISIBLE_ONLY；其余 → SEARCHING
    val group: SatelliteGroup
        get() =
            when {
                usedInFix -> SatelliteGroup.USED_IN_FIX
                cn0DbHz > 0 -> SatelliteGroup.VISIBLE_ONLY
                else -> SatelliteGroup.SEARCHING
            }

    // CN0 阈值：>=35 dB-Hz 强信号（通常是天顶卫星），>=25 中等，<25 弱信号
    val signalStrength: SignalStrength
        get() =
            when {
                cn0DbHz >= 35f -> SignalStrength.STRONG
                cn0DbHz >= 25f -> SignalStrength.MEDIUM
                else -> SignalStrength.WEAK
            }

    // ADR_STATE_VALID 未置位时 ADR 值可能因周跳导致整数米级跳变，不可使用
    val isAdrValid: Boolean
        get() =
            accumulatedDeltaRangeState?.let { state ->
                (state and GnssMeasurement.ADR_STATE_VALID) != 0
            } ?: false

    // 周跳（cycle slip）表示载波相位锁定临时丢失，导致 ADR 产生整数倍跳变
    val hasCycleSlip: Boolean
        get() =
            accumulatedDeltaRangeState?.let { state ->
                (state and GnssMeasurement.ADR_STATE_CYCLE_SLIP) != 0
            } ?: false

    // 载波相位锁定且已解码 TOW = 跟踪环达到最稳定状态
    val hasCarrierPhaseLock: Boolean
        get() =
            measurementState?.let { state ->
                (state and GnssMeasurement.STATE_TOW_DECODED) != 0
            } ?: false

    // 码锁定：跟踪环已锁定 C/A 码，能进行伪距测量 —— 跟踪层级的第一步
    val hasCodeLock: Boolean
        get() =
            measurementState?.let { state ->
                (state and GnssMeasurement.STATE_CODE_LOCK) != 0
            } ?: false

    // 位同步：已解调导航电文比特边界，跟踪层级第二步
    val hasBitSync: Boolean
        get() =
            measurementState?.let { state ->
                (state and GnssMeasurement.STATE_BIT_SYNC) != 0
            } ?: false

    // 子帧同步：已锁定子帧结构，能解析完整的导航电文 —— 跟踪层级最高步
    val hasSubframeSync: Boolean
        get() =
            measurementState?.let { state ->
                (state and GnssMeasurement.STATE_SUBFRAME_SYNC) != 0
            } ?: false

    /**
     * 有效载波相位（周）。
     *
     * 优先使用已废弃但若芯片仍上报的 [carrierCycles] / [fullCarrierPhaseCycleCount]；
     * 否则由 ADR（米）÷ 波长换算：cycles = ADR_m × f / c。
     * 仅在 ADR 有效且无周跳、且已知载波频率时才从 ADR 推导。
     */
    val effectiveCarrierPhaseCycles: Double?
        get() {
            carrierCycles?.let { return it.toDouble() }
            fullCarrierPhaseCycleCount?.let { return it.toDouble() }
            val adrMeters = accumulatedDeltaRangeMeters ?: return null
            val frequencyHz = carrierFrequencyHz ?: return null
            if (!isAdrValid || hasCycleSlip || frequencyHz <= 0f) return null
            return adrMeters * frequencyHz / SPEED_OF_LIGHT_METERS_PER_SECOND
        }

    /** 有效 ADR（米），仅在 ADR_STATE_VALID 且无周跳时返回。 */
    val effectiveAdrMeters: Double?
        get() =
            accumulatedDeltaRangeMeters?.takeIf { isAdrValid && !hasCycleSlip }

    /** ADR 不确定度（米），仅在 ADR 可用时返回。 */
    val effectiveAdrUncertaintyMeters: Double?
        get() =
            accumulatedDeltaRangeUncertaintyMeters?.takeIf { isAdrValid && !hasCycleSlip }

    private companion object {
        const val SPEED_OF_LIGHT_METERS_PER_SECOND = 299_792_458.0
    }
}

/** 信号强度等级，基于载噪比 CN0 (dB-Hz) 划分。STRONG >= 35, MEDIUM >= 25, WEAK < 25。 */

enum class SignalStrength {
    STRONG,
    MEDIUM,
    WEAK,
}
