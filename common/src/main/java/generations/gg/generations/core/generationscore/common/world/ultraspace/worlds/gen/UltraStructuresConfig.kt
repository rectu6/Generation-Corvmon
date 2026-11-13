package generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.gen

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.mojang.logging.LogUtils
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

/**
 * config/generations/core/ultra_space/ultra_structures.json, read by the server (solo's
 * integrated one too): how often the Ultra Space structures appear. One entry per
 * structure set ("ultra_crater/landmark": the world's great structure, "ultra_crater/sites":
 * its small ones): on or off, one try per square of `spacing` chunks, tries at least
 * `separation` chunks apart. The default values are the ones of the sets' JSON
 * (data/generations_core/worldgen/structure_set), which also hold what cannot change
 * here: which structures a set has and their weights (a datapack can override them).
 *
 * Read when the server starts and by /ultrawarp reload; like any worldgen setting, a
 * change applies to the chunks generated from then on, never to the ones already there.
 * Like UltraResourcesConfig, this file needs nothing of the mod (own logger): the
 * checks run it outside the game.
 */
object UltraStructuresConfig {
    class StructureSet(
        var enabled: Boolean = true,
        /** One try per square of this many chunks (a chunk is 16 blocks). */
        var spacing: Int = 32,
        /** Tries at least this many chunks apart (below spacing). */
        var separation: Int = 8,
    )

    class Settings(
        var version: Int = 0,
        /** false: no Ultra Space structure at all in new chunks. */
        var enabled: Boolean = true,
        var sets: LinkedHashMap<String, StructureSet> = LinkedHashMap(),
    )

    private const val VERSION = 1
    private val LOGGER = LogUtils.getLogger()
    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val file get() = Paths.get("config", "generations", "core", "ultra_space", "ultra_structures.json")

    /** The sets the loaded data has, with their default numbers (told by each placement when the data is read). */
    private val declared = ConcurrentHashMap<String, StructureSet>()

    @Volatile
    private var settings: Settings? = null

    fun declare(key: String, spacing: Int, separation: Int) {
        declared[key] = StructureSet(true, spacing, separation)
    }

    private fun current(): Settings = settings ?: synchronized(this) { settings ?: run { load(); settings!! } }

    fun enabled(key: String): Boolean = current().let { it.enabled && it.sets[key]?.enabled != false }

    fun spacing(key: String, default: Int): Int = current().sets[key]?.spacing ?: default

    fun separation(key: String, default: Int): Int = current().sets[key]?.separation ?: default

    @Synchronized
    fun load() {
        val file = file
        val read = try {
            if (Files.exists(file)) gson.fromJson(Files.readString(file), Settings::class.java) ?: Settings(VERSION) else Settings(VERSION)
        } catch (e: Exception) {
            LOGGER.error("Ultra Space structures: could not read {} ({}), default values used", file, e.message)
            Settings(VERSION)
        }
        // The sets of the data, in a stable order, each with the file's values when it has them.
        val sets = LinkedHashMap<String, StructureSet>()
        for (key in declared.keys.sorted()) {
            val set = read.sets[key] ?: declared[key]!!.let { StructureSet(it.enabled, it.spacing, it.separation) }
            set.spacing = set.spacing.coerceIn(1, 4096)
            set.separation = set.separation.coerceIn(0, set.spacing - 1)
            sets[key] = set
        }
        // Entries of sets the data does not have (yet): kept as they are.
        read.sets.filterKeys { it !in sets }.forEach { (key, set) -> sets[key] = set }
        read.sets = sets
        read.version = VERSION
        settings = read
        LOGGER.info(
            "Ultra Space structures: {}",
            if (!read.enabled) "off" else sets.entries.joinToString { (key, set) -> if (set.enabled) "$key every ${set.spacing} chunks" else "$key off" },
        )
        try {
            Files.createDirectories(file.parent)
            Files.writeString(file, gson.toJson(read))
        } catch (e: Exception) {
            LOGGER.error("Ultra Space structures: could not write {}", file, e)
        }
    }
}
