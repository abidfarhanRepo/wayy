package com.wayy.capture

import android.content.Context
import android.util.Log
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

enum class CaptureState {
    IDLE,
    STARTING,
    RECORDING,
    PAUSED,
    STOPPING,
    ERROR
}

data class CaptureError(
    val code: String,
    val message: String,
    val cause: Throwable? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class CaptureStats(
    val durationMs: Long = 0L,
    val videoFileSize: Long = 0L,
    val metadataFileSize: Long = 0L,
    val eventsWritten: Long = 0L,
    val storageStatus: StorageStatus? = null
)

class NavigationCaptureController(
    private val context: Context,
    private val metadataWriter: CaptureMetadataWriter = CaptureMetadataWriter(context),
    private val storageManager: CaptureStorageManager = CaptureStorageManager(context)
) {

    private val recording = AtomicReference<Recording?>(null)
    private val videoFile = AtomicReference<File?>(null)
    private val state = AtomicReference(CaptureState.IDLE)
    private val captureStartMs = AtomicLong(0L)
    private val eventCount = AtomicLong(0L)
    private val lastError = AtomicReference<CaptureError?>(null)
    
    private val _captureState = MutableStateFlow(CaptureState.IDLE)
    val captureStateFlow: StateFlow<CaptureState> = _captureState.asStateFlow()
    
    private val _captureStats = MutableStateFlow(CaptureStats())
    val captureStatsFlow: StateFlow<CaptureStats> = _captureStats.asStateFlow()
    
    private val _error = MutableStateFlow<CaptureError?>(null)
    val errorFlow: StateFlow<CaptureError?> = _error.asStateFlow()

    val isActive: Boolean
        get() = state.get() == CaptureState.RECORDING

    val currentState: CaptureState
        get() = state.get()

    fun attachVideoCapture(videoCapture: VideoCapture<Recorder>): Boolean {
        if (!isActive) return false
        if (recording.get() != null) return true
        
        return startRecording(videoCapture, CaptureSessionInfo.empty())
    }

    fun startIfNeeded(
        videoCapture: VideoCapture<Recorder>,
        sessionInfo: CaptureSessionInfo
    ): Boolean {
        if (recording.get() != null) return true
        
        val storageStatus = storageManager.getStorageStatus()
        if (!storageStatus.canCapture) {
            setError("STORAGE_FULL", "Insufficient storage: ${storageStatus.availableBytes} bytes available")
            return false
        }
        
        state.set(CaptureState.STARTING)
        _captureState.value = CaptureState.STARTING
        
        return startRecording(videoCapture, sessionInfo)
    }

    private fun startRecording(
        videoCapture: VideoCapture<Recorder>,
        sessionInfo: CaptureSessionInfo
    ): Boolean {
        return try {
            val outputFileResult = storageManager.createCaptureFile("nav_capture", "mp4")
            if (outputFileResult.isFailure) {
                val error = outputFileResult.exceptionOrNull()
                setError("FILE_CREATE_FAILED", "Failed to create capture file: ${error?.message}", error)
                state.set(CaptureState.ERROR)
                _captureState.value = CaptureState.ERROR
                return false
            }
            
            val outputFile = outputFileResult.getOrThrow()
            videoFile.set(outputFile)
            
            val metadataResult = metadataWriter.start(sessionInfo)
            if (metadataResult.isFailure) {
                val error = metadataResult.exceptionOrNull()
                setError("METADATA_FAILED", "Failed to start metadata: ${error?.message}", error)
                storageManager.deleteFileSafely(outputFile)
                videoFile.set(null)
                state.set(CaptureState.ERROR)
                _captureState.value = CaptureState.ERROR
                return false
            }
            
            val outputOptions = FileOutputOptions.Builder(outputFile).build()
            val executor = ContextCompat.getMainExecutor(context)
            
            val newRecording = videoCapture.output
                .prepareRecording(context, outputOptions)
                .start(executor) { event ->
                    handleVideoRecordEvent(event, outputFile)
                }
            
            recording.set(newRecording)
            captureStartMs.set(System.currentTimeMillis())
            eventCount.set(0L)
            state.set(CaptureState.RECORDING)
            _captureState.value = CaptureState.RECORDING
            lastError.set(null)
            _error.value = null
            
            Log.i(TAG, "Recording started: ${outputFile.name}")
            updateStats()
            true
        } catch (e: SecurityException) {
            setError("SECURITY_EXCEPTION", "Security exception during recording start", e)
            state.set(CaptureState.ERROR)
            _captureState.value = CaptureState.ERROR
            false
        } catch (e: IllegalStateException) {
            setError("ILLEGAL_STATE", "Illegal state during recording start: ${e.message}", e)
            state.set(CaptureState.ERROR)
            _captureState.value = CaptureState.ERROR
            false
        } catch (e: Exception) {
            setError("UNKNOWN", "Unexpected error during recording start: ${e.message}", e)
            state.set(CaptureState.ERROR)
            _captureState.value = CaptureState.ERROR
            false
        }
    }

    private fun handleVideoRecordEvent(
        event: VideoRecordEvent,
        outputFile: File
    ) {
        when (event) {
            is VideoRecordEvent.Start -> {
                Log.i(TAG, "Recording started event: ${outputFile.name}")
            }
            is VideoRecordEvent.Finalize -> {
                handleRecordingFinalize(event, outputFile)
            }
            is VideoRecordEvent.Status -> {
                checkStorageDuringRecording()
            }
            is VideoRecordEvent.Pause -> {
                Log.d(TAG, "Recording paused: ${outputFile.name}")
                state.set(CaptureState.PAUSED)
                _captureState.value = CaptureState.PAUSED
            }
            is VideoRecordEvent.Resume -> {
                Log.d(TAG, "Recording resumed: ${outputFile.name}")
                state.set(CaptureState.RECORDING)
                _captureState.value = CaptureState.RECORDING
            }
        }
    }

    private fun handleRecordingFinalize(event: VideoRecordEvent.Finalize, outputFile: File) {
        if (event.hasError()) {
            val error = event.error
            val cause = event.cause
            Log.e(TAG, "Recording finalize with error: $error, cause: $cause")
            setError(
                "FINALIZE_ERROR",
                "Recording finalize error: $error",
                cause
            )
            state.set(CaptureState.ERROR)
            _captureState.value = CaptureState.ERROR
        } else {
            Log.i(TAG, "Recording finalized successfully: ${outputFile.name}")
        }
        
        recording.set(null)
        state.set(CaptureState.IDLE)
        _captureState.value = CaptureState.IDLE
        storageManager.pruneIfNeeded()
        updateStats()
    }

    private fun checkStorageDuringRecording() {
        val status = storageManager.getStorageStatus()
        if (!status.canCapture && state.get() == CaptureState.RECORDING) {
            Log.w(TAG, "Storage critically low during recording, stopping")
            stop(CaptureSessionInfo(
                timestamp = System.currentTimeMillis(),
                data = mapOf("reason" to "storage_critical")
            ))
            setError("STORAGE_CRITICAL", "Recording stopped due to critical storage")
        }
    }

    fun stop(sessionInfo: CaptureSessionInfo): Boolean {
        if (state.get() == CaptureState.IDLE) return true
        
        state.set(CaptureState.STOPPING)
        _captureState.value = CaptureState.STOPPING
        
        return try {
            val currentRecording = recording.getAndSet(null)
            currentRecording?.stop()
            
            metadataWriter.stop(sessionInfo)
            
            captureStartMs.set(0L)
            state.set(CaptureState.IDLE)
            _captureState.value = CaptureState.IDLE
            
            Log.i(TAG, "Capture stopped successfully")
            storageManager.pruneIfNeeded()
            updateStats()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping capture", e)
            setError("STOP_FAILED", "Failed to stop capture: ${e.message}", e)
            state.set(CaptureState.ERROR)
            _captureState.value = CaptureState.ERROR
            false
        }
    }

    fun forceStop() {
        try {
            recording.getAndSet(null)?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error force stopping recording", e)
        }
        
        try {
            metadataWriter.stop(CaptureSessionInfo.empty())
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping metadata writer", e)
        }
        
        state.set(CaptureState.IDLE)
        _captureState.value = CaptureState.IDLE
        captureStartMs.set(0L)
    }

    fun logEvent(event: CaptureEvent): Boolean {
        if (!isActive) return false
        
        return try {
            val success = metadataWriter.appendThrottled(event)
            if (success) {
                eventCount.incrementAndGet()
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error logging capture event", e)
            false
        }
    }

    fun logEventImmediate(event: CaptureEvent): Boolean {
        if (!isActive) return false
        
        return try {
            val success = metadataWriter.append(event)
            if (success) {
                eventCount.incrementAndGet()
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error logging capture event immediate", e)
            false
        }
    }

    fun getStorageStatus(): StorageStatus {
        return storageManager.getStorageStatus()
    }

    fun getCaptureDuration(): Long {
        val start = captureStartMs.get()
        return if (start > 0) {
            System.currentTimeMillis() - start
        } else {
            0L
        }
    }

    fun getVideoFile(): File? = videoFile.get()

    private fun updateStats() {
        val videoFile = videoFile.get()
        
        _captureStats.value = CaptureStats(
            durationMs = getCaptureDuration(),
            videoFileSize = videoFile?.length() ?: 0L,
            metadataFileSize = 0L,
            eventsWritten = eventCount.get(),
            storageStatus = storageManager.getStorageStatus()
        )
    }

    private fun setError(code: String, message: String, cause: Throwable? = null) {
        val error = CaptureError(code, message, cause)
        lastError.set(error)
        _error.value = error
        Log.e(TAG, "Capture error [$code]: $message", cause)
    }

    fun clearError() {
        lastError.set(null)
        _error.value = null
    }

    companion object {
        private const val TAG = "NavCapture"
    }
}
