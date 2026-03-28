package com.example.gpstest.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.gpstest.R
import com.example.gpstest.domain.model.AGpsStatus
import com.example.gpstest.domain.model.DataStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AGpsStatusCard(
    status: AGpsStatus,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.data_status),
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            StatusRow(
                label = stringResource(R.string.time_sync),
                status = status.timeStatus
            )
            
            StatusRow(
                label = stringResource(R.string.ephemeris),
                status = status.ephemerisStatus
            )
            
            StatusRow(
                label = stringResource(R.string.almanac),
                status = status.almanacStatus
            )
            
            val injectionTime = status.lastInjectionTime ?: status.lastUpdateTime
            if (injectionTime != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.last_injection_time, formatTime(injectionTime)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    status: DataStatus,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (status == DataStatus.VALID) Icons.Default.Check else Icons.Default.Warning,
                contentDescription = null,
                tint = getStatusColor(status),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = getStatusText(status),
                style = MaterialTheme.typography.bodyMedium,
                color = getStatusColor(status)
            )
        }
    }
}

@Composable
private fun getStatusColor(status: DataStatus) = when (status) {
    DataStatus.VALID -> MaterialTheme.colorScheme.primary
    DataStatus.PARTIAL -> MaterialTheme.colorScheme.tertiary
    DataStatus.EXPIRED -> MaterialTheme.colorScheme.error
    DataStatus.MISSING -> MaterialTheme.colorScheme.error
    DataStatus.UNKNOWN -> MaterialTheme.colorScheme.outline
}

@Composable
private fun getStatusText(status: DataStatus) = when (status) {
    DataStatus.VALID -> stringResource(R.string.status_valid)
    DataStatus.PARTIAL -> stringResource(R.string.status_partial)
    DataStatus.EXPIRED -> stringResource(R.string.status_expired)
    DataStatus.MISSING -> stringResource(R.string.status_missing)
    DataStatus.UNKNOWN -> stringResource(R.string.status_unknown)
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
