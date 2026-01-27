package com.wayy.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wayy.data.model.Route
import com.wayy.ui.components.navigation.Direction
import com.wayy.data.repository.RouteRepository
import com.wayy.map.MapLibreManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.maplibre.geojson.Point

/**
 * UI State for navigation screen
 */
data class NavigationUiState(
    val isNavigating: Boolean = false,
    val isScanning: Boolean = false,
    val isARMode: Boolean = false,
    val is3DView: Boolean = false,
    val currentRoute: Route? = null,
    val currentLocation: Point? = null,
    val currentBearing: Float = 0f,  // Direction of travel in degrees (0-360)
    val nextDirection: Direction = Direction.STRAIGHT,
    val distanceToTurn: String = "",
    val currentStreet: String = "",
    val eta: String = "",
    val remainingDistance: String = "",
    val roadQuality: Float = 0f,
    val gForce: Float = 0f,
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * ViewModel for navigation functionality
 */
class NavigationViewModel(
    private val routeRepository: RouteRepository = RouteRepository(),
    private val mapManager: MapLibreManager? = null
) : ViewModel() {

    // Demo mode flag - set to false for production
    var isDemoMode: Boolean = true

    private val _uiState = MutableStateFlow(NavigationUiState())
    val uiState: StateFlow<NavigationUiState> = _uiState.asStateFlow()

    private val _speed = MutableStateFlow(0f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _bearing = MutableStateFlow(0f)
    val bearing: StateFlow<Float> = _bearing.asStateFlow()

    private val _destination = MutableStateFlow<Point?>(null)
    val destination: StateFlow<Point?> = _destination.asStateFlow()

    /**
     * Start navigation to a destination
     */
    fun startNavigation(destination: Point) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null
            )

            val currentLoc = _uiState.value.currentLocation
            if (currentLoc == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Location not available. Please enable GPS."
                )
                return@launch
            }

            when (val result = routeRepository.getRoute(currentLoc, destination)) {
                is Result -> {
                    if (result.isSuccess) {
                        val route = result.getOrNull()
                        if (route != null) {
                            _destination.value = destination
                            _uiState.value = _uiState.value.copy(
                                isNavigating = true,
                                currentRoute = route,
                                eta = formatDuration(route.duration),
                                remainingDistance = formatDistance(route.distance),
                                nextDirection = route.legs.firstOrNull()?.steps?.firstOrNull()?.let {
                                    parseDirection(it.maneuver.type, it.maneuver.modifier)
                                } ?: Direction.STRAIGHT,
                                currentStreet = route.legs.firstOrNull()?.steps?.firstOrNull()?.instruction ?: "",
                                distanceToTurn = formatDistance(
                                    route.legs.firstOrNull()?.steps?.firstOrNull()?.distance ?: 0.0
                                ),
                                isLoading = false,
                                error = null
                            )
                        } else {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = "Failed to calculate route"
                            )
                        }
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = result.exceptionOrNull()?.message ?: "Failed to calculate route"
                        )
                    }
                }
            }
        }
    }

    /**
     * Stop navigation
     */
    fun stopNavigation() {
        _destination.value = null
        _uiState.value = _uiState.value.copy(
            isNavigating = false,
            currentRoute = null,
            nextDirection = Direction.STRAIGHT,
            currentStreet = "",
            eta = "",
            remainingDistance = ""
        )
    }

    /**
     * Toggle navigation state
     */
    fun toggleNavigation() {
        if (_uiState.value.isNavigating) {
            stopNavigation()
        }
    }

    /**
     * Toggle scanning mode
     */
    fun toggleScanning() {
        _uiState.value = _uiState.value.copy(
            isScanning = !_uiState.value.isScanning
        )
    }

    /**
     * Toggle AR mode
     */
    fun toggleARMode() {
        _uiState.value = _uiState.value.copy(
            isARMode = !_uiState.value.isARMode
        )
    }

    /**
     * Toggle 3D view
     */
    fun toggle3DView() {
        _uiState.value = _uiState.value.copy(
            is3DView = !_uiState.value.is3DView
        )
    }

    /**
     * Update current location (called from LocationManager)
     * @param location Current GPS location
     * @param speed Current speed in m/s
     * @param bearing Current bearing/direction in degrees (0-360, where 0=North, 90=East)
     */
    fun updateLocation(location: Point, speed: Float = 0f, bearing: Float = 0f) {
        _uiState.value = _uiState.value.copy(
            currentLocation = location,
            currentBearing = bearing
        )
        _speed.value = speed
        _bearing.value = bearing

        // Update navigation progress if navigating
        if (_uiState.value.isNavigating) {
            updateNavigationProgress()
        }
    }

    /**
     * Update simulated stats (for demo purposes)
     */
    fun updateSimulatedStats(roadQuality: Float, gForce: Float) {
        _uiState.value = _uiState.value.copy(
            roadQuality = roadQuality,
            gForce = gForce
        )
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * Update navigation progress as user moves
     */
    private fun updateNavigationProgress() {
        val route = _uiState.value.currentRoute ?: return
        val currentLoc = _uiState.value.currentLocation ?: return

        // In a real app, you would:
        // 1. Find closest point on route
        // 2. Calculate remaining distance
        // 3. Update ETA based on current speed
        // 4. Get next maneuver

        // For demo, just update remaining display
        // This would be replaced with actual routing progress calculation
    }

    /**
     * Parse OSRM maneuver type to Direction enum
     */
    private fun parseDirection(type: String, modifier: String?): Direction {
        return when (type) {
            "arrive" -> Direction.STRAIGHT
            "depart" -> Direction.STRAIGHT
            "continue" -> Direction.STRAIGHT
            "turn" -> when (modifier) {
                "left" -> Direction.LEFT
                "right" -> Direction.RIGHT
                "slight left" -> Direction.SLIGHT_LEFT
                "slight right" -> Direction.SLIGHT_RIGHT
                "uturn" -> Direction.U_TURN
                else -> Direction.STRAIGHT
            }
            "merge" -> Direction.STRAIGHT
            "ramp" -> Direction.SLIGHT_RIGHT
            "fork" -> when (modifier) {
                "left" -> Direction.SLIGHT_LEFT
                "right" -> Direction.SLIGHT_RIGHT
                else -> Direction.STRAIGHT
            }
            else -> Direction.STRAIGHT
        }
    }

    /**
     * Format duration to human-readable string
     */
    private fun formatDuration(seconds: Long): String {
        val minutes = seconds / 60
        return if (minutes < 60) {
            "${minutes}m"
        } else {
            val hours = minutes / 60
            val remainingMinutes = minutes % 60
            if (remainingMinutes > 0) {
                "${hours}h ${remainingMinutes}m"
            } else {
                "${hours}h"
            }
        }
    }

    /**
     * Format distance to human-readable string
     */
    private fun formatDistance(meters: Double): String {
        return if (meters < 1000) {
            "${meters.toInt()}m"
        } else {
            val miles = meters * 0.000621371
            if (miles < 10) {
                String.format("%.1f mi", miles)
            } else {
                String.format("%.0f mi", miles)
            }
        }
    }
}
