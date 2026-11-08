package generations.gg.generations.core.generationscore.common.world.item

import generations.gg.generations.core.generationscore.common.world.container.ContainerFilter
import generations.gg.generations.core.generationscore.common.world.container.GenericContainer
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.component.ItemContainerContents
import net.minecraft.world.level.Level

/**
 * A bag of 27 slots that only holds Apricorns, stored in the item (like a shulker
 * box). Against duplication: the box's own slot is locked and cannot be swapped
 * with number keys or F, the content is only written while the box is still in the
 * same hand, and the menu closes as soon as it is not.
 */
class ApricornBoxItem(properties: Properties) : Item(properties) {

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val box = player.getItemInHand(hand)
        if (level.isClientSide) return InteractionResultHolder.success(box)

        val stillHeld = { p: Player -> p.getItemInHand(hand) === box }
        val content = object : SimpleContainer(SIZE) {
            init {
                box.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items)
            }

            override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean = ContainerFilter.APRICORNS.test(stack)

            override fun setChanged() {
                super.setChanged()
                if (stillHeld(player)) box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items))
            }
        }

        val lock = if (hand == InteractionHand.MAIN_HAND) player.inventory.selected else OFFHAND_SLOT
        GenericContainer.openScreen(
            content, 9, SIZE / 9, box.hoverName, player, lock,
            filter = ContainerFilter.APRICORNS, valid = stillHeld,
        )
        level.playSound(null, player.x, player.y, player.z, SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.8f, 1.0f)
        return InteractionResultHolder.consume(box)
    }

    override fun appendHoverText(stack: ItemStack, context: TooltipContext, tooltip: MutableList<Component>, flag: TooltipFlag) {
        val count = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItems().sumOf { it.count }
        tooltip.add(Component.translatable("item.generations_core.apricorn_box.tooltip", count))
    }

    companion object {
        const val SIZE = 27
        private const val OFFHAND_SLOT = 40
    }
}
