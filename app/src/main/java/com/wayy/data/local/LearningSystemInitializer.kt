package com.wayy.data.local

import android.content.Context
import android.util.Log
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Initialize the self-learning system
 * Handles database migration and initial setup
 */
class LearningSystemInitializer(private val context: Context) {
    
    companion object {
        private const val TAG = "LearningSystem"
        private const val DATABASE_NAME = "trip_logging.db"
        
        @Volatile
        private var database: TripLoggingDatabase? = null
        
        @Volatile
        private var learningDao: LearningDao? = null
        
        @Volatile
        private var routeDecisionLogger: RouteDecisionLogger? = null
        
        @Volatile
        private var exportManager: LearningDataExportManager? = null
        
        /**
         * Get or create the learning database instance
         */
        fun getDatabase(context: Context): TripLoggingDatabase {
            return database ?: synchronized(this) {
                database ?: Room.databaseBuilder(
                    context.applicationContext,
                    TripLoggingDatabase::class.java,
                    DATABASE_NAME
                )
                .fallbackToDestructiveMigration() // For development - will reset data on schema change
                .build()
                .also { database = it }
            }
        }
        
        /**
         * Get or create the LearningDao
         */
        fun getLearningDao(context: Context): LearningDao {
            return learningDao ?: synchronized(this) {
                learningDao ?: getDatabase(context).learningDao()
                    .also { learningDao = it }
            }
        }
        
        /**
         * Get or create the RouteDecisionLogger
         */
        fun getRouteDecisionLogger(context: Context): RouteDecisionLogger {
            return routeDecisionLogger ?: synchronized(this) {
                routeDecisionLogger ?: RouteDecisionLogger(
                    context.applicationContext,
                    getLearningDao(context)
                ).also { routeDecisionLogger = it }
            }
        }
        
        /**
         * Get or create the LearningDataExportManager
         */
        fun getExportManager(context: Context): LearningDataExportManager {
            return exportManager ?: synchronized(this) {
                exportManager ?: LearningDataExportManager(
                    context.applicationContext,
                    getLearningDao(context)
                ).also { exportManager = it }
            }
        }
        
        /**
         * Clear all singletons (useful for testing)
         */
        fun reset() {
            database = null
            learningDao = null
            routeDecisionLogger = null
            exportManager = null
        }
    }
    
    private val scope = CoroutineScope(Dispatchers.IO)
    
    /**
     * Initialize the learning system
     * Call this during app startup
     */
    fun initialize() {
        Log.d(TAG, "Initializing self-learning system...")
        
        scope.launch {
            try {
                // Initialize default user preferences if not exists
                initializeDefaultPreferences()
                
                // Cleanup expired data
                cleanupExpiredData()
                
                Log.d(TAG, "Self-learning system initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize learning system", e)
            }
        }
    }
    
    /**
     * Check if learning system is ready
     */
    fun isInitialized(): Boolean {
        return database != null
    }
    
    private suspend fun initializeDefaultPreferences() {
        val dao = getLearningDao(context)
        val existing = dao.getUserPreferences()
        
        if (existing == null) {
            Log.d(TAG, "Creating default user preferences")
            dao.saveUserPreferences(UserPreferenceEntity())
        }
    }
    
    private suspend fun cleanupExpiredData() {
        val dao = getLearningDao(context)
        val now = System.currentTimeMillis()
        
        // Cleanup expired anomalies
        val deletedAnomalies = dao.cleanupExpiredAnomalies(now)
        Log.d(TAG, "Cleaned up expired anomalies")
        
        // Note: Additional cleanup (old sessions, decisions) would be added here
        // based on data retention settings from LearningSettingsRepository
    }
}

/**
 * Convenience accessors for learning system components
 */
object LearningSystem {
    fun database(context: Context) = LearningSystemInitializer.getDatabase(context)
    fun dao(context: Context) = LearningSystemInitializer.getLearningDao(context)
    fun logger(context: Context) = LearningSystemInitializer.getRouteDecisionLogger(context)
    fun exportManager(context: Context) = LearningSystemInitializer.getExportManager(context)
}
