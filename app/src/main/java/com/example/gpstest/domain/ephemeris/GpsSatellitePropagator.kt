package com.example.gpstest.domain.ephemeris

import com.example.gpstest.domain.model.EcefCoordinate
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** GPS LNAV 子帧 1–3 的广播星历参数（SI 单位）。 */
data class GpsBroadcastEphemeris(
    val svid: Int,
    val weekNumber: Int,
    val toeSeconds: Double,
    val tocSeconds: Double,
    val sqrtA: Double,
    val eccentricity: Double,
    val inclinationRadians: Double,
    val longitudeOfAscendingNodeRadians: Double,
    val argumentOfPerigeeRadians: Double,
    val meanAnomalyRadians: Double,
    val deltaN: Double,
    val inclinationRate: Double,
    val longitudeRate: Double,
    val cuc: Double,
    val cus: Double,
    val cic: Double,
    val cis: Double,
    val crc: Double,
    val crs: Double,
    val af0Seconds: Double,
    val af1SecondsPerSecond: Double,
    val af2SecondsPerSecondSquared: Double,
    val groupDelaySeconds: Double,
)

data class GpsSatelliteState(
    val position: EcefCoordinate,
    val clockBiasSeconds: Double,
)

/** 根据 IS-GPS-200 广播星历计算指定 GPS 周内时刻的 ECEF 和钟差。 */
object GpsSatellitePropagator {
    const val EARTH_ROTATION_RATE = 7.2921151467e-5
    private const val GRAVITATIONAL_CONSTANT = 3.986005e14
    private const val RELATIVISTIC_CONSTANT = -4.442807633e-10
    private const val HALF_WEEK_SECONDS = 302_400.0
    private const val KEPLER_ITERATIONS = 12

    fun propagate(
        ephemeris: GpsBroadcastEphemeris,
        gpsSecondsOfWeek: Double,
    ): GpsSatelliteState {
        val tk = normalizeWeek(gpsSecondsOfWeek - ephemeris.toeSeconds)
        val semiMajorAxis = ephemeris.sqrtA * ephemeris.sqrtA
        val meanMotion = sqrt(GRAVITATIONAL_CONSTANT / (semiMajorAxis * semiMajorAxis * semiMajorAxis)) + ephemeris.deltaN
        val meanAnomaly = ephemeris.meanAnomalyRadians + meanMotion * tk
        val eccentricAnomaly = solveEccentricAnomaly(meanAnomaly, ephemeris.eccentricity)
        val trueAnomaly = atan2(sqrt(1 - ephemeris.eccentricity * ephemeris.eccentricity) * sin(eccentricAnomaly), cos(eccentricAnomaly) - ephemeris.eccentricity)
        val argument = trueAnomaly + ephemeris.argumentOfPerigeeRadians
        val twiceArgument = 2 * argument
        val correctedArgument = argument + ephemeris.cus * sin(twiceArgument) + ephemeris.cuc * cos(twiceArgument)
        val radius = semiMajorAxis * (1 - ephemeris.eccentricity * cos(eccentricAnomaly)) + ephemeris.crs * sin(twiceArgument) + ephemeris.crc * cos(twiceArgument)
        val inclination = ephemeris.inclinationRadians + ephemeris.inclinationRate * tk + ephemeris.cis * sin(twiceArgument) + ephemeris.cic * cos(twiceArgument)
        val orbitalX = radius * cos(correctedArgument)
        val orbitalY = radius * sin(correctedArgument)
        val longitude = ephemeris.longitudeOfAscendingNodeRadians + (ephemeris.longitudeRate - EARTH_ROTATION_RATE) * tk - EARTH_ROTATION_RATE * ephemeris.toeSeconds

        val clockTime = normalizeWeek(gpsSecondsOfWeek - ephemeris.tocSeconds)
        val relativistic = RELATIVISTIC_CONSTANT * ephemeris.eccentricity * ephemeris.sqrtA * sin(eccentricAnomaly)
        val clockBias = ephemeris.af0Seconds + ephemeris.af1SecondsPerSecond * clockTime + ephemeris.af2SecondsPerSecondSquared * clockTime * clockTime + relativistic - ephemeris.groupDelaySeconds

        return GpsSatelliteState(
            position =
                EcefCoordinate(
                    xMeters = orbitalX * cos(longitude) - orbitalY * cos(inclination) * sin(longitude),
                    yMeters = orbitalX * sin(longitude) + orbitalY * cos(inclination) * cos(longitude),
                    zMeters = orbitalY * sin(inclination),
                ),
            clockBiasSeconds = clockBias,
        )
    }

    private fun solveEccentricAnomaly(
        meanAnomaly: Double,
        eccentricity: Double,
    ): Double {
        var eccentricAnomaly = meanAnomaly
        repeat(KEPLER_ITERATIONS) {
            eccentricAnomaly = meanAnomaly + eccentricity * sin(eccentricAnomaly)
        }
        return eccentricAnomaly
    }

    private fun normalizeWeek(seconds: Double): Double =
        when {
            seconds > HALF_WEEK_SECONDS -> seconds - 2 * HALF_WEEK_SECONDS
            seconds < -HALF_WEEK_SECONDS -> seconds + 2 * HALF_WEEK_SECONDS
            else -> seconds
        }
}
