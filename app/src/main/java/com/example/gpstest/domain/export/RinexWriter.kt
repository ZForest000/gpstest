package com.example.gpstest.domain.export

import com.example.gpstest.domain.model.Constellation
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

data class RinexHeader(
    val markerName: String = "GPS Test",
    val approximatePositionXyz: DoubleArray? = null,
    val antennaDeltaHen: DoubleArray? = null,
)

data class RinexEpoch(
    val timestamp: Instant,
    val observations: List<RinexObservation>,
)

data class RinexObservation(
    val constellation: Constellation,
    val svid: Int,
    val pseudorangeMeters: Double?,
    val carrierPhaseCycles: Double?,
    val dopplerHz: Double?,
)

/** 最小 RINEX 3.04 observation writer，所有格式字段严格固定在 80 列记录内。 */
object RinexWriter {
    fun write(
        header: RinexHeader,
        epochs: List<RinexEpoch>,
    ): String =
        buildString {
            appendLine(record("     3.04           OBSERVATION DATA    M", "RINEX VERSION / TYPE"))
            appendLine(record("GpsTest              GPS Test            ", "PGM / RUN BY / DATE"))
            appendLine(record(header.markerName, "MARKER NAME"))
            header.approximatePositionXyz?.let { xyz ->
                appendLine(record(String.format(Locale.US, "%14.4f%14.4f%14.4f", xyz[0], xyz[1], xyz[2]), "APPROX POSITION XYZ"))
            }
            header.antennaDeltaHen?.let { hen ->
                appendLine(record(String.format(Locale.US, "%14.4f%14.4f%14.4f", hen[0], hen[1], hen[2]), "ANTENNA: DELTA H/E/N"))
            }
            epochs.flatMap { it.observations }.map { it.constellation }.distinct().sortedBy { it.constellationType }.forEach { constellation ->
                appendLine(record("${rinexSystem(constellation)}    3 C1C L1C D1C", "SYS / # / OBS TYPES"))
            }
            epochs.minByOrNull { it.timestamp }?.let { epoch ->
                appendLine(record(formatTime(epoch.timestamp), "TIME OF FIRST OBS"))
            }
            appendLine(record("", "END OF HEADER"))
            epochs.sortedBy { it.timestamp }.forEach { epoch ->
                appendLine(formatEpoch(epoch))
                epoch.observations.sortedWith(compareBy<RinexObservation> { it.constellation.constellationType }.thenBy { it.svid }).forEach { observation ->
                    append(rinexSystem(observation.constellation))
                    append(String.format(Locale.US, "%02d", observation.svid))
                    append(observationField(observation.pseudorangeMeters))
                    append(observationField(observation.carrierPhaseCycles))
                    append(observationField(observation.dopplerHz))
                    appendLine()
                }
            }
        }

    private fun record(
        content: String,
        label: String,
    ): String = content.take(60).padEnd(60) + label

    private fun formatTime(timestamp: Instant): String {
        val time = timestamp.atOffset(ZoneOffset.UTC)
        return String.format(Locale.US, "%6d%6d%6d%6d%6d%13.7f     GPS", time.year, time.monthValue, time.dayOfMonth, time.hour, time.minute, time.second + time.nano / 1e9)
    }

    private fun formatEpoch(epoch: RinexEpoch): String {
        val time = epoch.timestamp.atOffset(ZoneOffset.UTC)
        return String.format(Locale.US, "> %04d %02d %02d %02d %02d %10.7f  0 %2d", time.year, time.monthValue, time.dayOfMonth, time.hour, time.minute, time.second + time.nano / 1e9, epoch.observations.size)
    }

    private fun observationField(value: Double?): String = value?.let { String.format(Locale.US, "%14.3f  ", it) } ?: "                "

    private fun rinexSystem(constellation: Constellation): String =
        when (constellation) {
            Constellation.GPS -> "G"
            Constellation.GLONASS -> "R"
            Constellation.GALILEO -> "E"
            Constellation.BEIDOU -> "C"
            Constellation.QZSS -> "J"
            Constellation.SBAS -> "S"
            Constellation.IRNSS -> "I"
            Constellation.UNKNOWN -> "X"
        }
}
