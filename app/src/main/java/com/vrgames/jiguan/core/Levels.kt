package com.vrgames.jiguan.core

/** One level: its board, its best length and its words. The pars are proven by LevelsTest with the solver. */
class Level(
    val id: String,
    val chapter: Int,
    val title: String,
    /** What is new here. It states what things do and never what to do next. */
    val brief: String,
    val par: Int,
    val layout: Layout,
    val start: BoardState,
    /** Three hints, asked for one at a time: where to look, which mechanism, then how. */
    val hints: List<String>,
)

object Levels {
    private val E = List(SIZE) { "#####" }
    private val BOTTOM = listOf("#####", "#####", "..B.U", "#####", "#####")

    private fun key(l: Int, x: Int, y: Int) = Piece("K", Kind.KEY, Spot(l, x, y))
    private fun heavy(id: String, l: Int, x: Int, y: Int) = Piece(id, Kind.HEAVY, Spot(l, x, y))
    private fun light(l: Int, x: Int, y: Int) = Piece("S", Kind.LIGHT, Spot(l, x, y))
    private fun patrol(l: Int, x: Int, y: Int, d: Dir) = Patrol(Spot(l, x, y), d)
    private fun s(l: Int, x: Int, y: Int) = Spot(l, x, y)

    val ALL: List<Level> = listOf(
        Level(
            "L1", 1, "压板与桥", "压板：够重的棋子站在上面，连杆另一头的机关才生效，走开就恢复。杵够重，枢不够。", 5,
            Layout(listOf(E, E, listOf("#####", "#####", "..B.G", ".####", ".P###")), mapOf(s(2, 1, 4) to listOf(s(2, 2, 2)))),
            BoardState(listOf(heavy("H", 2, 0, 4), key(2, 0, 2)), emptyList()),
            listOf("桥是断的。看看这一层还有什么能动的东西。", "有一块压板连着桥，要够重的东西压住。", "让杵走到压板上不动，枢再过桥。"),
        ),
        Level(
            "L2", 1, "两枚杵接力", "两座断桥，各连一块压板。", 10,
            Layout(listOf(E, E, listOf("#####", "####G", "..B.B", "P.#P#", ".####")), mapOf(s(2, 0, 3) to listOf(s(2, 2, 2)), s(2, 3, 3) to listOf(s(2, 4, 2)))),
            BoardState(listOf(heavy("H1", 2, 0, 4), heavy("H2", 2, 1, 3), key(2, 0, 2)), emptyList()),
            listOf("路上有两座断桥，你有两枚杵。", "每块压板管一座桥。远处那块压板在第一座桥的另一边。", "一枚杵压住近处的压板，另一枚过桥去压远处的压板，枢最后走。"),
        ),
        Level(
            "L3", 1, "引它入门", "守阵人：沿直线走，被挡住就掉头，碰到枢就算失败。箭头是它下一步的方向。\n活门：打开时，上面的棋子会掉到正下方。如果正下方已有棋子，这扇门就走不过去。", 8,
            Layout(
                listOf(E, listOf("###.#", "#####", "#####", "#####", "#####"), listOf("G..T.", "####.", "####.", "#P..#", "#####")),
                mapOf(s(2, 1, 3) to listOf(s(2, 3, 0))),
            ),
            BoardState(listOf(heavy("H", 2, 2, 3), key(2, 4, 2)), listOf(patrol(2, 1, 0, Dir.E))),
            listOf("守阵人在这条路上来回走，枢绕不过去。", "它走到活门上时，如果门是开的，它就会掉下去。", "算好时间让杵踩上压板，等守阵人落下后，杵离开压板把门关上，枢才能过去。"),
        ),
        Level(
            "L4", 2, "连杆穿层", "升降台：站上去就升到正上方一层。\n连杆可以穿过棋盘，压板能控制别的层。捏一下压板，它连着的连杆会亮起来。", 11,
            Layout(
                listOf(
                    listOf("#####", "#####", "..B.U", "#####", "###P."),
                    listOf("#####", "#####", "U.B..", "#####", "#####"),
                    listOf("#####", "#####", ".G###", "#####", ".P###"),
                ),
                mapOf(s(2, 1, 4) to listOf(s(0, 2, 2)), s(0, 3, 4) to listOf(s(1, 2, 2))),
            ),
            BoardState(listOf(heavy("H1", 2, 0, 4), heavy("H2", 0, 4, 4), key(0, 0, 2)), emptyList()),
            listOf("枢要从底层一路升到顶层，底层和中层都有断桥。", "顶层的压板连着底层的桥，底层的压板连着中层的桥。", "两枚杵各自走上压板不动，枢就能一路走到天枢位。"),
        ),
        Level(
            "L5", 2, "掉层连锁", "闩板：重的东西踩一次就锁定，连着的机关一直生效。", 13,
            Layout(
                listOf(E, listOf("##L##", "#####", "..B.U", "#####", "#####"), listOf("G.T..", "###.#", "#....", "#P..#", "#...#")),
                mapOf(s(2, 1, 3) to listOf(s(2, 2, 0)), s(1, 2, 0) to listOf(s(1, 2, 2))),
            ),
            BoardState(listOf(heavy("H", 2, 2, 4), key(1, 0, 2)), listOf(patrol(2, 0, 0, Dir.E))),
            listOf("中层的桥没有压板直接管它。看看它连着什么。", "桥连着一块闩板，闩板在顶层活门的正下方。", "杵踩压板开门，让守阵人掉到闩板上。然后杵离开压板关门，枢再出发。"),
        ),
        Level(
            "L6", 2, "梭压守阵人", "梭：直线滑行，撞到东西才停。它太轻，压不动机关，但可以挡路。", 8,
            Layout(
                listOf(E, listOf("#####", "#####", "#####", "#####", "#..##"), listOf("P....", "#.###", "#.###", "..###", ".BB.G")),
                mapOf(s(2, 0, 0) to listOf(s(2, 1, 4), s(2, 2, 4))),
            ),
            BoardState(listOf(key(2, 0, 4), light(2, 1, 3)), listOf(patrol(2, 3, 0, Dir.W))),
            listOf("守阵人只在走到压板那一格时，桥才升起，这点时间过不了两段桥。", "能不能让守阵人停在压板上？梭可以挡路。", "等守阵人走到角落的压板上，把梭滑到它旁边，它就走不了了。"),
        ),
        Level(
            "L7", 3, "转层对位", "转轮：重的东西走上去，整层顺时针转 90°，上面的棋子和机关跟着转。", 7,
            Layout(
                listOf(E, listOf("..#..", "..W..", "#####", "..#..", "..U.."), listOf("#####", "#####", "###G.", "#####", "#####")),
                emptyMap(),
            ),
            BoardState(listOf(heavy("H", 1, 2, 0), key(1, 2, 3)), emptyList()),
            listOf("升降台上面，只有一个地方通到顶层。", "这一层能转。杵走上转轮，这一层就转 90°。", "让杵反复走上转轮，把升降台转到出口正下方，枢站在升降台上就会升上去。"),
        ),
        Level(
            "L8", 3, "转层改落点", "活门正下方如果是墙，棋子就掉不下去。转动下面一层，正下方就换了一格。", 8,
            Layout(
                listOf(E, listOf("###W.", "U####", ".####", "...##", "#####"), listOf("###.G", "#####", ".T###", "#P###", "#.###")),
                mapOf(s(2, 1, 3) to listOf(s(2, 1, 2))),
            ),
            BoardState(listOf(heavy("H1", 2, 1, 4), heavy("H2", 1, 4, 0), key(2, 0, 2)), emptyList()),
            listOf("活门下面现在是实心的，枢掉不下去。", "转动中层，活门下面就会换一格。活门还要有杵压住压板才开。", "顶层的杵压住压板，中层的杵踩一次转轮，枢走上活门，掉到中层后走到升降台。"),
        ),
        Level(
            "L9", 3, "它在转层", "守阵人也够重，它走上转轮，那一层一样会转。", 8,
            Layout(
                listOf(E, listOf(".W.##", "#####", "#####", "..#..", "..U.."), listOf("#####", "#####", "#G.B.", "####P", "####.")),
                mapOf(s(2, 4, 3) to listOf(s(2, 3, 2))),
            ),
            BoardState(listOf(heavy("H", 2, 4, 4), key(1, 1, 3)), listOf(patrol(1, 2, 0, Dir.W))),
            listOf("中层自己在转。看看是谁在转它。", "守阵人每次走上转轮，这一层就转 90°，升降台也跟着转。", "让枢先站到升降台上等，转到出口下面时会自己升上去。杵要压住顶层的压板。"),
        ),
        Level(
            "L10", 4, "先后有序", "前面的机关都会用到。", 19,
            Layout(
                listOf(BOTTOM, listOf("##L##", "#####", "U.B..", "#####", "#####"), listOf("G.T..", "###.#", ".....", "#P.P#", "#...#")),
                mapOf(s(2, 1, 3) to listOf(s(2, 2, 0)), s(2, 3, 3) to listOf(s(0, 2, 2)), s(1, 2, 0) to listOf(s(1, 2, 2))),
            ),
            BoardState(listOf(heavy("H", 2, 2, 3), key(0, 0, 2)), listOf(patrol(2, 0, 0, Dir.E))),
            listOf("杵只有一枚，却要管两块压板。想想先后。", "一块压板开活门，另一块管底层的桥。闩板踩下去之后就不用再管。", "先开活门让守阵人落到闩板上，再把杵移到另一块压板，让枢过底层的桥。"),
        ),
        Level(
            "L11", 4, "卡住再转", "前面的机关都会用到。", 12,
            Layout(
                listOf(E, listOf(".W#.#", "##.##", "##.##", "##.##", "##.U#"), listOf("P....", "#.###", "#.#TB", "..##B", "G##..")),
                mapOf(s(2, 0, 0) to listOf(s(2, 3, 2), s(2, 4, 2), s(2, 4, 3))),
            ),
            BoardState(listOf(heavy("H", 1, 0, 0), key(2, 4, 4), light(2, 1, 3)), listOf(patrol(2, 3, 0, Dir.W))),
            listOf("角落的压板同时管两段桥和活门。", "梭能把守阵人卡在压板上。活门下面要换成通道，需要转中层。", "杵踩一次转轮，等守阵人到角落时用梭卡住它。枢过两段桥走上活门，掉到中层后走到升降台。"),
        ),
        Level(
            "L12", 4, "机关匣开", "最后一关，三层全部用上。", 18,
            Layout(
                listOf(
                    listOf("#####", "#####", "..B..", "####U", "#####"),
                    listOf("W.#.#", "##..#", "L#B##", "##.##", "##U##"),
                    listOf("G.T..", "###.#", ".....", "#P.P#", "#####"),
                ),
                mapOf(s(2, 1, 3) to listOf(s(2, 2, 0)), s(2, 3, 3) to listOf(s(0, 2, 2)), s(1, 0, 2) to listOf(s(1, 2, 2))),
            ),
            BoardState(listOf(heavy("H1", 2, 2, 3), heavy("H2", 1, 1, 0), key(0, 0, 2)), listOf(patrol(2, 0, 0, Dir.E))),
            listOf("三层全部要用。先想枢最后从哪里上顶层，再往回想。", "中层要转动，闩板才会来到活门正下方，底层的升降台也才有地方可升。", "中层的杵踩转轮；顶层的杵先开活门让守阵人落到闩板上，再去压另一块板，让枢过底层的桥。"),
        ),
    )

    val CHAPTERS = listOf("压板和活门", "跨层联动", "转层", "合局")

    /** The narrator's clip played when a chapter opens, and what it says. */
    val CHAPTER_VOICE = mapOf(
        1 to "这只青铜匣，传说是一位老匠人留给徒弟的最后一道考题。匣里没有钥匙，只有三层棋盘。要打开它，先弄懂压板和活门。",
        2 to "第一层机关开了。老匠人说，好的机关，因和果不在一处。这一层的一块板，动的是另一层的一座桥。",
        3 to "再往里，棋盘自己也能转。转一下，上下对着的就全变了。老匠人最得意的，就是这一手。",
        4 to "到最后这几关，所有机关要合在一起。别急着动手，先想清楚最后一步，再一步步往回推。",
    )

    fun byId(id: String) = ALL.first { it.id == id }
}

/** One attempt at a level. Keeps every past state, so any step can be taken back. */
class Play(val level: Level) {
    val rules = Rules(level.layout)
    private val history = ArrayList<BoardState>()
    var state = level.start; private set
    var won = false; private set
    var caught = false; private set
    val steps get() = history.size
    var hintsAsked = 0; private set
    val canUndo get() = history.isNotEmpty()

    fun moves(id: String) = if (won || caught) emptyList() else rules.moves(state).filter { it.pieceId == id }

    /** Plays a move if it is legal. Returns what happened, or null. */
    fun move(m: Move): TurnResult? {
        if (won || caught || m !in rules.moves(state)) return null
        val r = rules.turn(state, m)
        history += state
        state = r.state
        won = r.won
        caught = r.caught
        return r
    }

    fun undo(): Boolean {
        if (history.isEmpty()) return false
        state = history.removeAt(history.size - 1)
        won = false
        caught = false
        return true
    }

    fun nextHint(): String? {
        if (hintsAsked >= level.hints.size) return level.hints.lastOrNull()
        return level.hints[hintsAsked++]
    }

    /** Three stars at the fewest steps, two within two more, one for getting there at all. */
    fun stars() = when {
        !won -> 0
        steps <= level.par -> 3
        steps <= level.par + 2 -> 2
        else -> 1
    }
}

/** Stars per level, saved as "id:stars". The trophies are what the design page promised. */
class Progress(val best: MutableMap<String, Int> = LinkedHashMap()) {
    val stars get() = best.values.sum()
    val maxStars get() = Levels.ALL.size * 3
    fun open(l: Level): Boolean {
        val k = Levels.ALL.indexOf(l)
        return k == 0 || (best[Levels.ALL[k - 1].id] ?: 0) > 0 || (best[l.id] ?: 0) > 0
    }
    val bronze get() = Levels.ALL.all { (best[it.id] ?: 0) > 0 }
    val silver get() = Levels.ALL.filter { it.chapter <= 2 }.all { (best[it.id] ?: 0) == 3 } && bronze
    val gold get() = Levels.ALL.all { (best[it.id] ?: 0) == 3 }
    val trophies get() = if (gold) 3 else if (silver) 2 else if (bronze) 1 else 0
    fun record(id: String, s: Int) { if (s > (best[id] ?: 0)) best[id] = s }
    fun save() = best.entries.joinToString(",") { "${it.key}:${it.value}" }

    companion object {
        fun load(text: String) = Progress().also { p ->
            text.split(",").filter { it.contains(":") }.forEach {
                val (k, v) = it.split(":")
                p.best[k] = v.toIntOrNull() ?: 0
            }
        }
    }
}
