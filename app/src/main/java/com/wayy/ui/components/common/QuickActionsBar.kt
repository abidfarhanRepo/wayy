package com.wayy.ui.components.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wayy.ui.theme.WayyColors

@Composable
fun QuickActionsBar(
    isNavigating: Boolean = false,
    onMenuClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onNavigateToggle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(WayyColors.Surface.copy(alpha = 0.95f))
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionButton(
                icon = Icons.Default.Search,
                label = "Search",
                onClick = onSearchClick,
                isPrimary = true,
                isActive = false
            )

            if (isNavigating) {
                ActionButton(
                    icon = Icons.Default.Close,
                    label = "Stop Nav",
                    onClick = onNavigateToggle,
                    isPrimary = false,
                    isActive = true,
                    activeColor = WayyColors.Error
                )
            } else {
                ActionButton(
                    icon = Icons.Default.Navigation,
                    label = "Navigate",
                    onClick = onNavigateToggle,
                    isPrimary = true,
                    isActive = false
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    isPrimary: Boolean = false,
    isActive: Boolean = false,
    activeColor: Color = WayyColors.Accent
) {
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isActive -> activeColor
            isPrimary -> WayyColors.Accent.copy(alpha = 0.15f)
            else -> Color.Transparent
        },
        animationSpec = tween(200),
        label = "bg_color"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isActive -> activeColor.copy(alpha = 0.5f)
            isPrimary -> WayyColors.Accent.copy(alpha = 0.4f)
            else -> WayyColors.SurfaceVariant
        },
        animationSpec = tween(200),
        label = "border_color"
    )

    val contentColor = when {
        isActive -> Color.White
        isPrimary -> WayyColors.Accent
        else -> WayyColors.PrimaryMuted
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor, RoundedCornerShape(16.dp))
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(
                    color = if (isPrimary || isActive) contentColor.copy(alpha = 0.15f) else Color.Transparent,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = contentColor,
            fontSize = 11.sp,
            fontWeight = if (isPrimary || isActive) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}
