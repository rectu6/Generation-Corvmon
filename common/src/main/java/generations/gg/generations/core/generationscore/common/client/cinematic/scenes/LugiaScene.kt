package generations.gg.generations.core.generationscore.common.client.cinematic.scenes

import generations.gg.generations.core.generationscore.common.client.cinematic.audio.LugiaMusic
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
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxMeshes.lerpColour
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxMeshes.withAlpha
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxParticles
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.PostParams
import generations.gg.generations.core.generationscore.common.world.cinematic.CinematicScenes
import net.minecraft.network.chat.Component
import org.joml.Vector3d
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Lugia's scene (LugiaScript), drawn on the watchers' clients only: the whirlpool on the water
 * (a disc of turning bands, foam arms, a wall of water; no block touched) or, without water, an
 * energy whirlpool on the ground; the waves, the dark clouds, far lightning and the rain (on
 * this client); the white shape; the column of water; the trails; the sphere, the rings and the
 * beam of Aeroblast with its shock wave (nobody hurt, nothing broken). Options of the start
 * packet: d, vy (the whirlpool), water, syaw (Lugia's yaw where it appears), test.
 */
class LugiaScene(landing: Vector3d, test: Boolean, override val title: Component, options: Map<String, String>) : CinematicScene {
    override val id = CinematicScenes.LUGIA
    override val script = LugiaScript(
        landing,
        test || options["test"] == "1",
        options["d"]?.toDoubleOrNull() ?: 12.0,
        options["vy"]?.toDoubleOrNull() ?: 0.0,
        options["water"] == "1",
        options["syaw"]?.toDoubleOrNull() ?: 0.0,
    )
    private val s = script
    override val subtitle: Component = Component.translatable("generations_core.cinematic.lugia.subtitle")

    // ---------------------------------------------------------------- materials

    private val waterMat = FxMaterial("lugia_water", Layer.ALPHA, Kind.NOISE).noise(0.02f, 0.12f, 2.5f, 0.3f).shape(0f, 0f, 0.6f, 1f)
    private val energyMat = FxMaterial("lugia_energy", Layer.ADD, Kind.NOISE).noise(0.05f, 0.4f, 3f, 0.4f)
    private val foamMat = FxMaterial("lugia_foam", Layer.ALPHA, Kind.STREAK).shape(0f, 0f, 0.4f, 1f)
    private val wallMat = FxMaterial("lugia_wall", Layer.ALPHA, Kind.NOISE).noise(0f, 0.9f, 2.2f, 0.55f).shape(1.5f, 0.4f, 0.5f, 1f)
    private val glows = FxMaterial("lugia_glows", Layer.ADD, Kind.HALO)
    private val clouds = FxMaterial("lugia_clouds", Layer.ALPHA, Kind.SMOKE).noise(0.01f, 0.02f, 1.5f, 0.3f).shape(0f, 0f, 2f, 1f)
    private val streaks = FxMaterial("lugia_streaks", Layer.ADD, Kind.STREAK)
    private val ringMat = FxMaterial("lugia_rings", Layer.ADD, Kind.RING).noise(0f, 0.3f, 4f, 0.3f)
    private val bolts = FxMaterial("lugia_bolts", Layer.ADD, Kind.WHITE)
    private val sphereMat = FxMaterial("lugia_sphere", Layer.ADD, Kind.NOISE).noise(0.3f, 0.6f, 3f, 0.25f).shape(2f, 0.7f, 0f, 1.4f)

    private val particleMaterials = arrayOf(
        FxMaterial("lugia_p_spray", Layer.ALPHA, Kind.SMOKE),
        FxMaterial("lugia_p_drop", Layer.ADD, Kind.SPARK),
        FxMaterial("lugia_p_mote", Layer.ADD, Kind.HALO),
        FxMaterial("lugia_p_mist", Layer.ALPHA, Kind.SMOKE),
    )

    override fun particles(capacity: Int) = FxParticles(capacity, particleMaterials)

    // ---------------------------------------------------------------- colours, scratch

    private val abyss = argb(0.02f, 0.06f, 0.16f, 1f)
    private val sea = argb(0.1f, 0.36f, 0.55f, 1f)
    private val foam = argb(0.9f, 0.96f, 1f, 1f)
    private val deep = argb(0.15f, 0.42f, 1f, 1f)
    private val cyan = argb(0.45f, 0.85f, 1f, 1f)
    private val white = argb(1f, 1f, 1f, 1f)
    private val storm = argb(0.13f, 0.15f, 0.21f, 1f)
    private val stormLit = argb(0.55f, 0.62f, 0.8f, 1f)

    private val v3 = Vector3f()
    private val w3 = Vector3f()
    private val local = Vector3d()
    private val other = Vector3d()
    private val world = Vector3d()
    private val pts = FloatArray(3 * 72)
    private val uv = FloatArray(2)
    private val vx = s.vortex.x
    private val vy = s.vortex.y
    private val vz = s.vortex.z

    companion object {
        const val SPRAY = 0
        const val DROP = 1
        const val MOTE = 2
        const val MIST = 3
    }

    // ---------------------------------------------------------------- helpers

    /** A local velocity in world axes (the frame only turns round y). */
    private fun emitLocal(frame: SceneFrame, material: Int, x: Double, y: Double, z: Double, lvx: Double, lvy: Double, lvz: Double, life: Float, size0: Float, size1: Float, colour: Int, drag: Float = 0f, gravity: Float = 0f, swirl: Float = 0f) {
        frame.camera.toWorld(x, y, z, world)
        val wx = frame.camera.toWorldDx(lvx, lvz)
        val wz = frame.camera.toWorldDz(lvx, lvz)
        frame.particles.emit(material, world.x, world.y, world.z, wx.toFloat(), lvy.toFloat(), wz.toFloat(), life, size0, size1, colour, drag = drag, gravity = gravity, swirl = swirl, angle = (random(frame.particles.count, 3) * 6.28).toFloat())
    }

    private fun burst(frame: SceneFrame, x: Double, y: Double, z: Double, n: Int, speed: Double, up: Double, salt: Int, colour: Int, material: Int, life: Float, size: Float, drag: Float = 1.4f, gravity: Float = 0f) {
        for (i in 0 until frame.count(n)) {
            val a = random(i, salt) * 2.0 * PI
            val b = (random(i, salt + 1) - 0.3) * PI * 0.5
            val sp = speed * (0.4 + random(i, salt + 2))
            emitLocal(frame, material, x, y, z, cos(b) * sin(a) * sp, sin(b) * sp + up, cos(b) * cos(a) * sp, life * (0.7f + 0.6f * random(i, salt + 3).toFloat()), size, size * 0.3f, colour, drag, gravity)
        }
    }

    /** n per second of action while k > 0. */
    private fun emit(frame: SceneFrame, rate: Double, k: Double, salt: Int, spawn: (Int) -> Unit) {
        if (k <= 0.02) return
        val n = frame.count((rate * k * frame.da * 1000.0).toInt()) / 1000.0
        var count = n.toInt()
        if (random((frame.a * 1000).toInt(), salt) < n - count) count++
        for (i in 0 until count) spawn((frame.a * 977).toInt() + i * 31 + salt)
    }

    private fun halo(frame: SceneFrame, batches: FxBatches, x: Double, y: Double, z: Double, size: Float, colour: Int, angle: Float = 0f) {
        val c = frame.rel(x, y, z, v3)
        FxMeshes.billboard(batches.of(glows), c.x, c.y, c.z, size, angle, colour, frame.right, frame.up)
    }

    /** A circle round the point c, perpendicular to (ax, ay, az) (unit), into pts: count of points (closed). */
    private fun circle(frame: SceneFrame, cx: Double, cy: Double, cz: Double, ax: Double, ay: Double, az: Double, radius: Double, steps: Int, spin: Double): Int {
        // Two axes perpendicular to a.
        var px = ay * 0.0 - az * 1.0
        var py = az * 0.0 - ax * 0.0
        var pz = ax * 1.0 - ay * 0.0
        var len = Math.sqrt(px * px + py * py + pz * pz)
        if (len < 1e-3) {
            px = 0.0; py = -az; pz = ay
            len = Math.sqrt(py * py + pz * pz).coerceAtLeast(1e-6)
        }
        px /= len; py /= len; pz /= len
        val qx = ay * pz - az * py
        val qy = az * px - ax * pz
        val qz = ax * py - ay * px
        val n = steps.coerceIn(3, 70)
        for (i in 0..n) {
            val t = spin + 2.0 * PI * i / n
            val c = cos(t) * radius
            val sn = sin(t) * radius
            frame.rel(cx + px * c + qx * sn, cy + py * c + qy * sn, cz + pz * c + qz * sn, w3)
            pts[i * 3] = w3.x; pts[i * 3 + 1] = w3.y; pts[i * 3 + 2] = w3.z
        }
        return n + 1
    }

    // ---------------------------------------------------------------- events

    override fun event(cue: Cue, frame: SceneFrame) {
        val a = cue.time
        when {
            cue.id == "surge" -> {
                for (i in 0 until frame.count(40)) {
                    val ang = random(i, 701) * 2.0 * PI
                    val r = 6.0 + random(i, 702) * 2.0
                    emitLocal(frame, SPRAY, vx + sin(ang) * r, vy + 0.2, vz + cos(ang) * r, sin(ang) * 2.0, 2.5 + random(i, 703) * 2.0, cos(ang) * 2.0, 1.6f, 0.8f, 2.2f, withAlpha(foam, 0.55f), drag = 1.2f, gravity = 2.5f)
                }
            }
            cue.id.startsWith("bolt") -> {
                val i = cue.id.removePrefix("bolt").toIntOrNull() ?: return
                val strike = s.strikes.getOrNull(i) ?: return
                frame.camera.toWorld(strike[1], s.base + 20.0, strike[2], world)
                frame.sound(frame.soundDelay(world.x, world.y, world.z), "minecraft:entity.lightning_bolt.thunder", world.x, world.y, world.z, 3.0f, (0.65 + 0.1 * random(i, 711)).toFloat())
            }
            cue.id == "burst" -> {
                burst(frame, vx, vy + 0.5, vz, 70, 6.0, 7.0, 721, withAlpha(foam, 0.6f), SPRAY, 2.2f, 1.6f, drag = 0.9f, gravity = 5f)
                burst(frame, vx, vy + 0.8, vz, 90, 9.0, 9.0, 731, withAlpha(foam, 0.95f), DROP, 1.8f, 0.22f, drag = 0.2f, gravity = 14f)
                burst(frame, vx, vy + 1.5, vz, 40, 4.0, 2.0, 741, withAlpha(cyan, 0.9f), MOTE, 1.6f, 0.5f)
            }
            cue.id == "cry" -> {
                s.headPos(a, local)
                burst(frame, local.x, local.y, local.z, 50, 7.0, 0.0, 751, withAlpha(white, 0.9f), MOTE, 1.2f, 0.35f)
            }
            cue.id == "fire" -> {
                burst(frame, s.sphere.x, s.sphere.y, s.sphere.z, 80, 12.0, 0.0, 761, withAlpha(white, 1f), DROP, 1.0f, 0.3f, drag = 1.8f)
                burst(frame, s.sphere.x, s.sphere.y, s.sphere.z, 30, 6.0, 0.0, 771, withAlpha(cyan, 0.8f), MOTE, 1.2f, 0.9f)
            }
            cue.id == "landed" -> {
                burst(frame, s.landing.x, s.landing.y + 0.3, s.landing.z, 40, 3.5, 0.5, 781, withAlpha(cyan, 0.85f), MOTE, 1.6f, 0.35f)
                burst(frame, s.landing.x, s.landing.y + 0.2, s.landing.z, 20, 2.5, 0.3, 791, withAlpha(foam, 0.4f), MIST, 2.0f, 1.4f)
            }
            cue.id == "vanish" -> {
                s.lugiaPos(a, local)
                burst(frame, local.x, local.y + 2.0, local.z, 50, 2.0, 0.6, 801, withAlpha(foam, 0.5f), MIST, 1.8f, 1.6f)
                burst(frame, local.x, local.y + 2.0, local.z, 60, 3.0, 1.0, 811, withAlpha(cyan, 0.9f), MOTE, 1.5f, 0.3f)
            }
        }
    }

    // ---------------------------------------------------------------- each frame

    override fun update(frame: SceneFrame) {
        val a = frame.a
        frame.camera.toWorld(vx, vy, vz, world)
        frame.particles.axisX = world.x
        frame.particles.axisZ = world.z
        val swirl = s.swirl(a)
        val size = s.swirlSize(a)
        // Bubbles and drops in the silence (rising from the centre).
        emit(frame, 18.0, s.glow(a), 1001) { i ->
            val ang = random(i, 1) * 2.0 * PI
            val r = random(i, 2) * 5.0 * size
            emitLocal(frame, MOTE, vx + sin(ang) * r, vy - 0.3, vz + cos(ang) * r, 0.0, 0.8 + random(i, 3) * 0.8, 0.0, 1.6f, 0.12f, 0.05f, withAlpha(cyan, 0.7f), swirl = 0.6f)
        }
        // Spray thrown off the wall, mist round the rim.
        emit(frame, 30.0, s.wall(a), 1011) { i ->
            val ang = random(i, 1) * 2.0 * PI
            val r = 6.0 * size
            emitLocal(frame, SPRAY, vx + sin(ang) * r, vy + random(i, 2) * 4.0 * s.wall(a), vz + cos(ang) * r, sin(ang) * 1.5, 1.0, cos(ang) * 1.5, 1.8f, 0.6f, 1.8f, withAlpha(foam, 0.35f), drag = 0.8f, gravity = 1.5f, swirl = 1.4f)
        }
        // The gusts: mist driven round the whirlpool.
        emit(frame, 14.0, s.storm(a) * (0.4 + 0.6 * swirl), 1021) { i ->
            val ang = random(i, 1) * 2.0 * PI
            val r = 8.0 + random(i, 2) * 14.0
            emitLocal(frame, MIST, vx + sin(ang) * r, s.base + 1.0 + random(i, 3) * 8.0, vz + cos(ang) * r, cos(ang) * 6.0, 0.2, -sin(ang) * 6.0, 2.4f, 2.0f, 3.5f, withAlpha(foam, 0.12f), drag = 0.2f, swirl = 0.5f)
        }
        // Light gathering into the sphere.
        emit(frame, 70.0, s.charge(a), 1031) { i ->
            val th = random(i, 1) * 2.0 * PI
            val ph = (random(i, 2) - 0.5) * PI
            val r = 6.0 + random(i, 3) * 3.0
            val ox = cos(ph) * sin(th) * r
            val oy = sin(ph) * r
            val oz = cos(ph) * cos(th) * r
            val life = 0.7
            emitLocal(frame, MOTE, s.sphere.x + ox, s.sphere.y + oy, s.sphere.z + oz, -ox / life * 0.95, -oy / life * 0.95, -oz / life * 0.95, life.toFloat(), 0.2f, 0.05f, withAlpha(if (i % 3 == 0) white else cyan, 0.9f))
        }
        // Sparks along the beam.
        val beam = s.beam(a)
        emit(frame, 60.0, beam, 1041) { i ->
            val t = random(i, 1) * s.beamLength(a)
            val ox = (random(i, 2) - 0.5) * 3.0
            val oy = (random(i, 3) - 0.5) * 3.0
            emitLocal(frame, DROP, s.sphere.x + s.beamDir.x * t + ox, s.sphere.y + s.beamDir.y * t + oy, s.sphere.z + s.beamDir.z * t, ox * 1.5, oy * 1.5, 0.0, 0.8f, 0.3f, 0.05f, withAlpha(white, 0.9f), drag = 1.0f)
        }
        // The trails' glitter, and the drops falling off Lugia as it rises.
        emit(frame, 40.0, s.trails(a).coerceAtMost(1.0), 1051) { i ->
            s.wingtip(a, if (i % 2 == 0) 1 else -1, local)
            emitLocal(frame, MOTE, local.x, local.y, local.z, 0.0, -0.3, 0.0, 1.0f, 0.25f, 0.05f, withAlpha(if (i % 3 == 0) white else cyan, 0.8f), drag = 0.5f)
        }
        emit(frame, 50.0, CameraMath.window(a, LugiaScript.BURST, LugiaScript.BURST + 0.2, LugiaScript.EMERGED, LugiaScript.TOP), 1061) { i ->
            s.lugiaPos(a, local)
            emitLocal(frame, DROP, local.x + (random(i, 1) - 0.5) * 5.0, local.y + 1.0 + random(i, 2) * 3.0, local.z + (random(i, 3) - 0.5) * 3.0, 0.0, -1.0, 0.0, 1.4f, 0.16f, 0.08f, withAlpha(foam, 0.8f), gravity = 9f)
        }
        frame.particles.update(frame.da.toFloat())
    }

    override fun draw(frame: SceneFrame, batches: FxBatches) {
        val a = frame.a
        drawWhirlpool(frame, batches, a)
        drawClouds(frame, batches, a)
        drawLightning(frame, batches, a)
        drawShape(frame, batches, a)
        drawColumn(frame, batches, a)
        drawTrails(frame, batches, a)
        drawCharge(frame, batches, a)
        drawBeam(frame, batches, a)
        frame.particles.render(batches, frame.cam.x, frame.cam.y, frame.cam.z, frame.right, frame.up)
    }

    private fun drawWhirlpool(frame: SceneFrame, batches: FxBatches, a: Double) {
        val k = s.swirl(a).toFloat()
        if (k < 0.01f) return
        val size = s.swirlSize(a).toFloat()
        val turns = s.swirlTurns(a).toFloat()
        val c = frame.rel(vx, vy + 0.06, vz, v3)
        val cx = c.x; val cy = c.y; val cz = c.z
        val r = 11f * size
        val bands = 6
        val mat = if (s.water) waterMat else energyMat
        val from = if (s.water) abyss else deep
        val to = if (s.water) sea else cyan
        val strength = if (s.water) 0.85f else 0.5f
        for (b in 0 until bands) {
            val t0 = b.toFloat() / bands
            val t1 = (b + 1f) / bands
            val inner = withAlpha(lerpColour(from, to, t0), k * strength * (1f - 0.55f * t0))
            val outer = withAlpha(lerpColour(from, to, t1), if (b == bands - 1) 0f else k * strength * (1f - 0.55f * t1))
            FxMeshes.ring(batches.of(mat), cx, cy, cz, r * t0, r * t1, 48, inner, outer, 2f + b, turns * (1.8f - 0.22f * b))
        }
        // Foam arms (on the water) or arcs of light (on the ground).
        val arm = if (s.water) foam else white
        for (i in 0 until 5) {
            FxMeshes.spiral(batches.of(if (s.water) foamMat else streaks), cx, cy + 0.05f, cz, 0f, 0f, 0.8f, r, 1.25f, -turns * 1.1f + i / 5f, 0.3f + 0.3f * k, withAlpha(arm, 0.7f * k), withAlpha(arm, 0f), 40)
        }
        // The deep blue light at the heart.
        val g = s.glow(a).toFloat()
        halo(frame, batches, vx, vy + 0.3, vz, 3f + 5f * size, withAlpha(deep, (0.35f * k + 0.45f * g) * (0.85f + 0.15f * sin(a.toFloat() * 4f))))
        if (g > 0.01f) halo(frame, batches, vx, vy - 0.5, vz, 2.5f, withAlpha(cyan, 0.6f * g))
        // The waves, outward rings.
        for (j in 0 until 4) {
            val p = (((a - 1.0) * 0.45 + j / 4.0) % 1.0 + 1.0) % 1.0
            val radius = (3.0 + 22.0 * p).toFloat()
            val alpha = (k * 0.45 * (1.0 - p) * (1.0 - p)).toFloat()
            if (alpha < 0.01f) continue
            FxMeshes.ring(batches.of(if (s.water) foamMat else streaks), cx, cy + 0.03f, cz, radius - 0.7f, radius, 64, withAlpha(arm, 0f), withAlpha(arm, alpha))
            FxMeshes.ring(batches.of(if (s.water) foamMat else streaks), cx, cy + 0.03f, cz, radius, radius + 0.7f, 64, withAlpha(arm, alpha), withAlpha(arm, 0f))
        }
        // The burst's great wave.
        val bw = CameraMath.phase(a, LugiaScript.BURST, LugiaScript.BURST + 2.5)
        if (bw > 0.0 && bw < 1.0) {
            val radius = (2.0 + 30.0 * easeOutCubic(bw)).toFloat()
            val alpha = (0.7 * (1.0 - bw)).toFloat()
            FxMeshes.ring(batches.of(if (s.water) foamMat else streaks), cx, cy + 0.04f, cz, radius - 1.5f, radius, 72, withAlpha(arm, 0f), withAlpha(arm, alpha))
            FxMeshes.ring(batches.of(if (s.water) foamMat else streaks), cx, cy + 0.04f, cz, radius, radius + 1.0f, 72, withAlpha(arm, alpha), withAlpha(arm, 0f))
        }
        // The wall of water turning round the centre, and the spout before the burst.
        val w = s.wall(a).toFloat()
        if (w > 0.01f) {
            FxMeshes.cylinder(
                batches.of(wallMat), cx, cy, cz, 0f, 5.5f * w, 5.2f * size, 6.6f * size, 40, 6,
                withAlpha(if (s.water) foam else cyan, 0.4f * w), withAlpha(if (s.water) sea else deep, 0f), 3f, turns * 1.3f, 1.5f, 0f, 0.3f,
            )
        }
        val spout = CameraMath.window(a, 9.5, 11.0, LugiaScript.BURST, LugiaScript.BURST + 0.6).toFloat()
        if (spout > 0.01f) {
            FxMeshes.cylinder(batches.of(wallMat), cx, cy, cz, 0f, 9f * spout, 2.4f, 1.2f, 24, 6, withAlpha(foam, 0.5f * spout), withAlpha(foam, 0f), 2f, turns * 2.2f, 1f, 0f, 0.6f)
        }
    }

    private fun drawClouds(frame: SceneFrame, batches: FxBatches, a: Double) {
        val k = s.storm(a).toFloat()
        if (k < 0.01f) return
        val lit = s.lightning(a).toFloat()
        val colour = lerpColour(storm, stormLit, lit * 0.7f)
        val n = if (frame.config.high) 30 else 18
        for (i in 0 until n) {
            val ang = i * 2.39996 + a * 0.02 * (if (i % 2 == 0) 1 else -1)
            val r = 18.0 + 42.0 * random(i, 901)
            val y = s.base + 30.0 + 12.0 * random(i, 902)
            val sz = (14.0 + 10.0 * random(i, 903)).toFloat()
            val c = frame.rel(vx + sin(ang) * r, y, vz + cos(ang) * r, v3)
            FxMeshes.billboard(batches.of(clouds), c.x, c.y, c.z, sz, (random(i, 904) * 6.28).toFloat() + a.toFloat() * 0.02f, withAlpha(colour, 0.55f * k), frame.right, frame.up)
        }
    }

    private fun drawLightning(frame: SceneFrame, batches: FxBatches, a: Double) {
        for ((i, strike) in s.strikes.withIndex()) {
            val age = a - strike[0]
            if (age < 0.0 || age > 0.35) continue
            val k = (1.0 - age / 0.35).toFloat()
            val top = frame.rel(strike[1], s.base + 46.0, strike[2], v3)
            val tx = top.x; val ty = top.y; val tz = top.z
            val bottom = frame.rel(strike[1] + 4.0, vy, strike[2] + 3.0, w3)
            FxMeshes.bolt(batches.of(bolts), tx, ty, tz, bottom.x, bottom.y, bottom.z, 4211 + i * 17, 0.18f, 0.55f, withAlpha(argb(0.75f, 0.85f, 1f, 1f), k))
            halo(frame, batches, strike[1], s.base + 44.0, strike[2], 26f, withAlpha(stormLit, 0.5f * k))
        }
    }

    /** The white shape in the whirlpool: its light. */
    private fun drawShape(frame: SceneFrame, batches: FxBatches, a: Double) {
        val k = s.silhouette(a).toFloat()
        if (k < 0.01f) return
        s.lugiaPos(a, local)
        halo(frame, batches, local.x, local.y + 2.0, local.z, 7f, withAlpha(cyan, 0.45f * k), a.toFloat() * 0.3f)
        halo(frame, batches, local.x, local.y + 2.0, local.z, 3f, withAlpha(white, 0.5f * k))
    }

    private fun drawColumn(frame: SceneFrame, batches: FxBatches, a: Double) {
        val k = s.column(a).toFloat()
        if (k < 0.01f) return
        val h = 16f * easeOutCubic(CameraMath.phase(a, LugiaScript.BURST, LugiaScript.BURST + 1.6)).toFloat()
        val c = frame.rel(vx, vy, vz, v3)
        FxMeshes.cylinder(batches.of(wallMat), c.x, c.y, c.z, 0f, h, 2.8f, 4.6f, 32, 8, withAlpha(foam, 0.6f * k), withAlpha(foam, 0f), 2f, s.swirlTurns(a).toFloat() * 2f, 1.5f, -a.toFloat(), 0.4f)
    }

    private fun drawTrails(frame: SceneFrame, batches: FxBatches, a: Double) {
        val k = s.trails(a).coerceAtMost(1.0).toFloat()
        if (k < 0.01f) return
        val n = 24
        for (side in intArrayOf(-1, 1)) {
            for (i in 0 until n) {
                s.wingtip(a - i * 0.05, side, local)
                frame.rel(local, w3)
                pts[i * 3] = w3.x; pts[i * 3 + 1] = w3.y; pts[i * 3 + 2] = w3.z
            }
            FxMeshes.ribbon(batches.of(streaks), pts, n, 0.5f, 0.04f, withAlpha(white, 0.85f * k), withAlpha(cyan, 0f))
        }
    }

    private fun drawCharge(frame: SceneFrame, batches: FxBatches, a: Double) {
        val k = s.charge(a).toFloat()
        val sp = s.sphere
        if (k > 0.01f) {
            val radius = (0.25 + 1.5 * smooth(CameraMath.phase(a, LugiaScript.CHARGE, LugiaScript.FIRE - 0.2))).toFloat() * k
            val c = frame.rel(sp.x, sp.y, sp.z, v3)
            FxMeshes.sphere(batches.of(sphereMat), c.x, c.y, c.z, radius, withAlpha(cyan, 0.85f * k), a.toFloat() * 0.6f)
            FxMeshes.sphere(batches.of(sphereMat), c.x, c.y, c.z, radius * 0.6f, withAlpha(white, 0.9f * k), -a.toFloat())
            halo(frame, batches, sp.x, sp.y, sp.z, radius * 4f, withAlpha(deep, 0.6f * k), a.toFloat())
            halo(frame, batches, sp.x, sp.y, sp.z, radius * 1.8f, withAlpha(white, 0.8f * k))
        }
        // Rings of energy round its body, horizontal and tilted, turning.
        val r = s.rings(a).toFloat()
        if (r > 0.01f) {
            s.lugiaPos(a, local)
            val cx = local.x; val cy = local.y + 2.2; val cz = local.z
            val c = frame.rel(cx, cy, cz, v3)
            val rr = (5.5 + 0.3 * sin(a * 3.0)).toFloat()
            FxMeshes.ring(batches.of(ringMat), c.x, c.y, c.z, rr - 0.25f, rr + 0.25f, 64, withAlpha(cyan, 0.8f * r), withAlpha(white, 0.8f * r), 4f, a.toFloat() * 0.8f)
            for (j in 0 until 2) {
                val tilt = Math.toRadians(if (j == 0) 35.0 else -35.0)
                val turn = a * (if (j == 0) 1.4 else -1.1)
                val ax = sin(tilt) * cos(turn)
                val ay = cos(tilt)
                val az = sin(tilt) * sin(turn)
                val count = circle(frame, cx, cy, cz, ax, ay, az, 4.8 + j * 0.8, 48, a * 2.0)
                FxMeshes.ribbon(batches.of(streaks), pts, count, 0.3f, 0.3f, withAlpha(white, 0.7f * r), withAlpha(cyan, 0.7f * r))
            }
        }
    }

    private fun drawBeam(frame: SceneFrame, batches: FxBatches, a: Double) {
        val k = s.beam(a).toFloat()
        val sp = s.sphere
        val dir = s.beamDir
        if (k > 0.01f) {
            val length = s.beamLength(a)
            val start = frame.rel(sp.x, sp.y, sp.z, v3)
            val ax = start.x; val ay = start.y; val az = start.z
            val end = frame.rel(sp.x + dir.x * length, sp.y + dir.y * length, sp.z + dir.z * length, w3)
            FxMeshes.beam(batches.of(streaks), ax, ay, az, end.x, end.y, end.z, 3.6f * k, 6.5f * k, withAlpha(deep, 0.45f * k), withAlpha(deep, 0f))
            FxMeshes.beam(batches.of(streaks), ax, ay, az, end.x, end.y, end.z, 1.6f * k, 2.6f * k, withAlpha(cyan, 0.9f * k), withAlpha(cyan, 0.15f * k))
            FxMeshes.beam(batches.of(bolts), ax, ay, az, end.x, end.y, end.z, 0.55f * k, 0.9f * k, withAlpha(white, k), withAlpha(white, 0.3f * k))
            // Two spirals of wind round it.
            for (h in 0 until 2) {
                val steps = 48
                // dir has no x: x is across it.
                val px = 1.0; val py = 0.0; val pz = 0.0
                val qx = dir.y * pz - dir.z * py
                val qy = dir.z * px - dir.x * pz
                val qz = dir.x * py - dir.y * px
                for (i in 0 until steps) {
                    val t = length * i / (steps - 1)
                    val ang = i * 0.55 - a * 14.0 + h * PI
                    val rad = 1.6 + 0.5 * sin(i * 0.3 + a * 5.0)
                    val c = cos(ang) * rad
                    val sn = sin(ang) * rad
                    frame.rel(sp.x + dir.x * t + px * c + qx * sn, sp.y + dir.y * t + py * c + qy * sn, sp.z + dir.z * t + pz * c + qz * sn, w3)
                    pts[i * 3] = w3.x; pts[i * 3 + 1] = w3.y; pts[i * 3 + 2] = w3.z
                }
                FxMeshes.ribbon(batches.of(streaks), pts, steps, 0.35f * k, 0.6f * k, withAlpha(white, 0.65f * k), withAlpha(cyan, 0.1f * k))
            }
            // Rings travelling along it, and those at the mouth.
            for (j in 0 until 5) {
                val t = (((a - LugiaScript.FIRE) * 60.0 + j * 24.0) % 120.0 + 120.0) % 120.0
                if (t > length) continue
                val alpha = (k * 0.7 * (1.0 - t / 120.0)).toFloat()
                val count = circle(frame, sp.x + dir.x * t, sp.y + dir.y * t, sp.z + dir.z * t, dir.x, dir.y, dir.z, 2.5 + t * 0.05, 40, a)
                FxMeshes.ribbon(batches.of(streaks), pts, count, 0.4f, 0.4f, withAlpha(white, alpha), withAlpha(cyan, alpha))
            }
            halo(frame, batches, sp.x, sp.y, sp.z, 9f * k, withAlpha(white, 0.8f * k))
            halo(frame, batches, sp.x + dir.x * length, sp.y + dir.y * length, sp.z + dir.z * length, 30f * k, withAlpha(cyan, 0.5f * k))
        }
        for (m in 0 until 2) {
            val p = CameraMath.phase(a, LugiaScript.FIRE + m * 0.3, LugiaScript.FIRE + m * 0.3 + 0.8)
            if (p <= 0.0 || p >= 1.0) continue
            val count = circle(frame, sp.x, sp.y, sp.z, dir.x, dir.y, dir.z, 1.0 + 9.0 * easeOutCubic(p), 56, 0.0)
            val alpha = (0.85 * (1.0 - p)).toFloat()
            FxMeshes.ribbon(batches.of(streaks), pts, count, 0.9f, 0.9f, withAlpha(white, alpha), withAlpha(cyan, alpha))
        }
        // The shock wave crossing the sky.
        val sw = s.shockwave(a)
        if (sw > 0.0 && sw < 1.0) {
            val t = 80.0 * easeOutCubic(sw)
            val count = circle(frame, sp.x + dir.x * t, sp.y + dir.y * t, sp.z + dir.z * t, dir.x, dir.y, dir.z, 4.0 + 30.0 * sw, 64, 0.0)
            val alpha = (0.55 * (1.0 - sw)).toFloat()
            FxMeshes.ribbon(batches.of(streaks), pts, count, 2.2f, 2.2f, withAlpha(white, alpha), withAlpha(cyan, alpha))
        }
    }

    // ---------------------------------------------------------------- the image

    override fun post(frame: SceneFrame, out: PostParams) {
        val a = frame.a
        // The burst's wave on the water.
        val b = a - LugiaScript.BURST
        if (b >= 0.0 && b < 1.2) {
            val c = frame.rel(vx, vy + 1.0, vz, v3)
            if (frame.project(c, uv)) out.wave(0, uv[0], uv[1], frame.screenRadius(c, (18.0 * easeOutCubic(b / 1.2)).toFloat()), (0.8 * (1.0 - smooth(b / 1.2))).toFloat(), 0.05f)
        }
        // The cry's ring round its head.
        val cry = a - LugiaScript.CRY
        if (cry >= 0.0 && cry < 0.8) {
            s.headPos(a, local)
            val c = frame.rel(local, v3)
            if (frame.project(c, uv)) out.wave(1, uv[0], uv[1], (0.65 * easeOutCubic(cry / 0.8)).toFloat(), (0.45 * (1.0 - smooth(cry / 0.8))).toFloat(), 0.05f)
        }
        // Aeroblast: the wave from the sphere, the sky bending round the beam.
        val f = a - LugiaScript.FIRE
        if (f >= 0.0 && f < 1.4) {
            val c = frame.rel(s.sphere, v3)
            if (frame.project(c, uv)) out.wave(0, uv[0], uv[1], frame.screenRadius(c, (40.0 * easeOutCubic(f / 1.4)).toFloat()), (0.9 * (1.0 - smooth(f / 1.4))).toFloat(), (0.04 + 0.05 * f).toFloat())
        }
        val beam = s.beam(a)
        val charge = s.charge(a)
        out.heat = (0.35 * beam + 0.15 * charge + 0.08 * s.wall(a)).toFloat()
        out.heatSpeed = 0.7f
        out.heatScale = 3f
        // Radial blur toward the sphere as it gathers, and at the shot.
        val radial = 0.25 * charge * charge + 0.6 * CameraMath.pulse(a, LugiaScript.FIRE, 0.05, 0.4) + 0.3 * CameraMath.decay(a, LugiaScript.BURST, 0.35)
        if (radial > 0.004) {
            val target = if (a < LugiaScript.CHARGE) frame.rel(vx, vy + 1.0, vz, v3) else frame.rel(s.sphere, v3)
            if (frame.project(target, uv)) {
                out.radialX = uv[0]
                out.radialY = uv[1]
                out.radial = radial.toFloat()
            }
        }
        // Light rays from the sphere, then from the beam's mouth.
        val rays = (0.6 * charge + 0.7 * beam).toFloat()
        if (rays > 0.004f && frame.project(frame.rel(s.sphere, v3), uv)) {
            out.raysX = uv[0]
            out.raysY = uv[1]
            out.rays = rays
            out.raysDecay = 0.96f
        }
        out.bloom = (0.9 + 0.5 * beam + 0.3 * charge + 0.3 * s.lightning(a)).toFloat()
        out.glow = 1f
        out.aberration = (0.4 + 2.2 * CameraMath.decay(a, LugiaScript.FIRE, 0.35) + 0.8 * CameraMath.decay(a, LugiaScript.BURST, 0.3)).toFloat()
        out.flashR = 0.88f
        out.flashG = 0.95f
        out.flashB = 1f
    }

    override fun actorLook(a: Double, out: ActorLook): ActorLook {
        if (a < LugiaScript.BURST) return out.ghost(withAlpha(white, (0.8 * s.silhouette(a)).toFloat()))
        val v = s.vanish(a)
        if (v > 0.0) return out.ghost(withAlpha(white, (1.0 - v).toFloat()))
        return out.normal()
    }

    override fun rain(a: Double): Float = (0.7 * s.storm(a)).toFloat()

    override fun thunder(a: Double): Float = (0.9 * s.storm(a)).toFloat()

    override fun duck(a: Double): Float = s.duck(a).toFloat()

    override fun music(stretch: Double, offset: Double): SynthRenderer = LugiaMusic.music(script, stretch, offset)

    override fun ambience(stretch: Double, offset: Double): SynthRenderer = LugiaMusic.ambience(script, stretch, offset)
}
