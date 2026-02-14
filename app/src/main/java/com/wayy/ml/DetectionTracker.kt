package com.wayy.ml

import android.os.SystemClock
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class DetectionTracker(
    private val iouThreshold: Float = 0.3f,
    private val smoothing: Float = 0.6f,
    private val maxAgeMs: Long = 900L,
    private val minConfidence: Float = 0.08f,
    private val maxTracks: Int = 30
) {
    private data class Track(
        var detection: MlDetection,
        var lastUpdatedMs: Long,
        var age: Int = 0,
        var visibleCount: Int = 1,
        var totalVisibleCount: Int = 1
    )

    private val tracks = mutableListOf<Track>()
    private var lastUpdateMs: Long = 0

    fun update(detections: List<MlDetection>): List<MlDetection> {
        val now = SystemClock.elapsedRealtime()
        lastUpdateMs = now

        val validDetections = detections
            .filter { it.score >= minConfidence }
            .filter { it.width > 0.01f && it.height > 0.01f }
            .take(maxTracks)

        val availableTracks = tracks.toMutableList()
        val updatedTracks = mutableListOf<Track>()

        for (detection in validDetections) {
            val match = availableTracks
                .filter { it.detection.classId == detection.classId }
                .maxByOrNull { iou(it.detection, detection) }

            if (match != null && iou(match.detection, detection) >= iouThreshold) {
                match.detection = smooth(match.detection, detection, match.age)
                match.lastUpdatedMs = now
                match.age++
                match.visibleCount++
                match.totalVisibleCount++
                updatedTracks.add(match)
                availableTracks.remove(match)
            } else {
                val newTrack = Track(
                    detection = detection,
                    lastUpdatedMs = now,
                    age = 0,
                    visibleCount = 1,
                    totalVisibleCount = 1
                )
                updatedTracks.add(newTrack)
            }
        }

        for (track in availableTracks) {
            val ageMs = now - track.lastUpdatedMs
            if (ageMs <= maxAgeMs && track.visibleCount >= MIN_VISIBLE_FOR_TENTATIVE) {
                track.visibleCount = 0
                updatedTracks.add(track)
            }
        }

        tracks.clear()
        tracks.addAll(updatedTracks.sortedByDescending { it.detection.score }.take(maxTracks))

        return tracks
            .filter { it.visibleCount >= MIN_VISIBLE_FOR_CONFIRMED || it.totalVisibleCount >= MIN_TOTAL_VISIBLE }
            .map { it.detection }
    }

    private fun smooth(previous: MlDetection, current: MlDetection, trackAge: Int): MlDetection {
        val adaptiveAlpha = if (trackAge < CONFIDENCE_RAMP_UP_FRAMES) {
            smoothing * (1f - trackAge / CONFIDENCE_RAMP_UP_FRAMES.toFloat())
        } else {
            smoothing
        }

        val clampedAlpha = adaptiveAlpha.coerceIn(MIN_SMOOTHING, MAX_SMOOTHING)

        val smoothedX = previous.x * (1 - clampedAlpha) + current.x * clampedAlpha
        val smoothedY = previous.y * (1 - clampedAlpha) + current.y * clampedAlpha
        val smoothedW = previous.width * (1 - clampedAlpha) + current.width * clampedAlpha
        val smoothedH = previous.height * (1 - clampedAlpha) + current.height * clampedAlpha

        val smoothedScore = if (current.score > previous.score) {
            current.score
        } else {
            previous.score * SCORE_DECAY + current.score * (1 - SCORE_DECAY)
        }

        return MlDetection(
            classId = current.classId,
            score = smoothedScore.coerceIn(0f, 1f),
            x = smoothedX.coerceIn(0f, 1f),
            y = smoothedY.coerceIn(0f, 1f),
            width = smoothedW.coerceIn(MIN_DETECTION_SIZE, 1f),
            height = smoothedH.coerceIn(MIN_DETECTION_SIZE, 1f)
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

    fun getTrackCount(): Int = tracks.size

    fun clear() {
        tracks.clear()
    }

    companion object {
        private const val MIN_VISIBLE_FOR_CONFIRMED = 2
        private const val MIN_VISIBLE_FOR_TENTATIVE = 1
        private const val MIN_TOTAL_VISIBLE = 2
        private const val CONFIDENCE_RAMP_UP_FRAMES = 5
        private const val MIN_SMOOTHING = 0.3f
        private const val MAX_SMOOTHING = 0.8f
        private const val SCORE_DECAY = 0.7f
        private const val MIN_DETECTION_SIZE = 0.01f
    }
}