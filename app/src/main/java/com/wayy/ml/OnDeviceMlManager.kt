package com.wayy.ml

import android.content.Context
import android.util.Log
import com.wayy.debug.DiagnosticLogger
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

data class MlAvailability(
    val isAvailable: Boolean,
    val message: String? = null
)

class OnDeviceMlManager(
    private val context: Context,
    private val diagnosticLogger: DiagnosticLogger? = null
) {
    private var interpreter: Interpreter? = null

    fun checkAvailability(modelAsset: String = DEFAULT_MODEL_ASSET): MlAvailability {
        return try {
            context.assets.openFd(modelAsset).close()
            MlAvailability(true)
        } catch (e: IOException) {
            MlAvailability(false, "ML model missing: add app/src/main/assets/$modelAsset")
        }
    }

    fun start(modelAsset: String = DEFAULT_MODEL_ASSET): MlAvailability {
        val availability = checkAvailability(modelAsset)
        if (!availability.isAvailable) {
            diagnosticLogger?.log(
                tag = TAG,
                message = "ML model missing",
                level = "ERROR",
                data = mapOf("modelAsset" to modelAsset)
            )
            return availability
        }
        if (interpreter == null) {
            try {
                interpreter = Interpreter(loadModelFile(modelAsset))
                diagnosticLogger?.log(tag = TAG, message = "ML model loaded")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load ML model", e)
                return MlAvailability(false, "Failed to load ML model: ${e.message ?: "unknown"}")
            }
        }
        return availability
    }

    fun stop() {
        interpreter?.close()
        interpreter = null
        diagnosticLogger?.log(tag = TAG, message = "ML scanning stopped")
    }

    fun isActive(): Boolean = interpreter != null

    private fun loadModelFile(modelAsset: String): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(modelAsset)
        FileInputStream(assetFileDescriptor.fileDescriptor).use { inputStream ->
            val fileChannel = inputStream.channel
            return fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                assetFileDescriptor.startOffset,
                assetFileDescriptor.declaredLength
            )
        }
    }

    companion object {
        private const val TAG = "WayyML"
        const val DEFAULT_MODEL_ASSET = "ml/model.tflite"
    }
}
