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
import androidx.compose.ui.graphics.Brush
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
    isApproaching: Boolean = false,
    modifier: Modifier = Modifier
) {
    val iconRotation by animateFloatAsState(
        targetValue = getDirectionRotation(direction),
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "turn_rotation"
    )

    val backgroundColor by animateColorAsState(
        targetValue = if (isApproaching) WayyColors.PrimaryLime else WayyColors.BgSecondary,
        animationSpec = tween(300),
        label = "bg_color"
    )

    val textColor by animateColorAsState(
        targetValue = if (isApproaching) WayyColors.BgPrimary else Color.White,
        animationSpec = tween(300),
        label = "text_color"
    )

    val pulseAlpha by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    GlassCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor.copy(alpha = 0.95f))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        if (isApproaching) WayyColors.BgPrimary.copy(alpha = pulseAlpha)
                        else WayyColors.PrimaryLime.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getDirectionIcon(direction),
                    contentDescription = instruction,
                    tint = if (isApproaching) WayyColors.PrimaryLime else textColor,
                    modifier = Modifier
                        .size(44.dp)
                        .graphicsLayer { rotationZ = iconRotation }
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = distanceText,
                        color = textColor,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = getDirectionText(direction),
                        color = if (isApproaching) textColor.copy(alpha = 0.8f) else WayyColors.TextSecondary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                if (streetName.isNotEmpty()) {
                    Text(
                        text = streetName,
                        color = if (isApproaching) textColor.copy(alpha = 0.7f) else WayyColors.TextSecondary,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isApproaching) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(WayyColors.PrimaryLime.copy(alpha = pulseAlpha))
                )
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
                WayyColors.BgSecondary.copy(alpha = 0.9f),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = getDirectionIcon(direction),
            contentDescription = null,
            tint = WayyColors.PrimaryLime,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer { rotationZ = iconRotation }
        )
        Text(
            text = distanceText,
            color = Color.White,
            fontSize = 16.sp,
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
