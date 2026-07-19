package com.example.gpstest.ui.screens.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.gpstest.R
import com.example.gpstest.domain.model.NavigationMessageFrame
import com.example.gpstest.ui.components.GpsCard
import com.example.gpstest.ui.components.GpsCardDensity
import com.example.gpstest.viewmodel.NavigationMessageUiState
import com.example.gpstest.viewmodel.NavigationMessageViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationMessageScreen(
    viewModel: NavigationMessageViewModel,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.startListening() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.navigation_message_title)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.cd_menu))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::clear) {
                        Icon(Icons.Default.ClearAll, contentDescription = stringResource(R.string.navigation_message_clear))
                    }
                },
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        NavigationContent(
            state = state,
            onSvidFilterChange = viewModel::setSvidFilter,
            modifier = Modifier.fillMaxSize().padding(paddingValues),
        )
    }
}

@Composable
private fun NavigationContent(
    state: NavigationMessageUiState,
    onSvidFilterChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = state.svidFilter,
            onValueChange = onSvidFilterChange,
            label = { Text(stringResource(R.string.navigation_message_svid_filter)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        LazyColumn(
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.filteredFrames.isEmpty()) {
                item {
                    Text(stringResource(R.string.navigation_message_empty), modifier = Modifier.padding(vertical = 24.dp))
                }
            }
            items(state.filteredFrames, key = { "${it.timestampMs}-${it.svid}-${it.submessageId}" }) { frame ->
                NavigationFrameCard(frame)
            }
        }
    }
}

@Composable
private fun NavigationFrameCard(frame: NavigationMessageFrame) {
    GpsCard(density = GpsCardDensity.COMPACT) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "${frame.constellation.shortName} SVID ${frame.svid} · ${frame.typeLabel()}",
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text =
                    stringResource(
                        R.string.navigation_message_metadata,
                        frame.status,
                        frame.messageId,
                        frame.submessageId,
                    ),
            )
            Text(text = frame.hexData, fontFamily = FontFamily.Monospace)
        }
    }
}

private fun NavigationMessageFrame.typeLabel(): String =
    when (type) {
        0x0101 -> "GPS L1 C/A"
        0x0102 -> "GPS L2 CNAV"
        0x0103 -> "GPS L5 CNAV"
        else -> "0x%04X".format(type)
    }
