package com.example.gpstest.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gpstest.R
import com.example.gpstest.domain.model.CapabilityState
import com.example.gpstest.domain.model.GnssCapabilitiesInfo
import com.example.gpstest.domain.model.toCapabilityState

@Composable
fun GnssCapabilitiesCard(
    capabilities: GnssCapabilitiesInfo,
    modifier: Modifier = Modifier,
) {
    GpsCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.gnss_capabilities_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            CapabilityRow(
                label = stringResource(R.string.cap_hardware_model),
                value = capabilities.hardwareModelName ?: stringResource(R.string.value_not_available),
            )
            CapabilityRow(
                label = stringResource(R.string.cap_hardware_year),
                value = capabilities.yearOfHardware ?: stringResource(R.string.value_not_available),
            )

            capabilities.hasMeasurements?.let {
                CapabilityStateRow(
                    label = stringResource(R.string.cap_measurements),
                    state = it.toCapabilityState(),
                )
            }
            capabilities.hasNavigationMessages?.let {
                CapabilityStateRow(
                    label = stringResource(R.string.cap_navigation_messages),
                    state = it.toCapabilityState(),
                )
            }
            capabilities.hasAntennaInfo?.let {
                CapabilityStateRow(
                    label = stringResource(R.string.cap_antenna_info),
                    state = it.toCapabilityState(),
                )
            }
            capabilities.hasAccumulatedDeltaRange?.let {
                CapabilityStateRow(
                    label = stringResource(R.string.cap_accumulated_delta_range),
                    state = it.toCapabilityState(),
                )
            }
            capabilities.hasMeasurementCorrections?.let {
                CapabilityStateRow(
                    label = stringResource(R.string.cap_measurement_corrections),
                    state = it.toCapabilityState(),
                )
            }
            capabilities.hasMeasurementCorrelationVectors?.let {
                CapabilityStateRow(
                    label = stringResource(R.string.cap_measurement_correlation_vectors),
                    state = it.toCapabilityState(),
                )
            }
        }
    }
}

@Composable
private fun CapabilityStateRow(
    label: String,
    state: CapabilityState,
) {
    val value =
        stringResource(
            when (state) {
                CapabilityState.SUPPORTED -> R.string.cap_supported
                CapabilityState.UNSUPPORTED -> R.string.cap_unsupported
                CapabilityState.UNKNOWN -> R.string.cap_unknown
            },
        )
    CapabilityRow(label = label, value = value)
}

@Composable
private fun CapabilityRow(
    label: String,
    value: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
    }
}
