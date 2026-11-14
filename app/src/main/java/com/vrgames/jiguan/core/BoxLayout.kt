package com.vrgames.jiguan.core

import kotlin.math.cos
import kotlin.math.sin

/**
 * Where the three trays stand, in the box's own space (y 0 the box's foot, the player at +z), and where the box stands
 * from the eyes.
 *
 * Device 2026-11-13 (v0.4): the stairs-and-lean layout of v0.3 brought the lower trays into the player's body. The trays
 * are stacked straight again, flat, as in the versions before, 20 cm apart when open, and the box stands further out.
 * To see a lower tray, the player raises the box on its lift ([Lift]) one tray at a time, and can turn it ([Lift.TURN_STEP]).
 *
 * Device 2026-11-14 (v0.4.1): still too near, "at least 30 cm further": the box's middle 0.95 m out (was 0.62).
 */
object BoxLayout {
    const val CELL = 0.08
    /** The desk top this far below the eyes, the box's middle this far out. */
    const val DESK_BELOW_EYE = 0.84
    const val AHEAD = 0.95
    const val BASE_Y = 0.06
    const val BASE_LIFT = 0.0
    const val GAP_CLOSED = 0.055
    const val GAP_OPEN = 0.145
    /** Open, one tray to the next. */
    const val PITCH = GAP_CLOSED + GAP_OPEN
    const val STAGGER = 0.0
    const val TILT_DEG = 0.0

    fun local(i: Int) = (i - (SIZE - 1) / 2.0) * CELL

    fun trayOrigin(l: Int, spread: Double) =
        Vec3(0.0, BASE_Y + BASE_LIFT * spread + l * (GAP_CLOSED + GAP_OPEN * spread), -l * STAGGER * spread)

    fun tiltDeg(spread: Double) = TILT_DEG * spread

    /** The tray's up direction (its surface normal) in box space. */
    fun normal(spread: Double): Vec3 {
        val a = Math.toRadians(tiltDeg(spread))
        return Vec3(0.0, cos(a), sin(a))
    }

    /** A point given in tray [l]'s own frame (x right, y up out of the tray, z toward the player), in box space. */
    fun trayPoint(l: Int, spread: Double, x: Double, y: Double, z: Double): Vec3 {
        val a = Math.toRadians(tiltDeg(spread))
        return trayOrigin(l, spread) + Vec3(x, y * cos(a) - z * sin(a), y * sin(a) + z * cos(a))
    }

    /** The middle of cell ([x], [y]) of tray [l]. */
    fun cell(l: Int, spread: Double, x: Int, y: Int) = trayPoint(l, spread, local(x), 0.0, local(y))

    /** The seated eye in box space with the box at its lowest (lift 0), by the layout's own measure. */
    val EYE = Vec3(0.0, DESK_BELOW_EYE, AHEAD)
}

/**
 * The box's lift (device 2026-11-13: "a lift under the box, 3 to 4 steps, one tray per step, a handle at the side").
 * Each step brings one tray to [VIEW_BELOW_EYE] under the eyes, where all its cells are seen past the tray above; one more
 * step down shows the whole box from above, when the desk leaves room. Heights are the box's rise over the desk top.
 */
object Lift {
    /**
     * The tray being looked at sits this far below the eyes. With the box 0.95 m out, a little lower than at 0.62 m keeps
     * the far cells in sight; the top tray is then already at this height with the box down on the desk.
     */
    const val VIEW_BELOW_EYE = 0.38
    /** The box turns by this many degrees a step. */
    const val TURN_STEP = 45.0

    /** The rise that brings tray [layer] to the viewing height. */
    fun riseFor(layer: Int) = (BoxLayout.DESK_BELOW_EYE - VIEW_BELOW_EYE - BoxLayout.trayOrigin(layer, 1.0).y).coerceAtLeast(0.0)

    /**
     * The lift's steps for a level whose trays in use are [used] (lowest first), lowest rise first: the overview (when it
     * stays above the desk), then the top tray, and down to the lowest tray in use.
     */
    fun steps(used: List<Int>): List<Double> {
        val layers = used.ifEmpty { listOf(0) }.sortedDescending()
        val focus = layers.map { riseFor(it) }
        val overview = focus.first() - BoxLayout.PITCH
        return ((if (overview >= -1e-9) listOf(overview.coerceAtLeast(0.0)) else emptyList()) + focus).distinct()
    }

    /** The step that shows tray [layer] (the one the 枢 starts on), in [steps] for [used]. */
    fun stepFor(used: List<Int>, layer: Int): Int {
        val s = steps(used)
        val r = riseFor(layer)
        return s.indices.minByOrNull { kotlin.math.abs(s[it] - r) } ?: 0
    }

    /** Which tray step [i] of [used] looks at (null: the overview). */
    fun trayAt(used: List<Int>, i: Int): Int? = used.firstOrNull { kotlin.math.abs(riseFor(it) - steps(used).getOrElse(i) { -1.0 }) < 1e-9 }
}
