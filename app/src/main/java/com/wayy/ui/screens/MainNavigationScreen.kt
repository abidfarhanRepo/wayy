package com.wayy.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.random.Random
import com.wayy.map.MapLibreManager
import com.wayy.map.MapViewAutoLifecycle
import com.wayy.map.MapStyleManager
import com.wayy.map.WazeStyleManager
import com.wayy.ui.components.navigation.ETACard
import com.wayy.ui.components.common.GForceCard
import com.wayy.ui.components.common.QuickActionsBar
import com.wayy.ui.components.common.RoadQualityCard
import com.wayy.ui.components.common.StatCard
import com.wayy.ui.components.common.TopBar
import com.wayy.ui.components.gauges.Speedometer
import com.wayy.ui.components.navigation.Direction
import com.wayy.ui.components.navigation.NavigationOverlay
import com.wayy.ui.theme.WayyColors
import com.wayy.viewmodel.NavigationViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

/**
 * Main navigation screen with map, speedometer, and controls
 */
@Composable
fun MainNavigationScreen(
    viewModel: NavigationViewModel = viewModel(),
    onMenuClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val speed by viewModel.speed.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val mapManager = remember { MapLibreManager(context) }
    val mapStyleManager = remember { MapStyleManager() }
    val wazeStyleManager = remember { WazeStyleManager() }

    // Snackbar for error messages
    val snackbarHostState = remember { SnackbarHostState() }

    // Show error snackbar when error occurs
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Simulate location and stats updates (for demo)
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            // Demo: Simulate speed, bearing, and location changes
            val simulatedSpeed = (30..70).random().toFloat()
            val simulatedBearing = (0..360).random().toFloat()  // Direction in degrees
            // Slight position variation for demo (Doha, Qatar area)
            val baseLat = 25.2854
            val baseLng = 51.5310
            val offset = (0..100).random().toDouble() / 100000.0
            viewModel.updateLocation(
                org.maplibre.geojson.Point.fromLngLat(baseLng + offset, baseLat + offset),
                simulatedSpeed,
                simulatedBearing
            )
            viewModel.updateSimulatedStats(
                roadQuality = (80..98).random().toFloat(),
                gForce = Random.nextDouble(0.5, 2.5).toFloat()
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WayyColors.BgPrimary)
    ) {
        // Map view
        MapViewAutoLifecycle(
            manager = mapManager,
            modifier = Modifier.fillMaxSize(),
            onMapReady = { map ->
                // FIRST: Apply dark style with CartoDB tiles
                mapStyleManager.applyDarkStyle(map) {
                    // Style loaded successfully - now configure map

                    // Center map on default location (Doha, Qatar)
                    map.cameraPosition = org.maplibre.android.camera.CameraPosition.Builder()
                        .target(LatLng(25.2854, 51.5310))  // Doha, Qatar
                        .zoom(13.0)
                        .bearing(0.0)  // Start with North orientation
                        .build()

                    // Add Waze-style user location marker
                    val locationSource = org.maplibre.android.style.sources.GeoJsonSource(
                        "location-source",
                        org.maplibre.geojson.Feature.fromGeometry(
                            org.maplibre.geojson.Point.fromLngLat(51.5310, 25.2854)  // Doha, Qatar
                        )
                    )
                    map.style?.addSource(locationSource)
                    wazeStyleManager.addUserLocationMarker(map, "location-source")
                }
            }
        )

        // Apply Waze-style route when navigation starts
        LaunchedEffect(uiState.isNavigating, uiState.currentRoute) {
            if (uiState.isNavigating && uiState.currentRoute != null) {
                // Get the map instance from mapManager
                mapManager.getMapLibreMap()?.let { map ->
                    // Clear existing route
                    wazeStyleManager.clearWazeRoute(map)

                    // Create route source from geometry
                    val route = uiState.currentRoute!!
                    val coordinates = route.geometry.map { point ->
                        org.maplibre.geojson.Point.fromLngLat(
                            point.longitude(),
                            point.latitude()
                        )
                    }
                    val lineString = org.maplibre.geojson.LineString.fromLngLats(coordinates)
                    val feature = org.maplibre.geojson.Feature.fromGeometry(lineString)
                    val source = org.maplibre.android.style.sources.GeoJsonSource(
                        "route-source",
                        feature
                    )

                    // Add source (remove if exists)
                    try {
                        map.style?.removeSource("route-source")
                    } catch (e: Exception) {
                        // Source may not exist yet
                    }
                    map.style?.addSource(source)

                    // Apply Waze-style route (glow + border + main line)
                    wazeStyleManager.addWazeRoute(map, "route-source")
                }
            }
        }

        // Grid overlay for cyberpunk effect
        AnimatedGridBackground(modifier = Modifier.fillMaxSize())

        // Top bar
        TopBar(
            onMenuClick = onMenuClick,
            onSettingsClick = onSettingsClick,
            isScanningActive = uiState.isScanning,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Navigation overlay (visible when navigating)
        AnimatedVisibility(
            visible = uiState.isNavigating,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = 0.8f,
                    stiffness = 400f
                )
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = 0.8f,
                    stiffness = 400f
                )
            ) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 80.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NavigationOverlay(
                    direction = uiState.nextDirection,
                    distance = uiState.distanceToTurn,
                    streetName = uiState.currentStreet,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                ETACard(
                    eta = uiState.eta,
                    remainingDistance = uiState.remainingDistance,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        // Speedometer (bottom left)
        Speedometer(
            speed = speed,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 100.dp)
        )

        // Stats panel (bottom right, above actions)
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 100.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RoadQualityCard(
                quality = "${uiState.roadQuality.toInt()}%"
            )
            GForceCard(
                gForce = String.format("%.1fg", uiState.gForce)
            )
        }

        // Quick actions bar (bottom center)
        QuickActionsBar(
            isNavigating = uiState.isNavigating,
            isScanning = uiState.isScanning,
            isARActive = uiState.isARMode,
            is3DActive = uiState.is3DView,
            onNavigateToggle = {
                if (uiState.isNavigating) {
                    viewModel.stopNavigation()
                } else {
                    // Demo: Start navigation to a fixed destination in Qatar
                    // From Doha to The Pearl (a famous area in Qatar)
                    viewModel.startNavigation(
                        org.maplibre.geojson.Point.fromLngLat(51.5450, 25.3774) // The Pearl, Qatar
                    )
                }
            },
            onScanToggle = { viewModel.toggleScanning() },
            onARModeToggle = { viewModel.toggleARMode() },
            on3DViewToggle = { viewModel.toggle3DView() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )

        // Snackbar host
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
    }
}

/**
 * Animated grid background for cyberpunk effect
 */
@Composable
fun AnimatedGridBackground(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        WayyColors.BgSecondary.copy(alpha = 0.5f),
                        WayyColors.BgPrimary.copy(alpha = 0.8f)
                    )
                )
            )
    ) {
        // TODO: Add animated grid lines
        // For now, just a subtle gradient background
    }
}

/**
 * Simple demo screen without map for testing UI
 */
@Composable
fun DemoNavigationScreen(
    viewModel: NavigationViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val speed by viewModel.speed.collectAsState()

    // Simulate updates
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            viewModel.updateLocation(
                org.maplibre.geojson.Point.fromLngLat(-122.4194, 37.7749),
                (30..70).random().toFloat()
            )
            viewModel.updateSimulatedStats(
                roadQuality = (80..98).random().toFloat(),
                gForce = Random.nextDouble(0.5, 2.5).toFloat()
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WayyColors.BgPrimary)
    ) {
        TopBar(
            onMenuClick = {},
            onSettingsClick = {},
            isScanningActive = uiState.isScanning
        )

        Speedometer(
            speed = speed,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        )

        QuickActionsBar(
            isNavigating = uiState.isNavigating,
            isScanning = uiState.isScanning,
            isARActive = uiState.isARMode,
            is3DActive = uiState.is3DView,
            onNavigateToggle = {
                if (uiState.isNavigating) {
                    viewModel.stopNavigation()
                } else {
                    viewModel.startNavigation(
                        org.maplibre.geojson.Point.fromLngLat(-122.4094, 37.7849)
                    )
                }
            },
            onScanToggle = { viewModel.toggleScanning() },
            onARModeToggle = { viewModel.toggleARMode() },
            on3DViewToggle = { viewModel.toggle3DView() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}
