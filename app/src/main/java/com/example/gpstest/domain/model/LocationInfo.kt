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
    val pressure: Float? = null
)
