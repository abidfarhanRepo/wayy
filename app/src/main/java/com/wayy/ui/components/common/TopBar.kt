package com.wayy.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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

/**
 * Top app bar with menu, title, and settings
 *
 * @param onMenuClick Callback for menu button
 * @param onSettingsClick Callback for settings button
 * @param isScanningActive Whether scanning is currently active
 * @param gpsAccuracyMeters Latest GPS accuracy estimate (meters)
 * @param showSettings Whether to show settings button
 * @param modifier Modifier for the bar
 */
@Composable
fun TopBar(
    onMenuClick: () -> Unit,
    onSettingsClick: () -> Unit,
    isScanningActive: Boolean = false,
    gpsAccuracyMeters: Float? = null,
    showSettings: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(WayyColors.BgPrimary.copy(alpha = 0.85f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                text = "MapPulse",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            if (isScanningActive) {
                Spacer(modifier = Modifier.width(6.dp))
                ScanningIndicator()
            }

            Spacer(modifier = Modifier.width(8.dp))
            GpsStrengthIndicator(accuracyMeters = gpsAccuracyMeters)
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

/**
 * Animated scanning indicator
 */
@Composable
private fun ScanningIndicator() {
    androidx.compose.animation.core.Animatable(0f)
    androidx.compose.animation.core.rememberInfiniteTransition()
    // TODO: Add pulsing dot animation
}

@Composable
private fun GpsStrengthIndicator(
    accuracyMeters: Float?
) {
    val (label, color) = when {
        accuracyMeters == null -> "GPS --" to WayyColors.TextSecondary
        accuracyMeters <= 5f -> "GPS Strong" to WayyColors.Success
        accuracyMeters <= 15f -> "GPS Ok" to WayyColors.Warning
        else -> "GPS Weak" to WayyColors.Error
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color = color, shape = CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = WayyColors.TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
