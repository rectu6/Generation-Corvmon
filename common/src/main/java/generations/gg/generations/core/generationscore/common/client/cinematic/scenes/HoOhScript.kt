package generations.gg.generations.core.generationscore.common.client.cinematic.scenes

import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraDirector
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.decay
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.easeInOutSine
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.lerp
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.phase
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.pulse
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.smooth
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.smoother
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.window
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.Shot
import org.joml.Vector3d
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Ho-Oh — the Passage of the Rainbow (HOOH-PLAN.md § 6), 13 s, pure (tested off-game). Its frame:
 * the player's feet, +z the passage's way (the most open side). `landing` is where the pursuit
 * begins (the server's first point of the flight): Ho-Oh is there at 13 s, flying on.
 *
 * 0–4.8 s: the signal. A cry from the sky, a golden light growing ahead and up to the left,
 * red, orange and gold embers coming down; from 2.2 s a rainbow forms across the sky.
 * 4.8–8.5 s: Ho-Oh comes out of the light, wings spread, and dives toward the player.
 * 8.5 s: it passes low over the player in slow motion, its cry, a gust of wind and light.
 * 8.5–13 s: a wide banked turn to the right, then on along the passage's way; the camera goes
 * back to the player's eyes: the pursuit begins.
 */
class HoOhScript(landing: Vector3d) : SceneScript {
    val landing = Vector3d(landing)

    private val slow = SlowMotion(ZENITH, 0.55, 0.55, 10.7, 0.7)

    override fun action(t: Double): Double = slow.action(t)

    // ---------------------------------------------------------------- Ho-Oh's path

    /** Where the light is (Ho-Oh comes out of it). */
    val light = Vector3d(-42.0, 48.0, 64.0)

    /** The rainbow: its centre (on the ground, far ahead) and radius. */
    val rainbowCentre = Vector3d(0.0, -10.0, 105.0)
    val rainbowRadius = 78.0

    private val points: List<Vector3d> = listOf(
        Vector3d(-58.0, 60.0, 84.0),
        Vector3d(light),
        Vector3d(-16.0, 30.0, 38.0),
        Vector3d(5.0, 15.5, 8.0),
        Vector3d(26.0, 19.0, 6.0),
        Vector3d(landing.x + 14.0, landing.y - 1.0, landing.z - 14.0),
        Vector3d(landing),
        Vector3d(landing.x, landing.y, landing.z + 16.0),
    )

    /** When Ho-Oh is at each point (points 1 to 6). */
    private val times = doubleArrayOf(EMERGE, 6.7, ZENITH, 10.1, 11.6, END)

    private fun coord(i: Int, c: Int): Double = when (c) {
        0 -> points[i].x
        1 -> points[i].y
        else -> points[i].z
    }

    /** Ho-Oh's feet at action time a. */
    fun hoohPos(a: Double, out: Vector3d): Vector3d {
        val t = a.coerceIn(EMERGE, END + 1.0)
        if (t >= END) {
            // On along the way at the pursuit's speed.
            val extra = t - END
            val dx = points[7].x - points[6].x
            val dz = points[7].z - points[6].z
            val l = sqrt(dx * dx + dz * dz).coerceAtLeast(1e-6)
            return out.set(landing.x + dx / l * 5.0 * extra, landing.y, landing.z + dz / l * 5.0 * extra)
        }
        var k = 0
        while (k < times.size - 2 && t > times[k + 1]) k++
        val u = ((t - times[k]) / (times[k + 1] - times[k])).coerceIn(0.0, 1.0)
        val i = k + 1
        val u2 = u * u
        val u3 = u2 * u
        val r = DoubleArray(3)
        for (c in 0..2) {
            val p0 = coord(i - 1, c)
            val p1 = coord(i, c)
            val p2 = coord(i + 1, c)
            val p3 = coord(i + 2, c)
            r[c] = 0.5 * (2.0 * p1 + (-p0 + p2) * u + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * u2 + (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * u3)
        }
        return out.set(r[0], r[1], r[2])
    }

    private val a1 = Vector3d()
    private val a2 = Vector3d()

    private fun yawTo(dx: Double, dz: Double): Double = Math.toDegrees(atan2(-dx, dz))

    override fun actor(a: Double, out: ActorKey): ActorKey {
        out.scale = SCALE * smooth(phase(a, EMERGE, EMERGE + 0.6))
        out.pose = if (a < EMERGE) ActorPose.HIDDEN else ActorPose.FLY
        hoohPos(a, out.pos)
        // Its heading and attitude from its way.
        hoohPos(a + 0.08, a1)
        hoohPos(a - 0.08, a2)
        val vx = a1.x - a2.x
        val vy = a1.y - a2.y
        val vz = a1.z - a2.z
        val h = sqrt(vx * vx + vz * vz)
        out.yaw = yawTo(vx, vz)
        out.pitch = (-Math.toDegrees(atan2(vy, h.coerceAtLeast(1e-6))) * 0.7).coerceIn(-35.0, 35.0)
        // Banking into the turn after its pass.
        hoohPos(a + 0.24, a1)
        val ahead = yawTo(a1.x - out.pos.x, a1.z - out.pos.z)
        var turn = (ahead - out.yaw) % 360.0
        if (turn > 180.0) turn -= 360.0
        if (turn < -180.0) turn += 360.0
        out.roll = (-turn * 1.6).coerceIn(-40.0, 40.0)
        return out
    }

    // ---------------------------------------------------------------- the camera

    private val m = Vector3d()

    /** From the player's eyes, a slow look up at the light and the rainbow. */
    private val sky = Shot("sky", 0.0, 0.0) { t, motion, out ->
        val u = easeInOutSine(phase(t, 0.0, EMERGE))
        out.pos.set(lerp(0.0, 1.2, u), lerp(1.62, 2.4, u), lerp(0.0, -2.0, u))
        out.target.set(lerp(0.0, light.x * 0.55, u), lerp(1.62, light.y * 0.45, u), lerp(10.0, light.z * 0.6, u))
        out.fov = lerp(70.0, 58.0, u)
        out.roll = -2.0 * u * motion.roll
    }

    /** Low beside the player, Ho-Oh diving at them. */
    private val dive = Shot("dive", EMERGE, 1.0) { t, motion, out ->
        val u = easeInOutSine(phase(t, EMERGE, 8.0))
        hoohPos(t, m)
        out.pos.set(lerp(2.6, 3.2, u), lerp(1.4, 1.1, u), lerp(-2.4, -3.2, u))
        out.target.set(m.x, m.y + 1.5, m.z)
        out.fov = lerp(56.0, 68.0, u) + 4.0 * pulse(t, ZENITH, 0.05, 0.5) * motion.punch
        out.roll = 3.0 * u * motion.roll
    }

    /** On the ground, looking up: the pass over the player. */
    private val zenith = Shot("zenith", 8.0, 0.0) { t, motion, out ->
        val u = easeInOutSine(phase(t, 8.0, 10.2))
        hoohPos(t, m)
        out.pos.set(lerp(-1.6, -1.0, u), 0.9, lerp(3.2, 2.0, u))
        out.target.set(m.x, m.y + 1.2, m.z)
        out.fov = 74.0 - 6.0 * u
        out.roll = 4.0 * u * motion.roll
    }

    /** Back behind the player, then their own view: Ho-Oh flying on ahead. */
    private val follow = Shot("follow", 10.2, 0.9) { t, _, out ->
        val u = easeInOutSine(phase(t, 10.2, END))
        hoohPos(t, m)
        out.pos.set(lerp(-2.2, 0.0, u), lerp(3.4, 1.62, u), lerp(-4.6, 0.0, u))
        out.target.set(lerp(m.x, m.x * 0.8, u), lerp(m.y + 1.0, m.y * 0.75, u), lerp(m.z, m.z, u))
        out.fov = lerp(62.0, 70.0, u)
        out.roll = 0.0
    }

    override val director = CameraDirector(listOf(sky, dive, zenith, follow))

    // ---------------------------------------------------------------- the look

    override fun cameraWeight(t: Double): Double = smoother(t / 1.0) * (1.0 - smoother((t - 11.4) / 1.6))

    override fun letterbox(t: Double): Double = smooth(t / 0.8) * (1.0 - smooth((t - 11.6) / 1.3))

    override fun title(t: Double): Double = window(t, 9.0, 9.5, 11.0, 11.6)

    override fun presence(t: Double): Double = smooth(t / 0.8) * (1.0 - smooth((t - 11.8) / 1.2))

    override fun shake(a: Double): Double = 0.35 * decay(a, ZENITH, 0.6) + 0.08 * window(a, EMERGE, 6.0, 7.5, 8.4)

    override fun flash(a: Double): Double = (0.3 * pulse(a, ZENITH, 0.04, 0.35) + 0.2 * pulse(a, EMERGE, 0.05, 0.4)).coerceIn(0.0, 1.0)

    /** Warm and golden, brighter as Ho-Oh passes. */
    override fun grade(t: Double, out: Grade): Grade {
        val warm = smooth(t / 2.0) * (1.0 - smooth((t - 11.5) / 1.5))
        val pass = window(t, 7.6, 8.4, 9.4, 10.6)
        out.r = 1.0 + 0.08 * warm + 0.05 * pass
        out.g = 1.0 + 0.02 * warm
        out.b = 1.0 - 0.08 * warm
        out.exposure = 1.0 + 0.06 * warm + 0.12 * pass
        out.saturation = 1.0 + 0.15 * warm
        out.contrast = 1.0 + 0.05 * warm
        out.vignette = 0.18 * warm
        return out
    }

    // ---------------------------------------------------------------- envelopes

    /** The golden light ahead in the sky. */
    fun glow(a: Double): Double = window(a, 0.4, 2.6, 6.0, 7.4)

    /** The rainbow: forms from 2.2 s (reveal), stays until the end. */
    fun rainbow(a: Double): Double = window(a, 2.2, 3.6, 11.0, 12.8)

    /** How much of the rainbow's arc is drawn (from left to right). */
    fun reveal(a: Double): Double = easeInOutSine(phase(a, 2.2, 4.4))

    /** The embers coming down round the player. */
    fun embers(a: Double): Double = window(a, 0.8, 2.0, 11.0, 12.6)

    /** Ho-Oh's trail and halo. */
    fun trail(a: Double): Double = window(a, EMERGE, EMERGE + 0.8, 12.6, 13.4)

    /** The light and wind of its pass. */
    fun gust(a: Double): Double = if (a < ZENITH) 0.0 else 1.0 - smooth((a - ZENITH) / 1.4)

    override fun envelopes(a: Double): DoubleArray = doubleArrayOf(glow(a), rainbow(a), embers(a), trail(a), gust(a))

    // ---------------------------------------------------------------- cues

    override val cues: List<Cue> = buildList {
        add(Cue(0.4, Cue.Kind.SOUND, "cobblemon:pokemon.hooh.cry", "origin", light.x * 0.5, light.y * 0.5, light.z * 0.5, volume = 3.5f, pitch = 0.92f))
        add(Cue(1.2, Cue.Kind.SOUND, "minecraft:block.beacon.activate", "origin", 0.0, 3.0, 4.0, volume = 1.2f, pitch = 1.3f))
        add(Cue(2.2, Cue.Kind.EVENT, "rainbow"))
        add(Cue(2.2, Cue.Kind.SOUND, "minecraft:block.amethyst_block.resonate", "origin", 0.0, 4.0, 6.0, volume = 1.4f, pitch = 0.8f))
        add(Cue(EMERGE, Cue.Kind.EVENT, "emerge"))
        add(Cue(EMERGE, Cue.Kind.SOUND, "minecraft:entity.ender_dragon.flap", "actor", volume = 3f, pitch = 1.2f))
        add(Cue(6.0, Cue.Kind.SOUND, "minecraft:entity.ender_dragon.flap", "actor", volume = 3f, pitch = 1.15f))
        add(Cue(7.2, Cue.Kind.SOUND, "minecraft:entity.ender_dragon.flap", "actor", volume = 3f, pitch = 1.1f))
        add(Cue(8.0, Cue.Kind.SOUND, "minecraft:entity.phantom.swoop", "actor", volume = 2.5f, pitch = 0.55f))
        add(Cue(ZENITH, Cue.Kind.CRY, "cobblemon:pokemon.hooh.cry", "actor", volume = 4f))
        add(Cue(ZENITH, Cue.Kind.EVENT, "zenith"))
        add(Cue(ZENITH + 0.1, Cue.Kind.SOUND, "minecraft:entity.breeze.wind_burst", "camera", volume = 1.4f, pitch = 0.7f))
        add(Cue(10.2, Cue.Kind.SOUND, "minecraft:entity.ender_dragon.flap", "actor", volume = 3f, pitch = 1.2f))
        add(Cue(11.5, Cue.Kind.SOUND, "minecraft:entity.ender_dragon.flap", "actor", volume = 2.6f, pitch = 1.25f))
    }.sortedBy { it.time }

    companion object {
        const val SECONDS = 13.0
        const val EMERGE = 4.8
        const val ZENITH = 8.5
        const val END = 13.0
        /** Ho-Oh's size (hooh/ho_oh_passage.json's default scale, the same in the pursuit). */
        const val SCALE = 1.6
    }
}
