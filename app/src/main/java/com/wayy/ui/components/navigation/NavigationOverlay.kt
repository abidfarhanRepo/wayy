package com.wayy.ui.components.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.TurnSharpLeft
import androidx.compose.material.icons.filled.TurnSharpRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wayy.ui.components.glass.GlassCard
import com.wayy.ui.theme.WayyColors

/**
 * Navigation direction types
 */
enum class Direction {
    STRAIGHT,
    LEFT,
    RIGHT,
    U_TURN,
    SLIGHT_LEFT,
    SLIGHT_RIGHT
}

/**
 * Navigation overlay showing current direction and distance
 *
 * @param direction Current navigation direction
 * @param distance Distance to next turn (in meters or formatted string)
 * @param streetName Name of current/upcoming street
 * @param modifier Modifier for the overlay
 */
@Composable
fun NavigationOverlay(
    direction: Direction,
    distance: String,
    streetName: String,
    modifier: Modifier = Modifier
) {
    val iconRotation by animateFloatAsState(
        targetValue = when (direction) {
            Direction.STRAIGHT -> 0f
            Direction.LEFT -> -90f
            Direction.RIGHT -> 90f
            Direction.U_TURN -> 180f
            Direction.SLIGHT_LEFT -> -45f
            Direction.SLIGHT_RIGHT -> 45f
        },
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = 400f
        ),
        label = "direction_rotation"
    )

    val directionIcon = when (direction) {
        Direction.STRAIGHT -> Icons.Default.ArrowUpward
        Direction.LEFT -> Icons.Default.TurnSharpLeft
        Direction.RIGHT -> Icons.Default.TurnSharpRight
        Direction.U_TURN -> Icons.Default.ArrowBack
        Direction.SLIGHT_LEFT -> Icons.Default.TurnSharpLeft
        Direction.SLIGHT_RIGHT -> Icons.Default.TurnSharpRight
    }

    val instructionText = when (direction) {
        Direction.STRAIGHT -> "Continue straight"
        Direction.LEFT -> "Turn left"
        Direction.RIGHT -> "Turn right"
        Direction.U_TURN -> "Make a U-turn"
        Direction.SLIGHT_LEFT -> "Bear left"
        Direction.SLIGHT_RIGHT -> "Bear right"
    }

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Direction icon
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(
                        color = WayyColors.PrimaryLime.copy(alpha = 0.2f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = directionIcon,
                    contentDescription = instructionText,
                    tint = WayyColors.PrimaryLime,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Direction info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = instructionText,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (streetName.isNotEmpty()) {
                    Text(
                        text = streetName,
                        color = WayyColors.TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }

            // Distance
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = distance,
                    color = WayyColors.PrimaryLime,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Large direction arrow for minimal navigation view
 */
@Composable
fun DirectionArrow(
    direction: Direction,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue = when (direction) {
            Direction.STRAIGHT -> 0f
            Direction.LEFT -> -90f
            Direction.RIGHT -> 90f
            Direction.U_TURN -> 180f
            Direction.SLIGHT_LEFT -> -45f
            Direction.SLIGHT_RIGHT -> 45f
        },
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = 500f
        ),
        label = "arrow_rotation"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.ArrowForward,
            contentDescription = null,
            tint = WayyColors.PrimaryLime,
            modifier = Modifier
                .size(80.dp)
                .padding(8.dp)
        )
    }
}
