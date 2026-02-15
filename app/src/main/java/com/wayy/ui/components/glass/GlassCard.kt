package com.wayy.ui.components.glass

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wayy.ui.theme.WayyColors

/**
 * Glass morphic card with frosted glass effect
 *
 * @param modifier Modifier for the card
 * @param borderColor Optional border color
 * @param content Card content
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color = WayyColors.GlassBorder,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = WayyColors.GlassLight
        ),
        border = BorderStroke(
            width = 1.dp,
            color = borderColor
        ),
        content = content
    )
}

/**
 * Glass card with darker background for elevated content
 */
@Composable
fun GlassCardElevated(
    modifier: Modifier = Modifier,
    borderColor: Color = WayyColors.GlassBorder,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = WayyColors.GlassMedium
        ),
        border = BorderStroke(
            width = 1.dp,
            color = borderColor
        ),
        content = content
    )
}

/**
 * Minimal glass panel for subtle backgrounds
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = WayyColors.GlassLight.copy(alpha = 0.85f)
        ),
        border = null,
        content = content
    )
}
