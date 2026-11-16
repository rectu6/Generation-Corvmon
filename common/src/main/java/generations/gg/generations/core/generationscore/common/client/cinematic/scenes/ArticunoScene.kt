package generations.gg.generations.core.generationscore.common.client.cinematic.scenes

import generations.gg.generations.core.generationscore.common.client.cinematic.audio.ArticunoMusic
import generations.gg.generations.core.generationscore.common.client.cinematic.audio.SynthRenderer
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.easeOutCubic
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.phase
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.random
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.smooth
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.CinematicTextures.Kind
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxBatches
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxBuffer
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxMaterial
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxMaterial.Layer
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxMeshes
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxMeshes.argb
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxParticles
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.PostParams
import net.minecraft.network.chat.Component
import org.joml.Quaternionf
import org.joml.Vector3d
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Articuno — Awakening of the Blizzard: the effects of ArticunoScript, drawn (CINEMATIQUES-PLAN.md
 * § 5). Everything is computed from the scene's time (the same on every client), except the
 * particles of the bursts (the pool).
 */
class ArticunoScene(landing: Vector3d, test: Boolean, override val title: Component) : CinematicScene {
    override val id = "articuno"
    override val script = ArticunoScript(landing, test)
    private val s = script
    override val subtitle: Component = Component.translatable("generations_core.cinematic.articuno.subtitle")

    // ---------------------------------------------------------------- materials

    private val columnCore = FxMaterial("column_core", Layer.ADD, Kind.WHITE).noise(0f, -0.9f, 2f, 0.25f).shape(1.4f, -0.85f, 0f, 1.6f)
    private val columnOuter = FxMaterial("column_outer", Layer.ADD, Kind.WHITE).noise(0.05f, -1.3f, 3f, 0.75f).shape(1.2f, -0.7f, 0f, 1f)
    private val groundGlow = FxMaterial("ground_glow", Layer.ADD, Kind.HALO).shape(0f, 0f, 0.4f, 1f)
    val crystals = FxMaterial("crystals", Layer.SOLID, Kind.WHITE, cull = true)
    private val glows = FxMaterial("glows", Layer.ADD, Kind.HALO)
    private val mist = FxMaterial("mist", Layer.ALPHA, Kind.WHITE).noise(-0.12f, 0.04f, 2.5f, 0.9f).shape(0f, 0f, 1.2f, 1f)
    private val mistHigh = FxMaterial("mist_high", Layer.ALPHA, Kind.WHITE).noise(-0.08f, -0.05f, 3.5f, 0.95f).shape(0f, 0f, 1.2f, 1f)
    private val frost = FxMaterial("frost", Layer.ALPHA, Kind.FROST).shape(0f, 0f, 0.3f, 1f)
    private val frostRing = FxMaterial("frost_ring", Layer.ADD, Kind.RING).noise(0f, 0.1f, 4f, 0.5f).shape(0f, 0f, 0.3f, 1.2f)
    private val coneMaterials = Array(3) { k ->
        FxMaterial("vortex_$k", Layer.ADD, Kind.WHITE).noise(0.18f + 0.1f * k, -0.35f - 0.15f * k, 2.2f + k, 0.9f).shape(1.3f, 0.55f, 0f, 1f)
    }
    private val clouds = FxMaterial("clouds", Layer.ALPHA, Kind.WHITE).noise(0.02f, 0.03f, 3f, 0.85f)
    private val cloudsHigh = FxMaterial("clouds_high", Layer.ALPHA, Kind.WHITE).noise(-0.03f, 0.02f, 4.5f, 0.9f)
    private val cloudRim = FxMaterial("cloud_rim", Layer.ADD, Kind.RING).noise(0f, 0.2f, 3f, 0.6f)
    private val flakes = FxMaterial("flakes", Layer.ADD, Kind.FLAKE)
    private val streaks = FxMaterial("streaks", Layer.ADD, Kind.STREAK)
    private val shock = FxMaterial("shock", Layer.ADD, Kind.WHITE).noise(0.3f, 0f, 3f, 0.5f).shape(1.5f, 0.5f, 0f, 1.3f)
    private val shell = FxMaterial("shell", Layer.ADD, Kind.WHITE).noise(0.1f, 0.2f, 2f, 0.4f).shape(2.5f, 1f, 0f, 1.2f)
    private val wall = FxMaterial("wall", Layer.ADD, Kind.WHITE).noise(0.4f, -0.2f, 3f, 0.85f).shape(0f, 0f, 0.5f, 1f)

    private val particleMaterials = arrayOf(
        FxMaterial("p_flake", Layer.ADD, Kind.FLAKE),
        FxMaterial("p_spark", Layer.ADD, Kind.SPARK),
        FxMaterial("p_smoke", Layer.ALPHA, Kind.SMOKE).shape(0f, 0f, 1f, 1f),
        FxMaterial("p_glow", Layer.ADD, Kind.HALO),
    )

    override fun particles(capacity: Int) = FxParticles(capacity, particleMaterials)

    init {
        crystals.tint[0] = 0.62f; crystals.tint[1] = 0.86f; crystals.tint[2] = 1f; crystals.tint[3] = 0.9f
        crystals.crystal[0] = 0.035f; crystals.crystal[1] = 2.2f; crystals.crystal[2] = 1.05f
    }

    // ---------------------------------------------------------------- state

    /** The vortex's turning, integrated (its speed follows its strength). */
    private var spin = 0.0
    private var spinUntil = 0.0

    private val v3 = Vector3f()
    private val w3 = Vector3f()
    private val local = Vector3d()
    private val local2 = Vector3d()
    private val q = Quaternionf()
    private val trail = FloatArray(3 * 16)
    private val trail2 = FloatArray(3 * 16)
    private val uv = FloatArray(2)

    private fun advanceSpin(a: Double) {
        if (a <= spinUntil) return
        // From where it was (a late client integrates from 0 the first time).
        var x = spinUntil
        while (x < a) {
            val step = minOf(0.05, a - x)
            spin += (0.3 + 0.7 * s.vortex(x + step / 2)) * step
            x += step
        }
        spinUntil = a
    }

    // ---------------------------------------------------------------- events

    override fun event(cue: Cue, frame: SceneFrame) {
        val p = frame.particles
        when (cue.id) {
            "reveal" -> {
                s.birdPos(cue.time, local)
                val c = frame.camera.toWorld(local.x, local.y + 0.8, local.z, local2)
                burst(p, c, frame.count(200), 0, 9f, 1.8f, 0.16f, argb(0.85f, 0.95f, 1f, 0.9f), drag = 2.5f)
                burst(p, c, frame.count(50), 1, 6f, 0.8f, 0.35f, argb(0.8f, 0.95f, 1f, 1f), drag = 3f)
            }
            "burst" -> {
                s.birdPos(ArticunoScript.EXPLOSION, local)
                val c = frame.camera.toWorld(local.x, local.y + 0.8, local.z, local2)
                burst(p, c, frame.count(420), 0, 22f, 2.4f, 0.2f, argb(0.9f, 0.97f, 1f, 0.95f), drag = 2.2f, gravity = 1.5f)
                burst(p, c, frame.count(160), 1, 16f, 1.2f, 0.4f, argb(0.75f, 0.92f, 1f, 1f), drag = 2.8f)
                for (i in 0 until frame.count(40)) {
                    val angle = random(i, 501) * 2.0 * PI
                    val r = 2.0 + 4.0 * random(i, 502)
                    val g = frame.camera.toWorld(local.x + sin(angle) * r, 0.3, local.z + cos(angle) * r, Vector3d())
                    p.emit(2, g.x, g.y, g.z, (sin(angle) * 7.0).toFloat(), 0.6f, (cos(angle) * 7.0).toFloat(), 2.6f, 1.2f, 3.6f, argb(0.86f, 0.92f, 1f, 0.5f), drag = 1.6f)
                }
            }
            "breath" -> {
                s.headPos(ArticunoScript.CRY, local)
                val c = frame.camera.toWorld(local.x, local.y, local.z + 0.6, local2)
                val forward = frame.camera.toWorld(0.0, 0.0, 1.0, Vector3d()).sub(frame.camera.origin.x, frame.camera.origin.y, frame.camera.origin.z)
                for (i in 0 until frame.count(90)) {
                    val spread = 0.35
                    val vx = forward.x * 7.0 + (random(i, 601) - 0.5) * 2 * spread * 7
                    val vy = -1.2 + (random(i, 602) - 0.5) * 2 * spread * 5
                    val vz = forward.z * 7.0 + (random(i, 603) - 0.5) * 2 * spread * 7
                    val kind = if (i % 3 == 0) 2 else 0
                    val colour = if (kind == 2) argb(0.85f, 0.92f, 1f, 0.35f) else argb(0.88f, 0.96f, 1f, 0.9f)
                    p.emit(kind, c.x, c.y, c.z, vx.toFloat(), vy.toFloat(), vz.toFloat(), 1.2f + random(i, 604).toFloat(), if (kind == 2) 0.4f else 0.12f, if (kind == 2) 2.2f else 0.06f, colour, drag = 1.8f, spin = 2f)
                }
            }
            "landing" -> {
                val c = frame.camera.toWorld(s.landing.x, s.landing.y + 0.15, s.landing.z, local2)
                for (i in 0 until frame.count(60)) {
                    val angle = random(i, 701) * 2.0 * PI
                    val speed = 3.0 + 3.0 * random(i, 702)
                    p.emit(2, c.x, c.y, c.z, (sin(angle) * speed).toFloat(), 0.5f, (cos(angle) * speed).toFloat(), 1.6f, 0.5f, 2.4f, argb(0.9f, 0.94f, 1f, 0.45f), drag = 2.4f)
                }
                burst(p, c, frame.count(60), 0, 4f, 1.2f, 0.1f, argb(0.9f, 0.96f, 1f, 0.85f), drag = 2.4f, gravity = 0.6f)
            }
            "vanish" -> {
                val c = frame.camera.toWorld(s.landing.x, s.landing.y + 1.0, s.landing.z, local2)
                burst(p, c, frame.count(220), 0, 6f, 1.6f, 0.14f, argb(0.92f, 0.97f, 1f, 0.95f), drag = 2f, gravity = 0.5f)
                burst(p, c, frame.count(80), 1, 4f, 0.9f, 0.3f, argb(0.8f, 0.94f, 1f, 1f), drag = 2.5f)
                burst(p, c, frame.count(70), 2, 2.5f, 2.2f, 1.6f, argb(0.92f, 0.95f, 1f, 0.5f), drag = 1.5f, grow = 2.2f)
                burst(p, c, 4, 3, 0.5f, 0.5f, 3.5f, argb(0.7f, 0.9f, 1f, 0.8f), drag = 1f)
            }
        }
    }

    /** Particles in every direction from c. */
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

    private var lastMist = 0.0

    override fun update(frame: SceneFrame) {
        val a = frame.a
        advanceSpin(a)
        val o = frame.camera.origin
        frame.particles.axisX = o.x
        frame.particles.axisZ = o.z
        // Cold mist crawling outward from the altar, a few puffs a second.
        val rate = frame.count(10)
        // A late client starts the mist where the scene is (no burst of every puff missed).
        if (lastMist == 0.0 && a > 0.5) lastMist = a
        if (a in 0.2..16.0 && rate > 0) {
            val every = 1.0 / rate
            var i = (lastMist / every).toInt()
            while (lastMist + every <= a) {
                lastMist += every
                i++
                val angle = random(i, 801) * 2.0 * PI
                val r = 0.8 + 5.0 * random(i, 802)
                val c = frame.camera.toWorld(sin(angle) * r, 0.25, cos(angle) * r, local2)
                frame.particles.emit(2, c.x, c.y, c.z, (sin(angle) * 1.2).toFloat(), 0.05f, (cos(angle) * 1.2).toFloat(), 4.5f, 1.5f, 4.0f, argb(0.82f, 0.9f, 1f, 0.28f * s.mistOpacity(a).toFloat() * 1.8f), drag = 0.3f, swirl = (0.2 * s.vortex(a)).toFloat())
            }
        } else lastMist = maxOf(lastMist, a)
        frame.particles.update(frame.da.toFloat())
    }

    override fun draw(frame: SceneFrame, batches: FxBatches) {
        val a = frame.a
        val medium = frame.config.post
        drawColumn(frame, batches, a)
        drawCrystals(frame, batches, a)
        drawGround(frame, batches, a)
        drawVortex(frame, batches, a)
        drawClouds(frame, batches, a)
        drawShards(frame, batches, a)
        drawBacklight(frame, batches, a, medium)
        drawTrails(frame, batches, a)
        drawBurst(frame, batches, a)
        drawSnow(frame, batches, a)
        frame.particles.render(batches, frame.cam.x, frame.cam.y, frame.cam.z, frame.right, frame.up)
    }

    private fun drawColumn(frame: SceneFrame, batches: FxBatches, a: Double) {
        val light = s.columnLight(a).toFloat()
        if (light <= 0.002f) return
        val height = s.columnHeight(a).toFloat()
        val c = frame.rel(0.0, 0.0, 0.0, v3)
        val pulse = 1f + 0.08f * sin(a * 9.0).toFloat()
        FxMeshes.cylinder(batches.of(columnCore), c.x, c.y, c.z, 0.6f, height, 0.42f * pulse, 0.5f, 20, 8, argb(0.6f, 0.88f, 1f, 0.95f * light), argb(0.6f, 0.88f, 1f, 0f), uScale = 2f, vScale = height / 6f)
        FxMeshes.cylinder(batches.of(columnOuter), c.x, c.y, c.z, 0.2f, height * 0.85f, 1.2f, 1.6f, 24, 8, argb(0.45f, 0.75f, 1f, 0.35f * light), argb(0.45f, 0.75f, 1f, 0f), uScale = 3f, vScale = height / 4f)
        FxMeshes.flat(batches.of(groundGlow), c.x, c.y + 0.05f, c.z, 3.6f, 0f, argb(0.55f, 0.85f, 1f, 0.75f * light))
        // The column's top: a soft glow where it meets the clouds.
        FxMeshes.billboard(batches.of(glows), c.x, c.y + height * 0.8f, c.z, 3.5f, 0f, argb(0.6f, 0.9f, 1f, 0.35f * light * smooth((a - 4.0) / 2.0).toFloat()), frame.right, frame.up)
    }

    private fun drawCrystals(frame: SceneFrame, batches: FxBatches, a: Double) {
        if (a >= ArticunoScript.EXPLOSION) return
        val buffer = batches.of(crystals)
        val glow = batches.of(glows)
        val radius = s.crystalRadius(a)
        for (i in 0 until 12) {
            val rise = s.crystalRise(i, a)
            if (rise <= 0.0) continue
            val angle = Math.toRadians(s.crystalAngle(i, a))
            val y = CameraMath.lerp(-1.6, 0.35, rise) + s.crystalLift(i, a)
            val c = frame.rel(sin(angle) * radius, y, cos(angle) * radius, v3)
            val tall = (1.1 + 0.5 * random(i, 41)).toFloat()
            q.identity().rotateY((angle + PI).toFloat()).rotateX(Math.toRadians(-15.0).toFloat()).rotateY((a * 0.7 + i).toFloat())
            FxMeshes.crystal(buffer, c.x, c.y, c.z, q, 0.3f, tall, argb(1f, 1f, 1f, 1f))
            val flicker = 0.75f + 0.25f * sin(a * 7.0 + i * 1.7).toFloat()
            FxMeshes.billboard(glow, c.x, c.y + tall * 0.45f, c.z, 0.9f, 0f, argb(0.55f, 0.85f, 1f, 0.38f * flicker * rise.toFloat()), frame.right, frame.up)
        }
    }

    private fun drawGround(frame: SceneFrame, batches: FxBatches, a: Double) {
        val c = frame.rel(0.0, 0.0, 0.0, v3)
        val cx = c.x
        val cy = c.y
        val cz = c.z
        // The frost left on the ground.
        val decal = s.frostDecalOpacity(a).toFloat()
        if (decal > 0.002f) frostDecal(batches.of(frost), cx, cy + 0.03f, cz, s.frostDecalRadius(a).toFloat(), decal)
        // The frost ring spreading from the altar.
        val ring = (s.frostOpacity(a) * (1.0 - 0.5 * phase(a, 3.0, 6.0))).toFloat()
        if (ring > 0.002f) {
            val r = s.frostRadius(a).toFloat()
            FxMeshes.ring(batches.of(frostRing), cx, cy + 0.04f, cz, maxOf(0f, r - 0.9f), r, 64, argb(0.7f, 0.9f, 1f, ring), argb(0.7f, 0.9f, 1f, ring), vScale = 8f)
        }
        // The cold wave of the burst, and its wall of snow.
        val wave = s.coldWaveOpacity(a).toFloat()
        if (wave > 0.002f) {
            val r = s.coldWaveRadius(a).toFloat()
            FxMeshes.ring(batches.of(frostRing), cx, cy + 0.06f, cz, maxOf(0f, r - 3f), r, 96, argb(0.75f, 0.93f, 1f, wave), argb(0.75f, 0.93f, 1f, wave), vScale = 14f)
            FxMeshes.cylinder(batches.of(wall), cx, cy, cz, 0f, 1.8f * wave, r, r + 0.4f, 64, 2, argb(0.8f, 0.94f, 1f, 0.55f * wave), argb(0.8f, 0.94f, 1f, 0f), uScale = 10f)
        }
        // Cold mist in sheets.
        val mistAlpha = s.mistOpacity(a).toFloat()
        if (mistAlpha > 0.002f) {
            val r = s.mistRadius(a).toFloat()
            val turn = (spin * 0.05).toFloat()
            FxMeshes.ring(batches.of(mist), cx, cy + 0.12f, cz, 0f, r, 48, argb(0.82f, 0.9f, 0.98f, 0.5f * mistAlpha), argb(0.82f, 0.9f, 0.98f, 0f), vScale = 3f, vOffset = turn)
            FxMeshes.ring(batches.of(mist), cx, cy + 0.45f, cz, 0f, r * 0.85f, 48, argb(0.85f, 0.92f, 1f, 0.38f * mistAlpha), argb(0.85f, 0.92f, 1f, 0f), vScale = 4f, vOffset = -turn * 1.3f)
            FxMeshes.ring(batches.of(mistHigh), cx, cy + 0.85f, cz, 0f, r * 0.7f, 48, argb(0.88f, 0.94f, 1f, 0.26f * mistAlpha), argb(0.88f, 0.94f, 1f, 0f), vScale = 5f, vOffset = turn * 1.7f)
        }
    }

    /** Frost tiles over a disc, fading toward its edge. */
    private fun frostDecal(buffer: FxBuffer, cx: Float, cy: Float, cz: Float, radius: Float, alpha: Float) {
        if (radius <= 0.1f) return
        val n = 12
        val cell = radius * 2f / n
        fun corner(i: Int, j: Int) {
            val x = -radius + i * cell
            val z = -radius + j * cell
            val d = sqrt(x * x + z * z) / radius
            val fade = (1f - smooth((d - 0.55) / 0.45).toFloat()).coerceIn(0f, 1f)
            buffer.vertex(cx + x, cy, cz + z, x / 5f, z / 5f, argb(0.85f, 0.93f, 1f, alpha * fade))
        }
        for (i in 0 until n) for (j in 0 until n) {
            corner(i, j)
            corner(i + 1, j)
            corner(i + 1, j + 1)
            corner(i, j + 1)
        }
    }

    private fun drawVortex(frame: SceneFrame, batches: FxBatches, a: Double) {
        val vortex = s.vortex(a).toFloat()
        if (vortex <= 0.002f) return
        val c = frame.rel(0.0, 0.0, 0.0, v3)
        for (k in 0 until 3) {
            val buffer = batches.of(coneMaterials[k])
            val turn = (-spin * (0.25 + 0.12 * k)).toFloat()
            val r0 = 1.4f + 0.8f * k
            val r1 = 7f + 2.5f * k
            val mid = 0.3f * vortex / (1f + 0.25f * k)
            val colour = argb(0.78f, 0.9f, 1f, 0f)
            FxMeshes.cylinder(buffer, c.x, c.y, c.z, 1.5f, 8f, r0, r0 + (r1 - r0) * 0.3f, 40, 4, colour, FxMeshes.withAlpha(colour, mid), uScale = 3f, uOffset = turn, vScale = 0.5f, twist = 0.35f)
            FxMeshes.cylinder(buffer, c.x, c.y, c.z, 8f, 23f, r0 + (r1 - r0) * 0.3f, r1, 40, 8, FxMeshes.withAlpha(colour, mid), FxMeshes.withAlpha(colour, mid * 0.2f), uScale = 3f, uOffset = turn + 0.35f * 3f, vScale = 1.5f, vOffset = 0.5f, twist = 1.1f)
        }
    }

    private fun drawClouds(frame: SceneFrame, batches: FxBatches, a: Double) {
        val opacity = s.cloudOpacity(a).toFloat()
        if (opacity <= 0.002f) return
        val r = s.cloudRadius(a).toFloat()
        val c = frame.rel(0.0, 24.0, 0.0, v3)
        val turn = (spin * 0.035).toFloat()
        FxMeshes.ring(batches.of(clouds), c.x, c.y, c.z, 2.8f, r, 64, argb(0.1f, 0.13f, 0.19f, 0.85f * opacity), argb(0.12f, 0.15f, 0.22f, 0f), vScale = 6f, vOffset = turn)
        FxMeshes.ring(batches.of(cloudsHigh), c.x, c.y + 0.8f, c.z, 3.5f, r * 0.85f, 64, argb(0.2f, 0.25f, 0.34f, 0.55f * opacity), argb(0.2f, 0.25f, 0.34f, 0f), vScale = 5f, vOffset = -turn * 1.4f)
        val light = (0.35f + 0.65f * s.columnLight(a).toFloat()) * opacity
        FxMeshes.ring(batches.of(cloudRim), c.x, c.y - 0.2f, c.z, 2.6f, 7f, 48, argb(0.55f, 0.85f, 1f, 0.6f * light), argb(0.55f, 0.85f, 1f, 0f), vScale = 4f, vOffset = turn)
        // Ice glinting in the clouds.
        if (a in 5.0..13.5) for (k in 0 until 12) {
            val at = 5.0 + k * 0.7 + 0.4 * random(k, 901)
            val x = (a - at) / 0.22
            if (x < 0.0 || x > 1.0) continue
            val angle = random(k, 902) * 2.0 * PI
            val d = 6.0 + 14.0 * random(k, 903)
            val p = frame.rel(sin(angle) * d, 23.4, cos(angle) * d, w3)
            val glow = sin(x * PI).toFloat()
            FxMeshes.billboard(batches.of(glows), p.x, p.y, p.z, 5f, 0f, argb(0.7f, 0.9f, 1f, 0.55f * glow * opacity), frame.right, frame.up)
        }
    }

    /** The 30 floating ice shards of the vortex, flung by the burst. */
    private fun drawShards(frame: SceneFrame, batches: FxBatches, a: Double) {
        val presence = s.shardsPresence(a)
        if (presence <= 0.0 || a > 16.2) return
        val buffer = batches.of(crystals)
        for (i in 0 until 30) {
            val r = 3.0 + 5.0 * random(i, 301)
            val h = 3.0 + 14.0 * random(i, 302)
            val omega = 1.6 + 2.0 / r
            val turnAt = minOf(spin, spinAtBurst())
            val angle = random(i, 303) * 2.0 * PI + omega * turnAt
            var x = sin(angle) * r
            var y = h + 0.4 * sin(a * 1.3 + i)
            var z = cos(angle) * r
            var scale = presence
            if (a > ArticunoScript.EXPLOSION) {
                val d = a - ArticunoScript.EXPLOSION
                val out = 14.0 * (1.0 - exp(-1.6 * d)) / 1.6
                x += sin(angle) * out
                z += cos(angle) * out
                y += 4.0 * (1.0 - exp(-1.6 * d)) / 1.6 - 2.5 * d * d
                y = maxOf(y, 0.2)
                scale *= 1.0 - smooth((a - 15.0) / 1.0)
            }
            if (scale <= 0.01) continue
            val c = frame.rel(x, y, z, v3)
            q.identity().rotateAxis((a * (1.5 + random(i, 304))).toFloat(), w3.set(random(i, 305).toFloat() - 0.5f, 1f, random(i, 306).toFloat() - 0.5f).normalize())
            FxMeshes.crystal(buffer, c.x, c.y, c.z, q, (0.1 + 0.08 * random(i, 307)).toFloat() * scale.toFloat(), (0.35 + 0.25 * random(i, 308)).toFloat() * scale.toFloat(), argb(1f, 1f, 1f, 1f))
        }
    }

    private var burstSpin = -1.0

    private fun spinAtBurst(): Double {
        if (spinUntil >= ArticunoScript.EXPLOSION && burstSpin < 0.0) burstSpin = spin
        return if (burstSpin >= 0.0) burstSpin else Double.MAX_VALUE
    }

    private fun drawBacklight(frame: SceneFrame, batches: FxBatches, a: Double, medium: Boolean) {
        val light = s.backlight(a).toFloat()
        if (light <= 0.002f) return
        val centre = backlightCentre(frame, a)
        val pulse = 1f + 0.06f * sin(a * 5.0).toFloat()
        FxMeshes.billboard(batches.of(glows), centre.x, centre.y, centre.z, 9f * pulse, 0f, argb(0.78f, 0.92f, 1f, 0.95f * light), frame.right, frame.up)
        FxMeshes.billboard(batches.of(glows), centre.x, centre.y, centre.z, 24f, 0f, argb(0.55f, 0.8f, 1f, 0.35f * light), frame.right, frame.up)
        if (!medium) return
        val buffer = batches.of(streaks)
        for (k in 0 until 10) {
            val angle = k / 10.0 * 2.0 * PI + a * 0.15
            val length = (12.0 + 6.0 * random(k, 401)).toFloat()
            val dx = (cos(angle) * frame.right.x + sin(angle) * frame.up.x).toFloat()
            val dy = (cos(angle) * frame.right.y + sin(angle) * frame.up.y).toFloat()
            val dz = (cos(angle) * frame.right.z + sin(angle) * frame.up.z).toFloat()
            FxMeshes.beam(
                buffer, centre.x, centre.y, centre.z, centre.x + dx * length, centre.y + dy * length, centre.z + dz * length,
                0.5f, 2.6f, argb(0.75f, 0.9f, 1f, 0.3f * light), argb(0.75f, 0.9f, 1f, 0f),
            )
        }
    }

    /** Behind Articuno as seen from the camera. */
    private fun backlightCentre(frame: SceneFrame, a: Double): Vector3f {
        s.birdPos(a, local)
        val bird = frame.rel(local.x, local.y + 0.9, local.z, w3)
        val d = bird.length().coerceAtLeast(0.1f)
        return bird.mul(1f + 5f / d)
    }

    private fun drawTrails(frame: SceneFrame, batches: FxBatches, a: Double) {
        val alpha = s.trails(a).toFloat()
        if (alpha <= 0.002f) return
        val count = 15
        for (side in intArrayOf(-1, 1)) {
            val points = if (side < 0) trail else trail2
            for (k in 0 until count) {
                val at = a - k * 0.04
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
                val p = frame.rel(local.x - hz * 1.5 * side, local.y + 0.5, local.z + hx * 1.5 * side, v3)
                points[k * 3] = p.x
                points[k * 3 + 1] = p.y
                points[k * 3 + 2] = p.z
            }
            FxMeshes.ribbon(batches.of(streaks), points, count, 0.22f, 0.02f, argb(0.82f, 0.95f, 1f, 0.8f * alpha), argb(0.82f, 0.95f, 1f, 0f))
        }
    }

    private fun drawBurst(frame: SceneFrame, batches: FxBatches, a: Double) {
        val d = a - ArticunoScript.EXPLOSION
        if (d < 0.0 || d > 3.5) return
        s.birdPos(ArticunoScript.EXPLOSION, local)
        // The ring of crystals and 28 more from Articuno, flung and falling.
        val buffer = batches.of(crystals)
        for (i in 0 until 40) {
            val fromRing = i < 12
            val sx: Double
            val sy: Double
            val sz: Double
            val dx: Double
            val dy: Double
            val dz: Double
            if (fromRing) {
                val angle = Math.toRadians(s.crystalAngle(i, ArticunoScript.EXPLOSION))
                val r = s.crystalRadius(ArticunoScript.EXPLOSION)
                sx = sin(angle) * r
                sy = 0.35 + s.crystalLift(i, ArticunoScript.EXPLOSION)
                sz = cos(angle) * r
                dx = sin(angle)
                dy = 0.6
                dz = cos(angle)
            } else {
                val u = random(i, 201) * 2.0 - 1.0
                val angle = random(i, 202) * 2.0 * PI
                val r = sqrt(1.0 - u * u)
                sx = local.x
                sy = local.y + 0.8
                sz = local.z
                dx = r * cos(angle)
                dy = u * 0.7 + 0.25
                dz = r * sin(angle)
            }
            val speed = 10.0 + 12.0 * random(i, 203)
            val k = 1.4
            val g = 9.8
            val e = (1.0 - exp(-k * d)) / k
            val x = sx + dx * speed * e
            val z = sz + dz * speed * e
            val y = maxOf(0.15, sy + (dy * speed + g / k) * e - g * d / k)
            val fade = 1.0 - smooth((d - 2.2) / 1.0)
            if (fade <= 0.01) continue
            val p = frame.rel(x, y, z, w3)
            q.identity().rotateAxis((d * (6.0 + 6.0 * random(i, 204)) * exp(-0.6 * d)).toFloat(), v3.set(random(i, 205).toFloat() - 0.5f, random(i, 206).toFloat() - 0.5f, 0.3f).normalize())
            val size = if (fromRing) 1f else (0.45f + 0.4f * random(i, 207).toFloat())
            FxMeshes.crystal(buffer, p.x, p.y, p.z, q, 0.28f * size * fade.toFloat(), 1.1f * size * fade.toFloat(), argb(1f, 1f, 1f, 1f))
        }
        val centre = frame.rel(local.x, local.y + 0.8, local.z, v3)
        // Shock wave (torus) and shell (rim of a sphere).
        if (d < 1.2) {
            val u = d / 1.1
            val alpha = (1.0 - smooth(u)).toFloat()
            FxMeshes.torus(batches.of(shock), centre.x, centre.y, centre.z, (25.0 * easeOutCubic(u)).toFloat(), (0.7 * (1.0 - u) + 0.12).toFloat(), 64, 10, argb(0.7f, 0.9f, 1f, 0.9f * alpha))
        }
        if (d < 0.75) {
            val u = d / 0.7
            FxMeshes.sphere(batches.of(shell), centre.x, centre.y, centre.z, (14.0 * easeOutCubic(u)).toFloat(), argb(0.75f, 0.92f, 1f, (0.6 * (1.0 - smooth(u))).toFloat()))
        }
        if (d < 0.6) {
            val glow = (1.0 - smooth(d / 0.5)).toFloat()
            FxMeshes.billboard(batches.of(glows), centre.x, centre.y, centre.z, (12.0 * (1.0 + d * 8.0)).toFloat(), 0f, argb(0.85f, 0.95f, 1f, glow), frame.right, frame.up)
        }
    }

    /** The vortex's snow (in a helical flow) and the snowfall around it. */
    private fun drawSnow(frame: SceneFrame, batches: FxBatches, a: Double) {
        val buffer = batches.of(flakes)
        val o = frame.camera.origin
        val vortex = smooth((a - 3.5) / 2.5) * (1.0 - smooth((a - 16.0) / 2.0))
        val blown = if (a > ArticunoScript.EXPLOSION) 1.4 * easeOutCubic((a - ArticunoScript.EXPLOSION) / 1.2) else 0.0
        val clearing = smooth((a - 16.0) / 2.0)
        if (vortex > 0.003) {
            val n = frame.count(2000)
            for (i in 0 until n) {
                val r0 = 1.2 + 11.8 * sqrt(random(i, 1))
                val h0 = 23.0 * random(i, 2)
                val vy = 2.5 + 2.5 * random(i, 3)
                val omega = (2.4 + 3.5 / (r0 + 0.6)) * (0.8 + 0.4 * random(i, 4))
                var y = (h0 + vy * a) % 23.0
                val angle = random(i, 5) * 2.0 * PI + omega * spin
                val r = r0 * (1.0 + 0.12 * sin(a * 0.8 + i)) * (1.0 - 0.2 * s.vortex(a)) * (1.0 + blown) * (1.0 + 0.8 * clearing)
                y -= 4.0 * clearing * clearing
                val edge = smooth(y / 1.5) * smooth((23.0 - y) / 2.0)
                val alpha = (0.75 * vortex * edge).toFloat()
                if (alpha < 0.01f) continue
                val wx = o.x + frame.camera.toWorldDx(sin(angle) * r, cos(angle) * r)
                val wz = o.z + frame.camera.toWorldDz(sin(angle) * r, cos(angle) * r)
                val px = (wx - frame.cam.x).toFloat()
                val py = (o.y + y - frame.cam.y).toFloat()
                val pz = (wz - frame.cam.z).toFloat()
                val size = (0.04 + 0.07 * random(i, 6)).toFloat()
                FxMeshes.billboard(buffer, px, py, pz, size, (a * (random(i, 7) * 4.0 - 2.0)).toFloat(), argb(0.86f, 0.94f, 1f, alpha), frame.right, frame.up)
            }
        }
        val fall = s.snowfall(a)
        if (fall > 0.003) {
            val n = frame.count(700)
            for (i in 0 until n) {
                val angle = random(i, 21) * 2.0 * PI + 0.15 * spin
                val r = 22.0 * sqrt(random(i, 22))
                val speed = 1.2 + 1.3 * random(i, 23)
                val y = 22.0 - ((random(i, 24) * 22.0 + speed * a) % 22.0)
                val edge = smooth(y / 1.0) * smooth((22.0 - y) / 2.0)
                val alpha = (0.6 * fall * edge).toFloat()
                if (alpha < 0.01f) continue
                val lx = sin(angle) * r + 0.6 * sin(a * 0.7 + i)
                val lz = cos(angle) * r
                val px = (o.x + frame.camera.toWorldDx(lx, lz) - frame.cam.x).toFloat()
                val py = (o.y + y - frame.cam.y).toFloat()
                val pz = (o.z + frame.camera.toWorldDz(lx, lz) - frame.cam.z).toFloat()
                FxMeshes.billboard(buffer, px, py, pz, (0.03 + 0.04 * random(i, 25)).toFloat(), (a * 1.3 + i).toFloat(), argb(0.9f, 0.95f, 1f, alpha), frame.right, frame.up)
            }
        }
    }

    // ---------------------------------------------------------------- the image

    override fun post(frame: SceneFrame, out: PostParams) {
        val a = frame.a
        val d = a - ArticunoScript.EXPLOSION
        if (d >= 0.0 && d < 1.2) {
            s.birdPos(ArticunoScript.EXPLOSION, local)
            val c = frame.rel(local.x, local.y + 0.8, local.z, v3)
            if (frame.project(c, uv)) {
                val radius = frame.screenRadius(c, (25.0 * easeOutCubic(d / 1.1)).toFloat())
                out.wave(0, uv[0], uv[1], radius, (0.9 * (1.0 - smooth(d / 1.0))).toFloat(), (0.04 + 0.05 * d).toFloat())
            }
        }
        val cry = a - ArticunoScript.CRY
        if (cry >= 0.0 && cry < 0.7) {
            s.headPos(a, local)
            val c = frame.rel(local, v3)
            if (frame.project(c, uv)) out.wave(1, uv[0], uv[1], (0.6 * easeOutCubic(cry / 0.7)).toFloat(), (0.5 * (1.0 - smooth(cry / 0.7))).toFloat(), 0.05f)
        }
        val light = s.backlight(a).toFloat()
        if (light > 0.002f) {
            val c = backlightCentre(frame, a)
            if (frame.project(c, uv)) {
                out.raysX = uv[0]
                out.raysY = uv[1]
                out.rays = 0.9f * light
                out.raysDecay = 0.96f
            }
        }
        out.bloom = (0.85 + 0.6 * CameraMath.decay(a, ArticunoScript.EXPLOSION, 0.6)).toFloat()
        out.glow = 1f
        out.aberration = (0.5 + 2.5 * CameraMath.decay(a, ArticunoScript.EXPLOSION, 0.35) + 0.8 * CameraMath.decay(a, ArticunoScript.REVEAL, 0.3)).toFloat()
        out.flashR = 0.88f
        out.flashG = 0.95f
        out.flashB = 1f
    }

    override fun music(stretch: Double, offset: Double): SynthRenderer = ArticunoMusic.music(script, stretch, offset)

    override fun ambience(stretch: Double, offset: Double): SynthRenderer = ArticunoMusic.ambience(script, stretch, offset)
}
