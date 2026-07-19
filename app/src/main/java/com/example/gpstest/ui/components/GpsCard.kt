package com.example.gpstest.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class GpsCardDensity {
    STANDARD,
    COMPACT,
}

enum class GpsCardTone {
    DEFAULT,
    ERROR,
}

@Composable
fun GpsCard(
    modifier: Modifier = Modifier,
    density: GpsCardDensity = GpsCardDensity.STANDARD,
    tone: GpsCardTone = GpsCardTone.DEFAULT,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val contentPadding =
        when (density) {
            GpsCardDensity.STANDARD -> 16.dp
            GpsCardDensity.COMPACT -> 12.dp
        }
    val shape = RoundedCornerShape(12.dp)
    val colors =
        when (tone) {
            GpsCardTone.DEFAULT ->
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            GpsCardTone.ERROR ->
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
        }
    val elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    val cardModifier = modifier.fillMaxWidth()

    if (onClick == null) {
        Card(
            modifier = cardModifier,
            shape = shape,
            colors = colors,
            elevation = elevation,
        ) {
            GpsCardContent(contentPadding, content)
        }
    } else {
        Card(
            onClick = onClick,
            modifier = cardModifier,
            shape = shape,
            colors = colors,
            elevation = elevation,
        ) {
            GpsCardContent(contentPadding, content)
        }
    }
}

@Composable
private fun GpsCardContent(
    contentPadding: Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.padding(contentPadding), content = content)
}
