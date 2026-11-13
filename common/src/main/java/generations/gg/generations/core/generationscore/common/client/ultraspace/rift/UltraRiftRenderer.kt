package generations.gg.generations.core.generationscore.common.client.ultraspace.rift

import com.mojang.blaze3d.vertex.VertexConsumer
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.world.ultraspace.rift.UltraRiftShape
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import org.joml.Vector3f
import java.util.Random
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The rifts in the world (phase 6), drawn in the translucent pass of the world
 * (LevelRendererMixin): a torn vertical opening, not a ring.
 * - a dark core of the destination's colour (it hides the world behind: a hole);
 * - inside, smaller copies of the outline pushed back away from the camera, turning:
 *   the depth of a passage;
 * - a glowing edge where a pulse of energy runs round, and a soft glow around it;
 * - a few broken arcs of energy flickering out of the edge, a halo;
 * - the edge wobbles slowly.
 * Some hundred quads per rift up close; far away only the core, the edge and the halo;
 * nothing beyond the view distance. Animations follow the client's ticks.
 */
object UltraRiftRenderer {
    private val GLOW: ResourceLocation = GenerationsCore.id("textures/ultra_space/glow.png")
    private val VORTEX: ResourceLocation = GenerationsCore.id("textures/ultra_space/vortex.png")
    private const val N = UltraRiftShape.POINTS

    /** A rift as drawn this frame. */
    private class Frame(
        val rift: UltraRiftClient.Rift,
        /** Camera-relative centre. */
        val cx: Float, val cy: Float, val cz: Float,
        val distance: Float,
        /** Opening (0 to 1), brightness (fade and flicker), flash of the collapse. */
        val scale: Float,
        val bright: Float,
        val flash: Float,
        /** The camera is on the normal's negative side: faces turned. */
        val flip: Boolean,
        /** Away from the camera, along the normal. */
        val back: Float,
        val wobble: FloatArray,
    )

    /** This frame's rifts wait for the shader pack's final image (renderAfterShaders). */
    private var deferred = false
    private var keptCamera: Camera? = null
    private var keptPartialTick = 0f
    private val keptFrustum = org.joml.Matrix4f()
    private val keptProjection = org.joml.Matrix4f()

    /**
     * In the world's translucent pass. With a shader pack the rifts are drawn after its
     * final pass instead (its lights, shadows and fog made them dark and broken): decided
     * here, the only moment Iris shows that it is drawing with a pack.
     */
    @JvmStatic
    fun render(camera: Camera, partialTick: Float, frustum: org.joml.Matrix4f, projection: org.joml.Matrix4f) {
        deferred = false
        if (UltraRiftClient.rifts.isEmpty()) return
        if (generations.gg.generations.core.generationscore.common.client.render.rarecandy.IrisFramebufferCompat.isShaderPackActive()) {
            keptCamera = camera
            keptPartialTick = partialTick
            keptFrustum.set(frustum)
            keptProjection.set(projection)
            deferred = true
            return
        }
        try {
            draw(camera, partialTick)
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("Ultra Space: could not draw the rifts", e)
        }
    }

    /**
     * Over the shader pack's final image (GameRendererMixin), with Minecraft's own shaders
     * and no fog, tested against the world's depth (Iris keeps it in the main framebuffer):
     * hills and walls still hide them.
     */
    @JvmStatic
    fun renderAfterShaders() {
        if (!deferred) return
        deferred = false
        val camera = keptCamera ?: return
        val minecraft = Minecraft.getInstance()
        val stack = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack()
        val previousProjection = org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix())
        val previousSorting = com.mojang.blaze3d.systems.RenderSystem.getVertexSorting()
        stack.pushMatrix()
        try {
            minecraft.mainRenderTarget.bindWrite(true)
            generations.gg.generations.core.generationscore.common.client.ultraspace.UltraWarpRideClient.restoreWriteMasks()
            generations.gg.generations.core.generationscore.common.client.ultraspace.UltraWarpRideClient.clearTextureUnits()
            com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(org.joml.Matrix4f(keptProjection), com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN)
            stack.identity()
            stack.mul(keptFrustum)
            com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix()
            net.minecraft.client.renderer.FogRenderer.setupNoFog()
            com.mojang.blaze3d.systems.RenderSystem.enableDepthTest()
            com.mojang.blaze3d.systems.RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL)
            draw(camera, keptPartialTick)
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("Ultra Space: could not draw the rifts", e)
        } finally {
            stack.popMatrix()
            com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix()
            com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(previousProjection, previousSorting)
        }
    }

    private fun draw(camera: Camera, partialTick: Float) {
        val config = UltraRiftClientConfig.settings
        val cam = camera.position
        val now = UltraRiftClient.ticks
        val time = (now + partialTick) / 20f
        val frames = ArrayList<Frame>()
        for (rift in UltraRiftClient.rifts.values) {
            val view = rift.view
            val cx = (view.x - cam.x).toFloat()
            val cy = (view.y - cam.y).toFloat()
            val cz = (view.z - cam.z).toFloat()
            val distance = sqrt(cx * cx + cy * cy + cz * cz)
            if (distance > config.viewDistance) continue
            val fade = 1f - smooth((distance - config.viewDistance * 0.8f) / (config.viewDistance * 0.2f))
            var scale = smooth((rift.age(now) + partialTick) / UltraRiftClient.OPEN_TICKS)
            var bright = fade
            var flash = 0f
            if (rift.closing >= 0) {
                val length = if (rift.collapse) UltraRiftClient.COLLAPSE_TICKS else UltraRiftClient.CLOSE_TICKS
                val p = ((now - rift.closing + partialTick) / length).coerceIn(0f, 1f)
                if (rift.collapse) {
                    scale *= (1f - p) * (1f - p)
                    flash = (1f - p * 2f).coerceAtLeast(0f)
                } else {
                    scale *= 1f - p
                    bright *= 1f - p * 0.5f + 0.35f * sin(p * 45f) * p
                }
            }
            if (scale <= 0.001f || bright <= 0.001f) continue
            // Which side the camera is on (the normal is (-rightZ, 0, rightX)).
            val side = -cx * -rift.rightZ + -cz * rift.rightX
            val wobble = FloatArray(N) { k ->
                if (config.distortion) 1f + 0.05f * sin(time * 1.7f + k * 1.3f + (view.seed and 0xFF).toFloat()) else 1f
            }
            frames += Frame(rift, cx, cy, cz, distance, scale, bright.coerceIn(0f, 1.5f), flash, side < 0, if (side < 0) 1f else -1f, wobble)
        }
        if (frames.isEmpty()) return
        val buffers = Minecraft.getInstance().renderBuffers().bufferSource()

        // The dark core first: it hides what is behind (no depth written: the layers inside stay seen).
        val coreType = RenderType.entityTranslucentEmissive(VORTEX)
        val core = buffers.getBuffer(coreType)
        for (f in frames) {
            val colour = argb(f.rift.view.color, 0.10f, 0.92f * f.bright.coerceAtMost(1f))
            fan(core, f, 0.99f, 0f, colour, time * 0.25f)
        }
        buffers.endBatch(coreType)

        // The passage's depth: turning copies of the outline, pushed back.
        val depthType = RenderType.eyes(VORTEX)
        val depth = buffers.getBuffer(depthType)
        for (f in frames) {
            val layers = if (f.distance < config.detailDistance) 3 else 1
            for (i in 0 until layers) {
                val size = floatArrayOf(0.86f, 0.62f, 0.38f)[i]
                val push = floatArrayOf(0.35f, 0.85f, 1.4f)[i]
                val strength = floatArrayOf(0.35f, 0.5f, 0.8f)[i] * f.bright * config.glow
                val spin = floatArrayOf(0.5f, -0.8f, 1.2f)[i]
                fan(depth, f, size, push, argb(f.rift.view.color, strength, 1f), time * spin)
            }
        }
        buffers.endBatch(depthType)

        // The glowing edge, its soft glow, the arcs, the halo.
        val glowType = RenderType.eyes(GLOW)
        val glow = buffers.getBuffer(glowType)
        for (f in frames) {
            val rgb = f.rift.view.color
            val detail = f.distance < config.detailDistance
            for (k in 0 until N) {
                val next = (k + 1) % N
                val pulseA = (0.65f + 0.35f * sin(time * 3f - k * 0.79f)) * f.bright * config.glow
                val pulseB = (0.65f + 0.35f * sin(time * 3f - next * 0.79f)) * f.bright * config.glow
                val innerA = argb(mix(rgb, 0xFFFFFF, 0.45f), pulseA, 1f)
                val innerB = argb(mix(rgb, 0xFFFFFF, 0.45f), pulseB, 1f)
                val outerA = argb(rgb, pulseA * 0.7f, 1f)
                val outerB = argb(rgb, pulseB * 0.7f, 1f)
                quad(glow, f,
                    point(f, k, 0.9f, 0f), 0.5f, 0.5f, innerA,
                    point(f, k, 1.13f, 0f), 0.5f, 0.02f, outerA,
                    point(f, next, 1.13f, 0f), 0.5f, 0.02f, outerB,
                    point(f, next, 0.9f, 0f), 0.5f, 0.5f, innerB)
                if (detail) {
                    val softA = argb(rgb, pulseA * 0.3f, 1f)
                    val softB = argb(rgb, pulseB * 0.3f, 1f)
                    quad(glow, f,
                        point(f, k, 1.13f, 0f), 0.5f, 0.5f, softA,
                        point(f, k, 1.42f, 0f), 0.5f, 0.02f, 0,
                        point(f, next, 1.42f, 0f), 0.5f, 0.02f, 0,
                        point(f, next, 1.13f, 0f), 0.5f, 0.5f, softB)
                }
            }
            if (detail) arcs(glow, f, rgb)
            if (UltraRiftClientConfig.settings.showTriggerZone) zone(glow, f)
            val view = f.rift.view
            halo(glow, camera, f, max(view.width, view.height) * 0.8f * f.scale, argb(rgb, 0.28f * f.bright * config.glow, 1f))
            if (f.flash > 0f) halo(glow, camera, f, max(view.width, view.height) * (1.3f - f.flash * 0.4f), argb(0xFFFFFF, f.flash, 1f))
        }
        buffers.endBatch(glowType)
    }

    /** A point of the outline (k), scaled, pushed back; camera-relative. */
    private fun point(f: Frame, k: Int, scale: Float, push: Float): Vector3f {
        val outline = f.rift.outline
        val s = scale * f.scale * f.wobble[k]
        val u = outline[2 * k] * s
        val v = outline[2 * k + 1] * s
        val nx = -f.rift.rightZ
        val nz = f.rift.rightX
        val d = push * f.back
        return Vector3f(f.cx + u * f.rift.rightX + nx * d, f.cy + v, f.cz + u * f.rift.rightZ + nz * d)
    }

    /** The outline filled from its middle, its texture turned by angle. */
    private fun fan(buffer: VertexConsumer, f: Frame, scale: Float, push: Float, colour: Int, angle: Float) {
        val centre = Vector3f(f.cx - f.rift.rightZ * push * f.back, f.cy, f.cz + f.rift.rightX * push * f.back)
        val c = cos(angle)
        val s = sin(angle)
        val w = f.rift.view.width / 2f
        val h = f.rift.view.height / 2f
        fun uv(k: Int): Pair<Float, Float> {
            val u = f.rift.outline[2 * k] / w
            val v = f.rift.outline[2 * k + 1] / h
            return (0.5f + 0.5f * (u * c - v * s)) to (0.5f + 0.5f * (u * s + v * c))
        }
        for (k in 0 until N) {
            val next = (k + 1) % N
            val a = point(f, k, scale, push)
            val b = point(f, next, scale, push)
            val (ua, va) = uv(k)
            val (ub, vb) = uv(next)
            quad(buffer, f, centre, 0.5f, 0.5f, colour, a, ua, va, colour, b, ub, vb, colour, b, ub, vb, colour)
        }
    }

    /** Three broken arcs out of the edge, new every few ticks. */
    private fun arcs(buffer: VertexConsumer, f: Frame, rgb: Int) {
        val random = Random(f.rift.view.seed xor (UltraRiftClient.ticks / 6) * 0x9E3779B97F4A7C15uL.toLong())
        val colour = argb(mix(rgb, 0xFFFFFF, 0.6f), 0.9f * f.bright, 1f)
        repeat(3) {
            val k = random.nextInt(N)
            var u = f.rift.outline[2 * k] * f.scale
            var v = f.rift.outline[2 * k + 1] * f.scale
            val length = sqrt(u * u + v * v).coerceAtLeast(0.01f)
            var du = u / length
            var dv = v / length
            repeat(4) {
                val step = 0.3f + random.nextFloat() * 0.3f
                du += (random.nextFloat() - 0.5f) * 0.9f
                dv += (random.nextFloat() - 0.5f) * 0.9f
                val norm = sqrt(du * du + dv * dv).coerceAtLeast(0.01f)
                du /= norm
                dv /= norm
                val u2 = u + du * step
                val v2 = v + dv * step
                // A thin strip in the plane, across the segment.
                val pu = -dv * 0.05f
                val pv = du * 0.05f
                quad(buffer, f,
                    plane(f, u - pu, v - pv), 0.5f, 0.5f, colour,
                    plane(f, u2 - pu, v2 - pv), 0.5f, 0.5f, colour,
                    plane(f, u2 + pu, v2 + pv), 0.5f, 0.5f, colour,
                    plane(f, u + pu, v + pv), 0.5f, 0.5f, colour)
                u = u2
                v = v2
            }
        }
    }

    /** The part that must be crossed (about the server's default, 70 %), outlined. */
    private fun zone(buffer: VertexConsumer, f: Frame) {
        val colour = argb(0xFFFFFF, 0.8f, 1f)
        for (k in 0 until N) {
            val next = (k + 1) % N
            quad(buffer, f, point(f, k, 0.68f, 0f), 0.5f, 0.5f, colour, point(f, k, 0.72f, 0f), 0.5f, 0.5f, colour,
                point(f, next, 0.72f, 0f), 0.5f, 0.5f, colour, point(f, next, 0.68f, 0f), 0.5f, 0.5f, colour)
        }
    }

    private fun plane(f: Frame, u: Float, v: Float) = Vector3f(f.cx + u * f.rift.rightX, f.cy + v, f.cz + u * f.rift.rightZ)

    /** A square facing the camera at the rift's middle. */
    private fun halo(buffer: VertexConsumer, camera: Camera, f: Frame, size: Float, colour: Int) {
        val left = Vector3f(camera.leftVector).mul(size)
        val up = Vector3f(camera.upVector).mul(size)
        val c = Vector3f(f.cx, f.cy, f.cz)
        val a = Vector3f(c).add(left).add(up)
        val b = Vector3f(c).add(left).sub(up)
        val d = Vector3f(c).sub(left).sub(up)
        val e = Vector3f(c).sub(left).add(up)
        // Always facing the camera: never turned.
        vertex(buffer, a, 0f, 0f, colour)
        vertex(buffer, b, 0f, 1f, colour)
        vertex(buffer, d, 1f, 1f, colour)
        vertex(buffer, e, 1f, 0f, colour)
    }

    /** A quad in the rift's plane, turned to face the camera. */
    private fun quad(
        buffer: VertexConsumer, f: Frame,
        a: Vector3f, ua: Float, va: Float, ca: Int,
        b: Vector3f, ub: Float, vb: Float, cb: Int,
        c: Vector3f, uc: Float, vc: Float, cc: Int,
        d: Vector3f, ud: Float, vd: Float, cd: Int,
    ) {
        if (!f.flip) {
            vertex(buffer, a, ua, va, ca); vertex(buffer, b, ub, vb, cb); vertex(buffer, c, uc, vc, cc); vertex(buffer, d, ud, vd, cd)
        } else {
            vertex(buffer, a, ua, va, ca); vertex(buffer, d, ud, vd, cd); vertex(buffer, c, uc, vc, cc); vertex(buffer, b, ub, vb, cb)
        }
    }

    private fun vertex(buffer: VertexConsumer, p: Vector3f, u: Float, v: Float, colour: Int) {
        buffer.addVertex(p.x, p.y, p.z, colour, u, v, OverlayTexture.NO_OVERLAY, LightTexture.FULL_BRIGHT, 0f, 1f, 0f)
    }

    /** The colour times strength (additive layers: the strength is the light), with this alpha. */
    private fun argb(rgb: Int, strength: Float, alpha: Float): Int {
        val r = (((rgb shr 16) and 0xFF) * strength).toInt().coerceIn(0, 255)
        val g = (((rgb shr 8) and 0xFF) * strength).toInt().coerceIn(0, 255)
        val b = ((rgb and 0xFF) * strength).toInt().coerceIn(0, 255)
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun channel(shift: Int) = ((((a shr shift) and 0xFF) * (1 - t) + ((b shr shift) and 0xFF) * t).toInt() and 0xFF) shl shift
        return channel(16) or channel(8) or channel(0)
    }

    private fun smooth(x: Float): Float {
        val t = x.coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }
}
