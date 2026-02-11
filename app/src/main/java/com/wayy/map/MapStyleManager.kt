package com.wayy.map

import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
// Note: Some MapLibre SDK versions may not have RasterSource or StyleTransition
// We'll use simpler styling approach

/**
 * Map style manager for dark theme navigation maps
 */
class MapStyleManager {

    companion object {
        // FREE TILE SOURCES
        // 1. OSM Standard - reliable, shows roads, no API key needed
        private const val OSM_TILES = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"

        // 2. For buildings and 3D view, you need a MapTiler API key:
        // Get free key at https://cloud.maptiler.com/
        // Replace YOUR_API_KEY below with your actual key
        // private const val MAPTILER_VECTOR_STYLE = "https://api.maptiler.com/maps/streets/style.json?key=YOUR_API_KEY"

        // Style constants
        const val ROUTE_SOURCE_ID = "route-source"
        const val ROUTE_LAYER_ID = "route-layer"
        const val ROUTE_ALTERNATE_SOURCE_ID = "route-alt-source"
        const val ROUTE_ALTERNATE_LAYER_ID = "route-alt-layer"
        const val LOCATION_SOURCE_ID = "location-source"
        const val LOCATION_LAYER_ID = "location-layer"
    }

    /**
     * Apply dark navigation style to map
     * Uses OSM tiles (free, no API key, shows roads)
     *
     * FOR BUILDINGS AND 3D VIEW:
     * You need to add a MapTiler API key above and uncomment the MAPTILER_VECTOR_STYLE line
     * Then change styleUrl to use MAPTILER_VECTOR_STYLE instead of OSM
     *
     * @param map The MapLibreMap instance
     * @param onStyleLoaded Callback when style is successfully loaded
     */
    fun applyDarkStyle(
        map: MapLibreMap,
        onStyleLoaded: () -> Unit = {}
    ) {
        // Build style JSON with OSM tiles
        val styleJson = """
        {
            "version": 8,
            "name": "Wayy Dark",
            "sources": {
                "osm-tiles": {
                    "type": "raster",
                    "tiles": ["$OSM_TILES"],
                    "tileSize": 256,
                    "attribution": "© OpenStreetMap contributors"
                }
            },
            "layers": [
                {
                    "id": "background",
                    "type": "background",
                    "paint": {
                        "background-color": "#0f172a"
                    }
                },
                {
                    "id": "osm-tiles-layer",
                    "type": "raster",
                    "source": "osm-tiles",
                    "paint": {
                        "raster-opacity": 1.0
                    }
                }
            ]
        }
        """.trimIndent()

        map.setStyle(Style.Builder().fromJson(styleJson)) { style ->
            android.util.Log.d("MapStyleManager", "Style loaded successfully with OSM tiles")
            onStyleLoaded()
        }
    }

    /**
     * Add route line to map with navigation styling
     */
    fun addRouteLayer(map: MapLibreMap, sourceId: String = ROUTE_SOURCE_ID) {
        val layer = LineLayer(ROUTE_LAYER_ID, sourceId).apply {
            setProperties(
                PropertyFactory.lineColor("#A3E635"),  // Primary lime
                PropertyFactory.lineWidth(5f),
                PropertyFactory.lineOpacity(0.9f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        }

        // Add layer at top of stack
        map.style?.addLayer(layer)
    }

    /**
     * Add alternate route layer (ghost route)
     */
    fun addAlternateRouteLayer(map: MapLibreMap, sourceId: String = ROUTE_ALTERNATE_SOURCE_ID) {
        val layer = LineLayer(ROUTE_ALTERNATE_LAYER_ID, sourceId).apply {
            setProperties(
                PropertyFactory.lineColor("#22D3EE"),  // Primary cyan
                PropertyFactory.lineWidth(4f),
                PropertyFactory.lineOpacity(0.5f),
                PropertyFactory.lineDasharray(arrayOf(2f, 2f)),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        }

        map.style?.addLayerBelow(layer, ROUTE_LAYER_ID)
    }

    /**
     * Add user location marker
     */
    fun addLocationMarker(map: MapLibreMap, sourceId: String = LOCATION_SOURCE_ID) {
        // Circle layer for accuracy radius
        val accuracyLayer = FillLayer("location-accuracy-layer", sourceId).apply {
            setProperties(
                PropertyFactory.fillColor("#A3E635"),
                PropertyFactory.fillOpacity(0.15f)
            )
        }

        // Inner circle for actual location
        val locationLayer = org.maplibre.android.style.layers.CircleLayer(
            LOCATION_LAYER_ID,
            sourceId
        ).apply {
            setProperties(
                PropertyFactory.circleRadius(8f),
                PropertyFactory.circleColor("#A3E635"),
                PropertyFactory.circleStrokeWidth(3f),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleOpacity(1f)
            )
        }

        map.style?.addLayer(accuracyLayer)
        map.style?.addLayer(locationLayer)
    }

    /**
     * Update map transition settings for smooth animations
     * Note: StyleTransition may not be available in all MapLibre versions
     */
    fun setSmoothTransitions(map: MapLibreMap) {
        // Disabled for compatibility - transitions are handled by map SDK
    }

    /**
     * Get route color based on route type
     */
    fun getRouteColor(isPrimary: Boolean): String {
        return if (isPrimary) "#A3E635" else "#22D3EE"
    }

    /**
     * Get route opacity based on route type
     */
    fun getRouteOpacity(isPrimary: Boolean): Float {
        return if (isPrimary) 0.9f else 0.5f
    }
}
