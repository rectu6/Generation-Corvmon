package generations.gg.generations.core.generationscore.common.world.rks.machine

import generations.gg.generations.core.generationscore.common.world.container.GenerationsContainers
import generations.gg.generations.core.generationscore.common.world.level.block.rks.RksConsoleBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

/**
 * The RKS Machine's menu (RKS-PLAN.md § 4.1): the console's 9 input slots, its output and the
 * three research files' slots, with the player's inventory. The machine's state, energy and
 * progress are read from the console's block entity (sent only when they change); every important
 * click is a packet the server checks (C2SRksActionPacket).
 *
 * The inputs are locked while the machine works; the research slots only open once the samples
 * are analysed (or while they hold a file).
 */
class RksConsoleMenu(
    id: Int,
    val inventory: Inventory,
    private val container: Container,
    val pos: BlockPos,
) : AbstractContainerMenu(GenerationsContainers.RKS_CONSOLE.value(), id) {

    /** The console on this side (the client's copy is kept up to date by its syncs). */
    val console: RksConsoleBlockEntity? get() = inventory.player.level().getBlockEntity(pos) as? RksConsoleBlockEntity

    private fun locked(): Boolean = console?.state?.locksSlots ?: false

    private fun researchOpen(): Boolean = console?.machine?.analysed == true

    inner class Input(index: Int, x: Int, y: Int) : Slot(container, index, x, y) {
        override fun mayPlace(stack: ItemStack): Boolean = !locked()
        override fun mayPickup(player: Player): Boolean = !locked()
    }

    inner class Output(x: Int, y: Int) : Slot(container, RksConsoleBlockEntity.OUTPUT, x, y) {
        override fun mayPlace(stack: ItemStack): Boolean = false
    }

    inner class Research(val file: Int, x: Int, y: Int) : Slot(container, RksConsoleBlockEntity.RESEARCH + file, x, y) {
        override fun mayPlace(stack: ItemStack): Boolean {
            if (locked() || !researchOpen()) return false
            val wanted = console?.researchItems()?.getOrNull(file) ?: return false
            return stack.`is`(wanted)
        }

        override fun mayPickup(player: Player): Boolean = !locked()

        override fun getMaxStackSize(): Int = 1

        override fun isActive(): Boolean = researchOpen() || hasItem()
    }

    init {
        checkContainerSize(container, RksConsoleBlockEntity.SLOTS)
        container.startOpen(inventory.player)
        for (row in 0..2) for (column in 0..2) addSlot(Input(row * 3 + column, INPUT_X + column * 18, INPUT_Y + row * 18))
        addSlot(Output(OUTPUT_X, OUTPUT_Y))
        for (i in 0 until RksConsoleBlockEntity.RESEARCH_SLOTS) addSlot(Research(i, RESEARCH_X + i * 22, RESEARCH_Y))
        for (row in 0..2) for (column in 0..8) addSlot(Slot(inventory, column + row * 9 + 9, PLAYER_X + column * 18, PLAYER_Y + row * 18))
        for (column in 0..8) addSlot(Slot(inventory, column, PLAYER_X + column * 18, PLAYER_Y + 58))
    }

    override fun quickMoveStack(player: Player, index: Int): ItemStack {
        val slot = slots.getOrNull(index) ?: return ItemStack.EMPTY
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY
        val stack = slot.item
        val original = stack.copy()
        val machineEnd = RksConsoleBlockEntity.SLOTS
        if (index < machineEnd) {
            if (!moveItemStackTo(stack, machineEnd, slots.size, true)) return ItemStack.EMPTY
        } else {
            // A research file to its slot first (when open), anything else to the inputs.
            var moved = false
            for (i in 0 until RksConsoleBlockEntity.RESEARCH_SLOTS) {
                val target = slots[RksConsoleBlockEntity.RESEARCH + i]
                if (!target.hasItem() && target.mayPlace(stack)) {
                    target.set(stack.split(1))
                    moved = true
                    break
                }
            }
            if (!moved && (locked() || !moveItemStackTo(stack, 0, RksConsoleBlockEntity.INPUTS, false))) return ItemStack.EMPTY
        }
        if (stack.isEmpty) slot.setByPlayer(ItemStack.EMPTY) else slot.setChanged()
        return original
    }

    override fun stillValid(player: Player): Boolean = container.stillValid(player)

    override fun removed(player: Player) {
        super.removed(player)
        container.stopOpen(player)
    }

    companion object {
        const val INPUT_X = 12
        const val INPUT_Y = 170
        const val OUTPUT_X = 84
        const val OUTPUT_Y = 188
        const val RESEARCH_X = 244
        const val RESEARCH_Y = 118
        const val PLAYER_X = 172
        const val PLAYER_Y = 160

        /** The client's menu: the console from the client's level, the slots synced by the menu. */
        fun fromBuffer(id: Int, inventory: Inventory, buffer: FriendlyByteBuf): RksConsoleMenu =
            RksConsoleMenu(id, inventory, SimpleContainer(RksConsoleBlockEntity.SLOTS), buffer.readBlockPos())
    }
}
