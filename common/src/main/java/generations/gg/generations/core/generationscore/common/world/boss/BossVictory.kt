package generations.gg.generations.core.generationscore.common.world.boss

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent
import com.cobblemon.mod.common.api.scheduling.afterOnServer
import com.cobblemon.mod.common.battles.actor.PokemonBattleActor
import com.cobblemon.mod.common.pokemon.Pokemon
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.network.packets.boss.S2CBossVictoryPacket
import generations.gg.generations.core.generationscore.common.network.packets.boss.S2CBossVictoryPacket.StoneResult
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.RandomSource
import net.minecraft.world.item.ItemStack
import java.util.UUID

/**
 * A boss beaten in battle (BATTLE_VICTORY, its Pokémon among the losers, not caught):
 * its rewards to the winning player, once per boss (flag in BossData and a set kept
 * while the server runs), then the victory screen (S2CBossVictoryPacket), sent once
 * Cobblemon's battle screen has closed. Fleeing, losing or a boss removed: nothing.
 *
 * Mega Stone (Legendary bosses, which are Mega forms): the stone of the form beaten,
 * with firstVictoryChance when the player never got that stone from a boss, else
 * repeatChance (BossRewards.megaStones).
 */
object BossVictory {
    /** Seconds before the victory screen: Cobblemon's battle screen closes first. */
    private const val SCREEN_DELAY = 2.5f

    private val random = RandomSource.create()
    private val rewarded = HashSet<UUID>()

    fun init() {
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL) { victory(it) }
    }

    fun reset() = rewarded.clear()

    private fun victory(event: BattleVictoryEvent) {
        if (event.wasWildCapture || !BossConfig.general.enabled) return
        val winners = event.winners.flatMap { it.getPlayerUUIDs() }.toSet()
        val players = event.battle.players.filter { it.uuid in winners }
        if (players.isEmpty()) return
        for (loser in event.losers) {
            val actor = loser as? PokemonBattleActor ?: continue
            val pokemon = actor.pokemon.originalPokemon
            val data = Bosses.of(pokemon) ?: continue
            if (!rewarded.add(data.id) || !BossData.markRewarded(pokemon.persistentData)) continue
            for (player in players) {
                reward(player, data.rarity, data.titan, pokemon)
                GenerationsCore.LOGGER.info("Bosses: {} beat a {} {}", player.gameProfile.name, data.rarity.id, pokemon.species.name)
            }
        }
    }

    /**
     * Draws and gives the rewards of a boss of this rarity, the Mega Stone of its form
     * when it is a Mega, then shows them. pokemon is null for /boss rewards test.
     */
    fun reward(player: ServerPlayer, rarity: BossRarity, titan: Boolean, pokemon: Pokemon?): List<ItemStack> {
        val items = BossRewards.roll(rarity, titan, random)
        var stone = ItemStack.EMPTY
        var result = StoneResult.NONE
        var chance = 0.0
        val mega = pokemon?.let(BossMegas::of)
        val settings = BossRewards.settings.megaStones
        if (mega != null && settings.enabled) {
            val candidate = BossRewards.stone(mega)
            if (candidate == null) {
                result = StoneResult.NO_STONE
            } else {
                val saved = BossMegaSavedData.get(player.server)
                val first = !saved.has(player.uuid, mega.key)
                chance = if (first) settings.firstVictoryChance else settings.repeatChance
                stone = candidate
                if (random.nextDouble() * 100.0 < chance) {
                    result = if (first) StoneResult.FIRST else StoneResult.LUCKY
                    saved.add(player.uuid, mega.key)
                    items += candidate.copy()
                } else {
                    result = StoneResult.MISSED
                }
            }
        }

        val dropped = give(player, items)
        if (dropped > 0) {
            player.sendSystemMessage(Component.translatable("generations_core.boss.victory.dropped", dropped).withStyle(ChatFormatting.GOLD))
        }
        if (result == StoneResult.FIRST || result == StoneResult.LUCKY) {
            val key = if (result == StoneResult.FIRST) "generations_core.boss.victory.stone_chat.first" else "generations_core.boss.victory.stone_chat.lucky"
            player.sendSystemMessage(Component.translatable(key, stone.hoverName, mega!!.displayName()).withStyle(ChatFormatting.LIGHT_PURPLE))
        }

        val packet = S2CBossVictoryPacket(
            species = pokemon?.species?.resourceIdentifier?.toString() ?: "",
            aspects = pokemon?.aspects?.toList() ?: emptyList(),
            rarity = rarity.id,
            titan = titan,
            level = pokemon?.level ?: 0,
            rewards = items.map { it.copy() },
            stone = stone,
            stoneResult = result,
            stoneChance = chance,
            dropped = dropped,
        )
        if (pokemon == null) packet.sendToPlayer(player)
        else afterOnServer(seconds = SCREEN_DELAY) { if (!player.hasDisconnected()) packet.sendToPlayer(player) }
        return items
    }

    /** Into the inventory; what does not fit falls at the player's feet. Returns the stacks dropped. */
    private fun give(player: ServerPlayer, items: List<ItemStack>): Int {
        var dropped = 0
        for (item in items) {
            val stack = item.copy()
            player.inventory.add(stack)
            if (!stack.isEmpty) {
                player.drop(stack, false)
                dropped++
            }
        }
        player.inventoryMenu.broadcastChanges()
        return dropped
    }
}
