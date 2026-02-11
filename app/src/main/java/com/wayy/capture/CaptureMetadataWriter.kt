package com.wayy.capture

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import java.io.File
import java.util.concurrent.atomic.AtomicLong

class CaptureMetadataWriter(context: Context) {

    private val storageManager = CaptureStorageManager(context)
    private val gson = Gson()
    private var metadataFile: File? = null
    private val lastEventTime = AtomicLong(0L)

    fun start(session: CaptureSessionInfo): File {
        val file = storageManager.createCaptureFile("metadata", "jsonl")
        metadataFile = file
        appendEvent(CaptureEvent("session_start", session.timestamp, session.data))
        return file
    }

    fun append(event: CaptureEvent) {
        appendEvent(event)
    }

    fun appendThrottled(event: CaptureEvent, minIntervalMs: Long = CaptureConfig.METADATA_EVENT_INTERVAL_MS) {
        val last = lastEventTime.get()
        if (event.timestamp - last < minIntervalMs) return
        if (lastEventTime.compareAndSet(last, event.timestamp)) {
            appendEvent(event)
        }
    }

    fun stop(session: CaptureSessionInfo) {
        appendEvent(CaptureEvent("session_end", session.timestamp, session.data))
        metadataFile = null
    }

    private fun appendEvent(event: CaptureEvent) {
        val file = metadataFile ?: return
        runCatching {
            file.appendText(gson.toJson(event) + "\n")
        }.onFailure { error ->
            Log.e("WayyCapture", "Failed to write metadata", error)
        }
    }
}

data class CaptureEvent(
    val type: String,
    val timestamp: Long,
    val payload: Map<String, Any?>
)

data class CaptureSessionInfo(
    val timestamp: Long,
    val data: Map<String, Any?>
)
