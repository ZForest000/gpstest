package com.example.gpstest.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.gpstest.R
import com.example.gpstest.data.local.ExternalEphemerisSource
import com.example.gpstest.data.local.ExternalGpsEphemerisResult
import com.example.gpstest.domain.ephemeris.GpsObservationBuildResult
import com.example.gpstest.domain.model.PositionSolution
import com.example.gpstest.domain.model.PositionSolutionStatus

@Composable
fun LocalPositionCard(
    solution: PositionSolution?,
    diagnostics: GpsObservationBuildResult?,
    externalEphemerisResult: ExternalGpsEphemerisResult?,
    modifier: Modifier = Modifier,
) {
    if (solution == null) return
    GpsCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.local_position_title),
                style = MaterialTheme.typography.titleMedium,
            )
            val statusText =
                when (solution.status) {
                    PositionSolutionStatus.AVAILABLE -> stringResource(R.string.local_position_available)
                    PositionSolutionStatus.INSUFFICIENT_OBSERVATIONS -> stringResource(R.string.local_position_insufficient)
                    PositionSolutionStatus.INVALID_OBSERVATION -> stringResource(R.string.local_position_invalid_observation)
                    PositionSolutionStatus.INVALID_INITIAL_POSITION,
                    PositionSolutionStatus.SINGULAR_GEOMETRY,
                    PositionSolutionStatus.DID_NOT_CONVERGE,
                    -> stringResource(R.string.local_position_unavailable)
                }
            Text(
                text = stringResource(R.string.local_position_status, statusText),
                style = MaterialTheme.typography.bodyMedium,
            )
            externalEphemerisResult?.let { result ->
                val externalText =
                    when (result.source) {
                        ExternalEphemerisSource.DOWNLOAD -> stringResource(R.string.external_ephemeris_downloaded, result.ephemerides.size)
                        ExternalEphemerisSource.CACHE -> stringResource(R.string.external_ephemeris_cached, result.ephemerides.size)
                        ExternalEphemerisSource.UNAVAILABLE -> stringResource(R.string.external_ephemeris_unavailable)
                    }
                Text(
                    text = externalText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (solution.status == PositionSolutionStatus.AVAILABLE) {
                solution.receiverPosition?.let { position ->
                    Text(
                        text = stringResource(R.string.local_position_ecef, position.xMeters, position.yMeters, position.zMeters),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                solution.receiverClockBiasMeters?.let { bias ->
                    Text(
                        text = stringResource(R.string.local_position_clock_bias, bias),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                solution.weightedResidualRmsMeters?.let { rms ->
                    Text(
                        text = stringResource(R.string.local_position_rms, rms),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else if (solution.status == PositionSolutionStatus.INSUFFICIENT_OBSERVATIONS && diagnostics != null) {
                Text(
                    text = stringResource(R.string.local_position_observation_count, diagnostics.observations.size, 4),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.local_position_valid_pseudorange_count, diagnostics.validPseudorangeCount, diagnostics.gpsSatelliteCount),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.local_position_ephemeris_count, diagnostics.loadedEphemerisCount),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (diagnostics.missingEphemerisSvids.isNotEmpty()) {
                    val svids = diagnostics.missingEphemerisSvids.joinToString(", ") { "G%02d".format(it) }
                    Text(
                        text = stringResource(R.string.local_position_missing_ephemeris, svids),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
