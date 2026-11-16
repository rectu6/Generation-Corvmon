package generations.gg.generations.core.generationscore.common.client.cinematic.scenes

import generations.gg.generations.core.generationscore.common.client.cinematic.audio.SynthRenderer
import generations.gg.generations.core.generationscore.common.client.cinematic.audio.ZapdosMusic
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
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.level.levelgen.Heightmap
import org.joml.Vector3d
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Zapdos — Judgement of the Thunder: the effects of ZapdosScript, drawn. The lightning is
 * made again 16 times a second (the same on every client: seeded by the time); the bursts'
 * particles are the pool's.
 */
class ZapdosScene(landing: Vector3d, test: Boolean, override val title: Component) : CinematicScene {
    override val id = "zapdos"
    override val script = ZapdosScript(landing, test)
    private val s = script
    override val subtitle: Component = Component.translatable("generations_core.cinematic.zapdos.subtitle")

    // ---------------------------------------------------------------- materials

    private val boltCore = FxMaterial("bolt_core", Layer.ADD, Kind.STREAK).shape(0f, 0f, 0f, 1.6f)
    private val boltGlow = FxMaterial("bolt_glow", Layer.ADD, Kind.STREAK)
    private val glows = FxMaterial("glows", Layer.ADD, Kind.HALO)
    private val clouds = FxMaterial("clouds", Layer.ALPHA, Kind.WHITE).noise(0.03f, 0.02f, 3f, 0.85f)
    private val cloudsHigh = FxMaterial("clouds_high", Layer.ALPHA, Kind.WHITE).noise(-0.04f, 0.03f, 4.5f, 0.9f)
    private val sphere = FxMaterial("sphere", Layer.ADD, Kind.WHITE).noise(0.35f, 0.5f, 2f, 0.45f).shape(1.6f, 0.6f, 0f, 1.4f)
    private val sphereInner = FxMaterial("sphere_inner", Layer.ADD, Kind.WHITE).noise(-0.5f, 0.3f, 3f, 0.6f).shape(1.2f, -0.6f, 0f, 1.2f)
    private val burn = FxMaterial("burn", Layer.ADD, Kind.HALO).shape(0f, 0f, 0.3f, 1f)
    private val scorch = FxMaterial("scorch", Layer.ALPHA, Kind.HALO).shape(0f, 0f, 0.3f, 1f)
    private val shock = FxMaterial("shock", Layer.ADD, Kind.WHITE).noise(0.4f, 0f, 3f, 0.5f).shape(1.5f, 0.5f, 0f, 1.3f)
    private val shell = FxMaterial("shell", Layer.ADD, Kind.WHITE).noise(0.2f, 0.3f, 2f, 0.4f).shape(2.5f, 1f, 0f, 1.2f)
    private val groundRing = FxMaterial("ground_ring", Layer.ADD, Kind.RING).noise(0f, 0.3f, 4f, 0.5f).shape(0f, 0f, 0.3f, 1.2f)
    private val trails = FxMaterial("trails", Layer.ADD, Kind.STREAK)

    private val particleMaterials = arrayOf(
        FxMaterial("p_spark", Layer.ADD, Kind.SPARK),
        FxMaterial("p_glow", Layer.ADD, Kind.HALO),
        FxMaterial("p_smoke", Layer.ALPHA, Kind.SMOKE).shape(0f, 0f, 1f, 1f),
        FxMaterial("p_ember", Layer.ADD, Kind.EMBER),
    )

    override fun particles(capacity: Int) = FxParticles(capacity, particleMaterials)

    // ---------------------------------------------------------------- colours

    private val zapCore = argb(1f, 0.98f, 0.86f, 1f)
    private val zapGlow = argb(1f, 0.84f, 0.32f, 1f)
    private val skyCore = argb(0.9f, 0.95f, 1f, 1f)
    private val skyGlow = argb(0.55f, 0.65f, 1f, 1f)

    // ---------------------------------------------------------------- state

    private val local = Vector3d()
    private val local2 = Vector3d()
    private val world = Vector3d()
    private val v3 = Vector3f()
    private val w3 = Vector3f()
    private val e3 = Vector3f()
    private val branch = Array(4) { Vector3f() }
    private val trail = FloatArray(3 * 16)
    private val trail2 = FloatArray(3 * 16)
    private val uv = FloatArray(2)

    /** The ground's height under each strike (world y), read once. */
    private var groundY: DoubleArray? = null

    private fun grounds(frame: SceneFrame): DoubleArray {
        groundY?.let { return it }
        val level = Minecraft.getInstance().level
        val o = frame.camera.origin
        val heights = DoubleArray(s.strikes.size) { k ->
            val strike = s.strikes[k]
            frame.camera.toWorld(strike[1], 0.0, strike[2], world)
            val y = level?.getHeight(Heightmap.Types.MOTION_BLOCKING, floor(world.x).toInt(), floor(world.z).toInt())?.toDouble() ?: o.y
            if (y < o.y - 24.0 || y > o.y + 24.0) o.y else y
        }
        groundY = heights
        return heights
    }

    // ---------------------------------------------------------------- lightning

    /** Bolt seeds change 16 times a second. */
    private fun seed(a: Double, salt: Int): Int = floor(a * 16.0).toInt() * 7919 + salt * 104729

    /** A bolt between camera-relative points, its glow, and branches from its middle. */
    private fun bolt(
        batches: FxBatches, from: Vector3f, to: Vector3f, seed: Int, spread: Float, width: Float, alpha: Float,
        core: Int, glow: Int, branches: Int = 0, branchLength: Float = 0f,
    ) {
        if (alpha <= 0.004f) return
        FxMeshes.bolt(batches.of(boltGlow), from.x, from.y, from.z, to.x, to.y, to.z, seed, spread, width * 4.5f, FxMeshes.withAlpha(glow, 0.32f * alpha))
        FxMeshes.bolt(batches.of(boltCore), from.x, from.y, from.z, to.x, to.y, to.z, seed, spread, width, FxMeshes.withAlpha(core, alpha))
        val n = branches.coerceAtMost(branch.size)
        if (n == 0) return
        for (b in 0 until n) FxMeshes.boltPoint(6 + b * 7, branch[b])
        val dx = to.x - from.x
        val dy = to.y - from.y
        val dz = to.z - from.z
        val len = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(0.01f)
        for (b in 0 until n) {
            val start = branch[b]
            val r1 = FxMeshes.rand(seed, 900 + b) - 0.5f
            val r2 = FxMeshes.rand(seed, 910 + b) - 0.5f
            val ex = start.x + (dx / len * 0.6f + r1) * branchLength
            val ey = start.y + (dy / len * 0.6f - 0.25f) * branchLength
            val ez = start.z + (dz / len * 0.6f + r2) * branchLength
            FxMeshes.bolt(batches.of(boltGlow), start.x, start.y, start.z, ex, ey, ez, seed + 31 * b, spread * 1.2f, width * 2.5f, FxMeshes.withAlpha(glow, 0.22f * alpha))
            FxMeshes.bolt(batches.of(boltCore), start.x, start.y, start.z, ex, ey, ez, seed + 31 * b, spread * 1.2f, width * 0.55f, FxMeshes.withAlpha(core, 0.8f * alpha))
        }
    }

    /** A point on a sphere (a unit direction from a seed). */
    private fun onSphere(seed: Int, salt: Int, out: Vector3f): Vector3f {
        val u = random(seed, salt) * 2.0 - 1.0
        val angle = random(seed, salt + 1) * 2.0 * PI
        val r = sqrt(1.0 - u * u)
        return out.set((r * cos(angle)).toFloat(), u.toFloat(), (r * sin(angle)).toFloat())
    }

    // ---------------------------------------------------------------- events

    override fun event(cue: Cue, frame: SceneFrame) {
        val p = frame.particles
        val id = cue.id
        when {
            id.startsWith("strike:") -> {
                val k = id.substringAfter(':').toIntOrNull() ?: return
                val strike = s.strikes.getOrNull(k) ?: return
                val ground = grounds(frame)[k]
                frame.camera.toWorld(strike[1], 0.0, strike[2], world)
                world.y = ground + 0.2
                sparks(p, world, frame.count(40), 0, 9f, 0.9f, 0.14f, argb(0.85f, 0.9f, 1f, 1f), gravity = 6f)
                sparks(p, world, 3, 1, 0.4f, 0.4f, 2.5f, argb(0.7f, 0.8f, 1f, 0.8f))
                frame.sound(0.0, "minecraft:entity.lightning_bolt.impact", world.x, world.y, world.z, 2.0f, 1.1f + 0.05f * (k % 4))
                frame.sound(frame.soundDelay(world.x, world.y, world.z), "minecraft:entity.lightning_bolt.thunder", world.x, world.y + 6.0, world.z, 2.6f, 0.85f + 0.04f * (k % 5))
            }
            id == "giant" -> {
                frame.camera.toWorld(0.0, 25.0, -9.0, world)
                frame.sound(0.0, "minecraft:entity.lightning_bolt.impact", world.x, world.y, world.z, 2.5f, 0.7f)
                frame.sound(frame.soundDelay(world.x, world.y, world.z) * 0.5, "minecraft:entity.lightning_bolt.thunder", world.x, world.y, world.z, 3.5f, 0.6f)
                // Sparks sucked into the sphere.
                frame.camera.toWorld(0.0, ZapdosScript.SPHERE_Y, 0.0, local2)
                for (i in 0 until frame.count(120)) {
                    onSphere(i * 13 + 7, 1101, v3)
                    val r = 6.0 + 3.0 * random(i, 1103)
                    p.emit(0, local2.x + v3.x * r, local2.y + v3.y * r, local2.z + v3.z * r, -v3.x * r.toFloat() * 1.4f, -v3.y * r.toFloat() * 1.4f, -v3.z * r.toFloat() * 1.4f, 0.62f, 0.12f, 0.05f, argb(1f, 0.92f, 0.5f, 1f))
                }
            }
            id == "burst" -> {
                frame.camera.toWorld(0.0, ZapdosScript.SPHERE_Y, 0.0, world)
                sparks(p, world, frame.count(400), 0, 20f, 1.4f, 0.16f, argb(1f, 0.92f, 0.5f, 1f), drag = 2.4f, gravity = 3f)
                sparks(p, world, frame.count(120), 3, 12f, 1.0f, 0.3f, argb(1f, 0.8f, 0.3f, 1f), drag = 2.8f)
                sparks(p, world, 6, 1, 1.0f, 0.6f, 6f, argb(1f, 0.9f, 0.5f, 0.8f))
            }
            id == "spread" -> {
                s.birdPos(ZapdosScript.SPREAD, local)
                frame.camera.toWorld(local.x, local.y + 0.8, local.z, world)
                sparks(p, world, frame.count(260), 0, 16f, 1.2f, 0.15f, argb(1f, 0.95f, 0.6f, 1f), drag = 2.2f, gravity = 2f)
                sparks(p, world, frame.count(80), 3, 10f, 0.9f, 0.25f, argb(1f, 0.85f, 0.35f, 1f), drag = 2.8f)
            }
            id == "backbolt" -> {
                backBoltPoint(frame, ZapdosScript.BACK_BOLT, local)
                frame.camera.toWorld(local.x, 0.0, local.z, world)
                frame.sound(0.0, "minecraft:entity.lightning_bolt.impact", world.x, world.y, world.z, 2.4f, 0.9f)
                frame.sound(frame.soundDelay(world.x, world.y, world.z), "minecraft:entity.lightning_bolt.thunder", world.x, world.y + 8.0, world.z, 3.0f, 0.75f)
            }
            id == "crackle" -> {
                s.headPos(ZapdosScript.CRY, local)
                frame.camera.toWorld(local.x, local.y, local.z, world)
                sparks(p, world, frame.count(120), 0, 8f, 0.9f, 0.12f, argb(1f, 0.95f, 0.6f, 1f), drag = 2.5f, gravity = 2f)
            }
            id == "landing" -> {
                frame.camera.toWorld(s.landing.x, s.landing.y + 0.15, s.landing.z, world)
                for (i in 0 until frame.count(50)) {
                    val angle = random(i, 1201) * 2.0 * PI
                    val speed = 3.0 + 3.0 * random(i, 1202)
                    p.emit(2, world.x, world.y, world.z, (sin(angle) * speed).toFloat(), 0.4f, (cos(angle) * speed).toFloat(), 1.5f, 0.45f, 2.2f, argb(0.6f, 0.58f, 0.55f, 0.4f), drag = 2.4f)
                }
                sparks(p, world, frame.count(60), 0, 6f, 0.6f, 0.1f, argb(1f, 0.95f, 0.6f, 1f), drag = 2f, gravity = 5f)
            }
            id == "vanish" -> {
                frame.camera.toWorld(s.landing.x, s.landing.y + 1.0, s.landing.z, world)
                sparks(p, world, frame.count(220), 0, 8f, 1.0f, 0.14f, argb(1f, 0.95f, 0.6f, 1f), drag = 2f, gravity = 2f)
                sparks(p, world, 5, 1, 0.5f, 0.5f, 3.5f, argb(1f, 0.9f, 0.5f, 0.85f))
                sparks(p, world, frame.count(50), 2, 2.5f, 1.8f, 1.4f, argb(0.6f, 0.6f, 0.62f, 0.45f), drag = 1.5f, grow = 2.2f)
            }
        }
    }

    private fun sparks(
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
                kind, c.x, c.y, c.z, (r * cos(angle) * k).toFloat(), (u * k + speed * 0.15f).toFloat(), (r * sin(angle) * k).toFloat(),
                life * (0.6f + 0.4f * random(seed, 14).toFloat()), size, size * grow, colour, drag = drag, gravity = gravity, spin = (random(seed, 15).toFloat() - 0.5f) * 8f,
            )
        }
    }

    // ---------------------------------------------------------------- each frame

    private var lastSpark = 0.0

    override fun update(frame: SceneFrame) {
        val a = frame.a
        grounds(frame)
        val o = frame.camera.origin
        frame.particles.axisX = o.x
        frame.particles.axisZ = o.z
        // Sparks jumping off the altar, then around Zapdos while it carries its energy.
        if (lastSpark == 0.0 && a > 0.5) lastSpark = a
        val rate = frame.count(14)
        if (rate > 0) {
            val every = 1.0 / rate
            var i = (lastSpark / every).toInt()
            while (lastSpark + every <= a) {
                lastSpark += every
                i++
                val altar = s.sparks(lastSpark)
                val energy = s.energy(lastSpark)
                if (altar > 0.05) {
                    frame.camera.toWorld((random(i, 1301) - 0.5) * 1.2, 1.1, (random(i, 1302) - 0.5) * 1.2, world)
                    frame.particles.emit(0, world.x, world.y, world.z, ((random(i, 1303) - 0.5) * 6).toFloat(), (2.0 + 3.0 * random(i, 1304)).toFloat(), ((random(i, 1305) - 0.5) * 6).toFloat(), 0.7f, 0.1f, 0.03f, argb(1f, 0.95f, 0.65f, altar.toFloat()), gravity = 9f)
                }
                if (energy > 0.05 && lastSpark < ZapdosScript.LANDING) {
                    s.birdPos(lastSpark, local)
                    frame.camera.toWorld(local.x + (random(i, 1306) - 0.5) * 2.4, local.y + 0.4 + random(i, 1307) * 1.4, local.z + (random(i, 1308) - 0.5) * 2.4, world)
                    frame.particles.emit(0, world.x, world.y, world.z, ((random(i, 1309) - 0.5) * 3).toFloat(), ((random(i, 1310) - 0.5) * 3).toFloat(), ((random(i, 1311) - 0.5) * 3).toFloat(), 0.45f, 0.09f, 0.02f, argb(1f, 0.93f, 0.55f, energy.toFloat()), drag = 1.5f)
                }
            }
        }
        frame.particles.update(frame.da.toFloat())
    }

    override fun draw(frame: SceneFrame, batches: FxBatches) {
        val a = frame.a
        drawAltar(frame, batches, a)
        drawClouds(frame, batches, a)
        drawSphere(frame, batches, a)
        drawStrikes(frame, batches, a)
        drawGiantBolt(frame, batches, a)
        drawBurst(frame, batches, a)
        drawChase(frame, batches, a)
        drawSpread(frame, batches, a)
        drawBackBolt(frame, batches, a)
        frame.particles.render(batches, frame.cam.x, frame.cam.y, frame.cam.z, frame.right, frame.up)
    }

    /** Arcs bouncing on the altar, a glow under them. */
    private fun drawAltar(frame: SceneFrame, batches: FxBatches, a: Double) {
        val altar = s.sparks(a).toFloat()
        if (altar <= 0.004f) return
        val top = frame.rel(0.0, 1.2, 0.0, v3)
        val c = frame.rel(0.0, 0.0, 0.0, w3)
        FxMeshes.flat(batches.of(burn), c.x, c.y + 0.05f, c.z, 2.6f, 0f, argb(1f, 0.85f, 0.45f, 0.6f * altar))
        val topX = top.x
        val topY = top.y
        val topZ = top.z
        for (k in 0 until 4) {
            val sd = seed(a, 20 + k)
            if (FxMeshes.rand(sd, 1) < 0.3f) continue
            val angle = random(sd, 2) * 2.0 * PI
            val r = 1.0 + 2.0 * random(sd, 3)
            val end = frame.rel(sin(angle) * r, 0.05, cos(angle) * r, e3)
            v3.set(topX, topY, topZ)
            val ex = end.x
            val ey = end.y
            val ez = end.z
            bolt(batches, v3, w3.set(ex, ey, ez), sd, 0.25f, 0.05f, altar, zapCore, zapGlow)
            FxMeshes.billboard(batches.of(glows), ex, ey + 0.1f, ez, 0.6f, 0f, argb(1f, 0.9f, 0.5f, 0.7f * altar), frame.right, frame.up)
        }
        FxMeshes.billboard(batches.of(glows), topX, topY, topZ, 1.6f, 0f, argb(1f, 0.92f, 0.55f, 0.55f * altar), frame.right, frame.up)
    }

    /** Storm clouds, lit from inside, with bolts between them. */
    private fun drawClouds(frame: SceneFrame, batches: FxBatches, a: Double) {
        val opacity = s.cloudOpacity(a).toFloat()
        if (opacity <= 0.004f) return
        val r = s.cloudRadius(a).toFloat()
        val c = frame.rel(0.0, 26.0, 0.0, v3)
        val turn = (a * 0.02).toFloat()
        FxMeshes.ring(batches.of(clouds), c.x, c.y, c.z, 0f, r, 64, argb(0.07f, 0.07f, 0.1f, 0.9f * opacity), argb(0.08f, 0.08f, 0.12f, 0f), vScale = 6f, vOffset = turn)
        FxMeshes.ring(batches.of(cloudsHigh), c.x, c.y + 0.8f, c.z, 0f, r * 0.9f, 64, argb(0.12f, 0.12f, 0.17f, 0.6f * opacity), argb(0.12f, 0.12f, 0.17f, 0f), vScale = 5f, vOffset = -turn * 1.5f)
        // Flashes inside the clouds.
        for (k in 0 until 40) {
            val at = 1.0 + k * 0.37 + 0.2 * random(k, 1401)
            val x = (a - at) / 0.13
            if (x < 0.0 || x > 1.0) continue
            val angle = random(k, 1402) * 2.0 * PI
            val d = 4.0 + 30.0 * random(k, 1403)
            val p = frame.rel(sin(angle) * d, 25.4, cos(angle) * d, w3)
            val glow = (sin(x * PI) * (0.6 + 0.4 * random(k, 1404))).toFloat()
            FxMeshes.billboard(batches.of(glows), p.x, p.y, p.z, (10.0 + 6.0 * random(k, 1405)).toFloat(), 0f, argb(0.7f, 0.75f, 1f, 0.55f * glow * opacity), frame.right, frame.up)
        }
        // Bolts between the clouds.
        for (k in 0 until 12) {
            val at = 2.0 + k * 1.15 + 0.3 * random(k, 1501)
            val d = a - at
            if (d < 0.0 || d > 0.18) continue
            val angle = random(k, 1502) * 2.0 * PI
            val r0 = 6.0 + 22.0 * random(k, 1503)
            val length = 8.0 + 8.0 * random(k, 1504)
            val from = frame.rel(sin(angle) * r0, 25.2, cos(angle) * r0, v3)
            val toX = sin(angle + length / r0) * r0
            val toZ = cos(angle + length / r0) * r0
            val to = frame.rel(toX, 24.6, toZ, w3)
            bolt(batches, from, to, seed(a, 40 + k), 0.18f, 0.09f, s.strikeBolt(d * 1.6).toFloat() * opacity, skyCore, skyGlow, 1, 3f)
        }
    }

    private fun drawSphere(frame: SceneFrame, batches: FxBatches, a: Double) {
        val radius = s.sphereRadius(a).toFloat()
        val light = s.sphereLight(a).toFloat()
        if (radius <= 0.01f || light <= 0.004f) return
        val c = frame.rel(0.0, ZapdosScript.SPHERE_Y, 0.0, v3)
        val cx = c.x
        val cy = c.y
        val cz = c.z
        val alpha = light.coerceAtMost(1f)
        FxMeshes.sphere(batches.of(sphere), cx, cy, cz, radius, argb(1f, 0.9f, 0.42f, 0.85f * alpha))
        FxMeshes.sphere(batches.of(sphereInner), cx, cy, cz, radius * 0.6f, argb(1f, 0.97f, 0.8f, 0.5f * alpha), uOffset = (a * 0.3).toFloat())
        FxMeshes.billboard(batches.of(glows), cx, cy, cz, radius * 2.8f, 0f, argb(1f, 0.95f, 0.6f, 0.6f * (light / 1.5f).coerceAtMost(1f)), frame.right, frame.up)
        FxMeshes.billboard(batches.of(glows), cx, cy, cz, radius * 6f, 0f, argb(1f, 0.85f, 0.4f, 0.22f * alpha), frame.right, frame.up)
        // Arcs running over it.
        for (k in 0 until 7) {
            val sd = seed(a, 60 + k)
            onSphere(sd, 3, v3)
            onSphere(sd, 7, w3)
            v3.mul(radius).add(cx, cy, cz)
            w3.mul(radius).add(cx, cy, cz)
            bolt(batches, v3, w3, sd, 0.28f, 0.05f, 0.85f * alpha, zapCore, zapGlow)
        }
        // Down to the altar.
        val tendril = s.tendril(a).toFloat()
        if (tendril > 0.004f) {
            val sd = seed(a, 80)
            v3.set(cx, cy - radius, cz)
            val altar = frame.rel(0.0, 1.2, 0.0, w3)
            bolt(batches, v3, altar, sd, 0.16f, 0.09f, tendril * (0.6f + 0.4f * FxMeshes.rand(sd, 5)), zapCore, zapGlow, 2, 1.6f)
        }
    }

    private fun drawStrikes(frame: SceneFrame, batches: FxBatches, a: Double) {
        val grounds = grounds(frame)
        val o = frame.camera.origin
        for ((k, strike) in s.strikes.withIndex()) {
            val d = a - strike[0]
            if (d < 0.0 || d > 6.0) continue
            val groundLocal = grounds[k] - o.y
            val bottom = frame.rel(strike[1], groundLocal, strike[2], w3)
            val bx = bottom.x
            val by = bottom.y
            val bz = bottom.z
            val on = s.strikeBolt(d).toFloat()
            if (on > 0.004f) {
                val top = frame.rel(strike[1] + 3.0 * (random(k, 1601) - 0.5), 26.0, strike[2] + 3.0 * (random(k, 1602) - 0.5), v3)
                bolt(batches, top, w3.set(bx, by, bz), k * 7717 + floor(d * 25).toInt(), 0.14f, 0.28f, on, skyCore, skyGlow, 3, 5f)
            }
            if (d < 0.35) FxMeshes.billboard(batches.of(glows), bx, by + 0.5f, bz, 7f, 0f, argb(0.75f, 0.82f, 1f, (1.0 - smooth(d / 0.3)).toFloat()), frame.right, frame.up)
            val glow = (1.0 - smooth(d / 3.0)).toFloat()
            if (glow > 0.004f) FxMeshes.flat(batches.of(burn), bx, by + 0.05f, bz, 2.6f, k.toFloat(), argb(1f, 0.5f, 0.15f, 0.9f * glow))
            val dark = (0.55 * smooth(d / 0.2) * (1.0 - smooth((d - 3.0) / 2.5))).toFloat()
            if (dark > 0.004f) FxMeshes.flat(batches.of(scorch), bx, by + 0.04f, bz, 3.2f, k.toFloat(), argb(0.05f, 0.04f, 0.03f, dark))
        }
    }

    private fun drawGiantBolt(frame: SceneFrame, batches: FxBatches, a: Double) {
        val on = s.giantBolt(a).toFloat()
        if (on <= 0.004f) return
        val from = frame.rel(-32.0, 25.0, -12.0, v3)
        val to = frame.rel(30.0, 23.0, -6.0, w3)
        bolt(batches, from, to, 4242 + floor((a - ZapdosScript.GIANT_BOLT) * 12).toInt(), 0.1f, 0.5f, on, skyCore, skyGlow, 4, 9f)
        for (k in 0 until 6) {
            val p = frame.rel(-30.0 + k * 12.0, 25.6, -10.0 + k, v3)
            FxMeshes.billboard(batches.of(glows), p.x, p.y, p.z, 18f, 0f, argb(0.7f, 0.78f, 1f, 0.45f * on), frame.right, frame.up)
        }
    }

    private fun drawBurst(frame: SceneFrame, batches: FxBatches, a: Double) {
        val d = a - ZapdosScript.BURST
        if (d < 0.0 || d > 1.2) return
        val c = frame.rel(0.0, ZapdosScript.SPHERE_Y, 0.0, v3)
        val cx = c.x
        val cy = c.y
        val cz = c.z
        if (d < 1.0) {
            val u = d / 1.0
            FxMeshes.torus(batches.of(shock), cx, cy, cz, (20.0 * easeOutCubic(u)).toFloat(), (0.6 * (1.0 - u) + 0.1).toFloat(), 64, 10, argb(1f, 0.9f, 0.45f, 0.9f * (1.0 - smooth(u)).toFloat()))
        }
        if (d < 0.65) {
            val u = d / 0.6
            FxMeshes.sphere(batches.of(shell), cx, cy, cz, (12.0 * easeOutCubic(u)).toFloat(), argb(1f, 0.92f, 0.55f, (0.6 * (1.0 - smooth(u))).toFloat()))
        }
        if (d < 0.5) FxMeshes.billboard(batches.of(glows), cx, cy, cz, (10.0 * (1.0 + d * 8.0)).toFloat(), 0f, argb(1f, 0.95f, 0.65f, (1.0 - smooth(d / 0.45)).toFloat()), frame.right, frame.up)
        if (d < 0.3) for (k in 0 until 8) {
            val sd = seed(a, 100 + k)
            onSphere(k * 31 + 5, 9, w3)
            val r = (6.0 + 4.0 * random(k, 1701)).toFloat()
            e3.set(cx + w3.x * r, cy + w3.y * r, cz + w3.z * r)
            v3.set(cx, cy, cz)
            bolt(batches, v3, e3, sd, 0.2f, 0.12f, (1.0 - smooth(d / 0.3)).toFloat(), zapCore, zapGlow, 1, 2f)
        }
    }

    /** Electric trails from its wings during the chase, a few arcs on it. */
    private fun drawChase(frame: SceneFrame, batches: FxBatches, a: Double) {
        val energy = s.energy(a).toFloat()
        if (a < ZapdosScript.BURST || a > ZapdosScript.LANDING || energy <= 0.004f) return
        val alpha = energy * (1f - smooth((a - (ZapdosScript.HOVER - 0.3)) / 0.6).toFloat()) * smooth((a - ZapdosScript.BURST) / 0.3).toFloat()
        if (alpha > 0.004f) {
            val count = 15
            for (side in intArrayOf(-1, 1)) {
                val points = if (side < 0) trail else trail2
                for (k in 0 until count) {
                    val at = maxOf(ZapdosScript.BURST, a - k * 0.035)
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
                    val p = frame.rel(local.x - hz * 1.7 * side, local.y + 0.7, local.z + hx * 1.7 * side, v3)
                    points[k * 3] = p.x
                    points[k * 3 + 1] = p.y
                    points[k * 3 + 2] = p.z
                }
                FxMeshes.ribbon(batches.of(trails), points, count, 0.26f, 0.02f, argb(1f, 0.95f, 0.65f, 0.85f * alpha), argb(1f, 0.85f, 0.35f, 0f))
            }
        }
        // Small arcs on its body while it carries the energy.
        s.birdPos(a, local)
        val body = frame.rel(local.x, local.y + 0.9, local.z, e3)
        val bx = body.x
        val by = body.y
        val bz = body.z
        val arcs = (0.45f * energy + s.bodyArcs(a).toFloat()).coerceAtMost(1f)
        for (k in 0 until 4) {
            val sd = seed(a, 120 + k)
            if (FxMeshes.rand(sd, 1) > arcs) continue
            onSphere(sd, 3, v3)
            onSphere(sd, 7, w3)
            v3.mul(1.3f).add(bx, by, bz)
            w3.mul(1.3f).add(bx, by, bz)
            bolt(batches, v3, w3, sd, 0.3f, 0.05f, arcs, zapCore, zapGlow)
        }
        if (arcs > 0.004f) FxMeshes.billboard(batches.of(glows), bx, by, bz, 3.2f, 0f, argb(1f, 0.9f, 0.5f, 0.22f * arcs), frame.right, frame.up)
    }

    /** The wings open: a ring of lightning, a ring wave, arcs to the ground. */
    private fun drawSpread(frame: SceneFrame, batches: FxBatches, a: Double) {
        val ring = s.spreadRing(a).toFloat()
        if (ring <= 0.004f) return
        s.birdPos(ZapdosScript.SPREAD, local)
        val c = frame.rel(local.x, local.y + 0.8, local.z, e3)
        val cx = c.x
        val cy = c.y
        val cz = c.z
        val r = s.spreadRadius(a)
        for (k in 0 until 12) {
            val angle = k / 12.0 * 2.0 * PI + 0.2 * random(k, 1801)
            val to = frame.rel(local.x + sin(angle) * r, local.y + 0.8 + (random(k, 1802) - 0.5) * 2.0, local.z + cos(angle) * r, w3)
            v3.set(cx, cy, cz)
            bolt(batches, v3, to, seed(a, 140 + k), 0.16f, 0.12f, ring, zapCore, zapGlow, 1, 2.5f)
        }
        FxMeshes.torus(batches.of(shock), cx, cy, cz, r.toFloat(), (0.3 * ring + 0.08).toFloat(), 64, 8, argb(1f, 0.92f, 0.5f, 0.85f * ring))
        if (a - ZapdosScript.SPREAD < 0.45) {
            for (k in 0 until 3) {
                val angle = k / 3.0 * 2.0 * PI + 0.7
                val g = frame.rel(local.x + sin(angle) * 3.0, 0.05, local.z + cos(angle) * 3.0, w3)
                v3.set(cx, cy, cz)
                bolt(batches, v3, g, seed(a, 160 + k), 0.15f, 0.14f, ring, zapCore, zapGlow, 1, 2f)
                FxMeshes.flat(batches.of(burn), g.x, g.y + 0.05f, g.z, 2f, k.toFloat(), argb(1f, 0.6f, 0.2f, 0.8f * ring))
            }
            val gc = frame.rel(local.x, 0.06, local.z, w3)
            FxMeshes.ring(batches.of(groundRing), gc.x, gc.y, gc.z, maxOf(0f, r.toFloat() - 2.5f), r.toFloat(), 64, argb(1f, 0.9f, 0.5f, ring), argb(1f, 0.9f, 0.5f, ring), vScale = 10f)
        }
    }

    /** The bolt behind Zapdos (backlight), and its glow. */
    private fun backBoltPoint(frame: SceneFrame, a: Double, out: Vector3d): Vector3d {
        s.birdPos(a, out)
        camLocal(frame, local2)
        var dx = out.x - local2.x
        var dz = out.z - local2.z
        val len = sqrt(dx * dx + dz * dz)
        if (len < 0.1) {
            dx = 0.0
            dz = -1.0
        } else {
            dx /= len
            dz /= len
        }
        return out.set(out.x + dx * 14.0, 0.0, out.z + dz * 14.0)
    }

    /** The camera in the local frame (as it is this frame). */
    private fun camLocal(frame: SceneFrame, out: Vector3d): Vector3d {
        val o = frame.camera.origin
        world.set(frame.cam)
        val dx = world.x - o.x
        val dz = world.z - o.z
        val cos = frame.camera.toWorldDx(1.0, 0.0)
        val sin = frame.camera.toWorldDz(1.0, 0.0)
        return out.set(cos * dx + sin * dz, world.y - o.y, -sin * dx + cos * dz)
    }

    private val backPoint = Vector3d()

    private fun drawBackBolt(frame: SceneFrame, batches: FxBatches, a: Double) {
        val on = s.backBolt(a).toFloat()
        if (on <= 0.004f) return
        backBoltPoint(frame, ZapdosScript.BACK_BOLT, backPoint)
        val bottom = frame.rel(backPoint.x, 0.0, backPoint.z, w3)
        val bx = bottom.x
        val by = bottom.y
        val bz = bottom.z
        val top = frame.rel(backPoint.x + 2.0, 26.0, backPoint.z - 1.0, v3)
        bolt(batches, top, w3, 9191 + floor((a - ZapdosScript.BACK_BOLT) * 14).toInt(), 0.12f, 0.6f, on, skyCore, skyGlow, 3, 6f)
        // The light behind it: Zapdos stands against it.
        s.birdPos(a, local)
        val bird = frame.rel(local.x, local.y + 0.9, local.z, e3)
        val d = bird.length().coerceAtLeast(0.1f)
        bird.mul(1f + 6f / d)
        FxMeshes.billboard(batches.of(glows), bird.x, bird.y, bird.z, 14f, 0f, argb(0.85f, 0.9f, 1f, 0.8f * on), frame.right, frame.up)
        FxMeshes.billboard(batches.of(glows), bx, by + 0.5f, bz, 8f, 0f, argb(0.75f, 0.82f, 1f, 0.8f * on), frame.right, frame.up)
    }

    // ---------------------------------------------------------------- the image

    override fun post(frame: SceneFrame, out: PostParams) {
        val a = frame.a
        // The burst, and the wings' wave.
        val d = a - ZapdosScript.BURST
        if (d >= 0.0 && d < 1.0) {
            val c = frame.rel(0.0, ZapdosScript.SPHERE_Y, 0.0, v3)
            if (frame.project(c, uv)) out.wave(0, uv[0], uv[1], frame.screenRadius(c, (20.0 * easeOutCubic(d)).toFloat()), (0.9 * (1.0 - smooth(d))).toFloat(), (0.04 + 0.05 * d).toFloat())
        }
        val e = a - ZapdosScript.SPREAD
        if (e >= 0.0 && e < 0.8) {
            s.birdPos(ZapdosScript.SPREAD, local)
            val c = frame.rel(local.x, local.y + 0.8, local.z, v3)
            if (frame.project(c, uv)) out.wave(1, uv[0], uv[1], frame.screenRadius(c, s.spreadRadius(a).toFloat()), (0.8 * (1.0 - smooth(e / 0.8))).toFloat(), 0.05f)
        }
        val cry = a - ZapdosScript.CRY
        if (cry >= 0.0 && cry < 0.7) {
            s.headPos(a, local)
            val c = frame.rel(local, v3)
            if (frame.project(c, uv)) out.wave(1, uv[0], uv[1], (0.6 * easeOutCubic(cry / 0.7)).toFloat(), (0.45 * (1.0 - smooth(cry / 0.7))).toFloat(), 0.05f)
        }
        // Radial blur toward the sphere as the orbit speeds up (HIGH).
        val spin = CameraMath.phase(a, 4.0, ZapdosScript.GIANT_BOLT)
        val radial = 0.35 * spin * spin * (1.0 - smooth((a - 9.2) / 0.3)) + 0.6 * CameraMath.pulse(a, ZapdosScript.BURST, 0.05, 0.35) + 0.4 * CameraMath.decay(a, ZapdosScript.SPREAD, 0.3)
        if (radial > 0.004) {
            val target = if (a < ZapdosScript.SPREAD - 0.5) frame.rel(0.0, ZapdosScript.SPHERE_Y, 0.0, v3) else {
                s.birdPos(a, local)
                frame.rel(local.x, local.y + 0.8, local.z, v3)
            }
            if (frame.project(target, uv)) {
                out.radialX = uv[0]
                out.radialY = uv[1]
                out.radial = radial.toFloat()
            }
        }
        // Light rays from the bolt behind it.
        val back = s.backBolt(a).toFloat()
        if (back > 0.004f) {
            s.birdPos(a, local)
            val bird = frame.rel(local.x, local.y + 0.9, local.z, v3)
            bird.mul(1f + 6f / bird.length().coerceAtLeast(0.1f))
            if (frame.project(bird, uv)) {
                out.raysX = uv[0]
                out.raysY = uv[1]
                out.rays = 0.8f * back
                out.raysDecay = 0.96f
            }
        }
        var strikes = 0.0
        for (strike in s.strikes) strikes += CameraMath.decay(a, strike[0], 0.25)
        out.bloom = (0.95 + 0.6 * CameraMath.decay(a, ZapdosScript.BURST, 0.6) + 0.4 * s.giantBolt(a) + 0.2 * strikes.coerceAtMost(1.0)).toFloat()
        out.glow = 1f
        out.aberration = (0.5 + 2.0 * CameraMath.decay(a, ZapdosScript.BURST, 0.35) + 1.5 * CameraMath.decay(a, ZapdosScript.SPREAD, 0.3) + 0.6 * strikes.coerceAtMost(1.0)).toFloat()
        out.flashR = 0.92f
        out.flashG = 0.95f
        out.flashB = 1f
    }

    override fun music(stretch: Double, offset: Double): SynthRenderer = ZapdosMusic.music(script, stretch, offset)

    override fun ambience(stretch: Double, offset: Double): SynthRenderer = ZapdosMusic.ambience(script, stretch, offset)
}
