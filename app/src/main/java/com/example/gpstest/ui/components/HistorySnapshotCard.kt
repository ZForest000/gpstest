package com.example.gpstest.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.gpstest.R
import com.example.gpstest.domain.model.SatelliteHistorySnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistorySnapshotCard(
    snapshot: SatelliteHistorySnapshot,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatTimestamp(snapshot.timestamp),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.signal_strength_avg, snapshot.averageSignalStrength.toInt()),
                style = MaterialTheme.typography.bodyMedium,
                color = getSignalColor(snapshot.averageSignalStrength)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatItemCompact(
                label = stringResource(R.string.used_in_fix),
                value = snapshot.usedInFixCount.toString()
            )
            StatItemCompact(
                label = stringResource(R.string.visible),
                value = snapshot.visibleCount.toString()
            )
            StatItemCompact(
                label = stringResource(R.string.total),
                value = snapshot.getEntries().size.toString()
            )
        }
    }
}

@Composable
private fun StatItemCompact(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun getSignalColor(cn0: Float): androidx.compose.ui.graphics.Color {
    return when {
        cn0 >= 35f -> com.example.gpstest.ui.theme.SignalStrong
        cn0 >= 25f -> com.example.gpstest.ui.theme.SignalMedium
        else -> com.example.gpstest.ui.theme.SignalWeak
    }
}
