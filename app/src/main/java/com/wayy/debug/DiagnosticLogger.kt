package com.wayy.debug

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DiagnosticLogger(context: Context) {

    private val logDir = File(context.filesDir, "diagnostics")
    private val gson = Gson()
    private val maxFileSize = 5L * 1024L * 1024L
    private val maxTotalSize = 200L * 1024L * 1024L
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    private val filePrefix = "diag"

    init {
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
    }

    @Synchronized
    fun log(tag: String, message: String, level: String = "INFO", data: Map<String, Any?>? = null) {
        val event = DiagnosticEvent(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            data = data
        )
        val file = ensureLogFile()
        runCatching {
            file.appendText(gson.toJson(event) + "\n")
        }.onFailure { error ->
            Log.e("WayyDiag", "Failed to write log", error)
        }
        pruneIfNeeded()
    }

    fun getLogDir(): File = logDir

    private fun ensureLogFile(): File {
        val current = logDir.listFiles()?.filter { it.isFile && it.name.startsWith(filePrefix) }
            ?.maxByOrNull { it.lastModified() }
        if (current == null || current.length() > maxFileSize) {
            val name = "${filePrefix}_${dateFormat.format(Date())}.jsonl"
            return File(logDir, name)
        }
        return current
    }

    private fun pruneIfNeeded() {
        val files = logDir.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() } ?: return
        var totalSize = files.sumOf { it.length() }
        if (totalSize <= maxTotalSize) return
        for (file in files) {
            if (totalSize <= maxTotalSize) break
            totalSize -= file.length()
            file.delete()
        }
    }
}

data class DiagnosticEvent(
    val timestamp: Long,
    val level: String,
    val tag: String,
    val message: String,
    val data: Map<String, Any?>? = null
)
