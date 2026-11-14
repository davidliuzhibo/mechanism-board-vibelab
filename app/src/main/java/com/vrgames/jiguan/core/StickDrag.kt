package com.vrgames.jiguan.core

import kotlin.math.abs

/**
 * The lift's control post worked like a stick (device 2026-11-14: "I drag the left handle left and right and nothing
 * happens"): pinch anywhere on the post and push the hand. Every [STEP] metres sideways turns the box once that way, every
 * [STEP] metres up or down lifts it one step. A pinch that hardly moves is a press of the arrow it started on.
 */
class StickDrag {
    enum class Move { LIFT_UP, LIFT_DOWN, TURN_LEFT, TURN_RIGHT }

    private var x = 0.0
    private var y = 0.0
    private var travel = 0.0
    /** How many steps this pinch has made. */
    var steps = 0; private set

    fun start() {
        x = 0.0; y = 0.0; travel = 0.0; steps = 0
    }

    /** The hand moved by [dx] to the right and [dy] up (metres, seen from the seat): the step it makes, if any. */
    fun by(dx: Double, dy: Double): Move? {
        x += dx; y += dy
        travel += kotlin.math.hypot(dx, dy)
        val ax = abs(x)
        val ay = abs(y)
        if (ax < STEP && ay < STEP) return null
        val m = if (ax >= ay) (if (x > 0) Move.TURN_RIGHT else Move.TURN_LEFT) else (if (y > 0) Move.LIFT_UP else Move.LIFT_DOWN)
        x = 0.0; y = 0.0
        steps++
        return m
    }

    /** At the end of the pinch: whether it was a press (no step made, the hand nearly still). */
    fun wasPress() = steps == 0 && travel < PRESS_TRAVEL

    companion object {
        const val STEP = 0.035
        const val PRESS_TRAVEL = 0.03
    }
}
