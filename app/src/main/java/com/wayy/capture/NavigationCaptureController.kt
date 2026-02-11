package com.wayy.capture

import android.content.Context
import android.util.Log
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import java.io.File

class NavigationCaptureController(
    private val context: Context,
    private val metadataWriter: CaptureMetadataWriter = CaptureMetadataWriter(context),
    private val storageManager: CaptureStorageManager = CaptureStorageManager(context)
) {

    private var recording: Recording? = null
    private var videoFile: File? = null
    private var isActive = false

    fun attachVideoCapture(videoCapture: VideoCapture<Recorder>) {
        if (isActive && recording == null) {
            startRecording(videoCapture)
        }
    }

    fun startIfNeeded(videoCapture: VideoCapture<Recorder>, sessionInfo: CaptureSessionInfo) {
        if (recording != null) return
        isActive = true
        startRecording(videoCapture, sessionInfo)
    }

    fun stop(sessionInfo: CaptureSessionInfo) {
        isActive = false
        recording?.stop()
        recording = null
        metadataWriter.stop(sessionInfo)
        storageManager.pruneIfNeeded()
    }

    fun logEvent(event: CaptureEvent) {
        metadataWriter.appendThrottled(event)
    }

    private fun startRecording(videoCapture: VideoCapture<Recorder>, sessionInfo: CaptureSessionInfo = emptySession()) {
        val outputFile = storageManager.createCaptureFile("nav_capture", "mp4")
        videoFile = outputFile
        val outputOptions = FileOutputOptions.Builder(outputFile).build()
        metadataWriter.start(sessionInfo)

        val executor = ContextCompat.getMainExecutor(context)
        recording = videoCapture.output
            .prepareRecording(context, outputOptions)
            .start(executor) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        Log.d("WayyCapture", "Recording started: ${outputFile.name}")
                    }
                    is VideoRecordEvent.Finalize -> {
                        Log.d("WayyCapture", "Recording finalized: ${outputFile.name}")
                        storageManager.pruneIfNeeded()
                    }
                }
            }
    }

    private fun emptySession(): CaptureSessionInfo {
        return CaptureSessionInfo(System.currentTimeMillis(), emptyMap())
    }
}
