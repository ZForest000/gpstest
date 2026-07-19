package com.example.gpstest.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.gpstest.R
import com.example.gpstest.domain.model.Constellation
import com.example.gpstest.domain.model.GnssSatellite
import com.example.gpstest.domain.model.SignalStrength
import com.example.gpstest.ui.theme.SignalMedium
import com.example.gpstest.ui.theme.SignalStrong
import com.example.gpstest.ui.theme.SignalWeak

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConstellationStatCard(
    usedInFix: List<GnssSatellite>,
    modifier: Modifier = Modifier,
) {
    if (usedInFix.isEmpty()) return

    val constellationCounts =
        usedInFix
            .groupBy { it.constellation }
            .mapValues { it.value.size }

    val signalCounts =
        usedInFix
            .groupBy { it.signalStrength }
            .mapValues { it.value.size }

    GpsCard(modifier = modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.constellation_stat_title),
                style = MaterialTheme.typography.titleMedium,
            )

            // Constellation row
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val constellationOrder =
                    Constellation.entries
                        .filter { it != Constellation.UNKNOWN }
                        .map { it to it.shortName to it.color }

                for ((pair, color) in constellationOrder) {
                    val (constellation, name) = pair
                    val count = constellationCounts[constellation] ?: 0
                    ConstellationChip(name = name, count = count, color = color)
                }
            }

            // Signal strength row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SignalChip(
                    label = stringResource(R.string.signal_strength_strong_short),
                    count = signalCounts[SignalStrength.STRONG] ?: 0,
                    color = SignalStrong,
                )
                SignalChip(
                    label = stringResource(R.string.signal_strength_medium_short),
                    count = signalCounts[SignalStrength.MEDIUM] ?: 0,
                    color = SignalMedium,
                )
                SignalChip(
                    label = stringResource(R.string.signal_strength_weak_short),
                    count = signalCounts[SignalStrength.WEAK] ?: 0,
                    color = SignalWeak,
                )
            }
        }
    }
}

@Composable
private fun ConstellationChip(
    name: String,
    count: Int,
    color: Color,
) {
    Row(
        modifier =
            Modifier
                .background(
                    color = color.copy(alpha = if (count > 0) 0.2f else 0.08f),
                    shape = RoundedCornerShape(16.dp),
                ).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            color = if (count > 0) color else color.copy(alpha = 0.5f),
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelLarge,
            color = if (count > 0) color else color.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun SignalChip(
    label: String,
    count: Int,
    color: Color,
) {
    Row(
        modifier =
            Modifier
                .background(
                    color = color.copy(alpha = if (count > 0) 0.2f else 0.08f),
                    shape = RoundedCornerShape(16.dp),
                ).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (count > 0) color else color.copy(alpha = 0.5f),
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelLarge,
            color = if (count > 0) color else color.copy(alpha = 0.5f),
        )
    }
}
