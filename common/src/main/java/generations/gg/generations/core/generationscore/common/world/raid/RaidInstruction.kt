package generations.gg.generations.core.generationscore.common.world.raid

import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.battles.dispatch.InterpreterInstruction

/**
 * A "|gcraid|..." line from raid.js: what the NPC allies and the boss did, faints,
 * revivals, the raid status. Handled in order with the rest of the battle.
 */
class RaidInstruction(private val message: BattleMessage) : InterpreterInstruction {
    override fun invoke(battle: PokemonBattle) {
        RaidManager.onRaidMessage(battle, message)
    }
}
