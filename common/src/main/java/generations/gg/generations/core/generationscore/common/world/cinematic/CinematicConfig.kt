package generations.gg.generations.core.generationscore.common.world.cinematic

import generations.gg.generations.core.generationscore.common.config.JsonConfigs

/**
 * config/generations/core/cinematics/cinematics.json: the server's side of the legendary
 * cinematics (CINEMATIQUES-PLAN.md § 4.2). Read at server start and by
 * /generations cinematic reload.
 *
 * testMode allows /generations cinematic test (on by default while the scenes are made);
 * debugOverlay shows the scene's time and shot on the clients of a test.
 */
class CinematicConfig(
    var enabled: Boolean = true,
    var durationSeconds: Double = CinematicScenes.WRITTEN_SECONDS,
    var spectatorRadius: Double = 32.0,
    var autoBattle: Boolean = true,
    var testMode: Boolean = true,
    var debugOverlay: Boolean = false,
    var reservationSeconds: Int = 180,
    /** Scene → species ("cobblemon:articuno"). */
    var scenes: MutableMap<String, String> = defaultScenes(),
) {
    @Suppress("SENSELESS_COMPARISON")
    fun validate() {
        durationSeconds = JsonConfigs.clamp(durationSeconds, CinematicScenes.MIN_SECONDS, CinematicScenes.MAX_SECONDS, CinematicScenes.WRITTEN_SECONDS)
        spectatorRadius = JsonConfigs.clamp(spectatorRadius, 0.0, 256.0, 32.0)
        reservationSeconds = reservationSeconds.coerceIn(0, 3600)
        if (scenes == null) scenes = defaultScenes()
        for (scene in CinematicScenes.ALL) {
            val species = scenes[scene]
            if (species == null || species.isBlank()) scenes[scene] = CinematicScenes.defaultSpecies(scene)
        }
    }

    val durationTicks: Int get() = (durationSeconds * 20.0).toInt()

    fun species(scene: String): String {
        val id = (scenes[scene] ?: CinematicScenes.defaultSpecies(scene)).trim().lowercase()
        return if (':' in id) id else "cobblemon:$id"
    }

    companion object {
        const val VERSION = 1

        fun defaultScenes(): MutableMap<String, String> =
            CinematicScenes.ALL.associateWithTo(LinkedHashMap()) { CinematicScenes.defaultSpecies(it) }

        fun load(): CinematicConfig = JsonConfigs.load(
            "Legendary cinematics", JsonConfigs.file("cinematics", "cinematics.json"), CinematicConfig::class.java, ::CinematicConfig, VERSION, { it.validate() },
        )
    }
}
