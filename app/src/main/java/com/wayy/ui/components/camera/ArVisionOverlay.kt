package com.wayy.ui.components.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.wayy.ml.LanePoint
import com.wayy.ml.MlDetection
import kotlin.math.max
import kotlin.math.pow

private val SkyTop = Color(0xFF070910)
private val SkyMid = Color(0xFF0D1220)
private val SkyNear = Color(0xFF141A2A)
private val RoadDark = Color(0xFF13161B)
private val RoadMid = Color(0xFF1B2027)
private val RoadLight = Color(0xFF262D36)
private val LaneWhite = Color(0xFFE5E7EB)
private val LaneYellow = Color(0xFFF6C453)
private val LaneBlue = Color(0xFF3B82F6)
private val LaneBlueSoft = Color(0xFF60A5FA)
private val EgoLaneTop = Color(0xFF1FDBB0)
private val EgoLaneBottom = Color(0xFF10B981)
private val VehicleBlue = Color(0xFF4DA3FF)
private val VehicleBlueHighlight = Color(0xFF9AD4FF)

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
    val laneWidth = with(LocalDensity.current) { 2.dp.toPx() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val horizonY = size.height * 0.34f
        val bottomY = size.height
        val centerX = size.width / 2f
        val roadWidthBottom = size.width * 0.92f

        drawSkyBackground(horizonY)
        drawHorizonGlow(horizonY)
        drawRoadSurface(horizonY, bottomY, centerX, roadWidthBottom)

        if (showLanes) {
            drawTeslaLaneMarkers(
                leftLane = leftLane,
                rightLane = rightLane,
                horizonY = horizonY,
                bottomY = bottomY,
                centerX = centerX,
                roadWidthBottom = roadWidthBottom,
                laneWidth = laneWidth
            )
        }

        if (showBoxes) {
            detections.forEach { detection ->
                drawTeslaVehicle(
                    detection = detection,
                    horizonY = horizonY,
                    bottomY = bottomY,
                    centerX = centerX,
                    roadWidthBottom = roadWidthBottom
                )
            }
        }

        drawVignette()
    }
}

private fun DrawScope.drawSkyBackground(horizonY: Float) {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                SkyTop,
                SkyMid,
                SkyNear
            ),
            startY = 0f,
            endY = horizonY
        )
    )
}

private fun DrawScope.drawHorizonGlow(horizonY: Float) {
    val glowHeight = size.height * 0.08f
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color(0xFF2DD4BF).copy(alpha = 0.18f),
                Color.Transparent
            ),
            startY = horizonY - glowHeight,
            endY = horizonY + glowHeight
        )
    )
}

private fun DrawScope.drawRoadSurface(
    horizonY: Float,
    bottomY: Float,
    centerX: Float,
    roadWidthBottom: Float
) {
    val roadTopWidth = roadWidthBottom * 0.18f

    val roadPath = Path().apply {
        moveTo(centerX - roadWidthBottom / 2, bottomY)
        lineTo(centerX - roadTopWidth / 2, horizonY)
        lineTo(centerX + roadTopWidth / 2, horizonY)
        lineTo(centerX + roadWidthBottom / 2, bottomY)
        close()
    }
    drawPath(
        path = roadPath,
        brush = Brush.verticalGradient(
            colors = listOf(RoadLight, RoadMid, RoadDark),
            startY = bottomY,
            endY = horizonY
        )
    )
    val highlightPath = Path().apply {
        val highlightBottom = roadWidthBottom * 0.18f
        val highlightTop = roadTopWidth * 0.55f
        moveTo(centerX - highlightBottom / 2, bottomY)
        lineTo(centerX - highlightTop / 2, horizonY)
        lineTo(centerX + highlightTop / 2, horizonY)
        lineTo(centerX + highlightBottom / 2, bottomY)
        close()
    }
    drawPath(
        path = highlightPath,
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF313843).copy(alpha = 0.55f),
                Color(0xFF1E232B).copy(alpha = 0.2f),
                Color.Transparent
            ),
            startY = bottomY,
            endY = horizonY
        )
    )
}

private fun DrawScope.drawTeslaLaneMarkers(
    leftLane: List<LanePoint>,
    rightLane: List<LanePoint>,
    horizonY: Float,
    bottomY: Float,
    centerX: Float,
    roadWidthBottom: Float,
    laneWidth: Float
) {
    val roadTopWidth = roadWidthBottom * 0.18f

    if (!drawDetectedLaneFill(leftLane, rightLane, horizonY, bottomY, centerX, roadWidthBottom)) {
        drawEgoLaneFill(
            horizonY = horizonY,
            bottomY = bottomY,
            centerX = centerX,
            roadWidthBottom = roadWidthBottom,
            roadTopWidth = roadTopWidth
        )
    }

    if (leftLane.isNotEmpty() && rightLane.isNotEmpty()) {
        drawRoadEdgeLines(
            horizonY = horizonY,
            bottomY = bottomY,
            centerX = centerX,
            roadWidthBottom = roadWidthBottom,
            roadTopWidth = roadTopWidth,
            laneWidth = laneWidth
        )
        val leftSorted = leftLane.sortedBy { it.y }
        val leftPath = Path()
        leftSorted.forEachIndexed { i, pt ->
            val pos = projectToPerspective(pt.x, pt.y, horizonY, bottomY, centerX, roadWidthBottom)
            if (i == 0) leftPath.moveTo(pos.x, pos.y) else leftPath.lineTo(pos.x, pos.y)
        }
        drawGlowingLane(leftPath, LaneBlueSoft, laneWidth)

        val rightSorted = rightLane.sortedBy { it.y }
        val rightPath = Path()
        rightSorted.forEachIndexed { i, pt ->
            val pos = projectToPerspective(pt.x, pt.y, horizonY, bottomY, centerX, roadWidthBottom)
            if (i == 0) rightPath.moveTo(pos.x, pos.y) else rightPath.lineTo(pos.x, pos.y)
        }
        drawGlowingLane(rightPath, LaneBlueSoft, laneWidth)

        drawCenterDashes(
            leftLane = leftLane,
            rightLane = rightLane,
            horizonY = horizonY,
            bottomY = bottomY,
            centerX = centerX,
            roadWidthBottom = roadWidthBottom,
            laneYellow = LaneYellow,
            laneWidth = laneWidth
        )
    } else {
        drawDefaultTeslaLanes(
            horizonY = horizonY,
            bottomY = bottomY,
            centerX = centerX,
            roadWidthBottom = roadWidthBottom,
            laneWhite = LaneWhite,
            laneYellow = LaneYellow,
            laneWidth = laneWidth
        )
    }
}

private fun projectToPerspective(
    x: Float,
    y: Float,
    horizonY: Float,
    bottomY: Float,
    centerX: Float,
    roadWidth: Float
): Offset {
    val depth = y.coerceIn(0f, 1f)
    val scale = 0.18f + depth * 0.82f
    val screenY = bottomY - (bottomY - horizonY) * depth.pow(1.15f)
    val screenX = centerX + (x - 0.5f) * roadWidth * scale
    return Offset(screenX, screenY)
}

private fun DrawScope.drawGlowingLane(
    path: Path,
    color: Color,
    laneWidth: Float
) {
    for (i in 3 downTo 1) {
        drawPath(
            path = path,
            color = color.copy(alpha = 0.08f * i),
            style = Stroke(
                width = laneWidth + (i * 6f),
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

private fun DrawScope.drawGlowingLine(
    start: Offset,
    end: Offset,
    color: Color,
    laneWidth: Float
) {
    for (i in 3 downTo 1) {
        drawLine(
            color = color.copy(alpha = 0.08f * i),
            start = start,
            end = end,
            strokeWidth = laneWidth + (i * 6f),
            cap = StrokeCap.Round
        )
    }
    drawLine(
        color = color,
        start = start,
        end = end,
        strokeWidth = laneWidth,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawDefaultTeslaLanes(
    horizonY: Float,
    bottomY: Float,
    centerX: Float,
    roadWidthBottom: Float,
    laneWhite: Color,
    laneYellow: Color,
    laneWidth: Float
) {
    val roadTopWidth = roadWidthBottom * 0.18f
    val laneCount = 3
    val laneStep = 1f / laneCount
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(22f, 18f), 0f)

    for (i in 0..laneCount) {
        val offset = -0.5f + laneStep * i
        val start = Offset(centerX + offset * roadWidthBottom, bottomY)
        val end = Offset(centerX + offset * roadTopWidth, horizonY)
        val isEdge = i == 0 || i == laneCount
        val color = if (isEdge) LaneBlue else laneWhite
        val alpha = if (isEdge) 0.9f else 0.75f
        drawLine(
            color = color.copy(alpha = alpha),
            start = start,
            end = end,
            strokeWidth = if (isEdge) laneWidth * 1.2f else laneWidth,
            cap = StrokeCap.Round,
            pathEffect = if (isEdge) null else dashEffect
        )
    }

    val numDashes = 12
    for (i in 0 until numDashes step 2) {
        val t1 = i.toFloat() / numDashes
        val t2 = (i + 1).toFloat() / numDashes
        val y1 = bottomY - (bottomY - horizonY) * t1.pow(1.2f)
        val y2 = bottomY - (bottomY - horizonY) * t2.pow(1.2f)
        val alpha = (1f - t1 * 0.6f).coerceIn(0.3f, 1f)
        val width = laneWidth * (0.75f + (1f - t1) * 0.5f)

        drawLine(
            color = laneYellow.copy(alpha = alpha),
            start = Offset(centerX, y1),
            end = Offset(centerX, y2),
            strokeWidth = width,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawCenterDashes(
    leftLane: List<LanePoint>,
    rightLane: List<LanePoint>,
    horizonY: Float,
    bottomY: Float,
    centerX: Float,
    roadWidthBottom: Float,
    laneYellow: Color,
    laneWidth: Float
) {
    val leftSorted = leftLane.sortedBy { it.y }
    val rightSorted = rightLane.sortedBy { it.y }
    val minSize = minOf(leftSorted.size, rightSorted.size)

    if (minSize < 2) {
        drawDefaultCenterDashes(horizonY, bottomY, centerX, laneYellow, laneWidth)
        return
    }

    for (i in 0 until minSize - 1 step 2) {
        val left1 = projectToPerspective(leftSorted[i].x, leftSorted[i].y, horizonY, bottomY, centerX, roadWidthBottom)
        val right1 = projectToPerspective(rightSorted[i].x, rightSorted[i].y, horizonY, bottomY, centerX, roadWidthBottom)
        val left2 = projectToPerspective(leftSorted[i + 1].x, leftSorted[i + 1].y, horizonY, bottomY, centerX, roadWidthBottom)
        val right2 = projectToPerspective(rightSorted[i + 1].x, rightSorted[i + 1].y, horizonY, bottomY, centerX, roadWidthBottom)

        val center1 = Offset((left1.x + right1.x) / 2, (left1.y + right1.y) / 2)
        val center2 = Offset((left2.x + right2.x) / 2, (left2.y + right2.y) / 2)

        val depth = 1f - ((center1.y - horizonY) / (bottomY - horizonY).coerceAtLeast(1f))
        val alpha = (0.4f + depth * 0.6f).coerceIn(0.3f, 1f)

        drawLine(
            color = laneYellow.copy(alpha = alpha),
            start = center1,
            end = center2,
            strokeWidth = laneWidth * (0.6f + depth * 0.6f),
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawDefaultCenterDashes(
    horizonY: Float,
    bottomY: Float,
    centerX: Float,
    laneYellow: Color,
    laneWidth: Float
) {
    val numDashes = 12
    for (i in 0 until numDashes step 2) {
        val t1 = i.toFloat() / numDashes
        val t2 = (i + 1).toFloat() / numDashes
        val y1 = bottomY - (bottomY - horizonY) * t1.pow(1.2f)
        val y2 = bottomY - (bottomY - horizonY) * t2.pow(1.2f)
        val alpha = (1f - t1 * 0.6f).coerceIn(0.3f, 1f)

        drawLine(
            color = laneYellow.copy(alpha = alpha),
            start = Offset(centerX, y1),
            end = Offset(centerX, y2),
            strokeWidth = laneWidth * 0.8f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawTeslaVehicle(
    detection: MlDetection,
    horizonY: Float,
    bottomY: Float,
    centerX: Float,
    roadWidthBottom: Float
) {
    val pos = projectToPerspective(detection.x, detection.y, horizonY, bottomY, centerX, roadWidthBottom)
    val depth = detection.y.coerceIn(0.05f, 1f)
    val safeWidth = detection.width.coerceIn(0.06f, 0.35f)
    val safeHeight = detection.height.coerceIn(0.06f, 0.25f)

    val baseWidth = safeWidth * size.width * (0.18f + depth * 0.5f)
    val baseHeight = safeHeight * size.height * (0.12f + depth * 0.45f)
    val lift = baseHeight * (0.55f + (1f - depth) * 0.2f)
    val topScale = 0.72f

    val baseLeft = pos.x - baseWidth / 2
    val baseTop = pos.y - baseHeight
    val topWidth = baseWidth * topScale
    val topHeight = baseHeight * topScale
    val topLeft = Offset(pos.x - topWidth / 2, baseTop - lift)

    val boxColor = VehicleBlue.copy(alpha = 0.85f)
    val glowColor = VehicleBlueHighlight.copy(alpha = 0.35f)
    val strokeWidth = (1.2f + depth * 1.8f).coerceAtMost(4f)

    fun drawEdge(start: Offset, end: Offset) {
        drawLine(
            color = glowColor,
            start = start,
            end = end,
            strokeWidth = strokeWidth * 2.2f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = boxColor,
            start = start,
            end = end,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }

    val base = listOf(
        Offset(baseLeft, baseTop + baseHeight),
        Offset(baseLeft + baseWidth, baseTop + baseHeight),
        Offset(baseLeft + baseWidth, baseTop),
        Offset(baseLeft, baseTop)
    )
    val top = listOf(
        Offset(topLeft.x, topLeft.y + topHeight),
        Offset(topLeft.x + topWidth, topLeft.y + topHeight),
        Offset(topLeft.x + topWidth, topLeft.y),
        Offset(topLeft.x, topLeft.y)
    )

    for (i in base.indices) {
        val next = (i + 1) % base.size
        drawEdge(base[i], base[next])
        drawEdge(top[i], top[next])
        drawEdge(base[i], top[i])
    }
}

private fun DrawScope.drawDetectedLaneFill(
    leftLane: List<LanePoint>,
    rightLane: List<LanePoint>,
    horizonY: Float,
    bottomY: Float,
    centerX: Float,
    roadWidthBottom: Float
): Boolean {
    if (leftLane.size < 2 || rightLane.size < 2) return false
    val leftSorted = leftLane.sortedBy { it.y }
    val rightSorted = rightLane.sortedBy { it.y }
    val path = Path()
    leftSorted.forEachIndexed { index, point ->
        val pos = projectToPerspective(point.x, point.y, horizonY, bottomY, centerX, roadWidthBottom)
        if (index == 0) path.moveTo(pos.x, pos.y) else path.lineTo(pos.x, pos.y)
    }
    for (i in rightSorted.size - 1 downTo 0) {
        val point = rightSorted[i]
        val pos = projectToPerspective(point.x, point.y, horizonY, bottomY, centerX, roadWidthBottom)
        path.lineTo(pos.x, pos.y)
    }
    path.close()
    val bottom = projectToPerspective(0.5f, rightSorted.last().y, horizonY, bottomY, centerX, roadWidthBottom).y
    val top = projectToPerspective(0.5f, rightSorted.first().y, horizonY, bottomY, centerX, roadWidthBottom).y
    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            colors = listOf(EgoLaneBottom.copy(alpha = 0.55f), EgoLaneTop.copy(alpha = 0.2f), Color.Transparent),
            startY = bottom,
            endY = top
        )
    )
    return true
}

private fun DrawScope.drawEgoLaneFill(
    horizonY: Float,
    bottomY: Float,
    centerX: Float,
    roadWidthBottom: Float,
    roadTopWidth: Float
) {
    val laneCount = 3
    val laneStep = 1f / laneCount
    val leftOffset = -0.5f + laneStep
    val rightOffset = leftOffset + laneStep
    val path = Path().apply {
        moveTo(centerX + leftOffset * roadWidthBottom, bottomY)
        lineTo(centerX + leftOffset * roadTopWidth, horizonY)
        lineTo(centerX + rightOffset * roadTopWidth, horizonY)
        lineTo(centerX + rightOffset * roadWidthBottom, bottomY)
        close()
    }
    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            colors = listOf(EgoLaneBottom.copy(alpha = 0.55f), EgoLaneTop.copy(alpha = 0.18f), Color.Transparent),
            startY = bottomY,
            endY = horizonY
        )
    )
}

private fun DrawScope.drawRoadEdgeLines(
    horizonY: Float,
    bottomY: Float,
    centerX: Float,
    roadWidthBottom: Float,
    roadTopWidth: Float,
    laneWidth: Float
) {
    drawGlowingLine(
        start = Offset(centerX - roadWidthBottom / 2, bottomY),
        end = Offset(centerX - roadTopWidth / 2, horizonY),
        color = LaneBlue,
        laneWidth = laneWidth * 1.1f
    )
    drawGlowingLine(
        start = Offset(centerX + roadWidthBottom / 2, bottomY),
        end = Offset(centerX + roadTopWidth / 2, horizonY),
        color = LaneBlue,
        laneWidth = laneWidth * 1.1f
    )
}

private fun DrawScope.drawVignette() {
    val radius = max(size.width, size.height) * 0.75f
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color(0xFF05070D).copy(alpha = 0.6f)),
            center = Offset(size.width / 2f, size.height * 0.45f),
            radius = radius
        )
    )
}
