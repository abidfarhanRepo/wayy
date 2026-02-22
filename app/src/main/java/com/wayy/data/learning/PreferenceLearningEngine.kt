package com.wayy.data.learning

import com.wayy.data.local.LearningDao
import com.wayy.data.local.RouteAlternativeInfo
import com.wayy.data.local.UserPreferenceEntity
import com.wayy.data.model.Route
import com.wayy.data.model.RouteStep
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

/**
 * User preference profile data class
 * Represents learned routing preferences
 */
data class UserPreferenceProfile(
    // Weight preferences (0.0 - 1.0) - should sum to 1.0
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
    val totalRoutesAnalyzed: Int = 0,
    val confidenceScore: Float = 0.0f,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * Default balanced profile
         */
        fun balanced() = UserPreferenceProfile(
            timeWeight = 0.5f,
            distanceWeight = 0.3f,
            simplicityWeight = 0.2f,
            scenicWeight = 0.0f
        )
        
        /**
         * Profile for users who prioritize speed
         */
        fun fastest() = UserPreferenceProfile(
            timeWeight = 0.8f,
            distanceWeight = 0.1f,
            simplicityWeight = 0.1f,
            scenicWeight = 0.0f
        )
        
        /**
         * Profile for users who prefer simple routes
         */
        fun simplest() = UserPreferenceProfile(
            timeWeight = 0.3f,
            distanceWeight = 0.2f,
            simplicityWeight = 0.5f,
            scenicWeight = 0.0f
        )
        
        /**
         * Profile for users who prefer scenic routes
         */
        fun scenic() = UserPreferenceProfile(
            timeWeight = 0.2f,
            distanceWeight = 0.1f,
            simplicityWeight = 0.2f,
            scenicWeight = 0.5f
        )
    }
    
    /**
     * Check if profile has enough data to be reliable
     */
    fun isReliable(minRoutes: Int = 10, minConfidence: Float = 0.3f): Boolean {
        return totalRoutesAnalyzed >= minRoutes && confidenceScore >= minConfidence
    }
    
    /**
     * Get dominant preference type
     */
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

enum class PreferenceType {
    TIME, DISTANCE, SIMPLICITY, SCENIC
}

/**
 * Route score result containing both score and breakdown
 */
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

/**
 * Preference Learning Engine
 * Manages user preference learning and route scoring
 */
class PreferenceLearningEngine(
    private val learningDao: LearningDao
) {
    companion object {
        private const val TAG = "PreferenceLearning"
        private const val LEARNING_RATE = 0.1f
        private const val MIN_SAMPLES_FOR_UPDATE = 5
        private const val MAX_WEIGHT = 0.8f
        private const val MIN_WEIGHT = 0.05f
    }
    
    /**
     * Get current user preference profile
     */
    suspend fun getUserProfile(): UserPreferenceProfile {
        val entity = learningDao.getUserPreferences()
        return entity?.toProfile() ?: UserPreferenceProfile.balanced()
    }
    
    /**
     * Flow of user preference profile
     */
    fun getUserProfileFlow(): Flow<UserPreferenceProfile> = flow {
        emit(getUserProfile())
    }
    
    /**
     * Score a route based on user preferences
     * Higher score = better match to user preferences
     * 
     * @param route The route to score
     * @param profile User preference profile
     * @param referenceRoutes All available routes for normalization
     * @return RouteScoreResult with detailed scoring
     */
    fun scoreRoute(
        route: Route,
        profile: UserPreferenceProfile,
        referenceRoutes: List<Route>
    ): RouteScoreResult {
        if (referenceRoutes.isEmpty()) {
            return RouteScoreResult(
                totalScore = 0.5,
                timeScore = 0.5,
                distanceScore = 0.5,
                simplicityScore = 0.5,
                scenicScore = 0.5,
                roadTypeScore = 0.5,
                normalizedTime = 0.5,
                normalizedDistance = 0.5,
                normalizedComplexity = 0.5
            )
        }
        
        // Normalize metrics across all routes
        val minDuration = referenceRoutes.minOfOrNull { it.duration } ?: route.duration
        val maxDuration = referenceRoutes.maxOfOrNull { it.duration } ?: route.duration
        val minDistance = referenceRoutes.minOfOrNull { it.distance } ?: route.distance
        val maxDistance = referenceRoutes.maxOfOrNull { it.distance } ?: route.distance
        
        // Calculate complexity (number of maneuvers)
        val complexity = calculateRouteComplexity(route)
        val complexities = referenceRoutes.map { calculateRouteComplexity(it) }
        val minComplexity = complexities.minOrNull() ?: complexity
        val maxComplexity = complexities.maxOrNull() ?: complexity
        
        // Normalize to 0-1 (lower is better for time/distance/complexity)
        val normalizedTime = normalizeLowerIsBetter(route.duration, minDuration, maxDuration)
        val normalizedDistance = normalizeLowerIsBetter(route.distance, minDistance, maxDistance)
        val normalizedComplexity = normalizeLowerIsBetter(complexity.toDouble(), minComplexity.toDouble(), maxComplexity.toDouble())
        
        // Scenic score (estimate based on road types)
        val scenicScore = estimateScenicScore(route)
        
        // Road type preference score
        val roadTypeScore = calculateRoadTypeScore(route, profile)
        
        // Weighted scores (higher is better)
        val timeScore = (1.0 - normalizedTime) * profile.timeWeight
        val distanceScore = (1.0 - normalizedDistance) * profile.distanceWeight
        val simplicityScore = (1.0 - normalizedComplexity) * profile.simplicityWeight
        val scenicWeightedScore = scenicScore * profile.scenicWeight
        
        // Calculate total score
        val totalScore = timeScore + distanceScore + simplicityScore + scenicWeightedScore + 
                        (roadTypeScore * 0.1) // Small bonus for road type preference
        
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
    
    /**
     * Rank multiple routes based on user preferences
     * Returns routes sorted by preference score (highest first)
     */
    fun rankRoutes(
        routes: List<Route>,
        profile: UserPreferenceProfile
    ): List<ScoredRoute> {
        if (routes.isEmpty()) return emptyList()
        
        return routes.mapIndexed { index, route ->
            val score = scoreRoute(route, profile, routes)
            ScoredRoute(
                route = route,
                originalIndex = index,
                score = score
            )
        }.sortedByDescending { it.score.totalScore }
    }
    
    /**
     * Update user preferences based on route choice
     * Uses online gradient descent to learn from decisions
     * 
     * @param chosenRouteIndex Index of route the user selected
     * @param routes All available route alternatives
     * @param previousProfile Current user profile
     * @return Updated profile
     */
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
        
        // Calculate what made the chosen route different from alternatives
        val preferenceDelta = analyzePreferenceDelta(chosenRoute, alternativeRoutes)
        
        // Update weights using online learning
        var newTimeWeight = updateWeight(
            previousProfile.timeWeight,
            preferenceDelta.timeDelta,
            previousProfile.totalRoutesAnalyzed
        )
        var newDistanceWeight = updateWeight(
            previousProfile.distanceWeight,
            preferenceDelta.distanceDelta,
            previousProfile.totalRoutesAnalyzed
        )
        var newSimplicityWeight = updateWeight(
            previousProfile.simplicityWeight,
            preferenceDelta.simplicityDelta,
            previousProfile.totalRoutesAnalyzed
        )
        var newScenicWeight = updateWeight(
            previousProfile.scenicWeight,
            preferenceDelta.scenicDelta,
            previousProfile.totalRoutesAnalyzed
        )
        
        // Normalize weights to sum to 1.0
        val totalWeight = newTimeWeight + newDistanceWeight + newSimplicityWeight + newScenicWeight
        if (totalWeight > 0) {
            newTimeWeight /= totalWeight
            newDistanceWeight /= totalWeight
            newSimplicityWeight /= totalWeight
            newScenicWeight /= totalWeight
        }
        
        // Update road type preferences
        val newHighwayPreference = updateRoadTypePreference(
            previousProfile.highwayPreference,
            preferenceDelta.highwayDelta,
            previousProfile.totalRoutesAnalyzed
        )
        
        // Calculate new confidence based on sample size
        val newConfidence = calculateConfidence(previousProfile.totalRoutesAnalyzed + 1)
        
        val updatedProfile = previousProfile.copy(
            timeWeight = newTimeWeight,
            distanceWeight = newDistanceWeight,
            simplicityWeight = newSimplicityWeight,
            scenicWeight = newScenicWeight,
            highwayPreference = newHighwayPreference,
            totalRoutesAnalyzed = previousProfile.totalRoutesAnalyzed + 1,
            confidenceScore = newConfidence,
            lastUpdated = System.currentTimeMillis()
        )
        
        // Save to database
        saveProfile(updatedProfile)
        
        return updatedProfile
    }
    
    /**
     * Reset user preferences to default
     */
    suspend fun resetToDefaults() {
        val defaultProfile = UserPreferenceProfile.balanced()
        saveProfile(defaultProfile)
    }
    
    /**
     * Set explicit user preferences (from calibration or settings)
     */
    suspend fun setExplicitPreferences(profile: UserPreferenceProfile) {
        saveProfile(profile.copy(
            lastUpdated = System.currentTimeMillis()
        ))
    }
    
    /**
     * Get preference learning statistics
     */
    suspend fun getLearningStatistics(): PreferenceLearningStats {
        val profile = getUserProfile()
        val routeChoices = learningDao.getRouteChoicesInRange(
            System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000), // 30 days
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
    
    // ==================== Private Helper Methods ====================
    
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
        // Estimate scenic value based on route characteristics
        // Scenic routes often have:
        // - Moderate speeds (not highway)
        // - More turns (winding roads)
        // - Longer distance relative to time
        
        val avgSpeed = if (route.duration > 0) route.distance / route.duration else 0.0
        val complexity = calculateRouteComplexity(route)
        val distanceTimeRatio = if (route.duration > 0) route.distance / route.duration else 0.0
        
        // Moderate speeds (10-20 m/s) suggest scenic roads
        val speedScore = when {
            avgSpeed in 10.0..20.0 -> 0.8
            avgSpeed in 8.0..25.0 -> 0.5
            else -> 0.2
        }
        
        // Some complexity is good for scenic
        val complexityScore = (complexity / 15.0).coerceIn(0.0, 1.0)
        
        return (speedScore * 0.6 + complexityScore * 0.4).coerceIn(0.0, 1.0)
    }
    
    private fun calculateRoadTypeScore(route: Route, profile: UserPreferenceProfile): Double {
        // Estimate highway percentage from average speed
        val avgSpeed = if (route.duration > 0) route.distance / route.duration else 0.0
        
        val highwayPercentage = when {
            avgSpeed > 22 -> 0.8
            avgSpeed > 18 -> 0.6
            avgSpeed > 14 -> 0.3
            else -> 0.1
        }
        
        // Score based on preference match
        return if (profile.highwayPreference > 0) {
            highwayPercentage * profile.highwayPreference
        } else if (profile.highwayPreference < 0) {
            (1 - highwayPercentage) * abs(profile.highwayPreference)
        } else {
            0.5
        }
    }
    
    private fun analyzePreferenceDelta(
        chosen: Route,
        alternatives: List<Route>
    ): PreferenceDelta {
        val avgAltDuration = alternatives.map { it.duration }.average()
        val avgAltDistance = alternatives.map { it.distance }.average()
        val avgAltComplexity = alternatives.map { calculateRouteComplexity(it) }.average()
        
        val chosenComplexity = calculateRouteComplexity(chosen)
        val chosenSpeed = if (chosen.duration > 0) chosen.distance / chosen.duration else 0.0
        val avgAltSpeed = alternatives.map { 
            if (it.duration > 0) it.distance / it.duration else 0.0 
        }.average()
        
        // Calculate deltas (positive = chosen is better for that metric)
        val timeDelta = if (avgAltDuration > 0) {
            ((avgAltDuration - chosen.duration) / avgAltDuration).toFloat()
        } else 0f
        
        val distanceDelta = if (avgAltDistance > 0) {
            ((avgAltDistance - chosen.distance) / avgAltDistance).toFloat()
        } else 0f
        
        val simplicityDelta = if (avgAltComplexity > 0) {
            ((avgAltComplexity - chosenComplexity) / avgAltComplexity).toFloat()
        } else 0f
        
        // Scenic delta (chosen has moderate speed vs alternatives)
        val scenicDelta = when {
            chosenSpeed in 10.0..20.0 && avgAltSpeed > 20 -> 0.5f
            chosenSpeed > 20 && avgAltSpeed in 10.0..20.0 -> -0.3f
            else -> 0f
        }
        
        // Highway preference delta
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
    
    private fun updateWeight(
        currentWeight: Float,
        delta: Float,
        sampleCount: Int
    ): Float {
        // Adaptive learning rate - decreases as we get more samples
        val adaptiveRate = LEARNING_RATE / (1 + sampleCount / 50f)
        
        // Update weight based on delta
        var newWeight = currentWeight + (delta * adaptiveRate)
        
        // Constrain to valid range
        return newWeight.coerceIn(MIN_WEIGHT, MAX_WEIGHT)
    }
    
    private fun updateRoadTypePreference(
        current: Float,
        delta: Float,
        sampleCount: Int
    ): Float {
        val adaptiveRate = LEARNING_RATE / (1 + sampleCount / 50f)
        return (current + delta * adaptiveRate).coerceIn(-1f, 1f)
    }
    
    private fun calculateConfidence(sampleCount: Int): Float {
        // Confidence increases with sample count but plateaus
        // Using a sigmoid-like function
        return (1 - exp(-sampleCount / 20.0)).toFloat().coerceIn(0f, 1f)
    }
    
    // ==================== Extension Functions ====================
    
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

/**
 * Scored route with detailed scoring information
 */
data class ScoredRoute(
    val route: Route,
    val originalIndex: Int,
    val score: RouteScoreResult
)

/**
 * Preference delta from route choice analysis
 */
private data class PreferenceDelta(
    val timeDelta: Float,
    val distanceDelta: Float,
    val simplicityDelta: Float,
    val scenicDelta: Float,
    val highwayDelta: Float
)

/**
 * Preference learning statistics
 */
data class PreferenceLearningStats(
    val totalRoutesAnalyzed: Int,
    val confidenceScore: Float,
    val dominantPreference: PreferenceType,
    val recentRouteChoices: Int,
    val isReliable: Boolean,
    val profile: UserPreferenceProfile
)
