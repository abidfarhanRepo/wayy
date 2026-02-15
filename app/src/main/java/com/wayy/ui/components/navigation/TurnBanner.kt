package com.wayy.ui.components.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wayy.ui.components.glass.GlassCard
import com.wayy.ui.theme.WayyColors

@Composable
fun TurnBanner(
    direction: Direction,
    distanceText: String,
    streetName: String,
    instruction: String,
    metricsText: String = "",
    isApproaching: Boolean = false,
    modifier: Modifier = Modifier
) {
    val iconRotation by animateFloatAsState(
        targetValue = getDirectionRotation(direction),
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "turn_rotation"
    )

    val backgroundColor by animateColorAsState(
        targetValue = if (isApproaching) WayyColors.SurfaceVariant else WayyColors.Surface,
        animationSpec = tween(300),
        label = "bg_color"
    )

    val accentColor by animateColorAsState(
        targetValue = if (isApproaching) WayyColors.Accent else WayyColors.PrimaryMuted,
        animationSpec = tween(300),
        label = "accent_color"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isApproaching) WayyColors.Accent.copy(alpha = 0.15f) else WayyColors.SurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getDirectionIcon(direction),
                    contentDescription = instruction,
                    tint = accentColor,
                    modifier = Modifier
                        .size(28.dp)
                        .graphicsLayer { rotationZ = iconRotation }
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = distanceText,
                        color = WayyColors.Primary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = getDirectionText(direction),
                        color = WayyColors.PrimaryMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                if (streetName.isNotEmpty()) {
                    Text(
                        text = streetName,
                        color = WayyColors.PrimaryMuted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (metricsText.isNotBlank()) {
                    Text(
                        text = metricsText,
                        color = WayyColors.PrimaryMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun CompactTurnIndicator(
    direction: Direction,
    distanceText: String,
    modifier: Modifier = Modifier
) {
    val iconRotation by animateFloatAsState(
        targetValue = getDirectionRotation(direction),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "compact_rotation"
    )

    Row(
        modifier = modifier
            .background(
                WayyColors.Surface,
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = getDirectionIcon(direction),
            contentDescription = null,
            tint = WayyColors.Accent,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer { rotationZ = iconRotation }
        )
        Text(
            text = distanceText,
            color = WayyColors.Primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun getDirectionRotation(direction: Direction): Float = when (direction) {
    Direction.STRAIGHT -> 0f
    Direction.LEFT -> -90f
    Direction.RIGHT -> 90f
    Direction.U_TURN -> 180f
    Direction.SLIGHT_LEFT -> -45f
    Direction.SLIGHT_RIGHT -> 45f
}

private fun getDirectionIcon(direction: Direction) = when (direction) {
    Direction.STRAIGHT -> Icons.Default.ArrowUpward
    Direction.LEFT -> Icons.Default.TurnSharpLeft
    Direction.RIGHT -> Icons.Default.TurnSharpRight
    Direction.U_TURN -> Icons.Default.UTurnLeft
    Direction.SLIGHT_LEFT -> Icons.Default.TurnSlightLeft
    Direction.SLIGHT_RIGHT -> Icons.Default.TurnSlightRight
}

private fun getDirectionText(direction: Direction) = when (direction) {
    Direction.STRAIGHT -> "Continue"
    Direction.LEFT -> "Turn left"
    Direction.RIGHT -> "Turn right"
    Direction.U_TURN -> "U-turn"
    Direction.SLIGHT_LEFT -> "Bear left"
    Direction.SLIGHT_RIGHT -> "Bear right"
}
