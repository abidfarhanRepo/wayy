package com.wayy.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wayy.ui.components.glass.GlassIconButton
import com.wayy.ui.theme.WayyColors

@Composable
fun TopBar(
    onMenuClick: () -> Unit,
    onSettingsClick: () -> Unit,
    isScanningActive: Boolean = false,
    gpsAccuracyMeters: Float? = null,
    showSettings: Boolean = true,
    isNavigating: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(WayyColors.Surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassIconButton(
            onClick = onMenuClick,
            icon = Icons.Default.Menu,
            contentDescription = "Menu"
        )

        Spacer(modifier = Modifier.weight(1f))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isNavigating) "Navigating" else "wayy",
                color = WayyColors.Primary,
                fontSize = 18.sp,
                fontWeight = if (isNavigating) FontWeight.Medium else FontWeight.Bold
            )

            if (isScanningActive) {
                Spacer(modifier = Modifier.width(8.dp))
                ScanningDot()
            }

            Spacer(modifier = Modifier.width(16.dp))
            GpsIndicator(accuracyMeters = gpsAccuracyMeters)
        }

        Spacer(modifier = Modifier.weight(1f))

        if (showSettings) {
            GlassIconButton(
                onClick = onSettingsClick,
                icon = Icons.Default.Settings,
                contentDescription = "Settings"
            )
        }
    }
}

@Composable
private fun ScanningDot() {
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(WayyColors.Accent, CircleShape)
    )
}

@Composable
private fun GpsIndicator(accuracyMeters: Float?) {
    val (label, color) = when {
        accuracyMeters == null -> "GPS --" to WayyColors.PrimaryMuted
        accuracyMeters <= 5f -> "GPS ${accuracyMeters.toInt()}m" to WayyColors.Success
        accuracyMeters <= 15f -> "GPS ${accuracyMeters.toInt()}m" to WayyColors.Warning
        else -> "GPS ${accuracyMeters.toInt()}m" to WayyColors.Error
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Text(
            text = label,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
