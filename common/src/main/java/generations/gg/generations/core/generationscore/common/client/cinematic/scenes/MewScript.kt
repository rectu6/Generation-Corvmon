package generations.gg.generations.core.generationscore.common.client.cinematic.scenes

import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraDirector
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.bezier
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.decay
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.easeInCubic
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.easeInOutCubic
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

/**
 * Mew — the end of the game (MEW-PLAN.md § 10), 24 s, pure (tested off-game). Its frame: the
 * player's feet, +z where they looked when they won.
 *
 * 0–5 s: Mew stops in front of the player, tilts its head, a soft energy; a slow orbit round it.
 * 5–10 s: it flies off, quick and playful, pink and blue trails, a joyful circle round the player.
 * 10–16.5 s: a psychic whirlwind, motes through the forest; six illusions appear round it and
 * converge, the camera orbiting.
 * 16.5–21 s: the fusion, a bright sphere of energy; its cry at 18 s with a psychic wave.
 * 21–24 s: a gentle descent to the landing point, the energy fading, the camera given back.
 *
 * The illusions are more actors of the scene (illusion(i, a, out)).
 */
class MewScript(landing: Vector3d, val test: Boolean) : SceneScript {
    val landing = Vector3d(landing)

    override fun action(t: Double): Double = t

    // ---------------------------------------------------------------- Mew's path

    /** Where it waits in front of the player, then the heart of the whirlwind. */
    val hover = Vector3d(0.0, 1.4, 3.0)
    val centre = Vector3d(0.0, 2.6, 4.6)
    private val tmp = Vector3d()
    private val c1 = Vector3d()
    private val c2 = Vector3d()

    private fun bob(a: Double, amount: Double) = amount * sin(a * 2.2)

    private fun circleAngle(a: Double): Double = 2.0 * PI * 1.25 * easeInOutSine(phase(a, CIRCLE_START, CIRCLE_END))

    private fun circle(a: Double, out: Vector3d): Vector3d {
        val angle = circleAngle(a)
        val r = 3.2
        return out.set(sin(angle) * r, 2.0 + 0.45 * sin(3.0 * angle) + 0.1 * sin(a * 3.0), cos(angle) * r)
    }

    /** Mew's feet at action time a. */
    fun mewPos(a: Double, out: Vector3d): Vector3d = when {
        a < FLIGHT -> out.set(hover.x + 0.05 * sin(a * 0.8), hover.y + bob(a, 0.08), hover.z)
        a < CIRCLE_START -> {
            // Up from its waiting place to the circle's start.
            circle(CIRCLE_START, tmp)
            val u = easeInOutSine(phase(a, FLIGHT, CIRCLE_START))
            out.set(hover.x, hover.y + bob(FLIGHT, 0.08), hover.z).lerp(tmp, u)
        }
        a < CIRCLE_END -> circle(a, out)
        a < WHIRL_IN -> {
            circle(CIRCLE_END, tmp)
            c1.set(tmp.x * 0.6, tmp.y + 1.2, tmp.z + 0.8)
            c2.set(centre.x, centre.y + 0.8, centre.z - 1.0)
            bezier(tmp, c1, c2, centre, easeInOutCubic(phase(a, CIRCLE_END, WHIRL_IN)), out)
        }
        a < DESCEND -> out.set(centre.x + 0.06 * sin(a * 0.9), centre.y + bob(a, 0.1) * window(a, WHIRL_IN, WHIRL_IN + 0.6, DESCEND - 0.6, DESCEND), centre.z)
        a < LANDING -> {
            c1.set(centre.x, centre.y - 0.3, centre.z - 0.4)
            c2.set(landing.x, landing.y + 0.9, landing.z + 0.3)
            bezier(centre, c1, c2, landing, easeInOutCubic(phase(a, DESCEND, LANDING)), out)
        }
        else -> out.set(landing)
    }

    fun headPos(a: Double, out: Vector3d): Vector3d = mewPos(a, out).add(0.0, HEAD, 0.0)

    /** A yaw that faces from a point toward another (0 = local +z). */
    private fun yawTo(dx: Double, dz: Double): Double = Math.toDegrees(atan2(-dx, dz))

    override fun actor(a: Double, out: ActorKey): ActorKey {
        mewPos(a, out.pos)
        out.scale = 1.0
        out.pitch = 0.0
        out.roll = 0.0
        out.pose = ActorPose.HOVER
        // Facing the player (the origin), except along its circle where it faces its way.
        val face = yawTo(-out.pos.x, -out.pos.z)
        out.yaw = when {
            a in CIRCLE_START..CIRCLE_END -> {
                val angle = circleAngle(a)
                val along = Math.toDegrees(-angle) + 90.0 - 180.0
                lerpAngle(face, along, window(a, CIRCLE_START, CIRCLE_START + 0.5, CIRCLE_END - 0.5, CIRCLE_END))
            }
            // A spin as it enters the whirlwind.
            a in CIRCLE_END..FUSION -> face + 360.0 * easeInOutSine(phase(a, WHIRL_IN - 0.4, FUSION - 0.5))
            else -> face
        }
        // Its head tilted, curious (0.8–4 s), and leaning into its turns.
        out.roll = 16.0 * window(a, 0.8, 1.4, 3.4, 4.2) + 14.0 * sin(circleAngle(a) * 2.0) * window(a, CIRCLE_START, CIRCLE_START + 0.6, CIRCLE_END - 0.6, CIRCLE_END)
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

    /** The illusions, more actors: six round the whirlwind, converging into Mew. */
    fun illusion(i: Int, a: Double, out: ActorKey): ActorKey {
        out.pose = ActorPose.HIDDEN
        out.scale = 0.0
        out.pitch = 0.0
        out.roll = 0.0
        if (a < ILLUSIONS - 0.01 || a >= FUSION) return out
        val angle = 2.0 * PI * i / ILLUSION_COUNT + 1.2 * (a - ILLUSIONS)
        val radius = 7.5 * (1.0 - easeInCubic(phase(a, CONVERGE, FUSION)))
        out.pos.set(centre.x + sin(angle) * radius, centre.y + 0.9 * sin(angle * 2.0 + a) * (radius / 7.5), centre.z + cos(angle) * radius)
        val appear = smooth((a - ILLUSIONS - 0.15 * i) / 0.45)
        out.scale = appear * (1.0 - 0.5 * phase(a, FUSION - 1.0, FUSION))
        if (out.scale <= 0.01) return out
        out.yaw = yawTo(centre.x - out.pos.x, centre.z - out.pos.z) + 90.0
        out.roll = 10.0 * sin(a * 2.0 + i)
        out.pose = ActorPose.HOVER
        return out
    }

    // ---------------------------------------------------------------- the camera

    private val m = Vector3d()

    /** A slow orbit round Mew as it waits. */
    private val meet = Shot("meet", 0.0, 0.0) { t, motion, out ->
        val u = easeInOutSine(phase(t, 0.0, 5.2))
        headPos(t, m)
        orbit(hover.x, hover.z, lerp(3.0, 2.5, u), -150.0 + 40.0 * u * motion.orbit, lerp(1.5, 1.7, u), out.pos)
        out.target.set(m.x, m.y - 0.1, m.z)
        out.fov = lerp(50.0, 42.0, u)
        out.roll = 1.5 * u * motion.roll
    }

    /** Wide behind the player: Mew's joyful circle round them. */
    private val play = Shot("play", FLIGHT, 0.8) { t, motion, out ->
        val v = phase(t, FLIGHT, CIRCLE_END)
        mewPos(t, m)
        val angle = 200.0 + 70.0 * easeInOutSine(v) * motion.orbit
        orbit(0.0, 0.0, lerp(6.4, 5.6, v), angle, lerp(2.6, 3.0, v), out.pos)
        out.target.set(m.x * 0.6, 1.4 + 0.5 * m.y * 0.3, m.z * 0.6)
        out.fov = 62.0
        out.roll = -2.0 * sin(v * PI) * motion.roll
    }

    /** An orbit round the whirlwind, the illusions converging. */
    private val whirl = Shot("whirl", CIRCLE_END, 0.8) { t, motion, out ->
        val v = phase(t, CIRCLE_END, FUSION_SHOT)
        val angle = 140.0 + 120.0 * easeInOutSine(v) * motion.orbit
        orbit(centre.x, centre.z, lerp(10.5, 8.0, v), angle, lerp(2.2, 3.4, v), out.pos)
        out.target.set(centre.x, centre.y + 0.2, centre.z)
        out.fov = lerp(64.0, 56.0, v)
        out.roll = 3.0 * sin(v * PI) * motion.roll
    }

    /** Low in front, looking up at the fusion and its cry. */
    private val fusion = Shot("fusion", FUSION_SHOT, 0.0) { t, motion, out ->
        val u = easeInOutSine(phase(t, FUSION_SHOT, FINAL_SHOT))
        out.pos.set(centre.x + lerp(1.8, 1.1, u), centre.y - lerp(1.5, 1.3, u), centre.z - lerp(4.2, 3.2, u))
        headPos(t, m)
        out.target.set(m.x, m.y - 0.2, m.z)
        out.fov = lerp(50.0, 42.0, u) + 6.0 * pulse(t, CRY, 0.04, 0.5) * motion.punch - 3.0 * pulse(t, FUSION, 0.05, 0.4) * motion.punch
        out.roll = 0.0
    }

    /** Beside the player, Mew coming down to them. */
    private val finale = Shot("final", FINAL_SHOT, 0.8) { t, _, out ->
        val u = easeInOutSine(phase(t, FINAL_SHOT, 22.4))
        out.pos.set(lerp(2.0, 1.4, u), lerp(1.7, 1.55, u), lerp(0.2, 0.9, u))
        headPos(t, m)
        out.target.set(m.x, m.y - 0.15, m.z)
        out.fov = lerp(52.0, 46.0, u)
        out.roll = 0.0
    }

    override val director = CameraDirector(listOf(meet, play, whirl, fusion, finale))

    // ---------------------------------------------------------------- the look

    override fun cameraWeight(t: Double): Double = smoother(t / 1.0) * (1.0 - smoother((t - 22.4) / 1.6))

    override fun letterbox(t: Double): Double = smooth(t / 0.8) * (1.0 - smooth((t - 22.5) / 1.3))

    override fun title(t: Double): Double = window(t, 18.6, 19.1, 21.0, 21.6)

    override fun presence(t: Double): Double = smooth(t / 0.8) * (1.0 - smooth((t - 22.5) / 1.5))

    override fun shake(a: Double): Double = 0.12 * decay(a, FUSION, 0.4) + 0.4 * decay(a, CRY, 0.5)

    override fun flash(a: Double): Double = (0.35 * pulse(a, FUSION, 0.03, 0.25) + 0.6 * pulse(a, CRY, 0.03, 0.3)).coerceIn(0.0, 1.0)

    /** Soft pinks and blues through the whirlwind, brighter at the fusion. */
    override fun grade(t: Double, out: Grade): Grade {
        val w = whirl(t)
        val s = sphere(t)
        val soft = smooth(t / 1.5) * (1.0 - smooth((t - 21.5) / 2.0))
        out.r = 1.0 + 0.05 * w + 0.04 * s
        out.g = 1.0 - 0.03 * w
        out.b = 1.0 + 0.08 * w + 0.05 * s
        out.exposure = 1.0 - 0.06 * w + 0.12 * s
        out.saturation = 1.0 + 0.1 * soft + 0.1 * w
        out.contrast = 1.0 + 0.05 * soft
        out.vignette = 0.2 * soft + 0.15 * w
        return out
    }

    // ---------------------------------------------------------------- envelopes

    /** Its soft energy while it waits. */
    fun energy(a: Double): Double = window(a, 0.3, 1.2, 4.5, 5.6)

    /** The trails of its flight. */
    fun trails(a: Double): Double = window(a, FLIGHT, FLIGHT + 0.5, CIRCLE_END + 0.2, WHIRL_IN)

    /** The whirlwind of motes. */
    fun whirl(a: Double): Double = window(a, CIRCLE_END, WHIRL_IN, FUSION - 0.1, FUSION + 0.6)

    /** The illusions' halos. */
    fun illusions(a: Double): Double = window(a, ILLUSIONS, ILLUSIONS + 0.6, FUSION - 0.2, FUSION)

    /** The sphere of the fusion. */
    fun sphere(a: Double): Double = window(a, FUSION, FUSION + 0.4, CRY + 0.8, DESCEND)

    /** Its cry's psychic wave. */
    fun cryWave(a: Double): Double = if (a < CRY) 0.0 else 1.0 - smooth((a - CRY) / 1.2)

    /** The sparkles as the energy fades. */
    fun sparkle(a: Double): Double = window(a, DESCEND - 0.5, DESCEND, LANDING + 0.5, 23.6)

    override fun envelopes(a: Double): DoubleArray = doubleArrayOf(energy(a), trails(a), whirl(a), illusions(a), sphere(a), cryWave(a), sparkle(a))

    // ---------------------------------------------------------------- cues

    override val cues: List<Cue> = buildList {
        add(Cue(0.4, Cue.Kind.SOUND, "minecraft:block.amethyst_block.chime", "actor", volume = 1.2f, pitch = 1.5f))
        add(Cue(1.1, Cue.Kind.CRY, "cobblemon:pokemon.mew.cry", "actor", volume = 0.9f, pitch = 1.15f))
        add(Cue(3.2, Cue.Kind.ANIMATE, "glad"))
        add(Cue(3.2, Cue.Kind.SOUND, "minecraft:entity.allay.ambient_without_item", "actor", volume = 1.0f, pitch = 1.4f))
        add(Cue(FLIGHT, Cue.Kind.EVENT, "flight"))
        add(Cue(FLIGHT, Cue.Kind.SOUND, "minecraft:entity.phantom.flap", "actor", volume = 0.8f, pitch = 1.8f))
        for ((i, at) in doubleArrayOf(6.6, 8.0, 9.4).withIndex()) {
            add(Cue(at, Cue.Kind.SOUND, "minecraft:entity.allay.ambient_with_item", "actor", volume = 0.9f, pitch = 1.3f + 0.1f * i))
            add(Cue(at + 0.3, Cue.Kind.SOUND, "minecraft:block.amethyst_block.chime", "actor", volume = 0.9f, pitch = 1.2f + 0.15f * i))
        }
        add(Cue(8.0, Cue.Kind.ANIMATE, "glad"))
        add(Cue(CIRCLE_END, Cue.Kind.EVENT, "whirl"))
        add(Cue(CIRCLE_END, Cue.Kind.SOUND, "minecraft:entity.illusioner.prepare_mirror", "actor", volume = 1.2f, pitch = 1.2f))
        add(Cue(ILLUSIONS, Cue.Kind.EVENT, "illusions"))
        add(Cue(ILLUSIONS, Cue.Kind.SOUND, "minecraft:entity.illusioner.mirror_move", "actor", volume = 1.3f, pitch = 1.3f))
        add(Cue(CONVERGE, Cue.Kind.SOUND, "minecraft:block.beacon.power_select", "actor", volume = 1.0f, pitch = 1.6f))
        add(Cue(FUSION, Cue.Kind.EVENT, "fusion"))
        add(Cue(FUSION, Cue.Kind.SOUND, "minecraft:block.beacon.activate", "actor", volume = 1.4f, pitch = 1.5f))
        add(Cue(FUSION, Cue.Kind.SOUND, "minecraft:entity.illusioner.cast_spell", "actor", volume = 1.2f, pitch = 1.2f))
        add(Cue(CRY, Cue.Kind.CRY, "cobblemon:pokemon.mew.cry", "actor", volume = 2.2f))
        add(Cue(CRY, Cue.Kind.EVENT, "cry"))
        add(Cue(CRY, Cue.Kind.SOUND, "minecraft:block.amethyst_block.resonate", "actor", volume = 1.4f, pitch = 1.4f))
        add(Cue(DESCEND, Cue.Kind.SOUND, "minecraft:block.beacon.deactivate", "actor", volume = 1.0f, pitch = 1.6f))
        add(Cue(LANDING, Cue.Kind.EVENT, "landing"))
        add(Cue(LANDING, Cue.Kind.ANIMATE, "glad"))
        add(Cue(LANDING, Cue.Kind.SOUND, "minecraft:block.amethyst_block.step", "actor", volume = 1.2f, pitch = 1.5f))
        if (test) {
            add(Cue(VANISH - 0.05, Cue.Kind.EVENT, "vanish"))
            add(Cue(VANISH, Cue.Kind.SOUND, "minecraft:entity.enderman.teleport", "actor", volume = 1.0f, pitch = 1.4f))
        }
    }.sortedBy { it.time }

    companion object {
        const val SECONDS = 24.0
        const val FLIGHT = 5.0
        const val CIRCLE_START = 5.8
        const val CIRCLE_END = 10.0
        const val WHIRL_IN = 11.0
        const val ILLUSIONS = 11.2
        const val CONVERGE = 13.5
        const val FUSION_SHOT = 16.2
        const val FUSION = 16.5
        const val CRY = 18.0
        const val FINAL_SHOT = 20.6
        const val DESCEND = 21.0
        const val LANDING = 22.2
        const val VANISH = 22.8
        const val VANISH_END = 23.2
        const val ILLUSION_COUNT = 6

        /** Mew's head over its feet. */
        const val HEAD = 0.5
    }
}
