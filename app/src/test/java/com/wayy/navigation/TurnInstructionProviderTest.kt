package com.wayy.navigation

import com.wayy.data.model.Route
import com.wayy.data.model.RouteLeg
import com.wayy.data.model.RouteStep
import com.wayy.data.model.StepManeuver
import com.wayy.ui.components.navigation.Direction
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.maplibre.geojson.Point

class TurnInstructionProviderTest {

    private lateinit var provider: TurnInstructionProvider
    private lateinit var mockRoute: Route

    @Before
    fun setup() {
        provider = TurnInstructionProvider()
        mockRoute = createMockRoute()
    }

    private fun createMockRoute(): Route {
        return Route(
            legs = listOf(
                RouteLeg(
                    steps = listOf(
                        RouteStep(
                            distance = 500.0,
                            duration = 60.0,
                            instruction = "Turn right onto Main Street",
                            maneuver = StepManeuver(
                                location = Point.fromLngLat(-122.409, 37.785),
                                type = "turn",
                                modifier = "right"
                            ),
                            name = "Main Street"
                        ),
                        RouteStep(
                            distance = 1000.0,
                            duration = 120.0,
                            instruction = "Continue straight on Oak Avenue",
                            maneuver = StepManeuver(
                                location = Point.fromLngLat(-122.410, 37.786),
                                type = "continue",
                                modifier = null
                            ),
                            name = "Oak Avenue"
                        ),
                        RouteStep(
                            distance = 300.0,
                            duration = 30.0,
                            instruction = "Turn left onto Pine Road",
                            maneuver = StepManeuver(
                                location = Point.fromLngLat(-122.411, 37.787),
                                type = "turn",
                                modifier = "left"
                            ),
                            name = "Pine Road"
                        )
                    ),
                    distance = 1800.0,
                    duration = 210.0
                )
            ),
            geometry = listOf(
                Point.fromLngLat(-122.408, 37.784),
                Point.fromLngLat(-122.409, 37.785),
                Point.fromLngLat(-122.410, 37.786),
                Point.fromLngLat(-122.411, 37.787)
            ),
            distance = 1800.0,
            duration = 210.0
        )
    }

    @Test
    fun `getCurrentInstruction returns null for empty route`() {
        val emptyRoute = Route(legs = emptyList(), geometry = emptyList())
        val instruction = provider.getCurrentInstruction(
            Point.fromLngLat(0.0, 0.0),
            emptyRoute
        )
        assertNull(instruction)
    }

    @Test
    fun `getCurrentInstruction returns instruction for valid route`() {
        val location = Point.fromLngLat(-122.408, 37.784)
        val instruction = provider.getCurrentInstruction(location, mockRoute)
        assertNotNull(instruction)
        assertEquals(Direction.RIGHT, instruction?.direction)
    }

    @Test
    fun `getCurrentInstruction calculates correct distance`() {
        val location = Point.fromLngLat(-122.408, 37.784)
        val instruction = provider.getCurrentInstruction(location, mockRoute)
        assertTrue("Distance should be positive", (instruction?.distanceMeters ?: 0) > 0)
    }

    @Test
    fun `getCurrentInstruction returns null for invalid step index`() {
        val location = Point.fromLngLat(-122.408, 37.784)
        val instruction = provider.getCurrentInstruction(location, mockRoute, 100)
        assertNull(instruction)
    }

    @Test
    fun `getNextInstruction returns null when no next step`() {
        val lastIndex = (mockRoute.legs.first().steps.size - 1)
        val instruction = provider.getNextInstruction(mockRoute, lastIndex)
        assertNull(instruction)
    }

    @Test
    fun `getNextInstruction returns next step instruction`() {
        val instruction = provider.getNextInstruction(mockRoute, 0)
        assertNotNull(instruction)
        assertEquals(Direction.STRAIGHT, instruction?.direction)
    }

    @Test
    fun `findCurrentStepIndex returns 0 for start of route`() {
        val location = Point.fromLngLat(-122.408, 37.784)
        val index = provider.findCurrentStepIndex(location, mockRoute)
        assertEquals(0, index)
    }

    @Test
    fun `findCurrentStepIndex returns last index when past all steps`() {
        val location = Point.fromLngLat(-122.420, 37.790)
        val index = provider.findCurrentStepIndex(location, mockRoute)
        assertEquals(mockRoute.legs.first().steps.lastIndex, index)
    }

    @Test
    fun `shouldAdvanceStep returns true when close to maneuver`() {
        val currentStep = mockRoute.legs.first().steps[0]
        val location = currentStep.maneuver.location
        assertTrue(provider.shouldAdvanceStep(location, currentStep))
    }

    @Test
    fun `shouldAdvanceStep returns false when far from maneuver`() {
        val currentStep = mockRoute.legs.first().steps[0]
        val location = Point.fromLngLat(-100.0, 30.0)
        assertFalse(provider.shouldAdvanceStep(location, currentStep))
    }

    @Test
    fun `parseDirection maps turn right correctly`() {
        assertEquals(Direction.RIGHT, provider.parseDirection("turn", "right"))
    }

    @Test
    fun `parseDirection maps turn left correctly`() {
        assertEquals(Direction.LEFT, provider.parseDirection("turn", "left"))
    }

    @Test
    fun `parseDirection maps slight turns correctly`() {
        assertEquals(Direction.SLIGHT_LEFT, provider.parseDirection("turn", "slight left"))
        assertEquals(Direction.SLIGHT_RIGHT, provider.parseDirection("turn", "slight right"))
    }

    @Test
    fun `parseDirection maps u-turn correctly`() {
        assertEquals(Direction.U_TURN, provider.parseDirection("turn", "uturn"))
        assertEquals(Direction.U_TURN, provider.parseDirection("continue", "uturn"))
    }

    @Test
    fun `parseDirection maps arrive and depart as straight`() {
        assertEquals(Direction.STRAIGHT, provider.parseDirection("arrive", null))
        assertEquals(Direction.STRAIGHT, provider.parseDirection("depart", null))
    }

    @Test
    fun `parseDirection maps merge correctly`() {
        assertEquals(Direction.SLIGHT_LEFT, provider.parseDirection("merge", "left"))
        assertEquals(Direction.SLIGHT_RIGHT, provider.parseDirection("merge", "right"))
    }

    @Test
    fun `parseDirection maps fork correctly`() {
        assertEquals(Direction.SLIGHT_LEFT, provider.parseDirection("fork", "left"))
        assertEquals(Direction.SLIGHT_RIGHT, provider.parseDirection("fork", "right"))
    }

    @Test
    fun `parseDirection maps on ramp and off ramp correctly`() {
        assertEquals(Direction.LEFT, provider.parseDirection("on ramp", "left"))
        assertEquals(Direction.RIGHT, provider.parseDirection("off ramp", "right"))
    }

    @Test
    fun `parseDirection maps roundabout correctly`() {
        assertEquals(Direction.LEFT, provider.parseDirection("roundabout", "left"))
        assertEquals(Direction.RIGHT, provider.parseDirection("roundabout", "right"))
    }

    @Test
    fun `parseDirection returns straight for unknown maneuvers`() {
        assertEquals(Direction.STRAIGHT, provider.parseDirection("unknown", null))
    }

    @Test
    fun `formatVoiceText includes street name when available`() {
        val instruction = TurnInstruction(
            direction = Direction.RIGHT,
            distanceMeters = 200.0,
            distanceText = "200m",
            instruction = "Turn right",
            streetName = "Main Street",
            maneuverLocation = Point.fromLngLat(0.0, 0.0),
            stepIndex = 0
        )
        val voiceText = provider.formatVoiceText(instruction)
        assertTrue("Voice text should contain street name", voiceText.contains("Main Street"))
        assertTrue("Voice text should contain direction", voiceText.contains("turn right"))
    }

    @Test
    fun `formatVoiceText works without street name`() {
        val instruction = TurnInstruction(
            direction = Direction.LEFT,
            distanceMeters = 200.0,
            distanceText = "200m",
            instruction = "Turn left",
            streetName = "",
            maneuverLocation = Point.fromLngLat(0.0, 0.0),
            stepIndex = 0
        )
        val voiceText = provider.formatVoiceText(instruction)
        assertTrue("Voice text should contain direction", voiceText.contains("turn left"))
        assertFalse("Voice text should not have 'onto'", voiceText.contains("onto"))
    }

    @Test
    fun `getAnnouncementPriority returns IMMEDIATE for close turns`() {
        val instruction = TurnInstruction(
            direction = Direction.RIGHT,
            distanceMeters = 50.0,
            distanceText = "50m",
            instruction = "Turn right",
            streetName = "Main Street",
            maneuverLocation = Point.fromLngLat(0.0, 0.0),
            stepIndex = 0
        )
        assertEquals(AnnouncementPriority.IMMEDIATE, provider.getAnnouncementPriority(instruction))
    }

    @Test
    fun `getAnnouncementPriority returns UPCOMING for medium distance`() {
        val instruction = TurnInstruction(
            direction = Direction.RIGHT,
            distanceMeters = 200.0,
            distanceText = "200m",
            instruction = "Turn right",
            streetName = "Main Street",
            maneuverLocation = Point.fromLngLat(0.0, 0.0),
            stepIndex = 0
        )
        assertEquals(AnnouncementPriority.UPCOMING, provider.getAnnouncementPriority(instruction))
    }

    @Test
    fun `getAnnouncementPriority returns APPROACHING for further distance`() {
        val instruction = TurnInstruction(
            direction = Direction.RIGHT,
            distanceMeters = 500.0,
            distanceText = "500m",
            instruction = "Turn right",
            streetName = "Main Street",
            maneuverLocation = Point.fromLngLat(0.0, 0.0),
            stepIndex = 0
        )
        assertEquals(AnnouncementPriority.APPROACHING, provider.getAnnouncementPriority(instruction))
    }

    @Test
    fun `getAnnouncementPriority returns EARLY for distant turns`() {
        val instruction = TurnInstruction(
            direction = Direction.RIGHT,
            distanceMeters = 1000.0,
            distanceText = "1km",
            instruction = "Turn right",
            streetName = "Main Street",
            maneuverLocation = Point.fromLngLat(0.0, 0.0),
            stepIndex = 0
        )
        assertEquals(AnnouncementPriority.EARLY, provider.getAnnouncementPriority(instruction))
    }

    @Test
    fun `shouldAnnounce returns true for first announcement`() {
        val instruction = TurnInstruction(
            direction = Direction.RIGHT,
            distanceMeters = 500.0,
            distanceText = "500m",
            instruction = "Turn right",
            streetName = "Main Street",
            maneuverLocation = Point.fromLngLat(0.0, 0.0),
            stepIndex = 0
        )
        assertTrue(provider.shouldAnnounce(instruction, null, false))
    }

    @Test
    fun `shouldAnnounce returns false if recently announced same distance`() {
        val instruction = TurnInstruction(
            direction = Direction.RIGHT,
            distanceMeters = 500.0,
            distanceText = "500m",
            instruction = "Turn right",
            streetName = "Main Street",
            maneuverLocation = Point.fromLngLat(0.0, 0.0),
            stepIndex = 0
        )
        assertFalse(provider.shouldAnnounce(instruction, 500.0, false))
    }

    @Test
    fun `instruction is marked complete when very close to maneuver`() {
        val step = mockRoute.legs.first().steps[0]
        val instruction = TurnInstruction(
            direction = Direction.RIGHT,
            distanceMeters = 10.0,
            distanceText = "10m",
            instruction = "Turn right",
            streetName = "Main Street",
            maneuverLocation = step.maneuver.location,
            stepIndex = 0,
            isComplete = true
        )
        assertTrue(instruction.isComplete)
    }
}
