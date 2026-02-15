package com.wayy.ui.components.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wayy.ui.components.glass.GlassIconButton
import com.wayy.ui.components.glass.GlassPanel
import com.wayy.ui.theme.WayyColors

/**
 * Quick actions bar with primary navigation actions
 *
 * @param isNavigating Whether navigation is active
 * @param isRecording Whether recording is active
 * @param onMenuClick Open menu callback
 * @param onNavigateToggle Toggle navigation callback
 * @param onRecordToggle Toggle recording callback
 * @param modifier Modifier for the bar
 */
@Composable
fun QuickActionsBar(
    isNavigating: Boolean = false,
    isRecording: Boolean = false,
    onMenuClick: () -> Unit = {},
    onNavigateToggle: () -> Unit = {},
    onRecordToggle: () -> Unit = {},
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

            if (isNavigating) {
                // Record button
                GlassIconButton(
                    onClick = onRecordToggle,
                    icon = Icons.Default.Videocam,
                    contentDescription = if (isRecording) "Stop recording" else "Start recording",
                    active = isRecording,
                    activeColor = WayyColors.Error
                )

                GlassIconButton(
                    onClick = onNavigateToggle,
                    icon = Icons.Default.Close,
                    contentDescription = "Stop navigation",
                    active = true,
                    activeColor = WayyColors.Error
                )
            } else {
                GlassIconButton(
                    onClick = onNavigateToggle,
                    icon = Icons.Default.Explore,
                    contentDescription = "Start navigation",
                    active = false,
                    activeColor = WayyColors.PrimaryOrange
                )
            }
        }
    }
}
