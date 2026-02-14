package com.wayy.ml

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.wayy.debug.DiagnosticLogger
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
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

data class LanePerformanceMetrics(
    val inferenceMs: Double,
    val fps: Double,
    val backend: InferenceBackend,
    val frameCount: Long,
    val avgInferenceMs: Double
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
    private var gpuDelegate: GpuDelegate? = null
    private var nnapiDelegate: NnApiDelegate? = null
    private var activeModelPath: String? = null
    private var inputWidth: Int = 0
    private var inputHeight: Int = 0
    private var inputDataType: DataType = DataType.FLOAT32
    private var imageProcessor: ImageProcessor? = null
    private var currentBackend: InferenceBackend = InferenceBackend.CPU

    private val frameCount = AtomicLong(0)
    private val totalInferenceTime = AtomicLong(0)
    private val lastFpsTime = AtomicLong(SystemClock.elapsedRealtime())
    private val fpsFrameCount = AtomicLong(0)
    private val currentFps = AtomicLong(0)
    private val isInitialized = AtomicBoolean(false)
    private val modelChecksum = AtomicReference<String?>(null)

    private var consecutiveFailures = 0
    private val inferenceLock = Any()

    val backend: InferenceBackend get() = currentBackend
    val isReady: Boolean get() = isInitialized.get() && interpreter != null

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
                val file = File(resolved.path)
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
            val fallbackPath = "$ASSET_PREFIX${DEFAULT_LANE_ASSET_PATH}"
            val fallbackAvailability = checkAvailability(fallbackPath)
            if (fallbackAvailability.isAvailable) {
                diagnosticLogger?.log(
                    tag = TAG,
                    message = "Lane model file not found, using bundled asset",
                    level = "WARN",
                    data = mapOf("originalPath" to modelPath, "fallbackPath" to fallbackPath)
                )
                return loadModel(fallbackPath)
            }
            diagnosticLogger?.log(
                tag = TAG,
                message = "Lane model missing",
                level = "ERROR",
                data = mapOf("modelPath" to modelPath)
            )
            return availability
        }
        return loadModel(modelPath)
    }

    private fun loadModel(modelPath: String): MlAvailability {
        val normalized = normalizeModelPath(modelPath)
        if (interpreter != null && activeModelPath != normalized) {
            stop()
        }
        if (interpreter == null) {
            var lastError: Exception? = null
            for (attempt in 1..MAX_LOAD_RETRIES) {
                try {
                    val modelBuffer = loadModelFile(modelPath)
                    if (!validateModel(modelBuffer, modelPath)) {
                        throw IOException("Model validation failed: checksum mismatch or corrupted")
                    }

                    val tempInterpreter = Interpreter(modelBuffer, Interpreter.Options())
                    configureInput(tempInterpreter)
                    tempInterpreter.close()
                    
                    Log.i(TAG, "Lane model input dimensions: ${inputWidth}x${inputHeight}")
                    
                    if (inputWidth <= 0 || inputHeight <= 0) {
                        throw IOException("Invalid lane model input dimensions: ${inputWidth}x${inputHeight}")
                    }

                    val (createdInterpreter, createdGpuDelegate, createdNnapiDelegate, backend) = createInterpreterWithGpuFallback(modelBuffer)

                    val inputShape = createdInterpreter.getInputTensor(0).shape()
                    val inputSize = if (inputShape.size >= 3) inputShape[1] else 0
                    if (inputSize != EXPECTED_INPUT_SIZE) {
                        Log.w(TAG, "Model input size $inputSize differs from expected $EXPECTED_INPUT_SIZE")
                    }

                    synchronized(inferenceLock) {
                        interpreter?.close()
                        gpuDelegate?.close()
                        nnapiDelegate?.close()
                        interpreter = createdInterpreter
                        gpuDelegate = createdGpuDelegate
                        nnapiDelegate = createdNnapiDelegate
                        currentBackend = backend
                    }

                    activeModelPath = normalized
                    isInitialized.set(true)
                    consecutiveFailures = 0

                    diagnosticLogger?.log(
                        tag = TAG,
                        message = "Lane model loaded successfully",
                        data = mapOf(
                            "backend" to backend.name,
                            "inputShape" to inputShape.joinToString(),
                            "inputSize" to inputSize,
                            "attempt" to attempt
                        )
                    )
                    Log.i(TAG, "Lane model loaded on $backend: inputShape=${inputShape.joinToString()}")
                    return MlAvailability(true)

                } catch (e: Exception) {
                    lastError = e
                    Log.w(TAG, "Lane model load attempt $attempt failed: ${e.message}")
                    if (attempt < MAX_LOAD_RETRIES) {
                        Thread.sleep(RETRY_DELAY_MS)
                    }
                }
            }

            Log.e(TAG, "Failed to load lane model after $MAX_LOAD_RETRIES attempts", lastError)
            diagnosticLogger?.log(
                tag = TAG,
                message = "Failed to load lane model after retries",
                level = "ERROR",
                data = mapOf("error" to lastError?.message)
            )
            return MlAvailability(false, "Failed to load lane model: ${lastError?.message ?: "unknown"}")
        }
        return MlAvailability(true)
    }

    private fun createInterpreterWithGpuFallback(modelBuffer: MappedByteBuffer): LaneQuad<Interpreter, GpuDelegate?, NnApiDelegate?, InferenceBackend> {
        val compatList = CompatibilityList()
        val gpuAvailable = try {
            compatList.isDelegateSupportedOnThisDevice
        } catch (e: Exception) {
            Log.w(TAG, "GPU compatibility check failed: ${e.message}")
            diagnosticLogger?.log(tag = TAG, message = "GPU compatibility check failed", level = "WARN", data = mapOf("error" to e.message))
            false
        }
        
        Log.i(TAG, "GPU available for lane model: $gpuAvailable, input dims: ${inputWidth}x${inputHeight}")

        if (gpuAvailable && inputWidth > 0 && inputHeight > 0) {
            try {
                Log.i(TAG, "Attempting GPU delegate for lane model with input: ${inputWidth}x${inputHeight}")
                val gpuOptions = compatList.bestOptionsForThisDevice
                val gpuDelegate = GpuDelegate(gpuOptions)
                val options = Interpreter.Options()
                    .addDelegate(gpuDelegate)
                    .setNumThreads(DEFAULT_NUM_THREADS)

                val interpreter = Interpreter(modelBuffer, options)
                
                try {
                    val inputTensor = interpreter.getInputTensor(0)
                    val shape = inputTensor.shape()
                    Log.i(TAG, "GPU delegate for lane model initialized, input shape: ${shape.joinToString()}")
                    
                    if (shape.size == 4 && shape[0] == 1 && shape[1] > 0 && shape[2] > 0) {
                        diagnosticLogger?.log(
                            tag = TAG,
                            message = "GPU delegate initialized for lane model",
                            data = mapOf("inputShape" to shape.joinToString())
                        )
                        return LaneQuad(interpreter, gpuDelegate, null, InferenceBackend.GPU)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "GPU input tensor verification failed for lane model: ${e.message}")
                }
                
                Log.w(TAG, "GPU delegate test failed for lane model, closing and trying fallback")
                gpuDelegate.close()
                interpreter.close()
            } catch (e: Exception) {
                Log.w(TAG, "GPU delegate failed for lane model: ${e.message}")
                diagnosticLogger?.log(
                    tag = TAG,
                    message = "GPU delegate failed for lane model",
                    level = "WARN",
                    data = mapOf("error" to e.message, "inputWidth" to inputWidth, "inputHeight" to inputHeight)
                )
            }
        } else if (inputWidth <= 0 || inputHeight <= 0) {
            Log.w(TAG, "Skipping GPU for lane model: input dimensions not configured (${inputWidth}x${inputHeight})")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            try {
                Log.i(TAG, "Attempting NNAPI delegate for lane model (Android ${Build.VERSION.SDK_INT})")
                val nnapiDelegate = NnApiDelegate()
                val options = Interpreter.Options()
                    .addDelegate(nnapiDelegate)
                    .setNumThreads(DEFAULT_NUM_THREADS)
                    
                val interpreter = Interpreter(modelBuffer, options)
                val inputTensor = interpreter.getInputTensor(0)
                val shape = inputTensor.shape()
                
                Log.i(TAG, "NNAPI delegate initialized for lane model, input shape: ${shape.joinToString()}")
                diagnosticLogger?.log(
                    tag = TAG,
                    message = "NNAPI delegate initialized for lane model",
                    data = mapOf("inputShape" to shape.joinToString())
                )
                return LaneQuad(interpreter, null, nnapiDelegate, InferenceBackend.GPU)
            } catch (e: Exception) {
                Log.w(TAG, "NNAPI delegate failed for lane model: ${e.message}")
                diagnosticLogger?.log(
                    tag = TAG,
                    message = "NNAPI delegate failed for lane model",
                    level = "WARN",
                    data = mapOf("error" to e.message)
                )
            }
        }

        Log.i(TAG, "Using CPU backend for lane segmentation (XNNPACK)")
        diagnosticLogger?.log(
            tag = TAG,
            message = "Using CPU backend for lane segmentation",
            data = mapOf("inputWidth" to inputWidth, "inputHeight" to inputHeight)
        )
        val options = Interpreter.Options()
            .setNumThreads(DEFAULT_NUM_THREADS)
            .setUseXNNPACK(true)
        return LaneQuad(Interpreter(modelBuffer, options), null, null, InferenceBackend.CPU)
    }

    fun stop() {
        synchronized(inferenceLock) {
            interpreter?.close()
            interpreter = null
            gpuDelegate?.close()
            gpuDelegate = null
            nnapiDelegate?.close()
            nnapiDelegate = null
        }
        imageProcessor = null
        inputWidth = 0
        inputHeight = 0
        isInitialized.set(false)
        activeModelPath = null
        modelChecksum.set(null)
        frameCount.set(0)
        totalInferenceTime.set(0)
    }

    fun isActive(): Boolean = isInitialized.get() && interpreter != null

    fun runSegmentation(bitmap: Bitmap): LaneSegmentationResult? {
        synchronized(inferenceLock) {
            val interpreter = interpreter ?: return null

            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                Log.w(TAG, "Too many consecutive lane segmentation failures, skipping")
                return null
            }

            if (bitmap.width <= 0 || bitmap.height <= 0) {
                Log.w(TAG, "Invalid bitmap dimensions: ${bitmap.width}x${bitmap.height}")
                return null
            }

            if (inputWidth == 0 || inputHeight == 0) {
                configureInput(interpreter)
            }
            if (inputWidth == 0 || inputHeight == 0) return null

            return try {
                val startTime = System.nanoTime()

                val inputImage = TensorImage(inputDataType)
                inputImage.load(bitmap)
                val processedImage = imageProcessor?.process(inputImage) ?: inputImage

                if (processedImage.buffer.remaining() == 0) {
                    Log.w(TAG, "Empty processed image buffer for lane segmentation")
                    return null
                }

                val outputTensorBuffers = HashMap<Int, TensorBuffer>()
                val outputs = HashMap<Int, Any>()

                for (index in 0 until interpreter.outputTensorCount) {
                    try {
                        val outputTensor = interpreter.getOutputTensor(index)
                        val buffer = TensorBuffer.createFixedSize(outputTensor.shape(), outputTensor.dataType())
                        outputTensorBuffers[index] = buffer
                        outputs[index] = buffer.buffer
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to get output tensor $index for lane: ${e.message}")
                        return null
                    }
                }

                interpreter.runForMultipleInputsOutputs(arrayOf(processedImage.buffer), outputs)

                val elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0

                val output0 = outputTensorBuffers[0]
                val output1 = outputTensorBuffers[1]
                val laneMask = if (output1 != null) {
                    decodeYoloSeg(output0, output1, inputWidth, inputHeight)
                } else {
                    decodeMask(output0)
                }

                consecutiveFailures = 0
                frameCount.incrementAndGet()
                totalInferenceTime.addAndGet(elapsedMs.toLong())
                updateFps()

                if (laneMask == null) {
                    return null
                }

                val result = buildLanePoints(laneMask.mask, laneMask.width, laneMask.height)
                if (result != null) {
                    Log.d(TAG, "Lane points left=${result.leftLane.size} right=${result.rightLane.size} in ${"%.1f".format(elapsedMs)}ms")
                }
                result
            } catch (e: Exception) {
                consecutiveFailures++
                Log.e(TAG, "Lane segmentation failed (attempt $consecutiveFailures): ${e.message}")
                diagnosticLogger?.log(
                    tag = TAG,
                    message = "Lane segmentation failed",
                    level = "ERROR",
                    data = mapOf("error" to e.message, "consecutiveFailures" to consecutiveFailures)
                )
                null
            }
        }
    }

    private fun updateFps() {
        val now = SystemClock.elapsedRealtime()
        val frames = fpsFrameCount.incrementAndGet()
        val elapsed = now - lastFpsTime.get()

        if (elapsed >= FPS_CALC_INTERVAL_MS) {
            currentFps.set((frames * 1000L / elapsed))
            fpsFrameCount.set(0)
            lastFpsTime.set(now)
        }
    }

    fun getPerformanceMetrics(): LanePerformanceMetrics {
        val frames = frameCount.get()
        return LanePerformanceMetrics(
            inferenceMs = if (frames > 0) totalInferenceTime.get().toDouble() / frames else 0.0,
            fps = currentFps.get().toDouble(),
            backend = currentBackend,
            frameCount = frames,
            avgInferenceMs = if (frames > 0) totalInferenceTime.get().toDouble() / frames else 0.0
        )
    }

    fun resetPerformanceMetrics() {
        frameCount.set(0)
        totalInferenceTime.set(0)
        fpsFrameCount.set(0)
        lastFpsTime.set(SystemClock.elapsedRealtime())
        currentFps.set(0)
    }

    private fun buildLanePoints(mask: FloatArray, width: Int, height: Int): LaneSegmentationResult? {
        if (mask.isEmpty() || width <= 0 || height <= 0) return null

        val left = mutableListOf<LanePoint>()
        val right = mutableListOf<LanePoint>()
        val startRow = (height * 0.4f).toInt().coerceAtLeast(0)
        val step = 3

        for (row in height - 1 downTo startRow step step) {
            val offset = row * width
            if (offset + width > mask.size) continue

            var leftIdx: Int? = null
            var rightIdx: Int? = null
            var leftConf = 0f
            var rightConf = 0f

            for (col in 0 until width / 2) {
                val conf = mask.getOrNull(offset + col) ?: 0f
                if (conf > MASK_THRESHOLD && conf > leftConf) {
                    leftIdx = col
                    leftConf = conf
                }
            }
            
            for (col in width - 1 downTo width / 2) {
                val conf = mask.getOrNull(offset + col) ?: 0f
                if (conf > MASK_THRESHOLD && conf > rightConf) {
                    rightIdx = col
                    rightConf = conf
                }
            }

            val minLaneWidth = width * 0.08f
            if (leftIdx != null && rightIdx != null && (rightIdx - leftIdx) < minLaneWidth) {
                val centerX = (leftIdx + rightIdx) / 2
                leftIdx = centerX - (minLaneWidth / 2).toInt()
                rightIdx = centerX + (minLaneWidth / 2).toInt()
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
        
        val smoothedLeft = smoothLanePoints(left)
        val smoothedRight = smoothLanePoints(right)
        
        return LaneSegmentationResult(smoothedLeft, smoothedRight)
    }
    
    private fun smoothLanePoints(points: List<LanePoint>): List<LanePoint> {
        if (points.size < 3) return points
        
        val sorted = points.sortedBy { it.y }
        val smoothed = mutableListOf<LanePoint>()
        
        for (i in sorted.indices) {
            val prev = sorted.getOrNull(i - 1) ?: sorted[i]
            val curr = sorted[i]
            val next = sorted.getOrNull(i + 1) ?: sorted[i]
            
            val smoothX = (prev.x + curr.x * 2 + next.x) / 4f
            smoothed.add(LanePoint(smoothX, curr.y))
        }
        
        return smoothed
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
            if (h <= 0 || w <= 0) return null
            val mask = FloatArray(w * h)
            var idx = 0
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val dataIndex = (y * w + x) * shape[3]
                    mask[idx++] = data.getOrNull(dataIndex) ?: 0f
                }
            }
            return LaneMask(mask, w, h)
        }
        h = shape[2]
        w = shape[3]
        if (h <= 0 || w <= 0) return null
        val mask = FloatArray(w * h)
        var idx = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                mask[idx++] = data.getOrNull(y * w + x) ?: 0f
            }
        }
        return LaneMask(mask, w, h)
    }

    private fun decodeYoloSeg(
        output0: TensorBuffer?,
        output1: TensorBuffer,
        @Suppress("UNUSED_PARAMETER")
    inputWidth: Int,
        @Suppress("UNUSED_PARAMETER")
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
        if (protoShape.size != 4) {
            Log.w(TAG, "Unexpected proto shape: ${protoShape.joinToString()}")
            return null
        }
        val protoData = output1.floatArray
        val maskDim: Int
        val maskH: Int
        val maskW: Int
        val protoLayoutChannelsLast = protoShape[3] == EXPECTED_MASK_DIM
        if (protoLayoutChannelsLast) {
            maskH = protoShape[1]
            maskW = protoShape[2]
            maskDim = protoShape[3]
        } else if (protoShape[1] == EXPECTED_MASK_DIM) {
            maskDim = protoShape[1]
            maskH = protoShape[2]
            maskW = protoShape[3]
        } else {
            maskDim = protoShape[1].coerceAtMost(protoShape[3])
            maskH = protoShape[2]
            maskW = protoShape[3]
            Log.d(TAG, "Proto shape ambiguous: ${protoShape.joinToString()}, assuming maskDim=$maskDim")
        }

        if (maskH <= 0 || maskW <= 0 || maskDim <= 0) {
            Log.w(TAG, "Invalid mask dimensions: maskH=$maskH, maskW=$maskW, maskDim=$maskDim")
            return null
        }

        val numClasses = channels - 4 - maskDim
        if (numClasses <= 0) {
            Log.w(TAG, "Invalid numClasses=$numClasses from channels=$channels, maskDim=$maskDim")
            return null
        }
        Log.d(TAG, "YOLO seg decode: channels=$channels, boxes=$boxes, maskDim=$maskDim, maskH=$maskH, maskW=$maskW, numClasses=$numClasses")
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
        if (bestCoeffs == null || bestScore < LANE_CONFIDENCE_THRESHOLD) {
            Log.d(TAG, "No valid lane detection: bestScore=$bestScore")
            return null
        }
        val mask = FloatArray(maskH * maskW)
        if (protoLayoutChannelsLast) {
            for (y in 0 until maskH) {
                for (x in 0 until maskW) {
                    var value = 0f
                    val base = (y * maskW + x) * maskDim
                    for (m in 0 until maskDim) {
                        value += (bestCoeffs.getOrNull(m) ?: 0f) * (protoData.getOrNull(base + m) ?: 0f)
                    }
                    mask[y * maskW + x] = sigmoid(value)
                }
            }
        } else {
            for (m in 0 until maskDim) {
                val coeff = bestCoeffs.getOrNull(m) ?: 0f
                val offset = m * maskH * maskW
                for (i in 0 until maskH * maskW) {
                    mask[i] = mask.getOrNull(i) ?: 0f + coeff * (protoData.getOrNull(offset + i) ?: 0f)
                }
            }
            for (i in mask.indices) {
                mask[i] = sigmoid(mask[i])
            }
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
        val clamped = value.coerceIn(-SIGMOID_CLAMP_MIN, SIGMOID_CLAMP_MAX)
        return (1f / (1f + exp(-clamped)))
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

    private fun validateModel(buffer: MappedByteBuffer, @Suppress("UNUSED_PARAMETER") modelPath: String): Boolean {
        return try {
            if (buffer.capacity() < MIN_MODEL_SIZE) {
                Log.w(TAG, "Lane model file too small: ${buffer.capacity()} bytes")
                return false
            }

            val md = MessageDigest.getInstance("SHA-256")
            val hash = ByteArray(buffer.capacity())
            buffer.position(0)
            buffer.get(hash)
            buffer.position(0)
            val digest = md.digest(hash)
            val checksum = digest.joinToString("") { "%02x".format(it) }
            modelChecksum.set(checksum)

            Log.d(TAG, "Lane model checksum: $checksum")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Lane model validation failed: ${e.message}")
            false
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
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure lane input: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "WayyLane"
        private const val DEFAULT_NUM_THREADS = 4
        private const val ASSET_PREFIX = "asset://"
        private const val FILE_PREFIX = "file://"
        private const val MASK_THRESHOLD = 0.25f
        private const val EXPECTED_INPUT_SIZE = 640
        private const val EXPECTED_MASK_DIM = 32
        const val DEFAULT_LANE_ASSET_PATH = "ml/lane_model.tflite"

        private const val MAX_LOAD_RETRIES = 3
        private const val RETRY_DELAY_MS = 500L
        private const val MAX_CONSECUTIVE_FAILURES = 5
        private const val MIN_MODEL_SIZE = 1024
        private const val LANE_CONFIDENCE_THRESHOLD = 0.02f

        private const val SIGMOID_CLAMP_MIN = -20f
        private const val SIGMOID_CLAMP_MAX = 20f

        private const val FPS_CALC_INTERVAL_MS = 1000L
    }
}

private data class LaneQuad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)