package com.wayy.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import android.util.Log
import com.wayy.data.sensor.LocationManager
import com.wayy.map.MapLibreManager
import com.wayy.map.MapViewAutoLifecycle
import com.wayy.map.MapStyleManager
import com.wayy.map.WazeStyleManager
import com.wayy.ui.components.common.TopBar
import com.wayy.ui.components.navigation.ETACard
import com.wayy.ui.components.navigation.TurnBanner
import com.wayy.ui.theme.WayyColors
import com.wayy.viewmodel.NavigationState
import com.wayy.viewmodel.NavigationViewModel
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

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
    val mapStyleManager = remember { MapStyleManager() }
    val wazeStyleManager = remember { WazeStyleManager() }
    val locationManager = remember { LocationManager(context) }

    val snackbarHostState = remember { SnackbarHostState() }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
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
            }
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
                    viewModel.updateLocation(update.location, update.speed, update.bearing)
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

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WayyColors.BgPrimary)
    ) {
        MapViewAutoLifecycle(
            manager = mapManager,
            modifier = Modifier.fillMaxSize(),
            onMapReady = { map ->
                mapStyleManager.applyDarkStyle(map) {
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

                    uiState.currentLocation?.let { location ->
                        mapManager.updateUserLocation(
                            LatLng(location.latitude(), location.longitude())
                        )
                        mapManager.centerOnUserLocation(
                            LatLng(location.latitude(), location.longitude())
                        )
                    }
                }
            }
        )

        LaunchedEffect(uiState.isNavigating, uiState.currentRoute) {
            mapManager.getMapLibreMap()?.let { map ->
                val style = map.style ?: return@let

                runCatching { style.removeLayer(MapStyleManager.ROUTE_LAYER_ID) }
                runCatching { style.removeSource(MapStyleManager.ROUTE_SOURCE_ID) }

                val route = uiState.currentRoute ?: return@let
                val coordinates = route.geometry.map { point ->
                    Point.fromLngLat(point.longitude(), point.latitude())
                }
                val lineString = LineString.fromLngLats(coordinates)
                val feature = Feature.fromGeometry(lineString)
                val source = GeoJsonSource(MapStyleManager.ROUTE_SOURCE_ID, feature)
                style.addSource(source)
                mapStyleManager.addRouteLayer(map, MapStyleManager.ROUTE_SOURCE_ID)

                val boundsBuilder = LatLngBounds.Builder()
                route.geometry.forEach { point ->
                    boundsBuilder.include(LatLng(point.latitude(), point.longitude()))
                }
                mapManager.fitToBounds(boundsBuilder.build())
            }
        }

        TopBar(
            onMenuClick = onMenuClick,
            onSettingsClick = onSettingsClick,
            isScanningActive = false,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        AnimatedVisibility(
            visible = uiState.isNavigating,
            enter = slideInVertically(
                initialOffsetY = { -it / 2 }
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { -it / 2 }
            ) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 70.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
            ) {
                if (uiState.navigationState is NavigationState.Arrived) {
                    TurnBanner(
                        direction = uiState.nextDirection,
                        distanceText = "",
                        streetName = "You have arrived",
                        instruction = "",
                        isApproaching = false,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                } else {
                    TurnBanner(
                        direction = uiState.nextDirection,
                        distanceText = uiState.distanceToTurn,
                        streetName = uiState.currentStreet,
                        instruction = uiState.currentInstruction,
                        isApproaching = uiState.isApproachingTurn,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }

                ETACard(
                    eta = uiState.eta,
                    remainingDistance = uiState.remainingDistance,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
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
    }
}
