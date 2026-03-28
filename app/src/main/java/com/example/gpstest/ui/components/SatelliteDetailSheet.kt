package com.example.gpstest.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.gpstest.R
import com.example.gpstest.domain.model.GnssSatellite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SatelliteDetailSheet(
    satellite: GnssSatellite,
    signalHistory: List<SignalReading> = emptyList(),
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(
                R.string.satellite_details,
                "${getConstellationName(satellite.constellation)}-${satellite.svid}"
            ),
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        DetailSection(stringResource(R.string.basic_info)) {
            DetailRow(
                stringResource(R.string.constellation_type),
                getConstellationFullName(satellite.constellation)
            )
            DetailRow(
                stringResource(R.string.satellite_id),
                "${satellite.svid}"
            )
            DetailRow(
                stringResource(R.string.signal_strength),
                "${satellite.cn0DbHz.toInt()} dB-Hz"
            )
            DetailRow(
                stringResource(R.string.azimuth),
                "${satellite.azimuthDegrees.toInt()}°"
            )
            DetailRow(
                stringResource(R.string.elevation),
                "${satellite.elevationDegrees.toInt()}°"
            )
            DetailRow(
                stringResource(R.string.status),
                if (satellite.usedInFix) stringResource(R.string.used_in_fix_yes)
                else stringResource(R.string.used_in_fix_no)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        DetailSection(stringResource(R.string.raw_measurement)) {
            DetailRow(
                stringResource(R.string.carrier_frequency),
                satellite.carrierFrequencyHz?.let { "%.2f MHz".format(it / 1_000_000) }
                    ?: "N/A"
            )
            DetailRow(
                stringResource(R.string.carrier_cycles),
                satellite.carrierCycles?.let { "%.2f".format(it) } ?: "N/A"
            )
            DetailRow(
                stringResource(R.string.doppler_shift),
                satellite.dopplerShiftHz?.let { "%.2f Hz".format(it) } ?: "N/A"
            )
            DetailRow(
                stringResource(R.string.timestamp),
                formatTimestamp(satellite.timeNanos)
            )
            DetailRow(
                stringResource(R.string.has_ephemeris),
                if (satellite.hasEphemeris) stringResource(R.string.yes) else stringResource(R.string.no)
            )
            DetailRow(
                stringResource(R.string.has_almanac),
                if (satellite.hasAlmanac) stringResource(R.string.yes) else stringResource(R.string.no)
            )
        }

        if (signalHistory.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            
            DetailSection(stringResource(R.string.signal_chart)) {
                SignalChart(
                    readings = signalHistory,
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (signalHistory.size > 1) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.signal_chart_subtitle, signalHistory.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun getConstellationName(constellation: com.example.gpstest.domain.model.Constellation): String {
    return when (constellation) {
        com.example.gpstest.domain.model.Constellation.GPS -> "GPS"
        com.example.gpstest.domain.model.Constellation.GLONASS -> "GLN"
        com.example.gpstest.domain.model.Constellation.GALILEO -> "GAL"
        com.example.gpstest.domain.model.Constellation.BEIDOU -> "BDS"
        com.example.gpstest.domain.model.Constellation.QZSS -> "QZS"
        com.example.gpstest.domain.model.Constellation.SBAS -> "SBAS"
        com.example.gpstest.domain.model.Constellation.UNKNOWN -> "UNK"
    }
}

@Composable
private fun getConstellationFullName(constellation: com.example.gpstest.domain.model.Constellation): String {
    return when (constellation) {
        com.example.gpstest.domain.model.Constellation.GPS -> stringResource(R.string.constellation_gps)
        com.example.gpstest.domain.model.Constellation.GLONASS -> stringResource(R.string.constellation_glonass)
        com.example.gpstest.domain.model.Constellation.GALILEO -> stringResource(R.string.constellation_galileo)
        com.example.gpstest.domain.model.Constellation.BEIDOU -> stringResource(R.string.constellation_beidou)
        com.example.gpstest.domain.model.Constellation.QZSS -> stringResource(R.string.constellation_qzss)
        com.example.gpstest.domain.model.Constellation.SBAS -> "SBAS"
        com.example.gpstest.domain.model.Constellation.UNKNOWN -> stringResource(R.string.constellation_unknown)
    }
}

private fun formatTimestamp(nanos: Long): String {
    val millis = nanos / 1_000_000
    val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    return sdf.format(Date(millis))
}
