package com.example.gpstest.ui.screens.agps

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.gpstest.domain.model.AGpsInjectionRecord
import com.example.gpstest.domain.model.AGpsSettings
import com.example.gpstest.ui.components.AGpsStatusCard
import com.example.gpstest.ui.components.GpsCard
import com.example.gpstest.ui.components.GpsCardTone
import com.example.gpstest.viewmodel.AGpsUiState
import com.example.gpstest.viewmodel.AGpsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val INTERVAL_HOURS = listOf(1, 6, 12, 24)

@OptIn(ExperimentalMaterial3Api::class)
// 4 个区域：状态卡片 → 自动更新配置 → 手动操作按钮 → 注入历史（含验证结果卡片）
@Composable
fun AGpsManagerScreen(
    viewModel: AGpsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val status by viewModel.status.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val history by viewModel.injectionHistory.collectAsState()
    val validationResult by viewModel.validationResult.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val dismissLabel = stringResource(R.string.dismiss)

    val importLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
        ) { uri: Uri? ->
            uri?.let { viewModel.importAndInject(it) }
        }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is AGpsUiState.Success -> {
                snackbarHostState.showSnackbar(message = state.message)
                viewModel.clearMessage()
            }
            is AGpsUiState.Error -> {
                snackbarHostState.showSnackbar(
                    message = state.message,
                    actionLabel = dismissLabel,
                )
                viewModel.clearMessage()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.agps_manager)) },
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
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
                AGpsStatusCard(status = status)
            }

            item {
                AutoUpdateCard(
                    settings = settings,
                    onSettingsChange = { viewModel.updateSettings(it) },
                )
            }

            item {
                ManualActionsCard(
                    onDownloadClick = { viewModel.downloadAndInject() },
                    onImportClick = { importLauncher.launch("*/*") },
                    onValidateSourceClick = {
                        viewModel.validateCurrentSource()
                    },
                    onTimeClick = { viewModel.injectTime() },
                    onClearClick = { viewModel.clearApsData() },
                    isLoading = uiState is AGpsUiState.Downloading || uiState is AGpsUiState.Injecting,
                )
            }

            validationResult?.let { result ->
                item {
                    ValidationResultCard(
                        result = result,
                        onDismiss = { viewModel.clearValidationResult() },
                    )
                }
            }

            if (history.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.injection_history),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        TextButton(onClick = { viewModel.clearInjectionHistory() }) {
                            Text(stringResource(R.string.agps_clear_history))
                        }
                    }
                }

                items(history) { record ->
                    HistoryItem(record = record)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AutoUpdateCard(
    settings: AGpsSettings,
    onSettingsChange: (AGpsSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    var urlText by remember { mutableStateOf(settings.downloadUrl) }
    var urlError by remember { mutableStateOf(false) }

    LaunchedEffect(settings.downloadUrl) {
        urlText = settings.downloadUrl
        urlError = false
    }

    GpsCard(modifier = modifier) {
        Column {
            Text(
                text = stringResource(R.string.auto_update),
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.enable_auto_update),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.auto_update_desc, settings.updateIntervalHours),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Switch(
                    checked = settings.autoUpdateEnabled,
                    onCheckedChange = { enabled ->
                        onSettingsChange(settings.copy(autoUpdateEnabled = enabled))
                    },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.agps_interval_hours),
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                INTERVAL_HOURS.forEach { hours ->
                    FilterChip(
                        selected = settings.updateIntervalHours == hours,
                        onClick = {
                            onSettingsChange(settings.copy(updateIntervalHours = hours))
                        },
                        label = { Text(hours.toString()) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = urlText,
                onValueChange = {
                    urlText = it
                    urlError = false
                },
                label = { Text(stringResource(R.string.agps_download_url)) },
                isError = urlError,
                supportingText =
                    if (urlError) {
                        { Text(stringResource(R.string.agps_url_invalid)) }
                    } else {
                        null
                    },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val trimmed = urlText.trim()
                    if (isValidDownloadUrl(trimmed)) {
                        urlError = false
                        onSettingsChange(settings.copy(downloadUrl = trimmed))
                    } else {
                        urlError = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.agps_save_url))
            }
        }
    }
}

@Composable
private fun ManualActionsCard(
    onDownloadClick: () -> Unit,
    onImportClick: () -> Unit,
    onValidateSourceClick: () -> Unit,
    onTimeClick: () -> Unit,
    onClearClick: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    GpsCard(modifier = modifier) {
        Column {
            Text(
                text = stringResource(R.string.manual_actions),
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onDownloadClick,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.download_now))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onImportClick,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.import_file))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onValidateSourceClick,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.agps_validate_source))
                }

                OutlinedButton(
                    onClick = onTimeClick,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.sync_time))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onClearClick,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.clear_agps_data))
            }
        }
    }
}

@Composable
private fun ValidationResultCard(
    result: com.example.gpstest.domain.repository.FileValidationResult,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GpsCard(
        modifier = modifier,
        tone = if (result.isValid) GpsCardTone.DEFAULT else GpsCardTone.ERROR,
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text =
                        stringResource(
                            if (result.isValid) {
                                R.string.agps_validation_ok
                            } else {
                                R.string.agps_validation_fail
                            },
                        ),
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.dismiss))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = result.summary,
                style = MaterialTheme.typography.bodyMedium,
            )

            if (!result.isValid && result.errorMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.agps_error_prefix, result.errorMessage),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }

            if (result.details != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = result.details,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun HistoryItem(
    record: AGpsInjectionRecord,
    modifier: Modifier = Modifier,
) {
    GpsCard(
        modifier = modifier,
        tone = if (record.success) GpsCardTone.DEFAULT else GpsCardTone.ERROR,
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatTimestamp(record.timestamp),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text =
                        if (record.success) {
                            stringResource(R.string.success)
                        } else {
                            stringResource(R.string.failed)
                        },
                    style = MaterialTheme.typography.labelMedium,
                    color =
                        if (record.success) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                )
            }

            record.errorMessage?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun isValidDownloadUrl(url: String): Boolean {
    if (url.isBlank()) return false
    return url.startsWith("http://") ||
        url.startsWith("https://") ||
        url.startsWith("file://")
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
