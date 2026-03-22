package com.example.gpstest.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gpstest.data.model.Satellite
import com.example.gpstest.data.model.constellationType
import com.example.gpstest.data.model.snrCn0
import com.example.gpstest.util.getConstellationColor
import com.example.gpstest.util.getConstellationName

/**
 * 卫星卡片组件
 *
 * @param satellite 卫星数据
 * @param onClick 点击回调（显示详情）
 * @param modifier 修饰符
 */
@Composable
fun SatelliteCard(
    satellite: Satellite,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                satellite.usedInFix -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                satellite.hasEphemeris -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：卫星标识和基本信息
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // 卫星标识圆圈
                Box(
                    modifier = Modifier
                        .size(48.dp)
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
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )
                        Text(
                            text = satellite.svid.toString(),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )
                    }
                }

                // 卫星状态信息
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "PRN: ${satellite.svid}",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 信号强度指示器
                        SignalStrengthIndicator(snr = satellite.snrCn0)

                        // 信号强度数值
                        Text(
                            text = "${satellite.snrCn0.toInt()} dB-Hz",
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                satellite.snrCn0 < 20 -> MaterialTheme.colorScheme.error
                                satellite.snrCn0 < 30 -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                    }

                    // 状态标签
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (satellite.usedInFix) {
                            StatusBadge(
                                text = "Used",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (satellite.hasAlmanac) {
                            StatusBadge(
                                text = "Alm",
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        if (satellite.hasEphemeris) {
                            StatusBadge(
                                text = "Eph",
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }

            // 右侧：详情按钮
            IconButton(
                onClick = onClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "View Details",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * 信号强度指示器
 *
 * @param snr 信噪比 (dB-Hz)
 */
@Composable
private fun SignalStrengthIndicator(
    snr: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(24.dp, 4.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(2.dp)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(
                    when {
                        snr < 10 -> 0.2f
                        snr < 20 -> 0.4f
                        snr < 30 -> 0.6f
                        snr < 40 -> 0.8f
                        else -> 1.0f
                    }
                )
                .background(
                    color = when {
                        snr < 20 -> MaterialTheme.colorScheme.error
                        snr < 30 -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.primary
                    },
                    shape = RoundedCornerShape(2.dp)
                )
        )
    }
}

/**
 * 状态标签组件
 *
 * @param text 标签文本
 * @param color 标签颜色
 */
@Composable
private fun StatusBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = color.copy(alpha = 0.15f),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium
            ),
            color = color
        )
    }
}

/**
 * 紧凑版卫星卡片（用于列表）
 *
 * @param satellite 卫星数据
 * @param onClick 点击回调
 * @param modifier 修饰符
 */
@Composable
fun CompactSatelliteCard(
    satellite: Satellite,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(getConstellationColor(satellite.constellationType)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = satellite.svid.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "${getConstellationName(satellite.constellationType)} ${satellite.svid}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${satellite.snrCn0.toInt()} dB-Hz",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (satellite.usedInFix) {
                StatusBadge(
                    text = "USED",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
