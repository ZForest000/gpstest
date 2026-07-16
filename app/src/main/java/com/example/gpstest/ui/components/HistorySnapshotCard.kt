package com.example.gpstest.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.gpstest.R
import com.example.gpstest.domain.model.SatelliteHistoryEntry
import com.example.gpstest.domain.model.SatelliteHistorySnapshot
import java.util.Locale

@Composable
fun HistorySnapshotCard(
    snapshot: SatelliteHistorySnapshot,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
) {
    var expanded by remember(snapshot.timestamp) { mutableStateOf(initiallyExpanded) }
    val entries = remember(snapshot.entriesJson) { snapshot.getEntries() }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                ).padding(12.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatMillisToDateTime(snapshot.timestamp),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.signal_strength_avg, snapshot.averageSignalStrength.toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = getSignalColor(snapshot.averageSignalStrength),
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription =
                    stringResource(
                        if (expanded) R.string.history_collapse else R.string.history_expand,
                    ),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatItemCompact(
                label = stringResource(R.string.used_in_fix),
                value = snapshot.usedInFixCount.toString(),
            )
            StatItemCompact(
                label = stringResource(R.string.visible),
                value = snapshot.visibleCount.toString(),
            )
            StatItemCompact(
                label = stringResource(R.string.total),
                value = entries.size.toString(),
            )
        }

        if (snapshot.hasLocation || snapshot.pdop != null || snapshot.ttffMs != null) {
            Spacer(modifier = Modifier.height(8.dp))
            QualitySummaryRow(snapshot)
        }

        AnimatedVisibility(visible = expanded) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                if (entries.isEmpty()) {
                    Text(
                        text = stringResource(R.string.history_no_entries),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.history_entries_title, entries.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    entries
                        .sortedWith(
                            compareByDescending<SatelliteHistoryEntry> { it.usedInFix }
                                .thenByDescending { it.cn0DbHz },
                        ).forEach { entry ->
                            EntryRow(entry = entry)
                        }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(onClick = onShare) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = stringResource(R.string.history_share_snapshot),
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.history_delete_snapshot),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QualitySummaryRow(snapshot: SatelliteHistorySnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (snapshot.hasLocation) {
            Text(
                text =
                    stringResource(
                        R.string.history_location_line,
                        String.format(Locale.US, "%.5f", snapshot.latitude),
                        String.format(Locale.US, "%.5f", snapshot.longitude),
                        snapshot.accuracy?.let { String.format(Locale.US, "%.1f", it) } ?: "—",
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (snapshot.pdop != null) {
            Text(
                text =
                    stringResource(
                        R.string.history_dop_line,
                        String.format(Locale.US, "%.1f", snapshot.pdop),
                        String.format(Locale.US, "%.1f", snapshot.hdop ?: 0.0),
                        String.format(Locale.US, "%.1f", snapshot.vdop ?: 0.0),
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (snapshot.ttffMs != null) {
            Text(
                text = stringResource(R.string.history_ttff_line, snapshot.ttffMs / 1000.0),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EntryRow(
    entry: SatelliteHistoryEntry,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${entry.getDisplayName()}-${entry.svid}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1.2f),
        )
        Text(
            text = stringResource(R.string.signal_strength_format, entry.cn0DbHz.toInt()),
            style = MaterialTheme.typography.bodySmall,
            color = getSignalColor(entry.cn0DbHz),
            modifier = Modifier.weight(1f),
        )
        Text(
            text =
                stringResource(
                    if (entry.usedInFix) R.string.used_in_fix_yes else R.string.used_in_fix_no,
                ),
            style = MaterialTheme.typography.labelSmall,
            color =
                if (entry.usedInFix) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatItemCompact(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
