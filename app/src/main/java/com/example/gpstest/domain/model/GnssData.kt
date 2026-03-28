package com.example.gpstest.domain.model

data class GnssData(
    val satellites: List<GnssSatellite>,
    val location: LocationInfo? = null
)
