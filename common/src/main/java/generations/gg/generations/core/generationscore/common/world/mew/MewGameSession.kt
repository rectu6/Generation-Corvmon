package generations.gg.generations.core.generationscore.common.world.mew

import java.util.UUID

/**
 * One player's challenge (MEW-PLAN.md § 8), without Minecraft (tested off-game): the opening, the
 * rounds in order, the step number that makes a stale click harmless, the errors of round 2, the
 * course of round 3, the time limit, leaving the area. Times are server ticks. The places come
 * from MewPlaces (MewGame asks for them when an event says so); the session only decides.
 *
 * A click carries the session id and the step its client saw: an older step, another session or
 * a click faster than every interactTicks is ignored.
 */
class MewGameSession(
    val id: Int,
    val player: UUID,
    /** A test copy (commands): the real progress is never touched. */
    val test: Boolean,
    val rules: Rules,
    now: Long,
    /** Mew comes back after a failure (a short scene of 3 s instead of the opening's 6.5 s). */
    val comeback: Boolean = false,
    /** Start straight at that round (test command); 0 = the configured order. */
    firstRound: Int = 0,
) {
    class Rules(
        val rounds: IntArray,
        val maxTicks: Long,
        val leaveTicks: Long,
        val catchDistance: Double,
        val interactDistance: Double = 6.0,
        val openingTicks: Long = 130L,
        val returnTicks: Long = 60L,
        val stuckTicks: Long = 800L,
        val interactTicks: Long = 4L,
        val maxErrors: Int = 3,
    )

    enum class Phase { OPENING, WAITING, ROUND, DONE }

    enum class Outcome { WON, TIME, GAVE_UP, LEFT, DISCONNECTED, DIED, DIMENSION, STOPPED }

    enum class Event {
        /** The next round's places are needed (MewGame calls beginRound). */
        NEED_ROUND,

        /** Round 2: a wrong copy; the hint grows. */
        ERROR,

        /** Round 2: third error, the copies are shuffled (MewGame calls beginRound again with new places). */
        RESHUFFLE,

        /** Round 3: Mew flies to its next point. */
        MOVE,

        /** Round 3: stuck for a while, Mew comes back toward the player (MewGame calls comeBack). */
        STUCK,

        /** Out of the area: one warning, then a failure after leaveTicks. */
        WARN,

        /** Over (see outcome). */
        END,
    }

    var phase = Phase.OPENING
        private set
    /** Index in rules.rounds of the round being played. */
    var roundIndex = 0
        private set
    var round = 0
        private set
    /** Changes with every change the client must follow. */
    var step = 0
        private set
    val startTick = now
    var phaseTick = now
        private set
    var roundTick = now
        private set
    /** This round's places: round 1 the hiding spot, round 2 the copies, round 3 the course (x, y, z each). */
    val points = ArrayList<DoubleArray>()
    var real = 0
        private set
    var errors = 0
        private set
    var course = 0
        private set
    private var progressTick = now
    private var outsideTick = -1L
    private var lastClick = Long.MIN_VALUE / 2
    var outcome: Outcome? = null
        private set
    /** The rounds won (round 2 copies shuffled count separately). */
    var reshuffles = 0
        private set

    init {
        if (firstRound in 1..3) {
            val i = rules.rounds.indexOf(firstRound)
            roundIndex = if (i >= 0) i else 0
        }
    }

    private val introTicks: Long get() = if (comeback) rules.returnTicks else rules.openingTicks

    /** Ticks left before the time limit (which starts after the opening). */
    fun ticksLeft(now: Long): Long = (startTick + introTicks + rules.maxTicks - now).coerceAtLeast(0L)

    val lastPoint: Boolean get() = course >= points.size - 1

    fun end(outcome: Outcome): Boolean {
        if (phase == Phase.DONE) return false
        phase = Phase.DONE
        this.outcome = outcome
        step++
        return true
    }

    /**
     * The places of the next round (or the same round shuffled): round 2's real copy is `real`.
     * From OPENING, WAITING or (a reshuffle, a test) ROUND.
     */
    fun beginRound(now: Long, places: List<DoubleArray>, real: Int): Boolean {
        if (phase == Phase.DONE || places.isEmpty()) return false
        val reshuffle = phase == Phase.ROUND && round == rules.rounds[roundIndex] && round == 2
        round = rules.rounds[roundIndex]
        phase = Phase.ROUND
        points.clear()
        points.addAll(places)
        this.real = real.coerceIn(0, places.size - 1)
        if (!reshuffle) {
            roundTick = now
            errors = 0
        } else {
            reshuffles++
            errors = 0
        }
        course = 0
        progressTick = now
        phaseTick = now
        step++
        return true
    }

    /** Round 3, stuck: Mew's current point moved toward the player. */
    fun comeBack(now: Long, place: DoubleArray) {
        if (phase != Phase.ROUND || round != 3 || course >= points.size) return
        points[course] = place
        progressTick = now
        step++
    }

    private fun roundWon(now: Long): Event {
        roundIndex++
        if (roundIndex >= rules.rounds.size) {
            end(Outcome.WON)
            return Event.END
        }
        phase = Phase.WAITING
        phaseTick = now
        step++
        return Event.NEED_ROUND
    }

    /**
     * Every tick of the session (MewGame: every 5 ticks is enough): the time limit, the opening,
     * leaving the area, round 3's chase. inArea: within the game's circle and the island.
     */
    fun tick(now: Long, x: Double, y: Double, z: Double, inArea: Boolean): Event? {
        if (phase == Phase.DONE) return null
        if (now >= startTick + introTicks + rules.maxTicks) {
            end(Outcome.TIME)
            return Event.END
        }
        if (phase == Phase.OPENING) {
            if (now - phaseTick >= introTicks) {
                phase = Phase.WAITING
                return Event.NEED_ROUND
            }
            return null
        }
        if (!inArea) {
            if (outsideTick < 0L) {
                outsideTick = now
                return Event.WARN
            }
            if (now - outsideTick >= rules.leaveTicks) {
                end(Outcome.LEFT)
                return Event.END
            }
        } else {
            outsideTick = -1L
        }
        if (phase == Phase.ROUND && round == 3 && points.isNotEmpty()) {
            val p = points[course.coerceAtMost(points.size - 1)]
            val dx = p[0] - x
            val dy = p[1] - y
            val dz = p[2] - z
            if (!lastPoint && dx * dx + dy * dy + dz * dz <= rules.catchDistance * rules.catchDistance) {
                course++
                progressTick = now
                step++
                return Event.MOVE
            }
            if (now - progressTick >= rules.stuckTicks) {
                progressTick = now
                return Event.STUCK
            }
        }
        return null
    }

    /**
     * A click on an illusion (target: its index; 0 for the only Mew of rounds 1 and 3). distance:
     * from the player's eyes to it, measured by the server. Null when ignored.
     */
    fun interact(now: Long, session: Int, clientStep: Int, target: Int, distance: Double): Event? {
        if (session != id || clientStep != step || phase != Phase.ROUND) return null
        if (now - lastClick < rules.interactTicks) return null
        if (!distance.isFinite() || distance > rules.interactDistance) return null
        if (target < 0 || target >= points.size) return null
        lastClick = now
        return when (round) {
            1 -> roundWon(now)
            2 -> {
                if (target == real) roundWon(now)
                else {
                    errors++
                    step++
                    if (errors >= rules.maxErrors) Event.RESHUFFLE else Event.ERROR
                }
            }
            else -> if (lastPoint && target == course) roundWon(now) else null
        }
    }
}
