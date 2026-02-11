package com.wayy.navigation

import org.maplibre.geojson.Point
import kotlin.math.*
import kotlin.math.roundToLong

object NavigationUtils {

    private const val EARTH_RADIUS_METERS = 6371000.0
    private const val EARTH_RADIUS_MILES = 3958.8

    fun calculateBearing(from: Point, to: Point): Float {
        val lat1 = Math.toRadians(from.latitude())
        val lat2 = Math.toRadians(to.latitude())
        val dLon = Math.toRadians(to.longitude() - from.longitude())

        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val bearing = Math.toDegrees(atan2(y, x))

        return ((bearing + 360) % 360).toFloat()
    }

    fun calculateDistanceMeters(from: Point, to: Point): Double {
        val lat1 = Math.toRadians(from.latitude())
        val lat2 = Math.toRadians(to.latitude())
        val dLat = Math.toRadians(to.latitude() - from.latitude())
        val dLon = Math.toRadians(to.longitude() - from.longitude())

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return EARTH_RADIUS_METERS * c
    }

    fun calculateDistanceMiles(from: Point, to: Point): Double {
        return calculateDistanceMeters(from, to) * 0.000621371
    }

    fun formatDistance(meters: Double): String {
        return if (meters < 1000) {
            "${meters.toInt()} m"
        } else {
            val km = meters / 1000.0
            if (km < 10) {
                String.format("%.1f km", km)
            } else {
                String.format("%.0f km", km)
            }
        }
    }

    fun formatDistanceImperial(meters: Double): String {
        val feet = meters * 3.28084
        return if (feet < 1000) {
            "${feet.toInt()} ft"
        } else {
            val miles = meters * 0.000621371
            if (miles < 10) {
                String.format("%.1f mi", miles)
            } else {
                String.format("%.0f mi", miles)
            }
        }
    }

    fun formatDuration(seconds: Double): String {
        val totalSeconds = kotlin.math.max(0, seconds.roundToLong())
        if (totalSeconds < 60) {
            return "<1m"
        }
        val minutes = totalSeconds / 60
        return if (minutes < 60) {
            "${minutes}m"
        } else {
            val hours = minutes / 60
            val remainingMinutes = minutes % 60
            if (remainingMinutes > 0) {
                "${hours}h ${remainingMinutes}m"
            } else {
                "${hours}h"
            }
        }
    }

    fun formatSpeed(mps: Float): String {
        val mph = mps * 2.23694f
        return String.format("%.0f mph", mph)
    }

    fun isPointOnRoute(
        point: Point,
        routeGeometry: List<Point>,
        toleranceMeters: Double = 50.0
    ): Boolean {
        if (routeGeometry.isEmpty()) return false

        for (i in 0 until routeGeometry.size - 1) {
            val segmentStart = routeGeometry[i]
            val segmentEnd = routeGeometry[i + 1]
            val distance = distanceToLineSegment(point, segmentStart, segmentEnd)
            if (distance <= toleranceMeters) {
                return true
            }
        }
        return false
    }

    fun findClosestPointOnRoute(
        point: Point,
        routeGeometry: List<Point>
    ): Pair<Int, Double> {
        if (routeGeometry.isEmpty()) return Pair(-1, Double.MAX_VALUE)

        var closestIndex = 0
        var minDistance = Double.MAX_VALUE

        for (i in routeGeometry.indices) {
            val distance = calculateDistanceMeters(point, routeGeometry[i])
            if (distance < minDistance) {
                minDistance = distance
                closestIndex = i
            }
        }

        return Pair(closestIndex, minDistance)
    }

    fun distanceToLineSegment(point: Point, lineStart: Point, lineEnd: Point): Double {
        val dx = lineEnd.longitude() - lineStart.longitude()
        val dy = lineEnd.latitude() - lineStart.latitude()

        val lengthSquared = dx * dx + dy * dy

        if (lengthSquared == 0.0) {
            return calculateDistanceMeters(point, lineStart)
        }

        var t = ((point.longitude() - lineStart.longitude()) * dx +
                (point.latitude() - lineStart.latitude()) * dy) / lengthSquared
        t = t.coerceIn(0.0, 1.0)

        val closestLon = lineStart.longitude() + t * dx
        val closestLat = lineStart.latitude() + t * dy
        val closestPoint = Point.fromLngLat(closestLon, closestLat)

        return calculateDistanceMeters(point, closestPoint)
    }

    fun calculateRemainingDistance(
        currentLocation: Point,
        routeGeometry: List<Point>,
        currentIndex: Int
    ): Double {
        if (routeGeometry.isEmpty() || currentIndex < 0 || currentIndex >= routeGeometry.size) {
            return 0.0
        }

        var totalDistance = calculateDistanceMeters(currentLocation, routeGeometry[currentIndex])

        for (i in currentIndex until routeGeometry.size - 1) {
            totalDistance += calculateDistanceMeters(routeGeometry[i], routeGeometry[i + 1])
        }

        return totalDistance
    }

    fun normalizeBearing(bearing: Float): Float {
        var result = bearing % 360
        if (result < 0) result += 360
        return result
    }

    fun bearingDifference(bearing1: Float, bearing2: Float): Float {
        var diff = bearing2 - bearing1
        if (diff > 180) diff -= 360
        if (diff < -180) diff += 360
        return diff
    }

    fun isMovingTowards(point1: Point, point2: Point, bearing: Float): Boolean {
        val bearingToPoint = calculateBearing(point1, point2)
        val difference = abs(bearingDifference(bearing, bearingToPoint))
        return difference < 45
    }

    fun calculateETA(
        remainingDistanceMeters: Double,
        currentSpeedMps: Float
    ): Long {
        if (currentSpeedMps <= 0) return Long.MAX_VALUE
        return (remainingDistanceMeters / currentSpeedMps).toLong()
    }

    fun interpolatePoint(from: Point, to: Point, fraction: Float): Point {
        val lat = from.latitude() + (to.latitude() - from.latitude()) * fraction
        val lon = from.longitude() + (to.longitude() - from.longitude()) * fraction
        return Point.fromLngLat(lon, lat)
    }
}
