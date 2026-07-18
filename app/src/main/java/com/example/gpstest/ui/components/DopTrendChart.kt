package com.example.gpstest.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.gpstest.R
import com.example.gpstest.domain.model.DopInfo
import com.example.gpstest.ui.theme.GalileoColor
import com.example.gpstest.ui.theme.GpsColor
import com.example.gpstest.ui.theme.SignalStrong

/** PDOP、HDOP 与 VDOP 的最近 60 个有效样本趋势。 */
@Composable
fun DopTrendChart(
    history: List<DopInfo>,
    modifier: Modifier = Modifier,
) {
    if (history.size < 2) return

    val pdopColor = SignalStrong
    val hdopColor = GpsColor
    val vdopColor = GalileoColor
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.dop_trend_chart_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DopLegend(pdopColor, "PDOP")
                DopLegend(hdopColor, "HDOP")
                DopLegend(vdopColor, "VDOP")
            }
            Canvas(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(132.dp),
            ) {
                val padding = 8.dp.toPx()
                val width = size.width - padding * 2
                val height = size.height - padding * 2
                val maximum =
                    history
                        .maxOf { maxOf(it.pdop, it.hdop, it.vdop) }
                        .coerceAtLeast(1.0)

                for (step in 0..4) {
                    val y = padding + height * step / 4
                    drawLine(
                        color = gridColor.copy(alpha = 0.35f),
                        start = Offset(padding, y),
                        end = Offset(padding + width, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                fun drawSeries(
                    color: Color,
                    selector: (DopInfo) -> Double,
                ) {
                    val path = Path()
                    history.forEachIndexed { index, dop ->
                        val x = padding + width * index / (history.size - 1)
                        val y = padding + height * (1 - (selector(dop) / maximum).toFloat())
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
                }

                drawSeries(pdopColor) { it.pdop }
                drawSeries(hdopColor) { it.hdop }
                drawSeries(vdopColor) { it.vdop }
            }
        }
    }
}

@Composable
private fun DopLegend(
    color: Color,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.height(8.dp).width(8.dp)) { drawCircle(color) }
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
