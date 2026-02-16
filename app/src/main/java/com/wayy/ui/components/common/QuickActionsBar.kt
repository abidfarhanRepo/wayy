package com.wayy.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
    onSearchClick: () -> Unit = {},
    onNavigateToggle: () -> Unit = {},
    onRecordToggle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(WayyColors.Surface)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            QuickActionButton(
                icon = Icons.Default.Search,
                label = "Search",
                onClick = onSearchClick,
                isPrimary = true
            )

            if (isNavigating) {
                QuickActionButton(
                    icon = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                    label = if (isRecording) "Stop Rec" else "Record",
                    onClick = onRecordToggle,
                    isPrimary = false,
                    isActive = isRecording,
                    activeColor = WayyColors.Error
                )

                QuickActionButton(
                    icon = Icons.Default.Close,
                    label = "Stop Nav",
                    onClick = onNavigateToggle,
                    isPrimary = false,
                    isActive = true,
                    activeColor = WayyColors.Error
                )
            } else {
                QuickActionButton(
                    icon = Icons.Default.Navigation,
                    label = "Navigate",
                    onClick = onNavigateToggle,
                    isPrimary = true
                )
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    isPrimary: Boolean = false,
    isActive: Boolean = false,
    activeColor: Color = WayyColors.Accent
) {
    val backgroundColor = when {
        isActive -> activeColor
        isPrimary -> WayyColors.SurfaceVariant
        else -> Color.Transparent
    }
    
    val contentColor = when {
        isActive -> Color.White
        isPrimary -> WayyColors.Primary
        else -> WayyColors.PrimaryMuted
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = contentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
