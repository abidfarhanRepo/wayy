package com.wayy.ml

import android.content.Context
import android.util.Log
import com.wayy.debug.DiagnosticLogger
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import android.graphics.Bitmap
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
    private var inputWidth: Int = 0
    private var inputHeight: Int = 0
    private var inputDataType: DataType = DataType.FLOAT32
    private var imageProcessor: ImageProcessor? = null

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
                val options = Interpreter.Options().setNumThreads(DEFAULT_NUM_THREADS)
                val created = Interpreter(loadModelFile(modelAsset), options)
                configureInput(created)
                interpreter = created
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
        imageProcessor = null
        inputWidth = 0
        inputHeight = 0
        diagnosticLogger?.log(tag = TAG, message = "ML scanning stopped")
    }

    fun isActive(): Boolean = interpreter != null

    fun runInference(bitmap: Bitmap): MlInferenceResult? {
        val interpreter = interpreter ?: return null
        if (inputWidth == 0 || inputHeight == 0) {
            configureInput(interpreter)
        }
        if (inputWidth == 0 || inputHeight == 0) return null

        val inputImage = TensorImage(inputDataType)
        inputImage.load(bitmap)
        val processedImage = imageProcessor?.process(inputImage) ?: inputImage

        val outputShapes = mutableListOf<IntArray>()
        val outputBuffers = HashMap<Int, Any>()
        for (index in 0 until interpreter.outputTensorCount) {
            val outputTensor = interpreter.getOutputTensor(index)
            val buffer = TensorBuffer.createFixedSize(outputTensor.shape(), outputTensor.dataType())
            outputBuffers[index] = buffer.buffer
            outputShapes.add(outputTensor.shape())
        }

        val startTime = System.nanoTime()
        interpreter.runForMultipleInputsOutputs(arrayOf(processedImage.buffer), outputBuffers)
        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0
        return MlInferenceResult(
            inferenceMs = elapsedMs,
            inputShape = intArrayOf(1, inputHeight, inputWidth, 3),
            outputShapes = outputShapes
        )
    }

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

    private fun configureInput(interpreter: Interpreter) {
        val inputTensor = interpreter.getInputTensor(0)
        val shape = inputTensor.shape()
        val dataType = inputTensor.dataType()
        if (shape.size != 4) {
            diagnosticLogger?.log(
                tag = TAG,
                message = "Unsupported input shape",
                level = "ERROR",
                data = mapOf("shape" to shape.joinToString())
            )
            return
        }
        val isNhwc = shape.last() == 3
        if (!isNhwc) {
            diagnosticLogger?.log(
                tag = TAG,
                message = "Unsupported input layout (expected NHWC)",
                level = "ERROR",
                data = mapOf("shape" to shape.joinToString())
            )
            return
        }
        inputHeight = shape[1]
        inputWidth = shape[2]
        inputDataType = dataType
        val processorBuilder = ImageProcessor.Builder()
            .add(ResizeOp(inputHeight, inputWidth, ResizeOp.ResizeMethod.BILINEAR))
        if (dataType == DataType.FLOAT32) {
            processorBuilder.add(NormalizeOp(0f, 255f))
        }
        imageProcessor = processorBuilder.build()
    }

    companion object {
        private const val TAG = "WayyML"
        private const val DEFAULT_NUM_THREADS = 4
        const val DEFAULT_MODEL_ASSET = "ml/model.tflite"
    }
}

data class MlInferenceResult(
    val inferenceMs: Double,
    val inputShape: IntArray,
    val outputShapes: List<IntArray>
)
