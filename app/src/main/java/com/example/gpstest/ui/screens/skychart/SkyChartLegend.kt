package com.example.gpstest.ui.screens.skychart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.gpstest.ui.theme.BeidouColor
import com.example.gpstest.ui.theme.GalileoColor
import com.example.gpstest.ui.theme.GlonassColor
import com.example.gpstest.ui.theme.GpsColor
import com.example.gpstest.ui.theme.QzssColor
import com.example.gpstest.ui.theme.SbasColor
import com.example.gpstest.ui.theme.UnknownConstellationColor

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SkyChartLegend(
    modifier: Modifier = Modifier
) {
    val items = listOf(
        "GPS" to GpsColor,
        "BDS" to BeidouColor,
        "GLO" to GlonassColor,
        "GAL" to GalileoColor,
        "QZS" to QzssColor,
        "SBAS" to SbasColor,
        "UNK" to UnknownConstellationColor,
    )

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 定位状态图例
        LegendItem(dotColor = MaterialTheme.colorScheme.onSurface, filled = true, label = "定位中")
        LegendItem(dotColor = MaterialTheme.colorScheme.onSurfaceVariant, filled = false, label = "可见")

        // 分隔
        Text(
            text = "|",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 星座颜色图例
        for ((name, color) in items) {
            LegendItem(dotColor = color, filled = true, label = name)
        }
    }
}

@Composable
private fun LegendItem(
    dotColor: Color,
    filled: Boolean,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .drawBehind {
                    if (filled) {
                        drawCircle(color = dotColor, radius = size.minDimension / 2f)
                    } else {
                        drawCircle(
                            color = dotColor,
                            radius = size.minDimension / 2f,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
                        )
                    }
                }
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
