package generations.gg.generations.core.generationscore.common.world.mew

import java.util.function.DoubleSupplier

/**
 * The hunt's transitions, without Minecraft (tested off-game by tools/MewBench.java). Every
 * transition exists from one precise state only, so a second call (a repeated command, two
 * events in the same tick, a reconnection) changes nothing.
 *
 * HUNTING → UNLOCKED (a draw) → CHALLENGE → WON → RELEASED → HUNTING (caught: a new hunt), with
 * CHALLENGE → COOLDOWN → CHALLENGE after a failure (no new draw) and RELEASED → HUNTING or
 * COOLDOWN when the Mew is lost (lostMew). The false appearances' countdown too.
 */
class MewStateMachine(val rules: Rules) {
    /** The hunt's numbers (MewConfig.Hunt.rules()). */
    class Rules(
        val firstDrawHours: Double,
        val drawEveryHours: Double,
        val guaranteeHours: Double,
        val tableHours: DoubleArray,
        val tablePercent: DoubleArray,
    ) {
        val guaranteeTicks: Long get() = MewMath.ticks(guaranteeHours)

        fun chance(hours: Double): Double = MewMath.chance(tableHours, tablePercent, hours)
    }

    /** One draw: at this many hours, with this chance; roll is the random number drawn (1 for the guarantee). */
    class Draw(val hours: Double, val chance: Double, val roll: Double, val success: Boolean, val guaranteed: Boolean)

    /** What adding presence did: the draws made (in order), and whether the hunt is now unlocked. */
    class Outcome(val draws: List<Draw>, val unlocked: Boolean) {
        companion object {
            val NONE = Outcome(emptyList(), false)
        }
    }

    /** The draw due at or after the counter (when none is planned yet). */
    fun schedule(record: MewRecord) {
        if (record.nextDrawTicks < 0L) record.nextDrawTicks = next(record.activeTicks, exclusive = false)
    }

    private fun next(ticks: Long, exclusive: Boolean): Long =
        MewMath.nextDraw(ticks, rules.firstDrawHours, rules.drawEveryHours, rules.guaranteeHours, exclusive)

    /**
     * Active presence on an island while hunting: the counter goes up, then every draw now due is
     * made in order until one succeeds (normally at most one per check). Nothing in another state.
     */
    fun addActive(record: MewRecord, ticks: Long, random: DoubleSupplier): Outcome {
        if (record.state != MewState.HUNTING || ticks <= 0L) return Outcome.NONE
        schedule(record)
        record.activeTicks += ticks
        var draws: MutableList<Draw>? = null
        var guard = 0
        while (record.state == MewState.HUNTING && record.activeTicks >= record.nextDrawTicks && guard++ < 10_000) {
            val draw = drawAt(record.nextDrawTicks, random)
            record.draws++
            (draws ?: ArrayList<Draw>().also { draws = it }).add(draw)
            if (draw.success) {
                unlock(record)
                break
            }
            record.nextDrawTicks = next(record.nextDrawTicks, exclusive = true)
        }
        return draws?.let { Outcome(it, record.state == MewState.UNLOCKED) } ?: Outcome.NONE
    }

    private fun drawAt(ticks: Long, random: DoubleSupplier): Draw {
        val hours = MewMath.hours(ticks)
        if (ticks >= rules.guaranteeTicks) return Draw(hours, 1.0, 1.0, success = true, guaranteed = true)
        val chance = rules.chance(hours)
        val roll = random.asDouble
        return Draw(hours, chance, roll, roll < chance, guaranteed = false)
    }

    /**
     * A draw now on the real counter (the draw command), with the chance of the current hours (the
     * guarantee beyond it); the schedule is not moved. Null when not hunting.
     */
    fun forceDraw(record: MewRecord, random: DoubleSupplier): Draw? {
        if (record.state != MewState.HUNTING) return null
        schedule(record)
        val draw = drawAt(record.activeTicks, random)
        record.draws++
        if (draw.success) unlock(record)
        return draw
    }

    /** HUNTING → UNLOCKED, once: false from any other state. */
    fun unlock(record: MewRecord): Boolean {
        if (record.state != MewState.HUNTING) return false
        record.state = MewState.UNLOCKED
        record.unlockedAtTicks = record.activeTicks
        return true
    }

    /** The counter set by hand (hunt set / add): the next draw is the first one due at or after it; no draw is made here. */
    fun setActive(record: MewRecord, ticks: Long) {
        record.activeTicks = ticks.coerceAtLeast(0L)
        record.nextDrawTicks = next(record.activeTicks, exclusive = false)
    }

    /**
     * The settings were read again (server start, reload): a hunting record's next draw follows the
     * current schedule, strictly after its counter (every draw due up to it has been made).
     */
    fun reschedule(record: MewRecord) {
        if (record.state == MewState.HUNTING) record.nextDrawTicks = next(record.activeTicks, exclusive = record.activeTicks > 0L)
    }

    /** A new hunt from zero (after a capture, or the reset command): only the number of hunts completed stays. */
    fun reset(record: MewRecord) {
        record.state = MewState.HUNTING
        record.activeTicks = 0L
        record.nextDrawTicks = next(0L, exclusive = false)
        record.draws = 0
        record.nextManifestTicks = -1L
        record.cooldownTicks = 0L
        record.attempts = 0
        record.mewUuid = ""
        record.releasedAtMs = 0L
        record.unlockedAtTicks = -1L
    }

    /** A new hunter's record, its first draw planned. */
    fun create(): MewRecord = MewRecord().also { schedule(it) }

    // ---------------------------------------------------------------- after the unlock (builds 60 to 63)

    /**
     * The challenge starts: from UNLOCKED (Mew's first visit) or from COOLDOWN once its wait is
     * over (Mew comes back); never twice. No draw is made again.
     */
    fun startChallenge(record: MewRecord): Boolean {
        val ok = record.state == MewState.UNLOCKED || (record.state == MewState.COOLDOWN && record.cooldownTicks <= 0L)
        if (!ok) return false
        record.state = MewState.CHALLENGE
        record.cooldownTicks = 0L
        record.attempts++
        return true
    }

    /** The challenge failed or was interrupted (D6): CHALLENGE → COOLDOWN with this many active ticks to wait. */
    fun fail(record: MewRecord, waitTicks: Long): Boolean {
        if (record.state != MewState.CHALLENGE) return false
        record.state = MewState.COOLDOWN
        record.cooldownTicks = waitTicks.coerceAtLeast(1L)
        return true
    }

    /** Active presence while waiting: the wait goes down. True when it is over (Mew may come back). */
    fun addCooldown(record: MewRecord, ticks: Long): Boolean {
        if (record.state != MewState.COOLDOWN) return false
        if (ticks > 0L) record.cooldownTicks = (record.cooldownTicks - ticks).coerceAtLeast(0L)
        return record.cooldownTicks <= 0L
    }

    /** The wait set by hand (the cooldown command), in COOLDOWN only. */
    fun setCooldown(record: MewRecord, ticks: Long): Boolean {
        if (record.state != MewState.COOLDOWN) return false
        record.cooldownTicks = ticks.coerceAtLeast(0L)
        return true
    }

    /** The three rounds won: CHALLENGE → WON, once. */
    fun win(record: MewRecord): Boolean {
        if (record.state != MewState.CHALLENGE) return false
        record.state = MewState.WON
        return true
    }

    /** The real Mew is out: WON → RELEASED, once (saved before it appears). */
    fun release(record: MewRecord, mewUuid: String, nowMs: Long): Boolean {
        if (record.state != MewState.WON) return false
        record.state = MewState.RELEASED
        record.mewUuid = mewUuid
        record.releasedAtMs = nowMs
        return true
    }

    /** Its hunter caught it: a new hunt from zero, one more completed. RELEASED only. */
    fun caught(record: MewRecord): Boolean {
        if (record.state != MewState.RELEASED) return false
        reset(record)
        record.completed++
        return true
    }

    /**
     * The Mew was lost (caught by someone else, fainted, gone, not caught in time): "reset" starts a
     * new hunt, "retry" waits again then replays the challenge (D8). RELEASED only.
     */
    fun lost(record: MewRecord, retry: Boolean, waitTicks: Long): Boolean {
        if (record.state != MewState.RELEASED) return false
        if (retry) {
            record.state = MewState.COOLDOWN
            record.cooldownTicks = waitTicks.coerceAtLeast(1L)
            record.mewUuid = ""
            record.releasedAtMs = 0L
        } else {
            reset(record)
        }
        return true
    }

    /**
     * The next false appearance (§ 6): a countdown of active ticks (the first one drawn at random
     * within an interval, so that players do not all see one at once), then a draw of the chance.
     * True when one should be shown now. HUNTING and COOLDOWN only.
     */
    fun manifestation(record: MewRecord, ticks: Long, intervalTicks: Long, chance: Double, random: DoubleSupplier): Boolean {
        if (record.state != MewState.HUNTING && record.state != MewState.COOLDOWN) return false
        val interval = intervalTicks.coerceAtLeast(20L)
        if (record.nextManifestTicks < 0L) record.nextManifestTicks = (random.asDouble * interval).toLong().coerceIn(0L, interval - 1L)
        record.nextManifestTicks -= ticks.coerceAtLeast(0L)
        if (record.nextManifestTicks > 0L) return false
        record.nextManifestTicks += interval
        if (record.nextManifestTicks <= 0L) record.nextManifestTicks = interval
        return random.asDouble < chance
    }
}
