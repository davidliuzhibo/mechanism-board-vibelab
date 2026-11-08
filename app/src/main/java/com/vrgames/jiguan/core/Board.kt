package com.vrgames.jiguan.core

/**
 * The rules of the bronze mechanism board. Pure, with no engine types, so the solver and the tests run
 * the very rules the player meets. The same rules live in docs/solver.py, where the levels were designed.
 *
 * Cells: . floor, # wall, ' ' gap, T trapdoor (open when linked), B bridge (up when linked), P plate (while a
 * heavy piece stands on it), L latch plate (stays on once pressed), U lift, W wheel (turns its layer 90 degrees
 * clockwise when a heavy piece walks onto it), G goal. Links belong to the cell, so they turn with the layer.
 */
const val SIZE = 5
const val LAYERS = 3

enum class Dir(val dx: Int, val dy: Int) {
    E(1, 0), W(-1, 0), N(0, -1), S(0, 1);

    val back get() = when (this) { E -> W; W -> E; N -> S; S -> N }
    val clockwise get() = when (this) { N -> E; E -> S; S -> W; W -> N }
}

/** A place on the board, in world coordinates unless named otherwise. */
data class Spot(val layer: Int, val x: Int, val y: Int)

enum class Kind { KEY, HEAVY, LIGHT }

data class Piece(val id: String, val kind: Kind, val at: Spot)

data class Patrol(val at: Spot, val dir: Dir)

data class BoardState(
    val pieces: List<Piece>,
    val patrols: List<Patrol>,
    val rotation: List<Int> = listOf(0, 0, 0),
    val latched: Set<Spot> = emptySet(),
) {
    val key get() = pieces.first { it.kind == Kind.KEY }
    fun piece(id: String) = pieces.firstOrNull { it.id == id }
}

/** What happened in a turn, in order, for the view to animate and the sounds to play. */
sealed interface Ev {
    data class Moved(val id: String, val from: Spot, val to: Spot) : Ev
    data class Fell(val id: String, val from: Spot, val to: Spot) : Ev
    data class Rose(val id: String, val from: Spot, val to: Spot) : Ev
    data class Turned(val layer: Int) : Ev
    data class Latched(val cell: Spot) : Ev
    data class PatrolMoved(val index: Int, val from: Spot, val to: Spot, val dir: Dir) : Ev
    data class PatrolFell(val index: Int, val from: Spot, val to: Spot) : Ev
    /** The linked cells that are on changed: doors swing, bridges rise, rods light. */
    data class Mechanisms(val on: Set<Spot>) : Ev
    data object Won : Ev
    data object Caught : Ev
}

data class Move(val pieceId: String, val to: Spot)

data class TurnResult(val state: BoardState, val won: Boolean, val caught: Boolean, val events: List<Ev>)

/** One level's fixed board: what each cell is and what each plate is linked to. */
class Layout(val layers: List<List<String>>, val links: Map<Spot, List<Spot>>) {
    init {
        require(layers.size == LAYERS && layers.all { l -> l.size == SIZE && l.all { it.length == SIZE } }) { "a board is 3 layers of 5 by 5" }
    }

    fun local(x: Int, y: Int, turns: Int): Pair<Int, Int> {
        var a = x
        var b = y
        repeat(turns.mod(4)) { val c = a; a = b; b = SIZE - 1 - c }
        return a to b
    }

    fun world(x: Int, y: Int, turns: Int): Pair<Int, Int> {
        var a = x
        var b = y
        repeat(turns.mod(4)) { val c = a; a = SIZE - 1 - b; b = c }
        return a to b
    }

    /** The cell under a world position, and its own coordinates on its layer. */
    fun cellId(s: BoardState, at: Spot): Spot {
        val (lx, ly) = local(at.x, at.y, s.rotation[at.layer])
        return Spot(at.layer, lx, ly)
    }

    fun cell(s: BoardState, at: Spot): Char {
        val id = cellId(s, at)
        return layers[id.layer][id.y][id.x]
    }

    fun worldOf(s: BoardState, cellId: Spot): Spot {
        val (x, y) = world(cellId.x, cellId.y, s.rotation[cellId.layer])
        return Spot(cellId.layer, x, y)
    }
}

class Rules(val layout: Layout) {
    private fun heavyCells(s: BoardState, pieces: List<Piece> = s.pieces, patrols: List<Patrol> = s.patrols) =
        pieces.filter { it.kind == Kind.HEAVY }.map { it.at } + patrols.map { it.at }

    /** Linked cells now on, and the latches including any just pressed. */
    private fun evaluate(pieces: List<Piece>, patrols: List<Patrol>, rot: List<Int>, latched: Set<Spot>): Pair<Set<Spot>, Set<Spot>> {
        val s = BoardState(pieces, patrols, rot, latched)
        val locks = latched.toMutableSet()
        val on = HashSet<Spot>()
        for (at in heavyCells(s)) {
            val c = layout.cell(s, at)
            val id = layout.cellId(s, at)
            if (c == 'L') locks += id
            else if (c == 'P') layout.links[id]?.let { on += it }
        }
        for (id in locks) layout.links[id]?.let { on += it }
        return on to locks
    }

    /** Linked cells now on, in the cell's own coordinates, for the view. */
    fun active(s: BoardState) = evaluate(s.pieces, s.patrols, s.rotation, s.latched).first

    private fun occupied(pieces: List<Piece>, patrols: List<Patrol>, at: Spot) =
        pieces.any { it.at == at } || patrols.any { it.at == at }

    private fun walkable(s: BoardState, at: Spot, on: Set<Spot>): Boolean {
        if (at.x !in 0 until SIZE || at.y !in 0 until SIZE) return false
        val c = layout.cell(s, at)
        if (c == '#' || c == ' ') return false
        return c != 'B' || layout.cellId(s, at) in on
    }

    /** An open trapdoor with a piece right below it: nobody can stand on it, so it cannot be crossed. */
    private fun plugged(s: BoardState, pieces: List<Piece>, patrols: List<Patrol>, at: Spot, on: Set<Spot>): Boolean =
        at.layer > 0 && layout.cell(s, at) == 'T' && layout.cellId(s, at) in on && occupied(pieces, patrols, Spot(at.layer - 1, at.x, at.y))

    private fun openDoor(s: BoardState, at: Spot, on: Set<Spot>) = layout.cell(s, at) == 'T' && layout.cellId(s, at) in on

    private fun falls(s: BoardState, at: Spot, on: Set<Spot>): Boolean {
        if (at.layer == 0) return false
        val c = layout.cell(s, at)
        val id = layout.cellId(s, at)
        return (c == 'T' && id in on) || (c == 'B' && id !in on)
    }

    private fun turnLayer(s: BoardState, l: Int, ev: MutableList<Ev>): BoardState {
        fun spin(a: Spot) = if (a.layer != l) a else layout.world(a.x, a.y, 1).let { (x, y) -> Spot(l, x, y) }
        ev += Ev.Turned(l)
        return s.copy(
            pieces = s.pieces.map { it.copy(at = spin(it.at)) },
            patrols = s.patrols.map { if (it.at.layer == l) Patrol(spin(it.at), it.dir.clockwise) else it },
            rotation = s.rotation.mapIndexed { i, r -> if (i == l) (r + 1) % 4 else r },
        )
    }

    /** Falls, lifts and latches until nothing changes. */
    private fun settle(start: BoardState, entered: Spot?, ev: MutableList<Ev>, shown: Array<Set<Spot>>): BoardState {
        var s = start
        if (entered != null && layout.cell(s, entered) == 'W') s = turnLayer(s, entered.layer, ev)
        repeat(40) {
            val (on, locks) = evaluate(s.pieces, s.patrols, s.rotation, s.latched)
            for (id in locks - s.latched) ev += Ev.Latched(id)
            if (on != shown[0]) {
                ev += Ev.Mechanisms(on)
                shown[0] = on
            }
            s = s.copy(latched = locks)
            var moved = false
            val ps = s.pieces.toMutableList()
            for (n in ps.indices) {
                val p = ps[n]
                val at = p.at
                val below = Spot(at.layer - 1, at.x, at.y)
                val above = Spot(at.layer + 1, at.x, at.y)
                if (falls(s, at, on) && layout.cell(s, below) != '#' && !occupied(ps, s.patrols, below)) {
                    ps[n] = p.copy(at = below)
                    ev += Ev.Fell(p.id, at, below)
                    moved = true
                } else if (layout.cell(s, at) == 'U' && at.layer < LAYERS - 1 && walkable(s, above, on) && !occupied(ps, s.patrols, above) && !openDoor(s, above, on)) {
                    ps[n] = p.copy(at = above)
                    ev += Ev.Rose(p.id, at, above)
                    moved = true
                }
            }
            val pa = s.patrols.toMutableList()
            for (n in pa.indices) {
                val g = pa[n]
                val below = Spot(g.at.layer - 1, g.at.x, g.at.y)
                if (falls(s, g.at, on) && layout.cell(s, below) != '#' && !occupied(ps, pa, below)) {
                    pa[n] = g.copy(at = below)
                    ev += Ev.PatrolFell(n, g.at, below)
                    moved = true
                }
            }
            s = s.copy(pieces = ps, patrols = pa)
            if (!moved) return s
        }
        error("mechanism loop")
    }

    private fun neighbors(p: Piece) = Dir.entries.map { Spot(p.at.layer, p.at.x + it.dx, p.at.y + it.dy) to it }

    /** Every move the player may make now. */
    fun moves(s: BoardState): List<Move> {
        val (on, _) = evaluate(s.pieces, s.patrols, s.rotation, s.latched)
        val out = ArrayList<Move>()
        for (p in s.pieces) {
            val rest = s.pieces.filter { it !== p }
            for ((first, d) in neighbors(p)) {
                if (!free(s, rest, first, on)) continue
                var to = first
                if (p.kind == Kind.LIGHT) {
                    while (true) {
                        if (layout.cell(s, to) == 'T' && layout.cellId(s, to) in on) break
                        val next = Spot(to.layer, to.x + d.dx, to.y + d.dy)
                        if (!free(s, rest, next, on)) break
                        to = next
                    }
                }
                out += Move(p.id, to)
            }
        }
        return out
    }

    private fun free(s: BoardState, rest: List<Piece>, at: Spot, on: Set<Spot>) =
        walkable(s, at, on) && !occupied(rest, s.patrols, at) && !plugged(s, rest, s.patrols, at, on)

    /** One full turn: the player's move, the mechanisms, then each patrol. */
    fun turn(start: BoardState, m: Move): TurnResult {
        val ev = ArrayList<Ev>()
        val shown = arrayOf(active(start))
        val mover = start.pieces.first { it.id == m.pieceId }
        ev += Ev.Moved(mover.id, mover.at, m.to)
        var s = start.copy(pieces = start.pieces.map { if (it.id == mover.id) it.copy(at = m.to) else it })
        s = settle(s, if (mover.kind == Kind.HEAVY) m.to else null, ev, shown)
        if (layout.cell(s, s.key.at) == 'G') {
            ev += Ev.Won
            return TurnResult(s, won = true, caught = false, ev)
        }
        for (n in s.patrols.indices) {
            val g = s.patrols[n]
            val (on, _) = evaluate(s.pieces, s.patrols, s.rotation, s.latched)
            val others = s.patrols.filterIndexed { i, _ -> i != n }
            var target: Patrol? = null
            for (d in listOf(g.dir, g.dir.back)) {
                val next = Spot(g.at.layer, g.at.x + d.dx, g.at.y + d.dy)
                if (s.key.at == next) {
                    ev += Ev.Caught
                    return TurnResult(s, won = false, caught = true, ev)
                }
                if (walkable(s, next, on) && !occupied(s.pieces, others, next) && !plugged(s, s.pieces, others, next, on)) {
                    target = Patrol(next, d)
                    break
                }
            }
            if (target != null) {
                ev += Ev.PatrolMoved(n, g.at, target.at, target.dir)
                s = s.copy(patrols = s.patrols.mapIndexed { i, x -> if (i == n) target else x })
                s = settle(s, target.at, ev, shown)
            }
        }
        return TurnResult(s, won = false, caught = false, ev)
    }

    /** The shortest way from here, or null. Breadth first, so the first solution found is the shortest. */
    fun solve(start: BoardState, cap: Int = 200_000): List<Move>? {
        val seen = HashMap<BoardState, Pair<BoardState, Move>?>()
        seen[start] = null
        val queue = ArrayDeque<BoardState>()
        queue += start
        while (queue.isNotEmpty() && seen.size < cap) {
            val s = queue.removeFirst()
            for (m in moves(s)) {
                val r = turn(s, m)
                if (r.caught || r.state in seen) continue
                seen[r.state] = s to m
                if (r.won) {
                    val path = ArrayList<Move>()
                    var cur = r.state
                    while (true) {
                        val (prev, mv) = seen[cur] ?: break
                        path += mv
                        cur = prev
                    }
                    return path.reversed()
                }
                queue += r.state
            }
        }
        return null
    }
}
