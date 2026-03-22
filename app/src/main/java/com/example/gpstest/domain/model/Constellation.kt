package com.example.gpstest.domain.model

enum class Constellation {
    GPS,
    SBAS,
    GLONASS,
    GALILEO,
    BEIDOU,
    QZSS,
    UNKNOWN;

    companion object {
        fun fromConstellationType(type: Int): Constellation {
            return when (type) {
                1 -> GPS
                2 -> SBAS
                3 -> GLONASS
                4 -> QZSS
                5 -> BEIDOU
                6 -> GALILEO
                else -> UNKNOWN
            }
        }
    }
}
