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

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color = WayyColors.SurfaceVariant,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = WayyColors.Surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = borderColor
        ),
        content = content
    )
}

@Composable
fun GlassCardElevated(
    modifier: Modifier = Modifier,
    borderColor: Color = WayyColors.SurfaceVariant,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = WayyColors.SurfaceVariant
        ),
        border = BorderStroke(
            width = 1.dp,
            color = borderColor
        ),
        content = content
    )
}

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = WayyColors.Surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = WayyColors.SurfaceVariant
        ),
        content = content
    )
}
