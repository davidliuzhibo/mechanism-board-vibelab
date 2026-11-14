package com.vrgames.jiguan.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * What stands between the eye and a piece (2026-11-12 review: a piece behind another was hard to pinch). Boxes are
 * axis-aligned in the box's own space, as the pieces' hit boxes are.
 */
object Sight {
    /** Whether the straight line from [eye] to [target] passes through the box at [center] with half sizes [half]. */
    fun blocks(eye: Vec3, target: Vec3, center: Vec3, half: Vec3): Boolean {
        val d = target - eye
        var t0 = 0.0
        var t1 = 1.0
        val o = doubleArrayOf(eye.x - center.x, eye.y - center.y, eye.z - center.z)
        val dir = doubleArrayOf(d.x, d.y, d.z)
        val h = doubleArrayOf(half.x, half.y, half.z)
        for (i in 0..2) {
            if (abs(dir[i]) < 1e-12) {
                if (abs(o[i]) > h[i]) return false
                continue
            }
            val a = (-h[i] - o[i]) / dir[i]
            val b = (h[i] - o[i]) / dir[i]
            t0 = max(t0, min(a, b))
            t1 = min(t1, max(a, b))
            if (t0 > t1) return false
        }
        // Only what is in front of the target counts, not a box the target itself stands in.
        return t0 < 0.97
    }
}
