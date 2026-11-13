package generations.gg.generations.core.generationscore.common.world.ultraspace.encounter

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.UltraWorlds
import java.nio.file.Files
import java.nio.file.Paths

/**
 * config/generations/core/ultra_space/ultra_encounters.json (server), phase 5: the
 * Pokémon that waits for the player after a successful Ultra Warp Ride. Its three
 * chances (an Ultra Beast or not; shiny if ordinary; shiny if an Ultra Beast) are the
 * tables of ultra_warp_ride.json (encounters), by light-years and portal level.
 */
object UltraEncountersConfig {
    class LevelTier(var fromLightYears: Float = 0f, var min: Int = 25, var max: Int = 35)

    class Settings(
        var version: Int = 0,
        var enabled: Boolean = true,
        /** Seconds after the arrival before it appears. */
        var delaySeconds: Float = 3f,
        /** How far from the player it may appear (blocks). */
        var searchRadius: Int = 10,
        /** Seconds between two tries when no spot was found (loading, no room). */
        var retrySeconds: Float = 5f,
        /** Minutes after which a drawn encounter that never found its place is dropped (0: never). */
        var expireMinutes: Float = 0f,
        /** Minutes during which only the traveller can throw a ball at it or battle it (0: none). */
        var priorityMinutes: Float = 3f,
        /** An ordinary encounter: a species of these buckets of the arrival biome's Ultra spawns. */
        var ordinaryBuckets: MutableList<String> = mutableListOf("uncommon", "rare"),
        /** Instead of the spawns, these species for a biome ("ultra_jungle/giant_jungle") or a world ("ultra_jungle"). */
        var species: LinkedHashMap<String, MutableList<String>> = LinkedHashMap(),
        /** Never an encounter: species with one of these labels (the Ultra Beasts have their own table). */
        var excludedLabels: MutableList<String> = mutableListOf("legendary", "mythical", "restricted", "ultra_beast", "paradox"),
        /** The ordinary encounter's level by light-years. */
        var levels: MutableList<LevelTier> = mutableListOf(
            LevelTier(0f, 25, 35), LevelTier(2500f, 30, 42), LevelTier(6000f, 38, 50), LevelTier(8000f, 45, 55),
        ),
        /** The Ultra Beasts of each world, by weight: the destination's own, and a second one a little rarer. */
        var ultraBeasts: LinkedHashMap<String, LinkedHashMap<String, Float>> = defaultBeasts(),
        var ultraBeastMinLevel: Int = 50,
        var ultraBeastMaxLevel: Int = 60,
    ) {
        fun validate() {
            delaySeconds = delaySeconds.coerceIn(0f, 120f)
            searchRadius = searchRadius.coerceIn(3, 32)
            retrySeconds = retrySeconds.coerceIn(1f, 120f)
            expireMinutes = expireMinutes.coerceIn(0f, 7 * 24 * 60f)
            priorityMinutes = priorityMinutes.coerceIn(0f, 60f)
            if (levels.isEmpty()) levels = mutableListOf(LevelTier())
            levels.forEach {
                it.fromLightYears = it.fromLightYears.coerceAtLeast(0f)
                it.min = it.min.coerceIn(1, 100)
                it.max = it.max.coerceIn(it.min, 100)
            }
            levels.sortBy { it.fromLightYears }
            ultraBeastMinLevel = ultraBeastMinLevel.coerceIn(1, 100)
            ultraBeastMaxLevel = ultraBeastMaxLevel.coerceIn(ultraBeastMinLevel, 100)
            val read = ultraBeasts
            ultraBeasts = LinkedHashMap()
            for (id in UltraWorlds.IDS) ultraBeasts[id] = (read[id] ?: defaultBeasts().getValue(id)).also { table ->
                table.entries.removeIf { it.key.isBlank() || it.value <= 0f }
            }
        }

        fun level(lightYears: Double): LevelTier = levels.lastOrNull { it.fromLightYears <= lightYears } ?: levels.first()
    }

    fun defaultBeasts(): LinkedHashMap<String, LinkedHashMap<String, Float>> = linkedMapOf(
        "ultra_jungle" to linkedMapOf("buzzwole" to 1f),
        "ultra_desert" to linkedMapOf("pheromosa" to 1f),
        "ultra_central" to linkedMapOf("xurkitree" to 1f),
        "ultra_crater" to linkedMapOf("celesteela" to 1f, "blacephalon" to 0.85f),
        "ultra_forest" to linkedMapOf("kartana" to 1f),
        "ultra_abyss" to linkedMapOf("nihilego" to 1f, "poipole" to 0.85f),
        "ultra_skyscraper" to linkedMapOf("guzzlord" to 1f, "stakataka" to 0.85f),
    )

    private const val VERSION = 1
    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val file get() = Paths.get("config", "generations", "core", "ultra_space", "ultra_encounters.json")

    var settings = Settings(VERSION).also { it.validate() }; private set

    fun load() {
        val file = file
        settings = try {
            if (Files.exists(file)) gson.fromJson(Files.readString(file), Settings::class.java) ?: Settings(VERSION) else Settings(VERSION)
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("Ultra Space: could not read {} ({}), default values used", file, e.message)
            settings = Settings(VERSION).also { it.validate() }
            return
        }
        @Suppress("SENSELESS_COMPARISON")
        settings.apply {
            if (ordinaryBuckets == null) ordinaryBuckets = mutableListOf("uncommon", "rare")
            if (species == null) species = LinkedHashMap()
            if (excludedLabels == null) excludedLabels = mutableListOf("legendary", "mythical", "restricted", "ultra_beast", "paradox")
            if (levels == null) levels = mutableListOf(LevelTier())
            if (ultraBeasts == null) ultraBeasts = defaultBeasts()
        }
        settings.validate()
        settings.version = VERSION
        try {
            Files.createDirectories(file.parent)
            Files.writeString(file, gson.toJson(settings))
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("Ultra Space: could not write {}", file, e)
        }
    }
}
