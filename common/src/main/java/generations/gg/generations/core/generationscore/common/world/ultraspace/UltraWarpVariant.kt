package generations.gg.generations.core.generationscore.common.world.ultraspace

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.util.cobblemonResource
import generations.gg.generations.core.generationscore.common.world.level.block.shrines.CelestialAltarBlock
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.state.BlockState

/**
 * The two faces of the Celestial Altar, given by its IS_SUN state (set by the altar
 * itself from the time of day): the Sun asks for Solgaleo, the Moon for Lunala.
 */
enum class UltraWarpVariant(val species: ResourceLocation) {
    SUN(cobblemonResource("solgaleo")),
    MOON(cobblemonResource("lunala"));

    val key: String get() = name.lowercase()

    /** The required Pokémon's name (the species is known on both sides). */
    fun speciesName(): Component =
        PokemonSpecies.getByIdentifier(species)?.translatedName
            ?: Component.literal(species.path.replaceFirstChar { it.uppercase() })

    companion object {
        /** The altar's face now, or null when this is not a Celestial Altar. */
        fun of(state: BlockState): UltraWarpVariant? =
            if (state.block !is CelestialAltarBlock) null
            else if (state.getValue(CelestialAltarBlock.IS_SUN)) SUN else MOON
    }
}
