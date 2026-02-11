package com.wayy.navigation

import com.wayy.fixtures.TestFixtures
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.geojson.Point

class RerouteUtilsTest {

    @Test
    fun checkIfOffRoute_trueWhenFar() {
        val rerouteUtils = RerouteUtils()
        val route = TestFixtures.createTestRoute()
        val offRoute = Point.fromLngLat(51.5600, 25.3100)
        assertTrue(rerouteUtils.checkIfOffRoute(offRoute, route, 50.0))
    }

    @Test
    fun checkIfOffRoute_falseWhenOnRoute() {
        val rerouteUtils = RerouteUtils()
        val route = TestFixtures.createTestRoute()
        val onRoute = TestFixtures.TEST_POINT_1
        assertFalse(rerouteUtils.checkIfOffRoute(onRoute, route, 200.0))
    }

    @Test
    fun getAdaptiveThreshold_capsAtMax() {
        val rerouteUtils = RerouteUtils()
        val threshold = rerouteUtils.getAdaptiveThreshold(50f)
        assertTrue(threshold <= 100.0)
    }
}
