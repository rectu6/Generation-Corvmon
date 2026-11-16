package generations.gg.generations.core.generationscore.common.world.catchcombo

import java.util.Random
import java.util.UUID

/**
 * The Catch Combo's rules apart from the game (tested and simulated outside it):
 * tiers, guaranteed IVs and the state machine of one player's combo.
 */
object CatchComboMath {
    /** From threshold captures in a row: shiny bonus (×) and number of IVs at 31. */
    class Tier(var threshold: Int = 1, var shinyMultiplier: Double = 1.0, var perfectIvs: Int = 0)

    /** Sorted by threshold, thresholds at least 1, duplicates (same threshold) keep the last. */
    fun sorted(tiers: List<Tier>): List<Tier> =
        tiers.filter { it.threshold >= 1 }.groupBy { it.threshold }.map { it.value.last() }.sortedBy { it.threshold }

    /** The tier reached with this count (tiers sorted), null below the first. */
    fun tier(tiers: List<Tier>, count: Int): Tier? = tiers.lastOrNull { it.threshold <= count }

    /** The next tier above this count, null when the last one is reached. */
    fun next(tiers: List<Tier>, count: Int): Tier? = tiers.firstOrNull { it.threshold > count }

    /**
     * At least wanted of the six IVs at 31: only stats under 31 are raised, chosen at
     * random; an IV is never lowered, stats already at 31 count. Returns the new values
     * (same order as given) and how many were raised.
     */
    fun guaranteeIvs(ivs: IntArray, wanted: Int, random: Random): Pair<IntArray, Int> {
        val result = ivs.copyOf()
        val need = wanted.coerceIn(0, result.size) - result.count { it >= 31 }
        if (need <= 0) return result to 0
        val below = result.indices.filter { result[it] < 31 }.toMutableList()
        // Fisher–Yates on the stats under 31, then the first ones raised.
        for (i in below.size - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val swap = below[i]; below[i] = below[j]; below[j] = swap
        }
        var raised = 0
        for (index in below.take(need)) {
            result[index] = 31
            raised++
        }
        return result to raised
    }

    /** One player's combo. */
    class State {
        var species: String? = null
        var count = 0
        /** The last captures counted (a capture is counted once, whatever happens). */
        val seen = ArrayDeque<UUID>()

        fun reset() {
            species = null
            count = 0
        }
    }

    enum class Change {
        /** Nothing changed (a capture already counted, a break with no combo). */
        NONE,
        /** First capture of a combo (no combo before). */
        START,
        /** One more of the same species. */
        UP,
        /** Another species: the combo restarts with it at 1 (breakOnOtherSpecies). */
        SWITCH,
        /** A break: the combo is lost. */
        BREAK,
    }

    const val SEEN_LIMIT = 32

    /** A capture of this species (combo key) and this Pokémon (its UUID). */
    fun capture(state: State, species: String, pokemon: UUID, maxCount: Int, breakOnOtherSpecies: Boolean): Change {
        if (pokemon in state.seen) return Change.NONE
        state.seen.addLast(pokemon)
        while (state.seen.size > SEEN_LIMIT) state.seen.removeFirst()
        val current = state.species
        return when {
            current == null || state.count <= 0 -> {
                state.species = species
                state.count = 1
                Change.START
            }
            current == species -> {
                state.count = (state.count + 1).coerceAtMost(maxCount.coerceAtLeast(1))
                Change.UP
            }
            breakOnOtherSpecies -> {
                state.species = species
                state.count = 1
                Change.SWITCH
            }
            else -> Change.NONE
        }
    }

    /** A break (K.O., PvP battle, battle stopped…): the combo is lost. */
    fun breakCombo(state: State): Change {
        if (state.species == null || state.count <= 0) return Change.NONE
        state.reset()
        return Change.BREAK
    }
}
