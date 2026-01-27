package com.wayy.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wayy.ui.components.glass.GlassCard
import com.wayy.ui.theme.WayyColors

/**
 * Stat card displaying a value with icon and label
 *
 * @param icon Icon to display
 * @param value Main value to show
 * @param label Descriptive label
 * @param iconColor Color for icon background
 * @param modifier Modifier for the card
 */
@Composable
fun StatCard(
    icon: ImageVector,
    value: String,
    label: String,
    iconColor: Color = WayyColors.PrimaryLime,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = iconColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = value,
                    color = iconColor,
                    fontSize = 16.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Text(
                    text = label,
                    color = WayyColors.TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Pre-configured stat cards for common metrics
 */
@Composable
fun RoadQualityCard(
    quality: String,
    modifier: Modifier = Modifier
) {
    StatCard(
        icon = Icons.Default.Terrain,
        value = quality,
        label = "Road Quality",
        iconColor = WayyColors.PrimaryLime,
        modifier = modifier
    )
}

@Composable
fun GForceCard(
    gForce: String,
    modifier: Modifier = Modifier
) {
    StatCard(
        icon = Icons.Default.Speed,
        value = gForce,
        label = "G-Force",
        iconColor = WayyColors.PrimaryCyan,
        modifier = modifier
    )
}
