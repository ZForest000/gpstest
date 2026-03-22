package com.example.gpstest.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gpstest.data.model.Satellite
import com.example.gpstest.data.model.almanac
import com.example.gpstest.data.model.azimuth
import com.example.gpstest.data.model.constellationType
import com.example.gpstest.data.model.elevation
import com.example.gpstest.data.model.ephemeris
import com.example.gpstest.data.model.snrCn0
import com.example.gpstest.util.getConstellationColor
import com.example.gpstest.util.getConstellationName
import kotlinx.coroutines.launch

/**
 * 卫星详情底部抽屉
 *
 * @param satellite 卫星数据
 * @param onDismiss 关闭回调
 * @param modifier 修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SatelliteDetailSheet(
    satellite: Satellite,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = BottomSheetDefaults.expandableState(
            skipHiddenState = false
        )
    )

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                // 顶部拖动条和关闭按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp, 4.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(2.dp)
                            )
                    )

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // 卫星标题
                SatelliteHeader(satellite = satellite)

                // 详细信息列表
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        StatSection(title = "Basic Information") {
                            StatRow(label = "Constellation", value = getConstellationName(satellite.constellationType))
                            StatRow(label = "PRN (SVID)", value = satellite.svid.toString())
                            StatRow(label = "Frequency", value = "${satellite.carrierFrequencyHz.toInt()} Hz")
                        }
                    }

                    item {
                        StatSection(title = "Signal Information") {
                            StatRow(label = "SNR/CN0", value = "${satellite.snrCn0.toFloat().toInt()} dB-Hz")
                            StatRow(label = "Signal Type", value = if (satellite.constellationType == 1) "L1" else "Multi-frequency")
                            satellite.almanac?.let {
                                StatRow(label = "Almanac", value = "Present")
                            }
                            satellite.ephemeris?.let {
                                StatRow(label = "Ephemeris", value = "Present")
                            }
                        }
                    }

                    item {
                        StatSection(title = "Position Information") {
                            satellite.azimuth?.let {
                                StatRow(label = "Azimuth", value = "${it.toFloat().toInt()}°")
                            }
                            satellite.elevation?.let {
                                StatRow(label = "Elevation", value = "${it.toFloat().toInt()}°")
                            }
                        }
                    }

                    item {
                        StatSection(title = "Status") {
                            StatRow(
                                label = "Used in Fix",
                                value = if (satellite.usedInFix) "Yes" else "No",
                                highlight = satellite.usedInFix
                            )
                            StatRow(
                                label = "Has Almanac",
                                value = if (satellite.hasAlmanac) "Yes" else "No"
                            )
                            StatRow(
                                label = "Has Ephemeris",
                                value = if (satellite.hasEphemeris) "Yes" else "No"
                            )
                        }
                    }

                    item {
                        StatSection(title = "Timing Information") {
                            satellite.time?.let {
                                StatRow(label = "Time", value = "$it ns")
                            }
                            satellite.clockBias?.let {
                                StatRow(label = "Clock Bias", value = "$it ns")
                            }
                            satellite.clockDrift?.let {
                                StatRow(label = "Clock Drift", value = "$it ns/s")
                            }
                            satellite.clockDiscontinuityCount?.let {
                                StatRow(label = "Clock Discontinuities", value = it.toString())
                            }
                        }
                    }

                    item {
                        StatSection(title = "Additional Information") {
                            satellite.basebandCn0?.let {
                                StatRow(label = "Baseband CN0", value = "${it.toFloat().toInt()} dB-Hz")
                            }
                            satellite.pseudorangeRate?.let {
                                StatRow(label = "Pseudorange Rate", value = "$it m/s")
                            }
                            satellite.pseudorangeRateUncertainty?.let {
                                StatRow(label = "Pseudorange Rate Uncertainty", value = "$it m/s")
                            }
                            satellite.ephemeris?.let {
                                StatRow(label = "Ephemeris Source", value = "GNSS Measurements")
                            }
                        }
                    }
                }
            }
        },
        sheetPeekHeight = 600.dp,
        modifier = modifier
    ) {}
}

/**
 * 卫星标题组件
 */
@Composable
private fun SatelliteHeader(
    satellite: Satellite,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 卫星标识圆圈
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(getConstellationColor(satellite.constellationType)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = getConstellationName(satellite.constellationType),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
                Text(
                    text = satellite.svid.toString(),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
            }
        }

        // 卫星名称和状态
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "${getConstellationName(satellite.constellationType)} Satellite ${satellite.svid}",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusBadge(
                    text = "${satellite.snrCn0.toFloat().toInt()} dB-Hz",
                    color = when {
                        satellite.snrCn0 < 20 -> MaterialTheme.colorScheme.error
                        satellite.snrCn0 < 30 -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.primary
                    }
                )

                if (satellite.usedInFix) {
                    StatusBadge(
                        text = "Used in Fix",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * 统计部分组件
 */
@Composable
private fun StatSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.primary
        )
        content()
    }
}

/**
 * 统计行组件
 */
@Composable
private fun StatRow(
    label: String,
    value: String,
    highlight: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal
            ),
            color = if (highlight) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}
