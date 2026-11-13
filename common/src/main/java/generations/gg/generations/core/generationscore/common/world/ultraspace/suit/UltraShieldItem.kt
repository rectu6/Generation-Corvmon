package generations.gg.generations.core.generationscore.common.world.ultraspace.suit

import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag

/** An environmental shield (UltraShield), installed on a piece of the Ultra Suit at the smithing table. */
class UltraShieldItem(val shield: UltraShield, properties: Properties) : Item(properties) {
    override fun appendHoverText(stack: ItemStack, context: TooltipContext, lines: MutableList<Component>, flag: TooltipFlag) {
        super.appendHoverText(stack, context, lines, flag)
        UltraSuit.appendShieldTooltip(shield, lines)
    }
}
