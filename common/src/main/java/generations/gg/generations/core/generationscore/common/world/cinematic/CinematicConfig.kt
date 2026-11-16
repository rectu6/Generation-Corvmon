package generations.gg.generations.core.generationscore.common.world.cinematic

import generations.gg.generations.core.generationscore.common.config.JsonConfigs

/**
 * config/generations/core/cinematics/cinematics.json: the server's side of the legendary
 * cinematics (CINEMATIQUES-PLAN.md § 4.2). Read at server start and by
 * /generations cinematic reload.
 *
 * testMode allows /generations cinematic test (on by default while the scenes are made);
 * debugOverlay shows the scene's time and shot on the clients of a test.
 *
 * durationSeconds and spectatorRadius are the birds'; sceneSeconds and sceneRadius set them for a
 * scene of their own (Mewtwo: 24 s, 50 blocks, RKS-PLAN.md D11). ambientRadius: the players near
 * the scene who cannot watch it (in a battle...) get its ambience only (no camera), client side.
 */
class CinematicConfig(
    var enabled: Boolean = true,
    var durationSeconds: Double = CinematicScenes.WRITTEN_SECONDS,
    var spectatorRadius: Double = 32.0,
    var autoBattle: Boolean = true,
    var testMode: Boolean = true,
    var debugOverlay: Boolean = false,
    var reservationSeconds: Int = 180,
    var sceneSeconds: MutableMap<String, Double> = defaultSceneSeconds(),
    var sceneRadius: MutableMap<String, Double> = defaultSceneRadius(),
    var ambientRadius: Double = 50.0,
    /** Scene → species ("cobblemon:articuno"). */
    var scenes: MutableMap<String, String> = defaultScenes(),
) {
    @Suppress("SENSELESS_COMPARISON")
    fun validate() {
        durationSeconds = JsonConfigs.clamp(durationSeconds, CinematicScenes.MIN_SECONDS, CinematicScenes.MAX_SECONDS, CinematicScenes.WRITTEN_SECONDS)
        spectatorRadius = JsonConfigs.clamp(spectatorRadius, 0.0, 256.0, 32.0)
        reservationSeconds = reservationSeconds.coerceIn(0, 3600)
        if (sceneSeconds == null) sceneSeconds = defaultSceneSeconds()
        for ((scene, seconds) in defaultSceneSeconds()) sceneSeconds.putIfAbsent(scene, seconds)
        sceneSeconds.replaceAll { scene, seconds -> JsonConfigs.clamp(seconds, CinematicScenes.minSeconds(scene), CinematicScenes.maxSeconds(scene), CinematicScenes.writtenSeconds(scene)) }
        if (sceneRadius == null) sceneRadius = defaultSceneRadius()
        for ((scene, radius) in defaultSceneRadius()) sceneRadius.putIfAbsent(scene, radius)
        sceneRadius.replaceAll { _, radius -> JsonConfigs.clamp(radius, 0.0, 256.0, 50.0) }
        ambientRadius = JsonConfigs.clamp(ambientRadius, 0.0, 256.0, 50.0)
        if (scenes == null) scenes = defaultScenes()
        for (scene in CinematicScenes.ALL) {
            val species = scenes[scene]
            if (species == null || species.isBlank()) scenes[scene] = CinematicScenes.defaultSpecies(scene)
        }
    }

    val durationTicks: Int get() = (durationSeconds * 20.0).toInt()

    /** That scene's duration (its own, or the birds'). */
    fun durationTicks(scene: String): Int = ((sceneSeconds[scene] ?: durationSeconds) * 20.0).toInt()

    /** That scene's spectators' radius (its own, or the birds'). */
    fun radius(scene: String): Double = sceneRadius[scene] ?: spectatorRadius

    fun species(scene: String): String {
        val id = (scenes[scene] ?: CinematicScenes.defaultSpecies(scene)).trim().lowercase()
        return if (':' in id) id else "cobblemon:$id"
    }

    companion object {
        const val VERSION = 1

        fun defaultSceneSeconds(): MutableMap<String, Double> =
            linkedMapOf(CinematicScenes.MEWTWO to CinematicScenes.MEWTWO_SECONDS, CinematicScenes.MEW to CinematicScenes.MEW_SECONDS)

        /** Mewtwo 50 blocks (D11 of the RKS plan), Mew 32 like the birds (D10 of the Mew plan). */
        fun defaultSceneRadius(): MutableMap<String, Double> = linkedMapOf(CinematicScenes.MEWTWO to 50.0, CinematicScenes.MEW to 32.0)

        fun defaultScenes(): MutableMap<String, String> =
            CinematicScenes.ALL.associateWithTo(LinkedHashMap()) { CinematicScenes.defaultSpecies(it) }

        fun load(): CinematicConfig = JsonConfigs.load(
            "Legendary cinematics", JsonConfigs.file("cinematics", "cinematics.json"), CinematicConfig::class.java, ::CinematicConfig, VERSION, { it.validate() },
        )
    }
}
