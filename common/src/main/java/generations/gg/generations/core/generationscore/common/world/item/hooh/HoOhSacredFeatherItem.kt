package generations.gg.generations.core.generationscore.common.world.item.hooh

import net.minecraft.ChatFormatting
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Rarity
import net.minecraft.world.item.TooltipFlag

/**
 * The Sacred Feather of Ho-Oh (HOOH-PLAN.md § 8): given only by a successful Passage of the
 * Rainbow (one per passage) or the test commands; no recipe, no loot. Taken by the RKS Machine
 * to make the Sacred Ash. Ho-Oh's fire does not burn it (fire and lava resistant).
 */
class HoOhSacredFeatherItem(properties: Properties) : Item(properties.stacksTo(16).rarity(Rarity.EPIC).fireResistant()) {
    override fun appendHoverText(stack: ItemStack, context: TooltipContext, tooltip: MutableList<Component>, flag: TooltipFlag) {
        val language = Language.getInstance()
        var key = "$descriptionId.tooltip"
        var i = 1
        while (language.has(key)) {
            tooltip += Component.translatable(key).withStyle(if (i == 1) ChatFormatting.GOLD else ChatFormatting.GRAY)
            i++
            key = "$descriptionId.tooltip.$i"
        }
    }
}
