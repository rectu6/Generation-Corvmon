package generations.gg.generations.core.generationscore.common.world.hooh

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NumericTag
import net.minecraft.nbt.Tag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID

/**
 * The passages of Ho-Oh, saved with the world: the Overworld's SavedData
 * generations_ho_oh_passage, one HoOhRecord by player (about 70 bytes). Marked dirty at every
 * change; written at once when a feather is won (it must survive a crash: no feather lost, none
 * given twice).
 */
class HoOhPassageData : SavedData() {
    val records = HashMap<UUID, HoOhRecord>()

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        val list = ListTag()
        records.forEach { (player, record) ->
            list.add(CompoundTag().apply {
                putUUID("player", player)
                record.save().forEach { (key, value) ->
                    when (value) {
                        is Long -> putLong(key, value)
                        is Int -> putInt(key, value)
                        is Boolean -> putInt(key, if (value) 1 else 0)
                        else -> putString(key, value.toString())
                    }
                }
            })
        }
        tag.put("records", list)
        return tag
    }

    companion object {
        const val NAME = "generations_ho_oh_passage"

        fun of(server: MinecraftServer): HoOhPassageData =
            server.overworld().dataStorage.computeIfAbsent(Factory(::HoOhPassageData, ::load, null), NAME)

        private fun load(tag: CompoundTag, registries: HolderLookup.Provider) = HoOhPassageData().apply {
            tag.getList("records", Tag.TAG_COMPOUND.toInt()).forEach { entry ->
                val compound = entry as CompoundTag
                runCatching {
                    val values = HashMap<String, Any?>()
                    for (key in compound.allKeys) {
                        val value = compound.get(key)
                        if (value is NumericTag) values[key] = value.asNumber
                    }
                    records[compound.getUUID("player")] = HoOhRecord.load(values)
                }
            }
        }

        fun get(server: MinecraftServer, player: UUID): HoOhRecord? = of(server).records[player]

        fun getOrCreate(server: MinecraftServer, player: UUID): HoOhRecord {
            val data = of(server)
            return data.records.getOrPut(player) { data.setDirty(); HoOhRecord() }
        }

        fun changed(server: MinecraftServer) = of(server).setDirty()

        fun saveNow(server: MinecraftServer) {
            of(server).setDirty()
            server.overworld().dataStorage.save()
        }
    }
}
