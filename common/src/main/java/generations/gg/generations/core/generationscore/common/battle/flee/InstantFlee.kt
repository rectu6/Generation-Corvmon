package generations.gg.generations.core.generationscore.common.battle.flee

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.actor.ActorType
import com.cobblemon.mod.common.api.battles.model.actor.EntityBackedBattleActor
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleFledEvent
import com.cobblemon.mod.common.api.events.battles.BattleFleeAttemptEvent
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.battles.FleeAttemptActionResponse
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.client.battle.BattleMakeChoicePacket
import com.cobblemon.mod.common.net.messages.client.battle.BattleQueueRequestPacket
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.cobblemon.mod.common.util.battleLang
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.world.boss.Bosses
import generations.gg.generations.core.generationscore.common.world.raid.RaidManager
import generations.gg.generations.core.generationscore.common.world.raid.RaidShowdown
import generations.gg.generations.core.generationscore.common.world.raid.fight.RaidBossActor
import generations.gg.generations.core.generationscore.common.world.ultraspace.encounter.UltraEncounters
import net.minecraft.ChatFormatting
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import java.util.Random
import java.util.UUID

/**
 * The Run button ends a wild battle at once (COMBO-FUITE-PERF-PLAN.md, 2.5), instead of
 * Cobblemon's "walk away from it". Only the server decides:
 * - PokemonBattleFleeMixin calls [onFleeAttempt] in place of checkFleeAttempt when the
 *   battle can use it: one player, only wild Pokémon, not a raid (bosses and the Ultra
 *   Warp Ride's arrival encounter as flee.json says). Otherwise Cobblemon's code runs.
 * - Trapped (Showdown's state of this turn: Mean Look, Shadow Tag, Arena Trap, Wrap…)
 *   → "You can't escape!", the battle goes on, unless a Smoke Ball is held or the
 *   Pokémon has Run Away. With the speed formula a failure asks for another action, and
 *   only one try per turn.
 * - Success: the flight is marked; at the next tick with no animation left,
 *   [onCheckFlee] runs exactly Cobblemon's flight (the wild Pokémon healed, BATTLE_FLED,
 *   its message, fled = true, stop()): result FLED, the interface closed by the normal
 *   end packet, the wild Pokémon left in the world. Several presses do the same once.
 * Cobblemon's flight by distance stays as it was.
 */
object InstantFlee {
    var config = FleeConfig().also { it.validate() }; private set

    private val accepted = HashSet<UUID>()
    private val attempts = HashMap<UUID, Int>()
    private val lastTry = HashMap<UUID, Int>()
    private val random = Random()

    fun load() {
        config = FleeConfig.load()
    }

    fun init() {
        load()
        PlatformEvents.SERVER_STARTED.subscribe(Priority.NORMAL) { load() }
        PlatformEvents.SERVER_STOPPING.subscribe(Priority.NORMAL) {
            accepted.clear(); attempts.clear(); lastTry.clear()
        }
    }

    /** At the end of every battle (PokemonBattleMixin). */
    @JvmStatic
    fun onBattleEnd(battle: PokemonBattle) {
        accepted.remove(battle.battleId)
        attempts.remove(battle.battleId)
        lastTry.remove(battle.battleId)
    }

    fun eligible(battle: PokemonBattle): Boolean {
        if (!config.enabled || !battle.isPvW || battle.ended) return false
        if (battle.playerUUIDs.count() != 1) return false
        if (battle.actors.any { it is RaidBossActor }) return false
        for (actor in battle.actors) {
            if (actor.type != ActorType.WILD) continue
            for (pokemon in actor.pokemonList) {
                val original = pokemon.effectedPokemon
                val entity = original.entity
                if (entity != null && RaidManager.isRaidEntity(entity)) return false
                if (!config.bosses && Bosses.isBoss(original)) return false
                if (!config.ultraEncounters && UltraEncounters.isArrivalEncounter(original)) return false
            }
        }
        return true
    }

    /** In place of PokemonBattle.checkFleeAttempt; true when a Run request was handled here. */
    @JvmStatic
    fun onFleeAttempt(battle: PokemonBattle): Boolean {
        val runner = battle.actors.find { actor -> actor.responses.any { it is FleeAttemptActionResponse } } as? PlayerBattleActor ?: return false
        if (!eligible(battle)) return false
        runner.responses.clear()
        // Already accepted: the battle ends at the next tick, nothing more to decide.
        if (battle.battleId in accepted) return true
        CobblemonEvents.BATTLE_FLEE_ATTEMPT.post(BattleFleeAttemptEvent(battle, runner))

        val mode = config.mode
        if (mode == FleeMath.Formula.SPEED && lastTry[battle.battleId] == battle.turn) {
            return refuse(runner, "generations_core.flee.once_per_turn")
        }
        val active = runner.activePokemon.mapNotNull { it.battlePokemon }
        val escapes = active.any { (config.smokeBall && holdsSmokeBall(it)) || (config.runAway && hasRunAway(it)) }
        val trapped = config.respectTrapping && !escapes && isTrapped(battle, runner)
        val attempt = (attempts[battle.battleId] ?: 0) + 1
        attempts[battle.battleId] = attempt
        lastTry[battle.battleId] = battle.turn
        val playerSpeed = active.maxOfOrNull { it.effectedPokemon.getStat(Stats.SPEED) } ?: 0
        val wildSpeed = battle.actors.filter { it.type == ActorType.WILD }.flatMap { it.activePokemon }.mapNotNull { it.battlePokemon }
            .maxOfOrNull { it.effectedPokemon.getStat(Stats.SPEED) } ?: 0
        return when (FleeMath.decide(mode, trapped, escapes, playerSpeed, wildSpeed, attempt, random)) {
            FleeMath.Outcome.ESCAPED -> {
                accepted += battle.battleId
                runner.mustChoose = false
                true
            }
            FleeMath.Outcome.TRAPPED -> refuse(runner, "generations_core.flee.trapped")
            FleeMath.Outcome.FAILED -> refuse(runner, "generations_core.flee.failed")
        }
    }

    /** The battle goes on: the player chooses another action. */
    private fun refuse(runner: PlayerBattleActor, key: String): Boolean {
        runner.mustChoose = true
        runner.request?.let { runner.sendUpdate(BattleQueueRequestPacket(it)) }
        runner.sendUpdate(BattleMakeChoicePacket())
        runner.entity?.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.RED))
        return true
    }

    /** Showdown's state of this turn; the request's flag when Showdown cannot be asked. */
    private fun isTrapped(battle: PokemonBattle, runner: PlayerBattleActor): Boolean {
        val showdown = runCatching { RaidShowdown.trapped(battle, runner.showdownId) }.getOrNull()
        if (showdown != null) return showdown
        return runner.request?.active?.any { it.trapped } == true
    }

    private fun holdsSmokeBall(pokemon: BattlePokemon): Boolean =
        BuiltInRegistries.ITEM.getKey(pokemon.effectedPokemon.heldItem().item).toString() == "cobblemon:smoke_ball"

    private fun hasRunAway(pokemon: BattlePokemon): Boolean = pokemon.effectedPokemon.ability.name.equals("runaway", ignoreCase = true)

    /** In place of PokemonBattle.checkFlee once a flight was accepted: Cobblemon's own flight. */
    @JvmStatic
    fun onCheckFlee(battle: PokemonBattle): Boolean {
        if (!accepted.remove(battle.battleId)) return false
        val player = battle.actors.filterIsInstance<PlayerBattleActor>().firstOrNull() ?: return false
        try {
            battle.actors.filter { it.type == ActorType.WILD }
                .filterIsInstance<EntityBackedBattleActor<*>>()
                .mapNotNull { it.entity }
                .filterIsInstance<PokemonEntity>()
                .forEach { it.pokemon.heal() }
            CobblemonEvents.BATTLE_FLED.post(BattleFledEvent(battle, player))
            battle.actors.filterIsInstance<EntityBackedBattleActor<*>>().mapNotNull { it.entity }
                .forEach { it.sendSystemMessage(battleLang("flee").withStyle(ChatFormatting.YELLOW)) }
            battle.fled = true
            battle.stop()
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("Instant flee: could not end battle {}", battle.battleId, e)
            battle.fled = true
            battle.stop()
        }
        return true
    }
}
