package com.wayy.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wayy.data.repository.LocalPoiItem
import com.wayy.data.repository.LocalPoiManager
import com.wayy.data.repository.TrafficReportItem
import com.wayy.data.repository.TrafficReportManager
import com.wayy.data.repository.RouteHistoryItem
import com.wayy.data.repository.RouteHistoryManager
import com.wayy.data.model.Route
import com.wayy.data.repository.PlaceResult
import com.wayy.data.repository.RouteRepository
import com.wayy.navigation.NavigationUtils
import com.wayy.navigation.RerouteResult
import com.wayy.navigation.RerouteUtils
import com.wayy.navigation.TurnInstruction
import com.wayy.navigation.TurnInstructionProvider
import com.wayy.ui.components.navigation.Direction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.maplibre.geojson.Point

sealed class NavigationState {
    object Idle : NavigationState()
    object Searching : NavigationState()
    object Routing : NavigationState()
    data class Navigating(val route: Route) : NavigationState()
    object Rerouting : NavigationState()
    object Arrived : NavigationState()
}

data class NavigationUiState(
    val navigationState: NavigationState = NavigationState.Idle,
    val isNavigating: Boolean = false,
    val isScanning: Boolean = false,
    val isARMode: Boolean = false,
    val is3DView: Boolean = false,
    val isCameraFollowing: Boolean = true,
    val currentRoute: Route? = null,
    val currentLocation: Point? = null,
    val currentBearing: Float = 0f,
    val currentSpeed: Float = 0f,
    val nextDirection: Direction = Direction.STRAIGHT,
    val nextNextDirection: Direction = Direction.STRAIGHT,
    val distanceToTurn: String = "",
    val distanceToTurnMeters: Double = 0.0,
    val currentInstruction: String = "",
    val currentStreet: String = "",
    val nextStreet: String = "",
    val eta: String = "",
    val remainingDistance: String = "",
    val remainingDistanceMeters: Double = 0.0,
    val currentStepIndex: Int = 0,
    val totalSteps: Int = 0,
    val roadQuality: Float = 0f,
    val gForce: Float = 0f,
    val speedLimitMph: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isOffRoute: Boolean = false,
    val isApproachingTurn: Boolean = false,
    val lastAnnouncedDistance: Double? = null
)

class NavigationViewModel(
    private val routeRepository: RouteRepository = RouteRepository(),
    private val turnProvider: TurnInstructionProvider = TurnInstructionProvider(),
    private val rerouteUtils: RerouteUtils = RerouteUtils(),
    private val routeHistoryManager: RouteHistoryManager? = null,
    private val localPoiManager: LocalPoiManager? = null,
    private val trafficReportManager: TrafficReportManager? = null
) : ViewModel() {

    var isDemoMode: Boolean = false

    private val _uiState = MutableStateFlow(NavigationUiState())
    val uiState: StateFlow<NavigationUiState> = _uiState.asStateFlow()

    private val _speed = MutableStateFlow(0f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _bearing = MutableStateFlow(0f)
    val bearing: StateFlow<Float> = _bearing.asStateFlow()

    private val _destination = MutableStateFlow<Point?>(null)
    val destination: StateFlow<Point?> = _destination.asStateFlow()

    private val _searchResults = MutableStateFlow<List<PlaceResult>>(emptyList())
    val searchResults: StateFlow<List<PlaceResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    val recentRoutes = routeHistoryManager?.recentRoutes?.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val localPois = localPoiManager?.recentPois?.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val trafficReports = trafficReportManager?.recentReports?.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    fun startNavigation(destination: Point, destinationName: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                navigationState = NavigationState.Routing,
                isLoading = true,
                error = null
            )

            val currentLoc = _uiState.value.currentLocation
            if (currentLoc == null) {
                _uiState.value = _uiState.value.copy(
                    navigationState = NavigationState.Idle,
                    isLoading = false,
                    error = "Location not available. Please enable GPS."
                )
                return@launch
            }

            when (val result = routeRepository.getRoute(currentLoc, destination)) {
                is kotlin.Result -> {
                    if (result.isSuccess) {
                        val route = result.getOrNull()
                        if (route != null) {
                            initializeNavigation(route, destination, destinationName)
                        } else {
                            _uiState.value = _uiState.value.copy(
                                navigationState = NavigationState.Idle,
                                isLoading = false,
                                error = "Failed to calculate route"
                            )
                        }
                    } else {
                        _uiState.value = _uiState.value.copy(
                            navigationState = NavigationState.Idle,
                            isLoading = false,
                            error = result.exceptionOrNull()?.message ?: "Failed to calculate route"
                        )
                    }
                }
            }
        }
    }

    fun searchPlaces(query: String) {
        viewModelScope.launch {
            _isSearching.value = true
            _searchError.value = null

            val result = routeRepository.searchPlaces(query)
            if (result.isSuccess) {
                _searchResults.value = result.getOrNull().orEmpty()
            } else {
                _searchResults.value = emptyList()
                _searchError.value = result.exceptionOrNull()?.message ?: "Search failed"
            }

            _isSearching.value = false
        }
    }

    fun clearSearchResults() {
        _searchResults.value = emptyList()
        _isSearching.value = false
        _searchError.value = null
    }

    fun addLocalPoi(name: String, category: String) {
        val location = _uiState.value.currentLocation
        if (localPoiManager == null || location == null) {
            _uiState.value = _uiState.value.copy(
                error = "Location not available for POI"
            )
            return
        }
        viewModelScope.launch {
            val item = LocalPoiItem(
                id = localPoiManager.generatePoiId(location.latitude(), location.longitude()),
                name = name,
                category = category.ifBlank { "General" },
                lat = location.latitude(),
                lng = location.longitude(),
                timestamp = System.currentTimeMillis()
            )
            localPoiManager.addPoi(item)
        }
    }

    fun reportTraffic() {
        val location = _uiState.value.currentLocation
        if (trafficReportManager == null || location == null) {
            _uiState.value = _uiState.value.copy(
                error = "Location not available for traffic report"
            )
            return
        }
        val speedMps = _uiState.value.currentSpeed / 2.23694f
        val severity = when {
            speedMps < 2 -> "stopped"
            speedMps < 6 -> "slow"
            speedMps < 12 -> "moderate"
            else -> "clear"
        }
        viewModelScope.launch {
            val report = TrafficReportItem(
                id = trafficReportManager.generateReportId(
                    location.latitude(),
                    location.longitude()
                ),
                lat = location.latitude(),
                lng = location.longitude(),
                speedMps = speedMps,
                severity = severity,
                timestamp = System.currentTimeMillis()
            )
            trafficReportManager.addReport(report)
        }
    }

    private fun initializeNavigation(route: Route, destination: Point, destinationName: String?) {
        val startLocation = _uiState.value.currentLocation
        _destination.value = destination

        val firstInstruction = turnProvider.getCurrentInstruction(
            _uiState.value.currentLocation ?: return,
            route,
            0
        )

        val nextInstruction = turnProvider.getNextInstruction(route, 0)
        val totalSteps = route.legs.sumOf { it.steps.size }

        _uiState.value = _uiState.value.copy(
            navigationState = NavigationState.Navigating(route),
            isNavigating = true,
            currentRoute = route,
            eta = NavigationUtils.formatDuration(route.duration),
            remainingDistance = NavigationUtils.formatDistance(route.distance),
            remainingDistanceMeters = route.distance,
            currentStepIndex = 0,
            totalSteps = totalSteps,
            nextDirection = firstInstruction?.direction ?: Direction.STRAIGHT,
            nextNextDirection = nextInstruction?.direction ?: Direction.STRAIGHT,
            distanceToTurn = firstInstruction?.distanceText ?: "",
            distanceToTurnMeters = firstInstruction?.distanceMeters ?: 0.0,
            currentInstruction = firstInstruction?.instruction ?: "",
            currentStreet = firstInstruction?.streetName ?: "",
            nextStreet = nextInstruction?.streetName ?: "",
            isApproachingTurn = (firstInstruction?.distanceMeters ?: 0.0) < 200,
            isLoading = false,
            error = null,
            isOffRoute = false,
            isCameraFollowing = true
        )

        rerouteUtils.resetState()

        if (routeHistoryManager != null && startLocation != null) {
            viewModelScope.launch {
                val routeId = routeHistoryManager.generateRouteId(
                    startLat = startLocation.latitude(),
                    startLng = startLocation.longitude(),
                    endLat = destination.latitude(),
                    endLng = destination.longitude()
                )
                val item = RouteHistoryItem(
                    id = routeId,
                    startLat = startLocation.latitude(),
                    startLng = startLocation.longitude(),
                    endLat = destination.latitude(),
                    endLng = destination.longitude(),
                    startName = "Current location",
                    endName = destinationName ?: "Destination",
                    distanceMeters = route.distance,
                    durationSeconds = route.duration,
                    timestamp = System.currentTimeMillis()
                )
                routeHistoryManager.addRoute(item)
            }
        }
    }

    fun stopNavigation() {
        _destination.value = null
        rerouteUtils.resetState()

        _uiState.value = _uiState.value.copy(
            navigationState = NavigationState.Idle,
            isNavigating = false,
            currentRoute = null,
            currentStepIndex = 0,
            nextDirection = Direction.STRAIGHT,
            currentStreet = "",
            nextStreet = "",
            eta = "",
            remainingDistance = "",
            remainingDistanceMeters = 0.0,
            isOffRoute = false,
            lastAnnouncedDistance = null
        )
    }

    fun toggleNavigation() {
        if (_uiState.value.isNavigating) {
            stopNavigation()
        }
    }

    fun toggleScanning() {
        _uiState.value = _uiState.value.copy(
            isScanning = !_uiState.value.isScanning
        )
    }

    fun toggleARMode() {
        _uiState.value = _uiState.value.copy(
            isARMode = !_uiState.value.isARMode
        )
    }

    fun toggle3DView() {
        _uiState.value = _uiState.value.copy(
            is3DView = !_uiState.value.is3DView
        )
    }

    fun toggleCameraFollow() {
        _uiState.value = _uiState.value.copy(
            isCameraFollowing = !_uiState.value.isCameraFollowing
        )
    }

    fun setSpeedLimit(limitMph: Int) {
        _uiState.value = _uiState.value.copy(speedLimitMph = limitMph)
    }

    fun updateLocation(location: Point, speed: Float = 0f, bearing: Float = 0f) {
        _uiState.value = _uiState.value.copy(
            currentLocation = location,
            currentBearing = bearing,
            currentSpeed = speed
        )
        _speed.value = speed
        _bearing.value = bearing

        if (_uiState.value.isNavigating) {
            updateNavigationProgress(location, speed)
        }
    }

    private fun updateNavigationProgress(location: Point, speed: Float) {
        val route = _uiState.value.currentRoute ?: return
        val destination = _destination.value ?: return

        if (rerouteUtils.isApproachingDestination(location, destination)) {
            handleArrival()
            return
        }

        viewModelScope.launch {
            val adaptiveThreshold = rerouteUtils.getAdaptiveThreshold(speed)
            val rerouteResult = rerouteUtils.checkAndReroute(
                location,
                route,
                destination,
                adaptiveThreshold
            )

            when (rerouteResult) {
                is RerouteResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        currentRoute = rerouteResult.route,
                        navigationState = NavigationState.Navigating(rerouteResult.route),
                        isOffRoute = false,
                        eta = NavigationUtils.formatDuration(rerouteResult.route.duration),
                        remainingDistance = NavigationUtils.formatDistance(rerouteResult.route.distance)
                    )
                }
                is RerouteResult.Failed -> {
                    _uiState.value = _uiState.value.copy(isOffRoute = true)
                }
                is RerouteResult.NotNeeded -> {
                    _uiState.value = _uiState.value.copy(isOffRoute = false)
                }
                is RerouteResult.InProgress -> {
                    _uiState.value = _uiState.value.copy(navigationState = NavigationState.Rerouting)
                }
            }
        }

        val newStepIndex = turnProvider.findCurrentStepIndex(
            location,
            route,
            _uiState.value.currentStepIndex
        )

        val currentInstruction = turnProvider.getCurrentInstruction(location, route, newStepIndex)
        val nextInstruction = turnProvider.getNextInstruction(route, newStepIndex)

        val (closestIndex, _) = NavigationUtils.findClosestPointOnRoute(location, route.geometry)
        val remainingMeters = NavigationUtils.calculateRemainingDistance(
            location,
            route.geometry,
            closestIndex
        )
        val etaSeconds = calculateEtaSeconds(route, remainingMeters, speed)

        _uiState.value = _uiState.value.copy(
            currentStepIndex = newStepIndex,
            nextDirection = currentInstruction?.direction ?: Direction.STRAIGHT,
            nextNextDirection = nextInstruction?.direction ?: Direction.STRAIGHT,
            distanceToTurn = currentInstruction?.distanceText ?: "",
            distanceToTurnMeters = currentInstruction?.distanceMeters ?: 0.0,
            currentInstruction = currentInstruction?.instruction ?: "",
            currentStreet = currentInstruction?.streetName ?: "",
            nextStreet = nextInstruction?.streetName ?: "",
            remainingDistanceMeters = remainingMeters,
            remainingDistance = NavigationUtils.formatDistance(remainingMeters),
            isApproachingTurn = (currentInstruction?.distanceMeters ?: 0.0) < 200,
            eta = NavigationUtils.formatDuration(etaSeconds)
        )
    }

    private fun calculateEtaSeconds(route: Route, remainingMeters: Double, speedMph: Float): Double {
        val speedMps = speedMph / 2.23694f
        if (speedMps >= 1f) {
            return remainingMeters / speedMps
        }
        val averageSpeedMps = if (route.duration > 0) {
            route.distance / route.duration
        } else {
            0.0
        }
        return if (averageSpeedMps > 0) {
            remainingMeters / averageSpeedMps
        } else {
            route.duration
        }
    }

    private fun handleArrival() {
        _uiState.value = _uiState.value.copy(
            navigationState = NavigationState.Arrived,
            isNavigating = false,
            nextDirection = Direction.STRAIGHT,
            distanceToTurn = "",
            currentStreet = "Destination",
            eta = "Arrived",
            remainingDistance = "0m",
            remainingDistanceMeters = 0.0
        )
    }

    fun updateSimulatedStats(roadQuality: Float, gForce: Float) {
        _uiState.value = _uiState.value.copy(
            roadQuality = roadQuality,
            gForce = gForce
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun getCurrentTurnInstruction(): TurnInstruction? {
        val location = _uiState.value.currentLocation ?: return null
        val route = _uiState.value.currentRoute ?: return null
        return turnProvider.getCurrentInstruction(location, route, _uiState.value.currentStepIndex)
    }

    fun getNextTurnInstruction(): TurnInstruction? {
        val route = _uiState.value.currentRoute ?: return null
        return turnProvider.getNextInstruction(route, _uiState.value.currentStepIndex)
    }
}
