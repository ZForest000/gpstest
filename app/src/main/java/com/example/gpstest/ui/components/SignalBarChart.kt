package com.example.gpstest.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.gpstest.R
import com.example.gpstest.domain.model.GnssSatellite
import com.example.gpstest.ui.theme.SignalMedium
import com.example.gpstest.ui.theme.SignalStrong
import com.example.gpstest.ui.theme.SignalWeak

/** 当前可见卫星的 C/N0 对比图。 */
@Composable
fun SignalBarChart(
    satellites: List<GnssSatellite>,
    modifier: Modifier = Modifier,
) {
    if (satellites.isEmpty()) return

    val bars = satellites.sortedByDescending { it.cn0DbHz }.take(24)
    GpsCard(modifier = modifier) {
        Column {
            Text(
                text = stringResource(R.string.signal_bar_chart_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Canvas(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(132.dp),
            ) {
                val baseline = size.height - 8.dp.toPx()
                val chartHeight = baseline - 8.dp.toPx()
                val gap = 3.dp.toPx()
                val barWidth = ((size.width - gap * (bars.size + 1)) / bars.size).coerceAtLeast(1.dp.toPx())

                bars.forEachIndexed { index, satellite ->
                    val height = chartHeight * (satellite.cn0DbHz.coerceIn(0f, 50f) / 50f)
                    val left = gap + index * (barWidth + gap)
                    drawRect(
                        color = signalColor(satellite.cn0DbHz),
                        topLeft =
                            androidx.compose.ui.geometry
                                .Offset(left, baseline - height),
                        size =
                            androidx.compose.ui.geometry
                                .Size(barWidth, height),
                    )
                }
            }
        }
    }
}

private fun signalColor(cn0DbHz: Float): Color =
    when {
        cn0DbHz >= 35f -> SignalStrong
        cn0DbHz >= 25f -> SignalMedium
        else -> SignalWeak
    }
