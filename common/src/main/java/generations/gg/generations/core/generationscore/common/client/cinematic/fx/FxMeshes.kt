package generations.gg.generations.core.generationscore.common.client.cinematic.fx

import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Procedural meshes for the cinematics (CINEMATIQUES-PLAN.md § 2.4), written into a frame's
 * buffer at camera-relative positions: rings, discs, cylinders and cones, tori, spheres,
 * crystals, billboards, beams, ribbons, spirals and lightning. Vertical things stand on the
 * world's Y axis.
 */
object FxMeshes {
    const val TAU = (2.0 * PI).toFloat()

    fun argb(r: Float, g: Float, b: Float, a: Float): Int {
        fun c(x: Float) = (x.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        return (c(a) shl 24) or (c(r) shl 16) or (c(g) shl 8) or c(b)
    }

    fun withAlpha(rgb: Int, a: Float): Int = (rgb and 0xFFFFFF) or ((a.coerceIn(0f, 1f) * 255f + 0.5f).toInt() shl 24)

    /**
     * A flat ring around a vertical axis (rIn 0: a disc). u goes across the band (inner to outer),
     * v around it (vScale turns, from vOffset).
     */
    fun ring(
        buffer: FxBuffer, cx: Float, cy: Float, cz: Float, rIn: Float, rOut: Float, segments: Int,
        inner: Int, outer: Int, vScale: Float = 1f, vOffset: Float = 0f,
    ) {
        if (rOut <= 0f) return
        for (s in 0 until segments) {
            val a0 = s.toFloat() / segments
            val a1 = (s + 1).toFloat() / segments
            val c0 = cos(a0 * TAU)
            val s0 = sin(a0 * TAU)
            val c1 = cos(a1 * TAU)
            val s1 = sin(a1 * TAU)
            val v0 = a0 * vScale + vOffset
            val v1 = a1 * vScale + vOffset
            buffer.vertex(cx + s0 * rIn, cy, cz + c0 * rIn, 0f, v0, inner)
            buffer.vertex(cx + s0 * rOut, cy, cz + c0 * rOut, 1f, v0, outer)
            buffer.vertex(cx + s1 * rOut, cy, cz + c1 * rOut, 1f, v1, outer)
            buffer.vertex(cx + s1 * rIn, cy, cz + c1 * rIn, 0f, v1, inner)
        }
    }

    /**
     * The side of a cylinder or cone around a vertical axis, from y0 (radius r0) to y1 (radius r1).
     * u around (uScale turns, uOffset), v along the height (vScale); twist turns the top by
     * that many turns (spirals). Normals point outward (for the rim).
     */
    fun cylinder(
        buffer: FxBuffer, cx: Float, cy: Float, cz: Float, y0: Float, y1: Float, r0: Float, r1: Float,
        segments: Int, rings: Int, bottom: Int, top: Int,
        uScale: Float = 1f, uOffset: Float = 0f, vScale: Float = 1f, vOffset: Float = 0f, twist: Float = 0f,
    ) {
        for (h in 0 until rings) {
            val t0 = h.toFloat() / rings
            val t1 = (h + 1).toFloat() / rings
            val ya = y0 + (y1 - y0) * t0
            val yb = y0 + (y1 - y0) * t1
            val ra = r0 + (r1 - r0) * t0
            val rb = r0 + (r1 - r0) * t1
            val ca = lerpColour(bottom, top, t0)
            val cb = lerpColour(bottom, top, t1)
            val slope = (r0 - r1) / maxOf(1e-3f, y1 - y0)
            for (s in 0 until segments) {
                val a0 = s.toFloat() / segments
                val a1 = (s + 1).toFloat() / segments
                fun corner(a: Float, t: Float, y: Float, r: Float, colour: Int) {
                    val angle = (a + twist * t) * TAU
                    val sx = sin(angle)
                    val cz2 = cos(angle)
                    val len = sqrt(1f + slope * slope)
                    buffer.vertex(cx + sx * r, cy + y, cz + cz2 * r, a * uScale + uOffset + twist * t * uScale, t * vScale + vOffset, colour, sx / len, slope / len, cz2 / len)
                }
                corner(a0, t0, ya, ra, ca)
                corner(a0, t1, yb, rb, cb)
                corner(a1, t1, yb, rb, cb)
                corner(a1, t0, ya, ra, ca)
            }
        }
    }

    /** A torus lying flat (major radius R, tube radius r). */
    fun torus(buffer: FxBuffer, cx: Float, cy: Float, cz: Float, major: Float, minor: Float, segments: Int, sides: Int, colour: Int, uOffset: Float = 0f) {
        if (major <= 0f || minor <= 0f) return
        for (s in 0 until segments) for (k in 0 until sides) {
            fun corner(si: Int, ki: Int) {
                val a = si.toFloat() / segments * TAU
                val b = ki.toFloat() / sides * TAU
                val nx = cos(b) * sin(a)
                val ny = sin(b)
                val nz = cos(b) * cos(a)
                val ring = major + minor * cos(b)
                buffer.vertex(cx + ring * sin(a), cy + minor * sin(b), cz + ring * cos(a), si.toFloat() / segments * 4f + uOffset, ki.toFloat() / sides, colour, nx, ny, nz)
            }
            corner(s, k)
            corner(s, k + 1)
            corner(s + 1, k + 1)
            corner(s + 1, k)
        }
    }

    private val sphere: FloatArray by lazy { icosphere(2) }

    /** A sphere (subdivided icosahedron, 320 faces). Normals outward. */
    fun sphere(buffer: FxBuffer, cx: Float, cy: Float, cz: Float, radius: Float, colour: Int, uOffset: Float = 0f) {
        if (radius <= 0f) return
        val v = sphere
        var i = 0
        while (i < v.size) {
            for (k in 0 until 4) {
                val j = i + (if (k == 3) 2 else k) * 3
                val x = v[j]
                val y = v[j + 1]
                val z = v[j + 2]
                val u = (kotlin.math.atan2(x, z) / TAU + 0.5f) * 3f + uOffset
                val w = y * 0.5f + 0.5f
                buffer.vertex(cx + x * radius, cy + y * radius, cz + z * radius, u, w, colour, x, y, z)
            }
            i += 9
        }
    }

    private fun icosphere(levels: Int): FloatArray {
        val t = ((1.0 + sqrt(5.0)) / 2.0).toFloat()
        val verts = mutableListOf(
            Vector3f(-1f, t, 0f), Vector3f(1f, t, 0f), Vector3f(-1f, -t, 0f), Vector3f(1f, -t, 0f),
            Vector3f(0f, -1f, t), Vector3f(0f, 1f, t), Vector3f(0f, -1f, -t), Vector3f(0f, 1f, -t),
            Vector3f(t, 0f, -1f), Vector3f(t, 0f, 1f), Vector3f(-t, 0f, -1f), Vector3f(-t, 0f, 1f),
        ).onEach { it.normalize() }
        var faces = listOf(
            intArrayOf(0, 11, 5), intArrayOf(0, 5, 1), intArrayOf(0, 1, 7), intArrayOf(0, 7, 10), intArrayOf(0, 10, 11),
            intArrayOf(1, 5, 9), intArrayOf(5, 11, 4), intArrayOf(11, 10, 2), intArrayOf(10, 7, 6), intArrayOf(7, 1, 8),
            intArrayOf(3, 9, 4), intArrayOf(3, 4, 2), intArrayOf(3, 2, 6), intArrayOf(3, 6, 8), intArrayOf(3, 8, 9),
            intArrayOf(4, 9, 5), intArrayOf(2, 4, 11), intArrayOf(6, 2, 10), intArrayOf(8, 6, 7), intArrayOf(9, 8, 1),
        )
        repeat(levels) {
            val cache = HashMap<Long, Int>()
            fun middle(a: Int, b: Int): Int {
                val key = minOf(a, b).toLong() shl 32 or maxOf(a, b).toLong()
                return cache.getOrPut(key) {
                    verts += Vector3f(verts[a]).add(verts[b]).normalize()
                    verts.size - 1
                }
            }
            faces = faces.flatMap { f ->
                val ab = middle(f[0], f[1])
                val bc = middle(f[1], f[2])
                val ca = middle(f[2], f[0])
                listOf(intArrayOf(f[0], ab, ca), intArrayOf(f[1], bc, ab), intArrayOf(f[2], ca, bc), intArrayOf(ab, bc, ca))
            }
        }
        val out = FloatArray(faces.size * 9)
        var i = 0
        for (f in faces) for (k in f) {
            out[i++] = verts[k].x
            out[i++] = verts[k].y
            out[i++] = verts[k].z
        }
        return out
    }

    private val crystalScratch = Array(8) { Vector3f() }
    private val normalScratch = Vector3f()
    private val edgeA = Vector3f()
    private val edgeB = Vector3f()

    /**
     * An ice crystal: a hexagonal bipyramid with flat facets (the top longer), turned by
     * `rotation` around its base centre. Its colour's alpha dissolves it (the crystal shader).
     */
    fun crystal(buffer: FxBuffer, cx: Float, cy: Float, cz: Float, rotation: Quaternionf, radius: Float, height: Float, colour: Int) {
        if (radius <= 0f || height <= 0f) return
        val p = crystalScratch
        for (k in 0 until 6) {
            val a = k / 6f * TAU
            p[k].set(sin(a) * radius, height * 0.3f, cos(a) * radius).rotate(rotation)
        }
        p[6].set(0f, height, 0f).rotate(rotation)
        p[7].set(0f, -height * 0.15f, 0f).rotate(rotation)
        for (k in 0 until 6) {
            val a = p[k]
            val b = p[(k + 1) % 6]
            facet(buffer, cx, cy, cz, a, b, p[6], colour, k)
            facet(buffer, cx, cy, cz, b, a, p[7], colour, k + 6)
        }
    }

    private fun facet(buffer: FxBuffer, cx: Float, cy: Float, cz: Float, a: Vector3f, b: Vector3f, apex: Vector3f, colour: Int, index: Int) {
        edgeA.set(b).sub(a)
        edgeB.set(apex).sub(a)
        val n = normalScratch.set(edgeA).cross(edgeB)
        if (n.lengthSquared() < 1e-12f) return
        n.normalize()
        val u = index * 0.37f
        buffer.vertex(cx + a.x, cy + a.y, cz + a.z, u, 0f, colour, n.x, n.y, n.z)
        buffer.vertex(cx + b.x, cy + b.y, cz + b.z, u + 0.3f, 0f, colour, n.x, n.y, n.z)
        buffer.vertex(cx + apex.x, cy + apex.y, cz + apex.z, u + 0.15f, 0.6f, colour, n.x, n.y, n.z)
        buffer.vertex(cx + apex.x, cy + apex.y, cz + apex.z, u + 0.15f, 0.6f, colour, n.x, n.y, n.z)
    }

    /**
     * A square facing the camera (right and up: the camera's axes), turned by angle (radians).
     * Its normal faces the camera.
     */
    fun billboard(buffer: FxBuffer, x: Float, y: Float, z: Float, size: Float, angle: Float, colour: Int, right: Vector3f, up: Vector3f, stretch: Float = 1f) {
        val c = cos(angle) * size
        val s = sin(angle) * size
        val rx = right.x * c + up.x * s
        val ry = right.y * c + up.y * s
        val rz = right.z * c + up.z * s
        val ux = (up.x * c - right.x * s) * stretch
        val uy = (up.y * c - right.y * s) * stretch
        val uz = (up.z * c - right.z * s) * stretch
        val len = sqrt(x * x + y * y + z * z).coerceAtLeast(1e-3f)
        val nx = -x / len
        val ny = -y / len
        val nz = -z / len
        buffer.vertex(x - rx - ux, y - ry - uy, z - rz - uz, 0f, 1f, colour, nx, ny, nz)
        buffer.vertex(x + rx - ux, y + ry - uy, z + rz - uz, 1f, 1f, colour, nx, ny, nz)
        buffer.vertex(x + rx + ux, y + ry + uy, z + rz + uz, 1f, 0f, colour, nx, ny, nz)
        buffer.vertex(x - rx + ux, y - ry + uy, z - rz + uz, 0f, 0f, colour, nx, ny, nz)
    }

    /** A flat square lying on the ground (a decal), turned by angle. */
    fun flat(buffer: FxBuffer, x: Float, y: Float, z: Float, size: Float, angle: Float, colour: Int, uv: Float = 1f) {
        val c = cos(angle) * size
        val s = sin(angle) * size
        buffer.vertex(x - c + s, y, z - s - c, 0f, 0f, colour)
        buffer.vertex(x + c + s, y, z + s - c, uv, 0f, colour)
        buffer.vertex(x + c - s, y, z + s + c, uv, uv, colour)
        buffer.vertex(x - c - s, y, z - s + c, 0f, uv, colour)
    }

    private val side = Vector3f()

    /**
     * A strip between two points, turned toward the camera (at the origin: positions are
     * camera-relative). u goes along it, v across.
     */
    fun beam(buffer: FxBuffer, ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, width0: Float, width1: Float, colour0: Int, colour1: Int, u0: Float = 0f, u1: Float = 1f) {
        val dx = bx - ax
        val dy = by - ay
        val dz = bz - az
        // Across: the direction × the view (from the middle).
        val mx = (ax + bx) * 0.5f
        val my = (ay + by) * 0.5f
        val mz = (az + bz) * 0.5f
        side.set(dy * mz - dz * my, dz * mx - dx * mz, dx * my - dy * mx)
        if (side.lengthSquared() < 1e-10f) return
        side.normalize()
        val sx0 = side.x * width0
        val sy0 = side.y * width0
        val sz0 = side.z * width0
        val sx1 = side.x * width1
        val sy1 = side.y * width1
        val sz1 = side.z * width1
        buffer.vertex(ax - sx0, ay - sy0, az - sz0, u0, 0f, colour0)
        buffer.vertex(ax + sx0, ay + sy0, az + sz0, u0, 1f, colour0)
        buffer.vertex(bx + sx1, by + sy1, bz + sz1, u1, 1f, colour1)
        buffer.vertex(bx - sx1, by - sy1, bz - sz1, u1, 0f, colour1)
    }

    /**
     * A ribbon through points (x, y, z triples, camera-relative), facing the camera; width and
     * colour along it from start to end.
     */
    fun ribbon(buffer: FxBuffer, points: FloatArray, count: Int, width0: Float, width1: Float, colour0: Int, colour1: Int) {
        for (i in 0 until count - 1) {
            val t0 = i.toFloat() / (count - 1)
            val t1 = (i + 1).toFloat() / (count - 1)
            beam(
                buffer, points[i * 3], points[i * 3 + 1], points[i * 3 + 2], points[i * 3 + 3], points[i * 3 + 4], points[i * 3 + 5],
                width0 + (width1 - width0) * t0, width0 + (width1 - width0) * t1, lerpColour(colour0, colour1, t0), lerpColour(colour0, colour1, t1), t0, t1,
            )
        }
    }

    private val helix = FloatArray(3 * 65)

    /** A spiral ribbon around a vertical axis, from (y0, r0) to (y1, r1), `turns` turns from `phase` (turns). */
    fun spiral(buffer: FxBuffer, cx: Float, cy: Float, cz: Float, y0: Float, y1: Float, r0: Float, r1: Float, turns: Float, phase: Float, width: Float, colour0: Int, colour1: Int, steps: Int = 48) {
        val n = steps.coerceIn(2, 64) + 1
        for (i in 0 until n) {
            val t = i.toFloat() / (n - 1)
            val a = (phase + turns * t) * TAU
            val r = r0 + (r1 - r0) * t
            helix[i * 3] = cx + sin(a) * r
            helix[i * 3 + 1] = cy + y0 + (y1 - y0) * t
            helix[i * 3 + 2] = cz + cos(a) * r
        }
        ribbon(buffer, helix, n, width, width * 0.6f, colour0, colour1)
    }

    private val boltPoints = FloatArray(3 * 65)

    /**
     * Lightning from a to b (camera-relative): the midpoint pushed aside, again and again
     * (5 levels, 33 points), with that seed; spread: how far aside (a fraction of the length).
     */
    fun bolt(buffer: FxBuffer, ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, seed: Int, spread: Float, width: Float, colour: Int) {
        val n = 33
        boltPoints[0] = ax; boltPoints[1] = ay; boltPoints[2] = az
        boltPoints[(n - 1) * 3] = bx; boltPoints[(n - 1) * 3 + 1] = by; boltPoints[(n - 1) * 3 + 2] = bz
        val length = sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay) + (bz - az) * (bz - az))
        var step = n - 1
        var amount = spread * length
        var level = 0
        while (step > 1) {
            val half = step / 2
            var i = 0
            while (i < n - 1) {
                val m = i + half
                val j = i + step
                for (k in 0 until 3) {
                    val mid = (boltPoints[i * 3 + k] + boltPoints[j * 3 + k]) * 0.5f
                    boltPoints[m * 3 + k] = mid + (rand(seed, level * 997 + m * 3 + k) - 0.5f) * amount
                }
                i += step
            }
            step = half
            amount *= 0.55f
            level++
        }
        ribbon(buffer, boltPoints, n, width, width * 0.4f, colour, withAlpha(colour, ((colour ushr 24) / 255f) * 0.6f))
    }

    /** A point of the last bolt drawn (0 to 32, camera-relative): where a branch starts. */
    fun boltPoint(index: Int, out: Vector3f): Vector3f {
        val i = index.coerceIn(0, 32) * 3
        return out.set(boltPoints[i], boltPoints[i + 1], boltPoints[i + 2])
    }

    fun rand(seed: Int, index: Int): Float {
        var h = seed * 374761393 + index * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xffffff) / 16777215f
    }

    fun lerpColour(a: Int, b: Int, t: Float): Int {
        if (a == b) return a
        fun ch(shift: Int): Int {
            val x = (a ushr shift) and 0xFF
            val y = (b ushr shift) and 0xFF
            return ((x + (y - x) * t).toInt().coerceIn(0, 255)) shl shift
        }
        return ch(24) or ch(16) or ch(8) or ch(0)
    }
}
