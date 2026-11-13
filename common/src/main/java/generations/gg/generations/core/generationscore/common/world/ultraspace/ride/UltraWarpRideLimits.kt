package generations.gg.generations.core.generationscore.common.world.ultraspace.ride

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Where the Pokémon may fly in the tunnel, the same on the client (which steers) and
 * the server (which checks).
 *
 * Its gameplay shape is an ellipse across the tunnel (halfWidth sideways, halfHeight
 * up and down, wings' beat included), halfLength along it. How far it reaches towards
 * a direction depends on that direction and on how it is tilted: banked (the ellipse
 * turns), nose up or down and turned aside (its length adds a little). Its centre may
 * go, in each direction, as far as the walls at their narrowest there, minus that
 * reach, minus a margin (floor, sides, ceiling): so it gets close to the floor without
 * going through, whatever its size. The client uses its real tilt; the server, which
 * does not know it, the untilted shape (the most room), plus a tolerance.
 *
 * A Pokémon too wide for the tunnel is made smaller, once for the whole journey.
 */
object UltraWarpRideLimits {
    /** Size of the Pokémon for this journey (1 = as in the world): its hitRadius takes at most maxFill of the narrowest radius. */
    fun riderScale(settings: UltraWarpRideSettings): Float {
        if (settings.hitRadius <= 0f) return 1f
        val fit = settings.maxFill * UltraWarpTunnel.minRadius(settings) / settings.hitRadius
        return min(1.0, fit).toFloat().coerceAtLeast(0.1f)
    }

    /** Room the Pokémon takes around its centre whatever its tilt, at its size for this journey (camera framing). */
    fun riderRadius(settings: UltraWarpRideSettings): Double = settings.hitRadius.toDouble() * riderScale(settings)

    /** Gameplay shape: half sizes in blocks across the tunnel (width, height) and along it. */
    class Shape(val halfWidth: Double, val halfHeight: Double, val halfLength: Double) {
        fun scaled(factor: Double) = Shape(halfWidth * factor, halfHeight * factor, halfLength * factor)
    }

    /** The Pokémon's shape at its size for this journey, wings' beat included. */
    fun shape(settings: UltraWarpRideSettings): Shape {
        val scale = riderScale(settings).toDouble()
        val rider = settings.rider
        return Shape(rider.halfWidth * scale, (rider.halfHeight + rider.flapAllowance) * scale, rider.halfLength * scale)
    }

    /** What counts for collisions: the shape times hitboxScale (a little forgiving). */
    fun hitbox(settings: UltraWarpRideSettings): Shape = shape(settings).scaled(settings.config.riders.hitboxScale.toDouble())

    /**
     * How far the shape reaches from its centre towards (nx, ny) (a unit direction, x
     * towards the tunnel's right, y up): banked by bank radians (its top towards the
     * right), nose up or down by pitch, turned aside by yaw.
     */
    fun reach(shape: Shape, nx: Double, ny: Double, bank: Double = 0.0, pitch: Double = 0.0, yaw: Double = 0.0): Double {
        val a = shape.halfWidth + shape.halfLength * abs(kotlin.math.sin(yaw))
        val b = shape.halfHeight + shape.halfLength * abs(kotlin.math.sin(pitch))
        val cos = kotlin.math.cos(bank)
        val sin = kotlin.math.sin(bank)
        // The Pokémon's right (cos, -sin) and up (sin, cos) across the tunnel.
        val along = nx * cos - ny * sin
        val up = nx * sin + ny * cos
        return sqrt(a * a * along * along + b * b * up * up)
    }

    /** Room kept from the walls towards a direction going up by ny: floor below, ceiling above, sides between. */
    fun margin(settings: UltraWarpRideSettings, ny: Double): Double {
        val limits = settings.config.limits
        return if (ny < 0) limits.sideMargin + (limits.floorMargin - limits.sideMargin) * -ny
        else limits.sideMargin + (limits.ceilingMargin - limits.sideMargin) * ny
    }

    /** How far from the axis the Pokémon's centre may go towards (nx, ny) at this distance along the tunnel. */
    fun allowed(
        tunnel: UltraWarpTunnel, distance: Double, shape: Shape, nx: Double, ny: Double,
        bank: Double = 0.0, pitch: Double = 0.0, yaw: Double = 0.0,
    ): Double = max(0.0, tunnel.coreRadius(distance) - margin(tunnel.settings, ny) - reach(shape, nx, ny, bank, pitch, yaw))

    /** Length of an offset (x sideways, y up) from the axis. */
    fun length(x: Double, y: Double): Double = sqrt(x * x + y * y)
}
