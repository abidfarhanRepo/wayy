package com.wayy.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * User Preference Profile for self-learning routing
 * Stores individual routing preferences learned over time
 */
@Entity(tableName = "user_preferences")
data class UserPreferenceEntity(
    @PrimaryKey val id: String = "default",
    
    // Weight preferences (0.0 - 1.0)
    val timeWeight: Float = 0.5f,
    val distanceWeight: Float = 0.3f,
    val simplicityWeight: Float = 0.2f,
    val scenicWeight: Float = 0.0f,
    
    // Road type preferences (-1.0 avoid to 1.0 prefer)
    val highwayPreference: Float = 0.0f,
    val arterialPreference: Float = 0.0f,
    val residentialPreference: Float = 0.0f,
    
    // Rerouting behavior
    val rerouteAcceptanceRate: Float = 0.7f,
    val minimumTimeSavingsThresholdSeconds: Int = 60,
    
    // Learning metadata
    val updatedAt: Long = System.currentTimeMillis(),
    val totalRoutesAnalyzed: Int = 0,
    val confidenceScore: Float = 0.0f
)

/**
 * Destination pattern for predictive destination suggestions
 * Learns from user's routine trips
 */
@Entity(tableName = "destination_patterns")
data class DestinationPatternEntity(
    @PrimaryKey val patternId: String,
    val destinationLat: Double,
    val destinationLng: Double,
    val destinationName: String,
    val destinationCategory: String? = null, // HOME, WORK, GYM, etc.
    
    // Temporal patterns (-1 = any)
    val dayOfWeek: Int = -1,
    val hourOfDay: Int = -1,
    val minuteOfDay: Int = -1,
    
    // Statistics
    val confidenceScore: Float = 0.0f,
    val occurrenceCount: Int = 0,
    val lastOccurred: Long = System.currentTimeMillis(),
    val firstOccurred: Long = System.currentTimeMillis(),
    
    // Pattern metadata
    val isActive: Boolean = true,
    val averageDurationSeconds: Long? = null
)

/**
 * Traffic prediction model for individual street segments
 * Stores learned traffic patterns per street
 */
@Entity(tableName = "traffic_models")
data class TrafficModelEntity(
    @PrimaryKey val streetName: String,
    val modelVersion: Int = 1,
    
    // Hourly averages (24 values) stored as JSON
    val hourlyAverages: String = "[]",
    
    // Weekly patterns (7 day patterns) stored as JSON
    val weeklyPatterns: String = "[]",
    
    // Model statistics
    val lastUpdated: Long = System.currentTimeMillis(),
    val sampleCount: Int = 0,
    val accuracyScore: Float = 0.0f,
    
    // Baseline metrics
    val baselineSpeedMps: Double? = null,
    val baselineDurationMs: Long? = null
)

/**
 * Log of rerouting decisions and outcomes
 * Used to learn user's rerouting preferences
 */
@Entity(tableName = "reroute_decisions")
data class RerouteDecisionEntity(
    @PrimaryKey val decisionId: String,
    val tripId: String,
    val timestamp: Long = System.currentTimeMillis(),
    
    // Route comparison
    val originalRouteDuration: Int, // seconds
    val suggestedRouteDuration: Int, // seconds
    val timeSavings: Int, // seconds
    
    // User action
    val userAction: String, // ACCEPTED, IGNORED, CANCELLED, LATER_ACCEPTED
    
    // Outcome (if accepted)
    val actualDuration: Int? = null, // seconds
    val predictedVsActualDiff: Int? = null, // seconds
    
    // Context stored as JSON
    val contextJson: String = "{}",
    
    // Reroute trigger reason
    val triggerReason: String? = null // TRAFFIC, USER_REQUESTED, DEVIATION, FASTER_ROUTE
)

/**
 * Detected road anomalies (closures, construction, accidents)
 * Learned from GPS patterns and user behavior
 */
@Entity(tableName = "detected_anomalies")
data class DetectedAnomalyEntity(
    @PrimaryKey val anomalyId: String,
    val type: String, // ROAD_CLOSURE, CONSTRUCTION, ACCIDENT, NEW_ROAD, SPEED_LIMIT_CHANGE
    
    // Location
    val latitude: Double,
    val longitude: Double,
    val streetName: String,
    
    // Detection info
    val detectedAt: Long = System.currentTimeMillis(),
    val confidence: Float = 0.0f,
    val expiresAt: Long, // TTL for temporary conditions
    
    // Verification
    val verified: Boolean = false,
    val reportedBy: String = "system", // "system" or user_id
    val confirmationCount: Int = 1,
    
    // Context
    val description: String? = null,
    val affectedAreaRadiusMeters: Int? = null
)

/**
 * Route choice decision log
 * Tracks why user chose one route over alternatives
 */
@Entity(tableName = "route_choices")
data class RouteChoiceEntity(
    @PrimaryKey val choiceId: String,
    val tripId: String,
    val timestamp: Long = System.currentTimeMillis(),
    
    // Chosen route
    val chosenRouteIndex: Int,
    val chosenRouteDuration: Int, // seconds
    val chosenRouteDistance: Double, // meters
    
    // Available alternatives stored as JSON
    val alternativesJson: String = "[]",
    
    // Decision context
    val decisionContextJson: String = "{}",
    
    // User behavior
    val wasRerouted: Boolean = false,
    val departureTime: Long? = null,
    val arrivalTime: Long? = null
)

/**
 * User driving session with extended learning metrics
 * Extends basic trip logging with learning-specific fields
 */
@Entity(tableName = "learned_sessions")
data class LearnedSessionEntity(
    @PrimaryKey val tripId: String,
    val startTime: Long,
    val endTime: Long? = null,
    
    // Location
    val startLat: Double,
    val startLng: Double,
    val endLat: Double? = null,
    val endLng: Double? = null,
    val destinationName: String? = null,
    val destinationCategory: String? = null,
    
    // Route characteristics
    val routeDistanceMeters: Double? = null,
    val estimatedDurationSeconds: Int? = null,
    val actualDurationSeconds: Int? = null,
    
    // Driving behavior
    val averageSpeedMps: Double? = null,
    val maxSpeedMps: Double? = null,
    val stopCount: Int? = null,
    
    // Learning metadata
    val dayOfWeek: Int,
    val hourOfDay: Int,
    val isWeekend: Boolean,
    val patternMatched: Boolean = false,
    val patternId: String? = null
)

/**
 * Types of anomalies that can be detected
 */
enum class AnomalyType {
    ROAD_CLOSURE,       // All traffic diverted
    CONSTRUCTION,       // Slow speeds, lane shifts
    ACCIDENT,          // Sudden stops, congestion
    NEW_ROAD,          // Trajectories not in map
    SPEED_LIMIT_CHANGE, // Collective speed shift
    WRONG_WAY_DRIVER,  // Anomalous bearings
    HEAVY_TRAFFIC      // Significant slowdown
}

/**
 * User actions on reroute suggestions
 */
enum class RerouteAction {
    ACCEPTED,
    IGNORED,
    CANCELLED,
    LATER_ACCEPTED
}

/**
 * Destination categories for pattern learning
 */
enum class DestinationCategory {
    HOME,
    WORK,
    GYM,
    GROCERY,
    RESTAURANT,
    SCHOOL,
    PARK,
    SHOPPING,
    OTHER
}
