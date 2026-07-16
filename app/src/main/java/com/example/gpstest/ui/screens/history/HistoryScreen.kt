package com.example.gpstest.ui.screens.history

import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.gpstest.R
import com.example.gpstest.data.local.HistoryExportHelper
import com.example.gpstest.domain.model.HistoryTimeFilter
import com.example.gpstest.domain.model.SatelliteHistorySnapshot
import com.example.gpstest.ui.components.HistorySnapshotCard
import com.example.gpstest.ui.components.HistoryTrendChart
import com.example.gpstest.viewmodel.SatelliteViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: SatelliteViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val historySnapshots by viewModel.historySnapshots.collectAsState()
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }
    var pendingDeleteTimestamp by remember { mutableStateOf<Long?>(null) }
    var timeFilter by remember { mutableStateOf(HistoryTimeFilter.ALL) }

    val filteredSnapshots =
        remember(historySnapshots, timeFilter) {
            timeFilter.apply(historySnapshots)
        }

    val shareFailedText = stringResource(R.string.history_export_failed)
    val shareTitle = stringResource(R.string.history_share_chooser)

    fun shareSnapshots(snapshots: List<SatelliteHistorySnapshot>) {
        val ok =
            HistoryExportHelper.shareCsv(
                context = context,
                snapshots = snapshots,
                chooserTitle = shareTitle,
            )
        if (!ok) {
            Toast.makeText(context, shareFailedText, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.saveSnapshotNow() }) {
                        Icon(
                            imageVector = Icons.Filled.Save,
                            contentDescription = stringResource(R.string.save_snapshot),
                        )
                    }
                    if (filteredSnapshots.isNotEmpty()) {
                        IconButton(onClick = { shareSnapshots(filteredSnapshots) }) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = stringResource(R.string.history_export_csv),
                            )
                        }
                    }
                    if (historySnapshots.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.clear_history),
                            )
                        }
                    }
                },
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            if (historySnapshots.isEmpty()) {
                EmptyHistoryContent(
                    onSaveSnapshot = { viewModel.saveSnapshotNow() },
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                HistoryListContent(
                    allCount = historySnapshots.size,
                    snapshots = filteredSnapshots,
                    timeFilter = timeFilter,
                    onTimeFilterChange = { timeFilter = it },
                    onDelete = { pendingDeleteTimestamp = it },
                    onShareSnapshot = { shareSnapshots(listOf(it)) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (showClearDialog) {
        ClearHistoryDialog(
            onConfirm = {
                viewModel.clearHistory()
                showClearDialog = false
            },
            onDismiss = { showClearDialog = false },
        )
    }

    pendingDeleteTimestamp?.let { timestamp ->
        DeleteSnapshotDialog(
            onConfirm = {
                viewModel.deleteSnapshot(timestamp)
                pendingDeleteTimestamp = null
            },
            onDismiss = { pendingDeleteTimestamp = null },
        )
    }
}

@Composable
private fun EmptyHistoryContent(
    onSaveSnapshot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.no_history),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.no_history_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onSaveSnapshot) {
            Text(stringResource(R.string.save_snapshot_now))
        }
    }
}

@Composable
private fun HistoryListContent(
    allCount: Int,
    snapshots: List<SatelliteHistorySnapshot>,
    timeFilter: HistoryTimeFilter,
    onTimeFilterChange: (HistoryTimeFilter) -> Unit,
    onDelete: (Long) -> Unit,
    onShareSnapshot: (SatelliteHistorySnapshot) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TimeFilterRow(
                selected = timeFilter,
                onSelected = onTimeFilterChange,
            )
        }

        if (snapshots.size >= 2) {
            item {
                HistoryTrendChart(snapshots = snapshots)
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text =
                        if (timeFilter == HistoryTimeFilter.ALL) {
                            stringResource(R.string.snapshot_count, allCount)
                        } else {
                            stringResource(R.string.snapshot_count_filtered, snapshots.size, allCount)
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (snapshots.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.history_filter_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        } else {
            items(snapshots, key = { it.timestamp }) { snapshot ->
                HistorySnapshotCard(
                    snapshot = snapshot,
                    onDelete = { onDelete(snapshot.timestamp) },
                    onShare = { onShareSnapshot(snapshot) },
                )
            }
        }
    }
}

@Composable
private fun TimeFilterRow(
    selected: HistoryTimeFilter,
    onSelected: (HistoryTimeFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(HistoryTimeFilter.entries) { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelected(filter) },
                label = { Text(stringResource(filter.labelRes())) },
            )
        }
    }
}

@Composable
private fun ClearHistoryDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clear_history_title)) },
        text = { Text(stringResource(R.string.clear_history_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.clear))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun DeleteSnapshotDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history_delete_title)) },
        text = { Text(stringResource(R.string.history_delete_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

private fun HistoryTimeFilter.labelRes(): Int =
    when (this) {
        HistoryTimeFilter.ALL -> R.string.history_filter_all
        HistoryTimeFilter.HOUR_1 -> R.string.history_filter_1h
        HistoryTimeFilter.HOUR_6 -> R.string.history_filter_6h
        HistoryTimeFilter.HOUR_24 -> R.string.history_filter_24h
        HistoryTimeFilter.DAY_7 -> R.string.history_filter_7d
    }
