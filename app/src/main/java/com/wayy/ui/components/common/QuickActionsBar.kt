package com.wayy.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
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
import com.wayy.ui.theme.WayyColors

@Composable
fun QuickActionsBar(
    isNavigating: Boolean = false,
    isRecording: Boolean = false,
    onMenuClick: () -> Unit = {},
    onNavigateToggle: () -> Unit = {},
    onRecordToggle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(WayyColors.Surface)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionButton(
                icon = Icons.Default.Search,
                label = "Search",
                onClick = onMenuClick,
                active = false
            )

            if (isNavigating) {
                ActionButton(
                    icon = if (isRecording) Icons.Default.Videocam else Icons.Default.Videocam,
                    label = if (isRecording) "Stop" else "Record",
                    onClick = onRecordToggle,
                    active = isRecording,
                    activeColor = WayyColors.Error
                )

                ActionButton(
                    icon = Icons.Default.Close,
                    label = "Stop",
                    onClick = onNavigateToggle,
                    active = true,
                    activeColor = WayyColors.Error
                )
            } else {
                ActionButton(
                    icon = Icons.Default.Explore,
                    label = "Navigate",
                    onClick = onNavigateToggle,
                    active = false
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    active: Boolean,
    activeColor: Color = WayyColors.Accent
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = if (active) activeColor else Color.Transparent,
            contentColor = if (active) Color.White else WayyColors.Primary
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.height(48.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
