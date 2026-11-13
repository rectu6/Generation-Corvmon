package generations.gg.generations.core.generationscore.common.world.ultraspace.spawning

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.world.ultraspace.weather.UltraWeatherEvent
import java.nio.file.Files
import java.nio.file.Paths

/**
 * config/generations/core/ultra_space/ultra_spawns.json (server), phase 5: what the
 * Cobblemon data cannot say. The Pokémon themselves, their biomes, levels, buckets and
 * weights, and the weathers' weights and own species, are Cobblemon data
 * (data/generations_core/spawn_pool_world/ultra_space, spawn_rules/ultra_space, written by
 * tools/ultra_spawns.py), which a datapack can override.
 */
object UltraSpawnsConfig {
    class UltraBeasts(
        var enabled: Boolean = true,
        /**
         * Of the Ultra Beasts Cobblemon picks (bucket ultra-rare, 0.2 % of the picks), the
         * share kept: about 1 natural spawn in 15 000 is an Ultra Beast.
         */
        var keepChance: Float = 1f / 30f,
        /** keepChance is multiplied by this during a dangerous weather (1: no bonus). */
        var dangerousWeatherMultiplier: Float = 1f,
        /** Wild Ultra Beasts alive at once in a world (0: no limit). */
        var maxAlivePerWorld: Int = 1,
        /** Minutes after a natural Ultra Beast before the next one in that world. */
        var cooldownMinutes: Float = 60f,
        /** A natural Ultra Beast of the Ultra worlds is shiny 1 time in this (its own odds; Cobblemon's 8192 for the rest). */
        var naturalShinyOdds: Float = 8192f,
    ) {
        fun validate() {
            keepChance = keepChance.coerceIn(0f, 1f)
            dangerousWeatherMultiplier = dangerousWeatherMultiplier.coerceIn(0f, 100f)
            maxAlivePerWorld = maxAlivePerWorld.coerceIn(0, 100)
            cooldownMinutes = cooldownMinutes.coerceIn(0f, 7 * 24 * 60f)
            naturalShinyOdds = naturalShinyOdds.coerceIn(1f, 1_000_000f)
        }
    }

    class Settings(
        var version: Int = 0,
        /** The weathers change the spawns (the spawn rules and the buckets below). */
        var weatherSpawns: Boolean = true,
        /**
         * During each dangerous weather, the weights of Cobblemon's buckets in its world are
         * multiplied (common 94, uncommon 5, rare 0.5, ultra-rare 0.2): rare encounters truly
         * more frequent. ultra-rare is left alone (the Ultra Beasts have their own setting).
         */
        var dangerousBuckets: LinkedHashMap<String, LinkedHashMap<String, Float>> = defaultBuckets(),
        var ultraBeasts: UltraBeasts = UltraBeasts(),
    ) {
        fun validate() {
            ultraBeasts.validate()
            val read = dangerousBuckets
            dangerousBuckets = LinkedHashMap()
            for (event in UltraWeatherEvent.entries.filter { it.dangerous }) {
                val buckets = read[event.id] ?: defaultBuckets().getValue(event.id)
                buckets.entries.removeIf { it.key.isBlank() }
                buckets.replaceAll { _, factor -> factor.coerceIn(0f, 100f) }
                dangerousBuckets[event.id] = buckets
            }
        }
    }

    fun defaultBuckets(): LinkedHashMap<String, LinkedHashMap<String, Float>> = LinkedHashMap(
        UltraWeatherEvent.entries.filter { it.dangerous }.associate { it.id to linkedMapOf("uncommon" to 1.5f, "rare" to 2.5f) }
    )

    private const val VERSION = 1
    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val file get() = Paths.get("config", "generations", "core", "ultra_space", "ultra_spawns.json")

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
            if (ultraBeasts == null) ultraBeasts = UltraBeasts()
            if (dangerousBuckets == null) dangerousBuckets = defaultBuckets()
        }
        settings.validate()
        settings.version = VERSION
        val beasts = settings.ultraBeasts
        GenerationsCore.LOGGER.info(
            "Ultra Space spawns: weathers {}, Ultra Beasts {} (kept 1 in {}, at most {} per world, {} min apart, shiny 1 in {})",
            if (settings.weatherSpawns) "on" else "off", if (beasts.enabled) "on" else "off",
            if (beasts.keepChance > 0f) Math.round(1f / beasts.keepChance) else 0, beasts.maxAlivePerWorld, beasts.cooldownMinutes, beasts.naturalShinyOdds,
        )
        try {
            Files.createDirectories(file.parent)
            Files.writeString(file, gson.toJson(settings))
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("Ultra Space: could not write {}", file, e)
        }
    }
}
