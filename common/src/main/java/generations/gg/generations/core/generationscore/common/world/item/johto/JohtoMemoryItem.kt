package generations.gg.generations.core.generationscore.common.world.item.johto

import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.locale.Language
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Rarity
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.component.CustomData
import java.util.UUID

/**
 * A memory of Raikou, Entei or Suicune (JOHTO-PLAN.md § 8): one per won trial, not stackable,
 * fire proof. It remembers who won it (custom data johto_owner / johto_owner_name, shown in its
 * tooltip): with memoriesBound (D12), only them may offer it at the Bell Tower's altar.
 */
class JohtoMemoryItem(properties: Properties) : Item(properties.stacksTo(1).rarity(Rarity.EPIC).fireResistant()) {
    override fun isFoil(stack: ItemStack): Boolean = true

    override fun appendHoverText(stack: ItemStack, context: TooltipContext, tooltip: MutableList<Component>, flag: TooltipFlag) {
        val language = Language.getInstance()
        var key = "$descriptionId.tooltip"
        var i = 1
        while (language.has(key)) {
            tooltip += Component.translatable(key).withStyle(if (i == 1) ChatFormatting.GOLD else ChatFormatting.GRAY)
            i++
            key = "$descriptionId.tooltip.$i"
        }
        ownerName(stack)?.let { tooltip += Component.translatable("generations_core.johto.memory.owner", it).withStyle(ChatFormatting.DARK_AQUA) }
    }

    companion object {
        private const val OWNER = "johto_owner"
        private const val OWNER_NAME = "johto_owner_name"

        fun stamp(stack: ItemStack, owner: UUID, name: String): ItemStack {
            val tag = stack.get(DataComponents.CUSTOM_DATA)?.copyTag() ?: CompoundTag()
            tag.putString(OWNER, owner.toString())
            tag.putString(OWNER_NAME, name)
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
            return stack
        }

        fun owner(stack: ItemStack): UUID? = stack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.takeIf { it.contains(OWNER) }
            ?.let { runCatching { UUID.fromString(it.getString(OWNER)) }.getOrNull() }

        fun ownerName(stack: ItemStack): String? = stack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.takeIf { it.contains(OWNER_NAME) }?.getString(OWNER_NAME)
    }
}
