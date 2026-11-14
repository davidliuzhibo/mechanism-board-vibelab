package com.vrgames.jiguan.render

import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.EnvironmentLightingSettingsComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.ImageBasedLightComponent
import com.pico.spatial.core.ecs.ImageBasedLightReceiverComponent
import com.pico.spatial.core.ecs.ImageBasedLightSource
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.OpacityControllerComponent
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MeshInstancesResource
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Transform
import com.pico.spatial.core.math.Vector3
import com.vrgames.jiguan.core.Ev
import com.vrgames.jiguan.core.Kind
import com.vrgames.jiguan.core.LAYERS
import com.vrgames.jiguan.core.Layout
import com.vrgames.jiguan.core.Move
import com.vrgames.jiguan.core.Play
import com.vrgames.jiguan.core.SIZE
import com.vrgames.jiguan.core.BoxLayout
import com.vrgames.jiguan.core.Sight
import com.vrgames.jiguan.core.Spot
import com.vrgames.jiguan.core.Vec3
import com.vrgames.jiguan.game.Game
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The bronze box on the desk. Its three trays open like a tool box: each higher tray moves up and back, so every
 * layer can be seen and pinched from the seat, with rods showing what is connected to what. Piece positions are
 * computed from the rules' world coordinates, and the mechanism tiles sit on a node that turns with their layer.
 */
class BoardView(private val models: Map<String, Entity>, private val tex: Map<String, TextureResource>) {
    /** The box: it rises on its lift and turns (device 2026-11-13, v0.4). Box space is this entity's frame. */
    val root = Entity()
    /** The desk top (the box's foot at its lowest), with the tomb props and the lift's control post; it never moves. */
    val deskNode = Entity()
    /** Where the stage stands the box's foot at its lowest (group frame). */
    var home = Vector3(0f, 0f, 0f)
        set(v) { field = v; deskNode.setPos(v); placeBox() }
    /** How high the box stands on its lift and how far it is turned, as shown now (they glide to Game's values). */
    @Volatile var liftNow = 0.0; private set
    @Volatile var yawNow = 0.0; private set
    private val pillar = model(MeshResource.createCylinder(1f, 0.07f), pbr(color(0.42f, 0.32f, 0.17f), 0.85f, 0.45f, Color4(0.05f, 0.04f, 0.02f, 1f)))
    private val iblSource = Entity()
    private val trays = Array(LAYERS) { Entity() }
    private val spins = Array(LAYERS) { Entity() }
    private val floors = Array(LAYERS) { Cells("f$it", material("tile_floor"), Vector3(TILE, 0.008f, TILE), -0.004f) }
    private val walls = Array(LAYERS) { Cells("w$it", material("tile_wall"), Vector3(TILE, WALL_H, TILE), WALL_H / 2) }
    /** Thin gold lines between the cells, so the grid reads from the seat even past low walls (2026-11-13). */
    private val gridLit = glow(color(0.86f, 0.66f, 0.3f, 1f)).shared()
    private val grids = Array(LAYERS) { gridLines() }
    private val frames = Array(LAYERS) { models["tray"]?.clone(Entity.CloneOptions(true, true)) ?: model(MeshResource.createBox(Vector3(0.44f, 0.012f, 0.44f)), pbr(color(0.3f, 0.25f, 0.15f), 0.8f, 0.5f)) }
    private val posts = Array(4) { model(MeshResource.createCylinder(1f, 0.006f), bronze) }
    private val mechs = ArrayList<Mech>()
    private val rods = ArrayList<Rod>()
    private val holders = HashMap<String, Entity>()
    private val guards = ArrayList<Entity>()
    private val arrows = ArrayList<Entity>()
    // Declared before init, which builds the control post. The lift's gold arrows go dark where the box can go no further (device 2026-11-14: level 1 has one tray only).
    private val liftGold = glow(color(1f, 0.8f, 0.36f, 1f)).shared()
    private val liftDark = pbr(color(0.3f, 0.26f, 0.18f), 0.6f, 0.6f, Color4(0f, 0f, 0f, 1f)).shared()
    private val liftCones = arrayOfNulls<Entity>(2)
    private val liftLit = booleanArrayOf(true, true)
    private val pads = Array(4) { pad() }
    private var padMoves: List<Move> = emptyList()
    private val hits = HashMap<Entity, Hit>()
    private val lever = Entity()
    private val leverArm = Entity()
    /** While the hand holds the lever: how far open it has been pulled, 0 closed to 1 open. */
    private var leverHold: Double? = null
    private val trophies = ArrayList<Entity>()
    private val sparks = Sparks("spark", 60, MeshResource.createBox(Vector3(0.008f, 0.008f, 0.008f)), glow(color(1f, 0.82f, 0.45f, 1f)))

    private var play: Play? = null
    private var layout: Layout? = null
    private var on: Set<Spot> = emptySet()
    private var rot = intArrayOf(0, 0, 0)
    private val at = HashMap<String, Spot>()
    private val anims = ArrayDeque<Anim>()
    private var anim: Anim? = null
    private var animT = 0.0
    private var spreadNow = 1.0
    private var flash = 0.0
    private var flashCell: Spot? = null
    private var showT = -1.0
    private var dragId: String? = null
    private var dragOffset = Vec3.ZERO
    private var clock = 0.0
    /** The player's eye in box space, kept up to date by the stage. */
    @Volatile var eye: Vec3? = null
    private var sightT = 0.0
    /** Pieces seen through (another piece behind them), pieces raised (hidden behind one), cells stepped aside. */
    private val ghosted = HashSet<String>()
    private val raised = HashSet<String>()
    private val cleared = HashSet<Spot>()

    val busy get() = anim != null || anims.isNotEmpty()

    sealed interface Hit {
        data class PieceHit(val id: String) : Hit
        data class PadHit(val index: Int) : Hit
        data class CellHit(val cell: Spot) : Hit
        data object LeverHit : Hit
        /** The control post on the desk: the lift up and down, the box turned left and right. */
        data object LiftUp : Hit
        data object LiftDown : Hit
        data object TurnLeft : Hit
        data object TurnRight : Hit
        /** The post itself: pinched and pushed like a stick. */
        data object Post : Hit
        val isControl get() = this == LiftUp || this == LiftDown || this == TurnLeft || this == TurnRight || this == Post
    }

    private sealed interface Anim {
        val seconds: Double
        data class Walk(val id: String, val from: Spot, val to: Spot, override val seconds: Double = 0.28) : Anim
        data class Drop(val id: String, val from: Spot, val to: Spot, override val seconds: Double = 0.42) : Anim
        data class Rise(val id: String, val from: Spot, val to: Spot, override val seconds: Double = 0.55) : Anim
        data class Turn(val layer: Int, override val seconds: Double = 0.7) : Anim
        data class Work(val set: Set<Spot>, override val seconds: Double = 0.3) : Anim
        data class Pause(override val seconds: Double) : Anim
    }

    private class Mech(val cell: Spot, val kind: Char, val body: Entity, val parts: List<Entity>) {
        var amount = 0f
        var target = 0f
    }

    private class Rod(val from: Spot, val to: Spot, val e: Entity)

    /** All the plain cells of one kind on one layer: a single draw call. */
    private inner class Cells(val name: String, material: Material, size: Vector3, val y: Float) {
        val entity: Entity = ModelEntity(MeshResource.createBox(size), material)
        private val ids = Array(SIZE * SIZE) { "$name$it" }
        private val instances = MeshInstancesResource.create(name, ids.map { MeshInstancesResource.Instance(it, HIDDEN) })

        init { entity.components[ModelComponent::class.java]!!.meshInstances = instances }

        fun show(cells: List<Pair<Int, Int>>) {
            for (i in ids.indices) {
                val c = cells.getOrNull(i)
                instances.update(ids[i], if (c == null) HIDDEN else Transform(Vector3(local(c.first), y, local(c.second)), EulerAngles(0f, 0f, 0f), Vector3(1f)))
            }
        }
    }

    init {
        root.addChild(iblSource)
        models["box_base"]?.clone(Entity.CloneOptions(true, true))?.let { b ->
            // A low plinth (2026-11-13: the trays came down so a seated player sees into all three).
            b.transform().setScaleVector(Vector3(0.5f, (BASE_Y - 0.004) .toFloat() / 0.509f, 0.5f))
            b.setPos(Vector3(0f, -0.002f, 0f))
            root.addChild(b)
        }
        for (l in 0 until LAYERS) {
            root.addChild(trays[l])
            trays[l].addChild(spins[l])
            trays[l].addChild(frames[l])
            spins[l].addChild(floors[l].entity)
            spins[l].addChild(walls[l].entity)
            spins[l].addChild(grids[l])
        }
        posts.forEach { root.addChild(it) }
        pads.forEachIndexed { i, p ->
            root.addChild(p)
            p.enabled = false
            hits[p] = Hit.PadHit(i)
        }
        // The lever at the box's right: a fixed base, and an arm on its pivot that the hand lifts to open the trays
        // and presses down to close them (2026-11-10 review: the lever should move, and move the way the hand goes).
        models["lever_base"]?.clone(Entity.CloneOptions(true, true))?.let {
            it.setScale(LEVER_SCALE)
            lever.addChild(it)
        }
        val arm = models["lever_arm"]?.clone(Entity.CloneOptions(true, true))?.also { it.setScale(LEVER_SCALE) }
            ?: model(MeshResource.createBox(Vector3(0.025f, 0.025f, 0.17f)), bronze).also { it.setPos(Vector3(0f, 0f, 0.085f)) }
        if (models["lever_arm"] != null) arm.setPos(Vector3(-LEVER_PIVOT.x, -LEVER_PIVOT.y, -LEVER_PIVOT.z))
        leverArm.addChild(arm)
        leverArm.setPos(LEVER_PIVOT)
        // The hit box rides on the arm, from the pivot out past the ring.
        leverArm.components.set(CollisionComponent(listOf(ShapeResource.createBox(Vector3(0.07f, 0.07f, 0.2f)).offsetByTranslation(Vector3(0f, 0f, 0.09f))), PhysicsMaterialResource()))
        leverArm.components.set(InteractableComponent())
        leverArm.components.set(HoverEffectComponent())
        lever.addChild(leverArm)
        lever.setPos(LEVER_AT)
        hits[leverArm] = Hit.LeverHit
        // On the desk, not on the box: it stays at the player's right whichever way the box is turned or raised.
        deskNode.addChild(lever)
        poseLever()
        root.addChild(sparks.entity)
        tombProps()
        controlPost()
        // The lift's column, from the desk up to the plinth; only seen while the box is raised.
        pillar.enabled = false
        root.addChild(pillar)
        placeTrays()
    }

    /**
     * The lift's handle, on the desk at the front left of the box (device 2026-11-13: "a handle at the side"): a bronze
     * post with a gold arrow up and one down (one tray a step), and two jade arrows left and right (the box turns 45°).
     * Device 2026-11-14: pinches on it were often missed and pushing it did nothing. The arrows and their pinch boxes are
     * larger, the post's own column takes a pinch too, and a pinch anywhere on it can be pushed like a stick
     * ([com.vrgames.jiguan.core.StickDrag]). It stands further left, clear of the raised box, so the hand's ray to it
     * does not pass the trays.
     */
    private fun controlPost() {
        val post = Entity()
        post.setPos(POST_AT)
        val metal = pbr(color(0.52f, 0.39f, 0.2f), 0.85f, 0.42f, Color4(0.05f, 0.04f, 0.02f, 1f))
        post.addChild(model(MeshResource.createCylinder(0.014f, 0.062f), metal).apply { setPos(Vector3(0f, 0.007f, 0f)) })
        post.addChild(model(MeshResource.createCylinder(0.24f, 0.012f), metal).apply { setPos(Vector3(0f, 0.12f, 0f)) })
        post.components.set(CollisionComponent(listOf(ShapeResource.createBox(Vector3(0.05f, 0.16f, 0.05f)).offsetByTranslation(Vector3(0f, 0.16f, 0f))), PhysicsMaterialResource()))
        post.components.set(InteractableComponent())
        post.components.set(HoverEffectComponent())
        hits[post] = Hit.Post
        val jade = glow(color(0.45f, 0.86f, 0.66f, 1f)).shared()
        fun knob(hit: Hit, mat: com.pico.spatial.core.ecs.resource.Material, at: Vector3, roll: Float): Entity {
            val k = Entity()
            k.setPos(at)
            val cone = model(MeshResource.createCone(0.042f, 0.026f), mat).apply { transform().eulerAngles = EulerAngles(0f, 0f, roll) }
            k.addChild(cone)
            k.components.set(CollisionComponent(listOf(ShapeResource.createBox(Vector3(0.068f, 0.068f, 0.068f))), PhysicsMaterialResource()))
            k.components.set(InteractableComponent())
            k.components.set(HoverEffectComponent())
            hits[k] = hit
            post.addChild(k)
            return cone
        }
        liftCones[0] = knob(Hit.LiftUp, liftGold, Vector3(0f, 0.285f, 0f), 0f)
        liftCones[1] = knob(Hit.LiftDown, liftGold, Vector3(0f, 0.04f, 0f), 180f)
        liftLit[0] = true; liftLit[1] = true
        knob(Hit.TurnLeft, jade, Vector3(-0.065f, 0.16f, 0f), 90f)
        knob(Hit.TurnRight, jade, Vector3(0.065f, 0.16f, 0f), -90f)
        deskNode.addChild(post)
    }


    private fun liftArrows() {
        val can = booleanArrayOf((Game.liftStep < Game.liftSteps.lastIndex), (Game.liftStep > 0))
        for (i in 0..1) {
            if (can[i] == liftLit[i]) continue
            val cone = liftCones[i] ?: continue
            cone.components[ModelComponent::class.java]?.materials?.let { m -> m[0] = if (can[i]) liftGold else liftDark }
            liftLit[i] = can[i]
        }
    }

    /** Stands the box at its lift height and turn. */
    private fun placeBox() {
        if (Game.debugView != null) return
        root.setPos(Vector3(home.x, (home.y + liftNow).toFloat(), home.z))
        root.transform().eulerAngles = EulerAngles(0f, yawNow.toFloat(), 0f)
        pillar.enabled = liftNow > 0.01
        if (pillar.enabled) {
            pillar.setPos(Vector3(0f, (-liftNow / 2).toFloat(), 0f))
            pillar.transform().setScaleVector(Vector3(1f, liftNow.toFloat(), 1f))
        }
    }

    /** The box glides to its lift step and turn (about 0.6 s a step). */
    private fun liftAndTurn(dt: Double) {
        liftArrows()
        val rise = Game.liftSteps.getOrElse(Game.liftStep) { 0.0 }
        var yaw = Game.boxYaw
        // Turn the short way round.
        while (yaw - yawNow > 180) yaw -= 360
        while (yaw - yawNow < -180) yaw += 360
        if (kotlin.math.abs(rise - liftNow) < 1e-4 && kotlin.math.abs(yaw - yawNow) < 1e-3) return
        liftNow += (rise - liftNow).coerceIn(-dt * 0.35, dt * 0.35)
        yawNow += (yaw - yawNow).coerceIn(-dt * 90, dt * 90)
        if (kotlin.math.abs(yaw - yawNow) < 1e-3) yawNow = Game.boxYaw
        placeBox()
    }

    private fun gridLines(): Entity {
        val g = Entity()
        val span = SIZE * CELL
        for (k in 0..SIZE) {
            val o = (k - SIZE / 2f) * CELL
            g.addChild(model(MeshResource.createBox(Vector3(span, 0.0016f, 0.0022f)), gridLit).apply { setPos(Vector3(0f, 0.0012f, o)) })
            g.addChild(model(MeshResource.createBox(Vector3(0.0022f, 0.0016f, span)), gridLit).apply { setPos(Vector3(o, 0.0012f, 0f)) })
        }
        return g
    }

    /**
     * Things brought up from the tomb, set on the desk around the box (2026-11-12 review: the scene was thin). Box space:
     * y 0 is the desk top, the player at +z.
     */
    private fun tombProps() {
        fun put(name: String, size: Float, x: Float, z: Float, yaw: Float): Entity? {
            val e = models[name]?.clone(Entity.CloneOptions(true, true)) ?: return null
            e.setScale(size)
            e.setPos(Vector3(x, 0f, z))
            e.transform().eulerAngles = EulerAngles(0f, yaw, 0f)
            deskNode.addChild(e)
            return e
        }
        put("prop_lamp", 0.28f, -0.56f, -0.18f, 20f)?.let {
            // A small flame in the lamp's dish.
            val flame = model(MeshResource.createSphere(1f), glow(color(1f, 0.72f, 0.32f, 1f)))
            flame.setPos(Vector3(-0.56f, 0.285f, -0.18f))
            flame.transform().setScaleVector(Vector3(0.011f, 0.02f, 0.011f))
            deskNode.addChild(flame)
        }
        put("prop_ding", 0.16f, 0.6f, -0.2f, -25f)
        put("prop_jade", 0.15f, 0.62f, 0.02f, -50f)
        put("prop_slips", 0.3f, -0.62f, 0.22f, 25f)
        // The compass gives its place at the front left to the lift's control post.
        put("prop_luopan", 0.2f, -0.62f, -0.02f, -10f)
    }

    fun hitOf(e: Entity?): Hit? {
        var x = e
        while (x != null) {
            hits[x]?.let { return it }
            x = x.getParent()
        }
        return null
    }

    private fun pad(): Entity {
        val e = model(MeshResource.createCylinder(0.003f, 0.032f), glow(color(0.95f, 0.8f, 0.45f, 0.75f)))
        e.components.set(CollisionComponent(listOf(ShapeResource.createBox(Vector3(CELL, 0.05f, CELL))), PhysicsMaterialResource()))
        e.components.set(InteractableComponent())
        e.components.set(HoverEffectComponent())
        return e
    }

    // Patina is not a mirror: a low metallic keeps the texture's colour, and the ambient glow carries the texture too, so
    // the dim workshop light does not turn every tile into the same flat grey.
    private fun material(name: String): PhysicallyBasedMaterial = pbr(color(1f, 1f, 1f), 0.35f, 0.5f, Color4(0.24f, 0.24f, 0.24f, 1f)).apply {
        tex[name]?.let { setBaseColorTexture(it); setEmissiveTexture(it) }
        tex["${name}_n"]?.let { setNormalTexture(it); setNormalScale(1f) }
    }

    private val bronze: PhysicallyBasedMaterial get() = pbr(color(0.52f, 0.39f, 0.2f), 0.85f, 0.42f, Color4(0.05f, 0.04f, 0.02f, 1f))
    private val rodLit = glow(color(1f, 0.78f, 0.35f, 1f)).shared()
    private val rodOff = pbr(color(0.5f, 0.38f, 0.2f), 0.85f, 0.42f, Color4(0.08f, 0.06f, 0.03f, 1f)).shared()

    /** Sets out a level from its start. */
    fun build(p: Play) {
        anims.clear()
        anim = null
        play = p
        layout = p.level.layout
        rot = intArrayOf(0, 0, 0)
        mechs.forEach { m -> m.body.getParent()?.removeChild(m.body); hits.remove(m.body) }
        mechs.clear()
        rods.forEach { r -> root.removeChild(r.e) }
        rods.clear()
        holders.values.forEach { h -> root.removeChild(h); hits.remove(h) }
        holders.clear()
        guards.forEach { g -> root.removeChild(g) }
        guards.clear()
        arrows.clear()
        ghosted.clear(); raised.clear(); cleared.clear()
        showMoves(emptyList())
        val lay = p.level.layout
        for (l in 0 until LAYERS) {
            val rows = lay.layers[l]
            val used = rows.any { r -> r.any { it != '#' } }
            val fl = ArrayList<Pair<Int, Int>>()
            val wa = ArrayList<Pair<Int, Int>>()
            for (y in 0 until SIZE) for (x in 0 until SIZE) when (rows[y][x]) {
                '#' -> if (used) wa += x to y
                '.' -> fl += x to y
                else -> mechs += makeMech(Spot(l, x, y), rows[y][x])
            }
            floors[l].show(fl)
            walls[l].show(wa)
            spins[l].transform().eulerAngles = EulerAngles(0f, 0f, 0f)
            frames[l].enabled = used  // A layer this level does not use would hang there as an empty frame.
            grids[l].enabled = used
        }
        for ((from, tos) in lay.links) for (t in tos) {
            val e = model(MeshResource.createCylinder(1f, 0.0035f), rodOff)
            root.addChild(e)
            rods += Rod(from, t, e)
        }
        for (x in p.state.pieces) {
            val h = Entity()
            val name = when (x.kind) { Kind.KEY -> "piece_key"; Kind.HEAVY -> "piece_heavy"; Kind.LIGHT -> "piece_light" }
            val body = models[name]?.clone(Entity.CloneOptions(true, true))
                ?: model(MeshResource.createBox(Vector3(0.05f, 0.06f, 0.05f)), pbr(if (x.kind == Kind.KEY) color(0.4f, 0.9f, 0.8f) else color(0.8f, 0.6f, 0.3f), 0.6f, 0.4f))
            body.setScale(PIECE_SIZE[x.kind] ?: 0.06f)
            h.addChild(body)
            h.components.set(CollisionComponent(listOf(PIECE_BOX), PhysicsMaterialResource()))
            h.components.set(InteractableComponent())
            h.components.set(HoverEffectComponent())
            hits[h] = Hit.PieceHit(x.id)
            holders[x.id] = h
            root.addChild(h)
        }
        for (i in p.state.patrols.indices) {
            val g = Entity()
            val body = models["piece_guard"]?.clone(Entity.CloneOptions(true, true)) ?: model(MeshResource.createBox(Vector3(0.04f, 0.09f, 0.04f)), pbr(color(0.8f, 0.3f, 0.2f), 0.6f, 0.4f))
            body.setScale(0.1f)
            g.addChild(body)
            val arrow = model(MeshResource.createBox(Vector3(0.02f, 0.003f, 0.02f)), glow(color(1f, 0.45f, 0.3f, 1f)))
            g.addChild(arrow)
            guards += g
            arrows += arrow
            root.addChild(g)
        }
        receive(root)
        snap(p)
    }

    private fun makeMech(c: Spot, ch: Char): Mech {
        val node = Entity()
        node.setPos(Vector3(local(c.x), 0f, local(c.y)))
        spins[c.layer].addChild(node)
        val parts = ArrayList<Entity>()
        fun tile(name: String, size: Vector3, y: Float) = model(MeshResource.createBox(size), material(name)).also { it.setPos(Vector3(0f, y, 0f)) }
        when (ch) {
            'T' -> {
                // Two leaves hinged at the sides.
                for ((name, side) in listOf("tile_door_l" to -1f, "tile_door_r" to 1f)) {
                    val hinge = Entity()
                    hinge.setPos(Vector3(side * TILE / 2, 0f, 0f))
                    val leaf = tile(name, Vector3(TILE / 2, 0.006f, TILE), -0.003f)
                    leaf.setPos(Vector3(-side * TILE / 4, -0.003f, 0f))
                    hinge.addChild(leaf)
                    node.addChild(hinge)
                    parts += hinge
                }
            }
            'B' -> {
                val hinge = Entity()
                hinge.setPos(Vector3(0f, 0f, -TILE / 2))
                val deck = tile("tile_bridge", Vector3(TILE, 0.006f, TILE), -0.003f)
                deck.setPos(Vector3(0f, -0.003f, TILE / 2))
                hinge.addChild(deck)
                node.addChild(hinge)
                parts += hinge
            }
            'P', 'L' -> {
                val top = tile(if (ch == 'P') "tile_plate" else "tile_latch", Vector3(TILE, 0.008f, TILE), -0.004f)
                node.addChild(top)
                parts += top
            }
            'U' -> node.addChild(tile("tile_lift", Vector3(TILE, 0.008f, TILE), -0.004f))
            'W' -> node.addChild(tile("tile_wheel", Vector3(TILE, 0.008f, TILE), -0.004f))
            'G' -> {
                node.addChild(tile("tile_goal", Vector3(TILE, 0.008f, TILE), -0.004f))
                val halo = model(MeshResource.createCylinder(0.002f, 0.03f), glow(color(0.5f, 1f, 0.85f, 0.45f)))
                halo.setPos(Vector3(0f, 0.003f, 0f))
                node.addChild(halo)
            }
            else -> node.addChild(tile("tile_floor", Vector3(TILE, 0.008f, TILE), -0.004f))
        }
        node.components.set(CollisionComponent(listOf(TILE_BOX), PhysicsMaterialResource()))
        node.components.set(InteractableComponent())
        node.components.set(HoverEffectComponent())
        hits[node] = Hit.CellHit(c)
        return Mech(c, ch, node, parts)
    }

    /** Puts everything where the rules say, at once. */
    fun snap(p: Play) {
        anims.clear()
        anim = null
        val s = p.state
        for (i in 0 until LAYERS) {
            rot[i] = s.rotation[i]
            spins[i].transform().eulerAngles = EulerAngles(0f, -90f * rot[i], 0f)
        }
        at.clear()
        for (x in s.pieces) at[x.id] = x.at
        s.patrols.forEachIndexed { i, g -> at["g$i"] = g.at }
        on = p.rules.active(s)
        for (m in mechs) {
            m.target = target(m)
            m.amount = m.target
            pose(m)
        }
        placeAll()
    }

    private fun target(m: Mech): Float {
        val p = play ?: return 0f
        return when (m.kind) {
            'T', 'B' -> if (m.cell in on) 1f else 0f
            'L' -> if (m.cell in (p.state.latched)) 1f else 0f
            'P' -> if (at.values.any { s -> p.level.layout.cellId(p.state, s) == m.cell } && heavyOn(m.cell)) 1f else 0f
            else -> 0f
        }
    }

    private fun heavyOn(c: Spot): Boolean {
        val p = play ?: return false
        return p.state.pieces.any { it.kind == Kind.HEAVY && p.level.layout.cellId(p.state, it.at) == c } ||
            p.state.patrols.any { p.level.layout.cellId(p.state, it.at) == c }
    }

    private fun pose(m: Mech) {
        when (m.kind) {
            'T' -> m.parts.forEachIndexed { i, h -> h.transform().eulerAngles = EulerAngles(0f, 0f, (if (i == 0) 1f else -1f) * -82f * m.amount) }
            'B' -> m.parts[0].transform().eulerAngles = EulerAngles(-85f * (1f - m.amount), 0f, 0f)
            'P', 'L' -> m.parts[0].setPos(Vector3(0f, -0.004f - 0.004f * m.amount, 0f))
        }
    }

    /**
     * Where tray [l]'s middle is. Opening, the trays climb back like a flight of stairs and each one leans toward the
     * player (2026-11-13 review: seated, the cells could not be seen; the top tray was nearly at eye height, edge on).
     */
    private fun trayOrigin(l: Int) = BoxLayout.trayOrigin(l, spreadNow)

    /** How far the trays lean toward the player now, in degrees: flat when the box is shut. */
    private fun tiltDeg() = BoxLayout.tiltDeg(spreadNow)

    private fun trayPoint(l: Int, x: Double, y: Double, z: Double) = BoxLayout.trayPoint(l, spreadNow, x, y, z)

    private fun cellPoint(s: Spot): Vec3 = trayPoint(s.layer, local(s.x).toDouble(), 0.0, local(s.y).toDouble())

    private fun placeTrays() {
        for (l in 0 until LAYERS) {
            val o = trayOrigin(l)
            trays[l].setPos(Vector3(o.x.toFloat(), o.y.toFloat(), o.z.toFloat()))
            trays[l].transform().eulerAngles = EulerAngles(tiltDeg().toFloat(), 0f, 0f)
        }
        val corners = listOf(-1 to -1, 1 to -1, -1 to 1, 1 to 1)
        corners.forEachIndexed { i, (a, b) ->
            val low = Vec3(a * 0.21, BASE_Y - 0.01, b * 0.21)
            val top = trayPoint(LAYERS - 1, a * 0.21, 0.0, b * 0.21)
            stick(posts[i], low, top)
        }
    }

    private fun stick(e: Entity, from: Vec3, to: Vec3) {
        val d = to - from
        val len = d.length()
        if (len < 1e-4) {
            e.enabled = false
            return
        }
        e.enabled = true
        val mid = from + d * 0.5
        e.setPos(Vector3(mid.x.toFloat(), mid.y.toFloat(), mid.z.toFloat()))
        e.transform().quaternion = quatFromY(d)
        e.transform().setScaleVector(Vector3(1f, len.toFloat(), 1f))
    }

    private fun placeRods() {
        val p = play ?: return
        val s = p.state.copy(rotation = rot.toList())
        for (r in rods) {
            val a = p.level.layout.worldOf(s, r.from)
            val b = p.level.layout.worldOf(s, r.to)
            stick(r.e, cellPoint(a) + Vec3(0.0, -0.012, 0.0), cellPoint(b) + Vec3(0.0, -0.012, 0.0))
            val lit = r.to in on || (flash > 0 && (flashCell == r.from || flashCell == r.to))
            r.e.components[ModelComponent::class.java]?.materials?.let { m -> m[0] = if (lit) rodLit else rodOff }
        }
    }

    private fun placeAll() {
        placeTrays()
        placeRods()
        for ((id, h) in holders) {
            if (id == dragId) continue
            val spot = at[id] ?: continue
            setPoint(h, cellPoint(spot))
        }
        guards.forEachIndexed { i, g ->
            val spot = at["g$i"] ?: return@forEachIndexed
            setPoint(g, cellPoint(spot))
            aim(i)
        }
        pads.forEachIndexed { i, pad ->
            val m = padMoves.getOrNull(i)
            pad.enabled = m != null
            if (m != null) setPoint(pad, cellPoint(m.to) + Vec3(0.0, 0.004, 0.0))
        }
    }

    private fun setPoint(e: Entity, v: Vec3) {
        e.setPos(Vector3(v.x.toFloat(), v.y.toFloat(), v.z.toFloat()))
        // Pieces, patrols and move pads stand on a tray: they lean with it.
        if (holders.containsValue(e) || e in guards || e in pads) e.transform().eulerAngles = EulerAngles(tiltDeg().toFloat(), 0f, 0f)
    }

    /** The arrow at the patrol's feet: where it goes next. */
    private fun aim(i: Int) {
        val d = play?.state?.patrols?.getOrNull(i)?.dir ?: return
        val arrow = arrows.getOrNull(i) ?: return
        arrow.setPos(Vector3(d.dx * 0.03f, 0.006f, d.dy * 0.03f))
        arrow.transform().eulerAngles = EulerAngles(0f, 45f, 0f)
    }

    fun showMoves(moves: List<Move>) {
        padMoves = moves.take(pads.size)
        pads.forEachIndexed { i, pad ->
            val m = padMoves.getOrNull(i)
            pad.enabled = m != null
            if (m != null) setPoint(pad, cellPoint(m.to) + Vec3(0.0, 0.004, 0.0))
        }
    }

    fun padMove(i: Int) = padMoves.getOrNull(i)

    fun flashLinks(cell: Spot) {
        flashCell = cell
        flash = 1.6
        placeRods()
    }

    // ---- Seeing past what is in front ----

    /**
     * A piece or move pad hidden behind another piece, or under a mechanism tile of a higher tray, should still take a
     * pinch: the piece in front turns see-through and keeps only a small hit box on its body, a tile in front stops
     * catching the pinch, and the hidden piece's hit box grows upward so its top shows over the piece in front.
     */
    private fun seeThrough() {
        val e = eye ?: return
        val p = play ?: return
        if (dragId != null) return
        val pieces = holders.keys.mapNotNull { id -> at[id]?.let { id to cellPoint(it) } }
        val targets = pieces.map { (id, c) -> id to c + Vec3(0.0, 0.035, 0.0) } +
            padMoves.mapIndexed { i, m -> "pad$i" to cellPoint(m.to) + Vec3(0.0, 0.01, 0.0) }
        val ghost = HashSet<String>()
        val raise = HashSet<String>()
        for ((tid, t) in targets) for ((id, c) in pieces) {
            if (id == tid) continue
            if (Sight.blocks(e, t, c + Vec3(0.0, 0.04, 0.0), PIECE_HALF)) {
                ghost += id
                if (!tid.startsWith("pad")) raise += tid
            }
        }
        raise -= ghost  // A piece both in front and behind keeps the small box: the one behind it must stay reachable.
        val s = p.state.copy(rotation = rot.toList())
        val clear = HashSet<Spot>()
        for (m in mechs) {
            val c = cellPoint(p.level.layout.worldOf(s, m.cell))
            if (targets.any { (_, t) -> Sight.blocks(e, t, c, TILE_HALF) }) clear += m.cell
        }
        for (id in holders.keys) {
            val h = holders[id] ?: continue
            val g = id in ghost
            val r = id in raise
            if (g != (id in ghosted) || r != (id in raised)) {
                h.components.set(CollisionComponent(listOf(if (g) GHOST_BOX else if (r) RAISED_BOX else PIECE_BOX), PhysicsMaterialResource()))
                val o = h.components[OpacityControllerComponent::class.java]
                if (o == null) h.components.set(OpacityControllerComponent(if (g) GHOST_OPACITY else 1f)) else o.opacity = if (g) GHOST_OPACITY else 1f
            }
        }
        for (m in mechs) {
            val c = m.cell in clear
            if (c != (m.cell in cleared)) m.body.components.set(CollisionComponent(listOf(if (c) TILE_AWAY else TILE_BOX), PhysicsMaterialResource()))
        }
        ghosted.clear(); ghosted += ghost
        raised.clear(); raised += raise
        cleared.clear(); cleared += clear
    }

    // ---- The lever in the hand ----

    fun leverStart() {
        leverHold = spreadNow
    }

    /** The hand moved by [d] (box space): the part of it along the ring's path turns the arm. */
    fun leverBy(d: Vec3) {
        val h = leverHold ?: return
        val a = h * LEVER_MAX_RAD
        val along = d.y * cos(a) - d.z * sin(a)
        leverHold = (h + along / LEVER_REACH / LEVER_MAX_RAD).coerceIn(0.0, 1.0)
    }

    /** Lets go: open if the arm was left more than half up. A pinch that hardly moved it flips it. */
    fun leverEnd(wasOpen: Boolean): Boolean {
        val h = leverHold ?: return wasOpen
        leverHold = null
        val start = if (wasOpen) 1.0 else 0.0
        return if (kotlin.math.abs(h - start) < 0.12) !wasOpen else h >= 0.5
    }

    private fun poseLever() {
        leverArm.transform().eulerAngles = EulerAngles((-LEVER_MAX_DEG * spreadNow).toFloat(), 0f, 0f)
    }

    // ---- Dragging a piece by hand ----

    fun dragStart(id: String) {
        dragId = id
        dragOffset = Vec3.ZERO
    }

    fun dragBy(d: Vec3) {
        val id = dragId ?: return
        dragOffset += d
        val spot = at[id] ?: return
        holders[id]?.let { setPoint(it, cellPoint(spot) + dragOffset + Vec3(0.0, 0.02, 0.0)) }
    }

    /** Lets go: the move nearest to the hand, if the piece was really carried somewhere. */
    fun dragEnd(): Move? {
        val id = dragId ?: return null
        dragId = null
        val spot = at[id] ?: return null
        val carried = dragOffset.length()
        val point = cellPoint(spot) + dragOffset
        val best = padMoves.minByOrNull { (cellPoint(it.to) - point).length() }
        holders[id]?.let { setPoint(it, cellPoint(spot)) }
        if (carried < DRAG_MIN || best == null) return null
        return if ((cellPoint(best.to) - point).length() < DROP_RADIUS) best else null
    }

    val isDragging get() = dragId != null

    // ---- Playing a turn ----

    fun play(events: List<Ev>) {
        for (e in events) when (e) {
            is Ev.Moved -> anims += Anim.Walk(e.id, e.from, e.to)
            is Ev.Fell -> anims += Anim.Drop(e.id, e.from, e.to)
            is Ev.Rose -> anims += Anim.Rise(e.id, e.from, e.to)
            is Ev.Turned -> anims += Anim.Turn(e.layer)
            is Ev.Mechanisms -> anims += Anim.Work(e.on)
            is Ev.PatrolMoved -> anims += Anim.Walk("g${e.index}", e.from, e.to)
            is Ev.PatrolFell -> anims += Anim.Drop("g${e.index}", e.from, e.to)
            is Ev.Latched -> { Game.sound?.play(Sfx.LATCH); anims += Anim.Pause(0.05) }
            Ev.Won, Ev.Caught -> anims += Anim.Pause(0.2)
        }
    }

    private fun startAnim(a: Anim) {
        when (a) {
            is Anim.Walk -> Game.sound?.play(Sfx.STEP)
            is Anim.Drop -> Game.sound?.play(Sfx.FALL)
            is Anim.Rise -> Game.sound?.play(Sfx.LIFT)
            is Anim.Turn -> Game.sound?.play(Sfx.TURN)
            is Anim.Work -> {
                on = a.set
                mechs.forEach { m -> if (m.kind == 'T' || m.kind == 'B') m.target = if (m.cell in on) 1f else 0f }
                Game.sound?.play(Sfx.DOOR)
                placeRods()
            }
            is Anim.Pause -> Unit
        }
    }

    private fun finishAnim(a: Anim) {
        when (a) {
            is Anim.Walk -> { at[a.id] = a.to; refreshPlates() }
            is Anim.Drop -> { at[a.id] = a.to; refreshPlates() }
            is Anim.Rise -> at[a.id] = a.to
            is Anim.Turn -> {
                val lay = layout ?: return
                rot[a.layer] = (rot[a.layer] + 1) % 4
                spins[a.layer].transform().eulerAngles = EulerAngles(0f, -90f * rot[a.layer], 0f)
                for ((k, s) in at.toList()) if (s.layer == a.layer) lay.world(s.x, s.y, 1).let { (x, y) -> at[k] = Spot(s.layer, x, y) }
                placeRods()
            }
            else -> Unit
        }
    }

    private fun refreshPlates() {
        val p = play ?: return
        val s = p.state.copy(rotation = rot.toList())
        val lay = p.level.layout
        val cells = at.mapNotNull { (k, v) -> if (k.startsWith("g") || k.startsWith("H")) lay.cellId(s, v) else null }.toSet()
        for (m in mechs) if (m.kind == 'P') {
            val down = m.cell in cells
            if (down && m.target == 0f) Game.sound?.play(Sfx.PLATE)
            m.target = if (down) 1f else 0f
        }
        for (m in mechs) if (m.kind == 'L' && m.cell in cells) m.target = 1f
    }

    private fun ease(t: Double) = t * t * (3 - 2 * t)

    private fun animate(a: Anim, t: Double) {
        when (a) {
            is Anim.Walk -> entityOf(a.id)?.let {
                val p = cellPoint(a.from) + (cellPoint(a.to) - cellPoint(a.from)) * ease(t)
                setPoint(it, p + Vec3(0.0, 0.014 * sin(t * PI), 0.0))
            }
            is Anim.Drop -> entityOf(a.id)?.let {
                val p = cellPoint(a.from) + (cellPoint(a.to) - cellPoint(a.from)) * (t * t)
                setPoint(it, p)
            }
            is Anim.Rise -> entityOf(a.id)?.let { setPoint(it, cellPoint(a.from) + (cellPoint(a.to) - cellPoint(a.from)) * ease(t)) }
            is Anim.Turn -> {
                val angle = (PI / 2) * ease(t)
                spins[a.layer].transform().eulerAngles = EulerAngles(0f, -90f * rot[a.layer] - 90f * ease(t).toFloat(), 0f)
                for ((k, s) in at) if (s.layer == a.layer) entityOf(k)?.let {
                    val dx = local(s.x).toDouble()
                    val dz = local(s.y).toDouble()
                    val rx = dx * cos(angle) - dz * sin(angle)
                    val rz = dx * sin(angle) + dz * cos(angle)
                    setPoint(it, trayPoint(s.layer, rx, 0.0, rz))
                }
            }
            else -> Unit
        }
    }

    private fun entityOf(id: String): Entity? = if (id.startsWith("g")) guards.getOrNull(id.drop(1).toIntOrNull() ?: -1) else holders[id]

    /** Every frame. */
    fun update(dt: Double) {
        clock += dt
        liftAndTurn(dt)
        // In the hand the trays follow the lever at once; let go, they finish the way the lever was left.
        val wanted = leverHold ?: if (Game.spread) 1.0 else 0.0
        if (kotlin.math.abs(spreadNow - wanted) > 1e-3) {
            val rate = if (leverHold != null) 6.0 else 1.4
            spreadNow += (wanted - spreadNow).coerceIn(-dt * rate, dt * rate)
            if (!busy) placeAll() else placeTrays()
            poseLever()
        }
        for (m in mechs) if (m.amount != m.target) {
            val step = (dt * 4).toFloat()
            m.amount = if (m.amount < m.target) minOf(m.target, m.amount + step) else maxOf(m.target, m.amount - step)
            pose(m)
        }
        if (flash > 0) {
            flash -= dt
            if (flash <= 0) placeRods()
        }
        sparks.update(dt)
        if (showT >= 0) showTick(dt)
        sightT -= dt
        if (sightT <= 0) {
            sightT = 0.15
            seeThrough()
        }
        val running = anim
        if (running != null) {
            animT += dt / running.seconds
            if (animT >= 1) {
                animate(running, 1.0)
                finishAnim(running)
                anim = null
            } else {
                animate(running, animT)
                return
            }
        }
        val nextAnim = anims.removeFirstOrNull()
        if (nextAnim != null) {
            anim = nextAnim
            animT = 0.0
            startAnim(nextAnim)
        } else if (running != null) {
            // The turn has played out: settle on the rules' own state and report.
            play?.let { p ->
                val keep = padMoves
                snapPositions(p)
                padMoves = keep
            }
            Game.turnShown()
        }
    }

    private fun snapPositions(p: Play) {
        val s = p.state
        at.clear()
        for (x in s.pieces) at[x.id] = x.at
        s.patrols.forEachIndexed { i, g -> at["g$i"] = g.at }
        for (i in 0 until LAYERS) rot[i] = s.rotation[i]
        on = p.rules.active(s)
        for (m in mechs) m.target = target(m)
        placeAll()
    }

    fun celebrate(p: Play) {
        val k = p.state.key.at
        val c = cellPoint(k) + Vec3(0.0, 0.08, 0.0)
        sparks.emit(c, 40, 0.5, 1.2, 0.012f, Vec3(0.0, 0.3, 0.0))
    }

    fun trophies(n: Int) {
        while (trophies.size < n) {
            val i = trophies.size
            val t = models["trophy"]?.clone(Entity.CloneOptions(true, true)) ?: return
            t.setScale(0.1f + i * 0.015f)
            t.setPos(Vector3(-0.62f + i * 0.13f, 0f, -0.05f))
            root.addChild(t)
            receive(t)
            trophies += t
        }
    }

    // ---- The hidden show, for the gold trophy: the whole box works by itself. ----

    fun startShow() {
        showT = 0.0
        Game.spread = true
    }

    private fun showTick(dt: Double) {
        showT += dt
        for (l in 0 until LAYERS) spins[l].transform().eulerAngles = EulerAngles(0f, (showT * (30 + l * 20) * (if (l % 2 == 0) 1 else -1)).toFloat(), 0f)
        for (m in mechs) if (m.kind == 'T' || m.kind == 'B') {
            m.amount = (0.5 + 0.5 * sin(showT * 2 + m.cell.x)).toFloat()
            pose(m)
        }
        if ((showT * 3).toInt() != ((showT - dt) * 3).toInt()) {
            val a = showT * 1.7
            sparks.emit(Vec3(0.2 * cos(a), 0.3, 0.2 * sin(a)), 6, 0.3, 1.0, 0.01f, Vec3(0.0, 0.2, 0.0))
        }
    }

    fun endShow() {
        showT = -1.0
        sparks.clear()
        play?.let { snap(it) }
    }

    fun useIbl(t: TextureResource) {
        iblSource.components.set(ImageBasedLightComponent(ImageBasedLightSource.Single(t), 7f))
        receive(root)
    }

    private fun receive(e: Entity) {
        if (iblSource.components[ImageBasedLightComponent::class.java] == null) return
        if (e.components[ModelComponent::class.java] != null && e.components[ImageBasedLightReceiverComponent::class.java] == null) {
            e.components.set(ImageBasedLightReceiverComponent(iblSource))
            e.components.set(EnvironmentLightingSettingsComponent(0f))
        }
        e.getChildren().forEach { receive(it) }
    }

    companion object {
        const val CELL = 0.08f
        private const val TILE = 0.077f
        /** Walls kept low so the cells behind them can be seen from the seat. */
        private const val WALL_H = 0.018f
        private const val BASE_Y = BoxLayout.BASE_Y
        private const val DRAG_MIN = 0.025
        private val PIECE_BOX = ShapeResource.createBox(Vector3(CELL, 0.09f, CELL)).offsetByTranslation(Vector3(0f, 0.04f, 0f)).shared()
        private val GHOST_BOX = ShapeResource.createBox(Vector3(CELL * 0.45f, 0.05f, CELL * 0.45f)).offsetByTranslation(Vector3(0f, 0.025f, 0f)).shared()
        private val RAISED_BOX = ShapeResource.createBox(Vector3(CELL, 0.22f, CELL)).offsetByTranslation(Vector3(0f, 0.105f, 0f)).shared()
        private val TILE_BOX = ShapeResource.createBox(Vector3(CELL, 0.012f, CELL)).shared()
        private val TILE_AWAY = ShapeResource.createBox(Vector3(0.002f, 0.002f, 0.002f)).shared()
        private val PIECE_HALF = Vec3(CELL / 2.0, 0.045, CELL / 2.0)
        private val TILE_HALF = Vec3(CELL / 2.0, 0.006, CELL / 2.0)
        private const val GHOST_OPACITY = 0.35f
        private const val LEVER_SCALE = 0.2f
        /** On the desk, from the box's middle: the control post at the front left, the lever at the right (name plates above). */
        val POST_AT = Vector3(-0.4f, 0f, 0.16f)
        val LEVER_AT = Vector3(0.38f, 0f, 0.12f)
        /** The arm's pivot in the lever model, at [LEVER_SCALE]. */
        private val LEVER_PIVOT = Vector3(0f, 0.144f * LEVER_SCALE, -0.336f * LEVER_SCALE)
        private const val LEVER_MAX_DEG = 55.0
        private const val LEVER_MAX_RAD = LEVER_MAX_DEG * PI / 180
        /** Pivot to the ring, where the hand holds it. */
        private const val LEVER_REACH = 0.15
        private const val DROP_RADIUS = 0.07
        private val HIDDEN = Transform(Vector3(0f, -100f, 0f), EulerAngles(0f, 0f, 0f), Vector3(0f))
        private val PIECE_SIZE = mapOf(Kind.KEY to 0.055f, Kind.HEAVY to 0.075f, Kind.LIGHT to 0.07f)
        private fun local(i: Int) = (i - (SIZE - 1) / 2f) * CELL
    }
}
