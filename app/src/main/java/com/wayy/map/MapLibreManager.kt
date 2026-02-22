package com.wayy.map

import android.content.Context
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.OnMapReadyCallback
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.io.File

/**
 * Manager for MapLibre map operations
 */
class MapLibreManager(private val context: Context) {

    private var mapView: MapView? = null
    private var mapLibreMap: MapLibreMap? = null

    companion object {
        private const val TILE_USER_AGENT = "WayyMap/1.0 (contact@wayy.app)"
        private const val CACHE_MAX_AGE_SECONDS = 60 * 60 * 24 * 7
        private const val CACHE_SIZE_BYTES = 200L * 1024L * 1024L
    }

    /**
     * Initialize MapLibre SDK (call once in Application)
     */
    fun initialize() {
        MapLibre.getInstance(context)
        HttpRequestUtil.setOkHttpClient(buildTileClient())
    }

    /**
     * Create and configure a new MapView
     */
    fun createMapView(
        onMapReady: (MapLibreMap) -> Unit
    ): MapView {
        val mapView = MapView(context)
        this.mapView = mapView

        mapView.onCreate(null)

        mapView.getMapAsync { map ->
            this.mapLibreMap = map
            configureMap(map)
            onMapReady(map)
        }

        return mapView
    }

    /**
     * Configure map style and settings
     */
    private fun configureMap(map: MapLibreMap) {
        map.uiSettings.isCompassEnabled = false
        map.uiSettings.isLogoEnabled = false
        map.uiSettings.isAttributionEnabled = false
    }

    private fun buildTileClient(): OkHttpClient {
        val cacheDir = File(context.cacheDir, "map-tiles")
        val cache = Cache(cacheDir, CACHE_SIZE_BYTES)
        val headerInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", TILE_USER_AGENT)
                .build()
            val response = chain.proceed(request)
            val cacheControl = response.header("Cache-Control").orEmpty()
            if (cacheControl.contains("no-store", ignoreCase = true) ||
                cacheControl.contains("no-cache", ignoreCase = true) ||
                cacheControl.isBlank()
            ) {
                response.newBuilder()
                    .header("Cache-Control", "public, max-age=$CACHE_MAX_AGE_SECONDS")
                    .build()
            } else {
                response
            }
        }
        return OkHttpClient.Builder()
            .cache(cache)
            .addInterceptor(headerInterceptor)
            .build()
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

    /**
     * Update user location marker position
     */
    fun updateUserLocation(location: LatLng, bearing: Float = 0f) {
        mapLibreMap?.style?.let { style ->
            val source = style.getSourceAs<GeoJsonSource>("location-source")
            if (source != null) {
                val point = Point.fromLngLat(location.longitude, location.latitude)
                val feature = Feature.fromGeometry(point)
                feature.addNumberProperty("bearing", bearing.toDouble())
                source.setGeoJson(feature)
                android.util.Log.d("WayyMap", "Updated location marker: lat=${location.latitude}, lon=${location.longitude}")
            } else {
                android.util.Log.w("WayyMap", "Location source not found, marker not updated")
            }
        }
    }

    /**
     * Center camera on user location (no bearing)
     */
    fun centerOnUserLocation(location: LatLng, zoom: Double = 15.0) {
        mapLibreMap?.let { map ->
            val position = CameraPosition.Builder()
                .target(location)
                .zoom(zoom)
                .build()
            map.easeCamera(CameraUpdateFactory.newCameraPosition(position), 300)
            android.util.Log.d("WayyMap", "Centering camera on location: lat=${location.latitude}, lon=${location.longitude}, zoom=$zoom")
        }
    }

    /**
     * Animate camera to follow user with bearing
     */
    fun animateToLocationWithBearing(location: LatLng, bearing: Float, zoom: Double = 17.0, tilt: Double = 45.0) {
        mapLibreMap?.let { map ->
            val position = CameraPosition.Builder()
                .target(location)
                .zoom(zoom)
                .bearing(bearing.toDouble())
                .tilt(tilt)
                .build()
            map.animateCamera(CameraUpdateFactory.newCameraPosition(position), 500)
        }
    }

    /**
     * Enable/disable camera following user location
     */
    fun setCameraFollowEnabled(enabled: Boolean) {
        mapLibreMap?.uiSettings?.isScrollGesturesEnabled = !enabled
    }
}
