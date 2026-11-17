package generations.gg.generations.core.generationscore.common.world.johto

import generations.gg.generations.core.generationscore.common.config.JsonConfigs

/**
 * config/generations/core/johto/johto.json: the Legend of Johto's rules (JOHTO-PLAN.md § 2).
 * Read at server start and by /generations johto reload. The places (towers, statues, zones,
 * spawn points, altar) are in johto_places.json, written by the admin commands.
 */
class JohtoConfig(
    var enabled: Boolean = true,
    /** The failure's delay before trying again, in real minutes (D9). */
    var cooldownMinutes: Double = 20.0,
    /** The countdown in the zone before a trial (leaving cancels it, D11). */
    var countdownSeconds: Int = 5,
    /** Out of the zone (or Entei's arena) this long: the trial fails. */
    var outsideSeconds: Int = 10,
    /** A whole trial longer than this fails. */
    var trialMaxMinutes: Double = 15.0,
    /** The latency allowed for in the windows (the player's measured ping, at most this). */
    var maxLatencyMs: Int = 600,
    /** The scenes already seen may be skipped (space held 1 s, D17); the rewards and spawns stay the server's. */
    var allowSkipSeen: Boolean = true,
    /** Only their winner may use the memories at the altar (D12). */
    var memoriesBound: Boolean = true,
    /** Generations' capture limits (2 per legendary) block nothing here (D13). */
    var respectCaughtLimits: Boolean = false,
    /** The nearby players who see an encounter or Ho-Oh's summoning without their camera taken. */
    var spectatorRadius: Double = 32.0,
    /** The wild beasts and Ho-Oh: their level and extra Cobblemon properties ("shiny=no"...). */
    var beastLevel: Int = 50,
    var beastProperties: String = "",
    var hoOhLevel: Int = 70,
    var hoOhProperties: String = "",
    /** The admin's test commands (they never change a normal progression). */
    var testMode: Boolean = true,
    var log: Boolean = true,
    var raikou: Raikou = Raikou(),
    var entei: Entei = Entei(),
    var suicune: Suicune = Suicune(),
) {
    /** The Thunder Race: tracks to follow, then flashing moves to anticipate, an interception a round. */
    class Raikou(
        var tracks: Int = 5,
        var trackRadius: Double = 2.6,
        var interceptRadius: Double = 2.8,
        /** How long Raikou waits at its last point (the window, plus the latency). */
        var interceptSeconds: Double = 3.0,
        /** Its flashes' warning before each move. */
        var warnSeconds: Double = 1.3,
        var missesAllowed: Int = 3,
        var roundSeconds: Int = 150,
    ) {
        fun validate() {
            tracks = tracks.coerceIn(2, 12)
            trackRadius = JsonConfigs.clamp(trackRadius, 1.0, 6.0, 2.6)
            interceptRadius = JsonConfigs.clamp(interceptRadius, 1.0, 6.0, 2.8)
            interceptSeconds = JsonConfigs.clamp(interceptSeconds, 1.0, 10.0, 3.0)
            warnSeconds = JsonConfigs.clamp(warnSeconds, 0.5, 4.0, 1.3)
            missesAllowed = missesAllowed.coerceIn(1, 10)
            roundSeconds = roundSeconds.coerceIn(30, 900)
        }
    }

    /** The Judgement of Flames: announced attacks, an endurance of its own, three seals. */
    class Entei(
        var arenaRadius: Double = 13.0,
        var endurance: Int = 100,
        var hitDamage: Int = 20,
        var warnSeconds: Double = 1.3,
        var sealSeconds: Double = 3.0,
        var attackSeconds: Double = 2.6,
        var roundSeconds: Int = 150,
    ) {
        fun validate() {
            arenaRadius = JsonConfigs.clamp(arenaRadius, 7.0, 32.0, 13.0)
            endurance = endurance.coerceIn(20, 1000)
            hitDamage = hitDamage.coerceIn(1, endurance)
            warnSeconds = JsonConfigs.clamp(warnSeconds, 0.6, 4.0, 1.3)
            sealSeconds = JsonConfigs.clamp(sealSeconds, 1.0, 10.0, 3.0)
            attackSeconds = JsonConfigs.clamp(attackSeconds, 1.2, 8.0, 2.6)
            roundSeconds = roundSeconds.coerceIn(30, 900)
        }
    }

    /** The Waters of Rebirth: three corrupted sources, a sequence each, a final harmony. */
    class Suicune(
        var findRadius: Double = 3.0,
        var notesMin: Int = 4,
        var notesMax: Int = 6,
        var errorsAllowed: Int = 6,
        var harmonyHits: Int = 3,
        var harmonyMisses: Int = 5,
        var harmonyWindowMs: Int = 350,
        var roundSeconds: Int = 240,
    ) {
        fun validate() {
            findRadius = JsonConfigs.clamp(findRadius, 1.5, 8.0, 3.0)
            notesMin = notesMin.coerceIn(2, 8)
            notesMax = notesMax.coerceIn(notesMin, 9)
            errorsAllowed = errorsAllowed.coerceIn(1, 30)
            harmonyHits = harmonyHits.coerceIn(1, 6)
            harmonyMisses = harmonyMisses.coerceIn(1, 20)
            harmonyWindowMs = harmonyWindowMs.coerceIn(100, 1500)
            roundSeconds = roundSeconds.coerceIn(30, 1200)
        }
    }

    @Suppress("SENSELESS_COMPARISON")
    fun validate() {
        cooldownMinutes = JsonConfigs.clamp(cooldownMinutes, 0.0, 24.0 * 60.0, 20.0)
        countdownSeconds = countdownSeconds.coerceIn(0, 30)
        outsideSeconds = outsideSeconds.coerceIn(2, 120)
        trialMaxMinutes = JsonConfigs.clamp(trialMaxMinutes, 2.0, 120.0, 15.0)
        maxLatencyMs = maxLatencyMs.coerceIn(0, 2000)
        spectatorRadius = JsonConfigs.clamp(spectatorRadius, 0.0, 128.0, 32.0)
        beastLevel = beastLevel.coerceIn(1, 100)
        hoOhLevel = hoOhLevel.coerceIn(1, 100)
        if (beastProperties == null) beastProperties = ""
        if (hoOhProperties == null) hoOhProperties = ""
        if (raikou == null) raikou = Raikou()
        if (entei == null) entei = Entei()
        if (suicune == null) suicune = Suicune()
        raikou.validate()
        entei.validate()
        suicune.validate()
    }

    val cooldownMs: Long get() = (cooldownMinutes * 60_000.0).toLong()

    companion object {
        const val VERSION = 1

        fun load(): JohtoConfig = JsonConfigs.load(
            "Legend of Johto", JsonConfigs.file("johto", "johto.json"), JohtoConfig::class.java, ::JohtoConfig, VERSION, { it.validate() },
        )
    }
}
