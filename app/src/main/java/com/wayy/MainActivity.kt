package com.wayy

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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
import androidx.core.content.ContextCompat
import com.wayy.ui.screens.DemoNavigationScreen
import com.wayy.ui.screens.MainNavigationScreen
import com.wayy.ui.screens.RouteOverviewScreen
import com.wayy.ui.theme.WayyColors
import com.wayy.ui.theme.WayyTheme
import com.wayy.ui.theme.WayyTypography

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

    LaunchedEffect(hasPermission) {
        if (!hasPermission) {
            onRequestPermission()
        }
    }

    when (currentScreen) {
        AppScreen.Main -> {
            MainNavigationScreen(
                onMenuClick = {
                    // Navigate to menu/settings
                },
                onSettingsClick = {
                    // Navigate to settings
                }
            )
        }
        AppScreen.RouteOverview -> {
            RouteOverviewScreen(
                onDestinationSelected = { destination ->
                    // Start navigation to destination
                    currentScreen = AppScreen.Main
                },
                onRecentRouteClick = {
                    // Navigate to route
                }
            )
        }
        AppScreen.Demo -> {
            DemoNavigationScreen()
        }
    }
}

/**
 * App screens enum
 */
sealed class AppScreen {
    object Main : AppScreen()
    object RouteOverview : AppScreen()
    object Demo : AppScreen()
}
