package com.example.gpstest.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
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
import com.example.gpstest.domain.model.SatelliteHistorySnapshot
import com.example.gpstest.ui.theme.GalileoColor
import com.example.gpstest.ui.theme.GpsColor
import com.example.gpstest.ui.theme.SignalStrong

/**
 * 历史趋势图：平均信号 / 定位卫星数 / 可见卫星数随时间变化。
 * 数据按时间正序绘制（旧→新）。
 */
@Composable
fun HistoryTrendChart(
    snapshots: List<SatelliteHistorySnapshot>,
    modifier: Modifier = Modifier,
) {
    if (snapshots.size < 2) return

    val ordered = snapshots.sortedBy { it.timestamp }
    val avgColor = SignalStrong
    val usedColor = GpsColor
    val visibleColor = GalileoColor
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    GpsCard(modifier = modifier) {
        Column {
            Text(
                text = stringResource(R.string.history_trend_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LegendDot(color = avgColor, label = stringResource(R.string.history_trend_avg_signal))
                LegendDot(color = usedColor, label = stringResource(R.string.history_trend_used))
                LegendDot(color = visibleColor, label = stringResource(R.string.history_trend_visible))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Canvas(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(140.dp),
            ) {
                val padding = 12.dp.toPx()
                val chartLeft = padding
                val chartTop = padding
                val chartWidth = size.width - padding * 2
                val chartHeight = size.height - padding * 2

                // 网格
                for (i in 0..4) {
                    val y = chartTop + chartHeight * i / 4
                    drawLine(
                        color = gridColor.copy(alpha = 0.35f),
                        start = Offset(chartLeft, y),
                        end = Offset(chartLeft + chartWidth, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                val n = ordered.size
                val maxSignal = (ordered.maxOfOrNull { it.averageSignalStrength } ?: 50f).coerceAtLeast(10f)
                val maxCount =
                    ordered
                        .maxOf { maxOf(it.usedInFixCount, it.visibleCount) }
                        .coerceAtLeast(4)
                        .toFloat()

                fun xAt(index: Int): Float =
                    if (n <= 1) {
                        chartLeft + chartWidth / 2
                    } else {
                        chartLeft + chartWidth * index / (n - 1)
                    }

                fun ySignal(value: Float): Float {
                    val normalized = (value.coerceIn(0f, maxSignal) / maxSignal)
                    return chartTop + chartHeight * (1 - normalized)
                }

                fun yCount(value: Int): Float {
                    val normalized = value.toFloat().coerceIn(0f, maxCount) / maxCount
                    return chartTop + chartHeight * (1 - normalized)
                }

                fun drawSeries(
                    color: Color,
                    points: List<Offset>,
                ) {
                    if (points.size < 2) return
                    val path = Path()
                    points.forEachIndexed { index, point ->
                        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                    }
                    drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
                    points.lastOrNull()?.let { last ->
                        drawCircle(color = color, radius = 3.5.dp.toPx(), center = last)
                    }
                }

                drawSeries(
                    avgColor,
                    ordered.mapIndexed { i, s -> Offset(xAt(i), ySignal(s.averageSignalStrength)) },
                )
                drawSeries(
                    usedColor,
                    ordered.mapIndexed { i, s -> Offset(xAt(i), yCount(s.usedInFixCount)) },
                )
                drawSeries(
                    visibleColor,
                    ordered.mapIndexed { i, s -> Offset(xAt(i), yCount(s.visibleCount)) },
                )
            }
        }
    }
}

@Composable
private fun LegendDot(
    color: Color,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(modifier = Modifier.size(8.dp)) {
            drawCircle(color = color)
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
