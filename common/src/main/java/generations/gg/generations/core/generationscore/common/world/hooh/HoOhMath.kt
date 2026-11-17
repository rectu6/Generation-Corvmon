package generations.gg.generations.core.generationscore.common.world.hooh

import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The rarity of the Passage of the Rainbow (HOOH-PLAN.md § 5), pure (tested off-game):
 * chance per draw, active ticks between two draws, active ticks needed since the last passage,
 * real milliseconds between two passages of a player.
 */
class HoOhRules(val chance: Double, val intervalTicks: Long, val minActiveTicks: Long, val cooldownMs: Long) {
    companion object {
        const val TICKS_PER_HOUR = 72_000.0
    }
}

/** A player's passages (saved with the world, HoOhPassageData). */
class HoOhRecord {
    /** Active play since the last passage (server ticks; the AFK time does not count). */
    var activeTicks = 0L
    /** Active play since the last draw. */
    var sinceDraw = 0L
    /** Real time of the last passage (ms), 0 for none. */
    var lastPassageMs = 0L
    var draws = 0
    var passages = 0
    var successes = 0
    /** A feather won and not yet in the inventory (given at the next connection if needed). */
    var pendingFeather = false
    /** The session that feather comes from; a session is rewarded once (rewardedSession). */
    var pendingSession = 0L
    var rewardedSession = 0L

    fun save(): Map<String, Any> = linkedMapOf(
        "active" to activeTicks, "sinceDraw" to sinceDraw, "last" to lastPassageMs, "draws" to draws,
        "passages" to passages, "successes" to successes, "pending" to pendingFeather,
        "pendingSession" to pendingSession, "rewarded" to rewardedSession,
    )

    companion object {
        private fun long(v: Any?): Long = (v as? Number)?.toLong() ?: 0L
        private fun int(v: Any?): Int = (v as? Number)?.toInt() ?: 0

        fun load(values: Map<String, Any?>): HoOhRecord = HoOhRecord().apply {
            activeTicks = long(values["active"])
            sinceDraw = long(values["sinceDraw"])
            lastPassageMs = long(values["last"])
            draws = int(values["draws"])
            passages = int(values["passages"])
            successes = int(values["successes"])
            pendingFeather = int(values["pending"]) != 0
            pendingSession = long(values["pendingSession"])
            rewardedSession = long(values["rewarded"])
        }
    }
}

object HoOhMath {
    /**
     * Adds active play (credit, in ticks) and tells whether a draw is due now: one every
     * intervalTicks of active play, never two at once (no catching up after a long absence).
     */
    fun due(record: HoOhRecord, credit: Long, rules: HoOhRules): Boolean {
        if (credit <= 0L) return false
        record.activeTicks += credit
        record.sinceDraw += credit
        if (record.sinceDraw < rules.intervalTicks) return false
        record.sinceDraw = record.sinceDraw % rules.intervalTicks
        return true
    }

    /** Draws are allowed: enough active play since the last passage, its real-time delay over. */
    fun eligible(record: HoOhRecord, rules: HoOhRules, nowMs: Long): Boolean =
        record.activeTicks >= rules.minActiveTicks && (record.lastPassageMs == 0L || nowMs - record.lastPassageMs >= rules.cooldownMs)

    /** A passage happened (whatever its end): the counters start again. */
    fun passed(record: HoOhRecord, nowMs: Long) {
        record.activeTicks = 0L
        record.sinceDraw = 0L
        record.lastPassageMs = nowMs
        record.passages++
    }

    /** The first draw after a passage: at the first multiple of the interval that reaches the minimum. */
    private fun firstDraw(rules: HoOhRules): Long =
        maxOf(1L, Math.ceil(rules.minActiveTicks / rules.intervalTicks.toDouble()).toLong()) * rules.intervalTicks

    /** The mean wait in hours of active play (geometric draws from the first one). */
    fun meanHours(rules: HoOhRules): Double {
        if (rules.chance <= 0.0) return Double.POSITIVE_INFINITY
        return (firstDraw(rules) + rules.intervalTicks * (1.0 / rules.chance - 1.0)) / HoOhRules.TICKS_PER_HOUR
    }

    /** The wait that a share q of the players do not exceed, in hours of active play. */
    fun quantileHours(rules: HoOhRules, q: Double): Double {
        if (rules.chance <= 0.0) return Double.POSITIVE_INFINITY
        val draws = if (rules.chance >= 1.0) 1.0 else Math.ceil(ln(1.0 - q) / ln(1.0 - rules.chance)).coerceAtLeast(1.0)
        return (firstDraw(rules) + (draws - 1.0) * rules.intervalTicks) / HoOhRules.TICKS_PER_HOUR
    }

    /** The share of the players who see Ho-Oh within that many hours of active play. */
    fun within(rules: HoOhRules, hours: Double): Double {
        val ticks = hours * HoOhRules.TICKS_PER_HOUR - firstDraw(rules)
        if (ticks < 0.0) return 0.0
        val draws = Math.floor(ticks / rules.intervalTicks) + 1.0
        return 1.0 - Math.pow(1.0 - rules.chance, draws)
    }
}

/**
 * Ho-Oh's flight (HOOH-PLAN.md § 7), pure and shared by the server (where it is, for the checks)
 * and the client (where it is drawn): a Catmull-Rom curve through the points the server adds as
 * it goes, walked by distance. A segment is settled once the point after it is known (its shape
 * no longer changes): Ho-Oh never goes past the settled length.
 *
 * Its motion: from `tick`, at `s0` along the curve, `speed` blocks a second; speed 0 means it
 * circles over that point (radius CIRCLE_RADIUS, a little higher).
 */
class HoOhFlight {
    private val xs = ArrayList<Double>()
    private val ys = ArrayList<Double>()
    private val zs = ArrayList<Double>()
    /** Cumulative length at each sample (SAMPLES per segment), by segment. */
    private val tables = ArrayList<DoubleArray>()
    private val starts = ArrayList<Double>()

    var motionTick = 0L
    var motionS = 0.0
    var motionSpeed = 0.0

    val size: Int get() = xs.size

    fun point(i: Int, out: DoubleArray): DoubleArray {
        out[0] = xs[i]; out[1] = ys[i]; out[2] = zs[i]
        return out
    }

    fun add(x: Double, y: Double, z: Double) {
        xs += x; ys += y; zs += z
        // The segment before the previous point is now settled: its table.
        val settledSegments = (xs.size - 2).coerceAtLeast(0)
        while (tables.size < settledSegments) {
            val i = tables.size
            val start = if (i == 0) 0.0 else starts[i - 1] + tables[i - 1][SAMPLES]
            val table = DoubleArray(SAMPLES + 1)
            val a = DoubleArray(3)
            val b = DoubleArray(3)
            segment(i, 0.0, a)
            for (k in 1..SAMPLES) {
                segment(i, k / SAMPLES.toDouble(), b)
                table[k] = table[k - 1] + dist(a, b)
                a[0] = b[0]; a[1] = b[1]; a[2] = b[2]
            }
            tables += table
            starts += start
        }
    }

    /** The length Ho-Oh may fly (the settled segments). */
    val settled: Double get() = if (tables.isEmpty()) 0.0 else starts.last() + tables.last()[SAMPLES]

    private fun dist(a: DoubleArray, b: DoubleArray): Double {
        val dx = a[0] - b[0]; val dy = a[1] - b[1]; val dz = a[2] - b[2]
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun coord(list: ArrayList<Double>, i: Int): Double = when {
        list.isEmpty() -> 0.0
        i < 0 -> 2.0 * list[0] - list[1.coerceAtMost(list.size - 1)]
        i >= list.size -> 2.0 * list[list.size - 1] - list[(list.size - 2).coerceAtLeast(0)]
        else -> list[i]
    }

    /** Segment i (from point i to i + 1) at u from 0 to 1. */
    fun segment(i: Int, u: Double, out: DoubleArray): DoubleArray {
        val u2 = u * u
        val u3 = u2 * u
        for ((c, list) in listOf(xs, ys, zs).withIndex()) {
            val p0 = coord(list, i - 1)
            val p1 = coord(list, i)
            val p2 = coord(list, i + 1)
            val p3 = coord(list, i + 2)
            out[c] = 0.5 * (2.0 * p1 + (-p0 + p2) * u + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * u2 + (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * u3)
        }
        return out
    }

    /** The point at distance s along the settled curve (clamped). */
    fun at(s: Double, out: DoubleArray): DoubleArray {
        if (tables.isEmpty()) {
            if (xs.isEmpty()) {
                out[0] = 0.0; out[1] = 0.0; out[2] = 0.0
                return out
            }
            return point(0, out)
        }
        val d = s.coerceIn(0.0, settled)
        var i = 0
        while (i < tables.size - 1 && starts[i + 1] <= d) i++
        val local = d - starts[i]
        val table = tables[i]
        var k = 1
        while (k < SAMPLES && table[k] < local) k++
        val span = (table[k] - table[k - 1]).coerceAtLeast(1e-9)
        val f = ((local - table[k - 1]) / span).coerceIn(0.0, 1.0)
        return segment(i, (k - 1 + f) / SAMPLES, out)
    }

    /** Distance along the curve at that tick (the motion; clamped to the settled length). */
    fun sAt(tick: Double): Double = (motionS + motionSpeed * (tick - motionTick) / 20.0).coerceIn(0.0, settled)

    fun setMotion(tick: Long, s: Double, speed: Double) {
        motionTick = tick
        motionS = s
        motionSpeed = speed
    }

    /**
     * Where Ho-Oh is at that tick (ticks with the frame's part): on the curve, or circling over
     * its point when it waits (speed 0).
     */
    fun position(tick: Double, out: DoubleArray): DoubleArray {
        val s = sAt(tick)
        at(s, out)
        if (motionSpeed <= 0.0) {
            val seconds = (tick - motionTick) / 20.0
            val angle = seconds * CIRCLE_SPEED
            out[0] += CIRCLE_RADIUS * sin(angle)
            out[1] += 2.0 + 1.2 * sin(seconds * 0.7)
            out[2] += CIRCLE_RADIUS * cos(angle)
        }
        return out
    }

    companion object {
        const val SAMPLES = 16
        const val CIRCLE_RADIUS = 10.0
        /** Radians a second: a full circle in about 13 s. */
        const val CIRCLE_SPEED = 0.48
    }
}
