package generations.gg.generations.core.generationscore.common.client.cinematic.camera

import org.joml.Matrix3f
import org.joml.Quaternionf
import org.joml.Vector3d
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The cinematics' maths, without Minecraft (tested off-game): curves, easings, smooth noise
 * and the camera's orientation. Everything is in the scene's local frame unless said: x to
 * the right, y up, z forward (the shrine's front).
 */
object CameraMath {
    fun clamp01(x: Double): Double = if (x < 0.0) 0.0 else if (x > 1.0) 1.0 else x

    /** Where t is between a and b, from 0 to 1 (clamped). */
    fun phase(t: Double, a: Double, b: Double): Double = if (b <= a) (if (t >= b) 1.0 else 0.0) else clamp01((t - a) / (b - a))

    fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t

    fun smooth(x: Double): Double {
        val t = clamp01(x)
        return t * t * (3.0 - 2.0 * t)
    }

    fun smoother(x: Double): Double {
        val t = clamp01(x)
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0)
    }

    fun easeInOutSine(x: Double): Double = -(cos(PI * clamp01(x)) - 1.0) / 2.0

    fun easeOutCubic(x: Double): Double {
        val t = 1.0 - clamp01(x)
        return 1.0 - t * t * t
    }

    fun easeInCubic(x: Double): Double {
        val t = clamp01(x)
        return t * t * t
    }

    fun easeInQuad(x: Double): Double {
        val t = clamp01(x)
        return t * t
    }

    fun easeOutQuad(x: Double): Double {
        val t = clamp01(x)
        return 1.0 - (1.0 - t) * (1.0 - t)
    }

    fun easeInOutCubic(x: Double): Double {
        val t = clamp01(x)
        return if (t < 0.5) 4.0 * t * t * t else 1.0 - (-2.0 * t + 2.0).let { it * it * it } / 2.0
    }

    /** A bump: 0 outside [a, d], 1 in [b, c], smooth ramps between. */
    fun window(t: Double, a: Double, b: Double, c: Double, d: Double): Double = smooth(phase(t, a, b)) * (1.0 - smooth(phase(t, c, d)))

    /** An exponential decay after a moment: 0 before it, 1 at it, then down with that time constant. */
    fun decay(t: Double, at: Double, tau: Double): Double = if (t < at) 0.0 else exp(-(t - at) / tau)

    /** A sharp attack then a decay: a pulse rising in `attack` seconds. */
    fun pulse(t: Double, at: Double, attack: Double, tau: Double): Double =
        if (t < at - attack) 0.0 else if (t < at) smooth((t - (at - attack)) / attack) else exp(-(t - at) / tau)

    /** Catmull-Rom through the points (centripetal enough for camera keys: uniform). u from 0 to 1 over the whole curve. */
    fun catmullRom(points: List<Vector3d>, u: Double, out: Vector3d): Vector3d {
        if (points.size == 1) return out.set(points[0])
        val segments = points.size - 1
        val x = clamp01(u) * segments
        val i = min(floor(x).toInt(), segments - 1)
        val t = x - i
        val p0 = points[max(i - 1, 0)]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points[min(i + 2, segments)]
        val t2 = t * t
        val t3 = t2 * t
        fun c(a: Double, b: Double, cc: Double, d: Double) = 0.5 * (2.0 * b + (-a + cc) * t + (2.0 * a - 5.0 * b + 4.0 * cc - d) * t2 + (-a + 3.0 * b - 3.0 * cc + d) * t3)
        return out.set(c(p0.x, p1.x, p2.x, p3.x), c(p0.y, p1.y, p2.y, p3.y), c(p0.z, p1.z, p2.z, p3.z))
    }

    /** Cubic Bézier. */
    fun bezier(p0: Vector3d, p1: Vector3d, p2: Vector3d, p3: Vector3d, u: Double, out: Vector3d): Vector3d {
        val t = clamp01(u)
        val m = 1.0 - t
        val a = m * m * m
        val b = 3.0 * m * m * t
        val c = 3.0 * m * t * t
        val d = t * t * t
        return out.set(
            a * p0.x + b * p1.x + c * p2.x + d * p3.x,
            a * p0.y + b * p1.y + c * p2.y + d * p3.y,
            a * p0.z + b * p1.z + c * p2.z + d * p3.z,
        )
    }

    /** A point on a circle around the vertical axis: angle in degrees from +z toward +x. */
    fun orbit(cx: Double, cz: Double, radius: Double, angleDeg: Double, y: Double, out: Vector3d): Vector3d {
        val a = Math.toRadians(angleDeg)
        return out.set(cx + radius * sin(a), y, cz + radius * cos(a))
    }

    private fun hash(i: Int, seed: Int): Double {
        var h = i * 374761393 + seed * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0x7fffffff) / 2147483647.0
    }

    /** Smooth value noise from -1 to 1 (continuous, about one change per unit of x). */
    fun noise(x: Double, seed: Int): Double {
        val i = floor(x).toInt()
        val f = x - i
        val u = f * f * (3.0 - 2.0 * f)
        return (lerp(hash(i, seed), hash(i + 1, seed), u)) * 2.0 - 1.0
    }

    /** Two octaves of noise, from about -1 to 1. */
    fun fbm(x: Double, seed: Int): Double = (noise(x, seed) * 0.7 + noise(x * 2.3 + 17.1, seed + 31) * 0.3)

    /** A deterministic random number in [0, 1) for (index, salt). */
    fun random(index: Int, salt: Int): Double = hash(index, salt * 7919 + 13)

    /**
     * The camera's rotation in Minecraft's convention (-Z looks forward, +Y up), looking from
     * `from` to `to`, turned by `rollDeg` around the view. Straight up or down keeps a sane up.
     */
    fun lookRotation(fx: Double, fy: Double, fz: Double, rollDeg: Double, out: Quaternionf): Quaternionf {
        val len = sqrt(fx * fx + fy * fy + fz * fz)
        if (len < 1e-6) return out.identity()
        val forward = Vector3f((fx / len).toFloat(), (fy / len).toFloat(), (fz / len).toFloat())
        var up = Vector3f(0f, 1f, 0f)
        if (abs(forward.y) > 0.999f) up = Vector3f(0f, 0f, if (forward.y > 0f) -1f else 1f)
        val z = Vector3f(forward).negate()
        val x = Vector3f(up).cross(z).normalize()
        val y = Vector3f(z).cross(x).normalize()
        out.setFromNormalized(Matrix3f(x, y, z))
        if (rollDeg != 0.0) out.rotateZ(Math.toRadians(rollDeg).toFloat())
        return out
    }
}

/** A shot's camera: where it is, what it looks at, its roll (degrees) and field of view (degrees). */
class CamPose {
    val pos = Vector3d()
    val target = Vector3d()
    var roll = 0.0
    var fov = 70.0

    fun set(other: CamPose): CamPose {
        pos.set(other.pos)
        target.set(other.target)
        roll = other.roll
        fov = other.fov
        return this
    }
}

/** The camera as drawn: position, rotation (Minecraft's convention), field of view. */
class CamFrame {
    val pos = Vector3d()
    val rotation = Quaternionf()
    var fov = 70.0

    /** What the shot looks at (for the terrain's ray), mixed like the positions. */
    val focus = Vector3d()

    fun set(pose: CamPose): CamFrame {
        pos.set(pose.pos)
        focus.set(pose.target)
        CameraMath.lookRotation(pose.target.x - pose.pos.x, pose.target.y - pose.pos.y, pose.target.z - pose.pos.z, pose.roll, rotation)
        fov = pose.fov
        return this
    }

    fun set(other: CamFrame): CamFrame {
        pos.set(other.pos)
        focus.set(other.focus)
        rotation.set(other.rotation)
        fov = other.fov
        return this
    }

    /** Toward the other frame: positions and fields of view mixed, rotations slerped. */
    fun mix(other: CamFrame, w: Double): CamFrame {
        if (w <= 0.0) return this
        if (w >= 1.0) return set(other)
        pos.lerp(other.pos, w)
        focus.lerp(other.focus, w)
        rotation.slerp(other.rotation, w.toFloat())
        fov += (other.fov - fov) * w
        return this
    }

    /** The view's forward direction (-Z turned). */
    fun forward(out: Vector3f): Vector3f = out.set(0f, 0f, -1f).rotate(rotation)

    fun isFinite(): Boolean =
        pos.x.isFinite() && pos.y.isFinite() && pos.z.isFinite() && fov.isFinite() &&
            rotation.x.isFinite() && rotation.y.isFinite() && rotation.z.isFinite() && rotation.w.isFinite()
}

/** Reduced camera motion (accessibility): no shake, less roll and FOV punch, slower orbits. */
class CameraMotion(val reduced: Boolean) {
    val roll: Double = if (reduced) 0.3 else 1.0
    val punch: Double = if (reduced) 0.3 else 1.0
    val orbit: Double = if (reduced) 0.6 else 1.0
    val shake: Double = if (reduced) 0.0 else 1.0
}
