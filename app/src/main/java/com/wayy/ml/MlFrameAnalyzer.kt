package com.wayy.ml

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.wayy.debug.DiagnosticLogger
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class MlFrameAnalyzer(
    private val mlManager: OnDeviceMlManager,
    private val diagnosticLogger: DiagnosticLogger? = null,
    private val isEnabled: () -> Boolean,
    private val onInference: ((MlInferenceResult) -> Unit)? = null,
    private val laneManager: LaneSegmentationManager? = null,
    private val onLaneResult: ((LaneSegmentationResult) -> Unit)? = null
) : ImageAnalysis.Analyzer {
    private var lastInferenceMs = 0L
    private val isProcessing = AtomicBoolean(false)
    private val consecutiveFailures = AtomicLong(0)
    private val lastSuccessfulInference = AtomicLong(0)
    private var adaptiveThrottleMs = BASE_THROTTLE_MS

    override fun analyze(image: ImageProxy) {
        try {
            if (!isEnabled()) {
                return
            }

            if (!mlManager.isActive() && laneManager?.isActive() != true) {
                return
            }

            val now = SystemClock.elapsedRealtime()
            val throttleMs = calculateThrottle()

            if (now - lastInferenceMs < throttleMs) {
                return
            }

            if (!isProcessing.compareAndSet(false, true)) {
                return
            }

            lastInferenceMs = now

            val bitmap = imageProxyToBitmap(image)
            if (bitmap == null) {
                Log.w(TAG, "Failed to convert image to bitmap")
                isProcessing.set(false)
                return
            }

            if (bitmap.width <= 0 || bitmap.height <= 0) {
                Log.w(TAG, "Invalid bitmap dimensions: ${bitmap.width}x${bitmap.height}")
                bitmap.recycle()
                isProcessing.set(false)
                return
            }

            val rotated = rotateBitmap(bitmap, image.imageInfo.rotationDegrees)

            var inferenceFailed = false

            if (mlManager.isActive()) {
                try {
                    val result = mlManager.runInference(rotated)
                    if (result != null) {
                        consecutiveFailures.set(0)
                        lastSuccessfulInference.set(now)
                        updateAdaptiveThrottle(result.inferenceMs)

                        onInference?.invoke(result)
                        diagnosticLogger?.log(
                            tag = TAG,
                            message = "ML inference",
                            data = mapOf(
                                "ms" to result.inferenceMs,
                                "backend" to result.backend.name,
                                "input" to result.inputShape.joinToString(),
                                "outputs" to result.outputShapes.joinToString { it.joinToString() },
                                "detections" to result.detections.size,
                                "avgMs" to result.avgInferenceMs
                            )
                        )
                        Log.d(
                            TAG,
                            "Inference ${"%.1f".format(result.inferenceMs)}ms (${result.backend}) dets=${result.detections.size}"
                        )
                    } else {
                        inferenceFailed = true
                    }
                } catch (e: Exception) {
                    inferenceFailed = true
                    Log.e(TAG, "ML inference exception: ${e.message}")
                }
            }

            if (laneManager?.isActive() == true) {
                try {
                    val lane = laneManager.runSegmentation(rotated)
                    if (lane != null) {
                        onLaneResult?.invoke(lane)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Lane segmentation exception: ${e.message}")
                }
            }

            if (inferenceFailed) {
                val failures = consecutiveFailures.incrementAndGet()
                if (failures >= MAX_CONSECUTIVE_FAILURES) {
                    Log.w(TAG, "Too many consecutive failures, increasing throttle")
                    adaptiveThrottleMs = (adaptiveThrottleMs * 1.5).toLong().coerceAtMost(MAX_THROTTLE_MS)
                }
            }

            if (rotated != bitmap) {
                rotated.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Frame analysis failed", e)
            diagnosticLogger?.log(
                tag = TAG,
                message = "Frame analysis failed",
                level = "ERROR",
                data = mapOf("error" to e.message)
            )
        } finally {
            isProcessing.set(false)
            image.close()
        }
    }

    private fun calculateThrottle(): Long {
        val failures = consecutiveFailures.get()
        return when {
            failures > MAX_CONSECUTIVE_FAILURES -> MAX_THROTTLE_MS
            failures > CONSECUTIVE_FAILURES_WARNING -> (adaptiveThrottleMs * 1.25).toLong().coerceAtMost(MAX_THROTTLE_MS)
            else -> adaptiveThrottleMs
        }
    }

    private fun updateAdaptiveThrottle(inferenceMs: Double) {
        adaptiveThrottleMs = when {
            inferenceMs > PERFORMANCE_THRESHOLD_SLOW_MS -> (adaptiveThrottleMs * 1.1).toLong().coerceAtMost(MAX_THROTTLE_MS)
            inferenceMs < PERFORMANCE_THRESHOLD_FAST_MS -> (adaptiveThrottleMs * 0.95).toLong().coerceAtLeast(BASE_THROTTLE_MS)
            else -> adaptiveThrottleMs
        }
    }

    fun getPerformanceMetrics(): MlPerformanceMetrics? {
        return if (mlManager.isActive()) {
            mlManager.getPerformanceMetrics()
        } else null
    }

    fun getLanePerformanceMetrics(): LanePerformanceMetrics? {
        return laneManager?.takeIf { it.isActive() }?.getPerformanceMetrics()
    }

    fun resetMetrics() {
        adaptiveThrottleMs = BASE_THROTTLE_MS
        consecutiveFailures.set(0)
        mlManager.resetPerformanceMetrics()
        laneManager?.resetPerformanceMetrics()
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        return try {
            val plane = image.planes.firstOrNull() ?: return null
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val width = image.width
            val height = image.height

            if (width <= 0 || height <= 0) return null

            val buffer = plane.buffer
            buffer.rewind()

            val rowLength = width * pixelStride
            val rgba = ByteArray(rowLength * height)
            var offset = 0
            for (row in 0 until height) {
                val position = row * rowStride
                if (position + rowLength <= buffer.capacity()) {
                    buffer.position(position)
                    buffer.get(rgba, offset, rowLength)
                }
                offset += rowLength
            }

            val argbPixels = IntArray(width * height)
            var pixelIndex = 0
            var byteIndex = 0
            while (pixelIndex < argbPixels.size && byteIndex + pixelStride - 1 < rgba.size) {
                val r = rgba.getOrNull(byteIndex)?.toInt()?.and(0xFF) ?: 0
                val g = rgba.getOrNull(byteIndex + 1)?.toInt()?.and(0xFF) ?: 0
                val b = rgba.getOrNull(byteIndex + 2)?.toInt()?.and(0xFF) ?: 0
                val a = rgba.getOrNull(byteIndex + 3)?.toInt()?.and(0xFF) ?: 255
                argbPixels[pixelIndex] = (a shl 24) or (r shl 16) or (g shl 8) or b
                pixelIndex += 1
                byteIndex += pixelStride
            }

            Bitmap.createBitmap(argbPixels, width, height, Bitmap.Config.ARGB_8888)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert image to bitmap: ${e.message}")
            null
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return bitmap
        return try {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) {
                bitmap.recycle()
            }
            rotated
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rotate bitmap: ${e.message}")
            bitmap
        }
    }

    companion object {
        private const val TAG = "WayyML"
        private const val BASE_THROTTLE_MS = 100L
        private const val MAX_THROTTLE_MS = 500L
        private const val MAX_CONSECUTIVE_FAILURES = 5
        private const val CONSECUTIVE_FAILURES_WARNING = 3
        private const val PERFORMANCE_THRESHOLD_SLOW_MS = 150.0
        private const val PERFORMANCE_THRESHOLD_FAST_MS = 50.0
    }
}