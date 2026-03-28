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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gpstest.R
import com.example.gpstest.viewmodel.TtffState

@Composable
fun TtffCard(
    ttffState: TtffState,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.ttff_title),
                style = MaterialTheme.typography.titleMedium
            )
        }

        when (ttffState) {
            is TtffState.Measuring -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.ttff_measuring),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            is TtffState.Completed -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = formatTtff(ttffState.ttffMs),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = ttffColor(ttffState.ttffMs)
                        )
                        Text(
                            text = stringResource(R.string.ttff_unit),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(onClick = onReset) {
                        Text(text = stringResource(R.string.ttff_reset))
                    }
                }
            }
        }
    }
}

private fun formatTtff(ttffMs: Long): String {
    val seconds = ttffMs / 1000.0
    return String.format("%.1f", seconds)
}

private fun ttffColor(ttffMs: Long): androidx.compose.ui.graphics.Color {
    val seconds = ttffMs / 1000.0
    return when {
        seconds < 10 -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
        seconds < 30 -> androidx.compose.ui.graphics.Color(0xFF8BC34A)
        seconds < 60 -> androidx.compose.ui.graphics.Color(0xFFFFC107)
        else -> androidx.compose.ui.graphics.Color(0xFFFF9800)
    }
}
