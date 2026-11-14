package com.vrgames.jiguan.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SightTest {
    private val half = Vec3(0.04, 0.045, 0.04)
    private val eye = Vec3(0.0, 0.49, 0.62)

    @Test
    fun aPieceInFrontHidesTheOneBehind() {
        // The eye 62 cm in front and 45 cm above the pieces: a piece one cell nearer, a little lower than the line, is in the way.
        val back = Vec3(0.0, 0.04, -0.08)
        val front = Vec3(0.0, 0.04, 0.0)
        assertTrue(Sight.blocks(eye, back, front, half))
    }

    @Test
    fun aPieceBehindOrBesideDoesNot() {
        val target = Vec3(0.0, 0.04, 0.0)
        assertFalse(Sight.blocks(eye, target, Vec3(0.0, 0.04, -0.08), half))
        assertFalse(Sight.blocks(eye, target, Vec3(0.16, 0.04, 0.08), half))
    }

    @Test
    fun aTallBoxAboveTheFrontPieceIsInSight() {
        // The top of a raised hit box behind is seen over the front piece.
        val front = Vec3(0.0, 0.04, 0.0)
        assertFalse(Sight.blocks(eye, Vec3(0.0, 0.17, -0.08), front, half))
    }
}
