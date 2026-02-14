package com.wayy.capture

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

enum class MetadataWriterState {
    IDLE,
    ACTIVE,
    ERROR
}

class CaptureMetadataWriter(context: Context) {

    private val storageManager = CaptureStorageManager(context)
    private val gson = Gson()
    private val metadataFile = AtomicReference<File?>(null)
    private val lastEventTime = AtomicLong(0L)
    private val state = AtomicReference(MetadataWriterState.IDLE)
    private val errorCount = AtomicLong(0L)
    
    private var fileWriter: FileWriter? = null

    val currentState: MetadataWriterState
        get() = state.get()

    fun start(session: CaptureSessionInfo): Result<File> {
        return try {
            val status = storageManager.getStorageStatus()
            if (!status.canCapture) {
                val error = IOException("Insufficient storage for metadata")
                state.set(MetadataWriterState.ERROR)
                return Result.failure(error)
            }

            val fileResult = storageManager.createCaptureFile("metadata", "jsonl")
            if (fileResult.isFailure) {
                state.set(MetadataWriterState.ERROR)
                return Result.failure(fileResult.exceptionOrNull() ?: IOException("Failed to create metadata file"))
            }

            val file = fileResult.getOrThrow()
            metadataFile.set(file)
            
            fileWriter = FileWriter(file, true)
            state.set(MetadataWriterState.ACTIVE)
            errorCount.set(0L)
            
            appendEvent(CaptureEvent("session_start", session.timestamp, session.data))
            Log.i(TAG, "Metadata writer started: ${file.name}")
            Result.success(file)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start metadata writer", e)
            state.set(MetadataWriterState.ERROR)
            Result.failure(e)
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception starting metadata writer", e)
            state.set(MetadataWriterState.ERROR)
            Result.failure(e)
        }
    }

    fun append(event: CaptureEvent): Boolean {
        return appendEvent(event)
    }

    fun appendThrottled(
        event: CaptureEvent,
        minIntervalMs: Long = CaptureConfig.METADATA_EVENT_INTERVAL_MS
    ): Boolean {
        val last = lastEventTime.get()
        if (event.timestamp - last < minIntervalMs) return false
        
        if (lastEventTime.compareAndSet(last, event.timestamp)) {
            return appendEvent(event)
        }
        return false
    }

    fun stop(session: CaptureSessionInfo): Boolean {
        return try {
            appendEvent(CaptureEvent("session_end", session.timestamp, session.data))
            flush()
            closeWriter()
            state.set(MetadataWriterState.IDLE)
            metadataFile.set(null)
            Log.i(TAG, "Metadata writer stopped")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping metadata writer", e)
            closeWriter()
            state.set(MetadataWriterState.IDLE)
            false
        }
    }

    fun flush(): Boolean {
        return try {
            fileWriter?.flush()
            true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to flush metadata writer", e)
            false
        }
    }

    private fun appendEvent(event: CaptureEvent): Boolean {
        if (state.get() != MetadataWriterState.ACTIVE) {
            return false
        }

        val file = metadataFile.get()
        if (file == null) {
            Log.w(TAG, "Cannot append event: no metadata file")
            return false
        }

        return try {
            val json = gson.toJson(event)
            val writer = fileWriter
            if (writer != null) {
                writer.write(json)
                writer.write("\n")
            } else {
                file.appendText(json + "\n")
            }
            true
        } catch (e: IOException) {
            val count = errorCount.incrementAndGet()
            Log.e(TAG, "Failed to write metadata event (error #$count)", e)
            
            if (count > MAX_ERRORS_BEFORE_STOP) {
                Log.e(TAG, "Too many metadata errors, stopping writer")
                state.set(MetadataWriterState.ERROR)
            }
            false
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "JSON serialization error for event", e)
            false
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception writing metadata", e)
            state.set(MetadataWriterState.ERROR)
            false
        }
    }

    private fun closeWriter() {
        try {
            fileWriter?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing file writer", e)
        }
        fileWriter = null
    }

    companion object {
        private const val TAG = "CaptureMetadata"
        private const val MAX_ERRORS_BEFORE_STOP = 5L
    }
}

data class CaptureEvent(
    val type: String,
    val timestamp: Long,
    val payload: Map<String, Any?>
) {
    init {
        require(type.isNotBlank()) { "Event type cannot be blank" }
        require(timestamp >= 0) { "Timestamp cannot be negative" }
    }
}

data class CaptureSessionInfo(
    val timestamp: Long,
    val data: Map<String, Any?>
) {
    init {
        require(timestamp >= 0) { "Timestamp cannot be negative" }
    }
    
    companion object {
        fun empty(): CaptureSessionInfo {
            return CaptureSessionInfo(System.currentTimeMillis(), emptyMap())
        }
    }
}
