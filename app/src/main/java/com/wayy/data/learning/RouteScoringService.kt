package com.wayy.data.learning

import android.content.Context
import android.util.Log
import com.wayy.data.local.LearningDao
import com.wayy.data.local.RouteDecisionContext
import com.wayy.data.model.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Service for integrating preference-based route scoring with the routing system
 */
class RouteScoringService(
    context: Context,
    private val learningDao: LearningDao,
    private val preferenceEngine: PreferenceLearningEngine
) {
    companion object {
        private const val TAG = "RouteScoringService"
    }
    
    private val scope = CoroutineScope(Dispatchers.IO)
    
    /**
     * Score and rank multiple route alternatives
     * Returns routes sorted by preference score (best match first)
     * 
     * @param routes List of route alternatives from OSRM
     * @param usePersonalization Whether to use learned preferences (false = default OSRM ordering)
     * @return List of scored routes sorted by preference
     */
    suspend fun scoreAndRankRoutes(
        routes: List<Route>,
        usePersonalization: Boolean = true
    ): List<ScoredRoute> {
        if (routes.isEmpty()) return emptyList()
        
        return if (usePersonalization) {
            val profile = preferenceEngine.getUserProfile()
            
            if (profile.isReliable()) {
                Log.d(TAG, "Using personalized route ranking (confidence: ${profile.confidenceScore})")
                preferenceEngine.rankRoutes(routes, profile)
            } else {
                Log.d(TAG, "Profile not reliable yet (${profile.totalRoutesAnalyzed} routes), using default ordering")
                // Return routes in original order with neutral scores
                routes.mapIndexed { index, route ->
                    ScoredRoute(
                        route = route,
                        originalIndex = index,
                        score = RouteScoreResult(
                            totalScore = 1.0 - (index * 0.1), // Slight preference for OSRM's first route
                            timeScore = 0.5,
                            distanceScore = 0.5,
                            simplicityScore = 0.5,
                            scenicScore = 0.5,
                            roadTypeScore = 0.5,
                            normalizedTime = 0.5,
                            normalizedDistance = 0.5,
                            normalizedComplexity = 0.5
                        )
                    )
                }
            }
        } else {
            // Default OSRM ordering - don't re-rank
            routes.mapIndexed { index, route ->
                ScoredRoute(
                    route = route,
                    originalIndex = index,
                    score = RouteScoreResult(
                        totalScore = 1.0 - (index * 0.1),
                        timeScore = 0.5,
                        distanceScore = 0.5,
                        simplicityScore = 0.5,
                        scenicScore = 0.5,
                        roadTypeScore = 0.5,
                        normalizedTime = 0.5,
                        normalizedDistance = 0.5,
                        normalizedComplexity = 0.5
                    )
                )
            }
        }
    }
    
    /**
     * Get the best route according to user preferences
     */
    suspend fun getBestRoute(routes: List<Route>): Route? {
        val scoredRoutes = scoreAndRankRoutes(routes)
        return scoredRoutes.firstOrNull()?.route
    }
    
    /**
     * Record a route choice for learning
     * Call this when user selects a route
     * 
     * @param chosenRouteIndex The index of chosen route in the original OSRM response
     * @param routes All available routes
     * @param context Decision context
     */
    fun recordRouteChoice(
        chosenRouteIndex: Int,
        routes: List<Route>,
        context: RouteDecisionContext
    ) {
        if (routes.size < 2) return // Need alternatives to learn
        
        scope.launch {
            try {
                val currentProfile = preferenceEngine.getUserProfile()
                
                // Update preferences based on this choice
                val updatedProfile = preferenceEngine.updatePreferencesFromChoice(
                    chosenRouteIndex,
                    routes,
                    currentProfile
                )
                
                Log.d(TAG, "Updated preferences from route choice: " +
                    "time=${updatedProfile.timeWeight}, " +
                    "distance=${updatedProfile.distanceWeight}, " +
                    "simplicity=${updatedProfile.simplicityWeight}")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update preferences from route choice", e)
            }
        }
    }
    
    /**
     * Get explanation of why a route was ranked a certain way
     * Useful for UI display
     */
    fun getRouteScoreExplanation(score: RouteScoreResult, profile: UserPreferenceProfile): RouteExplanation {
        val reasons = mutableListOf<PreferenceReason>()
        
        // Time preference
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
        
        // Distance preference
        if (profile.distanceWeight > 0.3f && score.normalizedDistance < 0.3) {
            reasons.add(PreferenceReason("Shorter distance", "Matches your preference for shorter routes", profile.distanceWeight))
        }
        
        // Simplicity preference
        if (profile.simplicityWeight > 0.3f && score.normalizedComplexity < 0.3) {
            reasons.add(PreferenceReason("Fewer turns", "Matches your preference for simpler routes", profile.simplicityWeight))
        }
        
        // Scenic preference
        if (profile.scenicWeight > 0.3f && score.scenicScore > 0.5) {
            reasons.add(PreferenceReason("Scenic route", "Matches your preference for scenic drives", profile.scenicWeight))
        }
        
        return RouteExplanation(
            overallScore = score.totalScore,
            matchPercentage = (score.totalScore * 100).toInt(),
            reasons = reasons.sortedByDescending { it.weight }
        )
    }
    
    /**
     * Compare two routes and explain the differences
     */
    fun compareRoutes(
        routeA: Route,
        routeB: Route,
        profile: UserPreferenceProfile
    ): RouteComparison {
        val routes = listOf(routeA, routeB)
        val scoreA = preferenceEngine.scoreRoute(routeA, profile, routes)
        val scoreB = preferenceEngine.scoreRoute(routeB, profile, routes)
        
        val timeDiff = routeA.duration - routeB.duration
        val distanceDiff = routeA.distance - routeB.distance
        val complexityA = calculateComplexity(routeA)
        val complexityB = calculateComplexity(routeB)
        
        val differences = mutableListOf<RouteDifference>()
        
        // Time difference
        if (kotlin.math.abs(timeDiff) > 60) { // More than 1 minute
            differences.add(RouteDifference(
                category = "Time",
                routeAValue = formatDuration(routeA.duration),
                routeBValue = formatDuration(routeB.duration),
                difference = formatTimeDiff(timeDiff),
                favoring = if (timeDiff < 0) "Route A" else "Route B",
                importance = profile.timeWeight
            ))
        }
        
        // Distance difference
        if (kotlin.math.abs(distanceDiff) > 500) { // More than 500m
            differences.add(RouteDifference(
                category = "Distance",
                routeAValue = formatDistance(routeA.distance),
                routeBValue = formatDistance(routeB.distance),
                difference = formatDistanceDiff(distanceDiff),
                favoring = if (distanceDiff < 0) "Route A" else "Route B",
                importance = profile.distanceWeight
            ))
        }
        
        // Complexity difference
        if (kotlin.math.abs(complexityA - complexityB) > 3) {
            differences.add(RouteDifference(
                category = "Complexity",
                routeAValue = "$complexityA turns",
                routeBValue = "$complexityB turns",
                difference = "${kotlin.math.abs(complexityA - complexityB)} fewer turns",
                favoring = if (complexityA < complexityB) "Route A" else "Route B",
                importance = profile.simplicityWeight
            ))
        }
        
        return RouteComparison(
            routeAScore = scoreA.totalScore,
            routeBScore = scoreB.totalScore,
            winner = if (scoreA.totalScore > scoreB.totalScore) "Route A" else "Route B",
            scoreDifference = kotlin.math.abs(scoreA.totalScore - scoreB.totalScore),
            differences = differences.sortedByDescending { it.importance }
        )
    }
    
    // ==================== Private Helpers ====================
    
    private fun calculateComplexity(route: Route): Int {
        return route.legs.sumOf { leg -> leg.steps.size }
    }
    
    private fun formatDuration(seconds: Double): String {
        val mins = (seconds / 60).toInt()
        return if (mins < 60) "$mins min" else "${mins / 60}h ${mins % 60}min"
    }
    
    private fun formatDistance(meters: Double): String {
        return if (meters < 1000) "${meters.toInt()}m" else "${(meters / 1000).toInt()}km"
    }
    
    private fun formatTimeDiff(diff: Double): String {
        val mins = kotlin.math.abs((diff / 60).toInt())
        return if (diff < 0) "$mins min faster" else "$mins min slower"
    }
    
    private fun formatDistanceDiff(diff: Double): String {
        return if (diff < 0) "${kotlin.math.abs(diff).toInt()}m shorter" else "${diff.toInt()}m longer"
    }
}

/**
 * Explanation of route scoring
 */
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

/**
 * Comparison between two routes
 */
data class RouteComparison(
    val routeAScore: Double,
    val routeBScore: Double,
    val winner: String,
    val scoreDifference: Double,
    val differences: List<RouteDifference>
)

data class RouteDifference(
    val category: String,
    val routeAValue: String,
    val routeBValue: String,
    val difference: String,
    val favoring: String,
    val importance: Float
)
