package com.example.gpstest.data.source

import com.example.gpstest.domain.model.AntennaInfo
import com.example.gpstest.domain.model.PhaseCenterVariationSummary

/**
 * 将平台天线字段映射为领域模型的纯函数集合（可 JVM 单测）。
 */
object AntennaInfoMapper {
    fun fromPrimitives(
        carrierFrequencyMHz: Double,
        pcoXMm: Double,
        pcoYMm: Double,
        pcoZMm: Double,
        pcoXUncertaintyMm: Double,
        pcoYUncertaintyMm: Double,
        pcoZUncertaintyMm: Double,
        pcvSummary: PhaseCenterVariationSummary?,
    ): AntennaInfo =
        AntennaInfo(
            carrierFrequencyMHz = carrierFrequencyMHz,
            pcoXMm = pcoXMm,
            pcoYMm = pcoYMm,
            pcoZMm = pcoZMm,
            pcoXUncertaintyMm = pcoXUncertaintyMm,
            pcoYUncertaintyMm = pcoYUncertaintyMm,
            pcoZUncertaintyMm = pcoZUncertaintyMm,
            pcvSummary = pcvSummary,
        )

    fun summarizePcv(
        corrections: Array<DoubleArray>?,
        deltaPhiDeg: Double,
        deltaThetaDeg: Double,
    ): PhaseCenterVariationSummary? {
        if (corrections == null || corrections.isEmpty()) return null
        var min = Double.POSITIVE_INFINITY
        var max = Double.NEGATIVE_INFINITY
        var count = 0
        for (row in corrections) {
            for (value in row) {
                if (value < min) min = value
                if (value > max) max = value
                count++
            }
        }
        if (count == 0) return null
        return PhaseCenterVariationSummary(
            deltaPhiDeg = deltaPhiDeg,
            deltaThetaDeg = deltaThetaDeg,
            sampleCount = count,
            minCorrectionMm = min,
            maxCorrectionMm = max,
        )
    }
}
