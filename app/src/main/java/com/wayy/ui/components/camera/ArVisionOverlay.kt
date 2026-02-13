package com.wayy.ui.components.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.wayy.ml.MlDetection
import com.wayy.ml.LanePoint
import com.wayy.ui.theme.WayyColors
import kotlin.math.abs
import kotlin.math.min

@Composable
fun ArVisionOverlay(
    detections: List<MlDetection>,
    showLanes: Boolean,
    showBoxes: Boolean,
    leftLane: List<LanePoint> = emptyList(),
    rightLane: List<LanePoint> = emptyList(),
    deviceBearing: Float? = null,
    turnBearing: Float? = null,
    isApproaching: Boolean = false,
    modifier: Modifier = Modifier
) {
    val strokeWidth = with(LocalDensity.current) { 2.dp.toPx() }
    val laneWidth = with(LocalDensity.current) { 3.dp.toPx() }
    val laneGlow = with(LocalDensity.current) { 6.dp.toPx() }
    val transition = rememberInfiniteTransition(label = "arOverlay")
    val lanePhase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "lanePhase"
    ).value
    val boxPulse = transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "boxPulse"
    ).value
    Canvas(modifier = modifier.fillMaxSize()) {
        val hasLaneModel = leftLane.isNotEmpty() || rightLane.isNotEmpty()
        if (showLanes && hasLaneModel) {
            fun drawLane(points: List<LanePoint>, color: Color) {
                val sorted = points.sortedBy { it.y }
                if (sorted.isEmpty()) return
                val path = Path()
                sorted.forEachIndexed { index, point ->
                    val x = point.x * size.width
                    val y = point.y * size.height
                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(width = laneWidth, cap = StrokeCap.Round)
                )
                drawPath(
                    path = path,
                    color = color.copy(alpha = 0.2f),
                    style = Stroke(width = laneGlow, cap = StrokeCap.Round)
                )
            }
            if (leftLane.isNotEmpty()) {
                drawLane(leftLane, WayyColors.PrimaryCyan.copy(alpha = 0.95f))
            }
            if (rightLane.isNotEmpty()) {
                drawLane(rightLane, WayyColors.PrimaryCyan.copy(alpha = 0.95f))
            }
        } else if (showLanes) {
            val horizonY = size.height * 0.35f
            val bottomY = size.height
            val leftBottomX = size.width * 0.18f
            val rightBottomX = size.width * 0.82f
            val bearingDelta = if (deviceBearing != null && turnBearing != null && isApproaching) {
                val raw = ((turnBearing - deviceBearing + 540f) % 360f) - 180f
                (raw / 90f).coerceIn(-1f, 1f)
            } else {
                0f
            }
            val laneShift = size.width * 0.08f * bearingDelta
            val leftTopX = size.width * 0.46f + laneShift
            val rightTopX = size.width * 0.54f + laneShift
            drawLine(
                color = WayyColors.PrimaryCyan.copy(alpha = 0.9f),
                start = Offset(leftBottomX, bottomY),
                end = Offset(leftTopX, horizonY),
                strokeWidth = laneWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = WayyColors.PrimaryCyan.copy(alpha = 0.9f),
                start = Offset(rightBottomX, bottomY),
                end = Offset(rightTopX, horizonY),
                strokeWidth = laneWidth,
                cap = StrokeCap.Round
            )
            for (i in 0..6) {
                val t = (i / 7f + lanePhase) % 1f
                val y = bottomY - (bottomY - horizonY) * t
                val leftX = leftBottomX + (leftTopX - leftBottomX) * t
                val rightX = rightBottomX + (rightTopX - rightBottomX) * t
                val alpha = 0.15f + (1f - abs(t - 0.5f)) * 0.4f
                drawLine(
                    color = WayyColors.PrimaryCyan.copy(alpha = alpha),
                    start = Offset(leftX, y),
                    end = Offset(rightX, y),
                    strokeWidth = strokeWidth
                )
            }
            drawLine(
                color = WayyColors.PrimaryCyan.copy(alpha = 0.15f),
                start = Offset(leftBottomX, bottomY),
                end = Offset(rightBottomX, bottomY),
                strokeWidth = laneGlow
            )
        }

        if (showBoxes) {
            detections.forEach { detection ->
                val width = detection.width * size.width
                val height = detection.height * size.height
                if (width <= 0f || height <= 0f) return@forEach
                val left = (detection.x * size.width) - width / 2f
                val top = (detection.y * size.height) - height / 2f
                val rect = Rect(left, top, left + width, top + height)
                val corner = min(width, height) * 0.2f
                val depth = min(width, height) * 0.08f
                val alpha = (0.4f + boxPulse * 0.6f) * detection.score.coerceIn(0.4f, 1f)
                val color = WayyColors.PrimaryLime.copy(alpha = alpha)
                drawLine(color, Offset(rect.left, rect.top), Offset(rect.left + corner, rect.top), strokeWidth, StrokeCap.Round)
                drawLine(color, Offset(rect.left, rect.top), Offset(rect.left, rect.top + corner), strokeWidth, StrokeCap.Round)
                drawLine(color, Offset(rect.right, rect.top), Offset(rect.right - corner, rect.top), strokeWidth, StrokeCap.Round)
                drawLine(color, Offset(rect.right, rect.top), Offset(rect.right, rect.top + corner), strokeWidth, StrokeCap.Round)
                drawLine(color, Offset(rect.left, rect.bottom), Offset(rect.left + corner, rect.bottom), strokeWidth, StrokeCap.Round)
                drawLine(color, Offset(rect.left, rect.bottom), Offset(rect.left, rect.bottom - corner), strokeWidth, StrokeCap.Round)
                drawLine(color, Offset(rect.right, rect.bottom), Offset(rect.right - corner, rect.bottom), strokeWidth, StrokeCap.Round)
                drawLine(color, Offset(rect.right, rect.bottom), Offset(rect.right, rect.bottom - corner), strokeWidth, StrokeCap.Round)
                val topRect = Rect(
                    rect.left - depth,
                    rect.top - depth,
                    rect.right - depth,
                    rect.top - depth + corner
                )
                drawRect(
                    color = color,
                    topLeft = Offset(topRect.left, topRect.top),
                    size = topRect.size,
                    style = Stroke(width = strokeWidth)
                )
            }
        }
    }
}
