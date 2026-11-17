package generations.gg.generations.core.generationscore.common.world.eon

import generations.gg.generations.core.generationscore.common.config.JsonConfigs
import generations.gg.generations.core.generationscore.common.world.mew.MewConfig

/**
 * config/generations/core/eon/eon.json: the Eon manifestations that give the Enigma Fragments
 * (EON-PLAN.md §§ 3 to 5), read at every server start and by /generations eon reload.
 *
 * Only the time of real exploration counts (an active player who moves, in an allowed dimension
 * and biome, away from the places that gave them a fragment lately): a draw after
 * firstCheckMinutes of it, then every checkIntervalMinutes, chancePercent each (33 %: about
 * 11 h of exploration for the 16 fragments of an Enigma Stone, tools/eon_odds_sim.py). A draw
 * also needs minPresenceMinutes of it in a row, minIntervalBlocks walked and minIntervalCells
 * cells of 64 blocks not seen in the previous interval (no circles). The chance is multiplied
 * by repeatBiomeFactor in a biome that gave one of the player's last biomeMemory fragments.
 * A region (a square of regionSize blocks) that gave a fragment waits regionCooldownHours (real
 * time), and no draw happens within minRewardDistance blocks of the last rewardMemory rewards
 * of that time. No guarantee. At most maxConcurrent manifestations on the server (full: the
 * draw waits, it is not lost).
 *
 * legacyBiomeFragments: Generations' original fragment for each new biome (off, D2).
 */
class EonConfig(
    var enabled: Boolean = true,
    var legacyBiomeFragments: Boolean = false,
    var chancePercent: Double = 33.0,
    var firstCheckMinutes: Double = 8.0,
    var checkIntervalMinutes: Double = 8.0,
    var minPresenceMinutes: Double = 2.0,
    var minSampleBlocks: Double = 8.0,
    var minIntervalBlocks: Double = 400.0,
    var minIntervalCells: Int = 5,
    var dimensions: MutableList<String> = mutableListOf("minecraft:overworld"),
    /** Empty: every biome (the other mods' too) but the excluded ones. Ids or tags ("#minecraft:is_forest"). */
    var biomes: MutableList<String> = mutableListOf(),
    var excludedBiomes: MutableList<String> = mutableListOf("#minecraft:is_ocean", "#minecraft:is_deep_ocean", "#minecraft:is_river"),
    var repeatBiomeFactor: Double = 0.6,
    var biomeMemory: Int = 4,
    var regionSize: Int = 384,
    var regionCooldownHours: Double = 6.0,
    var minRewardDistance: Double = 500.0,
    var rewardMemory: Int = 4,
    var maxConcurrent: Int = 16,
    var manifestation: Manifestation = Manifestation(),
    var afk: MewConfig.Afk = MewConfig.Afk(),
    /** Every draw and manifestation written to the log. */
    var log: Boolean = true,
) {
    /**
     * The manifestation itself (§ 4): the signal's length, the pursuit's (drawn between min and
     * max), the anomaly's speed and its dashes (blocks a second: a walking player makes 4.3, a
     * running one 5.6), its illusions, how near to keep (followRadius: it slows down beyond;
     * lostRadius for lostSeconds: lost), the core's time and reach, the whole thing's limit.
     */
    class Manifestation(
        var signalSeconds: Double = 6.0,
        var gazeSeconds: Double = 10.0,
        var pursuitMinSeconds: Double = 60.0,
        var pursuitMaxSeconds: Double = 90.0,
        var speed: Double = 3.6,
        var dashSpeed: Double = 5.2,
        var dashes: Int = 3,
        var illusions: Int = 2,
        var followRadius: Double = 16.0,
        var lostRadius: Double = 28.0,
        var lostSeconds: Double = 8.0,
        var coreSeconds: Double = 30.0,
        var interactRadius: Double = 4.5,
        var maxSeconds: Double = 150.0,
        var startDistanceMin: Double = 14.0,
        var startDistanceMax: Double = 22.0,
    ) {
        fun validate() {
            signalSeconds = JsonConfigs.clamp(signalSeconds, 1.0, 30.0, 6.0)
            gazeSeconds = JsonConfigs.clamp(gazeSeconds, 0.0, 60.0, 10.0)
            pursuitMinSeconds = JsonConfigs.clamp(pursuitMinSeconds, 10.0, 600.0, 60.0)
            pursuitMaxSeconds = JsonConfigs.clamp(pursuitMaxSeconds, pursuitMinSeconds, 600.0, maxOf(90.0, pursuitMinSeconds))
            speed = JsonConfigs.clamp(speed, 1.0, 12.0, 3.6)
            dashSpeed = JsonConfigs.clamp(dashSpeed, speed, 16.0, maxOf(5.2, speed))
            dashes = dashes.coerceIn(0, 12)
            illusions = illusions.coerceIn(0, 8)
            followRadius = JsonConfigs.clamp(followRadius, 6.0, 64.0, 16.0)
            lostRadius = JsonConfigs.clamp(lostRadius, followRadius + 4.0, 160.0, maxOf(28.0, followRadius + 4.0))
            lostSeconds = JsonConfigs.clamp(lostSeconds, 2.0, 120.0, 8.0)
            coreSeconds = JsonConfigs.clamp(coreSeconds, 5.0, 300.0, 30.0)
            interactRadius = JsonConfigs.clamp(interactRadius, 2.0, 8.0, 4.5)
            val least = signalSeconds + gazeSeconds + pursuitMaxSeconds + coreSeconds
            maxSeconds = JsonConfigs.clamp(maxSeconds, least, 1800.0, maxOf(150.0, least))
            startDistanceMin = JsonConfigs.clamp(startDistanceMin, 6.0, 48.0, 14.0)
            startDistanceMax = JsonConfigs.clamp(startDistanceMax, startDistanceMin, 64.0, maxOf(22.0, startDistanceMin))
        }
    }

    @Suppress("SENSELESS_COMPARISON")
    fun validate() {
        chancePercent = JsonConfigs.clamp(chancePercent, 0.0, 100.0, 33.0)
        firstCheckMinutes = JsonConfigs.clamp(firstCheckMinutes, 0.5, 24.0 * 60.0, 8.0)
        checkIntervalMinutes = JsonConfigs.clamp(checkIntervalMinutes, 0.5, 24.0 * 60.0, 8.0)
        minPresenceMinutes = JsonConfigs.clamp(minPresenceMinutes, 0.0, 120.0, 2.0)
        minSampleBlocks = JsonConfigs.clamp(minSampleBlocks, 0.0, 256.0, 8.0)
        minIntervalBlocks = JsonConfigs.clamp(minIntervalBlocks, 0.0, 100_000.0, 400.0)
        minIntervalCells = minIntervalCells.coerceIn(0, 32)
        if (dimensions == null) dimensions = mutableListOf("minecraft:overworld")
        if (biomes == null) biomes = mutableListOf()
        if (excludedBiomes == null) excludedBiomes = mutableListOf()
        repeatBiomeFactor = JsonConfigs.clamp(repeatBiomeFactor, 0.0, 1.0, 0.6)
        biomeMemory = biomeMemory.coerceIn(0, EonRecord.KEPT)
        regionSize = regionSize.coerceIn(32, 8192)
        regionCooldownHours = JsonConfigs.clamp(regionCooldownHours, 0.0, 24.0 * 365.0, 6.0)
        minRewardDistance = JsonConfigs.clamp(minRewardDistance, 0.0, 100_000.0, 500.0)
        rewardMemory = rewardMemory.coerceIn(0, EonRecord.KEPT)
        maxConcurrent = maxConcurrent.coerceIn(1, 256)
        if (manifestation == null) manifestation = Manifestation()
        manifestation.validate()
        if (afk == null) afk = MewConfig.Afk()
        afk.validate()
    }

    fun rules(): EonRules = EonRules(
        chance = chancePercent / 100.0,
        firstTicks = Math.round(firstCheckMinutes * 1200.0).coerceAtLeast(20L),
        intervalTicks = Math.round(checkIntervalMinutes * 1200.0).coerceAtLeast(20L),
        presenceTicks = Math.round(minPresenceMinutes * 1200.0),
        minSampleBlocks = minSampleBlocks,
        minIntervalBlocks = minIntervalBlocks,
        minIntervalCells = minIntervalCells,
        repeatFactor = repeatBiomeFactor,
        biomeMemory = biomeMemory,
        regionSize = regionSize,
        regionCooldownMs = Math.round(regionCooldownHours * 3_600_000.0),
        minRewardDistance = minRewardDistance,
        rewardMemory = rewardMemory,
    )

    companion object {
        const val VERSION = 1

        fun load(): EonConfig = JsonConfigs.load(
            "Eon manifestations", JsonConfigs.file("eon", "eon.json"), EonConfig::class.java, ::EonConfig, VERSION, { it.validate() },
        )
    }
}
