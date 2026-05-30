package com.example.gpstest.ui.components

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.example.gpstest.R
import com.example.gpstest.domain.model.Constellation
import com.example.gpstest.ui.theme.BeidouColor
import com.example.gpstest.ui.theme.GalileoColor
import com.example.gpstest.ui.theme.GlonassColor
import com.example.gpstest.ui.theme.GpsColor
import com.example.gpstest.ui.theme.QzssColor
import com.example.gpstest.ui.theme.SbasColor
import com.example.gpstest.ui.theme.IrnssColor
import com.example.gpstest.ui.theme.UnknownConstellationColor

val Constellation.color: Color
    get() = when (this) {
        Constellation.GPS -> GpsColor
        Constellation.BEIDOU -> BeidouColor
        Constellation.GLONASS -> GlonassColor
        Constellation.GALILEO -> GalileoColor
        Constellation.QZSS -> QzssColor
        Constellation.SBAS -> SbasColor
        Constellation.IRNSS -> IrnssColor
        Constellation.UNKNOWN -> UnknownConstellationColor
    }

@get:StringRes
val Constellation.fullNameResId: Int
    get() = when (this) {
        Constellation.GPS -> R.string.constellation_gps
        Constellation.GLONASS -> R.string.constellation_glonass
        Constellation.GALILEO -> R.string.constellation_galileo
        Constellation.BEIDOU -> R.string.constellation_beidou
        Constellation.QZSS -> R.string.constellation_qzss
        Constellation.SBAS -> R.string.constellation_sbas
        Constellation.IRNSS -> R.string.constellation_irnss
        Constellation.UNKNOWN -> R.string.constellation_unknown
    }

fun com.example.gpstest.domain.model.GnssSatellite.getDisplayName(): String {
    return if (this.constellation == Constellation.UNKNOWN && this.rawConstellationType != -1) {
        "UNK(${this.rawConstellationType})"
    } else {
        this.constellation.shortName
    }
}

fun com.example.gpstest.domain.model.SatelliteHistoryEntry.getDisplayName(): String {
    return if (this.constellationName == Constellation.UNKNOWN.name && this.rawConstellationType != null && this.rawConstellationType != -1) {
        "UNK(${this.rawConstellationType})"
    } else {
        val constell = runCatching { Constellation.valueOf(this.constellationName) }.getOrNull()
        constell?.shortName ?: this.constellationName
    }
}
