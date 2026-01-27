package com.wayy.ui.components.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wayy.ui.components.glass.GlassCard
import com.wayy.ui.theme.WayyColors

/**
 * ETA card displaying time and distance to destination
 *
 * @param eta Formatted ETA string (e.g., "12 min")
 * @param remainingDistance Formatted distance (e.g., "3.2 mi")
 * @param modifier Modifier for the card
 */
@Composable
fun ETACard(
    eta: String,
    remainingDistance: String,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Time section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = "Time",
                    tint = WayyColors.PrimaryLime,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = eta,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "ETA",
                    color = WayyColors.TextSecondary,
                    fontSize = 11.sp
                )
            }

            // Vertical divider
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(WayyColors.GlassBorder)
            )

            // Distance section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Route,
                    contentDescription = "Distance",
                    tint = WayyColors.PrimaryCyan,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = remainingDistance,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Remaining",
                    color = WayyColors.TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * Compact ETA display for minimal UI
 */
@Composable
fun ETACompact(
    eta: String,
    distance: String,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AccessTime,
                contentDescription = null,
                tint = WayyColors.PrimaryLime,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = eta,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "•",
                color = WayyColors.TextSecondary
            )
            Text(
                text = distance,
                color = WayyColors.TextSecondary,
                fontSize = 14.sp
            )
        }
    }
}
