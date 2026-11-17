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
    /** Lugia's shrine plays its scene before Lugia appears (LUGIA-PLAN.md); false: the original 5 s. */
    var lugiaShrine: Boolean = true,
    /** The players this near Lugia's shrine see its scene in their world, without its camera. */
    var lugiaSpectatorRadius: Double = 64.0,
    /** The Regis' shrines play their scene before the Regi appears (REGIS-PLAN.md); false: at once. */
    var regiShrines: Boolean = true,
    /** The players this near a Regi's shrine see its scene in their world, without its camera. */
    var regiSpectatorRadius: Double = 64.0,
    /** The Enigma Stone's summoning plays Latias's or Latios's scene (EON-PLAN.md § 7); false: the original appearance at once. */
    var eonCinematic: Boolean = true,
    /** The players this near the summoner see the scene in their world, without its camera. */
    var eonSpectatorRadius: Double = 48.0,
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
        lugiaSpectatorRadius = JsonConfigs.clamp(lugiaSpectatorRadius, 0.0, 128.0, 64.0)
        regiSpectatorRadius = JsonConfigs.clamp(regiSpectatorRadius, 0.0, 128.0, 64.0)
        eonSpectatorRadius = JsonConfigs.clamp(eonSpectatorRadius, 0.0, 128.0, 48.0)
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
            linkedMapOf(CinematicScenes.MEWTWO to CinematicScenes.MEWTWO_SECONDS, CinematicScenes.MEW to CinematicScenes.MEW_SECONDS, CinematicScenes.HO_OH to CinematicScenes.HO_OH_SECONDS).also { map ->
                // The Legend of Johto (JOHTO-PLAN.md): as written.
                for (scene in CinematicScenes.ALL.drop(6)) map[scene] = CinematicScenes.writtenSeconds(scene)
            }

        /**
         * Mewtwo 50 blocks (D11 of the RKS plan), Mew 32 like the birds (D10 of the Mew plan); Johto's
         * scenes are watched by their invoker alone (the nearby players get the ambience, johto.json).
         */
        fun defaultSceneRadius(): MutableMap<String, Double> = linkedMapOf(CinematicScenes.MEWTWO to 50.0, CinematicScenes.MEW to 32.0, CinematicScenes.HO_OH to 0.0).also { map ->
            for (scene in CinematicScenes.ALL.drop(6)) map[scene] = 0.0
        }

        fun defaultScenes(): MutableMap<String, String> =
            CinematicScenes.ALL.associateWithTo(LinkedHashMap()) { CinematicScenes.defaultSpecies(it) }

        fun load(): CinematicConfig = JsonConfigs.load(
            "Legendary cinematics", JsonConfigs.file("cinematics", "cinematics.json"), CinematicConfig::class.java, ::CinematicConfig, VERSION, { it.validate() },
        )
    }
}
