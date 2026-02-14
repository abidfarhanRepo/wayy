package com.wayy.ui.components.camera

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.wayy.ml.LanePoint
import com.wayy.ui.theme.WayyColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun DynamicLaneOverlay(
    leftLane: List<LanePoint>,
    rightLane: List<LanePoint>,
    modifier: Modifier = Modifier
) {
    val strokeWidth = with(LocalDensity.current) { 2.dp.toPx() }
    val laneWidth = with(LocalDensity.current) { 3.dp.toPx() }
    
    val transition = rememberInfiniteTransition(label = "dynamicLane")
    val flowPhase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "flowPhase"
    ).value
    
    val pulsePhase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulsePhase"
    ).value

    val leftSorted = remember(leftLane) { leftLane.sortedBy { it.y } }
    val rightSorted = remember(rightLane) { rightLane.sortedBy { it.y } }

    Canvas(modifier = modifier.fillMaxSize()) {
        if (leftSorted.isEmpty() || rightSorted.isEmpty()) return@Canvas
        
        val canvasWidth = size.width
        val canvasHeight = size.height

        fun toCanvasX(normalizedX: Float): Float = normalizedX.coerceIn(0f, 1f) * canvasWidth
        fun toCanvasY(normalizedY: Float): Float = normalizedY.coerceIn(0f, 1f) * canvasHeight

        fun buildBezierPath(points: List<LanePoint>): Path? {
            if (points.size < 2) return null
            
            val path = Path()
            val canvasPoints = points.map { Offset(toCanvasX(it.x), toCanvasY(it.y)) }
            
            path.moveTo(canvasPoints[0].x, canvasPoints[0].y)
            
            if (canvasPoints.size == 2) {
                path.lineTo(canvasPoints[1].x, canvasPoints[1].y)
            } else {
                for (i in 1 until canvasPoints.size - 1) {
                    val prev = canvasPoints[i - 1]
                    val curr = canvasPoints[i]
                    val next = canvasPoints[i + 1]
                    
                    val controlX1 = curr.x - (next.x - prev.x) * 0.2f
                    val controlY1 = curr.y - (next.y - prev.y) * 0.2f
                    val controlX2 = curr.x + (next.x - prev.x) * 0.2f
                    val controlY2 = curr.y + (next.y - prev.y) * 0.2f
                    
                    path.cubicTo(
                        controlX1, controlY1,
                        controlX2, controlY2,
                        next.x, next.y
                    )
                }
                
                val last = canvasPoints.last()
                path.lineTo(last.x, last.y)
            }
            
            return path
        }

        fun drawLanePolygonFill(leftPts: List<LanePoint>, rightPts: List<LanePoint>) {
            if (leftPts.size < 2 || rightPts.size < 2) return
            
            val leftPath = buildBezierPath(leftPts) ?: return
            val rightPath = buildBezierPath(rightPts.reversed()) ?: return
            
            val fillPath = Path()
            fillPath.addPath(leftPath)
            fillPath.addPath(rightPath)
            fillPath.close()
            
            val bottomY = toCanvasY(maxOf(leftPts.last().y, rightPts.last().y))
            val topY = toCanvasY(minOf(leftPts.first().y, rightPts.first().y))
            
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        WayyColors.PrimaryCyan.copy(alpha = 0.35f * (0.8f + pulsePhase * 0.2f)),
                        WayyColors.PrimaryCyan.copy(alpha = 0.15f),
                        WayyColors.PrimaryCyan.copy(alpha = 0.05f),
                        WayyColors.PrimaryCyan.copy(alpha = 0.02f)
                    ),
                    startY = bottomY,
                    endY = topY
                )
            )
        }

        @Suppress("UNUSED_VARIABLE")
        fun drawFlowLines(leftPts: List<LanePoint>, rightPts: List<LanePoint>, phase: Float) {
            if (leftPts.size < 2 || rightPts.size < 2) return
            
            val numFlowLines = 12
            val dashLength = 0.06f
            
            for (i in 0 until numFlowLines) {
                val baseT = i.toFloat() / numFlowLines
                val t = (baseT + phase) % 1f
                
                val leftIdx = (t * (leftPts.size - 1)).coerceIn(0f, (leftPts.size - 1).toFloat())
                val rightIdx = (t * (rightPts.size - 1)).coerceIn(0f, (rightPts.size - 1).toFloat())
                
                val leftLi = leftIdx.toInt().coerceIn(0, leftPts.size - 2)
                val leftLf = leftIdx - leftLi
                val rightLi = rightIdx.toInt().coerceIn(0, rightPts.size - 2)
                val rightLf = rightIdx - rightLi
                
                val leftPt = leftPts[leftLi]
                val leftNextPt = leftPts[(leftLi + 1).coerceAtMost(leftPts.size - 1)]
                val rightPt = rightPts[rightLi]
                val rightNextPt = rightPts[(rightLi + 1).coerceAtMost(rightPts.size - 1)]
                
                val leftX = toCanvasX(leftPt.x + (leftNextPt.x - leftPt.x) * leftLf)
                val leftY = toCanvasY(leftPt.y + (leftNextPt.y - leftPt.y) * leftLf)
                val rightX = toCanvasX(rightPt.x + (rightNextPt.x - rightPt.x) * rightLf)
                val rightY = toCanvasY(rightPt.y + (rightNextPt.y - rightPt.y) * rightLf)
                
                val endT = (t + dashLength).coerceAtMost(0.98f)
                val endLeftIdx = (endT * (leftPts.size - 1)).coerceIn(0f, (leftPts.size - 1).toFloat())
                val endRightIdx = (endT * (rightPts.size - 1)).coerceIn(0f, (rightPts.size - 1).toFloat())
                
                val endLeftLi = endLeftIdx.toInt().coerceIn(0, leftPts.size - 2)
                val endLeftLf = endLeftIdx - endLeftLi
                val endRightLi = endRightIdx.toInt().coerceIn(0, rightPts.size - 2)
                val endRightLf = endRightIdx - endRightLi
                
                val endLeftPt = leftPts[endLeftLi]
                val endLeftNextPt = leftPts[(endLeftLi + 1).coerceAtMost(leftPts.size - 1)]
                val endRightPt = rightPts[endRightLi]
                val endRightNextPt = rightPts[(endRightLi + 1).coerceAtMost(rightPts.size - 1)]
                
                val endLeftX = toCanvasX(endLeftPt.x + (endLeftNextPt.x - endLeftPt.x) * endLeftLf)
                val endLeftY = toCanvasY(endLeftPt.y + (endLeftNextPt.y - endLeftPt.y) * endLeftLf)
                val endRightX = toCanvasX(endRightPt.x + (endRightNextPt.x - endRightPt.x) * endRightLf)
                val endRightY = toCanvasY(endRightPt.y + (endRightNextPt.y - endRightPt.y) * endRightLf)
                
                val depthFade = (1f - t * 0.7f)
                val waveOffset = sin(phase * PI.toFloat() * 4 + i * 0.5f) * 3f
                val flowAlpha = 0.4f * depthFade * (0.7f + pulsePhase * 0.3f)
                
                val dashPath = Path()
                dashPath.moveTo(leftX + waveOffset, (leftY + rightY) / 2)
                dashPath.lineTo(endLeftX + waveOffset, (endLeftY + endRightY) / 2)
                
                drawPath(
                    path = dashPath,
                    color = WayyColors.PrimaryCyan.copy(alpha = flowAlpha),
                    style = Stroke(
                        width = strokeWidth * (1f - t * 0.5f),
                        cap = StrokeCap.Round
                    )
                )
            }
        }

        fun drawCenterGuidance(leftPts: List<LanePoint>, rightPts: List<LanePoint>, phase: Float) {
            if (leftPts.size < 2 || rightPts.size < 2) return
            
            val numDashes = 8
            
            for (i in 0 until numDashes) {
                val dashStart = i.toFloat() / numDashes
                val dashEnd = (dashStart + 0.04f).coerceAtMost(0.95f)
                
                val points = listOf(dashStart, dashEnd).map { t ->
                    val leftIdx = (t * (leftPts.size - 1)).coerceIn(0f, (leftPts.size - 1).toFloat())
                    val rightIdx = (t * (rightPts.size - 1)).coerceIn(0f, (rightPts.size - 1).toFloat())
                    
                    val leftLi = leftIdx.toInt().coerceIn(0, leftPts.size - 2)
                    val leftLf = leftIdx - leftLi
                    val rightLi = rightIdx.toInt().coerceIn(0, rightPts.size - 2)
                    val rightLf = rightIdx - rightLi
                    
                    val leftPt = leftPts[leftLi]
                    val leftNextPt = leftPts[(leftLi + 1).coerceAtMost(leftPts.size - 1)]
                    val rightPt = rightPts[rightLi]
                    val rightNextPt = rightPts[(rightLi + 1).coerceAtMost(rightPts.size - 1)]
                    
                    val leftX = toCanvasX(leftPt.x + (leftNextPt.x - leftPt.x) * leftLf)
                    val leftY = toCanvasY(leftPt.y + (leftNextPt.y - leftPt.y) * leftLf)
                    val rightX = toCanvasX(rightPt.x + (rightNextPt.x - rightPt.x) * rightLf)
                    val rightY = toCanvasY(rightPt.y + (rightNextPt.y - rightPt.y) * rightLf)
                    
                    Offset((leftX + rightX) / 2, (leftY + rightY) / 2)
                }
                
                val animatedT = (dashStart + phase * 0.1f) % 1f
                val depthFade = (1f - animatedT * 0.8f)
                val dashAlpha = 0.6f * depthFade * (0.6f + pulsePhase * 0.4f)
                
                drawLine(
                    color = WayyColors.PrimaryLime.copy(alpha = dashAlpha),
                    start = points[0],
                    end = points[1],
                    strokeWidth = strokeWidth * 0.8f * (1f - animatedT * 0.4f),
                    cap = StrokeCap.Round
                )
            }
        }

        fun drawLaneBoundaryWithGlow(path: Path, color: Color) {
            for (i in 4 downTo 1) {
                drawPath(
                    path = path,
                    color = color.copy(alpha = 0.06f * i * (0.8f + pulsePhase * 0.2f)),
                    style = Stroke(
                        width = laneWidth + (i * 10f),
                        cap = StrokeCap.Round
                    )
                )
            }
            
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = laneWidth, cap = StrokeCap.Round)
            )
        }

        try {
            drawLanePolygonFill(leftSorted, rightSorted)
            drawFlowLines(leftSorted, rightSorted, flowPhase)
            drawCenterGuidance(leftSorted, rightSorted, flowPhase)
            
            val leftPath = buildBezierPath(leftSorted)
            val rightPath = buildBezierPath(rightSorted)
            
            leftPath?.let { drawLaneBoundaryWithGlow(it, WayyColors.PrimaryCyan.copy(alpha = 0.9f)) }
            rightPath?.let { drawLaneBoundaryWithGlow(it, WayyColors.PrimaryCyan.copy(alpha = 0.9f)) }
            
        } catch (e: Exception) {
            // Silently handle drawing errors
        }
    }
}

fun List<LanePoint>.interpolateForSmoothCurve(numPoints: Int = 50): List<LanePoint> {
    if (this.size < 2) return this
    
    val sorted = this.sortedBy { it.y }
    if (sorted.size < 2) return sorted
    
    val result = mutableListOf<LanePoint>()
    
    for (i in 0 until numPoints) {
        val t = i.toFloat() / (numPoints - 1)
        val targetY = sorted.first().y + (sorted.last().y - sorted.first().y) * t
        
        var lowerIdx = 0
        for (j in sorted.indices) {
            if (sorted[j].y <= targetY) {
                lowerIdx = j
            } else {
                break
            }
        }
        
        val upperIdx = (lowerIdx + 1).coerceAtMost(sorted.size - 1)
        
        if (lowerIdx == upperIdx) {
            result.add(LanePoint(sorted[lowerIdx].x, targetY))
        } else {
            val lower = sorted[lowerIdx]
            val upper = sorted[upperIdx]
            val range = upper.y - lower.y
            
            val frac = if (range > 0.001f) (targetY - lower.y) / range else 0.5f
            
            val smoothX = lower.x + (upper.x - lower.x) * frac
            result.add(LanePoint(smoothX, targetY))
        }
    }
    
    return result
}