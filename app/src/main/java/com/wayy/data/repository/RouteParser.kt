package com.wayy.data.repository

import com.google.gson.JsonParser
import com.wayy.data.model.*
import org.maplibre.geojson.Point

object RouteParser {

    fun parseRouteResponse(jsonString: String): Route? {
        return try {
            val json = JsonParser.parseString(jsonString).asJsonObject

            val code = json.get("code")?.asString
            if (code != "Ok") return null

            val routesArray = json.getAsJsonArray("routes")
            if (routesArray == null || routesArray.size() == 0) return null

            val firstRoute = routesArray[0].asJsonObject
            parseOSRMRoute(firstRoute)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseOSRMRoute(routeJson: com.google.gson.JsonObject): Route {
        val distance = routeJson.get("distance")?.asDouble ?: 0.0
        val duration = routeJson.get("duration")?.asDouble ?: 0.0
        val geometryStr = routeJson.get("geometry")?.asString ?: ""
        val geometry = PolylineDecoder.decode(geometryStr)

        val legsArray = routeJson.getAsJsonArray("legs")
        val legs = if (legsArray != null) {
            legsArray.map { legElement ->
                parseOSRMLeg(legElement.asJsonObject)
            }
        } else {
            emptyList()
        }

        return Route(
            geometry = geometry,
            duration = duration,
            distance = distance,
            legs = legs
        )
    }

    private fun parseOSRMLeg(legJson: com.google.gson.JsonObject): RouteLeg {
        val distance = legJson.get("distance")?.asDouble ?: 0.0
        val duration = legJson.get("duration")?.asDouble ?: 0.0

        val stepsArray = legJson.getAsJsonArray("steps")
        val steps = if (stepsArray != null) {
            stepsArray.map { stepElement ->
                parseOSRMStep(stepElement.asJsonObject)
            }
        } else {
            emptyList()
        }

        return RouteLeg(
            steps = steps,
            distance = distance,
            duration = duration
        )
    }

    private fun parseOSRMStep(stepJson: com.google.gson.JsonObject): RouteStep {
        val distance = stepJson.get("distance")?.asDouble ?: 0.0
        val duration = stepJson.get("duration")?.asDouble ?: 0.0
        val name = stepJson.get("name")?.asString ?: ""
        val geometryStr = stepJson.get("geometry")?.asString ?: ""
        val geometry = PolylineDecoder.decode(geometryStr)

        val maneuverJson = stepJson.getAsJsonObject("maneuver")
        val maneuver = parseOSRMManeuver(maneuverJson)

        val instruction = buildInstruction(maneuver, name)

        return RouteStep(
            instruction = instruction,
            distance = distance,
            duration = duration,
            maneuver = maneuver,
            geometry = geometry
        )
    }

    private fun parseOSRMManeuver(maneuverJson: com.google.gson.JsonObject): Maneuver {
        val type = maneuverJson.get("type")?.asString ?: "turn"
        val modifier = maneuverJson.get("modifier")?.asString
        val bearingBefore = maneuverJson.get("bearing_before")?.asInt ?: 0
        val bearingAfter = maneuverJson.get("bearing_after")?.asInt ?: 0

        val locationArray = maneuverJson.getAsJsonArray("location")
        val location = if (locationArray != null && locationArray.size() >= 2) {
            val lon = locationArray[0].asDouble
            val lat = locationArray[1].asDouble
            Point.fromLngLat(lon, lat)
        } else {
            Point.fromLngLat(0.0, 0.0)
        }

        return Maneuver(
            type = type,
            modifier = modifier,
            location = location,
            bearingBefore = bearingBefore,
            bearingAfter = bearingAfter
        )
    }

    private fun buildInstruction(maneuver: Maneuver, streetName: String): String {
        val directionText = when (maneuver.modifier) {
            "left" -> "left"
            "right" -> "right"
            "slight left" -> "slight left"
            "slight right" -> "slight right"
            "sharp left" -> "sharp left"
            "sharp right" -> "sharp right"
            "uturn" -> "U-turn"
            "straight" -> "straight"
            else -> ""
        }

        val actionText = when (maneuver.type) {
            "turn" -> if (directionText.isNotEmpty()) "Turn $directionText" else "Turn"
            "continue" -> if (directionText.isNotEmpty()) "Continue $directionText" else "Continue"
            "merge" -> "Merge ${directionText.ifEmpty { "ahead" }}"
            "on ramp" -> "Take ramp $directionText"
            "off ramp" -> "Take exit $directionText"
            "fork" -> "Keep ${directionText.ifEmpty { "straight" }}"
            "arrive" -> "Arrive at destination"
            "depart" -> "Depart"
            "roundabout" -> "Enter roundabout"
            "exit roundabout" -> "Exit roundabout"
            "rotary" -> "Enter roundabout"
            "exit rotary" -> "Exit roundabout"
            else -> maneuver.type.replace("_", " ").replaceFirstChar { it.uppercase() }
        }

        return if (streetName.isNotEmpty() && maneuver.type != "arrive") {
            "$actionText onto $streetName"
        } else {
            actionText
        }
    }

    fun extractRouteSummary(route: Route): RouteSummary {
        return RouteSummary(
            totalDistance = route.distance,
            totalDuration = route.duration,
            totalSteps = route.legs.sumOf { it.steps.size },
            hasHighway = hasHighway(route),
            hasFerry = hasFerry(route)
        )
    }

    private fun hasHighway(route: Route): Boolean {
        return route.legs.any { leg ->
            leg.steps.any { step ->
                step.distance > 5000 && step.instruction.contains("highway", ignoreCase = true)
            }
        }
    }

    private fun hasFerry(route: Route): Boolean {
        return route.legs.any { leg ->
            leg.steps.any { step ->
                step.maneuver.type == "ferry"
            }
        }
    }
}

data class RouteSummary(
    val totalDistance: Double,
    val totalDuration: Double,
    val totalSteps: Int,
    val hasHighway: Boolean,
    val hasFerry: Boolean
)
