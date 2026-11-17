package generations.gg.generations.core.generationscore.common.client.cinematic.scenes

import generations.gg.generations.core.generationscore.common.client.cinematic.audio.EonMusic
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
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxMeshes.withAlpha
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxParticles
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.PostParams
import net.minecraft.network.chat.Component
import org.joml.Quaternionf
import org.joml.Vector3d
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Latias's scene (LatiasScript), drawn: the Enigma Stone waking (a pink crystal, red and pink
 * waves), the invisible silhouette seen by its distortion (a heat haze, faint streaks), the
 * illusions' halos and the bursts that draw their ways, the reveal's ripple, the red and white
 * trails of her flight, the pink psychic sphere and its wave, the sparkles as she comes down.
 * The illusions themselves are more actors (ghosts, drawn by the engine).
 */
class LatiasScene(landing: Vector3d, test: Boolean, override val title: Component) : CinematicScene {
    override val id = "latias"
    override val script = LatiasScript(landing, test)
    private val s = script
    override val subtitle: Component = Component.translatable("generations_core.cinematic.latias.subtitle")

    override val extraActors: Int = LatiasScript.ILLUSION_COUNT

    override fun extraActor(i: Int, a: Double, out: ActorKey): ActorKey = s.illusion(i, a, out)

    private val ghostTint = argb(1f, 0.86f, 0.94f, 1f)
    private val illusionTint = argb(1f, 0.55f, 0.78f, 1f)

    override fun actorLook(a: Double, out: ActorLook): ActorLook {
        if (a < LatiasScript.REVEAL) return out.ghost(withAlpha(ghostTint, (0.22 * s.ghost(a)).toFloat()))
        val v = (a - LatiasScript.REVEAL) / 0.8
        if (v < 1.0) return out.ghost(withAlpha(ghostTint, (0.25 + 0.75 * smooth(v)).toFloat()))
        return out.normal()
    }

    override fun extraLook(i: Int, a: Double, out: ActorLook): ActorLook =
        out.ghost(withAlpha(if (i % 2 == 0) illusionTint else argb(1f, 0.42f, 0.55f, 1f), (0.55 * s.illusions(a).coerceAtMost(1.0)).toFloat()))

    private val crystals = FxMaterial("latias_stone", Layer.SOLID, Kind.WHITE, cull = true)
    private val glows = FxMaterial("latias_glows", Layer.ADD, Kind.HALO)
    private val trails = FxMaterial("latias_trails", Layer.ADD, Kind.STREAK)
    private val streaks = FxMaterial("latias_streaks", Layer.ADD, Kind.STREAK).noise(0.6f, 0f, 2f, 0.5f)
    private val aura = FxMaterial("latias_aura", Layer.ADD, Kind.WHITE).noise(0.25f, 0.6f, 2.5f, 0.55f).shape(2.0f, 1f, 0f, 1.25f)
    private val wave = FxMaterial("latias_wave", Layer.ADD, Kind.RING).noise(0f, 0.25f, 4f, 0.5f).shape(0f, 0f, 0.3f, 1.2f)

    init {
        crystals.tint[0] = 1f; crystals.tint[1] = 0.55f; crystals.tint[2] = 0.75f; crystals.tint[3] = 0.9f
        crystals.crystal[0] = 0.035f; crystals.crystal[1] = 2.2f; crystals.crystal[2] = 1.05f
    }

    private val particleMaterials = arrayOf(
        FxMaterial("latias_p_mote", Layer.ADD, Kind.HALO),
        FxMaterial("latias_p_spark", Layer.ADD, Kind.SPARK),
    )

    override fun particles(capacity: Int) = FxParticles(capacity, particleMaterials)

    private val v3 = Vector3f()
    private val w3 = Vector3f()
    private val local = Vector3d()
    private val local2 = Vector3d()
    private val world = Vector3d()
    private val key = ActorKey()
    private val uv = FloatArray(2)
    private val q = Quaternionf()
    private val points = FloatArray(3 * 24)
    private var lastMote = 0.0
    private var lastStreak = 0.0
    private var lastSparkle = 0.0

    private val red = argb(1f, 0.25f, 0.35f, 1f)
    private val pink = argb(1f, 0.55f, 0.82f, 1f)
    private val white = argb(1f, 0.95f, 0.97f, 1f)

    // ---------------------------------------------------------------- events

    private fun burst(frame: SceneFrame, at: Vector3d, n: Int, speed: Double, salt: Int, colour: Int, material: Int = 1, life: Float = 0.9f, size: Float = 0.1f) {
        val p = frame.particles
        frame.camera.toWorld(at.x, at.y, at.z, world)
        for (i in 0 until frame.count(n)) {
            val a = random(i, salt) * 2.0 * PI
            val b = (random(i, salt + 1) - 0.5) * PI
            val sp = speed * (0.5 + random(i, salt + 2))
            p.emit(material, world.x, world.y, world.z, (cos(b) * sin(a) * sp).toFloat(), (sin(b) * sp).toFloat(), (cos(b) * cos(a) * sp).toFloat(), life, size, size * 0.2f, colour, drag = 2.2f)
        }
    }

    override fun event(cue: Cue, frame: SceneFrame) {
        val id = cue.id
        when {
            id == "ghost" -> burst(frame, s.centre(cue.time + 0.1, local), 10, 1.5, 1201, withAlpha(white, 0.6f), 0)
            id == "illusions" -> for (i in 0 until LatiasScript.ILLUSION_COUNT) {
                s.illusion(i, cue.time + 0.4, key)
                burst(frame, local.set(key.pos).add(0.0, 0.6, 0.0), 10, 1.8, 1211 + i * 3, pink)
            }
            id.startsWith("pop") -> {
                val i = id.removePrefix("pop").toIntOrNull() ?: return
                s.illusion(i, cue.time - 0.02, key)
                local.set(key.pos).add(0.0, 0.6, 0.0)
                burst(frame, local, 26, 3.5, 1231 + i * 5, pink)
                burst(frame, local, 14, 2.0, 1251 + i * 5, red, 0, 1.2f, 0.16f)
            }
            id == "reveal" -> {
                burst(frame, s.centre(cue.time + 0.05, local), 40, 4.5, 1271, white)
                burst(frame, local, 24, 2.5, 1281, pink, 0, 1.4f, 0.18f)
            }
            id == "pass" -> burst(frame, s.centre(cue.time, local), 30, 5.0, 1291, withAlpha(white, 0.9f))
            id == "sphere" -> burst(frame, s.centre(cue.time, local), 24, 1.5, 1301, pink, 0, 1.6f, 0.16f)
            id == "wave" -> {
                burst(frame, s.centre(cue.time, local), 70, 8.0, 1311, pink)
                burst(frame, local, 40, 5.0, 1321, white, 0, 1.4f, 0.14f)
            }
            id == "landing" || id == "vanish" -> burst(frame, s.latiasPos(cue.time, local).add(0.0, 0.4, 0.0), 18, 1.5, 1331, pink, 0)
        }
    }

    override fun update(frame: SceneFrame) {
        val a = frame.a
        val p = frame.particles
        // Motes round the stone, then round her.
        val motes = (s.stoneGlow(a) + 0.7 * s.sphere(a) + 0.4 * s.trails(a)).coerceAtMost(1.0)
        if (motes > 0.05) {
            val rate = frame.count(14)
            if (lastMote == 0.0) lastMote = a
            if (rate > 0) while (lastMote + 1.0 / rate <= a) {
                lastMote += 1.0 / rate
                val i = (lastMote * 173).toInt()
                val centre = if (a < LatiasScript.REVEAL) local.set(s.stone) else s.centre(lastMote, local)
                val ang = random(i, 1401) * 2.0 * PI
                val r = (if (a < LatiasScript.REVEAL) 0.25 else 0.6) + 0.6 * random(i, 1402)
                frame.camera.toWorld(centre.x + r * sin(ang), centre.y - 0.2 + 0.6 * random(i, 1403), centre.z + r * cos(ang), world)
                p.emit(0, world.x, world.y, world.z, 0f, 0.2f + random(i, 1404).toFloat() * 0.3f, 0f, 1.3f, 0.1f, 0.02f, withAlpha(if (i % 3 == 0) red else pink, 0.8f * motes.toFloat()), drag = 0.6f)
            }
        } else lastMote = maxOf(lastMote, a)
        // The distortion's faint glints along the invisible way.
        val ghost = s.ghost(a)
        if (ghost > 0.05) {
            val rate = frame.count(18)
            if (lastStreak == 0.0) lastStreak = a
            if (rate > 0) while (lastStreak + 1.0 / rate <= a) {
                lastStreak += 1.0 / rate
                val i = (lastStreak * 211).toInt()
                s.centre(lastStreak, local)
                frame.camera.toWorld(local.x + (random(i, 1411) - 0.5) * 2.0, local.y + (random(i, 1412) - 0.5) * 1.0, local.z + (random(i, 1413) - 0.5) * 2.0, world)
                p.emit(1, world.x, world.y, world.z, 0f, -0.1f, 0f, 0.8f, 0.07f, 0.01f, withAlpha(white, 0.5f * ghost.toFloat()), drag = 1f)
            }
        } else lastStreak = maxOf(lastStreak, a)
        // The sparkles as she comes down.
        val sparkle = s.sparkle(a)
        if (sparkle > 0.05) {
            val rate = frame.count(16)
            if (lastSparkle == 0.0) lastSparkle = a
            if (rate > 0) while (lastSparkle + 1.0 / rate <= a) {
                lastSparkle += 1.0 / rate
                val i = (lastSparkle * 157).toInt()
                s.latiasPos(lastSparkle, local)
                val ang = random(i, 1421) * 2.0 * PI
                frame.camera.toWorld(local.x + 0.8 * sin(ang), local.y + 1.2 * random(i, 1422), local.z + 0.8 * cos(ang), world)
                p.emit(1, world.x, world.y, world.z, 0f, -0.25f, 0f, 1.1f, 0.08f, 0.02f, withAlpha(pink, sparkle.toFloat()), drag = 1f)
            }
        } else lastSparkle = maxOf(lastSparkle, a)
        p.update(frame.da.toFloat())
    }

    override fun draw(frame: SceneFrame, batches: FxBatches) {
        val a = frame.a
        drawStone(frame, batches, a)
        drawGhost(frame, batches, a)
        drawIllusions(frame, batches, a)
        drawReveal(frame, batches, a)
        drawTrails(frame, batches, a)
        drawSphere(frame, batches, a)
        frame.particles.render(batches, frame.cam.x, frame.cam.y, frame.cam.z, frame.right, frame.up)
    }

    private fun drawStone(frame: SceneFrame, batches: FxBatches, a: Double) {
        val k = s.stoneGlow(a).toFloat()
        val w = s.waves(a).toFloat()
        if (k <= 0.01f && w <= 0.01f) return
        val c = frame.rel(s.stone.x, s.stone.y + 0.05 * sin(a * 2.0), s.stone.z, v3)
        if (k > 0.01f) {
            q.identity().rotateY((a * 1.6).toFloat())
            FxMeshes.crystal(batches.of(crystals), c.x, c.y, c.z, q, 0.11f * k, 0.3f * k, argb(1f, 1f, 1f, 1f))
            q.rotateX(PI.toFloat())
            FxMeshes.crystal(batches.of(crystals), c.x, c.y, c.z, q, 0.11f * k, 0.22f * k, argb(1f, 1f, 1f, 1f))
            val pulse = 1f + 0.12f * sin((a * 6.0).toFloat())
            FxMeshes.billboard(batches.of(glows), c.x, c.y, c.z, 0.9f * pulse * k, 0f, withAlpha(pink, 0.6f * k), frame.right, frame.up)
            FxMeshes.billboard(batches.of(glows), c.x, c.y, c.z, 2.2f * k, 0f, withAlpha(red, 0.25f * k), frame.right, frame.up)
        }
        // Red and pink waves going out from it.
        if (w > 0.01f) for (n in 0 until 3) {
            val u = ((a * 0.55 + n / 3.0) % 1.0).toFloat()
            val r = 0.3f + 9f * u
            FxMeshes.ring(batches.of(wave), c.x, c.y - 0.3f, c.z, maxOf(0f, r - 0.35f), r, 72, withAlpha(if (n % 2 == 0) pink else red, 0.55f * w * (1f - u)), withAlpha(red, 0f), vScale = 10f)
        }
    }

    private fun drawGhost(frame: SceneFrame, batches: FxBatches, a: Double) {
        val g = s.ghost(a).toFloat()
        if (g <= 0.01f) return
        // Faint streaks where the air bends behind her.
        for ((side, alpha) in listOf(0.0 to 0.22f, 0.6 to 0.12f, -0.6 to 0.12f)) {
            var n = 0
            for (i in 0 until 20) {
                val at = a - i * 0.04
                if (at < LatiasScript.GHOST) break
                s.centre(at, local)
                frame.rel(local.x + side, local.y + side * 0.3, local.z, w3)
                points[n * 3] = w3.x
                points[n * 3 + 1] = w3.y
                points[n * 3 + 2] = w3.z
                n++
            }
            if (n >= 2) FxMeshes.ribbon(batches.of(streaks), points, n, 0.3f, 0.02f, withAlpha(white, alpha * g), withAlpha(white, 0f))
        }
    }

    private fun drawIllusions(frame: SceneFrame, batches: FxBatches, a: Double) {
        val k = s.illusions(a).coerceAtMost(1.0).toFloat()
        for (i in 0 until LatiasScript.ILLUSION_COUNT) {
            if (k > 0.01f) {
                s.illusion(i, a, key)
                if (key.pose != ActorPose.HIDDEN) {
                    val c = frame.rel(key.pos.x, key.pos.y + LatiasScript.BODY * key.scale, key.pos.z, v3)
                    FxMeshes.billboard(batches.of(glows), c.x, c.y, c.z, 2.2f * key.scale.toFloat(), 0f, withAlpha(if (i % 2 == 0) pink else red, 0.4f * k), frame.right, frame.up)
                }
            }
            // A burst draws its way: its last second as a fading streak.
            val since = a - s.vanishAt(i)
            if (since in 0.0..0.9) {
                val fade = (1.0 - since / 0.9).toFloat()
                var n = 0
                for (j in 0 until 24) {
                    val at = s.vanishAt(i) - 0.02 - j * 0.05
                    s.illusion(i, at, key)
                    if (key.pose == ActorPose.HIDDEN) break
                    frame.rel(key.pos.x, key.pos.y + LatiasScript.BODY, key.pos.z, w3)
                    points[n * 3] = w3.x
                    points[n * 3 + 1] = w3.y
                    points[n * 3 + 2] = w3.z
                    n++
                }
                if (n >= 2) FxMeshes.ribbon(batches.of(trails), points, n, 0.28f, 0.02f, withAlpha(pink, 0.85f * fade), withAlpha(red, 0f))
            }
        }
    }

    private fun drawReveal(frame: SceneFrame, batches: FxBatches, a: Double) {
        val r = s.reveal(a).toFloat()
        if (r <= 0.01f) return
        s.centre(LatiasScript.REVEAL + 0.1, local)
        val c = frame.rel(local, v3)
        val u = 1f - r
        FxMeshes.sphere(batches.of(aura), c.x, c.y, c.z, 0.8f + 6f * u, argb(1f, 0.75f, 0.9f, 0.45f * r))
        FxMeshes.billboard(batches.of(glows), c.x, c.y, c.z, 5f * r, 0f, withAlpha(white, 0.6f * r), frame.right, frame.up)
    }

    private fun drawTrails(frame: SceneFrame, batches: FxBatches, a: Double) {
        val k = s.trails(a).toFloat()
        if (k <= 0.01f) return
        // Red and white, from the tips of her wings.
        for ((lateral, colour) in listOf(1.0 to red, -1.0 to white)) {
            var n = 0
            for (i in 0 until 24) {
                val at = a - i * 0.035
                if (at < LatiasScript.REVEAL + 0.2) break
                s.actor(at, key)
                s.centre(at, local)
                val yaw = Math.toRadians(key.yaw)
                val bank = Math.toRadians(key.roll)
                val sx = cos(yaw) * cos(bank)
                val sz = sin(yaw) * cos(bank)
                val sy = sin(bank)
                frame.rel(local.x + sx * lateral * 1.0, local.y + sy * lateral * 1.0, local.z + sz * lateral * 1.0, w3)
                points[n * 3] = w3.x
                points[n * 3 + 1] = w3.y
                points[n * 3 + 2] = w3.z
                n++
            }
            if (n >= 2) FxMeshes.ribbon(batches.of(trails), points, n, 0.2f, 0.01f, withAlpha(colour, 0.8f * k), withAlpha(colour, 0f))
        }
    }

    private fun drawSphere(frame: SceneFrame, batches: FxBatches, a: Double) {
        val k = s.sphere(a).toFloat()
        if (k > 0.01f) {
            val c = frame.rel(s.centre(a, local), v3)
            val pulse = 1f + 0.07f * sin((a * 6.0).toFloat())
            FxMeshes.sphere(batches.of(aura), c.x, c.y, c.z, (1.2f + 0.8f * k) * pulse, argb(1f, 0.55f, 0.8f, 0.5f * k), uOffset = (a * 0.3).toFloat())
            FxMeshes.billboard(batches.of(glows), c.x, c.y, c.z, 5f * k, 0f, argb(1f, 0.6f, 0.85f, 0.45f * k), frame.right, frame.up)
            for (n in 0 until 2) {
                val r = (2.2 + 0.5 * n).toFloat() * pulse
                FxMeshes.ring(batches.of(wave), c.x, c.y + (0.4 * sin(a * 2.0 + n * PI)).toFloat(), c.z, r - 0.15f, r, 64, withAlpha(pink, 0.6f * k), withAlpha(red, 0.2f * k), vScale = 8f, vOffset = (a * 0.5).toFloat())
            }
        }
        val wv = s.wave(a).toFloat()
        if (wv > 0.01f) {
            s.centre(LatiasScript.WAVE, local)
            val c = frame.rel(local, v3)
            val u = 1f - wv
            FxMeshes.sphere(batches.of(aura), c.x, c.y, c.z, 1.5f + 16f * u, argb(1f, 0.6f, 0.85f, 0.4f * wv))
            local2.set(local.x, 0.08, local.z)
            val g = frame.rel(local2, w3)
            val r = 1f + 18f * u
            FxMeshes.ring(batches.of(wave), g.x, g.y, g.z, maxOf(0f, r - 1.6f), r, 96, withAlpha(pink, wv), withAlpha(red, wv * 0.6f), vScale = 12f)
        }
    }

    override fun post(frame: SceneFrame, out: PostParams) {
        val a = frame.a
        // The reveal's ripple, then the sphere's wave.
        val reveal = a - LatiasScript.REVEAL
        if (reveal >= 0.0 && reveal < 1.0) {
            val c = frame.rel(s.centre(LatiasScript.REVEAL + 0.1, local), v3)
            if (frame.project(c, uv)) out.wave(0, uv[0], uv[1], (0.6 * CameraMath.easeOutCubic(reveal)).toFloat(), (0.4 * (1.0 - smooth(reveal))).toFloat(), 0.05f)
        }
        val w = a - LatiasScript.WAVE
        if (w >= 0.0 && w < 1.3) {
            val c = frame.rel(s.centre(LatiasScript.WAVE, local), v3)
            if (frame.project(c, uv)) {
                out.wave(1, uv[0], uv[1], (1.0 * CameraMath.easeOutCubic(w / 1.2)).toFloat(), (0.6 * (1.0 - smooth(w / 1.2))).toFloat(), 0.05f)
                if (frame.config.high) {
                    out.radialX = uv[0]
                    out.radialY = uv[1]
                    out.radial = (0.35 * (1.0 - smooth(w / 0.6))).toFloat()
                }
            }
        }
        // The pass at full speed: a radial blur toward her.
        val pass = CameraMath.window(a, LatiasScript.OVERHEAD - 0.5, LatiasScript.OVERHEAD - 0.1, LatiasScript.OVERHEAD + 0.2, LatiasScript.OVERHEAD + 0.7)
        if (pass > 0.01 && frame.config.high && out.radial <= 0f) {
            val c = frame.rel(s.centre(a, local), v3)
            if (frame.project(c, uv)) {
                out.radialX = uv[0]
                out.radialY = uv[1]
                out.radial = (0.28 * pass).toFloat()
            }
        }
        val sphere = s.sphere(a).toFloat()
        if (sphere > 0.01f) {
            val c = frame.rel(s.centre(a, local), v3)
            if (frame.project(c, uv)) {
                out.raysX = uv[0]
                out.raysY = uv[1]
                out.rays = 0.55f * sphere
                out.raysDecay = 0.95f
            }
        }
        // The invisible one bends the air.
        out.heat = (0.32 * s.ghost(a) + 0.12 * sphere).toFloat()
        out.heatSpeed = 0.9f
        out.heatScale = 3f
        out.bloom = (0.8 + 0.5 * sphere + 0.6 * CameraMath.decay(a, LatiasScript.WAVE, 0.6)).toFloat()
        out.glow = 1f
        out.aberration = (0.3 + 1.0 * pass + 1.2 * CameraMath.decay(a, LatiasScript.WAVE, 0.4) + 0.5 * CameraMath.decay(a, LatiasScript.REVEAL, 0.4)).toFloat()
        out.flashR = 1f
        out.flashG = 0.8f
        out.flashB = 0.9f
    }

    override fun music(stretch: Double, offset: Double): SynthRenderer = EonMusic.latiasMusic(script, stretch, offset)

    override fun ambience(stretch: Double, offset: Double): SynthRenderer = EonMusic.latiasAmbience(script, stretch, offset)
}
