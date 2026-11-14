package com.vrgames.jiguan.content

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.pico.spatial.core.ecs.DirectionalLightComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.LoadType
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.event.SceneEvents
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.TextureCreateOption
import com.pico.spatial.core.ecs.resource.TextureEncoding
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.lifecycle.Cancellable
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.tracking.DataProvider
import com.pico.spatial.tracking.hmd.HMDTrackingProvider
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.content.SpatialViewAttachments
import com.pico.spatial.ui.foundation.content.toColor4
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialDragGesture
import com.pico.spatial.ui.foundation.gesture.detectSpatialTapGesture
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import com.vrgames.jiguan.core.Vec3
import com.vrgames.jiguan.game.AutoPilot
import com.vrgames.jiguan.game.Game
import com.vrgames.jiguan.render.BoardView
import com.vrgames.jiguan.render.Music
import com.vrgames.jiguan.render.Sound
import com.vrgames.jiguan.render.color
import com.vrgames.jiguan.render.model
import com.vrgames.jiguan.render.setPos
import com.vrgames.jiguan.render.setScale
import com.vrgames.jiguan.render.shared
import com.vrgames.jiguan.render.transform
import com.vrgames.jiguan.render.unlit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private const val TAG = "BoardStage"

/** The craftsman's desk in front of the seated player, with the bronze box on it. Everything is set down once and stays. */
@Composable
fun BoardStage() {
    val context = LocalContext.current
    val density = LocalDensity.current
    val converter = LocalPhysicalLengthConverter.current
    val navigator = LocalSpatialNavigator.current
    val main = remember { Handler(Looper.getMainLooper()) }
    val head = remember { HMDTrackingProvider() }
    val subscription = remember { arrayOfNulls<Cancellable>(1) }
    var view by remember { mutableStateOf<BoardView?>(null) }
    val seat = remember { Seat() }
    val panels = remember { HashMap<String, Entity>() }
    val frames = remember { HashMap<String, Entity>() }
    val appVersion = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" }
    // Which thing a pinch started on.
    val dragging = remember { arrayOfNulls<BoardView.Hit>(1) }

    val openLevels: () -> Unit = {
        Game.windowOpen = true
        navigator.openWindowContainer(LEVELS_ID)
    }

    DisposableEffect(Unit) {
        Game.init(context)
        com.vrgames.jiguan.platform.AppExit.stageAlive = true
        Game.closeWindow = {
            navigator.closeWindowContainer(LEVELS_ID)
            Game.windowOpen = false
        }
        val hd = runCatching { head.start() }
        Log.i(TAG, "head ${hd.getOrNull() ?: hd.exceptionOrNull()?.message}")
        onDispose {
            com.vrgames.jiguan.platform.AppExit.stageAlive = false
            runCatching { head.stop() }
            subscription[0]?.cancel()
            Game.closeWindow()
            Game.closeWindow = {}
            Game.stopVoice()
        }
    }
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val music = Music(context).also {
            it.muted = !Game.musicOn
            Game.music = it
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) music.resume() else if (e == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) music.pause()
            // Home (or the headset off): the narrator stops too (2026-11-13: found in 标本柜, the voice went on after Home).
            if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP) Game.stopVoice()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            music.release()
            Game.music = null
        }
    }
    LaunchedEffect(Game.playVersion, view) {
        val v = view ?: return@LaunchedEffect
        Game.play?.let { v.build(it) }
    }
    LaunchedEffect(Game.progress.trophies, Game.version, view) { view?.trophies(Game.progress.trophies) }
    LaunchedEffect(Game.faceRequest) { if (Game.faceRequest > 0) seat.again() }
    LaunchedEffect(Game.postNoteVersion) {
        val n = Game.postNote ?: return@LaunchedEffect
        kotlinx.coroutines.delay(4000)
        if (Game.postNote == n) Game.postNote = null
    }
    LaunchedEffect(Game.noteVersion) {
        val n = Game.note ?: return@LaunchedEffect
        kotlinx.coroutines.delay(if (n.length > 24) 6000 else 3500)
        if (Game.note == n) Game.note = null
    }

    fun refresh() {
        val ph = Game.phase
        fun show(id: String, on: Boolean) = panels[id]?.let { if (it.enabled != on) it.enabled = on }
        val menu = Game.menuOpen
        show(PANEL_CARD, ph == Game.Phase.CARD)
        show(PANEL_INTRO, ph == Game.Phase.INTRO)
        show(PANEL_RESULT, ph == Game.Phase.RESULT)
        show(PANEL_SHOW, ph == Game.Phase.SHOW)
        show(PANEL_CAUGHT, ph == Game.Phase.CAUGHT)
        show(PANEL_HUD, (ph == Game.Phase.PLAYING) && !menu)
        show(PANEL_HINT, ph == Game.Phase.PLAYING && Game.hint != null)
        show(PANEL_MENU, menu)
        show(PANEL_NOTE, Game.note != null)
    }

    SideEffect { refresh() }

    /**
     * The lift's control post: one step a press of an arrow, or a step per push of the hand (device 2026-11-14). A quick
     * pinch can come as a tap and as a drag: once only.
     */
    val lastControl = remember { longArrayOf(0L) }
    val stick = remember { com.vrgames.jiguan.core.StickDrag() }
    fun control(hit: BoardView.Hit, pushed: Boolean = false) {
        val now = android.os.SystemClock.uptimeMillis()
        if (!pushed && now - lastControl[0] < 350) return
        lastControl[0] = now
        Log.i(TAG, "control $hit${if (pushed) " (pushed)" else ""}")
        main.post {
            when (hit) {
                BoardView.Hit.LiftUp -> Game.lift(1)
                BoardView.Hit.LiftDown -> Game.lift(-1)
                BoardView.Hit.TurnLeft -> Game.turn(-1)
                BoardView.Hit.TurnRight -> Game.turn(1)
                else -> Unit
            }
        }
    }

    SpatialView(
        Modifier.pointerInput(1) {
            detectSpatialTapGesture(context, TargetEntity.any()) { value ->
                val hit = view?.hitOf(value.targetEntity)
                Log.i(TAG, "tap $hit")
                if (hit != null && hit.isControl && hit != BoardView.Hit.Post) control(hit)
            }
        }.pointerInput(Unit) {
            fun meters(px: Float) = with(density) { converter.dpToLength(px.toDp(), LengthUnit.Meters) }.toDouble()
            detectSpatialDragGesture(
                context = context,
                targetedToEntity = TargetEntity.any(),
                onDragStart = { _, value ->
                    val v = view
                    val hit = v?.hitOf(value.targetEntity)
                    dragging[0] = hit
                    Log.i(TAG, "pinch $hit phase=${Game.phase}")
                    when (hit) {
                        is BoardView.Hit.PieceHit -> {
                            if (Game.selected != hit.id) Game.select(hit.id)
                            if (Game.selected == hit.id) v.dragStart(hit.id)
                        }
                        is BoardView.Hit.PadHit -> v.padMove(hit.index)?.let { Game.doMove(it) }
                        is BoardView.Hit.CellHit -> Game.inspect(hit.cell)
                        BoardView.Hit.LeverHit -> v.leverStart()
                        // A press is taken when the pinch ends without a push; the tap detector may take it first.
                        BoardView.Hit.LiftUp, BoardView.Hit.LiftDown, BoardView.Hit.TurnLeft, BoardView.Hit.TurnRight, BoardView.Hit.Post -> stick.start()
                        else -> Unit
                    }
                },
                onDragEnd = {
                    val v = view
                    dragging[0]?.let { h -> if (h.isControl && h != BoardView.Hit.Post && stick.wasPress()) control(h) }
                    when (dragging[0]) {
                        is BoardView.Hit.PieceHit -> v?.dragEnd()?.let { Game.doMove(it) }
                        BoardView.Hit.LeverHit -> v?.let { lv ->
                            val open = lv.leverEnd(Game.spread)
                            if (open != Game.spread) Game.sound?.play(com.vrgames.jiguan.render.Sfx.LIFT)
                            Game.spread = open
                        }
                        else -> Unit
                    }
                    dragging[0] = null
                },
                onDragCancel = {
                    dragging[0]?.let { h -> if (h.isControl && h != BoardView.Hit.Post && stick.wasPress()) control(h) }
                    view?.dragEnd()
                    if (dragging[0] == BoardView.Hit.LeverHit) view?.let { Game.spread = it.leverEnd(Game.spread) }
                    dragging[0] = null
                },
            ) { value ->
                val v = view ?: return@detectSpatialDragGesture
                // Compose y points down; space y points up. The hand moves in the room, the box is turned with the seat.
                val dx = meters(value.dragAmount.x)
                val dy = -meters(value.dragAmount.y)
                val dz = meters(value.dragAmount.z)
                val a = Math.toRadians(seat.yaw)
                val d = Vec3(dx * cos(a) - dz * sin(a), dy, dx * sin(a) + dz * cos(a))
                // Pieces move in the box's own space, which is turned on the lift; the lever stands on the desk.
                val b = Math.toRadians(-v.yawNow)
                val inBox = Vec3(d.x * cos(b) + d.z * sin(b), d.y, -d.x * sin(b) + d.z * cos(b))
                when (dragging[0]) {
                    is BoardView.Hit.PieceHit -> if (v.isDragging) v.dragBy(inBox)
                    BoardView.Hit.LeverHit -> v.leverBy(d)
                    else -> if (dragging[0]?.isControl == true) {
                        // Right and up as the player sees them from the seat.
                        when (stick.by(d.x, d.y)) {
                            com.vrgames.jiguan.core.StickDrag.Move.LIFT_UP -> control(BoardView.Hit.LiftUp, pushed = true)
                            com.vrgames.jiguan.core.StickDrag.Move.LIFT_DOWN -> control(BoardView.Hit.LiftDown, pushed = true)
                            com.vrgames.jiguan.core.StickDrag.Move.TURN_LEFT -> control(BoardView.Hit.TurnLeft, pushed = true)
                            com.vrgames.jiguan.core.StickDrag.Move.TURN_RIGHT -> control(BoardView.Hit.TurnRight, pushed = true)
                            null -> Unit
                        }
                    }
                }
            }
        },
        attachments = {
            AttachmentPanel(PANEL_CARD) { CardPanel(converter, openLevels) }
            AttachmentPanel(PANEL_INTRO) { IntroPanel(converter) }
            AttachmentPanel(PANEL_HUD) { HudPanel(converter) }
            AttachmentPanel(PANEL_HINT) { HintPanel(converter) }
            AttachmentPanel(PANEL_CAUGHT) { CaughtPanel(converter) }
            AttachmentPanel(PANEL_RESULT) { ResultPanel(converter, openLevels) }
            AttachmentPanel(PANEL_SHOW) { ShowPanel(converter) }
            AttachmentPanel(PANEL_NOTE) { NotePanel(converter) }
            AttachmentPanel(PANEL_TAG_POST) { NameTag(converter, "升降 · 旋转", "两指捏一下金箭头：升降一层\n两指捏一下绿箭头：左右转动\n捏一下就松开，不用拖", Game.postNote) }
            AttachmentPanel(PANEL_TAG_LEVER) { NameTag(converter, "开合", "捏住往上抬：拉开\n往下压：合拢") }
            AttachmentPanel(PANEL_MENU) {
                MenuPanel(converter, openLevels, faceMe = { Game.faceRequest++ }, quit = { Game.closeWindow(); com.vrgames.jiguan.platform.AppExit.finishAll(context) }, version = appVersion)
            }
        },
        update = { _, attachments ->
            view?.let {
                attach(seat, attachments, panels, frames)
                refresh()
            }
        },
        initial = { content, attachments ->
            // One warm light from the workshop's window, no real-time shadows; the image-based light does the rest.
            content.addEntity(Entity().apply {
                components.set(DirectionalLightComponent(Color(0xFFFFDFB8).toColor4(), 1100f, false))
                components[TransformComponent::class.java]?.setEulerAngles(EulerAngles(-58f, 22f, 0f))
            })
            val tex = HashMap<String, TextureResource>()
            var ibl: TextureResource? = null
            var skyTex: TextureResource? = null
            withContext(Dispatchers.IO) {
                for (name in TEXTURES) {
                    runCatching { TextureResource.load("textures/$name.jpg", LoadType.FROM_ASSETS, TextureCreateOption().apply { if (name.endsWith("_n")) textureEncoding = TextureEncoding.LINEAR }).shared() }
                        .onSuccess { tex[name] = it }
                        .onFailure { Log.i(TAG, "texture $name: ${it.message}") }
                }
                skyTex = runCatching { TextureResource.load("textures/sky_workshop.jpg", LoadType.FROM_ASSETS, TextureCreateOption()).shared() }.getOrNull()
                ibl = runCatching { TextureResource.load("textures/ibl_workshop.ktx", LoadType.FROM_ASSETS, TextureCreateOption().apply { textureEncoding = TextureEncoding.LINEAR }).shared() }.getOrNull()
            }
            val models = HashMap<String, Entity>()
            for (name in MODELS) {
                runCatching { Entity.loadSuspend("asset://models/$name.glb") }
                    .onSuccess { models[name] = it }
                    .onFailure { Log.i(TAG, "model $name: ${it.message}") }
            }
            for ((id, _) in FRAME_SIZE) {
                runCatching { Entity.loadSuspend("asset://panels/panel_$id.glb") }
                    .onSuccess { frames[id] = it }
                    .onFailure { Log.i(TAG, "frame $id: ${it.message}") }
            }
            val world = Entity()
            val sky = models["sky"]?.clone(Entity.CloneOptions(true, true))
                ?: model(com.pico.spatial.core.ecs.resource.MeshResource.createSphere(1f), unlit(color(0.08f, 0.07f, 0.06f)).apply { setCullingMode(MaterialCullingMode.FRONT) })
            sky.setScale(if (models["sky"] != null) 9f / 12f else 9f)
            sky.setPos(Vector3(0f, 1.2f, 0f))
            skyTex?.let { t -> setSky(sky, UnlitMaterial.create().apply { setBaseColorTexture(t) }) }
            world.addChild(sky)
            content.addEntity(world)
            val group = Entity()
            world.addChild(group)
            models["desk"]?.clone(Entity.CloneOptions(true, true))?.let { d ->
                // The model is 1 m along its longest side; its top comes to the board's foot.
                d.setScale(DESK_SCALE)
                d.setPos(Vector3(0f, BOARD_Y - DESK_TOP * DESK_SCALE, -AHEAD))
                // Its long side comes along z: turn it across the player.
                d.transform().eulerAngles = EulerAngles(0f, 90f, 0f)
                group.addChild(d)
            }
            val v = BoardView(models, tex)
            group.addChild(v.deskNode)
            group.addChild(v.root)
            v.home = Vector3(0f, BOARD_Y, -AHEAD)
            seat.world = world
            seat.group = group
            seat.board = v.root
            attach(seat, attachments, panels, frames)
            ibl?.let { v.useIbl(it) }
            Game.view = v
            Game.sound = Sound(v.root).also { it.load() }
            view = v
            Game.play?.let { v.build(it) } ?: run { Game.intro(com.vrgames.jiguan.core.Levels.ALL[0]); Game.toCard(); Game.play?.let { v.build(it) } }
            AutoPilot.ready()
            subscription[0] = content.subscribe(SceneEvents.Update::class.java) { e ->
                val dt = e.deltaTime.toDouble().coerceIn(0.0, 0.1)
                val pose = runCatching { if (head.state == DataProvider.State.STARTED) head.latestData?.hmdPose else null }.getOrNull()
                seat.frame(pose?.position, pose?.rotation, dt)
                pose?.position?.let { v.eye = seat.inBoard(it, v.liftNow, v.yawNow) }
                main.post { Game.tick(dt) }
            }
        },
    )
}

private fun setSky(e: Entity, m: Material) {
    e.components[ModelComponent::class.java]?.materials?.let { mats -> for (i in 0 until mats.size) mats[i] = m }
    e.getChildren().forEach { setSky(it, m) }
}

private val TILES = listOf("tile_floor", "tile_wall", "tile_plate", "tile_latch", "tile_door_l", "tile_door_r", "tile_bridge", "tile_lift", "tile_wheel", "tile_goal")
private val TEXTURES = TILES + TILES.map { "${it}_n" }
private val MODELS = listOf("sky", "desk", "box_base", "tray", "lever_base", "lever_arm", "piece_key", "piece_heavy", "piece_light", "piece_guard", "trophy", "prop_lamp", "prop_ding", "prop_jade", "prop_slips", "prop_luopan")
// The desk top 0.86 m below the eyes and the box 0.5 m out: with the trays leaning, all three can be seen into from the
// seat (2026-11-13 review: seated, the cells could not be seen). Checked by BoxSightTest.
private val BOARD_Y = (1.2 - com.vrgames.jiguan.core.BoxLayout.DESK_BELOW_EYE).toFloat()
private val AHEAD = com.vrgames.jiguan.core.BoxLayout.AHEAD.toFloat()
private const val DESK_SCALE = 1.5f
private const val DESK_TOP = 0.335f

private fun attach(seat: Seat, a: SpatialViewAttachments, panels: HashMap<String, Entity>, frames: HashMap<String, Entity>) {
    val group = seat.group ?: return
    fun put(id: String, pos: Vector3, pitch: Float, yaw: Float = 0f) {
        val e = a.entity(id) ?: return
        if (panels[id] === e) return
        panels[id] = e
        group.addChild(e)
        e.setPos(pos)
        e.transform().eulerAngles = EulerAngles(pitch, yaw, 0f)
        frames[id]?.let { f ->
            f.setPos(Vector3(0f, 0f, -0.006f))
            e.addChild(f)
        }
    }
    // Eye height is taken as 1.2 m here: the whole group is moved to the player's real height once.
    // High enough that the open box's top tray does not cover the buttons along the panel's bottom edge.
    // Device 2026-11-14: the box went 33 cm further out; the cards stand behind it now, above its raised top tray.
    put(PANEL_CARD, Vector3(0f, 1.45f, -1.28f), 0f)
    put(PANEL_INTRO, Vector3(0f, 1.47f, -1.28f), 0f)
    put(PANEL_RESULT, Vector3(0f, 1.45f, -1.28f), 0f)
    put(PANEL_CAUGHT, Vector3(0f, 1.38f, -1.25f), 0f)
    // Device 2026-11-14: 7 cm further left, clear of the control post's name plate.
    put(PANEL_HUD, Vector3(-0.65f, 1f, -0.62f), -8f, 42f)
    put(PANEL_MENU, Vector3(-0.65f, 1f, -0.62f), -8f, 42f)
    put(PANEL_HINT, Vector3(0.58f, 1.02f, -0.62f), -8f, -42f)
    // Above the box, not between the eyes and the trays.
    put(PANEL_NOTE, Vector3(0f, 1.4f, -1.25f), 0f)
    put(PANEL_SHOW, Vector3(0f, 1.4f, -1.25f), 0f)
    // Name plates over the control post and the lever (device 2026-11-14: the two had no names), leaning to the seat.
    val post = BoardView.POST_AT
    val lever = BoardView.LEVER_AT
    put(PANEL_TAG_POST, Vector3(post.x, BOARD_Y + 0.4f, -AHEAD + post.z), -18f, 20f)
    put(PANEL_TAG_LEVER, Vector3(lever.x, BOARD_Y + 0.27f, -AHEAD + lever.z - 0.08f), -18f, -18f)
}

/**
 * Where the player sits: the world turned once to the mean heading of the first second of tracking (and on
 * 「摆到我正前方」), and the desk brought to the player's own eye height. Then it stays, whatever the head does.
 */
private class Seat {
    var world: Entity? = null
    var group: Entity? = null
    var board: Entity? = null
    private val facing = com.vrgames.jiguan.core.Facing()
    private var waited = 0.0
    private var shown = false
    val yaw get() = facing.yaw
    private var origin: Vector3? = null
    private var lift = Float.NaN
    private var eyeLift = 0f

    /** A point of the room in the box's own space: the box raised by [rise] on its lift and turned by [turn] degrees. */
    fun inBoard(p: Vector3, rise: Double = 0.0, turn: Double = 0.0): Vec3? {
        val o = origin ?: return null
        val a = Math.toRadians(facing.yaw)
        val x = (p.x - o.x).toDouble()
        val z = (p.z - o.z).toDouble()
        val q = Vec3(x * cos(a) - z * sin(a), (p.y - lift - BOARD_Y).toDouble() - rise, x * sin(a) + z * cos(a) + AHEAD)
        val b = Math.toRadians(-turn)
        return Vec3(q.x * cos(b) + q.z * sin(b), q.y, -q.x * sin(b) + q.z * cos(b))
    }

    fun again() = facing.again()

    fun frame(eye: Vector3?, rot: Quat?, dt: Double) {
        // The player's own height setting moves the desk at once, panels and all.
        val want = eyeLift + Game.heightAdjust.toFloat()
        if (want != lift) {
            lift = want
            group?.setPos(Vector3(0f, lift, 0f))
        }
        if (!shown) {
            waited += dt
            if (facing.placed || waited >= com.vrgames.jiguan.core.Facing.GIVE_UP_S) shown = true
            world?.let { if (it.enabled != shown) it.enabled = shown }
        }
        val d = Game.debugView?.split(",")?.mapNotNull { it.trim().toFloatOrNull() }
        if (d != null && d.size >= 6) {
            board?.setPos(Vector3(d[0], d[1], d[2]))
            board?.transform()?.eulerAngles = EulerAngles(d[5], d[3], 0f)
            board?.transform()?.setScaleVector(Vector3(d[4]))
            return
        }
        if (rot == null || eye == null) return
        val f = rot.rotateVector(Vector3(0f, 0f, -1f))
        // Looking nearly straight up or down says nothing about the heading.
        val head = if (hypot(f.x, f.z) < 0.2f) null else com.vrgames.jiguan.core.Facing.heading(f.x.toDouble(), f.z.toDouble())
        if (facing.update(head, dt)) {
            shown = true
            world?.enabled = true
            world?.let {
                it.setPos(Vector3(eye.x, 0f, eye.z))
                it.transform().eulerAngles = EulerAngles(0f, facing.yaw.toFloat(), 0f)
            }
            eyeLift = eye.y - 1.2f
            origin = Vector3(eye.x, 0f, eye.z)
            board?.transform()?.setScaleVector(Vector3(1f))
            Game.view?.home = Vector3(0f, BOARD_Y, -AHEAD)
            Log.i(TAG, "facing placed at ${facing.yaw}, eye $eye")
        }
    }
}

private fun android.content.Context.findActivity(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}
