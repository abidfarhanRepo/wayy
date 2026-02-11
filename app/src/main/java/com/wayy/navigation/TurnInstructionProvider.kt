package com.wayy.navigation

import com.wayy.data.model.Route
import com.wayy.data.model.RouteStep
import com.wayy.ui.components.navigation.Direction
import org.maplibre.geojson.Point

data class TurnInstruction(
    val direction: Direction,
    val distanceMeters: Double,
    val distanceText: String,
    val instruction: String,
    val streetName: String,
    val maneuverLocation: Point,
    val stepIndex: Int,
    val isComplete: Boolean = false
)

class TurnInstructionProvider {

    fun getCurrentInstruction(
        currentLocation: Point,
        route: Route,
        currentStepIndex: Int = 0
    ): TurnInstruction? {
        val steps = route.legs.firstOrNull()?.steps ?: return null
        if (currentStepIndex >= steps.size) return null

        val currentStep = steps[currentStepIndex]
        val distanceToManeuver = if (currentStep.geometry.isNotEmpty()) {
            val (closestIndex, _) = NavigationUtils.findClosestPointOnRoute(
                currentLocation,
                currentStep.geometry
            )
            NavigationUtils.calculateRemainingDistance(
                currentLocation,
                currentStep.geometry,
                closestIndex
            )
        } else {
            NavigationUtils.calculateDistanceMeters(
                currentLocation,
                currentStep.maneuver.location
            )
        }

        return TurnInstruction(
            direction = parseDirection(currentStep.maneuver.type, currentStep.maneuver.modifier),
            distanceMeters = distanceToManeuver,
            distanceText = NavigationUtils.formatDistance(distanceToManeuver),
            instruction = currentStep.instruction,
            streetName = extractStreetName(currentStep),
            maneuverLocation = currentStep.maneuver.location,
            stepIndex = currentStepIndex,
            isComplete = distanceToManeuver < 15
        )
    }

    fun getNextInstruction(route: Route, currentStepIndex: Int = 0): TurnInstruction? {
        val steps = route.legs.firstOrNull()?.steps ?: return null
        val nextIndex = currentStepIndex + 1
        if (nextIndex >= steps.size) return null

        val nextStep = steps[nextIndex]
        return TurnInstruction(
            direction = parseDirection(nextStep.maneuver.type, nextStep.maneuver.modifier),
            distanceMeters = nextStep.distance,
            distanceText = NavigationUtils.formatDistance(nextStep.distance),
            instruction = nextStep.instruction,
            streetName = extractStreetName(nextStep),
            maneuverLocation = nextStep.maneuver.location,
            stepIndex = nextIndex
        )
    }

    fun findCurrentStepIndex(
        currentLocation: Point,
        route: Route,
        previousIndex: Int = 0
    ): Int {
        val steps = route.legs.firstOrNull()?.steps ?: return 0
        if (steps.isEmpty()) return 0

        for (i in previousIndex until steps.size) {
            val step = steps[i]
            val distanceToManeuver = NavigationUtils.calculateDistanceMeters(
                currentLocation,
                step.maneuver.location
            )

            if (distanceToManeuver > 30) {
                return i
            }
        }

        return steps.lastIndex
    }

    fun shouldAdvanceStep(
        currentLocation: Point,
        currentStep: RouteStep,
        thresholdMeters: Double = 20.0
    ): Boolean {
        val distanceToManeuver = NavigationUtils.calculateDistanceMeters(
            currentLocation,
            currentStep.maneuver.location
        )
        return distanceToManeuver <= thresholdMeters
    }

    fun parseDirection(maneuverType: String, modifier: String?): Direction {
        return when (maneuverType) {
            "arrive" -> Direction.STRAIGHT
            "depart" -> Direction.STRAIGHT
            "continue" -> when (modifier) {
                "left" -> Direction.SLIGHT_LEFT
                "right" -> Direction.SLIGHT_RIGHT
                "uturn" -> Direction.U_TURN
                else -> Direction.STRAIGHT
            }
            "turn" -> when (modifier) {
                "left" -> Direction.LEFT
                "right" -> Direction.RIGHT
                "slight left" -> Direction.SLIGHT_LEFT
                "slight right" -> Direction.SLIGHT_RIGHT
                "sharp left" -> Direction.LEFT
                "sharp right" -> Direction.RIGHT
                "uturn" -> Direction.U_TURN
                else -> Direction.STRAIGHT
            }
            "merge" -> when (modifier) {
                "left" -> Direction.SLIGHT_LEFT
                "right" -> Direction.SLIGHT_RIGHT
                else -> Direction.STRAIGHT
            }
            "on ramp", "off ramp" -> when (modifier) {
                "left" -> Direction.LEFT
                "right" -> Direction.RIGHT
                "slight left" -> Direction.SLIGHT_LEFT
                "slight right" -> Direction.SLIGHT_RIGHT
                else -> Direction.SLIGHT_RIGHT
            }
            "fork" -> when (modifier) {
                "left" -> Direction.SLIGHT_LEFT
                "right" -> Direction.SLIGHT_RIGHT
                else -> Direction.STRAIGHT
            }
            "roundabout" -> when (modifier) {
                "left" -> Direction.LEFT
                "right" -> Direction.RIGHT
                else -> Direction.STRAIGHT
            }
            "rotary" -> when (modifier) {
                "left" -> Direction.LEFT
                "right" -> Direction.RIGHT
                else -> Direction.STRAIGHT
            }
            "roundabout turn" -> when (modifier) {
                "left" -> Direction.LEFT
                "right" -> Direction.RIGHT
                else -> Direction.STRAIGHT
            }
            "notification" -> Direction.STRAIGHT
            "exit roundabout" -> Direction.STRAIGHT
            "exit rotary" -> Direction.STRAIGHT
            else -> Direction.STRAIGHT
        }
    }

    private fun extractStreetName(step: RouteStep): String {
        return step.instruction
            .replace(Regex("^(Turn |Continue |Bear |Make a |Head |Merge |Take |Keep )"), "")
            .replace(Regex("(left|right|straight|u-turn)\\s*$", RegexOption.IGNORE_CASE), "")
            .trim()
            .takeIf { it.isNotBlank() && it != step.maneuver.type }
            ?: ""
    }

    fun formatVoiceText(instruction: TurnInstruction): String {
        val distanceVoice = formatDistanceVoice(instruction.distanceMeters)
        val directionVoice = formatDirectionVoice(instruction.direction)

        return if (instruction.streetName.isNotEmpty()) {
            "In $distanceVoice, $directionVoice onto ${instruction.streetName}"
        } else {
            "In $distanceVoice, $directionVoice"
        }
    }

    private fun formatDistanceVoice(meters: Double): String {
        val feet = meters * 3.28084
        return when {
            feet < 100 -> "50 feet"
            feet < 200 -> "100 feet"
            feet < 500 -> "${(feet / 100).toInt() * 100} feet"
            feet < 1320 -> "quarter mile"
            feet < 2640 -> "half mile"
            feet < 5280 -> "${(feet / 528).toInt() / 2.0} miles"
            else -> "${(feet / 5280).toInt()} miles"
        }.replace(".0", "")
    }

    private fun formatDirectionVoice(direction: Direction): String {
        return when (direction) {
            Direction.STRAIGHT -> "continue straight"
            Direction.LEFT -> "turn left"
            Direction.RIGHT -> "turn right"
            Direction.U_TURN -> "make a U-turn"
            Direction.SLIGHT_LEFT -> "bear left"
            Direction.SLIGHT_RIGHT -> "bear right"
        }
    }

    fun getAnnouncementPriority(instruction: TurnInstruction): AnnouncementPriority {
        return when {
            instruction.distanceMeters < 100 -> AnnouncementPriority.IMMEDIATE
            instruction.distanceMeters < 300 -> AnnouncementPriority.UPCOMING
            instruction.distanceMeters < 800 -> AnnouncementPriority.APPROACHING
            else -> AnnouncementPriority.EARLY
        }
    }

    fun shouldAnnounce(
        instruction: TurnInstruction,
        lastAnnouncedDistance: Double?,
        isImmediateAnnounced: Boolean
    ): Boolean {
        val priority = getAnnouncementPriority(instruction)

        return when (priority) {
            AnnouncementPriority.EARLY -> lastAnnouncedDistance == null || lastAnnouncedDistance > 800
            AnnouncementPriority.APPROACHING -> lastAnnouncedDistance == null || lastAnnouncedDistance > 300
            AnnouncementPriority.UPCOMING -> lastAnnouncedDistance == null || lastAnnouncedDistance > 100
            AnnouncementPriority.IMMEDIATE -> !isImmediateAnnounced && instruction.distanceMeters < 50
        }
    }
}

enum class AnnouncementPriority {
    EARLY,
    APPROACHING,
    UPCOMING,
    IMMEDIATE
}
