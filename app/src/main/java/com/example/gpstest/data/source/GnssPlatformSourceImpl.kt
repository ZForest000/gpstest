package com.example.gpstest.data.source

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.GnssAntennaInfo
import android.location.GnssMeasurement
import android.location.GnssMeasurementsEvent
import android.location.GnssNavigationMessage
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.OnNmeaMessageListener
import android.os.Build
import com.example.gpstest.domain.model.AntennaInfo
import com.example.gpstest.domain.model.Constellation
import com.example.gpstest.domain.model.GnssCapabilitiesInfo
import com.example.gpstest.domain.model.GnssClockData
import com.example.gpstest.domain.model.GnssSatellite
import com.example.gpstest.domain.model.LocationInfo
import com.example.gpstest.domain.model.MultipathIndicator
import com.example.gpstest.domain.model.NavigationMessageFrame
import com.example.gpstest.domain.model.NmeaSentence
import com.example.gpstest.domain.model.PseudorangeMeasurement
import com.example.gpstest.domain.model.PseudorangeStatus
import com.example.gpstest.domain.util.PseudorangeCalculator
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

// dumpsys location 轮询间隔。该命令开销较大（数百 ms），过长则数据陈旧，过短则耗电
private const val DUMPSYS_POLL_INTERVAL_MS = 5000L

/** 每个平台注册的反注册必须独立执行，避免单个 OEM/Binder 异常阻断其余清理。 */
internal fun runGnssListenerCleanup(vararg actions: () -> Unit) {
    actions.forEach { action ->
        try {
            action()
        } catch (_: Exception) {
            // 某个 listener 已被系统移除时，继续清理其余 listener。
        }
    }
}

/**
 * Android GNSS 平台 API adapter。
 *
 * 将主采集的 4 个独立平台回调转换为 [GnssAcquisitionEvent]：
 * 1. [GnssStatus.Callback] — 卫星列表（星座、CN0、方位角、仰角、星历/历书状态）
 * 2. [GnssMeasurementsEvent.Callback] — 原始测量值（多普勒、多路径、ADR、载波相位）
 * 3. [LocationListener] — 位置信息（经纬度、海拔、精度）
 * 4. [SensorEventListener] — 气压传感器（用于气压高度辅助）
 *
 * 事件配对、freshness 与共享订阅语义由 [GnssAcquisitionSession] 持有；本类不保存
 * 跨 callback 的融合状态。
 */
@Suppress("DEPRECATION")
@OptIn(DelicateCoroutinesApi::class)
class GnssPlatformSourceImpl(
    private val context: Context,
) : GnssPlatformSource {
    private val locationManager: LocationManager?
        get() = context.getSystemService(LocationManager::class.java)

    private val sensorManager: SensorManager?
        get() = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    override fun getAcquisitionEvents(): Flow<GnssAcquisitionEvent> =
        callbackFlow {
            val speedOfLight = 299_792_458.0 // m/s

            val measurementCallback =
                object : GnssMeasurementsEvent.Callback() {
                    override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent) {
                        val clock = event.clock
                        val clockData =
                            GnssClockData(
                                timeNanos = clock.timeNanos,
                                biasNanos =
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                                        clock.hasBiasNanos()
                                    ) {
                                        clock.biasNanos
                                    } else {
                                        null
                                    },
                                fullBiasNanos = if (clock.hasFullBiasNanos()) clock.fullBiasNanos else null,
                                driftNanosPerSecond =
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                                        clock.hasDriftNanosPerSecond()
                                    ) {
                                        clock.driftNanosPerSecond
                                    } else {
                                        null
                                    },
                                biasUncertaintyNanos =
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                                        clock.hasBiasUncertaintyNanos()
                                    ) {
                                        clock.biasUncertaintyNanos
                                    } else {
                                        null
                                    },
                                driftUncertaintyNanosPerSecond =
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                                        clock.hasDriftUncertaintyNanosPerSecond()
                                    ) {
                                        clock.driftUncertaintyNanosPerSecond
                                    } else {
                                        null
                                    },
                                hardwareClockDiscontinuityCount = clock.hardwareClockDiscontinuityCount,
                                leapSecond = if (clock.hasLeapSecond()) clock.leapSecond else null,
                            )
                        val newMap = mutableMapOf<GnssSatelliteKey, GnssMeasurementExtras>()
                        for (measurement in event.measurements) {
                            val key = GnssSatelliteKey(measurement.constellationType, measurement.svid)
                            val carrierFreqHz =
                                if (measurement.hasCarrierFrequencyHz()) {
                                    measurement.carrierFrequencyHz.toDouble()
                                } else {
                                    null
                                }
                            // 多普勒频移 = -伪距率 × 载波频率 / 光速
                            // 负号：卫星接近时伪距率负值 → 多普勒正值（蓝移）
                            val dopplerShift =
                                if (carrierFreqHz != null) {
                                    -measurement.pseudorangeRateMetersPerSecond * carrierFreqHz / speedOfLight
                                } else {
                                    null
                                }
                            newMap[key] =
                                GnssMeasurementExtras(
                                    carrierCycles = if (measurement.hasCarrierCycles()) measurement.carrierCycles else null,
                                    dopplerShiftHz = dopplerShift,
                                    agcLevelDb =
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                                            measurement.hasAutomaticGainControlLevelDb()
                                        ) {
                                            measurement.automaticGainControlLevelDb
                                        } else {
                                            null
                                        },
                                    multipathIndicator = MultipathIndicator.fromInt(measurement.multipathIndicator),
                                    // 仅 ADR_STATE_VALID 置位时使用 ADR 值，否则可能含周跳导致的整数跳变
                                    accumulatedDeltaRangeMeters =
                                        if ((measurement.accumulatedDeltaRangeState and GnssMeasurement.ADR_STATE_VALID) != 0) {
                                            measurement.accumulatedDeltaRangeMeters
                                        } else {
                                            null
                                        },
                                    accumulatedDeltaRangeState = measurement.accumulatedDeltaRangeState,
                                    // ADR 无效时不确定性也无意义，一并置为 null
                                    accumulatedDeltaRangeUncertaintyMeters =
                                        if ((measurement.accumulatedDeltaRangeState and GnssMeasurement.ADR_STATE_VALID) != 0) {
                                            measurement.accumulatedDeltaRangeUncertaintyMeters
                                        } else {
                                            null
                                        },
                                    receivedSvTimeNanos = measurement.receivedSvTimeNanos,
                                    receivedSvTimeUncertaintyNanos = measurement.receivedSvTimeUncertaintyNanos.toDouble(),
                                    pseudorangeRateMetersPerSecond = measurement.pseudorangeRateMetersPerSecond,
                                    measurementState = measurement.state,
                                    measurementCn0DbHz = measurement.cn0DbHz,
                                    // 完整载波相位周期数 — RTK/PPP 整数模糊度解算的核心输入
                                    //
                                    // 理想来源是 API 34+ 的 getFullCarrierPhaseCycleCount()，但当前
                                    // android-35 stub 未暴露该方法（仅暴露已废弃的 getCarrierCycles()）。
                                    // 过渡策略：复用 carrierCycles 的值（语义一致，均为完整周期计数）。
                                    // 待项目升级到暴露新 API 的 SDK 后可直接切换数据源。
                                    fullCarrierPhaseCycleCount =
                                        if (measurement.hasCarrierCycles()) {
                                            measurement.carrierCycles
                                        } else {
                                            null
                                        },
                                    pseudorangeResult =
                                        PseudorangeCalculator.calculate(
                                            clockData,
                                            PseudorangeMeasurement(
                                                constellation = Constellation.fromConstellationType(measurement.constellationType),
                                                timeOffsetNanos = measurement.timeOffsetNanos,
                                                receivedSvTimeNanos = measurement.receivedSvTimeNanos,
                                                receivedSvTimeUncertaintyNanos = measurement.receivedSvTimeUncertaintyNanos.toDouble(),
                                                hasCodeLock =
                                                    (measurement.state and GnssMeasurement.STATE_CODE_LOCK) != 0,
                                                hasTowDecoded =
                                                    (measurement.state and GnssMeasurement.STATE_TOW_DECODED) != 0,
                                            ),
                                        ),
                                )
                        }
                        trySend(GnssAcquisitionEvent.Measurements(clockData, newMap))
                    }
                }

            val callback =
                object : GnssStatus.Callback() {
                    override fun onSatelliteStatusChanged(status: GnssStatus) {
                        val satellites = mutableListOf<GnssSatellite>()

                        for (i in 0 until status.satelliteCount) {
                            try {
                                val constellation =
                                    Constellation.fromConstellationType(
                                        status.getConstellationType(i),
                                    )

                                val basebandCn0 =
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                        if (status.hasBasebandCn0DbHz(i)) status.getBasebandCn0DbHz(i) else null
                                    } else {
                                        null
                                    }

                                val satellite =
                                    GnssSatellite(
                                        svid = status.getSvid(i),
                                        constellation = constellation,
                                        rawConstellationType = status.getConstellationType(i),
                                        cn0DbHz = status.getCn0DbHz(i),
                                        azimuthDegrees = status.getAzimuthDegrees(i),
                                        elevationDegrees = status.getElevationDegrees(i),
                                        hasAlmanac = status.hasAlmanacData(i),
                                        hasEphemeris = status.hasEphemerisData(i),
                                        usedInFix = status.usedInFix(i),
                                        carrierFrequencyHz =
                                            if (status.hasCarrierFrequencyHz(i)) {
                                                status.getCarrierFrequencyHz(i)
                                            } else {
                                                null
                                            },
                                        carrierCycles = null,
                                        dopplerShiftHz = null,
                                        timeNanos = System.nanoTime(),
                                        agcLevelDb = null,
                                        multipathIndicator = null,
                                        basebandCn0DbHz = basebandCn0,
                                        accumulatedDeltaRangeMeters = null,
                                        accumulatedDeltaRangeState = null,
                                        accumulatedDeltaRangeUncertaintyMeters = null,
                                        receivedSvTimeNanos = null,
                                        receivedSvTimeUncertaintyNanos = null,
                                        pseudorangeRateMetersPerSecond = null,
                                        measurementState = null,
                                        measurementCn0DbHz = null,
                                        fullCarrierPhaseCycleCount = null,
                                        pseudorangeMeters = null,
                                        pseudorangeUncertaintyMeters = null,
                                        pseudorangeStatus = PseudorangeStatus.MISSING_MEASUREMENT,
                                    )

                                satellites.add(satellite)
                            } catch (e: Exception) {
                                // 部分设备上报格式异常的卫星条目（如 constellationType = -1），
                                // 单独跳过该条目而非让整个状态更新失败
                            }
                        }

                        trySend(GnssAcquisitionEvent.SatelliteStatus(satellites))
                    }
                }

            val locationListener =
                object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        val locationInfo =
                            LocationInfo(
                                latitude = location.latitude,
                                longitude = location.longitude,
                                altitude = if (location.hasAltitude()) location.altitude else 0.0,
                                accuracy = if (location.hasAccuracy()) location.accuracy else 0f,
                                speed = if (location.hasSpeed()) location.speed else 0f,
                                bearing = if (location.hasBearing()) location.bearing else 0f,
                                timestamp = location.time,
                                verticalAccuracyMeters =
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                                        location.hasVerticalAccuracy()
                                    ) {
                                        location.verticalAccuracyMeters
                                    } else {
                                        null
                                    },
                                bearingAccuracyDegrees =
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                                        location.hasBearingAccuracy()
                                    ) {
                                        location.bearingAccuracyDegrees
                                    } else {
                                        null
                                    },
                                speedAccuracyMetersPerSecond =
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                                        location.hasSpeedAccuracy()
                                    ) {
                                        location.speedAccuracyMetersPerSecond
                                    } else {
                                        null
                                    },
                            )
                        trySend(GnssAcquisitionEvent.Location(locationInfo))
                    }
                }

            // 气压计海拔使用标准大气压模型（SensorManager.PRESSURE_STANDARD_ATMOSPHERE）
            // 缺乏当地海平面气压参考，高度值存在系统性偏差但趋势信息仍然可用
            val pressureListener =
                object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent?) {
                        event?.let {
                            if (it.sensor.type == Sensor.TYPE_PRESSURE && it.values.isNotEmpty()) {
                                val pressure = it.values[0]
                                val barometricAltitude =
                                    SensorManager
                                        .getAltitude(
                                            SensorManager.PRESSURE_STANDARD_ATMOSPHERE,
                                            pressure,
                                        ).toDouble()
                                trySend(GnssAcquisitionEvent.Pressure(pressure, barometricAltitude))
                            }
                        }
                    }

                    override fun onAccuracyChanged(
                        sensor: Sensor?,
                        accuracy: Int,
                    ) {}
                }

            val lm = locationManager
            if (lm == null) {
                close(IllegalStateException("LocationManager not available"))
                awaitClose()
                return@callbackFlow
            }

            val cleanupListeners: () -> Unit = {
                runGnssListenerCleanup(
                    { lm.unregisterGnssStatusCallback(callback) },
                    { lm.unregisterGnssMeasurementsCallback(measurementCallback) },
                    { lm.removeUpdates(locationListener) },
                    { sensorManager?.unregisterListener(pressureListener) },
                )
            }

            try {
                lm.registerGnssStatusCallback(context.mainExecutor, callback)
                lm.registerGnssMeasurementsCallback(context.mainExecutor, measurementCallback)
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    0f,
                    context.mainExecutor,
                    locationListener,
                )

                val pressureSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)
                if (pressureSensor != null) {
                    sensorManager?.registerListener(
                        pressureListener,
                        pressureSensor,
                        SensorManager.SENSOR_DELAY_UI,
                    )
                }
            } catch (e: SecurityException) {
                cleanupListeners()
                close(e)
                awaitClose()
                return@callbackFlow
            } catch (e: Exception) {
                cleanupListeners()
                close(e)
                awaitClose()
                return@callbackFlow
            }

            // dumpsys location 是开销较大的 shell 命令（数百 ms），且需 Shizuku/root 权限。
            // 用独立协程每 5 秒轮询一次：无权限时 fetchDumpsysGnssData() 首步即返回 null，
            // 开销可忽略；有权限时在 IO 线程执行并发出事件，驱动 ClockInfoCard
            // 的 DumpsysDataSection 显示基带 C/N0、测量计数、定位星座列表。
            val dumpsysJob =
                launch {
                    while (isActive) {
                        val data = withContext(Dispatchers.IO) { ShizukuHelper.fetchDumpsysGnssData() }
                        if (data != null) {
                            trySend(GnssAcquisitionEvent.Dumpsys(data))
                        }
                        delay(DUMPSYS_POLL_INTERVAL_MS)
                    }
                }

            awaitClose {
                // callbackFlow 要求协程取消时注销所有监听器以防止泄漏
                // 各反注册动作独立容错，确保其中一个 OEM/Binder 异常不会跳过后续清理。
                dumpsysJob.cancel()
                cleanupListeners()
            }
        }

    override fun getNmeaSentences(): Flow<NmeaSentence> =
        callbackFlow {
            val lm =
                locationManager ?: run {
                    close()
                    return@callbackFlow
                }

            val listener =
                OnNmeaMessageListener { message, timestamp ->
                    if (!isClosedForSend) {
                        trySend(NmeaSentence(timestampMs = timestamp, message = message))
                    }
                }

            // API 30 (R) 起新增 Executor 重载；旧重载在 R 上被标记 deprecated。
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    lm.addNmeaListener(context.mainExecutor, listener)
                } else {
                    @Suppress("DEPRECATION")
                    lm.addNmeaListener(listener)
                }
            } catch (e: SecurityException) {
                // 缺少位置权限或设备不支持 NMEA：直接关闭流
                close(e)
                return@callbackFlow
            }

            awaitClose {
                try {
                    lm.removeNmeaListener(listener)
                } catch (e: Exception) {
                    // Ignore cleanup errors
                }
            }
        }

    override fun getNavigationMessages(): Flow<NavigationMessageFrame> =
        callbackFlow {
            val lm =
                locationManager ?: run {
                    close()
                    return@callbackFlow
                }
            val callback =
                object : GnssNavigationMessage.Callback() {
                    override fun onGnssNavigationMessageReceived(event: GnssNavigationMessage) {
                        trySend(
                            NavigationMessageFrame(
                                // NavigationMessage 的 type 高字节编码星座（如 0x0101 = GPS L1 C/A）。
                                constellation = Constellation.fromConstellationType(event.type ushr 8),
                                svid = event.svid,
                                type = event.type,
                                status = event.status,
                                messageId = event.messageId,
                                submessageId = event.submessageId,
                                data = event.data.copyOf(),
                                timestampMs = System.currentTimeMillis(),
                            ),
                        )
                    }

                    override fun onStatusChanged(status: Int) {
                        if (status == GnssNavigationMessage.Callback.STATUS_NOT_SUPPORTED) {
                            close()
                        }
                    }
                }
            try {
                lm.registerGnssNavigationMessageCallback(context.mainExecutor, callback)
            } catch (e: SecurityException) {
                close(e)
                return@callbackFlow
            } catch (e: Exception) {
                close(e)
                return@callbackFlow
            }
            awaitClose {
                try {
                    lm.unregisterGnssNavigationMessageCallback(callback)
                } catch (_: Exception) {
                    // 清理失败不影响其他 GNSS 回调。
                }
            }
        }

    override fun getAntennaInfos(): Flow<List<AntennaInfo>> =
        callbackFlow {
            val lm = locationManager
            if (lm == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                trySend(emptyList())
                close()
                return@callbackFlow
            }

            val listener =
                GnssAntennaInfo.Listener { infos ->
                    if (!isClosedForSend) {
                        trySend(mapAntennaInfos(infos))
                    }
                }

            try {
                lm.gnssAntennaInfos?.let { initial ->
                    trySend(mapAntennaInfos(initial))
                }
                lm.registerAntennaInfoListener(context.mainExecutor, listener)
            } catch (e: SecurityException) {
                Timber.w(e, "Antenna info listener registration denied")
                trySend(emptyList())
                close(e)
                return@callbackFlow
            } catch (e: Exception) {
                Timber.w(e, "Antenna info listener registration failed")
                trySend(emptyList())
                close(e)
                return@callbackFlow
            }

            awaitClose {
                try {
                    lm.unregisterAntennaInfoListener(listener)
                } catch (_: Exception) {
                    // ignore cleanup
                }
            }
        }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun mapAntennaInfos(infos: List<GnssAntennaInfo>?): List<AntennaInfo> {
        if (infos.isNullOrEmpty()) return emptyList()
        return infos.map { platform ->
            val pco = platform.phaseCenterOffset
            val pcv = platform.phaseCenterVariationCorrections
            val summary =
                if (pcv != null) {
                    AntennaInfoMapper.summarizePcv(
                        corrections = pcv.correctionsArray,
                        deltaPhiDeg = pcv.deltaPhi,
                        deltaThetaDeg = pcv.deltaTheta,
                    )
                } else {
                    null
                }
            AntennaInfoMapper.fromPrimitives(
                carrierFrequencyMHz = platform.carrierFrequencyMHz.toDouble(),
                pcoXMm = pco.xOffsetMm,
                pcoYMm = pco.yOffsetMm,
                pcoZMm = pco.zOffsetMm,
                pcoXUncertaintyMm = pco.xOffsetUncertaintyMm,
                pcoYUncertaintyMm = pco.yOffsetUncertaintyMm,
                pcoZUncertaintyMm = pco.zOffsetUncertaintyMm,
                pcvSummary = summary,
            )
        }
    }

    override fun isSupported(): Boolean {
        val lm = locationManager ?: return false
        return lm.allProviders.contains("gps")
    }

    override fun getGnssCapabilities(): GnssCapabilitiesInfo? {
        val lm = locationManager ?: return null
        return try {
            val hardwareModelName =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    lm.gnssHardwareModelName
                } else {
                    null
                }
            val yearOfHardware =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    lm.gnssYearOfHardware.toString()
                } else {
                    null
                }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                return if (hardwareModelName != null || yearOfHardware != null) {
                    GnssCapabilitiesInfo(
                        hardwareModelName = hardwareModelName,
                        yearOfHardware = yearOfHardware,
                        hasMeasurements = null,
                        hasNavigationMessages = null,
                        hasAntennaInfo = null,
                        hasAccumulatedDeltaRange = null,
                        hasMeasurementCorrections = null,
                        hasMeasurementCorrelationVectors = null,
                    )
                } else {
                    null
                }
            }

            val cap = lm.gnssCapabilities
            val hasAccumulatedDeltaRange =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    cap.hasAccumulatedDeltaRange()
                } else {
                    null
                }
            val hasMeasurementCorrections =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    cap.hasMeasurementCorrections()
                } else {
                    null
                }
            val hasMeasurementCorrelationVectors =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    cap.hasMeasurementCorrelationVectors()
                } else {
                    null
                }

            GnssCapabilitiesInfo(
                hardwareModelName = hardwareModelName,
                yearOfHardware = yearOfHardware,
                // API 31 能力方法返回 boolean，转为领域层 1/0 编码
                hasMeasurements = cap.hasMeasurements().toCapabilityResult(),
                hasNavigationMessages = cap.hasNavigationMessages().toCapabilityResult(),
                hasAntennaInfo = cap.hasAntennaInfo().toCapabilityResult(),
                // API 34 方法返回类型不一致：ADR 返回 int；修正/相关向量返回 boolean
                hasAccumulatedDeltaRange = hasAccumulatedDeltaRange,
                hasMeasurementCorrections = hasMeasurementCorrections?.let { if (it) 1 else 0 },
                hasMeasurementCorrelationVectors = hasMeasurementCorrelationVectors?.let { if (it) 1 else 0 },
            )
        } catch (e: Exception) {
            // 部分 OEM 在调用 gnssCapabilities 时可能抛出异常（如 binder 调用失败），
            // 静默降级，不影响主数据流。
            null
        }
    }

    // Android GnssCapabilities 布尔返回值映射为领域层 Int 编码：true=1, false=0
    private fun Boolean.toCapabilityResult(): Int = if (this) 1 else 0
}
