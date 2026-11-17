package generations.gg.generations.core.generationscore.common.client.cinematic.scenes

import generations.gg.generations.core.generationscore.common.client.cinematic.audio.HoOhMusic
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
 * The Passage of the Rainbow (HoOhScript), drawn: the golden light in the sky and its rays, the
 * rainbow forming across the sky, red, orange and gold embers coming down, Ho-Oh's golden halo
 * and seven-band trail, the feathers and sparks it sheds, the burst of light and the wave of its
 * pass. Ho-Oh itself is the scene's actor (drawn by the engine, lit by the shader pack).
 */
class HoOhScene(landing: Vector3d, override val title: Component) : CinematicScene {
    override val id = "ho_oh"
    override val script = HoOhScript(landing)
    private val s = script
    override val subtitle: Component = Component.translatable("generations_core.cinematic.ho_oh.subtitle")

    private val glows = FxMaterial("hooh_glows", Layer.ADD, Kind.HALO)
    private val bands = FxMaterial("hooh_bands", Layer.ADD, Kind.STREAK)
    private val rays = FxMaterial("hooh_rays", Layer.ADD, Kind.STREAK).noise(0.2f, 0f, 3f, 0.35f)
    private val wave = FxMaterial("hooh_wave", Layer.ADD, Kind.RING).noise(0f, 0.3f, 4f, 0.4f).shape(0f, 0f, 0.3f, 1.2f)

    private val particleMaterials = arrayOf(
        FxMaterial("hooh_p_ember", Layer.ADD, Kind.EMBER),
        FxMaterial("hooh_p_spark", Layer.ADD, Kind.SPARK),
        FxMaterial("hooh_p_mote", Layer.ADD, Kind.HALO),
    )

    override fun particles(capacity: Int) = FxParticles(capacity, particleMaterials)

    private val v3 = Vector3f()
    private val w3 = Vector3f()
    private val local = Vector3d()
    private val world = Vector3d()
    private val uv = FloatArray(2)
    private val points = FloatArray(3 * 64)
    private var lastEmber = 0.0
    private var lastShed = 0.0

    private val gold = argb(1f, 0.82f, 0.32f, 1f)
    private val flame = argb(1f, 0.45f, 0.12f, 1f)
    private val white = argb(1f, 0.96f, 0.85f, 1f)
    private val rainbow = intArrayOf(
        argb(1f, 0.22f, 0.18f, 1f), argb(1f, 0.58f, 0.12f, 1f), argb(1f, 0.95f, 0.25f, 1f),
        argb(0.30f, 0.95f, 0.35f, 1f), argb(0.25f, 0.65f, 1f, 1f), argb(0.35f, 0.35f, 1f, 1f), argb(0.70f, 0.35f, 1f, 1f),
    )

    // ---------------------------------------------------------------- events

    private fun burst(frame: SceneFrame, at: Vector3d, n: Int, speed: Double, salt: Int, colour: Int, material: Int, life: Float = 1.4f, size: Float = 0.2f) {
        val p = frame.particles
        frame.camera.toWorld(at.x, at.y, at.z, world)
        for (i in 0 until frame.count(n)) {
            val a = random(i, salt) * 2.0 * PI
            val b = (random(i, salt + 1) - 0.5) * PI
            val sp = speed * (0.5 + random(i, salt + 2))
            p.emit(material, world.x, world.y, world.z, (cos(b) * sin(a) * sp).toFloat(), (sin(b) * sp).toFloat(), (cos(b) * cos(a) * sp).toFloat(), life, size, size * 0.2f, colour, drag = 1.6f)
        }
    }

    override fun event(cue: Cue, frame: SceneFrame) {
        when (cue.id) {
            "emerge" -> {
                burst(frame, s.light, 50, 8.0, 701, gold, 1, 2.0f, 0.5f)
                burst(frame, s.light, 30, 5.0, 711, white, 2, 2.4f, 0.8f)
            }
            "zenith" -> {
                s.hoohPos(cue.time, local).add(0.0, 1.5, 0.0)
                burst(frame, local, 70, 9.0, 721, gold, 1, 1.8f, 0.3f)
                for ((i, c) in rainbow.withIndex()) burst(frame, local, 10, 6.0, 731 + i * 5, c, 2, 2.2f, 0.45f)
                burst(frame, local, 40, 4.0, 771, flame, 0, 2.6f, 0.25f)
            }
        }
    }

    override fun update(frame: SceneFrame) {
        val a = frame.a
        val p = frame.particles
        // Embers and feathers of fire coming down round the player.
        val embers = s.embers(a)
        if (embers > 0.05) {
            val rate = frame.count(26)
            if (lastEmber == 0.0) lastEmber = a
            if (rate > 0) while (lastEmber + 1.0 / rate <= a) {
                lastEmber += 1.0 / rate
                val i = (lastEmber * 193).toInt()
                val ang = random(i, 801) * 2.0 * PI
                val r = 2.0 + 14.0 * random(i, 802)
                frame.camera.toWorld(r * sin(ang), 10.0 + 10.0 * random(i, 803), 4.0 + r * cos(ang), world)
                val colour = when (i % 3) {
                    0 -> gold
                    1 -> flame
                    else -> white
                }
                p.emit(if (i % 4 == 0) 1 else 0, world.x, world.y, world.z, ((random(i, 804) - 0.5) * 0.8).toFloat(), (-1.0 - random(i, 805)).toFloat(), ((random(i, 806) - 0.5) * 0.8).toFloat(), 6f, 0.14f, 0.05f, FxMeshes.withAlpha(colour, embers.toFloat()), drag = 0.3f, swirl = 0.4f)
            }
        } else lastEmber = maxOf(lastEmber, a)
        // What Ho-Oh sheds as it flies: embers, golden sparks, rainbow glints.
        val trail = s.trail(a)
        if (trail > 0.05) {
            val rate = frame.count(40)
            if (lastShed == 0.0) lastShed = a
            if (rate > 0) while (lastShed + 1.0 / rate <= a) {
                lastShed += 1.0 / rate
                val i = (lastShed * 211).toInt()
                s.hoohPos(lastShed, local)
                val spread = 3.2 * HoOhScript.SCALE
                frame.camera.toWorld(local.x + (random(i, 811) - 0.5) * spread, local.y + 1.0 + random(i, 812) * 1.6, local.z + (random(i, 813) - 0.5) * spread, world)
                val colour = when (i % 4) {
                    0 -> gold
                    1 -> flame
                    2 -> white
                    else -> rainbow[i % rainbow.size]
                }
                p.emit(if (i % 3 == 0) 1 else 0, world.x, world.y, world.z, 0f, -0.6f, 0f, 2.4f, 0.2f, 0.05f, FxMeshes.withAlpha(colour, trail.toFloat()), drag = 0.5f)
            }
        } else lastShed = maxOf(lastShed, a)
        p.update(frame.da.toFloat())
    }

    override fun draw(frame: SceneFrame, batches: FxBatches) {
        val a = frame.a
        drawLight(frame, batches, a)
        drawRainbow(frame, batches, a)
        drawHooh(frame, batches, a)
        drawGust(frame, batches, a)
        frame.particles.render(batches, frame.cam.x, frame.cam.y, frame.cam.z, frame.right, frame.up)
    }

    private fun drawLight(frame: SceneFrame, batches: FxBatches, a: Double) {
        val g = s.glow(a).toFloat()
        if (g <= 0.01f) return
        val c = frame.rel(s.light, v3)
        val pulse = 1f + 0.08f * sin((a * 3.0).toFloat())
        FxMeshes.billboard(batches.of(glows), c.x, c.y, c.z, 34f * g * pulse, 0f, FxMeshes.withAlpha(gold, 0.75f * g), frame.right, frame.up)
        FxMeshes.billboard(batches.of(glows), c.x, c.y, c.z, 14f * g, (a * 0.2).toFloat(), FxMeshes.withAlpha(white, 0.9f * g), frame.right, frame.up)
        // Shafts of light down from it.
        for (k in 0 until 5) {
            val ang = k * 1.26 + a * 0.05
            frame.rel(s.light.x + 10.0 * sin(ang), 0.0, s.light.z * 0.6 + 10.0 * cos(ang), w3)
            FxMeshes.beam(batches.of(rays), c.x, c.y, c.z, w3.x, w3.y, w3.z, 2.5f * g, 9f * g, FxMeshes.withAlpha(gold, 0.22f * g), FxMeshes.withAlpha(gold, 0f))
        }
    }

    private fun drawRainbow(frame: SceneFrame, batches: FxBatches, a: Double) {
        val k = s.rainbow(a).toFloat()
        if (k <= 0.01f) return
        val reveal = s.reveal(a)
        val steps = 48
        val n = (steps * reveal).toInt().coerceAtLeast(1) + 1
        val shimmer = 0.85f + 0.15f * sin((a * 2.0).toFloat())
        for ((band, colour) in rainbow.withIndex()) {
            val r = s.rainbowRadius - band * 2.6
            for (i in 0 until n) {
                // From the left foot of the arc toward the right.
                val ang = PI - PI * i / steps
                frame.rel(s.rainbowCentre.x + cos(ang) * r, s.rainbowCentre.y + sin(ang) * r, s.rainbowCentre.z, w3)
                points[i * 3] = w3.x
                points[i * 3 + 1] = w3.y
                points[i * 3 + 2] = w3.z
            }
            FxMeshes.ribbon(batches.of(bands), points, n, 1.5f, 1.5f, FxMeshes.withAlpha(colour, 0.5f * k * shimmer), FxMeshes.withAlpha(colour, 0.5f * k * shimmer))
        }
    }

    private fun drawHooh(frame: SceneFrame, batches: FxBatches, a: Double) {
        val k = s.trail(a).toFloat()
        if (k <= 0.01f) return
        // The seven bands of its trail, 2.5 s of its way.
        for ((band, colour) in rainbow.withIndex()) {
            val offset = (band - 3) * 0.32 * HoOhScript.SCALE
            var n = 0
            for (i in 0 until 50) {
                val at = a - i * 0.05
                if (at < HoOhScript.EMERGE) break
                s.hoohPos(at, local)
                frame.rel(local.x, local.y + 1.2 * HoOhScript.SCALE + offset, local.z, w3)
                points[n * 3] = w3.x
                points[n * 3 + 1] = w3.y
                points[n * 3 + 2] = w3.z
                n++
            }
            if (n >= 2) FxMeshes.ribbon(batches.of(bands), points, n, (0.28 * HoOhScript.SCALE).toFloat(), 0.02f, FxMeshes.withAlpha(colour, 0.7f * k), FxMeshes.withAlpha(colour, 0f))
        }
        // Its golden halo.
        val c = frame.rel(s.hoohPos(a, local).add(0.0, 1.4 * HoOhScript.SCALE, 0.0), v3)
        val pulse = 1f + 0.1f * sin((a * 3.0).toFloat())
        val sc = HoOhScript.SCALE.toFloat()
        FxMeshes.billboard(batches.of(glows), c.x, c.y, c.z, 8f * sc * pulse, 0f, FxMeshes.withAlpha(gold, 0.35f * k), frame.right, frame.up)
        FxMeshes.billboard(batches.of(glows), c.x, c.y, c.z, 3.8f * sc, (a * 0.4).toFloat(), FxMeshes.withAlpha(white, 0.45f * k), frame.right, frame.up)
    }

    private fun drawGust(frame: SceneFrame, batches: FxBatches, a: Double) {
        val g = s.gust(a).toFloat()
        if (g <= 0.01f) return
        val u = 1f - g
        val c = frame.rel(0.0, 0.1, 2.0, v3)
        val r = 1f + 18f * u
        FxMeshes.ring(batches.of(wave), c.x, c.y, c.z, maxOf(0f, r - 2f), r, 96, FxMeshes.withAlpha(gold, 0.8f * g), FxMeshes.withAlpha(flame, 0.6f * g), vScale = 12f)
    }

    override fun post(frame: SceneFrame, out: PostParams) {
        val a = frame.a
        // Rays from the light, then from Ho-Oh as it passes.
        val g = s.glow(a).toFloat()
        val pass = CameraMath.window(a, 7.4, 8.3, 9.2, 10.4).toFloat()
        if (g > pass && g > 0.01f) {
            if (frame.project(frame.rel(s.light, v3), uv)) {
                out.raysX = uv[0]
                out.raysY = uv[1]
                out.rays = 0.55f * g
                out.raysDecay = 0.96f
            }
        } else if (pass > 0.01f) {
            val c = frame.rel(s.hoohPos(a, local).add(0.0, 1.5, 0.0), v3)
            if (frame.project(c, uv)) {
                out.raysX = uv[0]
                out.raysY = uv[1]
                out.rays = 0.5f * pass
                out.raysDecay = 0.95f
            }
        }
        val gust = s.gust(a)
        if (gust > 0.01 && frame.config.high) {
            val c = frame.rel(s.hoohPos(HoOhScript.ZENITH, local), v3)
            if (frame.project(c, uv)) {
                out.radialX = uv[0]
                out.radialY = uv[1]
                out.radial = (0.3 * gust).toFloat()
            }
        }
        out.heat = (0.15 * s.trail(a) * smooth((a - 6.0) / 2.0)).toFloat()
        out.heatSpeed = 0.5f
        out.heatScale = 3f
    }

    override fun music(stretch: Double, offset: Double): SynthRenderer = HoOhMusic.music(script, stretch, offset)

    override fun ambience(stretch: Double, offset: Double): SynthRenderer = HoOhMusic.ambience(script, stretch, offset)
}
