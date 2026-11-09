package generations.gg.generations.core.generationscore.common.world.raid.fight

import com.cobblemon.mod.common.battles.ActiveBattlePokemon
import com.cobblemon.mod.common.battles.DefaultActionResponse
import com.cobblemon.mod.common.battles.MoveActionResponse
import com.cobblemon.mod.common.battles.ShowdownActionRequest
import com.cobblemon.mod.common.battles.ShowdownActionResponse
import com.cobblemon.mod.common.battles.actor.PokemonBattleActor
import com.cobblemon.mod.common.battles.ai.StrongBattleAI
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.world.raid.RaidShowdown
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * The raid boss in one player's battle. It holds its choice until the raid's turn
 * plays (RaidFight): every player has chosen, cheers are in, and the server picked
 * the boss's move once for all battles. Never flees by distance.
 *
 * Cobblemon's AI fallback does nothing when the request is gone, which would leave
 * the battle waiting for the boss forever: every path here ends with an answer
 * (the planned move, the AI's, or Showdown's default choice).
 */
class RaidBossActor(
    uuid: UUID,
    pokemon: BattlePokemon,
    skill: Int,
    /** Where the boss stands: the den, also for the copies fought without an entity. */
    private val center: Vec3,
) : PokemonBattleActor(uuid, pokemon, -1f, StrongBattleAI(skill)) {
    /** Waiting for the raid's turn. */
    var pending = false
        private set

    /** The request being held: Cobblemon may clear its own before the turn plays. */
    private var held: ShowdownActionRequest? = null

    override val initialPos: Vec3? get() = center

    override fun onChoiceRequested() {
        val request = request
        if (request == null) {
            // Nothing asked: make sure Cobblemon does not wait for an answer either.
            if (mustChoose) {
                mustChoose = false
                battle.checkForInputDispatch()
            }
            return
        }
        // Nothing to choose this time (the other side must act): answered at once.
        if (request.wait || request.active.isNullOrEmpty()) {
            super.onChoiceRequested()
            return
        }
        held = request
        pending = true
    }

    /** Plays the turn with the planned move when allowed, else the AI's choice. */
    fun release(move: String?) {
        if (!pending) return
        pending = false
        val request = request ?: held
        held = null
        val active = activePokemon.firstOrNull() ?: return
        val moveset = request?.active?.firstOrNull()
        val planned = if (request != null && moveset != null && !request.wait && request.forceSwitch.none { it }) planned(move, active, request) else null

        if (mustChoose && this.request != null) {
            if (planned != null && respond(listOf(planned))) return
            // Not allowed after all (locked move, choice item...): the AI chooses.
            try {
                super.onChoiceRequested()
            } catch (e: Exception) {
                GenerationsCore.LOGGER.warn("Raid: the boss AI failed, Showdown chooses for it", e)
            }
            if (mustChoose) this.request?.let { respond(it.iterate(activePokemon) { _, _, _ -> DefaultActionResponse() }) }
            return
        }
        // Cobblemon already sent the turn without the boss while Showdown waits for it:
        // the choice goes straight to Showdown.
        if (RaidShowdown.waiting(battle, showdownId)) {
            val choice = if (planned != null && moveset != null) planned.toShowdownString(active, moveset) else "default"
            GenerationsCore.LOGGER.warn("Raid: the boss's choice was not expected by Cobblemon, sent to Showdown ({})", choice)
            battle.writeShowdownAction(">$showdownId $choice")
        }
    }

    /** The planned move, aimed at this battle's opponent when it needs a target. */
    private fun planned(move: String?, active: ActiveBattlePokemon, request: ShowdownActionRequest): MoveActionResponse? {
        val chosen = request.active?.firstOrNull()?.moves?.find { it.id == move && it.canBeUsed() } ?: return null
        val targets = chosen.getTargets(active)?.takeIf { it.isNotEmpty() } ?: return MoveActionResponse(chosen.id)
        val target = targets.firstOrNull { !it.isAllied(active) } ?: targets.first()
        return MoveActionResponse(chosen.id, target.getPNX())
    }

    private fun respond(responses: List<ShowdownActionResponse>): Boolean = try {
        setActionResponses(responses)
        !mustChoose
    } catch (_: Exception) {
        false
    }
}
