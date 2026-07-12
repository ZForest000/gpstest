package com.example.gpstest.ui.screens.skychart

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.gpstest.domain.model.Constellation
import com.example.gpstest.ui.components.color

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SkyChartLegend(
    visibleConstellations: Set<Constellation>,
    onConstellationToggle: (Constellation) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // 定位状态图例
        LegendItem(dotColor = MaterialTheme.colorScheme.onSurface, filled = true, label = "定位中")
        LegendItem(dotColor = MaterialTheme.colorScheme.onSurfaceVariant, filled = false, label = "可见")

        // 分隔
        Text(
            text = "|",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 星座颜色图例
        for (constellation in Constellation.entries) {
            val isVisible = constellation in visibleConstellations
            LegendItem(
                dotColor = constellation.color,
                filled = true,
                label = constellation.shortName,
                enabled = isVisible,
                onClick = { onConstellationToggle(constellation) },
            )
        }
    }
}

@Composable
private fun LegendItem(
    dotColor: Color,
    filled: Boolean,
    label: String,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val interactionModifier =
        if (onClick != null) {
            Modifier.clickable(onClick = onClick)
        } else {
            Modifier
        }

    Row(
        modifier = interactionModifier.alpha(if (enabled) 1f else 0.35f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(10.dp)
                    .drawBehind {
                        if (filled) {
                            drawCircle(color = dotColor, radius = size.minDimension / 2f)
                        } else {
                            drawCircle(
                                color = dotColor,
                                radius = size.minDimension / 2f,
                                style =
                                    androidx.compose.ui.graphics.drawscope
                                        .Stroke(width = 1.5f),
                            )
                        }
                    },
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
