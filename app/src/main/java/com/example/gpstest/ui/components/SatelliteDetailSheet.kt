package com.example.gpstest.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.gpstest.R
import com.example.gpstest.domain.model.GnssSatellite
import com.example.gpstest.domain.model.MultipathIndicator
import com.example.gpstest.domain.model.PseudorangeStatus
import com.example.gpstest.ui.components.fullNameResId

@Composable
fun SatelliteDetailSheet(
    satellite: GnssSatellite,
    signalHistory: List<SignalReading> = emptyList(),
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
    ) {
        Text(
            text =
                stringResource(
                    R.string.satellite_details,
                    "${satellite.getDisplayName()}-${satellite.svid}",
                ),
            style = MaterialTheme.typography.titleLarge,
        )

        Spacer(modifier = Modifier.height(16.dp))

        DetailSection(stringResource(R.string.basic_info)) {
            DetailRow(
                stringResource(R.string.constellation_type),
                stringResource(satellite.constellation.fullNameResId),
            )
            DetailRow(
                stringResource(R.string.satellite_id),
                "${satellite.svid}",
            )
            DetailRow(
                stringResource(R.string.signal_strength),
                "${satellite.cn0DbHz.toInt()} dB-Hz",
            )
            DetailRow(
                stringResource(R.string.azimuth),
                "${satellite.azimuthDegrees.toInt()}°",
            )
            DetailRow(
                stringResource(R.string.elevation),
                "${satellite.elevationDegrees.toInt()}°",
            )
            DetailRow(
                stringResource(R.string.status),
                if (satellite.usedInFix) {
                    stringResource(R.string.used_in_fix_yes)
                } else {
                    stringResource(R.string.used_in_fix_no)
                },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        DetailSection(stringResource(R.string.raw_measurement)) {
            DetailRow(
                stringResource(R.string.carrier_frequency),
                satellite.carrierFrequencyHz?.let { "%.2f MHz".format(it / 1_000_000) }
                    ?: stringResource(R.string.value_not_available),
            )
            DetailRow(
                stringResource(R.string.carrier_phase_cycles),
                satellite.effectiveCarrierPhaseCycles?.let { "%,.3f cyc".format(it) }
                    ?: stringResource(R.string.value_not_available),
            )
            DetailRow(
                stringResource(R.string.accumulated_delta_range),
                satellite.effectiveAdrMeters?.let { "%.3f m".format(it) }
                    ?: stringResource(R.string.value_not_available),
            )
            DetailRow(
                stringResource(R.string.adr_uncertainty),
                satellite.effectiveAdrUncertaintyMeters?.let { "%.3f m".format(it) }
                    ?: stringResource(R.string.value_not_available),
            )
            DetailRow(
                stringResource(R.string.adr_status),
                adrStatusText(satellite),
            )
            DetailRow(
                stringResource(R.string.doppler_shift),
                satellite.dopplerShiftHz?.let { "%.2f Hz".format(it) }
                    ?: stringResource(R.string.value_not_available),
            )
            DetailRow(
                stringResource(R.string.agc_level),
                satellite.agcLevelDb?.let { "%.1f dB".format(it) } ?: "N/A",
            )
            DetailRow(
                stringResource(R.string.baseband_cn0),
                satellite.basebandCn0DbHz?.let { "%.1f dB-Hz".format(it) } ?: "N/A",
            )
            DetailRow(
                stringResource(R.string.multipath_detected),
                when (satellite.multipathIndicator) {
                    MultipathIndicator.DETECTED -> stringResource(R.string.yes)
                    MultipathIndicator.NOT_DETECTED -> stringResource(R.string.no)
                    else -> "N/A"
                },
            )
            DetailRow(
                stringResource(R.string.timestamp),
                formatNanosToTime(satellite.timeNanos),
            )
            DetailRow(
                stringResource(R.string.has_ephemeris),
                if (satellite.hasEphemeris) stringResource(R.string.yes) else stringResource(R.string.no),
            )
            DetailRow(
                stringResource(R.string.has_almanac),
                if (satellite.hasAlmanac) stringResource(R.string.yes) else stringResource(R.string.no),
            )
            DetailRow(
                stringResource(R.string.pseudorange_rate),
                satellite.pseudorangeRateMetersPerSecond?.let { "%.3f m/s".format(it) } ?: "N/A",
            )
            DetailRow(
                stringResource(R.string.measurement_cn0),
                satellite.measurementCn0DbHz?.let { "%.1f dB-Hz".format(it) } ?: "N/A",
            )
            DetailRow(
                stringResource(R.string.received_sv_time),
                satellite.receivedSvTimeNanos?.let { "%,d ns".format(it) } ?: "N/A",
            )
            DetailRow(
                stringResource(R.string.received_sv_time_uncertainty),
                satellite.receivedSvTimeUncertaintyNanos?.let { "%.3f ns".format(it) } ?: "N/A",
            )
            DetailRow(
                stringResource(R.string.pseudorange),
                satellite.pseudorangeMeters?.let {
                    stringResource(R.string.pseudorange_format_kilometers, it / 1_000.0)
                } ?: stringResource(R.string.value_not_available),
            )
            DetailRow(
                stringResource(R.string.pseudorange_uncertainty),
                satellite.pseudorangeUncertaintyMeters?.let {
                    stringResource(R.string.pseudorange_uncertainty_format_meters, it)
                } ?: stringResource(R.string.value_not_available),
            )
            DetailRow(
                stringResource(R.string.pseudorange_status),
                pseudorangeStatusText(satellite.pseudorangeStatus),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (signalHistory.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))

// 信号图表显示该卫星最近 60 秒的 CN0 变化，用于评估信号稳定性
            DetailSection(stringResource(R.string.signal_chart)) {
                SignalChart(
                    readings = signalHistory,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (signalHistory.size > 1) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.signal_chart_subtitle, signalHistory.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun pseudorangeStatusText(status: PseudorangeStatus): String =
    stringResource(
        when (status) {
            PseudorangeStatus.AVAILABLE -> R.string.pseudorange_status_available
            PseudorangeStatus.MISSING_MEASUREMENT -> R.string.pseudorange_status_missing_measurement
            PseudorangeStatus.UNSUPPORTED_CONSTELLATION -> R.string.pseudorange_status_unsupported_constellation
            PseudorangeStatus.MISSING_FULL_BIAS -> R.string.pseudorange_status_missing_full_bias
            PseudorangeStatus.MISSING_RECEIVED_SV_TIME -> R.string.pseudorange_status_missing_received_sv_time
            PseudorangeStatus.MISSING_CODE_LOCK -> R.string.pseudorange_status_missing_code_lock
            PseudorangeStatus.MISSING_TOW_DECODED -> R.string.pseudorange_status_missing_tow_decoded
            PseudorangeStatus.INVALID_INPUT -> R.string.pseudorange_status_invalid_input
            PseudorangeStatus.OUT_OF_RANGE -> R.string.pseudorange_status_out_of_range
        },
    )

@Composable
private fun adrStatusText(satellite: GnssSatellite): String =
    when {
        satellite.accumulatedDeltaRangeState == null ->
            stringResource(R.string.adr_status_unavailable)
        satellite.hasCycleSlip ->
            stringResource(R.string.adr_status_cycle_slip)
        satellite.isAdrValid ->
            stringResource(R.string.adr_status_valid)
        else ->
            stringResource(R.string.adr_status_invalid)
    }

@Composable
private fun DetailSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}
