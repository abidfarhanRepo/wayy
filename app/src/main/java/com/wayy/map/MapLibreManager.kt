package com.wayy.map

import android.content.Context
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.OnMapReadyCallback
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * Manager for MapLibre map operations
 */
class MapLibreManager(private val context: Context) {

    private var mapView: MapView? = null
    private var mapLibreMap: MapLibreMap? = null

    companion object {
        // CartoDB Dark Matter tiles (free, no API key required)
        // Sleek dark theme perfect for Waze-like navigation
        // NOTE: Removed @2x suffix as MapLibre doesn't handle it correctly
        private const val CARTO_DARK_TILES = "https://a.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png"

        // Alternative dark styles (commented out):
        // private const val OSM_RASTER_STYLE = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
        // private const val DARK_STYLE_URL = "https://demotiles.maplibre.org/style.json"
        // private const val THUNDERFOREST_DARK = "https://a.tile.thunderforest.com/transport-dark/{z}/{x}/{y}.png"
    }

    /**
     * Initialize MapLibre SDK (call once in Application)
     */
    fun initialize() {
        MapLibre.getInstance(context)
    }

    /**
     * Create and configure a new MapView
     */
    fun createMapView(
        onMapReady: (MapLibreMap) -> Unit
    ): MapView {
        val mapView = MapView(context)
        this.mapView = mapView

        mapView.getMapAsync { map ->
            this.mapLibreMap = map
            configureMap(map)
            onMapReady(map)
        }

        return mapView
    }

    /**
     * Configure map style and settings
     * Uses CartoDB Dark Matter tiles for Waze-like dark theme
     */
    private fun configureMap(map: MapLibreMap) {
        // Create style JSON with CartoDB Dark Matter tiles
        val styleJson = """
        {
            "version": 8,
            "name": "Wayy Dark",
            "sources": {
                "carto-dark": {
                    "type": "raster",
                    "tiles": ["$CARTO_DARK_TILES"],
                    "tileSize": 256,
                    "attribution": "© OpenStreetMap contributors, © CartoDB"
                }
            },
            "layers": [
                {
                    "id": "background",
                    "type": "background",
                    "paint": {
                        "background-color": "#020617"
                    }
                },
                {
                    "id": "carto-dark-tiles",
                    "type": "raster",
                    "source": "carto-dark",
                    "paint": {
                        "raster-opacity": 0.85,
                        "raster-saturation": -0.3
                    }
                }
            ]
        }
        """.trimIndent()

        map.setStyle(styleJson) {
            // Style loaded - hide UI elements for clean Waze-like look
            map.uiSettings.isCompassEnabled = false
            map.uiSettings.isLogoEnabled = false
            map.uiSettings.isAttributionEnabled = false
        }
    }

    /**
     * Center map on location with zoom
     */
    fun centerMap(location: LatLng, zoom: Double = 15.0) {
        mapLibreMap?.let { map ->
            val position = CameraPosition.Builder()
                .target(location)
                .zoom(zoom)
                .build()
            map.cameraPosition = position
        }
    }

    /**
     * Animate camera to location
     */
    fun animateToLocation(location: LatLng, zoom: Double = 15.0) {
        mapLibreMap?.let { map ->
            val position = CameraPosition.Builder()
                .target(location)
                .zoom(zoom)
                .build()
            map.animateCamera(CameraUpdateFactory.newCameraPosition(position), 1000)
        }
    }

    /**
     * Draw route on map
     */
    fun drawRoute(points: List<LatLng>) {
        mapLibreMap?.let { map ->
            // Convert to GeoJSON points
            val geoJsonPoints = points.map { Point.fromLngLat(it.longitude, it.latitude) }
            val lineString = LineString.fromLngLats(geoJsonPoints)
            val feature = Feature.fromGeometry(lineString)
            val featureCollection = FeatureCollection.fromFeature(feature)

            // Add source
            val source = GeoJsonSource("route-source", featureCollection)
            map.style?.addSource(source)

            // Add layer
            val layer = LineLayer("route-layer", "route-source")
            layer.setProperties(
                PropertyFactory.lineColor("#A3E635"), // Primary lime color
                PropertyFactory.lineWidth(5f),
                PropertyFactory.lineOpacity(0.8f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
            map.style?.addLayer(layer)
        }
    }

    /**
     * Clear route from map
     */
    fun clearRoute() {
        mapLibreMap?.style?.removeLayer("route-layer")
        mapLibreMap?.style?.removeSource("route-source")
    }

    /**
     * Add user location marker
     */
    fun addUserLocationMarker(location: LatLng) {
        mapLibreMap?.let { map ->
            val point = Point.fromLngLat(location.longitude, location.latitude)
            val feature = Feature.fromGeometry(point)
            val featureCollection = FeatureCollection.fromFeature(feature)

            val source = GeoJsonSource("location-source", featureCollection)
            map.style?.addSource(source)

            // You can add a circle layer here for location marker
        }
    }

    /**
     * Fit map to show all points
     */
    fun fitToBounds(bounds: LatLngBounds, padding: Int = 100) {
        mapLibreMap?.let { map ->
            map.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding), 1000)
        }
    }

    /**
     * Get the MapView for lifecycle management
     */
    fun getMapView(): MapView? = mapView

    /**
     * Cleanup
     */
    fun cleanup() {
        mapView?.onDestroy()
        mapView = null
        mapLibreMap = null
    }

    /**
     * Get the MapLibreMap instance for direct access
     */
    fun getMapLibreMap(): MapLibreMap? = mapLibreMap
}
