package com.example.gpstest.data.source

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.GnssMeasurement
import android.location.GnssMeasurementsEvent
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import com.example.gpstest.domain.model.Constellation
import com.example.gpstest.domain.model.GnssClockData
import com.example.gpstest.domain.model.GnssData
import com.example.gpstest.domain.model.GnssSatellite
import com.example.gpstest.domain.model.LocationInfo
import com.example.gpstest.domain.model.MultipathIndicator
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class GnssDataSourceImpl(
    private val context: Context
) : GnssDataSource {

    private val locationManager: LocationManager?
        get() = context.getSystemService(LocationManager::class.java)
    
    private val sensorManager: SensorManager?
        get() = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    override fun getGnssData(): Flow<GnssData> = callbackFlow {
        var currentSatellites: List<GnssSatellite> = emptyList()
        var currentLocation: LocationInfo? = null
        var currentPressure: Float? = null
        var currentBaroAltitude: Double? = null
        var currentClock: GnssClockData? = null
        var currentDumpsysData: DumpsysGnssData? = null

        data class MeasurementExtras(
            val carrierCycles: Long?,
            val dopplerShiftHz: Double?,
            val agcLevelDb: Double?,
            val multipathIndicator: MultipathIndicator?
        )
        var measurementMap = mutableMapOf<String, MeasurementExtras>()

        val speedOfLight = 299_792_458.0 // m/s

        val measurementCallback = object : GnssMeasurementsEvent.Callback() {
            override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent) {
                val newMap = mutableMapOf<String, MeasurementExtras>()
                for (measurement in event.measurements) {
                    val key = "${measurement.constellationType}_${measurement.svid}"
                    val carrierFreqHz = if (measurement.hasCarrierFrequencyHz()) {
                        measurement.carrierFrequencyHz.toDouble()
                    } else null
                    val dopplerShift = if (carrierFreqHz != null) {
                        -measurement.pseudorangeRateMetersPerSecond * carrierFreqHz / speedOfLight
                    } else null
                    newMap[key] = MeasurementExtras(
                        carrierCycles = if (measurement.hasCarrierCycles()) measurement.carrierCycles else null,
                        dopplerShiftHz = dopplerShift,
                        agcLevelDb = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && measurement.hasAutomaticGainControlLevelDb()) measurement.automaticGainControlLevelDb else null,
                        multipathIndicator = MultipathIndicator.fromInt(measurement.multipathIndicator)
                    )
                }
                measurementMap = newMap

                val clock = event.clock
                currentClock = GnssClockData(
                    timeNanos = clock.timeNanos,
                    biasNanos = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && clock.hasBiasNanos()) clock.biasNanos else null,
                    fullBiasNanos = if (clock.hasFullBiasNanos()) clock.fullBiasNanos else null,
                    driftNanosPerSecond = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && clock.hasDriftNanosPerSecond()) clock.driftNanosPerSecond else null,
                    biasUncertaintyNanos = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && clock.hasBiasUncertaintyNanos()) clock.biasUncertaintyNanos else null,
                    driftUncertaintyNanosPerSecond = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && clock.hasDriftUncertaintyNanosPerSecond()) clock.driftUncertaintyNanosPerSecond else null,
                    hardwareClockDiscontinuityCount = clock.hardwareClockDiscontinuityCount
                )

                if (currentSatellites.isNotEmpty()) {
                    currentSatellites = currentSatellites.map { sat ->
                        val key = "${toConstellationType(sat.constellation)}_${sat.svid}"
                        val extras = measurementMap[key]
                        if (extras != null) {
                            sat.copy(
                                carrierCycles = extras.carrierCycles ?: sat.carrierCycles,
                                dopplerShiftHz = extras.dopplerShiftHz ?: sat.dopplerShiftHz,
                                agcLevelDb = extras.agcLevelDb ?: sat.agcLevelDb,
                                multipathIndicator = extras.multipathIndicator ?: sat.multipathIndicator
                            )
                        } else sat
                    }
                    trySend(GnssData(currentSatellites, currentLocation, currentClock, currentDumpsysData))
                }
            }
        }

        val callback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                val satellites = mutableListOf<GnssSatellite>()

                for (i in 0 until status.satelliteCount) {
                    try {
                        val constellation = Constellation.fromConstellationType(
                            status.getConstellationType(i)
                        )

                        val key = "${status.getConstellationType(i)}_${status.getSvid(i)}"
                        val extras = measurementMap[key]

                        val basebandCn0 = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            if (status.hasBasebandCn0DbHz(i)) status.getBasebandCn0DbHz(i) else null
                        } else null

                        val satellite = GnssSatellite(
                            svid = status.getSvid(i),
                            constellation = constellation,
                            cn0DbHz = status.getCn0DbHz(i),
                            azimuthDegrees = status.getAzimuthDegrees(i),
                            elevationDegrees = status.getElevationDegrees(i),
                            hasAlmanac = status.hasAlmanacData(i),
                            hasEphemeris = status.hasEphemerisData(i),
                            usedInFix = status.usedInFix(i),
                            carrierFrequencyHz = if (status.hasCarrierFrequencyHz(i)) {
                                status.getCarrierFrequencyHz(i)
                            } else {
                                null
                            },
                            carrierCycles = extras?.carrierCycles,
                            dopplerShiftHz = extras?.dopplerShiftHz,
                            timeNanos = System.nanoTime(),
                            agcLevelDb = extras?.agcLevelDb,
                            multipathIndicator = extras?.multipathIndicator,
                            basebandCn0DbHz = basebandCn0
                        )

                        satellites.add(satellite)
                    } catch (e: Exception) {
                        // Skip invalid satellite
                    }
                }

                currentSatellites = satellites
                trySend(GnssData(currentSatellites, currentLocation, currentClock, currentDumpsysData))
            }
        }
        
        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                currentLocation = LocationInfo(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    altitude = if (location.hasAltitude()) location.altitude else 0.0,
                    accuracy = if (location.hasAccuracy()) location.accuracy else 0f,
                    speed = if (location.hasSpeed()) location.speed else 0f,
                    bearing = if (location.hasBearing()) location.bearing else 0f,
                    timestamp = location.time,
                    barometricAltitude = currentBaroAltitude,
                    pressure = currentPressure
                )
                trySend(GnssData(currentSatellites, currentLocation, currentClock, currentDumpsysData))
            }
        }
        
        val pressureListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    if (it.sensor.type == Sensor.TYPE_PRESSURE && it.values.isNotEmpty()) {
                        currentPressure = it.values[0]
                        currentBaroAltitude = SensorManager.getAltitude(
                            SensorManager.PRESSURE_STANDARD_ATMOSPHERE,
                            it.values[0]
                        ).toDouble()
                        
                        currentLocation?.let { loc ->
                            currentLocation = loc.copy(
                                barometricAltitude = currentBaroAltitude,
                                pressure = currentPressure
                            )
                            trySend(GnssData(currentSatellites, currentLocation, currentClock, currentDumpsysData))
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        val lm = locationManager
        if (lm == null) {
            close(IllegalStateException("LocationManager not available"))
            awaitClose()
            return@callbackFlow
        }

        try {
            lm.registerGnssStatusCallback(context.mainExecutor, callback)
            lm.registerGnssMeasurementsCallback(context.mainExecutor, measurementCallback)
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                0f,
                context.mainExecutor,
                locationListener
            )
            
            val pressureSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)
            if (pressureSensor != null) {
                sensorManager?.registerListener(
                    pressureListener,
                    pressureSensor,
                    SensorManager.SENSOR_DELAY_UI
                )
            }
        } catch (e: SecurityException) {
            close(e)
            awaitClose()
            return@callbackFlow
        } catch (e: Exception) {
            close(e)
            awaitClose()
            return@callbackFlow
        }

        awaitClose {
            try {
                locationManager?.unregisterGnssStatusCallback(callback)
                locationManager?.unregisterGnssMeasurementsCallback(measurementCallback)
                locationManager?.removeUpdates(locationListener)
                sensorManager?.unregisterListener(pressureListener)
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
    }

    override fun isSupported(): Boolean {
        val lm = locationManager ?: return false
        return lm.allProviders.contains("gps")
    }

    private fun toConstellationType(constellation: Constellation): Int {
        return when (constellation) {
            Constellation.GPS -> 1
            Constellation.SBAS -> 2
            Constellation.GLONASS -> 3
            Constellation.QZSS -> 4
            Constellation.BEIDOU -> 5
            Constellation.GALILEO -> 6
            Constellation.UNKNOWN -> 0
        }
    }
}
