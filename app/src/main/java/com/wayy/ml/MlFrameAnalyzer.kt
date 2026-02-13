package com.wayy.ml

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.wayy.debug.DiagnosticLogger
import java.nio.ByteBuffer

class MlFrameAnalyzer(
    private val mlManager: OnDeviceMlManager,
    private val diagnosticLogger: DiagnosticLogger? = null,
    private val isEnabled: () -> Boolean,
    private val onInference: ((MlInferenceResult) -> Unit)? = null,
    private val laneManager: LaneSegmentationManager? = null,
    private val onLaneResult: ((LaneSegmentationResult) -> Unit)? = null
) : ImageAnalysis.Analyzer {
    private var lastInferenceMs = 0L

    override fun analyze(image: ImageProxy) {
        try {
            if (!isEnabled() || !mlManager.isActive()) {
                return
            }
            val now = SystemClock.elapsedRealtime()
            if (now - lastInferenceMs < ANALYSIS_THROTTLE_MS) {
                return
            }
            lastInferenceMs = now
            val bitmap = imageProxyToBitmap(image) ?: return
            val rotated = rotateBitmap(bitmap, image.imageInfo.rotationDegrees)
            val result = mlManager.runInference(rotated)
            if (result != null) {
                onInference?.invoke(result)
                diagnosticLogger?.log(
                    tag = TAG,
                    message = "ML inference",
                    data = mapOf(
                        "ms" to result.inferenceMs,
                        "input" to result.inputShape.joinToString(),
                        "outputs" to result.outputShapes.joinToString { it.joinToString() },
                        "detections" to result.detections.size
                    )
                )
                Log.d(
                    TAG,
                    "Inference ${"%.1f".format(result.inferenceMs)}ms outputs=${result.outputShapes.size} dets=${result.detections.size}"
                )
            }
            val lane = laneManager?.takeIf { it.isActive() }?.runSegmentation(rotated)
            if (lane != null) {
                onLaneResult?.invoke(lane)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed", e)
        } finally {
            image.close()
        }
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val width = image.width
        val height = image.height
        val buffer = plane.buffer
        buffer.rewind()
        val rowLength = width * pixelStride
        val rgba = ByteArray(rowLength * height)
        var offset = 0
        for (row in 0 until height) {
            buffer.position(row * rowStride)
            buffer.get(rgba, offset, rowLength)
            offset += rowLength
        }
        val argbPixels = IntArray(width * height)
        var pixelIndex = 0
        var byteIndex = 0
        while (pixelIndex < argbPixels.size && byteIndex + 3 < rgba.size) {
            val r = rgba[byteIndex].toInt() and 0xFF
            val g = rgba[byteIndex + 1].toInt() and 0xFF
            val b = rgba[byteIndex + 2].toInt() and 0xFF
            val a = rgba[byteIndex + 3].toInt() and 0xFF
            argbPixels[pixelIndex] = (a shl 24) or (r shl 16) or (g shl 8) or b
            pixelIndex += 1
            byteIndex += pixelStride
        }
        return Bitmap.createBitmap(argbPixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) {
            bitmap.recycle()
        }
        return rotated
    }

    companion object {
        private const val TAG = "WayyML"
        private const val ANALYSIS_THROTTLE_MS = 300L
    }
}
