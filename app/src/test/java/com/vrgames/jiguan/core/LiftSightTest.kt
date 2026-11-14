package com.vrgames.jiguan.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Device 2026-11-13 (v0.4): the trays stacked straight again, a lift brings each tray in turn to the viewing height.
 * At its own step, every cell of that tray must be seen from the seat past the tray above, from above, and the box must
 * stay clear of the player's body.
 */
class LiftSightTest {
    private val open = 1.0
    private val all = listOf(0, 1, 2)

    /** The seated eye in box space with the box raised by [rise]. */
    private fun eyeAt(rise: Double) = Vec3(0.0, BoxLayout.DESK_BELOW_EYE - rise, BoxLayout.AHEAD)

    /** Whether the sight line from [eye] to cell ([x], [y]) of tray [l] passes under a tray above it. */
    private fun hidden(eye: Vec3, l: Int, x: Int, y: Int): Boolean {
        val target = BoxLayout.cell(l, open, x, y)
        for (u in l + 1 until LAYERS) {
            val under = BoxLayout.trayOrigin(u, open).y - 0.015
            if (under >= eye.y) continue
            val s = (eye.y - under) / (eye.y - target.y)
            if (s <= 0 || s >= 1) continue
            val px = eye.x + (target.x - eye.x) * s
            val pz = eye.z + (target.z - eye.z) * s
            val half = SIZE * BoxLayout.CELL / 2 + 0.02  // the frame's rim too
            if (abs(px) < half && abs(pz) < half) return true
        }
        return false
    }

    @Test
    fun atItsStepEveryCellOfEachTrayIsSeenPastTheTrayAbove() {
        for (l in all) {
            val eye = eyeAt(Lift.riseFor(l))
            for (x in 0 until SIZE) for (y in 0 until SIZE) assertTrue("tray $l cell $x,$y hidden at its step", !hidden(eye, l, x, y))
        }
    }

    @Test
    fun atItsStepATrayIsSeenFromAbove() {
        for (l in all) {
            val eye = eyeAt(Lift.riseFor(l))
            assertEquals(Lift.VIEW_BELOW_EYE, eye.y - BoxLayout.trayOrigin(l, open).y, 1e-9)
            for (x in 0 until SIZE) for (y in 0 until SIZE) {
                val c = BoxLayout.cell(l, open, x, y)
                val a = Math.toDegrees(atan2(eye.y - c.y, kotlin.math.hypot(c.x - eye.x, c.z - eye.z)))
                assertTrue("tray $l cell $x,$y seen at only ${"%.0f".format(a)}°", a >= 18.0)
            }
        }
    }

    @Test
    fun theLiftHasAStepPerTrayAndOneToLookDownWhenThereIsRoom() {
        assertEquals(3, Lift.steps(all).size)
        assertEquals(3, Lift.steps(listOf(0, 1)).size)
        assertEquals(2, Lift.steps(listOf(0)).size)
        for (u in listOf(all, listOf(0, 1), listOf(0))) {
            val s = Lift.steps(u)
            assertTrue("never below the desk: $s", s.all { it >= 0.0 })
            assertTrue("lowest first: $s", s.zipWithNext().all { (a, b) -> b > a })
            // Each step is one tray apart.
            s.zipWithNext().forEach { (a, b) -> assertEquals(BoxLayout.PITCH, b - a, 0.021) }
        }
    }

    @Test
    fun theDefaultStepShowsTheTrayTheKeyStartsOn() {
        for (l in all) assertEquals(l, Lift.trayAt(all, Lift.stepFor(all, l)))
    }

    @Test
    fun theBoxStaysClearOfTheBody() {
        val front = BoxLayout.AHEAD - SIZE * BoxLayout.CELL / 2 - 0.03
        // Device 2026-11-14: "at least 30 cm further" than v0.4, whose front edge was 0.37 m out.
        assertTrue("the box's front edge only ${"%.2f".format(front)} m out", front >= 0.37 + 0.30)
    }

    /** Device 2026-11-14: in level 1 the gold arrow did nothing. One tray there, so one step; the lift is needed later on. */
    @Test
    fun theFirstLevelNeedsNoLiftButALaterOneDoes() {
        fun used(l: Level) = (0 until LAYERS).filter { k -> l.layout.layers[k].any { r -> r.any { it != '#' } } }
        assertEquals(1, Lift.steps(used(Levels.ALL[0])).size)
        val first = Levels.ALL.indexOfFirst { Lift.steps(used(it)).size > 1 }
        assertTrue("no level uses the lift", first > 0)
        println("lift first used in level ${first + 1}")
    }
}
