package generations.gg.generations.core.generationscore.common.client.ultraspace.weather

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.world.ultraspace.weather.UltraWeatherEvent
import java.nio.file.Files
import java.nio.file.Paths

/**
 * config/generations/core/ultra_space/ultra_weather_client.json (each player's own): how
 * strong the Ultra weathers look and sound on this computer. What happens (which weather,
 * when, the damage) is the server's (ultra_weather.json). Sounds are Minecraft's ids (or
 * a resource pack's), changeable here.
 */
object UltraWeatherClientConfig {
    class Sounds(
        /** A sound looped while the weather lasts ("" for none). */
        var loop: String = "",
        var loopVolume: Float = 0.5f,
        /** Sounds played now and then around the player. */
        var sounds: List<String> = emptyList(),
        var soundsPerMinute: Float = 6f,
        var soundVolume: Float = 0.6f,
    ) {
        fun validate() {
            loopVolume = loopVolume.coerceIn(0f, 2f)
            soundsPerMinute = soundsPerMinute.coerceIn(0f, 600f)
            soundVolume = soundVolume.coerceIn(0f, 2f)
        }
    }

    class Settings(
        var version: Int = 0,
        /** Multipliers: 0 removes, 1 as designed. */
        var particles: Float = 1f,
        var overlay: Float = 1f,
        var fog: Float = 1f,
        /** The heat's swaying of the view (0: none, for those it makes unwell). */
        var viewSway: Float = 1f,
        var volume: Float = 1f,
        /** The indicator of the dangerous weathers, top left. */
        var indicator: Boolean = true,
        var sounds: LinkedHashMap<String, Sounds> = defaultSounds(),
    ) {
        fun validate() {
            particles = particles.coerceIn(0f, 3f)
            overlay = overlay.coerceIn(0f, 2f)
            fog = fog.coerceIn(0f, 1f)
            viewSway = viewSway.coerceIn(0f, 3f)
            volume = volume.coerceIn(0f, 2f)
            val defaults = defaultSounds()
            val read = sounds
            sounds = LinkedHashMap()
            for ((id, fallback) in defaults) sounds[id] = (read[id] ?: fallback).also { it.validate() }
        }
    }

    fun defaultSounds(): LinkedHashMap<String, Sounds> {
        fun s(loop: String, loopVolume: Float, perMinute: Float, volume: Float, vararg sounds: String) = Sounds(loop, loopVolume, sounds.toList(), perMinute, volume)
        val map = LinkedHashMap<String, Sounds>()
        for (event in UltraWeatherEvent.entries) {
            map[event.id] = when (event) {
                UltraWeatherEvent.TROPICAL_RAIN -> s("", 0f, 3f, 0.4f, "minecraft:entity.parrot.ambient")
                UltraWeatherEvent.EXTREME_HEAT -> s("minecraft:ambient.basalt_deltas.loop", 0.35f, 8f, 0.4f, "minecraft:block.fire.ambient")
                UltraWeatherEvent.INTENSE_SUN -> s("", 0f, 2f, 0.3f, "minecraft:block.amethyst_block.chime")
                UltraWeatherEvent.SANDSTORM -> s("minecraft:item.elytra.flying", 0.55f, 10f, 0.5f, "minecraft:entity.breeze.wind_burst")
                UltraWeatherEvent.ENERGY_SURGE -> s("minecraft:block.beacon.ambient", 0.6f, 6f, 0.4f, "minecraft:block.copper_bulb.turn_on", "minecraft:block.respawn_anchor.charge")
                UltraWeatherEvent.ELECTRIC_STORM -> s("minecraft:block.beacon.ambient", 0.35f, 8f, 0.5f, "minecraft:entity.lightning_bolt.thunder")
                UltraWeatherEvent.ASH_RAIN -> s("minecraft:ambient.basalt_deltas.loop", 0.5f, 4f, 0.5f, "minecraft:ambient.basalt_deltas.additions")
                UltraWeatherEvent.THERMAL_ERUPTION -> s("minecraft:ambient.basalt_deltas.loop", 0.8f, 12f, 0.6f, "minecraft:block.lava.pop", "minecraft:ambient.basalt_deltas.mood", "minecraft:block.lava.extinguish")
                UltraWeatherEvent.DIMENSIONAL_MIST -> s("minecraft:ambient.soul_sand_valley.loop", 0.4f, 4f, 0.4f, "minecraft:ambient.soul_sand_valley.additions")
                UltraWeatherEvent.CUTTING_WINDS -> s("minecraft:item.elytra.flying", 0.8f, 14f, 0.6f, "minecraft:entity.breeze.wind_burst", "minecraft:entity.breeze.whirl")
                UltraWeatherEvent.CRYSTAL_RAIN -> s("minecraft:block.conduit.ambient", 0.3f, 20f, 0.6f, "minecraft:block.amethyst_block.chime", "minecraft:block.amethyst_cluster.hit")
                UltraWeatherEvent.TOXIC_MIST -> s("minecraft:ambient.crimson_forest.loop", 0.45f, 8f, 0.5f, "minecraft:block.bubble_column.upwards_ambient", "minecraft:block.brewing_stand.brew")
                UltraWeatherEvent.DIMENSIONAL_DARKNESS -> s("minecraft:ambient.warped_forest.loop", 0.5f, 4f, 0.5f, "minecraft:ambient.cave", "minecraft:entity.warden.heartbeat")
                UltraWeatherEvent.ACID_RAIN -> s("", 0f, 10f, 0.25f, "minecraft:block.fire.extinguish", "minecraft:block.lava.extinguish")
            }
        }
        return map
    }

    private const val VERSION = 1
    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val file get() = Paths.get("config", "generations", "core", "ultra_space", "ultra_weather_client.json")

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
        if (settings.sounds == null) settings.sounds = defaultSounds()
        settings.sounds.values.forEach {
            @Suppress("SENSELESS_COMPARISON")
            if (it.sounds == null) it.sounds = emptyList()
            @Suppress("SENSELESS_COMPARISON")
            if (it.loop == null) it.loop = ""
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
