package generations.gg.generations.core.generationscore.common.client.ultraspace.rift

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import generations.gg.generations.core.generationscore.common.GenerationsCore
import java.nio.file.Files
import java.nio.file.Paths

/**
 * How this client draws the rifts (phase 6): config/generations/core/ultra_space/
 * ultra_rifts_client.json. Only looks: where they are and where they lead is the server's.
 * Also the height of the seat on Solgaleo and Lunala (their RareCandy models).
 */
object UltraRiftClientConfig {
    class Settings(
        var version: Int = 0,
        /** Not drawn farther than this (blocks); fades out over the last fifth. */
        var viewDistance: Float = 160f,
        /** Closer than this: the depth layers and the energy arcs. */
        var detailDistance: Float = 48f,
        /** Closer than this: particles and the hum. */
        var particleDistance: Float = 32f,
        var particlesPerSecond: Float = 10f,
        /** The slow wobble of the edge. */
        var distortion: Boolean = true,
        var sounds: Boolean = true,
        /** Brightness of the glowing edge (1 = as made). */
        var glow: Float = 1f,
        /** The part of the opening that must be crossed, outlined (for testing). */
        var showTriggerZone: Boolean = false,
        /**
         * Seat of the rider on these Pokémon (blocks above the model's feet), for
         * Cobblemon's riding: the other RareCandy models keep the common one.
         */
        var seats: LinkedHashMap<String, Float> = linkedMapOf("solgaleo" to 4.24f, "lunala" to 4.69f),
    ) {
        fun validate() {
            viewDistance = viewDistance.coerceIn(16f, 512f)
            detailDistance = detailDistance.coerceIn(0f, viewDistance)
            particleDistance = particleDistance.coerceIn(0f, viewDistance)
            particlesPerSecond = particlesPerSecond.coerceIn(0f, 60f)
            glow = glow.coerceIn(0.1f, 3f)
            val read = seats
            seats = LinkedHashMap(read.mapValues { it.value.coerceIn(0f, 20f) })
        }
    }

    private const val VERSION = 1
    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val file get() = Paths.get("config", "generations", "core", "ultra_space", "ultra_rifts_client.json")

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
        if (settings.seats == null) settings.seats = linkedMapOf("solgaleo" to 4.24f, "lunala" to 4.69f)
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
