package generations.gg.generations.core.generationscore.common.world.ultraspace.rift

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.saveddata.SavedData

/**
 * The open rifts of every Ultra world, saved with the save (the Overworld's data,
 * generations_core_ultra_rifts.dat): after a restart they are where they were, with the
 * time they had left (game time). A rift being crossed is never saved: it is gone.
 */
class UltraRiftSavedData : SavedData() {
    val rifts = ArrayList<UltraRift>()
    var nextId = 1

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        tag.putInt("nextId", nextId)
        tag.put("rifts", ListTag().apply { rifts.filter { !it.taken }.forEach { add(it.save()) } })
        return tag
    }

    companion object {
        const val NAME = "generations_core_ultra_rifts"

        private fun load(tag: CompoundTag, registries: HolderLookup.Provider): UltraRiftSavedData = UltraRiftSavedData().also { data ->
            data.nextId = tag.getInt("nextId").coerceAtLeast(1)
            tag.getList("rifts", Tag.TAG_COMPOUND.toInt()).forEach { entry ->
                runCatching { data.rifts.add(UltraRift.load(entry as CompoundTag)) }
            }
        }

        private val FACTORY = SavedData.Factory(::UltraRiftSavedData, ::load, null)

        fun get(server: MinecraftServer): UltraRiftSavedData = server.overworld().dataStorage.computeIfAbsent(FACTORY, NAME)
    }
}
