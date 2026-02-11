package com.wayy.navigation

import com.wayy.ui.components.navigation.Direction
import org.junit.Assert.assertEquals
import org.junit.Test

class TurnInstructionProviderTest {

    @Test
    fun parseDirection_turnLeft() {
        val provider = TurnInstructionProvider()
        val direction = provider.parseDirection("turn", "left")
        assertEquals(Direction.LEFT, direction)
    }

    @Test
    fun parseDirection_continueRight() {
        val provider = TurnInstructionProvider()
        val direction = provider.parseDirection("continue", "right")
        assertEquals(Direction.SLIGHT_RIGHT, direction)
    }

    @Test
    fun parseDirection_forkStraight() {
        val provider = TurnInstructionProvider()
        val direction = provider.parseDirection("fork", null)
        assertEquals(Direction.STRAIGHT, direction)
    }
}
