package generations.gg.generations.core.generationscore.common.event

import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.api.events.pokemon.HeldItemEvent
import com.cobblemon.mod.common.api.moves.BenchedMove
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.pokemon.feature.StringSpeciesFeature
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.api.types.tera.TeraTypes
import com.cobblemon.mod.common.net.messages.client.pokemon.update.BenchedMovesUpdatePacket
import com.cobblemon.mod.common.pokemon.Pokemon
import generations.gg.generations.core.generationscore.common.util.removeMove
import generations.gg.generations.core.generationscore.common.world.item.GenerationsItems
import net.minecraft.core.Holder
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.Item

object HeldItemFormeChange {
    fun ogerMaskChange(post: HeldItemEvent.Post) {
        val pokemon = post.pokemon

        if (pokemon.species.name != "Ogerpon") return

        val player: ServerPlayer? = pokemon.getOwnerPlayer()

        when {
            post.received.`is`(GenerationsItems.CORNERSTONE_MASK) -> {
                StringSpeciesFeature("ogre_mask", "cornerstone").apply(pokemon)
                pokemon.teraType = TeraTypes.ROCK
                player?.sendSystemMessage("Ogerpon's Tera Type has been set to Rock".text())
            }

            post.received.`is`(GenerationsItems.HEARTHFLAME_MASK) -> {
                StringSpeciesFeature("ogre_mask", "hearthflame").apply(pokemon)
                pokemon.teraType = TeraTypes.FIRE
                player?.sendSystemMessage("Ogerpon's Tera Type has been set to Fire".text())
            }

            post.received.`is`(GenerationsItems.WELLSPRING_MASK) -> {
                StringSpeciesFeature("ogre_mask", "wellspring").apply(pokemon)
                pokemon.teraType = TeraTypes.WATER
                player?.sendSystemMessage("Ogerpon's Tera Type has been set to Water".text())
            }

            setOf(
                GenerationsItems.CORNERSTONE_MASK,
                GenerationsItems.HEARTHFLAME_MASK,
                GenerationsItems.WELLSPRING_MASK
            ).map(Holder<Item>::value).contains(post.returned.item) -> {
                StringSpeciesFeature("ogre_mask", "teal").apply(pokemon)
                pokemon.teraType = TeraTypes.GRASS
                player?.sendSystemMessage("Ogerpon's Tera Type has been set to Grass".text())
            }
        }
    }

    fun removeBehemoth(post: HeldItemEvent.Post) {
        val pokemon = post.pokemon

        if (pokemon.species.name != "Zacian" && pokemon.species.name != "Zamazenta") return

        val player = pokemon.getOwnerPlayer()
        val benchedMoves = pokemon.benchedMoves

        when {
            post.received.`is`(GenerationsItems.CROWNED_SWORD)-> {
                for (benchedMove in benchedMoves) {
                    if (benchedMove.moveTemplate.name == "behemothblade") {
                        benchedMoves.remove(benchedMove)
                    }
                }
            }

            post.received.`is`(GenerationsItems.CROWNED_SHIELD)-> {
                for (benchedMove in benchedMoves) {
                    if (benchedMove.moveTemplate.name == "behemothbash") {
                        benchedMoves.remove(benchedMove)
                    }
                }
            }

            setOf(
                GenerationsItems.CROWNED_SWORD,
                GenerationsItems.CROWNED_SHIELD,
            ).map(Holder<Item>::value).contains(post.returned.item) -> {
                if (player != null) {
                    CobblemonNetwork.sendPacketToPlayer(player, BenchedMovesUpdatePacket({ pokemon }, benchedMoves))
                }
            }
        }
    }
}
