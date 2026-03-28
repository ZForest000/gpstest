package com.example.gpstest.domain.model

import com.example.gpstest.data.source.DumpsysGnssData

data class GnssData(
    val satellites: List<GnssSatellite>,
    val location: LocationInfo? = null,
    val clock: GnssClockData? = null,
    val dumpsysData: DumpsysGnssData? = null
) {
    val avgBasebandCn0DbHz: Float
        get() {
            val validBaseband = satellites.mapNotNull { it.basebandCn0DbHz }.filter { it > 0 }
            return if (validBaseband.isNotEmpty()) {
                validBaseband.average().toFloat()
            } else 0f
        }

    val avgCn0DbHz: Float
        get() {
            val validCn0 = satellites.map { it.cn0DbHz }.filter { it > 0 }
            return if (validCn0.isNotEmpty()) {
                validCn0.average().toFloat()
            } else 0f
        }
}
