package generations.gg.generations.core.generationscore.common.world.recipe

import com.cobblemon.mod.common.Cobblemon.storage
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.pokemon.FossilRevivedEvent
import com.cobblemon.mod.common.api.fossil.Fossil
import com.cobblemon.mod.common.api.fossil.Fossils
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.item.PokemonItem
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import generations.gg.generations.core.generationscore.common.GenerationsCore
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import org.joml.Vector4f
import java.util.Optional

/**
 * A fossil restored by the RKS Machine (decision D4): the Pokémon of a Cobblemon fossil
 * (data/cobblemon/fossils), read from Cobblemon when it is made, so it is the very Pokémon
 * Cobblemon's restoration tank gives; it goes to the party, or the PC when the party is full,
 * and Cobblemon's FOSSIL_REVIVED event is posted as the tank does.
 */
data class FossilResult(val fossil: ResourceLocation, val displaySpecies: Optional<ResourceLocation>) : RksResult<FossilResult> {

    val definition: Fossil? get() = Fossils.getByIdentifier(fossil)

    /**
     * The restored Pokémon's species: Cobblemon's, or the recipe's "species" while Cobblemon's
     * fossils have not reached the client yet (a recipe viewer may list the recipes first).
     */
    val species: ResourceLocation?
        get() {
            val name = definition?.result?.species?.trim()?.lowercase() ?: return displaySpecies.orElse(null)
            return if (':' in name) ResourceLocation.tryParse(name) else ResourceLocation.fromNamespaceAndPath("cobblemon", name)
        }

    override val stack: ItemStack
        get() {
            val species = species?.let { PokemonSpecies.getByIdentifier(it) } ?: return ItemStack.EMPTY
            return PokemonItem.from(species, definition?.result?.aspects ?: emptySet(), 1, Vector4f(1f, 1f, 1f, 1f))
        }

    override fun type(): RksResultType<FossilResult> = GenerationsRksTypes.FOSSIL.value() as RksResultType<FossilResult>

    override fun process(player: Player, host: RksMachineHost, stack: ItemStack) {
        val server = player as? ServerPlayer ?: return
        val properties = definition?.result ?: run {
            GenerationsCore.LOGGER.error("RKS Machine: unknown Cobblemon fossil {}, nothing restored", fossil)
            return
        }
        val pokemon = properties.copy().create(server)
        CobblemonEvents.FOSSIL_REVIVED.post(FossilRevivedEvent(pokemon, server))
        if (!storage.getParty(server).add(pokemon)) storage.getPC(server).add(pokemon)
        stack.count = 0
    }

    override val isPokemon: Boolean = true

    companion object {
        val CODEC: MapCodec<FossilResult> = RecordCodecBuilder.mapCodec { builder ->
            builder.group(
                ResourceLocation.CODEC.fieldOf("fossil").forGetter(FossilResult::fossil),
                ResourceLocation.CODEC.optionalFieldOf("species").forGetter(FossilResult::displaySpecies),
            ).apply(builder, ::FossilResult)
        }

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, FossilResult> = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, FossilResult::fossil,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), FossilResult::displaySpecies,
            ::FossilResult,
        )
    }
}
