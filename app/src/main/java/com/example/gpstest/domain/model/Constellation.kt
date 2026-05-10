package com.example.gpstest.domain.model

enum class Constellation(
    val shortName: String,
    val constellationType: Int,
) {
    GPS(shortName = "GPS", constellationType = 1),
    SBAS(shortName = "SBAS", constellationType = 2),
    GLONASS(shortName = "GLO", constellationType = 3),
    GALILEO(shortName = "GAL", constellationType = 6),
    BEIDOU(shortName = "BDS", constellationType = 5),
    QZSS(shortName = "QZS", constellationType = 4),
    UNKNOWN(shortName = "UNK", constellationType = -1),
    ;

    companion object {
        fun fromConstellationType(type: Int): Constellation =
            entries.find { it.constellationType == type } ?: UNKNOWN
    }
}
