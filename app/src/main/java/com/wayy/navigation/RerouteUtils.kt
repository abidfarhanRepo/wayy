package com.wayy.navigation

import com.wayy.data.model.Route
import com.wayy.data.repository.RouteRepository
import org.maplibre.geojson.Point

sealed class RerouteResult {
    data class Success(val route: Route) : RerouteResult()
    data class Failed(val reason: String) : RerouteResult()
    object NotNeeded : RerouteResult()
    object InProgress : RerouteResult()
}

class RerouteUtils(
    private val routeRepository: RouteRepository = RouteRepository()
) {

    private var lastRerouteTime: Long = 0
    private var consecutiveOffRouteCount: Int = 0
    private var lastOffRouteLocation: Point? = null

    companion object {
        private const val DEFAULT_OFF_ROUTE_THRESHOLD_METERS = 50.0
        private const val MIN_REROUTE_INTERVAL_MS = 5000L
        private const val OFF_ROUTE_CONFIRMATION_COUNT = 3
        private const val MAX_OFF_ROUTE_THRESHOLD_METERS = 100.0
    }

    suspend fun checkAndReroute(
        currentLocation: Point,
        currentRoute: Route,
        destination: Point,
        offRouteThresholdMeters: Double = DEFAULT_OFF_ROUTE_THRESHOLD_METERS
    ): RerouteResult {
        val isOffRoute = checkIfOffRoute(
            currentLocation,
            currentRoute,
            offRouteThresholdMeters
        )

        if (!isOffRoute) {
            consecutiveOffRouteCount = 0
            lastOffRouteLocation = null
            return RerouteResult.NotNeeded
        }

        consecutiveOffRouteCount++
        lastOffRouteLocation = currentLocation

        if (consecutiveOffRouteCount < OFF_ROUTE_CONFIRMATION_COUNT) {
            return RerouteResult.NotNeeded
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastRerouteTime < MIN_REROUTE_INTERVAL_MS) {
            return RerouteResult.InProgress
        }

        return calculateReroute(currentLocation, destination)
    }

    fun checkIfOffRoute(
        currentLocation: Point,
        route: Route,
        thresholdMeters: Double = DEFAULT_OFF_ROUTE_THRESHOLD_METERS
    ): Boolean {
        if (route.geometry.isEmpty()) return true

        return !NavigationUtils.isPointOnRoute(
            currentLocation,
            route.geometry,
            thresholdMeters
        )
    }

    suspend fun calculateReroute(
        origin: Point,
        destination: Point
    ): RerouteResult {
        lastRerouteTime = System.currentTimeMillis()

        val result = routeRepository.getRoute(origin, destination)
        return if (result.isSuccess) {
            val route = result.getOrNull()
            if (route != null) {
                RerouteResult.Success(route)
            } else {
                RerouteResult.Failed("No route found")
            }
        } else {
            RerouteResult.Failed(result.exceptionOrNull()?.message ?: "Route calculation failed")
        }
    }

    fun getDistanceFromRoute(location: Point, route: Route): Double {
        if (route.geometry.isEmpty()) return Double.MAX_VALUE

        val (_, distance) = NavigationUtils.findClosestPointOnRoute(location, route.geometry)
        return distance
    }

    fun getAdaptiveThreshold(currentSpeed: Float): Double {
        val baseThreshold = DEFAULT_OFF_ROUTE_THRESHOLD_METERS
        val speedFactor = (currentSpeed / 10f).coerceIn(0f, 3f)
        return (baseThreshold + (speedFactor * 15)).coerceAtMost(MAX_OFF_ROUTE_THRESHOLD_METERS)
    }

    fun isApproachingDestination(
        currentLocation: Point,
        destination: Point,
        thresholdMeters: Double = 30.0
    ): Boolean {
        return NavigationUtils.calculateDistanceMeters(currentLocation, destination) <= thresholdMeters
    }

    fun resetState() {
        lastRerouteTime = 0
        consecutiveOffRouteCount = 0
        lastOffRouteLocation = null
    }

    fun getLastOffRouteLocation(): Point? = lastOffRouteLocation
}
