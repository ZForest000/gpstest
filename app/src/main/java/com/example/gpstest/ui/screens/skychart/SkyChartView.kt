package com.example.gpstest.ui.screens.skychart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
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
import com.example.gpstest.domain.model.GnssSatellite
import com.example.gpstest.ui.components.color
import kotlin.math.cos
import kotlin.math.sin

private data class SatellitePlot(
    val satellite: GnssSatellite,
    val x: Float,
    val y: Float,
    val visualRadius: Float,
    val animAlpha: Float,
)

/**
 * 天空图极坐标画布。中心 = 天顶（仰角 90°），边缘 = 地平线（仰角 0°）。
 * 卫星点半径与 CN0 成正比（信号越强，点越大）。
 * 方位角 0° = 正北（坐标变换见 azimuthDegrees - 90 处注释）。
 */
@Composable
fun SkyChartView(
    satellites: List<AnimatedSatellite>,
    transformState: SkyChartTransformState,
    headingDegrees: Float,
    onSatelliteClick: (GnssSatellite) -> Unit,
    modifier: Modifier = Modifier,
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

    val plottableSatellites =
        satellites.filter {
            it.azimuthDegrees > 0f || it.elevationDegrees > 0f
        }

    BoxWithConstraints(modifier = modifier) {
        val sizePx = with(density) { minOf(maxWidth, maxHeight).toPx() }
        val center = Offset(sizePx / 2f, sizePx / 2f)
        val maxRadius = sizePx / 2f - with(density) { 24.dp.toPx() }
        val touchRadius = with(density) { 20.dp.toPx() }

        val plots =
            plottableSatellites.map { anim ->
                val elRad = anim.elevationDegrees.coerceIn(0f, 90f)
                val azRad = Math.toRadians((anim.azimuthDegrees - 90.0))
                // az - 90：数学坐标系角度 0 为 +X 轴（屏幕右方），
                // 减 90° 使方位角 0°（正北）显示在屏幕顶部
                val r = (1f - elRad / 90f) * maxRadius
                val x = center.x + r * cos(azRad).toFloat()
                val y = center.y + r * sin(azRad).toFloat()
                val visualRadius =
                    with(density) {
                        (5f + (anim.satellite.cn0DbHz.coerceIn(0f, 50f) / 50f) * 5f).dp.toPx()
                    }
                SatellitePlot(anim.satellite, x, y, visualRadius, anim.alpha)
            }

        // Stable keys for pointerInput; latest values read via rememberUpdatedState
        // so az/el/alpha animation does not cancel gestures mid-flight.
        val latestPlots = rememberUpdatedState(plots)
        val latestTransformState = rememberUpdatedState(transformState)
        val latestHeadingDegrees = rememberUpdatedState(headingDegrees)
        val latestOnSatelliteClick = rememberUpdatedState(onSatelliteClick)

        Canvas(
            modifier =
                Modifier
                    .semantics {
                        contentDescription = "卫星天空图，显示 ${plottableSatellites.size} 颗卫星的位置分布"
                    }.pointerInput(
                        transformState.northUp,
                        headingDegrees,
                        center,
                        maxRadius,
                    ) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val ts = latestTransformState.value
                            val heading = latestHeadingDegrees.value
                            if (zoom != 1f) {
                                ts.applyZoom(
                                    centroid = centroid,
                                    zoomChange = zoom,
                                    center = center,
                                    maxRadius = maxRadius,
                                )
                            }
                            if (pan != Offset.Zero) {
                                val adjustedPan =
                                    if (ts.northUp) {
                                        rotateOffset(pan, heading)
                                    } else {
                                        pan
                                    }
                                ts.applyPan(adjustedPan, maxRadius)
                            }
                        }
                    }.pointerInput(
                        transformState.scale,
                        transformState.offset,
                        transformState.northUp,
                        headingDegrees,
                        center,
                        maxRadius,
                    ) {
                        detectTapGestures(
                            onDoubleTap = {
                                latestTransformState.value.resetScaleAndOffset()
                            },
                            onTap = { screenOffset ->
                                val ts = latestTransformState.value
                                val heading = latestHeadingDegrees.value
                                val chartPoint =
                                    screenToChart(
                                        point = screenOffset,
                                        center = center,
                                        scale = ts.scale,
                                        offset = ts.offset,
                                        headingDeg = if (ts.northUp) heading else 0f,
                                        northUp = ts.northUp,
                                    )
                                val hit =
                                    latestPlots.value
                                        .filter { it.animAlpha > 0.5f }
                                        .minByOrNull { plot ->
                                            val dx = chartPoint.x - plot.x
                                            val dy = chartPoint.y - plot.y
                                            dx * dx + dy * dy
                                        }
                                if (hit != null) {
                                    val dx = chartPoint.x - hit.x
                                    val dy = chartPoint.y - hit.y
                                    if (dx * dx + dy * dy <= touchRadius * touchRadius) {
                                        latestOnSatelliteClick.value(hit.satellite)
                                    }
                                }
                            },
                        )
                    },
        ) {
            val scale = transformState.scale
            val pan = transformState.offset
            val northUp = transformState.northUp
            val heading = if (northUp) headingDegrees else 0f

            withTransform({
                // Order: translate(center) → rotate(-heading) → translate(offset) → scale
                translate(left = center.x, top = center.y)
                if (northUp) {
                    rotate(degrees = -heading, pivot = Offset.Zero)
                }
                translate(left = pan.x, top = pan.y)
                scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
                // Shift so absolute center-based coords still work under the stack
                translate(left = -center.x, top = -center.y)
            }) {
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
                        style =
                            Stroke(
                                width = if (el == 0f) 2f else 1f,
                                pathEffect = if (el != 0f) PathEffect.dashPathEffect(floatArrayOf(10f, 10f)) else null,
                            ),
                    )
                }

                // 十字线 (N-S, E-W)
                drawLine(gridColor, Offset(center.x - maxRadius, center.y), Offset(center.x + maxRadius, center.y), strokeWidth = 1f)
                drawLine(gridColor, Offset(center.x, center.y - maxRadius), Offset(center.x, center.y + maxRadius), strokeWidth = 1f)

                // 仰角标签 (30°, 60°)
                for (el in listOf(30f, 60f)) {
                    val r = (1f - el / 90f) * maxRadius
                    val labelResult =
                        textMeasurer.measure(
                            text = AnnotatedString("${el.toInt()}°"),
                            style = TextStyle(fontSize = 10.sp),
                        )
                    drawText(
                        textLayoutResult = labelResult,
                        color = labelColor,
                        topLeft = Offset(center.x + 4f, center.y - r - labelResult.size.height.toFloat()),
                    )
                }

                // 方位标签 (N, S, E, W)
                val directions = listOf("N" to -90f, "E" to 0f, "S" to 90f, "W" to 180f)
                for ((label, angleDeg) in directions) {
                    val angleRad = Math.toRadians(angleDeg.toDouble())
                    val labelR = maxRadius + with(density) { 12.dp.toPx() }
                    val lx = center.x + labelR * cos(angleRad).toFloat()
                    val ly = center.y + labelR * sin(angleRad).toFloat()
                    val labelResult =
                        textMeasurer.measure(
                            text = AnnotatedString(label),
                            style = TextStyle(fontSize = 12.sp),
                        )
                    drawText(
                        textLayoutResult = labelResult,
                        color = labelColor,
                        topLeft =
                            Offset(
                                lx - labelResult.size.width / 2f,
                                ly - labelResult.size.height / 2f,
                            ),
                    )
                }

                // 绘制卫星点
                for (plot in plots) {
                    if (plot.animAlpha <= 0.01f) continue

                    val sat = plot.satellite
                    val color = sat.constellation.color
                    val alpha = (if (sat.usedInFix) 1f else nonFixAlpha) * plot.animAlpha
                    val borderWidth = if (sat.usedInFix) with(density) { 2.dp.toPx() } else with(density) { 1.dp.toPx() }

                    drawCircle(
                        color = color.copy(alpha = alpha),
                        radius = plot.visualRadius,
                        center = Offset(plot.x, plot.y),
                    )
                    drawCircle(
                        color = color.copy(alpha = (if (sat.usedInFix) 1f else 0.7f) * plot.animAlpha),
                        radius = plot.visualRadius,
                        center = Offset(plot.x, plot.y),
                        style = Stroke(width = borderWidth),
                    )

                    val svidLabel =
                        textMeasurer.measure(
                            text = AnnotatedString(sat.svid.toString()),
                            style = TextStyle(fontSize = 10.sp),
                        )
                    drawText(
                        textLayoutResult = svidLabel,
                        color = color.copy(alpha = alpha),
                        topLeft =
                            Offset(
                                plot.x + plot.visualRadius + with(density) { 3.dp.toPx() },
                                plot.y - plot.visualRadius - svidLabel.size.height / 2f,
                            ),
                    )
                }
            }

            // 空状态提示 — outside transform so stays readable when zoomed
            if (plottableSatellites.isEmpty()) {
                val textResult =
                    textMeasurer.measure(
                        text = AnnotatedString("等待卫星信号..."),
                        style = TextStyle(fontSize = 14.sp),
                    )
                drawText(
                    textLayoutResult = textResult,
                    color = emptyTextColor,
                    topLeft =
                        Offset(
                            center.x - textResult.size.width / 2f,
                            center.y - textResult.size.height / 2f,
                        ),
                )
            }
        }
    }
}
