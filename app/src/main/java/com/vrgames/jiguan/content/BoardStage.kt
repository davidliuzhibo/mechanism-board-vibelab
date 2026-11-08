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
    // Which thing a pinch started on, and how far the lever has been moved.
    val dragging = remember { arrayOfNulls<BoardView.Hit>(1) }
    val leverMoved = remember { doubleArrayOf(0.0) }

    val openLevels: () -> Unit = {
        Game.windowOpen = true
        navigator.openWindowContainer(LEVELS_ID)
    }

    DisposableEffect(Unit) {
        Game.init(context)
        Game.closeWindow = {
            navigator.closeWindowContainer(LEVELS_ID)
            Game.windowOpen = false
        }
        val hd = runCatching { head.start() }
        Log.i(TAG, "head ${hd.getOrNull() ?: hd.exceptionOrNull()?.message}")
        onDispose {
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

    SpatialView(
        Modifier.pointerInput(Unit) {
            fun meters(px: Float) = with(density) { converter.dpToLength(px.toDp(), LengthUnit.Meters) }.toDouble()
            detectSpatialDragGesture(
                context = context,
                targetedToEntity = TargetEntity.any(),
                onDragStart = { _, value ->
                    val v = view
                    val hit = v?.hitOf(value.targetEntity)
                    dragging[0] = hit
                    leverMoved[0] = 0.0
                    Log.i(TAG, "pinch $hit phase=${Game.phase}")
                    when (hit) {
                        is BoardView.Hit.PieceHit -> {
                            if (Game.selected != hit.id) Game.select(hit.id)
                            if (Game.selected == hit.id) v.dragStart(hit.id)
                        }
                        is BoardView.Hit.PadHit -> v.padMove(hit.index)?.let { Game.doMove(it) }
                        is BoardView.Hit.CellHit -> Game.inspect(hit.cell)
                        else -> Unit
                    }
                },
                onDragEnd = {
                    val v = view
                    when (dragging[0]) {
                        is BoardView.Hit.PieceHit -> v?.dragEnd()?.let { Game.doMove(it) }
                        BoardView.Hit.LeverHit -> when {
                            leverMoved[0] > 0.03 -> Game.spread = true
                            leverMoved[0] < -0.03 -> Game.spread = false
                            else -> Game.spread = !Game.spread
                        }
                        else -> Unit
                    }
                    dragging[0] = null
                },
                onDragCancel = {
                    view?.dragEnd()
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
                when (dragging[0]) {
                    is BoardView.Hit.PieceHit -> if (v.isDragging) v.dragBy(d)
                    BoardView.Hit.LeverHit -> leverMoved[0] += dy
                    else -> Unit
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
            AttachmentPanel(PANEL_MENU) {
                MenuPanel(converter, openLevels, faceMe = { Game.faceRequest++ }, quit = { context.findActivity()?.finishAndRemoveTask() }, version = appVersion)
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
            v.root.setPos(Vector3(0f, BOARD_Y, -AHEAD))
            group.addChild(v.root)
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
private val MODELS = listOf("sky", "desk", "box_base", "tray", "lever", "piece_key", "piece_heavy", "piece_light", "piece_guard", "trophy")
private const val BOARD_Y = 0.48f
private const val AHEAD = 0.62f
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
    put(PANEL_CARD, Vector3(0f, 1.38f, -0.95f), 0f)
    put(PANEL_INTRO, Vector3(0f, 1.38f, -0.95f), 0f)
    put(PANEL_RESULT, Vector3(0f, 1.38f, -0.95f), 0f)
    put(PANEL_CAUGHT, Vector3(0f, 1.2f, -0.72f), 0f)
    put(PANEL_HUD, Vector3(-0.58f, 1f, -0.62f), -8f, 42f)
    put(PANEL_MENU, Vector3(-0.58f, 1f, -0.62f), -8f, 42f)
    put(PANEL_HINT, Vector3(0.58f, 1.02f, -0.62f), -8f, -42f)
    put(PANEL_NOTE, Vector3(0f, 0.86f, -0.34f), -35f)
    put(PANEL_SHOW, Vector3(0f, 0.86f, -0.34f), -35f)
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

    fun again() = facing.again()

    fun frame(eye: Vector3?, rot: Quat?, dt: Double) {
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
            group?.setPos(Vector3(0f, eye.y - 1.2f, 0f))
            board?.setPos(Vector3(0f, BOARD_Y, -AHEAD))
            board?.transform()?.eulerAngles = EulerAngles(0f, 0f, 0f)
            board?.transform()?.setScaleVector(Vector3(1f))
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
