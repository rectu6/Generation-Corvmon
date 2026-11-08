package generations.gg.generations.core.generationscore.common.battle

import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import generations.gg.generations.core.generationscore.common.world.item.ExpAllItem
import generations.gg.generations.core.generationscore.common.world.item.GenerationsItems
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

object ExpAllCalculator {
    fun BattlePokemon.isFainted(): Boolean {
        var isFainted = false
        if (this.health <= 0) {
            isFainted = true
        }
        return isFainted
    }

    fun BattlePokemon.calculateMultiplier(): Double {
        val hasParticipated = this.facedOpponents.isNotEmpty()
        var multiplier = 1.0
        if (!hasParticipated) {
            multiplier = 0.5
        }
        return multiplier
    }

    /** An Exp All turned on (ExpAllItem) is in the inventory or the offhand. */
    @JvmStatic
    fun ServerPlayer.hasExpAll(): Boolean {
        val isOn = { stack: ItemStack -> stack.`is`(GenerationsItems.EXP_ALL.value()) && ExpAllItem.isActive(stack) }
        return this.inventory.items.any(isOn) || isOn(this.offhandItem)
    }
}
