package com.example.gpstest.domain.util

import com.example.gpstest.domain.model.Constellation
import com.example.gpstest.domain.model.GnssSatellite
import com.example.gpstest.domain.model.SatelliteSortMode

data class SatelliteListQuery(
    val constellations: Set<Constellation>? = null,
    val svidQuery: String = "",
    val sortMode: SatelliteSortMode = SatelliteSortMode.CN0_DESC,
) {
    fun applyTo(satellites: List<GnssSatellite>): List<GnssSatellite> {
        val constellationFilter = constellations
        val query = svidQuery.trim()
        val filtered =
            satellites.filter { sat ->
                val constellationOk =
                    constellationFilter.isNullOrEmpty() || sat.constellation in constellationFilter
                val svidOk = query.isEmpty() || sat.svid.toString().contains(query)
                constellationOk && svidOk
            }
        return when (sortMode) {
            SatelliteSortMode.CN0_DESC ->
                filtered.sortedWith(
                    compareByDescending<GnssSatellite> { it.cn0DbHz }
                        .thenBy { it.constellation.name }
                        .thenBy { it.svid },
                )
            SatelliteSortMode.ELEVATION_DESC ->
                filtered.sortedWith(
                    compareByDescending<GnssSatellite> { it.elevationDegrees }
                        .thenBy { it.constellation.name }
                        .thenBy { it.svid },
                )
            SatelliteSortMode.SVID_ASC ->
                filtered.sortedWith(
                    compareBy<GnssSatellite> { it.svid }
                        .thenBy { it.constellation.name },
                )
        }
    }
}
