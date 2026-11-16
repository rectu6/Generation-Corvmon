package generations.gg.generations.core.generationscore.common.client.cinematic.scenes

import generations.gg.generations.core.generationscore.common.client.cinematic.audio.MewMusic
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
import net.minecraft.network.chat.Component
import org.joml.Vector3d
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The end of Mew's game (MewScript), drawn: its soft energy, the pink and blue trails of its
 * flight, the psychic whirlwind and its motes through the trees, the illusions' halos, the sphere
 * of the fusion and the wave of its cry, the sparkles as it comes down. The illusions themselves
 * are more actors (drawn by the engine like Mew).
 */
class MewScene(landing: Vector3d, test: Boolean, override val title: Component) : CinematicScene {
    override val id = "mew"
    override val script = MewScript(landing, test)
    private val s = script
    override val subtitle: Component = Component.translatable("generations_core.cinematic.mew.subtitle")

    override val extraActors: Int = MewScript.ILLUSION_COUNT

    override fun extraActor(i: Int, a: Double, out: ActorKey): ActorKey = s.illusion(i, a, out)

    private val glows = FxMaterial("mew_glows", Layer.ADD, Kind.HALO)
    private val trails = FxMaterial("mew_trails", Layer.ADD, Kind.STREAK)
    private val swirl = FxMaterial("mew_swirl", Layer.ADD, Kind.STREAK).noise(0.4f, 0f, 2f, 0.4f)
    private val aura = FxMaterial("mew_aura", Layer.ADD, Kind.WHITE).noise(0.25f, 0.6f, 2.5f, 0.55f).shape(2.0f, 1f, 0f, 1.25f)
    private val wave = FxMaterial("mew_wave", Layer.ADD, Kind.RING).noise(0f, 0.25f, 4f, 0.5f).shape(0f, 0f, 0.3f, 1.2f)

    private val particleMaterials = arrayOf(
        FxMaterial("mew_p_mote", Layer.ADD, Kind.HALO),
        FxMaterial("mew_p_spark", Layer.ADD, Kind.SPARK),
        FxMaterial("mew_p_puff", Layer.ALPHA, Kind.SMOKE).shape(0f, 0f, 0.8f, 1f),
    )

    override fun particles(capacity: Int) = FxParticles(capacity, particleMaterials)

    private val v3 = Vector3f()
    private val w3 = Vector3f()
    private val local = Vector3d()
    private val world = Vector3d()
    private val key = ActorKey()
    private val uv = FloatArray(2)
    private val trailPoints = FloatArray(3 * 16)
    private var lastMote = 0.0
    private var lastWhirl = 0.0
    private var lastSparkle = 0.0

    private val pink = argb(1f, 0.55f, 0.85f, 1f)
    private val blue = argb(0.5f, 0.75f, 1f, 1f)

    // ---------------------------------------------------------------- events

    private fun burst(frame: SceneFrame, at: Vector3d, n: Int, speed: Double, salt: Int, colour: Int, material: Int = 1) {
        val p = frame.particles
        frame.camera.toWorld(at.x, at.y, at.z, world)
        for (i in 0 until frame.count(n)) {
            val a = random(i, salt) * 2.0 * PI
            val b = (random(i, salt + 1) - 0.5) * PI
            val sp = speed * (0.5 + random(i, salt + 2))
            p.emit(material, world.x, world.y, world.z, (cos(b) * sin(a) * sp).toFloat(), (sin(b) * sp).toFloat(), (cos(b) * cos(a) * sp).toFloat(), 0.9f, 0.09f, 0.02f, colour, drag = 2.2f)
        }
    }

    override fun event(cue: Cue, frame: SceneFrame) {
        when (cue.id) {
            "flight" -> burst(frame, s.headPos(cue.time, local), 18, 2.5, 901, pink)
            "illusions" -> {
                for (i in 0 until MewScript.ILLUSION_COUNT) {
                    s.illusion(i, cue.time + 0.3, key)
                    burst(frame, local.set(key.pos).add(0.0, 0.4, 0.0), 8, 1.6, 911 + i * 3, pink, 2)
                }
            }
            "fusion" -> burst(frame, s.headPos(cue.time, local), 40, 5.0, 921, argb(1f, 0.85f, 1f, 1f))
            "cry" -> burst(frame, s.headPos(cue.time, local), 50, 7.0, 931, argb(0.85f, 0.8f, 1f, 1f))
            "landing", "vanish" -> burst(frame, s.mewPos(cue.time, local).add(0.0, 0.3, 0.0), 16, 1.5, 941, pink, 2)
        }
    }

    override fun update(frame: SceneFrame) {
        val a = frame.a
        val p = frame.particles
        frame.camera.toWorld(s.centre.x, s.centre.y, s.centre.z, world)
        p.axisX = world.x
        p.axisZ = world.z
        // Motes round Mew while it waits, then in its trails.
        val motes = (s.energy(a) + 0.6 * s.trails(a) + 0.8 * s.sphere(a)).coerceAtMost(1.0)
        if (motes > 0.05) {
            val rate = frame.count(12)
            if (lastMote == 0.0) lastMote = a
            if (rate > 0) while (lastMote + 1.0 / rate <= a) {
                lastMote += 1.0 / rate
                val i = (lastMote * 173).toInt()
                s.headPos(lastMote, local)
                val ang = random(i, 951) * 2.0 * PI
                val r = 0.3 + 0.6 * random(i, 952)
                frame.camera.toWorld(local.x + r * sin(ang), local.y - 0.3 + 0.8 * random(i, 953), local.z + r * cos(ang), world)
                val colour = if (i % 3 == 0) blue else pink
                p.emit(0, world.x, world.y, world.z, 0f, 0.25f + random(i, 954).toFloat() * 0.3f, 0f, 1.3f, 0.12f, 0.03f, FxMeshes.withAlpha(colour, 0.8f * motes.toFloat()), drag = 0.6f)
            }
        } else lastMote = maxOf(lastMote, a)
        // The whirlwind: motes from the trees all round, swirling in.
        val whirl = s.whirl(a)
        if (whirl > 0.05) {
            val rate = frame.count(30)
            if (lastWhirl == 0.0) lastWhirl = a
            if (rate > 0) while (lastWhirl + 1.0 / rate <= a) {
                lastWhirl += 1.0 / rate
                val i = (lastWhirl * 211).toInt()
                val ang = random(i, 961) * 2.0 * PI
                val r = 9.0 + 7.0 * random(i, 962)
                frame.camera.toWorld(s.centre.x + r * sin(ang), 0.5 + 4.0 * random(i, 963), s.centre.z + r * cos(ang), world)
                frame.camera.toWorld(s.centre.x, s.centre.y, s.centre.z, local)
                val dx = (local.x - world.x) / r * 2.2
                val dz = (local.z - world.z) / r * 2.2
                val colour = if (i % 4 == 0) blue else pink
                p.emit(0, world.x, world.y, world.z, dx.toFloat(), 0.3f, dz.toFloat(), 3.5f, 0.16f, 0.05f, FxMeshes.withAlpha(colour, 0.85f * whirl.toFloat()), drag = 0.2f, swirl = 1.4f)
            }
        } else lastWhirl = maxOf(lastWhirl, a)
        // The sparkles as it comes down.
        val sparkle = s.sparkle(a)
        if (sparkle > 0.05) {
            val rate = frame.count(14)
            if (lastSparkle == 0.0) lastSparkle = a
            if (rate > 0) while (lastSparkle + 1.0 / rate <= a) {
                lastSparkle += 1.0 / rate
                val i = (lastSparkle * 157).toInt()
                s.mewPos(lastSparkle, local)
                val ang = random(i, 971) * 2.0 * PI
                frame.camera.toWorld(local.x + 0.5 * sin(ang), local.y + 0.7 * random(i, 972), local.z + 0.5 * cos(ang), world)
                p.emit(1, world.x, world.y, world.z, 0f, -0.2f, 0f, 1.1f, 0.07f, 0.02f, FxMeshes.withAlpha(pink, sparkle.toFloat()), drag = 1f)
            }
        } else lastSparkle = maxOf(lastSparkle, a)
        p.update(frame.da.toFloat())
    }

    override fun draw(frame: SceneFrame, batches: FxBatches) {
        val a = frame.a
        drawEnergy(frame, batches, a)
        drawTrails(frame, batches, a)
        drawWhirl(frame, batches, a)
        drawIllusions(frame, batches, a)
        drawSphere(frame, batches, a)
        frame.particles.render(batches, frame.cam.x, frame.cam.y, frame.cam.z, frame.right, frame.up)
    }

    private fun drawEnergy(frame: SceneFrame, batches: FxBatches, a: Double) {
        val e = (s.energy(a) + 0.5 * s.trails(a)).coerceAtMost(1.0).toFloat()
        if (e <= 0.01f) return
        val c = frame.rel(s.headPos(a, local), v3)
        val pulse = 1f + 0.1f * sin((a * 4.0).toFloat())
        FxMeshes.billboard(batches.of(glows), c.x, c.y, c.z, 1.1f * pulse, 0f, FxMeshes.withAlpha(pink, 0.45f * e), frame.right, frame.up)
    }

    private fun drawTrails(frame: SceneFrame, batches: FxBatches, a: Double) {
        val k = s.trails(a).toFloat()
        if (k <= 0.01f) return
        for ((band, colour) in listOf(0.18 to pink, -0.12 to blue)) {
            var n = 0
            for (i in 0 until 16) {
                val at = a - i * 0.045
                if (at < MewScript.FLIGHT) break
                s.mewPos(at, local)
                frame.rel(local.x, local.y + 0.3 + band, local.z, w3)
                trailPoints[n * 3] = w3.x
                trailPoints[n * 3 + 1] = w3.y
                trailPoints[n * 3 + 2] = w3.z
                n++
            }
            if (n >= 2) FxMeshes.ribbon(batches.of(trails), trailPoints, n, 0.16f, 0.01f, FxMeshes.withAlpha(colour, 0.85f * k), FxMeshes.withAlpha(colour, 0f))
        }
    }

    private fun drawWhirl(frame: SceneFrame, batches: FxBatches, a: Double) {
        val w = s.whirl(a).toFloat()
        if (w <= 0.01f) return
        val base = frame.rel(s.centre.x, 0.2, s.centre.z, v3)
        val buffer = batches.of(swirl)
        FxMeshes.spiral(buffer, base.x, base.y, base.z, 0f, (s.centre.y + 1.5).toFloat(), 6.5f, 1.2f, 2.2f, (a * 1.3).toFloat(), 0.12f, FxMeshes.withAlpha(pink, 0.55f * w), FxMeshes.withAlpha(blue, 0f))
        FxMeshes.spiral(buffer, base.x, base.y, base.z, 0.3f, (s.centre.y + 2.0).toFloat(), 4.5f, 0.8f, 1.8f, (a * 1.7 + PI).toFloat(), 0.09f, FxMeshes.withAlpha(blue, 0.5f * w), FxMeshes.withAlpha(pink, 0f))
    }

    private fun drawIllusions(frame: SceneFrame, batches: FxBatches, a: Double) {
        val k = s.illusions(a).toFloat()
        if (k <= 0.01f) return
        for (i in 0 until MewScript.ILLUSION_COUNT) {
            s.illusion(i, a, key)
            if (key.pose == ActorPose.HIDDEN) continue
            val c = frame.rel(key.pos.x, key.pos.y + MewScript.HEAD * key.scale, key.pos.z, v3)
            FxMeshes.billboard(batches.of(glows), c.x, c.y, c.z, 1.3f * key.scale.toFloat(), 0f, FxMeshes.withAlpha(if (i % 2 == 0) pink else blue, 0.5f * k), frame.right, frame.up)
        }
    }

    private fun drawSphere(frame: SceneFrame, batches: FxBatches, a: Double) {
        val k = s.sphere(a).toFloat()
        if (k > 0.01f) {
            val c = frame.rel(s.headPos(a, local), v3)
            val pulse = 1f + 0.07f * sin((a * 6.0).toFloat())
            FxMeshes.sphere(batches.of(aura), c.x, c.y, c.z, (0.75f + 0.45f * k) * pulse, argb(1f, 0.7f, 0.95f, 0.55f * k), uOffset = (a * 0.3).toFloat())
            FxMeshes.billboard(batches.of(glows), c.x, c.y, c.z, 4.2f * k, 0f, argb(1f, 0.8f, 1f, 0.5f * k), frame.right, frame.up)
        }
        val cry = s.cryWave(a).toFloat()
        if (cry > 0.01f) {
            s.headPos(MewScript.CRY, local)
            val c = frame.rel(local, v3)
            val u = 1f - cry
            FxMeshes.sphere(batches.of(aura), c.x, c.y, c.z, 0.6f + 11f * u, argb(0.85f, 0.65f, 1f, 0.45f * cry))
            val g = frame.rel(local.x, 0.05, local.z, w3)
            val r = 0.5f + 13f * u
            FxMeshes.ring(batches.of(wave), g.x, g.y, g.z, maxOf(0f, r - 1.4f), r, 96, FxMeshes.withAlpha(pink, cry), FxMeshes.withAlpha(blue, cry), vScale = 12f)
        }
    }

    override fun post(frame: SceneFrame, out: PostParams) {
        val a = frame.a
        val cry = a - MewScript.CRY
        if (cry >= 0.0 && cry < 1.2) {
            val c = frame.rel(s.headPos(a, local), v3)
            if (frame.project(c, uv)) {
                out.wave(1, uv[0], uv[1], (0.9 * CameraMath.easeOutCubic(cry / 1.1)).toFloat(), (0.55 * (1.0 - smooth(cry / 1.1))).toFloat(), 0.05f)
                if (frame.config.high) {
                    out.radialX = uv[0]
                    out.radialY = uv[1]
                    out.radial = (0.35 * (1.0 - smooth(cry / 0.6))).toFloat()
                }
            }
        }
        val sphere = s.sphere(a).toFloat()
        if (sphere > 0.01f) {
            val c = frame.rel(s.headPos(a, local), v3)
            if (frame.project(c, uv)) {
                out.raysX = uv[0]
                out.raysY = uv[1]
                out.rays = 0.55f * sphere
                out.raysDecay = 0.95f
            }
        }
        out.heat = (0.2 * s.whirl(a) + 0.2 * sphere).toFloat()
        out.heatSpeed = 0.6f
        out.heatScale = 3f
        out.bloom = (0.8 + 0.5 * sphere + 0.6 * CameraMath.decay(a, MewScript.CRY, 0.6)).toFloat()
        out.glow = 1f
        out.aberration = (0.3 + 1.2 * CameraMath.decay(a, MewScript.CRY, 0.4) + 0.6 * CameraMath.decay(a, MewScript.FUSION, 0.35)).toFloat()
        out.flashR = 1f
        out.flashG = 0.85f
        out.flashB = 1f
    }

    override fun music(stretch: Double, offset: Double): SynthRenderer = MewMusic.music(script, stretch, offset)

    override fun ambience(stretch: Double, offset: Double): SynthRenderer = MewMusic.ambience(script, stretch, offset)
}
