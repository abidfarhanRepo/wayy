package com.wayy.navigation

import com.wayy.fixtures.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.geojson.Point
import kotlin.math.abs

class NavigationUtilsTest {

    @Test
    fun calculateDistanceMeters_samePoint_returnsZero() {
        val point = TestFixtures.TEST_POINT_1
        assertEquals(0.0, NavigationUtils.calculateDistanceMeters(point, point), 0.0)
    }

    @Test
    fun calculateDistanceMeters_knownCoordinates_returnsExpectedRange() {
        val point1 = TestFixtures.TEST_POINT_1
        val point2 = TestFixtures.TEST_POINT_2
        val distance = NavigationUtils.calculateDistanceMeters(point1, point2)
        assertTrue(distance in 1000.0..1100.0)
    }

    @Test
    fun calculateBearing_northSouth_returns180() {
        val north = Point.fromLngLat(0.0, 1.0)
        val south = Point.fromLngLat(0.0, 0.0)
        val bearing = NavigationUtils.calculateBearing(north, south)
        assertEquals(180f, bearing, 0.5f)
    }

    @Test
    fun bearingDifference_wrapsCorrectly() {
        val diff = NavigationUtils.bearingDifference(350f, 10f)
        assertEquals(20f, diff, 0.5f)
    }

    @Test
    fun formatDistance_meters() {
        assertEquals("500 m", NavigationUtils.formatDistance(500.0))
    }

    @Test
    fun formatDistance_kilometers() {
        assertEquals("2.5 km", NavigationUtils.formatDistance(2500.0))
    }

    @Test
    fun formatDuration_minutes() {
        assertEquals("1m", NavigationUtils.formatDuration(90.0))
    }

    @Test
    fun formatDuration_hoursMinutes() {
        assertEquals("1h 1m", NavigationUtils.formatDuration(3700.0))
    }

    @Test
    fun isPointOnRoute_trueWhenOnLine() {
        val geometry = listOf(
            Point.fromLngLat(0.0, 0.0),
            Point.fromLngLat(0.0, 0.01)
        )
        val onLine = Point.fromLngLat(0.0, 0.005)
        assertTrue(NavigationUtils.isPointOnRoute(onLine, geometry, 50.0))
    }

    @Test
    fun isPointOnRoute_falseWhenFar() {
        val geometry = listOf(
            Point.fromLngLat(0.0, 0.0),
            Point.fromLngLat(0.0, 0.01)
        )
        val offLine = Point.fromLngLat(0.01, 0.01)
        assertFalse(NavigationUtils.isPointOnRoute(offLine, geometry, 50.0))
    }
}
