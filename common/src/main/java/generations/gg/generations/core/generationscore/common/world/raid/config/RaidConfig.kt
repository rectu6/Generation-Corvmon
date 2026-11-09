package generations.gg.generations.core.generationscore.common.world.raid.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import generations.gg.generations.core.generationscore.common.GenerationsCore
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Raid settings, one JSON file per subject in config/generations/core/raids/.
 * Missing files are written with the default values; a file with an error is
 * reported in the log and keeps its defaults (no crash). Every value is checked
 * and clamped to a safe range. See docs/wiki/configuration-raids.md.
 */
object RaidConfig {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val folder: Path get() = Paths.get("config", "generations", "core", "raids")

    var dens = DensConfig(); private set
    var tiers = TiersConfig(); private set
    var battle = BattleConfig(); private set
    var capture = CaptureConfig(); private set
    var pokemon = RaidPokemonTables(); private set
    var rewards = RaidRewards(); private set

    fun tier(stars: Int): TierConfig = tiers.tiers.getOrNull((stars - 1).coerceIn(0, 5)) ?: TiersConfig.defaults()[(stars - 1).coerceIn(0, 5)]

    /** Settings that need no registry: at mod start, before the world generates. */
    fun loadSettings() {
        runCatching { Files.createDirectories(folder) }
        dens = read("dens.json", DensConfig::class.java) { DensConfig() }.also { it.validate() }
        tiers = read("tiers.json", TiersConfig::class.java) { migratedTiers() }.also { it.validate() }
        battle = read("battle.json", BattleConfig::class.java) { BattleConfig() }.also { it.validate() }
        capture = read("capture.json", CaptureConfig::class.java) { migratedCapture() }.also { it.validate() }
    }

    /** Every file again, the tables checked against the species and items (server start). */
    fun load() {
        loadSettings()
        pokemon = read("pokemon.json", RaidPokemonTables::class.java, defaultResource = "pokemon.json") { RaidPokemonTables() }.also { it.validate() }
        rewards = read("rewards.json", RaidRewards::class.java, defaultResource = "rewards.json") { RaidRewards() }.also { it.validate() }
        GenerationsCore.LOGGER.info(
            "Raids: {} biome entries, {} reward entries, {} dens per {} chunks",
            pokemon.tables.size + pokemon.fallback.size + pokemon.legendaries.size,
            rewards.starRewards.size + rewards.typeRewards.size + rewards.guaranteed.size,
            1, dens.denSpawnRarity,
        )
    }

    /**
     * Reads a file, or writes its defaults (from a resource of the mod when given,
     * else from the code). Errors keep the defaults and are logged.
     */
    private fun <T : Any> read(name: String, type: Class<T>, defaultResource: String? = null, defaults: () -> T): T {
        val file = folder.resolve(name)
        if (!Files.exists(file)) {
            val text = defaultResource?.let { resource(it) } ?: gson.toJson(defaults())
            runCatching { Files.writeString(file, text) }
                .onFailure { GenerationsCore.LOGGER.error("Raids: could not write {}", file, it) }
        }
        return try {
            gson.fromJson(Files.readString(file), type) ?: defaults().also { GenerationsCore.LOGGER.error("Raids: {} is empty, default values used", file) }
        } catch (e: JsonParseException) {
            GenerationsCore.LOGGER.error("Raids: {} is not valid JSON ({}), default values used", file, e.message)
            defaultResource?.let { resource(it) }?.let { runCatching { gson.fromJson(it, type) }.getOrNull() } ?: defaults()
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("Raids: could not read {}, default values used", file, e)
            defaults()
        }
    }

    private fun resource(name: String): String? =
        RaidConfig::class.java.getResourceAsStream("/generations_core/raids/$name")?.use { it.readBytes().decodeToString() }

    /** First creation: the HP multipliers come from main.json's former raid settings. */
    private fun migratedTiers(): TiersConfig {
        val old = GenerationsCore.CONFIG.raids.bossHealth
        return TiersConfig().also { config -> config.tiers.forEachIndexed { i, tier -> old.getOrNull(i)?.let { tier.hpMultiplier = it } } }
    }

    private fun migratedCapture(): CaptureConfig {
        val old = GenerationsCore.CONFIG.raids
        return CaptureConfig().also {
            it.baseChance = mutableListOf(1.0, 1.0, 1.0, 1.0, old.catchChance5Stars, old.catchChance6Stars)
        }
    }

    internal fun warn(message: String, vararg args: Any?) = GenerationsCore.LOGGER.warn("Raids: $message", *args)
}

// ---- dens.json ----------------------------------------------------------------

class DensConfig(
    /** Average number of chunks per den (48: about one den per 48 chunks). */
    var denSpawnRarity: Int = 48,
    /** Minimum distance between two dens, in blocks. */
    var minDistanceBetweenDens: Int = 96,
    /** Dens also on the surface of oceans, rivers and lakes. */
    var allowWaterDens: Boolean = true,
    /** Chance, every Minecraft morning, that an inactive den gets a new boss. */
    var denRespawnChance: Double = 0.75,
    /** Time of the day of the respawn attempt, in ticks (0 = sunrise, 6000 = noon). */
    var denRespawnTime: Long = 0,
    /** Weights of the tiers 1 to 6 stars. */
    var starWeights: MutableList<Double> = mutableListOf(42.0, 27.0, 18.0, 9.0, 3.5, 0.5),
    /** Multiplier of the 6-star (legendary) weight, set apart from the others. */
    var legendaryWeightMultiplier: Double = 1.0,
    /** A broken den drops its block (false: nothing, against duplication). */
    var denDropsItem: Boolean = false,
    /** Fleeing ends the raid like a defeat (the boss leaves, the den becomes inactive). */
    var fleeEndsRaid: Boolean = true,
    /** Tiers (lowest, highest) of the raid a Wishing Chip summons in an inactive den. */
    var wishingChipStars: MutableList<Int> = mutableListOf(1, 2),
    /** Tiers (lowest, highest) of the raid a Wishing Piece summons in an inactive den. */
    var wishingPieceStars: MutableList<Int> = mutableListOf(3, 4),
    /** Tiers (lowest, highest) of the raid a Wishing Star summons in an inactive den. */
    var wishingStarStars: MutableList<Int> = mutableListOf(5, 5),
    /** Chance that a Wishing Star summons a 6-star legendary raid instead. */
    var wishingStarLegendaryChance: Double = 0.01,
) {
    fun validate() {
        denSpawnRarity = denSpawnRarity.coerceIn(1, 100000)
        minDistanceBetweenDens = minDistanceBetweenDens.coerceIn(16, 100000)
        denRespawnChance = denRespawnChance.coerceIn(0.0, 1.0)
        denRespawnTime = Math.floorMod(denRespawnTime, 24000L)
        if (starWeights.size != 6 || starWeights.any { it < 0 } || starWeights.sum() <= 0) {
            RaidConfig.warn("dens.json starWeights needs 6 positive values, defaults used")
            starWeights = DensConfig().starWeights
        }
        legendaryWeightMultiplier = legendaryWeightMultiplier.coerceAtLeast(0.0)
        wishingChipStars = tiers(wishingChipStars, "wishingChipStars", DensConfig().wishingChipStars)
        wishingPieceStars = tiers(wishingPieceStars, "wishingPieceStars", DensConfig().wishingPieceStars)
        wishingStarStars = tiers(wishingStarStars, "wishingStarStars", DensConfig().wishingStarStars)
        wishingStarLegendaryChance = wishingStarLegendaryChance.coerceIn(0.0, 1.0)
    }

    /** Lowest and highest tier, each from 1 to 6, in order. */
    private fun tiers(value: MutableList<Int>?, key: String, default: MutableList<Int>): MutableList<Int> {
        if (value == null || value.size != 2) {
            RaidConfig.warn("dens.json $key needs two tiers [lowest, highest], defaults used")
            return default
        }
        val low = value[0].coerceIn(1, 6)
        val high = value[1].coerceIn(1, 6)
        return mutableListOf(minOf(low, high), maxOf(low, high))
    }
}

// ---- tiers.json ---------------------------------------------------------------

class ShockwaveTier(
    var enabled: Boolean = false,
    /** Chance per turn, once the other conditions are met. */
    var chance: Double = 0.0,
    var maxUses: Int = 0,
    /** Turns between two shockwaves. */
    var cooldownTurns: Int = 3,
    /** Only below this share of the boss's HP (1.0: any time). */
    var belowHealth: Double = 1.0,
)

class TierConfig(
    var stars: Int = 1,
    var minLevel: Int = 15,
    var maxLevel: Int = 20,
    var hpMultiplier: Float = 3f,
    /** IVs set to 31, on different random stats (0 to 6). */
    var perfectIvs: Int = 1,
    /** Shiny chance: 1 in shinyRate. */
    var shinyRate: Int = 4096,
    /** Segments of the Dynamax shield (0: no shield). */
    var shieldSegments: Int = 0,
    /** Most attacks of the boss in a turn. */
    var maxAttacks: Int = 1,
    /** Chance of each attack beyond the first. */
    var extraAttackChance: Double = 0.0,
    var shockwave: ShockwaveTier = ShockwaveTier(),
    /** Skill of the boss's AI, 0 to 5. */
    var aiSkill: Int = 1,
    /** Level of the NPC allies' Pokémon, from the tier's top level. */
    var npcLevelOffset: Int = 0,
) {
    fun validate(index: Int) {
        stars = index + 1
        minLevel = minLevel.coerceIn(1, 100)
        maxLevel = maxLevel.coerceIn(minLevel, 100)
        hpMultiplier = hpMultiplier.coerceIn(0.1f, 100f)
        perfectIvs = perfectIvs.coerceIn(0, 6)
        shinyRate = shinyRate.coerceAtLeast(1)
        shieldSegments = shieldSegments.coerceIn(0, 20)
        maxAttacks = maxAttacks.coerceIn(1, 5)
        extraAttackChance = extraAttackChance.coerceIn(0.0, 1.0)
        shockwave.chance = shockwave.chance.coerceIn(0.0, 1.0)
        shockwave.maxUses = shockwave.maxUses.coerceIn(0, 20)
        shockwave.cooldownTurns = shockwave.cooldownTurns.coerceIn(0, 20)
        shockwave.belowHealth = shockwave.belowHealth.coerceIn(0.0, 1.0)
        aiSkill = aiSkill.coerceIn(0, 5)
        npcLevelOffset = npcLevelOffset.coerceIn(-50, 50)
    }
}

class TiersConfig(var tiers: MutableList<TierConfig> = defaults()) {
    fun validate() {
        if (tiers.size != 6) {
            RaidConfig.warn("tiers.json needs 6 tiers, defaults used")
            tiers = defaults()
        }
        tiers.forEachIndexed { i, tier -> tier.validate(i) }
    }

    companion object {
        fun defaults() = mutableListOf(
            TierConfig(1, 15, 20, 3f, 1, 4096, 0, 1, 0.0, ShockwaveTier(), 1, 0),
            TierConfig(2, 25, 30, 4f, 2, 4096, 0, 1, 0.0, ShockwaveTier(), 2, 0),
            TierConfig(3, 35, 40, 5f, 3, 2048, 2, 2, 0.25, ShockwaveTier(true, 0.15, 1, 4, 0.75), 3, 0),
            TierConfig(4, 45, 50, 6f, 4, 1024, 3, 2, 0.35, ShockwaveTier(true, 0.2, 2, 3, 0.9), 3, 0),
            TierConfig(5, 55, 65, 7f, 5, 1024, 4, 3, 0.3, ShockwaveTier(true, 0.25, 2, 3, 1.0), 4, 0),
            TierConfig(6, 70, 75, 8f, 5, 512, 5, 3, 0.35, ShockwaveTier(true, 0.3, 3, 2, 1.0), 5, 0),
        )
    }
}

// ---- battle.json --------------------------------------------------------------

class ShieldConfig(
    /** Shares of the boss's HP at which a shield goes up, highest first (one phase each). */
    var thresholds: MutableList<Double> = mutableListOf(0.5),
    /** Damage taken by the boss is reduced by this share while the shield holds. */
    var damageReduction: Double = 0.75,
    /** Segments broken by a hit; Max and G-Max moves break maxMoveSegments. */
    var segmentsPerHit: Int = 1,
    var maxMoveSegments: Int = 2,
    /** Multi-hit moves: true breaks a segment per hit, false one per move. */
    var multiHitBreaksPerHit: Boolean = false,
    /** Status moves aimed at the boss fail while the shield holds. */
    var blockStatusMoves: Boolean = true,
    /** Defence and Special Defence stages the boss loses when its shield breaks. */
    var breakDefenseDrop: Int = 0,
)

class ShockwaveEffects(
    var clearBoosts: Boolean = true,
    /** Suppresses the participants' abilities (as Gastro Acid) for abilityTurns turns. */
    var suppressAbilities: Boolean = true,
    var abilityTurns: Int = 2,
    /** The boss loses its status (paralysis, burn, poison, sleep, freeze). */
    var cureBossStatus: Boolean = true,
)

class CheerConfig(
    /** Weights of the effects of a cheer. */
    var boostWeight: Double = 40.0,
    var cureWeight: Double = 25.0,
    var healWeight: Double = 25.0,
    var nothingWeight: Double = 10.0,
    /** Stages added to one random stat of each ally. */
    var boostStages: Int = 1,
    /** Share of max HP restored. */
    var healShare: Double = 0.2,
    /** NPC allies cheer on their own when they are down. */
    var npcCheer: Boolean = true,
    /** Seconds a player has to cheer before the turn goes on. */
    var cheerSeconds: Int = 8,
)

class BattleConfig(
    var maxTurns: Int = 10,
    var maxFaints: Int = 4,
    /** Seconds the lobby waits for the players to be ready. */
    var lobbySeconds: Int = 60,
    /** Seconds a player has to choose in multiplayer before a move is chosen for them. */
    var turnSeconds: Int = 60,
    var shield: ShieldConfig = ShieldConfig(),
    var shockwave: ShockwaveEffects = ShockwaveEffects(),
    var cheers: CheerConfig = CheerConfig(),
) {
    fun validate() {
        maxTurns = maxTurns.coerceIn(1, 100)
        maxFaints = maxFaints.coerceIn(1, 20)
        lobbySeconds = lobbySeconds.coerceIn(5, 600)
        turnSeconds = turnSeconds.coerceIn(10, 600)
        shield.thresholds = shield.thresholds.filter { it > 0.0 && it < 1.0 }.sortedDescending().toMutableList()
        shield.damageReduction = shield.damageReduction.coerceIn(0.0, 1.0)
        shield.segmentsPerHit = shield.segmentsPerHit.coerceIn(0, 10)
        shield.maxMoveSegments = shield.maxMoveSegments.coerceIn(0, 10)
        shield.breakDefenseDrop = shield.breakDefenseDrop.coerceIn(0, 6)
        shockwave.abilityTurns = shockwave.abilityTurns.coerceIn(0, 20)
        with(cheers) {
            listOf(boostWeight, cureWeight, healWeight, nothingWeight).let {
                if (it.any { w -> w < 0 } || it.sum() <= 0) {
                    RaidConfig.warn("battle.json cheer weights must be positive, defaults used")
                    boostWeight = 40.0; cureWeight = 25.0; healWeight = 25.0; nothingWeight = 10.0
                }
            }
            boostStages = boostStages.coerceIn(1, 6)
            healShare = healShare.coerceIn(0.0, 1.0)
            cheerSeconds = cheerSeconds.coerceIn(0, 60)
        }
    }
}

// ---- capture.json -------------------------------------------------------------

class CaptureConfig(
    /** Base catch chance for 1 to 6 stars. */
    var baseChance: MutableList<Double> = mutableListOf(1.0, 1.0, 1.0, 1.0, 0.7, 0.35),
    /** Multipliers of the base chance by Poké Ball (item id); others use defaultBallMultiplier. */
    var ballMultipliers: MutableMap<String, Double> = mutableMapOf(
        "cobblemon:great_ball" to 1.2, "cobblemon:ultra_ball" to 1.4, "cobblemon:dusk_ball" to 1.4,
        "cobblemon:quick_ball" to 1.2, "cobblemon:timer_ball" to 1.4, "cobblemon:beast_ball" to 0.5,
    ),
    var defaultBallMultiplier: Double = 1.0,
    /** A Master Ball always catches the boss. */
    var masterBallAlwaysCatches: Boolean = true,
    /** Multiplier for a shiny boss and for a legendary one. */
    var shinyMultiplier: Double = 1.0,
    var legendaryMultiplier: Double = 1.0,
    /** Poké Balls that cannot be used, for 1 to 6 stars (item ids). */
    var forbiddenBalls: MutableList<MutableList<String>> = mutableListOf(mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf()),
    /** Players whose Pokémon was knocked out at the end may still try. */
    var faintedPlayersEligible: Boolean = true,
) {
    fun validate() {
        if (baseChance.size != 6) {
            RaidConfig.warn("capture.json baseChance needs 6 values, defaults used")
            baseChance = CaptureConfig().baseChance
        }
        baseChance = baseChance.map { it.coerceIn(0.0, 1.0) }.toMutableList()
        ballMultipliers = ballMultipliers.mapValues { it.value.coerceAtLeast(0.0) }.toMutableMap()
        defaultBallMultiplier = defaultBallMultiplier.coerceAtLeast(0.0)
        while (forbiddenBalls.size < 6) forbiddenBalls.add(mutableListOf())
    }

    fun forbidden(stars: Int, ball: String) = forbiddenBalls.getOrNull(stars - 1)?.contains(ball) == true
}
