package com.wayy.navigation

import com.wayy.data.model.Route
import com.wayy.data.model.RouteLeg
import com.wayy.data.repository.RouteRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.maplibre.geojson.Point

class RerouteUtilsTest {

    private lateinit var rerouteUtils: RerouteUtils
    private lateinit var mockRoute: Route

    @Before
    fun setup() {
        rerouteUtils = RerouteUtils()
        mockRoute = createMockRoute()
    }

    private fun createMockRoute(): Route {
        return Route(
            legs = listOf(
                RouteLeg(
                    steps = emptyList(),
                    distance = 1000.0,
                    duration = 60.0
                )
            ),
            geometry = listOf(
                Point.fromLngLat(-122.408, 37.784),
                Point.fromLngLat(-122.409, 37.785),
                Point.fromLngLat(-122.410, 37.786),
                Point.fromLngLat(-122.411, 37.787)
            ),
            distance = 1000.0,
            duration = 60.0
        )
    }

    @Test
    fun `checkIfOffRoute returns true when far from route`() {
        val location = Point.fromLngLat(0.0, 0.0)
        assertTrue(rerouteUtils.checkIfOffRoute(location, mockRoute))
    }

    @Test
    fun `checkIfOffRoute returns false when on route`() {
        val location = Point.fromLngLat(-122.409, 37.785)
        assertFalse(rerouteUtils.checkIfOffRoute(location, mockRoute))
    }

    @Test
    fun `checkIfOffRoute returns true for empty route geometry`() {
        val emptyRoute = Route(legs = emptyList(), geometry = emptyList())
        val location = Point.fromLngLat(0.0, 0.0)
        assertTrue(rerouteUtils.checkIfOffRoute(location, emptyRoute))
    }

    @Test
    fun `checkIfOffRoute uses custom threshold`() {
        val location = Point.fromLngLat(-122.415, 37.788)
        assertTrue(rerouteUtils.checkIfOffRoute(location, mockRoute, 50.0))
        assertFalse(rerouteUtils.checkIfOffRoute(location, mockRoute, 5000.0))
    }

    @Test
    fun `getDistanceFromRoute returns distance to closest point`() {
        val location = Point.fromLngLat(-122.409, 37.785)
        val distance = rerouteUtils.getDistanceFromRoute(location, mockRoute)
        assertTrue("Distance should be small when on route", distance < 50)
    }

    @Test
    fun `getDistanceFromRoute returns MAX_VALUE for empty route`() {
        val emptyRoute = Route(legs = emptyList(), geometry = emptyList())
        val location = Point.fromLngLat(0.0, 0.0)
        assertEquals(Double.MAX_VALUE, rerouteUtils.getDistanceFromRoute(location, emptyRoute), 0.0)
    }

    @Test
    fun `getAdaptiveThreshold returns base threshold for zero speed`() {
        val threshold = rerouteUtils.getAdaptiveThreshold(0f)
        assertEquals(50.0, threshold, 0.1)
    }

    @Test
    fun `getAdaptiveThreshold increases with speed`() {
        val lowSpeedThreshold = rerouteUtils.getAdaptiveThreshold(10f)
        val highSpeedThreshold = rerouteUtils.getAdaptiveThreshold(30f)
        assertTrue("Higher speed should have higher threshold", highSpeedThreshold >= lowSpeedThreshold)
    }

    @Test
    fun `getAdaptiveThreshold caps at maximum`() {
        val threshold = rerouteUtils.getAdaptiveThreshold(100f)
        assertTrue("Threshold should not exceed maximum", threshold <= 100.0)
    }

    @Test
    fun `isApproachingDestination returns true when close`() {
        val current = Point.fromLngLat(-122.411, 37.787)
        val destination = Point.fromLngLat(-122.411, 37.787)
        assertTrue(rerouteUtils.isApproachingDestination(current, destination))
    }

    @Test
    fun `isApproachingDestination returns false when far`() {
        val current = Point.fromLngLat(-122.408, 37.784)
        val destination = Point.fromLngLat(-122.411, 37.787)
        assertFalse(rerouteUtils.isApproachingDestination(current, destination))
    }

    @Test
    fun `isApproachingDestination uses custom threshold`() {
        val current = Point.fromLngLat(-122.408, 37.784)
        val destination = Point.fromLngLat(-122.411, 37.787)
        assertFalse(rerouteUtils.isApproachingDestination(current, destination, 30.0))
        assertTrue(rerouteUtils.isApproachingDestination(current, destination, 10000.0))
    }

    @Test
    fun `resetState clears tracking variables`() = runBlocking {
        val offRouteLocation = Point.fromLngLat(-122.408, 37.784)
        val destination = Point.fromLngLat(-122.411, 37.787)
        
        repeat(4) {
            rerouteUtils.checkAndReroute(Point.fromLngLat(0.0, 0.0), mockRoute, destination)
        }
        
        assertNotNull(rerouteUtils.getLastOffRouteLocation())
        
        rerouteUtils.resetState()
        
        assertNull(rerouteUtils.getLastOffRouteLocation())
    }

    @Test
    fun `checkAndReroute returns NotNeeded when on route`() = runBlocking {
        val location = Point.fromLngLat(-122.409, 37.785)
        val destination = Point.fromLngLat(-122.411, 37.787)
        val result = rerouteUtils.checkAndReroute(location, mockRoute, destination)
        assertTrue(result is RerouteResult.NotNeeded)
    }

    @Test
    fun `checkAndReroute requires multiple off-route confirmations`() = runBlocking {
        val offRouteLocation = Point.fromLngLat(0.0, 0.0)
        val destination = Point.fromLngLat(-122.411, 37.787)
        
        val result1 = rerouteUtils.checkAndReroute(offRouteLocation, mockRoute, destination)
        assertTrue("First check should be NotNeeded", result1 is RerouteResult.NotNeeded)
        
        val result2 = rerouteUtils.checkAndReroute(offRouteLocation, mockRoute, destination)
        assertTrue("Second check should be NotNeeded", result2 is RerouteResult.NotNeeded)
    }

    @Test
    fun `getLastOffRouteLocation returns null initially`() {
        assertNull(rerouteUtils.getLastOffRouteLocation())
    }

    @Test
    fun `getLastOffRouteLocation returns location after off-route detected`() = runBlocking {
        val offRouteLocation = Point.fromLngLat(0.0, 0.0)
        val destination = Point.fromLngLat(-122.411, 37.787)
        
        rerouteUtils.checkAndReroute(offRouteLocation, mockRoute, destination)
        
        assertNotNull(rerouteUtils.getLastOffRouteLocation())
    }

    @Test
    fun `consecutive off-route count resets when back on route`() = runBlocking {
        val offRouteLocation = Point.fromLngLat(0.0, 0.0)
        val onRouteLocation = Point.fromLngLat(-122.409, 37.785)
        val destination = Point.fromLngLat(-122.411, 37.787)
        
        rerouteUtils.checkAndReroute(offRouteLocation, mockRoute, destination)
        rerouteUtils.checkAndReroute(offRouteLocation, mockRoute, destination)
        
        rerouteUtils.checkAndReroute(onRouteLocation, mockRoute, destination)
        
        rerouteUtils.resetState()
        val result = rerouteUtils.checkAndReroute(offRouteLocation, mockRoute, destination)
        assertTrue("Count should have reset", result is RerouteResult.NotNeeded)
    }
}
