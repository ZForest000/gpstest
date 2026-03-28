package com.example.gpstest.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gpstest.R
import com.example.gpstest.domain.model.Constellation
import com.example.gpstest.domain.model.GnssSatellite
import com.example.gpstest.ui.theme.BeidouColor
import com.example.gpstest.ui.theme.GalileoColor
import com.example.gpstest.ui.theme.GlonassColor
import com.example.gpstest.ui.theme.GpsColor
import com.example.gpstest.ui.theme.SignalMedium
import com.example.gpstest.ui.theme.SignalStrong
import com.example.gpstest.ui.theme.SignalWeak

@Composable
fun SatelliteCard(
    satellite: GnssSatellite,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left side: Constellation indicator and ID
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ConstellationIndicator(
                constellation = satellite.constellation,
                usedInFix = satellite.usedInFix
            )
            Column {
                Text(
                    text = "${getConstellationName(satellite.constellation)}-${satellite.svid}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(
                        R.string.signal_strength_format,
                        satellite.cn0DbHz.toInt()
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = getSignalColor(satellite.cn0DbHz)
                )
            }
        }

        // Right side: Azimuth and Elevation
        Text(
            text = stringResource(
                R.string.azimuth_elevation_format,
                satellite.elevationDegrees.toInt(),
                satellite.azimuthDegrees.toInt()
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ConstellationIndicator(
    constellation: Constellation,
    usedInFix: Boolean
) {
    val indicator = if (usedInFix) "🟢" else "⚪"

    Text(
        text = indicator,
        style = MaterialTheme.typography.titleMedium
    )
}

private fun getConstellationName(constellation: Constellation): String {
    return when (constellation) {
        Constellation.GPS -> "GPS"
        Constellation.GLONASS -> "GLN"
        Constellation.GALILEO -> "GAL"
        Constellation.BEIDOU -> "BDS"
        Constellation.QZSS -> "QZS"
        Constellation.SBAS -> "SBAS"
        Constellation.UNKNOWN -> "UNK"
    }
}

private fun getSignalColor(cn0: Float): Color {
    return when {
        cn0 >= 35f -> SignalStrong
        cn0 >= 25f -> SignalMedium
        else -> SignalWeak
    }
}
