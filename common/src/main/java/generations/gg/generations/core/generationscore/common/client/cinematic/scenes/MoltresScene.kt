package generations.gg.generations.core.generationscore.common.client.cinematic.scenes

import generations.gg.generations.core.generationscore.common.client.cinematic.audio.MoltresMusic
import generations.gg.generations.core.generationscore.common.client.cinematic.audio.SynthRenderer
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.easeOutCubic
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.random
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.smooth
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.CinematicTextures.Kind
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxBatches
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxMaterial
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxMaterial.Layer
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxMeshes
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxMeshes.argb
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxParticles
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.PostParams
import net.minecraft.network.chat.Component
import org.joml.Vector3d
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Moltres — Rebirth of the Flames: the effects of MoltresScript, drawn. The column, its rings
 * and the trail come from the scene's time; embers, sparks and ash are the pool's.
 */
class MoltresScene(landing: Vector3d, test: Boolean, override val title: Component) : CinematicScene {
    override val id = "moltres"
    override val script = MoltresScript(landing, test)
    private val s = script
    override val subtitle: Component = Component.translatable("generations_core.cinematic.moltres.subtitle")

    // ---------------------------------------------------------------- materials

    private val core = FxMaterial("core", Layer.ADD, Kind.WHITE).noise(0f, -2.2f, 2f, 0.2f).shape(1.4f, -0.85f, 0f, 1.7f)
    private val fire = FxMaterial("fire", Layer.ADD, Kind.WHITE).noise(0.1f, -1.6f, 3f, 0.85f).shape(1.1f, -0.5f, 0f, 1.3f)
    private val edge = FxMaterial("edge", Layer.ALPHA, Kind.WHITE).noise(-0.05f, -1.0f, 3.5f, 0.9f).shape(0f, 0f, 0.6f, 1f)
    private val glows = FxMaterial("glows", Layer.ADD, Kind.HALO)
    private val groundGlow = FxMaterial("ground_glow", Layer.ADD, Kind.HALO).shape(0f, 0f, 0.4f, 1f)
    private val rings = FxMaterial("rings", Layer.ADD, Kind.WHITE).noise(0.5f, 0f, 4f, 0.55f).shape(1.3f, 0.4f, 0f, 1.3f)
    private val groundRing = FxMaterial("ground_ring", Layer.ADD, Kind.RING).noise(0f, 0.4f, 4f, 0.5f).shape(0f, 0f, 0.3f, 1.2f)
    private val shell = FxMaterial("shell", Layer.ADD, Kind.WHITE).noise(0.1f, 0.4f, 2f, 0.4f).shape(2.5f, 1f, 0f, 1.2f)
    private val shock = FxMaterial("shock", Layer.ADD, Kind.WHITE).noise(0.4f, 0f, 3f, 0.5f).shape(1.5f, 0.5f, 0f, 1.3f)
    private val trails = FxMaterial("trails", Layer.ADD, Kind.STREAK)

    private val particleMaterials = arrayOf(
        FxMaterial("p_ember", Layer.ADD, Kind.EMBER),
        FxMaterial("p_spark", Layer.ADD, Kind.SPARK),
        FxMaterial("p_smoke", Layer.ALPHA, Kind.SMOKE).shape(0f, 0f, 1f, 1f),
        FxMaterial("p_glow", Layer.ADD, Kind.HALO),
    )

    override fun particles(capacity: Int) = FxParticles(capacity, particleMaterials)

    // ---------------------------------------------------------------- state

    private val local = Vector3d()
    private val local2 = Vector3d()
    private val world = Vector3d()
    private val v3 = Vector3f()
    private val w3 = Vector3f()
    private val trail = FloatArray(3 * 16)
    private val trail2 = FloatArray(3 * 16)
    private val trail3 = FloatArray(3 * 16)
    private val uv = FloatArray(2)

    /** The emission clocks (action seconds). */
    private val emitted = DoubleArray(6)
    private var started = false

    // ---------------------------------------------------------------- events

    override fun event(cue: Cue, frame: SceneFrame) {
        val p = frame.particles
        when (cue.id) {
            "column" -> {
                frame.camera.toWorld(0.0, 0.3, 0.0, world)
                burst(p, world, frame.count(140), 0, 8f, 1.6f, 0.18f, argb(1f, 0.6f, 0.15f, 1f), drag = 1.8f, gravity = -1.5f)
                burst(p, world, 4, 3, 0.4f, 0.6f, 5f, argb(1f, 0.6f, 0.2f, 0.8f))
            }
            "burst" -> {
                frame.camera.toWorld(0.0, 30.0, 0.0, world)
                burst(p, world, frame.count(400), 0, 20f, 2.2f, 0.22f, argb(1f, 0.62f, 0.18f, 1f), drag = 2.0f, gravity = 2f)
                burst(p, world, frame.count(160), 1, 15f, 1.2f, 0.4f, argb(1f, 0.85f, 0.45f, 1f), drag = 2.6f)
                burst(p, world, frame.count(60), 2, 6f, 2.6f, 1.6f, argb(0.25f, 0.18f, 0.15f, 0.45f), drag = 1.5f, grow = 2.5f)
                burst(p, world, 6, 3, 1f, 0.6f, 7f, argb(1f, 0.75f, 0.35f, 0.85f))
            }
            "breath" -> {
                s.headPos(MoltresScript.CRY, local)
                frame.camera.toWorld(local.x, local.y, local.z + 0.6, world)
                val forward = frame.camera.toWorld(0.0, 0.0, 1.0, Vector3d()).sub(frame.camera.origin.x, frame.camera.origin.y, frame.camera.origin.z)
                for (i in 0 until frame.count(110)) {
                    val spread = 0.35
                    val vx = forward.x * 8.0 + (random(i, 2601) - 0.5) * 2 * spread * 8
                    val vy = 0.5 + (random(i, 2602) - 0.5) * 2 * spread * 5
                    val vz = forward.z * 8.0 + (random(i, 2603) - 0.5) * 2 * spread * 8
                    val kind = if (i % 4 == 0) 2 else 0
                    val colour = if (kind == 2) argb(0.3f, 0.22f, 0.18f, 0.4f) else argb(1f, 0.6f + 0.3f * random(i, 2604).toFloat(), 0.15f, 1f)
                    p.emit(kind, world.x, world.y, world.z, vx.toFloat(), vy.toFloat(), vz.toFloat(), 1.0f + random(i, 2605).toFloat(), if (kind == 2) 0.4f else 0.16f, if (kind == 2) 2.2f else 0.05f, colour, drag = 1.8f, gravity = -1f, spin = 2f)
                }
            }
            "landing" -> {
                frame.camera.toWorld(s.landing.x, s.landing.y + 0.15, s.landing.z, world)
                for (i in 0 until frame.count(60)) {
                    val angle = random(i, 2701) * 2.0 * PI
                    val speed = 3.0 + 3.0 * random(i, 2702)
                    val kind = if (i % 3 == 0) 0 else 2
                    val colour = if (kind == 0) argb(1f, 0.6f, 0.15f, 1f) else argb(0.35f, 0.28f, 0.24f, 0.4f)
                    p.emit(kind, world.x, world.y, world.z, (sin(angle) * speed).toFloat(), 0.6f, (cos(angle) * speed).toFloat(), 1.6f, if (kind == 0) 0.14f else 0.5f, if (kind == 0) 0.04f else 2.4f, colour, drag = 2.4f, gravity = -0.5f)
                }
            }
            "vanish" -> {
                frame.camera.toWorld(s.landing.x, s.landing.y + 1.0, s.landing.z, world)
                burst(p, world, frame.count(220), 0, 6f, 1.6f, 0.18f, argb(1f, 0.62f, 0.18f, 1f), drag = 2f, gravity = -1.5f)
                burst(p, world, frame.count(70), 2, 2.5f, 2.2f, 1.6f, argb(0.3f, 0.22f, 0.18f, 0.5f), drag = 1.5f, grow = 2.2f)
                burst(p, world, 5, 3, 0.5f, 0.5f, 4f, argb(1f, 0.7f, 0.3f, 0.85f))
            }
        }
    }

    private fun burst(
        p: FxParticles, c: Vector3d, count: Int, kind: Int, speed: Float, life: Float, size: Float, colour: Int,
        drag: Float = 0f, gravity: Float = 0f, grow: Float = 0.3f,
    ) {
        for (i in 0 until count) {
            val seed = (c.x * 13 + c.y * 7 + c.z * 3).toInt() + i * 17 + kind * 101
            val u = random(seed, 11) * 2.0 - 1.0
            val angle = random(seed, 12) * 2.0 * PI
            val r = sqrt(1.0 - u * u)
            val k = speed * (0.35 + 0.65 * random(seed, 13)).toFloat()
            p.emit(
                kind, c.x, c.y, c.z, (r * cos(angle) * k).toFloat(), (u * k + speed * 0.1f).toFloat(), (r * sin(angle) * k).toFloat(),
                life * (0.6f + 0.4f * random(seed, 14).toFloat()), size, size * grow, colour, drag = drag, gravity = gravity, spin = (random(seed, 15).toFloat() - 0.5f) * 6f,
            )
        }
    }

    // ---------------------------------------------------------------- each frame

    /** Emits `base` (×quality) particles a second while `rate` is above 0, through the clock `slot`. */
    private inline fun emit(frame: SceneFrame, slot: Int, base: Int, a: Double, rate: Double, spawn: (Int) -> Unit) {
        val count = frame.count(base)
        if (count <= 0 || rate <= 0.004) {
            emitted[slot] = maxOf(emitted[slot], a)
            return
        }
        val every = 1.0 / (count * rate)
        var i = (emitted[slot] * 1000.0).toInt()
        var n = 0
        while (emitted[slot] + every <= a && n < 400) {
            emitted[slot] += every
            spawn(i++)
            n++
        }
        if (n >= 400) emitted[slot] = a
    }

    override fun update(frame: SceneFrame) {
        val a = frame.a
        if (!started) {
            started = true
            emitted.fill(a)
        }
        val o = frame.camera.origin
        val p = frame.particles
        p.axisX = o.x
        p.axisZ = o.z
        val column = s.column(a)
        val height = s.columnHeight(a)
        // Embers rising from the altar, then all along the column.
        emit(frame, 0, 18, a, s.altarGlow(a) * (1.0 - column)) { i ->
            val angle = random(i, 2801) * 2.0 * PI
            val r = 2.5 * sqrt(random(i, 2802))
            frame.camera.toWorld(sin(angle) * r, 0.3, cos(angle) * r, world)
            p.emit(0, world.x, world.y, world.z, 0f, (1.5 + 2.0 * random(i, 2803)).toFloat(), 0f, (2.5 + random(i, 2804)).toFloat(), 0.12f, 0.03f, argb(1f, 0.55f + 0.3f * random(i, 2805).toFloat(), 0.12f, 1f), drag = 0.4f, gravity = -0.3f, swirl = 0.3f)
        }
        emit(frame, 1, 40, a, column) { i ->
            val angle = random(i, 2811) * 2.0 * PI
            val r = 1.0 + 3.0 * random(i, 2812)
            frame.camera.toWorld(sin(angle) * r, random(i, 2813) * height * 0.7, cos(angle) * r, world)
            p.emit(0, world.x, world.y, world.z, 0f, (2.0 + 4.0 * random(i, 2814)).toFloat(), 0f, 2.5f, 0.14f, 0.03f, argb(1f, 0.5f + 0.4f * random(i, 2815).toFloat(), 0.1f, 1f), drag = 0.3f, gravity = -0.5f, swirl = (0.6 + 0.6 * random(i, 2816)).toFloat())
        }
        emit(frame, 2, 20, a, column) { i ->
            val angle = random(i, 2821) * 2.0 * PI
            val r = 1.5 + 2.5 * random(i, 2822)
            frame.camera.toWorld(sin(angle) * r, random(i, 2823) * height * 0.8, cos(angle) * r, world)
            p.emit(1, world.x, world.y, world.z, 0f, (3.0 + 3.0 * random(i, 2824)).toFloat(), 0f, 1.2f, 0.12f, 0.03f, argb(1f, 0.85f, 0.45f, 1f), drag = 0.5f, swirl = 1.5f)
        }
        emit(frame, 3, 8, a, column) { i ->
            val angle = random(i, 2831) * 2.0 * PI
            val r = 2.0 + 3.0 * random(i, 2832)
            frame.camera.toWorld(sin(angle) * r, random(i, 2833) * height * 0.6, cos(angle) * r, world)
            p.emit(2, world.x, world.y, world.z, 0f, (1.0 + random(i, 2834)).toFloat(), 0f, 3f, 0.25f, 0.7f, argb(0.12f, 0.1f, 0.09f, 0.35f), drag = 0.3f, swirl = 0.5f)
        }
        // Embers falling from its trail of flames.
        emit(frame, 4, 45, a, s.trail(a)) { i ->
            s.birdPos(a, local)
            val side = (random(i, 2841) - 0.5) * 3.4
            frame.camera.toWorld(local.x + side, local.y + 0.6, local.z + (random(i, 2842) - 0.5) * 1.5, world)
            p.emit(0, world.x, world.y, world.z, ((random(i, 2843) - 0.5) * 2).toFloat(), (1.0 + random(i, 2844)).toFloat(), ((random(i, 2845) - 0.5) * 2).toFloat(), (1.2 + 0.6 * random(i, 2846)).toFloat(), 0.2f, 0.04f, argb(1f, 0.55f + 0.35f * random(i, 2847).toFloat(), 0.12f, 1f), drag = 1f, gravity = -0.8f)
        }
        // The rain of embers over the final shot.
        emit(frame, 5, 50, a, s.emberRain(a)) { i ->
            val angle = random(i, 2851) * 2.0 * PI
            val r = 14.0 * sqrt(random(i, 2852))
            frame.camera.toWorld(s.landing.x + sin(angle) * r, 16.0 + 6.0 * random(i, 2853), s.landing.z + cos(angle) * r, world)
            p.emit(0, world.x, world.y, world.z, ((random(i, 2854) - 0.5) * 0.8).toFloat(), -1.5f, ((random(i, 2855) - 0.5) * 0.8).toFloat(), (4.5 + random(i, 2856)).toFloat(), 0.12f, 0.06f, argb(1f, 0.6f + 0.3f * random(i, 2857).toFloat(), 0.15f, 1f), gravity = 0.3f)
        }
        p.update(frame.da.toFloat())
    }

    override fun draw(frame: SceneFrame, batches: FxBatches) {
        val a = frame.a
        drawColumn(frame, batches, a)
        drawRings(frame, batches, a)
        drawSilhouette(frame, batches, a)
        drawBurst(frame, batches, a)
        drawTrail(frame, batches, a)
        frame.particles.render(batches, frame.cam.x, frame.cam.y, frame.cam.z, frame.right, frame.up)
    }

    private fun drawColumn(frame: SceneFrame, batches: FxBatches, a: Double) {
        val c = frame.rel(0.0, 0.0, 0.0, v3)
        val cx = c.x
        val cy = c.y
        val cz = c.z
        val altar = s.altarGlow(a).toFloat()
        val light = s.column(a).toFloat()
        val flicker = 0.85f + 0.15f * sin(a * 13.0).toFloat() * sin(a * 7.3 + 1.0).toFloat()
        FxMeshes.flat(batches.of(groundGlow), cx, cy + 0.05f, cz, 5f, 0f, argb(1f, 0.45f, 0.1f, (0.7f * altar + 0.3f * light) * flicker))
        if (altar > 0.004f) FxMeshes.billboard(batches.of(glows), cx, cy + 1.2f, cz, 2.6f, 0f, argb(1f, 0.55f, 0.15f, 0.55f * altar * flicker), frame.right, frame.up)
        // The ignition's ring on the ground.
        val d = a - MoltresScript.COLUMN
        if (d in 0.0..0.7) {
            val r = (8.0 * easeOutCubic(d / 0.5)).toFloat()
            val alpha = (1.0 - smooth(d / 0.7)).toFloat()
            FxMeshes.ring(batches.of(groundRing), cx, cy + 0.06f, cz, maxOf(0f, r - 2f), r, 64, argb(1f, 0.6f, 0.15f, alpha), argb(1f, 0.6f, 0.15f, alpha), vScale = 10f)
        }
        if (light <= 0.004f) return
        val h = s.columnHeight(a).toFloat()
        if (h <= 0.1f) return
        FxMeshes.cylinder(batches.of(core), cx, cy, cz, 0f, h, 0.75f * flicker, 0.5f, 20, 10, argb(1f, 0.95f, 0.75f, 0.95f * light), argb(1f, 0.9f, 0.6f, 0f), uScale = 2f, vScale = h / 6f)
        FxMeshes.cylinder(batches.of(fire), cx, cy, cz, 0f, h, 1.7f, 2.1f, 28, 10, argb(1f, 0.55f, 0.12f, 0.85f * light), argb(1f, 0.35f, 0.05f, 0f), uScale = 3f, vScale = h / 5f, twist = 0.3f)
        FxMeshes.cylinder(batches.of(edge), cx, cy, cz, 0f, h * 0.95f, 2.5f, 3.3f, 28, 10, argb(0.4f, 0.05f, 0.02f, 0.45f * light), argb(0.25f, 0.04f, 0.02f, 0f), uScale = 3f, vScale = h / 4f, twist = -0.2f)
        for (k in 0 until 3) {
            val y = h * (0.2f + 0.3f * k)
            FxMeshes.billboard(batches.of(glows), cx, cy + y, cz, 7f, 0f, argb(1f, 0.6f, 0.2f, 0.32f * light), frame.right, frame.up)
        }
        val flare = s.topFlare(a).toFloat()
        if (flare > 0.004f) FxMeshes.billboard(batches.of(glows), cx, cy + h, cz, 16f * flare + 4f, 0f, argb(1f, 0.85f, 0.5f, flare), frame.right, frame.up)
    }

    private fun drawRings(frame: SceneFrame, batches: FxBatches, a: Double) {
        val light = s.column(a).toFloat()
        if (light <= 0.004f) return
        val h = s.columnHeight(a)
        val c = frame.rel(0.0, 0.0, 0.0, v3)
        for (k in 0 until s.rings) {
            val u = s.ring(k, a)
            if (u < 0.0 || u > 1.0) continue
            val y = minOf(30.0 * u.pow(0.8), h)
            val radius = 1.5 + 5.5 * easeOutCubic(u)
            val alpha = ((1.0 - smooth(u)) * smooth(u / 0.1)).toFloat() * light
            FxMeshes.torus(batches.of(rings), c.x, c.y + y.toFloat(), c.z, radius.toFloat(), (0.3 * (1.0 - u) + 0.08).toFloat(), 48, 8, argb(1f, 0.55f, 0.12f, 0.85f * alpha))
        }
    }

    /** The light behind its silhouette in the heart of the fire. */
    private fun backlightCentre(frame: SceneFrame, a: Double): Vector3f {
        s.birdPos(a, local)
        val bird = frame.rel(local.x, local.y + 0.9, local.z, w3)
        val d = bird.length().coerceAtLeast(0.1f)
        return bird.mul(1f + 4f / d)
    }

    private fun drawSilhouette(frame: SceneFrame, batches: FxBatches, a: Double) {
        val light = s.backlight(a).toFloat()
        if (light <= 0.004f) return
        val c = backlightCentre(frame, a)
        FxMeshes.billboard(batches.of(glows), c.x, c.y, c.z, 8f, 0f, argb(1f, 0.92f, 0.7f, 0.95f * light), frame.right, frame.up)
        FxMeshes.billboard(batches.of(glows), c.x, c.y, c.z, 20f, 0f, argb(1f, 0.6f, 0.2f, 0.4f * light), frame.right, frame.up)
    }

    private fun drawBurst(frame: SceneFrame, batches: FxBatches, a: Double) {
        val d = a - MoltresScript.BURST
        if (d < 0.0 || d > 1.3) return
        val c = frame.rel(0.0, 30.0, 0.0, v3)
        if (d < 1.1) {
            val u = d / 1.1
            FxMeshes.torus(batches.of(shock), c.x, c.y, c.z, (22.0 * easeOutCubic(u)).toFloat(), (0.7 * (1.0 - u) + 0.12).toFloat(), 64, 10, argb(1f, 0.6f, 0.18f, 0.9f * (1.0 - smooth(u)).toFloat()))
        }
        if (d < 0.75) {
            val u = d / 0.7
            FxMeshes.sphere(batches.of(shell), c.x, c.y, c.z, (14.0 * easeOutCubic(u)).toFloat(), argb(1f, 0.7f, 0.3f, (0.6 * (1.0 - smooth(u))).toFloat()))
        }
        if (d < 0.6) FxMeshes.billboard(batches.of(glows), c.x, c.y, c.z, (14.0 * (1.0 + d * 8.0)).toFloat(), 0f, argb(1f, 0.85f, 0.55f, (1.0 - smooth(d / 0.5)).toFloat()), frame.right, frame.up)
    }

    /** Its trail of flames (body and wings), and a glow around it while it flies. */
    private fun drawTrail(frame: SceneFrame, batches: FxBatches, a: Double) {
        val alpha = s.trail(a).toFloat()
        if (alpha > 0.004f) {
            val count = 16
            for (side in intArrayOf(-1, 0, 1)) {
                val points = when (side) {
                    -1 -> trail
                    0 -> trail3
                    else -> trail2
                }
                for (k in 0 until count) {
                    val at = maxOf(MoltresScript.BURST, a - k * 0.045)
                    s.birdPos(at, local)
                    s.birdPos(at + 0.03, local2)
                    var hx = local2.x - local.x
                    var hz = local2.z - local.z
                    val len = sqrt(hx * hx + hz * hz)
                    if (len < 1e-4) {
                        hx = 0.0
                        hz = 1.0
                    } else {
                        hx /= len
                        hz /= len
                    }
                    val p = frame.rel(local.x - hz * 1.8 * side, local.y + 0.7, local.z + hx * 1.8 * side, v3)
                    points[k * 3] = p.x
                    points[k * 3 + 1] = p.y
                    points[k * 3 + 2] = p.z
                }
                val width = if (side == 0) 0.6f else 0.32f
                FxMeshes.ribbon(batches.of(trails), points, count, width, 0.04f, argb(1f, 0.78f, 0.32f, 0.9f * alpha), argb(0.8f, 0.15f, 0.03f, 0f))
            }
        }
        // A fire aura on Moltres from the burst to its landing.
        if (a >= MoltresScript.BURST && a < MoltresScript.LANDING + 0.8) {
            val aura = (0.35 * smooth((a - MoltresScript.BURST) / 0.3) * (1.0 - smooth((a - MoltresScript.LANDING) / 0.8))).toFloat()
            s.birdPos(a, local)
            val c = frame.rel(local.x, local.y + 0.9, local.z, v3)
            FxMeshes.billboard(batches.of(glows), c.x, c.y, c.z, 3.8f, 0f, argb(1f, 0.55f, 0.15f, aura), frame.right, frame.up)
        }
    }

    // ---------------------------------------------------------------- the image

    override fun post(frame: SceneFrame, out: PostParams) {
        val a = frame.a
        out.heat = s.heat(a).toFloat()
        out.heatSpeed = 0.6f
        out.heatScale = 4f
        out.heatFloor = 0f
        val d = a - MoltresScript.BURST
        if (d >= 0.0 && d < 1.1) {
            val c = frame.rel(0.0, 30.0, 0.0, v3)
            if (frame.project(c, uv)) out.wave(0, uv[0], uv[1], frame.screenRadius(c, (22.0 * easeOutCubic(d / 1.1)).toFloat()), (0.9 * (1.0 - smooth(d / 1.0))).toFloat(), (0.04 + 0.05 * d).toFloat())
        }
        val cry = a - MoltresScript.CRY
        if (cry >= 0.0 && cry < 0.7) {
            s.headPos(a, local)
            val c = frame.rel(local, v3)
            if (frame.project(c, uv)) out.wave(1, uv[0], uv[1], (0.6 * easeOutCubic(cry / 0.7)).toFloat(), (0.5 * (1.0 - smooth(cry / 0.7))).toFloat(), 0.05f)
        }
        // Light rays: behind the silhouette, otherwise from the column's top.
        val back = s.backlight(a).toFloat()
        val column = s.column(a).toFloat()
        if (back > 0.004f) {
            val c = backlightCentre(frame, a)
            if (frame.project(c, uv)) {
                out.raysX = uv[0]
                out.raysY = uv[1]
                out.rays = 0.9f * back
                out.raysDecay = 0.96f
            }
        } else if (column > 0.004f) {
            val c = frame.rel(0.0, s.columnHeight(a) * 0.8, 0.0, v3)
            if (frame.project(c, uv)) {
                out.raysX = uv[0]
                out.raysY = uv[1]
                out.rays = 0.35f * column
                out.raysDecay = 0.95f
            }
        }
        out.bloom = (1.0 + 0.6 * CameraMath.decay(a, MoltresScript.BURST, 0.6) + 0.25 * column).toFloat()
        out.glow = 1f
        out.aberration = (0.4 + 2.0 * CameraMath.decay(a, MoltresScript.BURST, 0.35)).toFloat()
        out.flashR = 1f
        out.flashG = 0.86f
        out.flashB = 0.62f
    }

    override fun music(stretch: Double, offset: Double): SynthRenderer = MoltresMusic.music(script, stretch, offset)

    override fun ambience(stretch: Double, offset: Double): SynthRenderer = MoltresMusic.ambience(script, stretch, offset)
}
