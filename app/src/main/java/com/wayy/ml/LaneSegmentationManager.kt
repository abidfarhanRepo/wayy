package com.wayy.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.wayy.debug.DiagnosticLogger
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

data class LanePoint(
    val x: Float,
    val y: Float
)

data class LaneSegmentationResult(
    val leftLane: List<LanePoint>,
    val rightLane: List<LanePoint>
)

private data class LaneResolvedModelPath(
    val type: LaneModelPathType,
    val path: String
)

private enum class LaneModelPathType {
    ASSET,
    FILE
}

class LaneSegmentationManager(
    private val context: Context,
    private val diagnosticLogger: DiagnosticLogger? = null
) {
    private var interpreter: Interpreter? = null
    private var activeModelPath: String? = null
    private var inputWidth: Int = 0
    private var inputHeight: Int = 0
    private var inputDataType: DataType = DataType.FLOAT32
    private var imageProcessor: ImageProcessor? = null

    fun checkAvailability(modelPath: String): MlAvailability {
        val resolved = resolveModelPath(modelPath)
        return when (resolved.type) {
            LaneModelPathType.ASSET -> {
                try {
                    context.assets.openFd(resolved.path).close()
                    MlAvailability(true)
                } catch (e: IOException) {
                    MlAvailability(false, "Lane model missing: add app/src/main/assets/${resolved.path}")
                }
            }
            LaneModelPathType.FILE -> {
                val file = java.io.File(resolved.path)
                if (file.exists()) {
                    MlAvailability(true)
                } else {
                    MlAvailability(false, "Lane model missing: ${resolved.path}")
                }
            }
        }
    }

    fun start(modelPath: String): MlAvailability {
        val availability = checkAvailability(modelPath)
        if (!availability.isAvailable) {
            diagnosticLogger?.log(
                tag = TAG,
                message = "Lane model missing",
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
                diagnosticLogger?.log(tag = TAG, message = "Lane model loaded")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load lane model", e)
                return MlAvailability(false, "Failed to load lane model: ${e.message ?: "unknown"}")
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
    }

    fun isActive(): Boolean = interpreter != null

    fun runSegmentation(bitmap: Bitmap): LaneSegmentationResult? {
        val interpreter = interpreter ?: return null
        if (inputWidth == 0 || inputHeight == 0) {
            configureInput(interpreter)
        }
        if (inputWidth == 0 || inputHeight == 0) return null

        val inputImage = TensorImage(inputDataType)
        inputImage.load(bitmap)
        val processedImage = imageProcessor?.process(inputImage) ?: inputImage

        val outputTensorBuffers = HashMap<Int, TensorBuffer>()
        val outputs = HashMap<Int, Any>()
        for (index in 0 until interpreter.outputTensorCount) {
            val outputTensor = interpreter.getOutputTensor(index)
            val buffer = TensorBuffer.createFixedSize(outputTensor.shape(), outputTensor.dataType())
            outputTensorBuffers[index] = buffer
            outputs[index] = buffer.buffer
        }
        interpreter.runForMultipleInputsOutputs(arrayOf(processedImage.buffer), outputs)

        val output0 = outputTensorBuffers[0]
        val output1 = outputTensorBuffers[1]
        val laneMask = if (output1 != null) {
            decodeYoloSeg(output0, output1, inputWidth, inputHeight)
        } else {
            decodeMask(output0)
        } ?: return null

        val result = buildLanePoints(laneMask.mask, laneMask.width, laneMask.height)
        if (result != null) {
            Log.d(TAG, "Lane points left=${result.leftLane.size} right=${result.rightLane.size}")
        }
        return result
    }

    private fun buildLanePoints(mask: FloatArray, width: Int, height: Int): LaneSegmentationResult? {
        val left = mutableListOf<LanePoint>()
        val right = mutableListOf<LanePoint>()
        val startRow = (height * 0.45f).toInt().coerceAtLeast(0)
        for (row in height - 1 downTo startRow step 4) {
            val offset = row * width
            var leftIdx: Int? = null
            var rightIdx: Int? = null
            for (col in 0 until width) {
                if (mask[offset + col] > MASK_THRESHOLD) {
                    leftIdx = col
                    break
                }
            }
            for (col in width - 1 downTo 0) {
                if (mask[offset + col] > MASK_THRESHOLD) {
                    rightIdx = col
                    break
                }
            }
            val hasGap = leftIdx != null && rightIdx != null && (rightIdx - leftIdx) < width * 0.02f
            if (hasGap) {
                continue
            }
            if (leftIdx != null) {
                val y = row / height.toFloat()
                left.add(LanePoint(leftIdx / width.toFloat(), y))
            }
            if (rightIdx != null) {
                val y = row / height.toFloat()
                right.add(LanePoint(rightIdx / width.toFloat(), y))
            }
        }
        if (left.isEmpty() && right.isEmpty()) return null
        return LaneSegmentationResult(left, right)
    }

    private data class LaneMask(val mask: FloatArray, val width: Int, val height: Int)

    private fun decodeMask(output: TensorBuffer?): LaneMask? {
        if (output == null) return null
        val shape = output.shape
        if (shape.size != 4) return null
        val h: Int
        val w: Int
        val channelsLast = shape[shape.lastIndex] <= 4
        val data = output.floatArray
        if (channelsLast) {
            h = shape[1]
            w = shape[2]
            val mask = FloatArray(w * h)
            var idx = 0
            for (y in 0 until h) {
                for (x in 0 until w) {
                    mask[idx++] = data[(y * w + x) * shape[3]]
                }
            }
            return LaneMask(mask, w, h)
        }
        h = shape[2]
        w = shape[3]
        val mask = FloatArray(w * h)
        var idx = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                mask[idx++] = data[y * w + x]
            }
        }
        return LaneMask(mask, w, h)
    }

    private fun decodeYoloSeg(
        output0: TensorBuffer?,
        output1: TensorBuffer,
        inputWidth: Int,
        inputHeight: Int
    ): LaneMask? {
        if (output0 == null) return null
        val detShape = normalizeDetShape(output0.shape) ?: return null
        var channels = detShape[1]
        var boxes = detShape[2]
        var channelsFirst = true
        if (channels > boxes) {
            val temp = channels
            channels = boxes
            boxes = temp
            channelsFirst = false
        }

        val protoShape = output1.shape
        if (protoShape.size != 4) return null
        val protoData = output1.floatArray
        val maskDim: Int
        val maskH: Int
        val maskW: Int
        val protoLayoutMaskFirst = protoShape[1] < protoShape[2]
        if (protoLayoutMaskFirst) {
            maskDim = protoShape[1]
            maskH = protoShape[2]
            maskW = protoShape[3]
        } else {
            maskH = protoShape[1]
            maskW = protoShape[2]
            maskDim = protoShape[3]
        }

        val numClasses = channels - 4 - maskDim
        if (numClasses <= 0) return null
        val detData = output0.floatArray
        var bestScore = 0f
        var bestCoeffs: FloatArray? = null
        for (b in 0 until boxes) {
            var classScore = 0f
            for (c in 0 until numClasses) {
                val raw = readValue(detData, channelsFirst, boxes, channels, 4 + c, b)
                val score = if (raw > 1f) sigmoid(raw) else raw
                if (score > classScore) {
                    classScore = score
                }
            }
            if (classScore > bestScore) {
                bestScore = classScore
                val coeffs = FloatArray(maskDim)
                for (m in 0 until maskDim) {
                    coeffs[m] = readValue(detData, channelsFirst, boxes, channels, 4 + numClasses + m, b)
                }
                bestCoeffs = coeffs
            }
        }
        if (bestCoeffs == null || bestScore < 0.05f) return null
        val mask = FloatArray(maskH * maskW)
        if (protoLayoutMaskFirst) {
            for (m in 0 until maskDim) {
                val coeff = bestCoeffs[m]
                val offset = m * maskH * maskW
                for (i in 0 until maskH * maskW) {
                    mask[i] += coeff * protoData[offset + i]
                }
            }
        } else {
            for (y in 0 until maskH) {
                for (x in 0 until maskW) {
                    var value = 0f
                    val base = (y * maskW + x) * maskDim
                    for (m in 0 until maskDim) {
                        value += bestCoeffs[m] * protoData[base + m]
                    }
                    mask[y * maskW + x] = value
                }
            }
        }
        for (i in mask.indices) {
            mask[i] = sigmoid(mask[i])
        }
        return LaneMask(mask, maskW, maskH)
    }

    private fun normalizeDetShape(shape: IntArray): IntArray? {
        if (shape.size == 3) return shape
        val squeezed = shape.filter { it != 1 }.toIntArray()
        return when (squeezed.size) {
            2 -> intArrayOf(1, squeezed[0], squeezed[1])
            3 -> squeezed
            else -> null
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

    private fun sigmoid(value: Float): Float {
        return (1f / (1f + exp(-value)))
    }

    private fun loadModelFile(modelPath: String): MappedByteBuffer {
        val resolved = resolveModelPath(modelPath)
        return when (resolved.type) {
            LaneModelPathType.ASSET -> {
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
            LaneModelPathType.FILE -> {
                FileInputStream(resolved.path).use { inputStream ->
                    val fileChannel = inputStream.channel
                    fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size())
                }
            }
        }
    }

    private fun resolveModelPath(modelPath: String): LaneResolvedModelPath {
        val trimmed = modelPath.trim()
        return when {
            trimmed.startsWith(FILE_PREFIX) -> LaneResolvedModelPath(
                LaneModelPathType.FILE,
                trimmed.removePrefix(FILE_PREFIX)
            )
            trimmed.startsWith(ASSET_PREFIX) -> LaneResolvedModelPath(
                LaneModelPathType.ASSET,
                trimmed.removePrefix(ASSET_PREFIX)
            )
            trimmed.startsWith("/") -> LaneResolvedModelPath(LaneModelPathType.FILE, trimmed)
            else -> LaneResolvedModelPath(LaneModelPathType.ASSET, trimmed)
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
        if (shape.size != 4) return
        val isNhwc = shape.last() == 3
        if (!isNhwc) return
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
        private const val TAG = "WayyLane"
        private const val DEFAULT_NUM_THREADS = 4
        private const val ASSET_PREFIX = "asset://"
        private const val FILE_PREFIX = "file://"
        private const val MASK_THRESHOLD = 0.35f
    }
}
