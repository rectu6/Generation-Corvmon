package generations.gg.generations.core.generationscore.common.world.ultraspace.weather

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.UltraWorlds
import net.minecraft.util.RandomSource
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Locale

/**
 * config/generations/core/ultra_space/ultra_weather.json (server): the weather cycles of
 * the seven Ultra worlds, their damage and their shelters.
 *
 * A world's cycle: a calm (calmMinutes, from the end of an event to the start of the
 * next), then an event drawn by weight, which lasts durationMinutes, then a calm again.
 * The time between two starts is not set: it is the event's duration plus the calm
 * (printed when the file is read), so that it can never be shorter than the event.
 */
object UltraWeatherConfig {
    class Range(var min: Float = 0f, var max: Float = 0f) {
        fun validate(floor: Float, ceiling: Float) {
            min = min.coerceIn(floor, ceiling)
            max = max.coerceIn(min, ceiling)
        }

        fun average(): Float = (min + max) / 2f

        fun roll(random: RandomSource): Float = if (max <= min) min else min + random.nextFloat() * (max - min)
    }

    class Damage(
        /** Half hearts at each hit. */
        var amount: Float = 1f,
        /** Progressive weathers: the most a hit can take. */
        var max: Float = 4f,
        /** Progressive weathers: added to each hit per minute spent exposed. */
        var rampPerMinute: Float = 0.5f,
        var intervalSeconds: Float = 4f,
    ) {
        fun validate() {
            amount = amount.coerceIn(0f, 1000f)
            max = max.coerceIn(amount, 1000f)
            rampPerMinute = rampPerMinute.coerceIn(0f, 1000f)
            intervalSeconds = intervalSeconds.coerceIn(0.5f, 600f)
        }
    }

    class Event(
        var enabled: Boolean = true,
        /** Chance against the world's other weather (weights, not percents). */
        var weight: Int = 50,
        var durationMinutes: Range = Range(40f, 60f),
        /** Dangerous weathers only. */
        var damage: Damage? = null,
    ) {
        fun validate(dangerous: Boolean, defaults: Damage) {
            weight = weight.coerceIn(0, 10000)
            durationMinutes.validate(1f, 24 * 60f)
            if (dangerous) (damage ?: Damage(defaults.amount, defaults.max, defaults.rampPerMinute, defaults.intervalSeconds).also { damage = it }).validate()
            else damage = null
        }
    }

    class World(
        var enabled: Boolean = true,
        var calmMinutes: Range = Range(5f, 15f),
        var events: LinkedHashMap<String, Event> = LinkedHashMap(),
    )

    class Grace(
        /** Seconds without damage after arriving in the world (journey, login, respawn). */
        var arrivalSeconds: Int = 30,
        /** Seconds without damage after the start of a dangerous weather (it grows meanwhile). */
        var eventStartSeconds: Int = 30,
    )

    class Shelters(
        /** How far the walls are looked for (8 directions). */
        var rayLength: Int = 12,
        /** Directions out of 8 that must meet a wall: building or cave (sandstorm, winds). */
        var buildingWalls: Int = 5,
        /** Directions out of 8 for the refuge from the eruption. */
        var refugeWalls: Int = 4,
        /** Blocks under the surface that count as underground. */
        var undergroundDepth: Int = 8,
        /** Blocks above the head looked at for the storm's roof. */
        var roofScan: Int = 24,
        /** The toxic mist: the most air cells a sealed space may hold. */
        var sealedVolume: Int = 300,
    )

    class Settings(
        var version: Int = 0,
        /** Off: no Ultra weather at all (the Ultra worlds stay calm, still apart from the Overworld's). */
        var enabled: Boolean = true,
        /** Seconds the weathers take to come and to go. */
        var transitionSeconds: Int = 30,
        /** Seconds before a dangerous weather when the players of the world are warned (0: no warning). */
        var warningSeconds: Int = 60,
        /** Ticks between two shelter checks of a player. */
        var checkIntervalTicks: Int = 10,
        /** The electric storm: seconds between two lightning bolts near a player outside (visual only). */
        var lightningSeconds: Range = Range(4f, 12f),
        var grace: Grace = Grace(),
        var shelters: Shelters = Shelters(),
        var worlds: LinkedHashMap<String, World> = defaultWorlds(),
    ) {
        fun validate() {
            transitionSeconds = transitionSeconds.coerceIn(0, 600)
            warningSeconds = warningSeconds.coerceIn(0, 1800)
            checkIntervalTicks = checkIntervalTicks.coerceIn(1, 200)
            lightningSeconds.validate(0.5f, 600f)
            grace.arrivalSeconds = grace.arrivalSeconds.coerceIn(0, 3600)
            grace.eventStartSeconds = grace.eventStartSeconds.coerceIn(0, 3600)
            shelters.rayLength = shelters.rayLength.coerceIn(2, 32)
            shelters.buildingWalls = shelters.buildingWalls.coerceIn(0, 8)
            shelters.refugeWalls = shelters.refugeWalls.coerceIn(0, 8)
            shelters.undergroundDepth = shelters.undergroundDepth.coerceIn(1, 64)
            shelters.roofScan = shelters.roofScan.coerceIn(2, 64)
            shelters.sealedVolume = shelters.sealedVolume.coerceIn(8, 4096)
            // Worlds and weathers missing from the file come back with their default values; unknown ones are dropped.
            val defaults = defaultWorlds()
            val read = worlds
            worlds = LinkedHashMap()
            for ((id, fallback) in defaults) {
                val world = read[id] ?: fallback
                world.calmMinutes.validate(0f, 24 * 60f)
                val events = LinkedHashMap<String, Event>()
                for (event in UltraWeatherEvent.of(id)) {
                    val value = world.events[event.id] ?: fallback.events.getValue(event.id)
                    value.validate(event.dangerous, defaultDamage(event))
                    events[event.id] = value
                }
                world.events = events
                worlds[id] = world
            }
        }
    }

    /** Provisional values: an event lasts 50 minutes on average, the calm 10: one start every hour. */
    fun defaultWorlds(): LinkedHashMap<String, World> = LinkedHashMap(UltraWorlds.IDS.associateWith { id ->
        World(events = LinkedHashMap(UltraWeatherEvent.of(id).associate { event ->
            event.id to Event(weight = if (event.dangerous) 45 else 55, damage = if (event.dangerous) defaultDamage(event) else null)
        }))
    })

    fun defaultDamage(event: UltraWeatherEvent): Damage = when (event) {
        UltraWeatherEvent.EXTREME_HEAT -> Damage(1f, 4f, 0.5f, 4f)
        UltraWeatherEvent.SANDSTORM -> Damage(1f, 3f, 0.5f, 3f)
        UltraWeatherEvent.ELECTRIC_STORM -> Damage(3f, 3f, 0f, 8f)
        UltraWeatherEvent.THERMAL_ERUPTION -> Damage(1f, 4f, 0.5f, 3f)
        UltraWeatherEvent.CUTTING_WINDS -> Damage(1f, 3f, 0.5f, 3f)
        UltraWeatherEvent.TOXIC_MIST -> Damage(2f, 2f, 0f, 5f)
        UltraWeatherEvent.ACID_RAIN -> Damage(1f, 3f, 0.5f, 4f)
        else -> Damage()
    }

    private const val VERSION = 1
    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val file get() = Paths.get("config", "generations", "core", "ultra_space", "ultra_weather.json")

    var settings = Settings(VERSION).also { it.validate() }; private set

    fun world(id: String): World? = settings.worlds[id]

    fun event(event: UltraWeatherEvent): Event? = settings.worlds[event.world]?.events?.get(event.id)

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
            if (grace == null) grace = Grace()
            if (shelters == null) shelters = Shelters()
            if (lightningSeconds == null) lightningSeconds = Range(4f, 12f)
            if (worlds == null) worlds = defaultWorlds()
            worlds.values.forEach { world ->
                if (world.calmMinutes == null) world.calmMinutes = Range(5f, 15f)
                if (world.events == null) world.events = LinkedHashMap()
                world.events.values.forEach { if (it.durationMinutes == null) it.durationMinutes = Range(40f, 60f) }
            }
        }
        settings.validate()
        settings.version = VERSION
        for ((id, world) in settings.worlds) GenerationsCore.LOGGER.info("Ultra Space weather: {}", describe(id, world))
        try {
            Files.createDirectories(file.parent)
            Files.writeString(file, gson.toJson(settings))
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("Ultra Space: could not write {}", file, e)
        }
    }

    /** "ultra_jungle: events 50 min + calm 10 min = 60 min between two starts (tropical_rain 55%, extreme_heat 45%)". */
    fun describe(id: String, world: World): String {
        val events = world.events.entries.filter { it.value.enabled && it.value.weight > 0 }
        if (!settings.enabled || !world.enabled || events.isEmpty()) return "$id: no weather"
        val total = events.sumOf { it.value.weight }.toFloat()
        val duration = events.sumOf { (it.value.durationMinutes.average() * it.value.weight).toDouble() }.toFloat() / total
        val calm = world.calmMinutes.average()
        return String.format(
            Locale.ROOT, "%s: events %.0f min + calm %.0f min = %.0f min between two starts (%s)",
            id, duration, calm, duration + calm,
            events.joinToString { "${it.key} ${Math.round(it.value.weight * 100f / total)}%" },
        )
    }
}
