package com.wayy.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO for self-learning system data access
 * Phase 1: Foundation - Data collection and storage
 */
@Dao
interface LearningDao {

    // ==================== User Preferences ====================
    
    @Query("SELECT * FROM user_preferences WHERE id = 'default' LIMIT 1")
    suspend fun getUserPreferences(): UserPreferenceEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUserPreferences(preferences: UserPreferenceEntity)
    
    @Query("UPDATE user_preferences SET timeWeight = :timeWeight, distanceWeight = :distanceWeight, simplicityWeight = :simplicityWeight, scenicWeight = :scenicWeight, updatedAt = :timestamp, totalRoutesAnalyzed = totalRoutesAnalyzed + 1 WHERE id = 'default'")
    suspend fun updatePreferenceWeights(
        timeWeight: Float,
        distanceWeight: Float,
        simplicityWeight: Float,
        scenicWeight: Float,
        timestamp: Long = System.currentTimeMillis()
    )
    
    @Query("UPDATE user_preferences SET rerouteAcceptanceRate = :acceptanceRate, minimumTimeSavingsThresholdSeconds = :threshold, updatedAt = :timestamp WHERE id = 'default'")
    suspend fun updateReroutePreferences(
        acceptanceRate: Float,
        threshold: Int,
        timestamp: Long = System.currentTimeMillis()
    )

    // ==================== Destination Patterns ====================
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDestinationPattern(pattern: DestinationPatternEntity)
    
    @Query("SELECT * FROM destination_patterns WHERE isActive = 1 ORDER BY confidenceScore DESC")
    suspend fun getAllActivePatterns(): List<DestinationPatternEntity>
    
    @Query("SELECT * FROM destination_patterns WHERE isActive = 1 AND (dayOfWeek = :dayOfWeek OR dayOfWeek = -1) AND (hourOfDay = :hourOfDay OR hourOfDay = -1) AND confidenceScore >= :minConfidence ORDER BY confidenceScore DESC, occurrenceCount DESC LIMIT :limit")
    suspend fun getPatternsForTimeContext(
        dayOfWeek: Int,
        hourOfDay: Int,
        minConfidence: Float = 0.3f,
        limit: Int = 10
    ): List<DestinationPatternEntity>
    
    @Query("SELECT * FROM destination_patterns WHERE destinationCategory = :category AND isActive = 1")
    suspend fun getPatternsByCategory(category: String): List<DestinationPatternEntity>
    
    @Query("UPDATE destination_patterns SET occurrenceCount = occurrenceCount + 1, lastOccurred = :timestamp, confidenceScore = :newConfidence WHERE patternId = :patternId")
    suspend fun updatePatternOccurrence(patternId: String, newConfidence: Float, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE destination_patterns SET isActive = 0 WHERE patternId = :patternId")
    suspend fun deactivatePattern(patternId: String)

    // ==================== Traffic Models ====================
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrafficModel(model: TrafficModelEntity)
    
    @Query("SELECT * FROM traffic_models WHERE streetName = :streetName LIMIT 1")
    suspend fun getTrafficModel(streetName: String): TrafficModelEntity?
    
    @Query("SELECT * FROM traffic_models WHERE sampleCount >= :minSamples ORDER BY accuracyScore DESC")
    suspend fun getReliableTrafficModels(minSamples: Int = 10): List<TrafficModelEntity>
    
    @Query("UPDATE traffic_models SET hourlyAverages = :hourlyAverages, weeklyPatterns = :weeklyPatterns, sampleCount = :sampleCount, accuracyScore = :accuracyScore, lastUpdated = :timestamp, baselineSpeedMps = :baselineSpeed WHERE streetName = :streetName")
    suspend fun updateTrafficModel(
        streetName: String,
        hourlyAverages: String,
        weeklyPatterns: String,
        sampleCount: Int,
        accuracyScore: Float,
        baselineSpeed: Double?,
        timestamp: Long = System.currentTimeMillis()
    )

    // ==================== Reroute Decisions ====================
    
    @Insert
    suspend fun logRerouteDecision(decision: RerouteDecisionEntity)
    
    @Query("SELECT * FROM reroute_decisions WHERE tripId = :tripId ORDER BY timestamp DESC")
    suspend fun getRerouteDecisionsForTrip(tripId: String): List<RerouteDecisionEntity>
    
    @Query("SELECT * FROM reroute_decisions WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRerouteDecisionsInRange(
        startTime: Long,
        endTime: Long,
        limit: Int = 100
    ): List<RerouteDecisionEntity>
    
    @Query("SELECT userAction, COUNT(*) as count FROM reroute_decisions WHERE timestamp >= :since GROUP BY userAction")
    suspend fun getRerouteAcceptanceStats(since: Long): List<RerouteAcceptanceStat>
    
    @Query("SELECT AVG(timeSavings) as avgSavings FROM reroute_decisions WHERE userAction = 'ACCEPTED' AND timestamp >= :since")
    suspend fun getAverageAcceptedTimeSavings(since: Long): Float?

    // ==================== Detected Anomalies ====================
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnomaly(anomaly: DetectedAnomalyEntity)
    
    @Query("SELECT * FROM detected_anomalies WHERE expiresAt > :currentTime AND confidence >= :minConfidence AND verified = :verifiedOnly ORDER BY detectedAt DESC LIMIT :limit")
    suspend fun getActiveAnomalies(
        currentTime: Long = System.currentTimeMillis(),
        minConfidence: Float = 0.5f,
        verifiedOnly: Boolean = false,
        limit: Int = 100
    ): List<DetectedAnomalyEntity>
    
    @Query("SELECT * FROM detected_anomalies WHERE latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLng AND :maxLng AND expiresAt > :currentTime AND confidence >= :minConfidence ORDER BY confidence DESC")
    suspend fun getAnomaliesInBoundingBox(
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double,
        currentTime: Long = System.currentTimeMillis(),
        minConfidence: Float = 0.5f
    ): List<DetectedAnomalyEntity>
    
    @Query("UPDATE detected_anomalies SET verified = 1, confirmationCount = confirmationCount + 1 WHERE anomalyId = :anomalyId")
    suspend fun verifyAnomaly(anomalyId: String)
    
    @Query("DELETE FROM detected_anomalies WHERE expiresAt < :currentTime")
    suspend fun cleanupExpiredAnomalies(currentTime: Long = System.currentTimeMillis())

    // ==================== Route Choices ====================
    
    @Insert
    suspend fun logRouteChoice(choice: RouteChoiceEntity)
    
    @Query("SELECT * FROM route_choices WHERE tripId = :tripId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getRouteChoiceForTrip(tripId: String): RouteChoiceEntity?
    
    @Query("SELECT * FROM route_choices WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRouteChoicesInRange(
        startTime: Long,
        endTime: Long,
        limit: Int = 100
    ): List<RouteChoiceEntity>
    
    @Query("SELECT COUNT(*) FROM route_choices WHERE wasRerouted = 1 AND timestamp >= :since")
    suspend fun getRerouteCount(since: Long): Int

    // ==================== Learned Sessions ====================
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLearnedSession(session: LearnedSessionEntity)
    
    @Query("SELECT * FROM learned_sessions ORDER BY startTime DESC LIMIT :limit")
    suspend fun getRecentLearnedSessions(limit: Int = 100): List<LearnedSessionEntity>
    
    @Query("SELECT * FROM learned_sessions WHERE patternMatched = 1 ORDER BY startTime DESC LIMIT :limit")
    suspend fun getPatternMatchedSessions(limit: Int = 100): List<LearnedSessionEntity>
    
    @Query("SELECT * FROM learned_sessions WHERE destinationCategory = :category AND startTime >= :since ORDER BY startTime DESC")
    suspend fun getSessionsByCategory(category: String, since: Long): List<LearnedSessionEntity>
    
    @Query("UPDATE learned_sessions SET actualDurationSeconds = :duration, endTime = :endTime WHERE tripId = :tripId")
    suspend fun completeLearnedSession(tripId: String, duration: Int, endTime: Long)

    // ==================== Analytics Queries ====================
    
    @Query("SELECT COUNT(*) FROM destination_patterns WHERE isActive = 1")
    suspend fun getActivePatternCount(): Int
    
    @Query("SELECT COUNT(*) FROM traffic_models WHERE sampleCount >= :minSamples")
    suspend fun getReliableTrafficModelCount(minSamples: Int = 10): Int
    
    @Query("SELECT COUNT(*) FROM reroute_decisions WHERE timestamp >= :since")
    suspend fun getRerouteDecisionCount(since: Long): Int
    
    @Query("SELECT COUNT(*) FROM detected_anomalies WHERE expiresAt > :currentTime")
    suspend fun getActiveAnomalyCount(currentTime: Long = System.currentTimeMillis()): Int
    
    @Query("SELECT COUNT(*) FROM route_choices WHERE timestamp >= :since")
    suspend fun getRouteChoiceCount(since: Long): Int
}

/**
 * Helper class for reroute acceptance statistics
 */
data class RerouteAcceptanceStat(
    val userAction: String,
    val count: Int
)
