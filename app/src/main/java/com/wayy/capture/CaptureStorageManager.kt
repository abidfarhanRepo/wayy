package com.wayy.capture

import android.content.Context
import android.os.StatFs
import android.util.Log
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class StorageStatus(
    val availableBytes: Long,
    val totalBytes: Long,
    val captureDirBytes: Long,
    val isLowStorage: Boolean,
    val isCriticalStorage: Boolean,
    val canCapture: Boolean,
    val estimatedRecordingSeconds: Long
)

class CaptureStorageManager(private val context: Context) {

    private val captureDir = File(context.filesDir, "capture")
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)

    init {
        ensureCaptureDir()
    }

    private fun ensureCaptureDir(): Boolean {
        return try {
            if (!captureDir.exists()) {
                val created = captureDir.mkdirs()
                if (!created) {
                    Log.e(TAG, "Failed to create capture directory")
                    return false
                }
            }
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception creating capture directory", e)
            false
        } catch (e: IOException) {
            Log.e(TAG, "IO exception creating capture directory", e)
            false
        }
    }

    fun createCaptureFile(prefix: String, extension: String): Result<File> {
        if (!ensureCaptureDir()) {
            return Result.failure(IOException("Capture directory unavailable"))
        }

        val status = getStorageStatus()
        if (!status.canCapture) {
            return Result.failure(IOException("Insufficient storage: ${status.availableBytes} bytes available"))
        }

        return try {
            val stamp = dateFormat.format(Date())
            val sanitizedPrefix = prefix.replace(Regex("[^a-zA-Z0-9_]"), "_")
            val sanitizedExt = extension.removePrefix(".").lowercase()
            val file = File(captureDir, "${sanitizedPrefix}_$stamp.$sanitizedExt")
            Result.success(file)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create capture file", e)
            Result.failure(e)
        }
    }

    fun getStorageStatus(): StorageStatus {
        val stat = StatFs(context.filesDir.absolutePath)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        val totalBytes = stat.blockCountLong * stat.blockSizeLong
        val captureDirBytes = calculateCaptureDirSize()
        
        val isCriticalStorage = availableBytes < CaptureConfig.MIN_STORAGE_BYTES
        val isLowStorage = availableBytes < CaptureConfig.LOW_STORAGE_WARNING_BYTES
        val canCapture = availableBytes >= CaptureConfig.MIN_STORAGE_BYTES
        
        val estimatedRecordingSeconds = if (canCapture) {
            (availableBytes - CaptureConfig.MIN_STORAGE_BYTES) / CaptureConfig.ESTIMATED_BYTES_PER_SECOND
        } else {
            0L
        }

        return StorageStatus(
            availableBytes = availableBytes,
            totalBytes = totalBytes,
            captureDirBytes = captureDirBytes,
            isLowStorage = isLowStorage,
            isCriticalStorage = isCriticalStorage,
            canCapture = canCapture,
            estimatedRecordingSeconds = estimatedRecordingSeconds
        )
    }

    fun formatStorageStatus(status: StorageStatus): String {
        val availableMb = status.availableBytes / (1024 * 1024)
        return when {
            status.isCriticalStorage -> "Storage critical: ${availableMb}MB"
            status.isLowStorage -> "Storage low: ${availableMb}MB"
            else -> "${availableMb}MB free"
        }
    }

    fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return when {
            hours > 0 -> String.format(Locale.US, "%dh %dm", hours, minutes)
            minutes > 0 -> String.format(Locale.US, "%dm %ds", minutes, secs)
            else -> String.format(Locale.US, "%ds", secs)
        }
    }

    private fun calculateCaptureDirSize(): Long {
        return try {
            if (!captureDir.exists()) return 0L
            captureDir.walkTopDown()
                .filter { it.isFile }
                .map { it.length() }
                .sum()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to calculate capture directory size", e)
            0L
        }
    }

    fun pruneIfNeeded(maxBytes: Long = CaptureConfig.MAX_STORAGE_BYTES): Boolean {
        if (!captureDir.exists()) return true
        
        return try {
            val files = captureDir.listFiles()
                ?.filter { it.isFile }
                ?.sortedBy { it.lastModified() }
            
            if (files.isNullOrEmpty()) return true
            
            var totalSize = files.sumOf { it.length() }
            if (totalSize <= maxBytes) return true
            
            var prunedCount = 0
            for (file in files) {
                if (totalSize <= maxBytes * 0.9) break
                
                val fileSize = file.length()
                if (deleteFileSafely(file)) {
                    totalSize -= fileSize
                    prunedCount++
                    Log.i(TAG, "Pruned capture file: ${file.name}")
                }
            }
            
            Log.i(TAG, "Pruned $prunedCount capture files, total size now: $totalSize bytes")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prune capture directory", e)
            false
        }
    }

    fun deleteFileSafely(file: File): Boolean {
        return try {
            if (file.exists()) {
                val deleted = file.delete()
                if (!deleted) {
                    Log.w(TAG, "Failed to delete file: ${file.name}")
                }
                deleted
            } else {
                true
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception deleting file: ${file.name}", e)
            false
        }
    }

    fun getCaptureDir(): File = captureDir

    fun listCaptureFiles(): List<File> {
        return try {
            captureDir.listFiles()
                ?.filter { it.isFile }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list capture files", e)
            emptyList()
        }
    }

    companion object {
        private const val TAG = "CaptureStorage"
    }
}
