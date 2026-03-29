package com.example.gpstest.ui.screens.help

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gpstest.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.help_title)) },
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
                HelpSection(
                    title = stringResource(R.string.help_ttff_title),
                    icon = { Icon(Icons.Default.Info, contentDescription = null) }
                ) {
                    HelpText(stringResource(R.string.help_ttff_content))
                    HelpValueRow(label = stringResource(R.string.help_ttff_excellent), value = "< 10s", color = Color(0xFF4CAF50))
                    HelpValueRow(label = stringResource(R.string.help_ttff_good), value = "10-30s", color = Color(0xFF8BC34A))
                    HelpValueRow(label = stringResource(R.string.help_ttff_fair), value = "30-60s", color = Color(0xFFFFC107))
                    HelpValueRow(label = stringResource(R.string.help_ttff_poor), value = "> 60s", color = Color(0xFFFF9800))
                }
            }

            item {
                HelpSection(
                    title = stringResource(R.string.help_location_title),
                    icon = { Icon(Icons.Default.Info, contentDescription = null) }
                ) {
                    HelpText(stringResource(R.string.help_location_content))
                    HelpSubItem(title = stringResource(R.string.help_latitude_title), desc = stringResource(R.string.help_latitude_desc))
                    HelpSubItem(title = stringResource(R.string.help_longitude_title), desc = stringResource(R.string.help_longitude_desc))
                    HelpSubItem(title = stringResource(R.string.help_altitude_title), desc = stringResource(R.string.help_altitude_desc))
                    HelpSubItem(title = stringResource(R.string.help_accuracy_title), desc = stringResource(R.string.help_accuracy_desc))
                    HelpSubItem(title = stringResource(R.string.help_speed_title), desc = stringResource(R.string.help_speed_desc))
                    HelpSubItem(title = stringResource(R.string.help_bearing_title), desc = stringResource(R.string.help_bearing_desc))
                }
            }

            item {
                HelpSection(
                    title = stringResource(R.string.help_constellation_title),
                    icon = { Icon(Icons.Default.Info, contentDescription = null) }
                ) {
                    HelpText(stringResource(R.string.help_constellation_content))
                    HelpSubItem(title = "GPS", desc = stringResource(R.string.help_constellation_gps))
                    HelpSubItem(title = "GLONASS", desc = stringResource(R.string.help_constellation_glonass))
                    HelpSubItem(title = "Galileo", desc = stringResource(R.string.help_constellation_galileo))
                    HelpSubItem(title = stringResource(R.string.help_constellation_beidou_name), desc = stringResource(R.string.help_constellation_beidou))
                    HelpSubItem(title = "QZSS", desc = stringResource(R.string.help_constellation_qzss))
                }
            }

            item {
                HelpSection(
                    title = stringResource(R.string.help_dop_title),
                    icon = { Icon(Icons.Default.Info, contentDescription = null) }
                ) {
                    HelpText(stringResource(R.string.help_dop_content))
                    HelpSubItem(title = "PDOP", desc = stringResource(R.string.help_dop_pdop))
                    HelpSubItem(title = "HDOP", desc = stringResource(R.string.help_dop_hdop))
                    HelpSubItem(title = "VDOP", desc = stringResource(R.string.help_dop_vdop))
                    Spacer(modifier = Modifier.height(8.dp))
                    HelpText(stringResource(R.string.help_dop_quality))
                    HelpValueRow(label = stringResource(R.string.help_dop_excellent), value = "< 2", color = Color(0xFF4CAF50))
                    HelpValueRow(label = stringResource(R.string.help_dop_good), value = "2-5", color = Color(0xFFFFC107))
                    HelpValueRow(label = stringResource(R.string.help_dop_fair), value = "5-10", color = Color(0xFFFF9800))
                    HelpValueRow(label = stringResource(R.string.help_dop_poor), value = "> 10", color = Color(0xFFF44336))
                }
            }

            item {
                HelpSection(
                    title = stringResource(R.string.help_satellite_title),
                    icon = { Icon(Icons.Default.Info, contentDescription = null) }
                ) {
                    HelpText(stringResource(R.string.help_satellite_content))
                    HelpSubItem(title = stringResource(R.string.help_cn0_title), desc = stringResource(R.string.help_cn0_desc))
                    HelpValueRow(label = stringResource(R.string.help_cn0_excellent), value = "> 40 dB-Hz", color = Color(0xFF4CAF50))
                    HelpValueRow(label = stringResource(R.string.help_cn0_good), value = "30-40 dB-Hz", color = Color(0xFF8BC34A))
                    HelpValueRow(label = stringResource(R.string.help_cn0_fair), value = "20-30 dB-Hz", color = Color(0xFFFFC107))
                    HelpValueRow(label = stringResource(R.string.help_cn0_poor), value = "< 20 dB-Hz", color = Color(0xFFFF9800))
                    Spacer(modifier = Modifier.height(8.dp))
                    HelpSubItem(title = stringResource(R.string.help_elevation_title), desc = stringResource(R.string.help_elevation_desc))
                    HelpSubItem(title = stringResource(R.string.help_azimuth_title), desc = stringResource(R.string.help_azimuth_desc))
                    HelpSubItem(title = stringResource(R.string.help_ephemeris_title), desc = stringResource(R.string.help_ephemeris_desc))
                    HelpSubItem(title = stringResource(R.string.help_almanac_title), desc = stringResource(R.string.help_almanac_desc))
                }
            }

            item {
                HelpSection(
                    title = stringResource(R.string.help_clock_title),
                    icon = { Icon(Icons.Default.Info, contentDescription = null) }
                ) {
                    HelpText(stringResource(R.string.help_clock_content))
                    HelpSubItem(title = stringResource(R.string.help_clock_bias_title), desc = stringResource(R.string.help_clock_bias_desc))
                    HelpSubItem(title = stringResource(R.string.help_clock_drift_title), desc = stringResource(R.string.help_clock_drift_desc))
                }
            }

            item {
                HelpSection(
                    title = stringResource(R.string.help_agps_title),
                    icon = { Icon(Icons.Default.Info, contentDescription = null) }
                ) {
                    HelpText(stringResource(R.string.help_agps_content))
                    HelpSubItem(title = stringResource(R.string.help_agps_ephemeris_title), desc = stringResource(R.string.help_agps_ephemeris_desc))
                    HelpSubItem(title = stringResource(R.string.help_agps_almanac_title), desc = stringResource(R.string.help_agps_almanac_desc))
                }
            }

            item {
                HelpSection(
                    title = stringResource(R.string.help_satellite_detail_title),
                    icon = { Icon(Icons.Default.Info, contentDescription = null) }
                ) {
                    HelpText(stringResource(R.string.help_satellite_detail_content))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.help_raw_measurement_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    HelpSubItem(title = stringResource(R.string.help_carrier_freq_title), desc = stringResource(R.string.help_carrier_freq_desc))
                    HelpSubItem(title = stringResource(R.string.help_carrier_cycles_title), desc = stringResource(R.string.help_carrier_cycles_desc))
                    HelpSubItem(title = stringResource(R.string.help_doppler_shift_title), desc = stringResource(R.string.help_doppler_shift_desc))
                    HelpSubItem(title = stringResource(R.string.help_agc_title), desc = stringResource(R.string.help_agc_desc))
                    HelpSubItem(title = stringResource(R.string.help_baseband_cn0_title), desc = stringResource(R.string.help_baseband_cn0_desc))
                    HelpSubItem(title = stringResource(R.string.help_multipath_title), desc = stringResource(R.string.help_multipath_desc))
                }
            }
        }
    }
}

@Composable
private fun HelpSection(
    title: String,
    icon: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                icon()
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            content()
        }
    }
}

@Composable
private fun HelpText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun HelpSubItem(
    title: String,
    desc: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = desc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HelpValueRow(
    label: String,
    value: String,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color = color, shape = RoundedCornerShape(4.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
    }
}
