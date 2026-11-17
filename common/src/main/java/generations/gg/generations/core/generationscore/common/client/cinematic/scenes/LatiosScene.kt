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
 * Latios's scene (LatiosScript), drawn: the stone's blue light and waves, the beam across the
 * sky, the far streak and its late boom, the blue trails and bent air of his passes, the energy
 * gathering, three psychic rings turning like a gyroscope, the great pulse into the sky, the shock
 * wave of light along the ground at his stop, the blue motes falling.
 */
class LatiosScene(landing: Vector3d, test: Boolean, override val title: Component) : CinematicScene {
    override val id = "latios"
    override val script = LatiosScript(landing, test)
    private val s = script
    override val subtitle: Component = Component.translatable("generations_core.cinematic.latios.subtitle")

    private val crystals = FxMaterial("latios_stone", Layer.SOLID, Kind.WHITE, cull = true)
    private val glows = FxMaterial("latios_glows", Layer.ADD, Kind.HALO)
    private val trails = FxMaterial("latios_trails", Layer.ADD, Kind.STREAK)
    private val beams = FxMaterial("latios_beams", Layer.ADD, Kind.STREAK).noise(1.2f, 0f, 2f, 0.3f)
    private val aura = FxMaterial("latios_aura", Layer.ADD, Kind.WHITE).noise(0.3f, 0.7f, 2.5f, 0.55f).shape(2.0f, 1f, 0f, 1.25f)
    private val wave = FxMaterial("latios_wave", Layer.ADD, Kind.RING).noise(0f, 0.3f, 4f, 0.5f).shape(0f, 0f, 0.3f, 1.2f)

    init {
        crystals.tint[0] = 0.45f; crystals.tint[1] = 0.7f; crystals.tint[2] = 1f; crystals.tint[3] = 0.9f
        crystals.crystal[0] = 0.035f; crystals.crystal[1] = 2.2f; crystals.crystal[2] = 1.05f
    }

    private val particleMaterials = arrayOf(
        FxMaterial("latios_p_mote", Layer.ADD, Kind.HALO),
        FxMaterial("latios_p_spark", Layer.ADD, Kind.SPARK),
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
    private val points = FloatArray(3 * 40)
    private var lastMote = 0.0
    private var lastEnergy = 0.0
    private var lastFall = 0.0

    private val blue = argb(0.3f, 0.55f, 1f, 1f)
    private val cyan = argb(0.5f, 0.85f, 1f, 1f)
    private val white = argb(0.92f, 0.96f, 1f, 1f)

    // ---------------------------------------------------------------- events

    private fun burst(frame: SceneFrame, at: Vector3d, n: Int, speed: Double, salt: Int, colour: Int, material: Int = 1, life: Float = 0.9f, size: Float = 0.1f, gravity: Float = 0f) {
        val p = frame.particles
        frame.camera.toWorld(at.x, at.y, at.z, world)
        for (i in 0 until frame.count(n)) {
            val a = random(i, salt) * 2.0 * PI
            val b = (random(i, salt + 1) - 0.5) * PI
            val sp = speed * (0.5 + random(i, salt + 2))
            p.emit(material, world.x, world.y, world.z, (cos(b) * sin(a) * sp).toFloat(), (sin(b) * sp).toFloat(), (cos(b) * cos(a) * sp).toFloat(), life, size, size * 0.2f, colour, drag = 2.0f, gravity = gravity)
        }
    }

    override fun event(cue: Cue, frame: SceneFrame) {
        when (cue.id) {
            "beam" -> burst(frame, s.beamHead(cue.time, local), 12, 6.0, 1501, cyan)
            "far" -> burst(frame, s.centre(cue.time + 0.02, local), 10, 8.0, 1511, white)
            "boom" -> {
                // The boom, late by the distance (343 m/s).
                s.centre(cue.time, local)
                frame.camera.toWorld(local.x, local.y, local.z, world)
                val delay = frame.soundDelay(world.x, world.y, world.z)
                frame.sound(delay, "minecraft:entity.firework_rocket.large_blast", world.x, world.y, world.z, 4.0f, 0.5f)
                frame.sound(delay + 0.05, "minecraft:entity.lightning_bolt.thunder", world.x, world.y, world.z, 0.6f, 1.6f)
            }
            "p1", "p2", "p3" -> {
                s.centre(cue.time, local)
                burst(frame, local, 40, 6.0, 1521 + cue.id.hashCode() % 17, cyan)
                burst(frame, local, 24, 3.5, 1541 + cue.id.hashCode() % 13, blue, 0, 1.3f, 0.16f)
            }
            "deploy" -> burst(frame, s.centre(cue.time, local), 30, 3.0, 1561, white)
            "rings" -> burst(frame, s.centre(cue.time, local), 24, 2.0, 1571, cyan, 0, 1.4f, 0.16f)
            "pulse" -> {
                s.centre(cue.time, local)
                burst(frame, local, 80, 9.0, 1581, white)
                burst(frame, local, 50, 6.0, 1591, blue, 0, 1.6f, 0.18f)
            }
            "brake" -> {
                local.set(s.stop.x, 0.3, s.stop.z)
                burst(frame, local, 60, 7.0, 1601, cyan)
                burst(frame, local, 30, 4.0, 1611, white, 0, 1.2f, 0.14f)
            }
            "landing", "vanish" -> burst(frame, s.latiosPos(cue.time, local).add(0.0, 0.4, 0.0), 18, 1.5, 1621, blue, 0)
        }
    }

    override fun update(frame: SceneFrame) {
        val a = frame.a
        val p = frame.particles
        // Motes round the stone, then along his passes.
        val motes = (s.stoneGlow(a) + 0.8 * s.passes(a)).coerceAtMost(1.0)
        if (motes > 0.05) {
            val rate = frame.count(18)
            if (lastMote == 0.0) lastMote = a
            if (rate > 0) while (lastMote + 1.0 / rate <= a) {
                lastMote += 1.0 / rate
                val i = (lastMote * 173).toInt()
                val centre = if (a < LatiosScript.P1) local.set(s.stone) else s.centre(lastMote, local)
                val r = (if (a < LatiosScript.P1) 0.25 else 0.8) + 0.6 * random(i, 1701)
                val ang = random(i, 1702) * 2.0 * PI
                frame.camera.toWorld(centre.x + r * sin(ang), centre.y + (random(i, 1703) - 0.5) * 0.8, centre.z + r * cos(ang), world)
                p.emit(0, world.x, world.y, world.z, ((random(i, 1704) - 0.5) * 1.2).toFloat(), 0.2f, ((random(i, 1705) - 0.5) * 1.2).toFloat(), 1.4f, 0.11f, 0.02f, withAlpha(if (i % 3 == 0) white else blue, 0.85f * motes.toFloat()), drag = 1.2f)
            }
        } else lastMote = maxOf(lastMote, a)
        // The energy gathering into him, from all round.
        val e = s.energy(a)
        if (e > 0.05) {
            val rate = frame.count(40)
            if (lastEnergy == 0.0) lastEnergy = a
            frame.camera.toWorld(s.hover.x, s.hover.y, s.hover.z, world)
            p.axisX = world.x
            p.axisZ = world.z
            if (rate > 0) while (lastEnergy + 1.0 / rate <= a) {
                lastEnergy += 1.0 / rate
                val i = (lastEnergy * 211).toInt()
                s.centre(lastEnergy, local2)
                val ang = random(i, 1711) * 2.0 * PI
                val r = 5.0 + 4.0 * random(i, 1712)
                frame.camera.toWorld(local2.x + r * sin(ang), local2.y + (random(i, 1713) - 0.5) * 5.0, local2.z + r * cos(ang), world)
                frame.camera.toWorld(local2.x, local2.y, local2.z, local)
                p.emit(0, world.x, world.y, world.z, ((local.x - world.x) * 0.9).toFloat(), ((local.y - world.y) * 0.9).toFloat(), ((local.z - world.z) * 0.9).toFloat(), 1.1f, 0.14f, 0.03f, withAlpha(if (i % 2 == 0) white else cyan, 0.9f * e.toFloat()), swirl = 1.6f)
            }
        } else lastEnergy = maxOf(lastEnergy, a)
        // The blue motes falling after the stop.
        val fall = s.fall(a)
        if (fall > 0.05) {
            val rate = frame.count(30)
            if (lastFall == 0.0) lastFall = a
            if (rate > 0) while (lastFall + 1.0 / rate <= a) {
                lastFall += 1.0 / rate
                val i = (lastFall * 157).toInt()
                val ang = random(i, 1721) * 2.0 * PI
                val r = 1.0 + 7.0 * random(i, 1722)
                frame.camera.toWorld(s.stop.x + r * sin(ang), 3.0 + 6.0 * random(i, 1723), s.stop.z + r * cos(ang), world)
                p.emit(0, world.x, world.y, world.z, 0f, -0.6f, 0f, 3.2f, 0.12f, 0.04f, withAlpha(if (i % 3 == 0) white else blue, 0.8f * fall.toFloat()), drag = 0.3f)
            }
        } else lastFall = maxOf(lastFall, a)
        p.update(frame.da.toFloat())
    }

    override fun draw(frame: SceneFrame, batches: FxBatches) {
        val a = frame.a
        drawStone(frame, batches, a)
        drawBeam(frame, batches, a)
        drawTrails(frame, batches, a)
        drawEnergy(frame, batches, a)
        drawPulse(frame, batches, a)
        frame.particles.render(batches, frame.cam.x, frame.cam.y, frame.cam.z, frame.right, frame.up)
    }

    private fun drawStone(frame: SceneFrame, batches: FxBatches, a: Double) {
        val k = s.stoneGlow(a).toFloat()
        if (k <= 0.01f) return
        val c = frame.rel(s.stone.x, s.stone.y + 0.05 * sin(a * 2.0), s.stone.z, v3)
        q.identity().rotateY((a * 1.8).toFloat())
        FxMeshes.crystal(batches.of(crystals), c.x, c.y, c.z, q, 0.11f * k, 0.3f * k, argb(1f, 1f, 1f, 1f))
        q.rotateX(PI.toFloat())
        FxMeshes.crystal(batches.of(crystals), c.x, c.y, c.z, q, 0.11f * k, 0.22f * k, argb(1f, 1f, 1f, 1f))
        val pulse = 1f + 0.12f * sin((a * 6.0).toFloat())
        FxMeshes.billboard(batches.of(glows), c.x, c.y, c.z, 0.9f * pulse * k, 0f, withAlpha(cyan, 0.65f * k), frame.right, frame.up)
        FxMeshes.billboard(batches.of(glows), c.x, c.y, c.z, 2.4f * k, 0f, withAlpha(blue, 0.3f * k), frame.right, frame.up)
        for (n in 0 until 3) {
            val u = ((a * 0.6 + n / 3.0) % 1.0).toFloat()
            val r = 0.3f + 8f * u
            FxMeshes.ring(batches.of(wave), c.x, c.y - 0.3f, c.z, maxOf(0f, r - 0.3f), r, 72, withAlpha(if (n % 2 == 0) cyan else blue, 0.5f * k * (1f - u)), withAlpha(blue, 0f), vScale = 10f)
        }
    }

    private fun drawBeam(frame: SceneFrame, batches: FxBatches, a: Double) {
        val k = s.beam(a).toFloat()
        if (k > 0.01f) {
            val from = frame.rel(s.beamFrom, v3)
            val head = frame.rel(s.beamHead(a, local), w3)
            FxMeshes.beam(batches.of(beams), from.x, from.y, from.z, head.x, head.y, head.z, 0.6f, 2.4f, withAlpha(blue, 0f), withAlpha(cyan, 0.75f * k))
            FxMeshes.billboard(batches.of(glows), head.x, head.y, head.z, 12f * k, 0f, withAlpha(white, 0.5f * k), frame.right, frame.up)
        }
        // The far silhouette's long streak.
        val f = s.far(a).toFloat()
        if (f > 0.01f) {
            val c = frame.rel(s.centre(a, local), v3)
            s.centre(a - 0.35, local2)
            val tail = frame.rel(local2, w3)
            FxMeshes.beam(batches.of(trails), tail.x, tail.y, tail.z, c.x, c.y, c.z, 0.2f, 2.2f, withAlpha(blue, 0f), withAlpha(cyan, 0.85f * f))
            FxMeshes.billboard(batches.of(glows), c.x, c.y, c.z, 9f, 0f, withAlpha(cyan, 0.45f * f), frame.right, frame.up)
        }
    }

    private fun drawTrails(frame: SceneFrame, batches: FxBatches, a: Double) {
        val k = s.passes(a).coerceAtMost(1.0).toFloat()
        if (k <= 0.01f) return
        for ((lateral, colour) in listOf(1.0 to blue, -1.0 to cyan, 0.0 to white)) {
            var n = 0
            for (i in 0 until 40) {
                val at = a - i * 0.025
                if (!s.shown(at) || at >= LatiosScript.HOVER) break
                s.actor(at, key)
                s.centre(at, local)
                val yaw = Math.toRadians(key.yaw)
                val bank = Math.toRadians(key.roll)
                val sx = cos(yaw) * cos(bank)
                val sz = sin(yaw) * cos(bank)
                val sy = sin(bank)
                frame.rel(local.x + sx * lateral * 1.1, local.y + sy * lateral * 1.1, local.z + sz * lateral * 1.1, w3)
                points[n * 3] = w3.x
                points[n * 3 + 1] = w3.y
                points[n * 3 + 2] = w3.z
                n++
            }
            val width = if (lateral == 0.0) 0.55f else 0.22f
            if (n >= 2) FxMeshes.ribbon(batches.of(trails), points, n, width, 0.01f, withAlpha(colour, (if (lateral == 0.0) 0.35f else 0.85f) * k), withAlpha(colour, 0f))
        }
    }

    /** A ring in a tilted plane (a strip turned to the camera along its points). */
    private fun tiltedRing(frame: SceneFrame, batches: FxBatches, centre: Vector3d, radius: Double, tiltX: Double, tiltZ: Double, spin: Double, colour: Int, width: Float) {
        val steps = 36
        for (i in 0..steps) {
            val ang = 2.0 * PI * i / steps + spin
            var x = cos(ang) * radius
            var y = 0.0
            var z = sin(ang) * radius
            // Tilt round x, then round z.
            val y1 = y * cos(tiltX) - z * sin(tiltX)
            val z1 = y * sin(tiltX) + z * cos(tiltX)
            y = y1
            z = z1
            val x2 = x * cos(tiltZ) - y * sin(tiltZ)
            val y2 = x * sin(tiltZ) + y * cos(tiltZ)
            x = x2
            y = y2
            frame.rel(centre.x + x, centre.y + y, centre.z + z, w3)
            points[i * 3] = w3.x
            points[i * 3 + 1] = w3.y
            points[i * 3 + 2] = w3.z
        }
        FxMeshes.ribbon(batches.of(trails), points, steps + 1, width, width, colour, colour)
    }

    private fun drawEnergy(frame: SceneFrame, batches: FxBatches, a: Double) {
        val e = s.energy(a).toFloat()
        val r = s.rings(a).toFloat()
        if (e <= 0.01f && r <= 0.01f) return
        s.centre(a, local2)
        val c = frame.rel(local2, v3)
        if (e > 0.01f) {
            val pulse = 1f + 0.08f * sin((a * 7.0).toFloat())
            FxMeshes.sphere(batches.of(aura), c.x, c.y, c.z, (1.1f + 0.9f * e) * pulse, argb(0.55f, 0.8f, 1f, 0.5f * e), uOffset = (a * 0.4).toFloat())
            FxMeshes.billboard(batches.of(glows), c.x, c.y, c.z, 6f * e, 0f, withAlpha(cyan, 0.45f * e), frame.right, frame.up)
        }
        if (r > 0.01f) {
            // Three rings turning like a gyroscope.
            tiltedRing(frame, batches, local2, 2.4, 0.5 + 0.6 * a, 0.0, a * 1.4, withAlpha(cyan, 0.8f * r), 0.09f)
            tiltedRing(frame, batches, local2, 2.9, -0.4, 0.7 + 0.5 * a, -a * 1.1, withAlpha(white, 0.65f * r), 0.07f)
            tiltedRing(frame, batches, local2, 3.4, 1.2 + 0.3 * a, -0.6, a * 0.8, withAlpha(blue, 0.75f * r), 0.1f)
        }
    }

    private fun drawPulse(frame: SceneFrame, batches: FxBatches, a: Double) {
        val p = s.pulseWave(a).toFloat()
        if (p > 0.01f) {
            s.centre(LatiosScript.PULSE, local)
            val c = frame.rel(local, v3)
            val u = 1f - p
            // Up into the sky, and a sphere going out (no harm).
            FxMeshes.beam(batches.of(beams), c.x, c.y, c.z, c.x, c.y + 140f, c.z, 2.6f * p + 0.4f, 0.6f, withAlpha(white, 0.85f * p), withAlpha(cyan, 0f))
            FxMeshes.sphere(batches.of(aura), c.x, c.y, c.z, 1.5f + 18f * u, argb(0.6f, 0.85f, 1f, 0.4f * p))
            FxMeshes.ring(batches.of(wave), c.x, c.y + 12f * u, c.z, maxOf(0f, 3f + 20f * u - 2f), 3f + 20f * u, 96, withAlpha(cyan, p), withAlpha(blue, 0.5f * p), vScale = 12f)
        }
        val sh = s.shock(a).toFloat()
        if (sh > 0.01f) {
            local.set(s.stop.x, 0.08, s.stop.z)
            val g = frame.rel(local, w3)
            val u = 1f - sh
            val radius = 0.8f + 16f * CameraMath.easeOutCubic(u.toDouble()).toFloat()
            FxMeshes.ring(batches.of(wave), g.x, g.y, g.z, maxOf(0f, radius - 1.8f), radius, 96, withAlpha(white, sh), withAlpha(cyan, 0.6f * sh), vScale = 12f)
            FxMeshes.ring(batches.of(wave), g.x, g.y + 0.04f, g.z, maxOf(0f, radius * 0.6f - 0.8f), radius * 0.6f, 72, withAlpha(cyan, 0.7f * sh), withAlpha(blue, 0f), vScale = 8f)
            FxMeshes.billboard(batches.of(glows), g.x, g.y + 1.2f, g.z, 7f * sh, 0f, withAlpha(white, 0.5f * sh), frame.right, frame.up)
        }
    }

    override fun post(frame: SceneFrame, out: PostParams) {
        val a = frame.a
        // The sonic boom over the player, the pulse, the stop: waves on the image.
        val boom = a - LatiosScript.P2_NEAR
        if (boom >= 0.0 && boom < 1.0) {
            val c = frame.rel(s.centre(LatiosScript.P2_NEAR, local), v3)
            if (frame.project(c, uv)) out.wave(0, uv[0], uv[1], (0.9 * CameraMath.easeOutCubic(boom)).toFloat(), (0.5 * (1.0 - smooth(boom))).toFloat(), 0.05f)
        }
        val pulse = a - LatiosScript.PULSE
        if (pulse >= 0.0 && pulse < 1.4) {
            val c = frame.rel(s.centre(LatiosScript.PULSE, local), v3)
            if (frame.project(c, uv)) {
                out.wave(1, uv[0], uv[1], (1.1 * CameraMath.easeOutCubic(pulse / 1.3)).toFloat(), (0.6 * (1.0 - smooth(pulse / 1.3))).toFloat(), 0.05f)
                if (frame.config.high) {
                    out.radialX = uv[0]
                    out.radialY = uv[1]
                    out.radial = (0.35 * (1.0 - smooth(pulse / 0.6))).toFloat()
                }
            }
        }
        val brake = a - LatiosScript.BRAKE
        if (brake >= 0.0 && brake < 1.0) {
            val c = frame.rel(local.set(s.stop.x, 0.5, s.stop.z), v3)
            if (frame.project(c, uv)) out.wave(0, uv[0], uv[1], (0.8 * CameraMath.easeOutCubic(brake)).toFloat(), (0.55 * (1.0 - smooth(brake))).toFloat(), 0.05f)
        }
        // His passes: a radial blur toward him, the air bending.
        val passes = s.passes(a).coerceAtMost(1.0)
        if (passes > 0.01 && frame.config.high && out.radial <= 0f) {
            val c = frame.rel(s.centre(a, local), v3)
            if (frame.project(c, uv)) {
                out.radialX = uv[0]
                out.radialY = uv[1]
                out.radial = (0.22 * passes).toFloat()
            }
        }
        val energy = s.energy(a).toFloat()
        if (energy > 0.01f) {
            val c = frame.rel(s.centre(a, local), v3)
            if (frame.project(c, uv)) {
                out.raysX = uv[0]
                out.raysY = uv[1]
                out.rays = 0.6f * energy
                out.raysDecay = 0.95f
            }
        }
        out.heat = (0.22 * passes + 0.1 * energy).toFloat()
        out.heatSpeed = 1.2f
        out.heatScale = 3f
        out.bloom = (0.8 + 0.5 * energy + 0.7 * CameraMath.decay(a, LatiosScript.PULSE, 0.6)).toFloat()
        out.glow = 1f
        out.aberration = (0.3 + 0.8 * passes + 1.2 * CameraMath.decay(a, LatiosScript.PULSE, 0.4) + 0.9 * CameraMath.decay(a, LatiosScript.BRAKE, 0.3)).toFloat()
        out.flashR = 0.85f
        out.flashG = 0.95f
        out.flashB = 1f
    }

    override fun music(stretch: Double, offset: Double): SynthRenderer = EonMusic.latiosMusic(script, stretch, offset)

    override fun ambience(stretch: Double, offset: Double): SynthRenderer = EonMusic.latiosAmbience(script, stretch, offset)
}
