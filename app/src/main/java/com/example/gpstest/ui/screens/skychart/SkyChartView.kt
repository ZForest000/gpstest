package com.example.gpstest.ui.screens.skychart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gpstest.domain.model.Constellation
import com.example.gpstest.domain.model.GnssSatellite
import com.example.gpstest.ui.theme.BeidouColor
import com.example.gpstest.ui.theme.GalileoColor
import com.example.gpstest.ui.theme.GlonassColor
import com.example.gpstest.ui.theme.GpsColor
import com.example.gpstest.ui.theme.QzssColor
import com.example.gpstest.ui.theme.SbasColor
import com.example.gpstest.ui.theme.UnknownConstellationColor
import kotlin.math.cos
import kotlin.math.sin

private data class SatellitePlot(
    val satellite: GnssSatellite,
    val x: Float,
    val y: Float,
    val visualRadius: Float
)

@Composable
fun SkyChartView(
    satellites: List<GnssSatellite>,
    onSatelliteClick: (GnssSatellite) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val isDarkTheme = isSystemInDarkTheme()

    // 暗色/亮色主题颜色
    val bgColor = if (isDarkTheme) Color(0xFF2A2A2A) else Color(0xFFF0F0F0)
    val gridColor = if (isDarkTheme) Color.White.copy(alpha = 0.2f) else Color(0xFFBDBDBD).copy(alpha = 0.3f)
    val labelColor = if (isDarkTheme) Color.White.copy(alpha = 0.6f) else Color(0xFF757575)
    val emptyTextColor = if (isDarkTheme) Color.White.copy(alpha = 0.4f) else Color(0xFF9E9E9E)
    val nonFixAlpha = if (isDarkTheme) 0.35f else 0.5f

    // 过滤有有效方位/仰角的卫星
    val plottableSatellites = satellites.filter {
        it.azimuthDegrees > 0f || it.elevationDegrees > 0f
    }

    BoxWithConstraints(modifier = modifier) {
        val sizePx = with(density) { minOf(maxWidth, maxHeight).toPx() }
        val center = Offset(sizePx / 2f, sizePx / 2f)
        val maxRadius = sizePx / 2f - with(density) { 24.dp.toPx() }
        val touchRadius = with(density) { 20.dp.toPx() }

        // 预计算卫星位置
        val plots = plottableSatellites.map { sat ->
            val elRad = sat.elevationDegrees.coerceIn(0f, 90f)
            val azRad = Math.toRadians((sat.azimuthDegrees - 90.0))
            val r = (1f - elRad / 90f) * maxRadius
            val x = center.x + r * cos(azRad).toFloat()
            val y = center.y + r * sin(azRad).toFloat()
            val visualRadius = with(density) {
                (5f + (sat.cn0DbHz.coerceIn(0f, 50f) / 50f) * 5f).dp.toPx()
            }
            SatellitePlot(sat, x, y, visualRadius)
        }

        Canvas(
            modifier = Modifier
                .semantics {
                    contentDescription = "卫星天空图，显示 ${plottableSatellites.size} 颗卫星的位置分布"
                }
                .pointerInput(plots) {
                    detectTapGestures { offset ->
                        val hit = plots.minByOrNull { plot ->
                            val dx = offset.x - plot.x
                            val dy = offset.y - plot.y
                            dx * dx + dy * dy
                        }
                        if (hit != null) {
                            val dx = offset.x - hit.x
                            val dy = offset.y - hit.y
                            if (dx * dx + dy * dy <= touchRadius * touchRadius) {
                                onSatelliteClick(hit.satellite)
                            }
                        }
                    }
                }
        ) {
            // 背景
            drawCircle(color = bgColor, radius = maxRadius, center = center)

            // 同心圆环 (0°, 30°, 60°, 90° 仰角)
            val elevations = listOf(0f, 30f, 60f, 90f)
            for (el in elevations) {
                val r = (1f - el / 90f) * maxRadius
                drawCircle(
                    color = gridColor,
                    radius = r,
                    center = center,
                    style = Stroke(
                        width = if (el == 0f) 2f else 1f,
                        pathEffect = if (el != 0f) PathEffect.dashPathEffect(floatArrayOf(10f, 10f)) else null
                    )
                )
            }

            // 十字线 (N-S, E-W)
            drawLine(gridColor, Offset(center.x - maxRadius, center.y), Offset(center.x + maxRadius, center.y), strokeWidth = 1f)
            drawLine(gridColor, Offset(center.x, center.y - maxRadius), Offset(center.x, center.y + maxRadius), strokeWidth = 1f)

            // 仰角标签 (30°, 60°)
            for (el in listOf(30f, 60f)) {
                val r = (1f - el / 90f) * maxRadius
                val labelResult = textMeasurer.measure(
                    text = AnnotatedString("${el.toInt()}°"),
                    style = TextStyle(fontSize = 10.sp)
                )
                drawText(
                    textLayoutResult = labelResult,
                    color = labelColor,
                    topLeft = Offset(center.x + 4f, center.y - r - labelResult.size.height.toFloat())
                )
            }

            // 方位标签 (N, S, E, W)
            val directions = listOf("N" to -90f, "E" to 0f, "S" to 90f, "W" to 180f)
            for ((label, angleDeg) in directions) {
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val labelR = maxRadius + with(density) { 12.dp.toPx() }
                val lx = center.x + labelR * cos(angleRad).toFloat()
                val ly = center.y + labelR * sin(angleRad).toFloat()
                val labelResult = textMeasurer.measure(
                    text = AnnotatedString(label),
                    style = TextStyle(fontSize = 12.sp)
                )
                drawText(
                    textLayoutResult = labelResult,
                    color = labelColor,
                    topLeft = Offset(
                        lx - labelResult.size.width / 2f,
                        ly - labelResult.size.height / 2f
                    )
                )
            }

            // 绘制卫星点
            for (plot in plots) {
                val sat = plot.satellite
                val color = getConstellationColor(sat.constellation)
                val alpha = if (sat.usedInFix) 1f else nonFixAlpha
                val borderWidth = if (sat.usedInFix) with(density) { 2.dp.toPx() } else with(density) { 1.dp.toPx() }

                drawCircle(
                    color = color.copy(alpha = alpha),
                    radius = plot.visualRadius,
                    center = Offset(plot.x, plot.y)
                )
                drawCircle(
                    color = color.copy(alpha = if (sat.usedInFix) 1f else 0.7f),
                    radius = plot.visualRadius,
                    center = Offset(plot.x, plot.y),
                    style = Stroke(width = borderWidth)
                )
            }

            // 空状态提示
            if (plottableSatellites.isEmpty()) {
                val textResult = textMeasurer.measure(
                    text = AnnotatedString("等待卫星信号..."),
                    style = TextStyle(fontSize = 14.sp)
                )
                drawText(
                    textLayoutResult = textResult,
                    color = emptyTextColor,
                    topLeft = Offset(
                        center.x - textResult.size.width / 2f,
                        center.y - textResult.size.height / 2f
                    )
                )
            }
        }
    }
}

private fun getConstellationColor(constellation: Constellation): Color {
    return when (constellation) {
        Constellation.GPS -> GpsColor
        Constellation.BEIDOU -> BeidouColor
        Constellation.GLONASS -> GlonassColor
        Constellation.GALILEO -> GalileoColor
        Constellation.QZSS -> QzssColor
        Constellation.SBAS -> SbasColor
        Constellation.UNKNOWN -> UnknownConstellationColor
    }
}
