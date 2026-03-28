package com.example.gpstest.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
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
import com.example.gpstest.ui.theme.BeidouColor
import com.example.gpstest.ui.theme.GalileoColor
import com.example.gpstest.ui.theme.GlonassColor
import com.example.gpstest.ui.theme.GpsColor
import com.example.gpstest.ui.theme.QzssColor
import com.example.gpstest.ui.theme.SbasColor
import com.example.gpstest.ui.theme.UnknownConstellationColor
import kotlin.math.roundToInt

private data class ConstellationHealthStat(
    val name: String,
    val color: Color,
    val availableCount: Int,
    val totalCount: Int
) {
    val ratio: Float
        get() = if (totalCount == 0) 0f else availableCount.toFloat() / totalCount
}

@Composable
fun ConstellationHealthSummaryCard(
    usedInFix: List<GnssSatellite>,
    allSatellites: List<GnssSatellite>,
    modifier: Modifier = Modifier
) {
    if (allSatellites.isEmpty()) return

    val stats = buildConstellationHealthStats(
        usedInFix = usedInFix,
        allSatellites = allSatellites
    )
    if (stats.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.constellation_health_summary),
            style = MaterialTheme.typography.titleMedium
        )

        stats.forEach { stat ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            R.string.constellation_health_ratio,
                            stat.name,
                            stat.availableCount,
                            stat.totalCount
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(
                            R.string.constellation_health_percent,
                            (stat.ratio * 100).roundToInt()
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = stat.color
                    )
                }

                LinearProgressIndicator(
                    progress = { stat.ratio.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = stat.color,
                    trackColor = stat.color.copy(alpha = 0.2f)
                )
            }
        }
    }
}

private fun buildConstellationHealthStats(
    usedInFix: List<GnssSatellite>,
    allSatellites: List<GnssSatellite>
): List<ConstellationHealthStat> {
    val totalByConstellation = allSatellites
        .groupBy { it.constellation }
        .mapValues { it.value.size }
    val availableByConstellation = usedInFix
        .groupBy { it.constellation }
        .mapValues { it.value.size }

    val order = listOf(
        Constellation.GPS,
        Constellation.BEIDOU,
        Constellation.GLONASS,
        Constellation.GALILEO,
        Constellation.QZSS,
        Constellation.SBAS,
        Constellation.UNKNOWN
    )

    return order.mapNotNull { constellation ->
        val total = totalByConstellation[constellation] ?: 0
        if (total == 0) return@mapNotNull null

        ConstellationHealthStat(
            name = constellation.shortName(),
            color = constellation.color(),
            availableCount = availableByConstellation[constellation] ?: 0,
            totalCount = total
        )
    }
}

private fun Constellation.shortName(): String {
    return when (this) {
        Constellation.GPS -> "GPS"
        Constellation.BEIDOU -> "BDS"
        Constellation.GLONASS -> "GLO"
        Constellation.GALILEO -> "GAL"
        Constellation.QZSS -> "QZS"
        Constellation.SBAS -> "SBAS"
        Constellation.UNKNOWN -> "UNKNOWN"
    }
}

private fun Constellation.color(): Color {
    return when (this) {
        Constellation.GPS -> GpsColor
        Constellation.BEIDOU -> BeidouColor
        Constellation.GLONASS -> GlonassColor
        Constellation.GALILEO -> GalileoColor
        Constellation.QZSS -> QzssColor
        Constellation.SBAS -> SbasColor
        Constellation.UNKNOWN -> UnknownConstellationColor
    }
}
