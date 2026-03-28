package com.example.gpstest.ui.screens.agps

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.gpstest.R
import com.example.gpstest.domain.model.AGpsInjectionRecord
import com.example.gpstest.domain.model.AGpsSettings
import com.example.gpstest.ui.components.AGpsStatusCard
import com.example.gpstest.viewmodel.AGpsUiState
import com.example.gpstest.viewmodel.AGpsViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AGpsManagerScreen(
    viewModel: AGpsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val status by viewModel.status.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val history by viewModel.injectionHistory.collectAsState()
    val validationResult by viewModel.validationResult.collectAsState()

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.injectFromFile(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.agps_manager)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                AGpsStatusCard(status = status)
            }

            item {
                AutoUpdateCard(
                    settings = settings,
                    onSettingsChange = { viewModel.updateSettings(it) }
                )
            }

            item {
                ManualActionsCard(
                    onDownloadClick = { viewModel.downloadAndInject() },
                    onFileClick = { 
                        fileLauncher.launch(arrayOf("*/*"))
                    },
                    onValidateSourceClick = {
                        viewModel.validateCurrentSource()
                    },
                    onTimeClick = { viewModel.injectTime() },
                    onClearClick = { viewModel.clearApsData() },
                    isLoading = uiState is AGpsUiState.Downloading || uiState is AGpsUiState.Injecting
                )
            }

            validationResult?.let { result ->
                item {
                    ValidationResultCard(
                        result = result,
                        onDismiss = { viewModel.clearValidationResult() }
                    )
                }
            }

            if (history.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.injection_history),
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                items(history) { record ->
                    HistoryItem(record = record)
                }
            }
        }

        when (val state = uiState) {
            is AGpsUiState.Success -> {
                LaunchedEffect(state) {
                    delay(2000)
                    viewModel.clearMessage()
                }
                Snackbar(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(state.message)
                }
            }
            is AGpsUiState.Error -> {
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearMessage() }) {
                            Text(stringResource(R.string.dismiss))
                        }
                    }
                ) {
                    Text(state.message)
                }
            }
            else -> {}
        }
    }
}

@Composable
private fun AutoUpdateCard(
    settings: AGpsSettings,
    onSettingsChange: (AGpsSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.auto_update),
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.enable_auto_update),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.auto_update_desc, settings.updateIntervalHours),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Switch(
                    checked = settings.autoUpdateEnabled,
                    onCheckedChange = { enabled ->
                        onSettingsChange(settings.copy(autoUpdateEnabled = enabled))
                    }
                )
            }
        }
    }
}

@Composable
private fun ManualActionsCard(
    onDownloadClick: () -> Unit,
    onFileClick: () -> Unit,
    onValidateSourceClick: () -> Unit,
    onTimeClick: () -> Unit,
    onClearClick: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.manual_actions),
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onDownloadClick,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.download_now))
                    }
                }
                
                OutlinedButton(
                    onClick = onFileClick,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.import_file))
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onValidateSourceClick,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("验证下载源")
                }
                
                OutlinedButton(
                    onClick = onTimeClick,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.sync_time))
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedButton(
                onClick = onClearClick,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
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
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (result.isValid) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (result.isValid) "验证成功" else "验证失败",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (result.isValid)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onErrorContainer
                )
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = result.summary,
                style = MaterialTheme.typography.bodyMedium
            )
            
            if (!result.isValid && result.errorMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "错误: ${result.errorMessage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            
            if (result.details != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = result.details,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun HistoryItem(
    record: AGpsInjectionRecord,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (record.success) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTimestamp(record.timestamp),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = if (record.success) stringResource(R.string.success) 
                           else stringResource(R.string.failed),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (record.success) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.error
                )
            }
            
            record.errorMessage?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
