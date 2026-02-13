package com.wayy.ml

import android.os.SystemClock
import kotlin.math.max
import kotlin.math.min

class DetectionTracker(
    private val iouThreshold: Float = 0.3f,
    private val smoothing: Float = 0.6f,
    private val maxAgeMs: Long = 900L
) {
    private data class Track(
        var detection: MlDetection,
        var lastUpdatedMs: Long
    )

    private val tracks = mutableListOf<Track>()

    fun update(detections: List<MlDetection>): List<MlDetection> {
        val now = SystemClock.elapsedRealtime()
        val availableTracks = tracks.toMutableList()
        val updatedTracks = mutableListOf<Track>()

        detections.forEach { detection ->
            val match = availableTracks
                .filter { it.detection.classId == detection.classId }
                .maxByOrNull { iou(it.detection, detection) }
            if (match != null && iou(match.detection, detection) >= iouThreshold) {
                match.detection = smooth(match.detection, detection)
                match.lastUpdatedMs = now
                updatedTracks.add(match)
                availableTracks.remove(match)
            } else {
                updatedTracks.add(Track(detection, now))
            }
        }

        tracks.clear()
        tracks.addAll(updatedTracks.filter { now - it.lastUpdatedMs <= maxAgeMs })
        return tracks.map { it.detection }
    }

    private fun smooth(previous: MlDetection, current: MlDetection): MlDetection {
        val alpha = smoothing
        return MlDetection(
            classId = current.classId,
            score = max(current.score, previous.score * 0.8f),
            x = previous.x * (1 - alpha) + current.x * alpha,
            y = previous.y * (1 - alpha) + current.y * alpha,
            width = previous.width * (1 - alpha) + current.width * alpha,
            height = previous.height * (1 - alpha) + current.height * alpha
        )
    }

    private fun iou(a: MlDetection, b: MlDetection): Float {
        val ax1 = a.x - a.width / 2f
        val ay1 = a.y - a.height / 2f
        val ax2 = a.x + a.width / 2f
        val ay2 = a.y + a.height / 2f
        val bx1 = b.x - b.width / 2f
        val by1 = b.y - b.height / 2f
        val bx2 = b.x + b.width / 2f
        val by2 = b.y + b.height / 2f
        val interX1 = max(ax1, bx1)
        val interY1 = max(ay1, by1)
        val interX2 = min(ax2, bx2)
        val interY2 = min(ay2, by2)
        val interW = max(0f, interX2 - interX1)
        val interH = max(0f, interY2 - interY1)
        val interArea = interW * interH
        val areaA = max(0f, ax2 - ax1) * max(0f, ay2 - ay1)
        val areaB = max(0f, bx2 - bx1) * max(0f, by2 - by1)
        val union = areaA + areaB - interArea
        return if (union <= 0f) 0f else interArea / union
    }
}
