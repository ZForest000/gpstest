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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// dumpsys location 轮询间隔。该命令开销较大（数百 ms），过长则数据陈旧，过短则耗电
private const val DUMPSYS_POLL_INTERVAL_MS = 5000L

/**
 * Android GNSS 平台 API 的统一数据源实现。
 *
 * 将 4 个独立平台回调合并为单个 [Flow]：
 * 1. [GnssStatus.Callback] — 卫星列表（星座、CN0、方位角、仰角、星历/历书状态）
 * 2. [GnssMeasurementsEvent.Callback] — 原始测量值（多普勒、多路径、ADR、载波相位）
 * 3. [LocationListener] — 位置信息（经纬度、海拔、精度）
 * 4. [SensorEventListener] — 气压传感器（用于气压高度辅助）
 *
 * 合并策略：测量回调先触发，将每颗卫星的测量数据暂存到 [measurementMap]；
 * 状态回调随后触发时，通过"星座类型_SVID"键将测量数据与卫星合并。
 * 任一回调触发时都尝试通过 [trySend] 发射最新的 [GnssData]。
 */
class GnssDataSourceImpl(
    private val context: Context,
) : GnssDataSource {
    private val locationManager: LocationManager?
        get() = context.getSystemService(LocationManager::class.java)

    private val sensorManager: SensorManager?
        get() = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    override fun getGnssData(): Flow<GnssData> =
        callbackFlow {
            var currentSatellites: List<GnssSatellite> = emptyList()
            var currentLocation: LocationInfo? = null
            var currentPressure: Float? = null
            var currentBaroAltitude: Double? = null
            var currentClock: GnssClockData? = null
            var currentDumpsysData: DumpsysGnssData? = null

            // 由于 Android 将 GNSS 测量值和状态分开在两个回调中传递，
            // 此结构用于暂存测量数据，在状态回调通过"星座类型_SVID"键合并
            data class MeasurementExtras(
                val carrierCycles: Long?,
                val dopplerShiftHz: Double?,
                val agcLevelDb: Double?,
                val multipathIndicator: MultipathIndicator?,
                val accumulatedDeltaRangeMeters: Double?,
                val accumulatedDeltaRangeState: Int?,
                val accumulatedDeltaRangeUncertaintyMeters: Double?,
                val receivedSvTimeNanos: Long?,
                val receivedSvTimeUncertaintyNanos: Double?,
                val pseudorangeRateMetersPerSecond: Double?,
                val measurementState: Int?,
                val measurementCn0DbHz: Double?,
                val fullCarrierPhaseCycleCount: Long?,
            )
            var measurementMap = mutableMapOf<String, MeasurementExtras>()

            val speedOfLight = 299_792_458.0 // m/s

            val measurementCallback =
                object : GnssMeasurementsEvent.Callback() {
                    override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent) {
                        val newMap = mutableMapOf<String, MeasurementExtras>()
                        for (measurement in event.measurements) {
                            val key = "${measurement.constellationType}_${measurement.svid}"
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
                                MeasurementExtras(
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
                                    accumulatedDeltaRangeUncertaintyMeters =
                                        // ADR 无效时不确定性也无意义，一并置为 null
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
                                )
                        }
                        measurementMap = newMap

                        val clock = event.clock
                        currentClock =
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
                            )

                        if (currentSatellites.isNotEmpty()) {
                            currentSatellites =
                                currentSatellites.map { sat ->
                                    val key = "${toConstellationType(sat.constellation)}_${sat.svid}"
                                    val extras = measurementMap[key]
                                    if (extras != null) {
                                        sat.copy(
                                            carrierCycles = extras.carrierCycles ?: sat.carrierCycles,
                                            dopplerShiftHz = extras.dopplerShiftHz ?: sat.dopplerShiftHz,
                                            agcLevelDb = extras.agcLevelDb ?: sat.agcLevelDb,
                                            multipathIndicator = extras.multipathIndicator ?: sat.multipathIndicator,
                                            accumulatedDeltaRangeMeters =
                                                extras.accumulatedDeltaRangeMeters ?: sat.accumulatedDeltaRangeMeters,
                                            accumulatedDeltaRangeState = extras.accumulatedDeltaRangeState ?: sat.accumulatedDeltaRangeState,
                                            accumulatedDeltaRangeUncertaintyMeters =
                                                extras.accumulatedDeltaRangeUncertaintyMeters ?: sat.accumulatedDeltaRangeUncertaintyMeters,
                                            receivedSvTimeNanos = extras.receivedSvTimeNanos ?: sat.receivedSvTimeNanos,
                                            receivedSvTimeUncertaintyNanos =
                                                extras.receivedSvTimeUncertaintyNanos ?: sat.receivedSvTimeUncertaintyNanos,
                                            pseudorangeRateMetersPerSecond =
                                                extras.pseudorangeRateMetersPerSecond ?: sat.pseudorangeRateMetersPerSecond,
                                            measurementState = extras.measurementState ?: sat.measurementState,
                                            measurementCn0DbHz = extras.measurementCn0DbHz ?: sat.measurementCn0DbHz,
                                            fullCarrierPhaseCycleCount = extras.fullCarrierPhaseCycleCount ?: sat.fullCarrierPhaseCycleCount,
                                        )
                                    } else {
                                        sat
                                    }
                                }
                            trySend(GnssData(currentSatellites, currentLocation, currentClock, currentDumpsysData))
                        }
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

                                val key = "${status.getConstellationType(i)}_${status.getSvid(i)}"
                                val extras = measurementMap[key]

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
                                        carrierCycles = extras?.carrierCycles,
                                        dopplerShiftHz = extras?.dopplerShiftHz,
                                        timeNanos = System.nanoTime(),
                                        agcLevelDb = extras?.agcLevelDb,
                                        multipathIndicator = extras?.multipathIndicator,
                                        basebandCn0DbHz = basebandCn0,
                                        accumulatedDeltaRangeMeters = extras?.accumulatedDeltaRangeMeters,
                                        accumulatedDeltaRangeState = extras?.accumulatedDeltaRangeState,
                                        accumulatedDeltaRangeUncertaintyMeters = extras?.accumulatedDeltaRangeUncertaintyMeters,
                                        receivedSvTimeNanos = extras?.receivedSvTimeNanos,
                                        receivedSvTimeUncertaintyNanos = extras?.receivedSvTimeUncertaintyNanos,
                                        pseudorangeRateMetersPerSecond = extras?.pseudorangeRateMetersPerSecond,
                                        measurementState = extras?.measurementState,
                                        measurementCn0DbHz = extras?.measurementCn0DbHz,
                                        fullCarrierPhaseCycleCount = extras?.fullCarrierPhaseCycleCount,
                                    )

                                satellites.add(satellite)
                            } catch (e: Exception) {
                                // 部分设备上报格式异常的卫星条目（如 constellationType = -1），
                                // 单独跳过该条目而非让整个状态更新失败
                            }
                        }

                        currentSatellites = satellites
                        trySend(GnssData(currentSatellites, currentLocation, currentClock, currentDumpsysData))
                    }
                }

            val locationListener =
                object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        currentLocation =
                            LocationInfo(
                                latitude = location.latitude,
                                longitude = location.longitude,
                                altitude = if (location.hasAltitude()) location.altitude else 0.0,
                                accuracy = if (location.hasAccuracy()) location.accuracy else 0f,
                                speed = if (location.hasSpeed()) location.speed else 0f,
                                bearing = if (location.hasBearing()) location.bearing else 0f,
                                timestamp = location.time,
                                barometricAltitude = currentBaroAltitude,
                                pressure = currentPressure,
                            )
                        trySend(GnssData(currentSatellites, currentLocation, currentClock, currentDumpsysData))
                    }
                }

            // 气压计海拔使用标准大气压模型（SensorManager.PRESSURE_STANDARD_ATMOSPHERE）
            // 缺乏当地海平面气压参考，高度值存在系统性偏差但趋势信息仍然可用
            val pressureListener =
                object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent?) {
                        event?.let {
                            if (it.sensor.type == Sensor.TYPE_PRESSURE && it.values.isNotEmpty()) {
                                // 气压是位置（LocationInfo）的辅助字段，静默更新即可；
                                // 下次位置回调（1Hz）会自然带上最新值。气压计可达数十 Hz，
                                // 若由它驱动 trySend 会高频重发整条数据，拖垮 UI。
                                currentPressure = it.values[0]
                                currentBaroAltitude =
                                    SensorManager
                                        .getAltitude(
                                            SensorManager.PRESSURE_STANDARD_ATMOSPHERE,
                                            it.values[0],
                                        ).toDouble()
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
                close(e)
                awaitClose()
                return@callbackFlow
            } catch (e: Exception) {
                close(e)
                awaitClose()
                return@callbackFlow
            }

            // dumpsys location 是开销较大的 shell 命令（数百 ms），且需 Shizuku/root 权限。
            // 用独立协程每 5 秒轮询一次：无权限时 fetchDumpsysGnssData() 首步即返回 null，
            // 开销可忽略；有权限时在 IO 线程执行并刷新 currentDumpsysData，驱动 ClockInfoCard
            // 的 DumpsysDataSection 显示基带 C/N0、测量计数、定位星座列表。
            val dumpsysJob =
                launch {
                    while (isActive) {
                        val data = withContext(Dispatchers.IO) { ShizukuHelper.fetchDumpsysGnssData() }
                        if (data != null) {
                            currentDumpsysData = data
                            trySend(GnssData(currentSatellites, currentLocation, currentClock, currentDumpsysData))
                        }
                        delay(DUMPSYS_POLL_INTERVAL_MS)
                    }
                }

            awaitClose {
                // callbackFlow 要求协程取消时注销所有监听器以防止泄漏
                // 注销顺序不影响正确性，但必须全部移除
                dumpsysJob.cancel()
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

    // 将 [Constellation] 枚举映射为 Android [GnssStatus] 的整型常量
    // 1=GPS, 2=SBAS, 3=GLONASS, 4=QZSS, 5=北斗, 6=伽利略, 0=未知
    private fun toConstellationType(constellation: Constellation): Int =
        when (constellation) {
            Constellation.GPS -> 1
            Constellation.SBAS -> 2
            Constellation.GLONASS -> 3
            Constellation.QZSS -> 4
            Constellation.BEIDOU -> 5
            Constellation.GALILEO -> 6
            Constellation.IRNSS -> 7
            Constellation.UNKNOWN -> 0
        }
}
