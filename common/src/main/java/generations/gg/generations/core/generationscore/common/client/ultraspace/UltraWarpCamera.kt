package generations.gg.generations.core.generationscore.common.client.ultraspace

import generations.gg.generations.core.generationscore.common.world.ultraspace.ride.UltraWarpTunnel
import org.joml.Quaternionf
import org.joml.Vector3d
import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The journey's camera, guided by the tunnel: it never turns freely.
 * - Where: on the tunnel's path, `distance` Pokémon sizes behind it and `height`
 *   above, following its moves across the tunnel by `follow`. Its offset from the
 *   axis is kept within the walls at its own place (minus wallMargin), before and
 *   after smoothing: it cannot leave the tunnel.
 * - In bends: if the walls would come between it and the Pokémon (checked at a few
 *   points of the line of sight), it comes nearer, quickly, and goes back slowly.
 * - Looking: at a point ahead of the Pokémon, with the tunnel's up, banked into the
 *   bends; smoothed so that nothing jerks.
 */
class UltraWarpCamera {
    /** Position in the tunnel's space, and how far along the path it is. */
    val position = Vector3d()
    var pathDistance = 0.0
        private set
    /** Minecraft's convention: looks along -Z, +Y up; the roll is in it. */
    val view = Quaternionf()
    val forward = Vector3f(0f, 0f, -1f)
    var distance = 0.0
        private set

    private var offsetX = 0.0
    private var offsetY = 0.0
    private var roll = 0.0
    private var started = false
    private val smoothedView = Quaternionf()

    private val t = Vector3d()
    private val u = Vector3d()
    private val r = Vector3d()
    private val axis = Vector3d()

    /**
     * size: room the Pokémon takes (its radius in the tunnel); rider: its centre;
     * steerX/Y: its offset from the axis; bank: the bends' banking, in degrees;
     * jolt: extra shake after a hit, in degrees.
     */
    fun update(
        tunnel: UltraWarpTunnel, config: UltraWarpClientConfig.Camera, dt: Double, time: Float, speed: Double,
        riderDistance: Double, rider: Vector3d, size: Double, steerX: Double, steerY: Double, bank: Double,
        jolt: Double = 0.0,
    ) {
        val height = config.height * size
        val wanted = (config.distance * size).coerceIn(config.minDistance.toDouble(), config.maxDistance.toDouble())

        // As far as wanted, nearer while the walls would hide the Pokémon.
        var candidate = wanted
        for (attempt in 0 until 6) {
            if (clearView(tunnel, config, riderDistance, candidate, rider, steerX, steerY, height)) break
            candidate = (candidate * 0.8).coerceAtLeast(config.minDistance * 0.5)
        }
        val follow = 1 - exp(-config.positionSmoothing * dt)
        distance = if (!started) candidate
            else if (candidate < distance) distance + (candidate - distance) * (1 - exp(-12 * dt))
            else distance + (candidate - distance) * (1 - exp(-1.5 * dt))

        // Place across the tunnel: a little towards the Pokémon, kept inside.
        pathDistance = riderDistance - distance
        val room = (tunnel.coreRadius(pathDistance) - config.wallMargin).coerceAtLeast(0.0)
        val targetX = steerX * config.follow
        val targetY = steerY * config.follow + height
        val (cx, cy) = inside(targetX, targetY, room)
        if (!started) {
            offsetX = cx
            offsetY = cy
        } else {
            offsetX += (cx - offsetX) * follow
            offsetY += (cy - offsetY) * follow
        }
        val (fx, fy) = inside(offsetX, offsetY, room)
        offsetX = fx
        offsetY = fy
        tunnel.frame(pathDistance, t, u, r)
        tunnel.position(pathDistance, axis)
        position.set(axis).fma(offsetX, r).fma(offsetY, u)

        // Looking ahead of the Pokémon, the tunnel's up banked into the bends.
        val ahead = tunnel.tangent(riderDistance)
        val look = Vector3d(rider).fma(config.lookAhead.toDouble(), ahead).sub(position)
        val forwardNow = Vector3f().set(look).normalize()
        val up = Vector3f().set(u)
        up.fma(-up.dot(forwardNow), forwardNow).normalize()
        val right = Vector3f(forwardNow).cross(up).normalize()
        val rollTarget = bank.coerceIn(-config.maxRoll.toDouble(), config.maxRoll.toDouble())
        roll = if (!started) rollTarget else roll + (rollTarget - roll) * (1 - exp(-config.smoothing * dt))
        val a = Math.toRadians(roll)
        val banked = Vector3f(up).mul(cos(a).toFloat()).add(right.mul(sin(a).toFloat()))
        val target = UltraWarpRideClient.cameraOrientation(forwardNow, banked, Quaternionf())
        if (!started) smoothedView.set(target)
        else smoothedView.slerp(target, (1 - exp(-config.smoothing * dt)).toFloat())
        started = true

        // A little shake, stronger with the speed.
        val shake = Math.toRadians(config.shake * min(1.0, speed / 40.0) + jolt).toFloat()
        view.set(smoothedView)
            .rotateY(shake * (sin(time * 13.1f) * 0.6f + sin(time * 7.3f) * 0.4f))
            .rotateX(shake * (sin(time * 11.7f) * 0.6f + sin(time * 5.9f) * 0.4f))
        forward.set(0f, 0f, -1f).rotate(view)
    }

    /**
     * Whether the line from the camera (at this distance behind) to the Pokémon stays
     * inside the walls: checked at five points along it, each against the tunnel's
     * walls where it is.
     */
    private fun clearView(
        tunnel: UltraWarpTunnel, config: UltraWarpClientConfig.Camera, riderDistance: Double, distance: Double,
        rider: Vector3d, steerX: Double, steerY: Double, height: Double,
    ): Boolean {
        val back = riderDistance - distance
        val room = (tunnel.coreRadius(back) - config.wallMargin).coerceAtLeast(0.0)
        val (ox, oy) = inside(steerX * config.follow, steerY * config.follow + height, room)
        tunnel.frame(back, t, u, r)
        val camera = tunnel.position(back, Vector3d()).fma(ox, r).fma(oy, u)
        val point = Vector3d()
        for (k in 1..5) {
            val f = k / 6.0
            point.set(camera).lerp(rider, f)
            val along = back + distance * f
            tunnel.frame(along, t, u, r)
            tunnel.position(along, axis)
            point.sub(axis)
            val across = point.fma(-point.dot(t), t).length()
            if (across > tunnel.coreRadius(along) - config.wallMargin * 0.5) return false
        }
        return true
    }

    /** An offset brought within room of the axis. */
    private fun inside(x: Double, y: Double, room: Double): Pair<Double, Double> {
        val length = sqrt(x * x + y * y)
        if (length <= room || length < 1e-9) return x to y
        val scale = room / length
        return x * scale to y * scale
    }
}
