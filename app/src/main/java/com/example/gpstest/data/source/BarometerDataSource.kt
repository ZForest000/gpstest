package com.example.gpstest.data.source

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

interface BarometerDataSource {
    fun getPressure(): Flow<Float?>
    fun getAltitude(seaLevelPressure: Float = SensorManager.PRESSURE_STANDARD_ATMOSPHERE): Flow<Double?>
    fun hasBarometer(): Boolean
}

class BarometerDataSourceImpl(
    private val context: Context
) : BarometerDataSource {

    private val sensorManager: SensorManager?
        get() = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    override fun hasBarometer(): Boolean {
        return sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE) != null
    }

    override fun getPressure(): Flow<Float?> = callbackFlow {
        val sensorManager = sensorManager
        val pressureSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)
        
        if (pressureSensor == null) {
            trySend(null)
            awaitClose()
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    if (it.sensor.type == Sensor.TYPE_PRESSURE && it.values.isNotEmpty()) {
                        trySend(it.values[0])
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        val registered = sensorManager.registerListener(
            listener,
            pressureSensor,
            SensorManager.SENSOR_DELAY_UI
        )

        if (!registered) {
            trySend(null)
            awaitClose()
            return@callbackFlow
        }

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }

    override fun getAltitude(seaLevelPressure: Float): Flow<Double?> = callbackFlow {
        val sensorManager = sensorManager
        val pressureSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)
        
        if (pressureSensor == null) {
            trySend(null)
            awaitClose()
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    if (it.sensor.type == Sensor.TYPE_PRESSURE && it.values.isNotEmpty()) {
                        val pressure = it.values[0]
                        val altitude = SensorManager.getAltitude(seaLevelPressure, pressure).toDouble()
                        trySend(altitude)
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        val registered = sensorManager.registerListener(
            listener,
            pressureSensor,
            SensorManager.SENSOR_DELAY_UI
        )

        if (!registered) {
            trySend(null)
            awaitClose()
            return@callbackFlow
        }

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }
}
