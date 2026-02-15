package com.wayy.ui.components.camera

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.TurnSharpLeft
import androidx.compose.material.icons.filled.TurnSharpRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wayy.navigation.NavigationUtils
import com.wayy.ui.components.navigation.Direction
import com.wayy.ui.theme.WayyColors
import kotlin.math.absoluteValue

@Composable
fun TurnArrowOverlay(
    direction: Direction,
    distanceToTurnMeters: Double,
    deviceBearing: Float,
    turnBearing: Float,
    isApproaching: Boolean,
    modifier: Modifier = Modifier
) {
    val baseRotation = when (direction) {
        Direction.STRAIGHT -> 0f
        Direction.LEFT -> -90f
        Direction.RIGHT -> 90f
        Direction.U_TURN -> 180f
        Direction.SLIGHT_LEFT -> -45f
        Direction.SLIGHT_RIGHT -> 45f
    }

    val safeDeviceBearing = deviceBearing.coerceIn(0f, 360f)
    val safeTurnBearing = turnBearing.coerceIn(0f, 360f)
    
    val bearingDelta = if (safeTurnBearing > 0f) {
        NavigationUtils.bearingDifference(safeDeviceBearing, safeTurnBearing)
    } else {
        baseRotation
    }

    val arrowRotation by animateFloatAsState(
        targetValue = bearingDelta.coerceIn(-180f, 180f),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "ar_arrow_rotation"
    )

    val pulseAlpha = if (isApproaching) {
        val transition = rememberInfiniteTransition(label = "ar_pulse")
        transition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(600),
                repeatMode = RepeatMode.Reverse
            ),
            label = "ar_pulse_alpha"
        ).value
    } else {
        0.85f
    }

    val arrowIcon: ImageVector = when (direction) {
        Direction.STRAIGHT -> Icons.Default.ArrowUpward
        Direction.LEFT -> Icons.Default.TurnSharpLeft
        Direction.RIGHT -> Icons.Default.TurnSharpRight
        Direction.U_TURN -> Icons.Filled.ArrowBack
        Direction.SLIGHT_LEFT -> Icons.Default.TurnSharpLeft
        Direction.SLIGHT_RIGHT -> Icons.Default.TurnSharpRight
    }
    
    val safeDistance = distanceToTurnMeters.coerceAtLeast(0.0)
    val distanceText = NavigationUtils.formatDistance(safeDistance)

    androidx.compose.foundation.layout.Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = arrowIcon,
            contentDescription = "Turn direction",
            tint = WayyColors.Accent.copy(alpha = pulseAlpha),
            modifier = Modifier
                .size(64.dp)
                .rotate(arrowRotation)
                .scale(1.3f)
        )
        Text(
            text = distanceText,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
