package generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.gen

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.mojang.logging.LogUtils
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest
import java.nio.file.Files
import java.nio.file.Paths

/**
 * config/generations/core/ultra_space/ultra_resources.json, read by the server (solo's
 * integrated one too): the ores and materials of the seven Ultra Space worlds. Each
 * world has one feature (generations_core:ultra_resources, in its 35 biomes' ore step)
 * which lays the veins this file lists, with Minecraft's own vein makers
 * (minecraft:ore, minecraft:scattered_ore).
 *
 * Read when the server starts and by /ultrawarp reload: a change applies to the chunks
 * generated from then on, never to the ones already there. Nothing regenerates.
 */
object UltraResourcesConfig {
    /** Another height band for a resource in one world. */
    class Band(var minY: Int = -64, var maxY: Int = 64)

    class Resource(
        var enabled: Boolean = true,
        /** The ore in the world's rock. */
        var block: String = "minecraft:stone",
        /** The ore in deepslate and tuff; empty: the same block. */
        var deepBlock: String = "",
        /** Vein size (as Minecraft's ore features count it). */
        var size: Int = 8,
        /** Single blocks spread out (like ancient debris) instead of a vein. */
        var scattered: Boolean = false,
        /** Chance (0..1) that a block of the vein touching air is left out; 1: never seen from a cave. */
        var airExposureDiscard: Float = 0f,
        /** Veins tried per chunk, before the world's and the biome's factors; a fraction is a chance (0.5: one chunk in two). */
        var tries: Float = 1f,
        /** Chance (0..1) that a chunk gets its tries at all. */
        var chance: Float = 1f,
        var minY: Int = -64,
        var maxY: Int = 64,
        /** "uniform", or "triangle" (most in the middle of the band, as Minecraft's trapezoid). */
        var shape: String = "uniform",
        /** Factor per world; a world not listed (or at 0) has none of it. */
        var worlds: LinkedHashMap<String, Float> = LinkedHashMap(),
        /** Another band in some worlds. */
        var altitudes: LinkedHashMap<String, Band> = LinkedHashMap(),
        /** Factor of a biome ("ultra_crater/steam_fields") instead of 1: one per biome, never added up, capped by maxBiomeFactor; 0: none there. */
        var biomes: LinkedHashMap<String, Float> = LinkedHashMap(),
        /** When not empty: in these biomes only. */
        var onlyBiomes: ArrayList<String> = ArrayList(),
        /** What it may replace (blocks, #tags); empty: the world's rock (rock, deepRock). */
        var replace: ArrayList<String> = ArrayList(),
    )

    class Settings(
        var version: Int = 0,
        /** false: no resource at all in the Ultra worlds' new chunks. */
        var enabled: Boolean = true,
        /** No biome factor above this. */
        var maxBiomeFactor: Float = 1.5f,
        /** Each world's rock: what `block` replaces. */
        var rock: LinkedHashMap<String, ArrayList<String>> = defaultRock(),
        /** What `deepBlock` replaces, in every world. */
        var deepRock: ArrayList<String> = arrayListOf("#minecraft:deepslate_ore_replaceables"),
        var resources: LinkedHashMap<String, Resource> = defaults(),
    ) {
        fun validate() {
            maxBiomeFactor = maxBiomeFactor.coerceIn(0f, 10f)
            for ((id, r) in resources) {
                r.size = r.size.coerceIn(1, 64)
                r.airExposureDiscard = r.airExposureDiscard.coerceIn(0f, 1f)
                r.tries = r.tries.coerceIn(0f, 256f)
                r.chance = r.chance.coerceIn(0f, 1f)
                if (r.maxY < r.minY) r.maxY = r.minY
                if (r.shape != "uniform" && r.shape != "triangle") {
                    LOGGER.warn("Ultra Space resources: shape '{}' of '{}' unknown, 'uniform' used", r.shape, id)
                    r.shape = "uniform"
                }
                (r.worlds.keys - WORLDS.toSet()).forEach { LOGGER.warn("Ultra Space resources: '{}' names no world '{}' (the worlds are {})", id, it, WORLDS) }
                r.worlds.replaceAll { _, factor -> factor.coerceIn(0f, 100f) }
                r.biomes.replaceAll { _, factor -> factor.coerceIn(0f, 100f) }
                r.altitudes.values.forEach { if (it.maxY < it.minY) it.maxY = it.minY }
            }
        }
    }

    /** A resource in one world, ready for the feature: blocks resolved, factors applied. */
    class Vein(
        val id: String,
        val ore: OreConfiguration,
        val scattered: Boolean,
        private val tries: Float,
        val chance: Float,
        private val minY: Int,
        private val maxY: Int,
        private val triangle: Boolean,
        private val biomes: Map<String, Float>,
        private val onlyBiomes: Set<String>,
    ) {
        /** Veins to try in a chunk of this biome ("ultra_crater/steam_fields"); a fraction is a chance. */
        fun tries(biome: String): Float {
            if (onlyBiomes.isNotEmpty() && biome !in onlyBiomes) return 0f
            return tries * (biomes[biome] ?: 1f)
        }

        fun y(random: RandomSource): Int {
            val range = maxY - minY
            if (!triangle) return minY + random.nextInt(range + 1)
            val half = range / 2
            return minY + random.nextInt(half + 1) + random.nextInt(range - half + 1)
        }
    }

    private const val VERSION = 1
    // Its own logger and its own list of the worlds (their ids are fixed for ever, see UltraWorlds):
    // this file then needs nothing of the mod, and the checks run it outside the game.
    private val LOGGER = LogUtils.getLogger()
    // Before `settings`: its default values name the worlds.
    private val WORLDS = listOf(JUNGLE, DESERT, CENTRAL, CRATER, FOREST, ABYSS, CITY)
    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val file get() = Paths.get("config", "generations", "core", "ultra_space", "ultra_resources.json")

    var settings = Settings(VERSION); private set

    /** Per world; null until the file was read (the registries must be there to resolve the blocks). */
    @Volatile
    private var veins: Map<String, List<Vein>>? = null

    /** The veins of a world; reads the file if a chunk asks before the server finished starting. */
    fun veins(world: String): List<Vein> {
        val ready = veins ?: synchronized(this) { veins ?: run { load(); veins!! } }
        return ready[world] ?: emptyList()
    }

    @Synchronized
    fun load() {
        val file = file
        val read = try {
            if (Files.exists(file)) gson.fromJson(Files.readString(file), Settings::class.java) ?: Settings(VERSION) else Settings(VERSION)
        } catch (e: Exception) {
            LOGGER.error("Ultra Space resources: could not read {} ({}), default values used", file, e.message)
            Settings(VERSION)
        }
        read.validate()
        read.version = VERSION
        settings = read
        veins = compile(read)
        LOGGER.info(
            "Ultra Space resources: {}",
            if (!read.enabled) "off" else WORLDS.joinToString { "$it ${veins!![it]?.size ?: 0}" },
        )
        // Written back: options added since, values brought into range.
        try {
            Files.createDirectories(file.parent)
            Files.writeString(file, gson.toJson(read))
        } catch (e: Exception) {
            LOGGER.error("Ultra Space resources: could not write {}", file, e)
        }
    }

    private fun compile(settings: Settings): Map<String, List<Vein>> {
        if (!settings.enabled) return emptyMap()
        val out = LinkedHashMap<String, MutableList<Vein>>()
        for ((id, r) in settings.resources) {
            if (!r.enabled) continue
            val block = state(r.block)
            if (block == null) {
                LOGGER.warn("Ultra Space resources: '{}' skipped, no block '{}'", id, r.block)
                continue
            }
            val deep = if (r.deepBlock.isBlank()) block else state(r.deepBlock) ?: block.also {
                LOGGER.warn("Ultra Space resources: '{}': no block '{}', '{}' used in deep rock too", id, r.deepBlock, r.block)
            }
            for ((world, factor) in r.worlds) {
                if (factor <= 0f || world !in WORLDS) continue
                val targets = ArrayList<OreConfiguration.TargetBlockState>()
                if (r.replace.isNotEmpty()) {
                    r.replace.mapNotNull { test(id, it) }.forEach { targets += OreConfiguration.target(it, block) }
                } else {
                    (settings.rock[world] ?: emptyList()).mapNotNull { test(id, it) }.forEach { targets += OreConfiguration.target(it, block) }
                    settings.deepRock.mapNotNull { test(id, it) }.forEach { targets += OreConfiguration.target(it, deep) }
                }
                if (targets.isEmpty()) continue
                val band = r.altitudes[world]
                val prefix = "$world/"
                out.getOrPut(world) { ArrayList() } += Vein(
                    id,
                    OreConfiguration(targets, r.size, r.airExposureDiscard),
                    r.scattered,
                    r.tries * factor,
                    r.chance,
                    band?.minY ?: r.minY,
                    band?.maxY ?: r.maxY,
                    r.shape == "triangle",
                    r.biomes.filterKeys { it.startsWith(prefix) }.mapValues { it.value.coerceAtMost(settings.maxBiomeFactor) },
                    r.onlyBiomes.toSet(),
                )
            }
        }
        return out
    }

    private fun state(id: String): BlockState? =
        ResourceLocation.tryParse(id)?.let { BuiltInRegistries.BLOCK.getOptional(it).orElse(null) }?.defaultBlockState()

    private fun test(resource: String, entry: String): RuleTest? {
        if (entry.startsWith("#")) {
            val tag = ResourceLocation.tryParse(entry.substring(1)) ?: return null
            return TagMatchTest(TagKey.create(Registries.BLOCK, tag))
        }
        val block = ResourceLocation.tryParse(entry)?.let { BuiltInRegistries.BLOCK.getOptional(it).orElse(null) }
        if (block == null) LOGGER.warn("Ultra Space resources: '{}': no block '{}' to replace", resource, entry)
        return block?.let { BlockMatchTest(it) }
    }

    // ------------------------------------------------------------------ default values

    private const val JUNGLE = "ultra_jungle"
    private const val DESERT = "ultra_desert"
    private const val CENTRAL = "ultra_central"
    private const val CRATER = "ultra_crater"
    private const val FOREST = "ultra_forest"
    private const val ABYSS = "ultra_abyss"
    private const val CITY = "ultra_skyscraper"

    /** Common ores: a little under the Overworld everywhere, the Overworld's amount where a world favours one. */
    private const val BASE = 0.7f
    private const val FAVOURED = 1f
    /** One biome's speciality. */
    private const val BONUS = 1.25f

    fun defaultRock(): LinkedHashMap<String, ArrayList<String>> {
        val stone = "#minecraft:stone_ore_replaceables"
        return linkedMapOf(
            JUNGLE to arrayListOf(stone),
            DESERT to arrayListOf(stone, "generations_core:ultra_sandstone"),
            CENTRAL to arrayListOf(stone, "generations_core:charge_stone"),
            CRATER to arrayListOf(stone),
            FOREST to arrayListOf(stone),
            ABYSS to arrayListOf(stone, "generations_core:cave_rock"),
            CITY to arrayListOf(stone),
        )
    }

    private fun everywhere(base: Float, vararg special: Pair<String, Float>): LinkedHashMap<String, Float> {
        val out = LinkedHashMap<String, Float>()
        WORLDS.forEach { out[it] = base }
        special.forEach { (world, factor) -> out[world] = factor }
        out.values.removeIf { it <= 0f }
        return out
    }

    private fun only(vararg worlds: String): LinkedHashMap<String, Float> = LinkedHashMap(worlds.associateWith { FAVOURED })

    private fun bonus(vararg biomes: String): LinkedHashMap<String, Float> = LinkedHashMap(biomes.associateWith { BONUS })

    private fun ore(
        block: String, deep: String, size: Int, discard: Float, tries: Float, shape: String, minY: Int, maxY: Int,
        worlds: LinkedHashMap<String, Float>, biomes: LinkedHashMap<String, Float> = LinkedHashMap(), chance: Float = 1f,
        onlyBiomes: List<String> = emptyList(), scattered: Boolean = false,
    ) = Resource(true, block, deep, size, scattered, discard, tries, chance, minY, maxY, shape, worlds, LinkedHashMap(), biomes, ArrayList(onlyBiomes))

    private fun vanilla(name: String) = "minecraft:${name}_ore" to "minecraft:deepslate_${name}_ore"

    fun defaults(): LinkedHashMap<String, Resource> {
        val out = LinkedHashMap<String, Resource>()

        // Minecraft's ores, with the Overworld's own sizes, counts and heights (1.21.1), times the world's factor.
        val (coal, deepCoal) = vanilla("coal")
        val coalWorlds = { abyss: Float -> everywhere(BASE, JUNGLE to FAVOURED, CITY to FAVOURED, ABYSS to abyss) }
        val coalBiomes = bonus("$JUNGLE/giant_jungle", "$CRATER/crater_plains", "$FOREST/origami_forest", "$CITY/residential")
        out["coal_upper"] = ore(coal, deepCoal, 17, 0f, 30f, "uniform", 136, 320, coalWorlds(0f), coalBiomes)
        out["coal_lower"] = ore(coal, deepCoal, 17, 0.5f, 20f, "triangle", 0, 192, coalWorlds(BASE), coalBiomes)

        val (iron, deepIron) = vanilla("iron")
        val ironWorlds = { abyss: Float -> everywhere(BASE, JUNGLE to FAVOURED, FOREST to FAVOURED, CITY to FAVOURED, ABYSS to abyss) }
        val ironBiomes = bonus("$JUNGLE/mountain_jungle", "$CENTRAL/magnetic_valley", "$FOREST/blade_valley", "$CITY/industrial_zone")
        out["iron_upper"] = ore(iron, deepIron, 9, 0f, 90f, "triangle", 80, 384, ironWorlds(0f), ironBiomes)
        out["iron_middle"] = ore(iron, deepIron, 9, 0f, 10f, "triangle", -24, 56, ironWorlds(BASE), ironBiomes)
        out["iron_small"] = ore(iron, deepIron, 4, 0f, 10f, "uniform", -64, 72, ironWorlds(BASE), ironBiomes)

        val (gold, deepGold) = vanilla("gold")
        val goldWorlds = { everywhere(BASE, DESERT to FAVOURED) }
        val goldBiomes = bonus("$DESERT/white_dunes", "$CRATER/yellow_plateaus")
        out["gold"] = ore(gold, deepGold, 9, 0.5f, 4f, "triangle", -64, 32, goldWorlds(), goldBiomes)
        out["gold_lower"] = ore(gold, deepGold, 9, 0.5f, 0.5f, "uniform", -64, -48, goldWorlds(), goldBiomes)

        val (redstone, deepRedstone) = vanilla("redstone")
        val redstoneWorlds = { everywhere(BASE, CENTRAL to FAVOURED, ABYSS to FAVOURED) }
        val redstoneBiomes = bonus("$CENTRAL/conductive_plains", "$ABYSS/fungal_forest")
        out["redstone"] = ore(redstone, deepRedstone, 8, 0f, 4f, "uniform", -64, 15, redstoneWorlds(), redstoneBiomes)
        out["redstone_lower"] = ore(redstone, deepRedstone, 8, 0f, 8f, "triangle", -96, -32, redstoneWorlds(), redstoneBiomes)

        val (lapis, deepLapis) = vanilla("lapis")
        val lapisWorlds = { everywhere(BASE, ABYSS to FAVOURED) }
        val lapisBiomes = bonus("$ABYSS/rocky_abyss", "$DESERT/oasis", "$FOREST/zen_garden")
        out["lapis"] = ore(lapis, deepLapis, 7, 0f, 2f, "triangle", -32, 32, lapisWorlds(), lapisBiomes)
        out["lapis_buried"] = ore(lapis, deepLapis, 7, 1f, 4f, "uniform", -64, 64, lapisWorlds(), lapisBiomes)

        val (diamond, deepDiamond) = vanilla("diamond")
        val diamondWorlds = { everywhere(BASE, CRATER to FAVOURED) }
        val diamondBiomes = bonus("$CRATER/steam_fields", "$CRATER/giant_craters")
        out["diamond"] = ore(diamond, deepDiamond, 4, 0.5f, 7f, "triangle", -144, 16, diamondWorlds(), diamondBiomes)
        out["diamond_medium"] = ore(diamond, deepDiamond, 8, 0.5f, 2f, "uniform", -64, -4, diamondWorlds(), diamondBiomes)
        out["diamond_large"] = ore(diamond, deepDiamond, 12, 0.7f, 1f, "triangle", -144, 16, diamondWorlds(), diamondBiomes, chance = 1f / 9f)
        out["diamond_buried"] = ore(diamond, deepDiamond, 8, 1f, 4f, "triangle", -144, 16, diamondWorlds(), diamondBiomes)

        // Emeralds: in the mountains only, well under the Overworld's.
        val (emerald, deepEmerald) = vanilla("emerald")
        val mountains = listOf("$JUNGLE/mountain_jungle", "$DESERT/ivory_canyon", "$CENTRAL/electric_mountains", "$CRATER/yellow_plateaus")
        out["emerald"] = ore(emerald, deepEmerald, 3, 0f, 100f, "triangle", -16, 480,
            linkedMapOf(JUNGLE to 0.3f, DESERT to 0.3f, CENTRAL to 0.3f, CRATER to 0.3f), onlyBiomes = mountains)

        // Copper: little, and none in the Ultra Plant (Poké Balls are made of it).
        val (copper, deepCopper) = vanilla("copper")
        out["copper"] = ore(copper, deepCopper, 10, 0f, 16f, "triangle", -16, 112, everywhere(0.25f, CENTRAL to 0f))

        // Generations' gems: measured deposits, in the worlds of their theme.
        fun gem(name: String, worlds: LinkedHashMap<String, Float>, biomes: LinkedHashMap<String, Float>) {
            val block = "generations_core:${name}_ore"
            val deep = "generations_core:deepslate_${name}_ore"
            out[name] = ore(block, deep, 4, 0.5f, 3f, "triangle", -48, 32, worlds, biomes)
            out["${name}_buried"] = ore(block, deep, 4, 1f, 2f, "uniform", -64, 48, LinkedHashMap(worlds), LinkedHashMap(biomes))
        }
        gem("crystal", only(DESERT, CENTRAL, ABYSS), bonus("$DESERT/crystal_desert", "$CENTRAL/crystal_fields", "$ABYSS/crystal_caverns"))
        gem("ruby", only(CRATER, DESERT), bonus("$CRATER/spire_forest", "$DESERT/ivory_canyon"))
        gem("sapphire", only(ABYSS, JUNGLE), bonus("$ABYSS/underground_lakes", "$JUNGLE/swamp_jungle"))
        out["silicon"] = ore("generations_core:silicon_ore", "generations_core:deepslate_silicon_ore", 8, 0.1f, 10f, "triangle", -24, 56,
            only(CENTRAL, CITY), bonus("$CENTRAL/cable_forest", "$CITY/downtown"))
        out["meteorite"] = ore("generations_core:meteorite_ore", "generations_core:deepslate_meteorite_ore", 3, 0f, 1f, "triangle", -64, 0,
            only(CRATER), bonus("$CRATER/giant_craters"), chance = 0.1f)

        // Evolution stones (Cobblemon's ores): rare, in their worlds only. Two tries a chunk, where
        // Cobblemon gives a favoured Overworld biome sixteen: about one ore block per chunk.
        fun stone(name: String, worlds: LinkedHashMap<String, Float>, biomes: LinkedHashMap<String, Float>) {
            out["${name}_stone"] = ore("cobblemon:${name}_stone_ore", "cobblemon:deepslate_${name}_stone_ore", 3, 0f, 2f, "triangle", -32, 96, worlds, biomes)
        }
        stone("leaf", only(JUNGLE, FOREST), bonus("$JUNGLE/glowing_jungle", "$FOREST/misty_forest"))
        stone("water", only(JUNGLE, ABYSS), bonus("$JUNGLE/swamp_jungle", "$ABYSS/underground_lakes"))
        stone("sun", only(DESERT), bonus("$DESERT/glass_desert"))
        stone("thunder", only(CENTRAL), bonus("$CENTRAL/electric_mountains"))
        stone("fire", only(CRATER), bonus("$CRATER/steam_fields"))
        stone("shiny", only(FOREST), bonus("$FOREST/golden_grove"))
        stone("dusk", only(ABYSS, CITY), bonus("$ABYSS/rocky_abyss", "$CITY/collapsed_ruins"))
        stone("moon", only(ABYSS), bonus("$ABYSS/luminous_caves"))
        stone("dawn", only(CITY), bonus("$CITY/urban_park"))
        stone("ice", LinkedHashMap(), LinkedHashMap())

        // Ultrite: as rare as the Nether's ancient debris, deep down, never touching air, in all seven worlds.
        val all = { everywhere(FAVOURED) }
        out["ultrite_large"] = ore("generations_core:ultrite_debris", "", 3, 1f, 0.7f, "triangle", -56, -40, all(), scattered = true)
        out["ultrite_small"] = ore("generations_core:ultrite_debris", "", 2, 1f, 0.6f, "uniform", -60, 40, all(), scattered = true)
        return out
    }
}
