package generations.gg.generations.core.generationscore.common.world.ultraspace.weather

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.saveddata.SavedData

/**
 * The weather of the seven Ultra worlds, saved with the Overworld
 * (data/generations_core_ultra_weather.dat): after a restart each world's event goes on
 * where it was (the times are the Overworld's game time, which stops with the server).
 */
class UltraWeatherSavedData : SavedData() {
    class WorldState(
        /** The weather in progress, null during a calm. */
        var event: String? = null,
        var start: Long = 0,
        var end: Long = 0,
        /** During a calm: the weather drawn for next, and when it starts (-1: not drawn yet). */
        var next: String? = null,
        var nextStart: Long = -1,
        var wind: Float = 0f,
        /** The players were warned of the next dangerous weather. */
        var warned: Boolean = false,
    )

    val worlds = LinkedHashMap<String, WorldState>()

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        for ((id, state) in worlds) {
            tag.put(id, CompoundTag().apply {
                state.event?.let { putString("event", it) }
                putLong("start", state.start)
                putLong("end", state.end)
                state.next?.let { putString("next", it) }
                putLong("nextStart", state.nextStart)
                putFloat("wind", state.wind)
                putBoolean("warned", state.warned)
            })
        }
        return tag
    }

    companion object {
        const val NAME = "generations_core_ultra_weather"

        private fun load(tag: CompoundTag, registries: HolderLookup.Provider): UltraWeatherSavedData = UltraWeatherSavedData().also { data ->
            for (id in tag.allKeys) {
                val world = tag.getCompound(id)
                data.worlds[id] = WorldState(
                    world.getString("event").ifEmpty { null },
                    world.getLong("start"),
                    world.getLong("end"),
                    world.getString("next").ifEmpty { null },
                    if (world.contains("nextStart")) world.getLong("nextStart") else -1,
                    world.getFloat("wind"),
                    world.getBoolean("warned"),
                )
            }
        }

        private val FACTORY = SavedData.Factory(::UltraWeatherSavedData, ::load, null)

        fun get(server: MinecraftServer): UltraWeatherSavedData = server.overworld().dataStorage.computeIfAbsent(FACTORY, NAME)
    }
}
