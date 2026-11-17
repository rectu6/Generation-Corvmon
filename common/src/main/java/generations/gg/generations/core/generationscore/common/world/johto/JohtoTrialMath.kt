package generations.gg.generations.core.generationscore.common.world.johto

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The trials' shapes and windows (JOHTO-PLAN.md §§ 6-7), pure: the same on the server (which
 * decides) and on the client (which draws), tested off-game. Shapes are flat (x, z): a thick
 * segment, discs, a ring with a gap, a cone. Times are game ticks.
 */
object JohtoTrialMath {
    /** Entei's attacks. */
    const val LINE = 0
    const val CIRCLES = 1
    const val WAVE = 2
    const val CONE = 3

    const val LINE_WIDTH = 2.4
    const val CIRCLE_RADIUS = 2.4
    const val WAVE_THICKNESS = 1.6
    /** The wave's gap, half its angle (radians). */
    const val WAVE_GAP = 0.5
    const val CONE_HALF = 0.45
    const val CONE_LENGTH = 15.0
    /** How long a wave takes to cross the arena (ticks). */
    const val WAVE_TICKS = 26

    /** Distance from (px, pz) to the segment a-b. */
    fun segment(px: Double, pz: Double, ax: Double, az: Double, bx: Double, bz: Double): Double {
        val dx = bx - ax
        val dz = bz - az
        val l2 = dx * dx + dz * dz
        val t = if (l2 < 1e-9) 0.0 else (((px - ax) * dx + (pz - az) * dz) / l2).coerceIn(0.0, 1.0)
        val cx = ax + dx * t - px
        val cz = az + dz * t - pz
        return sqrt(cx * cx + cz * cz)
    }

    /** The angle difference a - b in (-π, π]. */
    fun angleDiff(a: Double, b: Double): Double {
        var d = (a - b) % (2.0 * PI)
        if (d > PI) d -= 2.0 * PI
        if (d <= -PI) d += 2.0 * PI
        return d
    }

    /**
     * Whether an attack touches (px, pz) at tick `now`. p: its points, flat (x0, z0, x1, z1, ...):
     * LINE a, b; CIRCLES their centres; WAVE its centre and the gap's direction point; CONE its
     * apex and the point it aims at. active..end: when it burns. airborne: the player is jumping
     * (the wave passes under them).
     */
    fun hits(kind: Int, p: DoubleArray, active: Long, end: Long, arena: Double, now: Long, px: Double, pz: Double, airborne: Boolean): Boolean {
        if (now < active || now > end || p.size < 2) return false
        return when (kind) {
            LINE -> p.size >= 4 && segment(px, pz, p[0], p[1], p[2], p[3]) <= LINE_WIDTH / 2.0
            CIRCLES -> {
                var i = 0
                var hit = false
                while (i + 1 < p.size) {
                    val dx = px - p[i]
                    val dz = pz - p[i + 1]
                    if (dx * dx + dz * dz <= CIRCLE_RADIUS * CIRCLE_RADIUS) hit = true
                    i += 2
                }
                hit
            }
            WAVE -> {
                if (airborne || p.size < 4) return false
                val r = waveRadius(active, now, arena)
                val dx = px - p[0]
                val dz = pz - p[1]
                val d = sqrt(dx * dx + dz * dz)
                if (abs(d - r) > WAVE_THICKNESS / 2.0) return false
                val gap = atan2(p[3] - p[1], p[2] - p[0])
                abs(angleDiff(atan2(dz, dx), gap)) > WAVE_GAP
            }
            CONE -> {
                if (p.size < 4) return false
                val dx = px - p[0]
                val dz = pz - p[1]
                val d = sqrt(dx * dx + dz * dz)
                if (d > CONE_LENGTH || d < 0.5) return d < 0.5
                val aim = atan2(p[3] - p[1], p[2] - p[0])
                abs(angleDiff(atan2(dz, dx), aim)) <= CONE_HALF
            }
            else -> false
        }
    }

    /** The wave's radius at `now` (it leaves its centre at `active`). */
    fun waveRadius(active: Long, now: Long, arena: Double): Double = ((now - active).toDouble() / WAVE_TICKS).coerceIn(0.0, 1.0) * (arena + 2.0)

    /** How long each attack burns once active (ticks). */
    fun burnTicks(kind: Int): Int = when (kind) {
        WAVE -> WAVE_TICKS
        CONE -> 14
        else -> 12
    }

    /**
     * Within a window around a moment: the server's tick minus half the player's latency (the
     * click left their client that long before), ± the window.
     */
    fun onTime(serverTick: Long, latencyMs: Int, target: Long, windowMs: Int): Boolean {
        val sent = serverTick - (latencyMs / 2.0) / 50.0
        return abs(sent - target) * 50.0 <= windowMs
    }

    /** Flat distance. */
    fun flat(ax: Double, az: Double, bx: Double, bz: Double): Double {
        val dx = ax - bx
        val dz = az - bz
        return sqrt(dx * dx + dz * dz)
    }

    /** A point at a distance and angle from another (flat). */
    fun around(cx: Double, cz: Double, r: Double, angle: Double, out: DoubleArray): DoubleArray {
        out[0] = cx + cos(angle) * r
        out[1] = cz + sin(angle) * r
        return out
    }

    /**
     * Suicune's harmony: when the three rings meet at the source (ticks after the harmony starts),
     * the k-th time; they come closer together each time (the rhythm quickens a little).
     */
    fun harmonyTick(start: Long, k: Int): Long = start + 60L + (0 until k).sumOf { (80 - it * 6).coerceAtLeast(44).toLong() }
}
