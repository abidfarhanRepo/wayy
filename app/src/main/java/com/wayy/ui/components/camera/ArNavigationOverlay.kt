package com.wayy.ui.components.camera

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TurnSharpLeft
import androidx.compose.material.icons.filled.TurnSharpRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wayy.ml.MlDetection
import com.wayy.ml.LanePoint
import com.wayy.navigation.NavigationUtils
import com.wayy.ui.components.navigation.Direction
import com.wayy.ui.theme.WayyColors

data class ArTurnInstruction(
    val direction: Direction,
    val distanceText: String,
    val streetName: String
)

@Composable
fun ArNavigationOverlay(
    detections: List<MlDetection>,
    showLanes: Boolean,
    showBoxes: Boolean,
    leftLane: List<LanePoint> = emptyList(),
    rightLane: List<LanePoint> = emptyList(),
    deviceBearing: Float? = null,
    turnBearing: Float? = null,
    isApproaching: Boolean = false,
    isNavigating: Boolean = false,
    nextDirection: Direction = Direction.STRAIGHT,
    nextNextDirection: Direction = Direction.STRAIGHT,
    distanceToTurnMeters: Double = 0.0,
    distanceToTurnText: String = "",
    currentStreet: String = "",
    nextStreet: String = "",
    eta: String = "",
    remainingDistance: String = "",
    currentSpeed: Float = 0f,
    turnInstructions: List<ArTurnInstruction> = emptyList(),
    useOpenGL: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (useOpenGL && (detections.isNotEmpty() || leftLane.isNotEmpty() || rightLane.isNotEmpty())) {
            AROpenGLOverlay(
                detections = detections,
                leftLane = leftLane,
                rightLane = rightLane,
                imageWidth = 640,
                imageHeight = 480,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            ArVisionOverlay(
                detections = detections,
                showLanes = showLanes,
                showBoxes = showBoxes,
                leftLane = leftLane,
                rightLane = rightLane,
                deviceBearing = deviceBearing,
                turnBearing = turnBearing,
                isApproaching = isApproaching,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (isNavigating) {
            ArTurnIndicator(
                direction = nextDirection,
                distanceText = distanceToTurnText,
                distanceMeters = distanceToTurnMeters,
                streetName = currentStreet,
                isApproaching = isApproaching,
                deviceBearing = deviceBearing,
                turnBearing = turnBearing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp)
            )

            ArEtaCard(
                eta = eta,
                remainingDistance = remainingDistance,
                currentSpeed = currentSpeed,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 160.dp, end = 16.dp)
            )

            if (turnInstructions.isNotEmpty()) {
                ArTurnListPreview(
                    turns = turnInstructions,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 100.dp, start = 16.dp, end = 16.dp)
                )
            }

            if (nextStreet.isNotEmpty()) {
                ArNextStreetBadge(
                    streetName = nextStreet,
                    direction = nextNextDirection,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 160.dp, start = 16.dp)
                )
            }
        }
    }
}

@Composable
fun ArTurnIndicator(
    direction: Direction,
    distanceText: String,
    distanceMeters: Double,
    streetName: String,
    isApproaching: Boolean,
    deviceBearing: Float?,
    turnBearing: Float?,
    modifier: Modifier = Modifier
) {
    val pulseAlpha by rememberInfiniteTransition(label = "turnPulse").animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val distanceColor = when {
        distanceMeters < 50 -> WayyColors.Error
        distanceMeters < 150 -> WayyColors.Warning
        distanceMeters < 400 -> WayyColors.PrimaryLime
        else -> Color.White
    }

    val backgroundColor by animateColorAsState(
        targetValue = if (isApproaching) WayyColors.PrimaryLime.copy(alpha = 0.95f) else WayyColors.BgSecondary.copy(alpha = 0.9f),
        animationSpec = tween(300),
        label = "turnBgColor"
    )

    val textColor = if (isApproaching) WayyColors.BgPrimary else Color.White

    val baseRotation = when (direction) {
        Direction.STRAIGHT -> 0f
        Direction.LEFT -> -90f
        Direction.RIGHT -> 90f
        Direction.U_TURN -> 180f
        Direction.SLIGHT_LEFT -> -45f
        Direction.SLIGHT_RIGHT -> 45f
    }

    val arrowRotation by animateFloatAsState(
        targetValue = if (deviceBearing != null && turnBearing != null && turnBearing > 0f) {
            NavigationUtils.bearingDifference(deviceBearing, turnBearing)
        } else baseRotation,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "arrowRotation"
    )

    val arrowIcon: ImageVector = when (direction) {
        Direction.STRAIGHT -> Icons.Default.ArrowUpward
        Direction.LEFT -> Icons.Default.TurnSharpLeft
        Direction.RIGHT -> Icons.Default.TurnSharpRight
        Direction.U_TURN -> Icons.AutoMirrored.Filled.ArrowBack
        Direction.SLIGHT_LEFT -> Icons.Default.TurnSharpLeft
        Direction.SLIGHT_RIGHT -> Icons.Default.TurnSharpRight
    }

    Box(
        modifier = modifier
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        if (isApproaching) WayyColors.BgPrimary.copy(alpha = pulseAlpha)
                        else distanceColor.copy(alpha = 0.2f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = arrowIcon,
                    contentDescription = "Turn direction",
                    tint = if (isApproaching) WayyColors.PrimaryLime else distanceColor,
                    modifier = Modifier
                        .size(40.dp)
                        .rotate(arrowRotation)
                        .scale(1.1f)
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = distanceText,
                        color = if (isApproaching) textColor else distanceColor,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = getDirectionText(direction),
                        color = if (isApproaching) textColor.copy(alpha = 0.8f) else WayyColors.TextSecondary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }

                if (streetName.isNotEmpty()) {
                    Text(
                        text = streetName,
                        color = if (isApproaching) textColor.copy(alpha = 0.7f) else WayyColors.TextSecondary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isApproaching) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(WayyColors.PrimaryLime.copy(alpha = pulseAlpha))
                )
            }
        }
    }
}

@Composable
private fun ArEtaCard(
    eta: String,
    remainingDistance: String,
    currentSpeed: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(
                color = WayyColors.BgSecondary.copy(alpha = 0.85f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AccessTime,
                contentDescription = "ETA",
                tint = WayyColors.PrimaryLime,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = eta,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Route,
                contentDescription = "Distance",
                tint = WayyColors.PrimaryCyan,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = remainingDistance,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Speed,
                contentDescription = "Speed",
                tint = WayyColors.PrimaryOrange,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "${currentSpeed.toInt()} mph",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ArNextStreetBadge(
    streetName: String,
    direction: Direction,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                color = WayyColors.BgSecondary.copy(alpha = 0.8f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Then",
            color = WayyColors.TextSecondary,
            fontSize = 12.sp
        )
        Text(
            text = streetName,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(
            imageVector = getDirectionIcon(direction),
            contentDescription = null,
            tint = WayyColors.PrimaryCyan,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ArTurnListPreview(
    turns: List<ArTurnInstruction>,
    modifier: Modifier = Modifier
) {
    if (turns.isEmpty()) return

    Column(
        modifier = modifier
            .background(
                color = WayyColors.BgSecondary.copy(alpha = 0.75f),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Upcoming",
            color = WayyColors.TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        turns.take(3).forEach { turn ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = getDirectionIcon(turn.direction),
                    contentDescription = null,
                    tint = WayyColors.PrimaryCyan,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = turn.distanceText,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (turn.streetName.isNotEmpty()) {
                    Text(
                        text = turn.streetName,
                        color = WayyColors.TextSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
        }
    }
}

private fun getDirectionText(direction: Direction): String = when (direction) {
    Direction.STRAIGHT -> "Continue"
    Direction.LEFT -> "Turn left"
    Direction.RIGHT -> "Turn right"
    Direction.U_TURN -> "U-turn"
    Direction.SLIGHT_LEFT -> "Bear left"
    Direction.SLIGHT_RIGHT -> "Bear right"
}

private fun getDirectionIcon(direction: Direction): ImageVector = when (direction) {
    Direction.STRAIGHT -> Icons.Default.ArrowUpward
    Direction.LEFT -> Icons.Default.TurnSharpLeft
    Direction.RIGHT -> Icons.Default.TurnSharpRight
    Direction.U_TURN -> Icons.AutoMirrored.Filled.ArrowBack
    Direction.SLIGHT_LEFT -> Icons.Default.TurnSharpLeft
    Direction.SLIGHT_RIGHT -> Icons.Default.TurnSharpRight
}
