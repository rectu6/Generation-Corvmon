package generations.gg.generations.core.generationscore.common.network.packets

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.api.pokemon.feature.FlagSpeciesFeature
import com.cobblemon.mod.common.api.pokemon.feature.SpeciesFeature
import com.cobblemon.mod.common.api.pokemon.feature.StringSpeciesFeature
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import generations.gg.generations.core.generationscore.common.battle.MegaEvolutionAnimation
import generations.gg.generations.core.generationscore.common.util.applyCosmeticFeature
import generations.gg.generations.core.generationscore.common.util.removeCosmeticFeature
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

object GensInteractPokemonHandler : ServerNetworkPacketHandler<GensInteractPokemonPacket> {
    private val MEGA_ASPECTS = setOf("mega", "mega_x", "mega_y")

    /** Pokémon playing the Mega Evolution animation: further requests wait for its end. */
    private val megaEvolving = mutableSetOf<UUID>()

    override fun handle(packet: GensInteractPokemonPacket, server: MinecraftServer, player: ServerPlayer) {
        val pokemonEntity = player.serverLevel().getEntity(packet.pokemonID)
        if (pokemonEntity is PokemonEntity && !pokemonEntity.isBattleClone()) {
            if (packet.changeFormData == "revert") {
                pokemonEntity.pokemon.removeCosmeticFeature()
            } else {
                val feature: SpeciesFeature =
                    if (packet.changeFormData != "ultra") FlagSpeciesFeature(packet.changeFormData, true)
                    else StringSpeciesFeature("prism_fusion", packet.changeFormData)

                if (packet.changeFormData in MEGA_ASPECTS) megaEvolve(pokemonEntity, feature)
                else pokemonEntity.pokemon.applyCosmeticFeature(feature)
            }
        }
    }

    /** Same animation as in battle: the Pokémon stops and its Mega form appears at the burst. */
    private fun megaEvolve(entity: PokemonEntity, feature: SpeciesFeature) {
        if (!megaEvolving.add(entity.uuid)) return
        entity.navigation.stop()
        MegaEvolutionAnimation.play(entity)
        entity.after(MegaEvolutionAnimation.REVEAL_SECONDS) {
            if (entity.isAlive) entity.pokemon.applyCosmeticFeature(feature)
        }
        entity.after(MegaEvolutionAnimation.LENGTH_SECONDS) { megaEvolving.remove(entity.uuid) }
    }
}
