package com.wayy.ui.components.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.wayy.ui.components.glass.GlassButton
import com.wayy.ui.theme.WayyColors

/**
 * Quick actions bar with primary navigation actions
 *
 * @param isNavigating Whether navigation is active
 * @param isScanning Whether scanning mode is active
 * @param isARActive Whether AR mode is active
 * @param is3DActive Whether 3D view is active
 * @param onNavigateToggle Toggle navigation callback
 * @param onScanToggle Toggle scan callback
 * @param onARModeToggle Toggle AR mode callback
 * @param on3DViewToggle Toggle 3D view callback
 * @param modifier Modifier for the bar
 */
@Composable
fun QuickActionsBar(
    isNavigating: Boolean = false,
    isScanning: Boolean = false,
    isARActive: Boolean = false,
    is3DActive: Boolean = false,
    onNavigateToggle: () -> Unit = {},
    onScanToggle: () -> Unit = {},
    onARModeToggle: () -> Unit = {},
    on3DViewToggle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GlassButton(
            onClick = onNavigateToggle,
            icon = Icons.Default.Navigation,
            label = "Navigate",
            active = isNavigating,
            activeColor = WayyColors.PrimaryLime,
            modifier = Modifier.weight(1f)
        )

        GlassButton(
            onClick = onScanToggle,
            icon = if (isScanning) Icons.Default.QrCodeScanner else Icons.Outlined.QrCodeScanner,
            label = "Scan",
            active = isScanning,
            activeColor = WayyColors.PrimaryCyan,
            modifier = Modifier.weight(1f)
        )

        GlassButton(
            onClick = onARModeToggle,
            icon = if (isARActive) Icons.Default.ViewInAr else Icons.Outlined.ViewInAr,
            label = "AR",
            active = isARActive,
            activeColor = WayyColors.PrimaryPurple,
            modifier = Modifier.weight(1f)
        )

        GlassButton(
            onClick = on3DViewToggle,
            icon = Icons.Default.Explore,
            label = "3D",
            active = is3DActive,
            activeColor = WayyColors.PrimaryOrange,
            modifier = Modifier.weight(1f)
        )
    }
}
