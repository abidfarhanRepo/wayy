package com.wayy.fixtures

import com.wayy.data.model.Maneuver
import com.wayy.data.model.Route
import com.wayy.data.model.RouteLeg
import com.wayy.data.model.RouteStep
import com.wayy.data.repository.LocalPoiItem
import org.maplibre.geojson.Point

object TestFixtures {
    val TEST_POINT_1: Point = Point.fromLngLat(51.5310, 25.2854)
    val TEST_POINT_2: Point = Point.fromLngLat(51.5400, 25.2900)

    fun createTestRoute(): Route {
        val step = RouteStep(
            instruction = "Turn left",
            distance = 1500.0,
            duration = 300.0,
            maneuver = Maneuver(
                type = "turn",
                modifier = "left",
                location = TEST_POINT_2,
                bearingBefore = 0,
                bearingAfter = 90
            ),
            geometry = listOf(TEST_POINT_1, TEST_POINT_2)
        )
        val leg = RouteLeg(
            steps = listOf(step),
            distance = 1500.0,
            duration = 300.0
        )
        return Route(
            geometry = listOf(TEST_POINT_1, TEST_POINT_2),
            duration = 300.0,
            distance = 1500.0,
            legs = listOf(leg)
        )
    }

    fun createTestPoi(timestamp: Long = System.currentTimeMillis()): LocalPoiItem {
        return LocalPoiItem(
            id = "test_poi_1",
            name = "Test Gas Station",
            category = "gas",
            lat = 25.2860,
            lng = 51.5315,
            timestamp = timestamp
        )
    }
}
