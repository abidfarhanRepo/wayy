package com.wayy.data.local

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.wayy.data.model.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.maplibre.geojson.Point
import java.util.UUID

/**
 * Context information for route decision logging
 */
data class RouteDecisionContext(
    val departureTime: Long? = null,
    val estimatedArrival: Long? = null,
    val dayOfWeek: Int,
    val hourOfDay: Int,
    val isWeekend: Boolean,
    val weatherCondition: String? = null, // sunny, rainy, etc.
    val trafficCondition: String? = null, // light, moderate, heavy
    val userRushLevel: String? = null // normal, rushed, relaxed
)

/**
 * Alternative route information for logging
 */
data class RouteAlternativeInfo(
    val routeIndex: Int,
    val durationSeconds: Int,
    val distanceMeters: Double,
    val highwayPercentage: Float? = null,
    val tollCount: Int? = null,
    val complexityScore: Float? = null // number of turns / maneuvers
)

/**
 * Logger for route selection decisions
 * Tracks why users choose one route over alternatives
 */
class RouteDecisionLogger(
    private val context: Context,
    private val learningDao: LearningDao
) {
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)
    
    companion object {
        private const val TAG = "RouteDecisionLogger"
    }

    /**
     * Log a route selection decision
     * 
     * @param tripId The unique trip identifier
     * @param chosenRouteIndex Index of the selected route (0 = first route)
     * @param routes List of all available route alternatives
     * @param context Decision context (time, conditions, etc.)
     * @param wasRerouted Whether this was a reroute during navigation
     */
    fun logRouteChoice(
        tripId: String,
        chosenRouteIndex: Int,
        routes: List<Route>,
        context: RouteDecisionContext,
        wasRerouted: Boolean = false
    ) {
        if (routes.isEmpty() || chosenRouteIndex !in routes.indices) {
            Log.w(TAG, "Invalid route choice data - skipping log")
            return
        }
        
        val chosenRoute = routes[chosenRouteIndex]
        
        // Build alternative info list
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
            departureTime = context.departureTime,
            arrivalTime = null // Will be updated when trip completes
        )
        
        scope.launch {
            try {
                learningDao.logRouteChoice(choice)
                Log.d(TAG, "Logged route choice for trip $tripId - chose route $chosenRouteIndex")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log route choice", e)
            }
        }
    }
    
    /**
     * Log a simplified route choice with just basic info
     */
    fun logSimpleRouteChoice(
        tripId: String,
        chosenRouteIndex: Int,
        routeDurations: List<Int>,
        routeDistances: List<Double>,
        wasRerouted: Boolean = false
    ) {
        val routes = routeDurations.zip(routeDistances).map { (duration, distance) ->
            RouteAlternativeInfo(
                routeIndex = 0, // Will be overwritten
                durationSeconds = duration,
                distanceMeters = distance
            )
        }.mapIndexed { index, info -> info.copy(routeIndex = index) }
        
        val now = System.currentTimeMillis()
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = now
        
        val context = RouteDecisionContext(
            departureTime = now,
            dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK) - 1, // 0-6
            hourOfDay = calendar.get(java.util.Calendar.HOUR_OF_DAY),
            isWeekend = calendar.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.SATURDAY ||
                       calendar.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.SUNDAY
        )
        
        // Build dummy Route objects for the main function
        val dummyRoutes = routeDurations.zip(routeDistances).map { (duration, distance) ->
            Route(
                geometry = emptyList(),
                duration = duration.toDouble(),
                distance = distance,
                legs = emptyList()
            )
        }
        
        logRouteChoice(tripId, chosenRouteIndex, dummyRoutes, context, wasRerouted)
    }
    
    /**
     * Update the arrival time for a logged route choice
     */
    fun updateArrivalTime(tripId: String, arrivalTime: Long) {
        scope.launch {
            try {
                val choice = learningDao.getRouteChoiceForTrip(tripId)
                choice?.let {
                    // Note: Room doesn't support partial updates easily, 
                    // so we'd need to either:
                    // 1. Add an update method to DAO
                    // 2. Delete and re-insert
                    // For Phase 1, we'll just log this
                    Log.d(TAG, "Would update arrival time for trip $tripId to $arrivalTime")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update arrival time", e)
            }
        }
    }
    
    /**
     * Log preference data from route choice
     * Analyzes the choice to infer user preferences
     */
    fun analyzeAndLogPreference(
        tripId: String,
        chosenRouteIndex: Int,
        routes: List<Route>
    ) {
        if (routes.size < 2) return // Need alternatives to infer preference
        
        scope.launch {
            try {
                val chosen = routes[chosenRouteIndex]
                
                // Find fastest route
                val fastestRoute = routes.minByOrNull { it.duration }
                val shortestRoute = routes.minByOrNull { it.distance }
                
                val preferenceDelta = mutableMapOf<String, Float>()
                
                // Check if user chose fastest
                fastestRoute?.let { fastest ->
                    if (fastest !== chosen) {
                        val timeDiff = fastest.duration - chosen.duration
                        val percentSlower = (timeDiff / fastest.duration * 100).toFloat()
                        preferenceDelta["time_sensitivity"] = 1.0f - (percentSlower / 100f).coerceIn(0f, 1f)
                    } else {
                        preferenceDelta["time_sensitivity"] = 1.0f
                    }
                }
                
                // Check if user chose shortest
                shortestRoute?.let { shortest ->
                    if (shortest !== chosen) {
                        val distDiff = shortest.distance - chosen.distance
                        val percentLonger = (distDiff / shortest.distance * 100).toFloat()
                        preferenceDelta["distance_sensitivity"] = 1.0f - (percentLonger / 100f).coerceIn(0f, 1f)
                    } else {
                        preferenceDelta["distance_sensitivity"] = 1.0f
                    }
                }
                
                Log.d(TAG, "Preference analysis for trip $tripId: $preferenceDelta")
                
                // In Phase 2, this would update the user preference model
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to analyze preferences", e)
            }
        }
    }
    
    private fun generateChoiceId(): String {
        return "choice_${UUID.randomUUID().toString().take(8)}_${System.currentTimeMillis()}"
    }
    
    private fun calculateHighwayPercentage(route: Route): Float {
        // Simplified calculation - in production would analyze steps
        // For now, estimate based on average speed (faster = more highway)
        val totalDistance = route.distance
        val totalDuration = route.duration
        
        if (totalDuration <= 0) return 0f
        
        val avgSpeedMps = totalDistance / totalDuration
        // Highway speeds typically > 80 km/h (22 m/s)
        // Local speeds typically < 50 km/h (14 m/s)
        return when {
            avgSpeedMps > 22 -> 0.8f
            avgSpeedMps > 18 -> 0.6f
            avgSpeedMps > 14 -> 0.3f
            else -> 0.1f
        }
    }
    
    private fun calculateComplexityScore(route: Route): Float {
        // Count maneuvers as complexity indicator
        val maneuverCount = route.legs.sumOf { leg -> leg.steps.size }
        return (maneuverCount / 20f).coerceIn(0f, 1f) // Normalize to 0-1
    }
}

/**
 * Helper function to create a RouteDecisionContext for the current time
 */
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
