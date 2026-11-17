package generations.gg.generations.core.generationscore.common.world.eon

import java.util.Random
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/** The config's rules in the server's ticks (20 a second). Pure (tested off-game). */
class EonRules(
    val chance: Double,
    val firstTicks: Long,
    val intervalTicks: Long,
    val presenceTicks: Long,
    val minSampleBlocks: Double,
    val minIntervalBlocks: Double,
    val minIntervalCells: Int,
    val repeatFactor: Double,
    val biomeMemory: Int,
    val regionSize: Int,
    val regionCooldownMs: Long,
    val minRewardDistance: Double,
    val rewardMemory: Int,
) {
    companion object {
        /** One sample a player every 30 s. */
        const val SAMPLE_TICKS = 600
        /** The cells of the anti-circle rule: 64 blocks. */
        const val CELL = 64
    }
}

/** A fragment won: where, which biome, when (real time). */
class EonReward(val dimension: String, val x: Double, val z: Double, val biome: String, val timeMs: Long)

/**
 * One player's exploration (saved by EonData): the admissible time since the last draw, the
 * presence in a row, the blocks and cells of the interval (and the previous interval's cells),
 * the last rewards, a fragment waiting to be given, the counters shown by /generations eon info.
 */
class EonRecord {
    /** Admissible ticks since the last draw. */
    var counted = 0L
    /** Admissible ticks in all. */
    var totalTicks = 0L
    /** Admissible ticks in a row (a teleport, a death or a change of dimension resets it). */
    var presence = 0L
    /** True until the first draw, and again after a fragment won: firstTicks then. */
    var fresh = true
    /** A manifestation won by a draw but that found no place: started at the next sample, without a draw. */
    var owed = false
    var intervalBlocks = 0.0
    val cells = LinkedHashSet<Long>()
    val previousCells = LinkedHashSet<Long>()
    val rewards = ArrayList<EonReward>()
    /** Fragments won but not received yet (a disconnection, a crash), and the session they came from. */
    var pending = 0
    var pendingSession = 0L
    var rewardedSession = 0L
    var draws = 0
    var manifestations = 0
    var successes = 0
    var failures = 0
    var fragments = 0

    companion object {
        /** Rewards kept (the memories of the biomes and of the distances read from them). */
        const val KEPT = 8
        const val MAX_CELLS = 64
    }
}

object EonMath {
    // ---------------------------------------------------------------- places

    fun cell(x: Double, z: Double): Long = key(floor(x / EonRules.CELL).toLong(), floor(z / EonRules.CELL).toLong())

    fun region(x: Double, z: Double, size: Int): Long = key(floor(x / size).toLong(), floor(z / size).toLong())

    private fun key(a: Long, b: Long): Long = (a shl 32) or (b and 0xffffffffL)

    /** The cells of 64 blocks crossed from a point to another (the sample's way), at most 12. */
    fun cellsAlong(x0: Double, z0: Double, x1: Double, z1: Double, out: MutableCollection<Long>) {
        val dx = x1 - x0
        val dz = z1 - z0
        val length = sqrt(dx * dx + dz * dz)
        val steps = (length / (EonRules.CELL / 2.0)).toInt().coerceIn(0, 11)
        for (i in 0..steps) {
            val u = if (steps == 0) 1.0 else i / steps.toDouble()
            out += cell(x0 + dx * u, z0 + dz * u)
        }
    }

    // ---------------------------------------------------------------- samples

    enum class Move { MOVED, STILL, TELEPORT }

    /**
     * A sample's movement: displacement from the last sample against the blocks really travelled
     * (walking, swimming, riding, gliding, counted by the movement checks). Much more displacement
     * than travel: a teleport.
     */
    fun move(displacement: Double, travelled: Double, rules: EonRules): Move = when {
        displacement > travelled + 16.0 && displacement > 24.0 -> Move.TELEPORT
        displacement < rules.minSampleBlocks && travelled < rules.minSampleBlocks -> Move.STILL
        else -> Move.MOVED
    }

    /** Whether a reward of this player's makes this place wait (its region, or near a recent one). */
    fun blocked(record: EonRecord, dimension: String, x: Double, z: Double, nowMs: Long, rules: EonRules): Boolean {
        if (rules.regionCooldownMs <= 0L) return false
        val region = region(x, z, rules.regionSize)
        val recent = record.rewards.filter { it.dimension == dimension && nowMs - it.timeMs < rules.regionCooldownMs }
        if (recent.any { region(it.x, it.z, rules.regionSize) == region }) return true
        if (rules.minRewardDistance <= 0.0 || rules.rewardMemory <= 0) return false
        val near = rules.minRewardDistance * rules.minRewardDistance
        return recent.takeLast(rules.rewardMemory).any { (it.x - x) * (it.x - x) + (it.z - z) * (it.z - z) < near }
    }

    /** An admissible sample: its ticks, its blocks and the cells it crossed. */
    fun count(record: EonRecord, ticks: Long, blocks: Double, cells: Collection<Long>) {
        record.counted += ticks
        record.totalTicks += ticks
        record.presence += ticks
        record.intervalBlocks += blocks
        for (c in cells) if (record.cells.size < EonRecord.MAX_CELLS) record.cells += c
    }

    /** The cells of this interval not seen in the previous one. */
    fun newCells(record: EonRecord): Int = record.cells.count { it !in record.previousCells }

    fun threshold(record: EonRecord, rules: EonRules): Long = if (record.fresh) rules.firstTicks else rules.intervalTicks

    fun due(record: EonRecord, rules: EonRules): Boolean = record.counted >= threshold(record, rules)

    /** What a due draw still waits for (a translation key's end), or null: presence, blocks, new ground. */
    fun waiting(record: EonRecord, rules: EonRules): String? = when {
        record.presence < rules.presenceTicks -> "presence"
        record.intervalBlocks < rules.minIntervalBlocks -> "blocks"
        newCells(record) < rules.minIntervalCells -> "cells"
        else -> null
    }

    /** This draw's chance in that biome (less in a biome of the last fragments). */
    fun chance(record: EonRecord, biome: String, rules: EonRules): Double {
        val recent = record.rewards.takeLast(rules.biomeMemory).any { it.biome == biome }
        return rules.chance * if (recent && rules.biomeMemory > 0) rules.repeatFactor else 1.0
    }

    /** After a draw (won or not): a new interval, its cells kept to compare. */
    fun drawn(record: EonRecord) {
        record.counted = 0L
        record.fresh = false
        record.intervalBlocks = 0.0
        record.previousCells.clear()
        record.previousCells += record.cells
        record.cells.clear()
    }

    /** A fragment won there: remembered for the delays and the biomes (8 kept). */
    fun rewarded(record: EonRecord, dimension: String, x: Double, z: Double, biome: String, nowMs: Long) {
        record.rewards += EonReward(dimension, x, z, biome, nowMs)
        while (record.rewards.size > EonRecord.KEPT) record.rewards.removeAt(0)
        record.fresh = true
        record.counted = 0L
    }

    // ---------------------------------------------------------------- odds

    class Odds(val mean: Double, val median: Double, val p10: Double, val p90: Double)

    /**
     * The hours of exploration for the 16 fragments (the model of tools/eon_odds_sim.py): samples
     * of 30 s, a share `idle` of them not admissible, a draw every interval, the chance (× the
     * factor a quarter of the time), manifestations of 1.5 to 2.5 min won with `success`, then
     * minRewardDistance walked at 2 blocks a second.
     */
    fun odds(rules: EonRules, success: Double, idle: Double, runs: Int = 4000, seed: Long = 7L): Odds {
        val random = Random(seed)
        val hours = DoubleArray(runs)
        val need = (rules.intervalTicks / EonRules.SAMPLE_TICKS.toDouble()).coerceAtLeast(1.0)
        for (r in 0 until runs) {
            var minutes = 0.0
            var got = 0
            var guard = 0
            while (got < 16 && guard++ < 100_000) {
                var ok = 0
                var samples = 0
                while (ok < need) {
                    samples++
                    if (random.nextDouble() >= idle) ok++
                }
                minutes += samples * 0.5
                val chance = rules.chance * if (random.nextDouble() < 0.25) rules.repeatFactor else 1.0
                if (random.nextDouble() < chance) {
                    minutes += 1.5 + random.nextDouble()
                    if (random.nextDouble() < success) {
                        got++
                        minutes += rules.minRewardDistance / 2.0 / 60.0
                    }
                }
            }
            hours[r] = minutes / 60.0
        }
        hours.sort()
        return Odds(hours.average(), hours[runs / 2], hours[runs / 10], hours[runs * 9 / 10])
    }
}

/**
 * A manifestation's phases (pure): each change from one phase only, one reward a session
 * (claim succeeds once), no failure once the reward is granted.
 */
class EonMachine {
    enum class Phase { SIGNAL, ANOMALY, PURSUIT, CORE, STABILIZE, DONE, FAILED }

    var phase = Phase.SIGNAL
        private set

    val over: Boolean get() = phase == Phase.DONE || phase == Phase.FAILED

    fun toAnomaly(): Boolean = move(Phase.SIGNAL, Phase.ANOMALY)

    fun toPursuit(): Boolean = move(Phase.ANOMALY, Phase.PURSUIT)

    /** The pursuit's end (or a test's shortcut from any phase before it). */
    fun toCore(): Boolean {
        if (phase != Phase.SIGNAL && phase != Phase.ANOMALY && phase != Phase.PURSUIT) return false
        phase = Phase.CORE
        return true
    }

    /** The core clicked: true once. */
    fun claim(): Boolean = move(Phase.CORE, Phase.STABILIZE)

    fun finish(): Boolean = move(Phase.STABILIZE, Phase.DONE)

    /** Failed: never once the reward is granted. */
    fun fail(): Boolean {
        if (phase == Phase.STABILIZE || over) return false
        phase = Phase.FAILED
        return true
    }

    private fun move(from: Phase, to: Phase): Boolean {
        if (phase != from) return false
        phase = to
        return true
    }
}

/**
 * The anomaly's way (shared by the server and the client, pure): points near the ground, a
 * Catmull-Rom curve walked by distance. A segment is settled once the point after it is known;
 * the anomaly never goes past the settled length. Its motion: from `tick`, at `s0`, `speed`
 * blocks a second; 0: it hovers where it is (a slow bob).
 */
class EonPath {
    private val xs = ArrayList<Double>()
    private val ys = ArrayList<Double>()
    private val zs = ArrayList<Double>()
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
                val dx = a[0] - b[0]; val dy = a[1] - b[1]; val dz = a[2] - b[2]
                table[k] = table[k - 1] + sqrt(dx * dx + dy * dy + dz * dz)
                a[0] = b[0]; a[1] = b[1]; a[2] = b[2]
            }
            tables += table
            starts += start
        }
    }

    val settled: Double get() = if (tables.isEmpty()) 0.0 else starts.last() + tables.last()[SAMPLES]

    private fun coord(list: ArrayList<Double>, i: Int): Double = when {
        list.isEmpty() -> 0.0
        i < 0 -> 2.0 * list[0] - list[1.coerceAtMost(list.size - 1)]
        i >= list.size -> 2.0 * list[list.size - 1] - list[(list.size - 2).coerceAtLeast(0)]
        else -> list[i]
    }

    fun segment(i: Int, u: Double, out: DoubleArray): DoubleArray {
        val u2 = u * u
        val u3 = u2 * u
        for (c in 0..2) {
            val list = when (c) {
                0 -> xs
                1 -> ys
                else -> zs
            }
            val p0 = coord(list, i - 1)
            val p1 = coord(list, i)
            val p2 = coord(list, i + 1)
            val p3 = coord(list, i + 2)
            out[c] = 0.5 * (2.0 * p1 + (-p0 + p2) * u + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * u2 + (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * u3)
        }
        return out
    }

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

    fun sAt(tick: Double): Double = (motionS + motionSpeed * (tick - motionTick) / 20.0).coerceIn(0.0, settled)

    fun setMotion(tick: Long, s: Double, speed: Double) {
        motionTick = tick
        motionS = s
        motionSpeed = speed
    }

    /** Where it is at that tick: on the curve, bobbing gently when it hovers. */
    fun position(tick: Double, out: DoubleArray): DoubleArray {
        at(sAt(tick), out)
        val seconds = tick / 20.0
        out[1] += 0.12 * sin(seconds * 2.1) + if (motionSpeed <= 0.0) 0.1 * cos(seconds * 1.3) else 0.0
        return out
    }

    companion object {
        const val SAMPLES = 12
        /** The anomaly's centre over the ground. */
        const val HEIGHT = 1.7
    }
}
