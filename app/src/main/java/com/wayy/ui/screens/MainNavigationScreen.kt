package com.wayy.ui.screens

import android.Manifest
import android.os.Handler
import android.os.Looper
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import android.app.Activity
import android.content.pm.ActivityInfo
import android.util.Log
import androidx.compose.runtime.rememberUpdatedState
import com.wayy.data.settings.MapSettings
import com.wayy.data.settings.MapSettingsRepository
import com.wayy.data.sensor.LocationManager
import com.wayy.data.sensor.DeviceOrientationManager
import com.wayy.data.repository.LocalPoiItem
import com.wayy.ar.ARCapability
import com.wayy.ar.ARCapabilityChecker
import com.wayy.capture.CaptureEvent
import com.wayy.capture.CaptureSessionInfo
import com.wayy.capture.NavigationCaptureController
import com.wayy.debug.DiagnosticLogger
import com.wayy.map.MapLibreManager
import com.wayy.map.MapViewAutoLifecycle
import com.wayy.map.MapStyleManager
import com.wayy.map.WazeStyleManager
import com.wayy.map.OfflineMapManager
import com.wayy.navigation.NavigationUtils
import com.wayy.ui.components.camera.CameraPreviewCard
import com.wayy.ui.components.camera.CameraPreviewSurface
import com.wayy.ui.components.camera.ArVisionOverlay
import com.wayy.ui.components.camera.LaneConfig
import com.wayy.ui.components.camera.LaneGuidanceOverlay
import com.wayy.ui.components.camera.TurnArrowOverlay
import com.wayy.ui.components.common.QuickActionsBar
import com.wayy.ui.components.common.RoadQualityCard
import com.wayy.ui.components.common.GForceCard
import com.wayy.ui.components.common.TopBar
import com.wayy.ui.components.glass.GlassIconButton
import com.wayy.ui.components.gauges.SpeedometerSmall
import com.wayy.ui.components.navigation.TurnBanner
import com.wayy.ui.theme.WayyColors
import com.wayy.viewmodel.ARMode
import com.wayy.viewmodel.NavigationState
import com.wayy.viewmodel.NavigationViewModel
import com.wayy.ml.MlFrameAnalyzer
import com.wayy.ml.MlDetection
import com.wayy.ml.DetectionTracker
import com.wayy.ml.LaneSegmentationManager
import com.wayy.ml.LaneSegmentationResult
import com.wayy.ui.components.glass.GlassCardElevated
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.util.Locale

private const val MPH_TO_KMH = 1.60934f
private const val MPS_TO_KMH = 3.6f
private const val LANE_MODEL_PATH = "file:///data/data/com.wayy/files/models/lane_model.tflite"

/**
 * Minimal MVP navigation screen (map + GPS + search + route)
 */
@Composable
fun MainNavigationScreen(
    viewModel: NavigationViewModel = viewModel(),
    onMenuClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val mapManager = remember { MapLibreManager(context) }
    val mapStyleManager = remember { MapStyleManager(context) }
    val wazeStyleManager = remember { WazeStyleManager() }
    val locationManager = remember { LocationManager(context) }
    val orientationManager = remember { DeviceOrientationManager(context) }
    val arCapabilityChecker = remember { ARCapabilityChecker(context) }
    val captureController = remember { NavigationCaptureController(context) }
    val diagnosticLogger = remember { DiagnosticLogger(context) }
    val offlineMapManager = remember { OfflineMapManager(context, diagnosticLogger) }
    val mapSettingsRepository = remember { MapSettingsRepository(context) }
    val mapSettings by mapSettingsRepository.settingsFlow.collectAsState(initial = MapSettings())
    val mlManager = remember { viewModel.getMlManager() }
    val laneManager = remember { LaneSegmentationManager(context, diagnosticLogger) }
    val detectionTracker = remember { DetectionTracker() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var lastInferenceMs by remember { mutableStateOf<Double?>(null) }
    var lastDetections by remember { mutableStateOf(emptyList<MlDetection>()) }
    var laneResult by remember { mutableStateOf<LaneSegmentationResult?>(null) }
    val scanningState = rememberUpdatedState(uiState.isScanning)
    val mlAnalyzer = remember(mlManager, diagnosticLogger, mainHandler) {
        mlManager?.let { manager ->
            MlFrameAnalyzer(
                mlManager = manager,
                diagnosticLogger = diagnosticLogger,
                isEnabled = { scanningState.value },
                onInference = { result ->
                    mainHandler.post {
                        lastInferenceMs = result.inferenceMs
                        lastDetections = detectionTracker.update(result.detections)
                    }
                },
                laneManager = laneManager,
                onLaneResult = { result ->
                    mainHandler.post { laneResult = result }
                }
            )
        }
    }
    var videoCapture by remember { mutableStateOf<androidx.camera.video.VideoCapture<androidx.camera.video.Recorder>?>(null) }
    var offlineRequested by remember { mutableStateOf(false) }
    var captureStartMs by remember { mutableStateOf<Long?>(null) }
    var captureElapsedMs by remember { mutableStateOf(0L) }
    val localPois = viewModel.localPois?.collectAsState()?.value.orEmpty()
    val trafficReports = viewModel.trafficReports?.collectAsState()?.value.orEmpty()
    val trafficSegments = viewModel.trafficSegments.collectAsState().value
    val trafficSpeedMps by viewModel.trafficSpeedMps.collectAsState()
    val latestPois = rememberUpdatedState(localPois)
    val deviceBearing by orientationManager.currentBearing.collectAsState()
    val activity = context as? Activity
    val tilejsonOverride = mapSettings.tilejsonUrl
    val mapStyleOverride = mapSettings.mapStyleUrl

    fun resolveTrafficSeverity(speedMps: Double?): String {
        val speed = speedMps ?: return "moderate"
        return when {
            speed >= 12.0 -> "fast"
            speed >= 6.0 -> "moderate"
            else -> "slow"
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    var selectedPoi by remember { mutableStateOf<LocalPoiItem?>(null) }
    var showTrafficDialog by remember { mutableStateOf(false) }
    var showAddPoiDialog by remember { mutableStateOf(false) }
    var pendingPoiLocation by remember { mutableStateOf<Point?>(null) }
    var addPoiName by remember { mutableStateOf("") }
    var addPoiCategory by remember { mutableStateOf("general") }

    fun configureMapStyle(map: MapLibreMap) {
        mapStyleManager.applyDarkStyle(
            map,
            tilejsonUrlOverride = tilejsonOverride,
            mapStyleUrlOverride = mapStyleOverride
        ) {
            map.cameraPosition = CameraPosition.Builder()
                .target(LatLng(25.2854, 51.5310))
                .zoom(13.0)
                .bearing(0.0)
                .build()

            val locationSource = GeoJsonSource(
                MapStyleManager.LOCATION_SOURCE_ID,
                Feature.fromGeometry(Point.fromLngLat(51.5310, 25.2854))
            )
            map.style?.addSource(locationSource)
            wazeStyleManager.addUserLocationMarker(map, MapStyleManager.LOCATION_SOURCE_ID)

            val poiSource = GeoJsonSource(
                MapStyleManager.POI_SOURCE_ID,
                FeatureCollection.fromFeatures(emptyArray())
            )
            val trafficSource = GeoJsonSource(
                MapStyleManager.TRAFFIC_SOURCE_ID,
                FeatureCollection.fromFeatures(emptyArray())
            )
            val trafficIntensitySource = GeoJsonSource(
                MapStyleManager.TRAFFIC_INTENSITY_SOURCE_ID,
                FeatureCollection.fromFeatures(emptyArray())
            )
            map.style?.addSource(poiSource)
            map.style?.addSource(trafficSource)
            map.style?.addSource(trafficIntensitySource)
            mapStyleManager.addPoiLayer(map, MapStyleManager.POI_SOURCE_ID)
            mapStyleManager.addTrafficLayer(map, MapStyleManager.TRAFFIC_SOURCE_ID)
            mapStyleManager.addTrafficPulseLayer(map, MapStyleManager.TRAFFIC_SOURCE_ID)
            mapStyleManager.addTrafficIntensityLayer(
                map,
                MapStyleManager.TRAFFIC_INTENSITY_SOURCE_ID
            )

            uiState.currentLocation?.let { location ->
                mapManager.updateUserLocation(
                    LatLng(location.latitude(), location.longitude())
                )
                mapManager.centerOnUserLocation(
                    LatLng(location.latitude(), location.longitude())
                )
            }

            map.addOnMapClickListener { latLng ->
                val screenPoint = map.projection.toScreenLocation(latLng)
                val features = map.queryRenderedFeatures(
                    screenPoint,
                    MapStyleManager.POI_LAYER_ID
                )
                val poiId = features.firstOrNull()?.getStringProperty("id")
                val match = poiId?.let { id ->
                    latestPois.value.firstOrNull { poi -> poi.id == id }
                }
                selectedPoi = match
                match != null
            }

            map.addOnMapLongClickListener { latLng ->
                pendingPoiLocation = Point.fromLngLat(latLng.longitude, latLng.latitude)
                addPoiName = ""
                addPoiCategory = "general"
                showAddPoiDialog = true
                true
            }
        }
    }

    LaunchedEffect(tilejsonOverride, mapStyleOverride) {
        mapManager.getMapLibreMap()?.let { map ->
            configureMapStyle(map)
        }
    }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        diagnosticLogger.log(
            tag = "WayyCamera",
            message = if (granted) "Camera permission granted" else "Camera permission denied"
        )
    }

    LaunchedEffect(Unit) {
        val capability = arCapabilityChecker.check()
        viewModel.updateArCapability(capability)
        diagnosticLogger.log(
            tag = "WayyAR",
            message = "AR capability checked",
            data = mapOf("capability" to capability.capability.name, "message" to capability.message)
        )
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            val lastLocation = locationManager.getLastKnownLocation()
            val location = lastLocation ?: locationManager.getCurrentLocation()
            if (location == null) {
                snackbarHostState.showSnackbar(
                    "Unable to get location. Please ensure GPS is enabled."
                )
            } else {
                Log.d(
                    "WayyLocation",
                    "Initial location set lat=${location.latitude()}, lon=${location.longitude()}"
                )
                viewModel.updateLocation(location)
                mapManager.updateUserLocation(
                    LatLng(location.latitude(), location.longitude())
                )
                mapManager.centerOnUserLocation(
                    LatLng(location.latitude(), location.longitude())
                )
                if (!offlineRequested) {
                    offlineMapManager.ensureRegion(
                        center = LatLng(location.latitude(), location.longitude()),
                        radiusKm = 12.0,
                        tilejsonUrlOverride = tilejsonOverride,
                        mapStyleUrlOverride = mapStyleOverride
                    )
                    offlineRequested = true
                }
            }
        }
    }

    LaunchedEffect(uiState.arMode, hasCameraPermission) {
        if (uiState.arMode != ARMode.DISABLED && !hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(uiState.arMode) {
        activity?.requestedOrientation = if (uiState.arMode == ARMode.FULL_AR) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    LaunchedEffect(uiState.isScanning) {
        if (uiState.isScanning) {
            laneManager.start(LANE_MODEL_PATH)
        } else {
            laneManager.stop()
            laneResult = null
        }
    }

    LaunchedEffect(deviceBearing) {
        viewModel.updateDeviceBearing(deviceBearing)
    }

    DisposableEffect(uiState.isAROverlayActive, uiState.isNavigating) {
        if (uiState.isAROverlayActive && uiState.isNavigating) {
            orientationManager.start()
        } else {
            orientationManager.stop()
        }
        onDispose {
            orientationManager.stop()
        }
    }

    if (hasLocationPermission) {
        DisposableEffect(locationManager) {
            val job = coroutineScope.launch {
                locationManager.startLocationUpdates().collect { update ->
                    Log.d(
                        "WayyLocation",
                        "Live update lat=${update.location.latitude()}, lon=${update.location.longitude()}"
                    )
                    viewModel.updateLocation(
                        update.location,
                        update.speed,
                        update.bearing,
                        update.accuracy
                    )
                    captureController.logEvent(
                        CaptureEvent(
                            type = "location",
                            timestamp = System.currentTimeMillis(),
                            payload = mapOf(
                                "lat" to update.location.latitude(),
                                "lng" to update.location.longitude(),
                                "speedMph" to update.speed,
                                "bearing" to update.bearing,
                                "accuracy" to update.accuracy,
                                "arMode" to uiState.arMode.name
                            )
                        )
                    )
                    mapManager.updateUserLocation(
                        LatLng(update.location.latitude(), update.location.longitude()),
                        update.bearing
                    )

                    val currentState = viewModel.uiState.value
                    if (currentState.isCameraFollowing || currentState.isNavigating) {
                        mapManager.animateToLocationWithBearing(
                            LatLng(update.location.latitude(), update.location.longitude()),
                            update.bearing
                        )
                    }
                }
            }
            onDispose { job.cancel() }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            captureController.stop(
                CaptureSessionInfo(
                    timestamp = System.currentTimeMillis(),
                    data = mapOf("reason" to "composable_disposed")
                )
            )
        }
    }

    val shouldCapture = uiState.arMode != ARMode.DISABLED && uiState.isCaptureEnabled && hasCameraPermission
    LaunchedEffect(shouldCapture, videoCapture, uiState.currentRoute, uiState.arMode) {
        val capture = videoCapture
        if (shouldCapture && capture != null) {
            if (captureStartMs == null) {
                captureStartMs = System.currentTimeMillis()
            }
            captureController.startIfNeeded(
                capture,
                CaptureSessionInfo(
                    timestamp = System.currentTimeMillis(),
                    data = mapOf(
                        "arMode" to uiState.arMode.name,
                        "routeDistance" to uiState.currentRoute?.distance,
                        "routeDuration" to uiState.currentRoute?.duration
                    )
                )
            )
            diagnosticLogger.log(tag = "WayyCapture", message = "Capture started")
        } else if (!shouldCapture) {
            captureController.stop(
                CaptureSessionInfo(
                    timestamp = System.currentTimeMillis(),
                    data = mapOf("reason" to "capture_disabled_or_stopped")
                )
            )
            diagnosticLogger.log(tag = "WayyCapture", message = "Capture stopped")
            captureStartMs = null
        } else if (capture == null) {
            diagnosticLogger.log(
                tag = "WayyCapture",
                message = "Capture pending; video pipeline not ready",
                level = "WARN"
            )
        }
    }

    LaunchedEffect(captureStartMs) {
        val start = captureStartMs
        if (start == null) {
            captureElapsedMs = 0L
            return@LaunchedEffect
        }
        while (true) {
            captureElapsedMs = System.currentTimeMillis() - start
            delay(1000)
        }
    }

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WayyColors.BgPrimary)
    ) {
        if (uiState.arMode == ARMode.FULL_AR) {
            if (hasCameraPermission) {
                androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                    CameraPreviewSurface(
                        modifier = Modifier.fillMaxSize(),
                        onVideoCaptureReady = { capture ->
                            videoCapture = capture
                            captureController.attachVideoCapture(capture)
                        },
                        frameAnalyzer = mlAnalyzer,
                        onError = { error ->
                            diagnosticLogger.log(tag = "WayyAR", message = "Camera error", level = "WARN", data = mapOf("error" to error))
                        }
                    )
                    ArVisionOverlay(
                        detections = lastDetections,
                        showLanes = true,
                        showBoxes = uiState.isScanning,
                        leftLane = laneResult?.leftLane.orEmpty(),
                        rightLane = laneResult?.rightLane.orEmpty(),
                        deviceBearing = uiState.deviceBearing,
                        turnBearing = uiState.turnBearing,
                        isApproaching = uiState.isApproachingTurn,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(WayyColors.BgPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Camera permission required",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            }
        } else {
            MapViewAutoLifecycle(
                manager = mapManager,
                modifier = Modifier.fillMaxSize(),
                onMapReady = { map ->
                    configureMapStyle(map)
                }
            )
        }

        LaunchedEffect(localPois) {
            mapManager.getMapLibreMap()?.style?.getSourceAs<GeoJsonSource>(
                MapStyleManager.POI_SOURCE_ID
            )?.let { source ->
                val features = localPois.map { poi ->
                    Feature.fromGeometry(Point.fromLngLat(poi.lng, poi.lat)).apply {
                        addStringProperty("id", poi.id)
                        addStringProperty("name", poi.name)
                        addStringProperty("category", poi.category.lowercase())
                    }
                }
                source.setGeoJson(FeatureCollection.fromFeatures(features.toTypedArray()))
            }
        }

        LaunchedEffect(trafficReports) {
            mapManager.getMapLibreMap()?.style?.getSourceAs<GeoJsonSource>(
                MapStyleManager.TRAFFIC_SOURCE_ID
            )?.let { source ->
                val features = trafficReports.map { report ->
                    Feature.fromGeometry(Point.fromLngLat(report.lng, report.lat)).apply {
                        val severity = when (report.severity.lowercase()) {
                            "heavy", "slow", "stopped" -> "heavy"
                            "light", "clear" -> "light"
                            "moderate" -> "moderate"
                            else -> "moderate"
                        }
                        addStringProperty("severity", severity)
                    }
                }
                source.setGeoJson(FeatureCollection.fromFeatures(features.toTypedArray()))
            }
        }

        LaunchedEffect(Unit) {
            var expanded = false
            while (true) {
                val radius = if (expanded) 16f else 10f
                val opacity = if (expanded) 0.25f else 0.5f
                mapManager.getMapLibreMap()?.style?.getLayerAs<CircleLayer>(
                    MapStyleManager.TRAFFIC_PULSE_LAYER_ID
                )?.setProperties(
                    PropertyFactory.circleRadius(radius),
                    PropertyFactory.circleOpacity(opacity)
                )
                expanded = !expanded
                delay(800)
            }
        }

        LaunchedEffect(trafficSegments) {
            mapManager.getMapLibreMap()?.style?.getSourceAs<GeoJsonSource>(
                MapStyleManager.TRAFFIC_INTENSITY_SOURCE_ID
            )?.let { source ->
                val features = trafficSegments.map { segment ->
                    val line = LineString.fromLngLats(
                        listOf<Point>(
                            Point.fromLngLat(segment.startLng, segment.startLat),
                            Point.fromLngLat(segment.endLng, segment.endLat)
                        )
                    )
                    Feature.fromGeometry(line).apply {
                        addStringProperty("severity", segment.severity)
                    }
                }
                source.setGeoJson(FeatureCollection.fromFeatures(features.toTypedArray()))
            }
        }

        LaunchedEffect(uiState.isNavigating, uiState.currentRoute) {
            mapManager.getMapLibreMap()?.let { map ->
                val style = map.style ?: return@let

                runCatching { style.removeLayer(MapStyleManager.ROUTE_LAYER_ID) }
                runCatching { style.removeLayer(MapStyleManager.ROUTE_TRAFFIC_LAYER_ID) }
                runCatching { style.removeSource(MapStyleManager.ROUTE_SOURCE_ID) }

                val route = uiState.currentRoute ?: return@let
                val coordinates = route.geometry.map { point: Point ->
                    Point.fromLngLat(point.longitude(), point.latitude())
                }
                val lineString = LineString.fromLngLats(coordinates)
                val feature = Feature.fromGeometry(lineString).apply {
                    addStringProperty("trafficSeverity", resolveTrafficSeverity(trafficSpeedMps))
                }
                val source = GeoJsonSource(MapStyleManager.ROUTE_SOURCE_ID, feature)
                style.addSource(source)
                mapStyleManager.addRouteLayer(map, MapStyleManager.ROUTE_SOURCE_ID)
                mapStyleManager.addRouteTrafficLayer(map, MapStyleManager.ROUTE_SOURCE_ID)

                val boundsBuilder = LatLngBounds.Builder()
                route.geometry.forEach { point: Point ->
                    boundsBuilder.include(LatLng(point.latitude(), point.longitude()))
                }
                mapManager.fitToBounds(boundsBuilder.build())
            }
        }

        LaunchedEffect(trafficSpeedMps, uiState.currentRoute) {
            val route = uiState.currentRoute ?: return@LaunchedEffect
            mapManager.getMapLibreMap()?.style?.getSourceAs<GeoJsonSource>(
                MapStyleManager.ROUTE_SOURCE_ID
            )?.let { source ->
                val coordinates = route.geometry.map { point: Point ->
                    Point.fromLngLat(point.longitude(), point.latitude())
                }
                val lineString = LineString.fromLngLats(coordinates)
                val feature = Feature.fromGeometry(lineString).apply {
                    addStringProperty("trafficSeverity", resolveTrafficSeverity(trafficSpeedMps))
                }
                source.setGeoJson(feature)
            }
        }

        if (!uiState.isNavigating && uiState.arMode == ARMode.DISABLED) {
            TopBar(
                onMenuClick = onMenuClick,
                onSettingsClick = onSettingsClick,
                isScanningActive = uiState.isScanning,
                gpsAccuracyMeters = uiState.currentAccuracyMeters,
                showSettings = false,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        val laneConfigs = remember(uiState.nextDirection) {
            buildLaneConfigs(uiState.nextDirection)
        }

        AnimatedVisibility(
            visible = uiState.arMode == ARMode.PIP_OVERLAY,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 120.dp),
            enter = slideInVertically(initialOffsetY = { it }) + scaleIn() + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + scaleOut() + fadeOut()
        ) {
            CameraPreviewCard(
                direction = uiState.nextDirection,
                distanceToTurnMeters = uiState.distanceToTurnMeters,
                deviceBearing = uiState.deviceBearing,
                turnBearing = uiState.turnBearing,
                isApproaching = uiState.isApproachingTurn,
                lanes = laneConfigs,
                showGuidance = uiState.isNavigating,
                detections = lastDetections,
                showBoxes = uiState.isScanning,
                showLanes = true,
                leftLane = laneResult?.leftLane.orEmpty(),
                rightLane = laneResult?.rightLane.orEmpty(),
                hasCameraPermission = hasCameraPermission,
                frameAnalyzer = mlAnalyzer,
                onVideoCaptureReady = { capture ->
                    videoCapture = capture
                    captureController.attachVideoCapture(capture)
                }
            )
        }

        if (uiState.arMode == ARMode.FULL_AR && uiState.isNavigating) {
            TurnArrowOverlay(
                direction = uiState.nextDirection,
                distanceToTurnMeters = uiState.distanceToTurnMeters,
                deviceBearing = uiState.deviceBearing,
                turnBearing = uiState.turnBearing,
                isApproaching = uiState.isApproachingTurn,
                modifier = Modifier.align(Alignment.Center)
            )
            LaneGuidanceOverlay(
                lanes = laneConfigs,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
            )
        }

        if (uiState.arMode != ARMode.DISABLED) {
            val trafficSeverity = trafficSpeedMps?.let { resolveTrafficSeverity(it) }
            ArStatusHud(
                isScanning = uiState.isScanning,
                arMode = uiState.arMode,
                trafficSeverity = trafficSeverity,
                lastInferenceMs = lastInferenceMs,
                objectCount = lastDetections.size,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 12.dp, end = 16.dp)
            )
        }

        if (uiState.arMode != ARMode.DISABLED) {
            val roadQualityText = if (uiState.roadQuality > 0f) {
                "${(uiState.roadQuality * 100).toInt()}%"
            } else {
                "--"
            }
            val gForceText = if (uiState.gForce > 0f) {
                String.format(Locale.US, "%.2fg", uiState.gForce)
            } else {
                "--"
            }
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(top = 56.dp, start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RoadQualityCard(quality = roadQualityText)
                GForceCard(gForce = gForceText)
            }
        }

        if (captureStartMs != null) {
            CaptureTimerHud(
                elapsedMs = captureElapsedMs,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(top = 12.dp, start = 16.dp)
            )
        }

        selectedPoi?.let { poi ->
            val distanceText = uiState.currentLocation?.let { location ->
                NavigationUtils.formatDistance(
                    NavigationUtils.calculateDistanceMeters(
                        location,
                        Point.fromLngLat(poi.lng, poi.lat)
                    )
                )
            } ?: "n/a"
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 90.dp),
                colors = CardDefaults.cardColors(containerColor = WayyColors.BgSecondary)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = poiCategoryIcon(poi.category),
                                contentDescription = null,
                                tint = poiCategoryColor(poi.category)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = poi.name, color = Color.White)
                        }
                        IconButton(onClick = { selectedPoi = null }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = poiCategoryLabel(poi.category),
                        color = WayyColors.TextSecondary
                    )
                    Text(
                        text = "Distance: $distanceText",
                        color = WayyColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            viewModel.startNavigation(
                                Point.fromLngLat(poi.lng, poi.lat),
                                poi.name
                            )
                            selectedPoi = null
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Navigate")
                    }
                }
            }
        }

        if (showTrafficDialog) {
            AlertDialog(
                onDismissRequest = { showTrafficDialog = false },
                title = { Text(text = "Report traffic", color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = "Select severity", color = WayyColors.TextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("light", "moderate", "heavy").forEach { level ->
                                Button(
                                    onClick = {
                                        viewModel.reportTraffic(level)
                                        showTrafficDialog = false
                                    }
                                ) {
                                    Text(level.replaceFirstChar { it.titlecase() })
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                containerColor = WayyColors.BgSecondary,
                titleContentColor = Color.White,
                textContentColor = WayyColors.TextSecondary
            )
        }

        if (showAddPoiDialog && pendingPoiLocation != null) {
            val categories = listOf("gas", "food", "parking", "lodging", "general")
            AlertDialog(
                onDismissRequest = {
                    showAddPoiDialog = false
                    pendingPoiLocation = null
                },
                title = { Text(text = "Add POI here", color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = addPoiName,
                            onValueChange = { addPoiName = it },
                            label = { Text("Name") },
                            singleLine = true
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            categories.forEach { category ->
                                Button(
                                    onClick = { addPoiCategory = category },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (addPoiCategory == category) {
                                            WayyColors.PrimaryLime
                                        } else {
                                            WayyColors.BgTertiary
                                        }
                                    )
                                ) {
                                    Text(category.replaceFirstChar { it.titlecase() })
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            pendingPoiLocation?.let { location ->
                                viewModel.addLocalPoiAt(
                                    addPoiName.trim(),
                                    addPoiCategory,
                                    location
                                )
                            }
                            addPoiName = ""
                            addPoiCategory = "general"
                            showAddPoiDialog = false
                            pendingPoiLocation = null
                        },
                        enabled = addPoiName.isNotBlank()
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    Button(
                        onClick = {
                            showAddPoiDialog = false
                            pendingPoiLocation = null
                        }
                    ) {
                        Text("Cancel")
                    }
                },
                containerColor = WayyColors.BgSecondary,
                titleContentColor = Color.White,
                textContentColor = WayyColors.TextSecondary
            )
        }

        AnimatedVisibility(
            visible = uiState.isNavigating,
            enter = slideInVertically(
                initialOffsetY = { -it / 2 }
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { -it / 2 }
            ) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 6.dp)
        ) {
            val currentStreet = uiState.currentStreet.ifBlank { "Unknown" }
            val reportCount = trafficReports.count { report ->
                report.streetName?.equals(currentStreet, ignoreCase = true) == true
            }
            val speedKmh = uiState.currentSpeed * MPH_TO_KMH
            val speedText = String.format(Locale.US, "%.0f km/h", speedKmh)
            val trafficAvgKmhText = trafficSpeedMps?.let { speed ->
                String.format(Locale.US, "%.0f km/h", speed * MPS_TO_KMH)
            }
            val trafficSeverity = resolveTrafficSeverity(trafficSpeedMps)
                .replaceFirstChar { it.titlecase(Locale.US) }
            val trafficLabel = trafficAvgKmhText?.let {
                "Traffic $trafficSeverity $it"
            } ?: "Traffic $trafficSeverity"
            val metricsText = listOfNotNull(
                uiState.eta.takeIf { it.isNotBlank() }?.let { "ETA $it" },
                uiState.remainingDistance.takeIf { it.isNotBlank() }?.let { "Remain $it" },
                "Speed $speedText",
                trafficLabel,
                "Reports $reportCount"
            ).joinToString(" • ")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
            ) {
                if (uiState.navigationState is NavigationState.Arrived) {
                    TurnBanner(
                        direction = uiState.nextDirection,
                        distanceText = "",
                        streetName = "You have arrived",
                        instruction = "",
                        metricsText = metricsText,
                        isApproaching = false,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                } else {
                    TurnBanner(
                        direction = uiState.nextDirection,
                        distanceText = uiState.distanceToTurn,
                        streetName = currentStreet,
                        instruction = uiState.currentInstruction,
                        metricsText = metricsText,
                        isApproaching = uiState.isApproachingTurn,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 60.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = WayyColors.Error,
                contentColor = Color.White
            )
        }

        SpeedometerSmall(
            speed = uiState.currentSpeed * MPH_TO_KMH,
            unit = "km/h",
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 16.dp, bottom = 88.dp)
        )

        QuickActionsBar(
            isNavigating = uiState.isNavigating,
            isARActive = uiState.arMode != ARMode.DISABLED,
            is3DActive = uiState.is3DView,
            onMenuClick = onMenuClick,
            onNavigateToggle = { viewModel.toggleNavigation() },
            onARModeToggle = { viewModel.toggleARMode() },
            on3DViewToggle = { viewModel.toggle3DView() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        )

        GlassIconButton(
            onClick = { showTrafficDialog = true },
            icon = Icons.Default.ReportProblem,
            contentDescription = "Report traffic",
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 16.dp)
                .size(48.dp)
        )
    }
}

@Composable
private fun TrafficHistoryChart(bars: List<Double>) {
    val max = bars.maxOrNull()?.takeIf { it > 0 } ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        bars.forEach { value ->
            val heightRatio = (value / max).coerceIn(0.05, 1.0)
            Spacer(
                modifier = Modifier
                    .width(8.dp)
                    .height((48 * heightRatio).dp)
                    .background(
                        color = WayyColors.PrimaryCyan.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(4.dp)
                )
            )
        }

    }
}

private fun buildTrafficHistoryBars(
    history: List<NavigationViewModel.TrafficHistoryPoint>
): List<Double> {
    if (history.isEmpty()) return emptyList()
    val now = System.currentTimeMillis()
    val windowMs = 24 * 60 * 60 * 1000L
    val bucketSizeMs = 4 * 60 * 60 * 1000L
    val startMs = now - windowMs
    val buckets = (0 until 6).map { index ->
        val bucketStart = startMs + index * bucketSizeMs
        val bucketEnd = bucketStart + bucketSizeMs
        val speeds = history.filter { point ->
            point.bucketStartMs in bucketStart until bucketEnd
        }.map { it.averageSpeedMps }
        if (speeds.isNotEmpty()) speeds.average() else 0.0
    }
    return buckets
}

@Composable
private fun CaptureTimerHud(
    elapsedMs: Long,
    modifier: Modifier = Modifier
) {
    val totalSeconds = (elapsedMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val label = String.format(Locale.US, "REC %02d:%02d", minutes, seconds)
    GlassCardElevated(modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(
                modifier = Modifier
                    .size(8.dp)
                    .background(WayyColors.Error, RoundedCornerShape(4.dp))
            )
            Text(
                text = label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ArStatusHud(
    isScanning: Boolean,
    arMode: ARMode,
    trafficSeverity: String?,
    lastInferenceMs: Double?,
    objectCount: Int,
    modifier: Modifier = Modifier
) {
    val arLabel = when (arMode) {
        ARMode.FULL_AR -> "AR: Full"
        ARMode.PIP_OVERLAY -> "AR: PiP"
        ARMode.DISABLED -> "AR: Off"
    }
    val mlLabel = if (isScanning) "ML: On" else "ML: Off"
    val inferenceLabel = when {
        !isScanning -> "Inference: --"
        lastInferenceMs != null -> "Inference ${lastInferenceMs.toInt()}ms"
        else -> "Inference --"
    }
    val objectLabel = if (isScanning) "Objects ${objectCount}" else "Objects --"
    val trafficLabel = trafficSeverity?.let {
        "Traffic: ${it.replaceFirstChar { c -> c.titlecase() }}"
    } ?: "Traffic: --"
    val transition = rememberInfiniteTransition(label = "mlPulse")
    val pulse by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mlPulseAlpha"
    )
    val pulseAlpha = if (isScanning) pulse else 0.4f
    GlassCardElevated(modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(text = arLabel, color = Color.White, fontSize = 12.sp)
            Text(text = mlLabel, color = WayyColors.TextSecondary, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            color = WayyColors.PrimaryLime.copy(alpha = pulseAlpha),
                            shape = RoundedCornerShape(3.dp)
                        )
                )
                Text(text = inferenceLabel, color = WayyColors.TextSecondary, fontSize = 11.sp)
            }
            Text(text = objectLabel, color = WayyColors.TextSecondary, fontSize = 11.sp)
            Text(text = trafficLabel, color = WayyColors.TextSecondary, fontSize = 11.sp)
        }
    }
}

private fun buildLaneConfigs(direction: com.wayy.ui.components.navigation.Direction): List<LaneConfig> {
    return when (direction) {
        com.wayy.ui.components.navigation.Direction.LEFT ->
            listOf(
                LaneConfig("left", true),
                LaneConfig("center", false),
                LaneConfig("right", false)
            )
        com.wayy.ui.components.navigation.Direction.RIGHT ->
            listOf(
                LaneConfig("left", false),
                LaneConfig("center", false),
                LaneConfig("right", true)
            )
        com.wayy.ui.components.navigation.Direction.SLIGHT_LEFT ->
            listOf(
                LaneConfig("left", true),
                LaneConfig("center", true),
                LaneConfig("right", false)
            )
        com.wayy.ui.components.navigation.Direction.SLIGHT_RIGHT ->
            listOf(
                LaneConfig("left", false),
                LaneConfig("center", true),
                LaneConfig("right", true)
            )
        else ->
            listOf(
                LaneConfig("left", false),
                LaneConfig("center", true),
                LaneConfig("right", false)
            )
    }
}

private fun poiCategoryLabel(category: String): String {
    return when (category.trim().lowercase()) {
        "gas" -> "Gas"
        "food" -> "Food"
        "parking" -> "Parking"
        "lodging" -> "Lodging"
        else -> "General"
    }
}

private fun poiCategoryIcon(category: String) = when (category.trim().lowercase()) {
    "gas" -> Icons.Default.LocalGasStation
    "food" -> Icons.Default.Restaurant
    "parking" -> Icons.Default.LocalParking
    "lodging" -> Icons.Default.Hotel
    else -> Icons.Default.Place
}

private fun poiCategoryColor(category: String) = when (category.trim().lowercase()) {
    "gas" -> WayyColors.PrimaryOrange
    "food" -> WayyColors.PrimaryLime
    "parking" -> WayyColors.PrimaryCyan
    "lodging" -> WayyColors.PrimaryPurple
    else -> WayyColors.Info
}
