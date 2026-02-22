package com.wayy.data.model

import org.maplibre.geojson.Point

data class LatLng(
    val latitude: Double,
    val longitude: Double
)

data class Route(
    val geometry: List<Point>,
    val duration: Double,
    val distance: Double,
    val legs: List<RouteLeg>
)

data class RouteLeg(
    val steps: List<RouteStep>,
    val distance: Double,
    val duration: Double
)

data class RouteStep(
    val instruction: String,
    val distance: Double,
    val duration: Double,
    val maneuver: Maneuver,
    val geometry: List<Point>
)

data class Maneuver(
    val type: String,
    val modifier: String?,
    val location: Point,
    val bearingBefore: Int,
    val bearingAfter: Int
)

data class OSRMResponse(
    val code: String,
    val routes: List<OSRMRoute>
)

data class OSRMRoute(
    val geometry: String,
    val duration: Double,
    val distance: Double,
    val legs: List<OSRMLeg>
)

data class OSRMLeg(
    val steps: List<OSRMStep>,
    val distance: Double,
    val duration: Double
)

data class OSRMStep(
    val maneuver: OSRMManeuver,
    val distance: Double,
    val duration: Double,
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
