package generations.gg.generations.core.generationscore.common.world.ultraspace.ride

import org.joml.Vector3d
import java.util.SplittableRandom
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * The path of an Ultra Warp Ride tunnel, the same wherever it is built from the same
 * seed and settings: the client draws it, the server will place the gameplay of later
 * phases on it. No world blocks nor chunks: positions in a space of its own, starting
 * at the origin towards +Z.
 *
 * Points every segmentLength blocks, each turning a little from the last towards a
 * bend picked at random every curveChangeSegments points (smoothed, slope bounded),
 * joined by a Catmull-Rom curve, so that the distance along the path is about the
 * distance travelled. Points are made once, in order, as far as asked.
 */
class UltraWarpTunnel(seed: Long, val settings: UltraWarpRideSettings) {
    private val random = SplittableRandom(seed)
    private val points = ArrayList<Vector3d>()
    private val length = settings.segmentLength.toDouble().coerceAtLeast(1.0)
    private val phases = DoubleArray(4) { random.nextDouble() * Math.PI * 2 }

    private var yaw = 0.0
    private var pitch = 0.0
    private var yawRate = 0.0
    private var pitchRate = 0.0
    private var yawTarget = 0.0
    private var pitchTarget = 0.0
    private var untilChange = 0

    init {
        points.add(Vector3d())
        // A straight start, the bends come after.
        repeat(3) { points.add(Vector3d(0.0, 0.0, length * points.size)) }
    }

    private fun step() {
        val curve = settings.curveIntensity.toDouble()
        val maxPitch = settings.maxPitch.toDouble()
        if (untilChange <= 0) {
            yawTarget = (random.nextDouble() * 2 - 1) * curve
            pitchTarget = (random.nextDouble() * 2 - 1) * curve * 0.6
            untilChange = settings.curveChangeSegments.coerceAtLeast(1)
        }
        untilChange--
        // Back towards level before the slope gets too steep.
        if (pitch > maxPitch * 0.6) pitchTarget = -abs(pitchTarget) - 0.5
        else if (pitch < -maxPitch * 0.6) pitchTarget = abs(pitchTarget) + 0.5
        yawRate += (yawTarget - yawRate) * 0.3
        pitchRate += (pitchTarget - pitchRate) * 0.3
        yaw += yawRate
        pitch = (pitch + pitchRate).coerceIn(-maxPitch, maxPitch)
        val y = Math.toRadians(yaw)
        val p = Math.toRadians(pitch)
        val last = points.last()
        points.add(Vector3d(last.x - sin(y) * cos(p) * length, last.y + sin(p) * length, last.z + cos(y) * cos(p) * length))
    }

    /** Point i of the path; before the start, the line of the first segment. */
    private fun point(i: Int): Vector3d {
        if (i < 0) {
            val first = points[0]
            val second = points[1]
            return Vector3d(second).sub(first).mul(i.toDouble()).add(first)
        }
        while (points.size <= i) step()
        return points[i]
    }

    /** Position at this distance along the path. */
    fun position(distance: Double, out: Vector3d = Vector3d()): Vector3d {
        val u = distance / length
        val i = floor(u).toInt()
        val t = u - i
        val p0 = point(i - 1)
        val p1 = point(i)
        val p2 = point(i + 1)
        val p3 = point(i + 2)
        val t2 = t * t
        val t3 = t2 * t
        fun axis(a: Double, b: Double, c: Double, d: Double) =
            0.5 * (2 * b + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t2 + (-a + 3 * b - 3 * c + d) * t3)
        return out.set(axis(p0.x, p1.x, p2.x, p3.x), axis(p0.y, p1.y, p2.y, p3.y), axis(p0.z, p1.z, p2.z, p3.z))
    }

    /** Direction of the path at this distance (unit length). */
    fun tangent(distance: Double, out: Vector3d = Vector3d()): Vector3d {
        val u = distance / length
        val i = floor(u).toInt()
        val t = u - i
        val p0 = point(i - 1)
        val p1 = point(i)
        val p2 = point(i + 1)
        val p3 = point(i + 2)
        fun axis(a: Double, b: Double, c: Double, d: Double) =
            0.5 * ((-a + c) + 2 * (2 * a - 5 * b + 4 * c - d) * t + 3 * (-a + 3 * b - 3 * c + d) * t * t)
        out.set(axis(p0.x, p1.x, p2.x, p3.x), axis(p0.y, p1.y, p2.y, p3.y), axis(p0.z, p1.z, p2.z, p3.z))
        return if (out.lengthSquared() < 1e-9) out.set(0.0, 0.0, 1.0) else out.normalize()
    }

    /**
     * The tunnel's frame at this distance: up is the world's up made square to the path
     * (the slope never gets near vertical, so it never flips), right = tangent × up.
     */
    fun frame(distance: Double, tangent: Vector3d, up: Vector3d, right: Vector3d) {
        tangent(distance, tangent)
        up.set(0.0, 1.0, 0.0).fma(-tangent.y, tangent).normalize()
        tangent.cross(up, right).normalize()
    }

    /**
     * How fast the path turns sideways here, per block: positive towards the right.
     * Banks the rider and the camera into the bends.
     */
    fun turn(distance: Double): Double {
        val before = tangent(distance - 3.0)
        val after = tangent(distance + 3.0)
        val tangent = Vector3d()
        val up = Vector3d()
        val right = Vector3d()
        frame(distance, tangent, up, right)
        return after.sub(before).dot(right) / 6.0
    }

    // StrictMath: the same result on every machine, so that the server and the client
    // place the course's objects (UltraWarpCourse) the same way.
    private fun swell(distance: Double): Double =
        0.6 * StrictMath.sin(distance * 0.021 + phases[0]) + 0.4 * StrictMath.sin(distance * 0.057 + phases[1])

    /** Radius of the tunnel at this distance and angle around it: it swells and narrows. */
    fun radius(distance: Double, angle: Double): Double {
        val ripple = 0.06 * sin(angle * 3 + distance * 0.04 + phases[2]) + 0.03 * sin(angle * 5 - distance * 0.09 + phases[3])
        return settings.radius * (1 + settings.radiusVariation * swell(distance)) * (1 + ripple)
    }

    /**
     * The narrowest the walls get at this distance, whatever the angle (the ripples at
     * their lowest): what fits inside this circle never goes through a wall here.
     */
    fun coreRadius(distance: Double): Double = settings.radius * (1 + settings.radiusVariation * swell(distance)) * (1 - RIPPLE)

    companion object {
        /** Largest ripple of the walls around a ring, as a part of the radius. */
        const val RIPPLE = 0.09

        /** The narrowest the tunnel ever gets with these settings. */
        fun minRadius(settings: UltraWarpRideSettings): Double = settings.radius * (1 - settings.radiusVariation) * (1 - RIPPLE)
    }
}
