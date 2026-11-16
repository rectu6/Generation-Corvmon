package generations.gg.generations.core.generationscore.common.world.rks.machine

/**
 * What a sacrificed Psychic Pokémon gives to the M2 protocol (RKS-PLAN.md § 4.2), pure (checked
 * off-game on every Psychic species of Cobblemon): by its place in its evolution line, read from
 * its form (a pre-evolution? evolutions?), unless the configuration names its species.
 *
 * - the first of a line that evolves (Abra): firstStage (1);
 * - in the middle of a line (Kadabra): middleStage (2);
 * - the last of a line (Alakazam, Wobbuffet after Wynaut): finalStage (3);
 * - a Pokémon that does not evolve: noEvolution (2).
 */
object RksPoints {
    enum class Stage { FIRST, MIDDLE, FINAL, ALONE }

    fun stage(hasPreEvolution: Boolean, hasEvolutions: Boolean): Stage = when {
        hasPreEvolution && hasEvolutions -> Stage.MIDDLE
        hasPreEvolution -> Stage.FINAL
        hasEvolutions -> Stage.FIRST
        else -> Stage.ALONE
    }

    /** The points; override: the configuration's value for this species, if any. */
    fun points(stage: Stage, firstStage: Int, middleStage: Int, finalStage: Int, noEvolution: Int, override: Int? = null): Int =
        override ?: when (stage) {
            Stage.FIRST -> firstStage
            Stage.MIDDLE -> middleStage
            Stage.FINAL -> finalStage
            Stage.ALONE -> noEvolution
        }

    /**
     * Whether a species may be given: Psychic, none of the excluded labels or species, and in the
     * allowed list when it is not empty (ids as "cobblemon:abra", labels lower case).
     */
    fun eligible(
        psychic: Boolean, species: String, labels: Collection<String>,
        excludedLabels: Collection<String>, excludedSpecies: Collection<String>, allowedSpecies: Collection<String>,
    ): Boolean {
        if (!psychic) return false
        val id = normalise(species)
        if (excludedSpecies.any { normalise(it) == id }) return false
        if (allowedSpecies.isNotEmpty() && allowedSpecies.none { normalise(it) == id }) return false
        return labels.none { label -> excludedLabels.any { it.equals(label, ignoreCase = true) } }
    }

    fun normalise(species: String): String {
        val s = species.trim().lowercase()
        return if (':' in s) s else "cobblemon:$s"
    }

    /** What a sacrifice adds to the reserve (capped) and what is lost over the cap. */
    fun credit(energy: Int, required: Int, points: Int): IntArray {
        val room = (required - energy).coerceAtLeast(0)
        val added = minOf(room, points.coerceAtLeast(0))
        return intArrayOf(added, points.coerceAtLeast(0) - added)
    }
}
