package generations.gg.generations.core.generationscore.common.world.ultraspace.rift

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.isInBattle
import com.cobblemon.mod.common.util.party
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.world.ultraspace.UltraWarpSessions
import generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.UltraWarpTransfers
import generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.UltraWorlds
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * "Back to the Overworld" on the interaction wheel of the player's Solgaleo or Lunala
 * (phase 6). The client only names the Pokémon; everything is checked here, then the
 * existing return (UltraWarpTransfers.returnHome) takes the player back where their
 * journey began (UltraWarpTravellers.home), safely.
 */
object UltraReturn {
    private val lastRequest = HashMap<UUID, Int>()

    fun request(player: ServerPlayer, entity: UUID) {
        val reason = refusal(player, entity)
        if (reason != null) {
            player.sendSystemMessage(Component.translatable("generations_core.ultra_return.refused.$reason"))
            return
        }
        lastRequest[player.uuid] = player.server.tickCount
        val hold = (UltraRiftsConfig.wheelReturn.transitionSeconds * 20).toInt()
        if (!UltraWarpTransfers.returnHome(player, hold)) {
            player.sendSystemMessage(Component.translatable("generations_core.ultra_return.refused.busy"))
            return
        }
        GenerationsCore.LOGGER.info("Ultra Space: {} goes back from {} through their wheel", player.gameProfile.name, player.level().dimension().location())
    }

    /** Why not now (a key of the messages), null when all is met. */
    fun refusal(player: ServerPlayer, entity: UUID): String? {
        val config = UltraRiftsConfig.wheelReturn
        val now = player.server.tickCount
        return when {
            !config.enabled -> "disabled"
            !player.isAlive || player.isSpectator -> "unavailable"
            !UltraWorlds.isUltra(player.level().dimension()) -> "not_ultra"
            UltraWarpTransfers.busy(player.uuid) || UltraWarpSessions.get(player.uuid) != null -> "busy"
            (lastRequest[player.uuid] ?: Int.MIN_VALUE / 2) + config.cooldownSeconds * 20 > now -> "cooldown"
            config.refuseInBattle && player.isInBattle() -> "battle"
            else -> {
                val pokemon = player.serverLevel().getEntity(entity) as? PokemonEntity
                when {
                    pokemon == null || !pokemon.isAlive -> "pokemon"
                    pokemon.pokemon.species.resourceIdentifier.toString() !in config.species -> "pokemon"
                    pokemon.pokemon.getOwnerUUID() != player.uuid || player.party().none { it.uuid == pokemon.pokemon.uuid } -> "owner"
                    pokemon.distanceToSqr(player) > config.maxDistance * config.maxDistance -> "far"
                    config.refuseInBattle && pokemon.isBattling -> "battle"
                    else -> null
                }
            }
        }
    }
}
