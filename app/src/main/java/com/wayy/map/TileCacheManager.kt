package com.wayy.map

import android.content.Context
import android.util.Log
import java.io.File

class TileCacheManager(private val context: Context) {

    companion object {
        private const val TAG = "TileCacheManager"
        private const val MAX_CACHE_SIZE_BYTES: Long = 100 * 1024 * 1024
    }

    fun initialize() {
        setCacheSize(MAX_CACHE_SIZE_BYTES)
        Log.d(TAG, "TileCacheManager initialized with ${MAX_CACHE_SIZE_BYTES / (1024 * 1024)}MB cache")
    }

    private fun setCacheSize(sizeBytes: Long) {
        try {
            val cacheDir = File(context.cacheDir, "maplibre_cache")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            Log.d(TAG, "Cache directory: ${cacheDir.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set cache size", e)
        }
    }

    fun getCacheStats(): CacheStats {
        val cacheDir = File(context.cacheDir, "maplibre_cache")
        var totalSize = 0L
        var fileCount = 0
        
        if (cacheDir.exists()) {
            cacheDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    totalSize += file.length()
                    fileCount++
                }
            }
        }
        
        return CacheStats(
            totalSizeBytes = totalSize,
            maxSizeBytes = MAX_CACHE_SIZE_BYTES,
            fileCount = fileCount
        )
    }

    fun clearCache() {
        try {
            val cacheDir = File(context.cacheDir, "maplibre_cache")
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
                Log.d(TAG, "Cache cleared")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear cache", e)
        }
    }

    fun trimCacheIfNeeded() {
        val stats = getCacheStats()
        if (stats.totalSizeBytes > stats.maxSizeBytes) {
            clearCache()
            Log.d(TAG, "Cache trimmed - was over limit")
        }
    }

    data class CacheStats(
        val totalSizeBytes: Long,
        val maxSizeBytes: Long,
        val fileCount: Int
    ) {
        val usedMB: Double get() = totalSizeBytes / (1024.0 * 1024.0)
        val maxMB: Double get() = maxSizeBytes / (1024.0 * 1024.0)
        val usedPercent: Int get() = if (maxSizeBytes > 0) ((totalSizeBytes * 100) / maxSizeBytes).toInt() else 0
    }
}
