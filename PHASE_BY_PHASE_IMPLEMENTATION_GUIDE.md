# Wayy Self-Learning System - Phase-by-Phase Implementation Guide

## Overview
This guide provides step-by-step instructions for implementing the complete self-learning system from scratch. Each phase builds upon the previous one.

**Total Estimated Time**: 40-60 hours  
**Difficulty**: Intermediate  
**Prerequisites**: Kotlin, Room, Jetpack Compose, Coroutines

---

## Phase 1: Database Foundation (8-10 hours)

### Objective
Create Room database entities and DAO for storing learning data.

### Prerequisites
- Room dependency already in build.gradle
- Basic understanding of Room entities and DAOs

### Step 1.1: Create Learning Entities File
**File**: `app/src/main/java/com/wayy/data/local/LearningEntities.kt`

**Instructions**:
1. Create the file if it doesn't exist
2. Add the following entity classes:

```kotlin
package com.wayy.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_preferences")
data class UserPreferenceEntity(
    @PrimaryKey val id: String = "default",
    val timeWeight: Float = 0.5f,
    val distanceWeight: Float = 0.3f,
    val simplicityWeight: Float = 0.2f,
    val scenicWeight: Float = 0.0f,
    val highwayPreference: Float = 0.0f,
    val arterialPreference: Float = 0.0f,
    val residentialPreference: Float = 0.0f,
    val rerouteAcceptanceRate: Float = 0.7f,
    val minimumTimeSavingsThresholdSeconds: Int = 60,
    val updatedAt: Long = System.currentTimeMillis(),
    val totalRoutesAnalyzed: Int = 0,
    val confidenceScore: Float = 0.0f
)

@Entity(tableName = "destination_patterns")
data class DestinationPatternEntity(
    @PrimaryKey val patternId: String,
    val destinationLat: Double,
    val destinationLng: Double,
    val destinationName: String,
    val destinationCategory: String? = null,
    val dayOfWeek: Int = -1,
    val hourOfDay: Int = -1,
    val minuteOfDay: Int = -1,
    val confidenceScore: Float = 0.0f,
    val occurrenceCount: Int = 0,
    val lastOccurred: Long = System.currentTimeMillis(),
    val firstOccurred: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
    val averageDurationSeconds: Long? = null
)

@Entity(tableName = "route_choices")
data class RouteChoiceEntity(
    @PrimaryKey val choiceId: String,
    val tripId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val chosenRouteIndex: Int,
    val chosenRouteDuration: Int,
    val chosenRouteDistance: Double,
    val alternativesJson: String = "[]",
    val decisionContextJson: String = "{}",
    val wasRerouted: Boolean = false,
    val departureTime: Long? = null,
    val arrivalTime: Long? = null
)

@Entity(tableName = "reroute_decisions")
data class RerouteDecisionEntity(
    @PrimaryKey val decisionId: String,
    val tripId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val originalRouteDuration: Int,
    val suggestedRouteDuration: Int,
    val timeSavings: Int,
    val userAction: String,
    val actualDuration: Int? = null,
    val predictedVsActualDiff: Int? = null,
    val contextJson: String = "{}",
    val triggerReason: String? = null
)

@Entity(tableName = "detected_anomalies")
data class DetectedAnomalyEntity(
    @PrimaryKey val anomalyId: String,
    val type: String,
    val latitude: Double,
    val longitude: Double,
    val streetName: String,
    val detectedAt: Long = System.currentTimeMillis(),
    val confidence: Float = 0.0f,
    val expiresAt: Long,
    val verified: Boolean = false,
    val reportedBy: String = "system",
    val confirmationCount: Int = 1,
    val description: String? = null,
    val affectedAreaRadiusMeters: Int? = null
)

@Entity(tableName = "traffic_models")
data class TrafficModelEntity(
    @PrimaryKey val streetName: String,
    val modelVersion: Int = 1,
    val hourlyAverages: String = "[]",
    val weeklyPatterns: String = "[]",
    val lastUpdated: Long = System.currentTimeMillis(),
    val sampleCount: Int = 0,
    val accuracyScore: Float = 0.0f,
    val baselineSpeedMps: Double? = null,
    val baselineDurationMs: Long? = null
)

@Entity(tableName = "learned_sessions")
data class LearnedSessionEntity(
    @PrimaryKey val tripId: String,
    val startTime: Long,
    val endTime: Long? = null,
    val startLat: Double,
    val startLng: Double,
    val endLat: Double? = null,
    val endLng: Double? = null,
    val destinationName: String? = null,
    val destinationCategory: String? = null,
    val routeDistanceMeters: Double? = null,
    val estimatedDurationSeconds: Int? = null,
    val actualDurationSeconds: Int? = null,
    val averageSpeedMps: Double? = null,
    val maxSpeedMps: Double? = null,
    val stopCount: Int? = null,
    val dayOfWeek: Int,
    val hourOfDay: Int,
    val isWeekend: Boolean,
    val patternMatched: Boolean = false,
    val patternId: String? = null
)
```

### Step 1.2: Create Learning DAO
**File**: `app/src/main/java/com/wayy/data/local/LearningDao.kt`

```kotlin
package com.wayy.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LearningDao {

    // User Preferences
    @Query("SELECT * FROM user_preferences WHERE id = 'default' LIMIT 1")
    suspend fun getUserPreferences(): UserPreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUserPreferences(preferences: UserPreferenceEntity)

    @Query("""
        UPDATE user_preferences 
        SET timeWeight = :timeWeight, 
            distanceWeight = :distanceWeight, 
            simplicityWeight = :simplicityWeight, 
            scenicWeight = :scenicWeight, 
            updatedAt = :timestamp, 
            totalRoutesAnalyzed = totalRoutesAnalyzed + 1 
        WHERE id = 'default'
    """)
    suspend fun updatePreferenceWeights(
        timeWeight: Float,
        distanceWeight: Float,
        simplicityWeight: Float,
        scenicWeight: Float,
        timestamp: Long = System.currentTimeMillis()
    )

    // Destination Patterns
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDestinationPattern(pattern: DestinationPatternEntity)

    @Query("SELECT * FROM destination_patterns WHERE isActive = 1 ORDER BY confidenceScore DESC")
    suspend fun getAllActivePatterns(): List<DestinationPatternEntity>

    @Query("""
        SELECT * FROM destination_patterns 
        WHERE isActive = 1 
        AND (dayOfWeek = :dayOfWeek OR dayOfWeek = -1) 
        AND (hourOfDay = :hourOfDay OR hourOfDay = -1) 
        AND confidenceScore >= :minConfidence 
        ORDER BY confidenceScore DESC, occurrenceCount DESC 
        LIMIT :limit
    """)
    suspend fun getPatternsForTimeContext(
        dayOfWeek: Int,
        hourOfDay: Int,
        minConfidence: Float = 0.3f,
        limit: Int = 10
    ): List<DestinationPatternEntity>

    @Query("UPDATE destination_patterns SET occurrenceCount = occurrenceCount + 1, lastOccurred = :timestamp, confidenceScore = :newConfidence WHERE patternId = :patternId")
    suspend fun updatePatternOccurrence(
        patternId: String, 
        newConfidence: Float, 
        timestamp: Long = System.currentTimeMillis()
    )

    // Route Choices
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

    // Reroute Decisions
    @Insert
    suspend fun logRerouteDecision(decision: RerouteDecisionEntity)

    @Query("SELECT * FROM reroute_decisions WHERE tripId = :tripId ORDER BY timestamp DESC")
    suspend fun getRerouteDecisionsForTrip(tripId: String): List<RerouteDecisionEntity>

    // Anomalies
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnomaly(anomaly: DetectedAnomalyEntity)

    @Query("DELETE FROM detected_anomalies WHERE expiresAt < :currentTime")
    suspend fun cleanupExpiredAnomalies(currentTime: Long = System.currentTimeMillis())

    // Analytics Queries
    @Query("SELECT COUNT(*) FROM destination_patterns WHERE isActive = 1")
    suspend fun getActivePatternCount(): Int

    @Query("SELECT COUNT(*) FROM route_choices WHERE timestamp >= :since")
    suspend fun getRouteChoiceCount(since: Long): Int
}

data class RerouteAcceptanceStat(
    val userAction: String,
    val count: Int
)
```

### Step 1.3: Update Database Class
**File**: `app/src/main/java/com/wayy/data/local/TripLoggingDatabase.kt`

**Add these entities to the @Database annotation**:

```kotlin
@Database(
    entities = [
        // Existing entities...
        UserPreferenceEntity::class,
        DestinationPatternEntity::class,
        RouteChoiceEntity::class,
        RerouteDecisionEntity::class,
        DetectedAnomalyEntity::class,
        TrafficModelEntity::class,
        LearnedSessionEntity::class
    ],
    version = 2, // Increment version
    exportSchema = false
)
abstract class TripLoggingDatabase : RoomDatabase() {
    // Existing DAOs...
    abstract fun learningDao(): LearningDao
}
```

### Testing Phase 1
- [ ] Build succeeds without errors
- [ ] Database compiles
- [ ] All entities have @PrimaryKey
- [ ] DAO methods compile

**Run**: `./gradlew :app:compileDebugKotlin`

---

## Phase 2: Preference Learning Engine (10-12 hours)

### Objective
Implement the core learning algorithm that adapts to user route choices.

### Step 2.1: Create Data Classes
**File**: `app/src/main/java/com/wayy/data/learning/PreferenceModels.kt`

```kotlin
package com.wayy.data.learning

import com.wayy.data.model.Route

enum class PreferenceType {
    TIME, DISTANCE, SIMPLICITY, SCENIC
}

data class UserPreferenceProfile(
    val timeWeight: Float = 0.5f,
    val distanceWeight: Float = 0.3f,
    val simplicityWeight: Float = 0.2f,
    val scenicWeight: Float = 0.0f,
    val highwayPreference: Float = 0.0f,
    val arterialPreference: Float = 0.0f,
    val residentialPreference: Float = 0.0f,
    val rerouteAcceptanceRate: Float = 0.7f,
    val minimumTimeSavingsThresholdSeconds: Int = 60,
    val totalRoutesAnalyzed: Int = 0,
    val confidenceScore: Float = 0.0f,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    companion object {
        fun balanced() = UserPreferenceProfile(
            timeWeight = 0.5f, distanceWeight = 0.3f, 
            simplicityWeight = 0.2f, scenicWeight = 0.0f
        )
        
        fun fastest() = UserPreferenceProfile(
            timeWeight = 0.8f, distanceWeight = 0.1f, 
            simplicityWeight = 0.1f, scenicWeight = 0.0f
        )
        
        fun simplest() = UserPreferenceProfile(
            timeWeight = 0.3f, distanceWeight = 0.2f, 
            simplicityWeight = 0.5f, scenicWeight = 0.0f
        )
        
        fun scenic() = UserPreferenceProfile(
            timeWeight = 0.2f, distanceWeight = 0.1f, 
            simplicityWeight = 0.2f, scenicWeight = 0.5f
        )
    }
    
    fun isReliable(minRoutes: Int = 10, minConfidence: Float = 0.3f): Boolean {
        return totalRoutesAnalyzed >= minRoutes && confidenceScore >= minConfidence
    }
    
    fun getDominantPreference(): PreferenceType {
        val weights = mapOf(
            PreferenceType.TIME to timeWeight,
            PreferenceType.DISTANCE to distanceWeight,
            PreferenceType.SIMPLICITY to simplicityWeight,
            PreferenceType.SCENIC to scenicWeight
        )
        return weights.maxByOrNull { it.value }?.key ?: PreferenceType.TIME
    }
}

data class RouteScoreResult(
    val totalScore: Double,
    val timeScore: Double,
    val distanceScore: Double,
    val simplicityScore: Double,
    val scenicScore: Double,
    val roadTypeScore: Double,
    val normalizedTime: Double,
    val normalizedDistance: Double,
    val normalizedComplexity: Double
)

data class ScoredRoute(
    val route: Route,
    val originalIndex: Int,
    val score: RouteScoreResult
)

data class PreferenceLearningStats(
    val totalRoutesAnalyzed: Int,
    val confidenceScore: Float,
    val dominantPreference: PreferenceType,
    val recentRouteChoices: Int,
    val isReliable: Boolean,
    val profile: UserPreferenceProfile
)
```

### Step 2.2: Create Preference Learning Engine
**File**: `app/src/main/java/com/wayy/data/learning/PreferenceLearningEngine.kt`

```kotlin
package com.wayy.data.learning

import com.wayy.data.local.LearningDao
import com.wayy.data.local.UserPreferenceEntity
import com.wayy.data.model.Route
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.abs
import kotlin.math.exp

class PreferenceLearningEngine(private val learningDao: LearningDao) {
    
    companion object {
        private const val LEARNING_RATE = 0.1f
        private const val MIN_WEIGHT = 0.05f
        private const val MAX_WEIGHT = 0.8f
    }
    
    suspend fun getUserProfile(): UserPreferenceProfile {
        return learningDao.getUserPreferences()?.toProfile() ?: UserPreferenceProfile.balanced()
    }
    
    fun getUserProfileFlow(): Flow<UserPreferenceProfile> = flow {
        emit(getUserProfile())
    }
    
    fun scoreRoute(route: Route, profile: UserPreferenceProfile, referenceRoutes: List<Route>): RouteScoreResult {
        if (referenceRoutes.isEmpty()) {
            return RouteScoreResult(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5)
        }
        
        val minDuration = referenceRoutes.minOfOrNull { it.duration } ?: route.duration
        val maxDuration = referenceRoutes.maxOfOrNull { it.duration } ?: route.duration
        val minDistance = referenceRoutes.minOfOrNull { it.distance } ?: route.distance
        val maxDistance = referenceRoutes.maxOfOrNull { it.distance } ?: route.distance
        
        val complexity = calculateRouteComplexity(route)
        val complexities = referenceRoutes.map { calculateRouteComplexity(it) }
        val minComplexity = complexities.minOrNull() ?: complexity
        val maxComplexity = complexities.maxOrNull() ?: complexity
        
        val normalizedTime = normalizeLowerIsBetter(route.duration, minDuration, maxDuration)
        val normalizedDistance = normalizeLowerIsBetter(route.distance, minDistance, maxDistance)
        val normalizedComplexity = normalizeLowerIsBetter(complexity.toDouble(), minComplexity.toDouble(), maxComplexity.toDouble())
        
        val scenicScore = estimateScenicScore(route)
        val roadTypeScore = calculateRoadTypeScore(route, profile)
        
        val timeScore = (1.0 - normalizedTime) * profile.timeWeight
        val distanceScore = (1.0 - normalizedDistance) * profile.distanceWeight
        val simplicityScore = (1.0 - normalizedComplexity) * profile.simplicityWeight
        val scenicWeightedScore = scenicScore * profile.scenicWeight
        
        val totalScore = timeScore + distanceScore + simplicityScore + scenicWeightedScore + (roadTypeScore * 0.1)
        
        return RouteScoreResult(
            totalScore = totalScore.coerceIn(0.0, 1.0),
            timeScore = timeScore.coerceIn(0.0, 1.0),
            distanceScore = distanceScore.coerceIn(0.0, 1.0),
            simplicityScore = simplicityScore.coerceIn(0.0, 1.0),
            scenicScore = scenicWeightedScore.coerceIn(0.0, 1.0),
            roadTypeScore = roadTypeScore.coerceIn(0.0, 1.0),
            normalizedTime = normalizedTime,
            normalizedDistance = normalizedDistance,
            normalizedComplexity = normalizedComplexity
        )
    }
    
    fun rankRoutes(routes: List<Route>, profile: UserPreferenceProfile): List<ScoredRoute> {
        if (routes.isEmpty()) return emptyList()
        
        return routes.mapIndexed { index, route ->
            val score = scoreRoute(route, profile, routes)
            ScoredRoute(route = route, originalIndex = index, score = score)
        }.sortedByDescending { it.score.totalScore }
    }
    
    suspend fun updatePreferencesFromChoice(
        chosenRouteIndex: Int,
        routes: List<Route>,
        previousProfile: UserPreferenceProfile
    ): UserPreferenceProfile {
        if (routes.size < 2 || chosenRouteIndex !in routes.indices) {
            return previousProfile
        }
        
        val chosenRoute = routes[chosenRouteIndex]
        val alternativeRoutes = routes.filterIndexed { index, _ -> index != chosenRouteIndex }
        
        if (alternativeRoutes.isEmpty()) return previousProfile
        
        val preferenceDelta = analyzePreferenceDelta(chosenRoute, alternativeRoutes)
        
        val newTimeWeight = updateWeight(previousProfile.timeWeight, preferenceDelta.timeDelta, previousProfile.totalRoutesAnalyzed)
        val newDistanceWeight = updateWeight(previousProfile.distanceWeight, preferenceDelta.distanceDelta, previousProfile.totalRoutesAnalyzed)
        val newSimplicityWeight = updateWeight(previousProfile.simplicityWeight, preferenceDelta.simplicityDelta, previousProfile.totalRoutesAnalyzed)
        val newScenicWeight = updateWeight(previousProfile.scenicWeight, preferenceDelta.scenicDelta, previousProfile.totalRoutesAnalyzed)
        
        val totalWeight = newTimeWeight + newDistanceWeight + newSimplicityWeight + newScenicWeight
        val normalizedWeights = if (totalWeight > 0) {
            listOf(newTimeWeight, newDistanceWeight, newSimplicityWeight, newScenicWeight).map { it / totalWeight }
        } else {
            listOf(0.25f, 0.25f, 0.25f, 0.25f)
        }
        
        val newHighwayPreference = updateRoadTypePreference(previousProfile.highwayPreference, preferenceDelta.highwayDelta, previousProfile.totalRoutesAnalyzed)
        val newConfidence = calculateConfidence(previousProfile.totalRoutesAnalyzed + 1)
        
        val updatedProfile = previousProfile.copy(
            timeWeight = normalizedWeights[0].coerceIn(MIN_WEIGHT, MAX_WEIGHT),
            distanceWeight = normalizedWeights[1].coerceIn(MIN_WEIGHT, MAX_WEIGHT),
            simplicityWeight = normalizedWeights[2].coerceIn(MIN_WEIGHT, MAX_WEIGHT),
            scenicWeight = normalizedWeights[3].coerceIn(MIN_WEIGHT, MAX_WEIGHT),
            highwayPreference = newHighwayPreference,
            totalRoutesAnalyzed = previousProfile.totalRoutesAnalyzed + 1,
            confidenceScore = newConfidence,
            lastUpdated = System.currentTimeMillis()
        )
        
        saveProfile(updatedProfile)
        return updatedProfile
    }
    
    suspend fun resetToDefaults() {
        saveProfile(UserPreferenceProfile.balanced())
    }
    
    suspend fun setExplicitPreferences(profile: UserPreferenceProfile) {
        saveProfile(profile.copy(lastUpdated = System.currentTimeMillis()))
    }
    
    suspend fun getLearningStatistics(): PreferenceLearningStats {
        val profile = getUserProfile()
        val routeChoices = learningDao.getRouteChoicesInRange(
            System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000),
            System.currentTimeMillis(),
            1000
        )
        
        return PreferenceLearningStats(
            totalRoutesAnalyzed = profile.totalRoutesAnalyzed,
            confidenceScore = profile.confidenceScore,
            dominantPreference = profile.getDominantPreference(),
            recentRouteChoices = routeChoices.size,
            isReliable = profile.isReliable(),
            profile = profile
        )
    }
    
    // Private helper methods
    private suspend fun saveProfile(profile: UserPreferenceProfile) {
        learningDao.saveUserPreferences(profile.toEntity())
    }
    
    private fun normalizeLowerIsBetter(value: Double, min: Double, max: Double): Double {
        if (max == min) return 0.5
        return ((value - min) / (max - min)).coerceIn(0.0, 1.0)
    }
    
    private fun calculateRouteComplexity(route: Route): Int {
        return route.legs.sumOf { leg -> leg.steps.size }
    }
    
    private fun estimateScenicScore(route: Route): Double {
        val avgSpeed = if (route.duration > 0) route.distance / route.duration else 0.0
        val complexity = calculateRouteComplexity(route)
        
        val speedScore = when {
            avgSpeed in 10.0..20.0 -> 0.8
            avgSpeed in 8.0..25.0 -> 0.5
            else -> 0.2
        }
        
        val complexityScore = (complexity / 15.0).coerceIn(0.0, 1.0)
        return (speedScore * 0.6 + complexityScore * 0.4).coerceIn(0.0, 1.0)
    }
    
    private fun calculateRoadTypeScore(route: Route, profile: UserPreferenceProfile): Double {
        val avgSpeed = if (route.duration > 0) route.distance / route.duration else 0.0
        val highwayPercentage = when {
            avgSpeed > 22 -> 0.8
            avgSpeed > 18 -> 0.6
            avgSpeed > 14 -> 0.3
            else -> 0.1
        }
        
        return if (profile.highwayPreference > 0) {
            highwayPercentage * profile.highwayPreference
        } else if (profile.highwayPreference < 0) {
            (1 - highwayPercentage) * abs(profile.highwayPreference)
        } else 0.5
    }
    
    private fun analyzePreferenceDelta(chosen: Route, alternatives: List<Route>): PreferenceDelta {
        val avgAltDuration = alternatives.map { it.duration }.average()
        val avgAltDistance = alternatives.map { it.distance }.average()
        val avgAltComplexity = alternatives.map { calculateRouteComplexity(it) }.average()
        val chosenComplexity = calculateRouteComplexity(chosen)
        val chosenSpeed = if (chosen.duration > 0) chosen.distance / chosen.duration else 0.0
        val avgAltSpeed = alternatives.map { if (it.duration > 0) it.distance / it.duration else 0.0 }.average()
        
        val timeDelta = if (avgAltDuration > 0) ((avgAltDuration - chosen.duration) / avgAltDuration).toFloat() else 0f
        val distanceDelta = if (avgAltDistance > 0) ((avgAltDistance - chosen.distance) / avgAltDistance).toFloat() else 0f
        val simplicityDelta = if (avgAltComplexity > 0) ((avgAltComplexity - chosenComplexity) / avgAltComplexity).toFloat() else 0f
        
        val scenicDelta = when {
            chosenSpeed in 10.0..20.0 && avgAltSpeed > 20 -> 0.5f
            chosenSpeed > 20 && avgAltSpeed in 10.0..20.0 -> -0.3f
            else -> 0f
        }
        
        val highwayDelta = when {
            chosenSpeed > 22 && avgAltSpeed <= 22 -> 0.3f
            chosenSpeed <= 22 && avgAltSpeed > 22 -> -0.3f
            else -> 0f
        }
        
        return PreferenceDelta(
            timeDelta = timeDelta.coerceIn(-1f, 1f),
            distanceDelta = distanceDelta.coerceIn(-1f, 1f),
            simplicityDelta = simplicityDelta.coerceIn(-1f, 1f),
            scenicDelta = scenicDelta.coerceIn(-1f, 1f),
            highwayDelta = highwayDelta.coerceIn(-1f, 1f)
        )
    }
    
    private fun updateWeight(currentWeight: Float, delta: Float, sampleCount: Int): Float {
        val adaptiveRate = LEARNING_RATE / (1 + sampleCount / 50f)
        return (currentWeight + delta * adaptiveRate).coerceIn(MIN_WEIGHT, MAX_WEIGHT)
    }
    
    private fun updateRoadTypePreference(current: Float, delta: Float, sampleCount: Int): Float {
        val adaptiveRate = LEARNING_RATE / (1 + sampleCount / 50f)
        return (current + delta * adaptiveRate).coerceIn(-1f, 1f)
    }
    
    private fun calculateConfidence(sampleCount: Int): Float {
        return (1 - exp(-sampleCount / 20.0)).toFloat().coerceIn(0f, 1f)
    }
    
    private fun UserPreferenceEntity.toProfile(): UserPreferenceProfile {
        return UserPreferenceProfile(
            timeWeight = timeWeight,
            distanceWeight = distanceWeight,
            simplicityWeight = simplicityWeight,
            scenicWeight = scenicWeight,
            highwayPreference = highwayPreference,
            arterialPreference = arterialPreference,
            residentialPreference = residentialPreference,
            rerouteAcceptanceRate = rerouteAcceptanceRate,
            minimumTimeSavingsThresholdSeconds = minimumTimeSavingsThresholdSeconds,
            totalRoutesAnalyzed = totalRoutesAnalyzed,
            confidenceScore = confidenceScore,
            lastUpdated = updatedAt
        )
    }
    
    private fun UserPreferenceProfile.toEntity(): UserPreferenceEntity {
        return UserPreferenceEntity(
            id = "default",
            timeWeight = timeWeight,
            distanceWeight = distanceWeight,
            simplicityWeight = simplicityWeight,
            scenicWeight = scenicWeight,
            highwayPreference = highwayPreference,
            arterialPreference = arterialPreference,
            residentialPreference = residentialPreference,
            rerouteAcceptanceRate = rerouteAcceptanceRate,
            minimumTimeSavingsThresholdSeconds = minimumTimeSavingsThresholdSeconds,
            totalRoutesAnalyzed = totalRoutesAnalyzed,
            confidenceScore = confidenceScore,
            updatedAt = lastUpdated
        )
    }
}

private data class PreferenceDelta(
    val timeDelta: Float,
    val distanceDelta: Float,
    val simplicityDelta: Float,
    val scenicDelta: Float,
    val highwayDelta: Float
)
```

### Testing Phase 2
- [ ] Engine compiles
- [ ] Unit test: Create profile, update from choice, verify weights changed
- [ ] Unit test: Score two routes, verify ranking works

**Test Example**:
```kotlin
@Test
fun testPreferenceLearning() = runTest {
    val engine = PreferenceLearningEngine(dao)
    
    // Create test routes
    val route1 = Route(duration = 600.0, distance = 5000.0, ...) // Faster
    val route2 = Route(duration = 900.0, distance = 4000.0, ...) // Shorter
    
    // User chooses shorter route (prefers distance)
    val profile = engine.updatePreferencesFromChoice(1, listOf(route1, route2), UserPreferenceProfile.balanced())
    
    // Verify distance weight increased
    assertTrue(profile.distanceWeight > 0.3f)
}
```

---

## Phase 3: Route Scoring Service (6-8 hours)

### Objective
Integrate preference scoring with the routing system.

### Step 3.1: Create Route Scoring Service
**File**: `app/src/main/java/com/wayy/data/learning/RouteScoringService.kt`

```kotlin
package com.wayy.data.learning

import android.content.Context
import android.util.Log
import com.wayy.data.local.LearningDao
import com.wayy.data.local.RouteDecisionContext
import com.wayy.data.model.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RouteScoringService(
    context: Context,
    private val learningDao: LearningDao,
    private val preferenceEngine: PreferenceLearningEngine
) {
    companion object {
        private const val TAG = "RouteScoringService"
    }
    
    private val scope = CoroutineScope(Dispatchers.IO)
    
    suspend fun scoreAndRankRoutes(routes: List<Route>, usePersonalization: Boolean = true): List<ScoredRoute> {
        if (routes.isEmpty()) return emptyList()
        
        return if (usePersonalization) {
            val profile = preferenceEngine.getUserProfile()
            
            if (profile.isReliable()) {
                Log.d(TAG, "Using personalized route ranking (confidence: ${profile.confidenceScore})")
                preferenceEngine.rankRoutes(routes, profile)
            } else {
                Log.d(TAG, "Profile not reliable yet, using default ordering")
                routes.mapIndexed { index, route ->
                    ScoredRoute(
                        route = route,
                        originalIndex = index,
                        score = RouteScoreResult(
                            totalScore = 1.0 - (index * 0.1),
                            timeScore = 0.5, distanceScore = 0.5,
                            simplicityScore = 0.5, scenicScore = 0.5,
                            roadTypeScore = 0.5, normalizedTime = 0.5,
                            normalizedDistance = 0.5, normalizedComplexity = 0.5
                        )
                    )
                }
            }
        } else {
            routes.mapIndexed { index, route ->
                ScoredRoute(
                    route = route,
                    originalIndex = index,
                    score = RouteScoreResult(
                        totalScore = 1.0 - (index * 0.1),
                        timeScore = 0.5, distanceScore = 0.5,
                        simplicityScore = 0.5, scenicScore = 0.5,
                        roadTypeScore = 0.5, normalizedTime = 0.5,
                        normalizedDistance = 0.5, normalizedComplexity = 0.5
                    )
                )
            }
        }
    }
    
    suspend fun getBestRoute(routes: List<Route>): Route? {
        return scoreAndRankRoutes(routes).firstOrNull()?.route
    }
    
    fun recordRouteChoice(chosenRouteIndex: Int, routes: List<Route>, context: RouteDecisionContext) {
        if (routes.size < 2) return
        
        scope.launch {
            try {
                val currentProfile = preferenceEngine.getUserProfile()
                val updatedProfile = preferenceEngine.updatePreferencesFromChoice(
                    chosenRouteIndex, routes, currentProfile
                )
                Log.d(TAG, "Updated preferences: time=${updatedProfile.timeWeight}, distance=${updatedProfile.distanceWeight}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update preferences", e)
            }
        }
    }
    
    fun getRouteScoreExplanation(score: RouteScoreResult, profile: UserPreferenceProfile): RouteExplanation {
        val reasons = mutableListOf<PreferenceReason>()
        
        if (profile.timeWeight > 0.3f) {
            when {
                score.normalizedTime < 0.2 -> reasons.add(
                    PreferenceReason("Fastest route", "Matches your preference for speed", profile.timeWeight)
                )
                score.normalizedTime > 0.8 -> reasons.add(
                    PreferenceReason("Not the fastest", "You usually prefer quicker routes", -profile.timeWeight * 0.5f)
                )
            }
        }
        
        if (profile.distanceWeight > 0.3f && score.normalizedDistance < 0.3) {
            reasons.add(PreferenceReason("Shorter distance", "Matches your preference for shorter routes", profile.distanceWeight))
        }
        
        if (profile.simplicityWeight > 0.3f && score.normalizedComplexity < 0.3) {
            reasons.add(PreferenceReason("Fewer turns", "Matches your preference for simpler routes", profile.simplicityWeight))
        }
        
        return RouteExplanation(
            overallScore = score.totalScore,
            matchPercentage = (score.totalScore * 100).toInt(),
            reasons = reasons.sortedByDescending { it.weight }
        )
    }
}

data class RouteExplanation(
    val overallScore: Double,
    val matchPercentage: Int,
    val reasons: List<PreferenceReason>
)

data class PreferenceReason(
    val title: String,
    val description: String,
    val weight: Float
)
```

### Testing Phase 3
- [ ] Service integrates with ViewModel
- [ ] Test scoring with multiple routes
- [ ] Test recording route choice

---

## Phase 4: Route Decision Logger (4-6 hours)

### Objective
Capture detailed context when users make route decisions.

### Step 4.1: Create Route Decision Logger
**File**: `app/src/main/java/com/wayy/data/local/RouteDecisionLogger.kt`

```kotlin
package com.wayy.data.local

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.wayy.data.model.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

data class RouteDecisionContext(
    val departureTime: Long? = null,
    val estimatedArrival: Long? = null,
    val dayOfWeek: Int,
    val hourOfDay: Int,
    val isWeekend: Boolean,
    val weatherCondition: String? = null,
    val trafficCondition: String? = null,
    val userRushLevel: String? = null
)

data class RouteAlternativeInfo(
    val routeIndex: Int,
    val durationSeconds: Int,
    val distanceMeters: Double,
    val highwayPercentage: Float? = null,
    val tollCount: Int? = null,
    val complexityScore: Float? = null
)

class RouteDecisionLogger(
    private val context: Context,
    private val learningDao: LearningDao
) {
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)
    
    companion object {
        private const val TAG = "RouteDecisionLogger"
    }
    
    fun logRouteChoice(
        tripId: String,
        chosenRouteIndex: Int,
        routes: List<Route>,
        context: RouteDecisionContext,
        wasRerouted: Boolean = false
    ) {
        if (routes.isEmpty() || chosenRouteIndex !in routes.indices) {
            Log.w(TAG, "Invalid route choice data")
            return
        }
        
        val chosenRoute = routes[chosenRouteIndex]
        
        val alternatives = routes.mapIndexed { index, route ->
            RouteAlternativeInfo(
                routeIndex = index,
                durationSeconds = route.duration.toInt(),
                distanceMeters = route.distance,
                highwayPercentage = calculateHighwayPercentage(route),
                complexityScore = calculateComplexityScore(route)
            )
        }
        
        val choice = RouteChoiceEntity(
            choiceId = generateChoiceId(),
            tripId = tripId,
            chosenRouteIndex = chosenRouteIndex,
            chosenRouteDuration = chosenRoute.duration.toInt(),
            chosenRouteDistance = chosenRoute.distance,
            alternativesJson = gson.toJson(alternatives),
            decisionContextJson = gson.toJson(context),
            wasRerouted = wasRerouted,
            departureTime = context.departureTime
        )
        
        scope.launch {
            try {
                learningDao.logRouteChoice(choice)
                Log.d(TAG, "Logged route choice for trip $tripId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log route choice", e)
            }
        }
    }
    
    private fun generateChoiceId(): String {
        return "choice_${UUID.randomUUID().toString().take(8)}_${System.currentTimeMillis()}"
    }
    
    private fun calculateHighwayPercentage(route: Route): Float {
        val avgSpeed = if (route.duration > 0) route.distance / route.duration else 0.0
        return when {
            avgSpeed > 22 -> 0.8f
            avgSpeed > 18 -> 0.6f
            avgSpeed > 14 -> 0.3f
            else -> 0.1f
        }
    }
    
    private fun calculateComplexityScore(route: Route): Float {
        val maneuverCount = route.legs.sumOf { leg -> leg.steps.size }
        return (maneuverCount / 20f).coerceIn(0f, 1f)
    }
}

fun createCurrentRouteDecisionContext(): RouteDecisionContext {
    val now = System.currentTimeMillis()
    val calendar = java.util.Calendar.getInstance()
    calendar.timeInMillis = now
    
    return RouteDecisionContext(
        departureTime = now,
        dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK) - 1,
        hourOfDay = calendar.get(java.util.Calendar.HOUR_OF_DAY),
        isWeekend = calendar.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.SATURDAY ||
                   calendar.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.SUNDAY
    )
}
```

### Testing Phase 4
- [ ] Logger creates valid entities
- [ ] Context captures time correctly
- [ ] JSON serialization works

---

## Phase 5: Learning System Initializer (3-4 hours)

### Objective
Create singleton pattern for learning system initialization.

### Step 5.1: Create Learning System Initializer
**File**: `app/src/main/java/com/wayy/data/local/LearningSystemInitializer.kt`

```kotlin
package com.wayy.data.local

import android.content.Context
import android.util.Log
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
        
        fun getDatabase(context: Context): TripLoggingDatabase {
            return database ?: synchronized(this) {
                database ?: Room.databaseBuilder(
                    context.applicationContext,
                    TripLoggingDatabase::class.java,
                    DATABASE_NAME
                )
                .fallbackToDestructiveMigration()
                .build()
                .also { database = it }
            }
        }
        
        fun getLearningDao(context: Context): LearningDao {
            return learningDao ?: synchronized(this) {
                learningDao ?: getDatabase(context).learningDao().also { learningDao = it }
            }
        }
        
        fun getRouteDecisionLogger(context: Context): RouteDecisionLogger {
            return routeDecisionLogger ?: synchronized(this) {
                routeDecisionLogger ?: RouteDecisionLogger(
                    context.applicationContext,
                    getLearningDao(context)
                ).also { routeDecisionLogger = it }
            }
        }
        
        fun reset() {
            database = null
            learningDao = null
            routeDecisionLogger = null
        }
    }
    
    private val scope = CoroutineScope(Dispatchers.IO)
    
    fun initialize() {
        Log.d(TAG, "Initializing self-learning system...")
        
        scope.launch {
            try {
                initializeDefaultPreferences()
                cleanupExpiredData()
                Log.d(TAG, "Self-learning system initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize learning system", e)
            }
        }
    }
    
    private suspend fun initializeDefaultPreferences() {
        val dao = getLearningDao(context)
        if (dao.getUserPreferences() == null) {
            Log.d(TAG, "Creating default user preferences")
            dao.saveUserPreferences(UserPreferenceEntity())
        }
    }
    
    private suspend fun cleanupExpiredData() {
        getLearningDao(context).cleanupExpiredAnomalies(System.currentTimeMillis())
    }
}

object LearningSystem {
    fun database(context: Context) = LearningSystemInitializer.getDatabase(context)
    fun dao(context: Context) = LearningSystemInitializer.getLearningDao(context)
    fun logger(context: Context) = LearningSystemInitializer.getRouteDecisionLogger(context)
}
```

### Testing Phase 5
- [ ] Singleton pattern works
- [ ] Initialization creates default preferences
- [ ] Cleanup removes expired data

---

## Phase 6: UI Implementation (12-15 hours)

### Objective
Create PreferenceSettingsScreen for users to view and manage preferences.

### Step 6.1: Create Preference Settings Screen
**File**: `app/src/main/java/com/wayy/ui/screens/PreferenceSettingsScreen.kt`

This is a large file (757 lines in existing implementation). Key components:

1. **LearningStatusCard**: Shows confidence and routes analyzed
2. **PreferenceVisualizationCard**: Animated preference bars
3. **RoadTypePreferencesCard**: Highway/main/residential preferences
4. **LearningStatisticsCard**: Stats display
5. **QuickCalibrationDialog**: Preset profiles

**Implementation Steps**:

1. Create the main composable structure
2. Add dependency initialization
3. Implement status cards
4. Add preference visualization with animations
5. Create calibration dialog
6. Add reset functionality

**Key UI Components**:
```kotlin
@Composable
fun PreferenceSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val database = remember { LearningSystemInitializer.getDatabase(context) }
    val learningDao = remember { database.learningDao() }
    val preferenceEngine = remember { PreferenceLearningEngine(learningDao) }
    
    var preferenceStats by remember { mutableStateOf<PreferenceLearningStats?>(null) }
    
    LaunchedEffect(Unit) {
        preferenceStats = preferenceEngine.getLearningStatistics()
    }
    
    Column(modifier = Modifier.fillMaxSize().background(WayyColors.Background)) {
        PreferenceSettingsTopBar(onBack = onBack)
        
        preferenceStats?.let { stats ->
            LearningStatusCard(stats = stats)
            PreferenceVisualizationCard(profile = stats.profile)
            RoadTypePreferencesCard(profile = stats.profile)
            LearningStatisticsCard(stats = stats)
        }
    }
}
```

### Step 6.2: Add Navigation
**File**: `app/src/main/java/com/wayy/MainActivity.kt`

Add PreferenceSettingsScreen to navigation graph:
```kotlin
composable("preference_settings") {
    PreferenceSettingsScreen(
        onBack = { navController.popBackStack() }
    )
}
```

### Testing Phase 6
- [ ] Screen displays correctly
- [ ] Preference bars animate
- [ ] Calibration dialog works
- [ ] Reset functionality works

---

## Phase 7: Integration with Navigation (6-8 hours)

### Objective
Connect learning system with NavigationViewModel.

### Step 7.1: Update NavigationViewModel
**File**: `app/src/main/java/com/wayy/viewmodel/NavigationViewModel.kt`

Add learning system integration:
```kotlin
class NavigationViewModel(
    private val context: Context,
    private val learningDao: LearningDao
) : ViewModel() {
    
    private val preferenceEngine = PreferenceLearningEngine(learningDao)
    private val routeScoringService = RouteScoringService(context, learningDao, preferenceEngine)
    private val routeDecisionLogger = RouteDecisionLogger(context, learningDao)
    
    // When routes are calculated
    suspend fun calculateRoutes(start: LatLng, end: LatLng): List<ScoredRoute> {
        val osrmRoutes = routeRepository.getRoutes(start, end)
        return routeScoringService.scoreAndRankRoutes(osrmRoutes)
    }
    
    // When user selects a route
    fun onRouteSelected(tripId: String, chosenIndex: Int, routes: List<Route>) {
        val context = createCurrentRouteDecisionContext()
        
        // Log the decision
        routeDecisionLogger.logRouteChoice(tripId, chosenIndex, routes, context)
        
        // Update preferences
        routeScoringService.recordRouteChoice(chosenIndex, routes, context)
    }
}
```

### Step 7.2: Update RouteOverviewScreen
Show scored routes with preference indicators:
```kotlin
@Composable
fun RouteOverviewScreen(
    routes: List<ScoredRoute>,
    onRouteSelected: (Int) -> Unit
) {
    LazyColumn {
        items(routes) { scoredRoute ->
            RouteCard(
                route = scoredRoute.route,
                score = scoredRoute.score,
                matchPercentage = (scoredRoute.score.totalScore * 100).toInt(),
                onClick = { onRouteSelected(scoredRoute.originalIndex) }
            )
        }
    }
}
```

### Testing Phase 7
- [ ] Routes are scored and ranked
- [ ] User selection triggers learning
- [ ] Preferences persist across app restarts

---

## Phase 8: Testing & Optimization (6-8 hours)

### Objective
Test the complete system and optimize performance.

### Step 8.1: Write Unit Tests
**File**: `app/src/test/java/com/wayy/learning/PreferenceLearningEngineTest.kt`

```kotlin
@Test
fun testRouteScoring() {
    val route1 = Route(duration = 600.0, distance = 5000.0, ...)
    val route2 = Route(duration = 900.0, distance = 4000.0, ...)
    
    val engine = PreferenceLearningEngine(mockDao)
    val profile = UserPreferenceProfile.balanced()
    
    val scoredRoutes = engine.rankRoutes(listOf(route1, route2), profile)
    
    assertEquals(2, scoredRoutes.size)
    assertTrue(scoredRoutes[0].score.totalScore >= scoredRoutes[1].score.totalScore)
}

@Test
fun testPreferenceUpdate() = runTest {
    val engine = PreferenceLearningEngine(mockDao)
    
    val route1 = Route(duration = 600.0, distance = 5000.0, ...)
    val route2 = Route(duration = 900.0, distance = 4000.0, ...)
    
    // User chooses slower but shorter route
    val updatedProfile = engine.updatePreferencesFromChoice(
        1, listOf(route1, route2), UserPreferenceProfile.balanced()
    )
    
    // Distance weight should increase
    assertTrue(updatedProfile.distanceWeight > 0.3f)
}
```

### Step 8.2: Performance Testing
- [ ] Database queries execute < 50ms
- [ ] Route scoring < 10ms per route
- [ ] No ANRs during learning
- [ ] Memory usage < 50MB

### Step 8.3: Edge Cases
- [ ] Empty routes list
- [ ] Single route (no alternatives)
- [ ] Database corruption handling
- [ ] Very large route count (>100)

---

## Implementation Timeline

| Phase | Hours | Files Created | Complexity |
|-------|-------|---------------|------------|
| 1. Database | 8-10 | 2 (Entities, DAO) | ⭐⭐⭐ |
| 2. Learning Engine | 10-12 | 2 (Models, Engine) | ⭐⭐⭐⭐ |
| 3. Route Scoring | 6-8 | 1 (Service) | ⭐⭐⭐ |
| 4. Decision Logger | 4-6 | 1 (Logger) | ⭐⭐ |
| 5. Initializer | 3-4 | 1 (Initializer) | ⭐⭐ |
| 6. UI | 12-15 | 1 (Screen) | ⭐⭐⭐⭐ |
| 7. Integration | 6-8 | 2 (ViewModel, Screen) | ⭐⭐⭐ |
| 8. Testing | 6-8 | 1+ (Tests) | ⭐⭐⭐ |
| **Total** | **55-71** | **11 files** | **High** |

---

## Files Created/Modified

### New Files (11):
1. `LearningEntities.kt` - Room entities
2. `LearningDao.kt` - Data access
3. `PreferenceModels.kt` - Data classes
4. `PreferenceLearningEngine.kt` - Core algorithm
5. `RouteScoringService.kt` - Scoring service
6. `RouteDecisionLogger.kt` - Decision logging
7. `LearningSystemInitializer.kt` - Initialization
8. `PreferenceSettingsScreen.kt` - UI
9. `PreferenceLearningEngineTest.kt` - Unit tests

### Modified Files (3):
1. `TripLoggingDatabase.kt` - Add entities
2. `NavigationViewModel.kt` - Integration
3. `MainActivity.kt` - Add navigation

---

## Success Criteria

- [ ] User can view learning statistics
- [ ] System learns from route choices
- [ ] Routes are scored and ranked by preference
- [ ] UI shows preference visualization
- [ ] Quick calibration works
- [ ] Reset functionality works
- [ ] All unit tests pass
- [ ] No performance issues
- [ ] Database migrations handled

---

## Next Steps

1. **Phase 1**: Start with database entities
2. **Phase 2**: Implement learning engine
3. **Phase 3-5**: Build supporting services
4. **Phase 6**: Create UI
5. **Phase 7**: Integrate everything
6. **Phase 8**: Test thoroughly

**Estimated Total Time**: 55-71 hours of development work

**Ready to start?** Begin with Phase 1: Database Foundation
