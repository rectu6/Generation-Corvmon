package generations.gg.generations.core.generationscore.common.world.mew

import kotlin.math.abs
import kotlin.math.ceil

/**
 * The hunt's arithmetic, without Minecraft (MEW-PLAN.md §§ 4 and 5; tested off-game by
 * tools/MewBench.java). Times of the hunt are server ticks of active presence (20 a second,
 * 72 000 an hour); times of the away-from-keyboard rule are milliseconds (Minecraft's own idle
 * timer, ServerPlayer.getLastActionTime, is in milliseconds).
 */
object MewMath {
    const val TICKS_PER_HOUR = 72_000L

    @JvmStatic
    fun ticks(hours: Double): Long = Math.round(hours * TICKS_PER_HOUR)

    @JvmStatic
    fun hours(ticks: Long): Double = ticks / TICKS_PER_HOUR.toDouble()

    /**
     * The chance (0 to 1) of a draw made at this many hours: linear between the points of the table
     * (hours ascending, percents), the first value before it and the last one after it.
     */
    @JvmStatic
    fun chance(tableHours: DoubleArray, tablePercent: DoubleArray, at: Double): Double {
        val n = minOf(tableHours.size, tablePercent.size)
        if (n == 0) return 0.0
        if (at <= tableHours[0]) return (tablePercent[0] / 100.0).coerceIn(0.0, 1.0)
        for (i in 1 until n) {
            if (at <= tableHours[i]) {
                val span = tableHours[i] - tableHours[i - 1]
                val t = if (span <= 0.0) 1.0 else (at - tableHours[i - 1]) / span
                return ((tablePercent[i - 1] + (tablePercent[i] - tablePercent[i - 1]) * t) / 100.0).coerceIn(0.0, 1.0)
            }
        }
        return (tablePercent[n - 1] / 100.0).coerceIn(0.0, 1.0)
    }

    /**
     * The draw due at or after (strictly after when exclusive) this many active ticks: one at
     * firstHours, then one every everyHours, all strictly before guaranteeHours; then the
     * guarantee itself.
     */
    @JvmStatic
    fun nextDraw(activeTicks: Long, firstHours: Double, everyHours: Double, guaranteeHours: Double, exclusive: Boolean): Long {
        val guarantee = ticks(guaranteeHours)
        val first = ticks(firstHours)
        val every = maxOf(1L, ticks(everyHours))
        var i = if (activeTicks <= first) 0L else ceil((activeTicks - first).toDouble() / every).toLong()
        var at = first + i * every
        while (at < activeTicks || (exclusive && at == activeTicks)) {
            i++
            at = first + i * every
        }
        // Past the last regular draw: the guarantee (already due when the counter is beyond it).
        return if (at < guarantee) at else guarantee
    }

    /** Ticks of presence a check may add: the time since the previous check, at most twice the interval. */
    @JvmStatic
    fun credit(elapsedTicks: Long, checkSeconds: Int): Long = elapsedTicks.coerceIn(0L, 2L * checkSeconds * 20L)

    /** The tick (0 to period - 1) of each period at which this player's check falls, spread by their UUID. */
    @JvmStatic
    fun slot(uuidMost: Long, uuidLeast: Long, periodTicks: Int): Int {
        val h = uuidMost xor uuidLeast
        val mixed = (h xor (h ushr 32)).toInt() * -0x61c88647
        return Math.floorMod(mixed, maxOf(1, periodTicks))
    }

    enum class AfkStatus {
        /** Counts. */
        ACTIVE,

        /** Nothing done for afkSeconds (rule a). */
        IDLE,

        /** Not moved minMoveBlocks for stillMinutes, without an exemption (rule b). */
        STILL,
    }

    /**
     * What the server remembers of a connected player between two checks (about 40 bytes, never
     * saved): their last sample, the anchor of their last real move, and the times of their last
     * change and last move.
     */
    class Activity(nowMs: Long, x: Double, y: Double, z: Double, yaw: Float, pitch: Float, var lastCheckTick: Long) {
        var lastX = x
        var lastY = y
        var lastZ = z
        var lastYaw = yaw
        var lastPitch = pitch
        var anchorX = x
        var anchorY = y
        var anchorZ = z
        var lastBattleKey = 0L

        /** Last change seen between two samples (position, view, battle turn); being seen the first time counts. */
        var lastChangeMs = nowMs

        /** Last time the player was minMoveBlocks away from their anchor. */
        var lastMoveMs = nowMs

        /** A new sample (one per check). battleKey: 0 out of a battle, otherwise changes with every turn. */
        fun observe(nowMs: Long, x: Double, y: Double, z: Double, yaw: Float, pitch: Float, battleKey: Long, minMoveBlocks: Double) {
            val moved = sq(x - lastX) + sq(y - lastY) + sq(z - lastZ) > 0.01
            val looked = abs(wrap(yaw - lastYaw)) > 0.5f || abs(pitch - lastPitch) > 0.5f
            val fought = battleKey != 0L && battleKey != lastBattleKey
            if (moved || looked || fought) lastChangeMs = nowMs
            if (sq(x - anchorX) + sq(y - anchorY) + sq(z - anchorZ) >= minMoveBlocks * minMoveBlocks) {
                anchorX = x
                anchorY = y
                anchorZ = z
                lastMoveMs = nowMs
            }
            lastX = x
            lastY = y
            lastZ = z
            lastYaw = yaw
            lastPitch = pitch
            lastBattleKey = battleKey
        }

        private fun sq(v: Double) = v * v

        private fun wrap(degrees: Float): Float {
            var d = degrees % 360f
            if (d >= 180f) d -= 360f
            if (d < -180f) d += 360f
            return d
        }
    }

    /**
     * The away-from-keyboard rule. lastActionMs: Minecraft's idle timer (reset by actions, not by
     * walking or looking in 1.21.1, hence the samples of the Activity). exempt: fishing, battle or
     * riding (spares rule b only).
     */
    @JvmStatic
    fun afk(nowMs: Long, lastActionMs: Long, activity: Activity, exempt: Boolean, afkSeconds: Int, stillMinutes: Double): AfkStatus {
        val last = maxOf(lastActionMs, activity.lastChangeMs)
        if (nowMs - last >= afkSeconds * 1000L) return AfkStatus.IDLE
        if (!exempt && nowMs - activity.lastMoveMs > (stillMinutes * 60_000.0).toLong()) return AfkStatus.STILL
        return AfkStatus.ACTIVE
    }
}
