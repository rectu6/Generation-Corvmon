package generations.gg.generations.core.generationscore.common.world.level.block.raid

import generations.gg.generations.core.generationscore.common.world.level.block.entities.GenerationsBlockEntities
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import java.util.UUID

/**
 * A den's block entity. Its raid now lives in RaidDenRegistry (saved with the world,
 * usable with the chunk unloaded); stars and species only remain to take over the
 * raids of the former Wishing system.
 */
class RaidDenBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(GenerationsBlockEntities.RAID_DEN.value(), pos, state) {
    /** Former system: 1 to 6, 0 when the den is empty. */
    var stars = 0
    /** Former system: species id of the raid Pokémon. */
    var species = ""
    var boss: UUID? = null

    val isActive: Boolean get() = stars > 0

    companion object {
        /** Registration and beam, about once a second (RaidDens.tickDen). */
        fun serverTick(level: net.minecraft.world.level.Level, pos: BlockPos, state: BlockState, entity: RaidDenBlockEntity) {
            if (level !is net.minecraft.server.level.ServerLevel || Math.floorMod(level.gameTime + pos.asLong(), 20L) != 0L) return
            generations.gg.generations.core.generationscore.common.world.raid.den.RaidDens.tickDen(level, pos, state, entity)
        }
    }

    fun clear() {
        stars = 0
        species = ""
        boss = null
        setChanged()
    }

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        tag.putInt("stars", stars)
        tag.putString("species", species)
        boss?.let { tag.putUUID("boss", it) }
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        stars = tag.getInt("stars")
        species = tag.getString("species")
        boss = if (tag.hasUUID("boss")) tag.getUUID("boss") else null
    }
}
