package com.example.gpstest.ui.screens.satellite

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gpstest.data.model.Satellite
import com.example.gpstest.ui.components.CompactStatBar
import com.example.gpstest.ui.components.SatelliteCard
import com.example.gpstest.ui.components.SatelliteDetailSheet
import com.example.gpstest.ui.components.StatBar
import com.example.gpstest.viewmodel.SatelliteViewModel

/**
 * 卫星列表主界面
 *
 * @param viewModel 卫星视图模型
 * @param onRequestPermission 请求权限回调
 * @param modifier 修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SatelliteListScreen(
    viewModel: SatelliteViewModel,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedSatellite by remember { mutableStateOf<Satellite?>(null) }
    val pullToRefreshState = rememberPullToRefreshState()

    // 处理刷新
    if (pullToRefreshState.isRefreshing) {
        viewModel.refreshSatellites()
        // 刷新完成后重置状态
        if (!uiState.isLoading) {
            pullToRefreshState.endRefresh()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 顶部应用栏
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "GPS Debug Tool",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = if (uiState.hasLocationPermission) "Permission Granted" else "Permission Required",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uiState.hasLocationPermission) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshSatellites() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )

            // 主内容区域
            PullToRefreshBox(
                state = pullToRefreshState,
                onRefresh = { viewModel.refreshSatellites() },
                isRefreshing = uiState.isLoading,
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    // 没有权限
                    !uiState.hasLocationPermission -> {
                        PermissionRequestContent(onRequestPermission = onRequestPermission)
                    }
                    // 加载中
                    uiState.isLoading && uiState.satellites.isEmpty() -> {
                        LoadingContent()
                    }
                    // 错误状态
                    uiState.error != null -> {
                        ErrorContent(
                            error = uiState.error,
                            onRetry = { viewModel.refreshSatellites() }
                        )
                    }
                    // 正常显示
                    else -> {
                        SatelliteListContent(
                            satellites = uiState.satellites,
                            selectedSatellite = selectedSatellite,
                            onSatelliteClick = { selectedSatellite = it },
                            onDismissDetail = { selectedSatellite = null },
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }
}

/**
 * 权限请求内容
 */
@Composable
private fun PermissionRequestContent(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "Permission Required",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Location Permission Required",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "This app requires ACCESS_FINE_LOCATION permission to access GNSS satellite data. Please grant the permission to continue.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = "Grant Permission",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

/**
 * 加载中内容
 */
@Composable
private fun LoadingContent(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Listening for GNSS data...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 错误内容
 */
@Composable
private fun ErrorContent(
    error: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "Error",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Error Occurred",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = "Retry",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

/**
 * 卫星列表内容
 */
@Composable
private fun SatelliteListContent(
    satellites: List<Satellite>,
    selectedSatellite: Satellite?,
    onSatelliteClick: (Satellite) -> Unit,
    onDismissDetail: () -> Unit,
    viewModel: SatelliteViewModel,
    modifier: Modifier = Modifier
) {
    val stats by viewModel.stats.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 统计信息卡片
        item {
            StatisticsCard(stats = stats)
        }

        // 卫星列表标题
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Satellites",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "${satellites.size} detected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 卫星列表
        if (satellites.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Waiting for satellite data...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(satellites, key = { it.svid }) { satellite ->
                SatelliteCard(
                    satellite = satellite,
                    onClick = { onSatelliteClick(satellite) }
                )
            }
        }
    }

    // 详情底部抽屉
    selectedSatellite?.let { satellite ->
        SatelliteDetailSheet(
            satellite = satellite,
            onDismiss = onDismissDetail
        )
    }
}

/**
 * 统计信息卡片
 */
@Composable
private fun StatisticsCard(
    stats: SatelliteViewModel.SatelliteStats,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "GNSS Status",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            StatBar(
                label = "Total Satellites",
                value = stats.totalSatellites.toString()
            )

            StatBar(
                label = "Used in Fix",
                value = stats.usedInFix.toString()
            )

            StatBar(
                label = "Average SNR",
                value = "${stats.averageSnr.toInt()} dB-Hz"
            )

            // 详细统计
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CompactStatBar(
                    label = "GPS",
                    value = stats.byConstellation["GPS"]?.toString() ?: "0",
                    modifier = Modifier.weight(1f)
                )
                CompactStatBar(
                    label = "GLONASS",
                    value = stats.byConstellation["GLONASS"]?.toString() ?: "0",
                    modifier = Modifier.weight(1f)
                )
                CompactStatBar(
                    label = "Galileo",
                    value = stats.byConstellation["Galileo"]?.toString() ?: "0",
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CompactStatBar(
                    label = "BeiDou",
                    value = stats.byConstellation["BeiDou"]?.toString() ?: "0",
                    modifier = Modifier.weight(1f)
                )
                CompactStatBar(
                    label = "QZSS",
                    value = stats.byConstellation["QZSS"]?.toString() ?: "0",
                    modifier = Modifier.weight(1f)
                )
                CompactStatBar(
                    label = "NavIC",
                    value = stats.byConstellation["NavIC"]?.toString() ?: "0",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
