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
            .clip(RoundedCornerShape(12.dp))
            .background(WayyColors.BgSecondary.copy(alpha = 0.9f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
    val bgColor = if (isActive) WayyColors.PrimaryLime else WayyColors.BgTertiary
    val iconColor = if (isActive) WayyColors.BgPrimary else WayyColors.TextSecondary
    val alpha = if (isActive) 1f else 0.5f

    Box(
        modifier = Modifier
            .size(width = 36.dp, height = 48.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor.copy(alpha = alpha))
            .padding(4.dp),
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
            .size(if (isActive) 16.dp else 14.dp)
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
            .clip(RoundedCornerShape(8.dp))
            .background(WayyColors.BgSecondary.copy(alpha = 0.85f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalLanes) { index ->
            Box(
                modifier = Modifier
                    .size(width = 8.dp, height = 24.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (lanes.getOrNull(index)?.isActive == true)
                            WayyColors.PrimaryLime
                        else
                            WayyColors.BgTertiary.copy(alpha = 0.5f)
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
