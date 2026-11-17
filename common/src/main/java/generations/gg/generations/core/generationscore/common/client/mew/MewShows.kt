package generations.gg.generations.core.generationscore.common.client.mew

import org.joml.Vector3d
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Where a drawn Mew is (world feet), its way (Minecraft yaw), size and tilt; visible or not. */
class MewPose {
    val pos = Vector3d()
    var yaw = 0.0
    var roll = 0.0
    var scale = 1.0
    var visible = false

    fun isFinite(): Boolean = pos.x.isFinite() && pos.y.isFinite() && pos.z.isFinite() && yaw.isFinite() && roll.isFinite() && scale.isFinite()
}

/** Something a show does once: at its time, where Mew is (or at the show's origin). */
class MewShowEvent(val time: Double, val kind: Kind) {
    enum class Kind {
        /** Mew appears (a little burst). */
        APPEAR,
        CRY,
        /** Laughs (the "glad" animation and a giggle). */
        LAUGH,
        /** Vanishes in a pink puff. */
        PUFF,
        /** Teleports (a flash where it was). */
        TELEPORT,
        /** Explodes into psychic motes (the opening's end). */
        BURST,
        /** Dissolves into psychic motes that linger (B). */
        DISSOLVE,
    }
}

/**
 * A false appearance or one of the challenge's short scenes (MEW-PLAN.md §§ 6, 7, 9), without
 * Minecraft (tested off-game): Mew's pose at each moment (seconds since it began), its events, its
 * motes and trail. Everything is drawn from the server's packet (origin, way, second point, seed).
 */
abstract class MewShow(val duration: Double) {
    abstract val events: List<MewShowEvent>

    /** Mew at time t; player: the player's feet now (some shows turn toward them). */
    abstract fun pose(t: Double, player: Vector3d, out: MewPose): MewPose

    /** How many pink motes a second float at the show's own place (B), 0 for none. */
    open fun motes(t: Double): Double = 0.0

    /** Whether Mew leaves a trail behind it. */
    open fun trail(t: Double): Boolean = false

    /** The place of the motes (B: where the player will turn). */
    open val motesAt: Vector3d? get() = null

    /**
     * While Mew has not been looked at yet, this moment may slow down (HOOH-PLAN.md § 4: the show
     * waits for the player's gaze, 3 s at most). False for the challenge's scenes.
     */
    open fun holdable(t: Double): Boolean = false

    /** A false appearance (its Mew is drawn bigger and brighter, with an aura). */
    open val manifestation: Boolean get() = false

    companion object {
        fun smooth(x: Double): Double {
            val t = x.coerceIn(0.0, 1.0)
            return t * t * (3.0 - 2.0 * t)
        }

        fun phase(t: Double, a: Double, b: Double): Double = if (b <= a) (if (t >= b) 1.0 else 0.0) else ((t - a) / (b - a)).coerceIn(0.0, 1.0)

        /** Minecraft yaw from a point toward another. */
        fun yaw(fromX: Double, fromZ: Double, toX: Double, toZ: Double): Double = Math.toDegrees(atan2(-(toX - fromX), toZ - fromZ))

        fun lerpAngle(a: Double, b: Double, u: Double): Double {
            var d = (b - a) % 360.0
            if (d > 180.0) d -= 360.0
            if (d < -180.0) d += 360.0
            return a + d * u
        }

        /** A seeded number from 0 to 1. */
        fun random(seed: Int, salt: Int): Double {
            var h = seed * 0x27d4eb2d + salt * 0x165667b1
            h = h xor (h ushr 15)
            h *= 0x2c1b3c6d
            h = h xor (h ushr 12)
            return (h ushr 8) / 16777216.0
        }

        /** The false appearance of that type (0 to 3 = A to D). */
        fun manifestation(type: Int, seed: Int, origin: Vector3d, yaw: Double, second: Vector3d, seconds: Double): MewShow = when (type) {
            0 -> Crossing(origin, second, seconds.coerceIn(3.0, 10.0), seed)
            1 -> Presence(origin, seconds.coerceIn(5.0, 12.0))
            2 -> Aerial(origin, second, seconds.coerceIn(4.0, 12.0), seed)
            else -> Curious(origin, second, yaw, seconds.coerceIn(4.5, 10.0))
        }
    }
}

/** A — across the forest: a pass between two points of a clearing, a look at the player, a small cry, a puff. */
class Crossing(val from: Vector3d, val delta: Vector3d, duration: Double, val seed: Int) : MewShow(duration) {
    override val events = listOf(
        MewShowEvent(0.0, MewShowEvent.Kind.APPEAR),
        MewShowEvent(duration * 0.48, MewShowEvent.Kind.CRY),
        MewShowEvent(duration - 0.15, MewShowEvent.Kind.PUFF),
    )

    override fun holdable(t: Double): Boolean = t in 0.3..duration * 0.8

    override fun trail(t: Double): Boolean = t < duration - 0.15

    override val manifestation: Boolean get() = true

    override fun pose(t: Double, player: Vector3d, out: MewPose): MewPose {
        val u = phase(t, 0.0, duration - 0.1)
        // Eased so that it slows down half way, for its look (still always moving forward).
        val e = u + 0.6 * sin(2.0 * PI * u) / (2.0 * PI)
        out.pos.set(from.x + delta.x * e, from.y + delta.y * e + 0.35 * sin(PI * u) + 0.08 * sin(t * 9.0), from.z + delta.z * e)
        val way = yaw(0.0, 0.0, delta.x, delta.z)
        val look = yaw(out.pos.x, out.pos.z, player.x, player.z)
        out.yaw = lerpAngle(way, look, smooth(phase(u, 0.35, 0.45)) * (1.0 - smooth(phase(u, 0.6, 0.72))))
        out.roll = 12.0 * sin(u * PI * 2.0)
        out.scale = 1.0 - smooth(phase(t, duration - 0.3, duration - 0.1))
        out.visible = t < duration - 0.1
        return out
    }
}

/**
 * B — a presence: a cry behind the player; Mew is there when they turn, stares at them, then
 * dissolves into psychic motes that linger a few seconds (HOOH-PLAN.md § 4, D7).
 */
class Presence(val at: Vector3d, duration: Double) : MewShow(duration) {
    private val dissolve = (duration - 2.6).coerceAtLeast(2.0)

    override val events = listOf(
        MewShowEvent(0.2, MewShowEvent.Kind.CRY),
        MewShowEvent(0.55, MewShowEvent.Kind.APPEAR),
        MewShowEvent(dissolve * 0.6, MewShowEvent.Kind.LAUGH),
        MewShowEvent(dissolve, MewShowEvent.Kind.DISSOLVE),
    )

    override fun pose(t: Double, player: Vector3d, out: MewPose): MewPose {
        out.pos.set(at.x, at.y + 0.12 * sin(t * 2.2), at.z)
        out.yaw = yaw(at.x, at.z, player.x, player.z)
        out.roll = 7.0 * sin(t * 1.7)
        out.scale = smooth(phase(t, 0.5, 0.85)) * (1.0 - smooth(phase(t, dissolve - 0.1, dissolve + 0.25)))
        out.visible = t in 0.5..(dissolve + 0.25)
        return out
    }

    override fun holdable(t: Double): Boolean = t in 0.9..(dissolve - 0.3)

    override fun motes(t: Double): Double =
        4.0 * smooth(phase(t, 0.5, 1.0)) * (1.0 - smooth(phase(t, dissolve - 0.2, dissolve))) +
            16.0 * smooth(phase(t, dissolve, dissolve + 0.2)) * (1.0 - smooth(phase(t, duration - 1.2, duration)))

    override val motesAt: Vector3d get() = at

    override val manifestation: Boolean get() = true
}

/** C — in the sky: a flight over the trees, a psychic trail, a flash of teleportation. */
class Aerial(val from: Vector3d, val delta: Vector3d, duration: Double, val seed: Int) : MewShow(duration) {
    private val vanish = duration * 0.88

    override fun holdable(t: Double): Boolean = t in 0.3..(vanish - 0.4)

    override val manifestation: Boolean get() = true

    override val events = listOf(
        MewShowEvent(0.0, MewShowEvent.Kind.APPEAR),
        MewShowEvent(duration * 0.4, MewShowEvent.Kind.LAUGH),
        MewShowEvent(vanish, MewShowEvent.Kind.TELEPORT),
    )

    override fun pose(t: Double, player: Vector3d, out: MewPose): MewPose {
        val u = phase(t, 0.0, duration)
        out.pos.set(from.x + delta.x * u, from.y + delta.y * u + 0.6 * sin(u * PI * 3.0 + random(seed, 1) * 6.0), from.z + delta.z * u)
        out.yaw = yaw(0.0, 0.0, delta.x, delta.z)
        out.roll = 20.0 * sin(u * PI * 4.0)
        out.scale = 1.0
        out.visible = t < vanish
        return out
    }

    override fun trail(t: Double): Boolean = t < vanish
}

/** D — curious: it appears in front of the player, stares, circles round them once, laughs, vanishes. */
class Curious(val at: Vector3d, val toPlayer: Vector3d, val facing: Double, duration: Double) : MewShow(duration) {
    private val circleStart = 1.6
    private val circleEnd = (duration - 1.5).coerceAtLeast(circleStart + 2.0)

    override fun holdable(t: Double): Boolean = t in 0.3..circleStart

    override fun trail(t: Double): Boolean = t in circleStart..circleEnd

    override val manifestation: Boolean get() = true

    override val events = listOf(
        MewShowEvent(0.0, MewShowEvent.Kind.APPEAR),
        MewShowEvent(circleEnd + 0.3, MewShowEvent.Kind.LAUGH),
        MewShowEvent(duration - 0.2, MewShowEvent.Kind.PUFF),
    )

    override fun pose(t: Double, player: Vector3d, out: MewPose): MewPose {
        // The circle round the player's place when it appeared (the server's second point).
        val cx = at.x + toPlayer.x
        val cz = at.z + toPlayer.z
        val r = sqrt(toPlayer.x * toPlayer.x + toPlayer.z * toPlayer.z).coerceIn(2.0, 4.0)
        val start = Math.atan2(at.x - cx, at.z - cz)
        val turn = 2.0 * PI * smooth(phase(t, circleStart, circleEnd))
        val angle = start + turn
        out.pos.set(cx + sin(angle) * r, at.y + 0.15 * sin(t * 2.5), cz + cos(angle) * r)
        out.yaw = yaw(out.pos.x, out.pos.z, player.x, player.z)
        out.roll = 18.0 * smooth(phase(t, 0.4, 0.8)) * (1.0 - smooth(phase(t, 1.1, 1.4)))
        out.scale = smooth(phase(t, 0.0, 0.25)) * (1.0 - smooth(phase(t, duration - 0.35, duration - 0.15)))
        out.visible = t < duration - 0.15
        return out
    }
}

/**
 * The opening (§ 7, 6.5 s): Mew springs up in front of the player's eyes, circles round them
 * laughing, teleports twice, stares at them, then bursts into psychic motes.
 */
class Opening(val feet: Vector3d, val facing: Double) : MewShow(6.5) {
    override val events = listOf(
        MewShowEvent(0.0, MewShowEvent.Kind.APPEAR),
        MewShowEvent(0.3, MewShowEvent.Kind.CRY),
        MewShowEvent(1.6, MewShowEvent.Kind.LAUGH),
        MewShowEvent(2.9, MewShowEvent.Kind.LAUGH),
        MewShowEvent(TELEPORT1, MewShowEvent.Kind.TELEPORT),
        MewShowEvent(TELEPORT2, MewShowEvent.Kind.TELEPORT),
        MewShowEvent(5.4, MewShowEvent.Kind.LAUGH),
        MewShowEvent(6.3, MewShowEvent.Kind.BURST),
    )

    private fun around(angleDeg: Double, r: Double, y: Double, out: Vector3d): Vector3d {
        val a = Math.toRadians(facing + angleDeg)
        return out.set(feet.x - sin(a) * r, feet.y + y, feet.z + cos(a) * r)
    }

    override fun pose(t: Double, player: Vector3d, out: MewPose): MewPose {
        // Continuous everywhere but at its two teleports (3.6 s to the left, 4.4 s to the right).
        when {
            t < TELEPORT1 -> {
                val r = 3.0 - 0.4 * smooth(phase(t, 0.8, 1.2)) * (1.0 - smooth(phase(t, 2.9, 3.3)))
                around(360.0 * smooth(phase(t, 0.8, 3.3)), r, 1.35 + 0.15 * sin(t * 3.0), out.pos)
            }
            t < TELEPORT2 -> around(-50.0, 3.2, 1.55 + 0.08 * sin(t * 5.0), out.pos)
            else -> {
                val back = smooth(phase(t, 4.8, 5.3))
                around(50.0 * (1.0 - back), 3.2 - 0.4 * back, 1.25 + 0.15 * back + 0.08 * sin(t * 3.0), out.pos)
            }
        }
        out.yaw = yaw(out.pos.x, out.pos.z, player.x, player.z)
        out.roll = if (t in 4.8..6.0) 14.0 * smooth(phase(t, 4.9, 5.2)) else 8.0 * sin(t * 2.0)
        out.scale = smooth(phase(t, 0.0, 0.2)) * (1.0 - smooth(phase(t, 6.1, 6.3)))
        out.visible = t < 6.3
        return out
    }

    companion object {
        const val TELEPORT1 = 3.6
        const val TELEPORT2 = 4.4
    }
}

/** Mew comes back after a failure (§ 9, 3 s): it appears in front of the player, laughs, vanishes. */
class Comeback(val feet: Vector3d, val facing: Double) : MewShow(3.0) {
    override val events = listOf(
        MewShowEvent(0.0, MewShowEvent.Kind.APPEAR),
        MewShowEvent(0.8, MewShowEvent.Kind.LAUGH),
        MewShowEvent(2.8, MewShowEvent.Kind.PUFF),
    )

    override fun pose(t: Double, player: Vector3d, out: MewPose): MewPose {
        val a = Math.toRadians(facing)
        out.pos.set(feet.x - sin(a) * 3.0, feet.y + 1.3 + 0.1 * sin(t * 3.0), feet.z + cos(a) * 3.0)
        out.yaw = yaw(out.pos.x, out.pos.z, player.x, player.z)
        out.roll = 10.0 * sin(t * 2.5)
        out.scale = smooth(phase(t, 0.0, 0.2)) * (1.0 - smooth(phase(t, 2.6, 2.8)))
        out.visible = t < 2.8
        return out
    }
}
