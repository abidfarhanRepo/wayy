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
    private val isEnabled: () -> Boolean
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
                diagnosticLogger?.log(
                    tag = TAG,
                    message = "ML inference",
                    data = mapOf(
                        "ms" to result.inferenceMs,
                        "input" to result.inputShape.joinToString(),
                        "outputs" to result.outputShapes.joinToString { it.joinToString() }
                    )
                )
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
        val pixels = ByteArray(rowLength * height)
        var offset = 0
        for (row in 0 until height) {
            buffer.position(row * rowStride)
            buffer.get(pixels, offset, rowLength)
            offset += rowLength
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(pixels))
        return bitmap
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
