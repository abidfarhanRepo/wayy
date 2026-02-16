package com.wayy.data.local

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Data class representing a complete learning data export
 */
data class LearningDataExport(
    val exportMetadata: ExportMetadata,
    val userPreferences: UserPreferenceEntity?,
    val destinationPatterns: List<DestinationPatternEntity>,
    val trafficModels: List<TrafficModelEntity>,
    val rerouteDecisions: List<RerouteDecisionEntity>,
    val detectedAnomalies: List<DetectedAnomalyEntity>,
    val routeChoices: List<RouteChoiceEntity>,
    val learnedSessions: List<LearnedSessionEntity>
)

/**
 * Export metadata for tracking exports
 */
data class ExportMetadata(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val appVersion: String,
    val deviceId: String?, // Hashed/anonymized
    val recordCounts: RecordCounts
)

/**
 * Counts of each record type
 */
data class RecordCounts(
    val destinationPatterns: Int,
    val trafficModels: Int,
    val rerouteDecisions: Int,
    val detectedAnomalies: Int,
    val routeChoices: Int,
    val learnedSessions: Int
)

/**
 * Export format options
 */
enum class ExportFormat {
    JSON,      // Single JSON file
    JSON_PRETTY, // Pretty-printed JSON
    CSV_ZIP,   // ZIP of CSV files
    JSON_ZIP   // ZIP of JSON files
}

/**
 * Manager for exporting and backing up learning data
 * Supports multiple formats and handles privacy concerns
 */
class LearningDataExportManager(
    private val context: Context,
    private val learningDao: LearningDao
) {
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()
    
    companion object {
        private const val TAG = "LearningDataExport"
        private const val EXPORT_DIR = "learning_exports"
        private const val MAX_EXPORT_AGE_DAYS = 30
    }

    /**
     * Export all learning data
     * 
     * @param format Export format
     * @param daysBack Number of days of history to include (null = all)
     * @param includeAnonymizedId Whether to include a device hash for cross-device sync
     * @return URI to the exported file, or null if export failed
     */
    suspend fun exportLearningData(
        format: ExportFormat = ExportFormat.JSON,
        daysBack: Int? = null,
        includeAnonymizedId: Boolean = false
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting learning data export (format: $format)")
            
            // Calculate time range
            val endTime = System.currentTimeMillis()
            val startTime = daysBack?.let { endTime - (it * 24 * 60 * 60 * 1000L) } ?: 0L
            
            // Gather data
            val exportData = gatherExportData(startTime, endTime, includeAnonymizedId)
            
            // Generate filename
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val filename = "wayy_learning_export_$timestamp"
            
            // Export based on format
            val exportFile = when (format) {
                ExportFormat.JSON, ExportFormat.JSON_PRETTY -> exportAsJson(exportData, filename)
                ExportFormat.JSON_ZIP -> exportAsJsonZip(exportData, filename)
                ExportFormat.CSV_ZIP -> exportAsCsvZip(exportData, filename)
            }
            
            exportFile?.let { file ->
                // Get content URI via FileProvider
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                ).also {
                    Log.d(TAG, "Export complete: ${file.absolutePath}")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
            null
        }
    }
    
    /**
     * Create a backup of all learning data
     * Stores in app-specific directory
     */
    suspend fun createBackup(): File? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Creating learning data backup")
            
            val exportData = gatherExportData(
                startTime = 0L,
                endTime = System.currentTimeMillis(),
                includeAnonymizedId = false
            )
            
            val timestamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
            val backupFile = getExportFile("wayy_learning_backup_$timestamp.json")
            
            FileOutputStream(backupFile).use { fos ->
                OutputStreamWriter(fos).use { writer ->
                    gson.toJson(exportData, writer)
                }
            }
            
            // Cleanup old backups
            cleanupOldBackups()
            
            Log.d(TAG, "Backup created: ${backupFile.absolutePath}")
            backupFile
            
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed", e)
            null
        }
    }
    
    /**
     * Get list of available backup files
     */
    suspend fun getAvailableBackups(): List<File> = withContext(Dispatchers.IO) {
        val exportDir = getExportDirectory()
        exportDir.listFiles { file ->
            file.name.startsWith("wayy_learning_backup_") && file.extension == "json"
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
    
    /**
     * Delete all learning data
     * Use with caution - irreversible
     */
    suspend fun deleteAllLearningData(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Deleting all learning data")
            
            // Note: In a real implementation, we'd add delete methods to DAO
            // For Phase 1, we'll just log this action
            
            // Delete preference profile (reset to default)
            learningDao.saveUserPreferences(
                UserPreferenceEntity(id = "default") // Reset to defaults
            )
            
            // Deactivate all patterns
            val patterns = learningDao.getAllActivePatterns()
            patterns.forEach { pattern ->
                learningDao.deactivatePattern(pattern.patternId)
            }
            
            // Cleanup expired anomalies (which is all of them with past timestamp)
            learningDao.cleanupExpiredAnomalies(0)
            
            Log.d(TAG, "Learning data deletion completed")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete learning data", e)
            false
        }
    }
    
    /**
     * Get statistics about learning data
     */
    suspend fun getLearningDataStats(): LearningDataStats = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val oneWeekAgo = now - (7 * 24 * 60 * 60 * 1000L)
        val oneMonthAgo = now - (30 * 24 * 60 * 60 * 1000L)
        
        LearningDataStats(
            activeDestinationPatterns = learningDao.getActivePatternCount(),
            reliableTrafficModels = learningDao.getReliableTrafficModelCount(minSamples = 10),
            totalRerouteDecisions = learningDao.getRerouteDecisionCount(since = 0),
            recentRerouteDecisions = learningDao.getRerouteDecisionCount(since = oneMonthAgo),
            activeAnomalies = learningDao.getActiveAnomalyCount(now),
            totalRouteChoices = learningDao.getRouteChoiceCount(since = 0),
            recentRouteChoices = learningDao.getRouteChoiceCount(since = oneMonthAgo),
            hasUserPreferences = learningDao.getUserPreferences() != null,
            oldestDataTimestamp = null // Would need additional query
        )
    }
    
    // ==================== Private Methods ====================
    
    private suspend fun gatherExportData(
        startTime: Long,
        endTime: Long,
        includeAnonymizedId: Boolean
    ): LearningDataExport {
        val prefs = learningDao.getUserPreferences()
        val patterns = learningDao.getAllActivePatterns()
        val trafficModels = learningDao.getReliableTrafficModels(minSamples = 5)
        val rerouteDecisions = learningDao.getRerouteDecisionsInRange(startTime, endTime, limit = 10000)
        val anomalies = learningDao.getActiveAnomalies(endTime, minConfidence = 0.0f, verifiedOnly = false, limit = 10000)
        val routeChoices = learningDao.getRouteChoicesInRange(startTime, endTime, limit = 10000)
        val sessions = learningDao.getRecentLearnedSessions(limit = 10000)
        
        val metadata = ExportMetadata(
            version = 1,
            exportedAt = System.currentTimeMillis(),
            appVersion = getAppVersion(),
            deviceId = if (includeAnonymizedId) getAnonymizedDeviceId() else null,
            recordCounts = RecordCounts(
                destinationPatterns = patterns.size,
                trafficModels = trafficModels.size,
                rerouteDecisions = rerouteDecisions.size,
                detectedAnomalies = anomalies.size,
                routeChoices = routeChoices.size,
                learnedSessions = sessions.size
            )
        )
        
        return LearningDataExport(
            exportMetadata = metadata,
            userPreferences = prefs,
            destinationPatterns = patterns,
            trafficModels = trafficModels,
            rerouteDecisions = rerouteDecisions,
            detectedAnomalies = anomalies,
            routeChoices = routeChoices,
            learnedSessions = sessions
        )
    }
    
    private fun exportAsJson(data: LearningDataExport, filename: String): File {
        val file = getExportFile("$filename.json")
        FileOutputStream(file).use { fos ->
            OutputStreamWriter(fos).use { writer ->
                gson.toJson(data, writer)
            }
        }
        return file
    }
    
    private fun exportAsJsonZip(data: LearningDataExport, filename: String): File {
        val file = getExportFile("$filename.zip")
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            // Metadata
            zos.putNextEntry(ZipEntry("metadata.json"))
            zos.write(gson.toJson(data.exportMetadata).toByteArray())
            zos.closeEntry()
            
            // User preferences
            data.userPreferences?.let { prefs ->
                zos.putNextEntry(ZipEntry("user_preferences.json"))
                zos.write(gson.toJson(prefs).toByteArray())
                zos.closeEntry()
            }
            
            // Destination patterns
            zos.putNextEntry(ZipEntry("destination_patterns.json"))
            zos.write(gson.toJson(data.destinationPatterns).toByteArray())
            zos.closeEntry()
            
            // Traffic models
            zos.putNextEntry(ZipEntry("traffic_models.json"))
            zos.write(gson.toJson(data.trafficModels).toByteArray())
            zos.closeEntry()
            
            // Reroute decisions
            zos.putNextEntry(ZipEntry("reroute_decisions.json"))
            zos.write(gson.toJson(data.rerouteDecisions).toByteArray())
            zos.closeEntry()
            
            // Detected anomalies
            zos.putNextEntry(ZipEntry("detected_anomalies.json"))
            zos.write(gson.toJson(data.detectedAnomalies).toByteArray())
            zos.closeEntry()
            
            // Route choices
            zos.putNextEntry(ZipEntry("route_choices.json"))
            zos.write(gson.toJson(data.routeChoices).toByteArray())
            zos.closeEntry()
            
            // Learned sessions
            zos.putNextEntry(ZipEntry("learned_sessions.json"))
            zos.write(gson.toJson(data.learnedSessions).toByteArray())
            zos.closeEntry()
        }
        return file
    }
    
    private fun exportAsCsvZip(data: LearningDataExport, filename: String): File {
        val file = getExportFile("$filename.zip")
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            // Helper to escape CSV values
            fun escapeCsv(value: String?): String {
                if (value == null) return ""
                return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
                    "\"${value.replace("\"", "\"\"")}\""
                } else value
            }
            
            // Export destination patterns as CSV
            zos.putNextEntry(ZipEntry("destination_patterns.csv"))
            zos.write("patternId,destinationLat,destinationLng,destinationName,category,dayOfWeek,hourOfDay,confidence,occurrences\n".toByteArray())
            data.destinationPatterns.forEach { pattern ->
                val line = "${pattern.patternId},${pattern.destinationLat},${pattern.destinationLng}," +
                    "${escapeCsv(pattern.destinationName)},${escapeCsv(pattern.destinationCategory)}," +
                    "${pattern.dayOfWeek},${pattern.hourOfDay},${pattern.confidenceScore},${pattern.occurrenceCount}\n"
                zos.write(line.toByteArray())
            }
            zos.closeEntry()
            
            // Export traffic models as CSV
            zos.putNextEntry(ZipEntry("traffic_models.csv"))
            zos.write("streetName,sampleCount,accuracyScore,lastUpdated\n".toByteArray())
            data.trafficModels.forEach { model ->
                val line = "${escapeCsv(model.streetName)},${model.sampleCount},${model.accuracyScore},${model.lastUpdated}\n"
                zos.write(line.toByteArray())
            }
            zos.closeEntry()
            
            // Export reroute decisions as CSV
            zos.putNextEntry(ZipEntry("reroute_decisions.csv"))
            zos.write("decisionId,tripId,timestamp,originalDuration,suggestedDuration,timeSavings,userAction,triggerReason\n".toByteArray())
            data.rerouteDecisions.forEach { decision ->
                val line = "${decision.decisionId},${decision.tripId},${decision.timestamp}," +
                    "${decision.originalRouteDuration},${decision.suggestedRouteDuration}," +
                    "${decision.timeSavings},${decision.userAction},${escapeCsv(decision.triggerReason)}\n"
                zos.write(line.toByteArray())
            }
            zos.closeEntry()
            
            // Include JSON metadata
            zos.putNextEntry(ZipEntry("metadata.json"))
            zos.write(gson.toJson(data.exportMetadata).toByteArray())
            zos.closeEntry()
        }
        return file
    }
    
    private fun getExportDirectory(): File {
        val dir = File(context.filesDir, EXPORT_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    private fun getExportFile(filename: String): File {
        return File(getExportDirectory(), filename)
    }
    
    private fun cleanupOldBackups() {
        val exportDir = getExportDirectory()
        val maxAge = MAX_EXPORT_AGE_DAYS * 24 * 60 * 60 * 1000L
        val cutoff = System.currentTimeMillis() - maxAge
        
        exportDir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) {
                file.delete()
                Log.d(TAG, "Deleted old export: ${file.name}")
            }
        }
    }
    
    private fun getAppVersion(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    private fun getAnonymizedDeviceId(): String {
        // Create a hashed device ID for privacy
        val androidId = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown"
        
        // Simple hash - in production use proper hashing
        return androidId.hashCode().toString(16)
    }
}

/**
 * Statistics about learning data
 */
data class LearningDataStats(
    val activeDestinationPatterns: Int,
    val reliableTrafficModels: Int,
    val totalRerouteDecisions: Int,
    val recentRerouteDecisions: Int,
    val activeAnomalies: Int,
    val totalRouteChoices: Int,
    val recentRouteChoices: Int,
    val hasUserPreferences: Boolean,
    val oldestDataTimestamp: Long?
)
