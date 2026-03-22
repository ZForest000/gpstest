package com.example.gpstest.data.source

import android.content.Context
import android.location.GnssMeasurementsEvent
import android.location.LocationManager
import com.example.gpstest.domain.model.Constellation
import com.example.gpstest.domain.model.GnssSatellite
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class GnssDataSourceImpl(
    private val context: Context
) : GnssDataSource {

    private val locationManager: LocationManager?
        get() = context.getSystemService(LocationManager::class.java)

    override fun startListening(): Flow<List<GnssSatellite>> = callbackFlow {
        val callback = object : GnssMeasurementsEvent.Callback() {
            override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent) {
                val satellites = event.measurements.map { measurement ->
                    GnssSatellite(
                        svid = measurement.svid,
                        constellation = Constellation.fromConstellationType(
                            measurement.constellationType
                        ),
                        cn0DbHz = measurement.cn0DbHz,
                        azimuthDegrees = measurement.azimuthDegrees,
                        elevationDegrees = measurement.elevationDegrees,
                        hasAlmanac = measurement.hasAlmanac(),
                        hasEphemeris = measurement.hasEphemeris(),
                        usedInFix = measurement.usedInFix(),
                        carrierFrequencyHz = measurement.carrierFrequencyHz,
                        carrierCycles = measurement.carrierCycles,
                        dopplerShiftHz = measurement.dopplerShiftHz,
                        timeNanos = measurement.timeNanos
                    )
                }
                trySend(satellites)
            }

            override fun onStatusChanged(status: Int) {
                // Status can be: STATUS_NOT_SUPPORTED (0), STATUS_READY (1), STATUS_LOCATION_DISABLED (2)
                if (status == GnssMeasurementsEvent.Callback.STATUS_NOT_SUPPORTED) {
                    close()
                }
            }
        }

        val registered = locationManager?.registerGnssMeasurementsCallback(
            callback,
            context.mainExecutor
        ) ?: false

        if (!registered) {
            close()
            awaitClose()
        }

        awaitClose {
            locationManager?.unregisterGnssMeasurementsCallback(callback)
        }
    }

    override fun stopListening() {
        // Flow is automatically stopped when collector is cancelled
    }

    override fun isSupported(): Boolean {
        return locationManager?.getGnssHardwareCapabilities()?.let { true } ?: false
    }
}
