package generations.gg.generations.core.generationscore.common.client.cinematic.scenes

import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraDirector
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.bezier
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.decay
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.easeInCubic
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.easeInOutCubic
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.easeInOutSine
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.easeInQuad
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
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Zapdos — Judgement of the Thunder (CINEMATIQUES-PLAN.md § 5), 20 s, pure.
 *
 * 0–4 s: sparks and arcs on the altar, the sky darkens fast; a quick travelling from the
 * altar up to the sky, with a little roll.
 * 4–9 s: an accelerating orbit (20 to 120°/s) around a growing sphere of energy crossed by
 * arcs; storm clouds lit from inside; ten bolts strike the ground (thunder delayed by the
 * distance); radial blur in HIGH.
 * 9–15 s: a giant bolt across the sky at 9 s, the sphere implodes then bursts and Zapdos comes
 * out (9.65 s); a fast chase behind and above it; at 12.5 s its wings open at once ("special"),
 * an electric ring wave, arcs on its body and on the ground, a strong shake, in slow motion.
 * 15–20 s: it steadies above the shrine; at 16 s a bolt behind it (backlight); the cry at
 * 16.3 s; the energy dies; it lands at 18.4 s; back to the player's camera from 18.5 s.
 */
class ZapdosScript(landing: Vector3d, val test: Boolean) : SceneScript {
    val landing = Vector3d(landing)

    /** A short slow motion on the wings' opening, the time given back before 14 s. */
    val slowMotion = SlowMotion(slowAt = 12.66, slowWidth = 0.25, slowDepth = 0.55, catchAt = 13.55, catchWidth = 0.3)
    val spreadReal: Double = slowMotion.realOf(SPREAD)

    override fun action(t: Double): Double = slowMotion.action(t)

    /** The ground strikes: action time, local x and z (away from the front, where the player stands). */
    val strikes: List<DoubleArray> = listOf(
        doubleArrayOf(4.3, -9.0, -6.0), doubleArrayOf(4.8, 11.0, -9.0), doubleArrayOf(5.2, -14.0, 4.0), doubleArrayOf(5.9, 6.0, -15.0),
        doubleArrayOf(6.3, 15.0, 3.0), doubleArrayOf(6.8, -7.0, -14.0), doubleArrayOf(7.2, -16.0, -3.0), doubleArrayOf(7.7, 12.0, 11.0),
        doubleArrayOf(8.1, -12.0, 12.0), doubleArrayOf(8.5, 2.0, -18.0),
    )

    // ---------------------------------------------------------------- Zapdos's path

    private val keys = listOf(
        Vector3d(0.0, 9.0, 0.0), Vector3d(7.0, 13.5, -7.0), Vector3d(-2.0, 18.0, -15.0), Vector3d(-13.0, 15.5, -6.0),
        Vector3d(-11.0, 12.0, 8.0), Vector3d(-1.0, 9.5, 10.0), Vector3d(0.0, 6.5, 1.5),
    )
    private val tmpA = Vector3d()
    private val tmpB = Vector3d()
    private val bez1 = Vector3d()
    private val bez2 = Vector3d()

    /** Its velocity at the end of the chase (m per action second), to steady itself smoothly. */
    private val endVelocity: Vector3d = run {
        val a = Vector3d()
        val b = Vector3d()
        CameraMath.catmullRom(keys, 1.0 - 1e-3, a)
        CameraMath.catmullRom(keys, 1.0, b)
        b.sub(a).div(1e-3 * (HOVER - BURST))
    }

    /** Where it steadies above the shrine (its drift after the chase). */
    private val hoverPoint: Vector3d = Vector3d(keys.last()).fma(HOVER_TAU, endVelocity)

    private fun hover(a: Double, out: Vector3d): Vector3d {
        val d = a - HOVER
        val bob = 0.12 * sin(3.0 * d) * smooth(d / 0.6)
        return out.set(keys.last()).fma(HOVER_TAU * (1.0 - exp(-d / HOVER_TAU)), endVelocity).add(0.0, bob, 0.0)
    }

    /** Zapdos's feet at action time a (whatever its pose). */
    fun birdPos(a: Double, out: Vector3d): Vector3d = when {
        a < BURST -> out.set(keys.first())
        a < HOVER -> CameraMath.catmullRom(keys, (a - BURST) / (HOVER - BURST), out)
        a < GLIDE -> hover(a, out)
        a < LANDING -> {
            hover(GLIDE, tmpA)
            val u = easeInOutCubic((a - GLIDE) / (LANDING - GLIDE))
            bez1.set(tmpA.x, tmpA.y - 0.6, tmpA.z + 0.4)
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

    /** Heading, climb and turn of the path at a (degrees, degrees, degrees per second). */
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
        out[3] = sqrt(vx * vx + vy * vy + vz * vz)
    }

    private val path = DoubleArray(4)

    override fun actor(a: Double, out: ActorKey): ActorKey {
        out.scale = 1.0
        out.roll = 0.0
        out.pitch = 0.0
        out.yaw = 0.0
        birdPos(a, out.pos)
        when {
            a < BURST -> out.pose = ActorPose.HIDDEN
            a < HOVER -> {
                out.pose = ActorPose.FLY
                motion(a, path)
                val settle = smooth((a - (HOVER - 0.5)) / 0.5)
                out.yaw = lerpAngle(path[0], 0.0, settle)
                out.pitch = (path[1] * 0.5).coerceIn(-25.0, 30.0) * (1.0 - settle)
                out.roll = (-path[2] * 0.25).coerceIn(-40.0, 40.0) * (1.0 - settle) * smooth((a - BURST) / 0.3)
            }
            a < GLIDE -> {
                out.pose = ActorPose.HOVER
                out.pitch = -6.0 * smooth((a - HOVER) / 0.5)
            }
            a < LANDING -> {
                val flare = a >= LANDING - 0.6
                out.pose = if (flare) ActorPose.HOVER else ActorPose.FLY
                out.pitch = if (flare) -10.0 * window(a, LANDING - 0.6, LANDING - 0.45, LANDING - 0.15, LANDING) else 7.0 * window(a, GLIDE, GLIDE + 0.3, LANDING - 0.9, LANDING - 0.6)
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
    private val behind = Vector3d()

    /** A quick travelling from the altar up to the sky, a little roll. */
    private val ascent = Shot("ascent", 0.0, 0.0) { t, m, out ->
        val u = easeInOutCubic(t / 4.0)
        orbit(0.0, 0.0, lerp(2.2, 10.0, u), 25.0 + 30.0 * u * m.orbit, lerp(0.5, 11.0, Math.pow(u, 1.3)), out.pos)
        out.target.set(0.0, lerp(1.2, SPHERE_Y + 0.5, u), 0.0)
        out.fov = lerp(55.0, 62.0, u)
        out.roll = lerp(-4.0, 3.0, u) * m.roll
    }

    /** An orbit around the sphere, faster and faster (20 to 120°/s). */
    private val storm = Shot("storm", 3.8, 0.5) { t, m, out ->
        val v = phase(t, 4.0, 9.0)
        val angle = 25.0 + 30.0 * m.orbit + (100.0 * v + 166.7 * v * v * v) * m.orbit
        orbit(0.0, 0.0, lerp(10.0, 11.5, v), angle, lerp(11.0, 8.0, v), out.pos)
        out.target.set(0.0, SPHERE_Y, 0.0)
        out.fov = lerp(62.0, 70.0, v)
        out.roll = -5.0 * v * m.roll
    }

    /** The giant bolt and the sphere's implosion, from below. */
    private val implosion = Shot("implosion", GIANT_BOLT, 0.0) { t, m, out ->
        val u = easeInOutSine(phase(t, GIANT_BOLT, 9.9))
        out.pos.set(lerp(-6.0, -5.2, u), lerp(3.5, 3.8, u), lerp(14.0, 12.5, u))
        out.target.set(0.0, SPHERE_Y + 3.0, 0.0)
        out.fov = lerp(66.0, 60.0, u) + 6.0 * pulse(action(t), BURST, 0.05, 0.4) * m.punch
        out.roll = 2.0 * m.roll
    }

    /** A fast chase behind and above Zapdos. */
    private val chase = Shot("chase", 9.9, 0.6) { t, m, out ->
        val a = action(t)
        birdPos(a - 0.15, behind)
        birdPos(a - 0.4, tmpA)
        var dx = behind.x - tmpA.x
        var dy = behind.y - tmpA.y
        var dz = behind.z - tmpA.z
        val len = sqrt(dx * dx + dy * dy + dz * dz)
        if (len < 0.05) {
            dx = 0.0; dy = 0.0; dz = -1.0
        } else {
            dx /= len; dy /= len; dz /= len
        }
        out.pos.set(behind.x - dx * 7.0, maxOf(2.0, behind.y - dy * 7.0 + 3.2), behind.z - dz * 7.0)
        birdPos(a, bird)
        out.target.set(bird.x + dx * 3.0, bird.y + 0.5 + dy * 3.0, bird.z + dz * 3.0)
        motion(a, path)
        out.fov = 70.0 + 8.0 * pulse(a, SPREAD, 0.05, 0.5) * m.punch
        out.roll = (-path[2] * 0.06).coerceIn(-12.0, 12.0) * m.roll * (1.0 - smooth((t - 14.4) / 0.6))
    }

    /** The final shot: low, in front, a slow push toward its head. */
    private val finale = Shot("final", FINAL_SHOT, 0.0) { t, m, out ->
        val u = easeInOutSine(phase(t, FINAL_SHOT, 18.5))
        out.pos.set(landing.x + lerp(2.4, 1.5, u), landing.y + lerp(0.6, 0.8, u), landing.z + lerp(6.2, 4.4, u))
        headPos(action(t), bird)
        out.target.set(bird)
        out.fov = lerp(52.0, 44.0, u) - 3.0 * pulse(action(t), CRY, 0.05, 0.6) * m.punch
        out.roll = 0.0
    }

    override val director = CameraDirector(listOf(ascent, storm, implosion, chase, finale))

    // ---------------------------------------------------------------- the look

    override fun cameraWeight(t: Double): Double = smoother(t / 1.0) * (1.0 - smoother((t - 18.5) / 1.5))

    override fun letterbox(t: Double): Double = smooth(t / 0.8) * (1.0 - smooth((t - 18.6) / 1.2))

    override fun title(t: Double): Double = window(t, 15.8, 16.3, 18.1, 18.6)

    override fun shake(a: Double): Double {
        var s = 0.04 * sphereLight(a).coerceAtMost(1.0) + 0.5 * decay(a, GIANT_BOLT, 0.4) + 0.9 * decay(a, BURST, 0.5) +
            1.0 * decay(a, SPREAD, 0.45) + 0.35 * decay(a, BACK_BOLT, 0.3) + 0.2 * decay(a, CRY, 0.4) + 0.1 * decay(a, LANDING, 0.25)
        for (strike in strikes) s += 0.3 * decay(a, strike[0], 0.25)
        return s
    }

    override fun flash(a: Double): Double {
        var f = 0.6 * pulse(a, GIANT_BOLT, 0.02, 0.18) + 0.85 * pulse(a, BURST, 0.03, 0.22) + 0.5 * pulse(a, SPREAD, 0.03, 0.2) + 0.45 * pulse(a, BACK_BOLT, 0.02, 0.18)
        for (strike in strikes) f += 0.3 * pulse(a, strike[0], 0.02, 0.12)
        return f.coerceIn(0.0, 1.0)
    }

    /** The sky darkens fast, a cold violet storm, back to normal as the energy dies. */
    override fun grade(t: Double, out: Grade): Grade {
        val dark = smooth(t / 2.5) * (1.0 - smooth((t - 16.5) / 2.0))
        out.r = 1.0 - 0.08 * dark
        out.g = 1.0 - 0.05 * dark
        out.b = 1.0 + 0.08 * dark
        out.exposure = 1.0 - 0.36 * dark
        out.saturation = 1.0 - 0.3 * dark
        out.contrast = 1.0 + 0.15 * dark
        out.vignette = 0.4 + 0.2 * window(t, 4.0, 6.0, 15.0, 18.0)
        return out
    }

    // ---------------------------------------------------------------- envelopes (action time)

    /** Sparks and arcs on the altar. */
    fun sparks(a: Double): Double = window(a, 0.0, 0.3, 3.6, 4.6)

    fun cloudRadius(a: Double): Double = 40.0 * easeOutCubic((a - 0.4) / 3.0) + 12.0 * smooth((a - 16.5) / 2.0)

    fun cloudOpacity(a: Double): Double = 0.85 * smooth((a - 0.4) / 1.5) * (1.0 - smooth((a - 16.5) / 2.2))

    /** The sphere of energy: radius (m), then its implosion; gone after the burst. */
    fun sphereRadius(a: Double): Double = when {
        a < 3.6 -> 0.0
        a < GIANT_BOLT -> 0.4 + 3.1 * easeInQuad((a - 3.6) / 5.4)
        a < BURST -> lerp(3.5, 0.35, easeInCubic((a - GIANT_BOLT) / (BURST - GIANT_BOLT)))
        else -> 0.0
    }

    /** Its brightness (up to 2.5 as it implodes). */
    fun sphereLight(a: Double): Double = when {
        a < 3.6 -> 0.0
        a < GIANT_BOLT -> smooth((a - 3.6) / 0.8)
        a < BURST -> 1.0 + 1.5 * phase(a, GIANT_BOLT, BURST)
        else -> 0.0
    }

    /** The arc from the sphere down to the altar. */
    fun tendril(a: Double): Double = window(a, 4.2, 4.6, 8.8, 9.0)

    /** The giant bolt across the sky: on, off, on again (0 or 1, then fading). */
    fun giantBolt(a: Double): Double {
        val d = a - GIANT_BOLT
        if (d < 0.0 || d > 0.45) return 0.0
        return when {
            d < 0.08 -> 1.0
            d < 0.12 -> 0.15
            d < 0.22 -> 1.0
            else -> 1.0 - smooth((d - 0.22) / 0.23)
        }
    }

    /** A ground strike's bolt (seconds since it struck). */
    fun strikeBolt(d: Double): Double = when {
        d < 0.0 || d > 0.3 -> 0.0
        d < 0.06 -> 1.0
        d < 0.09 -> 0.2
        d < 0.15 -> 1.0
        else -> 1.0 - smooth((d - 0.15) / 0.15)
    }

    /** The ring of lightning when the wings open: radius (m) and strength. */
    fun spreadRadius(a: Double): Double = 18.0 * easeOutCubic((a - SPREAD) / 0.6)

    fun spreadRing(a: Double): Double = if (a < SPREAD) 0.0 else 1.0 - smooth((a - SPREAD) / 0.7)

    /** Arcs on its body. */
    fun bodyArcs(a: Double): Double = window(a, SPREAD, SPREAD + 0.05, 13.4, 13.8) + window(a, CRY, CRY + 0.05, CRY + 0.6, CRY + 0.9)

    /** The bolt behind it in the final shot (backlight). */
    fun backBolt(a: Double): Double {
        val d = a - BACK_BOLT
        if (d < 0.0 || d > 0.6) return 0.0
        return when {
            d < 0.1 -> 1.0
            d < 0.14 -> 0.2
            d < 0.26 -> 1.0
            else -> 1.0 - smooth((d - 0.26) / 0.34)
        }
    }

    /** The energy around Zapdos, dying after the cry. */
    fun energy(a: Double): Double = smooth((a - BURST) / 0.3) * (1.0 - smooth((a - 16.6) / 1.4))

    override fun envelopes(a: Double): DoubleArray = doubleArrayOf(
        sparks(a), cloudOpacity(a), sphereLight(a) / 2.5, tendril(a), giantBolt(a), spreadRing(a),
        bodyArcs(a).coerceAtMost(1.0), backBolt(a), energy(a),
    )

    // ---------------------------------------------------------------- cues

    override val cues: List<Cue> = buildList {
        if (test) add(Cue(0.0, Cue.Kind.SOUND, "generations_core:generations_core.item.lugia_shrine_song", volume = 1.0f))
        add(Cue(0.1, Cue.Kind.SOUND, "minecraft:block.beacon.activate", dy = 1.0, volume = 1.4f, pitch = 1.4f))
        listOf(0.4, 0.9, 1.3, 1.8, 2.2, 2.9, 3.4).forEachIndexed { i, at ->
            add(Cue(at, Cue.Kind.SOUND, "minecraft:block.copper_bulb.turn_on", dx = (i % 3 - 1) * 0.8, dy = 1.0, dz = ((i + 1) % 3 - 1) * 0.8, volume = 0.9f, pitch = 1.4f + 0.1f * (i % 6)))
        }
        add(Cue(1.0, Cue.Kind.SOUND, "minecraft:entity.lightning_bolt.thunder", dx = 20.0, dy = 26.0, volume = 1.2f, pitch = 0.6f))
        add(Cue(2.6, Cue.Kind.SOUND, "minecraft:entity.lightning_bolt.thunder", dx = -25.0, dy = 26.0, dz = -10.0, volume = 1.4f, pitch = 0.7f))
        add(Cue(3.6, Cue.Kind.SOUND, "minecraft:block.respawn_anchor.charge", dy = SPHERE_Y, volume = 1.5f, pitch = 0.7f))
        add(Cue(4.0, Cue.Kind.SOUND, "minecraft:block.beacon.ambient", dy = SPHERE_Y, volume = 1.6f, pitch = 0.6f))
        add(Cue(6.0, Cue.Kind.SOUND, "minecraft:block.respawn_anchor.charge", dy = SPHERE_Y, volume = 1.5f, pitch = 0.9f))
        add(Cue(7.0, Cue.Kind.SOUND, "minecraft:block.beacon.ambient", dy = SPHERE_Y, volume = 1.6f, pitch = 0.8f))
        add(Cue(8.0, Cue.Kind.SOUND, "minecraft:block.respawn_anchor.charge", dy = SPHERE_Y, volume = 1.6f, pitch = 1.1f))
        strikes.forEachIndexed { i, s -> add(Cue(s[0], Cue.Kind.EVENT, "strike:$i")) }
        add(Cue(GIANT_BOLT, Cue.Kind.EVENT, "giant"))
        add(Cue(GIANT_BOLT, Cue.Kind.SOUND, "minecraft:block.beacon.deactivate", dy = SPHERE_Y, volume = 1.5f, pitch = 0.6f))
        add(Cue(9.3, Cue.Kind.SOUND, "minecraft:block.respawn_anchor.deplete", dy = SPHERE_Y, volume = 1.6f, pitch = 1.5f))
        add(Cue(BURST, Cue.Kind.EVENT, "burst"))
        add(Cue(BURST, Cue.Kind.SOUND, "minecraft:entity.generic.explode", dy = SPHERE_Y, volume = 2.0f, pitch = 1.4f))
        add(Cue(BURST, Cue.Kind.SOUND, "minecraft:entity.lightning_bolt.thunder", dy = SPHERE_Y, volume = 2.5f, pitch = 1.2f))
        add(Cue(BURST, Cue.Kind.SOUND, "minecraft:entity.warden.sonic_boom", dy = SPHERE_Y, volume = 1.6f, pitch = 1.6f))
        var flap = 10.0
        while (flap < 14.8) {
            if (kotlin.math.abs(flap - SPREAD) > 0.2) add(Cue(flap, Cue.Kind.SOUND, "minecraft:entity.ender_dragon.flap", "actor", volume = 1.4f, pitch = 1.3f))
            flap += 0.5
        }
        add(Cue(11.0, Cue.Kind.SOUND, "minecraft:entity.breeze.wind_burst", "actor", volume = 1.2f, pitch = 1.4f))
        add(Cue(SPREAD, Cue.Kind.ANIMATE, "special"))
        add(Cue(SPREAD, Cue.Kind.EVENT, "spread"))
        add(Cue(SPREAD, Cue.Kind.SOUND, "minecraft:item.trident.thunder", "actor", volume = 2.2f, pitch = 1.0f))
        add(Cue(SPREAD, Cue.Kind.SOUND, "minecraft:entity.lightning_bolt.impact", "actor", volume = 2.0f, pitch = 0.9f))
        add(Cue(SPREAD, Cue.Kind.SOUND, "minecraft:entity.warden.sonic_boom", "actor", volume = 1.2f, pitch = 1.9f))
        add(Cue(BACK_BOLT, Cue.Kind.EVENT, "backbolt"))
        add(Cue(CRY, Cue.Kind.CRY, "cobblemon:pokemon.zapdos.cry", "actor", volume = 2.5f))
        add(Cue(CRY, Cue.Kind.SOUND, "minecraft:item.trident.thunder", "actor", volume = 0.8f, pitch = 1.5f))
        add(Cue(CRY, Cue.Kind.EVENT, "crackle"))
        add(Cue(16.6, Cue.Kind.SOUND, "minecraft:block.beacon.deactivate", "actor", volume = 1.4f, pitch = 1.2f))
        add(Cue(GLIDE + 0.4, Cue.Kind.SOUND, "minecraft:entity.ender_dragon.flap", "actor", volume = 1.5f, pitch = 1.1f))
        add(Cue(LANDING, Cue.Kind.EVENT, "landing"))
        add(Cue(LANDING, Cue.Kind.SOUND, "minecraft:entity.iron_golem.step", "actor", volume = 1.3f, pitch = 1.3f))
        add(Cue(LANDING, Cue.Kind.SOUND, "minecraft:block.copper_bulb.turn_off", "actor", volume = 1.2f, pitch = 0.7f))
        if (test) {
            add(Cue(VANISH - 0.05, Cue.Kind.EVENT, "vanish"))
            add(Cue(VANISH - 0.05, Cue.Kind.SOUND, "minecraft:entity.lightning_bolt.impact", "actor", volume = 1.2f, pitch = 1.8f))
            add(Cue(VANISH, Cue.Kind.SOUND, "minecraft:block.copper_bulb.turn_off", "actor", volume = 1.2f, pitch = 1.6f))
        }
    }.sortedBy { it.time }

    companion object {
        const val SPHERE_Y = 9.0
        const val GIANT_BOLT = 9.0
        const val BURST = 9.65
        const val SPREAD = 12.5
        const val FINAL_SHOT = 15.0
        const val HOVER = 15.2
        const val HOVER_TAU = 0.5
        const val BACK_BOLT = 16.0
        const val CRY = 16.3
        const val GLIDE = 17.2
        const val LANDING = 18.4
        const val VANISH = 19.25
        const val VANISH_END = 19.5
        const val HEAD = 1.4
    }
}
