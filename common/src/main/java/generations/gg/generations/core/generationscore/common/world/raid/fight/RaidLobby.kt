package generations.gg.generations.core.generationscore.common.world.raid.fight

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.pc
import generations.gg.generations.core.generationscore.common.network.packets.raid.RaidLobbyMember
import generations.gg.generations.core.generationscore.common.network.packets.raid.RaidPick
import generations.gg.generations.core.generationscore.common.network.packets.raid.S2CRaidLobbyPacket
import generations.gg.generations.core.generationscore.common.world.raid.RaidAlly
import generations.gg.generations.core.generationscore.common.world.raid.config.RaidConfig
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * The lobby of an active den: up to four players, each with the Pokémon they bring
 * (party or PC) and whether they are ready. NPCs take the places left. The raid
 * starts when every player is ready, or when the lobby's time runs out.
 */
class RaidLobby(
    val level: ServerLevel,
    val pos: BlockPos,
    val stars: Int,
    val boss: Pokemon,
    /** NPCs for the free places, in order. */
    val npcs: List<RaidAlly>,
) {
    class Entry(val uuid: UUID, val name: String) {
        var pokemon: UUID? = null
        var ready = false
    }

    val entries = LinkedHashMap<UUID, Entry>()
    /** Game time at which the raid starts anyway. */
    private var deadline = -1L

    val isEmpty: Boolean get() = entries.isEmpty()

    fun join(player: ServerPlayer): Boolean {
        if (player.uuid in entries) return true
        if (entries.size >= 4) return false
        val entry = Entry(player.uuid, player.gameProfile.name)
        entry.pokemon = picks(player).firstOrNull { it.box < 0 }?.uuid ?: picks(player).firstOrNull()?.uuid
        entries[player.uuid] = entry
        if (deadline < 0) deadline = level.gameTime + RaidConfig.battle.lobbySeconds * 20L
        return true
    }

    fun leave(uuid: UUID) {
        entries.remove(uuid)
        if (entries.isEmpty()) deadline = -1
    }

    fun select(player: ServerPlayer, pokemon: UUID) {
        val entry = entries[player.uuid] ?: return
        if (find(player, pokemon) != null) entry.pokemon = pokemon
    }

    fun toggleReady(player: ServerPlayer) {
        val entry = entries[player.uuid] ?: return
        entry.ready = !entry.ready
    }

    /** Every player ready, or the time is up. */
    fun shouldStart(): Boolean = entries.isNotEmpty() && (entries.values.all { it.ready } || level.gameTime >= deadline)

    fun secondsLeft(): Int = if (deadline < 0) RaidConfig.battle.lobbySeconds else ((deadline - level.gameTime) / 20L).toInt().coerceAtLeast(0)

    /** The Pokémon a player brings: party or PC. */
    fun find(player: ServerPlayer, uuid: UUID): Pokemon? = Cobblemon.storage.getParty(player)[uuid] ?: player.pc()[uuid]

    /** Every Pokémon the player can bring: party first, then the PC. */
    fun picks(player: ServerPlayer): List<RaidPick> {
        val picks = mutableListOf<RaidPick>()
        Cobblemon.storage.getParty(player).forEachIndexed { slot, pokemon -> picks += pick(pokemon, -1, slot) }
        player.pc().boxes.forEachIndexed { box, pcBox ->
            pcBox.getNonEmptySlots().forEach { (slot, pokemon) -> picks += pick(pokemon, box, slot) }
        }
        return picks
    }

    private fun pick(pokemon: Pokemon, box: Int, slot: Int) =
        RaidPick(pokemon.uuid, pokemon.species.resourceIdentifier.toString(), pokemon.aspects.toList(), label(pokemon), box, slot)

    /** Sends the lobby to each player, with their own Pokémon list. */
    fun send() {
        val server = level.server
        entries.values.forEach { entry ->
            val player = server.playerList.getPlayer(entry.uuid) ?: return@forEach
            val members = entries.values.map { other ->
                val pokemon = server.playerList.getPlayer(other.uuid)?.let { p -> other.pokemon?.let { find(p, it) } }
                RaidLobbyMember(
                    other.name, pokemon?.let(::label) ?: "", pokemon?.species?.resourceIdentifier?.toString() ?: "",
                    pokemon?.aspects?.toList() ?: emptyList(), if (other.uuid == entry.uuid) RaidLobbyMember.YOU else RaidLobbyMember.PLAYER, other.ready,
                )
            } + npcs.take(4 - entries.size).map {
                RaidLobbyMember(it.name, it.label, it.pokemon.species.resourceIdentifier.toString(), it.pokemon.aspects.toList(), RaidLobbyMember.NPC, true)
            }
            S2CRaidLobbyPacket(
                pos, stars, boss.species.resourceIdentifier.toString(), boss.aspects.toList(),
                members, picks(player), entry.pokemon, entry.ready, secondsLeft(),
                RaidConfig.battle.maxFaints, RaidConfig.battle.maxTurns,
            ).sendToPlayer(player)
        }
    }

    companion object {
        fun label(pokemon: Pokemon): String = pokemon.getDisplayName(false).string + " Nv. " + pokemon.level
    }
}
