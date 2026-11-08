package com.vrgames.jiguan.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every level is solvable at its stated best, and nothing in it can be done without. */
class LevelsTest {
    private fun remove(l: Level, id: String) = l.start.copy(pieces = l.start.pieces.filter { it.id != id })

    private val wheels = mapOf("L7" to Spot(1, 2, 1), "L8" to Spot(1, 3, 0), "L9" to Spot(1, 1, 0), "L11" to Spot(1, 1, 0), "L12" to Spot(1, 0, 0))

    @Test
    fun theBestLengthsAreTheOnesFromTheDesignPage() {
        assertEquals(listOf(5, 10, 8, 11, 13, 8, 7, 8, 8, 19, 12, 18), Levels.ALL.map { it.par })
        assertEquals(12, Levels.ALL.size)
    }

    @Test
    fun everyLevelIsSolvedAtExactlyItsPar() {
        for (l in Levels.ALL) {
            val path = Rules(l.layout).solve(l.start)
            assertNotNull(l.id, path)
            assertEquals(l.id, l.par, path!!.size)
        }
    }

    @Test
    fun playingTheSolutionWinsThreeStars() {
        for (l in Levels.ALL) {
            val p = Play(l)
            for (m in Rules(l.layout).solve(l.start)!!) assertNotNull(l.id, p.move(m))
            assertTrue(l.id, p.won)
            assertEquals(l.id, 3, p.stars())
        }
    }

    @Test
    fun noLevelCanBeSolvedWithoutAnyOfItsPieces() {
        for (l in Levels.ALL) for (x in l.start.pieces.filter { it.kind != Kind.KEY }) {
            assertNull("${l.id} without ${x.id}", Rules(l.layout).solve(remove(l, x.id)))
        }
    }

    @Test
    fun noLevelCanBeSolvedWithAnyLinkCut() {
        for (l in Levels.ALL) for (k in l.layout.links.keys) {
            val cut = Layout(l.layout.layers, l.layout.links - k)
            assertNull("${l.id} cut $k", Rules(cut).solve(l.start))
        }
    }

    @Test
    fun noWheelLevelCanBeSolvedWithoutItsWheel() {
        for ((id, w) in wheels) {
            val l = Levels.byId(id)
            assertEquals(id, 'W', l.layout.layers[w.layer][w.y][w.x])
            val layers = l.layout.layers.mapIndexed { i, rows ->
                if (i != w.layer) rows else rows.mapIndexed { y, r -> if (y != w.y) r else r.replaceRange(w.x, w.x + 1, ".") }
            }
            assertNull(id, Rules(Layout(layers, l.layout.links)).solve(l.start))
        }
    }

    @Test
    fun everyLinkPointsAtAMechanismThatUsesIt() {
        for (l in Levels.ALL) for ((from, tos) in l.layout.links) {
            assertTrue(l.id, l.layout.layers[from.layer][from.y][from.x] in "PL")
            for (t in tos) assertTrue(l.id, l.layout.layers[t.layer][t.y][t.x] in "TB")
        }
    }

    @Test
    fun theGoalIsAlwaysOnTheTop() {
        for (l in Levels.ALL) {
            assertTrue(l.id, l.layout.layers[2].any { 'G' in it })
            assertFalse(l.id, l.layout.layers[0].any { 'G' in it } || l.layout.layers[1].any { 'G' in it })
        }
    }

    @Test
    fun anOpenTrapdoorWithAPieceBelowCannotBeCrossedUntilItCloses() {
        val l = Levels.byId("L5")
        val r = Rules(l.layout)
        val door = Spot(2, 2, 0)
        val open = l.start.copy(
            pieces = listOf(Piece("H", Kind.HEAVY, Spot(2, 1, 3)), Piece("K", Kind.KEY, Spot(2, 3, 0))),
            patrols = listOf(Patrol(Spot(1, 2, 0), Dir.E)),
            latched = setOf(Spot(1, 2, 0)),
        )
        assertFalse(r.moves(open).any { it.pieceId == "K" && it.to == door })
        val closed = open.copy(pieces = listOf(Piece("H", Kind.HEAVY, Spot(2, 2, 3)), Piece("K", Kind.KEY, Spot(2, 3, 0))))
        assertTrue(r.moves(closed).any { it.pieceId == "K" && it.to == door })
    }

    @Test
    fun aHeavyPieceOnALatchKeepsItOnAfterLeaving() {
        val l = Levels.byId("L5")
        val p = Play(l)
        p.move(Move("H", Spot(2, 1, 4)))
        p.move(Move("H", Spot(2, 1, 3)))
        p.move(Move("H", Spot(2, 2, 3)))
        assertFalse(Spot(2, 2, 0) in p.rules.active(p.state))
        assertTrue(Spot(1, 2, 2) in p.rules.active(p.state))
    }

    @Test
    fun walkingIntoThePatrolsPathIsCaughtAndUndoTakesItBack() {
        val l = Levels.byId("L3")
        val p = Play(l)
        val before = p.state
        // The key steps out next to the patrol and waits while the other piece moves.
        p.move(Move("K", Spot(2, 4, 1)))
        p.move(Move("K", Spot(2, 4, 0)))
        p.move(Move("H", Spot(2, 3, 3)))
        assertTrue(p.caught)
        assertTrue(p.moves("K").isEmpty())
        while (p.canUndo) p.undo()
        assertEquals(before, p.state)
        assertFalse(p.caught)
    }

    @Test
    fun theLightPieceSlidesUntilStopped() {
        val p = Play(Levels.byId("L6"))
        val ups = p.moves("S").map { it.to }
        assertTrue(ups.toString(), Spot(2, 1, 0) in ups)
    }

    @Test
    fun aMoveThatIsNotLegalChangesNothing() {
        val p = Play(Levels.byId("L1"))
        val before = p.state
        assertNull(p.move(Move("K", Spot(2, 4, 4))))
        assertEquals(before, p.state)
        assertEquals(0, p.steps)
    }

    @Test
    fun starsFollowTheSteps() {
        val l = Levels.byId("L1")
        val p = Play(l)
        p.move(Move("H", Spot(2, 1, 4)))
        p.move(Move("H", Spot(2, 0, 4)))
        p.move(Move("H", Spot(2, 1, 4)))
        for (m in p.rules.solve(p.state)!!) p.move(m)
        assertEquals(7, p.steps)
        assertEquals(2, p.stars())
    }

    @Test
    fun hintsComeInThreeTiersAndAreAskedFor() {
        for (l in Levels.ALL) assertEquals(l.id, 3, l.hints.size)
        val p = Play(Levels.ALL[0])
        assertEquals(0, p.hintsAsked)
        assertEquals(Levels.ALL[0].hints[0], p.nextHint())
        assertEquals(1, p.hintsAsked)
    }

    @Test
    fun theWordsAvoidTheForbiddenOnes() {
        val bad = listOf("老虎机", "投币", "赌", "筹码", "奖金", "大奖", "中奖", "押注", "彩金", "赔率", "硬币", "翻倍", "彩票", "下注", "抽奖", "博彩", "轮盘")
        val text = Levels.ALL.flatMap { it.hints + it.brief + it.title } + Levels.CHAPTERS + Levels.CHAPTER_VOICE.values
        for (t in text) for (b in bad) assertFalse(t, b in t)
    }

    @Test
    fun trophiesAndSavedProgress() {
        val p = Progress()
        assertTrue(p.open(Levels.ALL[0]))
        assertFalse(p.open(Levels.ALL[1]))
        for (l in Levels.ALL) p.record(l.id, 1)
        assertEquals(1, p.trophies)
        for (l in Levels.ALL.filter { it.chapter <= 2 }) p.record(l.id, 3)
        assertEquals(2, p.trophies)
        for (l in Levels.ALL) p.record(l.id, 3)
        assertEquals(3, p.trophies)
        assertEquals(p.best, Progress.load(p.save()).best)
    }
}
