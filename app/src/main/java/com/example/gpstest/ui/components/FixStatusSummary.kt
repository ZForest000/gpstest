package com.example.gpstest.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.gpstest.R
import com.example.gpstest.domain.model.DopInfo
import com.example.gpstest.domain.model.LocationInfo
import com.example.gpstest.viewmodel.TtffState

data class FixStatusSummaryState(
    val hasFix: Boolean,
    val ttffSeconds: Double?,
    val pdop: Double?,
)

fun buildFixStatusSummaryState(
    location: LocationInfo?,
    dopInfo: DopInfo?,
    ttffState: TtffState,
): FixStatusSummaryState =
    FixStatusSummaryState(
        hasFix = location != null,
        ttffSeconds = (ttffState as? TtffState.Completed)?.ttffMs?.div(1000.0),
        pdop = dopInfo?.pdop,
    )

@Composable
fun FixStatusSummary(
    location: LocationInfo?,
    dopInfo: DopInfo?,
    ttffState: TtffState,
    modifier: Modifier = Modifier,
) {
    val state =
        remember(location, dopInfo, ttffState) {
            buildFixStatusSummaryState(
                location = location,
                dopInfo = dopInfo,
                ttffState = ttffState,
            )
        }

    GpsCard(modifier = modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.overview_fix_summary_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryMetric(
                    label = stringResource(R.string.overview_fix_status),
                    value =
                        stringResource(
                            if (state.hasFix) R.string.location_locked else R.string.location_searching,
                        ),
                    modifier = Modifier.weight(1f),
                )
                SummaryMetric(
                    label = stringResource(R.string.overview_ttff),
                    value =
                        state.ttffSeconds?.let { seconds ->
                            stringResource(R.string.overview_ttff_value, seconds)
                        } ?: stringResource(R.string.overview_metric_waiting),
                    modifier = Modifier.weight(1f),
                )
                SummaryMetric(
                    label = stringResource(R.string.overview_pdop),
                    value =
                        state.pdop?.let { pdop ->
                            stringResource(R.string.overview_pdop_value, pdop)
                        } ?: stringResource(R.string.overview_metric_waiting),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
