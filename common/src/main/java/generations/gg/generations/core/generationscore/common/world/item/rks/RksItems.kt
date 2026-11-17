package generations.gg.generations.core.generationscore.common.world.item.rks

import generations.gg.generations.core.generationscore.common.world.level.block.rks.RksConsoleBlockEntity
import generations.gg.generations.core.generationscore.common.world.level.block.rks.RksModuleBlock
import generations.gg.generations.core.generationscore.common.world.level.block.rks.RksModuleBlockEntity
import generations.gg.generations.core.generationscore.common.world.level.block.rks.RksModuleItemData
import generations.gg.generations.core.generationscore.common.world.rks.RksSettings
import generations.gg.generations.core.generationscore.common.world.rks.machine.RksMachines
import net.minecraft.ChatFormatting
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Rarity
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.context.BlockPlaceContext

/** Its lines of tooltip, from the language files: <description id>.tooltip, .tooltip.2, ... */
private fun lines(descriptionId: String, out: MutableList<Component>, colour: ChatFormatting = ChatFormatting.GRAY) {
    val language = net.minecraft.locale.Language.getInstance()
    var key = "$descriptionId.tooltip"
    var i = 1
    while (language.has(key)) {
        out += Component.translatable(key).withStyle(colour)
        i++
        key = "$descriptionId.tooltip.$i"
    }
}

/**
 * A module's item (RKS-PLAN.md § 3.1): drawn in 3D (D15, RksModuleItemRenderer); placed near an
 * incomplete console, it goes to its place in the console's way (D7, assisted placement). The
 * console's item shows the energy it keeps, the chamber's the damage.
 */
class RksModuleItem(block: RksModuleBlock, properties: Properties) : BlockItem(block, properties) {
    val module = block.module

    override fun updatePlacementContext(context: BlockPlaceContext): BlockPlaceContext? {
        val range = RksSettings.machine.assistedPlacementRange
        val target = RksMachines.assistedTarget(context.level, context.clickedPos, module, range) ?: return context
        if (target == context.clickedPos) return context
        return BlockPlaceContext.at(context, target, Direction.UP)
    }

    override fun appendHoverText(stack: ItemStack, context: TooltipContext, tooltip: MutableList<Component>, flag: TooltipFlag) {
        super.appendHoverText(stack, context, tooltip, flag)
        lines(descriptionId, tooltip)
        val data = RksModuleItemData.read(stack) ?: return
        val energy = data.getInt(RksConsoleBlockEntity.ITEM_ENERGY)
        if (energy > 0) tooltip += Component.translatable("generations_core.rks.item.energy", energy, RksSettings.mewtwo.energyRequired).withStyle(ChatFormatting.LIGHT_PURPLE)
        val fire = data.getInt(RksConsoleBlockEntity.ITEM_FIRE_ENERGY)
        if (fire > 0) tooltip += Component.translatable("generations_core.rks.item.energy.fire", fire, RksSettings.fire.energyRequired).withStyle(ChatFormatting.GOLD)
        if (data.getBoolean(RksModuleBlockEntity.ITEM_DAMAGED)) tooltip += Component.translatable("generations_core.rks.item.damaged").withStyle(ChatFormatting.RED)
    }
}

/**
 * The research files M-01, M-02 and M-03 (RKS-PLAN.md § 4.3, D14): taken at every new Mewtwo.
 * They cannot be obtained in survival: no recipe, no loot, no quest; /give and the test command
 * only.
 */
class RksResearchItem(properties: Properties) : Item(properties.stacksTo(16).rarity(Rarity.EPIC)) {
    override fun appendHoverText(stack: ItemStack, context: TooltipContext, tooltip: MutableList<Component>, flag: TooltipFlag) {
        lines(descriptionId, tooltip)
    }
}

/** Repairs a damaged RKS Machine (§ 5.4): used on any of its modules. */
class RksRepairKitItem(properties: Properties) : Item(properties.stacksTo(16).rarity(Rarity.RARE)) {
    override fun appendHoverText(stack: ItemStack, context: TooltipContext, tooltip: MutableList<Component>, flag: TooltipFlag) {
        lines(descriptionId, tooltip)
    }
}
