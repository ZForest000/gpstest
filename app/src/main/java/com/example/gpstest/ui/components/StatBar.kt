package com.example.gpstest.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.gpstest.R
import com.example.gpstest.domain.model.GnssSatellite

@Composable
fun StatBar(
    usedInFixCount: Int,
    visibleCount: Int,
    totalCount: Int,
    satellites: List<GnssSatellite> = emptyList(),
    modifier: Modifier = Modifier
) {
    val withEphemeris = satellites.count { it.hasEphemeris }
    val withAlmanac = satellites.count { it.hasAlmanac }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.signal_stats),
                style = MaterialTheme.typography.titleMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatItem(label = stringResource(R.string.used_in_fix), count = usedInFixCount)
                StatItem(label = stringResource(R.string.visible), count = visibleCount)
                StatItem(label = stringResource(R.string.total), count = totalCount)
            }
        }
        
        if (satellites.isNotEmpty()) {
            Spacer(modifier = Modifier.padding(top = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.ephemeris_short),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$withEphemeris/$totalCount",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (withEphemeris > totalCount / 2) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.almanac_short),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$withAlmanac/$totalCount",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (withAlmanac > totalCount / 2) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "$label: $count",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
