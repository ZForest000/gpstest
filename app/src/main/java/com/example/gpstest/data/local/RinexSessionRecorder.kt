package com.example.gpstest.data.local

import com.example.gpstest.domain.export.RinexEpoch
import com.example.gpstest.domain.export.RinexObservation
import com.example.gpstest.domain.model.GnssData
import com.example.gpstest.domain.model.PseudorangeStatus
import java.time.Instant

/** 将实时测量快照累积为可导出的 RINEX 观测历元。 */
class RinexSessionRecorder(
    private val maxEpochs: Int = MAX_EPOCHS,
) {
    private val recordedEpochs = ArrayDeque<RinexEpoch>(maxEpochs)

    val epochs: List<RinexEpoch>
        get() = recordedEpochs.toList()

    fun record(
        data: GnssData,
        timestamp: Instant = Instant.now(),
    ) {
        val observations =
            data.satellites.mapNotNull { satellite ->
                val pseudorange =
                    satellite.pseudorangeMeters.takeIf {
                        satellite.pseudorangeStatus == PseudorangeStatus.AVAILABLE
                    }
                val carrierPhase = satellite.effectiveCarrierPhaseCycles
                val doppler = satellite.dopplerShiftHz
                if (pseudorange == null && carrierPhase == null && doppler == null) {
                    null
                } else {
                    RinexObservation(
                        constellation = satellite.constellation,
                        svid = satellite.svid,
                        pseudorangeMeters = pseudorange,
                        carrierPhaseCycles = carrierPhase,
                        dopplerHz = doppler,
                    )
                }
            }
        if (observations.isEmpty()) return
        while (recordedEpochs.size >= maxEpochs) recordedEpochs.removeFirst()
        recordedEpochs.addLast(RinexEpoch(timestamp, observations))
    }

    fun clear() = recordedEpochs.clear()

    private companion object {
        const val MAX_EPOCHS = 7_200
    }
}
