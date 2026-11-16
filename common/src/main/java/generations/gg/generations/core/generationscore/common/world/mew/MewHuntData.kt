package generations.gg.generations.core.generationscore.common.world.mew

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NumericTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID

/**
 * The Mew hunts, saved with the world (D3): the Overworld's SavedData generations_mew_hunt, one
 * MewRecord by player who ever stood on an island (about 60 bytes each). Marked dirty at every
 * change (written by the world's autosave and at the stop), written at once at the important
 * moments (unlock, and later victory, release, capture).
 */
class MewHuntData : SavedData() {
    val records = HashMap<UUID, MewRecord>()

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        val list = ListTag()
        records.forEach { (player, record) ->
            list.add(CompoundTag().apply {
                putUUID("player", player)
                record.save().forEach { (key, value) ->
                    when (value) {
                        is Long -> putLong(key, value)
                        is Int -> putInt(key, value)
                        is Double -> putDouble(key, value)
                        is Boolean -> putBoolean(key, value)
                        else -> putString(key, value.toString())
                    }
                }
            })
        }
        tag.put("records", list)
        return tag
    }

    companion object {
        const val NAME = "generations_mew_hunt"

        fun of(server: MinecraftServer): MewHuntData =
            server.overworld().dataStorage.computeIfAbsent(Factory(::MewHuntData, ::load, null), NAME)

        private fun load(tag: CompoundTag, registries: HolderLookup.Provider) = MewHuntData().apply {
            tag.getList("records", Tag.TAG_COMPOUND.toInt()).forEach { entry ->
                val compound = entry as CompoundTag
                runCatching {
                    val values = HashMap<String, Any?>()
                    for (key in compound.allKeys) {
                        when (val value = compound.get(key)) {
                            is StringTag -> values[key] = value.asString
                            is NumericTag -> values[key] = value.asNumber
                        }
                    }
                    records[compound.getUUID("player")] = MewRecord.load(values)
                }
            }
        }

        fun get(server: MinecraftServer, player: UUID): MewRecord? = of(server).records[player]

        fun getOrCreate(server: MinecraftServer, player: UUID, machine: MewStateMachine): MewRecord {
            val data = of(server)
            return data.records.getOrPut(player) { data.setDirty(); machine.create() }
        }

        fun changed(server: MinecraftServer) = of(server).setDirty()

        /** Written to disk now (with the Overworld's other changed data): an unlock must survive a crash. */
        fun saveNow(server: MinecraftServer) {
            of(server).setDirty()
            server.overworld().dataStorage.save()
        }
    }
}
