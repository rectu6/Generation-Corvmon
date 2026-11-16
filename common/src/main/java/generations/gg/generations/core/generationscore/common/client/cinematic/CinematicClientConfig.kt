package generations.gg.generations.core.generationscore.common.client.cinematic

import generations.gg.generations.core.generationscore.common.config.JsonConfigs

/**
 * config/generations/core/cinematics/cinematics_client.json (CINEMATIQUES-PLAN.md § 4.3), read
 * again at the start of each scene: no need to restart the game.
 *
 * - quality: LOW (camera, model, essential 3D effects, sound), MEDIUM (+ particles ×2,
 *   distortion, light shafts), HIGH (+ particles ×3, bloom, chromatic aberration, full grade);
 * - particleIntensity: 0.25 to 2, on top of the quality;
 * - volume (effects and ambience) and musicVolume, 0 to 1 (on top of the master volume);
 * - reduceFlashes: flashes at 25 % at most, one every 0.5 s at most, never a white screen;
 * - reduceCameraMotion: no shake, roll and FOV punches ×0.3, slower orbits;
 * - letterbox: the black bars.
 */
class CinematicClientConfig(
    var quality: Quality = Quality.HIGH,
    var particleIntensity: Float = 1f,
    var volume: Float = 1f,
    var musicVolume: Float = 0.8f,
    var reduceFlashes: Boolean = false,
    var reduceCameraMotion: Boolean = false,
    var letterbox: Boolean = true,
) {
    enum class Quality(val particles: Int) { LOW(1), MEDIUM(2), HIGH(3) }

    @Suppress("SENSELESS_COMPARISON")
    fun validate() {
        if (quality == null) quality = Quality.HIGH
        particleIntensity = JsonConfigs.clamp(particleIntensity, 0.25f, 2f, 1f)
        volume = JsonConfigs.clamp(volume, 0f, 1f, 1f)
        musicVolume = JsonConfigs.clamp(musicVolume, 0f, 1f, 0.8f)
    }

    /** How many particles for a base count, with the quality and the intensity. */
    fun particles(base: Int): Int = (base * quality.particles * particleIntensity).toInt().coerceAtLeast(0)

    val post: Boolean get() = quality != Quality.LOW
    val high: Boolean get() = quality == Quality.HIGH

    companion object {
        const val VERSION = 1

        fun load(): CinematicClientConfig = JsonConfigs.load(
            "Legendary cinematics (client)", JsonConfigs.file("cinematics", "cinematics_client.json"), CinematicClientConfig::class.java, ::CinematicClientConfig, VERSION, { it.validate() },
        )
    }
}
