package generations.gg.generations.core.generationscore.common.world.raid.den

import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.saveddata.SavedData

/**
 * Every Dynamax den known in a level, saved with the world: its biome and its raid
 * (tier and boss, generated once). Dens register when their block is first loaded,
 * so this also holds the dens of old worlds. It works with the den's chunk unloaded.
 */
class RaidDenRegistry : SavedData() {
    class Den(val pos: BlockPos) {
        /** Biome id, read when the den registers. */
        var biome = ""
        /** 1 to 6 stars, 0 when inactive. */
        var stars = 0
        /** The raid's boss, generated once: species, level, IVs, nature, ability, shiny. */
        var boss: CompoundTag? = null
        /** Minecraft day of the last respawn attempt. */
        var lastAttemptDay = -1L

        val active: Boolean get() = stars > 0 && boss != null

        fun save(): CompoundTag = CompoundTag().apply {
            putLong("pos", pos.asLong())
            putString("biome", biome)
            putInt("stars", stars)
            boss?.let { put("boss", it) }
            putLong("lastAttemptDay", lastAttemptDay)
        }

        companion object {
            fun load(tag: CompoundTag) = Den(BlockPos.of(tag.getLong("pos"))).apply {
                biome = tag.getString("biome")
                stars = tag.getInt("stars")
                boss = if (tag.contains("boss", Tag.TAG_COMPOUND.toInt())) tag.getCompound("boss") else null
                lastAttemptDay = tag.getLong("lastAttemptDay")
            }
        }
    }

    private val dens = HashMap<Long, Den>()
    /** Minecraft day of the last morning processed. */
    var lastProcessedDay = -1L
        set(value) {
            field = value
            setDirty()
        }

    val all: Collection<Den> get() = dens.values

    operator fun get(pos: BlockPos): Den? = dens[pos.asLong()]

    fun add(pos: BlockPos): Den = Den(pos.immutable()).also {
        dens[pos.asLong()] = it
        setDirty()
    }

    fun remove(pos: BlockPos) {
        if (dens.remove(pos.asLong()) != null) setDirty()
    }

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        tag.put("dens", ListTag().apply { dens.values.forEach { add(it.save()) } })
        tag.putLong("lastProcessedDay", lastProcessedDay)
        return tag
    }

    companion object {
        private const val NAME = "generations_raid_dens"

        private fun factory() = Factory(::RaidDenRegistry, ::load, null)

        private fun load(tag: CompoundTag, registries: HolderLookup.Provider) = RaidDenRegistry().apply {
            tag.getList("dens", Tag.TAG_COMPOUND.toInt()).forEach { entry ->
                val den = Den.load(entry as CompoundTag)
                dens[den.pos.asLong()] = den
            }
            lastProcessedDay = tag.getLong("lastProcessedDay")
        }

        /** The registry of the level, created when needed. */
        fun of(level: ServerLevel): RaidDenRegistry = level.dataStorage.computeIfAbsent(factory(), NAME)

        /** The registry of the level if it has one. */
        fun existing(level: ServerLevel): RaidDenRegistry? = level.dataStorage.get(factory(), NAME)
    }
}
