package generations.gg.generations.core.generationscore.common.world.charm

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag

/** A charm: an ordinary item with a line saying what it does (and where it works). */
class CharmItem(properties: Properties, val charm: Charm) : Item(properties) {
    override fun appendHoverText(stack: ItemStack, context: TooltipContext, lines: MutableList<Component>, flag: TooltipFlag) {
        super.appendHoverText(stack, context, lines, flag)
        lines += Component.translatable(charm.effectKey).withStyle(ChatFormatting.GRAY)
        if (charm != Charm.OLD && charm != Charm.OVAL) lines += Component.translatable("generations_core.charm.where").withStyle(ChatFormatting.DARK_GRAY)
    }
}
