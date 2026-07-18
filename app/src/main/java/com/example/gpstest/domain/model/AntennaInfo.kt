package com.example.gpstest.domain.model

/**
 * 单条 GNSS 天线相位中心快照（对应平台 GnssAntennaInfo 一条）。
 * 字段名稳定，供 G2 RINEX ANTENNA: DELTA / 频点元数据使用。
 */
data class AntennaInfo(
    val carrierFrequencyMHz: Double,
    val pcoXMm: Double,
    val pcoYMm: Double,
    val pcoZMm: Double,
    val pcoXUncertaintyMm: Double,
    val pcoYUncertaintyMm: Double,
    val pcoZUncertaintyMm: Double,
    val pcvSummary: PhaseCenterVariationSummary?,
)

/**
 * 相位中心变化（PCV）网格的轻量摘要。
 * 不保留完整 double[][]，避免 StateFlow 体积膨胀。
 */
data class PhaseCenterVariationSummary(
    val deltaPhiDeg: Double,
    val deltaThetaDeg: Double,
    val sampleCount: Int,
    val minCorrectionMm: Double,
    val maxCorrectionMm: Double,
)
