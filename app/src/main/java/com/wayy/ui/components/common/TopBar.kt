package com.wayy.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
 * @param modifier Modifier for the bar
 */
@Composable
fun TopBar(
    onMenuClick: () -> Unit,
    onSettingsClick: () -> Unit,
    isScanningActive: Boolean = false,
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
                ScanningIndicator()
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        GlassIconButton(
            onClick = onSettingsClick,
            icon = Icons.Default.Settings,
            contentDescription = "Settings"
        )
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
