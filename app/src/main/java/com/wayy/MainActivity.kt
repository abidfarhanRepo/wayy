package com.wayy

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.wayy.ui.screens.MainNavigationScreen
import com.wayy.ui.screens.RouteOverviewScreen
import com.wayy.ui.theme.WayyColors
import com.wayy.ui.theme.WayyTheme
import com.wayy.ui.theme.WayyTypography
import com.wayy.data.local.TripLoggingManager
import com.wayy.data.repository.LocalPoiManager
import com.wayy.data.repository.RouteHistoryManager
import com.wayy.data.repository.TrafficReportManager
import com.wayy.viewmodel.NavigationViewModel
import com.wayy.debug.DiagnosticLogger
import com.wayy.ml.OnDeviceMlManager
import org.maplibre.geojson.Point

/**
 * Main activity for MapPulse Ultimate
 */
class MainActivity : ComponentActivity() {

    private var hasLocationPermission by mutableStateOf(false)

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions.values.all { it }
        if (!hasLocationPermission) {
            Toast.makeText(
                this,
                "Location permission required for navigation",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        checkLocationPermission()

        setContent {
            WayyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = WayyColors.BgPrimary
                ) {
                    AppContent(
                        hasPermission = hasLocationPermission,
                        onRequestPermission = { requestLocationPermission() }
                    )
                }
            }
        }
    }

    private fun checkLocationPermission() {
        hasLocationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }
}

/**
 * Main app content
 */
@Composable
fun AppContent(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Main) }
    val context = LocalContext.current
    val navigationViewModel: NavigationViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(NavigationViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
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
        }
    )

    LaunchedEffect(hasPermission) {
        if (!hasPermission) {
            onRequestPermission()
        }
    }

    when (currentScreen) {
        AppScreen.Main -> {
            MainNavigationScreen(
                viewModel = navigationViewModel,
                onMenuClick = {
                    currentScreen = AppScreen.RouteOverview
                },
                onSettingsClick = {
                    // Navigate to settings
                }
            )
        }
        AppScreen.RouteOverview -> {
            RouteOverviewScreen(
                viewModel = navigationViewModel,
                onDestinationSelected = { destination ->
                    navigationViewModel.startNavigation(
                        Point.fromLngLat(destination.lon, destination.lat),
                        destination.display_name
                    )
                    currentScreen = AppScreen.Main
                },
                onBack = {
                    currentScreen = AppScreen.Main
                },
                onRecentRouteClick = { recentRoute ->
                    navigationViewModel.startNavigation(
                        Point.fromLngLat(recentRoute.endLng, recentRoute.endLat),
                        recentRoute.endName
                    )
                    currentScreen = AppScreen.Main
                },
                onPoiSelected = { poi ->
                    navigationViewModel.startNavigation(
                        Point.fromLngLat(poi.lng, poi.lat),
                        poi.name
                    )
                    currentScreen = AppScreen.Main
                }
            )
        }
    }
}

/**
 * App screens enum
 */
sealed class AppScreen {
    object Main : AppScreen()
    object RouteOverview : AppScreen()
}
