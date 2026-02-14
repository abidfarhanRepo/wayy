package com.wayy.ar.renderer

import android.util.Log
import com.wayy.ar.model.*
import com.wayy.ml.LanePoint
import com.wayy.ml.MlDetection
import kotlin.math.atan
import kotlin.math.tan

object ARDataConverter {
    private const val TAG = "WayyARConverter"

    fun convertToARFrame(
        detections: List<MlDetection>,
        leftLane: List<LanePoint>,
        rightLane: List<LanePoint>,
        imageWidth: Int,
        imageHeight: Int,
        cameraPose: CameraPose = CameraPose()
    ): ARFrame {
        Log.d(TAG, "Converting: ${detections.size} detections, left=${leftLane.size}, right=${rightLane.size}")

        val lanes3D = convertLanesTo3D(leftLane, rightLane, imageWidth, imageHeight)
        val objects3D = convertDetectionsTo3D(detections, imageWidth, imageHeight)

        Log.d(TAG, "Converted: ${lanes3D.size} lanes, ${objects3D.size} objects")

        return ARFrame(
            timestamp = System.currentTimeMillis(),
            lanes = lanes3D,
            objects = objects3D,
            cameraPose = cameraPose,
            imageWidth = imageWidth,
            imageHeight = imageHeight
        )
    }

    private fun convertLanesTo3D(
        leftLane: List<LanePoint>,
        rightLane: List<LanePoint>,
        imageWidth: Int,
        imageHeight: Int
    ): List<Lane3D> {
        val lanes = mutableListOf<Lane3D>()

        if (leftLane.isNotEmpty()) {
            val leftPoints = leftLane.map { point ->
                pixelToWorld(point.x * imageWidth, point.y * imageHeight, imageWidth, imageHeight)
            }
            lanes.add(Lane3D(
                points = leftPoints,
                confidence = 0.8f,
                laneType = LaneType.LEFT_BOUNDARY
            ))
            Log.v(TAG, "Left lane: ${leftPoints.size} points")
        }

        if (rightLane.isNotEmpty()) {
            val rightPoints = rightLane.map { point ->
                pixelToWorld(point.x * imageWidth, point.y * imageHeight, imageWidth, imageHeight)
            }
            lanes.add(Lane3D(
                points = rightPoints,
                confidence = 0.8f,
                laneType = LaneType.RIGHT_BOUNDARY
            ))
            Log.v(TAG, "Right lane: ${rightPoints.size} points")
        }

        return lanes
    }

    private fun convertDetectionsTo3D(
        detections: List<MlDetection>,
        imageWidth: Int,
        imageHeight: Int
    ): List<Object3D> {
        return detections.mapNotNull { detection ->
            detectionToObject3D(detection, imageWidth, imageHeight)
        }
    }

    private fun detectionToObject3D(
        detection: MlDetection,
        imageWidth: Int,
        imageHeight: Int
    ): Object3D? {
        val centerX = detection.x * imageWidth
        val centerY = detection.y * imageHeight
        val width = detection.width * imageWidth
        val height = detection.height * imageHeight

        val depth = estimateDepthFromBbox(height, imageHeight)
        if (depth < 0) {
            return null
        }

        val focalLength = imageHeight * 1.2f
        val x = (centerX - imageWidth / 2f) / focalLength * depth
        val z = -depth

        val className = getClassName(detection.classId)
        val objHeight = estimateObjectHeight(className)
        val objWidth = estimateObjectWidth(className)

        Log.v(TAG, "Detection ${detection.classId}: center=($centerX, $centerY) -> 3D=($x, 0, $z), depth=$depth")

        return Object3D(
            position = Point3D(x, 0f, z),
            dimensions = Dimensions3D(objWidth, objHeight, objWidth),
            className = className,
            confidence = detection.score,
            trackingId = -1
        )
    }

    fun pixelToWorld(px: Float, py: Float, imageWidth: Int, imageHeight: Int): Point3D {
        val focalLength = imageHeight * 1.2f
        val cameraHeight = 1.5f

        val yNorm = (py - imageHeight / 2f) / focalLength
        val xNorm = (px - imageWidth / 2f) / focalLength

        val depth = if (yNorm > 0.01f) {
            cameraHeight / tan(atan(yNorm))
        } else {
            100f
        }

        val x = xNorm * depth
        val z = -depth

        return Point3D(x, 0.05f, z)
    }

    private fun estimateDepthFromBbox(bboxHeight: Float, imageHeight: Int): Float {
        val focalLength = imageHeight * 1.2f
        val realHeight = 1.5f

        if (bboxHeight <= 0) return -1f

        val depth = (focalLength * realHeight) / bboxHeight
        return depth.coerceIn(1f, 150f)
    }

    private fun getClassName(classId: Int): String {
        return when (classId) {
            0 -> "person"
            1 -> "bicycle"
            2 -> "car"
            3 -> "motorcycle"
            4 -> "airplane"
            5 -> "bus"
            6 -> "train"
            7 -> "truck"
            8 -> "boat"
            in 9..255 -> "object"
            else -> "unknown"
        }
    }

    private fun estimateObjectHeight(className: String): Float {
        return when (className.lowercase()) {
            "car" -> 1.5f
            "truck", "bus" -> 3.0f
            "person" -> 1.7f
            "bicycle", "motorcycle" -> 1.2f
            else -> 1.5f
        }
    }

    private fun estimateObjectWidth(className: String): Float {
        return when (className.lowercase()) {
            "car" -> 1.8f
            "truck", "bus" -> 2.5f
            "person" -> 0.5f
            "bicycle", "motorcycle" -> 0.8f
            else -> 1.5f
        }
    }
}
