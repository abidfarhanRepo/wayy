package com.wayy.ar.model

data class ARFrame(
    val timestamp: Long = System.currentTimeMillis(),
    val lanes: List<Lane3D> = emptyList(),
    val objects: List<Object3D> = emptyList(),
    val cameraPose: CameraPose = CameraPose(),
    val imageWidth: Int = 640,
    val imageHeight: Int = 480
)

data class CameraPose(
    val x: Float = 0f,
    val y: Float = 1.5f,
    val z: Float = 0f,
    val pitch: Float = 0f,
    val yaw: Float = 0f,
    val roll: Float = 0f
)

data class Lane3D(
    val points: List<Point3D>,
    val confidence: Float = 0.5f,
    val laneType: LaneType = LaneType.UNKNOWN
)

data class Point3D(
    val x: Float,
    val y: Float,
    val z: Float
)

enum class LaneType {
    LEFT_BOUNDARY,
    RIGHT_BOUNDARY,
    CENTER_LINE,
    DASHED,
    SOLID,
    UNKNOWN
}

data class Object3D(
    val position: Point3D,
    val dimensions: Dimensions3D,
    val className: String,
    val confidence: Float,
    val trackingId: Int = -1
)

data class Dimensions3D(
    val width: Float,
    val height: Float,
    val depth: Float
)
