package com.vrgames.jiguan.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Device 2026-11-14: the control post must answer a push of the hand, not only a pinch on its small arrows. */
class StickDragTest {
    @Test
    fun aPushSidewaysTurnsAndUpOrDownLifts() {
        val s = StickDrag()
        s.start()
        assertNull(s.by(0.02, 0.0))
        assertEquals(StickDrag.Move.TURN_RIGHT, s.by(0.02, 0.005))
        s.start()
        assertEquals(StickDrag.Move.TURN_LEFT, s.by(-0.04, 0.01))
        s.start()
        assertEquals(StickDrag.Move.LIFT_UP, s.by(0.01, 0.04))
        s.start()
        assertEquals(StickDrag.Move.LIFT_DOWN, s.by(0.0, -0.05))
    }

    @Test
    fun aLongPushMakesOneStepPerStretch() {
        val s = StickDrag()
        s.start()
        val moves = (0 until 20).mapNotNull { s.by(0.01, 0.0) }
        assertEquals(5, moves.size)
        assertTrue(moves.all { it == StickDrag.Move.TURN_RIGHT })
        assertFalse(s.wasPress())
    }

    @Test
    fun aStillPinchIsAPress() {
        val s = StickDrag()
        s.start()
        s.by(0.005, -0.004)
        s.by(-0.006, 0.003)
        assertTrue(s.wasPress())
        // Wobbling about without making a step is not a press.
        s.start()
        repeat(6) { s.by(if (it % 2 == 0) 0.02 else -0.02, 0.0) }
        assertFalse(s.wasPress())
    }
}
