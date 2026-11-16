package generations.gg.generations.core.generationscore.common.world.cinematic

/**
 * A legendary cinematic's life on the server, without Minecraft (tested off-game): what to
 * do at each tick, and what an interruption leaves to do. Ticks are the session level's game
 * time. The Pokémon appears once at most, whatever happens (the spawn flag), and never in test
 * mode: 1 s (spawnLeadTicks) before the end, or at once when a scene is cut short before that.
 */
class CinematicStateMachine(
    val startTick: Long,
    val durationTicks: Int,
    val test: Boolean,
    val spawnLeadTicks: Int = 20,
) {
    enum class State { SCHEDULED, PLAYING, FINISHED, INTERRUPTED }

    enum class Action { SPAWN, FINISH }

    /** What an interruption leaves to do: spawnNow = the Pokémon must appear now (shrine mode, not there yet). */
    data class Interruption(val spawnNow: Boolean)

    var state = State.SCHEDULED
        private set

    /** The Pokémon appeared (or must, right now): never twice. */
    var spawned = false
        private set

    val endTick: Long get() = startTick + durationTicks

    val isOver: Boolean get() = state == State.FINISHED || state == State.INTERRUPTED

    init {
        require(durationTicks > spawnLeadTicks) { "a cinematic lasts longer than its spawn lead" }
    }

    /** This tick's actions, in order (the spawn before the end). Nothing once over. */
    fun tick(now: Long): List<Action> {
        if (isOver) return emptyList()
        if (state == State.SCHEDULED && now >= startTick) state = State.PLAYING
        var out: MutableList<Action>? = null
        if (!test && !spawned && now >= endTick - spawnLeadTicks) {
            spawned = true
            out = ArrayList(2)
            out += Action.SPAWN
        }
        if (now >= endTick) {
            state = State.FINISHED
            if (out == null) out = ArrayList(1)
            out += Action.FINISH
        }
        return out ?: emptyList()
    }

    /** Cut short: what is left to do the first time, null when it was already over. */
    fun interrupt(): Interruption? {
        if (isOver) return null
        state = State.INTERRUPTED
        val spawnNow = !test && !spawned
        if (spawnNow) spawned = true
        return Interruption(spawnNow)
    }

    /** Seconds since the start (negative before it). */
    fun elapsedSeconds(now: Long): Double = (now - startTick) / 20.0
}
