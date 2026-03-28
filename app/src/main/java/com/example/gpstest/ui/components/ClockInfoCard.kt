package com.example.gpstest.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.gpstest.R
import com.example.gpstest.data.source.DumpsysGnssData
import com.example.gpstest.data.source.ShizukuHelper
import com.example.gpstest.domain.model.GnssClockData
import com.example.gpstest.domain.model.GnssData

@Composable
fun ClockInfoCard(
    gnssData: GnssData,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.clock_info),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            gnssData.clock?.let { clock ->
                ClockDataSection(clock)
            } ?: run {
                Text(
                    text = "N/A",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            AvgBasebandCn0Section(gnssData)

            gnssData.dumpsysData?.let { dumpsysData ->
                Spacer(modifier = Modifier.height(12.dp))
                DumpsysDataSection(dumpsysData)
            }

            Spacer(modifier = Modifier.height(12.dp))

            ShizukuStatusSection()
        }
    }
}

@Composable
private fun ClockDataSection(clock: GnssClockData) {
    Column {
        clock.totalBiasMicroseconds?.let { bias ->
            DetailRow(
                stringResource(R.string.clock_bias),
                "%.3f μs".format(bias)
            )
        }
        clock.driftMicrosecondsPerSecond?.let { drift ->
            DetailRow(
                stringResource(R.string.clock_drift),
                "%.6f μs/s".format(drift)
            )
        }
        clock.biasUncertaintyNanos?.let { uncertainty ->
            DetailRow(
                stringResource(R.string.clock_bias_uncertainty),
                "%.1f ns".format(uncertainty)
            )
        }
        clock.driftUncertaintyNanosPerSecond?.let { uncertainty ->
            DetailRow(
                stringResource(R.string.clock_drift_uncertainty),
                "%.3f ns/s".format(uncertainty)
            )
        }
    }
}

@Composable
private fun AvgBasebandCn0Section(gnssData: GnssData) {
    val avgBaseband = gnssData.avgBasebandCn0DbHz
    val avgCn0 = gnssData.avgCn0DbHz

    Column {
        if (avgBaseband > 0) {
            DetailRow(
                stringResource(R.string.avg_baseband_cn0),
                "%.1f dB-Hz".format(avgBaseband)
            )
        }
        if (avgCn0 > 0) {
            DetailRow(
                stringResource(R.string.signal_strength),
                "%.1f dB-Hz".format(avgCn0)
            )
        }
    }
}

@Composable
private fun DumpsysDataSection(dumpsysData: DumpsysGnssData) {
    Column {
        dumpsysData.avgBasebandCn0?.let { cn0 ->
            DetailRow(
                stringResource(R.string.avg_baseband_cn0) + " (dumpsys)",
                "%.1f dB-Hz".format(cn0)
            )
        }
        if (dumpsysData.measurementCount > 0) {
            DetailRow(
                stringResource(R.string.measurement_count),
                "${dumpsysData.measurementCount}"
            )
        }
        if (dumpsysData.usedInFixConstellations.isNotEmpty()) {
            DetailRow(
                stringResource(R.string.used_constellations),
                dumpsysData.usedInFixConstellations.joinToString(", ")
            )
        }
    }
}

@Composable
private fun ShizukuStatusSection() {
    val isAvailable = ShizukuHelper.isShizukuAvailable
    val isGranted = ShizukuHelper.isPermissionGranted
    val isRoot = ShizukuHelper.isRootMode

    val statusText = when {
        !isAvailable -> stringResource(R.string.shizuku_unavailable)
        !isGranted -> stringResource(R.string.shizuku_permission_required)
        else -> {
            val mode = if (isRoot) stringResource(R.string.root_mode) else stringResource(R.string.adb_mode)
            stringResource(R.string.shizuku_available, mode)
        }
    }

    val statusColor = when {
        isAvailable && isGranted -> MaterialTheme.colorScheme.primary
        isAvailable -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(R.string.shizuku_status) + ": ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color = statusColor
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
    }
}
