package com.example.gpstest.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.gpstest.R
import com.example.gpstest.domain.model.AppSettings
import com.example.gpstest.domain.model.DarkModeConfig
import com.example.gpstest.ui.components.GpsCard
import com.example.gpstest.ui.components.GpsCardMeta
import com.example.gpstest.ui.components.GpsCardTitle
import com.example.gpstest.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                AppearanceCard(
                    darkMode = settings.darkMode,
                    onDarkModeChange = { mode ->
                        viewModel.updateSettings(settings.copy(darkMode = mode))
                    },
                )
            }

            item {
                NmeaCard(
                    nmeaEnabled = settings.nmeaEnabled,
                    onNmeaEnabledChange = { enabled ->
                        viewModel.updateSettings(settings.copy(nmeaEnabled = enabled))
                    },
                )
            }

            item {
                SnapshotCard(
                    settings = settings,
                    onSettingsChange = { viewModel.updateSettings(it) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceCard(
    darkMode: DarkModeConfig,
    onDarkModeChange: (DarkModeConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    GpsCard(modifier = modifier) {
        Column {
            GpsCardTitle(text = stringResource(R.string.settings_dark_mode))
            Spacer(modifier = Modifier.height(4.dp))
            GpsCardMeta(text = stringResource(R.string.settings_dark_mode_summary))
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = darkMode == DarkModeConfig.SYSTEM,
                    onClick = { onDarkModeChange(DarkModeConfig.SYSTEM) },
                    label = { Text(stringResource(R.string.settings_dark_mode_system)) },
                )
                FilterChip(
                    selected = darkMode == DarkModeConfig.ON,
                    onClick = { onDarkModeChange(DarkModeConfig.ON) },
                    label = { Text(stringResource(R.string.settings_dark_mode_on)) },
                )
                FilterChip(
                    selected = darkMode == DarkModeConfig.OFF,
                    onClick = { onDarkModeChange(DarkModeConfig.OFF) },
                    label = { Text(stringResource(R.string.settings_dark_mode_off)) },
                )
            }
        }
    }
}

@Composable
private fun NmeaCard(
    nmeaEnabled: Boolean,
    onNmeaEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    GpsCard(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                GpsCardTitle(text = stringResource(R.string.settings_nmea_enabled))
                Spacer(modifier = Modifier.height(4.dp))
                GpsCardMeta(text = stringResource(R.string.settings_nmea_enabled_summary))
            }
            Switch(
                checked = nmeaEnabled,
                onCheckedChange = onNmeaEnabledChange,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SnapshotCard(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    GpsCard(modifier = modifier) {
        Column {
            GpsCardTitle(text = stringResource(R.string.settings_snapshot_section))

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_auto_save),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.settings_auto_save_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.autoSaveEnabled,
                    onCheckedChange = { enabled ->
                        onSettingsChange(settings.copy(autoSaveEnabled = enabled))
                    },
                )
            }

            if (settings.autoSaveEnabled) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.settings_snapshot_interval),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppSettings.INTERVAL_OPTIONS_MS.forEach { intervalMs ->
                        FilterChip(
                            selected = settings.snapshotIntervalMs == intervalMs,
                            onClick = {
                                onSettingsChange(settings.copy(snapshotIntervalMs = intervalMs))
                            },
                            label = { Text(intervalLabel(intervalMs)) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.settings_max_snapshots),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppSettings.MAX_SNAPSHOTS_OPTIONS.forEach { count ->
                        FilterChip(
                            selected = settings.maxSnapshots == count,
                            onClick = {
                                onSettingsChange(settings.copy(maxSnapshots = count))
                            },
                            label = {
                                Text(stringResource(R.string.settings_count_format, count))
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.settings_retention_days),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppSettings.RETENTION_DAYS_OPTIONS.forEach { days ->
                        FilterChip(
                            selected = settings.retentionDays == days,
                            onClick = {
                                onSettingsChange(settings.copy(retentionDays = days))
                            },
                            label = {
                                Text(stringResource(R.string.settings_days_format, days))
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun intervalLabel(intervalMs: Long): String =
    when (intervalMs) {
        30_000L -> stringResource(R.string.settings_interval_30s)
        60_000L -> stringResource(R.string.settings_interval_1min)
        120_000L -> stringResource(R.string.settings_interval_2min)
        300_000L -> stringResource(R.string.settings_interval_5min)
        else -> "${intervalMs / 1000}s"
    }
