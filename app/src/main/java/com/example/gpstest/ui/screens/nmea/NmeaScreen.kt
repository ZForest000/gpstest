package com.example.gpstest.ui.screens.nmea

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.gpstest.PermissionState
import com.example.gpstest.R
import com.example.gpstest.data.local.NmeaExportHelper
import com.example.gpstest.domain.model.NmeaParsedSnapshot
import com.example.gpstest.domain.model.NmeaSentence
import com.example.gpstest.ui.components.GpsCard
import com.example.gpstest.ui.components.GpsCardDensity
import com.example.gpstest.ui.components.PermissionRequiredContent
import com.example.gpstest.viewmodel.NmeaUiState
import com.example.gpstest.viewmodel.NmeaViewModel
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NmeaScreen(
    viewModel: NmeaViewModel,
    permissionState: PermissionState,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val shareFailedText = stringResource(R.string.nmea_export_failed)
    val shareTitle = stringResource(R.string.nmea_share_chooser)

    val filtered =
        remember(uiState.sentences, uiState.typeFilter) {
            uiState.filteredSentences
        }

    LaunchedEffect(filtered.size, uiState.frozen) {
        if (!uiState.frozen && filtered.isNotEmpty()) {
            listState.animateScrollToItem(filtered.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.main_nmea_data_title)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.setFrozen(!uiState.frozen) },
                    ) {
                        Icon(
                            imageVector =
                                if (uiState.frozen) {
                                    Icons.Default.PlayArrow
                                } else {
                                    Icons.Default.Pause
                                },
                            contentDescription =
                                stringResource(
                                    if (uiState.frozen) R.string.nmea_resume else R.string.nmea_freeze,
                                ),
                        )
                    }
                    IconButton(onClick = { viewModel.clear() }) {
                        Icon(
                            imageVector = Icons.Default.ClearAll,
                            contentDescription = stringResource(R.string.nmea_clear),
                        )
                    }
                    IconButton(
                        onClick = {
                            val ok =
                                NmeaExportHelper.shareNmea(
                                    context = context,
                                    sentences = viewModel.getExportSentences(),
                                    chooserTitle = shareTitle,
                                )
                            if (!ok) {
                                Toast.makeText(context, shareFailedText, Toast.LENGTH_SHORT).show()
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(R.string.nmea_share),
                        )
                    }
                },
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        when {
            permissionState != PermissionState.GRANTED -> {
                PermissionRequiredContent(
                    permissionState = permissionState,
                    onRequestPermission = onRequestPermission,
                    onOpenAppSettings = onOpenAppSettings,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                )
            }
            !uiState.enabled -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.nmea_disabled),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                NmeaContent(
                    uiState = uiState,
                    filtered = filtered,
                    listState = listState,
                    onTypeFilterChange = viewModel::setTypeFilter,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NmeaContent(
    uiState: NmeaUiState,
    filtered: List<NmeaSentence>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onTypeFilterChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        StatusRow(uiState = uiState)
        Spacer(modifier = Modifier.height(8.dp))
        TypeFilterRow(
            typeFilter = uiState.typeFilter,
            typeCounts = uiState.typeCounts,
            onTypeFilterChange = onTypeFilterChange,
        )
        Spacer(modifier = Modifier.height(8.dp))
        ParsedCard(parsed = uiState.parsed)
        Spacer(modifier = Modifier.height(8.dp))

        if (filtered.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.nmea_no_data),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(
                    items = filtered,
                    key = { "${it.timestampMs}-${it.message}" },
                ) { sentence ->
                    Text(
                        text = formatSentence(sentence),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusRow(
    uiState: NmeaUiState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text =
                stringResource(
                    if (uiState.frozen) R.string.nmea_freeze else R.string.nmea_resume,
                ),
            style = MaterialTheme.typography.labelLarge,
            color =
                if (uiState.frozen) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
        )
        Text(
            text = stringResource(R.string.nmea_rate_format, uiState.rateHz),
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text =
                stringResource(
                    R.string.nmea_buffer_format,
                    uiState.bufferSize,
                    uiState.maxBufferSize,
                ),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeFilterRow(
    typeFilter: String,
    typeCounts: Map<String, Int>,
    onTypeFilterChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = typeFilter == NmeaUiState.FILTER_ALL,
            onClick = { onTypeFilterChange(NmeaUiState.FILTER_ALL) },
            label = { Text(stringResource(R.string.nmea_filter_all)) },
        )
        typeCounts.keys.sorted().forEach { type ->
            val count = typeCounts[type] ?: 0
            FilterChip(
                selected = typeFilter == type,
                onClick = { onTypeFilterChange(type) },
                label = {
                    Text(stringResource(R.string.nmea_type_count_format, type, count))
                },
            )
        }
    }
}

@Composable
private fun ParsedCard(
    parsed: NmeaParsedSnapshot,
    modifier: Modifier = Modifier,
) {
    val gga = parsed.gga
    val rmc = parsed.rmc
    if (gga == null && rmc == null) return

    GpsCard(
        modifier = modifier,
        density = GpsCardDensity.COMPACT,
    ) {
        Column {
            Text(
                text = stringResource(R.string.nmea_parsed_title),
                style = MaterialTheme.typography.titleSmall,
            )
            if (gga != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text =
                        stringResource(
                            R.string.nmea_parsed_gga,
                            gga.fixQuality,
                            gga.numSatellites,
                            gga.hdop?.let { String.format(Locale.US, "%.1f", it) } ?: "--",
                            gga.altitude?.let { String.format(Locale.US, "%.1f m", it) } ?: "--",
                        ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (rmc != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text =
                        stringResource(
                            R.string.nmea_parsed_rmc,
                            rmc.status?.toString() ?: "--",
                            rmc.sogKnots?.let { String.format(Locale.US, "%.1f", it) } ?: "--",
                            rmc.cogDegrees?.let { String.format(Locale.US, "%.1f", it) } ?: "--",
                        ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun formatSentence(sentence: NmeaSentence): String {
    val cal = Calendar.getInstance().apply { timeInMillis = sentence.timestampMs }
    val h = cal.get(Calendar.HOUR_OF_DAY)
    val m = cal.get(Calendar.MINUTE)
    val s = cal.get(Calendar.SECOND)
    val ms = cal.get(Calendar.MILLISECOND)
    return String.format(
        Locale.US,
        "[%02d:%02d:%02d.%03d] %s",
        h,
        m,
        s,
        ms,
        sentence.message.trim(),
    )
}
