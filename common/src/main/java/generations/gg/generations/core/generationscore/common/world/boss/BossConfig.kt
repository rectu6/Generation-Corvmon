package generations.gg.generations.core.generationscore.common.world.boss

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import generations.gg.generations.core.generationscore.common.GenerationsCore
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Boss settings, one JSON file per subject in config/generations/core/bosses/, as the
 * raids' (RaidConfig): missing files are written with the default values, a file with
 * an error is reported in the log and keeps its defaults (never a crash), and every
 * value is checked and clamped to a safe range. /boss reload reads them again.
 */
object BossConfig {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val folder: Path get() = Paths.get("config", "generations", "core", "bosses")

    var general = BossGeneralConfig().also { it.validate() }; private set
    var rarities = BossRaritiesConfig().also { it.validate() }; private set
    var spawns = BossSpawnsConfig().also { it.validate() }; private set
    var species = BossSpeciesConfig().also { it.validate() }; private set

    fun rarity(rarity: BossRarity): BossRarityConfig = rarities.of(rarity)

    fun load() {
        runCatching { Files.createDirectories(folder) }
        general = read("general.json", BossGeneralConfig::class.java) { BossGeneralConfig() }.also { it.validate() }
        rarities = read("rarities.json", BossRaritiesConfig::class.java) { BossRaritiesConfig() }.also { it.validate() }
        spawns = read("spawns.json", BossSpawnsConfig::class.java) { BossSpawnsConfig() }.also { it.validate() }
        species = read("species.json", BossSpeciesConfig::class.java) { BossSpeciesConfig() }.also { it.validate() }
        GenerationsCore.LOGGER.info(
            "Bosses: {}, spawn chance {}, at most {} per dimension, {} per minute",
            if (general.enabled && spawns.enabled) "natural spawns on" else "natural spawns off",
            spawns.chance, spawns.maxPerDimension, spawns.maxPerMinute,
        )
    }

    /** Reads a file, or writes its defaults. Errors keep the defaults and are logged. */
    private fun <T : Any> read(name: String, type: Class<T>, defaults: () -> T): T {
        val file = folder.resolve(name)
        if (!Files.exists(file)) {
            runCatching { Files.writeString(file, gson.toJson(defaults())) }
                .onFailure { GenerationsCore.LOGGER.error("Bosses: could not write {}", file, it) }
        }
        return try {
            gson.fromJson(Files.readString(file), type) ?: defaults().also { GenerationsCore.LOGGER.error("Bosses: {} is empty, default values used", file) }
        } catch (e: JsonParseException) {
            GenerationsCore.LOGGER.error("Bosses: {} is not valid JSON ({}), default values used", file, e.message)
            defaults()
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("Bosses: could not read {}, default values used", file, e)
            defaults()
        }
    }

    internal fun warn(message: String, vararg args: Any?) = GenerationsCore.LOGGER.warn("Bosses: $message", *args)

    /** "#RRGGBB" or "RRGGBB" to RGB, or the fallback. */
    fun parseColor(text: String?, fallback: Int): Int {
        val hex = text?.trim()?.removePrefix("#") ?: return fallback
        if (hex.length != 6) return fallback
        return hex.toIntOrNull(16) ?: fallback
    }

    /** Species ids as Cobblemon writes them: "charizard" means cobblemon:charizard. */
    fun speciesId(text: String): String {
        val id = text.trim().lowercase()
        return if (':' in id) id else "cobblemon:$id"
    }
}

// ---- general.json ---------------------------------------------------------------

class BossGeneralConfig(
    /** Every boss feature (natural spawns, capture refusal…); false: no new boss, existing ones stay. */
    var enabled: Boolean = true,
    /** Highest height of a boss, in blocks: the size multiplier is lowered for big species (never below 1). */
    var maxHeightBlocks: Float = 12f,
    /** Size of a Titan boss, multiplied with its rarity's (lot D: the Titan variant itself). */
    var titanScaleMultiplier: Float = 1.25f,
    /** Highest level of a boss (lot B: adaptive level, above 100 only in Showdown). */
    var maxLevel: Int = 125,
    /** A boss cannot be caught: the Poké Ball bounces off and is dropped where it hit. */
    var refuseCapture: Boolean = true,
    /** Only one player may fight a boss (Cobblemon already refuses a second battle). */
    var soloBattlesOnly: Boolean = true,
    /** Permission level of the /boss commands when no permission mod decides (2 = operators). */
    var commandPermissionLevel: Int = 2,
) {
    fun validate() {
        if (!maxHeightBlocks.isFinite()) maxHeightBlocks = 12f
        maxHeightBlocks = maxHeightBlocks.coerceIn(1f, 64f)
        if (!titanScaleMultiplier.isFinite()) titanScaleMultiplier = 1.25f
        titanScaleMultiplier = titanScaleMultiplier.coerceIn(1f, 4f)
        maxLevel = maxLevel.coerceIn(1, 125)
        commandPermissionLevel = commandPermissionLevel.coerceIn(0, 4)
    }
}

// ---- rarities.json --------------------------------------------------------------

class BossRarityConfig(
    /** Colour of the boss's name in messages, as "#RRGGBB" (the client's own is in boss_client.json). */
    var color: String = "#FFFFFF",
    /** Weight in the draw of a natural boss's rarity (0: never drawn; /boss spawn still can). */
    var weight: Double = 1.0,
    /** Levels added to the boss's level (lot B). */
    var levelBonus: Int = 0,
    /** Size multiplier (1 = normal size), capped by general.json's maxHeightBlocks. */
    var scale: Float = 1f,
) {
    val rgb: Int get() = BossConfig.parseColor(color, 0xFFFFFF)

    fun validate(rarity: BossRarity) {
        @Suppress("SENSELESS_COMPARISON")
        if (color == null || BossConfig.parseColor(color, -1) == -1) {
            BossConfig.warn("rarities.json: {} has no valid colour (\"#RRGGBB\"), default used", rarity.id)
            color = hex(rarity.defaultColor)
        }
        if (!weight.isFinite() || weight < 0.0) weight = rarity.defaultWeight
        weight = weight.coerceAtMost(1_000_000.0)
        levelBonus = levelBonus.coerceIn(0, 100)
        if (!scale.isFinite()) scale = rarity.defaultScale
        scale = scale.coerceIn(0.5f, 10f)
    }

    companion object {
        fun hex(rgb: Int) = "#%06X".format(rgb and 0xFFFFFF)
        fun defaults(rarity: BossRarity) =
            BossRarityConfig(hex(rarity.defaultColor), rarity.defaultWeight, rarity.defaultLevelBonus, rarity.defaultScale)
    }
}

class BossRaritiesConfig(
    var common: BossRarityConfig = BossRarityConfig.defaults(BossRarity.COMMON),
    var uncommon: BossRarityConfig = BossRarityConfig.defaults(BossRarity.UNCOMMON),
    var rare: BossRarityConfig = BossRarityConfig.defaults(BossRarity.RARE),
    var epic: BossRarityConfig = BossRarityConfig.defaults(BossRarity.EPIC),
    var legendary: BossRarityConfig = BossRarityConfig.defaults(BossRarity.LEGENDARY),
) {
    fun of(rarity: BossRarity): BossRarityConfig = when (rarity) {
        BossRarity.COMMON -> common
        BossRarity.UNCOMMON -> uncommon
        BossRarity.RARE -> rare
        BossRarity.EPIC -> epic
        BossRarity.LEGENDARY -> legendary
    }

    @Suppress("SENSELESS_COMPARISON")
    fun validate() {
        if (common == null) common = BossRarityConfig.defaults(BossRarity.COMMON)
        if (uncommon == null) uncommon = BossRarityConfig.defaults(BossRarity.UNCOMMON)
        if (rare == null) rare = BossRarityConfig.defaults(BossRarity.RARE)
        if (epic == null) epic = BossRarityConfig.defaults(BossRarity.EPIC)
        if (legendary == null) legendary = BossRarityConfig.defaults(BossRarity.LEGENDARY)
        BossRarity.entries.forEach { of(it).validate(it) }
        if (BossRarity.entries.sumOf { of(it).weight } <= 0.0) {
            BossConfig.warn("rarities.json: every weight is 0, default weights used")
            BossRarity.entries.forEach { of(it).weight = it.defaultWeight }
        }
    }
}

// ---- spawns.json ----------------------------------------------------------------

class BossSpawnsConfig(
    /** Natural spawns may become bosses. */
    var enabled: Boolean = true,
    /** Chance that a natural spawn which passed every filter becomes a boss (0.01 = 1 in 100). */
    var chance: Double = 0.01,
    /** Bosses alive at once in one dimension (loaded ones). */
    var maxPerDimension: Int = 6,
    /** Bosses alive at once in one cell of cellSize × cellSize blocks. */
    var maxPerCell: Int = 1,
    var cellSize: Int = 256,
    /** A boss appears only between these distances of the nearest player, in blocks. */
    var minPlayerDistance: Double = 24.0,
    var maxPlayerDistance: Double = 128.0,
    /** Smallest distance between two bosses, in blocks. */
    var minDistanceBetweenBosses: Double = 96.0,
    /** Natural bosses on the whole server per minute, at most (against bursts). */
    var maxPerMinute: Int = 2,
    /** A boss out of battle leaves after this many minutes (0: never). */
    var lifetimeMinutes: Double = 30.0,
    /** Cobblemon's own despawn (far from every player) also applies to bosses. */
    var naturalDespawn: Boolean = true,
    /** Players within this distance are told a boss appeared (0: nobody). */
    var announceRadius: Double = 64.0,
    /** Bosses in the 7 Ultra Space worlds. */
    var allowUltraWorlds: Boolean = false,
    /** Pokémon fished with a Poké Rod may become bosses. */
    var allowFishing: Boolean = false,
    /** Dimensions without bosses, as "minecraft:the_nether". */
    var excludedDimensions: MutableList<String> = mutableListOf(),
) {
    @Suppress("SENSELESS_COMPARISON")
    fun validate() {
        if (!chance.isFinite()) chance = 0.01
        chance = chance.coerceIn(0.0, 1.0)
        maxPerDimension = maxPerDimension.coerceIn(0, 1000)
        maxPerCell = maxPerCell.coerceIn(0, 1000)
        cellSize = cellSize.coerceIn(16, 16384)
        if (!minPlayerDistance.isFinite()) minPlayerDistance = 24.0
        if (!maxPlayerDistance.isFinite()) maxPlayerDistance = 128.0
        minPlayerDistance = minPlayerDistance.coerceIn(0.0, 512.0)
        maxPlayerDistance = maxPlayerDistance.coerceIn(minPlayerDistance, 1024.0)
        if (!minDistanceBetweenBosses.isFinite()) minDistanceBetweenBosses = 96.0
        minDistanceBetweenBosses = minDistanceBetweenBosses.coerceIn(0.0, 4096.0)
        maxPerMinute = maxPerMinute.coerceIn(0, 1200)
        if (!lifetimeMinutes.isFinite()) lifetimeMinutes = 30.0
        lifetimeMinutes = lifetimeMinutes.coerceIn(0.0, 100_000.0)
        if (!announceRadius.isFinite()) announceRadius = 64.0
        announceRadius = announceRadius.coerceIn(0.0, 1024.0)
        if (excludedDimensions == null) excludedDimensions = mutableListOf()
        excludedDimensions = excludedDimensions.filterNotNull().map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toMutableList()
    }
}

// ---- species.json ---------------------------------------------------------------

class BossSpeciesConfig(
    /** Species with one of these labels never become bosses naturally. */
    var excludedLabels: MutableList<String> = mutableListOf("legendary", "mythical", "ultra_beast", "paradox"),
    /** Species that never become bosses naturally, as "charizard" or "cobblemon:charizard". */
    var excludedSpecies: MutableList<String> = mutableListOf(),
    /** When not empty, only these species become bosses naturally. */
    var onlySpecies: MutableList<String> = mutableListOf(),
    /**
     * Legendary bosses are Mega forms only: weight of each Mega in their draw, as
     * "charizard:mega_x" (1 when not listed, 0 = never).
     */
    var megaWeights: MutableMap<String, Double> = BossMegas.DEFAULT_STONES.keys.associateWithTo(LinkedHashMap()) { 1.0 },
) {
    @Transient private var excluded: Set<String> = emptySet()
    @Transient private var only: Set<String> = emptySet()

    fun megaWeight(key: String): Double = megaWeights[key] ?: 1.0

    @Suppress("SENSELESS_COMPARISON")
    fun validate() {
        if (excludedLabels == null) excludedLabels = mutableListOf()
        if (excludedSpecies == null) excludedSpecies = mutableListOf()
        if (onlySpecies == null) onlySpecies = mutableListOf()
        if (megaWeights == null) megaWeights = LinkedHashMap()
        megaWeights = megaWeights.entries.filter { it.key != null && it.value != null }
            .associateTo(LinkedHashMap()) { (key, weight) -> key.trim().lowercase() to (if (weight.isFinite()) weight.coerceIn(0.0, 1_000_000.0) else 1.0) }
        excludedLabels = excludedLabels.filterNotNull().map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toMutableList()
        excluded = excludedSpecies.filterNotNull().filter { it.isNotBlank() }.map(BossConfig::speciesId).toSet()
        only = onlySpecies.filterNotNull().filter { it.isNotBlank() }.map(BossConfig::speciesId).toSet()
    }

    /** May this species (its identifier, as cobblemon:charizard) become a boss naturally? */
    fun allows(speciesId: String): Boolean = speciesId !in excluded && (only.isEmpty() || speciesId in only)
}
