package generations.gg.generations.core.generationscore.common.world.mew

import generations.gg.generations.core.generationscore.common.config.JsonConfigs

/**
 * config/generations/core/mew/mew.json (MEW-PLAN.md § 13). Read at server start and by
 * /generations mew reload. Pure (no Minecraft class): the bench builds one with its defaults.
 *
 * Used now (build 59): hunt, afk. Declared for the next conversations, with their values already
 * checked: manifestations (§ 6), minigame (§ 8), retry (§ 9), spawn (§ 11), lostMew and
 * releaseTimeoutMinutes (§ 12), testMode (§ 14).
 */
class MewConfig(
    var hunt: Hunt = Hunt(),
    var afk: Afk = Afk(),
    var manifestations: Manifestations = Manifestations(),
    var minigame: Minigame = Minigame(),
    var retry: Retry = Retry(),
    var spawn: Spawn = Spawn(),
    /** What a lost Mew (caught by someone else, fainted, gone, not caught in time) does: "reset" or "retry" (D8). */
    var lostMew: String = LOST_RESET,
    var releaseTimeoutMinutes: Int = 60,
    /** The test commands of the last conversation (manifest, minigame, cinematic, spawn) are allowed. */
    var testMode: Boolean = true,
) {
    /**
     * The hunt: every online player in a zone is checked every checkSeconds (spread by their UUID);
     * active time there is added to their own counter. Draws at firstDrawHours, then every
     * drawEveryHours, with the chance read in chanceTable (hours → percent, linear between the
     * points, the first and last values outside them); the encounter is certain at guaranteeHours.
     */
    class Hunt(
        var enabled: Boolean = true,
        var checkSeconds: Int = 30,
        var firstDrawHours: Double = 12.0,
        var drawEveryHours: Double = 2.0,
        var chanceTable: MutableList<ChancePoint> = defaultTable(),
        var guaranteeHours: Double = 50.0,
    ) {
        @Suppress("SENSELESS_COMPARISON")
        fun validate() {
            checkSeconds = checkSeconds.coerceIn(5, 600)
            firstDrawHours = JsonConfigs.clamp(firstDrawHours, 0.0, 10_000.0, 12.0)
            drawEveryHours = JsonConfigs.clamp(drawEveryHours, 0.05, 1_000.0, 2.0)
            guaranteeHours = JsonConfigs.clamp(guaranteeHours, 0.01, 10_000.0, 50.0)
            if (chanceTable == null) chanceTable = defaultTable()
            chanceTable.removeIf { it == null || !it.hours.isFinite() || !it.percent.isFinite() }
            chanceTable.forEach { it.hours = it.hours.coerceIn(0.0, 10_000.0); it.percent = it.percent.coerceIn(0.0, 100.0) }
            chanceTable.sortBy { it.hours }
            if (chanceTable.isEmpty()) chanceTable = defaultTable()
        }

        /** What the state machine needs, as plain numbers. */
        fun rules(): MewStateMachine.Rules = MewStateMachine.Rules(
            firstDrawHours, drawEveryHours, guaranteeHours,
            chanceTable.map { it.hours }.toDoubleArray(), chanceTable.map { it.percent }.toDoubleArray(),
        )
    }

    class ChancePoint(var hours: Double = 0.0, var percent: Double = 0.0)

    /**
     * Away from keyboard (MEW-PLAN.md § 4): a check counts only if (a) the player did something in
     * the last afkSeconds (an action seen by Minecraft — clicks, use, chat, hotbar, sneak — or a
     * change of position, of view or of battle turn between two checks), and (b) they moved at
     * least minMoveBlocks in the last stillMinutes. Fishing (a bobber out), a Cobblemon battle and
     * riding spare (b) when their option is on.
     */
    class Afk(
        var enabled: Boolean = true,
        var afkSeconds: Int = 300,
        var minMoveBlocks: Double = 3.0,
        var stillMinutes: Double = 15.0,
        var fishingExempt: Boolean = true,
        var battleExempt: Boolean = true,
        var ridingExempt: Boolean = true,
    ) {
        fun validate() {
            afkSeconds = afkSeconds.coerceIn(30, 24 * 3600)
            minMoveBlocks = JsonConfigs.clamp(minMoveBlocks, 0.0, 256.0, 3.0)
            stillMinutes = JsonConfigs.clamp(stillMinutes, 1.0, 24.0 * 60.0, 15.0)
        }
    }

    /** False appearances A to D (§ 6, conversation 2). */
    class Manifestations(
        var enabled: Boolean = true,
        var chancePercent: Double = 10.0,
        var intervalMinutes: Double = 15.0,
        /** Also while the player waits after a failed challenge (D12). */
        var duringCooldown: Boolean = true,
        var types: MutableMap<String, ManifestType> = defaultTypes(),
    ) {
        @Suppress("SENSELESS_COMPARISON")
        fun validate() {
            chancePercent = JsonConfigs.clamp(chancePercent, 0.0, 100.0, 10.0)
            intervalMinutes = JsonConfigs.clamp(intervalMinutes, 1.0, 24.0 * 60.0, 15.0)
            if (types == null) types = defaultTypes()
            types.values.removeIf { it == null }
            defaultTypes().forEach { (id, type) -> types.putIfAbsent(id, type) }
            types.values.forEach { it.validate() }
        }
    }

    class ManifestType(var enabled: Boolean = true, var weight: Int = 1, var seconds: Double = 4.0) {
        fun validate() {
            weight = weight.coerceIn(0, 1000)
            seconds = JsonConfigs.clamp(seconds, 0.5, 30.0, 4.0)
        }
    }

    /** Mew's game in three rounds (§ 8, conversation 3). */
    class Minigame(
        /** The rounds played, in order (1 find me, 2 the real one, 3 catch me). */
        var rounds: MutableList<Int> = mutableListOf(1, 2, 3),
        var maxMinutes: Double = 6.0,
        /** Copies in round 2, the real one included. */
        var illusions: Int = 5,
        var hideMinBlocks: Double = 10.0,
        var hideMaxBlocks: Double = 20.0,
        var courseMinPoints: Int = 6,
        var courseMaxPoints: Int = 8,
        var courseMinBlocks: Double = 8.0,
        var courseMaxBlocks: Double = 14.0,
        var catchBlocks: Double = 5.0,
        var hintStrength: Double = 1.0,
        var gameRadius: Double = 24.0,
        var leaveSeconds: Int = 20,
        var interactBlocks: Double = 4.5,
    ) {
        @Suppress("SENSELESS_COMPARISON")
        fun validate() {
            if (rounds == null) rounds = mutableListOf(1, 2, 3)
            rounds.removeIf { it == null || it !in 1..3 }
            if (rounds.isEmpty()) rounds = mutableListOf(1, 2, 3)
            maxMinutes = JsonConfigs.clamp(maxMinutes, 0.5, 60.0, 6.0)
            illusions = illusions.coerceIn(2, 8)
            hideMinBlocks = JsonConfigs.clamp(hideMinBlocks, 2.0, 64.0, 10.0)
            hideMaxBlocks = JsonConfigs.clamp(hideMaxBlocks, hideMinBlocks, 64.0, 20.0)
            courseMinPoints = courseMinPoints.coerceIn(2, 8)
            courseMaxPoints = courseMaxPoints.coerceIn(courseMinPoints, 8)
            courseMinBlocks = JsonConfigs.clamp(courseMinBlocks, 2.0, 64.0, 8.0)
            courseMaxBlocks = JsonConfigs.clamp(courseMaxBlocks, courseMinBlocks, 64.0, 14.0)
            catchBlocks = JsonConfigs.clamp(catchBlocks, 1.0, 16.0, 5.0)
            hintStrength = JsonConfigs.clamp(hintStrength, 0.0, 3.0, 1.0)
            gameRadius = JsonConfigs.clamp(gameRadius, 8.0, 64.0, 24.0)
            leaveSeconds = leaveSeconds.coerceIn(1, 600)
            interactBlocks = JsonConfigs.clamp(interactBlocks, 1.0, 6.0, 4.5)
        }
    }

    /** After a failed challenge (§ 9, conversation 3). */
    class Retry(
        /** Active minutes on an island before Mew comes back (D6: also after a disconnection). */
        var retryMinutes: Int = 45,
        var disconnectFails: Boolean = true,
    ) {
        fun validate() {
            retryMinutes = retryMinutes.coerceIn(1, 600)
        }
    }

    /** The real Mew (§ 11, conversation 4). */
    class Spawn(
        var level: Int = 50,
        /** More Cobblemon properties, "" for none. */
        var properties: String = "",
        var cinematic: Boolean = true,
        /** A cinematic that cannot start now is tried again after this many seconds, then Mew appears without it (D9). */
        var cinematicRetrySeconds: Int = 60,
    ) {
        @Suppress("SENSELESS_COMPARISON")
        fun validate() {
            level = level.coerceIn(1, 100)
            if (properties == null) properties = ""
            cinematicRetrySeconds = cinematicRetrySeconds.coerceIn(5, 3600)
        }
    }

    @Suppress("SENSELESS_COMPARISON")
    fun validate() {
        if (hunt == null) hunt = Hunt()
        hunt.validate()
        if (afk == null) afk = Afk()
        afk.validate()
        if (manifestations == null) manifestations = Manifestations()
        manifestations.validate()
        if (minigame == null) minigame = Minigame()
        minigame.validate()
        if (retry == null) retry = Retry()
        retry.validate()
        if (spawn == null) spawn = Spawn()
        spawn.validate()
        if (lostMew != LOST_RETRY) lostMew = LOST_RESET
        releaseTimeoutMinutes = releaseTimeoutMinutes.coerceIn(1, 24 * 60)
    }

    companion object {
        const val VERSION = 1
        const val LOST_RESET = "reset"
        const val LOST_RETRY = "retry"

        /**
         * Scale B of the plan (D1), points set so that the draws at 12, 14 … 48 h with linear
         * interpolation give a mean of 35.5 h and 10 % of the hunts at the 50 h guarantee
         * (MewBench; the plan's 2.6 % at 20 h and 8.4 % at 30 h came from a smooth curve and gave
         * 35.0 h with straight lines).
         */
        @JvmStatic
        fun defaultTable(): MutableList<ChancePoint> = mutableListOf(
            ChancePoint(12.0, 1.2), ChancePoint(20.0, 2.4), ChancePoint(30.0, 7.8), ChancePoint(40.0, 17.8), ChancePoint(48.0, 30.0),
        )

        fun defaultTypes(): MutableMap<String, ManifestType> = linkedMapOf(
            "a" to ManifestType(true, 3, 3.0),
            "b" to ManifestType(true, 3, 6.0),
            "c" to ManifestType(true, 2, 5.0),
            "d" to ManifestType(true, 2, 5.0),
        )

        fun load(): MewConfig = JsonConfigs.load(
            "Mew", JsonConfigs.file("mew", "mew.json"), MewConfig::class.java, ::MewConfig, VERSION, { it.validate() },
        )
    }
}
