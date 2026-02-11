package com.wayy.navigation

import org.junit.Assert.*
import org.junit.Test
import org.maplibre.geojson.Point
import kotlin.math.abs

class NavigationUtilsTest {

    @Test
    fun `calculateDistanceMeters returns 0 for same point`() {
        val point = Point.fromLngLat(0.0, 0.0)
        val distance = NavigationUtils.calculateDistanceMeters(point, point)
        assertEquals(0.0, distance, 0.1)
    }

    @Test
    fun `calculateDistanceMeters calculates correct distance between known points`() {
        val nyc = Point.fromLngLat(-74.006, 40.7128)
        val la = Point.fromLngLat(-118.2437, 34.0522)
        val distance = NavigationUtils.calculateDistanceMeters(nyc, la)
        assertTrue("Distance should be ~3935km", distance in 3_930_000.0..3_945_000.0)
    }

    @Test
    fun `calculateDistanceMeters handles short distances`() {
        val point1 = Point.fromLngLat(0.0, 0.0)
        val point2 = Point.fromLngLat(0.001, 0.001)
        val distance = NavigationUtils.calculateDistanceMeters(point1, point2)
        assertTrue("Short distance should be ~157m", distance in 100.0..200.0)
    }

    @Test
    fun `calculateBearing returns correct cardinal directions`() {
        val center = Point.fromLngLat(0.0, 0.0)
        val north = Point.fromLngLat(0.0, 1.0)
        val east = Point.fromLngLat(1.0, 0.0)
        val south = Point.fromLngLat(0.0, -1.0)
        val west = Point.fromLngLat(-1.0, 0.0)

        assertEquals(0f, NavigationUtils.calculateBearing(center, north), 5f)
        assertEquals(90f, NavigationUtils.calculateBearing(center, east), 5f)
        assertEquals(180f, NavigationUtils.calculateBearing(center, south), 5f)
        assertEquals(270f, NavigationUtils.calculateBearing(center, west), 5f)
    }

    @Test
    fun `calculateBearing handles same point`() {
        val point = Point.fromLngLat(10.0, 20.0)
        val bearing = NavigationUtils.calculateBearing(point, point)
        assertTrue("Bearing should be valid (0-360)", bearing in 0f..360f)
    }

    @Test
    fun `formatDistance formats meters correctly`() {
        assertEquals("500m", NavigationUtils.formatDistance(500.0))
        assertEquals("999m", NavigationUtils.formatDistance(999.0))
    }

    @Test
    fun `formatDistance converts to miles for longer distances`() {
        val result1609 = NavigationUtils.formatDistance(1609.34)
        assertTrue("1 mile should show as ~1.0 mi", result1609.contains("mi"))
    }

    @Test
    fun `formatDuration formats minutes correctly`() {
        assertEquals("5m", NavigationUtils.formatDuration(300))
        assertEquals("0m", NavigationUtils.formatDuration(0))
        assertEquals("59m", NavigationUtils.formatDuration(3540))
    }

    @Test
    fun `formatDuration formats hours and minutes correctly`() {
        assertEquals("1h", NavigationUtils.formatDuration(3600))
        assertEquals("1h 30m", NavigationUtils.formatDuration(5400))
        assertEquals("2h 15m", NavigationUtils.formatDuration(8100))
    }

    @Test
    fun `formatSpeed converts mps to mph`() {
        val result = NavigationUtils.formatSpeed(10f)
        assertTrue("10 m/s should be ~22 mph", result.contains("22"))
    }

    @Test
    fun `isPointOnRoute returns true when point is on route`() {
        val route = listOf(
            Point.fromLngLat(0.0, 0.0),
            Point.fromLngLat(0.001, 0.001),
            Point.fromLngLat(0.002, 0.002)
        )
        val point = Point.fromLngLat(0.001, 0.001)
        assertTrue(NavigationUtils.isPointOnRoute(point, route))
    }

    @Test
    fun `isPointOnRoute returns false when point is far from route`() {
        val route = listOf(
            Point.fromLngLat(0.0, 0.0),
            Point.fromLngLat(0.001, 0.001)
        )
        val point = Point.fromLngLat(5.0, 5.0)
        assertFalse(NavigationUtils.isPointOnRoute(point, route, 50.0))
    }

    @Test
    fun `isPointOnRoute returns false for empty route`() {
        val point = Point.fromLngLat(0.0, 0.0)
        assertFalse(NavigationUtils.isPointOnRoute(point, emptyList()))
    }

    @Test
    fun `findClosestPointOnRoute returns correct index and distance`() {
        val route = listOf(
            Point.fromLngLat(0.0, 0.0),
            Point.fromLngLat(0.001, 0.001),
            Point.fromLngLat(0.002, 0.002)
        )
        val point = Point.fromLngLat(0.0015, 0.0015)
        val (index, distance) = NavigationUtils.findClosestPointOnRoute(point, route)
        assertEquals(1, index)
        assertTrue("Distance should be small", distance < 100)
    }

    @Test
    fun `findClosestPointOnRoute handles empty route`() {
        val point = Point.fromLngLat(0.0, 0.0)
        val (index, distance) = NavigationUtils.findClosestPointOnRoute(point, emptyList())
        assertEquals(-1, index)
        assertEquals(Double.MAX_VALUE, distance, 0.0)
    }

    @Test
    fun `normalizeBearing normalizes negative bearings`() {
        assertEquals(270f, NavigationUtils.normalizeBearing(-90f), 0.1f)
        assertEquals(180f, NavigationUtils.normalizeBearing(-180f), 0.1f)
        assertEquals(0f, NavigationUtils.normalizeBearing(-360f), 0.1f)
    }

    @Test
    fun `normalizeBearing normalizes bearings over 360`() {
        assertEquals(90f, NavigationUtils.normalizeBearing(450f), 0.1f)
        assertEquals(0f, NavigationUtils.normalizeBearing(360f), 0.1f)
    }

    @Test
    fun `bearingDifference calculates correct differences`() {
        assertEquals(90f, NavigationUtils.bearingDifference(0f, 90f), 0.1f)
        assertEquals(-90f, NavigationUtils.bearingDifference(90f, 0f), 0.1f)
        assertEquals(180f, NavigationUtils.bearingDifference(0f, 180f), 0.1f)
        assertEquals(-180f, NavigationUtils.bearingDifference(180f, 0f), 0.1f)
    }

    @Test
    fun `bearingDifference wraps around correctly`() {
        assertEquals(90f, NavigationUtils.bearingDifference(270f, 0f), 0.1f)
        assertEquals(-90f, NavigationUtils.bearingDifference(0f, 270f), 0.1f)
    }

    @Test
    fun `calculateRemainingDistance calculates correctly`() {
        val route = listOf(
            Point.fromLngLat(0.0, 0.0),
            Point.fromLngLat(0.01, 0.01),
            Point.fromLngLat(0.02, 0.02)
        )
        val currentLocation = Point.fromLngLat(0.01, 0.01)
        val distance = NavigationUtils.calculateRemainingDistance(currentLocation, route, 1)
        assertTrue("Should have some remaining distance", distance > 0)
    }

    @Test
    fun `calculateRemainingDistance returns 0 for invalid index`() {
        val route = listOf(
            Point.fromLngLat(0.0, 0.0),
            Point.fromLngLat(0.01, 0.01)
        )
        val currentLocation = Point.fromLngLat(0.01, 0.01)
        assertEquals(0.0, NavigationUtils.calculateRemainingDistance(currentLocation, route, -1), 0.1)
        assertEquals(0.0, NavigationUtils.calculateRemainingDistance(currentLocation, route, 10), 0.1)
    }

    @Test
    fun `isMovingTowards returns true when bearing matches direction`() {
        val from = Point.fromLngLat(0.0, 0.0)
        val to = Point.fromLngLat(0.0, 1.0)
        assertTrue(NavigationUtils.isMovingTowards(from, to, 0f))
    }

    @Test
    fun `isMovingTowards returns false when bearing is opposite`() {
        val from = Point.fromLngLat(0.0, 0.0)
        val to = Point.fromLngLat(0.0, 1.0)
        assertFalse(NavigationUtils.isMovingTowards(from, to, 180f))
    }

    @Test
    fun `calculateETA returns reasonable time`() {
        val eta = NavigationUtils.calculateETA(1000.0, 10f)
        assertEquals(100L, eta)
    }

    @Test
    fun `calculateETA returns MAX_VALUE for zero speed`() {
        val eta = NavigationUtils.calculateETA(1000.0, 0f)
        assertEquals(Long.MAX_VALUE, eta)
    }

    @Test
    fun `interpolatePoint creates point between two locations`() {
        val from = Point.fromLngLat(0.0, 0.0)
        val to = Point.fromLngLat(10.0, 10.0)
        val mid = NavigationUtils.interpolatePoint(from, to, 0.5f)
        assertEquals(5.0, mid.longitude(), 0.01)
        assertEquals(5.0, mid.latitude(), 0.01)
    }

    @Test
    fun `distanceToLineSegment returns 0 for point on line`() {
        val start = Point.fromLngLat(0.0, 0.0)
        val end = Point.fromLngLat(0.01, 0.0)
        val point = Point.fromLngLat(0.005, 0.0)
        val distance = NavigationUtils.distanceToLineSegment(point, start, end)
        assertTrue("Point on line should have near 0 distance", distance < 10)
    }

    @Test
    fun `distanceToLineSegment handles point beyond segment end`() {
        val start = Point.fromLngLat(0.0, 0.0)
        val end = Point.fromLngLat(0.01, 0.0)
        val point = Point.fromLngLat(0.02, 0.0)
        val distance = NavigationUtils.distanceToLineSegment(point, start, end)
        assertTrue("Distance should be calculated from segment end", distance > 0)
    }
}
