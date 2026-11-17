package generations.gg.generations.core.generationscore.common.world.johto

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
 * The Legend of Johto, saved with the world: the Overworld's SavedData generations_johto, one
 * JohtoRecord by player. Marked dirty at every change; written at once (saveNow) at the moments
 * that must survive a crash: an ash consumed, a session opened, a trial won, a memory given,
 * before a wild beast appears, the memories consumed, before Ho-Oh appears.
 */
class JohtoData : SavedData() {
    val records = HashMap<UUID, JohtoRecord>()

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
        const val NAME = "generations_johto"

        fun of(server: MinecraftServer): JohtoData =
            server.overworld().dataStorage.computeIfAbsent(Factory(::JohtoData, ::load, null), NAME)

        private fun load(tag: CompoundTag, registries: HolderLookup.Provider) = JohtoData().apply {
            tag.getList("records", Tag.TAG_COMPOUND.toInt()).forEach { entry ->
                val compound = entry as CompoundTag
                runCatching {
                    val values = HashMap<String, Any?>()
                    for (key in compound.allKeys) {
                        when (val value = compound.get(key)) {
                            is NumericTag -> values[key] = value.asNumber
                            is StringTag -> values[key] = value.asString
                        }
                    }
                    records[compound.getUUID("player")] = JohtoRecord.load(values)
                }
            }
        }

        fun get(server: MinecraftServer, player: UUID): JohtoRecord? = of(server).records[player]

        fun getOrCreate(server: MinecraftServer, player: UUID): JohtoRecord {
            val data = of(server)
            return data.records.getOrPut(player) { data.setDirty(); JohtoRecord() }
        }

        fun changed(server: MinecraftServer) = of(server).setDirty()

        /** Written now (the critical moments). */
        fun saveNow(server: MinecraftServer) {
            of(server).setDirty()
            server.overworld().dataStorage.save()
        }
    }
}
