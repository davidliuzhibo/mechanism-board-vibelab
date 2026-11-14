package com.vrgames.jiguan.game

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vrgames.jiguan.core.Ev
import com.vrgames.jiguan.core.Level
import com.vrgames.jiguan.core.Levels
import com.vrgames.jiguan.core.Lift
import com.vrgames.jiguan.core.LAYERS
import com.vrgames.jiguan.core.Move
import com.vrgames.jiguan.core.Play
import com.vrgames.jiguan.core.Progress
import com.vrgames.jiguan.core.Spot
import com.vrgames.jiguan.render.BoardView
import com.vrgames.jiguan.render.Music
import com.vrgames.jiguan.render.Sound

/** State the stage, its panels and the levels window share. Stars and music are saved; a level in progress is not. */
object Game {
    private const val PREFS = "jiguan"
    private const val TAG = "BoardGame"

    enum class Phase { CARD, INTRO, PLAYING, CAUGHT, RESULT, SHOW }

    private var prefs: android.content.SharedPreferences? = null
    private var app: Context? = null
    var progress = Progress(); private set
    var level by mutableStateOf<Level>(Levels.ALL[0]); private set
    var play by mutableStateOf<Play?>(null); private set
    var playVersion by mutableIntStateOf(0); private set
    var phase by mutableStateOf(Phase.CARD)
    var version by mutableIntStateOf(0); private set
    var note by mutableStateOf<String?>(null)
    var noteVersion by mutableIntStateOf(0); private set
    var hint by mutableStateOf<String?>(null); private set
    var selected by mutableStateOf<String?>(null); private set
    var menuOpen by mutableStateOf(false)
    var confirmWipe by mutableStateOf(false)
    /** The menu shows how the lift's post and the lever work. */
    var menuHelp by mutableStateOf(false)
    var musicOn by mutableStateOf(true); private set
    var spread by mutableStateOf(true)
    /** The box's lift: its steps (rise over the desk, lowest first) for this level, and the step it is on. */
    var liftSteps by mutableStateOf(listOf(0.0)); private set
    var liftStep by mutableIntStateOf(0); private set
    /** A few words shown on the control post's name plate for 4 s (device 2026-11-14: a pinch on the gold arrow seemed to do nothing). */
    var postNote by mutableStateOf<String?>(null)
    var postNoteVersion by mutableIntStateOf(0); private set
    /** The trays [l] uses, lowest first. */
    private fun usedOf(l: Level) = (0 until LAYERS).filter { k -> l.layout.layers[k].any { r -> r.any { it != '#' } } }
    /** The first level (1-based) whose box has more than one lift step, or 0. */
    val firstLiftLevel: Int by lazy { Levels.ALL.indexOfFirst { Lift.steps(usedOf(it)).size > 1 } + 1 }
    val liftUseless get() = liftSteps.size <= 1

    private fun postSay(text: String) {
        postNote = text
        postNoteVersion++
    }
    /** The box turned on its lift, degrees (steps of [Lift.TURN_STEP]). */
    var boxYaw by mutableStateOf(0.0); private set
    private var usedLayers = listOf(0)
    var faceRequest by mutableIntStateOf(0)
    /** The player's own raise or drop of the whole desk, meters (menu 「机关匣高度」), kept between sessions. */
    @Volatile var heightAdjust = 0.0; private set
    var heightLabel by mutableStateOf("标准"); private set
    var windowOpen by mutableStateOf(false)
    var closeWindow: () -> Unit = {}
    var view: BoardView? = null
    var sound: Sound? = null
    var music: Music? = null
    var trophiesBefore = 0
    var timeScale = 1.0
    var debugView by mutableStateOf<String?>(null)
    var speaking by mutableStateOf(false); private set
    private var player: MediaPlayer? = null
    private val heardChapter = HashSet<Int>()

    val index get() = Levels.ALL.indexOf(level)
    val hasNext get() = index + 1 < Levels.ALL.size

    fun init(context: Context) {
        if (prefs != null) return
        app = context.applicationContext
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { p ->
            musicOn = p.getBoolean("music", true)
            setHeight(p.getFloat("height", 0f).toDouble(), save = false)
            progress = Progress.load(p.getString("best", "") ?: "")
        }
        Log.i(TAG, "stars ${progress.stars}/${progress.maxStars}")
        AutoPilot.register(context)
    }

    fun say(text: String) {
        note = text
        noteVersion++
    }

    /** The first level without a star, or the last. */
    fun start() = intro(Levels.ALL.firstOrNull { (progress.best[it.id] ?: 0) == 0 } ?: Levels.ALL.last())

    fun intro(l: Level) {
        if (!progress.open(l)) {
            say("这一关还没开放：先解开前面的。")
            return
        }
        fresh(l)
        spread = true
        phase = Phase.INTRO
        if (l.chapter !in heardChapter && Levels.ALL.first { it.chapter == l.chapter } === l) {
            heardChapter += l.chapter
            speakChapter(l.chapter)
        }
        version++
    }

    private fun fresh(l: Level) {
        // A new level puts the box on the step that shows the tray the 枢 starts on, facing the player; a retry keeps it.
        if (l !== level || play == null) {
            usedLayers = usedOf(l)
            liftSteps = Lift.steps(usedLayers)
            liftStep = Lift.stepFor(usedLayers, l.start.key.at.layer)
            boxYaw = 0.0
        }
        selected = null
        hint = null
        level = l
        play = Play(l)
        playVersion++
    }

    fun begin() {
        stopVoice()
        fresh(level)
        phase = Phase.PLAYING
        music?.resume()
        Log.i(TAG, "begin ${level.id}")
        version++
    }

    fun retry() = begin()

    fun next() = if (hasNext) intro(Levels.ALL[index + 1]) else toCard()

    fun toCard() {
        stopVoice()
        selected = null
        phase = Phase.CARD
        version++
    }

    /** A pinch on a piece: its legal moves appear. A second pinch puts it down. */
    fun select(id: String) {
        val p = play ?: return
        if (phase != Phase.PLAYING || view?.busy == true || p.state.piece(id) == null) return
        selected = if (selected == id) null else id
        sound?.play(com.vrgames.jiguan.render.Sfx.PICK)
        view?.showMoves(selected?.let { p.moves(it) } ?: emptyList())
        version++
    }

    fun doMove(m: Move): Boolean {
        val p = play ?: return false
        if (phase != Phase.PLAYING || view?.busy == true) return false
        val r = p.move(m) ?: return false
        selected = null
        view?.showMoves(emptyList())
        view?.play(r.events)
        Log.i(TAG, "move ${m.pieceId} ${m.to} step ${p.steps}")
        version++
        return true
    }

    /** The view calls this when the turn's animation has played out. */
    fun turnShown() {
        val p = play ?: return
        when {
            p.won -> finish(p)
            p.caught -> {
                phase = Phase.CAUGHT
                sound?.play(com.vrgames.jiguan.render.Sfx.WRONG)
            }
        }
        version++
    }

    fun undo() {
        val p = play ?: return
        if (view?.busy == true || !p.undo()) return
        selected = null
        phase = Phase.PLAYING
        view?.showMoves(emptyList())
        view?.snap(p)
        version++
    }

    fun askHint() {
        val p = play ?: return
        hint = p.nextHint()
        Log.i(TAG, "hint ${p.hintsAsked}")
        version++
    }

    fun inspect(cell: Spot) {
        val p = play ?: return
        val text = describe(p, cell)
        if (text != null) say(text)
        view?.flashLinks(cell)
    }

    /** What a mechanism is and what it is connected to: facts, never what to do. */
    private fun describe(p: Play, cell: Spot): String? {
        val l = p.level.layout
        fun where(c: Spot) = LAYER_NAME[c.layer] + (when (l.layers[c.layer][c.y][c.x]) { 'T' -> "的活门"; 'B' -> "的桥"; else -> "" })
        val on = p.rules.active(p.state)
        val links = l.links[cell]?.joinToString("、") { where(it) }
        return when (l.layers[cell.layer][cell.y][cell.x]) {
            'P' -> "压板，连着$links。"
            'L' -> if (cell in p.state.latched) "闩板，已锁定，连着$links。" else "闩板，没有锁定，连着$links。"
            'T' -> if (cell in on) "活门，开着。" else "活门，关着。"
            'B' -> if (cell in on) "桥，升着。" else "桥，落着。"
            'U' -> "升降台，通向上一层。"
            'W' -> "转轮，重的东西走上去，这一层就转 90°。"
            'G' -> "天枢位。"
            else -> null
        }
    }

    private fun finish(p: Play) {
        trophiesBefore = progress.trophies
        val s = p.stars()
        progress.record(p.level.id, s)
        save()
        phase = Phase.RESULT
        sound?.play(com.vrgames.jiguan.render.Sfx.FANFARE)
        view?.celebrate(p)
        Log.i(TAG, "finish ${p.level.id} stars=$s steps=${p.steps} par=${p.level.par}")
    }

    private fun speakChapter(c: Int) {
        stopVoice()
        val a = app ?: return
        runCatching {
            val fd = a.assets.openFd("narration/chapter$c.ogg")
            player = MediaPlayer().apply {
                setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                fd.close()
                setOnCompletionListener { stopVoice() }
                prepare()
                start()
            }
            speaking = true
            music?.duck(true)
        }.onFailure { Log.w(TAG, "chapter $c: ${it.message}") }
    }

    fun replayChapter() {
        if (speaking) stopVoice() else speakChapter(level.chapter)
        version++
    }

    fun stopVoice() {
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
        if (speaking) {
            speaking = false
            music?.duck(false)
        }
    }

    fun tick(dt0: Double) {
        val dt = dt0 * timeScale
        view?.update(dt)
    }

    fun show() {
        if (!progress.gold && !AutoPilot.debug) return
        stopVoice()
        phase = Phase.SHOW
        sound?.play(com.vrgames.jiguan.render.Sfx.FANFARE)
        view?.startShow()
        version++
    }

    fun endShow() {
        view?.endShow()
        toCard()
    }

    private fun save() {
        prefs?.edit()?.putString("best", progress.save())?.apply()
    }

    fun wipe() {
        progress = Progress()
        save()
        heardChapter.clear()
        fresh(Levels.ALL[0])
        phase = Phase.CARD
        version++
    }

    /** The lift one step up (+1) or down (-1): the next tray down comes up to the eyes, or back. */
    fun lift(dir: Int) {
        val to = (liftStep + dir).coerceIn(0, liftSteps.lastIndex)
        if (to == liftStep) {
            // Said on the post itself, where the eyes are; the box giving a small bump would be missed.
            postSay(when {
                liftUseless -> "这一关只有一层，已经在眼前，\n用不着升降。" + (if (firstLiftLevel > 0) "\n第 $firstLiftLevel 关起有好几层才用得上。" else "")
                dir > 0 -> "青铜匣已经升到最高，\n不能再升了。\n捏下面的金箭头可以降回去。"
                else -> "青铜匣已经降到最低，\n不能再降了。\n捏上面的金箭头可以升起来。"
            })
            return
        }
        liftStep = to
        sound?.play(com.vrgames.jiguan.render.Sfx.LIFT)
        val tray = Lift.trayAt(usedLayers, to)
        say("升降：第 ${to + 1} / ${liftSteps.size} 档，" + (if (tray == null) "从上面看整只机关匣。" else "第 ${tray + 1} 层正对视线。"))
        version++
    }

    /** Turns the whole box one step left (-1) or right (+1). */
    fun turn(dir: Int) {
        boxYaw = ((boxYaw + dir * Lift.TURN_STEP) % 360 + 360) % 360
        sound?.play(com.vrgames.jiguan.render.Sfx.LIFT)
        version++
    }

    /** Raises (or lowers) the desk by [step] meters, within ±15 cm. */
    fun nudgeHeight(step: Double) = setHeight(heightAdjust + step)

    private fun setHeight(v: Double, save: Boolean = true) {
        heightAdjust = (Math.round(v.coerceIn(-HEIGHT_MAX, HEIGHT_MAX) * 100) / 100.0)
        val cm = Math.round(heightAdjust * 100).toInt()
        heightLabel = when { cm == 0 -> "标准"; cm > 0 -> "高 $cm 厘米"; else -> "低 ${-cm} 厘米" }
        if (save) prefs?.edit()?.putFloat("height", heightAdjust.toFloat())?.apply()
    }

    const val HEIGHT_MAX = 0.15
    const val HEIGHT_STEP = 0.03

    fun toggleMusic() {
        musicOn = !musicOn
        music?.muted = !musicOn
        prefs?.edit()?.putBoolean("music", musicOn)?.apply()
    }

    fun grant(n: Int) {
        for (l in Levels.ALL) progress.record(l.id, n)
        save()
        version++
    }

    val LAYER_NAME = listOf("底层", "中层", "顶层")
}
