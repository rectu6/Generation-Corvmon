package generations.gg.generations.core.generationscore.common.client.cinematic.scenes

import generations.gg.generations.core.generationscore.common.client.cinematic.audio.MewtwoMusic
import generations.gg.generations.core.generationscore.common.client.cinematic.audio.SynthRenderer
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath
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
import generations.gg.generations.core.generationscore.common.world.level.block.rks.RksModuleBlock
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import org.joml.Quaternionf
import org.joml.Vector3d
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * The awakening of Mewtwo (MewtwoScript), drawn: the emergency lights and the violet fog round
 * the machine, the arcs, the cracks spreading on the glass, its 3D shards (refracting, the crystal
 * shader) thrown out with the liquid, the shock wave, the aura round Mewtwo, debris levitating,
 * its cry's psychic wave. The machine's own model (its glass, then its broken glass) is drawn by
 * its renderer; with no machine there (/generations cinematic test mewtwo) the scene draws a tank
 * of its own.
 */
class MewtwoScene(landing: Vector3d, test: Boolean, override val title: Component, tankFeet: Double = 1.05) : CinematicScene {
    override val id = "mewtwo"
    override val script = MewtwoScript(landing, test, tankFeet)
    private val s = script
    override val subtitle: Component = Component.translatable("generations_core.cinematic.mewtwo.subtitle")

    val shards = FxMaterial("m2_shards", Layer.SOLID, Kind.WHITE, cull = true)
    val debris = FxMaterial("m2_debris", Layer.SOLID, Kind.WHITE, cull = true)
    private val tube = FxMaterial("m2_tube", Layer.ALPHA, Kind.WHITE).shape(1.6f, 0.7f, 0f, 1f)
    private val liquid = FxMaterial("m2_liquid", Layer.ALPHA, Kind.WHITE).noise(0.05f, 0.25f, 3f, 0.3f).shape(0f, 0f, 0f, 1f)
    private val cracks = FxMaterial("m2_cracks", Layer.ADD, Kind.WHITE)
    private val glows = FxMaterial("m2_glows", Layer.ADD, Kind.HALO)
    private val beams = FxMaterial("m2_beams", Layer.ADD, Kind.STREAK)
    private val fog = FxMaterial("m2_fog", Layer.ALPHA, Kind.WHITE).noise(0.03f, 0.02f, 3f, 0.85f).shape(0f, 0f, 1.2f, 1f)
    private val fogHigh = FxMaterial("m2_fog_high", Layer.ALPHA, Kind.WHITE).noise(-0.02f, 0.03f, 4f, 0.9f).shape(0f, 0f, 1.2f, 1f)
    private val aura = FxMaterial("m2_aura", Layer.ADD, Kind.WHITE).noise(0.2f, 0.5f, 2.5f, 0.6f).shape(2.2f, 1f, 0f, 1.2f)
    private val shock = FxMaterial("m2_shock", Layer.ADD, Kind.WHITE).noise(0.3f, 0f, 3f, 0.5f).shape(1.5f, 0.5f, 0f, 1.3f)
    private val wave = FxMaterial("m2_wave", Layer.ADD, Kind.RING).noise(0f, 0.2f, 4f, 0.5f).shape(0f, 0f, 0.3f, 1.2f)
    private val arcs = FxMaterial("m2_arcs", Layer.ADD, Kind.WHITE)

    private val particleMaterials = arrayOf(
        FxMaterial("m2_p_spark", Layer.ADD, Kind.SPARK),
        FxMaterial("m2_p_drop", Layer.ALPHA, Kind.SMOKE).shape(0f, 0f, 0.6f, 1f),
        FxMaterial("m2_p_glow", Layer.ADD, Kind.HALO),
        FxMaterial("m2_p_dust", Layer.ALPHA, Kind.SMOKE).shape(0f, 0f, 1f, 1f),
    )

    override fun particles(capacity: Int) = FxParticles(capacity, particleMaterials)

    init {
        shards.tint[0] = 0.72f; shards.tint[1] = 0.92f; shards.tint[2] = 1f; shards.tint[3] = 0.55f
        shards.crystal[0] = 0.045f; shards.crystal[1] = 2.6f; shards.crystal[2] = 1.1f
        debris.tint[0] = 0.42f; debris.tint[1] = 0.3f; debris.tint[2] = 0.62f; debris.tint[3] = 0.7f
        debris.crystal[0] = 0.02f; debris.crystal[1] = 2.0f; debris.crystal[2] = 0.8f
    }

    /** A machine stands at the scene's origin (its renderer draws the tank): checked once. */
    private var machine: Boolean? = null

    private val v3 = Vector3f()
    private val w3 = Vector3f()
    private val local = Vector3d()
    private val world = Vector3d()
    private val q = Quaternionf()
    private val uv = FloatArray(2)
    private var lastSmoke = 0.0
    private var lastMote = 0.0

    // ---------------------------------------------------------------- the glass's cracks (on the cylinder)

    /** Crack paths: (angle, height) points on the glass, grown in three steps. */
    private val crackPaths: List<DoubleArray> = buildList {
        for (k in 0 until 14) {
            var angle = random(k, 401) * 2.0 * PI
            // Most cracks face the front (the camera's side), where they read best.
            angle = (angle - PI) * 0.7
            var y = 0.9 + 2.3 * random(k, 402)
            val path = DoubleArray(2 * 9)
            for (i in 0 until 9) {
                path[2 * i] = angle
                path[2 * i + 1] = y
                angle += (random(k * 31 + i, 403) - 0.5) * 0.35
                y += (random(k * 31 + i, 404) - 0.5) * 0.45
            }
            add(path)
        }
    }

    // ---------------------------------------------------------------- events

    override fun event(cue: Cue, frame: SceneFrame) {
        val p = frame.particles
        when (cue.id) {
            "break" -> {
                // The liquid thrown out of the tank, and droplets.
                val n = frame.count(70)
                for (i in 0 until n) {
                    val a = random(i, 501) * 2.0 * PI
                    val y = 0.7 + 2.6 * random(i, 502)
                    frame.camera.toWorld(0.72 * sin(a), y, 0.72 * cos(a), world)
                    val speed = 2.0 + 4.0 * random(i, 503)
                    val vx = frame.camera.toWorldDx(sin(a) * speed, cos(a) * speed).toFloat()
                    val vz = frame.camera.toWorldDz(sin(a) * speed, cos(a) * speed).toFloat()
                    p.emit(1, world.x, world.y, world.z, vx, (1.0 + 2.0 * random(i, 504)).toFloat(), vz, 1.6f + random(i, 505).toFloat(), 0.25f, 0.9f, argb(0.72f, 0.42f, 0.95f, 0.55f), drag = 1.4f, gravity = 6f)
                    if (i % 2 == 0) p.emit(0, world.x, world.y, world.z, vx * 1.4f, (2.0 * random(i, 506)).toFloat(), vz * 1.4f, 0.9f, 0.06f, 0.02f, argb(0.85f, 0.7f, 1f, 1f), drag = 0.6f, gravity = 9f)
                }
            }
            "surge", "pulse" -> {
                s.headPos(cue.time, local)
                frame.camera.toWorld(local.x, local.y - 0.6, local.z, world)
                p.emit(2, world.x, world.y, world.z, 0f, 0f, 0f, 0.7f, 0.6f, 2.6f, argb(0.75f, 0.45f, 1f, 0.6f))
            }
            "cry" -> {
                val n = frame.count(40)
                s.headPos(cue.time, local)
                for (i in 0 until n) {
                    val a = random(i, 601) * 2.0 * PI
                    val b = (random(i, 602) - 0.5) * PI
                    frame.camera.toWorld(local.x, local.y - 0.5, local.z, world)
                    val sp = 4.0 + 5.0 * random(i, 603)
                    p.emit(0, world.x, world.y, world.z, (cos(b) * sin(a) * sp).toFloat(), (sin(b) * sp).toFloat(), (cos(b) * cos(a) * sp).toFloat(), 0.8f, 0.08f, 0.02f, argb(0.9f, 0.7f, 1f, 1f), drag = 2f)
                }
            }
            "landing" -> {
                val n = frame.count(14)
                for (i in 0 until n) {
                    val a = random(i, 701) * 2.0 * PI
                    frame.camera.toWorld(s.landing.x + 0.4 * sin(a), s.landing.y + 0.1, s.landing.z + 0.4 * cos(a), world)
                    p.emit(3, world.x, world.y, world.z, (frame.camera.toWorldDx(sin(a), cos(a)) * 1.2).toFloat(), 0.15f, (frame.camera.toWorldDz(sin(a), cos(a)) * 1.2).toFloat(), 1.4f, 0.4f, 1.4f, argb(0.6f, 0.55f, 0.7f, 0.35f), drag = 1.5f)
                }
            }
        }
    }

    override fun update(frame: SceneFrame) {
        val a = frame.a
        val o = frame.camera.origin
        if (machine == null) {
            val level = Minecraft.getInstance().level
            machine = level != null && level.getBlockState(BlockPos.containing(o.x, o.y + 1.0, o.z)).block is RksModuleBlock
        }
        frame.particles.axisX = o.x
        frame.particles.axisZ = o.z
        // Smoke from the broken tank, motes round Mewtwo in its aura.
        if (a > MewtwoScript.BREAK && a < 20.0) {
            val rate = frame.count(4)
            if (lastSmoke == 0.0) lastSmoke = a
            if (rate > 0) while (lastSmoke + 1.0 / rate <= a) {
                lastSmoke += 1.0 / rate
                val i = (lastSmoke * 97).toInt()
                val ang = random(i, 801) * 2.0 * PI
                frame.camera.toWorld(0.6 * sin(ang), 3.3, 0.6 * cos(ang), world)
                frame.particles.emit(3, world.x, world.y, world.z, 0f, 0.5f, 0f, 3.2f, 0.4f, 1.8f, argb(0.3f, 0.28f, 0.34f, 0.35f), drag = 0.4f, swirl = 0.1f)
            }
        } else lastSmoke = maxOf(lastSmoke, a)
        val aura = s.aura(a)
        if (aura > 0.05) {
            val rate = frame.count(10)
            if (lastMote == 0.0) lastMote = a
            if (rate > 0) while (lastMote + 1.0 / rate <= a) {
                lastMote += 1.0 / rate
                val i = (lastMote * 131).toInt()
                s.mewtwoPos(lastMote, local)
                val ang = random(i, 811) * 2.0 * PI
                val r = 0.6 + 0.8 * random(i, 812)
                frame.camera.toWorld(local.x + r * sin(ang), local.y + 0.2 + 1.6 * random(i, 813), local.z + r * cos(ang), world)
                frame.particles.emit(2, world.x, world.y, world.z, 0f, 0.4f + random(i, 814).toFloat() * 0.5f, 0f, 1.4f, 0.12f, 0.02f, argb(0.75f, 0.45f, 1f, 0.8f * aura.toFloat()), drag = 0.5f, swirl = 0.6f)
            }
        } else lastMote = maxOf(lastMote, a)
        frame.particles.update(frame.da.toFloat())
    }

    override fun draw(frame: SceneFrame, batches: FxBatches) {
        val a = frame.a
        drawFog(frame, batches, a)
        drawAlarm(frame, batches, a)
        if (machine == false) drawTank(frame, batches, a)
        drawCracks(frame, batches, a)
        drawInnerGlow(frame, batches, a)
        drawArcs(frame, batches, a)
        drawShards(frame, batches, a)
        drawShock(frame, batches, a)
        drawAura(frame, batches, a)
        drawDebris(frame, batches, a)
        frame.particles.render(batches, frame.cam.x, frame.cam.y, frame.cam.z, frame.right, frame.up)
    }

    /** The violet fog round the machine, over 20 m (the ambience). */
    private fun drawFog(frame: SceneFrame, batches: FxBatches, a: Double) {
        val f = s.fog(a).toFloat()
        if (f <= 0.01f) return
        val c = frame.rel(0.0, 0.0, 0.0, v3)
        val turn = (a * 0.02).toFloat()
        val r = 6f + 16f * f
        FxMeshes.ring(batches.of(fog), c.x, c.y + 0.15f, c.z, 0f, r, 48, argb(0.22f, 0.1f, 0.32f, 0.55f * f), argb(0.22f, 0.1f, 0.32f, 0f), vScale = 5f, vOffset = turn)
        FxMeshes.ring(batches.of(fogHigh), c.x, c.y + 0.7f, c.z, 0f, r * 0.8f, 48, argb(0.3f, 0.14f, 0.4f, 0.32f * f), argb(0.3f, 0.14f, 0.4f, 0f), vScale = 6f, vOffset = -turn * 1.5f)
    }

    /** Red emergency lights turning on the machine's cap and at the console's corners. */
    private fun drawAlarm(frame: SceneFrame, batches: FxBatches, a: Double) {
        val alarm = s.alarm(a).toFloat()
        if (alarm <= 0.01f) return
        val spin = (a * 3.4)
        for ((k, p) in listOf(doubleArrayOf(0.0, 4.15, 0.0), doubleArrayOf(-1.4, 1.1, 1.35), doubleArrayOf(1.4, 1.1, 1.35)).withIndex()) {
            val c = frame.rel(p[0], p[1], p[2], v3)
            val pulse = (0.7f + 0.3f * sin((a * 9.0 + k).toFloat()))
            FxMeshes.billboard(batches.of(glows), c.x, c.y, c.z, 0.9f * pulse, 0f, argb(1f, 0.15f, 0.1f, 0.9f * alarm), frame.right, frame.up)
            for (side in 0..1) {
                val angle = spin + k * 1.3 + side * PI
                val length = if (k == 0) 14.0 else 9.0
                val end = frame.rel(p[0] + sin(angle) * length, p[1] - 0.6, p[2] + cos(angle) * length, w3)
                FxMeshes.beam(batches.of(beams), c.x, c.y, c.z, end.x, end.y, end.z, 0.25f, 2.2f, argb(1f, 0.18f, 0.12f, 0.45f * alarm), argb(1f, 0.18f, 0.12f, 0f))
            }
        }
    }

    /** With no machine (a test away from one): a glass tube and the liquid, until the breach. */
    private fun drawTank(frame: SceneFrame, batches: FxBatches, a: Double) {
        if (a >= MewtwoScript.BREAK) return
        val c = frame.rel(0.0, 0.0, 0.0, v3)
        FxMeshes.cylinder(batches.of(liquid), c.x, c.y, c.z, 0.56f, 3.3f, 0.66f, 0.66f, 24, 4, argb(0.6f, 0.35f, 0.85f, 0.55f), argb(0.6f, 0.35f, 0.85f, 0.45f), uScale = 2f, vScale = 1f)
        FxMeshes.cylinder(batches.of(tube), c.x, c.y, c.z, 0.55f, 3.45f, 0.72f, 0.72f, 32, 4, argb(0.7f, 0.9f, 1f, 0.35f), argb(0.7f, 0.9f, 1f, 0.35f))
    }

    private fun crackPoint(path: DoubleArray, i: Int, out: Vector3f): Vector3f {
        val angle = path[2 * i]
        val y = path[2 * i + 1]
        return frame.rel(0.735 * sin(angle), y, 0.735 * cos(angle), out)
    }

    private lateinit var frame: SceneFrame

    /** The cracks spreading on the glass during the alarm. */
    private fun drawCracks(frame: SceneFrame, batches: FxBatches, a: Double) {
        this.frame = frame
        val grown = s.cracks(a)
        if (grown <= 0.001) return
        val buffer = batches.of(cracks)
        val flicker = (0.75 + 0.25 * sin(a * 23.0)).toFloat()
        for ((k, path) in crackPaths.withIndex()) {
            // Each crack appears at its step, then grows.
            val step = k % 3
            val own = ((grown * 3.0 - step) / 1.2).coerceIn(0.0, 1.0)
            if (own <= 0.0) continue
            val segments = (8 * own).toInt().coerceAtLeast(1)
            for (i in 0 until segments) {
                val p0 = crackPoint(path, i, v3)
                val p1 = crackPoint(path, i + 1, w3)
                FxMeshes.beam(buffer, p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, 0.018f, 0.012f, argb(0.85f, 0.95f, 1f, 0.85f * flicker), argb(0.85f, 0.95f, 1f, 0.75f * flicker))
            }
        }
    }

    /** The clone's glow in the tank (its energy, the surge). */
    private fun drawInnerGlow(frame: SceneFrame, batches: FxBatches, a: Double) {
        val glow = s.liquidGlow(a).toFloat()
        if (glow <= 0.01f) return
        s.headPos(a, local)
        val c = frame.rel(local.x, local.y - 0.7, local.z, v3)
        FxMeshes.billboard(batches.of(glows), c.x, c.y, c.z, 1.6f + 1.2f * glow, 0f, argb(0.7f, 0.4f, 1f, 0.45f * glow), frame.right, frame.up)
    }

    /** Arcs from the reactor and the cap to the glass during the alarm; round Mewtwo at its cry. */
    private fun drawArcs(frame: SceneFrame, batches: FxBatches, a: Double) {
        val arcs = s.arcs(a).toFloat()
        if (arcs <= 0.01f) return
        val buffer = batches.of(this.arcs)
        val tick = (a * 14.0).toInt()
        val colour = argb(0.8f, 0.65f, 1f, 0.9f * arcs)
        if (a < MewtwoScript.BREAK + 0.3) {
            for (k in 0 until 3) {
                val seed = tick * 13 + k
                val from = frame.rel(0.0, 3.95, -1.0, v3)
                val angle = (random(seed, 901) - 0.5) * 2.6
                val to = frame.rel(0.72 * sin(angle), 1.0 + 2.2 * random(seed, 902), 0.72 * cos(angle), w3)
                FxMeshes.bolt(buffer, from.x, from.y, from.z, to.x, to.y, to.z, seed, 0.25f, 0.03f, colour)
            }
        } else {
            s.headPos(a, local)
            for (k in 0 until 4) {
                val seed = tick * 17 + k
                val from = frame.rel(local.x, local.y - 0.6, local.z, v3)
                val ang = random(seed, 911) * 2.0 * PI
                val r = 1.2 + 0.8 * random(seed, 912)
                val to = frame.rel(local.x + r * sin(ang), local.y - 0.6 + (random(seed, 913) - 0.5) * 1.6, local.z + r * cos(ang), w3)
                FxMeshes.bolt(buffer, from.x, from.y, from.z, to.x, to.y, to.z, seed, 0.3f, 0.025f, colour)
            }
        }
    }

    /** The glass's 3D shards, thrown out at the breach (closed form: drag and gravity), then lying. */
    private fun drawShards(frame: SceneFrame, batches: FxBatches, a: Double) {
        val d = a - MewtwoScript.BREAK
        if (d < 0.0 || d > 9.0) return
        val buffer = batches.of(shards)
        val n = if (frame.config.high) 90 else if (frame.config.post) 60 else 34
        val fadeAll = 1.0 - smooth((d - 6.5) / 2.0)
        for (i in 0 until n) {
            val angle = random(i, 201) * 2.0 * PI
            val y0 = 0.6 + 2.8 * random(i, 202)
            // Out from the glass, mostly forward and to the sides.
            val speed = 2.5 + 6.5 * random(i, 203)
            val up = 0.5 + 2.5 * random(i, 204)
            val k = 1.6
            val g = 9.8
            val e = (1.0 - exp(-k * d)) / k
            val x = 0.72 * sin(angle) + sin(angle) * speed * e
            val z = 0.72 * cos(angle) + cos(angle) * speed * e
            val y = maxOf(0.03, y0 + (up + g / k) * e - g * d / k)
            val landed = y <= 0.031
            val p = frame.rel(x, y, z, v3)
            val tumble = if (landed) 0.0 else d * (5.0 + 7.0 * random(i, 205))
            q.identity().rotateAxis(tumble.toFloat() + i.toFloat(), w3.set(random(i, 206).toFloat() - 0.5f, random(i, 207).toFloat() - 0.5f, 0.3f).normalize())
            if (landed) q.identity().rotateAxis(PI.toFloat() / 2f, 1f, 0f, 0f).rotateZ(i.toFloat())
            val size = (0.5 + 0.6 * random(i, 208)) * fadeAll
            if (size <= 0.02) continue
            FxMeshes.crystal(buffer, p.x, p.y, p.z, q, (0.05 * size).toFloat(), (0.32 * size).toFloat(), argb(1f, 1f, 1f, 1f))
        }
    }

    /** The shock wave of the breach (a torus and a ring on the ground) and the cry's sphere. */
    private fun drawShock(frame: SceneFrame, batches: FxBatches, a: Double) {
        val strength = s.shock(a).toFloat()
        if (strength > 0.01f) {
            val c = frame.rel(0.0, 2.0, 0.0, v3)
            val r = s.shockRadius(a).toFloat()
            FxMeshes.torus(batches.of(shock), c.x, c.y, c.z, r, 0.15f + 0.6f * strength, 64, 10, argb(0.8f, 0.7f, 1f, 0.8f * strength))
            val g = frame.rel(0.0, 0.05, 0.0, w3)
            FxMeshes.ring(batches.of(wave), g.x, g.y, g.z, maxOf(0f, r * 0.8f - 1.5f), r * 0.8f, 96, argb(0.8f, 0.7f, 1f, strength), argb(0.8f, 0.7f, 1f, strength), vScale = 12f)
            if (a - MewtwoScript.BREAK < 0.25) FxMeshes.billboard(batches.of(glows), c.x, c.y, c.z, 9f, 0f, argb(0.85f, 0.8f, 1f, (1f - ((a - MewtwoScript.BREAK) / 0.25).toFloat()) * 0.9f), frame.right, frame.up)
        }
        val cry = s.cryWave(a).toFloat()
        if (cry > 0.01f) {
            s.headPos(MewtwoScript.CRY, local)
            val c = frame.rel(local.x, local.y - 0.5, local.z, v3)
            val u = 1f - cry
            FxMeshes.sphere(batches.of(aura), c.x, c.y, c.z, 0.6f + 9f * u, argb(0.7f, 0.45f, 1f, 0.55f * cry))
        }
    }

    /** Mewtwo's violet aura: a shell, a spiral, a halo. */
    private fun drawAura(frame: SceneFrame, batches: FxBatches, a: Double) {
        val strength = s.aura(a).toFloat()
        if (strength <= 0.01f) return
        s.mewtwoPos(a, local)
        val scale = s.scaleAt(a).toFloat()
        val c = frame.rel(local.x, local.y + 1.0 * scale, local.z, v3)
        val pulse = 1f + 0.06f * sin((a * 5.0).toFloat())
        FxMeshes.sphere(batches.of(aura), c.x, c.y, c.z, 1.25f * pulse * scale, argb(0.62f, 0.38f, 1f, 0.5f * strength), uOffset = (a * 0.2).toFloat())
        val base = frame.rel(local.x, local.y, local.z, w3)
        FxMeshes.spiral(batches.of(aura), base.x, base.y, base.z, 0.1f, 2.2f * scale, 0.9f, 0.5f, 2.2f, (a * 0.6).toFloat(), 0.08f, argb(0.8f, 0.6f, 1f, 0.7f * strength), argb(0.8f, 0.6f, 1f, 0f))
        FxMeshes.billboard(batches.of(glows), c.x, c.y, c.z, 3.6f * scale, 0f, argb(0.65f, 0.4f, 1f, 0.4f * strength), frame.right, frame.up)
    }

    /** Debris from the broken tank levitating round Mewtwo. */
    private fun drawDebris(frame: SceneFrame, batches: FxBatches, a: Double) {
        val k = s.debris(a)
        if (k <= 0.01) return
        val buffer = batches.of(debris)
        s.mewtwoPos(a, local)
        val n = if (frame.config.post) 16 else 9
        for (i in 0 until n) {
            val r = 1.5 + 1.1 * random(i, 301)
            val speed = 0.35 + 0.4 * random(i, 302)
            val angle = random(i, 303) * 2.0 * PI + a * speed
            val y = local.y + 0.3 + 2.0 * random(i, 304) + 0.25 * sin(a * (0.8 + random(i, 305)) + i)
            // Rising from the ground as it begins.
            val rise = smooth((a - (MewtwoScript.BREAK + 0.6 + 0.08 * i)) / 0.8)
            val p = frame.rel(local.x + r * sin(angle), y * rise + 0.05 * (1 - rise), local.z + r * cos(angle), v3)
            q.identity().rotateXYZ((a * 0.7 + i).toFloat(), (a * 0.5 + i * 2).toFloat(), 0f)
            val size = (0.6 + 0.6 * random(i, 306)) * k
            FxMeshes.crystal(buffer, p.x, p.y, p.z, q, (0.09 * size).toFloat(), (0.28 * size).toFloat(), argb(1f, 1f, 1f, 1f))
        }
    }

    override fun post(frame: SceneFrame, out: PostParams) {
        val a = frame.a
        val d = a - MewtwoScript.BREAK
        if (d >= 0.0 && d < 1.2) {
            val c = frame.rel(0.0, 2.0, 0.0, v3)
            if (frame.project(c, uv)) {
                val radius = frame.screenRadius(c, s.shockRadius(a).toFloat())
                out.wave(0, uv[0], uv[1], radius, (0.9 * (1.0 - smooth(d / 1.0))).toFloat(), (0.04 + 0.05 * d).toFloat())
                if (frame.config.high) {
                    out.radialX = uv[0]
                    out.radialY = uv[1]
                    out.radial = (0.5 * (1.0 - smooth(d / 0.6))).toFloat()
                }
            }
        }
        val cry = a - MewtwoScript.CRY
        if (cry >= 0.0 && cry < 1.0) {
            s.headPos(a, local)
            val c = frame.rel(local.x, local.y - 0.4, local.z, v3)
            if (frame.project(c, uv)) out.wave(1, uv[0], uv[1], (0.8 * CameraMath.easeOutCubic(cry / 0.9)).toFloat(), (0.6 * (1.0 - smooth(cry / 0.9))).toFloat(), 0.05f)
        }
        val aura = s.aura(a).toFloat()
        if (aura > 0.01f) {
            s.headPos(a, local)
            val c = frame.rel(local.x, local.y - 0.6, local.z, v3)
            if (frame.project(c, uv)) {
                out.raysX = uv[0]
                out.raysY = uv[1]
                out.rays = 0.6f * aura
                out.raysDecay = 0.95f
            }
        }
        // Distortion from the alarm's heat and the energy (MEDIUM and HIGH).
        out.heat = (0.25 * s.alarm(a) * (1.0 - s.shock(a)) + 0.35 * aura).toFloat()
        out.heatSpeed = 0.5f
        out.heatScale = 3.5f
        out.bloom = (0.8 + 0.7 * CameraMath.decay(a, MewtwoScript.BREAK, 0.6) + 0.4 * aura).toFloat()
        out.glow = 1f
        out.aberration = (0.4 + 2.6 * CameraMath.decay(a, MewtwoScript.BREAK, 0.35) + 1.2 * CameraMath.decay(a, MewtwoScript.CRY, 0.4)).toFloat()
        out.flashR = 0.92f
        out.flashG = 0.85f
        out.flashB = 1f
    }

    override fun music(stretch: Double, offset: Double): SynthRenderer = MewtwoMusic.music(script, stretch, offset)

    override fun ambience(stretch: Double, offset: Double): SynthRenderer = MewtwoMusic.ambience(script, stretch, offset)
}
