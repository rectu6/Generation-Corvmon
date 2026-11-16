package generations.gg.generations.core.generationscore.common.world.charm

import com.mojang.datafixers.util.Pair
import generations.gg.generations.core.generationscore.common.world.container.GenerationsContainers
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

/**
 * The charm case's menu: a row of 8 charm slots, a row of 4 battle item slots below (both
 * centered), and the player's inventory. Each slot takes one item of its own kind (like an
 * armor slot), never an item already in the case; the server checks it (Slot.mayPlace).
 */
class CharmCaseMenu(id: Int, playerInventory: Inventory, private val case: Container) :
    AbstractContainerMenu(GenerationsContainers.CHARM_CASE.value(), id) {

    /** On the client: the slots are filled by the server's updates. */
    constructor(id: Int, playerInventory: Inventory) : this(id, playerInventory, SimpleContainer(CharmCase.SIZE))

    class CaseSlot(container: Container, index: Int, x: Int, y: Int) : Slot(container, index, x, y) {
        val place: CharmCase.Place get() = CharmCase.PLACES[containerSlot]

        override fun mayPlace(stack: ItemStack): Boolean =
            place.accepts(stack) && !CharmCase.hasElsewhere(container, stack.item, containerSlot)

        override fun getMaxStackSize(): Int = 1

        /** The silhouette of the slot's item when empty, like the armor slots. */
        override fun getNoItemIcon(): Pair<ResourceLocation, ResourceLocation> = Pair.of(InventoryMenu.BLOCK_ATLAS, place.silhouette)
    }

    init {
        checkContainerSize(case, CharmCase.SIZE)
        case.startOpen(playerInventory.player)
        row(0, CharmCase.CHARMS.size, CHARMS_Y)
        row(CharmCase.CHARMS.size, CharmCase.BATTLE_ITEMS.size, BATTLE_ITEMS_Y)
        // As a chest (ChestMenu: 103 and 161 for 4 rows, whose slots end at 89): the inventory below the case.
        val offset = CASE_BOTTOM - 89
        for (row in 0 until 3) for (column in 0 until 9) {
            addSlot(Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 103 + row * 18 + offset))
        }
        for (column in 0 until 9) addSlot(Slot(playerInventory, column, 8 + column * 18, 161 + offset))
    }

    /** A centered row of count case slots from first. */
    private fun row(first: Int, count: Int, y: Int) {
        val left = (WIDTH - count * 18) / 2 + 1
        for (i in 0 until count) addSlot(CaseSlot(case, first + i, left + i * 18, y))
    }

    override fun stillValid(player: Player): Boolean = player.isAlive

    override fun quickMoveStack(player: Player, index: Int): ItemStack {
        val slot = slots.getOrNull(index) ?: return ItemStack.EMPTY
        if (!slot.hasItem()) return ItemStack.EMPTY
        val stack = slot.item
        val copy = stack.copy()
        if (index < CharmCase.SIZE) {
            if (!moveItemStackTo(stack, CharmCase.SIZE, slots.size, true)) return ItemStack.EMPTY
        } else {
            // One item into the slot of its kind.
            if (!moveItemStackTo(stack, 0, CharmCase.SIZE, false)) return ItemStack.EMPTY
        }
        if (stack.isEmpty) slot.setByPlayer(ItemStack.EMPTY) else slot.setChanged()
        if (stack.count == copy.count) return ItemStack.EMPTY
        slot.onTake(player, stack)
        return copy
    }

    override fun removed(player: Player) {
        super.removed(player)
        case.stopOpen(player)
    }

    companion object {
        /** Width of the screen (a chest's). */
        const val WIDTH = 176

        /** Top of the charms' row, of the battle items' row (4 pixels apart), and the bottom of the case's part. */
        const val CHARMS_Y = 18
        const val BATTLE_ITEMS_Y = CHARMS_Y + 18 + 4
        const val CASE_BOTTOM = BATTLE_ITEMS_Y - 1 + 18
    }
}
