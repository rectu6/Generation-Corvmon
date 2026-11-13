package generations.gg.generations.core.generationscore.common.client.ultraspace

import com.mojang.blaze3d.vertex.VertexConsumer
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.world.ultraspace.ride.UltraWarpCourse
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import org.joml.Vector3d
import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/**
 * The course's objects in the tunnel, where the server and the client both place
 * them (UltraWarpCourse), drawn glowing (additive) from just behind the camera to the
 * objects' view distance: orange orbs (a warm core in a ring of light), blue electric
 * orbs (a cold core, a ring, flickering bolts), and the bursts of what was taken or
 * touched. What was taken or touched is no longer drawn.
 */
object UltraWarpObjectRenderer {
    private val GLOW: ResourceLocation = GenerationsCore.id("textures/ultra_space/glow.png")
    private val RING: ResourceLocation = GenerationsCore.id("textures/ultra_space/ring.png")
    private val BOLT: ResourceLocation = GenerationsCore.id("textures/ultra_space/bolt.png")
    private val CRYSTAL: ResourceLocation = GenerationsCore.id("textures/ultra_space/crystal.png")
    private val VORTEX: ResourceLocation = GenerationsCore.id("textures/ultra_space/vortex.png")

    private const val ORANGE = 0xFFA030
    private const val ORANGE_RING = 0xFFB850
    private const val BLUE = 0x4CC0FF
    private const val BLUE_RING = 0xB8ECFF
    private const val BOLT_COLOUR = 0xD8F4FF
    private const val ARC_COLOUR = 0x6A9CFF
    private const val CRYSTAL_COLOUR = 0xC8A8FF
    private const val PULL_COLOUR = 0xA060FF
    private const val PUSH_COLOUR = 0x40FFD0

    /** An object of this frame, where it is from the camera, and how far it has faded in. */
    private class Seen(val item: UltraWarpCourse.Item, val position: Vector3f, val fade: Float)

    private val t = Vector3d()
    private val u = Vector3d()
    private val r = Vector3d()

    /** The objects of this frame (not taken nor touched), from just behind the camera to the view distance. */
    private fun visible(ride: UltraWarpRideClient.Ride): List<Seen> {
        val objects = ride.settings.config.objects
        val view = min(objects.viewDistance.toDouble(), ride.config.visual.viewDistance.toDouble())
        val items = ArrayList<UltraWarpCourse.Item>()
        ride.course.items(ride.cameraPath - 4.0, ride.cameraPath + view, items)
        val seen = ArrayList<Seen>()
        for (item in items) {
            if (item.id in ride.consumed) continue
            val ahead = item.distance - ride.cameraPath
            if (ahead < -4.0 || ahead > view) continue
            seen.add(Seen(item, place(ride, item.distance, item.x, item.y), 1f - smoothstep(view.toFloat() * 0.7f, view.toFloat(), ahead.toFloat())))
        }
        return seen
    }

    /**
     * Debris: violet crystals (a double pyramid of 5 to 7 sides, its own shape from the
     * seed), turning slowly; solid, drawn before the Pokémon.
     */
    fun renderOpaque(ride: UltraWarpRideClient.Ride, buffers: MultiBufferSource.BufferSource) {
        val debris = visible(ride).filter { it.item.kind == UltraWarpCourse.Kind.DEBRIS }
        if (debris.isEmpty()) return
        val time = ride.seconds
        val type = RenderType.entityCutoutNoCull(CRYSTAL)
        val buffer = buffers.getBuffer(type)
        for (s in debris) crystal(buffer, s, time)
        buffers.endBatch(type)
    }

    private fun crystal(buffer: VertexConsumer, s: Seen, time: Float) {
        val look = s.item.look
        val sides = 5 + (hash(look, 1) * 3).toInt()
        val radius = s.item.radius.toFloat()
        val top = Vector3f(0f, radius * (1.1f + 0.6f * hash(look, 2).toFloat()), 0f)
        val bottom = Vector3f(0f, -radius * (0.7f + 0.4f * hash(look, 3).toFloat()), 0f)
        val ring = List(sides) { i ->
            val angle = (i + hash(look, 10 + i).toFloat() * 0.4f) * 2f * Math.PI.toFloat() / sides
            val r = radius * (0.6f + 0.4f * hash(look, 20 + i).toFloat())
            Vector3f(cos(angle) * r, radius * 0.15f * (hash(look, 30 + i).toFloat() - 0.5f), sin(angle) * r)
        }
        val spin = org.joml.Quaternionf()
            .rotateAxis(time * (0.4f + hash(look, 4).toFloat()), Vector3f(hash(look, 5).toFloat() - 0.5f, 1f, hash(look, 6).toFloat() - 0.5f).normalize())
        val colour = shade(CRYSTAL_COLOUR, 0.6f + 0.4f * s.fade)
        fun face(a: Vector3f, b: Vector3f, c: Vector3f) {
            val pa = Vector3f(a).rotate(spin).add(s.position)
            val pb = Vector3f(b).rotate(spin).add(s.position)
            val pc = Vector3f(c).rotate(spin).add(s.position)
            val normal = Vector3f(pb).sub(pa).cross(Vector3f(pc).sub(pa)).normalize()
            for ((p, uv) in listOf(pa to (0f to 0f), pb to (1f to 0f), pc to (0.5f to 1f), pc to (0.5f to 1f))) {
                buffer.addVertex(p.x, p.y, p.z, colour, uv.first, uv.second, OverlayTexture.NO_OVERLAY, LightTexture.FULL_BRIGHT, normal.x, normal.y, normal.z)
            }
        }
        for (i in 0 until sides) {
            val a = ring[i]
            val b = ring[(i + 1) % sides]
            face(top, b, a)
            face(bottom, a, b)
        }
    }

    fun render(ride: UltraWarpRideClient.Ride, buffers: MultiBufferSource.BufferSource) {
        val objects = ride.settings.config.objects
        val view = min(objects.viewDistance.toDouble(), ride.config.visual.viewDistance.toDouble())
        val items = ArrayList<UltraWarpCourse.Item>()
        ride.course.items(ride.cameraPath - 4.0, ride.cameraPath + view, items)
        val seen = ArrayList<Seen>()
        for (item in items) {
            if (item.id in ride.consumed) continue
            val ahead = item.distance - ride.cameraPath
            if (ahead < -4.0 || ahead > view) continue
            seen.add(Seen(item, place(ride, item.distance, item.x, item.y), 1f - smoothstep(view.toFloat() * 0.7f, view.toFloat(), ahead.toFloat())))
        }
        val right = Vector3f(1f, 0f, 0f).rotate(ride.view)
        val up = Vector3f(0f, 1f, 0f).rotate(ride.view)
        val time = ride.seconds

        // Cores and haloes.
        draw(buffers, RenderType.eyes(GLOW)) { buffer ->
            for (s in seen) {
                val pulse = 1f + 0.1f * sin(time * 5f + s.item.id)
                when (s.item.kind) {
                    UltraWarpCourse.Kind.ORB -> {
                        billboard(buffer, s.position, right, up, s.item.radius.toFloat() * 3.2f * pulse, 0f, shade(ORANGE, 0.35f * s.fade))
                        billboard(buffer, s.position, right, up, s.item.radius.toFloat() * 1.4f * pulse, 0f, shade(ORANGE, 1.2f * s.fade))
                    }
                    UltraWarpCourse.Kind.SPARK -> {
                        billboard(buffer, s.position, right, up, s.item.radius.toFloat() * 2.6f * pulse, 0f, shade(BLUE, 0.4f * s.fade))
                        billboard(buffer, s.position, right, up, s.item.radius.toFloat() * 1.2f, 0f, shade(BLUE, 1.3f * s.fade))
                    }
                    else -> {}
                }
            }
            for (burst in ride.bursts) {
                val p = ((time - burst.start) / 0.6f).coerceIn(0f, 1f)
                val colour = if (burst.kind == UltraWarpCourse.Kind.ORB) ORANGE else BLUE
                billboard(buffer, relative(ride, burst.position), right, up, 2f + 5f * p, 0f, shade(colour, 1.4f * (1f - p)))
            }
            // Necrozma's blinding light.
            ride.necrozmaPosition()?.let { position ->
                val centre = relative(ride, position)
                val pulse = 1f + 0.15f * sin(time * 6f)
                billboard(buffer, centre, right, up, 16f * pulse, 0f, shade(0xFFE8B0, 0.45f))
                billboard(buffer, centre, right, up, 6f * pulse, time * 0.5f, shade(0xFFFFF0, 1.6f))
            }
        }

        // Rings.
        draw(buffers, RenderType.eyes(RING)) { buffer ->
            for (s in seen) {
                when (s.item.kind) {
                    UltraWarpCourse.Kind.ORB -> billboard(buffer, s.position, right, up, s.item.radius.toFloat() * 2.1f, time * 1.2f, shade(ORANGE_RING, 1.1f * s.fade))
                    UltraWarpCourse.Kind.SPARK -> billboard(buffer, s.position, right, up, s.item.radius.toFloat() * 1.9f, -time * 2f, shade(BLUE_RING, 0.9f * s.fade))
                    else -> {}
                }
            }
            for (burst in ride.bursts) {
                val p = ((time - burst.start) / 0.6f).coerceIn(0f, 1f)
                val colour = if (burst.kind == UltraWarpCourse.Kind.ORB) ORANGE_RING else BLUE_RING
                billboard(buffer, relative(ride, burst.position), right, up, 2.5f + 8f * p, 0f, shade(colour, 1.2f * (1f - p)))
            }
            currents(ride, buffer, time)
        }

        // Gravitational anomalies: a turning swirl, violet when it pulls, green-blue when it pushes.
        draw(buffers, RenderType.eyes(VORTEX)) { buffer ->
            for (s in seen) {
                if (s.item.kind != UltraWarpCourse.Kind.GRAVITY) continue
                val pulls = s.item.strength >= 0
                val turn = if (pulls) -time * 2.2f else time * 2.2f
                val colour = if (pulls) PULL_COLOUR else PUSH_COLOUR
                billboard(buffer, s.position, right, up, s.item.radius.toFloat() * 1.7f, turn, shade(colour, 0.9f * s.fade))
                billboard(buffer, s.position, right, up, s.item.radius.toFloat() * 1.1f, -turn * 1.6f, shade(colour, 0.6f * s.fade))
            }
        }

        // Electric bolts around the blue orbs, a new shape 14 times per second.
        draw(buffers, RenderType.eyes(BOLT)) { buffer ->
            val step = floor(time * 14f).toLong()
            for (s in seen) {
                if (s.item.kind != UltraWarpCourse.Kind.SPARK) continue
                val radius = s.item.radius.toFloat()
                for (n in 0 until 4) {
                    val seed = s.item.look * 31 + n * 7919L + step * 104729L
                    val end = Vector3f(
                        (hash(seed, 1) * 2 - 1).toFloat(), (hash(seed, 2) * 2 - 1).toFloat(), (hash(seed, 3) * 2 - 1).toFloat(),
                    ).normalize(radius * 1.8f).add(s.position)
                    bolt(buffer, s.position, end, seed, 0.09f, shade(BOLT_COLOUR, 1.3f * s.fade))
                }
            }
            // Electric arcs across the tunnel: a wide dim glow and a bright core, flickering.
            for (s in seen) {
                if (s.item.kind != UltraWarpCourse.Kind.ARC) continue
                val a = s.position
                val b = place(ride, s.item.distance, s.item.x2, s.item.y2)
                val flicker = 0.75f + 0.25f * hash(s.item.look + step, 9).toFloat()
                for (n in 0 until 2) {
                    val seed = s.item.look * 17 + n * 6151L + step * 92821L
                    bolt(buffer, a, b, seed, 0.45f, shade(ARC_COLOUR, 0.55f * flicker * s.fade), 12)
                    bolt(buffer, a, b, seed, 0.13f, shade(BOLT_COLOUR, 1.4f * flicker * s.fade), 12)
                }
            }
        }
    }

    /**
     * Speed currents: rings across the tunnel every 4 blocks along their tube,
     * streaming backwards, that the Pokémon flies through; both faces.
     */
    private fun currents(ride: UltraWarpRideClient.Ride, buffer: VertexConsumer, time: Float) {
        val radius = ride.settings.config.objects.currentRadius.toFloat()
        val view = ride.cameraPath + min(ride.settings.config.objects.viewDistance.toDouble(), ride.config.visual.viewDistance.toDouble())
        for (window in ride.course.windows) {
            if (window.kind != UltraWarpCourse.WindowKind.CURRENT || window.to < ride.cameraPath || window.from > view) continue
            val shift = (time * 18.0) % 4.0
            var d = window.from + 4.0 - shift
            while (d < window.to) {
                if (d > ride.cameraPath - 2 && d < view) {
                    val fade = smoothstep(0f, 12f, (d - window.from).toFloat()) * smoothstep(0f, 12f, (window.to - d).toFloat())
                    ride.tunnel.frame(d, t, u, r)
                    val centre = place(ride, d, window.x, window.y)
                    val rx = Vector3f(r.x.toFloat(), r.y.toFloat(), r.z.toFloat()).mul(radius)
                    val ux = Vector3f(u.x.toFloat(), u.y.toFloat(), u.z.toFloat()).mul(radius)
                    val colour = shade(0x50E8FF, 1.1f * fade)
                    val a = Vector3f(centre).sub(rx).sub(ux)
                    val b = Vector3f(centre).add(rx).sub(ux)
                    val c = Vector3f(centre).add(rx).add(ux)
                    val e = Vector3f(centre).sub(rx).add(ux)
                    for ((p, uv) in listOf(a to (0f to 1f), b to (1f to 1f), c to (1f to 0f), e to (0f to 0f), e to (0f to 0f), c to (1f to 0f), b to (1f to 1f), a to (0f to 1f))) {
                        vertex(buffer, p.x, p.y, p.z, uv.first, uv.second, colour)
                    }
                }
                d += 4.0
            }
        }
    }

    /** Where a place of the tunnel (distance, across) is from the camera. */
    fun place(ride: UltraWarpRideClient.Ride, distance: Double, x: Double, y: Double): Vector3f {
        ride.tunnel.frame(distance, t, u, r)
        val p = ride.tunnel.position(distance).fma(x, r).fma(y, u).sub(ride.camera)
        return Vector3f(p.x.toFloat(), p.y.toFloat(), p.z.toFloat())
    }

    private fun relative(ride: UltraWarpRideClient.Ride, position: Vector3d): Vector3f =
        Vector3f((position.x - ride.camera.x).toFloat(), (position.y - ride.camera.y).toFloat(), (position.z - ride.camera.z).toFloat())

    fun draw(buffers: MultiBufferSource.BufferSource, type: RenderType, emit: (VertexConsumer) -> Unit) {
        emit(buffers.getBuffer(type))
        buffers.endBatch(type)
    }

    /** A square facing the camera, turned by angle, of half size size. */
    fun billboard(buffer: VertexConsumer, centre: Vector3f, right: Vector3f, up: Vector3f, size: Float, angle: Float, colour: Int) {
        val c = cos(angle)
        val s = sin(angle)
        val rx = (right.x * c + up.x * s) * size
        val ry = (right.y * c + up.y * s) * size
        val rz = (right.z * c + up.z * s) * size
        val ux = (up.x * c - right.x * s) * size
        val uy = (up.y * c - right.y * s) * size
        val uz = (up.z * c - right.z * s) * size
        vertex(buffer, centre.x - rx - ux, centre.y - ry - uy, centre.z - rz - uz, 0f, 1f, colour)
        vertex(buffer, centre.x + rx - ux, centre.y + ry - uy, centre.z + rz - uz, 1f, 1f, colour)
        vertex(buffer, centre.x + rx + ux, centre.y + ry + uy, centre.z + rz + uz, 1f, 0f, colour)
        vertex(buffer, centre.x - rx + ux, centre.y - ry + uy, centre.z - rz + uz, 0f, 0f, colour)
    }

    /** A jagged bolt of light from a to b (camera-relative), its zigzag drawn from the seed. */
    fun bolt(buffer: VertexConsumer, a: Vector3f, b: Vector3f, seed: Long, width: Float, colour: Int, segments: Int = 6) {
        val length = a.distance(b)
        val axis = Vector3f(b).sub(a)
        // Two directions across the bolt for the zigzag.
        val side = Vector3f(axis).cross(if (kotlin.math.abs(axis.y) < 0.9f * length) Vector3f(0f, 1f, 0f) else Vector3f(1f, 0f, 0f)).normalize()
        val other = Vector3f(axis).cross(side).normalize()
        var previous = Vector3f(a)
        for (i in 1..segments) {
            val f = i.toFloat() / segments
            val next = Vector3f(axis).mul(f).add(a)
            if (i < segments) {
                val amplitude = length * 0.14f
                next.fma(((hash(seed, i * 2) * 2 - 1) * amplitude).toFloat(), side)
                next.fma(((hash(seed, i * 2 + 1) * 2 - 1) * amplitude).toFloat(), other)
            }
            segment(buffer, previous, next, width, colour)
            previous = next
        }
    }

    /** A strip of light from a to b facing the camera, both faces. */
    fun segment(buffer: VertexConsumer, a: Vector3f, b: Vector3f, width: Float, colour: Int) {
        val along = Vector3f(b).sub(a)
        val middle = Vector3f(a).add(b).mul(0.5f)
        val side = Vector3f(along).cross(middle)
        if (side.lengthSquared() < 1e-8f) return
        side.normalize(width)
        vertex(buffer, a.x - side.x, a.y - side.y, a.z - side.z, 0f, 0f, colour)
        vertex(buffer, a.x + side.x, a.y + side.y, a.z + side.z, 1f, 0f, colour)
        vertex(buffer, b.x + side.x, b.y + side.y, b.z + side.z, 1f, 1f, colour)
        vertex(buffer, b.x - side.x, b.y - side.y, b.z - side.z, 0f, 1f, colour)
        vertex(buffer, b.x - side.x, b.y - side.y, b.z - side.z, 0f, 1f, colour)
        vertex(buffer, b.x + side.x, b.y + side.y, b.z + side.z, 1f, 1f, colour)
        vertex(buffer, a.x + side.x, a.y + side.y, a.z + side.z, 1f, 0f, colour)
        vertex(buffer, a.x - side.x, a.y - side.y, a.z - side.z, 0f, 0f, colour)
    }

    fun vertex(buffer: VertexConsumer, x: Float, y: Float, z: Float, u: Float, v: Float, colour: Int) {
        buffer.addVertex(x, y, z, colour, u, v, OverlayTexture.NO_OVERLAY, LightTexture.FULL_BRIGHT, 0f, 1f, 0f)
    }

    fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }

    /** Colour 0xRRGGBB times a factor, opaque (additive drawing: darker is fainter). */
    fun shade(rgb: Int, factor: Float): Int {
        val f = factor.coerceIn(0f, 4f)
        val red = (((rgb shr 16) and 0xFF) * f).toInt().coerceIn(0, 255)
        val green = (((rgb shr 8) and 0xFF) * f).toInt().coerceIn(0, 255)
        val blue = ((rgb and 0xFF) * f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
    }

    /** A number in [0, 1) from a seed and a salt. */
    fun hash(seed: Long, salt: Int): Double {
        var x = seed xor (salt.toLong() * 0x2545F4914F6CDD1DL)
        x = (x xor (x ushr 30)) * -0x40a7b892e31b1a47L
        x = (x xor (x ushr 27)) * -0x6b2fb644ecceee15L
        x = x xor (x ushr 31)
        return (x ushr 11) * (1.0 / (1L shl 53))
    }
}
