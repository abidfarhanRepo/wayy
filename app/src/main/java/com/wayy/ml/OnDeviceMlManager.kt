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

data class MlDetection(
    val classId: Int,
    val score: Float,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

class OnDeviceMlManager(
    private val context: Context,
    private val diagnosticLogger: DiagnosticLogger? = null
) {
    private var interpreter: Interpreter? = null
    private var activeModelPath: String? = null
    private var inputWidth: Int = 0
    private var inputHeight: Int = 0
    private var inputDataType: DataType = DataType.FLOAT32
    private var imageProcessor: ImageProcessor? = null

    fun checkAvailability(modelPath: String = DEFAULT_MODEL_ASSET): MlAvailability {
        val resolved = resolveModelPath(modelPath)
        return when (resolved.type) {
            ModelPathType.ASSET -> {
                try {
                    context.assets.openFd(resolved.path).close()
                    MlAvailability(true)
                } catch (e: IOException) {
                    MlAvailability(false, "ML model missing: add app/src/main/assets/${resolved.path}")
                }
            }
            ModelPathType.FILE -> {
                val file = java.io.File(resolved.path)
                if (file.exists()) {
                    MlAvailability(true)
                } else {
                    MlAvailability(false, "ML model missing: ${resolved.path}")
                }
            }
        }
    }

    fun start(modelPath: String = DEFAULT_MODEL_ASSET): MlAvailability {
        val availability = checkAvailability(modelPath)
        if (!availability.isAvailable) {
            diagnosticLogger?.log(
                tag = TAG,
                message = "ML model missing",
                level = "ERROR",
                data = mapOf("modelPath" to modelPath)
            )
            return availability
        }
        val normalized = normalizeModelPath(modelPath)
        if (interpreter != null && activeModelPath != normalized) {
            stop()
        }
        if (interpreter == null) {
            try {
                val options = Interpreter.Options().setNumThreads(DEFAULT_NUM_THREADS)
                val created = Interpreter(loadModelFile(modelPath), options)
                configureInput(created)
                interpreter = created
                activeModelPath = normalized
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
        val outputTensorBuffers = HashMap<Int, TensorBuffer>()
        for (index in 0 until interpreter.outputTensorCount) {
            val outputTensor = interpreter.getOutputTensor(index)
            val buffer = TensorBuffer.createFixedSize(outputTensor.shape(), outputTensor.dataType())
            outputTensorBuffers[index] = buffer
            outputBuffers[index] = buffer.buffer
            outputShapes.add(outputTensor.shape())
        }

        val startTime = System.nanoTime()
        interpreter.runForMultipleInputsOutputs(arrayOf(processedImage.buffer), outputBuffers)
        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0
        val detections = parseDetections(
            outputTensorBuffers[0],
            outputShapes.firstOrNull(),
            inputWidth,
            inputHeight
        )
        return MlInferenceResult(
            inferenceMs = elapsedMs,
            inputShape = intArrayOf(1, inputHeight, inputWidth, 3),
            outputShapes = outputShapes,
            detections = detections
        )
    }

    private fun loadModelFile(modelPath: String): MappedByteBuffer {
        val resolved = resolveModelPath(modelPath)
        return when (resolved.type) {
            ModelPathType.ASSET -> {
                val assetFileDescriptor = context.assets.openFd(resolved.path)
                FileInputStream(assetFileDescriptor.fileDescriptor).use { inputStream ->
                    val fileChannel = inputStream.channel
                    fileChannel.map(
                        FileChannel.MapMode.READ_ONLY,
                        assetFileDescriptor.startOffset,
                        assetFileDescriptor.declaredLength
                    )
                }
            }
            ModelPathType.FILE -> {
                FileInputStream(resolved.path).use { inputStream ->
                    val fileChannel = inputStream.channel
                    fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size())
                }
            }
        }
    }

    private fun resolveModelPath(modelPath: String): ResolvedModelPath {
        val trimmed = modelPath.trim()
        return when {
            trimmed.startsWith(FILE_PREFIX) -> ResolvedModelPath(
                ModelPathType.FILE,
                trimmed.removePrefix(FILE_PREFIX)
            )
            trimmed.startsWith(ASSET_PREFIX) -> ResolvedModelPath(
                ModelPathType.ASSET,
                trimmed.removePrefix(ASSET_PREFIX)
            )
            trimmed.startsWith("/") -> ResolvedModelPath(ModelPathType.FILE, trimmed)
            else -> ResolvedModelPath(ModelPathType.ASSET, trimmed)
        }
    }

    private fun normalizeModelPath(modelPath: String): String {
        val resolved = resolveModelPath(modelPath)
        return "${resolved.type.name}:${resolved.path}"
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

    private fun parseDetections(
        buffer: TensorBuffer?,
        shape: IntArray?,
        inputWidth: Int,
        inputHeight: Int
    ): List<MlDetection> {
        if (buffer == null || shape == null) return emptyList()
        val normalizedShape = normalizeOutputShape(shape) ?: return emptyList()
        var channels = normalizedShape[1]
        var boxes = normalizedShape[2]
        var channelsFirst = true
        if (channels > boxes) {
            val temp = channels
            channels = boxes
            boxes = temp
            channelsFirst = false
        }
        if (channels < 6 || boxes <= 0) return emptyList()
        val data = try {
            buffer.floatArray
        } catch (e: Exception) {
            return emptyList()
        }
        val detections = ArrayList<MlDetection>()
        val threshold = 0.08f
        val minSize = 0.02f
        for (b in 0 until boxes) {
            val x = normalizeCoord(
                readValue(data, channelsFirst, boxes, channels, 0, b),
                inputWidth
            )
            val y = normalizeCoord(
                readValue(data, channelsFirst, boxes, channels, 1, b),
                inputHeight
            )
            val w = normalizeCoord(
                readValue(data, channelsFirst, boxes, channels, 2, b),
                inputWidth
            )
            val h = normalizeCoord(
                readValue(data, channelsFirst, boxes, channels, 3, b),
                inputHeight
            )
            var bestScore = 0f
            var bestClass = -1
            for (c in 4 until channels) {
                val rawScore = readValue(data, channelsFirst, boxes, channels, c, b)
                val score = if (rawScore > 1f) sigmoid(rawScore) else rawScore
                if (score > bestScore) {
                    bestScore = score
                    bestClass = c - 4
                }
            }
            if (bestClass >= 0 && bestScore >= threshold) {
                if (w < minSize || h < minSize) continue
                detections.add(
                    MlDetection(
                        classId = bestClass,
                        score = bestScore,
                        x = x.coerceIn(0f, 1f),
                        y = y.coerceIn(0f, 1f),
                        width = w.coerceIn(0f, 1f),
                        height = h.coerceIn(0f, 1f)
                    )
                )
            }
        }
        detections.sortByDescending { it.score }
        return detections.take(20)
    }

    private fun normalizeOutputShape(shape: IntArray): IntArray? {
        if (shape.size == 3) return shape
        val squeezed = shape.filter { it != 1 }.toIntArray()
        return when (squeezed.size) {
            2 -> intArrayOf(1, squeezed[0], squeezed[1])
            3 -> squeezed
            else -> null
        }
    }

    private fun sigmoid(value: Float): Float {
        return (1f / (1f + kotlin.math.exp(-value)))
    }

    private fun normalizeCoord(raw: Float, size: Int): Float {
        return when {
            raw < 0f -> sigmoid(raw)
            raw > 1.5f && size > 0 -> (raw / size).coerceIn(0f, 1f)
            raw > 1.5f -> sigmoid(raw)
            else -> raw
        }
    }

    private fun readValue(
        data: FloatArray,
        channelsFirst: Boolean,
        boxes: Int,
        channels: Int,
        channel: Int,
        boxIndex: Int
    ): Float {
        val index = if (channelsFirst) {
            channel * boxes + boxIndex
        } else {
            boxIndex * channels + channel
        }
        return data.getOrNull(index) ?: 0f
    }

    companion object {
        private const val TAG = "WayyML"
        private const val DEFAULT_NUM_THREADS = 4
        const val DEFAULT_MODEL_ASSET = "ml/model.tflite"
        private const val ASSET_PREFIX = "asset://"
        private const val FILE_PREFIX = "file://"
    }
}

private enum class ModelPathType {
    ASSET,
    FILE
}

private data class ResolvedModelPath(
    val type: ModelPathType,
    val path: String
)

data class MlInferenceResult(
    val inferenceMs: Double,
    val inputShape: IntArray,
    val outputShapes: List<IntArray>,
    val detections: List<MlDetection> = emptyList()
)
