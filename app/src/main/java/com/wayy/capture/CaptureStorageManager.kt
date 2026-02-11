package com.wayy.capture

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CaptureStorageManager(context: Context) {

    private val captureDir = File(context.filesDir, "capture")

    init {
        if (!captureDir.exists()) {
            captureDir.mkdirs()
        }
    }

    fun createCaptureFile(prefix: String, extension: String): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(captureDir, "${prefix}_$stamp.$extension")
    }

    fun pruneIfNeeded(maxBytes: Long = CaptureConfig.MAX_STORAGE_BYTES) {
        if (!captureDir.exists()) return
        val files = captureDir.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() }
            ?: return
        var totalSize = files.sumOf { it.length() }
        if (totalSize <= maxBytes) return
        for (file in files) {
            if (totalSize <= maxBytes) break
            totalSize -= file.length()
            val deleted = file.delete()
            Log.w("WayyCapture", "Pruned ${file.name}, deleted=$deleted")
        }
    }

    fun getCaptureDir(): File = captureDir
}
