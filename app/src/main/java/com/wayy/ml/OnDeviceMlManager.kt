package com.wayy.ml

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.wayy.debug.DiagnosticLogger
import android.os.Build
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
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

enum class InferenceBackend {
    GPU,
    CPU
}

data class MlPerformanceMetrics(
    val inferenceMs: Double,
    val fps: Double,
    val backend: InferenceBackend,
    val frameCount: Long,
    val avgInferenceMs: Double,
    val totalDetections: Int
)

class OnDeviceMlManager(
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
    private val totalDetections = AtomicLong(0)
    private val lastFpsTime = AtomicLong(SystemClock.elapsedRealtime())
    private val fpsFrameCount = AtomicLong(0)
    private val currentFps = AtomicReference(0.0)
    private val isInitialized = AtomicBoolean(false)
    private val modelChecksum = AtomicReference<String?>(null)

    private var consecutiveFailures = 0
    private var lastInferenceSuccess = true
    private val inferenceLock = Any()

    val backend: InferenceBackend get() = currentBackend
    val isReady: Boolean get() = isInitialized.get() && interpreter != null

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
                val file = File(resolved.path)
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
                    
                    Log.i(TAG, "Model input dimensions: ${inputWidth}x${inputHeight}")
                    
                    if (inputWidth <= 0 || inputHeight <= 0) {
                        throw IOException("Invalid model input dimensions: ${inputWidth}x${inputHeight}")
                    }

                    val (createdInterpreter, createdGpuDelegate, createdNnapiDelegate, backend) = createInterpreterWithGpuFallback(modelBuffer)

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
                        message = "ML model loaded",
                        data = mapOf(
                            "backend" to backend.name,
                            "inputWidth" to inputWidth,
                            "inputHeight" to inputHeight,
                            "attempt" to attempt
                        )
                    )
                    Log.i(TAG, "ML model loaded on $backend after $attempt attempt(s)")
                    return availability

                } catch (e: Exception) {
                    lastError = e
                    Log.w(TAG, "Model load attempt $attempt failed: ${e.message}")
                    diagnosticLogger?.log(
                        tag = TAG,
                        message = "Model load attempt failed",
                        level = "WARN",
                        data = mapOf("attempt" to attempt, "error" to e.message)
                    )
                    if (attempt < MAX_LOAD_RETRIES) {
                        Thread.sleep(RETRY_DELAY_MS)
                    }
                }
            }

            Log.e(TAG, "Failed to load ML model after $MAX_LOAD_RETRIES attempts", lastError)
            return MlAvailability(false, "Failed to load ML model after retries: ${lastError?.message ?: "unknown"}")
        }
        return availability
    }

    private fun createInterpreterWithGpuFallback(modelBuffer: MappedByteBuffer): MlQuad<Interpreter, GpuDelegate?, NnApiDelegate?, InferenceBackend> {
        val compatList = CompatibilityList()
        val gpuAvailable = try {
            compatList.isDelegateSupportedOnThisDevice
        } catch (e: Exception) {
            Log.w(TAG, "GPU compatibility check failed: ${e.message}")
            diagnosticLogger?.log(tag = TAG, message = "GPU compatibility check failed", level = "WARN", data = mapOf("error" to e.message))
            false
        }
        
        Log.i(TAG, "GPU available: $gpuAvailable, input dims: ${inputWidth}x${inputHeight}")

        if (gpuAvailable && inputWidth > 0 && inputHeight > 0) {
            try {
                Log.i(TAG, "Attempting GPU delegate initialization with input: ${inputWidth}x${inputHeight}")
                val gpuOptions = compatList.bestOptionsForThisDevice
                val gpuDelegate = GpuDelegate(gpuOptions)
                val options = Interpreter.Options()
                    .addDelegate(gpuDelegate)
                    .setNumThreads(DEFAULT_NUM_THREADS)

                val interpreter = Interpreter(modelBuffer, options)
                
                try {
                    val inputTensor = interpreter.getInputTensor(0)
                    val shape = inputTensor.shape()
                    Log.i(TAG, "GPU delegate initialized successfully, input shape: ${shape.joinToString()}")
                    
                    if (shape.size == 4 && shape[0] == 1 && shape[1] > 0 && shape[2] > 0) {
                        diagnosticLogger?.log(
                            tag = TAG,
                            message = "GPU delegate initialized successfully",
                            data = mapOf("inputShape" to shape.joinToString())
                        )
                        return MlQuad(interpreter, gpuDelegate, null, InferenceBackend.GPU)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "GPU input tensor verification failed: ${e.message}")
                }
                
                Log.w(TAG, "GPU delegate test failed, closing and trying fallback")
                gpuDelegate.close()
                interpreter.close()
            } catch (e: Exception) {
                Log.w(TAG, "GPU delegate initialization failed: ${e.message}")
                diagnosticLogger?.log(
                    tag = TAG,
                    message = "GPU delegate initialization failed",
                    level = "WARN",
                    data = mapOf("error" to e.message, "inputWidth" to inputWidth, "inputHeight" to inputHeight)
                )
            }
        } else if (inputWidth <= 0 || inputHeight <= 0) {
            Log.w(TAG, "Skipping GPU: input dimensions not configured (${inputWidth}x${inputHeight})")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            try {
                Log.i(TAG, "Attempting NNAPI delegate as fallback (Android ${Build.VERSION.SDK_INT})")
                val nnapiDelegate = NnApiDelegate()
                val options = Interpreter.Options()
                    .addDelegate(nnapiDelegate)
                    .setNumThreads(DEFAULT_NUM_THREADS)
                    
                val interpreter = Interpreter(modelBuffer, options)
                val inputTensor = interpreter.getInputTensor(0)
                val shape = inputTensor.shape()
                
                Log.i(TAG, "NNAPI delegate initialized successfully, input shape: ${shape.joinToString()}")
                diagnosticLogger?.log(
                    tag = TAG,
                    message = "NNAPI delegate initialized",
                    data = mapOf("inputShape" to shape.joinToString())
                )
                return MlQuad(interpreter, null, nnapiDelegate, InferenceBackend.GPU)
            } catch (e: Exception) {
                Log.w(TAG, "NNAPI delegate failed, falling back to CPU: ${e.message}")
                diagnosticLogger?.log(
                    tag = TAG,
                    message = "NNAPI delegate failed",
                    level = "WARN",
                    data = mapOf("error" to e.message)
                )
            }
        }

        Log.i(TAG, "Using CPU backend for inference (XNNPACK)")
        diagnosticLogger?.log(
            tag = TAG,
            message = "Using CPU backend for inference",
            data = mapOf("inputWidth" to inputWidth, "inputHeight" to inputHeight)
        )
        val options = Interpreter.Options()
            .setNumThreads(DEFAULT_NUM_THREADS)
            .setUseXNNPACK(true)
        return MlQuad(Interpreter(modelBuffer, options), null, null, InferenceBackend.CPU)
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
        totalDetections.set(0)
        diagnosticLogger?.log(tag = TAG, message = "ML scanning stopped")
    }

    fun isActive(): Boolean = isInitialized.get() && interpreter != null

    fun runInference(bitmap: Bitmap): MlInferenceResult? {
        synchronized(inferenceLock) {
            val interpreter = interpreter ?: return null

            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                Log.w(TAG, "Too many consecutive failures, skipping inference")
                return null
            }

            if (bitmap.width <= 0 || bitmap.height <= 0) {
                Log.w(TAG, "Invalid bitmap dimensions: ${bitmap.width}x${bitmap.height}")
                return null
            }

            if (inputWidth == 0 || inputHeight == 0) {
                configureInput(interpreter)
            }
            if (inputWidth == 0 || inputHeight == 0) {
                Log.w(TAG, "Input not configured properly")
                return null
            }

            return try {
                val startTime = System.nanoTime()

                val inputImage = TensorImage(inputDataType)
                inputImage.load(bitmap)
                val processedImage = imageProcessor?.process(inputImage) ?: inputImage

                if (processedImage.buffer.remaining() == 0) {
                    Log.w(TAG, "Empty processed image buffer")
                    return null
                }

                val outputShapes = mutableListOf<IntArray>()
                val outputBuffers = HashMap<Int, Any>()
                val outputTensorBuffers = HashMap<Int, TensorBuffer>()

                for (index in 0 until interpreter.outputTensorCount) {
                    try {
                        val outputTensor = interpreter.getOutputTensor(index)
                        val buffer = TensorBuffer.createFixedSize(outputTensor.shape(), outputTensor.dataType())
                        outputTensorBuffers[index] = buffer
                        outputBuffers[index] = buffer.buffer
                        outputShapes.add(outputTensor.shape())
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to get output tensor $index: ${e.message}")
                        return null
                    }
                }

                val inputs = arrayOf(processedImage.buffer)
                interpreter.runForMultipleInputsOutputs(inputs, outputBuffers)

                val elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0

                val detections = parseDetections(
                    outputTensorBuffers[0],
                    outputShapes.firstOrNull(),
                    inputWidth,
                    inputHeight
                )

                consecutiveFailures = 0
                lastInferenceSuccess = true

                frameCount.incrementAndGet()
                totalInferenceTime.addAndGet(elapsedMs.toLong())
                totalDetections.addAndGet(detections.size.toLong())

                updateFps()

                MlInferenceResult(
                    inferenceMs = elapsedMs,
                    inputShape = intArrayOf(1, inputHeight, inputWidth, 3),
                    outputShapes = outputShapes,
                    detections = detections,
                    backend = currentBackend,
                    frameCount = frameCount.get(),
                    avgInferenceMs = if (frameCount.get() > 0) totalInferenceTime.get().toDouble() / frameCount.get() else 0.0
                )
            } catch (e: Exception) {
                consecutiveFailures++
                lastInferenceSuccess = false
                Log.e(TAG, "Inference failed (attempt $consecutiveFailures): ${e.message}")
                diagnosticLogger?.log(
                    tag = TAG,
                    message = "Inference failed",
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
            val fps = frames * 1000.0 / elapsed
            currentFps.set(fps)
            fpsFrameCount.set(0)
            lastFpsTime.set(now)
        }
    }

    fun getPerformanceMetrics(): MlPerformanceMetrics {
        val frames = frameCount.get()
        return MlPerformanceMetrics(
            inferenceMs = if (frames > 0) totalInferenceTime.get().toDouble() / frames else 0.0,
            fps = currentFps.get(),
            backend = currentBackend,
            frameCount = frames,
            avgInferenceMs = if (frames > 0) totalInferenceTime.get().toDouble() / frames else 0.0,
            totalDetections = totalDetections.get().toInt()
        )
    }

    fun resetPerformanceMetrics() {
        frameCount.set(0)
        totalInferenceTime.set(0)
        totalDetections.set(0)
        fpsFrameCount.set(0)
        lastFpsTime.set(SystemClock.elapsedRealtime())
        currentFps.set(0.0)
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

    private fun validateModel(buffer: MappedByteBuffer, @Suppress("UNUSED_PARAMETER") modelPath: String): Boolean {
        return try {
            if (buffer.capacity() < MIN_MODEL_SIZE) {
                Log.w(TAG, "Model file too small: ${buffer.capacity()} bytes")
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

            Log.d(TAG, "Model checksum: $checksum")
            diagnosticLogger?.log(
                tag = TAG,
                message = "Model validated",
                data = mapOf("checksum" to checksum.substring(0, 16), "size" to buffer.capacity())
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "Model validation failed: ${e.message}")
            false
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
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure input: ${e.message}")
        }
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

            if (bestClass >= 0 && bestScore >= CONFIDENCE_THRESHOLD) {
                if (w < MIN_DETECTION_SIZE || h < MIN_DETECTION_SIZE) continue
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

        val nmsDetections = applyNms(detections, NMS_IOU_THRESHOLD)
        return nmsDetections.sortedByDescending { it.score }.take(MAX_DETECTIONS)
    }

    private fun applyNms(detections: List<MlDetection>, iouThreshold: Float): List<MlDetection> {
        if (detections.size <= 1) return detections

        val sorted = detections.sortedByDescending { it.score }
        val selected = mutableListOf<MlDetection>()

        for (det in sorted) {
            var shouldAdd = true
            for (existing in selected) {
                if (iou(det, existing) > iouThreshold) {
                    shouldAdd = false
                    break
                }
            }
            if (shouldAdd) {
                selected.add(det)
            }
        }
        return selected
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

        val interX1 = maxOf(ax1, bx1)
        val interY1 = maxOf(ay1, by1)
        val interX2 = minOf(ax2, bx2)
        val interY2 = minOf(ay2, by2)

        val interW = maxOf(0f, interX2 - interX1)
        val interH = maxOf(0f, interY2 - interY1)
        val interArea = interW * interH

        val areaA = maxOf(0f, ax2 - ax1) * maxOf(0f, ay2 - ay1)
        val areaB = maxOf(0f, bx2 - bx1) * maxOf(0f, by2 - by1)
        val union = areaA + areaB - interArea

        return if (union <= 0f) 0f else interArea / union
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
        val clamped = value.coerceIn(-SIGMOID_CLAMP_MIN, SIGMOID_CLAMP_MAX)
        return (1f / (1f + kotlin.math.exp(-clamped)))
    }

    private fun normalizeCoord(raw: Float, size: Int): Float {
        return when {
            raw < 0f -> sigmoid(raw)
            raw > 1.5f && size > 0 -> (raw / size).coerceIn(0f, 1f)
            raw > 1.5f -> sigmoid(raw)
            else -> raw.coerceIn(0f, 1f)
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

        private const val MAX_LOAD_RETRIES = 3
        private const val RETRY_DELAY_MS = 500L
        private const val MAX_CONSECUTIVE_FAILURES = 5
        private const val MIN_MODEL_SIZE = 1024

        private const val CONFIDENCE_THRESHOLD = 0.10f
        private const val MIN_DETECTION_SIZE = 0.02f
        private const val NMS_IOU_THRESHOLD = 0.45f
        private const val MAX_DETECTIONS = 50

        private const val SIGMOID_CLAMP_MIN = -20f
        private const val SIGMOID_CLAMP_MAX = 20f

        private const val FPS_CALC_INTERVAL_MS = 1000L
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
    val detections: List<MlDetection> = emptyList(),
    val backend: InferenceBackend = InferenceBackend.CPU,
    val frameCount: Long = 0,
    val avgInferenceMs: Double = 0.0
)

internal data class MlQuad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)