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
import com.wayy.ui.components.glass.GlassCardElevated
import com.wayy.ui.theme.WayyColors

@Composable
fun ETACard(
    eta: String,
    remainingDistance: String,
    modifier: Modifier = Modifier
) {
    GlassCardElevated(modifier = modifier) {
        Row(
            modifier = Modifier
                .background(WayyColors.SurfaceVariant)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = "Time",
                    tint = WayyColors.Accent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = eta,
                    color = WayyColors.Primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "ETA",
                    color = WayyColors.PrimaryMuted,
                    fontSize = 10.sp
                )
            }

            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(32.dp)
                    .clip(CircleShape)
                    .background(WayyColors.SurfaceVariant)
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Route,
                    contentDescription = "Distance",
                    tint = WayyColors.Accent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = remainingDistance,
                    color = WayyColors.Primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Remaining",
                    color = WayyColors.PrimaryMuted,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun ETACompact(
    eta: String,
    distance: String,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AccessTime,
                contentDescription = null,
                tint = WayyColors.Accent,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = eta,
                color = WayyColors.Primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "•",
                color = WayyColors.PrimaryMuted
            )
            Text(
                text = distance,
                color = WayyColors.PrimaryMuted,
                fontSize = 12.sp
            )
        }
    }
}
