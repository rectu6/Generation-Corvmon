package generations.gg.generations.core.generationscore.common.world.ultraspace.encounter

import java.util.Random

/**
 * The draw of the arrival's encounter (phase 5), apart from the game so that it can be
 * simulated (/ultrawarp encounter simulate, and outside the game). Three independent
 * chances, each its own:
 * 1. an Ultra Beast or an ordinary Pokémon (beastChance);
 * 2. an ordinary Pokémon is shiny by ordinaryShinyChance;
 * 3. an Ultra Beast is shiny by beastShinyChance only: an ordinary Pokémon's bonus never
 *    reaches it.
 */
object UltraEncounterDraw {
    class Candidate(val species: String, val weight: Float)

    class Result(
        val ultraBeast: Boolean,
        val species: String,
        val level: Int,
        val shiny: Boolean,
        /** The chance that applied to its shininess. */
        val shinyChance: Float,
    )

    fun draw(
        random: Random,
        beastChance: Float,
        ordinaryShinyChance: Float,
        beastShinyChance: Float,
        beasts: Map<String, Float>,
        ordinary: List<Candidate>,
        ordinaryLevel: IntRange,
        beastLevel: IntRange,
    ): Result? {
        val canBeast = beasts.values.any { it > 0f }
        if (canBeast && random.nextDouble() < beastChance) {
            val species = weighted(random, beasts.entries.map { Candidate(it.key, it.value) }) ?: return null
            return Result(true, species, pick(random, beastLevel), random.nextDouble() < beastShinyChance, beastShinyChance)
        }
        val species = weighted(random, ordinary) ?: return null
        return Result(false, species, pick(random, ordinaryLevel), random.nextDouble() < ordinaryShinyChance, ordinaryShinyChance)
    }

    private fun pick(random: Random, range: IntRange): Int = if (range.last <= range.first) range.first else range.first + random.nextInt(range.last - range.first + 1)

    private fun weighted(random: Random, candidates: List<Candidate>): String? {
        val usable = candidates.filter { it.weight > 0f }
        val total = usable.sumOf { it.weight.toDouble() }
        if (total <= 0.0) return null
        var roll = random.nextDouble() * total
        for (candidate in usable) {
            if (roll < candidate.weight) return candidate.species
            roll -= candidate.weight
        }
        return usable.last().species
    }
}
