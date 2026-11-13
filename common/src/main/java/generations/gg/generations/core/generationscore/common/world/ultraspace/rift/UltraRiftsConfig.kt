package generations.gg.generations.core.generationscore.common.world.ultraspace.rift

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.UltraWorlds
import java.nio.file.Files
import java.nio.file.Paths

/**
 * The rifts between the Ultra worlds and the return through the interaction wheel
 * (phase 6): config/generations/core/ultra_space/ultra_rifts.json, the server's.
 * The safe arrival (search, protection, fallback platform) stays in ultra_worlds.json,
 * the destinations' colours in ultra_warp_ride.json: not repeated here.
 */
object UltraRiftsConfig {
    class Range(var min: Float = 0f, var max: Float = 0f) {
        fun validate(low: Float, high: Float) {
            min = min.coerceIn(low, high)
            max = max.coerceIn(min, high)
        }

        fun pick(random: java.util.Random): Float = if (max <= min) min else min + random.nextFloat() * (max - min)
    }

    class Rifts {
        var enabled: Boolean = true
        /** The worlds where rifts open (ids: ultra_jungle...). */
        var dimensions: MutableList<String> = UltraWorlds.IDS.toMutableList()
        /** Every this many seconds, each world with players may try to open one. */
        var attemptIntervalSeconds: Int = 45
        /** The chance that an attempt is made at all (then it may still find no place). */
        var spawnChance: Float = 0.35f
        /** Places tried per attempt (columns). */
        var triesPerAttempt: Int = 8
        var maxPerDimension: Int = 3
        /** At most this many open rifts within nearPlayerRadius of the player a new one opens near. */
        var maxNearPlayer: Int = 1
        var nearPlayerRadius: Int = 160
        var lifetimeSeconds: Range = Range(300f, 600f)
        var minDistanceBetween: Int = 192
        /** How far from a player a new rift opens (blocks), in chunks already loaded only. */
        var spawnDistance: Range = Range(48f, 112f)
        /** Never this close to a world's arrival points (blocks). */
        var arrivalPointClearance: Int = 96
        /** Under the open sky: the bottom of the opening this high above the ground under it. */
        var heightAboveGround: Range = Range(8f, 24f)
        /** Never lower or higher than this (absolute y of the centre). */
        var minY: Int = -40
        var maxY: Int = 300
        /** In a world under a roof (the Abyss): a hall at least this high (blocks of air). */
        var cavernMinHeight: Int = 14
        /** Size of the opening (blocks) and how much each rift may differ (0.2 = ±20 %). */
        var width: Float = 5f
        var height: Float = 8f
        var sizeVariation: Float = 0.2f
        /** Air kept free around the opening (blocks). */
        var clearanceMargin: Int = 2
        /** The part of the opening that must be crossed (0.7 = its middle 70 %): edges never count. */
        var innerFraction: Float = 0.7f
        /** A move longer than this in one tick is a teleport, not a crossing (blocks). */
        var maxStepPerTick: Float = 16f
        /** Weight of each destination (0 = never); closed or unloaded worlds are never chosen. */
        var destinationWeights: LinkedHashMap<String, Float> = LinkedHashMap(UltraWorlds.IDS.associateWith { 1f })
        /** A rift may lead to its own world. */
        var allowSameDimension: Boolean = false
        /** Gone after its first crossing. */
        var singleUse: Boolean = true
        /** The mounts that can go through (species ids). */
        var mounts: MutableList<String> = mutableListOf("cobblemon:solgaleo", "cobblemon:lunala")
        /** Seconds of the passage (the colour's fade) before the arrival. */
        var transitionSeconds: Float = 1.2f
        /** "coordinates": first at the rift's x/z in the other world, then its arrival points; "points": the arrival points. */
        var arrival: String = "coordinates"

        fun validate() {
            dimensions = dimensions.filter { it in UltraWorlds.IDS }.distinct().toMutableList()
            attemptIntervalSeconds = attemptIntervalSeconds.coerceIn(5, 3600)
            spawnChance = spawnChance.coerceIn(0f, 1f)
            triesPerAttempt = triesPerAttempt.coerceIn(1, 32)
            maxPerDimension = maxPerDimension.coerceIn(0, 64)
            maxNearPlayer = maxNearPlayer.coerceIn(1, 16)
            nearPlayerRadius = nearPlayerRadius.coerceIn(16, 1024)
            lifetimeSeconds.validate(30f, 7200f)
            minDistanceBetween = minDistanceBetween.coerceIn(16, 4096)
            spawnDistance.validate(16f, 160f)
            arrivalPointClearance = arrivalPointClearance.coerceIn(0, 1024)
            heightAboveGround.validate(3f, 64f)
            minY = minY.coerceIn(-60, 310)
            maxY = maxY.coerceIn(minY, 310)
            cavernMinHeight = cavernMinHeight.coerceIn(8, 64)
            width = width.coerceIn(2f, 16f)
            height = height.coerceIn(3f, 20f)
            sizeVariation = sizeVariation.coerceIn(0f, 0.5f)
            clearanceMargin = clearanceMargin.coerceIn(0, 6)
            innerFraction = innerFraction.coerceIn(0.3f, 0.95f)
            maxStepPerTick = maxStepPerTick.coerceIn(2f, 64f)
            val weights = destinationWeights
            destinationWeights = LinkedHashMap(UltraWorlds.IDS.associateWith { (weights[it] ?: 1f).coerceIn(0f, 1000f) })
            mounts = mounts.distinct().toMutableList()
            transitionSeconds = transitionSeconds.coerceIn(0f, 10f)
            if (arrival != "coordinates" && arrival != "points") arrival = "coordinates"
        }
    }

    class WheelReturn {
        var enabled: Boolean = true
        /** The Pokémon whose wheel offers the return (species ids). */
        var species: MutableList<String> = mutableListOf("cobblemon:solgaleo", "cobblemon:lunala")
        /** The Pokémon this close to the player (blocks). */
        var maxDistance: Float = 8f
        /** Refused during a battle of the player or of the Pokémon. */
        var refuseInBattle: Boolean = true
        var transitionSeconds: Float = 1.5f
        /** Seconds between two requests of a player. */
        var cooldownSeconds: Int = 10
        /** The departure point was in another normal dimension (the Nether...): the Overworld's spawn instead. */
        var overworldOnly: Boolean = false

        fun validate() {
            species = species.distinct().toMutableList()
            maxDistance = maxDistance.coerceIn(2f, 64f)
            transitionSeconds = transitionSeconds.coerceIn(0f, 10f)
            cooldownSeconds = cooldownSeconds.coerceIn(0, 600)
        }
    }

    class Test {
        /** Every rift opened, refused place, crossing and closing in the log. */
        var logRifts: Boolean = false
    }

    class Settings(
        var version: Int = 0,
        var rifts: Rifts = Rifts(),
        var wheelReturn: WheelReturn = WheelReturn(),
        var test: Test = Test(),
    ) {
        fun validate() {
            rifts.validate()
            wheelReturn.validate()
        }
    }

    private const val VERSION = 1
    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val file get() = Paths.get("config", "generations", "core", "ultra_space", "ultra_rifts.json")

    var settings = Settings(VERSION).also { it.validate() }; private set

    val rifts: Rifts get() = settings.rifts
    val wheelReturn: WheelReturn get() = settings.wheelReturn

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
        val rifts = settings.rifts
        GenerationsCore.LOGGER.info(
            "Ultra Space: rifts {} (at most {} per world, one try every {} s at {} %, {}-{} s each), wheel return {}",
            if (rifts.enabled) "on" else "off", rifts.maxPerDimension, rifts.attemptIntervalSeconds, (rifts.spawnChance * 100).toInt(),
            rifts.lifetimeSeconds.min.toInt(), rifts.lifetimeSeconds.max.toInt(), if (settings.wheelReturn.enabled) "on" else "off",
        )
        try {
            Files.createDirectories(file.parent)
            Files.writeString(file, gson.toJson(settings))
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("Ultra Space: could not write {}", file, e)
        }
    }
}
