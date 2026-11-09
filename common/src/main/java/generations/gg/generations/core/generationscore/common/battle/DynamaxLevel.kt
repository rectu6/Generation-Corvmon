package generations.gg.generations.core.generationscore.common.battle

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.feature.IntSpeciesFeature
import com.cobblemon.mod.common.pokemon.Pokemon

/**
 * Dynamax Level (0 to 10) and G-Max Factor. Cobblemon stores both on the Pokémon
 * (dmaxLevel, gmaxFactor) and sends them to Showdown: the level raises the HP of a
 * Dynamaxed Pokémon, the factor makes it Gigantamax. The level is also kept in the
 * "dynamax_level" feature, shown as a bar in the summary
 * (data/generations_core/global_species_features/dynamax_level.json).
 */
object DynamaxLevel {
    const val FEATURE = "dynamax_level"

    @JvmStatic
    fun max(): Int = Cobblemon.config.maxDynamaxLevel

    @JvmStatic
    fun get(pokemon: Pokemon): Int = pokemon.getFeature<IntSpeciesFeature>(FEATURE)?.value ?: pokemon.dmaxLevel

    @JvmStatic
    fun set(pokemon: Pokemon, level: Int) {
        val value = level.coerceIn(0, max())
        pokemon.dmaxLevel = value
        val feature = pokemon.getFeature<IntSpeciesFeature>(FEATURE)
            ?: IntSpeciesFeature(FEATURE, value).also { (pokemon.features as MutableList).add(it) }
        feature.value = value
        pokemon.markFeatureDirty(feature)
    }

    /** Before a battle: Showdown reads dmaxLevel, the summary bar is the reference. */
    @JvmStatic
    fun sync(pokemon: Pokemon) {
        val level = get(pokemon)
        if (pokemon.dmaxLevel != level) pokemon.dmaxLevel = level
    }

    /** Species with a G-Max form (Cobblemon tags it with the "gmax" aspect). */
    @JvmStatic
    fun canGigantamax(pokemon: Pokemon): Boolean = pokemon.species.forms.any { "gmax" in it.aspects }
}
