package com.wayy.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap

/**
 * MapLibre MapView Composable wrapper
 *
 * @param modifier Modifier for the view
 * @param onMapReady Callback when map is ready
 * @param onCreate Callback for lifecycle onCreate
 * @param onStart Callback for lifecycle onStart
 * @param onResume Callback for lifecycle onResume
 * @param onPause Callback for lifecycle onPause
 * @param onStop Callback for lifecycle onStop
 * @param onDestroy Callback for lifecycle onDestroy
 */
@Composable
fun MapLibreView(
    modifier: Modifier = Modifier,
    onMapReady: (MapLibreMap) -> Unit = {},
    onCreate: ((MapView) -> Unit)? = null,
    onDestroy: ((MapView) -> Unit)? = null
) {
    var mapView by remember { mutableStateOf<MapView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                onCreate(null)
                mapView = this
                onCreate?.invoke(this)

                getMapAsync { map ->
                    onMapReady(map)
                }
            }
        },
        update = { _ ->
            // Update callbacks if they change
        }
    )

    // Handle lifecycle
    DisposableEffect(mapView) {
        val view = mapView
        onDispose {
            view?.onDestroy()
            if (view != null) {
                onDestroy?.invoke(view)
            }
        }
    }
}

/**
 * Simplified MapView with auto lifecycle management
 * Properly handles onResume/onPause/onDestroy with Compose lifecycle
 */
@Composable
fun MapViewAutoLifecycle(
    manager: MapLibreManager,
    modifier: Modifier = Modifier,
    onMapReady: (MapLibreMap) -> Unit = {}
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val mapView = remember { manager.createMapView(onMapReady) }

    // Handle lifecycle events
    DisposableEffect(lifecycle) {
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                super.onStart(owner)
                mapView.onStart()
            }

            override fun onResume(owner: LifecycleOwner) {
                super.onResume(owner)
                mapView.onResume()
            }

            override fun onPause(owner: LifecycleOwner) {
                super.onPause(owner)
                mapView.onPause()
            }

            override fun onStop(owner: LifecycleOwner) {
                super.onStop(owner)
                mapView.onStop()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                super.onDestroy(owner)
                mapView.onDestroy()
            }
        }

        lifecycle.addObserver(observer)

        onDispose {
            lifecycle.removeObserver(observer)
            // Don't destroy here - let the lifecycle handle it
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { mapView }
    )
}
