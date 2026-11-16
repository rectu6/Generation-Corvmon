package generations.gg.generations.core.generationscore.common.world.level.block.rks

import generations.gg.generations.core.generationscore.common.world.level.block.entities.GenerationsBlockEntities
import generations.gg.generations.core.generationscore.common.world.level.block.entities.SimpleBlockEntity
import generations.gg.generations.core.generationscore.common.world.rks.machine.RksModule
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB

/**
 * A module's block entity (chamber, reactor, power module, analyser), on its first cell only.
 * Little to keep: the chamber's damage (RKS-PLAN.md § 3.2: it stays with the chamber, also in its
 * item). The console it belongs to is found from its place and its facing (RksMachines.consoleOf).
 */
class RksModuleBlockEntity(pos: BlockPos, state: BlockState) : SimpleBlockEntity(GenerationsBlockEntities.RKS_MODULE, pos, state) {
    val module: RksModule get() = (blockState.block as? RksModuleBlock)?.module ?: RksModule.CHAMBER

    /** The chamber's glass is broken (the machine is damaged until repaired). */
    var damaged = false
        private set

    fun setDamaged(value: Boolean) {
        if (damaged == value) return
        damaged = value
        sync()
    }

    override fun readNbt(nbt: CompoundTag, provider: HolderLookup.Provider) {
        damaged = nbt.getBoolean(DAMAGED)
    }

    override fun writeNbt(nbt: CompoundTag, provider: HolderLookup.Provider) {
        if (damaged) nbt.putBoolean(DAMAGED, true)
    }

    /** Drawn from its cells' box (the machine's parts rise above some of them). */
    fun renderBox(): AABB = AABB(blockPos).inflate(2.0, 0.0, 2.0).expandTowards(0.0, 4.0, 0.0)

    companion object {
        private const val DAMAGED = "Damaged"
        /** In the chamber's item: the machine it came from was damaged. */
        const val ITEM_DAMAGED = "RksDamaged"
    }
}
