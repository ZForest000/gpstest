package com.example.gpstest.ui.screens.skychart

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Exposes device azimuth relative to true north (0..360) only while [enabled]
 * and the host lifecycle is at least STARTED. Unregisters immediately otherwise.
 * If TYPE_ROTATION_VECTOR is missing, returns 0f with no error UI.
 */
@Composable
fun rememberCompassHeading(enabled: Boolean): Float {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var heading by remember { mutableFloatStateOf(0f) }

    DisposableEffect(enabled, lifecycleOwner) {
        if (!enabled) {
            heading = 0f
            onDispose { }
        } else {
            val sensorManager =
                context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            if (sensorManager == null || rotationSensor == null) {
                heading = 0f
                onDispose { }
            } else {
                var smoothed = heading
                val listener =
                    object : SensorEventListener {
                        private val rotationMatrix = FloatArray(9)
                        private val orientation = FloatArray(3)
                        private val remapped = FloatArray(9)

                        override fun onSensorChanged(event: SensorEvent?) {
                            if (event?.sensor?.type != Sensor.TYPE_ROTATION_VECTOR) return
                            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                            val display =
                                (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
                                    ?.defaultDisplay
                            val rotation = display?.rotation ?: Surface.ROTATION_0
                            val (axisX, axisY) =
                                when (rotation) {
                                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                                }
                            SensorManager.remapCoordinateSystem(
                                rotationMatrix,
                                axisX,
                                axisY,
                                remapped,
                            )
                            SensorManager.getOrientation(remapped, orientation)
                            // azimuth radians → degrees [0, 360)
                            val raw =
                                normalizeDegrees(
                                    Math.toDegrees(orientation[0].toDouble()).toFloat(),
                                )
                            // Shortest-arc low-pass ~120 ms at SENSOR_DELAY_UI (~60 Hz)
                            // alpha ≈ dt / (tau + dt); use fixed blend 0.15 ≈ 100–150 ms feel
                            smoothed = shortestArcBlend(smoothed, raw, 0.15f)
                            heading = smoothed
                        }

                        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                    }

                var registered = false
                fun register() {
                    if (!registered) {
                        sensorManager.registerListener(
                            listener,
                            rotationSensor,
                            SensorManager.SENSOR_DELAY_UI,
                        )
                        registered = true
                    }
                }

                fun unregister() {
                    if (registered) {
                        sensorManager.unregisterListener(listener)
                        registered = false
                    }
                }

                val observer =
                    LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> register()
                            Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> unregister()
                            else -> Unit
                        }
                    }
                lifecycleOwner.lifecycle.addObserver(observer)
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    register()
                }

                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    unregister()
                }
            }
        }
    }

    return if (enabled) heading else 0f
}

/** Blend toward [target] along shortest arc by [alpha] (0..1). */
internal fun shortestArcBlend(current: Float, target: Float, alpha: Float): Float {
    val from = normalizeDegrees(current)
    val to = normalizeDegrees(target)
    var delta = to - from
    if (delta > 180f) delta -= 360f
    if (delta < -180f) delta += 360f
    return normalizeDegrees(from + delta * alpha.coerceIn(0f, 1f))
}
