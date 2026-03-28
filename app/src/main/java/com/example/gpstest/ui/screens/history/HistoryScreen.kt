package com.example.gpstest.ui.screens.history

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.gpstest.R
import com.example.gpstest.domain.model.SatelliteHistorySnapshot
import com.example.gpstest.ui.components.HistorySnapshotCard
import com.example.gpstest.viewmodel.SatelliteViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: SatelliteViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val historySnapshots by viewModel.historySnapshots.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.saveSnapshotNow() }) {
                        Icon(
                            imageVector = Icons.Filled.Save,
                            contentDescription = stringResource(R.string.save_snapshot)
                        )
                    }
                    if (historySnapshots.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.clear_history)
                            )
                        }
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (historySnapshots.isEmpty()) {
                EmptyHistoryContent(
                    onSaveSnapshot = { viewModel.saveSnapshotNow() },
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                HistoryListContent(
                    snapshots = historySnapshots,
                    modifier = Modifier.fillMaxSize()
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
            onDismiss = { showClearDialog = false }
        )
    }
}

@Composable
private fun EmptyHistoryContent(
    onSaveSnapshot: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.no_history),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.no_history_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onSaveSnapshot) {
            Text(stringResource(R.string.save_snapshot_now))
        }
    }
}

@Composable
private fun HistoryListContent(
    snapshots: List<SatelliteHistorySnapshot>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.snapshot_count, snapshots.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(snapshots, key = { it.timestamp }) { snapshot ->
            HistorySnapshotCard(snapshot = snapshot)
        }
    }
}

@Composable
private fun ClearHistoryDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
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
        }
    )
}
