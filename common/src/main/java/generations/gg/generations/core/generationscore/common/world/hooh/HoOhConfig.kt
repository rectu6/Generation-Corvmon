package generations.gg.generations.core.generationscore.common.world.hooh

import generations.gg.generations.core.generationscore.common.config.JsonConfigs
import generations.gg.generations.core.generationscore.common.world.mew.MewConfig

/**
 * config/generations/core/hooh/ho_oh_passage.json: the Passage of the Rainbow (HOOH-PLAN.md
 * §§ 5 to 8), read at every server start and by /generations hooh reload.
 *
 * Rarity (D1): a draw every checkIntervalMinutes of active play (away from keyboard excluded,
 * the same rule as Mew's hunt: afk), only after minActiveHours of active play since the player's
 * last passage and cooldownHours of real time; chancePercent each draw (0.75 %: about 24 h of
 * active play on average, half of the players within 17 h). No guarantee. At most maxConcurrent
 * passages on the server at once; only in the dimensions listed, never in excludedBiomes
 * ("minecraft:ocean" or a tag "#minecraft:is_ocean").
 *
 * cinematic: the signal and the passage as a scene of the engine (the camera taken about 13 s,
 * D2), otherwise Ho-Oh comes without taking the camera. scale: Ho-Oh's size in the sky.
 *
 * pursuit: its length (minSeconds to maxSeconds, drawn), Ho-Oh's cruising speed (blocks a
 * second; a running player makes 5.6), followRadius (it slows down beyond), waitRadius (it
 * circles and waits beyond), lostRadius and lostSeconds (lost: the passage fails), finishRadius
 * (how close to its last point to get the feather), timeoutSeconds, its height over the trees.
 */
class HoOhConfig(
    var enabled: Boolean = true,
    var chancePercent: Double = 0.75,
    var checkIntervalMinutes: Double = 10.0,
    var minActiveHours: Double = 2.0,
    var cooldownHours: Double = 24.0,
    var maxConcurrent: Int = 3,
    var dimensions: MutableList<String> = mutableListOf("minecraft:overworld"),
    var excludedBiomes: MutableList<String> = mutableListOf("#minecraft:is_ocean"),
    var cinematic: Boolean = true,
    var scale: Double = 1.6,
    var pursuit: Pursuit = Pursuit(),
    var afk: MewConfig.Afk = MewConfig.Afk(),
    /** Every draw and passage written to the log. */
    var log: Boolean = true,
) {
    class Pursuit(
        var minSeconds: Double = 45.0,
        var maxSeconds: Double = 90.0,
        var speed: Double = 5.0,
        var followRadius: Double = 28.0,
        var waitRadius: Double = 44.0,
        var lostRadius: Double = 64.0,
        var lostSeconds: Double = 12.0,
        var finishRadius: Double = 16.0,
        var timeoutSeconds: Double = 150.0,
        var altitudeMin: Double = 18.0,
        var altitudeMax: Double = 26.0,
    ) {
        fun validate() {
            minSeconds = JsonConfigs.clamp(minSeconds, 15.0, 600.0, 45.0)
            maxSeconds = JsonConfigs.clamp(maxSeconds, minSeconds, 600.0, maxOf(90.0, minSeconds))
            speed = JsonConfigs.clamp(speed, 1.0, 20.0, 5.0)
            followRadius = JsonConfigs.clamp(followRadius, 8.0, 128.0, 28.0)
            waitRadius = JsonConfigs.clamp(waitRadius, followRadius, 160.0, maxOf(44.0, followRadius))
            lostRadius = JsonConfigs.clamp(lostRadius, waitRadius, 256.0, maxOf(64.0, waitRadius))
            lostSeconds = JsonConfigs.clamp(lostSeconds, 2.0, 120.0, 12.0)
            finishRadius = JsonConfigs.clamp(finishRadius, 4.0, 64.0, 16.0)
            timeoutSeconds = JsonConfigs.clamp(timeoutSeconds, maxSeconds + 10.0, 1200.0, maxOf(150.0, maxSeconds + 10.0))
            altitudeMin = JsonConfigs.clamp(altitudeMin, 8.0, 64.0, 18.0)
            altitudeMax = JsonConfigs.clamp(altitudeMax, altitudeMin, 96.0, maxOf(26.0, altitudeMin))
        }
    }

    @Suppress("SENSELESS_COMPARISON")
    fun validate() {
        chancePercent = JsonConfigs.clamp(chancePercent, 0.0, 100.0, 0.75)
        checkIntervalMinutes = JsonConfigs.clamp(checkIntervalMinutes, 0.5, 24.0 * 60.0, 10.0)
        minActiveHours = JsonConfigs.clamp(minActiveHours, 0.0, 1000.0, 2.0)
        cooldownHours = JsonConfigs.clamp(cooldownHours, 0.0, 24.0 * 365.0, 24.0)
        maxConcurrent = maxConcurrent.coerceIn(1, 64)
        if (dimensions == null) dimensions = mutableListOf("minecraft:overworld")
        if (excludedBiomes == null) excludedBiomes = mutableListOf()
        scale = JsonConfigs.clamp(scale, 0.5, 4.0, 1.6)
        if (pursuit == null) pursuit = Pursuit()
        pursuit.validate()
        if (afk == null) afk = MewConfig.Afk()
        afk.validate()
    }

    fun rules(): HoOhRules = HoOhRules(
        chancePercent / 100.0,
        Math.round(checkIntervalMinutes * 60.0 * 20.0).coerceAtLeast(20L),
        Math.round(minActiveHours * HoOhRules.TICKS_PER_HOUR),
        Math.round(cooldownHours * 3_600_000.0),
    )

    companion object {
        const val VERSION = 1

        fun load(): HoOhConfig = JsonConfigs.load(
            "Ho-Oh passage", JsonConfigs.file("hooh", "ho_oh_passage.json"), HoOhConfig::class.java, ::HoOhConfig, VERSION, { it.validate() },
        )
    }
}
