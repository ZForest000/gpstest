package com.example.gpstest.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gpstest.R
import com.example.gpstest.domain.model.LocationInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LocationCard(
    location: LocationInfo?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (location != null) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.location_info),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = if (location != null) {
                        stringResource(R.string.location_locked)
                    } else {
                        stringResource(R.string.location_searching)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (location != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            
            if (location != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.lat_format, location.latitude),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.lng_format, location.longitude),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.altitude_format, location.altitude),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.accuracy_format, location.accuracy),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                
                location.barometricAltitude?.let { baroAlt ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.baro_altitude_format, baroAlt),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        location.pressure?.let { pressure ->
                            Text(
                                text = stringResource(R.string.pressure_format, pressure),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                Text(
                    text = "更新: ${timeFormat.format(Date(location.timestamp))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
