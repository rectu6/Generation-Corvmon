package generations.gg.generations.core.generationscore.common.world.raid

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.scheduling.afterOnServer
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.cobblemon.mod.common.util.isInBattle
import com.cobblemon.mod.common.util.pc
import generations.gg.generations.core.generationscore.common.network.packets.raid.C2SRaidLobbyPacket
import generations.gg.generations.core.generationscore.common.network.packets.raid.C2SRaidResultPacket
import generations.gg.generations.core.generationscore.common.network.packets.raid.S2CRaidLobbyClosePacket
import generations.gg.generations.core.generationscore.common.world.raid.config.RaidConfig
import generations.gg.generations.core.generationscore.common.world.raid.den.RaidDenRegistry
import generations.gg.generations.core.generationscore.common.world.raid.den.RaidDens
import generations.gg.generations.core.generationscore.common.world.raid.fight.RaidBossActor
import generations.gg.generations.core.generationscore.common.world.raid.fight.RaidFight
import generations.gg.generations.core.generationscore.common.world.raid.fight.RaidLobby
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import java.util.UUID

/**
 * Dynamax raids (docs/wiki/raids-dynamax.md), settings in config/generations/core/raids/.
 *
 * 1. Dens appear in the world; some mornings a boss appears in an inactive den
 *    (RaidDens), generated once from the biome's table.
 * 2. Clicking an active den joins its lobby (RaidLobby): up to four players, each
 *    with the Pokémon they bring (party or PC); NPCs take the places left.
 * 3. When everyone is ready, or the time is up, the raid starts (RaidFight): the boss
 *    appears above the den, Dynamaxed and huge, and each player fights it in a battle
 *    kept in step with the others; the rules run in Showdown (raid.js).
 * 4. Victory: each player gets one catch try and their rewards (RaidResults).
 *    Defeat: the Dynamax energy explodes. The den becomes inactive either way.
 */
object RaidManager {
    /** Persistent data on the boss: its den position. */
    const val BOSS_KEY = "generations_raid_boss"
    /** Persistent data on the healed copy of a player's Pokémon that fights the raid. */
    const val COPY_KEY = "generations_raid_copy"
    private const val LOBBY_RANGE = 16.0

    private val lobbies = HashMap<Pair<ResourceKey<Level>, BlockPos>, RaidLobby>()
    private val fights = HashMap<Pair<ResourceKey<Level>, BlockPos>, RaidFight>()
    private val fightsByBattle = HashMap<UUID, RaidFight>()
    /** Entities (and Pokémon, for the copies Cobblemon sends out) of the raids being fought. */
    private val live = HashMap<UUID, RaidFight>()

    private fun key(level: ServerLevel, pos: BlockPos) = level.dimension() to pos

    /** An entity or Pokémon of this raid: kept while the raid lasts. */
    fun track(fight: RaidFight, id: UUID) {
        live[id] = fight
    }

    /** The boss, the NPCs and their Pokémon, the players' copies: never saved with the world. */
    @JvmStatic
    fun isRaidEntity(entity: Entity): Boolean {
        if (RaidAllies.TEMPORARY in entity.tags) return true
        val data = (entity as? PokemonEntity)?.pokemon?.persistentData ?: return false
        return data.contains(BOSS_KEY) || data.contains(COPY_KEY)
    }

    /**
     * A raid entity whose raid is no longer fought (a crash, a save from before this
     * check): it would stay in the world, could be fought or caught, so it is removed.
     */
    @JvmStatic
    fun isStray(entity: Entity): Boolean {
        if (!isRaidEntity(entity) || entity.uuid in live) return false
        val pokemon = (entity as? PokemonEntity)?.pokemon
        return pokemon == null || pokemon.uuid !in live
    }

    fun init() {
        RaidConfig.loadSettings()
        RaidShowdown.init()
        RaidDens.init()
        PlatformEvents.SERVER_STARTED.subscribe(Priority.NORMAL) { RaidConfig.load() }
        PlatformEvents.SERVER_STOPPING.subscribe(Priority.NORMAL) {
            lobbies.clear()
            fights.clear()
            fightsByBattle.clear()
            live.clear()
        }
        PlatformEvents.SERVER_TICK_POST.subscribe(Priority.NORMAL) { tick(it.server) }
        PlatformEvents.SERVER_PLAYER_LOGOUT.subscribe(Priority.NORMAL) { event -> leaveLobbies(event.player.uuid) }
        // Something won and not taken yet: the end menu opens again.
        PlatformEvents.SERVER_PLAYER_LOGIN.subscribe(Priority.NORMAL) { event ->
            val player = event.player
            afterOnServer(3f) { if (!player.hasDisconnected() && RaidResults.has(player)) RaidResults.send(player) }
        }

        // Isolation: the boss and the NPCs' Pokémon cannot be caught, battled, nor drop anything.
        CobblemonEvents.THROWN_POKEBALL_HIT.subscribe(Priority.HIGH) { event ->
            if (isRaidEntity(event.pokemon)) {
                event.cancel()
                (event.pokeBall.owner as? ServerPlayer)?.displayClientMessage(Component.translatable("generations_core.raid.boss_uncatchable"), true)
            }
        }
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(Priority.HIGH) { event ->
            if (event.battle.actors.any { it is RaidBossActor }) return@subscribe
            val raidEntity = event.battle.actors.any { actor -> actor.pokemonList.any { pokemon -> pokemon.entity?.let(::isRaidEntity) == true } }
            if (raidEntity) event.cancel()
        }
        CobblemonEvents.LOOT_DROPPED.subscribe(Priority.HIGH) { event ->
            if (event.entity?.tags?.contains(RaidAllies.TEMPORARY) == true) event.cancel()
        }
        CobblemonEvents.BATTLE_FAINTED.subscribe(Priority.NORMAL) { event ->
            if (event.killed.actor is RaidBossActor) fightsByBattle[event.battle.battleId]?.onBossFainted(event.battle)
        }
    }

    /** A den with a lobby or a raid being fought. */
    fun isBusy(level: ServerLevel, pos: BlockPos) = key(level, pos) in lobbies || key(level, pos) in fights

    // ---- Lobby ----------------------------------------------------------------

    /** Click on an active den: joins (or opens) its lobby. */
    fun joinLobby(level: ServerLevel, pos: BlockPos, player: ServerPlayer) {
        if (player.isInBattle()) {
            player.displayClientMessage(Component.translatable("generations_core.raid.in_battle"), true)
            return
        }
        // A won raid not settled yet (its catch, its rewards): its menu first, so that a
        // new victory never replaces it.
        if (RaidResults.has(player)) {
            RaidResults.send(player)
            return
        }
        if (key(level, pos) in fights) {
            player.displayClientMessage(Component.translatable("generations_core.raid.busy"), true)
            return
        }
        val den = RaidDenRegistry.of(level)[pos]
        val boss = den?.let { RaidDens.boss(level, it) }
        if (den == null || !den.active || boss == null) {
            player.displayClientMessage(Component.translatable("generations_core.raid.empty_den"), true)
            return
        }
        if (Cobblemon.storage.getParty(player).none() && player.pc().boxes.all { it.getNonEmptySlots().isEmpty() }) {
            player.displayClientMessage(Component.translatable("generations_core.raid.no_pokemon"), true)
            return
        }
        lobbies.filterValues { it.entries.containsKey(player.uuid) && it.pos != pos }.keys.forEach { other -> lobbies[other]?.let { leave(it, player.uuid) } }
        val lobby = lobbies.getOrPut(key(level, pos)) {
            val biome = ResourceLocation.tryParse(den.biome)?.let { id ->
                level.registryAccess().registryOrThrow(Registries.BIOME).getHolder(ResourceKey.create(Registries.BIOME, id)).orElse(null)
            }
            RaidLobby(level, pos, den.stars, boss, RaidAllies.create(den.stars, level.random, biome))
        }
        if (!lobby.join(player)) {
            player.displayClientMessage(Component.translatable("generations_core.raid.full"), true)
            return
        }
        lobby.send()
    }

    fun onLobbyAction(player: ServerPlayer, packet: C2SRaidLobbyPacket) {
        val lobby = lobbies[key(player.serverLevel(), packet.pos)] ?: return
        if (!lobby.entries.containsKey(player.uuid)) return
        when (packet.action) {
            C2SRaidLobbyPacket.Action.SELECT -> packet.pokemon?.let { lobby.select(player, it) }
            C2SRaidLobbyPacket.Action.READY -> lobby.toggleReady(player)
            C2SRaidLobbyPacket.Action.QUIT -> {
                leave(lobby, player.uuid)
                return
            }
        }
        lobby.send()
    }

    private fun leave(lobby: RaidLobby, uuid: UUID) {
        lobby.leave(uuid)
        lobby.level.server.playerList.getPlayer(uuid)?.let { S2CRaidLobbyClosePacket().sendToPlayer(it) }
        if (lobby.isEmpty) lobbies.remove(key(lobby.level, lobby.pos)) else lobby.send()
    }

    private fun leaveLobbies(uuid: UUID) {
        lobbies.values.filter { it.entries.containsKey(uuid) }.toList().forEach { leave(it, uuid) }
    }

    private fun tick(server: MinecraftServer) {
        if (server.tickCount % 10 == 0) {
            lobbies.values.toList().forEach { lobby ->
                // Players gone or too far leave the lobby.
                lobby.entries.keys.toList().forEach { uuid ->
                    val player = server.playerList.getPlayer(uuid)
                    if (player == null || player.level() != lobby.level || player.position().distanceTo(lobby.pos.center) > LOBBY_RANGE || player.isInBattle()) leave(lobby, uuid)
                }
                if (lobby.isEmpty) return@forEach
                if (lobby.shouldStart()) start(lobby) else if (server.tickCount % 20 == 0) lobby.send()
            }
        }
        fights.values.toList().forEach { it.tick() }
    }

    /** The lobby's players fight: their Pokémon, NPCs for the places left. */
    private fun start(lobby: RaidLobby) {
        lobbies.remove(key(lobby.level, lobby.pos))
        val server = lobby.level.server
        val entries = lobby.entries.values.mapNotNull { entry ->
            val player = server.playerList.getPlayer(entry.uuid) ?: return@mapNotNull null
            S2CRaidLobbyClosePacket().sendToPlayer(player)
            val pokemon = entry.pokemon?.let { lobby.find(player, it) }
                ?: Cobblemon.storage.getParty(player).firstOrNull { !it.isFainted() } ?: Cobblemon.storage.getParty(player).firstOrNull()
                ?: return@mapNotNull null
            player to pokemon
        }
        if (entries.isEmpty()) return
        val fight = RaidFight(lobby.level, lobby.pos, lobby.stars, lobby.boss, entries, lobby.npcs.take(4 - entries.size))
        fights[key(lobby.level, lobby.pos)] = fight
        if (!fight.start()) {
            fights.remove(key(lobby.level, lobby.pos))
            return
        }
        fight.battleIds.forEach { fightsByBattle[it] = fight }
    }

    fun fightEnded(fight: RaidFight) {
        fights.remove(key(fight.level, fight.pos))
        // Kept a little: the battles' last messages and faints still come, the boss and
        // the NPCs play their way out.
        afterOnServer(15f) {
            fightsByBattle.values.removeAll { it === fight }
            live.values.removeAll { it === fight }
        }
    }

    /** The den block is gone: its lobby closes, its raid stops. */
    fun denRemoved(level: ServerLevel, pos: BlockPos) {
        lobbies[key(level, pos)]?.let { lobby -> lobby.entries.keys.toList().forEach { leave(lobby, it) } }
        lobbies.remove(key(level, pos))
        fights[key(level, pos)]?.end("abandoned")
    }

    // ---- Battle ---------------------------------------------------------------

    /** Lines of raid.js: what the NPCs and the boss did, faints, revivals, shield, status. */
    fun onRaidMessage(battle: PokemonBattle, message: BattleMessage) {
        val fight = fightsByBattle[battle.battleId] ?: return
        val type = message.argumentAt(0) ?: return
        fight.onMessage(battle, type, (1..5).map { message.argumentAt(it) ?: "" })
    }

    fun cheer(player: ServerPlayer, accept: Boolean) {
        fights.values.firstOrNull { fight -> fight.members.any { it.uuid == player.uuid } }?.cheer(player, accept)
    }

    fun onResultAction(player: ServerPlayer, packet: C2SRaidResultPacket) = RaidResults.onAction(player, packet)
}
