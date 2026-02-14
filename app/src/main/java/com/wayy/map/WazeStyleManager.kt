package com.wayy.map

import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.PropertyFactory.*

/**
 * Waze-like visual effects manager
 * Handles animations, gradients, and styling for Waze-like navigation experience
 */
class WazeStyleManager {

    companion object {
        // Layer IDs
        private const val ROUTE_GLOW_LAYER = "route-glow-layer"
        private const val ROUTE_BORDER_LAYER = "route-border-layer"
        private const val ROUTE_MAIN_LAYER = "route-main-layer"
        private const val LOCATION_PULSE_LAYER = "location-pulse-layer"
        private const val LOCATION_DOT_LAYER = "location-dot-layer"

        // Colors (Waze-inspired)
        private const val COLOR_ROUTE_GLOW = "#22D3EE"    // Cyan
        private const val COLOR_ROUTE_MAIN = "#A3E635"    // Lime
        private const val COLOR_ROUTE_BORDER = "#FFFFFF"  // White
        private const val COLOR_LOCATION_PULSE = "#22D3EE" // Cyan
        private const val COLOR_LOCATION_DOT = "#FFFFFF"   // White
        private const val COLOR_LOCATION_BORDER = "#A3E635" // Lime

        // Traffic colors
        private const val COLOR_TRAFFIC_FAST = "#10B981"      // Green
        private const val COLOR_TRAFFIC_MODERATE = "#F59E0B"  // Yellow
        private const val COLOR_TRAFFIC_SLOW = "#EF4444"      // Red
    }

    /**
     * Add complete Waze-style route with glow, border, and main line
     *
     * Creates a triple-layer effect:
     * 1. Outer glow (cyan, 15px) - neon effect
     * 2. Border (white, 10px) - definition
     * 3. Main line (lime, 8px) - prominent route
     */
    fun addWazeRoute(
        map: MapLibreMap,
        sourceId: String = "route-source"
    ) {
        // Add layers in order (bottom to top)
        addRouteGlow(map, sourceId)
        addRouteBorder(map, sourceId)
        addRouteMain(map, sourceId)
    }

    /**
     * Outer glow layer (creates the neon effect)
     */
    private fun addRouteGlow(map: MapLibreMap, sourceId: String) {
        val layer = LineLayer(ROUTE_GLOW_LAYER, sourceId).apply {
            setProperties(
                PropertyFactory.lineColor(COLOR_ROUTE_GLOW),
                PropertyFactory.lineWidth(15f),
                PropertyFactory.lineOpacity(0.3f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        }
        map.style?.addLayer(layer)
    }

    /**
     * Border layer (creates definition and separates glow from main line)
     */
    private fun addRouteBorder(map: MapLibreMap, sourceId: String) {
        val layer = LineLayer(ROUTE_BORDER_LAYER, sourceId).apply {
            setProperties(
                PropertyFactory.lineColor(COLOR_ROUTE_BORDER),
                PropertyFactory.lineWidth(10f),
                PropertyFactory.lineOpacity(0.3f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        }
        map.style?.addLayer(layer)
    }

    /**
     * Main route line (bright, prominent)
     */
    private fun addRouteMain(map: MapLibreMap, sourceId: String) {
        val layer = LineLayer(ROUTE_MAIN_LAYER, sourceId).apply {
            setProperties(
                PropertyFactory.lineColor(COLOR_ROUTE_MAIN),
                PropertyFactory.lineWidth(8f),
                PropertyFactory.lineOpacity(1f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        }
        map.style?.addLayer(layer)
    }

    /**
     * Add animated user location marker with pulsing ring
     *
     * Creates a two-layer effect:
     * 1. Outer pulsing ring (cyan, 25px) - location accuracy
     * 2. Inner dot (white with lime border, 10px) - actual position
     */
    fun addUserLocationMarker(
        map: MapLibreMap,
        sourceId: String
    ) {
        android.util.Log.d("WayyWaze", "Adding location marker layers for source: $sourceId")

        // Pulsing outer ring (location accuracy indicator)
        val pulseLayer = CircleLayer(LOCATION_PULSE_LAYER, sourceId).apply {
            setProperties(
                PropertyFactory.circleRadius(25f),
                PropertyFactory.circleColor(COLOR_LOCATION_PULSE),
                PropertyFactory.circleOpacity(0.3f),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleStrokeColor(COLOR_LOCATION_BORDER)
            )
        }
        map.style?.addLayer(pulseLayer)
        android.util.Log.d("WayyWaze", "Added pulse layer: $LOCATION_PULSE_LAYER")

        // Inner dot (actual location)
        val dotLayer = CircleLayer(LOCATION_DOT_LAYER, sourceId).apply {
            setProperties(
                PropertyFactory.circleRadius(10f),
                PropertyFactory.circleColor(COLOR_LOCATION_DOT),
                PropertyFactory.circleStrokeWidth(3f),
                PropertyFactory.circleStrokeColor(COLOR_LOCATION_BORDER),
                PropertyFactory.circleOpacity(1f)
            )
        }
        map.style?.addLayer(dotLayer)
        android.util.Log.d("WayyWaze", "Added dot layer: $LOCATION_DOT_LAYER")
    }

    /**
     * Clear all Waze-style layers
     */
    fun clearWazeRoute(map: MapLibreMap) {
        listOf(
            ROUTE_GLOW_LAYER,
            ROUTE_BORDER_LAYER,
            ROUTE_MAIN_LAYER,
            LOCATION_PULSE_LAYER,
            LOCATION_DOT_LAYER
        ).forEach { layerId ->
            try {
                map.style?.removeLayer(layerId)
            } catch (e: Exception) {
                // Layer may not exist
            }
        }
    }

    /**
     * Get traffic color based on congestion level (0-1)
     * 0.0 - 0.3: Fast (green)
     * 0.3 - 0.7: Moderate (yellow)
     * 0.7 - 1.0: Slow (red)
     */
    fun getTrafficColor(congestion: Float): String {
        return when {
            congestion < 0.3f -> COLOR_TRAFFIC_FAST
            congestion < 0.7f -> COLOR_TRAFFIC_MODERATE
            else -> COLOR_TRAFFIC_SLOW
        }
    }
}
