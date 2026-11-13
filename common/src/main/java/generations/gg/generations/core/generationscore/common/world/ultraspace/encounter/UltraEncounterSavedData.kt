package generations.gg.generations.core.generationscore.common.world.ultraspace.encounter

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID

/**
 * The arrival encounters (phase 5), saved with the Overworld
 * (data/generations_core_ultra_encounters.dat), one per journey: drawn once at the
 * arrival and saved before it appears, so that neither a disconnection nor a restart
 * draws it again; SPAWNED once it is in the world, never again for that journey.
 */
class UltraEncounterSavedData : SavedData() {
    enum class State { DRAWN, SPAWNED, EXPIRED, NONE }

    class Entry(
        val journey: UUID,
        val player: UUID,
        /** The Ultra world (ultra_jungle...) where it appears. */
        val world: String,
        val biome: String,
        val lightYears: Double,
        val portalLevel: Int,
        val ultraBeast: Boolean,
        val species: String,
        val level: Int,
        val shiny: Boolean,
        /** Game time of the draw, and from when it may appear. */
        val drawnAt: Long,
        var readyAt: Long,
        var state: State,
        var entity: UUID? = null,
        val test: Boolean = false,
    ) {
        fun save(): CompoundTag = CompoundTag().apply {
            putUUID("journey", journey)
            putUUID("player", player)
            putString("world", world)
            putString("biome", biome)
            putDouble("lightYears", lightYears)
            putInt("portalLevel", portalLevel)
            putBoolean("ultraBeast", ultraBeast)
            putString("species", species)
            putInt("level", level)
            putBoolean("shiny", shiny)
            putLong("drawnAt", drawnAt)
            putLong("readyAt", readyAt)
            putString("state", state.name)
            entity?.let { putUUID("entity", it) }
            putBoolean("test", test)
        }

        companion object {
            fun load(tag: CompoundTag) = Entry(
                tag.getUUID("journey"), tag.getUUID("player"), tag.getString("world"), tag.getString("biome"), tag.getDouble("lightYears"),
                tag.getInt("portalLevel"), tag.getBoolean("ultraBeast"), tag.getString("species"), tag.getInt("level"), tag.getBoolean("shiny"),
                tag.getLong("drawnAt"), tag.getLong("readyAt"),
                runCatching { State.valueOf(tag.getString("state")) }.getOrDefault(State.NONE),
                if (tag.hasUUID("entity")) tag.getUUID("entity") else null, tag.getBoolean("test"),
            )
        }
    }

    /** By journey, in the order they came. */
    val entries = LinkedHashMap<UUID, Entry>()

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        tag.put("encounters", ListTag().apply { entries.values.forEach { add(it.save()) } })
        return tag
    }

    /** Old finished entries go (the journeys' ids stay known while they are kept). */
    fun prune() {
        val done = entries.values.filter { it.state != State.DRAWN }
        if (done.size > KEEP) done.take(done.size - KEEP).forEach { entries.remove(it.journey) }
    }

    companion object {
        private const val NAME = "generations_core_ultra_encounters"
        private const val KEEP = 2000

        private fun load(tag: CompoundTag, registries: HolderLookup.Provider) = UltraEncounterSavedData().apply {
            tag.getList("encounters", Tag.TAG_COMPOUND.toInt()).forEach { entry ->
                runCatching { Entry.load(entry as CompoundTag) }.getOrNull()?.let { entries[it.journey] = it }
            }
        }

        private val FACTORY = SavedData.Factory(::UltraEncounterSavedData, ::load, null)

        fun get(server: MinecraftServer): UltraEncounterSavedData = server.overworld().dataStorage.computeIfAbsent(FACTORY, NAME)
    }
}
