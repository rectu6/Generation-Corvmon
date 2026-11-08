package generations.gg.generations.core.generationscore.common.world.item

import generations.gg.generations.core.generationscore.common.world.item.components.GenerationsDataComponents
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level

/**
 * Right-click turns it on or off. While it is on and in the inventory, every
 * party Pokémon gains experience after a battle (ExpAllCalculator), and Exp Shares
 * give nothing more (PokemonBattleMixin).
 */
class ExpAllItem(properties: Properties) : Item(properties) {

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(hand)
        if (!level.isClientSide) {
            val active = !isActive(stack)
            stack.set(GenerationsDataComponents.ACTIVE.value(), active)
            player.displayClientMessage(Component.translatable(if (active) "item.generations_core.exp_all.on" else "item.generations_core.exp_all.off"), true)
            level.playSound(null, player.x, player.y, player.z, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.4f, if (active) 1.3f else 0.8f)
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
    }

    override fun isFoil(stack: ItemStack): Boolean = isActive(stack)

    override fun appendHoverText(stack: ItemStack, context: TooltipContext, tooltip: MutableList<Component>, flag: TooltipFlag) {
        tooltip.add(Component.translatable(if (isActive(stack)) "item.generations_core.exp_all.tooltip.on" else "item.generations_core.exp_all.tooltip.off"))
    }

    companion object {
        @JvmStatic
        fun isActive(stack: ItemStack): Boolean = stack.getOrDefault(GenerationsDataComponents.ACTIVE.value(), false)
    }
}
