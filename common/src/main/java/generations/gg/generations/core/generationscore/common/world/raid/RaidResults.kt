package generations.gg.generations.core.generationscore.common.world.raid

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.item.PokeBallItem
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.pc
import com.cobblemon.mod.common.util.pokedex
import generations.gg.generations.core.generationscore.common.battle.DynamaxVisuals
import generations.gg.generations.core.generationscore.common.network.packets.raid.C2SRaidResultPacket
import generations.gg.generations.core.generationscore.common.network.packets.raid.S2CRaidResultPacket
import generations.gg.generations.core.generationscore.common.network.packets.raid.S2CRaidResultPacket.State
import generations.gg.generations.core.generationscore.common.world.raid.config.RaidConfig
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID

/**
 * What each player won in a raid and has not taken yet: the Pokémon to catch
 * (one try) and the rewards, drawn once at the victory. Saved with the world: closing
 * the menu, disconnecting or a crash loses nothing and gives nothing twice. The menu
 * opens again at login while something is left.
 */
class RaidResults : SavedData() {
    class Result(
        val prize: CompoundTag,
        val stars: Int,
        val rewards: List<ItemStack>,
        var state: State,
        var ball: String = "",
    ) {
        fun save(registries: HolderLookup.Provider) = CompoundTag().apply {
            put("prize", prize)
            putInt("stars", stars)
            put("rewards", ListTag().apply { rewards.forEach { add(it.save(registries)) } })
            putString("state", state.name)
            putString("ball", ball)
        }

        companion object {
            fun load(tag: CompoundTag, registries: HolderLookup.Provider) = Result(
                tag.getCompound("prize"),
                tag.getInt("stars"),
                tag.getList("rewards", Tag.TAG_COMPOUND.toInt()).mapNotNull { ItemStack.parse(registries, it).orElse(null) },
                runCatching { State.valueOf(tag.getString("state")) }.getOrDefault(State.CHOICE),
                tag.getString("ball"),
            )
        }
    }

    private val results = HashMap<UUID, Result>()

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        val list = ListTag()
        results.forEach { (player, result) ->
            list.add(result.save(registries).apply { putUUID("player", player) })
        }
        tag.put("results", list)
        return tag
    }

    companion object {
        private const val NAME = "generations_raid_results"

        private fun of(server: MinecraftServer): RaidResults =
            server.overworld().dataStorage.computeIfAbsent(Factory(::RaidResults, ::load, null), NAME)

        private fun load(tag: CompoundTag, registries: HolderLookup.Provider) = RaidResults().apply {
            tag.getList("results", Tag.TAG_COMPOUND.toInt()).forEach { entry ->
                val compound = entry as CompoundTag
                runCatching { results[compound.getUUID("player")] = Result.load(compound, registries) }
            }
        }

        /**
         * A won raid for this player: the boss to catch (its own copy, healed, back to
         * its size, with its IVs, nature, ability and shiny) and the rewards drawn now.
         */
        fun create(player: ServerPlayer, boss: Pokemon, stars: Int) {
            val registries = player.server.registryAccess()
            val prize = boss.clone(true, registries).apply {
                uuid = UUID.randomUUID()
                DynamaxVisuals.end(this)
                scaleModifier = 1f
                heal()
                persistentData.remove(RaidManager.BOSS_KEY)
            }
            val types = boss.types.map { it.name.lowercase() }
            val rewards = RaidConfig.rewards.draw(stars, types, player.random)
            val data = of(player.server)
            // A result not taken yet from an earlier raid is given now.
            data.results.remove(player.uuid)?.let { give(player, it.rewards) }
            data.results[player.uuid] = Result(prize.saveToNBT(registries), stars, rewards, State.CHOICE)
            data.setDirty()
        }

        fun has(player: ServerPlayer) = of(player.server).results.containsKey(player.uuid)

        /** Opens the end menu with what is left. */
        fun send(player: ServerPlayer) {
            val result = of(player.server).results[player.uuid] ?: return
            val pokemon = runCatching { Pokemon.loadFromNBT(player.server.registryAccess(), result.prize) }.getOrNull()
            S2CRaidResultPacket(
                pokemon?.species?.resourceIdentifier?.toString() ?: "",
                pokemon?.aspects?.toList() ?: emptyList(),
                result.stars,
                result.rewards,
                result.state,
                result.ball,
            ).sendToPlayer(player)
        }

        fun onAction(player: ServerPlayer, packet: C2SRaidResultPacket) {
            val data = of(player.server)
            val result = data.results[player.uuid] ?: return
            when (packet.action) {
                C2SRaidResultPacket.Action.CATCH -> if (result.state == State.CHOICE && tryCatch(player, result, packet.slot)) {
                    data.setDirty()
                    send(player)
                }
                C2SRaidResultPacket.Action.SKIP -> if (result.state == State.CHOICE) {
                    result.state = State.SKIPPED
                    data.setDirty()
                    send(player)
                }
                // Closed before the catch was decided (the menu cannot be left with
                // Escape then): everything is kept, the menu opens again at the next
                // login or by clicking a den.
                C2SRaidResultPacket.Action.CLOSE -> if (result.state == State.CHOICE) {
                    player.sendSystemMessage(Component.translatable("generations_core.raid.result.kept"))
                } else {
                    claim(player, data)
                }
                // Claiming, or closing the menu once the catch is decided: the rewards
                // are given, never lost.
                C2SRaidResultPacket.Action.CLAIM -> if (result.state != State.CHOICE) claim(player, data)
            }
        }

        /** The rewards are given once: the result is removed first. */
        private fun claim(player: ServerPlayer, data: RaidResults) {
            val result = data.results.remove(player.uuid) ?: return
            data.setDirty()
            give(player, result.rewards)
            player.sendSystemMessage(Component.translatable("generations_core.raid.rewards"))
        }

        /**
         * The single try (capture.json): base chance of the tier times the ball's
         * multiplier and the boss's ones; a Master Ball always works unless disabled.
         */
        private fun tryCatch(player: ServerPlayer, result: Result, slot: Int): Boolean {
            if (slot !in 0 until player.inventory.containerSize) return false
            val stack = player.inventory.getItem(slot)
            val ball = (stack.item as? PokeBallItem)?.pokeBall ?: return false
            val id = BuiltInRegistries.ITEM.getKey(stack.item).toString()
            val config = RaidConfig.capture
            if (config.forbidden(result.stars, id)) {
                player.displayClientMessage(Component.translatable("generations_core.raid.result.ball_forbidden"), true)
                return false
            }
            val pokemon = runCatching { Pokemon.loadFromNBT(player.server.registryAccess(), result.prize) }.getOrNull() ?: return false
            // No room in the party nor the PC: no throw, the ball and the try are kept.
            val party = Cobblemon.storage.getParty(player)
            if (party.getFirstAvailablePosition() == null && player.pc().getFirstAvailablePosition() == null) {
                player.displayClientMessage(Component.translatable("generations_core.raid.result.no_room"), true)
                return false
            }
            result.ball = id
            val ballItem = stack.item
            stack.consume(1, player)

            val legendary = "legendary" in pokemon.species.labels || "mythical" in pokemon.species.labels
            val chance = if (ball.name.path == "master_ball" && config.masterBallAlwaysCatches) 1.0
            else (config.baseChance[(result.stars - 1).coerceIn(0, 5)] *
                (config.ballMultipliers[id] ?: config.defaultBallMultiplier) *
                (if (pokemon.shiny) config.shinyMultiplier else 1.0) *
                (if (legendary) config.legendaryMultiplier else 1.0)).coerceIn(0.0, 1.0)
            if (player.random.nextDouble() >= chance) {
                result.state = State.ESCAPED
                return true
            }
            pokemon.caughtBall = ball
            pokemon.setOriginalTrainer(player.uuid)
            // The party sends it to the PC when full.
            if (!party.add(pokemon)) {
                // Room taken meanwhile: the ball comes back and the try is kept.
                result.state = State.CHOICE
                result.ball = ""
                give(player, listOf(ItemStack(ballItem)))
                player.displayClientMessage(Component.translatable("generations_core.raid.result.no_room"), true)
                return true
            }
            player.pokedex().catch(pokemon)
            result.state = State.CAUGHT
            return true
        }

        private fun give(player: ServerPlayer, stacks: List<ItemStack>) {
            stacks.forEach { stack ->
                val copy = stack.copy()
                if (!player.inventory.add(copy)) player.drop(copy, false)
            }
        }
    }
}
