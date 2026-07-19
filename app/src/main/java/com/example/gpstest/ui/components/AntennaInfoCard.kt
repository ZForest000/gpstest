package com.example.gpstest.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gpstest.R
import com.example.gpstest.domain.model.AntennaInfo

@Composable
fun AntennaInfoCard(
    infos: List<AntennaInfo>,
    modifier: Modifier = Modifier,
) {
    GpsCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GpsCardTitle(text = stringResource(R.string.antenna_info_title))
            infos.forEachIndexed { index, info ->
                if (index > 0) {
                    HorizontalDivider()
                }
                AntennaInfoEntry(info = info)
            }
        }
    }
}

@Composable
private fun AntennaInfoEntry(info: AntennaInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text =
                stringResource(
                    R.string.antenna_carrier_format,
                    info.carrierFrequencyMHz,
                ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text =
                stringResource(
                    R.string.antenna_pco_format,
                    info.pcoXMm,
                    info.pcoYMm,
                    info.pcoZMm,
                ),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text =
                stringResource(
                    R.string.antenna_pco_uncertainty_format,
                    info.pcoXUncertaintyMm,
                    info.pcoYUncertaintyMm,
                    info.pcoZUncertaintyMm,
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        info.pcvSummary?.let { pcv ->
            Text(
                text =
                    stringResource(
                        R.string.antenna_pcv_summary_format,
                        pcv.deltaPhiDeg,
                        pcv.deltaThetaDeg,
                        pcv.sampleCount,
                        pcv.minCorrectionMm,
                        pcv.maxCorrectionMm,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
