package com.wayy.ui.components

import com.wayy.ui.components.navigation.LaneDirection
import com.wayy.ui.components.navigation.LaneGuidanceFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class LaneGuidanceFactoryTest {

    @Test
    fun createStandardLanes_threeLanes() {
        val lanes = LaneGuidanceFactory.createStandardLanes(LaneDirection.STRAIGHT, 3)
        assertEquals(3, lanes.size)
        assertEquals(true, lanes[1].isActive)
    }
}
