package com.example.gpstest.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.gpstest.ui.theme.SignalMedium
import com.example.gpstest.ui.theme.SignalStrong
import com.example.gpstest.ui.theme.SignalWeak
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun getSignalColor(cn0: Float): Color =
    when {
        cn0 >= 35f -> SignalStrong
        cn0 >= 25f -> SignalMedium
        else -> SignalWeak
    }

fun formatNanosToTime(nanos: Long): String {
    val millis = nanos / 1_000_000
    return SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(millis))
}

fun formatMillisToDateTime(millis: Long): String = SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault()).format(Date(millis))

@Composable
fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    verticalPadding: Dp = 4.dp,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = verticalPadding),
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}
