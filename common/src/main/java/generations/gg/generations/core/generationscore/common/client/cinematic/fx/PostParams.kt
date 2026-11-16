package generations.gg.generations.core.generationscore.common.client.cinematic.fx

/**
 * What the final composition does this frame (set by the scene, limited by the quality and the
 * accessibility settings). Waves: centre in screen uv (0 to 1, y up), radius in screen heights,
 * strength (0 = none), width.
 */
class PostParams {
    val waves = Array(2) { FloatArray(5) }
    var heat = 0f
    var heatSpeed = 0.3f
    var heatScale = 3f
    var heatFloor = 0f
    var r = 1f
    var g = 1f
    var b = 1f
    var exposure = 1f
    var saturation = 1f
    var contrast = 1f
    var vignette = 0f
    var aberration = 0f
    var flashR = 1f
    var flashG = 1f
    var flashB = 1f
    var flash = 0f
    var raysX = 0.5f
    var raysY = 0.5f
    var rays = 0f
    var raysDecay = 0.95f
    var glow = 1f
    var bloom = 0f

    /** Radial blur toward a screen point (uv), HIGH only. */
    var radialX = 0.5f
    var radialY = 0.5f
    var radial = 0f

    fun reset() {
        for (w in waves) w.fill(0f)
        heat = 0f
        r = 1f; g = 1f; b = 1f; exposure = 1f; saturation = 1f; contrast = 1f; vignette = 0f; aberration = 0f
        flash = 0f; flashR = 1f; flashG = 1f; flashB = 1f
        rays = 0f
        glow = 1f
        bloom = 0f
        radial = 0f
    }

    fun wave(i: Int, x: Float, y: Float, radius: Float, strength: Float, width: Float) {
        val w = waves[i]
        w[0] = x; w[1] = y; w[2] = radius; w[3] = strength; w[4] = width
    }
}

/**
 * Accessibility for the flashes (pure, tested off-game): with reduceFlashes, 25 % at most and
 * one every 0.5 s at most (a new one waits for the last to end); never a white screen.
 */
class FlashLimiter(private val reduce: Boolean) {
    private var lastPeak = -10.0
    private var previous = 0.0
    private var rising = false
    private var accepted = false

    /** The flash drawn at time t (seconds, increasing) for the scene's raw flash. */
    fun limit(t: Double, raw: Double): Double {
        val value = raw.coerceIn(0.0, 1.0)
        if (!reduce) {
            previous = value
            return value * 0.92
        }
        val up = value > previous + 1e-4
        if (up && !rising) {
            // A new flash: refused when the last one shown started less than 0.5 s ago.
            accepted = t - lastPeak >= 0.5
            if (accepted) lastPeak = t
        }
        rising = up
        previous = value
        if (!accepted) return 0.0
        // Faded out by the end of its half second (no sudden cut).
        val x = ((t - lastPeak - 0.35) / 0.15).coerceIn(0.0, 1.0)
        return minOf(value, 0.25) * (1.0 - x * x * (3.0 - 2.0 * x))
    }
}
