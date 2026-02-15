package com.wayy.ui.components.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.wayy.ui.theme.WayyColors

data class LaneConfig(
    val directions: List<LaneDirection>,
    val isActive: Boolean
)

enum class LaneDirection {
    STRAIGHT,
    LEFT,
    RIGHT,
    SLIGHT_LEFT,
    SLIGHT_RIGHT,
    U_TURN
}

@Composable
fun LaneGuidanceView(
    lanes: List<LaneConfig>,
    modifier: Modifier = Modifier
) {
    if (lanes.isEmpty()) return

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(WayyColors.Surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        lanes.forEach { lane ->
            LaneIndicator(
                directions = lane.directions,
                isActive = lane.isActive
            )
        }
    }
}

@Composable
private fun LaneIndicator(
    directions: List<LaneDirection>,
    isActive: Boolean
) {
    val bgColor = if (isActive) WayyColors.Accent else WayyColors.SurfaceVariant
    val iconColor = if (isActive) Color.White else WayyColors.PrimaryMuted

    Box(
        modifier = Modifier
            .size(width = 28.dp, height = 36.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            directions.forEach { direction ->
                LaneArrow(
                    direction = direction,
                    color = iconColor,
                    isActive = isActive
                )
            }
        }
    }
}

@Composable
private fun LaneArrow(
    direction: LaneDirection,
    color: Color,
    isActive: Boolean
) {
    val rotation = when (direction) {
        LaneDirection.STRAIGHT -> 0f
        LaneDirection.LEFT -> -90f
        LaneDirection.RIGHT -> 90f
        LaneDirection.SLIGHT_LEFT -> -45f
        LaneDirection.SLIGHT_RIGHT -> 45f
        LaneDirection.U_TURN -> 180f
    }

    Icon(
        imageVector = Icons.Default.ArrowUpward,
        contentDescription = null,
        tint = color,
        modifier = Modifier
            .size(if (isActive) 14.dp else 12.dp)
            .alpha(if (isActive) 1f else 0.6f)
            .then(
                if (direction != LaneDirection.STRAIGHT) {
                    Modifier.graphicsLayer { rotationZ = rotation }
                } else Modifier
            )
    )
}

@Composable
fun LaneGuidanceCompact(
    lanes: List<LaneConfig>,
    modifier: Modifier = Modifier
) {
    if (lanes.isEmpty()) return

    val totalLanes = lanes.size

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(WayyColors.Surface)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalLanes) { index ->
            Box(
                modifier = Modifier
                    .size(width = 6.dp, height = 18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (lanes.getOrNull(index)?.isActive == true)
                            WayyColors.Accent
                        else
                            WayyColors.SurfaceVariant
                    )
            )
        }
    }
}

object LaneGuidanceFactory {
    fun createFromDirections(availableDirections: List<LaneDirection>, activeDirection: LaneDirection): List<LaneConfig> {
        return availableDirections.map { direction ->
            LaneConfig(
                directions = listOf(direction),
                isActive = direction == activeDirection
            )
        }
    }

    fun createStandardLanes(activeDirection: LaneDirection, laneCount: Int): List<LaneConfig> {
        return when (laneCount) {
            2 -> listOf(
                LaneConfig(listOf(LaneDirection.LEFT, LaneDirection.STRAIGHT), activeDirection == LaneDirection.LEFT),
                LaneConfig(listOf(LaneDirection.STRAIGHT, LaneDirection.RIGHT), activeDirection != LaneDirection.LEFT)
            )
            3 -> listOf(
                LaneConfig(listOf(LaneDirection.LEFT), activeDirection == LaneDirection.LEFT),
                LaneConfig(listOf(LaneDirection.STRAIGHT), activeDirection == LaneDirection.STRAIGHT),
                LaneConfig(listOf(LaneDirection.RIGHT), activeDirection == LaneDirection.RIGHT)
            )
            4 -> listOf(
                LaneConfig(listOf(LaneDirection.LEFT), activeDirection == LaneDirection.LEFT),
                LaneConfig(listOf(LaneDirection.STRAIGHT), activeDirection == LaneDirection.STRAIGHT),
                LaneConfig(listOf(LaneDirection.STRAIGHT), activeDirection == LaneDirection.STRAIGHT),
                LaneConfig(listOf(LaneDirection.RIGHT), activeDirection == LaneDirection.RIGHT)
            )
            else -> listOf(
                LaneConfig(listOf(LaneDirection.STRAIGHT), true)
            )
        }
    }
}
