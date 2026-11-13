package generations.gg.generations.core.generationscore.common.world.ultraspace.suit

import generations.gg.generations.core.generationscore.common.world.item.armor.GenerationsArmorMaterials
import generations.gg.generations.core.generationscore.common.world.item.components.GenerationsDataComponents
import generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.UltraWarpTransfers
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ArmorItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

/**
 * The Ultra Suit: the mod's existing "ultra" armour set (GenerationsArmor.ULTRA:
 * ultra_helmet, ultra_chestplate, ultra_leggings, ultra_boots, material "ultra"), kept as
 * it is (its speed, its recipes). Phase 4.4 makes it the only way to breathe in the Ultra
 * worlds (UltraBreathing); phase 4.5 gives its pieces the environmental shields
 * (UltraShield), kept in their ultra_shields component.
 */
object UltraSuit {
    val SLOTS = listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)

    /** One of the four pieces of the Ultra Suit (by its material: a copy keeps it). */
    fun isPieceItem(item: Item): Boolean = (item as? ArmorItem)?.material?.`is`(GenerationsArmorMaterials.ULTRA) == true

    fun isPiece(stack: ItemStack): Boolean = !stack.isEmpty && isPieceItem(stack.item)

    /** The four pieces worn. */
    fun fullSuit(entity: LivingEntity): Boolean = SLOTS.all { isPiece(entity.getItemBySlot(it)) }

    /** Pieces of the suit worn (0 to 4). */
    fun piecesWorn(entity: LivingEntity): Int = SLOTS.count { isPiece(entity.getItemBySlot(it)) }

    fun shields(stack: ItemStack): List<UltraShield> = stack.get(GenerationsDataComponents.ULTRA_SHIELDS.value()) ?: emptyList()

    /** The piece with this shield installed too (a copy; enchantments, damage and name kept). */
    fun withShield(stack: ItemStack, shield: UltraShield): ItemStack = stack.copyWithCount(1).also {
        it.set(GenerationsDataComponents.ULTRA_SHIELDS.value(), UltraShield.normalise(shields(stack) + shield))
    }

    /** The piece without this shield (the component removed with the last one). */
    fun withoutShield(stack: ItemStack, shield: UltraShield): ItemStack = stack.copy().also {
        val left = shields(stack) - shield
        if (left.isEmpty()) it.remove(GenerationsDataComponents.ULTRA_SHIELDS.value())
        else it.set(GenerationsDataComponents.ULTRA_SHIELDS.value(), left)
    }

    /** Ultra pieces worn that carry this shield (0 to 4). */
    fun piecesWith(entity: LivingEntity, shield: UltraShield): Int = SLOTS.count {
        val stack = entity.getItemBySlot(it)
        isPiece(stack) && shield in shields(stack)
    }

    /**
     * The part of the shield's danger it removes for this entity, from 0 to 1 (1: no
     * damage). With requireAllPieces (the default), all or nothing: the four pieces worn
     * must all carry it.
     */
    fun protection(entity: LivingEntity, shield: UltraShield): Float {
        val pieces = piecesWith(entity, shield)
        val rules = UltraSuitConfig.settings.shields
        return if (rules.requireAllPieces) (if (pieces == 4) 1f else 0f) else (pieces * rules.partialPerPiece).coerceAtMost(1f)
    }

    /** The lines a piece of the suit adds to its description. */
    fun appendPieceTooltip(stack: ItemStack, lines: MutableList<Component>) {
        lines.add(Component.translatable("generations_core.ultra_suit.tooltip.breathing").withStyle(ChatFormatting.AQUA))
        val shields = shields(stack)
        if (shields.isEmpty()) {
            lines.add(Component.translatable("generations_core.ultra_suit.tooltip.none").withStyle(ChatFormatting.DARK_GRAY))
            lines.add(Component.translatable("generations_core.ultra_suit.tooltip.install").withStyle(ChatFormatting.DARK_GRAY))
        } else {
            lines.add(Component.translatable("generations_core.ultra_suit.tooltip.shields", shields.size, UltraShield.entries.size).withStyle(ChatFormatting.GRAY))
            shields.forEach { lines.add(Component.literal(" ◆ ").withColor(it.colour).append(it.displayName())) }
        }
    }

    /** The lines of a shield's description. */
    fun appendShieldTooltip(shield: UltraShield, lines: MutableList<Component>) {
        lines.add(
            Component.translatable(
                "generations_core.ultra_shield.tooltip.protects",
                Component.translatable("generations_core.ultra_weather.event.${shield.hazard}").withColor(shield.colour),
                UltraWarpTransfers.worldName(shield.world),
            ).withStyle(ChatFormatting.GRAY),
        )
        lines.add(Component.translatable("generations_core.ultra_shield.tooltip.pieces").withStyle(ChatFormatting.DARK_GRAY))
        lines.add(Component.translatable("generations_core.ultra_shield.tooltip.install").withStyle(ChatFormatting.DARK_GRAY))
        lines.add(Component.translatable("generations_core.ultra_shield.tooltip.breathing").withStyle(ChatFormatting.DARK_GRAY))
    }
}
