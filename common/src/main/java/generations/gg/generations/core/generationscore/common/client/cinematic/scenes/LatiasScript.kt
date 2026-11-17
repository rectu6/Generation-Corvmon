package generations.gg.generations.core.generationscore.common.client.cinematic.scenes

import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraDirector
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.decay
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.easeInCubic
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.easeInOutSine
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.lerp
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.orbit
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
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A path through points at given times (pure): cubic Hermite pieces whose tangents follow the
 * times (smooth speeds through the points), still at its ends when asked.
 */
class TimedTrack(private val times: DoubleArray, private val points: Array<Vector3d>, private val startStill: Boolean = false, private val endStill: Boolean = false) {
    init {
        require(times.size == points.size && times.size >= 2) { "a track has two points or more, one time each" }
    }

    val start: Double get() = times.first()
    val end: Double get() = times.last()

    private fun tangent(i: Int, out: Vector3d): Vector3d {
        if (i == 0) return if (startStill) out.set(0.0, 0.0, 0.0) else out.set(points[1]).sub(points[0]).div(times[1] - times[0])
        val n = points.size - 1
        if (i == n) return if (endStill) out.set(0.0, 0.0, 0.0) else out.set(points[n]).sub(points[n - 1]).div(times[n] - times[n - 1])
        return out.set(points[i + 1]).sub(points[i - 1]).div(times[i + 1] - times[i - 1])
    }

    private val m0 = Vector3d()
    private val m1 = Vector3d()

    fun at(a: Double, out: Vector3d): Vector3d {
        if (a <= times[0]) return out.set(points[0])
        if (a >= times.last()) return out.set(points.last())
        var i = 0
        while (i < times.size - 2 && times[i + 1] <= a) i++
        val h = times[i + 1] - times[i]
        val u = (a - times[i]) / h
        tangent(i, m0)
        tangent(i + 1, m1)
        val u2 = u * u
        val u3 = u2 * u
        val h00 = 2 * u3 - 3 * u2 + 1
        val h10 = u3 - 2 * u2 + u
        val h01 = -2 * u3 + 3 * u2
        val h11 = u3 - u2
        val p0 = points[i]
        val p1 = points[i + 1]
        return out.set(
            h00 * p0.x + h10 * h * m0.x + h01 * p1.x + h11 * h * m1.x,
            h00 * p0.y + h10 * h * m0.y + h01 * p1.y + h11 * h * m1.y,
            h00 * p0.z + h10 * h * m0.z + h01 * p1.z + h11 * h * m1.z,
        )
    }
}

/** A yaw that faces from a point toward another (0 = local +z). */
internal fun yawToward(dx: Double, dz: Double): Double = Math.toDegrees(atan2(-dx, dz))

/** An actor's yaw, pitch and bank along a path, from its velocity (finite differences). */
internal fun attitude(pos: (Double, Vector3d) -> Vector3d, a: Double, out: ActorKey, fallbackYaw: Double, bankScale: Double = 0.22, maxBank: Double = 60.0) {
    val p0 = Vector3d()
    val p1 = Vector3d()
    val p2 = Vector3d()
    pos(a - 0.06, p0)
    pos(a, p1)
    pos(a + 0.06, p2)
    val vx = (p2.x - p0.x) / 0.12
    val vy = (p2.y - p0.y) / 0.12
    val vz = (p2.z - p0.z) / 0.12
    val h = sqrt(vx * vx + vz * vz)
    out.yaw = if (h > 0.8) yawToward(vx, vz) else fallbackYaw
    out.pitch = if (h + kotlin.math.abs(vy) > 0.8) (-Math.toDegrees(atan2(vy, h)) * 0.85).coerceIn(-80.0, 80.0) else 0.0
    // Banking into the turn: the yaw's rate.
    val y0 = if (sqrt((p1.x - p0.x) * (p1.x - p0.x) + (p1.z - p0.z) * (p1.z - p0.z)) > 0.02) yawToward(p1.x - p0.x, p1.z - p0.z) else out.yaw
    val y1 = if (sqrt((p2.x - p1.x) * (p2.x - p1.x) + (p2.z - p1.z) * (p2.z - p1.z)) > 0.02) yawToward(p2.x - p1.x, p2.z - p1.z) else out.yaw
    var d = (y1 - y0) % 360.0
    if (d > 180.0) d -= 360.0
    if (d < -180.0) d += 360.0
    out.roll = (-d / 0.06 * bankScale).coerceIn(-maxBank, maxBank)
}

/**
 * Latias — the Scarlet Mirage (EON-PLAN.md § 6), 23 s, pure (tested off-game). Its frame: the
 * player's feet, +z where they looked when the summoning ended.
 *
 * 0–5 s: the Enigma Stone wakes in front of the player, red and pink waves; an invisible
 * silhouette crosses the sky, seen only by its distortion, the camera following it.
 * 5–10 s: six luminous illusions of Latias circle the player and vanish one after the other,
 * small pink bursts drawing their ways.
 * 10–17 s: the real Latias: a dive, a tight turn, a vertical climb, a roll, a pass at full speed
 * over the player (slowed down), red and white trails.
 * 17–23 s: still in the air, a pink psychic sphere; its wave dispels the last illusions, her cry;
 * she comes down to the meeting point, the camera given back.
 */
class LatiasScript(landing: Vector3d, val test: Boolean) : SceneScript {
    val landing = Vector3d(landing)

    private val slow = SlowMotion(OVERHEAD, 0.45, 0.5, OVERHEAD + 1.6, 0.6)

    override fun action(t: Double): Double = slow.action(t)

    // ---------------------------------------------------------------- the paths

    /** The Enigma Stone, floating in front of the player. */
    val stone = Vector3d(0.0, 1.25, 0.85)

    /** The invisible silhouette crossing the sky. */
    private val ghostTrack = TimedTrack(
        doubleArrayOf(GHOST, 2.6, 3.6, GHOST_END),
        arrayOf(Vector3d(-34.0, 16.0, 26.0), Vector3d(-12.0, 11.0, 16.0), Vector3d(6.0, 9.0, 12.5), Vector3d(30.0, 13.0, 21.0)),
    )

    /** The flight: the dive, the turn, the climb, the roll, the pass, the braking. */
    private val flightTrack = TimedTrack(
        doubleArrayOf(REVEAL, 10.9, DIVE_BOTTOM, 12.2, TURN, 13.2, CLIMB_TOP, 14.3, OVERHEAD, 15.7, 16.4, HOVER),
        arrayOf(
            Vector3d(-22.0, 19.0, 32.0), Vector3d(-14.0, 11.0, 24.0), Vector3d(-5.0, 3.4, 15.0), Vector3d(3.0, 3.0, 12.0),
            Vector3d(7.0, 4.2, 7.0), Vector3d(6.5, 10.0, 6.0), Vector3d(5.0, 18.0, 6.0), Vector3d(2.0, 15.0, -6.0),
            Vector3d(0.0, 4.8, 0.5), Vector3d(-2.0, 8.0, 14.0), Vector3d(-4.0, 7.5, 13.0), Vector3d(0.0, 6.0, 9.5),
        ),
        endStill = true,
    )

    val hover = Vector3d(0.0, 6.0, 9.5)
    private val descentTrack: TimedTrack
    private val tmp = Vector3d()

    init {
        val mid = Vector3d(landing.x * 0.6, landing.y + 3.0, landing.z + 2.6)
        descentTrack = TimedTrack(doubleArrayOf(DESCEND, 21.0, LANDING), arrayOf(Vector3d(hover), mid, Vector3d(landing)), startStill = true, endStill = true)
    }

    /** Latias's feet at action time a (hidden before the reveal but for the silhouette). */
    fun latiasPos(a: Double, out: Vector3d): Vector3d = when {
        a < REVEAL -> ghostTrack.at(a, out)
        a < HOVER -> flightTrack.at(a, out)
        a < DESCEND -> out.set(hover.x + 0.1 * sin(a * 0.9), hover.y + 0.15 * sin(a * 2.0), hover.z)
        else -> descentTrack.at(a, out)
    }

    fun centre(a: Double, out: Vector3d): Vector3d = latiasPos(a, out).add(0.0, BODY, 0.0)

    override fun actor(a: Double, out: ActorKey): ActorKey {
        latiasPos(a, out.pos)
        out.scale = 1.0
        out.pitch = 0.0
        out.roll = 0.0
        val face = yawToward(-out.pos.x, -out.pos.z)
        when {
            a < GHOST || (a >= GHOST_END && a < REVEAL) -> {
                out.pose = ActorPose.HIDDEN
                out.yaw = face
            }
            a < GHOST_END -> {
                out.pose = ActorPose.FLY
                attitude(::latiasPos, a, out, face, 0.15, 35.0)
            }
            a < HOVER -> {
                out.pose = ActorPose.FLY
                attitude(::latiasPos, a, out, face)
                // The roll at the top of the climb.
                out.roll += 360.0 * easeInOutSine(phase(a, ROLL, ROLL_END))
                // Turning to face the player as she brakes.
                val brake = smooth(phase(a, 16.2, HOVER))
                out.yaw = lerpAngle(out.yaw, face, brake)
                out.pitch *= 1.0 - brake
                out.roll *= 1.0 - brake
                if (brake > 0.6) out.pose = ActorPose.HOVER
            }
            else -> {
                out.pose = ActorPose.HOVER
                out.yaw = face
                out.roll = 6.0 * sin(a * 1.3) * (1.0 - phase(a, DESCEND, LANDING))
            }
        }
        if (test && a >= VANISH) {
            out.scale = 1.0 - easeInCubic(phase(a, VANISH, VANISH_END))
            if (a >= VANISH_END) out.pose = ActorPose.HIDDEN
        }
        return out
    }

    private fun lerpAngle(a: Double, b: Double, u: Double): Double {
        var d = (b - a) % 360.0
        if (d > 180.0) d -= 360.0
        if (d < -180.0) d += 360.0
        return a + d * u
    }

    /** When illusion i of the circle vanishes (one after the other, in a shuffled order). */
    fun vanishAt(i: Int): Double = POPS + 0.4 * VANISH_ORDER.indexOf(i)

    /** The illusions, more actors: six circling the player (5–9.8 s), six round her before the wave (17–19 s). */
    fun illusion(i: Int, a: Double, out: ActorKey): ActorKey {
        out.pose = ActorPose.HIDDEN
        out.scale = 0.0
        out.pitch = 0.0
        out.roll = 0.0
        if (a >= ILLUSIONS && a < vanishAt(i)) {
            val angle = 2.0 * PI * i / ILLUSION_COUNT + 0.95 * (a - ILLUSIONS)
            val radius = 8.5 + 1.5 * sin(a + i)
            out.pos.set(sin(angle) * radius, 2.6 + 1.3 * sin(0.8 * a + i * 1.1), cos(angle) * radius)
            out.scale = smooth((a - ILLUSIONS - 0.12 * i) / 0.5)
            if (out.scale <= 0.01) return out
            // Along the circle, leaning in.
            out.yaw = Math.toDegrees(-angle) - 90.0
            out.roll = -24.0
            out.pitch = -6.0 * cos(0.8 * a + i * 1.1)
            out.pose = ActorPose.FLY
            return out
        }
        if (a >= HOVER && a < WAVE) {
            val angle = 2.0 * PI * i / ILLUSION_COUNT - 1.4 * (a - HOVER)
            val radius = 3.6 + 0.4 * sin(a * 2.0 + i)
            out.pos.set(hover.x + sin(angle) * radius, hover.y - 0.4 + 0.6 * sin(a * 1.5 + i), hover.z + cos(angle) * radius)
            out.scale = 0.8 * smooth((a - HOVER - 0.1 * i) / 0.5)
            if (out.scale <= 0.01) return out
            out.yaw = Math.toDegrees(-angle) + 90.0
            out.roll = 20.0
            out.pose = ActorPose.FLY
        }
        return out
    }

    // ---------------------------------------------------------------- the camera

    private val m = Vector3d()

    /** Close on the stone waking up, the player's view leaning in. */
    private val stoneShot = Shot("stone", 0.0, 0.0) { t, motion, out ->
        val u = easeInOutSine(phase(t, 0.0, GHOST + 0.5))
        out.pos.set(lerp(0.45, 0.95, u), lerp(1.6, 1.4, u), lerp(-0.15, 0.25, u))
        out.target.set(stone.x, stone.y + 0.05, stone.z)
        out.fov = lerp(56.0, 40.0, u)
        out.roll = 1.5 * u * motion.roll
    }

    /** From behind the player, following the distortion across the sky. */
    private val ghostShot = Shot("ghost", GHOST + 0.5, 0.9) { t, motion, out ->
        val u = easeInOutSine(phase(t, GHOST + 0.5, GHOST_END))
        out.pos.set(lerp(-1.0, -1.6, u), 1.55, lerp(-1.4, -1.0, u))
        latiasPos(action(t).coerceIn(GHOST, GHOST_END), m)
        out.target.set(m.x, m.y + 0.5, m.z)
        out.fov = 62.0
        out.roll = -1.5 * sin(u * PI) * motion.roll
    }

    /** A low orbit round the player, the illusions circling over them. */
    private val circleShot = Shot("illusions", GHOST_END, 0.9) { t, motion, out ->
        val u = easeInOutSine(phase(t, GHOST_END, REVEAL))
        orbit(0.0, 0.0, lerp(3.8, 3.2, u), -160.0 + 80.0 * u * motion.orbit, lerp(1.0, 1.3, u), out.pos)
        out.target.set(0.0, 3.4, 0.0)
        out.fov = 70.0
        out.roll = 2.0 * sin(u * PI) * motion.roll
    }

    /** The reveal and the dive, from beside the player. */
    private val diveShot = Shot("dive", REVEAL, 0.6) { t, _, out ->
        val u = easeInOutSine(phase(t, REVEAL, DIVE_BOTTOM))
        out.pos.set(lerp(2.4, 1.8, u), lerp(1.5, 1.2, u), lerp(1.6, 2.6, u))
        latiasPos(action(t), m)
        out.target.set(m.x, m.y + BODY, m.z)
        out.fov = lerp(58.0, 52.0, u)
        out.roll = 0.0
    }

    /** Low, the tight turn and the climb. */
    private val turnShot = Shot("turn", DIVE_BOTTOM, 0.5) { t, motion, out ->
        val u = easeInOutSine(phase(t, DIVE_BOTTOM, CLIMB_TOP))
        out.pos.set(lerp(-1.6, -0.6, u), lerp(1.1, 0.9, u), lerp(4.2, 3.4, u))
        latiasPos(action(t), m)
        out.target.set(m.x, m.y + BODY, m.z)
        out.fov = lerp(60.0, 66.0, u)
        out.roll = -2.5 * u * motion.roll
    }

    /** Behind the player, looking up: the roll, then the pass over their head (slowed down). */
    private val passShot = Shot("pass", CLIMB_TOP, 0.5) { t, motion, out ->
        val u = easeInOutSine(phase(t, CLIMB_TOP, 15.9))
        out.pos.set(lerp(-3.2, -2.6, u), lerp(1.3, 1.5, u), lerp(-3.2, -2.4, u))
        latiasPos(action(t), m)
        out.target.set(m.x, m.y + BODY, m.z)
        out.fov = 68.0 - 6.0 * pulse(t, OVERHEAD, 0.05, 0.5) * motion.punch
        out.roll = 3.0 * sin(u * PI) * motion.roll
    }

    /** She brakes and turns back. */
    private val brakeShot = Shot("brake", 15.9, 0.7) { t, _, out ->
        val u = easeInOutSine(phase(t, 15.9, HOVER + 0.2))
        out.pos.set(lerp(-2.8, -1.8, u), lerp(1.8, 2.2, u), lerp(2.6, 3.6, u))
        latiasPos(action(t), m)
        out.target.set(m.x, m.y + BODY, m.z)
        out.fov = lerp(58.0, 50.0, u)
        out.roll = 0.0
    }

    /** In front, a little below her: the sphere, its wave, her cry. */
    private val sphereShot = Shot("sphere", HOVER + 0.2, 0.7) { t, motion, out ->
        val u = easeInOutSine(phase(t, HOVER + 0.2, DESCEND + 0.2))
        out.pos.set(lerp(1.6, 1.1, u), lerp(2.2, 2.6, u), lerp(4.0, 4.8, u))
        centre(action(t), m)
        out.target.set(m.x, m.y, m.z)
        out.fov = lerp(50.0, 44.0, u) + 7.0 * pulse(t, WAVE, 0.04, 0.5) * motion.punch
        out.roll = 0.0
    }

    /** Beside the player, Latias coming down to them. */
    private val finalShot = Shot("final", DESCEND + 0.2, 0.8) { t, _, out ->
        val u = easeInOutSine(phase(t, DESCEND + 0.2, 22.4))
        out.pos.set(lerp(1.9, 1.5, u), lerp(1.8, 1.6, u), lerp(0.3, 0.8, u))
        centre(action(t), m)
        out.target.set(m.x, m.y, m.z)
        out.fov = lerp(54.0, 48.0, u)
        out.roll = 0.0
    }

    override val director = CameraDirector(listOf(stoneShot, ghostShot, circleShot, diveShot, turnShot, passShot, brakeShot, sphereShot, finalShot))

    // ---------------------------------------------------------------- the look

    override fun cameraWeight(t: Double): Double = smoother(t / 1.0) * (1.0 - smoother((t - 21.6) / 1.4))

    override fun letterbox(t: Double): Double = smooth(t / 0.8) * (1.0 - smooth((t - 21.8) / 1.2))

    override fun title(t: Double): Double = window(t, 19.5, 20.0, 21.4, 22.0)

    override fun presence(t: Double): Double = smooth(t / 0.8) * (1.0 - smooth((t - 21.8) / 1.2))

    override fun shake(a: Double): Double = 0.12 * decay(a, DIVE_BOTTOM, 0.3) + 0.25 * decay(a, OVERHEAD, 0.4) + 0.35 * decay(a, WAVE, 0.5)

    override fun flash(a: Double): Double = (0.35 * pulse(a, REVEAL, 0.03, 0.3) + 0.55 * pulse(a, WAVE, 0.03, 0.35)).coerceIn(0.0, 1.0)

    /** Warm pinks and reds, a soft vignette while she is invisible, brighter at the sphere. */
    override fun grade(t: Double, out: Grade): Grade {
        val g = ghost(t)
        val s = sphere(t)
        val soft = smooth(t / 1.5) * (1.0 - smooth((t - 21.5) / 1.5))
        out.r = 1.0 + 0.06 * soft + 0.05 * s
        out.g = 1.0 - 0.03 * soft
        out.b = 1.0 + 0.03 * soft + 0.03 * g
        out.exposure = 1.0 - 0.05 * g + 0.1 * s
        out.saturation = 1.0 + 0.12 * soft
        out.contrast = 1.0 + 0.05 * soft
        out.vignette = 0.2 * soft + 0.15 * g
        return out
    }

    // ---------------------------------------------------------------- envelopes

    fun stoneGlow(a: Double): Double = window(a, 0.0, 0.5, 2.2, 3.2)

    fun waves(a: Double): Double = window(a, 0.3, 0.8, 4.2, 5.2)

    fun ghost(a: Double): Double = window(a, GHOST, GHOST + 0.4, GHOST_END - 0.4, GHOST_END)

    fun illusions(a: Double): Double = window(a, ILLUSIONS, ILLUSIONS + 0.6, POPS + 2.2, REVEAL) + window(a, HOVER, HOVER + 0.5, WAVE - 0.1, WAVE + 0.1)

    fun reveal(a: Double): Double = if (a < REVEAL) 0.0 else 1.0 - smooth((a - REVEAL) / 0.9)

    fun trails(a: Double): Double = window(a, REVEAL + 0.2, REVEAL + 0.7, 16.4, HOVER)

    fun sphere(a: Double): Double = window(a, SPHERE, SPHERE + 0.8, WAVE, WAVE + 0.6)

    fun wave(a: Double): Double = if (a < WAVE) 0.0 else 1.0 - smooth((a - WAVE) / 1.3)

    fun sparkle(a: Double): Double = window(a, DESCEND, DESCEND + 0.6, LANDING, 22.8)

    override fun envelopes(a: Double): DoubleArray = doubleArrayOf(stoneGlow(a), waves(a), ghost(a), illusions(a).coerceAtMost(1.0), reveal(a), trails(a), sphere(a), wave(a), sparkle(a))

    // ---------------------------------------------------------------- cues

    override val cues: List<Cue> = buildList {
        add(Cue(0.2, Cue.Kind.SOUND, "minecraft:block.amethyst_block.resonate", "origin", 0.0, 1.2, 0.85, volume = 1.4f, pitch = 0.9f))
        add(Cue(0.6, Cue.Kind.SOUND, "minecraft:block.beacon.activate", "origin", 0.0, 1.2, 0.85, volume = 1.0f, pitch = 1.6f))
        add(Cue(GHOST, Cue.Kind.EVENT, "ghost"))
        add(Cue(1.7, Cue.Kind.SOUND, "minecraft:entity.phantom.swoop", "actor", volume = 1.4f, pitch = 1.6f))
        add(Cue(3.4, Cue.Kind.SOUND, "minecraft:entity.phantom.swoop", "actor", volume = 1.2f, pitch = 1.8f))
        add(Cue(ILLUSIONS, Cue.Kind.EVENT, "illusions"))
        add(Cue(ILLUSIONS, Cue.Kind.SOUND, "minecraft:entity.illusioner.prepare_mirror", "origin", 0.0, 3.0, 0.0, volume = 1.6f, pitch = 1.2f))
        add(Cue(6.2, Cue.Kind.SOUND, "minecraft:entity.allay.ambient_with_item", "origin", 0.0, 3.0, 0.0, volume = 1.0f, pitch = 1.1f))
        for (i in 0 until ILLUSION_COUNT) {
            add(Cue(vanishAt(i), Cue.Kind.EVENT, "pop$i"))
            add(Cue(vanishAt(i), Cue.Kind.SOUND, "minecraft:block.amethyst_cluster.break", "origin", 0.0, 3.0, 0.0, volume = 1.2f, pitch = 1.3f + 0.08f * i))
        }
        add(Cue(REVEAL, Cue.Kind.EVENT, "reveal"))
        add(Cue(REVEAL, Cue.Kind.SOUND, "minecraft:entity.illusioner.cast_spell", "actor", volume = 1.8f, pitch = 1.3f))
        add(Cue(10.3, Cue.Kind.CRY, "cobblemon:pokemon.latias.cry", "actor", volume = 1.4f, pitch = 1.1f))
        add(Cue(11.0, Cue.Kind.SOUND, "minecraft:entity.phantom.swoop", "actor", volume = 1.8f, pitch = 1.4f))
        add(Cue(TURN, Cue.Kind.SOUND, "minecraft:entity.phantom.flap", "actor", volume = 1.4f, pitch = 1.5f))
        add(Cue(ROLL, Cue.Kind.SOUND, "minecraft:item.trident.riptide_1", "actor", volume = 1.4f, pitch = 1.6f))
        add(Cue(14.6, Cue.Kind.SOUND, "minecraft:entity.phantom.swoop", "actor", volume = 2.2f, pitch = 1.2f))
        add(Cue(OVERHEAD, Cue.Kind.EVENT, "pass"))
        add(Cue(OVERHEAD, Cue.Kind.SOUND, "minecraft:item.trident.riptide_3", "actor", volume = 2.0f, pitch = 1.3f))
        add(Cue(SPHERE, Cue.Kind.ANIMATE, "special"))
        add(Cue(SPHERE, Cue.Kind.EVENT, "sphere"))
        add(Cue(SPHERE, Cue.Kind.SOUND, "minecraft:block.beacon.power_select", "actor", volume = 1.6f, pitch = 1.5f))
        add(Cue(WAVE, Cue.Kind.CRY, "cobblemon:pokemon.latias.cry", "actor", volume = 2.4f))
        add(Cue(WAVE, Cue.Kind.EVENT, "wave"))
        add(Cue(WAVE, Cue.Kind.SOUND, "minecraft:block.amethyst_block.resonate", "actor", volume = 1.8f, pitch = 1.4f))
        add(Cue(WAVE, Cue.Kind.SOUND, "minecraft:block.beacon.activate", "actor", volume = 1.4f, pitch = 1.7f))
        add(Cue(DESCEND, Cue.Kind.SOUND, "minecraft:entity.allay.ambient_with_item", "actor", volume = 1.0f, pitch = 1.2f))
        add(Cue(LANDING, Cue.Kind.EVENT, "landing"))
        add(Cue(LANDING, Cue.Kind.ANIMATE, "glad"))
        add(Cue(LANDING, Cue.Kind.SOUND, "minecraft:block.amethyst_block.chime", "actor", volume = 1.2f, pitch = 1.5f))
        if (test) {
            add(Cue(VANISH - 0.05, Cue.Kind.EVENT, "vanish"))
            add(Cue(VANISH, Cue.Kind.SOUND, "minecraft:entity.enderman.teleport", "actor", volume = 1.0f, pitch = 1.4f))
        }
    }.sortedBy { it.time }

    companion object {
        const val SECONDS = 23.0
        const val GHOST = 1.4
        const val GHOST_END = 5.0
        const val ILLUSIONS = 5.0
        const val POPS = 7.4
        const val REVEAL = 10.0
        const val DIVE_BOTTOM = 11.7
        const val TURN = 12.7
        const val CLIMB_TOP = 13.7
        const val ROLL = 13.6
        const val ROLL_END = 14.3
        const val OVERHEAD = 15.0
        const val HOVER = 17.0
        const val SPHERE = 17.4
        const val WAVE = 19.0
        const val DESCEND = 19.6
        const val LANDING = 22.2
        const val VANISH = 22.5
        const val VANISH_END = 22.9
        const val ILLUSION_COUNT = 6
        val VANISH_ORDER = intArrayOf(0, 3, 1, 4, 2, 5)

        /** Her body's centre over her feet. */
        const val BODY = 0.7
    }
}
