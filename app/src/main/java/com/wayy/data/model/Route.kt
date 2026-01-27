package com.wayy.data.model

import com.google.gson.Gson
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import org.maplibre.geojson.Point
import java.lang.reflect.Type
import kotlin.math.abs

/**
 * Route data models
 */

/**
 * Complete route from OSRM
 */
data class Route(
    val geometry: List<Point>,
    val duration: Long,        // in seconds
    val distance: Double,      // in meters
    val legs: List<RouteLeg>
)

/**
 * A segment of the route between two waypoints
 */
data class RouteLeg(
    val steps: List<RouteStep>,
    val distance: Double,      // in meters
    val duration: Long         // in seconds
)

/**
 * Single navigation instruction step
 */
data class RouteStep(
    val instruction: String,
    val distance: Double,      // in meters
    val duration: Long,        // in seconds
    val maneuver: Maneuver,
    val geometry: List<Point>
)

/**
 * Turn maneuver information
 */
data class Maneuver(
    val type: String,          // turn, arrive, depart, etc.
    val modifier: String?,     // left, right, uturn, etc.
    val location: Point,
    val bearingBefore: Int,
    val bearingAfter: Int
)

/**
 * OSRM API response wrapper
 */
data class OSRMResponse(
    val code: String,
    val routes: List<OSRMRoute>
)

data class OSRMRoute(
    val geometry: String,          // Encoded polyline
    val duration: Long,
    val distance: Double,
    val legs: List<OSRMLeg>
)

data class OSRMLeg(
    val steps: List<OSRMStep>,
    val distance: Double,
    val duration: Long
)

data class OSRMStep(
    val maneuver: OSRMManeuver,
    val distance: Double,
    val duration: Long,
    val geometry: String,
    val name: String
)

data class OSRMManeuver(
    val type: String,
    val modifier: String?,
    val location: List<Double>,
    val bearing_before: Int,
    val bearing_after: Int
)

/**
 * Polyline decoder for OSRM geometry
 */
object PolylineDecoder {
    fun decode(encoded: String): List<Point> {
        val points = mutableListOf<Point>()
        var index = 0
        var lat = 0
        var lng = 0

        while (index < encoded.length) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if ((result and 1) != 0) -(result shr 1) - 1 else (result shr 1)
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if ((result and 1) != 0) -(result shr 1) - 1 else (result shr 1)
            lng += dlng

            points.add(Point.fromLngLat(lng * 1e-5, lat * 1e-5))
        }

        return points
    }
}
