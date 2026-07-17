package com.example.gpstest.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.gpstest.R
import com.example.gpstest.domain.model.Constellation
import com.example.gpstest.domain.model.SatelliteSortMode

private val FilterConstellations =
    listOf(
        Constellation.GPS,
        Constellation.GLONASS,
        Constellation.GALILEO,
        Constellation.BEIDOU,
        Constellation.QZSS,
        Constellation.SBAS,
    )

@Composable
fun SatelliteFilterBar(
    selectedConstellations: Set<Constellation>,
    sortMode: SatelliteSortMode,
    svidQuery: String,
    frozen: Boolean,
    onConstellationToggle: (Constellation) -> Unit,
    onSortModeChange: (SatelliteSortMode) -> Unit,
    onSvidQueryChange: (String) -> Unit,
    onFrozenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val allSelected = selectedConstellations.isEmpty()

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.satellite_filter_title),
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(onClick = { onFrozenChange(!frozen) }) {
                Icon(
                    imageVector =
                        if (frozen) {
                            Icons.Default.PlayArrow
                        } else {
                            Icons.Default.Pause
                        },
                    contentDescription =
                        stringResource(
                            if (frozen) {
                                R.string.satellite_resume
                            } else {
                                R.string.satellite_freeze
                            },
                        ),
                )
            }
        }

        if (frozen) {
            Text(
                text = stringResource(R.string.satellite_frozen_banner),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item {
                FilterChip(
                    selected = allSelected,
                    onClick = { },
                    enabled = false,
                    label = { Text(stringResource(R.string.constellation_all)) },
                )
            }
            items(FilterConstellations) { constellation ->
                FilterChip(
                    selected = constellation in selectedConstellations,
                    onClick = { onConstellationToggle(constellation) },
                    label = { Text(constellation.shortName) },
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = sortMode == SatelliteSortMode.CN0_DESC,
                    onClick = { onSortModeChange(SatelliteSortMode.CN0_DESC) },
                    label = { Text(stringResource(R.string.satellite_sort_cn0)) },
                )
            }
            item {
                FilterChip(
                    selected = sortMode == SatelliteSortMode.ELEVATION_DESC,
                    onClick = { onSortModeChange(SatelliteSortMode.ELEVATION_DESC) },
                    label = { Text(stringResource(R.string.satellite_sort_elevation)) },
                )
            }
            item {
                FilterChip(
                    selected = sortMode == SatelliteSortMode.SVID_ASC,
                    onClick = { onSortModeChange(SatelliteSortMode.SVID_ASC) },
                    label = { Text(stringResource(R.string.satellite_sort_svid)) },
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = svidQuery,
            onValueChange = onSvidQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.satellite_search_svid)) },
            singleLine = true,
        )
    }
}
