package generations.gg.generations.core.generationscore.common.world.catchcombo

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.actor.ActorType
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleFaintedEvent
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.network.packets.combo.S2CCatchComboPacket
import generations.gg.generations.core.generationscore.common.world.boss.Bosses
import generations.gg.generations.core.generationscore.common.world.raid.RaidManager
import generations.gg.generations.core.generationscore.common.world.raid.fight.RaidBossActor
import generations.gg.generations.core.generationscore.common.world.shiny.ShinyMath
import generations.gg.generations.core.generationscore.common.world.shiny.ShinyOdds
import generations.gg.generations.core.generationscore.common.world.ultraspace.encounter.UltraEncounters
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.Random
import java.util.UUID

/**
 * The Catch Combo (COMBO-FUITE-PERF-PLAN.md, 2.3): catching the same species in a row
 * raises the shiny chance of that species and guarantees IVs at 31, on the spawns of the
 * combo player's own spawner. In memory per player (about 100 bytes), lost on logout;
 * one map read per spawn and per event, nothing per tick, one packet to that player only
 * when the combo changes.
 *
 * - Counting: POKEMON_CAPTURED of a wild Pokémon, once per Pokémon (its UUID). Raids,
 *   excluded species (labels, list) and other dimensions are ignored.
 * - Breaks (catch_combo.json, each can be turned off): another species (restart at 1), a
 *   wild Pokémon or a boss knocked out in battle (once per battle and Pokémon), a wild
 *   battle that ends with no outcome (the only "the wild Pokémon left" of Cobblemon
 *   1.8.4), a battle against a player, logout. Not a break: meeting one, fleeing, a
 *   missed ball, changing dimension.
 * - Bonuses, when the spawn is made (PokemonSpawnActionMixin around
 *   PokemonSpawnAction.createEntity): shiny through ShinyOdds (the combo species, the
 *   player whose spawner made it), IVs once the Pokémon exists and before it is in the
 *   world (never lowered, marked so they are never applied twice), then the spawn is kept
 *   for that player reservationSeconds (UltraEncounters.reserve). Nothing already in the
 *   world is changed.
 */
object CatchCombo {
    private const val IV_MARK = "generations_core_catch_combo_ivs"

    var config = CatchComboConfig().also { it.validate() }; private set

    private val states = HashMap<UUID, CatchComboMath.State>()
    /** Wild Pokémon already counted as knocked out, per battle (cleared at its end). */
    private val fainted = HashMap<UUID, MutableSet<UUID>>()
    /** Wild battles that had an outcome (victory, defeat, flight, capture). */
    private val outcomes = HashSet<UUID>()
    /** Wild battles that just ended: checked at the next tick (all their events are in). */
    private val ended = ArrayList<Pair<UUID, List<UUID>>>()
    private val random = Random()

    enum class Reason(val key: String) {
        OTHER_SPECIES("other_species"), WILD_FAINTED("wild_fainted"), BOSS_FAINTED("boss_fainted"),
        BATTLE_STOPPED("battle_stopped"), PVP("pvp"), COMMAND("command");

        val text: Component get() = Component.translatable("generations_core.catch_combo.break.$key")
    }

    fun load() {
        config = CatchComboConfig.load()
    }

    fun init() {
        load()
        PlatformEvents.SERVER_STARTED.subscribe(Priority.NORMAL) { load() }
        PlatformEvents.SERVER_STOPPING.subscribe(Priority.NORMAL) {
            states.clear(); fainted.clear(); outcomes.clear(); ended.clear()
        }
        PlatformEvents.SERVER_TICK_POST.subscribe(Priority.NORMAL) { event ->
            SpawnContext.clear()
            if (ended.isNotEmpty()) checkEnded(event.server)
        }
        PlatformEvents.SERVER_PLAYER_LOGIN.subscribe(Priority.NORMAL) { event -> send(event.player, S2CCatchComboPacket.Event.SYNC, null) }
        PlatformEvents.SERVER_PLAYER_LOGOUT.subscribe(Priority.NORMAL) { event ->
            if (config.breaks.disconnect) states.remove(event.player.uuid)
        }
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.LOW) { onCapture(it) }
        CobblemonEvents.BATTLE_FAINTED.subscribe(Priority.NORMAL) { onFainted(it) }
        CobblemonEvents.BATTLE_STARTED_POST.subscribe(Priority.NORMAL) { event ->
            if (event.battle.isPvP && config.breaks.pvpBattle) event.battle.players.forEach { breakFor(it, Reason.PVP) }
        }
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL) { outcomes += it.battle.battleId }
        CobblemonEvents.BATTLE_FLED.subscribe(Priority.NORMAL) { outcomes += it.battle.battleId }
        ShinyOdds.register { player, species, pokemon -> shinyBonus(player, species, pokemon) }
    }

    // ---- species and eligibility ----

    /** The combo's key: the species id, with the form for species of separateForms. */
    fun key(species: Species, formName: String?): String {
        val id = species.resourceIdentifier.toString()
        if (formName == null || id !in config.separateForms) return id
        val form = formName.lowercase()
        return if (form.isEmpty() || form == "normal" || form == species.standardForm.name.lowercase()) id else "$id/$form"
    }

    fun key(pokemon: Pokemon): String = key(pokemon.species, pokemon.form.name)

    /** The species of a combo key (without its form). */
    fun speciesOf(key: String): Species? = runCatching {
        PokemonSpecies.getByIdentifier(net.minecraft.resources.ResourceLocation.parse(key.substringBefore('/')))
    }.getOrNull()

    fun eligible(species: Species): Boolean {
        if (species.resourceIdentifier.toString() in config.excludedSpecies) return false
        return config.excludedLabels.none { it in species.labels }
    }

    private fun inDimension(player: ServerPlayer): Boolean =
        config.dimensions.isEmpty() || player.level().dimension().location().toString() in config.dimensions

    fun state(player: ServerPlayer): CatchComboMath.State = states.getOrPut(player.uuid) { CatchComboMath.State() }

    fun stateOrNull(player: ServerPlayer): CatchComboMath.State? = states[player.uuid]?.takeIf { it.species != null && it.count > 0 }

    fun tier(state: CatchComboMath.State?): CatchComboMath.Tier? = state?.let { CatchComboMath.tier(config.sortedTiers, it.count) }

    // ---- bonuses ----

    /**
     * The combo's shiny bonus (ShinyOdds): its species only, in a spawn made by this
     * player's spawner (or a special chance / a preview, pokemon == null).
     */
    private fun shinyBonus(player: ServerPlayer, species: Species?, pokemon: Pokemon?): ShinyMath.Bonus? {
        if (!config.enabled || species == null || !eligible(species) || !inDimension(player)) return null
        val spawn = SpawnContext.of(player)
        if (pokemon != null) {
            if (spawn == null) return null
            if (spawn.fishing && !config.fishingBonuses) return null
        }
        val state = stateOrNull(player) ?: return null
        val combo = if (pokemon != null) key(pokemon) else key(species, null)
        if (combo != state.species) return null
        val tier = tier(state) ?: return null
        if (tier.shinyMultiplier <= 1.0) return null
        spawn?.let {
            it.shinyBoosted = true
            it.shinyMultiplier = tier.shinyMultiplier
        }
        return ShinyMath.Bonus("catch_combo ${state.count}", tier.shinyMultiplier)
    }

    /** After Cobblemon made the spawn (before it is in the world): IVs and the reservation. */
    @JvmStatic
    fun afterSpawn(entity: PokemonEntity?, spawn: SpawnContext.Spawn?) {
        if (entity == null || spawn == null || !config.enabled) return
        val player = spawn.player
        if (spawn.fishing && !config.fishingBonuses) return
        val pokemon = entity.pokemon
        val state = stateOrNull(player) ?: return
        if (key(pokemon) != state.species || !eligible(pokemon.species) || !inDimension(player)) return
        val tier = tier(state) ?: return
        var raised = 0
        if (tier.perfectIvs > 0 && !pokemon.persistentData.getBoolean(IV_MARK)) {
            val stats = Stats.PERMANENT.toList()
            val ivs = IntArray(stats.size) { pokemon.ivs.getOrDefault(stats[it]) }
            val (result, count) = CatchComboMath.guaranteeIvs(ivs, tier.perfectIvs, random)
            for (i in stats.indices) if (result[i] > ivs[i]) pokemon.setIV(stats[i], result[i])
            pokemon.persistentData.putBoolean(IV_MARK, true)
            raised = count
        }
        if (config.reservationSeconds > 0 && (spawn.shinyBoosted || tier.perfectIvs > 0)) {
            UltraEncounters.reserve(pokemon, player.uuid, entity.level().gameTime + config.reservationSeconds * 20L)
        }
        if (config.log) {
            GenerationsCore.LOGGER.info(
                "Catch Combo: {} spawned for {} (combo {}): shiny ×{}{}, {} IVs raised to 31{}",
                state.species, player.gameProfile.name, state.count, ShinyMath.format(if (spawn.shinyBoosted) spawn.shinyMultiplier else 1.0),
                if (pokemon.shiny) " SHINY" else "", raised, if (spawn.fishing) ", fishing" else "",
            )
        }
    }

    // ---- counting and breaks ----

    private fun isRaid(battle: PokemonBattle?): Boolean = battle != null && battle.actors.any { it is RaidBossActor }

    private fun onCapture(event: PokemonCapturedEvent) {
        val player = event.player
        val battle = Cobblemon.battleRegistry.getBattleByParticipatingPlayer(player)
        battle?.let { outcomes += it.battleId }
        if (!config.enabled) return
        val pokemon = event.pokemon
        if (pokemon.persistentData.contains(RaidManager.BOSS_KEY) || pokemon.persistentData.contains(RaidManager.COPY_KEY) || isRaid(battle)) return
        if (!eligible(pokemon.species) || !inDimension(player)) return
        val state = state(player)
        val before = state.species
        val change = CatchComboMath.capture(state, key(pokemon), pokemon.uuid, config.maxCount, config.breaks.otherSpecies)
        if (change == CatchComboMath.Change.NONE) return
        val tierBefore = if (change == CatchComboMath.Change.UP) CatchComboMath.tier(config.sortedTiers, state.count - 1) else null
        val tierNow = tier(state)
        val newTier = tierNow != null && tierNow !== tierBefore
        if (config.log) GenerationsCore.LOGGER.info("Catch Combo: {} caught {} ({}): {} ×{}", player.gameProfile.name, key(pokemon), change, state.species, state.count)
        when (change) {
            CatchComboMath.Change.SWITCH -> {
                send(player, S2CCatchComboPacket.Event.BREAK, Reason.OTHER_SPECIES)
                if (before != null) tell(player, Component.translatable("generations_core.catch_combo.chat.break", Reason.OTHER_SPECIES.text).withStyle(ChatFormatting.RED))
            }
            else -> {
                send(player, if (newTier) S2CCatchComboPacket.Event.TIER else S2CCatchComboPacket.Event.UP, null)
                if (newTier && tierNow != null) tell(player, Component.translatable("generations_core.catch_combo.chat.tier", state.count, ShinyMath.format(tierNow.shinyMultiplier), tierNow.perfectIvs).withStyle(ChatFormatting.GOLD))
            }
        }
    }

    private fun onFainted(event: BattleFaintedEvent) {
        val battle = event.battle
        val killed = event.killed
        if (killed.actor.type != ActorType.WILD || isRaid(battle)) return
        val pokemon = killed.effectedPokemon
        val entity = pokemon.entity
        if (entity != null && RaidManager.isRaidEntity(entity)) return
        if (!fainted.getOrPut(battle.battleId) { HashSet() }.add(pokemon.uuid)) return
        val boss = Bosses.isBoss(pokemon)
        if (boss && !config.breaks.bossFainted) return
        if (!boss && !config.breaks.wildFainted) return
        battle.players.forEach { breakFor(it, if (boss) Reason.BOSS_FAINTED else Reason.WILD_FAINTED) }
    }

    /** PokemonBattleMixin, at the end of every battle. */
    @JvmStatic
    fun onBattleEnd(battle: PokemonBattle) {
        fainted.remove(battle.battleId)
        if (!battle.isPvW || isRaid(battle)) {
            outcomes.remove(battle.battleId)
            return
        }
        if (battle.fled) outcomes += battle.battleId
        ended += battle.battleId to battle.playerUUIDs.toList()
    }

    private fun checkEnded(server: MinecraftServer) {
        val list = ArrayList(ended)
        ended.clear()
        for ((battleId, players) in list) {
            val hadOutcome = outcomes.remove(battleId)
            if (hadOutcome || !config.breaks.battleStopped) continue
            players.mapNotNull { server.playerList.getPlayer(it) }.forEach { breakFor(it, Reason.BATTLE_STOPPED) }
        }
    }

    fun breakFor(player: ServerPlayer, reason: Reason) {
        val state = states[player.uuid] ?: return
        if (CatchComboMath.breakCombo(state) == CatchComboMath.Change.NONE) return
        if (config.log) GenerationsCore.LOGGER.info("Catch Combo: {} lost the combo ({})", player.gameProfile.name, reason.key)
        send(player, S2CCatchComboPacket.Event.BREAK, reason)
        tell(player, Component.translatable("generations_core.catch_combo.chat.break", reason.text).withStyle(ChatFormatting.RED))
    }

    // ---- commands ----

    fun set(player: ServerPlayer, key: String, count: Int) {
        val state = state(player)
        state.species = key
        state.count = count.coerceIn(1, config.maxCount)
        send(player, S2CCatchComboPacket.Event.SYNC, null)
    }

    fun reset(player: ServerPlayer) {
        states[player.uuid]?.reset()
        send(player, S2CCatchComboPacket.Event.RESET, Reason.COMMAND)
    }

    // ---- to the player ----

    private fun tell(player: ServerPlayer, message: Component) {
        if (config.chatMessages) player.sendSystemMessage(message)
    }

    fun send(player: ServerPlayer, event: S2CCatchComboPacket.Event, reason: Reason?) {
        val state = stateOrNull(player)
        val tiers = config.sortedTiers
        val tier = tier(state)
        val next = state?.let { CatchComboMath.next(tiers, it.count) }
        runCatching {
            S2CCatchComboPacket(
                enabled = config.enabled,
                species = state?.species ?: "",
                count = state?.count ?: 0,
                shinyMultiplier = tier?.shinyMultiplier?.toFloat() ?: 1f,
                perfectIvs = tier?.perfectIvs ?: 0,
                tierThreshold = tier?.threshold ?: 0,
                nextThreshold = next?.threshold ?: 0,
                nextShinyMultiplier = next?.shinyMultiplier?.toFloat() ?: 0f,
                nextPerfectIvs = next?.perfectIvs ?: 0,
                event = event,
                reason = reason?.key ?: "",
            ).sendToPlayer(player)
        }.onFailure { GenerationsCore.LOGGER.error("Catch Combo: could not send the combo to {}", player.gameProfile.name, it) }
    }
}
