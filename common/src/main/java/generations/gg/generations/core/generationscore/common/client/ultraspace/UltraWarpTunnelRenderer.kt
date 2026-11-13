package generations.gg.generations.core.generationscore.common.client.ultraspace

import generations.gg.generations.core.generationscore.common.GenerationsCore
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import org.joml.Vector3d
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/**
 * The Ultra Warp Ride tunnel, drawn around the camera each frame (no blocks, no
 * chunks): rings along the path from just behind the camera to viewDistance ahead,
 * joined by quads. Walls: a swirling texture (tools/ultra_warp_textures.py) turning
 * and twisting along the tunnel, darker with the distance; a second glowing layer
 * inside, scrolling faster (depth); light streaks rushing past (speed); a light at
 * the far end. The ends are closed so that nothing of the sky shows.
 */
object UltraWarpTunnelRenderer {
    private val WALL: ResourceLocation = GenerationsCore.id("textures/ultra_space/tunnel_wall.png")
    private val WALL_GLOW: ResourceLocation = GenerationsCore.id("textures/ultra_space/tunnel_glow.png")
    private val STREAK: ResourceLocation = GenerationsCore.id("textures/ultra_space/streak.png")
    private val GLOW: ResourceLocation = GenerationsCore.id("textures/ultra_space/glow.png")
    private const val AROUND = 2f
    private const val BEHIND = 6.0

    /** The rings of this frame: camera-relative positions, inward normals, fade, distance. */
    private class Rings {
        var count = 0
        var sides = 0
        var positions = FloatArray(0)
        var normals = FloatArray(0)
        var fade = FloatArray(0)
        var distance = DoubleArray(0)
        val centres = ArrayList<Vector3f>()

        fun resize(count: Int, sides: Int) {
            this.count = count
            this.sides = sides
            val vertices = count * (sides + 1)
            if (positions.size < vertices * 3) {
                positions = FloatArray(vertices * 3)
                normals = FloatArray(vertices * 3)
            }
            if (fade.size < count) {
                fade = FloatArray(count)
                distance = DoubleArray(count)
            }
            while (centres.size < count) centres.add(Vector3f())
        }
    }

    private val rings = Rings()

    private fun viewDistance(ride: UltraWarpRideClient.Ride): Double =
        min(ride.config.visual.viewDistance.toDouble(), Minecraft.getInstance().gameRenderer.depthFar * 0.85)

    /** How far along the path the camera is (it follows the tunnel, see UltraWarpCamera). */
    private fun cameraDistance(ride: UltraWarpRideClient.Ride): Double = ride.cameraPath

    private fun build(ride: UltraWarpRideClient.Ride) {
        val visual = ride.config.visual
        val spacing = visual.ringSpacing.toDouble()
        val start = floor((cameraDistance(ride) - BEHIND) / spacing) * spacing
        val end = cameraDistance(ride) + viewDistance(ride)
        val count = ((end - start) / spacing).toInt() + 1
        val sides = visual.sides
        rings.resize(count, sides)
        val t = Vector3d()
        val u = Vector3d()
        val r = Vector3d()
        val centre = Vector3d()
        val camDistance = cameraDistance(ride)
        for (k in 0 until count) {
            val d = start + k * spacing
            rings.distance[k] = d
            ride.tunnel.frame(d, t, u, r)
            ride.tunnel.position(d, centre)
            centre.sub(ride.camera)
            rings.centres[k].set(centre)
            val ahead = d - camDistance
            rings.fade[k] = 1f - smoothstep(visual.fadeStart, visual.fadeEnd, ahead.toFloat())
            for (j in 0..sides) {
                val angle = j * 2 * PI / sides
                val c = cos(angle)
                val s = sin(angle)
                val radius = ride.tunnel.radius(d, angle)
                val nx = r.x * c + u.x * s
                val ny = r.y * c + u.y * s
                val nz = r.z * c + u.z * s
                val i = (k * (sides + 1) + j) * 3
                rings.positions[i] = (centre.x + nx * radius).toFloat()
                rings.positions[i + 1] = (centre.y + ny * radius).toFloat()
                rings.positions[i + 2] = (centre.z + nz * radius).toFloat()
                rings.normals[i] = -nx.toFloat()
                rings.normals[i + 1] = -ny.toFloat()
                rings.normals[i + 2] = -nz.toFloat()
            }
        }
    }

    fun renderOpaque(ride: UltraWarpRideClient.Ride, buffers: MultiBufferSource.BufferSource) {
        build(ride)
        val visual = ride.config.visual
        val type = RenderType.entityCutoutNoCull(WALL)
        val buffer = buffers.getBuffer(type)
        val wall = ride.wallColour()
        val time = ride.seconds
        val sides = rings.sides
        val texLength = visual.textureLength.toDouble()
        // Walls: the swirl turns with the time and twists along the tunnel.
        for (k in 0 until rings.count - 1) {
            for (j in 0 until sides) {
                wallVertex(buffer, ride, k, j, wall, visual.brightness, texLength, visual.twist, visual.swirlSpeed, time)
                wallVertex(buffer, ride, k, j + 1, wall, visual.brightness, texLength, visual.twist, visual.swirlSpeed, time)
                wallVertex(buffer, ride, k + 1, j + 1, wall, visual.brightness, texLength, visual.twist, visual.swirlSpeed, time)
                wallVertex(buffer, ride, k + 1, j, wall, visual.brightness, texLength, visual.twist, visual.swirlSpeed, time)
            }
        }
        // Both ends closed in the dark: no sky through the tunnel.
        val fog = UltraWarpClientConfig.rgb(visual.fogColor)
        cap(buffer, 0, fog)
        cap(buffer, rings.count - 1, fog)
        buffers.endBatch(type)
    }

    private fun wallVertex(
        buffer: com.mojang.blaze3d.vertex.VertexConsumer, ride: UltraWarpRideClient.Ride, k: Int, j: Int,
        rgb: Int, brightness: Float, texLength: Double, twist: Float, swirl: Float, time: Float,
    ) {
        val i = (k * (rings.sides + 1) + j) * 3
        val along = rings.distance[k] / texLength
        val u = (j.toFloat() / rings.sides * AROUND + (along * twist).toFloat() + time * swirl)
        val v = along.toFloat()
        buffer.addVertex(
            rings.positions[i], rings.positions[i + 1], rings.positions[i + 2],
            shade(rgb, rings.fade[k] * brightness), u, v,
            OverlayTexture.NO_OVERLAY, LightTexture.FULL_BRIGHT,
            rings.normals[i], rings.normals[i + 1], rings.normals[i + 2],
        )
    }

    /** A dark disc over ring k (a fan of quads). */
    private fun cap(buffer: com.mojang.blaze3d.vertex.VertexConsumer, k: Int, rgb: Int) {
        val centre = rings.centres[k]
        val colour = 0xFF000000.toInt() or rgb
        for (j in 0 until rings.sides) {
            val a = (k * (rings.sides + 1) + j) * 3
            val b = a + 3
            for ((x, y, z) in listOf(
                Triple(centre.x, centre.y, centre.z),
                Triple(rings.positions[a], rings.positions[a + 1], rings.positions[a + 2]),
                Triple(rings.positions[b], rings.positions[b + 1], rings.positions[b + 2]),
                Triple(rings.positions[b], rings.positions[b + 1], rings.positions[b + 2]),
            )) {
                buffer.addVertex(x, y, z, colour, 0.5f, 0.5f, OverlayTexture.NO_OVERLAY, LightTexture.FULL_BRIGHT, 0f, 1f, 0f)
            }
        }
    }

    fun renderTranslucent(ride: UltraWarpRideClient.Ride, buffers: MultiBufferSource.BufferSource) {
        if (rings.count < 2) return
        val visual = ride.config.visual
        val time = ride.seconds
        if (visual.glowLayer) glowLayer(ride, buffers, time)
        if (visual.streaks > 0) streaks(ride, buffers)
        if (visual.endGlow) endGlow(ride, buffers, time)
    }

    /** Inner glowing layer, a little inside the walls, faster: the tunnel seems deep. */
    private fun glowLayer(ride: UltraWarpRideClient.Ride, buffers: MultiBufferSource.BufferSource, time: Float) {
        val visual = ride.config.visual
        val type = RenderType.eyes(WALL_GLOW)
        val buffer = buffers.getBuffer(type)
        val rgb = ride.glowColour()
        val sides = rings.sides
        val texLength = visual.textureLength * 0.7
        fun vertex(k: Int, j: Int) {
            val i = (k * (sides + 1) + j) * 3
            val centre = rings.centres[k]
            val x = centre.x + (rings.positions[i] - centre.x) * 0.93f
            val y = centre.y + (rings.positions[i + 1] - centre.y) * 0.93f
            val z = centre.z + (rings.positions[i + 2] - centre.z) * 0.93f
            val along = rings.distance[k] / texLength
            val u = j.toFloat() / sides * AROUND - (along * visual.twist * 1.5).toFloat() + time * visual.swirlSpeed * 2.5f
            val v = along.toFloat() + time * 0.15f
            buffer.addVertex(x, y, z, shade(rgb, rings.fade[k] * visual.brightness * 0.8f), u, v, OverlayTexture.NO_OVERLAY, LightTexture.FULL_BRIGHT, 0f, 1f, 0f)
        }
        for (k in 0 until rings.count - 1) {
            for (j in 0 until sides) {
                vertex(k, j)
                vertex(k, j + 1)
                vertex(k + 1, j + 1)
                vertex(k + 1, j)
            }
        }
        buffers.endBatch(type)
    }

    /**
     * Light streaks: fixed points of the tunnel's space near the walls, stretched
     * along it with the speed, so that they rush past. Where they are comes from the
     * seed and their slot along the tunnel: nothing to keep from one frame to the next.
     */
    private fun streaks(ride: UltraWarpRideClient.Ride, buffers: MultiBufferSource.BufferSource) {
        val visual = ride.config.visual
        val type = RenderType.eyes(STREAK)
        val buffer = buffers.getBuffer(type)
        val rgb = UltraWarpClientConfig.rgb(visual.streakColor)
        val camDistance = cameraDistance(ride)
        val window = viewDistance(ride) * 0.75
        val spacing = window / visual.streaks
        val first = floor(camDistance / spacing).toLong()
        val length = (ride.speed * 0.15).coerceIn(1.0, 10.0)
        val t = Vector3d()
        val u = Vector3d()
        val r = Vector3d()
        val p = Vector3d()
        val side = Vector3d()
        for (n in 0..visual.streaks) {
            val slot = first + n
            val d = (slot + hash(ride.packet.seed, slot, 1)) * spacing
            val ahead = d - camDistance
            if (ahead < 1.0) continue
            val angle = hash(ride.packet.seed, slot, 2) * 2 * PI
            val depth = 0.55 + 0.4 * hash(ride.packet.seed, slot, 3)
            ride.tunnel.frame(d, t, u, r)
            ride.tunnel.position(d, p)
            val radius = ride.tunnel.radius(d, angle) * depth
            p.fma(cos(angle) * radius, r).fma(sin(angle) * radius, u).sub(ride.camera)
            // Width square to the streak and to the eye.
            p.cross(t, side)
            if (side.lengthSquared() < 1e-6) continue
            side.normalize(0.06 + 0.06 * hash(ride.packet.seed, slot, 4))
            val fade = 1f - smoothstep(visual.fadeStart, visual.fadeEnd, ahead.toFloat())
            val colour = shade(rgb, fade * visual.brightness)
            val half = length / 2
            fun vertex(along: Double, across: Double, uu: Float, vv: Float) {
                buffer.addVertex(
                    (p.x + t.x * along + side.x * across).toFloat(), (p.y + t.y * along + side.y * across).toFloat(), (p.z + t.z * along + side.z * across).toFloat(),
                    colour, uu, vv, OverlayTexture.NO_OVERLAY, LightTexture.FULL_BRIGHT, 0f, 1f, 0f,
                )
            }
            // Both faces: the glowing types hide the back of a quad.
            vertex(-half, -1.0, 0f, 1f)
            vertex(-half, 1.0, 1f, 1f)
            vertex(half, 1.0, 1f, 0f)
            vertex(half, -1.0, 0f, 0f)
            vertex(half, -1.0, 0f, 0f)
            vertex(half, 1.0, 1f, 0f)
            vertex(-half, 1.0, 1f, 1f)
            vertex(-half, -1.0, 0f, 1f)
        }
        buffers.endBatch(type)
    }

    /** The light at the end of the tunnel, facing the camera, breathing slowly. */
    private fun endGlow(ride: UltraWarpRideClient.Ride, buffers: MultiBufferSource.BufferSource, time: Float) {
        val visual = ride.config.visual
        val type = RenderType.eyes(GLOW)
        val buffer = buffers.getBuffer(type)
        val k = (rings.count - 3).coerceAtLeast(0)
        val centre = rings.centres[k]
        val right = Vector3f(1f, 0f, 0f).rotate(ride.view)
        val up = Vector3f(0f, 1f, 0f).rotate(ride.view)
        val rgb = UltraWarpClientConfig.rgb(visual.endColor)
        val pulse = 0.85f + 0.15f * sin(time * 1.7f)
        for ((scale, strength) in listOf(ride.packet.settings.radius * 3.2f to 0.55f, ride.packet.settings.radius * 1.2f to 1f)) {
            val size = scale * pulse
            val colour = shade(rgb, strength * visual.brightness)
            fun vertex(a: Float, b: Float, uu: Float, vv: Float) {
                buffer.addVertex(
                    centre.x + right.x * a * size + up.x * b * size, centre.y + right.y * a * size + up.y * b * size, centre.z + right.z * a * size + up.z * b * size,
                    colour, uu, vv, OverlayTexture.NO_OVERLAY, LightTexture.FULL_BRIGHT, 0f, 1f, 0f,
                )
            }
            vertex(-1f, -1f, 0f, 1f)
            vertex(1f, -1f, 1f, 1f)
            vertex(1f, 1f, 1f, 0f)
            vertex(-1f, 1f, 0f, 0f)
        }
        buffers.endBatch(type)
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }

    /** Colour 0xRRGGBB times a factor, opaque. */
    private fun shade(rgb: Int, factor: Float): Int {
        val f = factor.coerceIn(0f, 4f)
        val red = (((rgb shr 16) and 0xFF) * f).toInt().coerceIn(0, 255)
        val green = (((rgb shr 8) and 0xFF) * f).toInt().coerceIn(0, 255)
        val blue = ((rgb and 0xFF) * f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
    }

    /** A number in [0, 1) from the seed, a slot and a salt: the same every frame. */
    private fun hash(seed: Long, slot: Long, salt: Int): Double {
        var x = seed xor (slot * -0x61c8864680b583ebL) xor (salt.toLong() * 0x2545F4914F6CDD1DL)
        x = (x xor (x ushr 30)) * -0x40a7b892e31b1a47L
        x = (x xor (x ushr 27)) * -0x6b2fb644ecceee15L
        x = x xor (x ushr 31)
        return (x ushr 11) * (1.0 / (1L shl 53))
    }
}
