package com.wayy.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Privacy levels for learning data sharing
 */
enum class PrivacyLevel {
    LOCAL_ONLY,      // All learning on-device only
    ANONYMOUS,       // Send anonymized aggregates only
    PERSONALIZED     // Account-based personalization (requires account)
}

/**
 * Learning settings data class
 * Controls all self-learning features and privacy settings
 */
data class LearningSettings(
    // Feature toggles
    val learningEnabled: Boolean = true,
    val destinationPredictionEnabled: Boolean = true,
    val trafficLearningEnabled: Boolean = true,
    val anomalyDetectionEnabled: Boolean = true,
    val rerouteLearningEnabled: Boolean = true,
    val preferenceLearningEnabled: Boolean = true,
    
    // Advanced features
    val federatedLearningEnabled: Boolean = false,
    
    // Update frequency
    val modelUpdateFrequency: ModelUpdateFrequency = ModelUpdateFrequency.WEEKLY,
    val minimumSamplesForTraining: Int = 50,
    
    // Privacy
    val privacyLevel: PrivacyLevel = PrivacyLevel.LOCAL_ONLY,
    
    // Data retention (days)
    val dataRetentionDays: Int = 90,
    val maxStoredSessions: Int = 1000,
    val maxStoredAnomalies: Int = 500,
    
    // Export settings
    val autoBackupEnabled: Boolean = false,
    val backupFrequencyDays: Int = 7
)

/**
 * Model update frequency options
 */
enum class ModelUpdateFrequency(val value: String) {
    DAILY("daily"),
    WEEKLY("weekly"),
    MONTHLY("monthly"),
    MANUAL("manual")
}

private val Context.learningSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "learning_settings"
)

/**
 * Repository for learning system settings
 * Manages all self-learning preferences and privacy controls
 */
class LearningSettingsRepository(private val context: Context) {

    private object Keys {
        // Feature toggles
        val LEARNING_ENABLED = booleanPreferencesKey("learning_enabled")
        val DESTINATION_PREDICTION_ENABLED = booleanPreferencesKey("destination_prediction_enabled")
        val TRAFFIC_LEARNING_ENABLED = booleanPreferencesKey("traffic_learning_enabled")
        val ANOMALY_DETECTION_ENABLED = booleanPreferencesKey("anomaly_detection_enabled")
        val REROUTE_LEARNING_ENABLED = booleanPreferencesKey("reroute_learning_enabled")
        val PREFERENCE_LEARNING_ENABLED = booleanPreferencesKey("preference_learning_enabled")
        
        // Advanced
        val FEDERATED_LEARNING_ENABLED = booleanPreferencesKey("federated_learning_enabled")
        
        // Update frequency
        val MODEL_UPDATE_FREQUENCY = stringPreferencesKey("model_update_frequency")
        val MINIMUM_SAMPLES_FOR_TRAINING = intPreferencesKey("minimum_samples_for_training")
        
        // Privacy
        val PRIVACY_LEVEL = stringPreferencesKey("privacy_level")
        
        // Data retention
        val DATA_RETENTION_DAYS = intPreferencesKey("data_retention_days")
        val MAX_STORED_SESSIONS = intPreferencesKey("max_stored_sessions")
        val MAX_STORED_ANOMALIES = intPreferencesKey("max_stored_anomalies")
        
        // Backup
        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val BACKUP_FREQUENCY_DAYS = intPreferencesKey("backup_frequency_days")
    }

    /**
     * Flow of current learning settings
     */
    val settingsFlow: Flow<LearningSettings> = context.learningSettingsDataStore.data.map { prefs ->
        LearningSettings(
            learningEnabled = prefs[Keys.LEARNING_ENABLED] ?: true,
            destinationPredictionEnabled = prefs[Keys.DESTINATION_PREDICTION_ENABLED] ?: true,
            trafficLearningEnabled = prefs[Keys.TRAFFIC_LEARNING_ENABLED] ?: true,
            anomalyDetectionEnabled = prefs[Keys.ANOMALY_DETECTION_ENABLED] ?: true,
            rerouteLearningEnabled = prefs[Keys.REROUTE_LEARNING_ENABLED] ?: true,
            preferenceLearningEnabled = prefs[Keys.PREFERENCE_LEARNING_ENABLED] ?: true,
            federatedLearningEnabled = prefs[Keys.FEDERATED_LEARNING_ENABLED] ?: false,
            modelUpdateFrequency = prefs[Keys.MODEL_UPDATE_FREQUENCY]?.let { 
                ModelUpdateFrequency.values().find { freq -> freq.value == it } 
            } ?: ModelUpdateFrequency.WEEKLY,
            minimumSamplesForTraining = prefs[Keys.MINIMUM_SAMPLES_FOR_TRAINING] ?: 50,
            privacyLevel = prefs[Keys.PRIVACY_LEVEL]?.let {
                try {
                    PrivacyLevel.valueOf(it)
                } catch (e: IllegalArgumentException) {
                    PrivacyLevel.LOCAL_ONLY
                }
            } ?: PrivacyLevel.LOCAL_ONLY,
            dataRetentionDays = prefs[Keys.DATA_RETENTION_DAYS] ?: 90,
            maxStoredSessions = prefs[Keys.MAX_STORED_SESSIONS] ?: 1000,
            maxStoredAnomalies = prefs[Keys.MAX_STORED_ANOMALIES] ?: 500,
            autoBackupEnabled = prefs[Keys.AUTO_BACKUP_ENABLED] ?: false,
            backupFrequencyDays = prefs[Keys.BACKUP_FREQUENCY_DAYS] ?: 7
        )
    }

    // ==================== Feature Toggles ====================
    
    suspend fun setLearningEnabled(enabled: Boolean) {
        context.learningSettingsDataStore.edit { prefs ->
            prefs[Keys.LEARNING_ENABLED] = enabled
        }
    }
    
    suspend fun setDestinationPredictionEnabled(enabled: Boolean) {
        context.learningSettingsDataStore.edit { prefs ->
            prefs[Keys.DESTINATION_PREDICTION_ENABLED] = enabled
        }
    }
    
    suspend fun setTrafficLearningEnabled(enabled: Boolean) {
        context.learningSettingsDataStore.edit { prefs ->
            prefs[Keys.TRAFFIC_LEARNING_ENABLED] = enabled
        }
    }
    
    suspend fun setAnomalyDetectionEnabled(enabled: Boolean) {
        context.learningSettingsDataStore.edit { prefs ->
            prefs[Keys.ANOMALY_DETECTION_ENABLED] = enabled
        }
    }
    
    suspend fun setRerouteLearningEnabled(enabled: Boolean) {
        context.learningSettingsDataStore.edit { prefs ->
            prefs[Keys.REROUTE_LEARNING_ENABLED] = enabled
        }
    }
    
    suspend fun setPreferenceLearningEnabled(enabled: Boolean) {
        context.learningSettingsDataStore.edit { prefs ->
            prefs[Keys.PREFERENCE_LEARNING_ENABLED] = enabled
        }
    }

    // ==================== Advanced Settings ====================
    
    suspend fun setFederatedLearningEnabled(enabled: Boolean) {
        context.learningSettingsDataStore.edit { prefs ->
            prefs[Keys.FEDERATED_LEARNING_ENABLED] = enabled
        }
    }
    
    suspend fun setModelUpdateFrequency(frequency: ModelUpdateFrequency) {
        context.learningSettingsDataStore.edit { prefs ->
            prefs[Keys.MODEL_UPDATE_FREQUENCY] = frequency.value
        }
    }
    
    suspend fun setMinimumSamplesForTraining(samples: Int) {
        context.learningSettingsDataStore.edit { prefs ->
            prefs[Keys.MINIMUM_SAMPLES_FOR_TRAINING] = samples.coerceIn(10, 1000)
        }
    }

    // ==================== Privacy Settings ====================
    
    suspend fun setPrivacyLevel(level: PrivacyLevel) {
        context.learningSettingsDataStore.edit { prefs ->
            prefs[Keys.PRIVACY_LEVEL] = level.name
        }
    }
    
    suspend fun setDataRetentionDays(days: Int) {
        context.learningSettingsDataStore.edit { prefs ->
            prefs[Keys.DATA_RETENTION_DAYS] = days.coerceIn(7, 365)
        }
    }
    
    suspend fun setMaxStoredSessions(max: Int) {
        context.learningSettingsDataStore.edit { prefs ->
            prefs[Keys.MAX_STORED_SESSIONS] = max.coerceIn(100, 10000)
        }
    }
    
    suspend fun setMaxStoredAnomalies(max: Int) {
        context.learningSettingsDataStore.edit { prefs ->
            prefs[Keys.MAX_STORED_ANOMALIES] = max.coerceIn(100, 2000)
        }
    }

    // ==================== Backup Settings ====================
    
    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        context.learningSettingsDataStore.edit { prefs ->
            prefs[Keys.AUTO_BACKUP_ENABLED] = enabled
        }
    }
    
    suspend fun setBackupFrequencyDays(days: Int) {
        context.learningSettingsDataStore.edit { prefs ->
            prefs[Keys.BACKUP_FREQUENCY_DAYS] = days.coerceIn(1, 30)
        }
    }

    // ==================== Master Reset ====================
    
    /**
     * Reset all learning settings to defaults
     */
    suspend fun resetToDefaults() {
        context.learningSettingsDataStore.edit { prefs ->
            prefs.clear()
        }
    }
    
    /**
     * Disable all learning features (privacy mode)
     */
    suspend fun disableAllLearning() {
        context.learningSettingsDataStore.edit { prefs ->
            prefs[Keys.LEARNING_ENABLED] = false
            prefs[Keys.DESTINATION_PREDICTION_ENABLED] = false
            prefs[Keys.TRAFFIC_LEARNING_ENABLED] = false
            prefs[Keys.ANOMALY_DETECTION_ENABLED] = false
            prefs[Keys.REROUTE_LEARNING_ENABLED] = false
            prefs[Keys.PREFERENCE_LEARNING_ENABLED] = false
            prefs[Keys.FEDERATED_LEARNING_ENABLED] = false
            prefs[Keys.PRIVACY_LEVEL] = PrivacyLevel.LOCAL_ONLY.name
        }
    }
}
