package com.wayy.ui.components.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wayy.ui.components.glass.GlassIconButton
import com.wayy.ui.components.glass.GlassPanel
import com.wayy.ui.theme.WayyColors

/**
 * Quick actions bar with primary navigation actions
 *
 * @param isNavigating Whether navigation is active
 * @param isARActive Whether AR mode is active
 * @param is3DActive Whether 3D view is active
 * @param onMenuClick Open menu callback
 * @param onNavigateToggle Toggle navigation callback
 * @param onARModeToggle Toggle AR mode callback
 * @param on3DViewToggle Toggle 3D view callback
 * @param modifier Modifier for the bar
 */
@Composable
fun QuickActionsBar(
    isNavigating: Boolean = false,
    isARActive: Boolean = false,
    is3DActive: Boolean = false,
    onMenuClick: () -> Unit = {},
    onNavigateToggle: () -> Unit = {},
    onARModeToggle: () -> Unit = {},
    on3DViewToggle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    GlassPanel(modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassIconButton(
                onClick = onMenuClick,
                icon = Icons.Default.Search,
                contentDescription = "Search"
            )

            GlassIconButton(
                onClick = onARModeToggle,
                icon = if (isARActive) Icons.Default.ViewInAr else Icons.Outlined.ViewInAr,
                contentDescription = "AR mode",
                active = isARActive,
                activeColor = WayyColors.PrimaryPurple
            )

            if (isNavigating) {
                GlassIconButton(
                    onClick = onNavigateToggle,
                    icon = Icons.Default.Close,
                    contentDescription = "Stop navigation",
                    active = true,
                    activeColor = WayyColors.Error
                )
            } else {
                GlassIconButton(
                    onClick = on3DViewToggle,
                    icon = Icons.Default.Explore,
                    contentDescription = "3D view",
                    active = is3DActive,
                    activeColor = WayyColors.PrimaryOrange
                )
            }
        }
    }
}
