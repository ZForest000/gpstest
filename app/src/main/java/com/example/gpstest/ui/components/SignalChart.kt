package com.example.gpstest.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.gpstest.ui.theme.SignalStrong
import com.example.gpstest.ui.theme.SignalMedium
import com.example.gpstest.ui.theme.SignalWeak

data class SignalReading(
    val timestamp: Long,
    val cn0DbHz: Float
)

@Composable
fun SignalChart(
    readings: List<SignalReading>,
    modifier: Modifier = Modifier,
    minSignal: Float = 0f,
    maxSignal: Float = 50f
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textGreen = SignalStrong
    val textYellow = SignalMedium
    val textRed = SignalWeak
    val lineColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val padding = 16.dp.toPx()
        val chartWidth = canvasWidth - padding * 2
        val chartHeight = canvasHeight - padding * 2

        drawGridLines(
            chartLeft = padding,
            chartTop = padding,
            chartWidth = chartWidth,
            chartHeight = chartHeight,
            gridColor = gridColor,
            minSignal = minSignal,
            maxSignal = maxSignal,
            strongThreshold = 35f,
            mediumThreshold = 25f,
            textGreen = textGreen,
            textYellow = textYellow,
            textRed = textRed
        )

        if (readings.isNotEmpty()) {
            drawSignalLine(
                readings = readings,
                chartLeft = padding,
                chartTop = padding,
                chartWidth = chartWidth,
                chartHeight = chartHeight,
                lineColor = lineColor,
                minSignal = minSignal,
                maxSignal = maxSignal
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGridLines(
    chartLeft: Float,
    chartTop: Float,
    chartWidth: Float,
    chartHeight: Float,
    gridColor: Color,
    minSignal: Float,
    maxSignal: Float,
    strongThreshold: Float,
    mediumThreshold: Float,
    textGreen: Color,
    textYellow: Color,
    textRed: Color
) {
    val signalRange = maxSignal - minSignal
    
    val strongY = chartTop + chartHeight * (1 - (strongThreshold - minSignal) / signalRange)
    val mediumY = chartTop + chartHeight * (1 - (mediumThreshold - minSignal) / signalRange)

    drawLine(
        color = textGreen.copy(alpha = 0.3f),
        start = Offset(chartLeft, strongY),
        end = Offset(chartLeft + chartWidth, strongY),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
    )

    drawLine(
        color = textYellow.copy(alpha = 0.3f),
        start = Offset(chartLeft, mediumY),
        end = Offset(chartLeft + chartWidth, mediumY),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
    )

    for (i in 0..4) {
        val x = chartLeft + chartWidth * i / 4
        drawLine(
            color = gridColor.copy(alpha = 0.3f),
            start = Offset(x, chartTop),
            end = Offset(x, chartTop + chartHeight),
            strokeWidth = 1.dp.toPx()
        )
    }

    drawLine(
        color = gridColor,
        start = Offset(chartLeft, chartTop + chartHeight),
        end = Offset(chartLeft + chartWidth, chartTop + chartHeight),
        strokeWidth = 1.dp.toPx()
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSignalLine(
    readings: List<SignalReading>,
    chartLeft: Float,
    chartTop: Float,
    chartWidth: Float,
    chartHeight: Float,
    lineColor: Color,
    minSignal: Float,
    maxSignal: Float
) {
    if (readings.size < 2) return

    val signalRange = maxSignal - minSignal
    val path = Path()

    readings.forEachIndexed { index, reading ->
        val x = chartLeft + chartWidth * index / (readings.size - 1)
        val normalizedSignal = (reading.cn0DbHz.coerceIn(minSignal, maxSignal) - minSignal) / signalRange
        val y = chartTop + chartHeight * (1 - normalizedSignal)

        if (index == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }

    drawPath(
        path = path,
        color = lineColor,
        style = Stroke(width = 2.dp.toPx())
    )

    readings.lastOrNull()?.let { lastReading ->
        val x = chartLeft + chartWidth
        val normalizedSignal = (lastReading.cn0DbHz.coerceIn(minSignal, maxSignal) - minSignal) / signalRange
        val y = chartTop + chartHeight * (1 - normalizedSignal)

        drawCircle(
            color = lineColor,
            radius = 4.dp.toPx(),
            center = Offset(x, y)
        )
    }
}
