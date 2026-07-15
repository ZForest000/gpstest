package com.example.gpstest.domain.model

data class LocationInfo(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracy: Float,
    val speed: Float,
    val bearing: Float,
    val timestamp: Long,
    val barometricAltitude: Double? = null,
    val pressure: Float? = null,
    // API 26+ 垂直/航向/速度精度；设备未上报时为 null
    val verticalAccuracyMeters: Float? = null,
    val bearingAccuracyDegrees: Float? = null,
    val speedAccuracyMetersPerSecond: Float? = null,
)
