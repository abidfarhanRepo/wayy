package com.wayy.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wayy.data.local.TripLoggingManager
import com.wayy.data.repository.LocalPoiItem
import com.wayy.data.repository.LocalPoiManager
import com.wayy.data.repository.TrafficReportItem
import com.wayy.data.repository.TrafficReportManager
import com.wayy.data.repository.RouteHistoryItem
import com.wayy.data.repository.RouteHistoryManager
import com.wayy.data.model.Route
import com.wayy.data.repository.PlaceResult
import com.wayy.data.repository.RouteRepository
import com.wayy.ml.OnDeviceMlManager
import com.wayy.navigation.NavigationUtils
import com.wayy.navigation.RerouteResult
import com.wayy.navigation.RerouteUtils
import com.wayy.navigation.TurnInstruction
import com.wayy.navigation.TurnInstructionProvider
import com.wayy.ui.components.navigation.Direction
import com.wayy.ar.ARCapability
import com.wayy.ar.ARCapabilityStatus
import com.wayy.debug.DiagnosticLogger
import kotlinx.coroutines.Dispatchers
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

enum class ActivationReason {
    APPROACHING_EXIT,
    APPROACHING_JUNCTION,
    APPROACHING_TURN,
    USER_MANUAL,
    APPROACHING_FORK
}

enum class ARMode {
    DISABLED,
    PIP_OVERLAY,
    FULL_AR
}

data class NavigationUiState(
    val navigationState: NavigationState = NavigationState.Idle,
    val isNavigating: Boolean = false,
    val isScanning: Boolean = false,
    val isARMode: Boolean = false,
    val arMode: ARMode = ARMode.DISABLED,
    val is3DView: Boolean = false,
    val isCameraFollowing: Boolean = true,
    val isAROverlayActive: Boolean = false,
    val deviceBearing: Float = 0f,
    val arActivationReason: ActivationReason? = null,
    val arCapability: ARCapability = ARCapability.CAMERA_FALLBACK,
    val arFallbackReason: String? = null,
    val turnBearing: Float = 0f,
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
    val lastAnnouncedDistance: Double? = null,
    val isCaptureEnabled: Boolean = true
)

class NavigationViewModel(
    private val routeRepository: RouteRepository = RouteRepository(),
    private val turnProvider: TurnInstructionProvider = TurnInstructionProvider(),
    private val rerouteUtils: RerouteUtils = RerouteUtils(),
    private val routeHistoryManager: RouteHistoryManager? = null,
    private val localPoiManager: LocalPoiManager? = null,
    private val trafficReportManager: TrafficReportManager? = null,
    private val tripLoggingManager: TripLoggingManager? = null,
    private val diagnosticLogger: DiagnosticLogger? = null,
    private val mlManager: OnDeviceMlManager? = null
) : ViewModel() {

    var isDemoMode: Boolean = false

    private var activeTripId: String? = null
    private var streetSegmentTracker: StreetSegmentTracker? = null
    private var lastTrafficLookupKey: String? = null
    private var lastTrafficSegmentsRefreshTime: Long = 0L
    private var lastTrafficHistoryKey: String? = null
    private var lastTrafficHistoryRefreshTime: Long = 0L
    private var lastLocationLogTime: Long = 0L

    private val trafficRefreshIntervalMs = 10_000L
    private val trafficHistoryRefreshIntervalMs = 60_000L

    private val _uiState = MutableStateFlow(NavigationUiState())
    val uiState: StateFlow<NavigationUiState> = _uiState.asStateFlow()

    private val _speed = MutableStateFlow(0f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _bearing = MutableStateFlow(0f)
    val bearing: StateFlow<Float> = _bearing.asStateFlow()

    private val _trafficSpeedMps = MutableStateFlow<Double?>(null)
    val trafficSpeedMps: StateFlow<Double?> = _trafficSpeedMps.asStateFlow()

    private val _trafficSegments = MutableStateFlow<List<TrafficSegment>>(emptyList())
    val trafficSegments: StateFlow<List<TrafficSegment>> = _trafficSegments.asStateFlow()

    private val _trafficHistory = MutableStateFlow<List<TrafficHistoryPoint>>(emptyList())
    val trafficHistory: StateFlow<List<TrafficHistoryPoint>> = _trafficHistory.asStateFlow()

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

    private data class StreetSegmentTracker(
        val streetName: String,
        val startTime: Long,
        var lastTime: Long,
        val startPoint: Point,
        var lastPoint: Point,
        var distanceMeters: Double,
        var sampleCount: Int
    )

    data class TrafficSegment(
        val startLat: Double,
        val startLng: Double,
        val endLat: Double,
        val endLng: Double,
        val severity: String,
        val averageSpeedMps: Double
    )

    data class TrafficHistoryPoint(
        val bucketStartMs: Long,
        val averageSpeedMps: Double
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

            diagnosticLogger?.log(
                tag = "WayyNav",
                message = "Start navigation",
                data = mapOf("destination" to destinationName)
            )

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

    fun searchPlaces(query: String, location: Point? = null) {
        viewModelScope.launch {
            _isSearching.value = true
            _searchError.value = null

            val result = routeRepository.searchPlaces(query, location)
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
        addLocalPoiAt(name, category, location)
    }

    fun addLocalPoiAt(name: String, category: String, location: Point) {
        if (localPoiManager == null) {
            _uiState.value = _uiState.value.copy(
                error = "POI manager not available"
            )
            return
        }
        val normalizedCategory = normalizePoiCategory(category)
        viewModelScope.launch {
            val item = LocalPoiItem(
                id = localPoiManager.generatePoiId(location.latitude(), location.longitude()),
                name = name,
                category = normalizedCategory,
                lat = location.latitude(),
                lng = location.longitude(),
                timestamp = System.currentTimeMillis()
            )
            localPoiManager.addPoi(item)
        }
    }

    fun removeLocalPoi(poiId: String) {
        if (localPoiManager == null) {
            _uiState.value = _uiState.value.copy(
                error = "POI manager not available"
            )
            return
        }
        viewModelScope.launch {
            localPoiManager.removePoi(poiId)
        }
    }

    fun reportTraffic(severity: String) {
        val location = _uiState.value.currentLocation
        if (trafficReportManager == null || location == null) {
            _uiState.value = _uiState.value.copy(
                error = "Location not available for traffic report"
            )
            return
        }
        val speedMps = _uiState.value.currentSpeed / 2.23694f
        val normalizedSeverity = severity.trim().lowercase().ifBlank {
            when {
                speedMps < 2 -> "heavy"
                speedMps < 6 -> "moderate"
                else -> "light"
            }
        }
        val streetName = _uiState.value.currentStreet.ifBlank { "Unknown" }
        viewModelScope.launch {
            val report = TrafficReportItem(
                id = trafficReportManager.generateReportId(
                    location.latitude(),
                    location.longitude()
                ),
                lat = location.latitude(),
                lng = location.longitude(),
                speedMps = speedMps,
                severity = normalizedSeverity,
                timestamp = System.currentTimeMillis(),
                streetName = streetName
            )
            trafficReportManager.addReport(report)
        }
    }

    private fun normalizePoiCategory(category: String): String {
        val trimmed = category.trim()
        return if (trimmed.isBlank()) {
            "general"
        } else {
            trimmed.lowercase()
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

        if (startLocation != null) {
            startTripLogging(startLocation, destination, destinationName)
        }
    }

    fun stopNavigation() {
        finalizeTrip(_uiState.value.currentLocation)
        _destination.value = null
        rerouteUtils.resetState()
        diagnosticLogger?.log(tag = "WayyNav", message = "Stop navigation")

        _uiState.value = _uiState.value.copy(
            navigationState = NavigationState.Idle,
            isNavigating = false,
            isAROverlayActive = false,
            arActivationReason = null,
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
        setScanningEnabled(!_uiState.value.isScanning)
    }

    fun setScanningEnabled(enabled: Boolean) {
        val manager = mlManager
        if (manager == null) {
            _uiState.value = _uiState.value.copy(
                error = "ML manager not available"
            )
            return
        }
        if (enabled) {
            val availability = manager.start()
            if (!availability.isAvailable) {
                _uiState.value = _uiState.value.copy(
                    error = availability.message ?: "ML model not available"
                )
                return
            }
        } else {
            manager.stop()
        }
        _uiState.value = _uiState.value.copy(
            isScanning = enabled
        )
    }

    fun toggleARMode() {
        val nextMode = when (_uiState.value.arMode) {
            ARMode.DISABLED -> ARMode.PIP_OVERLAY
            ARMode.PIP_OVERLAY -> ARMode.FULL_AR
            ARMode.FULL_AR -> ARMode.DISABLED
        }
        setArMode(nextMode, ActivationReason.USER_MANUAL)
    }

    fun setArMode(mode: ARMode, reason: ActivationReason? = null) {
        val current = _uiState.value
        val capability = current.arCapability
        val canFullAr = capability == ARCapability.ARCORE || capability == ARCapability.HUAWEI_AR
        val resolvedMode = if (mode == ARMode.FULL_AR && !canFullAr) ARMode.PIP_OVERLAY else mode
        val fallbackReason = if (mode == ARMode.FULL_AR && !canFullAr) {
            "AR runtime unavailable"
        } else {
            null
        }
        val arActive = resolvedMode != ARMode.DISABLED
        _uiState.value = current.copy(
            arMode = resolvedMode,
            isARMode = arActive,
            isAROverlayActive = arActive,
            arActivationReason = if (reason != null && arActive) reason else current.arActivationReason,
            arFallbackReason = fallbackReason
        )
        diagnosticLogger?.log(
            tag = "WayyAR",
            message = "AR mode changed",
            data = mapOf("mode" to resolvedMode.name, "fallback" to fallbackReason)
        )
    }

    fun updateArCapability(status: ARCapabilityStatus) {
        val current = _uiState.value
        val canFullAr = status.capability == ARCapability.ARCORE || status.capability == ARCapability.HUAWEI_AR
        val resolvedMode = if (current.arMode == ARMode.FULL_AR && !canFullAr) {
            ARMode.PIP_OVERLAY
        } else {
            current.arMode
        }
        _uiState.value = current.copy(
            arCapability = status.capability,
            arMode = resolvedMode,
            isARMode = resolvedMode != ARMode.DISABLED,
            isAROverlayActive = resolvedMode != ARMode.DISABLED,
            arFallbackReason = if (resolvedMode != current.arMode) status.message else current.arFallbackReason
        )
        diagnosticLogger?.log(
            tag = "WayyAR",
            message = "AR capability updated",
            data = mapOf("capability" to status.capability.name, "message" to status.message)
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

    fun updateDeviceBearing(bearing: Float) {
        _uiState.value = _uiState.value.copy(deviceBearing = bearing)
    }

    fun updateLocation(
        location: Point,
        speed: Float = 0f,
        bearing: Float = 0f,
        accuracy: Float = 0f
    ) {
        _uiState.value = _uiState.value.copy(
            currentLocation = location,
            currentBearing = bearing,
            currentSpeed = speed
        )
        _speed.value = speed
        _bearing.value = bearing

        logLocationIfNeeded(location, speed, bearing, accuracy)

        if (_uiState.value.isNavigating) {
            updateNavigationProgress(location, speed)
            logTripSample(location, speed, bearing, accuracy)
        }
    }

    private fun updateNavigationProgress(location: Point, speed: Float) {
        val route = _uiState.value.currentRoute ?: return
        val destination = _destination.value ?: return

        if (rerouteUtils.isApproachingDestination(location, destination)) {
            handleArrival(location)
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
        val currentStep = route.legs.firstOrNull()?.steps?.getOrNull(newStepIndex)
        val maneuverType = currentStep?.maneuver?.type?.lowercase().orEmpty()
        val distanceToTurn = currentInstruction?.distanceMeters ?: Double.MAX_VALUE
        val activationReason = determineActivationReason(maneuverType, distanceToTurn)

        refreshTrafficStatsIfNeeded(currentInstruction?.streetName.orEmpty())
        refreshTrafficHistoryIfNeeded(currentInstruction?.streetName.orEmpty())
        refreshTrafficSegmentsIfNeeded()

        val (closestIndex, _) = NavigationUtils.findClosestPointOnRoute(location, route.geometry)
        val remainingMeters = NavigationUtils.calculateRemainingDistance(
            location,
            route.geometry,
            closestIndex
        )
        val etaSeconds = calculateEtaSeconds(
            route,
            remainingMeters,
            speed,
            _trafficSpeedMps.value
        )

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
            eta = NavigationUtils.formatDuration(etaSeconds),
            arActivationReason = activationReason,
            isAROverlayActive = _uiState.value.arMode != ARMode.DISABLED,
            turnBearing = currentStep?.maneuver?.bearingAfter?.toFloat() ?: 0f
        )
    }

    private fun calculateEtaSeconds(
        route: Route,
        remainingMeters: Double,
        speedMph: Float,
        trafficSpeedMps: Double?
    ): Double {
        val speedMps = speedMph / 2.23694f
        val routeAverageMps = if (route.duration > 0) {
            route.distance / route.duration
        } else {
            0.0
        }
        val trafficMps = trafficSpeedMps?.takeIf { it > 0 }
        val baselineMps = trafficMps ?: routeAverageMps
        val effectiveMps = when {
            speedMps >= 1f && baselineMps > 0 -> (speedMps + baselineMps) / 2.0
            speedMps >= 1f -> speedMps.toDouble()
            baselineMps > 0 -> baselineMps
            else -> 0.0
        }
        return if (effectiveMps > 0) {
            remainingMeters / effectiveMps
        } else {
            route.duration
        }
    }

    private fun refreshTrafficStatsIfNeeded(streetName: String) {
        val manager = tripLoggingManager ?: return
        val normalizedStreet = streetName.ifBlank { "Unknown" }
        val bucketStartMs = TripLoggingManager.bucketStart(System.currentTimeMillis())
        val key = "${normalizedStreet}_$bucketStartMs"
        if (key == lastTrafficLookupKey) return
        lastTrafficLookupKey = key
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _trafficSpeedMps.value = manager.getTrafficSpeedMps(
                    normalizedStreet,
                    bucketStartMs
                )
            } catch (e: Exception) {
                Log.e("WayyTrip", "Traffic stat lookup failed", e)
                _trafficSpeedMps.value = null
            }
        }
    }

    private fun refreshTrafficHistoryIfNeeded(streetName: String) {
        val manager = tripLoggingManager ?: return
        val normalizedStreet = streetName.ifBlank { "Unknown" }
        val now = System.currentTimeMillis()
        if (now - lastTrafficHistoryRefreshTime < trafficHistoryRefreshIntervalMs) return
        val bucketStartMs = TripLoggingManager.bucketStart(now)
        val key = "${normalizedStreet}_$bucketStartMs"
        if (key == lastTrafficHistoryKey) return
        lastTrafficHistoryKey = key
        lastTrafficHistoryRefreshTime = now
        val startMs = bucketStartMs - (24 * 60 * 60 * 1000L)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val history = manager.getTrafficHistory(normalizedStreet, startMs, bucketStartMs)
                _trafficHistory.value = history.map { item ->
                    TrafficHistoryPoint(item.bucketStartMs, item.averageSpeedMps)
                }
            } catch (e: Exception) {
                Log.e("WayyTrip", "Traffic history lookup failed", e)
                _trafficHistory.value = emptyList()
            }
        }
    }

    private fun refreshTrafficSegmentsIfNeeded() {
        val manager = tripLoggingManager ?: return
        val now = System.currentTimeMillis()
        if (now - lastTrafficSegmentsRefreshTime < trafficRefreshIntervalMs) return
        lastTrafficSegmentsRefreshTime = now
        val windowStartMs = now - (2 * 60 * 60 * 1000L)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val segments = manager.getRecentStreetSegments(windowStartMs, now, 200)
                _trafficSegments.value = segments.map { segment ->
                    TrafficSegment(
                        startLat = segment.startLat,
                        startLng = segment.startLng,
                        endLat = segment.endLat,
                        endLng = segment.endLng,
                        severity = getTrafficSeverity(segment.averageSpeedMps),
                        averageSpeedMps = segment.averageSpeedMps
                    )
                }
            } catch (e: Exception) {
                Log.e("WayyTrip", "Traffic segment lookup failed", e)
                _trafficSegments.value = emptyList()
            }
        }
    }

    private fun getTrafficSeverity(speedMps: Double): String {
        return when {
            speedMps >= 12.0 -> "fast"
            speedMps >= 6.0 -> "moderate"
            else -> "slow"
        }
    }

    private fun determineActivationReason(
        maneuverType: String,
        distanceToTurn: Double
    ): ActivationReason? {
        if (distanceToTurn.isNaN() || distanceToTurn.isInfinite()) return null
        return when {
            maneuverType == "off ramp" && distanceToTurn < 500 -> ActivationReason.APPROACHING_EXIT
            maneuverType == "fork" && distanceToTurn < 300 -> ActivationReason.APPROACHING_FORK
            maneuverType == "roundabout" && distanceToTurn < 200 -> ActivationReason.APPROACHING_JUNCTION
            maneuverType == "roundabout turn" && distanceToTurn < 200 -> ActivationReason.APPROACHING_JUNCTION
            maneuverType == "merge" && distanceToTurn < 300 -> ActivationReason.APPROACHING_JUNCTION
            distanceToTurn < 200 -> ActivationReason.APPROACHING_TURN
            else -> null
        }
    }

    private fun logLocationIfNeeded(
        location: Point,
        speed: Float,
        bearing: Float,
        accuracy: Float
    ) {
        val now = System.currentTimeMillis()
        if (now - lastLocationLogTime < 10_000L) return
        lastLocationLogTime = now
        diagnosticLogger?.log(
            tag = "WayyLocation",
            message = "Location update",
            data = mapOf(
                "lat" to location.latitude(),
                "lng" to location.longitude(),
                "speedMph" to speed,
                "bearing" to bearing,
                "accuracy" to accuracy,
                "arMode" to _uiState.value.arMode.name
            )
        )
    }

    private fun handleArrival(location: Point) {
        finalizeTrip(location)
        _uiState.value = _uiState.value.copy(
            navigationState = NavigationState.Arrived,
            isNavigating = false,
            isAROverlayActive = false,
            arActivationReason = null,
            nextDirection = Direction.STRAIGHT,
            distanceToTurn = "",
            currentStreet = "Destination",
            eta = "Arrived",
            remainingDistance = "0m",
            remainingDistanceMeters = 0.0
        )
    }

    private fun startTripLogging(
        startLocation: Point,
        destination: Point,
        destinationName: String?
    ) {
        val manager = tripLoggingManager ?: return
        val tripId = manager.generateTripId(
            startLat = startLocation.latitude(),
            startLng = startLocation.longitude(),
            endLat = destination.latitude(),
            endLng = destination.longitude()
        )
        activeTripId = tripId
        streetSegmentTracker = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                manager.startTrip(tripId, startLocation, destination, destinationName)
            } catch (e: Exception) {
                Log.e("WayyTrip", "Trip start failed", e)
                _uiState.value = _uiState.value.copy(
                    error = "Trip logging error: ${e.message ?: "unknown"}"
                )
            }
        }
    }

    private fun logTripSample(
        location: Point,
        speedMph: Float,
        bearing: Float,
        accuracy: Float
    ) {
        val tripId = activeTripId ?: return
        val manager = tripLoggingManager ?: return
        val timestamp = System.currentTimeMillis()
        val speedMps = speedMph / 2.23694f
        val streetName = _uiState.value.currentStreet.ifBlank { "Unknown" }
        val stepIndex = _uiState.value.currentStepIndex
        val remainingDistanceMeters = _uiState.value.remainingDistanceMeters

        viewModelScope.launch(Dispatchers.IO) {
            try {
                manager.logGpsSample(
                    tripId = tripId,
                    location = location,
                    timestamp = timestamp,
                    speedMps = speedMps,
                    bearing = bearing,
                    accuracy = accuracy,
                    streetName = streetName,
                    stepIndex = stepIndex,
                    remainingDistanceMeters = remainingDistanceMeters,
                    isNavigating = true
                )
            } catch (e: Exception) {
                Log.e("WayyTrip", "GPS sample logging failed", e)
                _uiState.value = _uiState.value.copy(
                    error = "Trip logging error: ${e.message ?: "unknown"}"
                )
            }
        }

        updateStreetSegment(tripId, location, streetName, timestamp)
    }

    private fun updateStreetSegment(
        tripId: String,
        location: Point,
        streetName: String,
        timestamp: Long
    ) {
        val normalizedStreet = streetName.ifBlank { "Unknown" }
        val tracker = streetSegmentTracker
        if (tracker == null || tracker.streetName != normalizedStreet) {
            tracker?.let { finalizeStreetSegment(tripId, it) }
            streetSegmentTracker = StreetSegmentTracker(
                streetName = normalizedStreet,
                startTime = timestamp,
                lastTime = timestamp,
                startPoint = location,
                lastPoint = location,
                distanceMeters = 0.0,
                sampleCount = 1
            )
            return
        }

        val distance = NavigationUtils.calculateDistanceMeters(tracker.lastPoint, location)
        tracker.distanceMeters += distance
        tracker.lastPoint = location
        tracker.lastTime = timestamp
        tracker.sampleCount += 1
    }

    private fun finalizeStreetSegment(tripId: String, tracker: StreetSegmentTracker) {
        val manager = tripLoggingManager ?: return
        val durationMs = (tracker.lastTime - tracker.startTime).coerceAtLeast(0)
        val averageSpeedMps = if (durationMs > 0) {
            tracker.distanceMeters / (durationMs / 1000.0)
        } else {
            0.0
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                manager.logStreetSegment(
                    tripId = tripId,
                    streetName = tracker.streetName,
                    startTime = tracker.startTime,
                    endTime = tracker.lastTime,
                    durationMs = durationMs,
                    distanceMeters = tracker.distanceMeters,
                    avgSpeedMps = averageSpeedMps,
                    sampleCount = tracker.sampleCount,
                    startPoint = tracker.startPoint,
                    endPoint = tracker.lastPoint
                )
            } catch (e: Exception) {
                Log.e("WayyTrip", "Street segment logging failed", e)
                _uiState.value = _uiState.value.copy(
                    error = "Trip logging error: ${e.message ?: "unknown"}"
                )
            }
        }
    }

    private fun finalizeTrip(endLocation: Point?) {
        val tripId = activeTripId ?: return
        val manager = tripLoggingManager
        streetSegmentTracker?.let { finalizeStreetSegment(tripId, it) }
        streetSegmentTracker = null
        activeTripId = null
        if (manager == null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                manager.endTrip(tripId, endLocation)
            } catch (e: Exception) {
                Log.e("WayyTrip", "Trip end failed", e)
                _uiState.value = _uiState.value.copy(
                    error = "Trip logging error: ${e.message ?: "unknown"}"
                )
            }
        }
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
