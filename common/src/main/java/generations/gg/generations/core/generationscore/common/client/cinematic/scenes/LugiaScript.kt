package generations.gg.generations.core.generationscore.common.client.cinematic.scenes

import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CamPose
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraDirector
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.decay
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.easeInOutCubic
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.easeInOutSine
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.easeOutCubic
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.lerp
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.phase
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.pulse
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.smooth
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.smoother
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.window
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.Shot
import org.joml.Vector3d
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * « Le Réveil du Gardien des Mers » (LUGIA-PLAN.md § 5), pure (tested off-game by
 * tools/LugiaBench). 31 s in five sequences: the silence of the depths (0–5), the anger of the
 * oceans (5–12, the white shape rising), the appearance (12–20: out of the whirlpool, wings
 * opened, a spin, the cry at 17.2, a wide turn), Aeroblast (20–26: the sphere, the rings, the
 * beam at 23.3, away from the players), the meeting (26–31: down to the shrine, where the real
 * Lugia appears).
 *
 * The frame: the shrine's ground at the origin, the whirlpool at (0, vy, -d), so +z is the
 * shrine's side away from it (where the invoker stands). `landing`: where Lugia appears (the
 * clicked block's centre); `spawnYaw`: its yaw there, in this frame. `test`: the shrine's song
 * is the scene's own, and Lugia vanishes at the end (nothing appears).
 */
class LugiaScript(
    landing: Vector3d,
    val test: Boolean,
    distance: Double,
    height: Double,
    val water: Boolean,
    val spawnYaw: Double,
) : SceneScript {
    val landing = Vector3d(landing)
    val d = distance.coerceIn(6.0, 24.0)
    val vy = height.coerceIn(-12.0, 3.0)
    val vortex = Vector3d(0.0, vy, -d)

    /** The cameras' floor: the shrine's ground or the whirlpool's, the higher. */
    val base = max(0.0, vy)
    val topY = base + 17.0

    /**
     * Where Lugia hovers for Aeroblast, facing -z (away from the shrine's side): over the
     * whirlpool's near side, never closer than 6.5 m to the shrine (room for its turn back).
     */
    val hover = Vector3d(0.0, base + 14.5, minOf(-d + 4.0, -6.5))

    /** The Aeroblast sphere, in front of Lugia's head, and the beam's way (up 15°, toward -z). */
    val sphere = Vector3d(hover.x, hover.y + 3.4, hover.z - 3.2)
    val beamDir = Vector3d(0.0, sin(Math.toRadians(15.0)), -cos(Math.toRadians(15.0)))

    private val slow = SlowMotion(12.5, 0.45, 0.5, 14.8, 1.0)

    override fun action(t: Double): Double = slow.action(t)

    // ---------------------------------------------------------------- Lugia

    private val p0 = Vector3d(vortex.x, topY, vortex.z)
    /** Where the glide starts (the top's bob at GLIDE) and the descent (the hover's bob at DESCENT). */
    private val g0 = Vector3d(p0.x, p0.y + 0.25 * sin((GLIDE - TOP) * 3.0), p0.z)
    private val h0 = Vector3d(hover.x, hover.y + 0.25 * sin((DESCENT - HOVER) * 1.6), hover.z)
    private val p1 = Vector3d(p0.x + 9.0, p0.y - 0.5, p0.z - 4.0)
    private val p2 = Vector3d(hover.x, hover.y + 0.5, hover.z + 6.0)
    private val q1 = Vector3d(hover.x + 7.0, hover.y + 1.5, hover.z - 2.0)
    // Wide enough that the turn back toward the shrine never folds on itself (no sudden spin).
    private val q2 = Vector3d(this.landing.x + 5.0, this.landing.y + 6.0, this.landing.z - 6.0)
    private val q3 = Vector3d(this.landing.x, this.landing.y + 2.4, this.landing.z)
    private val tmp = Vector3d()
    private val tan = Vector3d()

    private fun bezier(a: Vector3d, b: Vector3d, c: Vector3d, e: Vector3d, u: Double, out: Vector3d): Vector3d {
        val v = 1.0 - u
        val k0 = v * v * v
        val k1 = 3.0 * v * v * u
        val k2 = 3.0 * v * u * u
        val k3 = u * u * u
        return out.set(a.x * k0 + b.x * k1 + c.x * k2 + e.x * k3, a.y * k0 + b.y * k1 + c.y * k2 + e.y * k3, a.z * k0 + b.z * k1 + c.z * k2 + e.z * k3)
    }

    private fun bezierTangent(a: Vector3d, b: Vector3d, c: Vector3d, e: Vector3d, u: Double, out: Vector3d): Vector3d {
        val v = 1.0 - u
        val k0 = 3.0 * v * v
        val k1 = 6.0 * v * u
        val k2 = 3.0 * u * u
        return out.set(
            (b.x - a.x) * k0 + (c.x - b.x) * k1 + (e.x - c.x) * k2,
            (b.y - a.y) * k0 + (c.y - b.y) * k1 + (e.y - c.y) * k2,
            (b.z - a.z) * k0 + (c.z - b.z) * k1 + (e.z - c.z) * k2,
        )
    }

    /** Lugia's feet at action time a. */
    fun lugiaPos(a: Double, out: Vector3d): Vector3d = when {
        a < BURST -> {
            val u = easeInOutSine(phase(a, SHAPE, BURST))
            out.set(vortex.x, vortex.y + lerp(if (water) -4.5 else -2.5, -0.6, u), vortex.z)
        }
        a < EMERGED -> {
            val u = easeOutCubic(phase(a, BURST, EMERGED))
            out.set(vortex.x, vortex.y + lerp(-0.6, 9.0, u), vortex.z)
        }
        a < TOP -> {
            val u = easeInOutSine(phase(a, EMERGED, TOP))
            val r = 1.2 * sin(PI * u)
            out.set(vortex.x + r * sin(u * 2.0 * PI), lerp(vortex.y + 9.0, topY, u), vortex.z + r * (1.0 - cos(u * 2.0 * PI)) * 0.5)
        }
        a < GLIDE -> out.set(p0.x, p0.y + 0.25 * sin((a - TOP) * 3.0), p0.z)
        a < HOVER -> {
            val u = easeInOutSine(phase(a, GLIDE, HOVER))
            bezier(g0, p1, p2, hover, u, out)
        }
        a < DESCENT -> {
            out.set(hover).add(0.0, 0.25 * sin((a - HOVER) * 1.6), 0.0)
            // The recoil of Aeroblast, back toward +z.
            out.z += 0.9 * smooth(phase(a, FIRE, FIRE + 0.25)) * (1.0 - smooth(phase(a, FIRE + 0.6, DESCENT)))
            out
        }
        a < ARRIVED -> {
            val u = easeInOutSine(phase(a, DESCENT, ARRIVED))
            bezier(h0, q1, q2, q3, u, out)
        }
        a < LANDED -> {
            val u = smoother(phase(a, ARRIVED, LANDED))
            out.set(landing.x, lerp(q3.y, landing.y, u), landing.z)
        }
        else -> out.set(landing)
    }

    private fun yawOf(dx: Double, dz: Double): Double = Math.toDegrees(atan2(-dx, dz))

    private fun wrap(deg: Double): Double {
        var x = (deg + 180.0) % 360.0
        if (x < 0) x += 360.0
        return x - 180.0
    }

    private fun lerpAngle(a: Double, b: Double, t: Double): Double = a + wrap(b - a) * t

    private fun glideYaw(u: Double): Double {
        bezierTangent(g0, p1, p2, hover, u.coerceIn(0.0, 1.0), tan)
        return yawOf(tan.x, tan.z)
    }

    private fun descentYaw(u: Double): Double {
        bezierTangent(h0, q1, q2, q3, u.coerceIn(0.0, 1.0), tan)
        return yawOf(tan.x, tan.z)
    }

    fun lugiaYaw(a: Double): Double = when {
        a < EMERGED -> 0.0 + 12.0 * sin(a * 0.7) * (1.0 - smooth(phase(a, BURST, EMERGED)))
        a < GLIDE -> 360.0 * easeInOutCubic(phase(a, EMERGED + 0.2, TOP - 0.2))
        a < HOVER -> {
            val u = easeInOutSine(phase(a, GLIDE, HOVER))
            // From facing the shrine (the cry) into the turn, then the curve's own way.
            lerpAngle(0.0, glideYaw(u), smooth(phase(a, GLIDE, GLIDE + 0.7)))
        }
        a < DESCENT -> 180.0
        a < ARRIVED -> {
            val u = easeInOutSine(phase(a, DESCENT, ARRIVED))
            lerpAngle(180.0, descentYaw(u), smooth(phase(a, DESCENT, DESCENT + 0.9)))
        }
        else -> lerpAngle(descentYaw(1.0), spawnYaw, smooth(phase(a, ARRIVED, LANDED + 0.3)))
    }

    override fun actor(a: Double, out: ActorKey): ActorKey {
        out.scale = 1.0
        out.pose = when {
            a < SHAPE -> ActorPose.HIDDEN
            a < BURST -> ActorPose.HOVER
            a < TOP - 0.2 -> ActorPose.FLY
            a < GLIDE -> ActorPose.HOVER
            a < HOVER - 0.4 -> ActorPose.FLY
            a < DESCENT -> ActorPose.HOVER
            a < ARRIVED -> ActorPose.FLY
            a < LANDED -> ActorPose.HOVER
            test && a >= END - 0.15 -> ActorPose.HIDDEN
            else -> ActorPose.STAND
        }
        lugiaPos(a, out.pos)
        out.yaw = lugiaYaw(a)
        out.pitch = when {
            a < EMERGED -> lerp(-30.0, -10.0, phase(a, BURST, EMERGED)) * smooth(phase(a, BURST - 0.3, BURST + 0.3))
            a < TOP -> lerp(-10.0, -18.0, sin(PI * phase(a, EMERGED, TOP))) * (1.0 - smooth(phase(a, TOP - 0.6, TOP)))
            a < GLIDE -> 0.0
            a < HOVER -> 12.0 * sin(PI * phase(a, GLIDE, HOVER))
            a < DESCENT -> 0.0
            a < ARRIVED -> 16.0 * sin(PI * phase(a, DESCENT, ARRIVED))
            else -> 0.0
        }
        out.roll = actorRoll(a)
        return out
    }

    /** Its head, near enough (the cry's wave, the light). */
    fun headPos(a: Double, out: Vector3d): Vector3d {
        lugiaPos(a, out)
        val yaw = Math.toRadians(lugiaYaw(a))
        return out.add(-sin(yaw) * 2.2, 3.6, cos(yaw) * 2.2)
    }

    /** A wingtip (side ±1) at action time a: for the trails. */
    fun wingtip(a: Double, side: Int, out: Vector3d): Vector3d {
        lugiaPos(a, out)
        val yaw = Math.toRadians(lugiaYaw(a))
        val roll = Math.toRadians(actorRoll(a))
        val span = 4.2 * side
        return out.add(cos(yaw) * span * cos(roll), 2.6 + span * sin(roll) * 0.6, sin(yaw) * span * cos(roll)).add(sin(yaw) * 0.4, 0.0, -cos(yaw) * 0.4)
    }

    private fun actorRoll(a: Double): Double = when {
        a in GLIDE..HOVER -> 30.0 * sin(PI * phase(a, GLIDE, HOVER))
        a in DESCENT..ARRIVED -> 22.0 * sin(PI * phase(a, DESCENT, ARRIVED))
        else -> 0.0
    }

    // ---------------------------------------------------------------- the world's look

    /** The whirlpool, from 0 to 1. */
    fun swirl(a: Double): Double = window(a, 0.8, 6.0, 26.0, 29.5)

    /** Its size, from 0.4 to 1. */
    fun swirlSize(a: Double): Double = 0.4 + 0.6 * smooth(phase(a, 1.0, 9.0))

    /** Its rotation, in turns (faster and faster, slower as it dies out). */
    fun swirlTurns(a: Double): Double {
        val x = a.coerceAtMost(26.0)
        return 0.05 * x + 0.011 * x * x + (a - x) * 0.4
    }

    /** The deep blue light under the surface. */
    fun glow(a: Double): Double = window(a, 0.3, 3.5, 12.2, 14.0)

    /** Clouds, wind, rain and lightning. */
    fun storm(a: Double): Double = window(a, 4.5, 8.5, 26.0, 30.0)

    /** The water's wall turning round the centre. */
    fun wall(a: Double): Double = window(a, 5.0, 9.0, 12.0, 15.0)

    /** The white shape rising in it. */
    fun silhouette(a: Double): Double = window(a, SHAPE, SHAPE + 2.3, BURST - 0.1, BURST + 0.35)

    /** The column of water thrown up by Lugia. */
    fun column(a: Double): Double = window(a, BURST, BURST + 0.12, BURST + 1.2, BURST + 2.8)

    fun trails(a: Double): Double = window(a, BURST, BURST + 0.5, HOVER - 0.2, HOVER + 0.6) + window(a, DESCENT + 0.2, DESCENT + 0.8, ARRIVED - 0.1, LANDED)

    fun charge(a: Double): Double = window(a, CHARGE, FIRE - 0.3, FIRE - 0.05, FIRE + 0.2)

    /** The rings round its body. */
    fun rings(a: Double): Double = window(a, CHARGE + 0.4, CHARGE + 1.4, FIRE + 1.3, FIRE + 2.3)

    fun beam(a: Double): Double = window(a, FIRE - 0.05, FIRE + 0.05, FIRE + 1.4, FIRE + 2.4)

    /** The beam's length (it shoots out in a quarter of a second). */
    fun beamLength(a: Double): Double = 140.0 * easeOutCubic(phase(a, FIRE, FIRE + 0.25))

    /** The shock wave crossing the sky along the beam, from 0 (at the sphere) to 1. */
    fun shockwave(a: Double): Double = phase(a, FIRE, FIRE + 1.6)

    /** The other sounds fading (Minecraft's), from 0 to 1. */
    fun duck(a: Double): Double = window(a, 0.0, 2.5, 27.5, 30.8)

    /** Test only: Lugia dissolving at the end (nothing appears). */
    fun vanish(a: Double): Double = if (test) smooth(phase(a, END - 0.85, END - 0.2)) else 0.0

    /** The lightning far away: times, and where (x, z) in the frame. */
    val strikes: List<DoubleArray> = listOf(6.1, 7.4, 8.9, 10.2, 11.2, 15.6, 18.9, 21.6, 25.0).mapIndexed { i, at ->
        val angle = PI + (i - 4) * 0.33
        val r = 38.0 + 9.0 * (i % 3)
        doubleArrayOf(at, vortex.x + r * sin(angle), vortex.z + r * cos(angle))
    }

    /** The light of the strikes (the clouds lit). */
    fun lightning(a: Double): Double {
        var k = 0.0
        for (s in strikes) k += pulse(a, s[0], 0.03, 0.18)
        return k.coerceIn(0.0, 1.0)
    }

    override fun envelopes(a: Double): DoubleArray = doubleArrayOf(
        swirl(a), glow(a), storm(a), wall(a), silhouette(a), column(a), trails(a).coerceAtMost(1.0), charge(a), rings(a), beam(a),
        shockwave(a), duck(a), vanish(a), lightning(a),
    )

    // ---------------------------------------------------------------- the camera

    private val m = Vector3d()

    private fun shot(name: String, start: Double, blend: Double, f: (Double, CamPose) -> Unit) = Shot(name, start, blend) { t, _, out -> f(t, out) }

    /** A point round the whirlpool: angle in degrees from +z toward +x, at a height above the floor. */
    private fun around(angle: Double, radius: Double, above: Double, out: Vector3d): Vector3d {
        val r = Math.toRadians(angle)
        return out.set(vortex.x + radius * sin(r), base + above, vortex.z + radius * cos(r))
    }

    private val depths = shot("depths", 0.0, 0.0) { t, out ->
        val u = easeInOutSine(phase(t, 0.0, 5.4))
        out.pos.set(lerp(2.4, 1.2, u), lerp(1.9, 2.8, u), lerp(6.5, 3.5, u))
        val k = smooth(phase(t, 0.5, 5.0))
        out.target.set(0.0, lerp(1.2, vortex.y + 0.5, k), lerp(0.0, vortex.z, k))
        out.fov = lerp(62.0, 55.0, u)
    }
    private val storm = shot("storm", 5.2, 1.6) { t, out ->
        val u = easeInOutSine(phase(t, 5.2, 8.8))
        around(lerp(35.0, 70.0, u), 14.0, lerp(3.5, 2.6, u), out.pos)
        out.target.set(vortex.x, vortex.y + 1.5, vortex.z)
        out.fov = lerp(64.0, 60.0, u)
    }
    private val rising = shot("rising", 8.6, 1.4) { t, out ->
        val u = easeInOutSine(phase(t, 8.6, 12.0))
        around(lerp(70.0, 40.0, u), lerp(12.0, 10.0, u), lerp(2.2, 4.5, u), out.pos)
        out.target.set(vortex.x, vortex.y + lerp(-0.5, 1.5, u), vortex.z)
        out.fov = lerp(52.0, 48.0, u)
    }
    private val burst = shot("burst", BURST, 0.0) { t, out ->
        val u = easeInOutSine(phase(t, BURST, 14.4))
        out.pos.set(vortex.x + lerp(5.5, 6.5, u), base + lerp(1.0, 1.6, u), vortex.z + lerp(7.5, 8.0, u))
        lugiaPos(action(t), m)
        out.target.set(m.x, m.y + 2.5, m.z)
        out.fov = lerp(70.0, 66.0, u)
    }
    private val ascent = shot("ascent", 14.0, 0.9) { t, out ->
        val u = easeInOutSine(phase(t, 14.0, 17.0))
        around(lerp(50.0, 20.0, u), 11.0, lerp(2.0, 11.0, smooth(u)), out.pos)
        lugiaPos(action(t), m)
        out.target.set(m.x, m.y + 2.2, m.z)
        out.fov = lerp(62.0, 58.0, u)
    }
    private val orbit = shot("orbit", 16.8, 1.2) { t, out ->
        val u = easeInOutSine(phase(t, 16.8, 20.6))
        val a = action(t)
        lugiaPos(a, m)
        val angle = Math.toRadians(lerp(20.0, 190.0, u))
        val r = lerp(14.0, 13.0, u)
        out.pos.set(m.x + r * sin(angle), max(m.y + lerp(1.0, 2.0, u), base + 3.0), m.z + r * cos(angle))
        out.target.set(m.x, m.y + 2.0, m.z)
        out.fov = 55.0
    }
    private val chargeShot = shot("charge", 20.6, 1.0) { t, out ->
        val u = easeInOutSine(phase(t, 20.6, 23.2))
        out.pos.set(hover.x + lerp(-7.0, -5.0, u), hover.y + lerp(-1.8, -1.2, u), hover.z + lerp(-11.0, -8.5, u))
        out.target.set(sphere.x, lerp(sphere.y - 0.8, sphere.y, u), sphere.z + 1.0)
        out.fov = lerp(54.0, 48.0, u)
    }
    // A cut on the action: the beam seen leaving, then from the side, across the sky.
    private val aeroblast = shot("aeroblast", FIRE + 0.25, 0.0) { t, out ->
        val u = easeInOutSine(phase(t, FIRE + 0.25, 26.0))
        out.pos.set(hover.x + lerp(17.0, 18.5, u), hover.y + lerp(2.0, 3.0, u), hover.z + lerp(-4.0, -6.0, u))
        // Between Lugia and the beam: both in the frame.
        out.target.set(hover.x, hover.y + lerp(4.0, 6.0, u), hover.z - lerp(8.0, 12.0, u))
        out.fov = 70.0
    }
    private val descent = shot("descent", DESCENT, 1.6) { t, out ->
        val u = easeInOutSine(phase(t, DESCENT, 29.4))
        out.pos.set(lerp(4.0, 3.0, u), lerp(2.0, 1.9, u), lerp(9.0, 10.0, u))
        lugiaPos(action(t), m)
        out.target.set(m.x, m.y + 2.2, m.z)
        out.fov = 62.0
    }
    private val meeting = shot("meeting", 29.0, 1.0) { t, out ->
        val u = easeInOutSine(phase(t, 29.0, END))
        out.pos.set(lerp(2.6, 2.2, u), 1.9, lerp(9.0, 8.0, u))
        out.target.set(landing.x, landing.y + 2.4, landing.z)
        out.fov = 60.0
    }

    override val director = CameraDirector(listOf(depths, storm, rising, burst, ascent, orbit, chargeShot, aeroblast, descent, meeting))

    override fun cameraWeight(t: Double): Double = smoother(t / 1.2) * (1.0 - smoother((t - 29.2) / 1.8))
    override fun letterbox(t: Double): Double = smooth(t / 0.8) * (1.0 - smooth((t - 29.4) / 1.5))
    override fun title(t: Double): Double = window(t, 18.0, 18.6, 20.4, 21.2)
    override fun presence(t: Double): Double = smooth(t / 0.8) * (1.0 - smooth((t - 29.4) / 1.6))

    override fun shake(a: Double): Double =
        0.12 * window(a, 0.5, 3.0, 10.0, 12.5) + 0.2 * window(a, 5.0, 8.0, 11.5, 12.2) + 0.6 * decay(a, BURST, 0.5) + 0.25 * decay(a, CRY, 0.5) +
            0.35 * window(a, 21.5, FIRE - 0.1, FIRE, FIRE + 0.1) + 0.9 * decay(a, FIRE, 0.6) + 0.15 * decay(a, LANDED, 0.4)

    override fun flash(a: Double): Double {
        var k = 0.0
        for (s in strikes) k += 0.16 * pulse(a, s[0], 0.03, 0.15)
        k += 0.35 * pulse(a, BURST, 0.05, 0.4) + 0.6 * pulse(a, FIRE, 0.05, 0.45)
        return k.coerceIn(0.0, 1.0)
    }

    override fun grade(t: Double, out: Grade): Grade {
        val a = action(t)
        val k = window(a, 2.0, 7.0, 26.0, 30.0)
        val l = lightning(a)
        val b = beam(a)
        out.r = 1.0 - 0.12 * k + 0.05 * l
        out.g = 1.0 - 0.04 * k + 0.05 * l
        out.b = 1.0 + 0.1 * k + 0.06 * b
        out.exposure = 1.0 - 0.12 * k + 0.12 * l + 0.1 * b
        out.saturation = 1.0 - 0.1 * k + 0.08 * b
        out.contrast = 1.0 + 0.08 * k
        out.vignette = 0.12 + 0.18 * k
        return out
    }

    // ---------------------------------------------------------------- sounds

    override val cues: List<Cue> = buildList {
        val vx = vortex.x
        val vh = vortex.y + 0.5
        val vz = vortex.z
        if (test) add(Cue(0.0, Cue.Kind.SOUND, "generations_core:generations_core.item.lugia_shrine_song", "origin", 0.0, 1.5, 0.0, volume = 1.0f))
        add(Cue(0.2, Cue.Kind.SOUND, "minecraft:block.conduit.activate", "origin", 0.0, 1.0, 0.0, volume = 1.3f, pitch = 0.6f))
        add(Cue(0.9, Cue.Kind.SOUND, "minecraft:block.bubble_column.whirlpool_inside", "origin", vx, vh, vz, volume = 1.6f, pitch = 0.6f))
        add(Cue(2.4, Cue.Kind.SOUND, "minecraft:ambient.underwater.loop.additions.ultra_rare", "camera", volume = 1.2f, pitch = 0.7f))
        add(Cue(3.6, Cue.Kind.SOUND, "minecraft:block.bubble_column.upwards_ambient", "origin", vx, vh, vz, volume = 2.0f, pitch = 0.5f))
        add(Cue(5.0, Cue.Kind.EVENT, "surge"))
        add(Cue(5.0, Cue.Kind.SOUND, "minecraft:entity.player.splash.high_speed", "origin", vx, vh, vz, volume = 2.2f, pitch = 0.5f))
        add(Cue(7.0, Cue.Kind.SOUND, "minecraft:entity.breeze.wind_burst", "camera", volume = 1.0f, pitch = 0.55f))
        add(Cue(9.6, Cue.Kind.SOUND, "minecraft:block.conduit.ambient", "origin", vx, vh, vz, volume = 2.0f, pitch = 0.6f))
        add(Cue(10.6, Cue.Kind.SOUND, "minecraft:entity.breeze.wind_burst", "camera", volume = 1.2f, pitch = 0.5f))
        for ((i, s) in strikes.withIndex()) add(Cue(s[0], Cue.Kind.EVENT, "bolt$i"))
        add(Cue(BURST, Cue.Kind.EVENT, "burst"))
        add(Cue(BURST, Cue.Kind.SOUND, "minecraft:entity.generic.splash", "origin", vx, vh, vz, volume = 3.0f, pitch = 0.45f))
        add(Cue(BURST + 0.05, Cue.Kind.SOUND, "minecraft:item.trident.riptide_3", "origin", vx, vh, vz, volume = 2.4f, pitch = 0.7f))
        add(Cue(BURST + 0.35, Cue.Kind.ANIMATE, "glad"))
        for ((i, at) in doubleArrayOf(13.4, 14.1, 14.8, 15.5, 18.4, 19.4).withIndex()) {
            add(Cue(at, Cue.Kind.SOUND, "minecraft:entity.ender_dragon.flap", "actor", volume = 3.0f, pitch = 0.75f + 0.03f * i))
        }
        add(Cue(CRY, Cue.Kind.CRY, "cobblemon:pokemon.lugia.cry", "actor", volume = 4.0f, pitch = 0.95f))
        add(Cue(CRY, Cue.Kind.EVENT, "cry"))
        add(Cue(CHARGE + 0.2, Cue.Kind.SOUND, "minecraft:entity.warden.sonic_charge", "actor", volume = 2.6f, pitch = 0.8f))
        add(Cue(CHARGE + 0.4, Cue.Kind.SOUND, "minecraft:block.beacon.activate", "actor", volume = 1.6f, pitch = 0.6f))
        add(Cue(22.4, Cue.Kind.SOUND, "minecraft:block.respawn_anchor.charge", "actor", volume = 2.0f, pitch = 0.6f))
        add(Cue(FIRE - 0.3, Cue.Kind.ANIMATE, "special"))
        add(Cue(FIRE, Cue.Kind.EVENT, "fire"))
        add(Cue(FIRE, Cue.Kind.SOUND, "minecraft:entity.warden.sonic_boom", "actor", volume = 4.0f, pitch = 0.7f))
        add(Cue(FIRE + 0.05, Cue.Kind.SOUND, "minecraft:item.trident.thunder", "actor", volume = 2.0f, pitch = 0.7f))
        add(Cue(FIRE + 0.8, Cue.Kind.SOUND, "minecraft:entity.breeze.wind_burst", "camera", volume = 2.0f, pitch = 0.45f))
        for ((i, at) in doubleArrayOf(26.4, 27.4, 28.3).withIndex()) {
            add(Cue(at, Cue.Kind.SOUND, "minecraft:entity.ender_dragon.flap", "actor", volume = 2.6f, pitch = 0.85f + 0.04f * i))
        }
        add(Cue(LANDED, Cue.Kind.EVENT, "landed"))
        add(Cue(LANDED, Cue.Kind.SOUND, "minecraft:block.conduit.deactivate", "origin", 0.0, 1.0, 0.0, volume = 1.2f, pitch = 0.8f))
        add(Cue(LANDED + 0.3, Cue.Kind.CRY, "cobblemon:pokemon.lugia.cry", "actor", volume = 2.5f, pitch = 1.0f))
        if (test) add(Cue(END - 0.85, Cue.Kind.EVENT, "vanish"))
    }.sortedBy { it.time }

    companion object {
        /** The white shape appears in the whirlpool. */
        const val SHAPE = 7.5
        const val BURST = 12.0
        const val EMERGED = 14.2
        const val TOP = 16.8
        const val CRY = 17.2
        const val GLIDE = 17.8
        const val HOVER = 20.4
        const val CHARGE = 20.4
        const val FIRE = 23.3
        const val DESCENT = 26.0
        const val ARRIVED = 28.9
        const val LANDED = 29.6
        const val END = 31.0
    }
}
