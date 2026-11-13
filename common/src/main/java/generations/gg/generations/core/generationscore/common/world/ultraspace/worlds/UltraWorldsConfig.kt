package generations.gg.generations.core.generationscore.common.world.ultraspace.worlds

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import generations.gg.generations.core.generationscore.common.GenerationsCore
import java.nio.file.Files
import java.nio.file.Paths

/**
 * config/generations/core/ultra_space/ultra_worlds.json, read by the server (solo's
 * integrated one too): which Ultra Space worlds are open, how arrivals are made safe,
 * the return, the test options. The dimension ids are not here: they are fixed
 * (UltraWorlds); a destination is led to a world by portals.destinations[].dimension
 * in ultra_warp_ride.json.
 */
object UltraWorldsConfig {
    class World(var enabled: Boolean = true)

    /** Where and how a player arriving in a world is put down. */
    class Arrival(
        /** Arrival points per world, and how far from the world's middle they spread (blocks). */
        var points: Int = 8,
        var spread: Int = 1200,
        var minRadius: Int = 64,
        /** How far from its point a player's own spot may be, so that not all arrive on one block. */
        var scatter: Int = 6,
        /** How far around a point's ideal place the generator is asked for land (blocks, no chunk loaded). */
        var landSearch: Int = 256,
        /** How far around a point a safe spot is looked for, once its chunks are loaded. */
        var searchRadius: Int = 24,
        /** Highest drop next to the spot (blocks): more is a precipice. */
        var maxDrop: Int = 3,
        /** How far around the spot the drop is checked. */
        var edgeRadius: Int = 2,
        /** No lava, fire, magma, cactus... this close. */
        var hazardRadius: Int = 2,
        /** No player build (block entity, blocks of #generations_core:ultra_arrival_built) this close; 0 = not checked. */
        var buildRadius: Int = 8,
        /** Under the open sky only (never inside a building or a cave). */
        var requireSky: Boolean = true,
        /** No other player this close to the spot. */
        var playerRadius: Int = 4,
        /** Seconds to load a place's chunks before trying another. */
        var loadTimeoutSeconds: Int = 20,
        /** Minutes before a point found unsafe is tried again. */
        var retryMinutes: Int = 10,
        /** New places tried when every point failed. */
        var freshTries: Int = 3,
        /** No safe spot anywhere: "platform" (a small one, only where there is air or fluid) or "return" (the player stays). */
        var fallback: String = "platform",
        var platformBlock: String = "minecraft:stone",
    ) {
        fun validate() {
            points = points.coerceIn(1, 64)
            spread = spread.coerceIn(0, 100_000)
            minRadius = minRadius.coerceIn(0, spread)
            scatter = scatter.coerceIn(0, 32)
            landSearch = landSearch.coerceIn(0, 4096)
            searchRadius = searchRadius.coerceIn(0, 64)
            maxDrop = maxDrop.coerceIn(1, 64)
            edgeRadius = edgeRadius.coerceIn(0, 8)
            hazardRadius = hazardRadius.coerceIn(0, 8)
            buildRadius = buildRadius.coerceIn(0, 16)
            playerRadius = playerRadius.coerceIn(0, 32)
            loadTimeoutSeconds = loadTimeoutSeconds.coerceIn(2, 300)
            retryMinutes = retryMinutes.coerceIn(0, 10_000)
            freshTries = freshTries.coerceIn(0, 16)
            if (fallback != "platform" && fallback != "return") {
                GenerationsCore.LOGGER.warn("Ultra Space: arrival fallback '{}' unknown, 'platform' used", fallback)
                fallback = "platform"
            }
        }
    }

    /** Right after arriving (or coming back): no damage, no fall. */
    class Protection(var seconds: Float = 5f) {
        fun validate() {
            seconds = seconds.coerceIn(0f, 120f)
        }
    }

    /** The provisional return (/ultrawarp test return) to where the player was before leaving. */
    class ReturnHome(var searchRadius: Int = 8) {
        fun validate() {
            searchRadius = searchRadius.coerceIn(0, 64)
        }
    }

    class Test(
        /** Every arrival search in the log (points tried, why refused). */
        var logSearch: Boolean = true,
    )

    class Settings(
        var version: Int = 0,
        var worlds: LinkedHashMap<String, World> = defaultWorlds(),
        /** No portal leads to a closed world (the test command can still place one). */
        var hidePortalsOfDisabledWorlds: Boolean = true,
        var arrival: Arrival = Arrival(),
        var protection: Protection = Protection(),
        var returnHome: ReturnHome = ReturnHome(),
        var test: Test = Test(),
    ) {
        fun validate() {
            // The seven worlds, in their order, whatever the file has; unknown ids dropped.
            val read = worlds
            (read.keys - UltraWorlds.IDS.toSet()).forEach { GenerationsCore.LOGGER.warn("Ultra Space: no world '{}' (the worlds are {})", it, UltraWorlds.IDS) }
            worlds = LinkedHashMap(UltraWorlds.IDS.associateWith { read[it] ?: World() })
            arrival.validate()
            protection.validate()
            returnHome.validate()
        }
    }

    fun defaultWorlds(): LinkedHashMap<String, World> = LinkedHashMap(UltraWorlds.IDS.associateWith { World() })

    private const val VERSION = 1
    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val file get() = Paths.get("config", "generations", "core", "ultra_space", "ultra_worlds.json")

    var settings = Settings(VERSION); private set

    fun load() {
        val file = file
        settings = try {
            if (Files.exists(file)) gson.fromJson(Files.readString(file), Settings::class.java) ?: Settings(VERSION) else Settings(VERSION)
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("Ultra Space: could not read {} ({}), default values used", file, e.message)
            settings = Settings(VERSION).also { it.validate() }
            return
        }.also { it.validate() }
        settings.version = VERSION
        GenerationsCore.LOGGER.info(
            "Ultra Space: worlds {}",
            settings.worlds.entries.joinToString { (id, world) -> "$id ${if (world.enabled) "open" else "closed"}" },
        )
        // Written back: options added since, values brought into range.
        try {
            Files.createDirectories(file.parent)
            Files.writeString(file, gson.toJson(settings))
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("Ultra Space: could not write {}", file, e)
        }
    }
}
