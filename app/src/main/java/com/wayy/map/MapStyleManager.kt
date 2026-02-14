package com.wayy.map

import android.content.Context
import com.wayy.BuildConfig
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import java.io.File

/**
 * Map style manager for dark theme navigation maps
 */
class MapStyleManager(private val context: Context) {

    companion object {
        val STYLE_URI: String = BuildConfig.MAP_STYLE_URL.ifBlank { DEFAULT_STYLE_URL }
        private const val PROTOMAPS_STYLE_ASSET = "protomaps_style.json"
        private const val DEFAULT_STYLE_URL = "asset://protomaps_style.json"
        private const val DEFAULT_PMTILES_SOURCE_URL = "pmtiles://asset://doha.pmtiles"

        // Style constants
        const val ROUTE_SOURCE_ID = "route-source"
        const val ROUTE_LAYER_ID = "route-layer"
        const val ROUTE_TRAFFIC_LAYER_ID = "route-traffic-layer"
        const val ROUTE_ALTERNATE_SOURCE_ID = "route-alt-source"
        const val ROUTE_ALTERNATE_LAYER_ID = "route-alt-layer"
        const val LOCATION_SOURCE_ID = "location-source"
        const val LOCATION_LAYER_ID = "location-layer"
        const val POI_SOURCE_ID = "poi-source"
        const val POI_LAYER_ID = "poi-layer"
        const val TRAFFIC_SOURCE_ID = "traffic-source"
        const val TRAFFIC_LAYER_ID = "traffic-layer"
        const val TRAFFIC_PULSE_LAYER_ID = "traffic-pulse-layer"
        const val TRAFFIC_INTENSITY_SOURCE_ID = "traffic-intensity-source"
        const val TRAFFIC_INTENSITY_LAYER_FAST_ID = "traffic-intensity-fast-layer"
        const val TRAFFIC_INTENSITY_LAYER_MODERATE_ID = "traffic-intensity-moderate-layer"
        const val TRAFFIC_INTENSITY_LAYER_SLOW_ID = "traffic-intensity-slow-layer"

        private const val POI_GAS_COLOR = "#FB923C"
        private const val POI_FOOD_COLOR = "#A3E635"
        private const val POI_PARKING_COLOR = "#22D3EE"
        private const val POI_LODGING_COLOR = "#A855F7"
        private const val POI_DEFAULT_COLOR = "#3B82F6"
    }

     fun applyDarkStyle(
        map: MapLibreMap,
        tilejsonUrlOverride: String? = null,
        mapStyleUrlOverride: String? = null,
        onStyleLoaded: () -> Unit = {}
    ) {
        val pmtilesTilejsonUrl = tilejsonUrlOverride?.trim().orEmpty()
            .ifBlank { BuildConfig.PMTILES_TILEJSON_URL }

        val styleUri = when {
            pmtilesTilejsonUrl.isNotBlank() -> {
                android.util.Log.d("MapStyleManager", "Using remote PMTiles TileJSON: $pmtilesTilejsonUrl")
                loadStyleWithTilejson(pmtilesTilejsonUrl)
            }
            mapStyleUrlOverride.isNullOrBlank().not() -> {
                android.util.Log.d("MapStyleManager", "Using override style: $mapStyleUrlOverride")
                mapStyleUrlOverride!!.trim()
            }
            BuildConfig.MAP_STYLE_URL.isNotBlank() -> {
                android.util.Log.d("MapStyleManager", "Using build config style: ${BuildConfig.MAP_STYLE_URL}")
                BuildConfig.MAP_STYLE_URL
            }
            else -> {
                android.util.Log.d("MapStyleManager", "Using embedded PMTiles style")
                DEFAULT_STYLE_URL
            }
        }

        map.setStyle(Style.Builder().fromUri(styleUri)) { style ->
            applyEnglishLabels(style)
            android.util.Log.d("MapStyleManager", "Style loaded successfully: $styleUri")
            onStyleLoaded()
        }
    }

    private fun loadStyleWithTilejson(tilejsonUrl: String): String {
        val rawStyle = context.assets.open(PROTOMAPS_STYLE_ASSET)
            .bufferedReader()
            .use { it.readText() }
        val styleJson = rawStyle.replace(DEFAULT_PMTILES_SOURCE_URL, tilejsonUrl)
        val cacheStyleFile = File(context.cacheDir, "protomaps_style_remote.json")
        cacheStyleFile.parentFile?.mkdirs()
        cacheStyleFile.writeText(styleJson)
        return "file://${cacheStyleFile.absolutePath}"
    }

    private fun applyEnglishLabels(style: Style) {
        val labelExpression = Expression.coalesce(
            Expression.get("name:en"),
            Expression.get("name:latin"),
            Expression.get("name"),
            Expression.get("pgf:name")
        )
        style.layers.orEmpty()
            .filterIsInstance<SymbolLayer>()
            .forEach { layer ->
                try {
                    layer.setProperties(
                        PropertyFactory.textField(labelExpression)
                    )
                } catch (e: Exception) {
                    android.util.Log.w("MapStyleManager", "Could not set labels for layer ${layer.id}: ${e.message}")
                }
            }
        android.util.Log.d("MapStyleManager", "Applied English labels to ${style.layers.count { it is SymbolLayer }} symbol layers")
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

    fun addRouteTrafficLayer(map: MapLibreMap, sourceId: String = ROUTE_SOURCE_ID) {
        val layer = LineLayer(ROUTE_TRAFFIC_LAYER_ID, sourceId).apply {
            setProperties(
                PropertyFactory.lineColor(
                    Expression.match(
                        Expression.get("trafficSeverity"),
                        Expression.literal("#A3E635"),
                        Expression.stop("slow", Expression.literal("#EF4444")),
                        Expression.stop("moderate", Expression.literal("#FB923C")),
                        Expression.stop("fast", Expression.literal("#A3E635"))
                    )
                ),
                PropertyFactory.lineWidth(8f),
                PropertyFactory.lineOpacity(0.55f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        }
        map.style?.addLayerBelow(layer, ROUTE_LAYER_ID)
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
    @Suppress("UNUSED_PARAMETER")
    fun setSmoothTransitions(map: MapLibreMap) {
        // Disabled for compatibility - transitions are handled by map SDK
    }

    fun addPoiLayer(map: MapLibreMap, sourceId: String = POI_SOURCE_ID) {
        val layer = CircleLayer(POI_LAYER_ID, sourceId).apply {
            setProperties(
                PropertyFactory.circleRadius(6f),
                PropertyFactory.circleColor(
                    Expression.match(
                        Expression.downcase(Expression.get("category")),
                        Expression.literal(POI_DEFAULT_COLOR),
                        Expression.stop("gas", Expression.literal(POI_GAS_COLOR)),
                        Expression.stop("food", Expression.literal(POI_FOOD_COLOR)),
                        Expression.stop("parking", Expression.literal(POI_PARKING_COLOR)),
                        Expression.stop("lodging", Expression.literal(POI_LODGING_COLOR))
                    )
                ),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(1.5f),
                PropertyFactory.circleOpacity(0.9f)
            )
        }
        map.style?.addLayer(layer)
    }

    fun addTrafficLayer(map: MapLibreMap, sourceId: String = TRAFFIC_SOURCE_ID) {
        val layer = CircleLayer(TRAFFIC_LAYER_ID, sourceId).apply {
            setProperties(
                PropertyFactory.circleRadius(7f),
                PropertyFactory.circleColor(
                    Expression.match(
                        Expression.downcase(Expression.get("severity")),
                        Expression.literal("#FB923C"),
                        Expression.stop("light", Expression.literal("#A3E635")),
                        Expression.stop("moderate", Expression.literal("#FB923C")),
                        Expression.stop("heavy", Expression.literal("#EF4444"))
                    )
                ),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(1.5f),
                PropertyFactory.circleOpacity(0.85f)
            )
        }
        map.style?.addLayer(layer)
    }

    fun addTrafficPulseLayer(map: MapLibreMap, sourceId: String = TRAFFIC_SOURCE_ID) {
        val layer = CircleLayer(TRAFFIC_PULSE_LAYER_ID, sourceId).apply {
            setProperties(
                PropertyFactory.circleRadius(12f),
                PropertyFactory.circleColor("#EF4444"),
                PropertyFactory.circleOpacity(0.4f)
            )
            setFilter(Expression.eq(Expression.downcase(Expression.get("severity")), "heavy"))
        }
        map.style?.addLayer(layer)
    }

    fun addTrafficIntensityLayer(
        map: MapLibreMap,
        sourceId: String = TRAFFIC_INTENSITY_SOURCE_ID
    ) {
        val slowLayer = LineLayer(TRAFFIC_INTENSITY_LAYER_SLOW_ID, sourceId).apply {
            setProperties(
                PropertyFactory.lineColor("#EF4444"),
                PropertyFactory.lineWidth(6f),
                PropertyFactory.lineOpacity(0.8f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
            setFilter(Expression.eq(Expression.get("severity"), "slow"))
        }

        val moderateLayer = LineLayer(TRAFFIC_INTENSITY_LAYER_MODERATE_ID, sourceId).apply {
            setProperties(
                PropertyFactory.lineColor("#FB923C"),
                PropertyFactory.lineWidth(6f),
                PropertyFactory.lineOpacity(0.8f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
            setFilter(Expression.eq(Expression.get("severity"), "moderate"))
        }

        val fastLayer = LineLayer(TRAFFIC_INTENSITY_LAYER_FAST_ID, sourceId).apply {
            setProperties(
                PropertyFactory.lineColor("#A3E635"),
                PropertyFactory.lineWidth(6f),
                PropertyFactory.lineOpacity(0.8f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
            setFilter(Expression.eq(Expression.get("severity"), "fast"))
        }

        map.style?.addLayer(slowLayer)
        map.style?.addLayer(moderateLayer)
        map.style?.addLayer(fastLayer)
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
