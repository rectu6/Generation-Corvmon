package generations.gg.generations.core.generationscore.common.client.cinematic.scenes

import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraDirector
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.bezier
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.decay
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.easeInCubic
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.easeInOutCubic
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.easeInOutSine
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.easeOutCubic
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.lerp
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.orbit
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.phase
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.pulse
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.smooth
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.smoother
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.window
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.Shot
import org.joml.Vector3d
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Moltres — Rebirth of the Flames (CINEMATIQUES-PLAN.md § 5), 20 s, pure.
 *
 * 0–4 s: a red and orange glow on the altar, embers rising, heat haze; the camera cranes down
 * and moves in toward the altar.
 * 4–9 s: a column of fire rises in layers (white core, scrolling fire, dark red edge) up to
 * 30 m, rings of fire widen and climb, sparks and ash whirl; the camera spirals up along it
 * from 2 to 28 m.
 * 9–15 s: Moltres's silhouette in the heart of the fire, then it bursts from the top in a blaze
 * of light (10.2 s, slow motion), dives, and flies a full banked circle above the shrine
 * (radius 14 m, 18 m high) with a trail of flames; the camera follows it from near the column.
 * 15–20 s: a slow descent, wings open under a rain of embers, the cry at 16 s, the column dies;
 * it lands at 18.4 s; back to the player's camera from 18.5 s.
 */
class MoltresScript(landing: Vector3d, val test: Boolean) : SceneScript {
    val landing = Vector3d(landing)

    /** Slow motion on the burst from the top, the time given back before the circle's end. */
    val slowMotion = SlowMotion(slowAt = 10.36, slowWidth = 0.3, slowDepth = 0.55, catchAt = 11.7, catchWidth = 0.6)
    val burstReal: Double = slowMotion.realOf(BURST)

    override fun action(t: Double): Double = slowMotion.action(t)

    // ---------------------------------------------------------------- Moltres's path

    /** The burst, the dive, a full circle (8 steps of 45°, 11.4 to 14.6 s), the way out, every 0.4 s. */
    private val keys: List<Vector3d> = buildList {
        add(Vector3d(0.0, 25.5, 0.0))
        add(Vector3d(1.0, 30.0, -3.0))
        // The dive, in two steps.
        add(Vector3d(0.5, 25.0, -9.5))
        for (k in 0..8) {
            val phi = Math.toRadians(180.0 + 45.0 * k)
            add(Vector3d(14.0 * sin(phi), 18.0 + 1.2 * sin(2.0 * phi), 14.0 * cos(phi)))
        }
        add(Vector3d(-5.0, 12.0, -3.0))
        add(Vector3d(0.0, 7.0, 1.5))
    }
    private val keyStep = 0.4
    private val flightEnd = BURST + keyStep * (keys.size - 1)

    private val tmpA = Vector3d()
    private val tmpB = Vector3d()
    private val bez1 = Vector3d()
    private val bez2 = Vector3d()

    private val endVelocity: Vector3d = run {
        val a = Vector3d()
        val b = Vector3d()
        CameraMath.catmullRom(keys, 1.0 - 1e-3, a)
        CameraMath.catmullRom(keys, 1.0, b)
        b.sub(a).div(1e-3 * (flightEnd - BURST))
    }

    /** The slow descent after the flight (its drift, then down by a metre). */
    private fun descent(a: Double, out: Vector3d): Vector3d {
        val d = a - flightEnd
        val bob = 0.15 * sin(2.6 * d) * smooth(d / 0.6)
        val sink = -1.2 * smooth(d / (GLIDE - flightEnd))
        return out.set(keys.last()).fma(DRIFT * (1.0 - exp(-d / DRIFT)), endVelocity).add(0.0, bob + sink, 0.0)
    }

    /** Moltres's feet at action time a (whatever its pose). */
    fun birdPos(a: Double, out: Vector3d): Vector3d = when {
        a < SILHOUETTE -> out.set(keys.first()).sub(0.0, 1.5, 0.0)
        a < BURST -> out.set(0.0, 24.0 + 1.5 * smooth((a - SILHOUETTE) / (BURST - SILHOUETTE)), 0.0)
        a < flightEnd -> CameraMath.catmullRom(keys, (a - BURST) / (flightEnd - BURST), out)
        a < GLIDE -> descent(a, out)
        a < LANDING -> {
            descent(GLIDE, tmpA)
            val u = easeInOutCubic((a - GLIDE) / (LANDING - GLIDE))
            bez1.set(tmpA.x, tmpA.y - 0.5, tmpA.z + 0.4)
            bez2.set(landing.x, landing.y + 1.8, landing.z - 0.5)
            bezier(tmpA, bez1, bez2, landing, u, out)
        }
        else -> out.set(landing)
    }

    fun headPos(a: Double, out: Vector3d): Vector3d = birdPos(a, out).add(0.0, HEAD, 0.0)

    private fun headingOf(dx: Double, dz: Double): Double = Math.toDegrees(atan2(-dx, dz))

    private fun wrap(deg: Double): Double {
        var d = deg % 360.0
        if (d > 180.0) d -= 360.0
        if (d < -180.0) d += 360.0
        return d
    }

    private fun lerpAngle(a: Double, b: Double, t: Double): Double = a + wrap(b - a) * t

    private val path = DoubleArray(3)

    private fun motion(a: Double, out: DoubleArray) {
        birdPos(a - 0.03, tmpA)
        birdPos(a + 0.03, tmpB)
        val vx = (tmpB.x - tmpA.x) / 0.06
        val vy = (tmpB.y - tmpA.y) / 0.06
        val vz = (tmpB.z - tmpA.z) / 0.06
        val horizontal = sqrt(vx * vx + vz * vz)
        val heading = if (horizontal > 0.3) headingOf(vx, vz) else 0.0
        birdPos(a + 0.09, tmpA)
        val heading2 = if (horizontal > 0.3) headingOf(tmpA.x - tmpB.x, tmpA.z - tmpB.z) else heading
        out[0] = heading
        out[1] = Math.toDegrees(atan2(-vy, maxOf(horizontal, 0.5)))
        out[2] = wrap(heading2 - heading) / 0.06
    }

    override fun actor(a: Double, out: ActorKey): ActorKey {
        out.scale = 1.0
        out.roll = 0.0
        out.pitch = 0.0
        out.yaw = 0.0
        birdPos(a, out.pos)
        when {
            a < SILHOUETTE -> out.pose = ActorPose.HIDDEN
            a < BURST -> {
                out.pose = ActorPose.HOVER
                // Facing the silhouette's camera.
                out.yaw = -35.0
                out.pitch = -10.0
            }
            a < flightEnd -> {
                out.pose = ActorPose.FLY
                motion(a, path)
                val settle = smooth((a - (flightEnd - 0.5)) / 0.5)
                val start = 1.0 - smooth((a - BURST) / 0.3)
                out.yaw = lerpAngle(lerpAngle(path[0], 0.0, settle), -35.0, start)
                out.pitch = (path[1] * 0.5).coerceIn(-35.0, 35.0) * (1.0 - settle)
                // The banked circle: leaning into the turn.
                out.roll = (-path[2] * 0.3).coerceIn(-45.0, 45.0) * (1.0 - settle) * (1.0 - start)
            }
            a < GLIDE -> {
                out.pose = ActorPose.HOVER
                out.pitch = -6.0 * smooth((a - flightEnd) / 0.5)
            }
            a < LANDING -> {
                val flare = a >= LANDING - 0.6
                out.pose = if (flare) ActorPose.HOVER else ActorPose.FLY
                out.pitch = if (flare) -10.0 * window(a, LANDING - 0.6, LANDING - 0.45, LANDING - 0.15, LANDING) else 6.0 * window(a, GLIDE, GLIDE + 0.3, LANDING - 0.9, LANDING - 0.6)
            }
            else -> {
                out.pose = ActorPose.STAND
                if (test) {
                    if (a >= VANISH_END) out.pose = ActorPose.HIDDEN
                    out.scale = 1.0 - easeInCubic(phase(a, VANISH, VANISH_END))
                }
            }
        }
        return out
    }

    // ---------------------------------------------------------------- the camera

    private val bird = Vector3d()
    private val rig = Vector3d()

    /** From high up, a crane down and in toward the glowing altar. */
    private val glow = Shot("glow", 0.0, 0.0) { t, m, out ->
        val u = easeInOutSine(t / 4.0)
        orbit(0.0, 0.0, lerp(11.0, 4.2, u), lerp(-20.0, -20.0 + 30.0 * m.orbit, u), lerp(9.0, 1.1, u), out.pos)
        out.target.set(0.0, lerp(0.6, 1.6, u), 0.0)
        out.fov = lerp(58.0, 52.0, u)
        out.roll = 0.0
    }

    /** A spiral up along the column, from 2 to 28 m. */
    private val spiral = Shot("spiral", 3.8, 0.5) { t, m, out ->
        val v = phase(t, 4.0, SILHOUETTE)
        val h = lerp(1.5, 28.0, easeInOutSine(v))
        orbit(0.0, 0.0, lerp(5.5, 7.5, v), -20.0 + 30.0 * m.orbit + 400.0 * v * m.orbit, h, out.pos)
        out.target.set(0.0, h + 1.5 * smooth(v / 0.2), 0.0)
        out.fov = lerp(60.0, 70.0, v)
        out.roll = -6.0 * sin(Math.PI * v) * m.roll
    }

    /** The silhouette in the heart of the fire, from above and aside. */
    private val silhouette = Shot("silhouette", SILHOUETTE, 0.0) { t, m, out ->
        val u = easeInOutSine(phase(t, SILHOUETTE, burstReal))
        out.pos.set(lerp(-5.0, -4.2, u), lerp(28.0, 28.5, u), lerp(8.0, 6.8, u))
        birdPos(action(t), bird)
        out.target.set(bird.x, bird.y + 0.8, bird.z)
        out.fov = lerp(46.0, 40.0, u)
        out.roll = 2.0 * m.roll
    }

    /**
     * The burst, then the circle: first a camera beside the top that follows the dive, then
     * one near the column, on Moltres's side, turning with it (the column behind the camera).
     */
    private val flight = Shot("flight", burstReal - 0.05, 0.35) { t, m, out ->
        val a = action(t)
        birdPos(a, bird)
        val near = smooth((a - 10.8) / 0.6)
        // Beside the top.
        val ax = lerp(-6.0, -7.0, smooth((a - BURST) / 0.6))
        val ay = 26.0
        val az = lerp(10.0, 9.0, smooth((a - BURST) / 0.6))
        // Near the column, on its side.
        birdPos(a - 0.12, rig)
        var dx = rig.x
        var dz = rig.z
        val len = sqrt(dx * dx + dz * dz)
        if (len < 0.5) {
            dx = 0.0
            dz = -1.0
        } else {
            dx /= len
            dz /= len
        }
        val bx = dx * 4.0
        val by = 15.5
        val bz = dz * 4.0
        out.pos.set(lerp(ax, bx, near), lerp(ay, by, near), lerp(az, bz, near))
        out.target.set(bird.x, bird.y + 0.4, bird.z)
        out.fov = lerp(60.0, 74.0, near) + 8.0 * pulse(a, BURST, 0.05, 0.45) * m.punch
        out.roll = 8.0 * near * m.roll * (1.0 - smooth((a - (flightEnd - 1.0)) / 0.8))
    }

    /** The final shot: low, in front, a slow push toward its head. */
    private val finale = Shot("final", FINAL_SHOT, 0.0) { t, m, out ->
        val u = easeInOutSine(phase(t, FINAL_SHOT, 18.5))
        out.pos.set(landing.x + lerp(2.6, 1.6, u), landing.y + lerp(0.6, 0.8, u), landing.z + lerp(6.0, 4.3, u))
        headPos(action(t), bird)
        out.target.set(bird)
        out.fov = lerp(54.0, 45.0, u) - 3.0 * pulse(action(t), CRY, 0.05, 0.6) * m.punch
        out.roll = 0.0
    }

    override val director = CameraDirector(listOf(glow, spiral, silhouette, flight, finale))

    // ---------------------------------------------------------------- the look

    override fun cameraWeight(t: Double): Double = smoother(t / 1.0) * (1.0 - smoother((t - 18.5) / 1.5))

    override fun letterbox(t: Double): Double = smooth(t / 0.8) * (1.0 - smooth((t - 18.6) / 1.2))

    override fun title(t: Double): Double = window(t, 15.6, 16.1, 18.1, 18.6)

    override fun shake(a: Double): Double =
        0.05 * column(a) + 0.15 * decay(a, COLUMN, 0.5) + 0.9 * decay(a, BURST, 0.5) + 0.2 * decay(a, CRY, 0.4) + 0.1 * decay(a, LANDING, 0.25)

    override fun flash(a: Double): Double = (0.3 * pulse(a, COLUMN, 0.05, 0.25) + 0.85 * pulse(a, BURST, 0.04, 0.24)).coerceIn(0.0, 1.0)

    /** Warm: red-orange shadows, the fire's light; back to normal as the column dies. */
    override fun grade(t: Double, out: Grade): Grade {
        val clear = smooth((t - 16.5) / 2.0)
        val warm = smooth(t / 2.0) * (1.0 - 0.85 * clear)
        out.r = 1.0 + 0.12 * warm
        out.g = 1.0 + 0.01 * warm
        out.b = 1.0 - 0.16 * warm
        out.exposure = 1.0 - 0.1 * smooth(t / 2.0) * (1.0 - smooth((t - 3.8) / 0.8)) + 0.06 * window(t, 4.0, 6.0, 15.0, 17.0)
        out.saturation = 1.0 + 0.12 * warm
        out.contrast = 1.0 + 0.1 * warm
        out.vignette = 0.38 + 0.2 * window(t, 4.0, 6.0, 15.0, 18.0)
        return out
    }

    // ---------------------------------------------------------------- envelopes (action time)

    /** The glow on the altar. */
    fun altarGlow(a: Double): Double = smooth(a / 1.2) * (1.0 - smooth((a - 16.2) / 1.6))

    /** The column's height (m): it rises, then dies from the top down. */
    fun columnHeight(a: Double): Double = 30.0 * easeOutCubic((a - (COLUMN - 0.2)) / 1.0) * (1.0 - 0.9 * smooth((a - 16.2) / 1.6))

    /** The column's light. */
    fun column(a: Double): Double = window(a, COLUMN - 0.2, COLUMN + 0.6, 16.2, 17.8)

    /** A flare at the column's top when Moltres bursts out. */
    fun topFlare(a: Double): Double = pulse(a, BURST, 0.05, 0.6)

    /** Heat haze (MEDIUM and above). */
    fun heat(a: Double): Double = 0.6 * window(a, 0.2, 1.5, 16.0, 18.0)

    /** The light behind its silhouette. */
    fun backlight(a: Double): Double = window(a, SILHOUETTE - 0.2, SILHOUETTE + 0.3, BURST - 0.1, BURST + 0.2)

    /** Its trail of flames. */
    fun trail(a: Double): Double = window(a, BURST, BURST + 0.2, flightEnd - 0.4, flightEnd + 0.2)

    /** The rain of embers of the final shot. */
    fun emberRain(a: Double): Double = window(a, 14.8, 15.4, 17.6, 18.6)

    /** Rings of fire climbing the column: ring k's progress (0 to 1, negative before it starts). */
    fun ring(k: Int, a: Double): Double = (a - (COLUMN + 0.4 + 0.8 * k)) / 2.4

    val rings = 13

    override fun envelopes(a: Double): DoubleArray = doubleArrayOf(
        altarGlow(a), column(a), topFlare(a), heat(a) / 0.6, backlight(a), trail(a), emberRain(a), columnHeight(a) / 30.0,
    )

    /** The scene time when the flight ends (the descent begins), for the scene. */
    val descentStart: Double get() = flightEnd

    // ---------------------------------------------------------------- cues

    override val cues: List<Cue> = buildList {
        if (test) add(Cue(0.0, Cue.Kind.SOUND, "generations_core:generations_core.item.lugia_shrine_song", volume = 1.0f))
        add(Cue(0.1, Cue.Kind.SOUND, "minecraft:item.firecharge.use", dy = 1.0, volume = 1.4f, pitch = 0.6f))
        for (at in doubleArrayOf(0.5, 2.0, 3.0)) add(Cue(at, Cue.Kind.SOUND, "minecraft:block.fire.ambient", dy = 1.0, volume = 1.5f, pitch = 0.8f))
        for ((i, at) in doubleArrayOf(1.2, 1.9, 2.6).withIndex()) add(Cue(at, Cue.Kind.SOUND, "minecraft:block.lava.pop", dx = (i - 1) * 1.2, dy = 0.5, volume = 1.2f, pitch = 0.8f + 0.1f * i))
        add(Cue(COLUMN, Cue.Kind.EVENT, "column"))
        add(Cue(COLUMN, Cue.Kind.SOUND, "minecraft:entity.blaze.shoot", dy = 2.0, volume = 2.0f, pitch = 0.5f))
        add(Cue(COLUMN, Cue.Kind.SOUND, "minecraft:entity.ghast.shoot", dy = 4.0, volume = 1.6f, pitch = 0.6f))
        add(Cue(COLUMN, Cue.Kind.SOUND, "minecraft:item.firecharge.use", dy = 2.0, volume = 2.0f, pitch = 0.5f))
        for ((i, at) in doubleArrayOf(4.5, 6.0, 7.5).withIndex()) add(Cue(at, Cue.Kind.SOUND, "minecraft:entity.blaze.burn", dy = 10.0 + 6.0 * i, volume = 1.4f, pitch = 0.7f))
        for ((i, at) in doubleArrayOf(5.2, 6.8).withIndex()) add(Cue(at, Cue.Kind.SOUND, "minecraft:block.fire.ambient", dy = 12.0 + 8.0 * i, volume = 1.6f, pitch = 0.7f))
        add(Cue(8.4, Cue.Kind.SOUND, "minecraft:block.respawn_anchor.charge", dy = 24.0, volume = 1.6f, pitch = 0.6f))
        add(Cue(SILHOUETTE, Cue.Kind.SOUND, "minecraft:entity.blaze.ambient", "actor", volume = 1.6f, pitch = 0.5f))
        add(Cue(SILHOUETTE + 0.1, Cue.Kind.SOUND, "minecraft:entity.ender_dragon.flap", "actor", volume = 1.4f, pitch = 0.9f))
        add(Cue(BURST, Cue.Kind.EVENT, "burst"))
        add(Cue(BURST, Cue.Kind.SOUND, "minecraft:entity.generic.explode", "actor", volume = 2.5f, pitch = 0.9f))
        add(Cue(BURST, Cue.Kind.SOUND, "minecraft:entity.ghast.shoot", "actor", volume = 2.0f, pitch = 1.2f))
        add(Cue(BURST, Cue.Kind.SOUND, "minecraft:entity.warden.sonic_boom", "actor", volume = 1.4f, pitch = 1.4f))
        add(Cue(BURST, Cue.Kind.SOUND, "minecraft:item.firecharge.use", "actor", volume = 2.0f, pitch = 1.0f))
        var flap = 10.6
        while (flap < 14.8) {
            add(Cue(flap, Cue.Kind.SOUND, "minecraft:entity.ender_dragon.flap", "actor", volume = 1.6f, pitch = 1.0f))
            flap += 0.45
        }
        for (at in doubleArrayOf(11.4, 12.6, 13.8)) add(Cue(at, Cue.Kind.SOUND, "minecraft:entity.blaze.shoot", "actor", volume = 1.2f, pitch = 0.8f))
        add(Cue(CRY, Cue.Kind.CRY, "cobblemon:pokemon.moltres.cry", "actor", volume = 2.5f))
        add(Cue(CRY, Cue.Kind.SOUND, "minecraft:entity.blaze.shoot", "actor", volume = 1.0f, pitch = 0.6f))
        add(Cue(CRY, Cue.Kind.EVENT, "breath"))
        add(Cue(16.4, Cue.Kind.SOUND, "minecraft:block.fire.extinguish", dy = 4.0, volume = 1.8f, pitch = 0.6f))
        add(Cue(GLIDE, Cue.Kind.SOUND, "minecraft:entity.ender_dragon.flap", "actor", volume = 1.5f, pitch = 1.0f))
        add(Cue(LANDING, Cue.Kind.EVENT, "landing"))
        add(Cue(LANDING, Cue.Kind.SOUND, "minecraft:block.lava.extinguish", "actor", volume = 1.2f, pitch = 1.0f))
        add(Cue(LANDING, Cue.Kind.SOUND, "minecraft:entity.iron_golem.step", "actor", volume = 1.3f, pitch = 1.2f))
        if (test) {
            add(Cue(VANISH - 0.05, Cue.Kind.EVENT, "vanish"))
            add(Cue(VANISH - 0.05, Cue.Kind.SOUND, "minecraft:block.fire.extinguish", "actor", volume = 1.5f, pitch = 1.4f))
            add(Cue(VANISH, Cue.Kind.SOUND, "minecraft:item.firecharge.use", "actor", volume = 1.2f, pitch = 1.5f))
        }
    }.sortedBy { it.time }

    companion object {
        const val COLUMN = 4.0
        const val SILHOUETTE = 9.0
        const val BURST = 10.2
        const val FINAL_SHOT = 15.0
        const val DRIFT = 0.5
        const val CRY = 16.0
        const val GLIDE = 17.4
        const val LANDING = 18.4
        const val VANISH = 19.25
        const val VANISH_END = 19.5
        const val HEAD = 1.5
    }
}
