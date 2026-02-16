package com.wayy

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.BackHandler
import com.wayy.ui.components.common.AppDrawer
import com.wayy.ui.screens.MainNavigationScreen
import com.wayy.ui.screens.SearchDestinationScreen
import com.wayy.ui.screens.SettingsScreen
import com.wayy.ui.screens.HistoryScreen
import com.wayy.ui.screens.SavedPlacesScreen
import com.wayy.ui.theme.WayyColors
import com.wayy.ui.theme.WayyTheme
import com.wayy.data.local.TripLoggingManager
import com.wayy.data.repository.LocalPoiManager
import com.wayy.data.repository.RouteHistoryManager
import com.wayy.data.repository.TrafficReportManager
import com.wayy.viewmodel.NavigationViewModel
import com.wayy.debug.DiagnosticLogger
import com.wayy.ml.OnDeviceMlManager

class MainActivity : ComponentActivity() {
    private var hasLocationPermission by mutableStateOf(false)

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions.values.all { it }
        if (!hasLocationPermission) {
            Toast.makeText(this, "Location permission required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        checkLocationPermission()

        setContent {
            WayyTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = WayyColors.Background) {
                    AppContent(hasPermission = hasLocationPermission, onRequestPermission = { requestLocationPermission() })
                }
            }
        }
    }

    private fun checkLocationPermission() {
        hasLocationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
}

@Composable
fun AppContent(hasPermission: Boolean, onRequestPermission: () -> Unit) {
    var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Main) }
    var showDrawer by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val navigationViewModel: NavigationViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(NavigationViewModel::class.java)) {
                val diagnosticLogger = DiagnosticLogger(context)
                return NavigationViewModel(
                    routeHistoryManager = RouteHistoryManager(context),
                    localPoiManager = LocalPoiManager(context),
                    trafficReportManager = TrafficReportManager(context),
                    tripLoggingManager = TripLoggingManager(context),
                    diagnosticLogger = diagnosticLogger,
                    mlManager = OnDeviceMlManager(context, diagnosticLogger)
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    })

    BackHandler(enabled = currentScreen != AppScreen.Main) {
        currentScreen = AppScreen.Main
    }

    LaunchedEffect(hasPermission) { if (!hasPermission) { onRequestPermission() } }

    Box(modifier = Modifier.fillMaxSize()) {
        when (currentScreen) {
            AppScreen.Main -> MainNavigationScreen(
                viewModel = navigationViewModel,
                onMenuClick = { showDrawer = true },
                onSettingsClick = { currentScreen = AppScreen.Settings },
                onSearchClick = {
                    navigationViewModel.resetForNewSearch()
                    currentScreen = AppScreen.SearchDestination
                }
            )
            AppScreen.SearchDestination -> SearchDestinationScreen(
                onBack = { currentScreen = AppScreen.Main },
                onDestinationSelected = { destination ->
                    navigationViewModel.startNavigation(
                        org.maplibre.geojson.Point.fromLngLat(destination.lon, destination.lat),
                        destination.display_name
                    )
                    currentScreen = AppScreen.Main
                },
                onRecentRouteClick = { place ->
                    navigationViewModel.startNavigation(
                        org.maplibre.geojson.Point.fromLngLat(place.lon, place.lat),
                        place.display_name
                    )
                    currentScreen = AppScreen.Main
                }
            )
            AppScreen.Settings -> SettingsScreen(onBack = { currentScreen = AppScreen.Main })
            AppScreen.History -> HistoryScreen(
                viewModel = navigationViewModel,
                onBack = { currentScreen = AppScreen.Main },
                onRouteClick = { route ->
                    navigationViewModel.startNavigation(
                        org.maplibre.geojson.Point.fromLngLat(route.endLng, route.endLat),
                        route.endName
                    )
                    currentScreen = AppScreen.Main
                }
            )
            AppScreen.SavedPlaces -> SavedPlacesScreen(
                viewModel = navigationViewModel,
                onBack = { currentScreen = AppScreen.Main },
                onPlaceClick = { poi ->
                    navigationViewModel.startNavigation(
                        org.maplibre.geojson.Point.fromLngLat(poi.lng, poi.lat),
                        poi.name
                    )
                    currentScreen = AppScreen.Main
                }
            )
            else -> {}
        }

        if (showDrawer) {
            Box(modifier = Modifier.fillMaxSize().background(WayyColors.Background.copy(alpha = 0.5f)).clickable { showDrawer = false })
            Box(modifier = Modifier.fillMaxHeight().width(280.dp)) {
                AppDrawer(
                    onNavigateToRouteOverview = {
                        navigationViewModel.resetForNewSearch()
                        currentScreen = AppScreen.SearchDestination
                    },
                    onNavigateToSettings = { currentScreen = AppScreen.Settings },
                    onNavigateToHistory = { currentScreen = AppScreen.History },
                    onNavigateToSavedPlaces = { currentScreen = AppScreen.SavedPlaces },
                    onClose = { showDrawer = false }
                )
            }
        }
    }
}

sealed class AppScreen {
    object Main : AppScreen()
    object SearchDestination : AppScreen()
    object Settings : AppScreen()
    object History : AppScreen()
    object SavedPlaces : AppScreen()
}
